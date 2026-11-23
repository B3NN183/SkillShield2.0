package de.skillshield.core.recognizer.kv;

import de.skillshield.core.SynthetischeKvNummern;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen.Bereich;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hilfen des Fehlalarm-Pruefstands 1b (Feature kvnr-bsnr-erkennung, T-9; AK-16, D-4, D-5, E-3). Diese Klasse ist
 * ab der Abnahme von T-9 eingefroren (Plan Regel 13); erlaubt sind reine Ergaenzungen. Sie ruft das Produkt nicht auf.
 *
 * <ul>
 * <li>{@link #kvnrOhneKontextReferenz}: eigenstaendige, zeichenkettenbasierte Umsetzung der Regel 1b (Plan Kap. 4.3,
 * Schritte 1 bis 5, ohne den Abgleich mit Funden mit Kontextwort), schaltbar ueber {@link Optionen}.</li>
 * <li>{@link #kuenstlicherErkenner}: ein {@link Recognizer} auf Grundlage der Referenz; mit abgeschalteter
 * Eigenschaft ist er der "kaputte" Erkenner der Selbsttests (jede Eigenschaft muss den Pruefstand anschlagen
 * lassen).</li>
 * <li>{@link #FREMDMUSTER}: der feste Satz von Fremdmustern fuer AK-16 (b), gebildet zur Laufzeit aus
 * {@link SynthetischeKvNummern#fremdKvnr} mit festem Seed (keine Nummer als Literal).</li>
 * <li>{@link #zufallsFremdnummernTexte}: die Texte fuer AK-16 (c), je ein Satz aus neutralen Rauschwoertern mit einer
 * {@link SynthetischeKvNummern#zufallsKvnr}.</li>
 * </ul>
 * Meldungen und Beschreibungen nennen nie Text oder Ziffern aus Testdaten.
 */
public final class KvnrOhneKontextHilfen {

    private KvnrOhneKontextHilfen() {
    }

    /** Quelle der Funde der kuenstlichen Erkenner (wie die des Produkts in T-10). */
    public static final String QUELLE = "kvnr-ohne-kontext";

    /** Konfidenz der Funde der kuenstlichen Erkenner. */
    public static final double KONFIDENZ = 0.60;

    /** Negativkontext D-4 (eigene Liste, ASCII, klein; Vergleich mit Faltung A-Z). */
    public static final List<String> NEGATIVKONTEXT = List.of("artikelnummer", "art.-nr.", "rechnungsnummer",
            "rechnungs-nr.", "vorgangsnummer", "aktenzeichen", "az.", "kennzeichen", "bestellnummer",
            "auftragsnummer", "kundennummer", "seriennummer", "sendungsnummer", "ticket");

    /** Die Woerter aus D-4 in der Schreibweise der Anforderung (fuer Saetze mit Negativkontext). */
    public static final List<String> NEGATIVKONTEXT_SCHREIBWEISE = List.of("Artikelnummer", "Art.-Nr.",
            "Rechnungsnummer", "Rechnungs-Nr.", "Vorgangsnummer", "Aktenzeichen", "Az.", "Kennzeichen",
            "Bestellnummer", "Auftragsnummer", "Kundennummer", "Seriennummer", "Sendungsnummer", "Ticket");

    private static final int NEUN_ZIFFERN = 9;
    private static final int ACHT_ZIFFERN = 8;
    private static final int KANDIDAT_LAENGE = 10;

    // ------------------------------------------------------------------ Referenz und kuenstliche Erkenner

    /**
     * Eigenschaften der Referenz; jede kann fuer einen Selbsttest abgeschaltet werden.
     *
     * @param pruefziffer   Pruefziffer muss nach A-1 stimmen
     * @param negativkontext Wort aus D-4 unmittelbar davor verhindert den Fund
     * @param wortgrenzen   Wortgrenzen vorn und hinten (AK-13) werden verlangt
     * @param klein         auch ein Kleinbuchstabe {@code a-z} am Anfang ist ein Kandidat (sonst nur {@code A-Z})
     * @param asciiGrenze   die Wortgrenzen kennen nur ASCII-Zeichen (kaputt: Umlaut, Kombinationszeichen sperren nicht)
     * @param unicodeGross  jeder Grossbuchstabe nach {@link Character#isUpperCase(char)} ist ein Anfang (gegen A-3)
     * @param einfacherLeerraum der Negativkontext ueberspringt nur Leerzeichen, {@code :} und {@code =} (kaputt: Tab,
     *                      Zeilenumbruch und geschuetztes Leerzeichen werden nicht uebersprungen)
     * @param unicodeZiffern die neun Ziffern werden per {@link Character#isDigit(char)} erkannt und mit
     *                      {@link Character#digit(char, int)} gewertet (kaputt: arabisch-indische, Vollbreite-Ziffern
     *                      u. a. zaehlen als Ziffern; verstoesst gegen Kap. 4.3 Schritt 1)
     */
    public record Optionen(boolean pruefziffer, boolean negativkontext, boolean wortgrenzen, boolean klein,
            boolean asciiGrenze, boolean unicodeGross, boolean einfacherLeerraum, boolean unicodeZiffern) {

        /** Die Regel 1b, wie in Kap. 4.3 beschrieben. */
        public static final Optionen REFERENZ =
                new Optionen(true, true, true, false, false, false, false, false);

        /** Ohne Pruefung der Pruefziffer. */
        public Optionen ohnePruefziffer() {
            return new Optionen(false, negativkontext, wortgrenzen, klein, asciiGrenze, unicodeGross,
                    einfacherLeerraum, unicodeZiffern);
        }

        /** Ohne Negativkontext. */
        public Optionen ohneNegativkontext() {
            return new Optionen(pruefziffer, false, wortgrenzen, klein, asciiGrenze, unicodeGross,
                    einfacherLeerraum, unicodeZiffern);
        }

        /** Ohne Wortgrenzen. */
        public Optionen ohneWortgrenzen() {
            return new Optionen(pruefziffer, negativkontext, false, klein, asciiGrenze, unicodeGross,
                    einfacherLeerraum, unicodeZiffern);
        }

        /** Mit Kleinbuchstaben. */
        public Optionen mitKleinbuchstaben() {
            return new Optionen(pruefziffer, negativkontext, wortgrenzen, true, asciiGrenze, unicodeGross,
                    einfacherLeerraum, unicodeZiffern);
        }

        /** Mit reiner ASCII-Wortgrenze. */
        public Optionen mitAsciiGrenze() {
            return new Optionen(pruefziffer, negativkontext, wortgrenzen, klein, true, unicodeGross,
                    einfacherLeerraum, unicodeZiffern);
        }

        /** Mit jedem Grossbuchstaben als Anfang. */
        public Optionen mitUnicodeGross() {
            return new Optionen(pruefziffer, negativkontext, wortgrenzen, klein, asciiGrenze, true,
                    einfacherLeerraum, unicodeZiffern);
        }

        /** Mit Negativkontext, der nur Leerzeichen, Doppelpunkt und Gleichheitszeichen ueberspringt. */
        public Optionen mitEinfachemLeerraum() {
            return new Optionen(pruefziffer, negativkontext, wortgrenzen, klein, asciiGrenze, unicodeGross, true,
                    unicodeZiffern);
        }

        /** Mit Ziffern per {@code Character.isDigit} und {@code Character.digit} (auch Nicht-ASCII-Ziffern). */
        public Optionen mitUnicodeZiffern() {
            return new Optionen(pruefziffer, negativkontext, wortgrenzen, klein, asciiGrenze, unicodeGross,
                    einfacherLeerraum, true);
        }
    }

    /** Die Referenz der Regel 1b: die zehn Zeichen jedes Kandidaten, aufsteigend. */
    public static List<Bereich> kvnrOhneKontextReferenz(String text) {
        return kvnrOhneKontextReferenz(text, Optionen.REFERENZ);
    }

    /** Wie {@link #kvnrOhneKontextReferenz(String)} mit den gewaehlten Eigenschaften. */
    public static List<Bereich> kvnrOhneKontextReferenz(String text, Optionen optionen) {
        List<Bereich> treffer = new ArrayList<>();
        for (int i = 0; i + KANDIDAT_LAENGE <= text.length(); i++) {
            if (istKandidat(text, i, optionen)) {
                treffer.add(new Bereich(i, i + KANDIDAT_LAENGE));
            }
        }
        return treffer;
    }

    private static boolean istKandidat(String text, int i, Optionen o) {
        char anfang = text.charAt(i);
        boolean gross = o.unicodeGross() ? Character.isUpperCase(anfang) : anfang >= 'A' && anfang <= 'Z';
        boolean klein = anfang >= 'a' && anfang <= 'z';
        if (!(gross || (o.klein() && klein)) || !neunZiffern(text, i + 1, o.unicodeZiffern())) {
            return false;
        }
        if (o.wortgrenzen() && !(wortgrenzeVorn(text, i, o) && wortgrenzeHinten(text, i + KANDIDAT_LAENGE, o))) {
            return false;
        }
        if (o.pruefziffer() && !pruefzifferStimmt(text, i, o.unicodeZiffern())) {
            return false;
        }
        return !(o.negativkontext() && negativkontextDavor(text, i, o));
    }

    /** Neun Ziffern ab {@code von}: nur ASCII, oder mit {@code unicode} jede Ziffer nach Character.isDigit. */
    private static boolean neunZiffern(String text, int von, boolean unicode) {
        for (int k = 0; k < NEUN_ZIFFERN; k++) {
            if (von + k >= text.length() || !ziffer(text.charAt(von + k), unicode)) {
                return false;
            }
        }
        return true;
    }

    private static boolean ziffer(char c, boolean unicode) {
        return unicode ? Character.isDigit(c) : asciiZiffer(c);
    }

    private static boolean asciiZiffer(char c) {
        return c >= '0' && c <= '9';
    }

    /** Wert einer Ziffer: ASCII, oder mit {@code unicode} nach Character.digit. */
    private static int ziffernWert(char c, boolean unicode) {
        return unicode ? Character.digit(c, 10) : c - '0';
    }

    private static boolean pruefzifferStimmt(String text, int i, boolean unicode) {
        char gross = Character.toUpperCase(text.charAt(i));
        char buchstabe = gross >= 'A' && gross <= 'Z' ? gross : 'A';
        StringBuilder acht = new StringBuilder();
        for (int k = i + 1; k < i + 1 + ACHT_ZIFFERN; k++) {
            acht.append((char) ('0' + ziffernWert(text.charAt(k), unicode)));
        }
        int pruef = ziffernWert(text.charAt(i + KANDIDAT_LAENGE - 1), unicode);
        return KvnrBsnrHilfen.kvnrPruefziffer(buchstabe, acht.toString()) == pruef;
    }

    /** Buchstabe, Kombinationszeichen, Ziffer oder eines von {@code _ - / @} und der Rueckwaertsstrich. */
    private static boolean sperrzeichen(int cp, Optionen o) {
        if (o.asciiGrenze()) {
            return asciiSperrzeichen(cp);
        }
        int art = Character.getType(cp);
        boolean kombination = art == Character.NON_SPACING_MARK || art == Character.COMBINING_SPACING_MARK
                || art == Character.ENCLOSING_MARK;
        return Character.isLetter(cp) || kombination || Character.isDigit(cp) || cp == '_' || cp == '-'
                || cp == '/' || cp == '\\' || cp == '@';
    }

    /** Wie {@link #sperrzeichen}, aber nur mit ASCII-Zeichen (kaputte Grenze der Selbsttests). */
    private static boolean asciiSperrzeichen(int cp) {
        return cp < 128 && (Character.isLetterOrDigit(cp) || cp == '_' || cp == '-' || cp == '/' || cp == '\\'
                || cp == '@');
    }

    private static boolean wortgrenzeVorn(String text, int i, Optionen o) {
        return i == 0 || !sperrzeichen(Character.codePointBefore(text, i), o);
    }

    private static boolean wortgrenzeHinten(String text, int ende, Optionen o) {
        if (ende >= text.length()) {
            return true;
        }
        char c = text.charAt(ende);
        if (sperrzeichen(Character.codePointAt(text, ende), o)) {
            return false;
        }
        boolean satzzeichen = c == '.' || c == ',';
        return !(satzzeichen && ende + 1 < text.length() && asciiZiffer(text.charAt(ende + 1)));
    }

    /** Ob unmittelbar vor Position {@code i} (nur Leerraum, {@code :} und {@code =} dazwischen) ein D-4-Wort steht. */
    private static boolean negativkontextDavor(String text, int i, Optionen o) {
        int ende = i;
        while (ende > 0 && istZwischenzeichen(text.charAt(ende - 1), o.einfacherLeerraum())) {
            ende--;
        }
        for (String wort : NEGATIVKONTEXT) {
            int start = ende - wort.length();
            if (start >= 0 && endetMitWort(text, start, wort)
                    && (start == 0 || !Character.isLetterOrDigit(Character.codePointBefore(text, start)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean istZwischenzeichen(char c, boolean einfach) {
        if (einfach) {
            return c == ' ' || c == ':' || c == '=';
        }
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == ':' || c == '=';
    }

    /** Vergleich ab {@code start} mit Faltung der Grossbuchstaben {@code A-Z} auf klein, sonst zeichengleich. */
    private static boolean endetMitWort(String text, int start, String wort) {
        for (int k = 0; k < wort.length(); k++) {
            char c = text.charAt(start + k);
            char gefaltet = c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c;
            if (gefaltet != wort.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    /** Ein Erkenner, der die Referenz mit den gewaehlten Eigenschaften als Funde KVNR (0,60) liefert. */
    public static Recognizer kuenstlicherErkenner(Optionen optionen) {
        return kuenstlicherErkenner(optionen, QUELLE, KONFIDENZ);
    }

    /** Wie {@link #kuenstlicherErkenner(Optionen)}, aber mit anderer Quelle und Konfidenz (Selbsttest der Ausnahme). */
    public static Recognizer kuenstlicherErkenner(Optionen optionen, String quelle, double konfidenz) {
        return text -> {
            List<Finding> funde = new ArrayList<>();
            for (Bereich b : kvnrOhneKontextReferenz(text, optionen)) {
                funde.add(new Finding(FindingType.KVNR, text.substring(b.start(), b.ende()), b.start(), b.ende(),
                        konfidenz, quelle));
            }
            return funde;
        };
    }

    private static final Pattern BUCHSTABE_MIT_ACHT_ODER_NEUN_ZIFFERN =
            Pattern.compile("(?<![A-Za-z0-9])([A-Z])([0-9]{8,9})(?![A-Za-z0-9])");

    private static final Pattern BUCHSTABE_MIT_NEUN_ZIFFERN_UND_REST =
            Pattern.compile("(?<![A-Za-z0-9])([A-Z])([0-9]{9})[0-9]*");

    private static Finding kvnrFund(String text, Matcher m) {
        return new Finding(FindingType.KVNR, text.substring(m.start(), m.end()), m.start(), m.end(), KONFIDENZ,
                QUELLE);
    }

    /**
     * Kuenstlicher Erkenner "acht bis neun Ziffern mit Pruefziffer": Buchstabe und acht oder neun Ziffern mit
     * Wortgrenzen; die letzte Ziffer muss die Pruefziffer der davor stehenden Ziffern sein, bei nur sieben davor
     * aufgefuellt mit einer Null vorn ({@code nullVorn}) oder hinten. Selbsttest fuer die Gruppe "acht Ziffern".
     */
    public static Recognizer kuenstlicherAchtBisNeunZiffernErkenner(boolean nullVorn) {
        return text -> {
            List<Finding> funde = new ArrayList<>();
            Matcher m = BUCHSTABE_MIT_ACHT_ODER_NEUN_ZIFFERN.matcher(text);
            while (m.find()) {
                String ziffern = m.group(2);
                String davor = ziffern.substring(0, ziffern.length() - 1);
                String acht = davor.length() == ACHT_ZIFFERN ? davor : (nullVorn ? "0" + davor : davor + "0");
                int pruef = KvnrBsnrHilfen.kvnrPruefziffer(m.group(1).charAt(0), acht);
                if (pruef == ziffern.charAt(ziffern.length() - 1) - '0') {
                    funde.add(kvnrFund(text, m));
                }
            }
            return funde;
        };
    }

    /**
     * Kuenstlicher Erkenner "neun Ziffern und beliebige weitere": Buchstabe, neun Ziffern mit gueltiger Pruefziffer
     * und danach beliebig viele Ziffern (keine Wortgrenze hinten). Selbsttest fuer die Gruppe "zehn Ziffern".
     */
    public static Recognizer kuenstlicherNeunZiffernUndBeliebigeErkenner() {
        return text -> {
            List<Finding> funde = new ArrayList<>();
            Matcher m = BUCHSTABE_MIT_NEUN_ZIFFERN_UND_REST.matcher(text);
            while (m.find()) {
                String ziffern = m.group(2);
                int pruef = KvnrBsnrHilfen.kvnrPruefziffer(m.group(1).charAt(0), ziffern.substring(0, ACHT_ZIFFERN));
                if (pruef == ziffern.charAt(ACHT_ZIFFERN) - '0') {
                    funde.add(kvnrFund(text, m));
                }
            }
            return funde;
        };
    }

    // ------------------------------------------------------------------ AK-16 (b): Fremdmuster

    /** Gruppe: Saetze mit Nummernarten (ungueltige Pruefziffer, Wort nicht unmittelbar davor). */
    public static final String G_NUMMERNART = "nummernart";
    /** Gruppe: je Wort aus D-4 eine gueltige Fremdnummer unmittelbar dahinter. */
    public static final String G_NEGATIVKONTEXT = "negativkontext";
    /** Gruppe: ungueltige Pruefziffer in neutralen Saetzen. */
    public static final String G_PRUEFZIFFER = "pruefziffer";
    /** Gruppe: gueltige Fremdnummer mit verletzter Wortgrenze (AK-13). */
    public static final String G_WORTGRENZE = "wortgrenze";
    /** Gruppe: gueltige Fremdnummer mit Kleinbuchstaben. */
    public static final String G_KLEIN = "klein";
    /** Gruppe: gueltige Fremdnummer mit Trenner. */
    public static final String G_TRENNER = "trenner";
    /** Gruppe: gueltige Fremdnummer mit zwei Buchstaben. */
    public static final String G_ZWEI_BUCHSTABEN = "zweiBuchstaben";
    /** Gruppe: gueltige Fremdnummer mit Nicht-ASCII-Zeichen (Buchstabe, Kombinationszeichen) direkt davor/dahinter. */
    public static final String G_WORTGRENZE_UNICODE = "wortgrenzeUnicode";
    /** Gruppe: Wort aus D-4 und gueltige Fremdnummer, dazwischen Tab, Zeilenumbruch oder geschuetztes Leerzeichen. */
    public static final String G_NEGATIV_LEERRAUM = "negativLeerraum";
    /** Gruppe: Nicht-ASCII-Grossbuchstabe am Anfang der Form (A-3), sonst gueltig. */
    public static final String G_NICHT_ASCII_ANFANG = "nichtAsciiAnfang";
    /** Gruppe: gueltige Fremdnummer, bei der genau eine der neun Ziffern eine wertgleiche Nicht-ASCII-Ziffer ist. */
    public static final String G_NICHT_ASCII_ZIFFER = "nichtAsciiZiffer";
    /** Gruppe: Buchstabe und acht Ziffern. */
    public static final String G_ACHT_ZIFFERN = "achtZiffern";
    /** Gruppe: Buchstabe und zehn Ziffern. */
    public static final String G_ZEHN_ZIFFERN = "zehnZiffern";

    /**
     * Ein Fremdmuster: Gruppe, Beschreibung (ohne Text und Ziffern) und der Satz. Der Satz enthaelt eine
     * Fremdnummer, die zur Laufzeit gebildet wurde.
     */
    public record Fremdmuster(String gruppe, String beschreibung, String text) {
    }

    /** Fester Seed der Fremdmuster (acht Dezimalstellen). */
    public static final long SEED_FREMDMUSTER = 20261127L;

    /** Zeichen aus AK-13, die direkt vor oder hinter der Nummer stehen duerfen nicht. */
    private static final char[] WORTGRENZE_ZEICHEN = {'x', '7', '_', '-', '/', '\\', '@'};

    private static final String[] WORTGRENZE_NAMEN = {"Buchstabe", "Ziffer", "Unterstrich", "Bindestrich",
        "Schraegstrich", "Rueckwaertsstrich", "At-Zeichen"};

    private static final String[] NUMMERNARTEN = {"Artikelnummer", "Rechnungsnummer", "Vorgangsnummer",
        "Kennzeichen", "Seriennummer", "Sendungsnummer", "Kundennummer", "Bestellnummer", "Auftragsnummer",
        "Ticketnummer"};

    private static final String[] TRENNER_ZEICHEN = {" ", "-", "/", "."};

    private static final String[] TRENNER_NAMEN = {"Leerzeichen", "Bindestrich", "Schraegstrich", "Punkt"};

    /** Der feste Satz der Fremdmuster (AK-16 b), deterministisch gebildet. */
    public static final List<Fremdmuster> FREMDMUSTER = fremdmusterBauen();

    /** Setzt den ersten Buchstaben einer Nummer klein (Kleinbuchstabe-Form). */
    public static String klein(String nummer) {
        return nummer.substring(0, 1).toLowerCase(Locale.ROOT) + nummer.substring(1);
    }

    private static List<Fremdmuster> fremdmusterBauen() {
        Random r = new Random(SEED_FREMDMUSTER);
        List<Fremdmuster> f = new ArrayList<>();
        for (String art : NUMMERNARTEN) {
            f.add(new Fremdmuster(G_NUMMERNART, "Nummernart " + art + ", falsche Pruefziffer",
                    "Bericht zur " + art + ": Eintrag " + SynthetischeKvNummern.fremdKvnr(r, false)
                    + " ohne weitere Angabe."));
        }
        String[] trenner = {" ", ": ", " = "};
        String[] trennerName = {"Leerzeichen", "Doppelpunkt", "Gleichheitszeichen"};
        for (String wort : NEGATIVKONTEXT_SCHREIBWEISE) {
            for (int t = 0; t < trenner.length; t++) {
                f.add(negativ(r, wort, trenner[t], "Wort " + wort + ", Trenner " + trennerName[t]));
            }
            f.add(negativ(r, wort.toUpperCase(Locale.ROOT), " ", "Wort " + wort + ", gross"));
            f.add(negativ(r, wort.toLowerCase(Locale.ROOT), " ", "Wort " + wort + ", klein"));
        }
        String[] neutral = {"Die Messreihe wurde unter der Kennung %s abgelegt.",
            "Im Lager liegt die Palette %s bereit.", "Zum Wetterbericht gehoert die Folge %s im Anhang.",
            "Der Karton mit der Marke %s steht im Regal.",
            "Laut Zeitplan wird %s morgen geliefert.", "Notiert wurde %s, sonst nichts."};
        for (int k = 0; k < neutral.length; k++) {
            f.add(new Fremdmuster(G_PRUEFZIFFER, "Neutraler Satz " + (k + 1) + ", falsche Pruefziffer",
                    neutral[k].replace("%s", SynthetischeKvNummern.fremdKvnr(r, false))));
        }
        for (int k = 0; k < WORTGRENZE_ZEICHEN.length; k++) {
            f.add(new Fremdmuster(G_WORTGRENZE, "Wortgrenze verletzt: " + WORTGRENZE_NAMEN[k] + " davor",
                    "Wert " + WORTGRENZE_ZEICHEN[k] + SynthetischeKvNummern.fremdKvnr(r, true) + " im Lager."));
            String hinter = WORTGRENZE_ZEICHEN[k] == '@' ? "@example.invalid"
                    : String.valueOf(WORTGRENZE_ZEICHEN[k]);
            f.add(new Fremdmuster(G_WORTGRENZE, "Wortgrenze verletzt: " + WORTGRENZE_NAMEN[k] + " dahinter",
                    "Wert " + SynthetischeKvNummern.fremdKvnr(r, true) + hinter + " im Lager."));
        }
        f.add(new Fremdmuster(G_WORTGRENZE, "Wortgrenze verletzt: Punkt und Ziffer dahinter",
                "Wert " + SynthetischeKvNummern.fremdKvnr(r, true) + ".5 im Lager."));
        f.add(new Fremdmuster(G_WORTGRENZE, "Wortgrenze verletzt: Komma und Ziffer dahinter",
                "Wert " + SynthetischeKvNummern.fremdKvnr(r, true) + ",5 im Lager."));
        for (int k = 0; k < 3; k++) {
            f.add(new Fremdmuster(G_KLEIN, "Kleinbuchstabe " + (k + 1),
                    "Notiz: " + klein(SynthetischeKvNummern.fremdKvnr(r, true)) + " wurde vermerkt."));
        }
        for (int k = 0; k < TRENNER_ZEICHEN.length; k++) {
            String n = SynthetischeKvNummern.fremdKvnr(r, true);
            f.add(new Fremdmuster(G_TRENNER, "Trenner " + TRENNER_NAMEN[k] + " hinter dem Buchstaben",
                    "Notiz: " + n.substring(0, 1) + TRENNER_ZEICHEN[k] + n.substring(1) + " wurde vermerkt."));
            String m = SynthetischeKvNummern.fremdKvnr(r, true);
            f.add(new Fremdmuster(G_TRENNER, "Trenner " + TRENNER_NAMEN[k] + " mitten in den Ziffern",
                    "Notiz: " + m.substring(0, 5) + TRENNER_ZEICHEN[k] + m.substring(5) + " wurde vermerkt."));
        }
        f.add(new Fremdmuster(G_ZWEI_BUCHSTABEN, "Zwei Buchstaben, der erste A",
                "Notiz: A" + SynthetischeKvNummern.fremdKvnr(r, true) + " wurde vermerkt."));
        f.add(new Fremdmuster(G_ZWEI_BUCHSTABEN, "Zwei Buchstaben, QX",
                "Notiz: QX" + SynthetischeKvNummern.fremdKvnr(r, true) + " wurde vermerkt."));
        for (int k = 0; k < 2; k++) {
            String n = SynthetischeKvNummern.fremdKvnr(r, true);
            f.add(new Fremdmuster(G_ACHT_ZIFFERN, "Buchstabe und acht Ziffern " + (k + 1),
                    "Notiz: " + n.substring(0, n.length() - 1) + " wurde vermerkt."));
            String m = SynthetischeKvNummern.fremdKvnr(r, true);
            f.add(new Fremdmuster(G_ZEHN_ZIFFERN, "Buchstabe und zehn Ziffern " + (k + 1),
                    "Notiz: " + m + (k == 0 ? "4" : "0") + " wurde vermerkt."));
        }
        achtZiffernErgaenzen(r, f);
        zehnZiffernErgaenzen(r, f);
        wortgrenzeUnicodeErgaenzen(r, f);
        negativLeerraumErgaenzen(r, f);
        nichtAsciiAnfangErgaenzen(r, f);
        nichtAsciiZifferErgaenzen(r, f);
        return List.copyOf(f);
    }

    /**
     * Weitere Achterformen, die nach dem Verfahren "gueltig aussehen": die achte Ziffer ist die Pruefziffer der sieben
     * davor, aufgefuellt mit einer Null vorn bzw. hinten, und eine gueltige Nummer ohne ihre erste Ziffer.
     */
    private static void achtZiffernErgaenzen(Random r, List<Fremdmuster> f) {
        for (int k = 0; k < 3; k++) {
            String n = SynthetischeKvNummern.fremdKvnr(r, true);
            String sieben = n.substring(1, 8);
            int vorn = KvnrBsnrHilfen.kvnrPruefziffer(n.charAt(0), "0" + sieben);
            f.add(new Fremdmuster(G_ACHT_ZIFFERN, "Acht Ziffern, achte Ziffer passt zu vorangestellter Null " + (k + 1),
                    "Notiz: " + n.charAt(0) + sieben + vorn + " wurde vermerkt."));
            String m = SynthetischeKvNummern.fremdKvnr(r, true);
            String sieben2 = m.substring(1, 8);
            int hinten = KvnrBsnrHilfen.kvnrPruefziffer(m.charAt(0), sieben2 + "0");
            f.add(new Fremdmuster(G_ACHT_ZIFFERN, "Acht Ziffern, achte Ziffer passt zu angehaengter Null " + (k + 1),
                    "Notiz: " + m.charAt(0) + sieben2 + hinten + " wurde vermerkt."));
        }
        for (int k = 0; k < 2; k++) {
            String n = SynthetischeKvNummern.fremdKvnr(r, true);
            f.add(new Fremdmuster(G_ACHT_ZIFFERN, "Gueltige Nummer ohne die erste Ziffer " + (k + 1),
                    "Notiz: " + n.charAt(0) + n.substring(2) + " wurde vermerkt."));
        }
    }

    /** Weitere Zehnerformen: neunte Ziffer passt zu den ersten Stellen, die zehnte Ziffer ist beliebig. */
    private static void zehnZiffernErgaenzen(Random r, List<Fremdmuster> f) {
        for (int k = 0; k < 4; k++) {
            String m = SynthetischeKvNummern.fremdKvnr(r, true);
            f.add(new Fremdmuster(G_ZEHN_ZIFFERN, "Gueltige Nummer mit beliebiger zehnter Ziffer " + (k + 1),
                    "Notiz: " + m + r.nextInt(10) + " wurde vermerkt."));
        }
    }

    /** Nicht-ASCII-Zeichen aus AK-13, zur Laufzeit erzeugt: Umlaut, Alpha, Zeichen ausserhalb der BMP, Akzent. */
    private static void wortgrenzeUnicodeErgaenzen(Random r, List<Fremdmuster> f) {
        int[] zeichenCodes = {0xE4, 0x3B1, 0x1D400, 0x301};
        String[] namen = {"Umlaut", "griechischer Buchstabe", "Buchstabe ausserhalb der BMP", "Kombinationszeichen"};
        for (int k = 0; k < zeichenCodes.length; k++) {
            String zeichen = new String(Character.toChars(zeichenCodes[k]));
            f.add(new Fremdmuster(G_WORTGRENZE_UNICODE, "Wortgrenze verletzt: " + namen[k] + " davor",
                    "Wert " + zeichen + SynthetischeKvNummern.fremdKvnr(r, true) + " im Lager."));
            f.add(new Fremdmuster(G_WORTGRENZE_UNICODE, "Wortgrenze verletzt: " + namen[k] + " dahinter",
                    "Wert " + SynthetischeKvNummern.fremdKvnr(r, true) + zeichen + " im Lager."));
        }
    }

    /** Leerraum zwischen Wort und Nummer, zur Laufzeit erzeugt: Tab, Zeilenumbruch, geschuetztes Leerzeichen. */
    private static void negativLeerraumErgaenzen(Random r, List<Fremdmuster> f) {
        String[] woerter = {"Ticket", "Aktenzeichen", "Rechnungsnummer", "Kennzeichen"};
        String[] trenner = {String.valueOf((char) 0x09), String.valueOf((char) 0x0A), String.valueOf((char) 0xA0)};
        String[] namen = {"Tab", "Zeilenumbruch", "geschuetztes Leerzeichen"};
        for (String wort : woerter) {
            for (int t = 0; t < trenner.length; t++) {
                f.add(negativ(G_NEGATIV_LEERRAUM, r, wort, trenner[t], "Wort " + wort + ", Leerraum " + namen[t]));
            }
        }
    }

    /** Grossbuchstaben ausserhalb von A-Z am Anfang: Ae, Oe, griechisches Alpha, kyrillisches Zhe. */
    private static void nichtAsciiAnfangErgaenzen(Random r, List<Fremdmuster> f) {
        int[] anfaenge = {0xC4, 0xD6, 0x391, 0x416};
        for (int k = 0; k < anfaenge.length; k++) {
            String n = SynthetischeKvNummern.fremdKvnr(r, true);
            String acht = n.substring(1, 9);
            int pruef = KvnrBsnrHilfen.kvnrPruefziffer('A', acht);
            f.add(new Fremdmuster(G_NICHT_ASCII_ANFANG, "Nicht-ASCII-Grossbuchstabe am Anfang " + (k + 1),
                    "Notiz: " + new String(Character.toChars(anfaenge[k])) + acht + pruef + " wurde vermerkt."));
        }
    }

    /**
     * Gueltige Fremdnummer, bei der genau eine der neun Ziffern (Stelle 5 bis 9 im Text, 9 ist die Pruefziffer) zur
     * Laufzeit durch die wertgleiche Nicht-ASCII-Ziffer ersetzt ist: arabisch-indisch (ab 0x0660) oder Vollbreite
     * (ab 0xFF10). Stellen 1 bis 4 bleiben unberuehrt (Waechter).
     */
    private static void nichtAsciiZifferErgaenzen(Random r, List<Fremdmuster> f) {
        int[] basis = {0x0660, 0xFF10, 0x0660, 0xFF10};
        int[] stelle = {5, 7, 9, 9};
        String[] namen = {"arabisch-indische Ziffer an Stelle 5", "Vollbreite-Ziffer an Stelle 7",
            "arabisch-indische Pruefziffer", "Vollbreite-Pruefziffer"};
        for (int k = 0; k < basis.length; k++) {
            String n = SynthetischeKvNummern.fremdKvnr(r, true);
            int wert = n.charAt(stelle[k]) - '0';
            String ersatz = new String(Character.toChars(basis[k] + wert));
            f.add(new Fremdmuster(G_NICHT_ASCII_ZIFFER, "Nicht-ASCII-Ziffer: " + namen[k],
                    "Notiz: " + n.substring(0, stelle[k]) + ersatz + n.substring(stelle[k] + 1)
                    + " wurde vermerkt."));
        }
    }

    private static Fremdmuster negativ(Random r, String wort, String trenner, String beschreibung) {
        return negativ(G_NEGATIVKONTEXT, r, wort, trenner, beschreibung);
    }

    private static Fremdmuster negativ(String gruppe, Random r, String wort, String trenner, String beschreibung) {
        return new Fremdmuster(gruppe, beschreibung,
                "Siehe " + wort + trenner + SynthetischeKvNummern.fremdKvnr(r, true) + " im System.");
    }

    // ------------------------------------------------------------------ AK-16 (c): zufaellige Fremdnummern

    /** Neutrale Rauschwoerter ohne KVNR-Kontextwort und ohne Wort aus D-4 (ASCII, ohne Ziffern). */
    private static final String[] RAUSCHWOERTER = {"Lager", "Regal", "Lieferung", "Muster", "bereit", "morgen",
        "Fenster", "Tisch", "gruen", "Karton", "Palette", "Halle", "offen", "kurz", "Stunde", "Wetter", "heute",
        "Zeitplan", "Ordner", "Schrank", "Probe", "leicht", "Wagen", "Hof", "Garten", "Bank", "Kiste", "Messung",
        "Notiz", "Tafel", "und", "der", "die", "das", "im", "am", "zum", "wurde", "steht", "liegt"};

    /** Kleinste und groesste Zahl der Rauschwoerter je Satz. */
    private static final int MIN_WOERTER = 5;
    private static final int WOERTER_SPANNE = 8;

    /**
     * Bildet {@code anzahl} Saetze aus je 5 bis 12 Rauschwoertern, zwischen denen (oder davor oder dahinter) genau eine
     * {@link SynthetischeKvNummern#zufallsKvnr} steht, getrennt durch Leerzeichen, Satzende mit Punkt. Ein Satz, in
     * dem die Referenz {@link KvnrBsnrHilfen#kvnrKontextReferenz} ein KVNR-Kontextwort findet (zufaellig aus Buchstaben
     * des Satzes gebildet), wird verworfen und neu gebildet. Deterministisch fuer den Seed.
     */
    public static List<String> zufallsFremdnummernTexte(long seed, int anzahl) {
        Random r = new Random(seed);
        List<String> texte = new ArrayList<>(anzahl);
        while (texte.size() < anzahl) {
            int woerter = MIN_WOERTER + r.nextInt(WOERTER_SPANNE);
            int stelle = r.nextInt(woerter + 1);
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k <= woerter; k++) {
                if (k > 0) {
                    sb.append(' ');
                }
                if (k == stelle) {
                    sb.append(SynthetischeKvNummern.zufallsKvnr(r));
                } else {
                    sb.append(RAUSCHWOERTER[r.nextInt(RAUSCHWOERTER.length)]);
                }
            }
            sb.append('.');
            String text = sb.toString();
            if (!KvnrBsnrHilfen.kvnrKontextReferenz(text)) {
                texte.add(text);
            }
        }
        return texte;
    }
}
