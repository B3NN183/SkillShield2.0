package de.skillshield.core;

import de.skillshield.core.conflict.ConflictResolver;
import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.general.EmailRecognizer;
import de.skillshield.core.recognizer.general.Ipv4Recognizer;
import de.skillshield.core.recognizer.general.PathRecognizer;
import de.skillshield.core.recognizer.general.UrlRecognizer;
import de.skillshield.core.recognizer.kv.KvStandTrenner;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.Bereich;
import de.skillshield.core.recognizer.kv.KvnrRecognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Nachrechnung der Kollisionsfaelle (Plan kvnr-bsnr-erkennung, Nachtrag 3 zu T-1, Punkt 3; Nachtrag 2 und 3 zu T-6,
 * Punkt 5). Die Hilfe steht in einer eigenen Klasse, weil {@link KvnrBsnrHilfen} seit T-1 eingefroren ist.
 *
 * <p>Was sie absichert und was nicht: Die Nachrechnung bildet die Zusammensetzung von {@code TextScanner.standard()}
 * nach (E-Mail, IPv4, URL, Pfad, LANR und IK der eingefrorenen Kopie, KVNR) und die Filterstufe (Vorrang des
 * Bestands, Zuschneiden nach Nachtrag 3 zu T-6) unabhaengig von der Produktklasse nach: Sie bestimmt den Bestand
 * selbst (Positionen der Funde von EMAIL, IP, URL und PFAD im Scan ohne KVNR), schneidet die KVNR-Kandidaten
 * zeichenweise zu ({@link #kvnrZuschnitt}) und fuehrt alles mit {@link ConflictResolver#resolve} zusammen. Sie
 * sichert damit die Zusammensetzung und die Gleichheit der LANR-/IK-Erkenner mit der Kopie und dass die
 * Produktfilterstufe dasselbe zuschneidet. Sie entscheidet nicht, ob ein Kollisionsfall zulaessig ist; das tut
 * allein die eingefrorene Pruefregel ({@code bestandsabweichungen}).
 *
 * <p>Fuer jeden alten Fund {@code f} (Fundliste des alten Scanners), zu dem das Produkt keinen gleichen Fund
 * (gleicher Bereich und Typ, mindestens gleiche Konfidenz) liefert, ist der ueberlappende Produktfund {@code g}
 * der Kollisionspartner. Ist {@code g} ein zusammengelegter Fund, der {@code f} umfasst und echt groesser ist, liegt
 * ein K-4-Fall vor; die Liste der Nachrechnung muss dann einen Fund mit Bereich, Typ und Konfidenz von {@code g}
 * enthalten.
 *
 * <p>Zusaetzlich gezaehlt werden die Kollisionsfaelle, die nach der eingefrorenen Pruefregel nur ueber verstreute
 * Buchstaben im Fenster als "nahe" gelten (Kontextwort nicht als zusammenhaengendes Wort im Fenster, keine enge
 * KVNR-Form im Fenster), und die Typwechsel bei Nicht-KV-Alttypen: ein alter Fund von E-Mail, IP, URL oder Pfad,
 * den ein Produktfund anderen Typs auch nur teilweise ueberdeckt, unabhaengig von K-4 und von der Konfidenz. Alle
 * Meldungen nennen nur Zahlen, nie Text oder Ziffern.
 */
public final class KvnrKollisionsNachrechnung {

    /**
     * Zaehler einer Nachrechnung.
     *
     * @param kollisionen       alte Funde ohne gleichen Produktfund, die ein ueberlappender Produktfund ersetzt
     * @param k4Faelle          davon K-4-Faelle (zusammengelegter Fund umfasst den alten Fund echt)
     * @param k4Abweichungen    K-4-Faelle, bei denen die Nachrechnung keinen Fund mit Bereich, Typ und Konfidenz
     *                          des Produktfundes liefert
     * @param nurVerstreut      Kollisionsfaelle, die nur ueber verstreute Buchstaben im Fenster als nahe gelten
     * @param typwechselNichtKv alte Funde eines Nicht-KV-Alttyps (nicht LANR/IK), die ein Produktfund anderen Typs
     *                          ueberdeckt (unabhaengig von K-4)
     * @param listenUngleich    Texte, in denen die ganze nachgerechnete Liste nicht gleich der Produktliste ist
     */
    public record Ergebnis(int kollisionen, int k4Faelle, int k4Abweichungen, int nurVerstreut,
            int typwechselNichtKv, int listenUngleich) {

        /** Die Null der Summenbildung. */
        public static final Ergebnis NULL = new Ergebnis(0, 0, 0, 0, 0, 0);

        /** Summe je Feld. */
        public Ergebnis plus(Ergebnis o) {
            return new Ergebnis(kollisionen + o.kollisionen, k4Faelle + o.k4Faelle,
                    k4Abweichungen + o.k4Abweichungen, nurVerstreut + o.nurVerstreut,
                    typwechselNichtKv + o.typwechselNichtKv, listenUngleich + o.listenUngleich);
        }

        /** Kurzform ohne Text und Ziffern, fuer Meldungen. */
        public String kurz() {
            return "Kollisionen " + kollisionen + ", K-4 " + k4Faelle + ", K-4-Abweichungen " + k4Abweichungen
                    + ", nur verstreut " + nurVerstreut + ", Typwechsel Nicht-KV " + typwechselNichtKv
                    + ", Listen ungleich " + listenUngleich;
        }
    }

    /** Kontextwoerter (ASCII, klein; {@code ae} fuer den Umlaut) der KVNR und der BSNR, eigene Liste. */
    private static final List<String> WOERTER = List.of("kvnr", "versichertennummer", "versichertennr",
            "krankenversichertennummer", "krankenversichertennr", "krankenversicherungsnummer",
            "krankenversicherungsnr", "egknummer", "egknr", "bsnr", "betriebsstaettennummer",
            "betriebsstaettennr", "nebenbetriebsstaettennummer", "nebenbetriebsstaettennr");
    /** Vorwoerter, auf die hoechstens zwei Trennzeichen und {@code nr} oder {@code nummer} folgen. */
    private static final List<String> VORWOERTER = List.of("versicherten", "krankenversicherten", "egk", "kv",
            "betriebsstaette", "betriebsstaetten", "nebenbetriebsstaette", "nebenbetriebsstaetten");
    private static final int MAX_TRENNZEICHEN = 2;
    private static final char AE_KLEIN = (char) 0xE4;

    private KvnrKollisionsNachrechnung() {
    }

    /**
     * Ergebnis des unabhaengig nachgebildeten Zuschneidens der KVNR-Kandidaten.
     *
     * @param kandidaten  die KVNR-Kandidaten, die in die Aufloesung gehen: unveraenderte Kandidaten ohne
     *                    gemeinsame Position mit dem Bestand und die Restfunde
     * @param restfunde   Zahl der Restfunde (Quelle {@code kvnr-rest})
     * @param unterSchwelle Zahl der entfallenen Reststuecke, die nach dem Kuerzen noch Zeichen, aber weniger als
     *                    drei Ziffern haben
     * @param zugeschnitten Zahl der KVNR-Kandidaten, die mindestens eine Position mit dem Bestand teilen
     */
    public record Zuschnitt(List<Finding> kandidaten, int restfunde, int unterSchwelle, int zugeschnitten) {
    }

    /**
     * Bildet das Zuschneiden der KVNR-Kandidaten (Nachtrag 3 zu T-6) mit einem einfachen zeichenweisen Algorithmus
     * nach: Der Bestand ist die Menge der Positionen, die ein Fund der Typen EMAIL, IP, URL oder PFAD im Scan ohne
     * KVNR-Erkenner abdeckt (Aufloesung der Kandidaten der alten Erkenner mit {@link ConflictResolver#resolve}).
     * Von jedem KVNR-Kandidaten mit mindestens einer Bestandsposition bleiben die zusammenhaengenden Laeufe
     * ausserhalb des Bestands; jeder Lauf wird an beiden Enden auf ASCII-Buchstaben und -Ziffern gekuerzt und
     * bleibt als Fund (Typ KVNR, Quelle {@code kvnr-rest}, Konfidenz hoechstens 0,80), wenn er mindestens drei
     * Ziffern hat. Kandidaten ohne Bestandsposition bleiben unveraendert.
     */
    public static Zuschnitt kvnrZuschnitt(String text) {
        List<Finding> alte = new ArrayList<>();
        for (Recognizer r : List.of(new EmailRecognizer(), new Ipv4Recognizer(), new UrlRecognizer(),
                new PathRecognizer(), KvStandTrenner.lanr(), KvStandTrenner.ik())) {
            alte.addAll(r.detect(text));
        }
        boolean[] imBestand = new boolean[text.length()];
        for (Finding f : ConflictResolver.resolve(text, alte)) {
            if (istBestandstyp(f)) {
                for (int i = f.start(); i < f.end(); i++) {
                    imBestand[i] = true;
                }
            }
        }
        List<Finding> ergebnis = new ArrayList<>();
        int restfunde = 0;
        int unterSchwelle = 0;
        int zugeschnitten = 0;
        for (Finding k : new KvnrRecognizer().detect(text)) {
            boolean beruehrt = false;
            for (int i = k.start(); i < k.end(); i++) {
                beruehrt |= imBestand[i];
            }
            if (!beruehrt) {
                ergebnis.add(k);
                continue;
            }
            zugeschnitten++;
            int i = k.start();
            while (i < k.end()) {
                if (imBestand[i]) {
                    i++;
                    continue;
                }
                int j = i;
                while (j < k.end() && !imBestand[j]) {
                    j++;
                }
                int von = i;
                int bis = j;
                while (von < bis && !asciiBuchstabeOderZiffer(text.charAt(von))) {
                    von++;
                }
                while (bis > von && !asciiBuchstabeOderZiffer(text.charAt(bis - 1))) {
                    bis--;
                }
                int ziffern = 0;
                for (int p = von; p < bis; p++) {
                    ziffern += text.charAt(p) >= '0' && text.charAt(p) <= '9' ? 1 : 0;
                }
                if (ziffern >= 3) {
                    ergebnis.add(new Finding(k.type(), text.substring(von, bis), von, bis,
                            Math.min(k.confidence(), 0.80), "kvnr-rest"));
                    restfunde++;
                } else if (bis > von) {
                    unterSchwelle++;
                }
                i = j;
            }
        }
        return new Zuschnitt(ergebnis, restfunde, unterSchwelle, zugeschnitten);
    }

    private static boolean asciiBuchstabeOderZiffer(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /**
     * Die Kandidaten der alten Erkenner und die zugeschnittenen KVNR-Kandidaten ({@link #kvnrZuschnitt}), mit
     * {@link ConflictResolver#resolve} zusammengefuehrt (eigene Bestimmung, nicht der Code des Produkts).
     */
    public static List<Finding> nachgerechnet(String text) {
        List<Finding> kandidaten = new ArrayList<>();
        for (Recognizer r : List.of(new EmailRecognizer(), new Ipv4Recognizer(), new UrlRecognizer(),
                new PathRecognizer(), KvStandTrenner.lanr(), KvStandTrenner.ik())) {
            kandidaten.addAll(r.detect(text));
        }
        kandidaten.addAll(kvnrZuschnitt(text).kandidaten());
        return ConflictResolver.resolve(text, kandidaten);
    }

    private static boolean istBestandstyp(Finding f) {
        String name = f.type().name();
        return name.equals("EMAIL") || name.equals("IP") || name.equals("URL") || name.equals("PFAD");
    }

    /**
     * Ob der Text ein Kontextwort der KVNR oder BSNR als zusammenhaengendes Wort enthaelt (auch mit hoechstens zwei
     * Trennzeichen zwischen Vorwort und {@code Nr}/{@code Nummer}). Sperr- und Weit-Schreibweisen ({@code K V N R})
     * sind kein zusammenhaengendes Wort.
     */
    public static boolean zusammenhaengendesKontextwort(String fenster) {
        String klein = fenster.toLowerCase(Locale.ROOT).replace(String.valueOf(AE_KLEIN), "ae");
        for (String w : WOERTER) {
            if (klein.contains(w)) {
                return true;
            }
        }
        for (String v : VORWOERTER) {
            int i = klein.indexOf(v);
            while (i >= 0) {
                int j = i + v.length();
                int uebersprungen = 0;
                while (j < klein.length() && uebersprungen < MAX_TRENNZEICHEN
                        && !Character.isLetterOrDigit(klein.charAt(j))) {
                    j++;
                    uebersprungen++;
                }
                if (klein.startsWith("nr", j) || klein.startsWith("nummer", j)) {
                    return true;
                }
                i = klein.indexOf(v, i + 1);
            }
        }
        return false;
    }

    private static boolean gleicherFund(Finding f, Finding g) {
        return g.start() == f.start() && g.end() == f.end() && g.type().equals(f.type())
                && g.confidence() >= f.confidence();
    }

    private static boolean ueberlappt(Finding f, Finding g) {
        return g.start() < f.end() && g.end() > f.start();
    }

    private static boolean istK4Paar(Finding f, Finding g) {
        return g.source().equals(Finding.SOURCE_MERGED) && g.start() <= f.start() && g.end() >= f.end()
                && (g.start() < f.start() || g.end() > f.end());
    }

    private static boolean nachrechnungEnthaelt(List<Finding> nach, Finding g) {
        for (Finding h : nach) {
            if (h.start() == g.start() && h.end() == g.end() && h.type().equals(g.type())
                    && Double.compare(h.confidence(), g.confidence()) == 0) {
                return true;
            }
        }
        return false;
    }

    /** Ob das Fenster um {@code g} nach der eingefrorenen Pruefregel nahe ist (Kontextwort nach Buchstabenstrom). */
    private static boolean naheNachPruefregel(String text, Finding g, List<Bereich> formen) {
        int von = Math.max(0, g.start() - KvnrBsnrHilfen.NAEHE);
        int bis = Math.min(text.length(), g.end() + KvnrBsnrHilfen.NAEHE);
        String fenster = text.substring(von, bis);
        return KvnrBsnrHilfen.kvnrKontextReferenz(fenster) || KvnrBsnrHilfen.bsnrKontextReferenz(fenster)
                || formNahe(formen, von, bis);
    }

    private static boolean formNahe(List<Bereich> formen, int von, int bis) {
        for (Bereich b : formen) {
            if (b.start() < bis && b.ende() > von) {
                return true;
            }
        }
        return false;
    }

    private static boolean nurUeberVerstreuteBuchstaben(String text, Finding g, List<Bereich> formen) {
        if (!naheNachPruefregel(text, g, formen)) {
            return false;
        }
        int von = Math.max(0, g.start() - KvnrBsnrHilfen.NAEHE);
        int bis = Math.min(text.length(), g.end() + KvnrBsnrHilfen.NAEHE);
        return !zusammenhaengendesKontextwort(text.substring(von, bis)) && !formNahe(formen, von, bis);
    }

    /**
     * Rechnet einen Text nach.
     *
     * @param text    der Text
     * @param alt     Fundliste des alten Scanners
     * @param produkt Fundliste des Produkts (in Selbsttests auch eine gezielt veraenderte)
     */
    public static Ergebnis rechne(String text, List<Finding> alt, List<Finding> produkt) {
        List<Finding> nach = nachgerechnet(text);
        List<Bereich> formen = KvnrBsnrHilfen.kvnrFormEngTreffer(text);
        int kollisionen = 0;
        int k4 = 0;
        int abweichungen = 0;
        int verstreut = 0;
        int typwechsel = 0;
        for (Finding f : alt) {
            Finding partner = partner(f, produkt);
            if (partner == null) {
                continue;
            }
            kollisionen++;
            verstreut += nurUeberVerstreuteBuchstaben(text, partner, formen) ? 1 : 0;
            if (istK4Paar(f, partner)) {
                k4++;
                abweichungen += nachrechnungEnthaelt(nach, partner) ? 0 : 1;
            }
        }
        for (Finding f : alt) {
            typwechsel += !KvnrBsnrHilfen.istKv(f) && ueberdecktVonAnderemTyp(f, produkt) ? 1 : 0;
        }
        int ungleich = nach.equals(produkt) ? 0 : 1;
        return new Ergebnis(kollisionen, k4, abweichungen, verstreut, typwechsel, ungleich);
    }

    private static boolean ueberdecktVonAnderemTyp(Finding f, List<Finding> produkt) {
        for (Finding g : produkt) {
            if (ueberlappt(f, g) && !g.type().equals(f.type())) {
                return true;
            }
        }
        return false;
    }

    /** Der ueberlappende Produktfund eines alten Fundes ohne gleichen Produktfund, sonst {@code null}. */
    private static Finding partner(Finding f, List<Finding> produkt) {
        for (Finding g : produkt) {
            if (gleicherFund(f, g)) {
                return null;
            }
        }
        for (Finding g : produkt) {
            if (ueberlappt(f, g)) {
                return g;
            }
        }
        return null;
    }
}
