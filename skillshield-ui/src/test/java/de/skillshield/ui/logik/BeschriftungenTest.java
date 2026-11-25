package de.skillshield.ui.logik;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.geheimnis.GeheimnisTypen;
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

    /** geheimnisse-erkennung T-4 (D-15, AK-44): Passwort und Token oder Schluessel haben deutsche Namen. */
    @Test
    void geheimnisTypenHabenDeutscheBeschriftungen() {
        assertEquals("Passwort", Beschriftungen.typ(fund("PASSWORT")));
        assertEquals("Token oder Schl" + (char) 0xFC + "ssel", Beschriftungen.typ(fund("TOKEN")));
        assertEquals("Passwort", Beschriftungen.typ(new Finding(GeheimnisTypen.PASSWORT, "x", 0, 1, 0.95, "test")));
        assertEquals("Token oder Schl" + (char) 0xFC + "ssel",
                Beschriftungen.typ(new Finding(GeheimnisTypen.TOKEN, "x", 0, 1, 0.95, "test")));
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
