package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.general.EmailRecognizer;
import de.skillshield.core.recognizer.general.Ipv4Recognizer;
import de.skillshield.core.recognizer.general.PathRecognizer;
import de.skillshield.core.recognizer.general.UrlRecognizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Hilfen des Features kvnr-bsnr-erkennung, die ohne jeden Aufruf des Produkts auskommen (Plan Kap. 5.2, T-1):
 * der Scanner {@link #ALT_TRENNER} mit den Erkennern der eingefrorenen Kopie, die unabhaengigen Referenzen fuer
 * Kontextwoerter und Form, und die Pruefregel {@link #bestandsabweichungen}. Die Meldungen der Tests nennen nur
 * Zahlen, nie Text oder Ziffern.
 *
 * <p><b>Referenzen.</b> {@link #kvnrKontextReferenz} und {@link #bsnrKontextReferenz} lesen den Buchstabenstrom des
 * Textes (alle Zeichen ohne {@link Character#isLetter(int)} entfallen, also auch Leerraum, Satzzeichen, Ziffern und
 * unsichtbare Zeichen; {@code A-Z} und {@code U+00C4} werden gefaltet) und suchen darin ein Kontextwort der Art oder
 * ein Vorwort mit {@code nr} bzw. {@code nummer}. Das ist bewusst eine Ueberschaetzung: Sperr- und Weit-Schreibweisen
 * und zufaellige Buchstabenfolgen zaehlen mit. Ein Text, in dem die Referenz nichts findet, hat sicher kein
 * Kontextwort der Art. {@link #kvnrFormReferenz} (weit, nur Messwert) ueberschaetzt die Texte mit einer KVNR-Form
 * ebenso; die Bedingung "KVNR-Form" der Pruefregel ist die enge Referenz {@link #kvnrFormEngTreffer}.
 *
 * <p><b>Pruefregel.</b> {@link #bestandsabweichungen} vergleicht die Fundliste von {@link #ALT_TRENNER} mit der des
 * neuen Standes auf Scannerebene (Plan Kap. 5.2, S-1 und S-2) und ordnet jede zulaessige Abweichung einem der
 * Kollisionsfaelle K-1 bis K-4 zu (Kap. 5.2, Tabelle, verschaerft durch Nachtrag 2 zu T-1). Alles andere ist ein
 * Verstoss. {@link #s3Pruefung} prueft S-3 oertlich.
 */
public final class KvnrBsnrHilfen {

    private KvnrBsnrHilfen() {
    }

    /** Kleines a mit Umlaut (U+00E4). */
    public static final char AE_KLEIN = (char) 0xE4;
    /** Grosses A mit Umlaut (U+00C4). */
    public static final char AE_GROSS = (char) 0xC4;

    private static final char NBSP = (char) 0xA0;
    private static final char DUENN = (char) 0x2009;
    private static final char SCHMAL_NBSP = (char) 0x202F;

    /** Typname der KVNR; ein Vergleich ueber den Namen haengt nicht davon ab, dass die Konstante schon existiert. */
    private static final String KVNR = "KVNR";
    private static final String BSNR = "BSNR";
    private static final String LANR = "LANR";
    private static final String IK = "IK";

    /**
     * Der Scanner mit dem Stand am Ende von kv-trenner-und-umbruch: E-Mail, IPv4, URL, Pfad und die Erkenner der
     * eingefrorenen Kopie fuer LANR und IK (Plan Kap. 5.2, {@code ALT}).
     */
    public static final TextScanner ALT_TRENNER = new TextScanner(List.of(new EmailRecognizer(),
            new Ipv4Recognizer(), new UrlRecognizer(), new PathRecognizer(), KvStandTrenner.lanr(),
            KvStandTrenner.ik()));

    // ------------------------------------------------------------------ Referenzen

    /** Kontextwoerter und Vorwoerter der KVNR (ASCII, klein; Plan D-2), eigene Liste unabhaengig vom Produkt. */
    private static final List<String> KVNR_WOERTER = List.of("kvnr", "versichertennummer", "versichertennr",
            "krankenversichertennummer", "krankenversichertennr", "krankenversicherungsnummer",
            "krankenversicherungsnr", "egknummer", "egknr");
    private static final List<String> KVNR_VORWOERTER = List.of("versicherten", "krankenversicherten", "egk", "kv");

    /** Kontextwoerter und Vorwoerter der BSNR (ASCII mit {@code ae}; die Formen mit Umlaut entstehen daraus). */
    private static final List<String> BSNR_WOERTER = List.of("bsnr", "bsnrnr", "nbsnr", "betriebsstaettennummer",
            "betriebsstaettennr", "nebenbetriebsstaettennummer", "nebenbetriebsstaettennr");
    private static final List<String> BSNR_VORWOERTER = List.of("betriebsstaette", "betriebsstaetten",
            "nebenbetriebsstaette", "nebenbetriebsstaetten");

    /**
     * Der Buchstabenstrom: nur Zeichen mit {@link Character#isLetter(int)}, {@code A-Z} und {@code U+00C4}
     * gefaltet, kein {@code toLowerCase} (wie im Scanner, damit zum Beispiel das Kelvin-Zeichen kein {@code k} wird).
     * Verstreute Buchstaben, zwischen denen anderes steht, bilden so ein Wort; siehe {@link #fenster} und Nachtrag 3
     * zu T-1, Punkt 3.
     */
    static String buchstabenstrom(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) {
                continue;
            }
            if (cp >= 'A' && cp <= 'Z') {
                sb.append((char) (cp + ('a' - 'A')));
            } else if (cp == AE_GROSS) {
                sb.append(AE_KLEIN);
            } else {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    private static boolean enthaeltWort(String strom, List<String> woerter, List<String> vorwoerter) {
        for (String w : woerter) {
            if (strom.contains(w) || strom.contains(w.replace("ae", String.valueOf(AE_KLEIN)))) {
                return true;
            }
        }
        for (String v : vorwoerter) {
            for (String nach : List.of("nr", "nummer")) {
                String wort = v + nach;
                if (strom.contains(wort) || strom.contains(wort.replace("ae", String.valueOf(AE_KLEIN)))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Ob der Buchstabenstrom des Textes ein KVNR-Kontextwort oder ein KVNR-Vorwort mit {@code nr}/{@code nummer}
     * enthaelt (Ueberschaetzung, siehe Klassenbeschreibung).
     */
    public static boolean kvnrKontextReferenz(String text) {
        return enthaeltWort(buchstabenstrom(text), KVNR_WOERTER, KVNR_VORWOERTER);
    }

    /**
     * Ob der Buchstabenstrom des Textes ein BSNR-Kontextwort oder ein BSNR-Vorwort mit {@code nr}/{@code nummer}
     * enthaelt (Ueberschaetzung, siehe Klassenbeschreibung); {@code ae} und {@code U+00E4}/{@code U+00C4} gelten
     * gleich.
     */
    public static boolean bsnrKontextReferenz(String text) {
        return enthaeltWort(buchstabenstrom(text), BSNR_WOERTER, BSNR_VORWOERTER);
    }

    /**
     * Ob irgendwo ein Grossbuchstabe {@code A-Z} unmittelbar von genau neun ASCII-Ziffern gefolgt wird (keine zehnte
     * Ziffer dahinter), ohne Pruefziffer und ohne Wortgrenzen davor. Nur ein Messwert (Ueberschaetzung der Texte
     * mit einer KVNR-Form); als Bedingung gilt die enge Referenz {@link #kvnrFormEngTreffer}.
     */
    public static boolean kvnrFormReferenz(String text) {
        for (int i = 0; i + NEUN_ZIFFERN < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 'A' || c > 'Z') {
                continue;
            }
            if (neunZiffernAb(text, i + 1) && (i + 1 + NEUN_ZIFFERN == text.length()
                    || !istAsciiZiffer(text.charAt(i + 1 + NEUN_ZIFFERN)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean istAsciiZiffer(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean neunZiffernAb(String text, int von) {
        if (von + NEUN_ZIFFERN > text.length()) {
            return false;
        }
        for (int k = 0; k < NEUN_ZIFFERN; k++) {
            if (!istAsciiZiffer(text.charAt(von + k))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Pruefziffer der KVNR nach Anforderung A-1 (eigene Rechnung, unabhaengig vom Produkt): der Buchstabe wird zur
     * zweistelligen Position im Alphabet (A = 01 bis Z = 26), zusammen mit den acht Ziffern ergeben sich zehn
     * Ziffern; sie werden von links abwechselnd mit 1, 2, 1, 2 ... gewichtet, von jedem Produkt wird die Quersumme
     * gebildet, die Summe modulo 10 ist die Pruefziffer (die neunte Ziffer nach dem Buchstaben). Lehrbeispiel der
     * Quelle: Buchstabe {@code A}, Ziffern eins bis acht aufsteigend, Pruefziffer 0.
     *
     * @param buchstabe   Grossbuchstabe {@code A-Z}
     * @param achtZiffern die ersten acht Ziffern nach dem Buchstaben
     */
    public static int kvnrPruefziffer(char buchstabe, String achtZiffern) {
        if (buchstabe < 'A' || buchstabe > 'Z' || achtZiffern.length() != ACHT_ZIFFERN) {
            throw new IllegalArgumentException("Buchstabe A-Z und genau acht Ziffern erwartet");
        }
        int wert = buchstabe - 'A' + 1;
        int summe = quersumme(wert / 10) + quersumme(2 * (wert % 10));
        for (int i = 0; i < ACHT_ZIFFERN; i++) {
            int ziffer = achtZiffern.charAt(i) - '0';
            int gewicht = i % 2 == 0 ? 1 : 2;
            summe += quersumme(ziffer * gewicht);
        }
        return summe % 10;
    }

    /** Die neun Ziffern einer KVNR: die acht Ziffern und danach die Pruefziffer nach {@link #kvnrPruefziffer}. */
    public static String kvnrNeunZiffern(char buchstabe, String achtZiffern) {
        return achtZiffern + kvnrPruefziffer(buchstabe, achtZiffern);
    }

    private static int quersumme(int zahl) {
        return zahl / 10 + zahl % 10;
    }

    /** Ein Bereich im Text, {@code start} inklusive, {@code ende} exklusive. */
    public record Bereich(int start, int ende) {
    }

    /**
     * Die Treffer der engen KVNR-Form (Anforderung E-3): Grossbuchstabe {@code A-Z}, davor kein Buchstabe und keine
     * Ziffer, genau neun ASCII-Ziffern, dahinter kein Buchstabe und keine Ziffer, Pruefziffer nach A-1 gueltig. Jeder
     * Treffer umfasst den Buchstaben und die neun Ziffern. Der Vergleich ueber {@link Character#isLetterOrDigit} ist
     * bewusst weit: je mehr Zeichen die Grenze verletzen, desto weniger Treffer gibt es und desto strenger wird
     * geprueft.
     */
    public static List<Bereich> kvnrFormEngTreffer(String text) {
        List<Bereich> treffer = new ArrayList<>();
        for (int i = 0; i + NEUN_ZIFFERN < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 'A' || c > 'Z' || !neunZiffernAb(text, i + 1)) {
                continue;
            }
            int ende = i + 1 + NEUN_ZIFFERN;
            boolean davorFrei = i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1));
            boolean dahinterFrei = ende == text.length() || !Character.isLetterOrDigit(text.charAt(ende));
            String acht = text.substring(i + 1, i + 1 + ACHT_ZIFFERN);
            int pruef = text.charAt(ende - 1) - '0';
            if (davorFrei && dahinterFrei && kvnrPruefziffer(c, acht) == pruef) {
                treffer.add(new Bereich(i, ende));
            }
        }
        return treffer;
    }

    /** Ob der Text mindestens einen Treffer der engen KVNR-Form hat. */
    public static boolean kvnrFormEng(String text) {
        return !kvnrFormEngTreffer(text).isEmpty();
    }

    // ------------------------------------------------------------------ Pruefregel

    /**
     * Ergebnis der Pruefregel fuer einen Text: Zahl der alten Funde je Kollisionsfall und Zahl der Verstoesse.
     *
     * @param k1         alte Funde, die ein KVNR-Fund mit vorangestelltem Buchstaben ersetzt (K-1)
     * @param k2         alte Funde, deren Typ bei gleicher Konfidenz zu KVNR/BSNR wechselt (K-2)
     * @param k3         alte Funde, deren Typ bei hoeherer Konfidenz zu KVNR/BSNR wechselt (K-3)
     * @param k4         alte Funde, die ein zusammengelegter Fund umfasst (K-4)
     * @param verstoesse verlorene Abdeckung (je Text einmal) und alle alten Funde ohne zulaessige Entsprechung
     */
    public record Abweichungen(int k1, int k2, int k3, int k4, int verstoesse) {

        /** Ob weder ein Kollisionsfall noch ein Verstoss vorliegt. */
        public boolean keine() {
            return k1 + k2 + k3 + k4 + verstoesse == 0;
        }

        /** Summe der Kollisionsfaelle K-1 bis K-4. */
        public int kollisionen() {
            return k1 + k2 + k3 + k4;
        }

        /** Summe je Feld. */
        public Abweichungen plus(Abweichungen o) {
            return new Abweichungen(k1 + o.k1, k2 + o.k2, k3 + o.k3, k4 + o.k4, verstoesse + o.verstoesse);
        }

        /** Kurzform ohne Text und Ziffern, fuer Meldungen. */
        public String kurz() {
            return "K-1 " + k1 + ", K-2 " + k2 + ", K-3 " + k3 + ", K-4 " + k4 + ", Verstoesse " + verstoesse;
        }
    }

    /** Die Null der Summenbildung. */
    public static final Abweichungen KEINE = new Abweichungen(0, 0, 0, 0, 0);

    /** Einordnung eines alten Fundes durch {@link #ordneEin}. */
    private enum Fall {
        EXAKT, K1, K2, K3, K4, KEIN_FALL
    }

    /** Fenster der oertlichen Bedingung: Zeichen vor dem Anfang und hinter dem Ende des neuen Fundes. */
    public static final int NAEHE = 120;
    /** Untergrenze der Konfidenz bei K-1: mindestens {@code min(alte Konfidenz, MIN_KONFIDENZ_K1)}. */
    private static final double MIN_KONFIDENZ_K1 = 0.50;
    /** Untergrenze der Konfidenz bei K-4: mindestens {@code min(alte Konfidenz, MIN_KONFIDENZ_K4)}. */
    private static final double MIN_KONFIDENZ_K4 = 0.40;
    /**
     * Obergrenze bei K-4 (Nachtrag 3 zu T-1, Punkt 2): Zahl der Positionen des neuen Fundes, die von keinem alten Fund
     * abgedeckt sind (eine KVNR-/BSNR-Schreibweise mit Trennern).
     */
    public static final int MAX_UNABGEDECKT_K4 = 24;
    /** Abstand des neuen KVNR-Fundes bei K-1: nur der Buchstabe. */
    private static final int ABSTAND_NUR_BUCHSTABE = 1;
    /** Abstand des neuen KVNR-Fundes bei K-1: Buchstabe und ein Gruppentrenner. */
    private static final int ABSTAND_BUCHSTABE_UND_TRENNER = 2;
    private static final int ACHT_ZIFFERN = 8;
    private static final int NEUN_ZIFFERN = 9;

    /**
     * Die Pruefregel aus Plan Kap. 5.2 mit Nachtrag 2 zu T-1 auf einem Text. {@code alt} ist die Fundliste von
     * {@link #ALT_TRENNER}, {@code neu} die des neuen Standes. "In der Naehe" heisst: innerhalb des neuen Fundes oder
     * hoechstens {@link #NAEHE} Zeichen vor seinem Anfang oder hinter seinem Ende (ein Kontextwort muss ganz im
     * Fenster liegen, eine Form muss das Fenster beruehren). Die KVNR-Form ist die enge Referenz
     * {@link #kvnrFormEngTreffer}.
     *
     * <ul>
     * <li>S-1: Jede Position, die {@code alt} abdeckt, deckt {@code neu} ab; sonst ein Verstoss je Text.</li>
     * <li>S-2: Zu jedem alten Fund {@code f} gibt es einen neuen Fund mit gleichem Start, Ende und Typ und einer
     * Konfidenz, die nicht kleiner ist; oder es gilt einer der Kollisionsfaelle, jeder nur unter seinen
     * Bedingungen: K-1 (neuer KVNR-Fund mit gleichem Ende, ein oder zwei Zeichen vor {@code f}, Buchstabe und
     * gegebenenfalls ein Gruppentrenner; {@code f} ist LANR oder IK; Konfidenz mindestens
     * {@code min(alt, 0,50)}; in der Naehe des neuen Fundes ein KVNR-Kontextwort oder die enge KVNR-Form), K-2 und
     * K-3 (gleicher Bereich, KVNR oder BSNR, bei gleicher Konfidenz nur, wenn der Typname lexikographisch vor dem
     * von {@code f} steht, bei hoeherer Konfidenz immer; {@code f} ist LANR oder IK; in der Naehe ein
     * Kontextwort der Art des neuen Typs), K-4 (ein zusammengelegter Fund, der {@code f} umfasst und echt groesser
     * ist; Typ eines alten Fundes, der ganz in ihm liegt, oder (nur bei altem LANR-/IK-Fund) KVNR oder BSNR;
     * hoechstens {@link #MAX_UNABGEDECKT_K4} Positionen ohne alten Fund; Konfidenz mindestens
     * {@code min(alt, 0,40)}; in der Naehe ein KVNR- oder BSNR-Kontextwort oder die enge KVNR-Form). Jeder andere
     * alte Fund ohne Entsprechung ist ein Verstoss.</li>
     * </ul>
     */
    public static Abweichungen bestandsabweichungen(String text, List<Finding> alt, List<Finding> neu) {
        List<Bereich> formen = kvnrFormEngTreffer(text);
        int k1 = 0;
        int k2 = 0;
        int k3 = 0;
        int k4 = 0;
        int verstoesse = abdeckungVerloren(text.length(), alt, neu) ? 1 : 0;
        for (Finding f : alt) {
            Fall fall = ordneEin(text, f, alt, neu, formen);
            if (fall == Fall.K1) {
                k1++;
            } else if (fall == Fall.K2) {
                k2++;
            } else if (fall == Fall.K3) {
                k3++;
            } else if (fall == Fall.K4) {
                k4++;
            } else if (fall == Fall.KEIN_FALL) {
                verstoesse++;
            }
        }
        return new Abweichungen(k1, k2, k3, k4, verstoesse);
    }

    /**
     * Ergebnis von {@link #s3Pruefung}.
     *
     * @param geprueft   Zahl der Funde (alte und neue), in deren Naehe weder Kontextwort noch Form steht
     * @param verstoesse davon Funde ohne gleichen Fund (alle Felder) in der jeweils anderen Liste
     */
    public record S3(int geprueft, int verstoesse) {
    }

    /**
     * S-3 (Plan Kap. 5.2, Nachtrag 2 zu T-1): Jeder alte und jeder neue Fund, in dessen Naehe (Fenster wie bei
     * {@link #bestandsabweichungen}) weder ein KVNR- oder BSNR-Kontextwort noch die enge KVNR-Form steht, hat in der
     * anderen Liste einen gleichen Fund (alle Felder).
     */
    public static S3 s3Pruefung(String text, List<Finding> alt, List<Finding> neu) {
        List<Bereich> formen = kvnrFormEngTreffer(text);
        boolean irgendwo = !formen.isEmpty() || kvnrKontextReferenz(text) || bsnrKontextReferenz(text);
        int geprueft = 0;
        int verstoesse = 0;
        for (Finding f : alt) {
            if (!irgendwo || !kontextNahe(text, f, formen)) {
                geprueft++;
                verstoesse += neu.contains(f) ? 0 : 1;
            }
        }
        for (Finding g : neu) {
            if (!irgendwo || !kontextNahe(text, g, formen)) {
                geprueft++;
                verstoesse += alt.contains(g) ? 0 : 1;
            }
        }
        return new S3(geprueft, verstoesse);
    }

    // ------------------------------------------------------------------ oertliche Bedingungen

    /**
     * Das Fenster um den neuen Fund. Die Kontextwort-Referenzen arbeiten auf dem Buchstabenstrom des Fensters
     * ({@link #buchstabenstrom}); verstreute Buchstaben im Fenster koennen daher ein Kontextwort bilden (bewusste
     * Ueberschaetzung von "nahe"). In T-1 bleibt das so; Nachtrag 3 zu T-1, Punkt 3, regelt die Zaehlung dieser
     * Faelle fuer spaetere Tasks.
     */
    private static String fenster(String text, Finding g) {
        return text.substring(Math.max(0, g.start() - NAEHE), Math.min(text.length(), g.end() + NAEHE));
    }

    private static boolean formNahe(String text, Finding g, List<Bereich> formen) {
        int von = Math.max(0, g.start() - NAEHE);
        int bis = Math.min(text.length(), g.end() + NAEHE);
        for (Bereich b : formen) {
            if (b.start() < bis && b.ende() > von) {
                return true;
            }
        }
        return false;
    }

    private static boolean kvnrKontextNahe(String text, Finding g) {
        return kvnrKontextReferenz(fenster(text, g));
    }

    private static boolean bsnrKontextNahe(String text, Finding g) {
        return bsnrKontextReferenz(fenster(text, g));
    }

    private static boolean kvnrKontextOderFormNahe(String text, Finding g, List<Bereich> formen) {
        return kvnrKontextNahe(text, g) || formNahe(text, g, formen);
    }

    private static boolean kontextNahe(String text, Finding g, List<Bereich> formen) {
        return kvnrKontextOderFormNahe(text, g, formen) || bsnrKontextNahe(text, g);
    }

    // ------------------------------------------------------------------ Einordnung

    private static boolean abdeckungVerloren(int laenge, List<Finding> alt, List<Finding> neu) {
        boolean[] imNeuen = new boolean[laenge];
        for (Finding g : neu) {
            for (int i = g.start(); i < g.end(); i++) {
                imNeuen[i] = true;
            }
        }
        for (Finding f : alt) {
            for (int i = f.start(); i < f.end(); i++) {
                if (!imNeuen[i]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Ob der Fund ein LANR- oder IK-Fund ist (Typvergleich ueber den Namen). */
    public static boolean istKv(Finding f) {
        String name = f.type().name();
        return name.equals(LANR) || name.equals(IK);
    }

    private static boolean istNeueArt(Finding g) {
        String name = g.type().name();
        return name.equals(KVNR) || name.equals(BSNR);
    }

    /** Ob das Zeichen ein Gruppentrenner der Ziffernketten ist (Leerzeichen, U+00A0, U+2009, U+202F, - . /). */
    private static boolean istTrenner(char c) {
        return c == ' ' || c == NBSP || c == DUENN || c == SCHMAL_NBSP || c == '-' || c == '.' || c == '/';
    }

    /**
     * Gross- und Kleinbuchstaben {@code A-Z}: Eine KVNR mit Kontextwort darf auch mit Kleinbuchstaben stehen
     * (Anforderung D-1), also gilt fuer K-1 mit Kontextwort jeder ASCII-Buchstabe; die enge Form verlangt dagegen
     * Grossbuchstaben.
     */
    private static boolean istAsciiBuchstabe(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean gleicherBereich(Finding f, Finding g) {
        return g.start() == f.start() && g.end() == f.end();
    }

    private static Fall ordneEin(String text, Finding f, List<Finding> alt, List<Finding> neu,
            List<Bereich> formen) {
        for (Finding g : neu) {
            if (gleicherBereich(f, g) && g.type().equals(f.type()) && g.confidence() >= f.confidence()) {
                return Fall.EXAKT;
            }
        }
        if (istKv(f)) {
            for (Finding g : neu) {
                Fall typwechsel = typwechsel(text, f, g);
                if (typwechsel != Fall.KEIN_FALL) {
                    return typwechsel;
                }
                if (istK1(text, f, g, formen)) {
                    return Fall.K1;
                }
            }
        }
        for (Finding g : neu) {
            if (istK4(text, f, alt, g, formen)) {
                return Fall.K4;
            }
        }
        return Fall.KEIN_FALL;
    }

    /** K-3 oder K-2 (gleicher Bereich, Typ KVNR oder BSNR, Kontextwort der Art in der Naehe), sonst KEIN_FALL. */
    private static Fall typwechsel(String text, Finding f, Finding g) {
        if (!gleicherBereich(f, g) || !istNeueArt(g)) {
            return Fall.KEIN_FALL;
        }
        boolean kontext = g.type().name().equals(KVNR) ? kvnrKontextNahe(text, g) : bsnrKontextNahe(text, g);
        if (kontext && g.confidence() > f.confidence()) {
            return Fall.K3;
        }
        boolean davor = g.type().name().compareTo(f.type().name()) < 0;
        if (kontext && davor && g.confidence() == f.confidence()) {
            return Fall.K2;
        }
        return Fall.KEIN_FALL;
    }

    private static boolean istK1(String text, Finding f, Finding g, List<Bereich> formen) {
        return g.type().name().equals(KVNR) && g.end() == f.end() && vorangestellterBuchstabe(text, g, f)
                && g.confidence() >= Math.min(f.confidence(), MIN_KONFIDENZ_K1)
                && kvnrKontextOderFormNahe(text, g, formen);
    }

    /**
     * K-4 nach Nachtrag 3 zu T-1: Typ (ist {@code f} kein LANR-/IK-Fund, gilt nur der Typ eines alten Fundes, der
     * ganz in {@code g} liegt; fuer LANR/IK zusaetzlich KVNR und BSNR) und Ausdehnung (hoechstens
     * {@link #MAX_UNABGEDECKT_K4} Positionen von {@code g} ohne alten Fund).
     */
    private static boolean istK4(String text, Finding f, List<Finding> alt, Finding g, List<Bereich> formen) {
        boolean umfasst = g.start() <= f.start() && g.end() >= f.end();
        boolean echtGroesser = g.start() < f.start() || g.end() > f.end();
        if (!g.source().equals(Finding.SOURCE_MERGED) || !umfasst || !echtGroesser) {
            return false;
        }
        if (g.confidence() < Math.min(f.confidence(), MIN_KONFIDENZ_K4)) {
            return false;
        }
        boolean typErlaubt = typEinesUmfasstenAltenFundes(g, alt) || (istKv(f) && istNeueArt(g));
        return typErlaubt && unabgedeckt(g, alt) <= MAX_UNABGEDECKT_K4
                && (kvnrKontextOderFormNahe(text, g, formen) || bsnrKontextNahe(text, g));
    }

    /** Zahl der Positionen von {@code g}, die kein alter Fund abdeckt. */
    private static int unabgedeckt(Finding g, List<Finding> alt) {
        int zahl = 0;
        for (int i = g.start(); i < g.end(); i++) {
            boolean abgedeckt = false;
            for (Finding h : alt) {
                if (i >= h.start() && i < h.end()) {
                    abgedeckt = true;
                    break;
                }
            }
            zahl += abgedeckt ? 0 : 1;
        }
        return zahl;
    }

    private static boolean typEinesUmfasstenAltenFundes(Finding g, List<Finding> alt) {
        for (Finding h : alt) {
            if (h.start() >= g.start() && h.end() <= g.end() && h.type().equals(g.type())) {
                return true;
            }
        }
        return false;
    }

    /** Ob {@code g} genau einen Buchstaben und hoechstens einen Gruppentrenner vor {@code f} beginnt. */
    private static boolean vorangestellterBuchstabe(String text, Finding g, Finding f) {
        int vor = f.start() - g.start();
        if (vor != ABSTAND_NUR_BUCHSTABE && vor != ABSTAND_BUCHSTABE_UND_TRENNER) {
            return false;
        }
        if (!istAsciiBuchstabe(text.charAt(g.start()))) {
            return false;
        }
        return vor == ABSTAND_NUR_BUCHSTABE || istTrenner(text.charAt(g.start() + 1));
    }
}
