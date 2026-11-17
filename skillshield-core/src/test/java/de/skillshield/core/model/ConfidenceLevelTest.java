package de.skillshield.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConfidenceLevelTest {

    @ParameterizedTest
    @CsvSource({
            "1.0, SICHER",
            "0.95, SICHER",
            "0.90, SICHER",
            "0.8999999, WAHRSCHEINLICH",
            "0.85, WAHRSCHEINLICH",
            "0.80, WAHRSCHEINLICH",
            "0.70, WAHRSCHEINLICH",
            "0.6999999, FRAGLICH",
            "0.5, FRAGLICH",
            "0.0, FRAGLICH"
    })
    void zuordnungAnDenSchwellen(double confidence, ConfidenceLevel erwartet) {
        assertEquals(erwartet, ConfidenceLevel.of(confidence));
    }

    @Test
    void schwellenkonstanten() {
        assertEquals(0.90, ConfidenceLevel.SCHWELLE_SICHER);
        assertEquals(0.70, ConfidenceLevel.SCHWELLE_WAHRSCHEINLICH);
    }

    @Test
    void ausserhalbDesBereichsUndNanWerdenAbgelehnt() {
        assertThrows(IllegalArgumentException.class, () -> ConfidenceLevel.of(-0.01));
        assertThrows(IllegalArgumentException.class, () -> ConfidenceLevel.of(1.01));
        assertThrows(IllegalArgumentException.class, () -> ConfidenceLevel.of(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> ConfidenceLevel.of(Double.POSITIVE_INFINITY));
    }

    @Test
    void zuordnungIstBeiWiederholungGleich() {
        assertEquals(ConfidenceLevel.of(0.8), ConfidenceLevel.of(0.8));
    }
}
