package de.skillshield.vault;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Smoke- und Richtungstest des Moduls skillshield-vault. */
class VaultModulTest {

    @Test
    void eigenesModulpaketIstKompiliert() {
        assertDoesNotThrow(() -> Class.forName("de.skillshield.vault.package-info"));
    }

    @Test
    void erlaubtesModulCoreLiegtAufDemKlassenpfad() {
        assertDoesNotThrow(() -> Class.forName("de.skillshield.core.package-info"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"io", "ui", "app"})
    void verboteneModuleLiegenNichtAufDemKlassenpfad(String modul) {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("de.skillshield." + modul + ".package-info"));
    }
}
