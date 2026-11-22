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
    static final char SHY = (char) 0xAD;
    static final char ZWSP = (char) 0x200B;
    static final char ZWNJ = (char) 0x200C;
    static final char ZWJ = (char) 0x200D;
    static final char WORD_JOINER = (char) 0x2060;
    static final char BOM = (char) 0xFEFF;
    static final char VS16 = (char) 0xFE0F;
    static final char AKUT = (char) 0x0301;
    static final char DUENN = (char) 0x2009;
    static final char SCHMAL_NBSP = (char) 0x202F;
    static final char NEL = (char) 0x85;
    static final char LS = (char) 0x2028;
    static final char PS = (char) 0x2029;

    /** Kontextwort-Varianten je Art (fuer den Eigenschaftstest). */
    static final String[] KW_LANR = {"LANR", "lanr", "Lanr", "LANR-Nr.", "LANRNR", "Arztnummer", "Arzt-Nr.",
        "Arzt Nummer", "Praxis-LANR", "Lebenslange Arztnummer", "ARZT-NR", "arztnr"};
    static final String[] KW_IK = {"IK", "ik", "Ik", "IK-Nr.", "IKNR", "IK-Nummer", "IKNUMMER",
        "Institutionskennzeichen", "Praxis-IK", "INSTITUTIONSKENNZEICHEN", "iknr"};

    /** Konfiguration je Nummernart. */
    static final class Art {
        final String name;
        final boolean lanr;
        final Recognizer erkenner;
        final FindingType typ;
        final String kw;
        final String kwLang;
        final String gueltig;
        final String gueltig2;
        final String gueltig3;
        final String ungueltig;
        final double sicher;
        final String quelle;
        final String[] kws;
        final String[] fremdeKws;

        Art(String name, boolean lanr, Recognizer erkenner, FindingType typ, String kw, String kwLang, String gueltig,
                String gueltig2, String gueltig3, String ungueltig, double sicher, String quelle, String[] kws,
                String[] fremdeKws) {
            this.name = name;
            this.lanr = lanr;
            this.erkenner = erkenner;
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
    }

    static final Art LANR_ART = new Art("LANR", true, new LanrRecognizer(), FindingType.LANR, "LANR", "Arztnummer",
            LANR_GUELTIG, LANR_GUELTIG_ZWEI, LANR_GUELTIG_FACHGRUPPE, LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, 0.95,
            "lanr", KW_LANR, KW_IK);
    static final Art IK_ART = new Art("IK", false, new IkRecognizer(), FindingType.IK, "IK", "Institutionskennzeichen",
            IK_GUELTIG, IK_GUELTIG_ZWEI, IK_GUELTIG_DREI, IK_UNGUELTIG_KOMPLEMENT, 0.92, "ik", KW_IK, KW_LANR);
    static final List<Art> ARTEN = List.of(LANR_ART, IK_ART);

    /** Der Text aus Codepunkten. */
    static String u(int... codepunkte) {
        return new String(codepunkte, 0, codepunkte.length);
    }

    // ------------------------------------------------------------------ Pruefhilfen auf Erkennerebene

    /** Erwarteter Fund: Wert und Konfidenz (Stufe und Quelle folgen daraus). */
    record Erw(String wert, double konfidenz) {
    }

    // ------------------------------------------------------------------ Hilfen

    static List<Finding> scan(Art a, String text) {
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
        return funde;
    }

    /** Genau ein Fund {@code wert} direkt hinter {@code vor}. */
    static void assertEin(Art a, String vor, String wert, String nach, double konfidenz) {
        List<Finding> funde = scan(a, vor + wert + nach);
        assertEquals(1, funde.size(), a.name + " Anzahl Funde");
        Finding f = funde.get(0);
        assertTrue(wert.equals(f.originalValue()), a.name + " Originalwert");
        assertEquals(vor.length(), f.start(), a.name + " Start");
        assertEquals(vor.length() + wert.length(), f.end(), a.name + " Ende");
        assertEquals(konfidenz, f.confidence(), a.name + " Konfidenz");
        assertEquals(ConfidenceLevel.of(konfidenz), f.level(), a.name + " Stufe");
        assertEquals(a.quelleFuer(konfidenz), f.source(), a.name + " Quelle");
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
