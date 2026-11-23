package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.KVNR_MARKER;
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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Klartextfreiheit und Architektur der KVNR (Feature kvnr-bsnr-erkennung, Task T-8; AK-35). Erweitert die Pruefung
 * auf die KVNR-Funde aller vier Quellen ({@code kvnr}, {@code kvnr-pruefziffer-ungueltig},
 * {@code kvnr-form-abweichend} und {@code kvnr-rest}) mit der Marker-Nummer {@code KVNR_MARKER}: Weder
 * {@code toString()} der Funde, der Fundliste, des Ergebnisses und der Zuordnung noch der pseudonymisierte Text noch
 * die Meldungen der Ausnahmen enthalten die Nummer, ihre Ziffern oder Teile davon. Ausserdem der Abgleich der
 * Erkennerlisten der Schutzgarantie- und Grenzlasttests mit {@code TextScanner.standard()}. Kein Netzwerk-Code im
 * Produkt: {@code ArchitekturRegelnTest} liest alle Produktdateien, die KVNR-Klassen stehen dort in der Liste der
 * bekannten Dateien.
 *
 * <p>Die Meldungen dieser Tests nennen weder Text noch Ziffern.
 */
class KvnrKlartextFreiheitTest {

    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();
    private static final String MARKER = KVNR_MARKER;
    private static final String BUCHSTABE = MARKER.substring(0, 1);
    private static final String ZIFFERN = MARKER.substring(1);
    private static final Path TESTQUELLE = Path.of("src", "test", "java", "de", "skillshield", "core");

    /** Die Marker-Nummer mit geaenderter letzter Ziffer: falsche Pruefziffer. */
    private static String markerUngueltig() {
        char letzte = MARKER.charAt(MARKER.length() - 1);
        char andere = letzte == '0' ? '1' : (char) (letzte - 1);
        return MARKER.substring(0, MARKER.length() - 1) + andere;
    }

    /** Teile des Markers, die in keiner Ausgabe vorkommen duerfen: ganze Nummer, Ziffern, sechs Ziffern vorn/hinten. */
    private static List<String> verboteneTeile() {
        return List.of(MARKER, ZIFFERN, ZIFFERN.substring(0, 6), ZIFFERN.substring(3), markerUngueltig());
    }

    /** Ein Text je Quelle: Quelle des erwarteten Fundes und Text. */
    private static List<String[]> texteJeQuelle() {
        String rest = "KVNR " + BUCHSTABE + ZIFFERN.substring(0, 3) + " " + ZIFFERN.substring(3, 6) + "."
                + ZIFFERN.substring(6) + "@example.invalid";
        return List.of(
                new String[] {"kvnr", "KVNR " + MARKER + " Ende"},
                new String[] {"kvnr-pruefziffer-ungueltig", "KVNR " + markerUngueltig() + " Ende"},
                new String[] {"kvnr-form-abweichend", "KVNR " + MARKER.substring(0, MARKER.length() - 1) + " Ende"},
                new String[] {"kvnr-rest", rest});
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
    void ak35_kvnrFundeAllerVierQuellen_keinMarkerInToStringErgebnisZuordnungUndText() {
        for (String[] fall : texteJeQuelle()) {
            String quelle = fall[0];
            String text = fall[1];
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
                    ersetzt.mapping().toString(), ersetzt.text(), ohneUebergabe.toString(),
                    ohneUebergabe.mapping().toString(), zurueck.toString(), leer.toString(),
                    zurueck.unresolved().toString(), leer.unresolved().toString()));
            for (Finding f : funde) {
                ausgaben.add(f.toString());
            }
            assertOhneMarker(ausgaben, quelle);
            assertTrue(text.equals(zurueck.text()), "Roundtrip nicht zeichengleich, Quelle " + quelle);
            assertTrue(text.equals(ohneUebergabe.text()), "Text ohne Uebergabe veraendert, Quelle " + quelle);
        }
    }

    @Test
    void ak35_kvnrRestfund_quelleKvnrRest_istEinFundDesTextScannersMitHoechstensNullAchtzig() {
        String text = texteJeQuelle().get(3)[1];
        List<Finding> funde = TextScanner.standard().scan(text);
        int rest = 0;
        for (Finding f : funde) {
            if ("kvnr-rest".equals(f.source())) {
                rest++;
                assertTrue(f.confidence() <= 0.80, "Konfidenz des Restfundes");
                assertFalse(f.toString().contains(BUCHSTABE + ZIFFERN.substring(0, 3)), "Wert in toString");
            }
        }
        assertEquals(1, rest, "Zahl der Restfunde");
        // der KVNR-Erkenner allein liefert keinen Restfund: die Quelle gibt es nur im TextScanner
        for (Finding f : new KvnrRecognizer().detect(text)) {
            assertFalse("kvnr-rest".equals(f.source()), "Restfund auf Erkennerebene");
        }
    }

    // ------------------------------------------------------------------ Ausnahmen

    @Test
    void ak35_ausnahmen_ohneMarkerInMeldungUrsacheUndUnterdrueckten() {
        // null: feste Meldung "text"
        NullPointerException npe = assertThrows(NullPointerException.class, () -> new KvnrRecognizer().detect(null));
        assertTrue("text".equals(npe.getMessage()), "Meldung bei null");
        // ein Erkenner, der den Marker in seine Ausnahme schreibt: der Scanner kapselt klartextfrei
        Recognizer wirft = t -> {
            throw new IllegalArgumentException(MARKER);
        };
        for (String[] fall : texteJeQuelle()) {
            TextScanner scanner = new TextScanner(List.of(new KvnrRecognizer(), wirft));
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> scanner.scan(fall[1]));
            assertOhneMarker(List.of(String.valueOf(e.getMessage()), e.toString()), fall[0]);
            assertNull(e.getCause(), "Ursache");
            assertEquals(0, e.getSuppressed().length, "Unterdrueckte Ausnahmen");
        }
        // Pruefziffernverfahren und Art: feste Meldung ohne Werte der Eingabe
        int[] ziffern = new int[ZIFFERN.length()];
        for (int i = 0; i < ziffern.length; i++) {
            ziffern[i] = ZIFFERN.charAt(i) - '0';
        }
        int[] zuKurz = Arrays.copyOf(ziffern, ziffern.length - 1);
        IllegalArgumentException laenge = assertThrows(IllegalArgumentException.class,
                () -> KvnrPruefziffer.istGueltig(BUCHSTABE.charAt(0), zuKurz));
        IllegalArgumentException buchstabe = assertThrows(IllegalArgumentException.class,
                () -> KvnrPruefziffer.istGueltig((char) 0xE4, ziffern));
        IllegalArgumentException ohneBuchstabe = assertThrows(IllegalArgumentException.class,
                () -> KvArt.KVNR.isValid(ziffern));
        for (IllegalArgumentException e : List.of(laenge, buchstabe, ohneBuchstabe)) {
            String meldung = String.valueOf(e.getMessage());
            assertOhneMarker(List.of(meldung, e.toString()), "Pruefziffernverfahren");
            assertFalse(meldung.chars().anyMatch(Character::isDigit), "Ziffer in der Meldung");
            assertNull(e.getCause(), "Ursache");
        }
    }

    // ------------------------------------------------------------------ Abgleich der Erkennerlisten

    private static String lies(String datei) {
        try {
            return Files.readString(TESTQUELLE.resolve(datei), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Testquelle nicht lesbar: " + datei, e);
        }
    }

    private static int indexVon(Class<? extends Recognizer> klasse) {
        List<Recognizer> liste = TextScanner.standard().recognizers();
        for (int i = 0; i < liste.size(); i++) {
            if (klasse.isInstance(liste.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Die Erkennerlisten der Schutzgarantie- und Grenzlasttests kommen aus {@code TextScanner.standard()} und koennen
     * deshalb nicht von der Standardliste abweichen; die Positionen von LANR, IK und KVNR in {@code RoundtripFuzzTest}
     * entsprechen der Standardliste; der KVNR-Erkenner ist enthalten.
     */
    @Test
    void abgleich_erkennerlistenDerSchutzgarantieUndGrenzlasttests_entsprechenTextScannerStandard() {
        assertTrue(indexVon(KvnrRecognizer.class) >= 0, "KVNR-Erkenner fehlt in standard()");
        for (String datei : List.of("GrenzlastTest.java", "RoundtripFuzzTest.java")) {
            assertTrue(lies(datei).contains("TextScanner.standard().recognizers()"),
                    "Erkennerliste von " + datei + " stammt nicht aus TextScanner.standard()");
        }
        String fuzz = lies("RoundtripFuzzTest.java");
        assertEquals(indexVon(LanrRecognizer.class), konstante(fuzz, "ERKENNER_LANR"), "Position LANR");
        assertEquals(indexVon(IkRecognizer.class), konstante(fuzz, "ERKENNER_IK"), "Position IK");
        assertEquals(indexVon(KvnrRecognizer.class), konstante(fuzz, "ERKENNER_KVNR"), "Position KVNR");
    }

    private static int konstante(String quelltext, String name) {
        Matcher m = Pattern.compile(name + "\\s*=\\s*(\\d+)\\s*;").matcher(quelltext);
        assertTrue(m.find(), "Konstante nicht gefunden: " + name);
        return Integer.parseInt(m.group(1));
    }
}
