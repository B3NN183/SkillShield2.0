package de.skillshield.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.pseudonymize.PlaceholderSyntax;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FindingTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {"PFAD", "EMAIL", "IP", "URL", "TEXT", "A", "PERSON", "X1", "A1B2C3"})
    void gueltigeNamenWerdenAkzeptiert(String name) {
        assertEquals(name, new FindingType(name).name());
    }

    @Test
    void zweiunddreissigZeichenSindErlaubt() {
        String name = "A" + "B".repeat(31);
        assertEquals(32, name.length());
        assertEquals(name, new FindingType(name).name());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pfad", "Pfad", "PFAD_1", "PFAD 1", "", " ", "1PFAD", "PF-AD", "MÜLL", "ＰＦＡＤ", "A\n"})
    void ungueltigeNamenWerdenAbgelehntOhneEcho(String name) {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> new FindingType(name));
        if (!name.isEmpty() && !name.isBlank()) {
            assertFalse(e.getMessage().contains(name), "Meldung darf den Namen nicht wiederholen");
        }
    }

    @Test
    void dreiunddreissigZeichenWerdenAbgelehnt() {
        String name = "A".repeat(33);
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> new FindingType(name));
        assertFalse(e.getMessage().contains(name));
    }

    @Test
    void nullWirdMitParameternamenAbgelehnt() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> new FindingType(null));
        assertEquals("name", e.getMessage());
    }

    @Test
    void vordefinierteKonstantenHabenDieErwartetenNamen() {
        assertEquals("PFAD", FindingType.PFAD.name());
        assertEquals("EMAIL", FindingType.EMAIL.name());
        assertEquals("IP", FindingType.IP.name());
        assertEquals("URL", FindingType.URL.name());
        assertEquals("TEXT", FindingType.TEXT.name());
    }

    @Test
    void gleicherNameIstGleich() {
        assertEquals(FindingType.PFAD, new FindingType("PFAD"));
        assertEquals(FindingType.PFAD.hashCode(), new FindingType("PFAD").hashCode());
    }

    @Test
    void kvFundtypenIkUndLanrSindGueltigeKonstanten() {
        assertEquals("IK", FindingType.IK.name());
        assertEquals("LANR", FindingType.LANR.name());
        assertTrue(FindingType.isValidName(FindingType.IK.name()));
        assertTrue(FindingType.isValidName(FindingType.LANR.name()));
        assertEquals(FindingType.IK, new FindingType("IK"));
        assertEquals(FindingType.LANR, new FindingType("LANR"));
        assertNotEquals(FindingType.IK, FindingType.LANR);
    }

    @Test
    void kvFundtypenErgebenDiePlatzhalterIkUndLanr() {
        assertEquals("[[IK_1]]", PlaceholderSyntax.format(FindingType.IK, 1));
        assertEquals("[[LANR_1]]", PlaceholderSyntax.format(FindingType.LANR, 1));
    }
}
