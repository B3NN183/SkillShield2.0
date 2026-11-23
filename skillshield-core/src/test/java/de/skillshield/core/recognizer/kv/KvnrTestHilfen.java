package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.recognizer.kv.KvTestHilfen.NBSP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.List;

/**
 * Hilfen der KVNR-Tests (Feature kvnr-bsnr-erkennung, T-4). Die Ergaenzungen stehen hier und nicht in
 * {@link KvnrBsnrHilfen}, weil diese Klasse seit T-1 eingefroren ist. Alle Meldungen nennen weder Text noch
 * Nummern.
 */
final class KvnrTestHilfen {

    static final String QUELLE = "kvnr";
    static final String QUELLE_PZ = "kvnr-pruefziffer-ungueltig";
    static final String QUELLE_FORM = "kvnr-form-abweichend";
    static final double SICHER = 0.95;
    static final double PZ_UNGUELTIG = 0.50;
    static final double FORM = 0.40;

    /** Gruppentrenner einer Nummer (D-1): Leerzeichen, geschuetztes Leerzeichen, Bindestrich, Punkt, Schraegstrich. */
    static final String[] TRENNER = {" ", String.valueOf(NBSP), "-", ".", "/"};
    /** Feste Zerlegungen der neun Ziffern in Gruppen. */
    static final int[][] GRUPPEN = {{3, 3, 3}, {4, 5}, {1, 8}, {5, 4}, {2, 7}, {2, 2, 5}};

    private static final KvnrRecognizer ERKENNER = new KvnrRecognizer();
    private static final int NEUN = 9;
    private static final int MIN_ZIFFERN = 6;
    private static final int MAX_ZIFFERN = 12;

    private KvnrTestHilfen() {
    }

    /** Die Ziffern in Gruppen der angegebenen Groessen, je zwei Gruppen durch {@code trenner} verbunden. */
    static String gruppiert(String ziffern, int[] gruppen, String trenner) {
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        for (int i = 0; i < gruppen.length; i++) {
            if (i > 0) {
                sb.append(trenner);
            }
            sb.append(ziffern, pos, pos + gruppen[i]);
            pos += gruppen[i];
        }
        assertEquals(ziffern.length(), pos, "Gruppen ergeben nicht alle Ziffern");
        return sb.toString();
    }

    /**
     * Die erwartete Quelle, abgeleitet aus der Nummer (Regel 9, nicht aus der Konfidenz): Buchstabe und neun Ziffern
     * mit Pruefziffer nach der unabhaengigen Referenz {@link KvnrBsnrHilfen#kvnrPruefziffer} gueltig ergibt
     * {@code kvnr}, mit falscher Pruefziffer {@code kvnr-pruefziffer-ungueltig}, jede andere Form
     * {@code kvnr-form-abweichend}. Trenner in der Nummer werden uebergangen.
     */
    static String quelleNachNummerKvnr(String nummer) {
        char buchstabe = 0;
        StringBuilder ziffern = new StringBuilder();
        for (int i = 0; i < nummer.length(); i++) {
            char c = nummer.charAt(i);
            if (c >= '0' && c <= '9') {
                ziffern.append(c);
            } else if (i == 0 && ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) {
                buchstabe = Character.toUpperCase(c);
            }
        }
        if (ziffern.length() == NEUN && buchstabe != 0) {
            int soll = KvnrBsnrHilfen.kvnrPruefziffer(buchstabe, ziffern.substring(0, NEUN - 1));
            return soll == ziffern.charAt(NEUN - 1) - '0' ? QUELLE : QUELLE_PZ;
        }
        return QUELLE_FORM;
    }

    /** Die erwartete Konfidenz bei direktem Kontext, abgeleitet aus der Quelle. */
    static double direkteKonfidenz(String quelle) {
        return switch (quelle) {
            case QUELLE -> SICHER;
            case QUELLE_PZ -> PZ_UNGUELTIG;
            default -> FORM;
        };
    }

    /**
     * Scant mit dem {@link KvnrRecognizer}. Vorher muss der Text den Laufzeit-Waechter
     * {@link KvnrSynthetischWaechter#assertKvnrSynthetisch} bestehen; danach gelten die Invarianten: Typ KVNR,
     * Teilstring, sortiert, ueberlappungsfrei, Quelle und Konfidenz passend zur Nummer des Fundes (Konfidenz
     * hoechstens die des direkten Kontexts).
     *
     * <p><b>Warnhinweis:</b> Der Laufzeit-Waechter wertet jeden einzelnen Buchstaben, einen optionalen Trenner und
     * neun Ziffern als KVNR-Form, also auch den Endbuchstaben eines Kontextworts vor einer reinen LANR-, IK- oder
     * Laengen-Ziffernfolge. Ergibt das zufaellig eine gueltige Pruefziffer ohne {@code 0000} an Stelle 2 bis 5,
     * schlaegt der Waechter an (rund 10 Prozent); das ist kein Produktfehler. Dann ein anderes Kontextwort oder
     * eine andere Konstante waehlen.
     */
    static List<Finding> scanKvnr(String text) {
        KvnrSynthetischWaechter.assertKvnrSynthetisch(text);
        List<Finding> funde = ERKENNER.detect(text);
        assertNotNull(funde, "Fundliste fehlt");
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertTrue(text.substring(f.start(), f.end()).equals(f.originalValue()), "Teilstring-Invariante");
            assertEquals(FindingType.KVNR, f.type(), "Typ");
            assertTrue(f.start() >= vorigesEnde, "Funde ueberlappen oder sind nicht sortiert");
            vorigesEnde = f.end();
            String quelle = quelleNachNummerKvnr(f.originalValue());
            assertTrue(quelle.equals(f.source()), "Quelle passt nicht zur Nummer");
            assertTrue(f.confidence() <= direkteKonfidenz(quelle), "Konfidenz ueber der Stufe der Quelle");
            int ziffern = 0;
            for (int i = 0; i < f.originalValue().length(); i++) {
                char c = f.originalValue().charAt(i);
                ziffern += c >= '0' && c <= '9' ? 1 : 0;
            }
            assertTrue(ziffern >= MIN_ZIFFERN && ziffern <= MAX_ZIFFERN, "Ziffernzahl ausserhalb 6 bis 12");
        }
        return funde;
    }
}
