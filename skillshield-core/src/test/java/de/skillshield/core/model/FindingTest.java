package de.skillshield.core.model;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FindingTest {

    private static final String MARKER = "GEHEIM-mustermann-4711";

    @Test
    void gueltigerFundHatAlleFelder() {
        String wert = bs("C:¦Tools¦BeispielTool¦tool.exe");
        String text = "Start " + wert + " jetzt";
        Finding f = new Finding(FindingType.PFAD, wert, 6, 6 + wert.length(), 0.95, "pfad-laufwerk");
        assertEquals(FindingType.PFAD, f.type());
        assertEquals(wert, f.originalValue());
        assertEquals(wert, text.substring(f.start(), f.end()));
        assertEquals(0.95, f.confidence());
        assertEquals("pfad-laufwerk", f.source());
        assertEquals(ConfidenceLevel.SICHER, f.level());
    }

    @Test
    void positionenFolgenSubstringKonvention() {
        String text = "ab" + MARKER + "cd";
        Finding f = new Finding(FindingType.TEXT, MARKER, 2, 2 + MARKER.length(), 0.5, "test");
        assertEquals(MARKER, text.substring(f.start(), f.end()));
        assertEquals(ConfidenceLevel.FRAGLICH, f.level());
    }

    @Test
    void negativeStartpositionWirdAbgelehnt() {
        assertThrows(IllegalArgumentException.class,
                () -> new Finding(FindingType.IP, "ab", -1, 1, 0.5, "t"));
    }

    @Test
    void startNichtKleinerAlsEndeWirdAbgelehnt() {
        assertThrows(IllegalArgumentException.class,
                () -> new Finding(FindingType.IP, "", 3, 3, 0.5, "t"));
        assertThrows(IllegalArgumentException.class,
                () -> new Finding(FindingType.IP, "ab", 5, 3, 0.5, "t"));
    }

    @Test
    void laengenabweichungWirdAbgelehnt() {
        assertThrows(IllegalArgumentException.class,
                () -> new Finding(FindingType.IP, "abc", 0, 2, 0.5, "t"));
        assertThrows(IllegalArgumentException.class,
                () -> new Finding(FindingType.IP, "a", 0, 2, 0.5, "t"));
    }

    @Test
    void konfidenzAusserhalbDesBereichsOderNanWirdAbgelehnt() {
        assertThrows(IllegalArgumentException.class,
                () -> new Finding(FindingType.IP, "ab", 0, 2, -0.1, "t"));
        assertThrows(IllegalArgumentException.class,
                () -> new Finding(FindingType.IP, "ab", 0, 2, 1.1, "t"));
        assertThrows(IllegalArgumentException.class,
                () -> new Finding(FindingType.IP, "ab", 0, 2, Double.NaN, "t"));
    }

    @Test
    void randwerteDerKonfidenzSindErlaubt() {
        assertEquals(ConfidenceLevel.FRAGLICH, new Finding(FindingType.IP, "ab", 0, 2, 0.0, "t").level());
        assertEquals(ConfidenceLevel.SICHER, new Finding(FindingType.IP, "ab", 0, 2, 1.0, "t").level());
    }

    @Test
    void nullFelderWerdenMitParameternamenAbgelehnt() {
        assertEquals("type", assertThrows(NullPointerException.class,
                () -> new Finding(null, "ab", 0, 2, 0.5, "t")).getMessage());
        assertEquals("originalValue", assertThrows(NullPointerException.class,
                () -> new Finding(FindingType.IP, null, 0, 2, 0.5, "t")).getMessage());
        assertEquals("source", assertThrows(NullPointerException.class,
                () -> new Finding(FindingType.IP, "ab", 0, 2, 0.5, null)).getMessage());
    }

    @Test
    void manuellerFundHatKonfidenzEinsUndQuelleManuell() {
        Finding f = Finding.manual(new FindingType("PERSON"), "Muster", 4, 10);
        assertEquals(1.0, f.confidence());
        assertEquals("manuell", f.source());
        assertEquals(Finding.SOURCE_MANUAL, f.source());
        assertEquals(ConfidenceLevel.SICHER, f.level());
        assertEquals("PERSON", f.type().name());
        assertEquals("Muster", f.originalValue());
    }

    @Test
    void manuellerFundPrueftDieGleichenInvarianten() {
        assertThrows(IllegalArgumentException.class, () -> Finding.manual(FindingType.TEXT, "abc", 0, 2));
        assertThrows(IllegalArgumentException.class, () -> Finding.manual(FindingType.TEXT, "abc", -1, 2));
    }

    @Test
    void toStringHatDasFestgelegteFormatOhneOriginalwert() {
        Finding f = new Finding(FindingType.PFAD, "C:/a/b", 6, 12, 0.95, "pfad-laufwerk");
        assertEquals("Finding[type=PFAD, start=6, end=12, confidence=0.95, level=SICHER, source=pfad-laufwerk]",
                f.toString());
    }

    @Test
    void toStringEnthaeltKeinenMarkerOriginalwert() {
        Finding f = new Finding(FindingType.TEXT, MARKER, 0, MARKER.length(), 1.0, "test");
        assertFalse(f.toString().contains(MARKER));
        assertFalse(f.toString().contains("mustermann"));
    }

    @Test
    void ausnahmemeldungenEnthaltenKeinenOriginalwert() {
        IllegalArgumentException laenge = assertThrows(IllegalArgumentException.class,
                () -> new Finding(FindingType.TEXT, MARKER, 0, 3, 0.5, "t"));
        IllegalArgumentException position = assertThrows(IllegalArgumentException.class,
                () -> new Finding(FindingType.TEXT, MARKER, -1, MARKER.length(), 0.5, "t"));
        IllegalArgumentException konfidenz = assertThrows(IllegalArgumentException.class,
                () -> new Finding(FindingType.TEXT, MARKER, 0, MARKER.length(), 2.0, "t"));
        for (IllegalArgumentException e : new IllegalArgumentException[] {laenge, position, konfidenz}) {
            assertFalse(e.getMessage().contains(MARKER));
            assertFalse(e.getMessage().contains("mustermann"));
        }
    }

    @Test
    void gleicheWerteSindGleich() {
        Finding a = new Finding(FindingType.IP, "192.0.2.10", 0, 10, 0.85, "ipv4");
        Finding b = new Finding(FindingType.IP, "192.0.2.10", 0, 10, 0.85, "ipv4");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertTrue(a.level() == ConfidenceLevel.WAHRSCHEINLICH);
    }
}
