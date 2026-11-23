package de.skillshield.core.recognizer;

import static de.skillshield.core.SynthetischeKvNummern.IK_FORM_DOKU_IP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.kv.KvTestHilfen;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Direkttests des Kuerzens der Raender beim Zuschneiden ({@code TextScanner.zuschneiden}, Feature
 * kvnr-bsnr-erkennung, Task T-8; Nachtrag 3 zu T-6 Punkt 1 und Nachtrag 5 zu T-6 Punkt 4): Jedes Reststueck wird an
 * beiden Enden auf ASCII-Buchstaben und -Ziffern gekuerzt (Trenner, Leerraum, Satzzeichen, Nicht-ASCII-Zeichen und
 * unsichtbare Zeichen am Rand entfallen) und bleibt nur mit mindestens drei ASCII-Ziffern ein Fund. Im Inneren eines
 * Reststuecks wird nichts gekuerzt, und nur ASCII-Ziffern zaehlen als Ziffern. Dazu ein Lasttest mit einem Kandidaten
 * mit sehr vielen Schnitten und mit sehr vielen Kandidaten (Aufwand O(n log n), Grenze 2 s, exakte Zahl).
 *
 * <p>Die Ziffernfolgen sind kurz (drei Ziffern) und frei gewaehlt; es kommt keine Nummer mit Buchstabe und neun
 * Ziffern vor. Wertvergleiche mit Text laufen ueber {@code assertTrue(equals)} mit fester Meldung. Nicht-ASCII-Zeichen
 * stehen nur als {@code (char) 0x...} bzw. ueber {@code Character.toChars}.
 */
class TextScannerKvnrRaenderTest {

    private static final String QUELLE_REST = "kvnr-rest";
    private static final String DREI_VORN = "123";
    private static final String DREI_HINTEN = "456";
    private static final double KONFIDENZ = 0.40;
    private static final Duration GRENZE = Duration.ofSeconds(2);

    // ------------------------------------------------------------------ Hilfen

    /** Der Kandidat umfasst den ganzen Text; der Bestandsfund (IP) liegt in {@code [von, bis)}. */
    private static List<Finding> schneide(String text, int von, int bis) {
        Finding kandidat = new Finding(FindingType.KVNR, text, 0, text.length(), KONFIDENZ, "kvnr-form-abweichend");
        Finding bestand = new Finding(FindingType.IP, text.substring(von, bis), von, bis, 0.85, "ipv4");
        return TextScanner.zuschneiden(List.of(kandidat), QUELLE_REST, List.of(bestand));
    }

    private static void assertRest(Finding f, String wert, int start, double konfidenz) {
        assertEquals(FindingType.KVNR, f.type(), "Typ");
        assertTrue(QUELLE_REST.equals(f.source()), "Quelle");
        assertTrue(wert.equals(f.originalValue()), "Wert");
        assertEquals(start, f.start(), "Start");
        assertEquals(start + wert.length(), f.end(), "Ende");
        assertEquals(konfidenz, f.confidence(), "Konfidenz");
    }

    private static String zeichen(int codepunkt) {
        return new String(Character.toChars(codepunkt));
    }

    /** Randzeichen, die beim Kuerzen entfallen muessen: keine ASCII-Buchstaben und keine ASCII-Ziffern. */
    private static List<String> randzeichen() {
        List<String> rand = new ArrayList<>(List.of(" ", "-", ".", ",", "/", ":", "(", "\t", "\n",
                String.valueOf((char) 0xA0),
                // Nicht-ASCII-Ziffern: arabisch-indische Drei, vollbreite Drei
                String.valueOf((char) 0x663), String.valueOf((char) 0xFF13),
                // Nicht-ASCII-Buchstaben
                String.valueOf((char) 0xE4), String.valueOf((char) 0x3A9),
                // Surrogatpaar (ein Codepunkt ausserhalb der Grundebene, zwei Zeichen)
                zeichen(0x1F600), zeichen(0xE0100)));
        for (String uz : KvTestHilfen.uzAk1Texte()) {
            rand.add(uz);
        }
        return rand;
    }

    // ------------------------------------------------------------------ Kuerzen der Raender

    @Test
    void zuschneiden_rand_nichtAsciiZeichenUndTrennerAmRandEntfallen_beideSeiten() {
        for (String rand : randzeichen()) {
            // Aufbau: rand 123 rand IP rand 456 rand; der Bestandsfund liegt in der Mitte
            String vorn = rand + DREI_VORN + rand;
            String hinten = rand + DREI_HINTEN + rand;
            String text = vorn + IK_FORM_DOKU_IP + hinten;
            List<Finding> reste = schneide(text, vorn.length(), vorn.length() + IK_FORM_DOKU_IP.length());
            assertEquals(2, reste.size(), "Zahl der Reststuecke, Randlaenge " + rand.length());
            assertRest(reste.get(0), DREI_VORN, rand.length(), KONFIDENZ);
            assertRest(reste.get(1), DREI_HINTEN, vorn.length() + IK_FORM_DOKU_IP.length() + rand.length(),
                    KONFIDENZ);
        }
    }

    @Test
    void zuschneiden_rand_asciiBuchstabeAmRandBleibtImReststueck() {
        String vorn = "Z" + DREI_VORN + " ";
        String hinten = " " + DREI_HINTEN + "A";
        String text = vorn + IK_FORM_DOKU_IP + hinten;
        List<Finding> reste = schneide(text, vorn.length(), vorn.length() + IK_FORM_DOKU_IP.length());
        assertEquals(2, reste.size(), "Zahl der Reststuecke");
        assertRest(reste.get(0), "Z" + DREI_VORN, 0, KONFIDENZ);
        assertRest(reste.get(1), DREI_HINTEN + "A", vorn.length() + IK_FORM_DOKU_IP.length() + 1, KONFIDENZ);
    }

    @Test
    void zuschneiden_nichtAsciiZiffernZaehlenNichtAlsZiffern() {
        String arabisch = String.valueOf((char) 0x663);
        String vollbreit = String.valueOf((char) 0xFF13);
        for (String ziffer : List.of(arabisch, vollbreit)) {
            // zwei ASCII-Ziffern und eine Nicht-ASCII-Ziffer: nur zwei Ziffern zaehlen, das Reststueck entfaellt
            String vorn = "12" + ziffer + " ";
            String text = vorn + IK_FORM_DOKU_IP;
            assertEquals(0, schneide(text, vorn.length(), text.length()).size(), "Reststuecke mit zwei ASCII-Ziffern");
            // drei ASCII-Ziffern, die Nicht-ASCII-Ziffer im Inneren bleibt unveraendert Teil des Reststuecks
            String drei = "1" + ziffer + "23";
            String mitInnerer = drei + " " + IK_FORM_DOKU_IP;
            List<Finding> reste = schneide(mitInnerer, drei.length() + 1, mitInnerer.length());
            assertEquals(1, reste.size(), "Reststuecke mit drei ASCII-Ziffern");
            assertRest(reste.get(0), drei, 0, KONFIDENZ);
        }
    }

    @Test
    void zuschneiden_surrogatpaarImInnerenBleibtGanzUndAmRandWirdEsGanzGekuerzt() {
        String emoji = zeichen(0x1F600);
        // im Inneren: drei ASCII-Ziffern mit dem Paar dazwischen, das Paar bleibt unzerteilt
        String innen = "1" + emoji + "23";
        String text = innen + " " + IK_FORM_DOKU_IP;
        List<Finding> reste = schneide(text, innen.length() + 1, text.length());
        assertEquals(1, reste.size(), "Zahl der Reststuecke");
        assertRest(reste.get(0), innen, 0, KONFIDENZ);
        // am Rand: beide Haelften des Paars entfallen, Start liegt hinter dem ganzen Paar
        String vorn = emoji + DREI_VORN;
        String amRand = vorn + emoji + IK_FORM_DOKU_IP;
        List<Finding> randReste = schneide(amRand, vorn.length() + emoji.length(), amRand.length());
        assertEquals(1, randReste.size(), "Zahl der Reststuecke am Rand");
        assertRest(randReste.get(0), DREI_VORN, emoji.length(), KONFIDENZ);
        // nur das Paar und zwei Ziffern: entfaellt
        String zwei = emoji + "12" + emoji + IK_FORM_DOKU_IP;
        assertEquals(0, schneide(zwei, zwei.length() - IK_FORM_DOKU_IP.length(), zwei.length()).size(),
                "Reststuecke mit zwei Ziffern");
    }

    @Test
    void zuschneiden_unsichtbaresZeichenImInnerenBleibtUndAmRandWirdGekuerzt() {
        for (String uz : KvTestHilfen.uzAk1Texte()) {
            String innen = "12" + uz + "3";
            String text = uz + innen + uz + IK_FORM_DOKU_IP;
            List<Finding> reste = schneide(text, text.length() - IK_FORM_DOKU_IP.length(), text.length());
            assertEquals(1, reste.size(), "Zahl der Reststuecke");
            assertRest(reste.get(0), innen, uz.length(), KONFIDENZ);
            // nur zwei Ziffern mit unsichtbarem Zeichen dazwischen: keine drei Ziffern, kein Fund
            String zwei = "1" + uz + "2" + uz + IK_FORM_DOKU_IP;
            assertEquals(0, schneide(zwei, zwei.length() - IK_FORM_DOKU_IP.length(), zwei.length()).size(),
                    "Reststuecke mit zwei Ziffern");
        }
    }

    @Test
    void zuschneiden_restfundBehaeltDieKonfidenzDesKandidatenHoechstensNullAchtzig() {
        String vorn = DREI_VORN + " ";
        String text = vorn + IK_FORM_DOKU_IP;
        for (double konfidenz : new double[] {0.40, 0.50, 0.60, 0.80, 0.95}) {
            Finding kandidat = new Finding(FindingType.KVNR, text, 0, text.length(), konfidenz, "kvnr");
            Finding bestand = new Finding(FindingType.IP, IK_FORM_DOKU_IP, vorn.length(), text.length(), 0.85, "ipv4");
            List<Finding> reste = TextScanner.zuschneiden(List.of(kandidat), QUELLE_REST, List.of(bestand));
            assertEquals(1, reste.size(), "Zahl der Reststuecke");
            assertRest(reste.get(0), DREI_VORN, 0, Math.min(konfidenz, 0.80));
        }
    }

    // ------------------------------------------------------------------ Last

    /**
     * Ein Kandidat mit sehr vielen Schnitten: Der Kandidat umfasst einen Text von mindestens einer Million Zeichen, in
     * dem {@code 123 IP } wiederholt steht; jeder Bestandsfund (IP) schneidet ihn. Erwartet je Wiederholung ein
     * Reststueck {@code 123} (der Rest hinter der letzten IP besteht nur aus einem Leerzeichen und entfaellt). Formel:
     * Baustein 4 + 12 + 1 = 17 Zeichen, Wiederholungen r = aufgerundet 1 000 000 / 17 = 58 824; Reststuecke = r.
     */
    @Test
    void zuschneiden_einKandidatMitSehrVielenSchnitten_exakteZahlUnterZweiSekunden() {
        String baustein = DREI_VORN + " " + IK_FORM_DOKU_IP + " ";
        assertEquals(17, baustein.length(), "Bausteinlaenge");
        int r = (1_000_000 + baustein.length() - 1) / baustein.length();
        assertEquals(58_824, r, "Wiederholungen");
        String text = baustein.repeat(r);
        Finding kandidat = new Finding(FindingType.KVNR, text, 0, text.length(), KONFIDENZ, "kvnr-form-abweichend");
        List<Finding> bestand = new ArrayList<>(r);
        for (int i = 0; i < r; i++) {
            int start = i * baustein.length() + DREI_VORN.length() + 1;
            bestand.add(new Finding(FindingType.IP, IK_FORM_DOKU_IP, start, start + IK_FORM_DOKU_IP.length(), 0.85,
                    "ipv4"));
        }
        List<Finding> reste = assertTimeoutPreemptively(GRENZE,
                () -> TextScanner.zuschneiden(List.of(kandidat), QUELLE_REST, bestand), "Laufzeitgrenze 2 s");
        assertEquals(r, reste.size(), "Zahl der Reststuecke");
        assertRest(reste.get(0), DREI_VORN, 0, KONFIDENZ);
        assertRest(reste.get(r - 1), DREI_VORN, (r - 1) * baustein.length(), KONFIDENZ);
    }

    /**
     * Sehr viele Kandidaten: je Wiederholung ein Kandidat, der {@code 123 IP} umfasst (Rest {@code 123}), und ein
     * Kandidat nur ueber die IP (liegt ganz im Bestand und entfaellt). Erwartet r Reststuecke (r wie im Test davor).
     */
    @Test
    void zuschneiden_sehrVieleKandidatenAbwechselndMitBestandsfunden_exakteZahlUnterZweiSekunden() {
        String baustein = DREI_VORN + " " + IK_FORM_DOKU_IP + " ";
        int r = (1_000_000 + baustein.length() - 1) / baustein.length();
        String text = baustein.repeat(r);
        List<Finding> kandidaten = new ArrayList<>(2 * r);
        List<Finding> bestand = new ArrayList<>(r);
        for (int i = 0; i < r; i++) {
            int von = i * baustein.length();
            int ipStart = von + DREI_VORN.length() + 1;
            int ipEnde = ipStart + IK_FORM_DOKU_IP.length();
            kandidaten.add(new Finding(FindingType.KVNR, text.substring(von, ipEnde), von, ipEnde, KONFIDENZ,
                    "kvnr-form-abweichend"));
            kandidaten.add(new Finding(FindingType.KVNR, IK_FORM_DOKU_IP, ipStart, ipEnde, KONFIDENZ,
                    "kvnr-form-abweichend"));
            bestand.add(new Finding(FindingType.IP, IK_FORM_DOKU_IP, ipStart, ipEnde, 0.85, "ipv4"));
        }
        List<Finding> reste = assertTimeoutPreemptively(GRENZE,
                () -> TextScanner.zuschneiden(kandidaten, QUELLE_REST, bestand), "Laufzeitgrenze 2 s");
        assertEquals(r, reste.size(), "Zahl der Reststuecke");
        for (Finding f : reste) {
            assertTrue(QUELLE_REST.equals(f.source()), "Quelle");
            assertTrue(DREI_VORN.equals(f.originalValue()), "Wert");
        }
    }

    /**
     * Ein einziger Bestandsbereich (URL) ueber den ganzen Text von einer Million Zeichen, darin sehr viele Kandidaten:
     * Jeder Kandidat liegt ganz im Bestand, es bleibt kein Reststueck (exakt 0). Formel: Baustein {@code Z123/} = 5
     * Zeichen, Wiederholungen r = (1 000 000 + 5 - 1) / 5 = 200 000, Textlaenge 1 000 000; Kandidaten = r, Reststuecke
     * = 0. Gegenprobe: ohne Bestandsfund bleiben alle r Kandidaten unveraendert.
     */
    @Test
    void zuschneiden_einBestandsbereichMitSehrVielenKandidatenDarin_alleEntfallen_exakteZahlUnterZweiSekunden() {
        String baustein = "Z" + DREI_VORN + "/";
        assertEquals(5, baustein.length(), "Bausteinlaenge");
        int r = (1_000_000 + baustein.length() - 1) / baustein.length();
        assertEquals(200_000, r, "Wiederholungen");
        String text = baustein.repeat(r);
        assertEquals(1_000_000, text.length(), "Textlaenge");
        List<Finding> kandidaten = new ArrayList<>(r);
        for (int i = 0; i < r; i++) {
            int von = i * baustein.length();
            int bis = von + baustein.length() - 1;
            kandidaten.add(new Finding(FindingType.KVNR, text.substring(von, bis), von, bis, KONFIDENZ,
                    "kvnr-form-abweichend"));
        }
        List<Finding> bestand = List.of(new Finding(FindingType.URL, text, 0, text.length(), 0.95, "url"));
        List<Finding> reste = assertTimeoutPreemptively(GRENZE,
                () -> TextScanner.zuschneiden(kandidaten, QUELLE_REST, bestand), "Laufzeitgrenze 2 s");
        assertEquals(0, reste.size(), "Zahl der Reststuecke");
        // Gegenprobe: ohne Bestandsfund bleiben alle Kandidaten unveraendert
        List<Finding> alle = assertTimeoutPreemptively(GRENZE,
                () -> TextScanner.zuschneiden(kandidaten, QUELLE_REST, List.of()), "Laufzeitgrenze 2 s (ohne Bestand)");
        assertEquals(r, alle.size(), "Zahl der Kandidaten ohne Bestand");
    }
}
