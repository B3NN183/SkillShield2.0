package de.skillshield.ui.logik;

import static de.skillshield.ui.logik.Attrappen.scannen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import de.skillshield.ui.logik.Attrappen.TestDialoge;
import de.skillshield.ui.logik.Attrappen.TestZwischenablage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Roundtrip mit manuell markiertem synthetischem Namen (AK-29). */
class ManuellRoundtripTest {

    private static final String EMOJI = new String(Character.toChars(0x1F600));

    private final Sitzung sitzung = Sitzung.standard(new TestZwischenablage(), new TestDialoge());

    private ReidentificationResult zurueck(String text) {
        List<Finding> gewaehlt = sitzung.zeilen().stream().filter(FundZeile::angewaehlt).map(FundZeile::fund).toList();
        PseudonymizationResult kern = new Pseudonymizer().pseudonymize(text, gewaehlt);
        assertEquals(kern.text(), sitzung.textFuerKi(), "Text fuer die KI entspricht dem Kern");
        return new Reidentifier().restore(sitzung.textFuerKi(), kern.mapping());
    }

    @Test
    void einManuellMarkierterNameKommtZeichengenauZurueck() {
        String text = "Max Mustermann schreibt an mustermann@example.org. Gruss, Max Mustermann";
        scannen(sitzung, text);
        sitzung.markierungHinzufuegen(0, 14);

        assertFalse(sitzung.textFuerKi().contains("Mustermann"));
        ReidentificationResult ergebnis = zurueck(text);

        assertEquals(text, ergebnis.text());
        assertTrue(ergebnis.unresolved().isEmpty());
    }

    @Test
    void auchMitAbgewaehltemFundUndEmojiInDerMarkierungKommtDerTextZurueck() {
        String text = "Frau Beispielname " + EMOJI + " an 192.0.2.10; Beispielname " + EMOJI + "!";
        scannen(sitzung, text);
        int start = text.indexOf("Beispielname");
        sitzung.markierungHinzufuegen(start, start + "Beispielname".length() + 2);
        sitzung.anwaehlen(sitzung.zeilen().size() - 1, false);

        ReidentificationResult ergebnis = zurueck(text);

        assertEquals(text, ergebnis.text());
        assertTrue(sitzung.zeilen().stream().anyMatch(z -> !z.angewaehlt()));
    }

    @Test
    void eineErweiterteMarkierungUeberEinenAutomatischenFundKommtZeichengenauZurueck() {
        String text = "Schreiben Sie an Max Mustermann <mustermann@example.org> bis morgen.";
        scannen(sitzung, text);
        int start = text.indexOf("Max");
        sitzung.markierungHinzufuegen(start, text.indexOf("mustermann@") + 5);

        assertEquals(1, sitzung.zeilen().size());
        assertEquals(text, zurueck(text).text());
    }
}
