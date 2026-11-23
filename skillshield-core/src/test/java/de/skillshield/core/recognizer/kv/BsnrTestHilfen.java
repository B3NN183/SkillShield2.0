package de.skillshield.core.recognizer.kv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.List;

/**
 * Hilfen und unabhaengige Referenz der BSNR-Tests (Feature kvnr-bsnr-erkennung, T-13). Die Klasse ist neu, weil
 * {@link KvnrBsnrHilfen} seit T-1 eingefroren ist. Die Referenz der Regionalcodes ({@link #bekanntReferenz}) ist aus
 * der Anforderung (Nachtrag der Hauptsitzung vom 23.11.2026, Anlage 1 der KBV-Richtlinie) als Bereiche geschrieben und
 * ruft {@link BsnrRegionalcodes} nicht auf. Alle Meldungen nennen weder Text noch Nummern.
 */
final class BsnrTestHilfen {

    static final String QUELLE = "bsnr";
    static final String QUELLE_REGIONALCODE = "bsnr-regionalcode-unbekannt";
    static final String QUELLE_LAENGE = "bsnr-laenge-abweichend";
    static final double SICHER = 0.90;
    static final double WEITER = 0.80;
    static final double KOMMA = 0.60;
    static final double REGIONALCODE_UNBEKANNT = 0.50;
    static final double LAENGE = 0.40;

    private static final BsnrRecognizer ERKENNER = new BsnrRecognizer();
    private static final int NEUN = 9;
    private static final int MIN_ZIFFERN = 6;
    private static final int MAX_ZIFFERN = 12;
    private static final int SONDER_35 = 35;
    private static final int SONDER_75 = 75;

    private BsnrTestHilfen() {
    }

    /**
     * Ist die Zahl aus zwei Ziffern ein Schluessel der Anlage 1 (78 Werte): 01 bis 03, 06 bis 21, 24, 25, 27, 28, 31,
     * 37 bis 73, 78 bis 81, 83, 85 bis 91, 93 bis 96, 98? Die Sonderkennungen 35 und 75 und alles andere sind
     * {@code false}.
     */
    static boolean schluesselReferenz(int zahl) {
        return (zahl >= 1 && zahl <= 3) || (zahl >= 6 && zahl <= 21) || zahl == 24 || zahl == 25 || zahl == 27
                || zahl == 28 || zahl == 31 || (zahl >= 37 && zahl <= 73) || (zahl >= 78 && zahl <= 81) || zahl == 83
                || (zahl >= 85 && zahl <= 91) || (zahl >= 93 && zahl <= 96) || zahl == 98;
    }

    /** Zahl der Schluessel nach der Referenz (78 laut Anlage 1). */
    static int schluesselAnzahl() {
        int anzahl = 0;
        for (int i = 0; i < 100; i++) {
            anzahl += schluesselReferenz(i) ? 1 : 0;
        }
        return anzahl;
    }

    /**
     * Hat die Folge aus neun Ziffern einen bekannten Regionalcode? Schluessel der Anlage 1 an Stelle 1 und 2; bei 35
     * und 75 an Stelle 3 und 4 (Paragraph 6 Absatz 3).
     */
    static boolean bekanntReferenz(String neunZiffern) {
        assertEquals(NEUN, neunZiffern.length(), "Referenz erwartet neun Ziffern");
        int erste = Integer.parseInt(neunZiffern.substring(0, 2));
        if (erste == SONDER_35 || erste == SONDER_75) {
            return schluesselReferenz(Integer.parseInt(neunZiffern.substring(2, 4)));
        }
        return schluesselReferenz(erste);
    }

    /** Die Ziffern einer Nummer ohne Trenner. */
    static String nurZiffern(String nummer) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nummer.length(); i++) {
            char c = nummer.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Die erwartete Quelle, abgeleitet aus der Nummer (Regel 9, nicht aus der Konfidenz): neun Ziffern mit bekanntem
     * Regionalcode ergeben {@code bsnr}, mit unbekanntem {@code bsnr-regionalcode-unbekannt}, jede andere Laenge
     * {@code bsnr-laenge-abweichend}. Trenner werden uebergangen.
     */
    static String quelleNachNummerBsnr(String nummer) {
        String ziffern = nurZiffern(nummer);
        if (ziffern.length() == NEUN) {
            return bekanntReferenz(ziffern) ? QUELLE : QUELLE_REGIONALCODE;
        }
        return QUELLE_LAENGE;
    }

    /** Die erwartete Konfidenz bei direktem Kontext, abgeleitet aus der Quelle. */
    static double direkteKonfidenz(String quelle) {
        return switch (quelle) {
            case QUELLE -> SICHER;
            case QUELLE_REGIONALCODE -> REGIONALCODE_UNBEKANNT;
            default -> LAENGE;
        };
    }

    /**
     * Scant mit dem {@link BsnrRecognizer}. Vorher muss der Text den Laufzeit-Waechter
     * {@link BsnrSynthetischWaechter#assertBsnrSynthetisch} bestehen; danach gelten die Invarianten: Typ BSNR,
     * Teilstring, sortiert, ueberlappungsfrei, Quelle und Konfidenz passend zur Nummer des Fundes (Konfidenz hoechstens
     * die des direkten Kontexts), Ziffernzahl 6 bis 12.
     */
    static List<Finding> scanBsnr(String text) {
        BsnrSynthetischWaechter.assertBsnrSynthetisch(text);
        return pruefeFunde(text, ERKENNER.detect(text));
    }

    private static List<Finding> pruefeFunde(String text, List<Finding> funde) {
        assertNotNull(funde, "Fundliste fehlt");
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertTrue(text.substring(f.start(), f.end()).equals(f.originalValue()), "Teilstring-Invariante");
            assertEquals(FindingType.BSNR, f.type(), "Typ");
            assertTrue(f.start() >= vorigesEnde, "Funde ueberlappen oder sind nicht sortiert");
            vorigesEnde = f.end();
            String quelle = quelleNachNummerBsnr(f.originalValue());
            assertTrue(quelle.equals(f.source()), "Quelle passt nicht zur Nummer");
            assertTrue(f.confidence() <= direkteKonfidenz(quelle), "Konfidenz ueber der Stufe der Quelle");
            int ziffern = nurZiffern(f.originalValue()).length();
            assertTrue(ziffern >= MIN_ZIFFERN && ziffern <= MAX_ZIFFERN, "Ziffernzahl ausserhalb 6 bis 12");
        }
        return funde;
    }
}
