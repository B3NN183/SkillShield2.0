package de.skillshield.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Smoke- und Richtungstest des Moduls skillshield-ui. */
class UiModulTest {

    @Test
    void eigenesModulpaketIstKompiliert() {
        assertDoesNotThrow(() -> Class.forName("de.skillshield.ui.package-info"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"core", "io", "vault"})
    void erlaubteModuleLiegenAufDemKlassenpfad(String modul) {
        assertDoesNotThrow(() -> Class.forName("de.skillshield." + modul + ".package-info"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"app"})
    void verboteneModuleLiegenNichtAufDemKlassenpfad(String modul) {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("de.skillshield." + modul + ".package-info"));
    }
}
