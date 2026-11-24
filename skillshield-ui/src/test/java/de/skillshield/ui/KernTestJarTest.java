package de.skillshield.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.SynthetischeKvNummern;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.TextScanner;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Beweist, dass das test-jar des Kerns (A-1) im ui-Modul nutzbar ist und eine gueltige KVNR erkannt wird. */
class KernTestJarTest {

    @Test
    void synthetischeKvnrMitKontextwortWirdVomStandardScannerAlsKvnrErkannt() {
        String text = "KVNR " + SynthetischeKvNummern.KVNR_GUELTIG;

        List<Finding> funde = TextScanner.standard().scan(text);

        assertTrue(funde.stream().anyMatch(f -> f.type().equals(FindingType.KVNR)
                && f.originalValue().equals(SynthetischeKvNummern.KVNR_GUELTIG)),
                "gueltige synthetische KVNR wurde nicht als KVNR erkannt, Funde: " + funde.size());
    }
}
