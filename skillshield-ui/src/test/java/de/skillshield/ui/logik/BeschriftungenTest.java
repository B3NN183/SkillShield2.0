package de.skillshield.ui.logik;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** D-13: deutsche Klartext-Beschriftung von Typ und Stufe (AK-13, AK-17). */
class BeschriftungenTest {

    private static Finding fund(String typ) {
        return new Finding(new FindingType(typ), "x", 0, 1, 0.9, "test");
    }

    @ParameterizedTest
    @CsvSource({"PFAD,Pfad", "EMAIL,E-Mail", "IP,IP-Adresse", "URL,Internetadresse", "IK,IK", "LANR,LANR",
            "KVNR,KVNR", "BSNR,BSNR", "TEXT,Manuell"})
    void typenHabenDieBeschriftungenAusD13(String typ, String erwartet) {
        assertEquals(erwartet, Beschriftungen.typ(fund(typ)));
    }

    @Test
    void manuellerFundHatDenTypManuell() {
        assertEquals("Manuell", Beschriftungen.typ(Finding.manual(FindingType.TEXT, "Beispielname", 0, 12)));
    }

    @Test
    void unbekannterTypnameWirdUnveraendertAngezeigt() {
        assertEquals("NEUERTYP7", Beschriftungen.typ(fund("NEUERTYP7")));
    }

    @Test
    void stufenSindAlsWortLesbar() {
        assertEquals("sicher", Beschriftungen.stufe(ConfidenceLevel.SICHER));
        assertEquals("wahrscheinlich", Beschriftungen.stufe(ConfidenceLevel.WAHRSCHEINLICH));
        assertEquals("fraglich", Beschriftungen.stufe(ConfidenceLevel.FRAGLICH));
    }
}
