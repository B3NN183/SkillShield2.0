package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG_DREI;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.IK_UNGUELTIG_KOMPLEMENT;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG_FACHGRUPPE;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.SynthetischeKvNummern.bildeIk;
import static de.skillshield.core.SynthetischeKvNummern.bildeLanr;
import static de.skillshield.core.SynthetischeKvNummern.ikGueltigReferenz;
import static de.skillshield.core.SynthetischeKvNummern.lanrGueltigReferenz;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Gemeinsame Testhilfen der KV-Erkennungstests ({@code KvKontextTest}, {@code KvErkennungEigenschaftTest},
 * {@code KvNachtragTest}): Konstanten, die Konfiguration je Nummernart ({@link Art}) und der
 * Waechter der Testdatenregel (T-1 Punkt 10).
 *
 * <p><b>Differenzpruefung.</b> {@link #scan} vergleicht jedes Ergebnis zusaetzlich mit dem eingefrorenen Vorgaenger
 * ({@link Art#vorgaenger}); {@link #verletztBestandsschutz} ist die Pruefregel (Bestandsschutz je Fund, mit der
 * Ausnahme 14g). Zusaetzlich vergleicht {@link #scan} mit dem Stand am Ende von kv-kontext-luecken
 * ({@link Art#standLuecken}); dort gilt {@link #verletztBestandsschutzOhneAusnahme} streng und ohne jede Ausnahme.
 *
 * <p><b>Regel aus T-1 (Punkt 10).</b> Kein Test bildet zur Laufzeit eine neunstellige Nummer, die nach LANR- oder
 * IK-Verfahren gueltig ist und nicht mit {@code 00} beginnt. {@link #assertKeinGueltigesNeunerfensterOhne00} liest
 * unabhaengig vom Produkt alle Ziffernketten und prueft jedes aus ganzen Bloecken gebildete Fenster mit genau neun
 * Ziffern, das also ein Segment des Scanners sein koennte. Die Meldung nennt nie Ziffern.
 */
public final class KvTestHilfen {

    private KvTestHilfen() {
    }

    static final double WAHRSCHEINLICH = 0.80;
    static final double PZ_UNGUELTIG = 0.50;
    static final double LAENGE = 0.40;

    static final char NBSP = (char) 0xA0;
    public static final char SHY = (char) 0xAD;
    public static final char ZWSP = (char) 0x200B;
    static final char ZWNJ = (char) 0x200C;
    static final char ZWJ = (char) 0x200D;
    static final char WORD_JOINER = (char) 0x2060;
    public static final char BOM = (char) 0xFEFF;
    static final char VS16 = (char) 0xFE0F;
    static final char AKUT = (char) 0x0301;
    static final char DUENN = (char) 0x2009;
    static final char SCHMAL_NBSP = (char) 0x202F;
    static final char NEL = (char) 0x85;
    public static final char GESCH_BINDESTRICH = (char) 0x2011;
    public static final char HALBGEVIERT = (char) 0x2013;
    static final char LS = (char) 0x2028;
    static final char PS = (char) 0x2029;
    public static final char LRM = (char) 0x200E;
    public static final char EN_SPACE = (char) 0x2002;

    /**
     * Unabhaengig vom Produkt, je Codepunkt: Formatzeichen (Kategorie {@code FORMAT}) und Variantenwaehler
     * U+FE00 bis U+FE0F.
     */
    public static boolean unsichtbar(int codepunkt) {
        return Character.getType(codepunkt) == Character.FORMAT || (codepunkt >= 0xFE00 && codepunkt <= 0xFE0F);
    }

    /**
     * Unabhaengig vom Produkt: Gibt es ein CR, dem ein oder mehr unsichtbare Zeichen (je Codepunkt, auch ausserhalb
     * der BMP) und dann ein LF folgen?
     */
    public static boolean enthaeltCrUnsichtbarLf(String text) {
        for (int i = text.indexOf('\r'); i >= 0; i = text.indexOf('\r', i + 1)) {
            int j = i + 1;
            while (j < text.length() && unsichtbar(text.codePointAt(j))) {
                j += Character.charCount(text.codePointAt(j));
            }
            if (j > i + 1 && j < text.length() && text.charAt(j) == '\n') {
                return true;
            }
        }
        return false;
    }

    /** Kontextwort-Varianten je Art (fuer den Eigenschaftstest). */
    static final String[] KW_LANR = {"LANR", "lanr", "Lanr", "LANR-Nr.", "LANRNR", "Arztnummer", "Arzt-Nr.",
        "Arzt Nummer", "Praxis-LANR", "Lebenslange Arztnummer", "ARZT-NR", "arztnr"};
    static final String[] KW_IK = {"IK", "ik", "Ik", "IK-Nr.", "IKNR", "IK-Nummer", "IKNUMMER",
        "Institutionskennzeichen", "Praxis-IK", "INSTITUTIONSKENNZEICHEN", "iknr"};

    /** Konfiguration je Nummernart. */
    public static final class Art {
        public final String name;
        final boolean lanr;
        public final Recognizer erkenner;
        /** Erkenner des eingefrorenen Standes vor kv-kontext-luecken (Vergleich alt gegen neu). */
        public final Recognizer vorgaenger;
        /** Erkenner des eingefrorenen Standes am Ende von kv-kontext-luecken (strenge Differenz ohne Ausnahme). */
        public final Recognizer standLuecken;
        final FindingType typ;
        final String kw;
        final String kwLang;
        final String gueltig;
        final String gueltig2;
        final String gueltig3;
        public final String ungueltig;
        final double sicher;
        final String quelle;
        final String[] kws;
        final String[] fremdeKws;

        Art(String name, boolean lanr, Recognizer erkenner, Recognizer vorgaenger, Recognizer standLuecken,
                FindingType typ, String kw, String kwLang, String gueltig, String gueltig2, String gueltig3,
                String ungueltig, double sicher, String quelle, String[] kws, String[] fremdeKws) {
            this.name = name;
            this.lanr = lanr;
            this.erkenner = erkenner;
            this.vorgaenger = vorgaenger;
            this.standLuecken = standLuecken;
            this.typ = typ;
            this.kw = kw;
            this.kwLang = kwLang;
            this.gueltig = gueltig;
            this.gueltig2 = gueltig2;
            this.gueltig3 = gueltig3;
            this.ungueltig = ungueltig;
            this.sicher = sicher;
            this.quelle = quelle;
            this.kws = kws;
            this.fremdeKws = fremdeKws;
        }

        boolean gueltig(String ziffern) {
            return lanr ? lanrGueltigReferenz(ziffern) : ikGueltigReferenz(ziffern);
        }

        String bilde(Random r) {
            return lanr ? bildeLanr(r) : bildeIk(r);
        }

        String quellePruefziffer() {
            return quelle + "-pruefziffer-ungueltig";
        }

        String quelleLaenge() {
            return quelle + "-laenge-abweichend";
        }

        String quelleFuer(double konfidenz) {
            if (konfidenz == PZ_UNGUELTIG) {
                return quellePruefziffer();
            }
            if (konfidenz == LAENGE) {
                return quelleLaenge();
            }
            return quelle;
        }

        /**
         * Die Quelle folgt nur der Nummer (Ziffernzahl und Pruefziffer), nie der Konfidenz: neun Ziffern gueltig
         * {@code quelle}, neun ungueltig Pruefziffer-Quelle, sonst Laengen-Quelle. Auch eine gueltige Nummer mit
         * Konfidenz 0,50 hat damit die Quelle {@code quelle}.
         */
        public String quelleNachNummer(String wert) {
            StringBuilder ziffern = new StringBuilder();
            for (int i = 0; i < wert.length(); i++) {
                if (istZiffer(wert.charAt(i))) {
                    ziffern.append(wert.charAt(i));
                }
            }
            if (ziffern.length() != 9) {
                return quelleLaenge();
            }
            return gueltig(ziffern.toString()) ? quelle : quellePruefziffer();
        }
    }

    public static final Art LANR_ART = new Art("LANR", true, new LanrRecognizer(), KvVorgaenger.lanr(),
            KvStandLuecken.lanr(), FindingType.LANR, "LANR", "Arztnummer",
            LANR_GUELTIG, LANR_GUELTIG_ZWEI, LANR_GUELTIG_FACHGRUPPE, LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, 0.95,
            "lanr", KW_LANR, KW_IK);
    public static final Art IK_ART = new Art("IK", false, new IkRecognizer(), KvVorgaenger.ik(),
            KvStandLuecken.ik(), FindingType.IK, "IK", "Institutionskennzeichen",
            IK_GUELTIG, IK_GUELTIG_ZWEI, IK_GUELTIG_DREI, IK_UNGUELTIG_KOMPLEMENT, 0.92, "ik", KW_IK, KW_LANR);
    public static final List<Art> ARTEN = List.of(LANR_ART, IK_ART);

    /** Der Text aus Codepunkten. */
    static String u(int... codepunkte) {
        return new String(codepunkte, 0, codepunkte.length);
    }

    // ------------------------------------------------------------------ Pruefhilfen auf Erkennerebene

    /** Erwarteter Fund: Wert und Konfidenz (Stufe und Quelle folgen daraus). */
    record Erw(String wert, double konfidenz) {
    }

    // ------------------------------------------------------------------ Hilfen

    public static List<Finding> scan(Art a, String text) {
        assertKeinGueltigesNeunerfensterOhne00(text, "Waechter " + a.name);
        List<Finding> funde = a.erkenner.detect(text);
        assertNotNull(funde);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertTrue(text.substring(f.start(), f.end()).equals(f.originalValue()), a.name + " Teilstring-Invariante");
            assertEquals(a.typ, f.type(), a.name + " Typ");
            assertTrue(f.start() >= vorigesEnde, a.name + " Funde ueberlappen oder sind nicht sortiert");
            vorigesEnde = f.end();
        }
        pruefeGegenVorgaenger(a, text, funde);
        pruefeGegenStandLuecken(a, text, funde);
        return funde;
    }

    /**
     * Strenger Bestandsschutz gegen den eingefrorenen Stand am Ende von kv-kontext-luecken: kein Fund fehlt, keine
     * Konfidenz sinkt, die Quelle bleibt gleich, ohne jede Ausnahme ({@link #verletztBestandsschutzOhneAusnahme}).
     * Die Meldung nennt nie Text oder Ziffern.
     */
    static void pruefeGegenStandLuecken(Art a, String text, List<Finding> neu) {
        List<Finding> alt = a.standLuecken.detect(text);
        assertFalse(verletztBestandsschutzOhneAusnahme(alt, neu), a.name + " Differenz Stand Luecken: Fund fehlt, "
                + "hat geringere Konfidenz oder andere Quelle (Textlaenge " + text.length() + ", " + alt.size()
                + " alte Funde)");
    }

    /** Der Fund aus {@code neu} mit gleichem Start, Ende und Typ wie {@code f}, sonst {@code null}. */
    private static Finding findeGleichenFund(Finding f, List<Finding> neu) {
        for (Finding g : neu) {
            if (g.start() == f.start() && g.end() == f.end() && g.type() == f.type()) {
                return g;
            }
        }
        return null;
    }

    /**
     * Die strenge Pruefregel (ohne Assertion, damit sie einen Selbsttest bekommt): Jeder alte Fund hat einen neuen
     * Fund mit gleichem Start, Ende und Typ, gleicher Quelle und einer Konfidenz, die nicht kleiner ist. Keine
     * Ausnahme, auch nicht die Senkung von 0,80 auf 0,50 bei Quelle {@code ik} (Grenze 14g des Vorgaengers).
     */
    public static boolean verletztBestandsschutzOhneAusnahme(List<Finding> alt, List<Finding> neu) {
        for (Finding f : alt) {
            Finding gleich = findeGleichenFund(f, neu);
            if (gleich == null || gleich.confidence() < f.confidence() || !f.source().equals(gleich.source())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Bestandsschutz gegen den eingefrorenen Vorgaenger auf Erkennerebene: Jeder Fund des Vorgaengers kommt im neuen
     * Ergebnis mit gleichem Start, Ende und Typ vor, und die neue Konfidenz ist nicht kleiner, ausser der alte Fund
     * hat die Quelle {@code ik} und eine Konfidenz bis 0,80 und der neue Fund hat die Quelle {@code ik} und genau die
     * Konfidenz 0,50 (Plan Hinweis 7, Grenze 14g). Die Meldung nennt nie Text oder Ziffern.
     */
    static void pruefeGegenVorgaenger(Art a, String text, List<Finding> neu) {
        List<Finding> alt = a.vorgaenger.detect(text);
        assertFalse(verletztBestandsschutz(alt, neu), a.name + " Differenz alt/neu: Fund des Vorgaengers fehlt oder "
                + "hat geringere Konfidenz (Textlaenge " + text.length() + ", " + alt.size() + " alte Funde)");
    }

    /** Die Pruefregel selbst (ohne Assertion), damit sie einen Selbsttest bekommt. */
    public static boolean verletztBestandsschutz(List<Finding> alt, List<Finding> neu) {
        for (Finding f : alt) {
            Finding gleich = findeGleichenFund(f, neu);
            if (gleich == null) {
                return true;
            }
            boolean ausnahme = "ik".equals(f.source()) && f.confidence() <= WAHRSCHEINLICH
                    && "ik".equals(gleich.source()) && gleich.confidence() == PZ_UNGUELTIG;
            if (gleich.confidence() < f.confidence() && !ausnahme) {
                return true;
            }
        }
        return false;
    }

    /** Genau ein Fund {@code wert} direkt hinter {@code vor}; die Quelle folgt der Konfidenz (Bestandsregel). */
    static void assertEin(Art a, String vor, String wert, String nach, double konfidenz) {
        assertEin(a, vor, wert, nach, konfidenz, a.quelleFuer(konfidenz));
    }

    /** Wie oben mit ausdruecklicher Quelle, zum Beispiel {@link Art#quelleNachNummer(String)}. */
    static void assertEin(Art a, String vor, String wert, String nach, double konfidenz, String quelle) {
        List<Finding> funde = scan(a, vor + wert + nach);
        assertEquals(1, funde.size(), a.name + " Anzahl Funde");
        Finding f = funde.get(0);
        assertTrue(wert.equals(f.originalValue()), a.name + " Originalwert");
        assertEquals(vor.length(), f.start(), a.name + " Start");
        assertEquals(vor.length() + wert.length(), f.end(), a.name + " Ende");
        assertEquals(konfidenz, f.confidence(), a.name + " Konfidenz");
        assertEquals(ConfidenceLevel.of(konfidenz), f.level(), a.name + " Stufe");
        assertEquals(quelle, f.source(), a.name + " Quelle");
    }

    static void sicher(Art a, String vor, String wert, String nach) {
        assertEin(a, vor, wert, nach, a.sicher);
    }

    static void wahrscheinlich(Art a, String vor, String wert, String nach) {
        assertEin(a, vor, wert, nach, WAHRSCHEINLICH);
    }

    static void assertKein(Art a, String text) {
        assertEquals(List.of(), scan(a, text), a.name + " erwartet kein Fund");
    }

    /** Die Funde in Reihenfolge; die Position folgt der Suche des Werts ab dem Ende des vorigen Funds. */
    static void assertFolge(Art a, String text, Erw... erwartet) {
        List<Finding> funde = scan(a, text);
        assertEquals(erwartet.length, funde.size(), a.name + " Anzahl Funde");
        int ab = 0;
        for (int i = 0; i < erwartet.length; i++) {
            Finding f = funde.get(i);
            Erw e = erwartet[i];
            int start = text.indexOf(e.wert(), ab);
            assertTrue(start >= 0, a.name + " Wert " + i + " nicht im Text");
            assertTrue(e.wert().equals(f.originalValue()), a.name + " Originalwert " + i);
            assertEquals(start, f.start(), a.name + " Start " + i);
            assertEquals(start + e.wert().length(), f.end(), a.name + " Ende " + i);
            assertEquals(e.konfidenz(), f.confidence(), a.name + " Konfidenz " + i);
            assertEquals(ConfidenceLevel.of(e.konfidenz()), f.level(), a.name + " Stufe " + i);
            assertEquals(a.quelleFuer(e.konfidenz()), f.source(), a.name + " Quelle " + i);
            ab = f.end();
        }
    }

    // ------------------------------------------------------------------ Laufzeithilfen (AK-21, OF-8)

    static int wiederholungen(int mindestlaenge, String praefix, String baustein, String schluss) {
        return (mindestlaenge - praefix.length() - schluss.length() + baustein.length() - 1) / baustein.length();
    }

    static String bauen(String praefix, String baustein, String schluss, int mindestlaenge) {
        return praefix + baustein.repeat(wiederholungen(mindestlaenge, praefix, baustein, schluss)) + schluss;
    }

    /**
     * Aufwaermlauf (nicht gemessen) mit 10 000 Zeichen derselben Bauart, dann Messlauf mit mindestens einer Million
     * Zeichen unter hartem Limit von 2 Sekunden und exakter Fundzahl (Formel im Aufrufer begruendet).
     */
    static void laufzeit(Art a, String praefix, String baustein, String schluss, int erwartet,
            String formel) {
        String warm = bauen(praefix, baustein, schluss, 10_000);
        assertKeinGueltigesNeunerfensterOhne00(warm, "Waechter Laufzeit " + a.name);
        a.erkenner.detect(warm);
        String text = bauen(praefix, baustein, schluss, 1_000_000);
        assertKeinGueltigesNeunerfensterOhne00(text, "Waechter Laufzeit " + a.name);
        int ist = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> a.erkenner.detect(text).size(),
                a.name + " Laufzeitgrenze 2 s ueberschritten: " + formel);
        assertEquals(erwartet, ist, a.name + " " + formel);
    }

    /** Anzahl der Wiederholungen eines Bausteins ohne Praefix und Schluss: aufgerundet 1 000 000 / Bausteinlaenge. */
    static int ceilMillion(String baustein) {
        return (1_000_000 + baustein.length() - 1) / baustein.length();
    }

    // ------------------------------------------------------------------ Waechter der Testdatenregel

    /**
     * Absicherung der Testdatenregel (T-1 Punkt 10) unabhaengig vom Produkt: Jedes Fenster aus ganzen Bloecken einer
     * Ziffernkette mit genau neun Ziffern ist ein moegliches Scanner-Segment; ohne Praefix {@code 00} darf es nach
     * keinem der beiden Referenzverfahren gueltig sein. Die Meldung nennt nie Ziffern.
     */
    static void assertKeinGueltigesNeunerfensterOhne00(String text, String meldung) {
        int[] zaehlung = neunerfenster(text, "00");
        assertEquals(0, zaehlung[1], meldung + ": gueltiges Neunerfenster ohne Praefix 00 (Waechter)");
    }

    /**
     * Zaehlt die Fenster aus ganzen Bloecken einer Ziffernkette mit genau neun Ziffern, deren Ziffern nicht mit
     * {@code ausgenommenPraefix} beginnen ({@code null}: keine Ausnahme; Ergebnis[0]), und davon die nach LANR- oder
     * IK-Referenz gueltigen (Ergebnis[1]).
     */
    public static int[] neunerfenster(String text, String ausgenommenPraefix) {
        int[] ergebnis = new int[2];
        int n = text.length();
        int i = 0;
        while (i < n) {
            if (!istZiffer(text.charAt(i))) {
                i++;
                continue;
            }
            List<int[]> bloecke = new ArrayList<>();
            int p = i;
            while (true) {
                int s = p;
                while (p < n && istZiffer(text.charAt(p))) {
                    p++;
                }
                bloecke.add(new int[] {s, p});
                if (p + 1 < n && istGruppentrenner(text.charAt(p)) && istZiffer(text.charAt(p + 1))) {
                    p++;
                } else {
                    break;
                }
            }
            zaehleFenster(text, bloecke, ausgenommenPraefix, ergebnis);
            i = p;
        }
        return ergebnis;
    }

    static void zaehleFenster(String text, List<int[]> bloecke, String ausgenommenPraefix, int[] ergebnis) {
        for (int s = 0; s < bloecke.size(); s++) {
            int summe = 0;
            StringBuilder ziffern = new StringBuilder();
            for (int e = s; e < bloecke.size() && summe < 9; e++) {
                summe += bloecke.get(e)[1] - bloecke.get(e)[0];
                ziffern.append(text, bloecke.get(e)[0], bloecke.get(e)[1]);
                String neun = ziffern.toString();
                if (summe == 9 && (ausgenommenPraefix == null || !neun.startsWith(ausgenommenPraefix))) {
                    ergebnis[0]++;
                    if (lanrGueltigReferenz(neun) || ikGueltigReferenz(neun)) {
                        ergebnis[1]++;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Referenz fuer Sperrschrift (T-5, Orakel)

    private static final List<String> SPERR_WOERTER_LANR = List.of("lanr", "lanrnr", "arztnummer", "arztnr");
    private static final List<String> SPERR_WOERTER_IK = List.of("ik", "iknr", "iknummer", "institutionskennzeichen");

    /**
     * Unabhaengige Referenz zu den gesperrt geschriebenen Kontextwoertern (D-1, D-2; Plan T-5). Sie nutzt weder den
     * Scanner noch den Vorgaenger und ist bewusst anders gebaut: Zuerst werden alle unsichtbaren Zeichen entfernt
     * (mit Indexabbildung auf den Originaltext), dann werden einfache Folgen "Einzelbuchstabe, genau ein Trenner,
     * Einzelbuchstabe, ..." gesucht. Ein Einzelbuchstabe ist ein Buchstabe, an den links und rechts kein Buchstabe
     * (und kein Kombinationszeichen) grenzt. Zulaessige Trenner: Leerzeichen, NBSP, THIN, NNBSP, Punkt, Bindestrich,
     * U+2011, U+2013. Eine Folge ist ein Kontextwort, wenn sie 2 bis 23 Buchstaben hat und ihre nach {@code A-Z}
     * gefalteten Buchstaben genau einem Wort der Art entsprechen (andere Buchstaben passen nie). Laufzeit egal.
     *
     * @return je Fund {@code {Start, Ende}} im Originaltext, vom ersten Buchstaben bis hinter den letzten
     */
    static List<int[]> sperrKontextwoerterReferenz(String text, Art a) {
        List<String> woerter = a.lanr ? SPERR_WOERTER_LANR : SPERR_WOERTER_IK;
        int[] cp = new int[text.length()];
        int[] ursprung = new int[text.length()];
        int n = 0;
        for (int i = 0; i < text.length();) {
            int c = text.codePointAt(i);
            if (!istUnsichtbarReferenz(c)) {
                cp[n] = c;
                ursprung[n] = i;
                n++;
            }
            i += Character.charCount(c);
        }
        List<int[]> treffer = new ArrayList<>();
        int i = 0;
        while (i < n) {
            if (!istEinzelbuchstabeReferenz(cp, n, i)) {
                i++;
                continue;
            }
            int letzter = i;
            StringBuilder gefaltet = new StringBuilder();
            gefaltet.append(faltungReferenz(cp[i]));
            int anzahl = 1;
            while (letzter + 2 < n && istSperrTrennerReferenz(cp[letzter + 1])
                    && istEinzelbuchstabeReferenz(cp, n, letzter + 2)) {
                letzter += 2;
                anzahl++;
                gefaltet.append(faltungReferenz(cp[letzter]));
            }
            if (anzahl >= 2 && anzahl <= 23 && woerter.contains(gefaltet.toString())) {
                treffer.add(new int[] {ursprung[i], ursprung[letzter] + Character.charCount(cp[letzter])});
            }
            i = letzter + 1;
        }
        return treffer;
    }

    private static boolean istUnsichtbarReferenz(int c) {
        return Character.getType(c) == Character.FORMAT || (c >= 0xFE00 && c <= 0xFE0F);
    }

    private static boolean istBuchstabeOderMarkReferenz(int c) {
        int typ = Character.getType(c);
        return Character.isLetter(c) || typ == Character.NON_SPACING_MARK || typ == Character.ENCLOSING_MARK
                || typ == Character.COMBINING_SPACING_MARK;
    }

    private static boolean istEinzelbuchstabeReferenz(int[] cp, int n, int i) {
        if (!Character.isLetter(cp[i])) {
            return false;
        }
        boolean linksFrei = i == 0 || !istBuchstabeOderMarkReferenz(cp[i - 1]);
        boolean rechtsFrei = i + 1 >= n || !istBuchstabeOderMarkReferenz(cp[i + 1]);
        return linksFrei && rechtsFrei;
    }

    private static boolean istSperrTrennerReferenz(int c) {
        return c == ' ' || c == NBSP || c == DUENN || c == SCHMAL_NBSP || c == '.' || c == '-'
                || c == GESCH_BINDESTRICH || c == HALBGEVIERT;
    }

    /** Faltung nur fuer {@code A-Z}; jeder andere Buchstabe wird zu einem Zeichen, das in keinem Wort vorkommt. */
    private static char faltungReferenz(int c) {
        if (c >= 'A' && c <= 'Z') {
            return (char) (c + 32);
        }
        if (c >= 'a' && c <= 'z') {
            return (char) c;
        }
        return '?';
    }

    static boolean istZiffer(char c) {
        return c >= '0' && c <= '9';
    }

    static boolean istGruppentrenner(char c) {
        return c == ' ' || c == NBSP || c == DUENN || c == SCHMAL_NBSP || c == '-' || c == '.' || c == '/';
    }

    /** Neun Ziffern in 3-3-3 mit dem Trenner {@code t}. */
    static String gruppiert(String neun, String t) {
        return neun.substring(0, 3) + t + neun.substring(3, 6) + t + neun.substring(6);
    }

    static String zeichen(int codepunkt) {
        return new String(Character.toChars(codepunkt));
    }
}
