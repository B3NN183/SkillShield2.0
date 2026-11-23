package de.skillshield.core.recognizer.kv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.skillshield.core.SynthetischeKvNummern;

/**
 * Laufzeit-Wächter für synthetische KVNR (Feature kvnr-bsnr-erkennung, T-3, Anforderung A-6 und Hinweis H-1): In
 * einem Testtext hat jede Folge aus einem ASCII-Buchstaben, optional einem Trenner und neun Ziffern aus ganzen
 * Ziffernblöcken, die nach der unabhängigen Referenz {@link KvnrBsnrHilfen#kvnrPruefziffer} gültig ist, die Ziffern
 * {@code 0000} an den Stellen 2 bis 5 (also als erste vier der neun Ziffern). Einzige Ausnahme ist das Lehrbeispiel
 * der Quelle ({@link SynthetischeKvNummern#KVNR_LEHRBEISPIEL}) mit dem Buchstaben A. Trenner sind dieselben wie im
 * statischen Wächter, vollbreite Ziffern (U+FF10 bis U+FF19) zählen als Ziffern.
 *
 * <p>Der Wächter liegt in einer eigenen Klasse, weil {@link KvnrBsnrHilfen} seit T-1 eingefroren ist. Meldungen
 * nennen nur Zahlen, nie Text oder Ziffern. Die Fremdnummern der Fehlalarm-Prüfstände (später) mit ungültiger
 * Prüfziffer sind von der Regel nicht betroffen; gültige Fremdnummern müssen sie ebenfalls mit {@code 0000} bilden.
 */
public final class KvnrSynthetischWaechter {

    private static final int NEUN = 9;
    private static final String PRAEFIX = "0000";
    private static final char VOLLBREIT_NULL = (char) 0xFF10;
    private static final char VOLLBREIT_NEUN = (char) 0xFF19;

    /**
     * Ergebnis einer Prüfung.
     *
     * @param gueltigeFormen Zahl der Formen (Buchstabe, optional ein Trenner, neun Ziffern aus ganzen Blöcken), die
     *                       nach der Referenz gültig sind
     * @param verstoesse     davon die Zahl ohne die Ziffern 0000 an Stelle 2 bis 5 (ohne das Lehrbeispiel)
     */
    public record Befund(int gueltigeFormen, int verstoesse) {
    }

    private KvnrSynthetischWaechter() {
    }

    private static boolean istBuchstabe(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    /** ASCII-Ziffer oder vollbreite Ziffer (U+FF10 bis U+FF19). */
    private static boolean istZiffer(char c) {
        return (c >= '0' && c <= '9') || (c >= VOLLBREIT_NULL && c <= VOLLBREIT_NEUN);
    }

    /** Die Ziffer als ASCII-Ziffer; eine vollbreite Ziffer wird auf ihre ASCII-Entsprechung abgebildet. */
    private static char asciiZiffer(char c) {
        return c >= VOLLBREIT_NULL ? (char) ('0' + (c - VOLLBREIT_NULL)) : c;
    }

    /**
     * Trenner innerhalb einer Kette (D-1), dieselbe Menge wie im statischen Wächter: Leerzeichen, Tab, NBSP,
     * Bindestrich, Punkt, Schrägstrich, Unterstrich.
     */
    private static boolean istTrenner(char c) {
        return c == ' ' || c == '\t' || c == (char) 0xA0 || c == '-' || c == '.' || c == '/' || c == '_';
    }

    /** Prüft einen Text und zählt gültige Formen und Verstöße. */
    public static Befund pruefe(String text) {
        int gueltig = 0;
        int verstoesse = 0;
        String lehrbeispiel = SynthetischeKvNummern.KVNR_LEHRBEISPIEL.substring(1);
        for (int i = 0; i < text.length(); i++) {
            char buchstabe = text.charAt(i);
            if (!istBuchstabe(buchstabe)) {
                continue;
            }
            int j = i + 1;
            if (j + 1 < text.length() && istTrenner(text.charAt(j)) && istZiffer(text.charAt(j + 1))) {
                j++;
            }
            String neun = ersteNeunZiffernAusGanzenBloecken(text, j);
            if (neun == null) {
                continue;
            }
            char gross = Character.toUpperCase(buchstabe);
            int pruef = KvnrBsnrHilfen.kvnrPruefziffer(gross, neun.substring(0, NEUN - 1));
            if (pruef != neun.charAt(NEUN - 1) - '0') {
                continue;
            }
            gueltig++;
            boolean lehr = gross == 'A' && neun.equals(lehrbeispiel);
            if (!neun.startsWith(PRAEFIX) && !lehr) {
                verstoesse++;
            }
        }
        return new Befund(gueltig, verstoesse);
    }

    /**
     * Liest ab {@code von} Ziffernblöcke, je zwei Blöcke durch genau einen Trenner verbunden. Haben die ersten
     * Blöcke zusammen genau neun Ziffern, werden diese neun (als ASCII-Ziffern) zurückgegeben. Hat ein Block die
     * Summe über neun gebracht, bevor sie genau neun war, oder endet die Folge davor, ist das Ergebnis {@code null}.
     */
    private static String ersteNeunZiffernAusGanzenBloecken(String text, int von) {
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

    /** Verlangt, dass der Text keinen Verstoß enthält; die Meldung nennt nur die Zahl der Verstöße. */
    public static void assertKvnrSynthetisch(String text) {
        Befund b = pruefe(text);
        assertEquals(0, b.verstoesse(), "Gueltige KVNR-Form ohne die Ziffern 0000 an Stelle 2 bis 5 im Testtext");
    }
}
