package de.skillshield.app;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Smoke- und Richtungstest des Moduls skillshield-app. */
class AppModulTest {

    @Test
    void eigenesModulpaketIstKompiliert() {
        assertDoesNotThrow(() -> Class.forName("de.skillshield.app.package-info"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"core", "io", "vault", "ui"})
    void erlaubteModuleLiegenAufDemKlassenpfad(String modul) {
        assertDoesNotThrow(() -> Class.forName("de.skillshield." + modul + ".package-info"));
    }
}
