package de.skillshield.io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Smoke- und Richtungstest des Moduls skillshield-io. */
class IoModulTest {

    @Test
    void eigenesModulpaketIstKompiliert() {
        assertDoesNotThrow(() -> Class.forName("de.skillshield.io.package-info"));
    }

    @Test
    void erlaubtesModulCoreLiegtAufDemKlassenpfad() {
        assertDoesNotThrow(() -> Class.forName("de.skillshield.core.package-info"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vault", "ui", "app"})
    void verboteneModuleLiegenNichtAufDemKlassenpfad(String modul) {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("de.skillshield." + modul + ".package-info"));
    }
}
