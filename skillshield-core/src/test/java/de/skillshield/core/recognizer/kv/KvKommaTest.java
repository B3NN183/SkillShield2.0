package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.LAENGE_10;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_8;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.kv.KvTestHilfen.Art;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests zu Komma und Semikolon vor dem nachgestellten Kontextwort (Feature kv-kontext-luecken, Task T-2, D-5,
 * Grenze 12, AK-13 bis AK-18, AK-20, AK-21 auf Erkennerebene, AK-24 Laufzeit). Jeder Test laeuft fuer beide
 * Nummernarten; Nummern stammen aus {@code SynthetischeKvNummern}, jeder Text geht durch den Waechter und die
 * Differenzpruefung gegen den Vorgaenger in {@link KvTestHilfen#scan}.
 */
class KvKommaTest {

    private static final double KOMMA = 0.60;

    private static void komma(Art a, String vor, String wert, String nach) {
        assertEin(a, vor, wert, nach, KOMMA);
    }

    /** Kontextwort-Varianten ohne ein Wort davor ({@code Praxis-}, {@code Lebenslange}). */
    private static String[] ohneWortDavor(Art a) {
        return Arrays.stream(a.kws).filter(k -> !k.startsWith("Praxis") && !k.startsWith("Lebenslange"))
                .toArray(String[]::new);
    }

    // ------------------------------------------------------------------ AK-14, AK-13

    @Test
    void ak14_kommaOderSemikolonVorNachgestelltemKontextwort_fraglichMitNummerAlsBereich() {
        for (Art a : ARTEN) {
            komma(a, "", a.gueltig, ", " + a.kw);
            komma(a, "", a.gueltig, "; " + a.kw);
            komma(a, "Rechnung ", a.gueltig, ", " + a.kw);
        }
    }

    @Test
    void ak14_quelleUndStufe() {
        for (Art a : ARTEN) {
            List<Finding> funde = scan(a, a.gueltig + ", " + a.kw);
            assertEquals(1, funde.size());
            assertEquals(a.quelle, funde.get(0).source(), a.name + " Quelle");
            assertEquals(KOMMA, funde.get(0).confidence(), a.name + " Konfidenz");
            assertEquals(ConfidenceLevel.of(KOMMA), funde.get(0).level(), a.name + " Stufe");
        }
    }

    @Test
    void ak13_dasKommaStehtNieImFundbereich() {
        for (Art a : ARTEN) {
            for (String z : new String[] {",", ";"}) {
                List<Finding> funde = scan(a, a.gueltig + z + " " + a.kw);
                assertEquals(1, funde.size());
                assertEquals(0, funde.get(0).start(), a.name + " Start");
                assertEquals(a.gueltig.length(), funde.get(0).end(), a.name + " Ende");
            }
        }
    }

    // ------------------------------------------------------------------ AK-15

    @Test
    void ak15_leerraumUndKlammerRundUmDasKomma_gleicherFund() {
        for (Art a : ARTEN) {
            komma(a, "", a.gueltig, " , " + a.kw);
            komma(a, "", a.gueltig, "," + a.kw);
            komma(a, "", a.gueltig, ", (" + a.kw + ")");
            komma(a, "", a.gueltig, " ; [" + a.kw + "]");
            komma(a, "", a.gueltig, ",\t" + a.kw);
            komma(a, "", a.gueltig, ",\t\t " + a.kw);
            komma(a, "", a.gueltig, ", [" + a.kw + "]");
            komma(a, "", a.gueltig, "; (" + a.kw + ")");
        }
    }

    @Test
    void ak15_schmaleUndGeschuetzteLeerzeichenVorUndNachDemKomma() {
        for (Art a : ARTEN) {
            komma(a, "", a.gueltig, DUENN + "," + NBSP + a.kw);
            komma(a, "", a.gueltig, SCHMAL_NBSP + ";" + DUENN + a.kw);
            komma(a, "", a.gueltig, NBSP + "," + SCHMAL_NBSP + a.kw);
        }
    }

    @Test
    void ak15_alleKontextwortFormenDerArt() {
        for (Art a : ARTEN) {
            for (String kw : ohneWortDavor(a)) {
                komma(a, "", a.gueltig, ", " + kw);
                komma(a, "", a.gueltig, "; " + kw);
            }
        }
    }

    @Test
    void ak15_benannteBeispieleAusDerAnforderung() {
        komma(LANR_ART, "", LANR_ART.gueltig, ", LANR-Nr.");
        komma(LANR_ART, "", LANR_ART.gueltig, ", Arzt-Nr.");
        komma(IK_ART, "", IK_ART.gueltig, ", Institutionskennzeichen");
        komma(IK_ART, "", IK_ART.gueltig, ", IK-Nummer");
    }

    @Test
    void ak15_kontextwortMitUnsichtbaremZeichen_nachDemKomma_dazwischen_undVorDemKomma() {
        for (Art a : ARTEN) {
            komma(a, "", a.gueltig, ", " + ZWSP + a.kw);
            komma(a, "", a.gueltig, "," + ZWSP + " " + a.kw);
            komma(a, "", a.gueltig, ", " + a.kw.charAt(0) + ZWSP + a.kw.substring(1));
            komma(a, "", a.gueltig, ZWSP + ", " + a.kw);
        }
    }

    @Test
    void ak15_wortDavorLeertDieFolge_keinFund() {
        for (Art a : ARTEN) {
            assertKein(a, a.gueltig + ", Praxis-" + a.kw);
            assertKein(a, a.gueltig + ", Lebenslange " + a.kwLang);
        }
    }

    @Test
    void ak15_kontextwortDerAnderenArt_keinFund() {
        assertKein(LANR_ART, LANR_ART.gueltig + ", IK");
        assertKein(IK_ART, IK_ART.gueltig + "; LANR");
    }

    // ------------------------------------------------------------------ AK-16

    @Test
    void ak16_nummernformen_trennerUngueltigUndLaenge() {
        for (Art a : ARTEN) {
            komma(a, "", gruppiert(a.gueltig, " "), ", " + a.kw);
            komma(a, "", gruppiert(a.gueltig, "-"), "; " + a.kw);
            komma(a, "", gruppiert(a.gueltig, String.valueOf(NBSP)), ", " + a.kw);
            assertEin(a, "", a.ungueltig, ", " + a.kw, PZ_UNGUELTIG);
            assertEin(a, "", gruppiert(a.ungueltig, " "), "; " + a.kw, PZ_UNGUELTIG);
            assertEin(a, "", LAENGE_7, ", " + a.kw, LAENGE);
            assertEin(a, "", LAENGE_8, "; " + a.kw, LAENGE);
            assertEin(a, "", LAENGE_10, ", " + a.kw, LAENGE);
        }
    }

    // ------------------------------------------------------------------ AK-17

    @Test
    void ak17_folgeVonNummern_alleMit060() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + ", " + a.gueltig2 + ", " + a.kw, new Erw(a.gueltig, KOMMA),
                    new Erw(a.gueltig2, KOMMA));
            assertFolge(a, a.gueltig + " und " + a.gueltig2 + ", " + a.kw, new Erw(a.gueltig, KOMMA),
                    new Erw(a.gueltig2, KOMMA));
            assertFolge(a, a.gueltig + "; " + a.gueltig2 + "; " + a.kw, new Erw(a.gueltig, KOMMA),
                    new Erw(a.gueltig2, KOMMA));
            assertFolge(a, a.gueltig + " / " + a.gueltig2 + ", " + a.kw, new Erw(a.gueltig, KOMMA),
                    new Erw(a.gueltig2, KOMMA));
            assertFolge(a, a.gueltig + " " + a.gueltig2 + ", " + a.kw, new Erw(a.gueltig, KOMMA),
                    new Erw(a.gueltig2, KOMMA));
            assertFolge(a, a.gueltig + ", " + a.gueltig2 + ", " + a.gueltig3 + ", " + a.kw,
                    new Erw(a.gueltig, KOMMA), new Erw(a.gueltig2, KOMMA), new Erw(a.gueltig3, KOMMA));
        }
    }

    @Test
    void ak17_kommaZwischenDenNummernAlleinGenuegtNicht_ohneKommaVorDemKontextwortBleibtBestand() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + ", " + a.gueltig2 + " (" + a.kw + ")", new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
        }
    }

    @Test
    void ak17_gemischteStufen() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + ", " + a.ungueltig + ", " + LAENGE_7 + ", " + a.kw, new Erw(a.gueltig, KOMMA),
                    new Erw(a.ungueltig, PZ_UNGUELTIG), new Erw(LAENGE_7, LAENGE));
        }
    }

    // ------------------------------------------------------------------ AK-18

    @Test
    void ak18_kein_doppeltesOderAnderesZeichenUndWortDazwischen() {
        for (Art a : ARTEN) {
            assertKein(a, a.gueltig + ",, " + a.kw);
            assertKein(a, a.gueltig + ",; " + a.kw);
            assertKein(a, a.gueltig + ";; " + a.kw);
            assertKein(a, a.gueltig + ", und " + a.kw);
            assertKein(a, a.gueltig + ", / " + a.kw);
            assertKein(a, a.gueltig + " /, " + a.kw);
            assertKein(a, a.gueltig + ": " + a.kw);
            assertKein(a, a.gueltig + ". " + a.kw);
            assertKein(a, a.gueltig + ", Fax " + a.kw);
            assertKein(a, a.gueltig + ", 12 " + a.kw);
        }
    }

    @Test
    void ak18_kein_zeilenumbruchHinterDemKomma() {
        for (Art a : ARTEN) {
            for (String umbruch : new String[] {"\n", "\r\n", "\r", String.valueOf(NEL), String.valueOf(LS),
                String.valueOf(PS)}) {
                assertKein(a, a.gueltig + "," + umbruch + a.kw);
                assertKein(a, a.gueltig + ", " + umbruch + " " + a.kw);
            }
        }
    }

    @Test
    void ak18_kein_klammerVorDemKommaOderZweiKlammern() {
        for (Art a : ARTEN) {
            assertKein(a, a.gueltig + "(, " + a.kw + ")");
            assertKein(a, a.gueltig + " [; " + a.kw + "]");
            assertKein(a, a.gueltig + ", ((" + a.kw + ")");
            assertKein(a, a.gueltig + ", ([" + a.kw + "])");
            assertKein(a, a.gueltig + "( ," + a.kw);
        }
    }

    @Test
    void ak18_bestandOhneKommaBleibtOhneFund() {
        for (Art a : ARTEN) {
            assertKein(a, a.gueltig + " und " + a.kw);
            assertKein(a, a.gueltig + " / " + a.kw);
        }
    }

    @Test
    void ak18_fundHinterDemKontextwortBleibtUndDerNeueFundKommtDazu() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + ", " + a.kw + " " + a.gueltig2, new Erw(a.gueltig, KOMMA),
                    new Erw(a.gueltig2, a.sicher));
        }
    }

    // ------------------------------------------------------------------ AK-19 (Teil Komma)

    @Test
    void ak19_kommaVorEinerAnderenFolgeOhneKontextwort_keinFund() {
        for (Art a : ARTEN) {
            assertKein(a, a.gueltig + ", " + a.gueltig2);
            assertKein(a, a.gueltig + ", x " + a.gueltig2);
        }
    }

    // ------------------------------------------------------------------ AK-20

    @Test
    void ak20_bestandOhneKommaBleibtUnveraendert() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, "", a.gueltig, " (" + a.kw + ")");
            wahrscheinlich(a, "", a.gueltig, " " + a.kw);
            sicher(a, a.kw + " ", a.gueltig, "");
            assertFolge(a, a.kw + " " + a.gueltig + ", " + a.gueltig2, new Erw(a.gueltig, a.sicher),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
        }
    }

    @Test
    void ak20_kommaVorKontextwortDasSelbstSchonErschliesst_ersterKontextGewinnt() {
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + " " + a.gueltig + ", " + a.kw + " " + a.gueltig2, new Erw(a.gueltig, a.sicher),
                    new Erw(a.gueltig2, a.sicher));
            assertFolge(a, a.kw + " " + a.gueltig + ", " + a.kw, new Erw(a.gueltig, a.sicher));
        }
    }

    // ------------------------------------------------------------------ AK-21 (Erkennerebene)

    @Test
    void ak21_lanrErkennerLiefertKandidatHinterIkMitKommaUndLanr() {
        assertFolge(LANR_ART, "IK " + LANR_ART.gueltig + ", LANR", new Erw(LANR_ART.gueltig, KOMMA));
        assertFolge(IK_ART, "LANR " + IK_ART.gueltig + ", IK", new Erw(IK_ART.gueltig, KOMMA));
    }

    // ------------------------------------------------------------------ AK-24 Laufzeit (Erkennerebene)

    @Test
    void ak24_laufzeit_nummerMitKommaOhneKontextwort_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "", a.gueltig + ", ", "", 0, "0 Funde: kein Kontextwort");
        }
    }

    @Test
    void ak24_laufzeit_nummernMitKommaUndKontextwortAmEnde_alleFunde() {
        for (Art a : ARTEN) {
            String baustein = a.gueltig + ", ";
            String schluss = a.kw;
            int r = wiederholungen(1_000_000, "", baustein, schluss);
            laufzeit(a, "", baustein, schluss, r, r + " Funde: je Wiederholung eine Nummer, alle 0,60");
        }
    }

    @Test
    void ak24_laufzeit_nummerKommaKontextwortWiederholt_einFundJeNummer() {
        for (Art a : ARTEN) {
            String baustein = a.gueltig + ", " + a.kw + " ";
            int r = wiederholungen(1_000_000, "", baustein, "");
            laufzeit(a, "", baustein, "", r, r + " Funde: erste 0,60, die uebrigen direkt");
            List<Finding> klein = scan(a, baustein.repeat(3));
            assertEquals(3, klein.size());
            assertEquals(KOMMA, klein.get(0).confidence());
            assertEquals(a.sicher, klein.get(1).confidence());
            assertEquals(a.sicher, klein.get(2).confidence());
        }
    }
}
