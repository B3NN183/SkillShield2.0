package de.skillshield.core.pseudonymize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.FindingType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlaceholderMappingTest {

    private static final String MARKER = "GEHEIM-mustermann-4711";

    @Test
    void formatErzeugtKanonischenPlatzhalter() {
        assertEquals("[[PFAD_1]]", PlaceholderSyntax.format(FindingType.PFAD, 1));
        assertEquals("[[EMAIL_123456789]]", PlaceholderSyntax.format(FindingType.EMAIL, 123456789));
        assertEquals("[[PERSON_7]]", PlaceholderSyntax.format(new FindingType("PERSON"), 7));
    }

    @Test
    void formatLehntNullUndNummerKleinerEinsAb() {
        assertThrows(NullPointerException.class, () -> PlaceholderSyntax.format(null, 1));
        assertThrows(IllegalArgumentException.class, () -> PlaceholderSyntax.format(FindingType.IP, 0));
        assertThrows(IllegalArgumentException.class, () -> PlaceholderSyntax.format(FindingType.IP, -3));
    }

    @Test
    void leereZuordnung() {
        PlaceholderMapping m = PlaceholderMapping.empty();
        assertEquals(0, m.size());
        assertTrue(m.placeholders().isEmpty());
        assertTrue(m.typeNames().isEmpty());
        assertTrue(m.resolve("[[PFAD_1]]").isEmpty());
        assertSame(m, PlaceholderMapping.of(Map.of()));
    }

    @Test
    void aufloesenUndAbfragen() {
        PlaceholderMapping m = PlaceholderMapping.of(Map.of("[[PFAD_1]]", "a", "[[IP_2]]", "b"));
        assertEquals("a", m.resolve("[[PFAD_1]]").orElseThrow());
        assertEquals("b", m.resolve("[[IP_2]]").orElseThrow());
        assertTrue(m.resolve("[[PFAD_2]]").isEmpty());
        assertTrue(m.resolve("[[pfad_1]]").isEmpty());
        assertEquals(2, m.size());
        assertEquals(java.util.Set.of("PFAD", "IP"), m.typeNames());
    }

    @Test
    void reihenfolgeFolgtDerNummernfolge() {
        Map<String, String> in = new LinkedHashMap<>();
        in.put("[[IP_10]]", "x");
        in.put("[[PFAD_2]]", "y");
        in.put("[[EMAIL_1]]", "z");
        assertEquals(List.of("[[EMAIL_1]]", "[[PFAD_2]]", "[[IP_10]]"),
                new ArrayList<>(PlaceholderMapping.of(in).placeholders()));
    }

    @Test
    void defensiveKopieDerEingabe() {
        Map<String, String> in = new HashMap<>();
        in.put("[[PFAD_1]]", "a");
        PlaceholderMapping m = PlaceholderMapping.of(in);
        in.put("[[PFAD_2]]", "b");
        in.put("[[PFAD_1]]", "geaendert");
        assertEquals(1, m.size());
        assertEquals("a", m.resolve("[[PFAD_1]]").orElseThrow());
    }

    @Test
    void sichtenSindUnveraenderlich() {
        PlaceholderMapping m = PlaceholderMapping.of(Map.of("[[PFAD_1]]", "a"));
        assertThrows(UnsupportedOperationException.class, () -> m.placeholders().add("[[IP_1]]"));
        assertThrows(UnsupportedOperationException.class, () -> m.placeholders().clear());
        assertThrows(UnsupportedOperationException.class, () -> m.typeNames().add("X"));
        assertThrows(UnsupportedOperationException.class, () -> m.typeNames().clear());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "PFAD_1", "[[PFAD_1]", "[PFAD_1]]", "[[PFAD_0]]", "[[PFAD_01]]", "[[PFAD_]]",
        "[[_1]]", "[[pfad_1]]", "[[ PFAD_1 ]]", "[[PFAD_1_2]]", "[[PFAD_1234567890]]", "[[PFAD1]]", "[[1PFAD_1]]",
        "[[PFAD_-1]]", "[[PFAD_1]] ", "[[A_1]][[B_2]]", "[[PFAD__1]]"})
    void nichtKanonischeSchluesselWerdenAbgelehnt(String key) {
        Map<String, String> in = Map.of(key, MARKER);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> PlaceholderMapping.of(in));
        assertFalse(String.valueOf(e.getMessage()).contains(MARKER));
        assertFalse(String.valueOf(e.getMessage()).contains("PFAD_1"));
    }

    @Test
    void nullEingaben() {
        NullPointerException e1 = assertThrows(NullPointerException.class, () -> PlaceholderMapping.of(null));
        assertEquals("placeholderToOriginal", e1.getMessage());
        Map<String, String> nullWert = new HashMap<>();
        nullWert.put("[[PFAD_1]]", null);
        assertThrows(NullPointerException.class, () -> PlaceholderMapping.of(nullWert));
        Map<String, String> nullKey = new HashMap<>();
        nullKey.put(null, "x");
        assertThrows(NullPointerException.class, () -> PlaceholderMapping.of(nullKey));
        PlaceholderMapping m = PlaceholderMapping.empty();
        assertThrows(NullPointerException.class, () -> m.resolve(null));
    }

    @Test
    void toStringEnthaeltNurAnzahl() {
        PlaceholderMapping m = PlaceholderMapping.of(Map.of("[[PFAD_1]]", MARKER, "[[IP_2]]", MARKER + "2"));
        assertEquals("PlaceholderMapping[size=2]", m.toString());
        assertFalse(m.toString().contains(MARKER));
        assertEquals("PlaceholderMapping[size=0]", PlaceholderMapping.empty().toString());
    }

    @Test
    void gleichheitNachInhalt() {
        PlaceholderMapping a = PlaceholderMapping.of(Map.of("[[PFAD_1]]", "a"));
        PlaceholderMapping b = PlaceholderMapping.of(Map.of("[[PFAD_1]]", "a"));
        PlaceholderMapping c = PlaceholderMapping.of(Map.of("[[PFAD_1]]", "b"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
