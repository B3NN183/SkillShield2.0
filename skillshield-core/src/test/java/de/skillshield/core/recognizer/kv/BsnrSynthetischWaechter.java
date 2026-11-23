package de.skillshield.core.recognizer.kv;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Laufzeit-Waechter fuer synthetische BSNR in Testtexten (Feature kvnr-bsnr-erkennung, T-12, Anforderung A-6,
 * Nachtrag zu Teil 2 Punkt 1 und 5). Er liegt in einer eigenen Klasse, weil {@link KvnrBsnrHilfen} eingefroren ist.
 *
 * <p>Zwei Regeln, beide ohne Ausnahmeliste:
 * <ol>
 *   <li><b>Neunerfolgen.</b> Jede Folge aus ganzen Ziffernbloecken mit zusammen genau neun Ziffern (Bloecke durch genau
 *       einen Trenner verbunden) hat entweder das Praefix {@code 00} oder die Ziffern {@code 00000} an den Stellen 3
 *       bis 7 (BSNR nach A-6). Vollbreite Ziffern (U+FF10 bis U+FF19) zaehlen als Ziffern.</li>
 *   <li><b>Keine ungewollte KVNR-Form.</b> Die enge KVNR-Form nach E-3 ({@link KvnrBsnrHilfen#kvnrFormEngTreffer}:
 *       Grossbuchstabe mit Wortgrenze davor, genau neun Ziffern, Wortgrenze dahinter, gueltige Pruefziffer) kommt im
 *       Teil-2-Text nicht vor, ausser der Aufrufer nennt die Zahl der beabsichtigten Formen. Steht eine BSNR-Testnummer
 *       direkt hinter einem einzelnen Grossbuchstaben und ist die Pruefziffer zufaellig passend, entstuende sonst mit
 *       Teil 1b ein KVNR-Fund (Regel 12 des Plans).</li>
 * </ol>
 *
 * <p>Meldungen nennen nur Zahlen, nie Text oder Ziffern aus dem Testtext.
 */
public final class BsnrSynthetischWaechter {

    private static final int NEUN = 9;
    private static final String PRAEFIX = "00";
    private static final int NULLBLOCK_VON = 2;
    private static final int NULLBLOCK_BIS = 7;
    private static final String NULLBLOCK = "00000";
    private static final char VOLLBREIT_NULL = (char) 0xFF10;
    private static final char VOLLBREIT_NEUN = (char) 0xFF19;

    /**
     * Ergebnis einer Pruefung.
     *
     * @param neunerfolgen Zahl der gefundenen Neunerfolgen (ganze Bloecke, zusammen genau neun Ziffern)
     * @param verstoesse   davon die Zahl ohne Praefix {@code 00} und ohne {@code 00000} an Stelle 3 bis 7
     * @param kvnrFormen   Zahl der Treffer der engen KVNR-Form im Text
     */
    public record Befund(int neunerfolgen, int verstoesse, int kvnrFormen) {
    }

    private BsnrSynthetischWaechter() {
    }

    /** ASCII-Ziffer oder vollbreite Ziffer. */
    private static boolean istZiffer(char c) {
        return (c >= '0' && c <= '9') || (c >= VOLLBREIT_NULL && c <= VOLLBREIT_NEUN);
    }

    private static char asciiZiffer(char c) {
        return c >= VOLLBREIT_NULL ? (char) ('0' + (c - VOLLBREIT_NULL)) : c;
    }

    /** Trenner innerhalb einer Kette: Leerzeichen, Tab, NBSP, Bindestrich, Punkt, Schraegstrich, Unterstrich. */
    private static boolean istTrenner(char c) {
        return c == ' ' || c == '\t' || c == (char) 0xA0 || c == '-' || c == '.' || c == '/' || c == '_';
    }

    /**
     * Prueft einen Text und zaehlt Neunerfolgen, Verstoesse und Treffer der engen KVNR-Form. Ueberlappende Ketten
     * werden mehrfach gezaehlt (konservativ): jeder Ziffernlauf-Anfang zaehlt fuer sich.
     */
    public static Befund pruefe(String text) {
        int neunerfolgen = 0;
        int verstoesse = 0;
        for (int i = 0; i < text.length(); i++) {
            boolean laufAnfang = istZiffer(text.charAt(i)) && (i == 0 || !istZiffer(text.charAt(i - 1)));
            if (!laufAnfang) {
                continue;
            }
            String neun = neunerfolgAus(text, i);
            if (neun == null) {
                continue;
            }
            neunerfolgen++;
            String mitte = neun.substring(NULLBLOCK_VON, NULLBLOCK_BIS);
            boolean erlaubt = neun.startsWith(PRAEFIX) || mitte.equals(NULLBLOCK);
            verstoesse += erlaubt ? 0 : 1;
        }
        return new Befund(neunerfolgen, verstoesse, KvnrBsnrHilfen.kvnrFormEngTreffer(text).size());
    }

    /**
     * Liest ab {@code von} Ziffernbloecke, je zwei durch genau einen Trenner verbunden. Haben die ersten Bloecke
     * zusammen genau neun Ziffern, werden diese als ASCII-Ziffern zurueckgegeben; sonst {@code null}.
     */
    private static String neunerfolgAus(String text, int von) {
        StringBuilder ziffern = new StringBuilder();
        int i = von;
        while (i < text.length() && istZiffer(text.charAt(i))) {
            while (i < text.length() && istZiffer(text.charAt(i))) {
                ziffern.append(asciiZiffer(text.charAt(i)));
                i++;
            }
            if (ziffern.length() == NEUN) {
                return ziffern.toString();
            }
            if (ziffern.length() > NEUN) {
                return null;
            }
            if (i + 1 < text.length() && istTrenner(text.charAt(i)) && istZiffer(text.charAt(i + 1))) {
                i++;
            } else {
                return null;
            }
        }
        return null;
    }

    /** Verlangt, dass der Text keinen Verstoss und keine KVNR-Form enthaelt. */
    public static void assertBsnrSynthetisch(String text) {
        assertBsnrSynthetisch(text, 0);
    }

    /**
     * Verlangt, dass der Text keinen Verstoss gegen die Neunerfolgen-Regel und genau {@code erlaubteKvnrFormen}
     * Treffer der engen KVNR-Form enthaelt (beabsichtigte KVNR-Funde des Tests).
     */
    public static void assertBsnrSynthetisch(String text, int erlaubteKvnrFormen) {
        Befund b = pruefe(text);
        assertTrue(b.verstoesse() == 0, "Neunerfolge ohne Praefix 00 und ohne 00000 an Stelle 3 bis 7 im Testtext: "
                + b.verstoesse());
        assertTrue(b.kvnrFormen() == erlaubteKvnrFormen, "Ungewollte KVNR-Form im Teil-2-Testtext: gefunden "
                + b.kvnrFormen() + ", erlaubt " + erlaubteKvnrFormen);
    }
}
