package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG_DREI;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.IK_UNGUELTIG_KOMPLEMENT;
import static de.skillshield.core.SynthetischeKvNummern.IK_UNGUELTIG_NULL;
import static de.skillshield.core.SynthetischeKvNummern.IK_UNGUELTIG_VERTAUSCHT;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG_FACHGRUPPE;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG_PRUEFZIFFER_NULL;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_VERTAUSCHT;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_ZIFFER_GEAENDERT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.FindingType;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Bestandsschutz fuer {@link KvArt} (Plan kvnr-bsnr-erkennung, Regel 11, T-1): Die eingefrorenen Kopien
 * ({@code KvNumberScannerStandLuecken}, {@code KvNumberScannerStandTrenner}) und der Scanner fragen fuer LANR und IK
 * genau die Werte ab, die hier festgeschrieben sind. Das Feature darf {@code KvArt} nur additiv aendern; faellt
 * einer dieser Werte fuer LANR oder IK anders aus, schlaegt dieser Test an, bevor ein Siegel es merkt. Die Werte
 * stehen hier bewusst als Literale und nicht aus {@code KvArt} abgeleitet. Die Test-Nummern kommen aus
 * {@code SynthetischeKvNummern}.
 */
class KvArtBestandTest {

    private static int[] ziffern(String nummer) {
        int[] d = new int[nummer.length()];
        for (int i = 0; i < d.length; i++) {
            d[i] = nummer.charAt(i) - '0';
        }
        return d;
    }

    @Test
    void lanr_typKonfidenzUndQuelleSindUnveraendert() {
        assertTrue(KvArt.LANR.type().equals(FindingType.LANR), "LANR: Typ");
        assertEquals(0.95, KvArt.LANR.confidenceDirekt(), 0.0, "LANR: Konfidenz bei direktem Kontext");
        assertEquals("lanr", KvArt.LANR.source(), "LANR: Quelle");
    }

    @Test
    void ik_typKonfidenzUndQuelleSindUnveraendert() {
        assertTrue(KvArt.IK.type().equals(FindingType.IK), "IK: Typ");
        assertEquals(0.92, KvArt.IK.confidenceDirekt(), 0.0, "IK: Konfidenz bei direktem Kontext");
        assertEquals("ik", KvArt.IK.source(), "IK: Quelle");
    }

    @Test
    void lanr_kontextwoerterUndVorwoerterSindUnveraendert() {
        assertTrue(KvArt.LANR.kontextwoerter().equals(Set.of("lanr", "lanrnr", "arztnummer", "arztnr")),
                "LANR: Kontextwoerter");
        assertTrue(KvArt.LANR.vorwoerter().equals(Set.of("arzt")), "LANR: Vorwoerter");
    }

    @Test
    void ik_kontextwoerterUndVorwoerterSindUnveraendert() {
        assertTrue(KvArt.IK.kontextwoerter().equals(Set.of("ik", "iknr", "iknummer", "institutionskennzeichen")),
                "IK: Kontextwoerter");
        assertTrue(KvArt.IK.vorwoerter().isEmpty(), "IK: Vorwoerter");
    }

    @Test
    void beideQuellenSuffixeSindUnveraendert() {
        assertEquals("lanr-pruefziffer-ungueltig", KvArt.LANR.sourcePruefzifferUngueltig(), "LANR: Pruefziffer");
        assertEquals("lanr-laenge-abweichend", KvArt.LANR.sourceLaengeAbweichend(), "LANR: Laenge");
        assertEquals("ik-pruefziffer-ungueltig", KvArt.IK.sourcePruefzifferUngueltig(), "IK: Pruefziffer");
        assertEquals("ik-laenge-abweichend", KvArt.IK.sourceLaengeAbweichend(), "IK: Laenge");
    }

    @Test
    void lanrUndIkStehenWeiterAnErsterUndZweiterStelle() {
        assertEquals(0, KvArt.LANR.ordinal(), "LANR: Reihenfolge");
        assertEquals(1, KvArt.IK.ordinal(), "IK: Reihenfolge");
        assertTrue(KvArt.valueOf("LANR") == KvArt.LANR && KvArt.valueOf("IK") == KvArt.IK, "Namen");
    }

    @Test
    void lanr_isValid_anDenVektorenAusSynthetischeKvNummern() {
        for (String gueltig : new String[] {LANR_GUELTIG, LANR_GUELTIG_ZWEI, LANR_GUELTIG_FACHGRUPPE,
            LANR_GUELTIG_PRUEFZIFFER_NULL}) {
            assertTrue(KvArt.LANR.isValid(ziffern(gueltig)), "LANR: gueltiger Vektor wird abgelehnt");
        }
        for (String ungueltig : new String[] {LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, LANR_UNGUELTIG_VERTAUSCHT,
            LANR_UNGUELTIG_ZIFFER_GEAENDERT}) {
            assertFalse(KvArt.LANR.isValid(ziffern(ungueltig)), "LANR: ungueltiger Vektor wird angenommen");
        }
    }

    @Test
    void ik_isValid_anDenVektorenAusSynthetischeKvNummern() {
        for (String gueltig : new String[] {IK_GUELTIG, IK_GUELTIG_ZWEI, IK_GUELTIG_DREI}) {
            assertTrue(KvArt.IK.isValid(ziffern(gueltig)), "IK: gueltiger Vektor wird abgelehnt");
        }
        for (String ungueltig : new String[] {IK_UNGUELTIG_KOMPLEMENT, IK_UNGUELTIG_NULL, IK_UNGUELTIG_VERTAUSCHT}) {
            assertFalse(KvArt.IK.isValid(ziffern(ungueltig)), "IK: ungueltiger Vektor wird angenommen");
        }
    }

    /**
     * Quer ueber den Wertebereich (fester Seed, 20 000 Ziffernfolgen aus neun zufaelligen Werten): {@code isValid}
     * gleicht fuer LANR und IK dem Verfahren in {@link KvCheckDigits}, das das Feature nicht aendert.
     */
    @Test
    void isValid_gleichtFuerLanrUndIkDemVerfahrenInKvCheckDigits() {
        Random r = new Random(4_711L);
        int gueltigLanr = 0;
        int gueltigIk = 0;
        for (int i = 0; i < 20_000; i++) {
            int[] d = new int[9];
            for (int k = 0; k < d.length; k++) {
                d[k] = r.nextInt(10);
            }
            boolean lanr = KvCheckDigits.isValidLanr(d);
            boolean ik = KvCheckDigits.isValidIk(d);
            assertTrue(KvArt.LANR.isValid(d) == lanr, "LANR: isValid weicht ab, Iteration " + i);
            assertTrue(KvArt.IK.isValid(d) == ik, "IK: isValid weicht ab, Iteration " + i);
            gueltigLanr += lanr ? 1 : 0;
            gueltigIk += ik ? 1 : 0;
        }
        assertTrue(gueltigLanr > 1_000, "Stichprobe enthaelt zu wenige gueltige LANR-Vektoren: " + gueltigLanr);
        assertTrue(gueltigIk > 1_000, "Stichprobe enthaelt zu wenige gueltige IK-Vektoren: " + gueltigIk);
    }
}
