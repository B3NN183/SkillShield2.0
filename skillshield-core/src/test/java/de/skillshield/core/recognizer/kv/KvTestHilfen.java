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
import java.util.Arrays;
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
 * <p><b>Fehlalarm-Pruefung (Teil 2, kv-trenner-und-umbruch, T-5).</b> {@link #scan} ruft zusaetzlich
 * {@link #pruefeKeineZusaetzlichenFunde} auf: In Texten ohne {@code CR UZ+ LF} ({@link #enthaeltCrUnsichtbarLf}) und
 * ohne Weit-Kontextwort der Art mit erweiterter Luecke ({@link #weitKontextwoerterReferenz}) muss die Fundliste des
 * Produkts der der eingefrorenen Kopie in allen Feldern gleichen ({@link #abweichendeFunde}). In Texten mit solchem
 * Weit-Kontextwort ist ein zusaetzlicher Fund nur zulaessig, wenn die Kopie ihn auf der
 * {@link #wortVergleichsform Wort-Vergleichsform} liefert ({@link #fehlalarmZaehlung}). Die Referenzen
 * ({@link #weitFolgenReferenz}), der kuenstliche Neu-Erkenner ({@link #kuenstlicherNeuErkenner}) fuer die
 * Selbsttests und die Vergleichsform sind unabhaengig vom Produkt gebaut.
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
    public static final char VS1 = (char) 0xFE00;

    /**
     * Die sechs unsichtbaren Zeichen aus AK-1 des Features kv-trenner-und-umbruch und der Variantenwaehler U+FE00.
     * Die Reihenfolge ist fest: Zufallsfolgen der Tests, die daraus ziehen, haengen von ihr ab.
     */
    static final char[] UZ_AK1 = {ZWSP, SHY, BOM, WORD_JOINER, LRM, VS16, VS1};

    /** Die Zeichen von {@link #UZ_AK1} in derselben Reihenfolge, je als Zeichenkette (jedes Mal neu angelegt). */
    public static String[] uzAk1Texte() {
        String[] texte = new String[UZ_AK1.length];
        for (int i = 0; i < texte.length; i++) {
            texte[i] = String.valueOf(UZ_AK1[i]);
        }
        return texte;
    }

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

    /**
     * Unabhaengige Referenz der Umbruchzaehlung (D-1, Feature kv-trenner-und-umbruch, AK-24 (b)): Zahl der
     * Zeilenumbrueche einer Zeichenkette aus Umbruechen, unsichtbaren Zeichen und Leerraum. CR, dann ein oder mehrere
     * unsichtbare Zeichen (je Codepunkt), dann LF ist ein Umbruch ({@code CR LF} ebenso); jedes andere CR, LF,
     * U+0085, U+2028 und U+2029 ist je ein Umbruch; unsichtbare Zeichen und Leerraum zaehlen nicht. Die Referenz
     * ruft weder Scanner noch Erkenner noch eine eingefrorene Kopie auf.
     */
    public static int umbruchZahlReferenz(String folge) {
        int zahl = 0;
        int i = 0;
        while (i < folge.length()) {
            char c = folge.charAt(i);
            if (c == '\r') {
                int j = i + 1;
                while (j < folge.length() && unsichtbar(folge.codePointAt(j))) {
                    j += Character.charCount(folge.codePointAt(j));
                }
                i = j < folge.length() && folge.charAt(j) == '\n' ? j + 1 : i + 1;
                zahl++;
            } else {
                if (c == '\n' || c == NEL || c == LS || c == PS) {
                    zahl++;
                }
                i++;
            }
        }
        return zahl;
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
        List<Finding> alt = a.standLuecken.detect(text);
        pruefeGegenVorgaenger(a, text, funde);
        pruefeGegenStandLuecken(a, text, funde, alt);
        pruefeKeineZusaetzlichenFunde(a, text, funde, alt);
        return funde;
    }

    /**
     * Fehlalarm-Pruefstand fuer Teil 2 (kv-trenner-und-umbruch, D-8, AK-15, AK-16, T-5) mit der Fundliste der Kopie
     * ({@code alt}, einmal berechnet): Die Zahl nach {@link #fehlalarmZaehlung} ist 0. Die Meldung nennt nie Text
     * oder Ziffern.
     */
    static void pruefeKeineZusaetzlichenFunde(Art a, String text, List<Finding> neu, List<Finding> alt) {
        int zusaetzlich = fehlalarmZahl(a, text, neu, alt);
        assertEquals(0, zusaetzlich, a.name + " Fehlalarm Teil 2: " + zusaetzlich + " zusaetzlicher Fund gegenueber "
                + "dem Stand Luecken (Textlaenge " + text.length() + ")");
    }

    /** Wie oben, die Fundliste der Kopie wird hier berechnet. */
    static void pruefeKeineZusaetzlichenFunde(Art a, String text, List<Finding> neu) {
        pruefeKeineZusaetzlichenFunde(a, text, neu, a.standLuecken.detect(text));
    }

    /** Zahl der Funde, die der Fehlalarm-Pruefstand meldet ({@link #fehlalarmZaehlung}, erster Wert). */
    static int fehlalarmZahl(Art a, String text, List<Finding> neu) {
        return fehlalarmZahl(a, text, neu, a.standLuecken.detect(text));
    }

    /** Wie oben mit der bereits berechneten Fundliste der Kopie. */
    static int fehlalarmZahl(Art a, String text, List<Finding> neu, List<Finding> alt) {
        return fehlalarmZaehlung(a, text, neu, alt)[0];
    }

    /**
     * Die Zaehlung des Fehlalarm-Pruefstands je Text und Art, {@code {unerklaert, erklaert}}:
     * <ul>
     * <li>Text mit {@code CR UZ+ LF} ({@link #enthaeltCrUnsichtbarLf}): {@code {0, 0}}; dort wirkt Teil 1 (Kap. 13
     * Hinweis 1 des Plans), und die Kopie unterscheidet sich gewollt vom Produkt.</li>
     * <li>Text ohne Weit-Kontextwort der Art mit erweiterter Luecke ({@link #vonFehlalarmPruefungAusgenommen}
     * ist falsch): volle Gleichheit mit der Kopie, unerklaert ist {@link #abweichendeFunde}, erklaert 0.</li>
     * <li>Text mit einem solchen Weit-Kontextwort: Ein Fund des Produkts, den die Kopie auf dem Originaltext nicht
     * mit gleichem Start, Ende und Typ liefert, ist <em>erklaert</em>, wenn die Kopie ihn auf der
     * {@link #wortVergleichsform Wort-Vergleichsform} der Art (auf den Originaltext zurueckgebildet) mit gleichem
     * Start, Ende und Typ liefert (die Konfidenz wird nicht verglichen); sonst <em>unerklaert</em> und damit ein
     * Fehlalarm.</li>
     * </ul>
     */
    static int[] fehlalarmZaehlung(Art a, String text, List<Finding> neu, List<Finding> alt) {
        if (enthaeltCrUnsichtbarLf(text)) {
            return new int[2];
        }
        if (!vonFehlalarmPruefungAusgenommen(a, text)) {
            return new int[] {abweichendeFunde(alt, neu), 0};
        }
        Vergleichsform vf = wortVergleichsform(text, a);
        List<Finding> erklaerend = vf.aufOriginal(a.standLuecken.detect(vf.text()));
        int unerklaert = 0;
        int erklaert = 0;
        for (Finding f : neu) {
            if (findeGleichenFund(f, alt) != null) {
                continue;
            }
            if (findeGleichenFund(f, erklaerend) != null) {
                erklaert++;
            } else {
                unerklaert++;
            }
        }
        return new int[] {unerklaert, erklaert};
    }

    /**
     * Ausgenommen von der strengen Pruefung (volle Gleichheit) sind Texte mit einem Weit-Kontextwort der Art mit
     * mindestens einer erweiterten Luecke; dort gilt die Erklaerungsbedingung in {@link #fehlalarmZaehlung}. Texte mit
     * {@code CR UZ+ LF} nimmt {@link #fehlalarmZaehlung} ganz aus.
     */
    static boolean vonFehlalarmPruefungAusgenommen(Art a, String text) {
        return hatErweitertesWeitKontextwort(weitFolgenReferenz(text), a);
    }

    /**
     * Zahl der Abweichungen des Produkts von der Kopie in einem nicht ausgenommenen Text (volle Gleichheit in Start,
     * Ende, Typ, Konfidenz und Quelle, gleiche Anzahl): die Funde in {@code neu} ohne vollstaendig gleichen Fund in
     * {@code alt} (zusaetzliche, in Konfidenz oder Quelle geaenderte) plus die fehlenden Funde, wenn {@code neu}
     * kuerzer ist.
     */
    public static int abweichendeFunde(List<Finding> alt, List<Finding> neu) {
        int zahl = 0;
        for (Finding f : neu) {
            boolean gleich = false;
            for (Finding g : alt) {
                gleich |= g.start() == f.start() && g.end() == f.end() && g.type() == f.type()
                        && g.confidence() == f.confidence() && g.source().equals(f.source());
            }
            zahl += gleich ? 0 : 1;
        }
        return zahl + Math.max(0, alt.size() - neu.size());
    }

    /**
     * Zahl der Funde in {@code neu}, zu denen {@code alt} keinen Fund mit gleichem Start, Ende und Typ enthaelt
     * (AK-15: "ein zusaetzlicher Fund ist jeder Fund, der im alten Ergebnis nicht mit gleichem Typ und Bereich
     * enthalten ist"). Fehlende Funde zaehlen hier nicht.
     */
    public static int zusaetzlicheFunde(List<Finding> alt, List<Finding> neu) {
        int zahl = 0;
        for (Finding f : neu) {
            if (findeGleichenFund(f, alt) == null) {
                zahl++;
            }
        }
        return zahl;
    }

    /**
     * Strenger Bestandsschutz gegen den eingefrorenen Stand am Ende von kv-kontext-luecken: kein Fund fehlt, keine
     * Konfidenz sinkt, die Quelle bleibt gleich, ohne jede Ausnahme ({@link #verletztBestandsschutzOhneAusnahme}).
     * Die Meldung nennt nie Text oder Ziffern.
     */
    static void pruefeGegenStandLuecken(Art a, String text, List<Finding> neu) {
        pruefeGegenStandLuecken(a, text, neu, a.standLuecken.detect(text));
    }

    /** Wie oben mit der bereits berechneten Fundliste der Kopie. */
    static void pruefeGegenStandLuecken(Art a, String text, List<Finding> neu, List<Finding> alt) {
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

    /** Die Kontextwoerter der LANR ab vier Buchstaben, gefaltet (gemeinsam fuer Sperrschrift und Weit-Folgen). */
    private static final List<String> WOERTER_LANR = List.of("lanr", "lanrnr", "arztnummer", "arztnr");
    /** Die Kontextwoerter der IK ab vier Buchstaben, gefaltet (das Zwei-Buchstaben-Wort fehlt hier, D-4). */
    private static final List<String> WOERTER_IK_LANG = List.of("iknr", "iknummer", "institutionskennzeichen");
    private static final List<String> SPERR_WOERTER_IK = List.of("ik", "iknr", "iknummer", "institutionskennzeichen");

    /**
     * Der Text ohne unsichtbare Zeichen (je Codepunkt) mit Abbildung auf den Originaltext.
     *
     * @param cp die Codepunkte ohne unsichtbare Zeichen
     * @param ursprung je Codepunkt der Index seines ersten Zeichens im Originaltext
     * @param n Zahl der Codepunkte
     */
    private record OhneUz(int[] cp, int[] ursprung, int n) {
    }

    private static OhneUz ohneUnsichtbare(String text) {
        int[] cp = new int[text.length()];
        int[] ursprung = new int[text.length()];
        int n = 0;
        for (int i = 0; i < text.length();) {
            int c = text.codePointAt(i);
            if (!unsichtbar(c)) {
                cp[n] = c;
                ursprung[n] = i;
                n++;
            }
            i += Character.charCount(c);
        }
        return new OhneUz(cp, ursprung, n);
    }

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
        List<String> woerter = a.lanr ? WOERTER_LANR : SPERR_WOERTER_IK;
        OhneUz s = ohneUnsichtbare(text);
        int[] cp = s.cp();
        int[] ursprung = s.ursprung();
        int n = s.n();
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

    // ------------------------------------------------------------------ Referenz Weit-Folgen (T-5, Orakel Teil 2)

    /** Luecke nach D-2 (a): genau ein Sperr-Trenner (Leerraum der Sperrschrift, Punkt, Bindestrich, U+2011, U+2013). */
    static final int LUECKE_STRENG = 1;
    /** Luecke nach D-2 (b): zwei oder drei Leerraumzeichen der Sperrschrift. */
    static final int LUECKE_LEERRAUM = 2;
    /** Luecke nach D-2 (c): ein Punkt und ein bis drei Leerraumzeichen der Sperrschrift. */
    static final int LUECKE_PUNKT = 4;
    /** Luecke nach D-2 (d): genau ein Tab. */
    static final int LUECKE_TAB = 8;

    /**
     * Eine maximale Folge von Einzelbuchstaben, je durch eine Luecke nach D-2 verbunden.
     *
     * @param start Beginn des ersten Buchstabens im Originaltext
     * @param ende Ende hinter dem letzten Buchstaben im Originaltext
     * @param buchstaben Zahl der Buchstaben
     * @param gefaltet die Buchstaben, {@code A-Z} zu {@code a-z} gefaltet, jeder andere Buchstabe als {@code ?}
     * @param luecken Bitmenge der vorkommenden Lueckenarten ({@link #LUECKE_STRENG} usw.)
     */
    public record WeitFolge(int start, int ende, int buchstaben, String gefaltet, int luecken) {

        /** Mindestens eine Luecke nach D-2 (b), (c) oder (d). */
        public boolean erweitert() {
            return (luecken & (LUECKE_LEERRAUM | LUECKE_PUNKT | LUECKE_TAB)) != 0;
        }
    }

    /**
     * Unabhaengige Referenz der Weit-Folgen (D-2, D-3; Plan Kap. 4.3). Sie nutzt weder Scanner noch eingefrorene
     * Kopie und ist bewusst anders gebaut als der Zustandsautomat des Plans: Zuerst werden alle unsichtbaren Zeichen
     * entfernt (mit Indexabbildung auf den Originaltext), dann wird zu jedem Einzelbuchstaben (Buchstabe ohne
     * Buchstaben oder Kombinationszeichen links und rechts) die nicht leere Zeichenfolge bis zum naechsten Buchstaben
     * als Zeichenkette gegen die Luecken (a) bis (d) geprueft; passt sie und ist der naechste Buchstabe wieder ein
     * Einzelbuchstabe, gehoert er zur Folge. Zusaetzliche Zeichen in der Luecke (Ziffern, Umbruch, Komma, Doppelpunkt,
     * vier Leerzeichen, zwei Tabs, Tab mit Leerzeichen, {@code .} nach Leerzeichen ...) beenden die Folge. Laufzeit
     * egal.
     */
    public static List<WeitFolge> weitFolgenReferenz(String text) {
        OhneUz s = ohneUnsichtbare(text);
        int[] cp = s.cp();
        int[] ursprung = s.ursprung();
        int n = s.n();
        List<WeitFolge> folgen = new ArrayList<>();
        int i = 0;
        while (i < n) {
            if (!istEinzelbuchstabeReferenz(cp, n, i)) {
                i++;
                continue;
            }
            int letzter = i;
            StringBuilder gefaltet = new StringBuilder().append(faltungReferenz(cp[i]));
            int luecken = 0;
            while (true) {
                int j = letzter + 1;
                while (j < n && !Character.isLetter(cp[j])) {
                    j++;
                }
                if (j >= n || !istEinzelbuchstabeReferenz(cp, n, j)) {
                    break;
                }
                int art = lueckenArtReferenz(cp, letzter + 1, j);
                if (art == 0) {
                    break;
                }
                luecken |= art;
                gefaltet.append(faltungReferenz(cp[j]));
                letzter = j;
            }
            folgen.add(new WeitFolge(ursprung[i], ursprung[letzter] + Character.charCount(cp[letzter]),
                    gefaltet.length(), gefaltet.toString(), luecken));
            i = letzter + 1;
        }
        return folgen;
    }

    private static boolean istLeerraum4Referenz(int c) {
        return c == ' ' || c == NBSP || c == DUENN || c == SCHMAL_NBSP;
    }

    /** Die Art der Luecke {@code cp[von..bis)} nach D-2 ({@code LUECKE_*}), 0 wenn sie keine Luecke ist. */
    private static int lueckenArtReferenz(int[] cp, int von, int bis) {
        int laenge = bis - von;
        if (laenge == 1) {
            int c = cp[von];
            if (c == '\t') {
                return LUECKE_TAB;
            }
            boolean streng = istLeerraum4Referenz(c) || c == '.' || c == '-' || c == GESCH_BINDESTRICH
                    || c == HALBGEVIERT;
            return streng ? LUECKE_STRENG : 0;
        }
        if (laenge > 4) {
            return 0;
        }
        boolean nurLeerraum = laenge <= 3;
        for (int k = von; k < bis; k++) {
            nurLeerraum &= istLeerraum4Referenz(cp[k]);
        }
        if (nurLeerraum) {
            return LUECKE_LEERRAUM;
        }
        boolean punktUndLeerraum = cp[von] == '.';
        for (int k = von + 1; k < bis; k++) {
            punktUndLeerraum &= istLeerraum4Referenz(cp[k]);
        }
        return punktUndLeerraum ? LUECKE_PUNKT : 0;
    }

    /**
     * Die Weit-Folgen, die mit 4 bis 23 Buchstaben einem Kontextwort der Art ab vier Buchstaben gleichen (D-3; nie
     * das Zwei-Buchstaben-Wort, D-4), je {@code {Start, Ende, erweitert}} ({@code erweitert} 1 oder 0).
     */
    static List<int[]> weitKontextwoerterReferenz(String text, Art a) {
        List<int[]> treffer = new ArrayList<>();
        for (WeitFolge f : weitFolgenReferenz(text)) {
            if (istWeitKontextwort(f, a)) {
                treffer.add(new int[] {f.start(), f.ende(), f.erweitert() ? 1 : 0});
            }
        }
        return treffer;
    }

    private static boolean istWeitKontextwort(WeitFolge f, Art a) {
        List<String> woerter = a.lanr ? WOERTER_LANR : WOERTER_IK_LANG;
        return f.buchstaben() >= 4 && f.buchstaben() <= 23 && woerter.contains(f.gefaltet());
    }

    /** Gibt es in den Folgen ein Weit-Kontextwort der Art mit mindestens einer erweiterten Luecke? */
    public static boolean hatErweitertesWeitKontextwort(List<WeitFolge> folgen, Art a) {
        for (WeitFolge f : folgen) {
            if (f.erweitert() && istWeitKontextwort(f, a)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ausnahme des Fehlalarm-Pruefstands auf Scannerebene (beide Arten zugleich) von der strengen Pruefung: Der Text
     * enthaelt {@code CR UZ+ LF} (dann wird er gar nicht geprueft) oder fuer mindestens eine der beiden Arten ein
     * Weit-Kontextwort mit erweiterter Luecke (dann gilt die Erklaerungsbedingung mit der Wort-Vergleichsform beider
     * Arten, {@link #wortVergleichsform}).
     */
    public static boolean vonFehlalarmPruefungBeiderArtenAusgenommen(String text) {
        List<WeitFolge> folgen = weitFolgenReferenz(text);
        return enthaeltCrUnsichtbarLf(text) || hatErweitertesWeitKontextwort(folgen, LANR_ART)
                || hatErweitertesWeitKontextwort(folgen, IK_ART);
    }

    // ------------------------------------------------------------------ Wort-Vergleichsform (Plan 9a, D-5)

    /**
     * Die Wort-Vergleichsform eines Textes und die Abbildung ihrer Positionen auf den Originaltext.
     *
     * @param original der Originaltext
     * @param text die Vergleichsform
     * @param von je Zeichen der Vergleichsform der Anfang des Bereichs im Originaltext, den es vertritt
     * @param bis je Zeichen der Vergleichsform das Ende dieses Bereichs
     */
    public record Vergleichsform(String original, String text, int[] von, int[] bis) {

        /**
         * Bildet Funde auf der Vergleichsform auf den Originaltext zurueck: Start ist der abgebildete Start, Ende
         * das abgebildete Ende (hinter dem Bereich des letzten Zeichens); Typ, Konfidenz und Quelle bleiben.
         */
        public List<Finding> aufOriginal(List<Finding> funde) {
            List<Finding> ergebnis = new ArrayList<>();
            for (Finding f : funde) {
                int s = von[f.start()];
                int e = bis[f.end() - 1];
                ergebnis.add(new Finding(f.type(), original.substring(s, e), s, e, f.confidence(), f.source()));
            }
            return ergebnis;
        }
    }

    /**
     * Die <b>Wort-Vergleichsform</b> (Plan Kap. 3.2.5, 9a): Im Originaltext werden nur die <em>erweiterten</em>
     * Luecken (D-2 (b), (c), (d)) <em>innerhalb</em> der Weit-Kontextwoerter der angegebenen Arten, wie sie
     * {@link #weitKontextwoerterReferenz} mit erweiterter Luecke findet, je durch genau ein Leerzeichen ersetzt. Eine
     * Luecke ist dabei der gesamte Bereich zwischen dem Ende eines Buchstabens und dem Beginn des naechsten
     * Buchstabens der Folge, einschliesslich darin liegender unsichtbarer Zeichen. Alles andere bleibt Zeichen fuer
     * Zeichen unveraendert: strenge Luecken (D-2 (a)) auch im Weit-Kontextwort, alle Luecken ausserhalb der
     * Woerter (zum Beispiel {@code I. K.} neben einem {@code L. A. N. R.}), und das Zeichen oder der Trenner direkt
     * hinter dem letzten Buchstaben (der Schlusspunkt): {@code L.  A.  N.  R.<Tab>des} wird zu
     * {@code L A N R.<Tab>des}. Nicht ersetzt wird <em>jede</em> erweiterte Luecke des Textes: Sonst wuerde
     * {@code I. K. <IK>} (D-4, zwei Buchstaben) zu {@code I K <IK>} und der Fehlalarm waere "erklaert".
     *
     * <p>Die Abbildung fuehrt je Zeichen der Vergleichsform den Bereich im Originaltext ({@code von}, {@code bis});
     * ein eingesetztes Leerzeichen vertritt die ganze ersetzte Luecke. Unabhaengig vom Produkt gebaut (nutzt nur die
     * Referenzen dieser Klasse).
     *
     * @param arten die Arten, deren Weit-Kontextwoerter gelten (Erkennerebene: eine Art, Scannerebene: beide)
     */
    public static Vergleichsform wortVergleichsform(String text, Art... arten) {
        OhneUz s = ohneUnsichtbare(text);
        List<int[]> luecken = new ArrayList<>();
        for (Art a : arten) {
            for (int[] wort : weitKontextwoerterReferenz(text, a)) {
                if (wort[2] == 1) {
                    erweiterteLuecken(s, wort[0], wort[1], luecken);
                }
            }
        }
        luecken.sort((x, y) -> Integer.compare(x[0], y[0]));
        StringBuilder sb = new StringBuilder();
        int[] von = new int[text.length()];
        int[] bis = new int[text.length()];
        int pos = 0;
        for (int[] l : luecken) {
            for (; pos < l[0]; pos++) {
                von[sb.length()] = pos;
                bis[sb.length()] = pos + 1;
                sb.append(text.charAt(pos));
            }
            von[sb.length()] = l[0];
            bis[sb.length()] = l[1];
            sb.append(' ');
            pos = l[1];
        }
        for (; pos < text.length(); pos++) {
            von[sb.length()] = pos;
            bis[sb.length()] = pos + 1;
            sb.append(text.charAt(pos));
        }
        return new Vergleichsform(text, sb.toString(), Arrays.copyOf(von, sb.length()),
                Arrays.copyOf(bis, sb.length()));
    }

    /**
     * Haengt die erweiterten Luecken der Buchstaben im Originalbereich {@code [start, ende)} an {@code aus} an, je
     * als {@code {Anfang, Ende}} im Originaltext (von hinter dem einen bis vor den naechsten Buchstaben).
     */
    private static void erweiterteLuecken(OhneUz s, int start, int ende, List<int[]> aus) {
        int voriger = -1;
        for (int i = 0; i < s.n(); i++) {
            if (s.ursprung()[i] < start || s.ursprung()[i] >= ende || !Character.isLetter(s.cp()[i])) {
                continue;
            }
            if (voriger >= 0) {
                int art = lueckenArtReferenz(s.cp(), voriger + 1, i);
                if (art != 0 && art != LUECKE_STRENG) {
                    aus.add(new int[] {s.ursprung()[voriger] + Character.charCount(s.cp()[voriger]),
                        s.ursprung()[i]});
                }
            }
            voriger = i;
        }
    }

    // ------------------------------------------------------------------ Kuenstlicher Neu-Erkenner (Trennschaerfe)

    /** Kuenstlicher Fehlalarm: ein Fund hinter {@code I. K. } (Zwei-Buchstaben-Wort, D-4). */
    public static final int FEHLALARM_IK_INITIALEN = 0;
    /** Kuenstlicher Fehlalarm: ein Fund hinter einem Einzelbuchstaben mit Punkt und Leerzeichen (Initialen). */
    public static final int FEHLALARM_INITIALE = 1;
    /** Kuenstlicher Fehlalarm: ein Fund direkt hinter einer Leerzeile ({@code LF LF}). */
    public static final int FEHLALARM_LEERZEILE = 2;

    /**
     * Ein kuenstlicher "richtiger" Neu-Erkenner nur fuer Selbsttests: wendet {@code kopie} auf die
     * {@link #wortVergleichsform Wort-Vergleichsform} fuer {@code arten} an und bildet die Funde auf den Originaltext
     * zurueck. Er verhaelt sich wie ein korrektes Teil 2 (Weit-Kontextwort wirkt wie das gesperrte Kontextwort) und
     * darf vom Fehlalarm-Pruefstand nie gemeldet werden.
     */
    public static Recognizer kuenstlicherRichtigerNeuErkenner(Recognizer kopie, Art... arten) {
        return text -> {
            Vergleichsform vf = wortVergleichsform(text, arten);
            return vf.aufOriginal(kopie.detect(vf.text()));
        };
    }

    /**
     * Ein kuenstlicher "Neu"-Erkenner, der die Funde von {@code basis} unveraendert bereichsgleich liefert, aber die
     * Konfidenz um {@code zuschlag} anhebt (hoechstens 1,0) und, wenn {@code quelle} nicht {@code null} ist, die
     * Quelle ersetzt. Der Pruefstand muss das in nicht ausgenommenen Texten melden (volle Gleichheit).
     */
    public static Recognizer kuenstlicherAbweichenderNeuErkenner(Recognizer basis, double zuschlag, String quelle) {
        return text -> {
            List<Finding> ergebnis = new ArrayList<>();
            for (Finding f : basis.detect(text)) {
                ergebnis.add(new Finding(f.type(), f.originalValue(), f.start(), f.end(),
                        Math.min(1.0, f.confidence() + zuschlag), quelle == null ? f.source() : quelle));
            }
            return ergebnis;
        };
    }

    /**
     * Ein kuenstlicher "Neu"-Erkenner nur fuer Selbsttests des Fehlalarm-Pruefstands: liefert alle Funde von
     * {@code basis} und zusaetzlich je eine Ziffernkette mit mindestens sechs Ziffern, die hinter einem
     * Fehlalarm-Muster steht ({@code modus}: {@link #FEHLALARM_IK_INITIALEN}, {@link #FEHLALARM_INITIALE} oder
     * {@link #FEHLALARM_LEERZEILE}),
     * sofern sie keinen vorhandenen Fund ueberlappt. Der Pruefstand muss diesen zusaetzlichen Fund melden.
     */
    public static Recognizer kuenstlicherNeuErkenner(Recognizer basis, FindingType typ, String quelle, int modus) {
        return text -> {
            List<Finding> alle = new ArrayList<>(basis.detect(text));
            int s = 0;
            while (s < text.length()) {
                int e = s;
                while (e < text.length() && istZiffer(text.charAt(e))) {
                    e++;
                }
                if (e - s >= 6 && hinterFehlalarmMuster(text, s, modus) && !ueberlappt(alle, s, e)) {
                    alle.add(new Finding(typ, text.substring(s, e), s, e, WAHRSCHEINLICH, quelle));
                }
                s = Math.max(e, s + 1);
            }
            alle.sort((x, y) -> Integer.compare(x.start(), y.start()));
            return alle;
        };
    }

    private static boolean hinterFehlalarmMuster(String text, int s, int modus) {
        if (s > 0 && istZiffer(text.charAt(s - 1))) {
            return false;
        }
        if (modus == FEHLALARM_IK_INITIALEN) {
            return s >= 6 && text.startsWith("I. K. ", s - 6);
        }
        if (modus == FEHLALARM_LEERZEILE) {
            return s >= 2 && text.startsWith("\n\n", s - 2);
        }
        return s >= 3 && text.charAt(s - 1) == ' ' && text.charAt(s - 2) == '.'
                && Character.isLetter(text.charAt(s - 3)) && (s < 4 || !Character.isLetter(text.charAt(s - 4)));
    }

    private static boolean ueberlappt(List<Finding> funde, int start, int ende) {
        for (Finding f : funde) {
            if (f.start() < ende && start < f.end()) {
                return true;
            }
        }
        return false;
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
