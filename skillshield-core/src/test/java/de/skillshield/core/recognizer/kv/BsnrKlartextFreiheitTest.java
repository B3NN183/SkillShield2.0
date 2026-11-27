package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.BSNR_MARKER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Klartextfreiheit der BSNR (Feature kvnr-bsnr-erkennung, Task T-14; AK-35). Die Klasse ist neu, weil die bestehenden
 * Klartextklassen je Art gebaut sind ({@code KlartextFreiheitTest}: LANR und IK; {@code KvnrKlartextFreiheitTest}:
 * KVNR) und {@code KlartextFreiheitTest} ueber eine feste Markerliste laeuft. Sie prueft die BSNR-Funde aller vier
 * Quellen ({@code bsnr}, {@code bsnr-regionalcode-unbekannt}, {@code bsnr-laenge-abweichend}, {@code bsnr-rest}) mit
 * der Marker-Nummer {@code BSNR_MARKER}: Weder {@code toString()} der Funde, der Fundliste, des Ergebnisses und der
 * Zuordnung noch der pseudonymisierte Text noch die Meldungen der Ausnahmen enthalten die Nummer, ihre Ziffern oder
 * Teile davon. Ausserdem die Lage des BSNR-Erkenners in {@code TextScanner.standard()}.
 *
 * <p>Die Meldungen dieser Tests nennen weder Text noch Ziffern.
 */
class BsnrKlartextFreiheitTest {

    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();
    private static final String MARKER = BSNR_MARKER;
    private static final double REST_HOECHSTENS = 0.80;

    /** Marker mit Regionalcode 00 (unbekannt): erste zwei Ziffern ersetzt, der Rest bleibt. */
    private static String markerUnbekannt() {
        return "00" + MARKER.substring(2);
    }

    /** Marker mit einer zehnten Ziffer (Laenge abweichend). */
    private static String markerZehnstellig() {
        return MARKER + "1";
    }

    /** Teile des Markers, die in keiner Ausgabe vorkommen duerfen: ganze Nummer, sechs Ziffern vorn und hinten. */
    private static List<String> verboteneTeile() {
        return List.of(MARKER, MARKER.substring(0, 6), MARKER.substring(3), markerUnbekannt(), markerZehnstellig());
    }

    /** Ein Text je Quelle: Quelle des erwarteten Fundes und Text. */
    private static List<String[]> texteJeQuelle() {
        String rest = "BSNR " + MARKER.substring(0, 3) + " " + MARKER.substring(3, 6) + "." + MARKER.substring(6)
                + "@example.invalid";
        return List.of(
                new String[] {"bsnr", "BSNR " + MARKER + " Ende"},
                new String[] {"bsnr-regionalcode-unbekannt", "BSNR " + markerUnbekannt() + " Ende"},
                new String[] {"bsnr-laenge-abweichend", "BSNR " + markerZehnstellig() + " Ende"},
                new String[] {"bsnr-rest", rest});
    }

    private static void assertOhneMarker(List<String> ausgaben, String fall) {
        for (String ausgabe : ausgaben) {
            for (String teil : verboteneTeile()) {
                assertFalse(ausgabe.contains(teil), "Marker oder Teil davon in einer Ausgabe, Fall " + fall);
            }
        }
    }

    // ------------------------------------------------------------------ Funde aller Quellen

    @Test
    void ak35_bsnrFundeAllerVierQuellen_keinMarkerInToStringErgebnisZuordnungUndText() {
        for (String[] fall : texteJeQuelle()) {
            String quelle = fall[0];
            String text = fall[1];
            BsnrSynthetischWaechter.assertBsnrSynthetisch(text);
            List<Finding> funde = TextScanner.standard().scan(text);
            boolean gefunden = false;
            for (Finding f : funde) {
                gefunden |= quelle.equals(f.source());
            }
            assertTrue(gefunden, "Es gibt keinen Fund der Quelle " + quelle);
            PseudonymizationResult ersetzt = PSEUDONYMIZER.pseudonymize(text, funde);
            PseudonymizationResult ohneUebergabe = PSEUDONYMIZER.pseudonymize(text, List.of());
            ReidentificationResult zurueck = REIDENTIFIER.restore(ersetzt.text(), ersetzt.mapping());
            ReidentificationResult leer = REIDENTIFIER.restore(ersetzt.text(), ohneUebergabe.mapping());
            List<String> ausgaben = new ArrayList<>(List.of(funde.toString(), ersetzt.toString(),
                    ersetzt.mapping().toString(), ersetzt.text(), ohneUebergabe.mapping().toString(),
                    zurueck.toString(), leer.toString(), zurueck.unresolved().toString(),
                    leer.unresolved().toString()));
            for (Finding f : funde) {
                ausgaben.add(f.toString());
                assertFalse(f.source().chars().anyMatch(Character::isDigit), "Ziffer in der Quelle, Quelle " + quelle);
            }
            assertOhneMarker(ausgaben, quelle);
            assertTrue(text.equals(zurueck.text()), "Roundtrip nicht zeichengleich, Quelle " + quelle);
            assertTrue(text.equals(ohneUebergabe.text()), "Text ohne Uebergabe veraendert, Quelle " + quelle);
        }
    }

    /** Der Restfund {@code bsnr-rest} gehoert nur dem {@code TextScanner} (Zuschneiden), hoechstens 0,80. */
    @Test
    void ak35_bsnrRestfund_quelleBsnrRest_istEinFundDesTextScannersMitHoechstensNullAchtzig() {
        String text = texteJeQuelle().get(3)[1];
        int rest = 0;
        for (Finding f : TextScanner.standard().scan(text)) {
            if ("bsnr-rest".equals(f.source())) {
                rest++;
                assertTrue(f.confidence() <= REST_HOECHSTENS, "Konfidenz des Restfundes");
                assertFalse(f.toString().contains(MARKER.substring(0, 3) + MARKER.substring(3, 6)), "Wert in toString");
            }
        }
        assertEquals(1, rest, "Zahl der Restfunde");
        for (Finding f : new BsnrRecognizer().detect(text)) {
            assertFalse("bsnr-rest".equals(f.source()), "Restfund auf Erkennerebene");
        }
    }

    // ------------------------------------------------------------------ Ausnahmen

    @Test
    void ak35_ausnahmen_ohneMarkerInMeldungUrsacheUndUnterdrueckten() {
        NullPointerException npe = assertThrows(NullPointerException.class, () -> new BsnrRecognizer().detect(null));
        assertTrue("text".equals(npe.getMessage()), "Meldung bei null");
        // ein Erkenner, der den Marker in seine Ausnahme schreibt: der Scanner kapselt klartextfrei
        Recognizer wirft = t -> {
            throw new IllegalArgumentException(MARKER);
        };
        for (String[] fall : texteJeQuelle()) {
            assertFalse(new BsnrRecognizer().detect(fall[1]).isEmpty(), "Der BSNR-Erkenner findet die Nummer");
            TextScanner scanner = new TextScanner(List.of(new BsnrRecognizer(), wirft));
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> scanner.scan(fall[1]));
            assertOhneMarker(List.of(String.valueOf(e.getMessage()), e.toString()), fall[0]);
            assertNull(e.getCause(), "Ursache");
            assertEquals(0, e.getSuppressed().length, "Unterdrueckte Ausnahmen");
        }
        // Regionalcode-Pruefung: feste Meldung ohne Eingabewerte und ohne Marker
        IllegalArgumentException zuGross = assertThrows(IllegalArgumentException.class,
                () -> BsnrRegionalcodes.istBekannt(MARKER.length() + 1, 0));
        IllegalArgumentException negativ = assertThrows(IllegalArgumentException.class,
                () -> BsnrRegionalcodes.istBekannt(0, -1));
        for (IllegalArgumentException e : List.of(zuGross, negativ)) {
            assertOhneMarker(List.of(String.valueOf(e.getMessage()), e.toString()), "Regionalcode");
            assertNull(e.getCause(), "Ursache");
        }
    }

    // ------------------------------------------------------------------ Lage in der Standardliste

    /**
     * Der BSNR-Erkenner steht an Index 7 von {@code standard()} (nach der KVNR), genau einmal. Seit
     * geheimnisse-erkennung T-4 folgt der Passwort-Erkenner an Index 8; der Name des Tests bleibt unveraendert.
     */
    @Test
    void abgleich_bsnrErkennerSteht_alsLetzterInDerStandardliste() {
        List<Recognizer> liste = TextScanner.standard().recognizers();
        // Umgestellt durch geheimnisse-erkennung T-4: Zahl der Standard-Erkenner 9 (alter Wert: 8); Index 7
        // bleibt der BSNR-Erkenner
        // Umgestellt durch geheimnisse-erkennung T-6: Zahl der Standard-Erkenner 10 (alter Wert: 9); Index 7
        // bleibt der BSNR-Erkenner
        assertEquals(10, liste.size(), "Zahl der Standard-Erkenner");
        assertTrue(liste.get(7) instanceof BsnrRecognizer, "Index 7 ist der BSNR-Erkenner");
        assertTrue(liste.get(6) instanceof KvnrRecognizer, "Index 6 ist der KVNR-Erkenner");
        assertEquals(1, liste.stream().filter(r -> r instanceof BsnrRecognizer).count(), "Zahl der BSNR-Erkenner");
    }
}
