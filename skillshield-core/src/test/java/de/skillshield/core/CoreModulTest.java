package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Smoke- und Richtungstest des Moduls skillshield-core. */
class CoreModulTest {

    @Test
    void eigenesModulpaketIstKompiliert() {
        assertDoesNotThrow(() -> Class.forName("de.skillshield.core.package-info"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"io", "vault", "ui", "app"})
    void verboteneModuleLiegenNichtAufDemKlassenpfad(String modul) {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("de.skillshield." + modul + ".package-info"));
    }
}
