package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.kv.KvTestHilfen.Art;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests zu gesperrt geschriebenen Kontextwoertern (Feature kv-kontext-luecken, Task T-3 ab drei Buchstaben, D-1,
 * D-2, D-4, D-6, D-7, Grenze 14; AK-1 bis AK-3, AK-7 bis AK-10, AK-12, AK-13, AK-19 (LANR), AK-24 Laufzeit; Task T-4
 * das Zwei-Buchstaben-Wort {@code I K}, D-3, AK-4 bis AK-6, AK-9 (IK), AK-10, AK-11, AK-13, AK-19 (IK), Abschnitt am
 * Ende der Klasse). Nummern stammen aus {@code SynthetischeKvNummern}; jeder Text geht durch den Waechter und die
 * Differenzpruefung gegen den Vorgaenger in {@link KvTestHilfen#scan}. Je Art wird das kuerzeste gesperrt schreibbare
 * Kontextwort ab drei Buchstaben benutzt ({@code LANR}, {@code IKNR}). Die Quelle der {@code I K}-Funde wird aus der
 * Nummer abgeleitet, nie aus der Konfidenz (gueltige IK unter {@code I K}: 0,50 und Quelle {@code ik}).
 */
class KvSperrschriftTest {

    private static final double SPERR = 0.80;
    private static final double KOMMA = 0.60;

    /** Alle acht Sperr-Trenner nach D-1. */
    private static final String[] TRENNER = {" ", ".", "-", String.valueOf(NBSP), String.valueOf(DUENN),
        String.valueOf(SCHMAL_NBSP), String.valueOf(GESCH_BINDESTRICH), String.valueOf(HALBGEVIERT)};

    /** Buchstaben des Wortes, je durch {@code trenner} verbunden. */
    private static String sperr(String wort, String trenner) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < wort.length(); i++) {
            if (i > 0) {
                b.append(trenner);
            }
            b.append(wort.charAt(i));
        }
        return b.toString();
    }

    /** Das gesperrt geschriebene Kontextwort der Art, mit Leerzeichen getrennt. */
    private static String sperr(Art a) {
        return sperr(wort(a), " ");
    }

    private static String wort(Art a) {
        return a.lanr ? "LANR" : "IKNR";
    }

    private static void sperrFund(Art a, String vor, String wert, String nach) {
        assertEin(a, vor, wert, nach, SPERR);
    }

    // ------------------------------------------------------------------ AK-1, AK-2, AK-13

    @Test
    void ak1_grundfall_leerzeichen_doppelpunkt_kleinschreibung_wahrscheinlich() {
        for (Art a : ARTEN) {
            String w = wort(a);
            sperrFund(a, sperr(w, " ") + " ", a.gueltig, "");
            sperrFund(a, sperr(w, " ") + ": ", a.gueltig, "");
            sperrFund(a, sperr(w.toLowerCase(), " ") + " ", a.gueltig, "");
        }
    }

    @Test
    void ak1_stufeQuelleUndBereich() {
        for (Art a : ARTEN) {
            String vor = sperr(a) + " ";
            List<Finding> funde = scan(a, vor + a.gueltig + ".");
            assertEquals(1, funde.size(), a.name + " Anzahl");
            Finding f = funde.get(0);
            assertEquals(ConfidenceLevel.WAHRSCHEINLICH, f.level(), a.name + " Stufe");
            assertEquals(a.quelle, f.source(), a.name + " Quelle");
            assertEquals(vor.length(), f.start(), a.name + " Start");
            assertEquals(vor.length() + a.gueltig.length(), f.end(), a.name + " Ende ohne Punkt");
        }
    }

    @Test
    void ak2_alleAchtTrenner_jeFundMit080() {
        for (Art a : ARTEN) {
            for (String t : TRENNER) {
                sperrFund(a, sperr(wort(a), t) + " ", a.gueltig, "");
                sperrFund(a, sperr(wort(a), t) + t, a.gueltig, "");
            }
        }
    }

    @Test
    void ak2_trennerAmEndeUndGemischt() {
        for (Art a : ARTEN) {
            String w = wort(a);
            sperrFund(a, sperr(w, ".") + ". ", a.gueltig, "");
            sperrFund(a, sperr(w, ".") + " ", a.gueltig, "");
            sperrFund(a, sperr(w, "-") + "- ", a.gueltig, "");
            sperrFund(a, sperr(w, "-") + "-", a.gueltig, "");
        }
        sperrFund(LANR_ART, "L.A-N R ", LANR_ART.gueltig, "");
        sperrFund(LANR_ART, "L" + NBSP + "A" + DUENN + "N" + SCHMAL_NBSP + "R" + HALBGEVIERT, LANR_ART.gueltig, "");
        sperrFund(IK_ART, "I.K-N R ", IK_ART.gueltig, "");
    }

    @Test
    void ak13_dasKontextwortUndDerPunktStehenNieImFundbereich() {
        for (Art a : ARTEN) {
            for (String vor : new String[] {sperr(a) + " ", sperr(wort(a), ".") + ". "}) {
                List<Finding> funde = scan(a, vor + a.gueltig + ".");
                assertEquals(1, funde.size());
                assertEquals(a.gueltig, funde.get(0).originalValue(), a.name + " Wert");
                assertEquals(vor.length(), funde.get(0).start(), a.name + " Start");
            }
        }
    }

    @Test
    void ak2_direktAnDerZiffernketteOhneLeerzeichen() {
        for (Art a : ARTEN) {
            sperrFund(a, sperr(a), a.gueltig, "");
            sperrFund(a, sperr(wort(a), ".") + ".", a.gueltig, "");
        }
    }

    // ------------------------------------------------------------------ AK-3

    @Test
    void ak3_laengereKontextwoerter() {
        for (String w : new String[] {"ARZTNR", "ARZTNUMMER", "LANRNR"}) {
            sperrFund(LANR_ART, sperr(w, " ") + " ", LANR_ART.gueltig, "");
            sperrFund(LANR_ART, sperr(w, ".") + ". ", LANR_ART.gueltig, "");
        }
        for (String w : new String[] {"IKNR", "IKNUMMER", "INSTITUTIONSKENNZEICHEN"}) {
            sperrFund(IK_ART, sperr(w, " ") + " ", IK_ART.gueltig, "");
            sperrFund(IK_ART, sperr(w, "-") + " ", IK_ART.gueltig, "");
        }
    }

    @Test
    void ak3_kontextwortDerAnderenArt_keinFund() {
        assertKein(LANR_ART, sperr("IKNR", " ") + " " + LANR_ART.gueltig);
        assertKein(IK_ART, sperr("LANR", " ") + " " + IK_ART.gueltig);
        assertKein(IK_ART, sperr("ARZTNR", " ") + " " + IK_ART.gueltig);
    }

    // ------------------------------------------------------------------ AK-7

    @Test
    void ak7_maximaleFolge_keinFund() {
        for (Art a : ARTEN) {
            String w = wort(a);
            assertKein(a, "A " + sperr(a) + " " + a.gueltig);
            assertKein(a, sperr(a) + " X " + a.gueltig);
            assertKein(a, sperr(w + "X", " ") + " " + a.gueltig);
            assertKein(a, sperr("X" + w, " ") + " " + a.gueltig);
            assertKein(a, sperr(w + w, " ") + " " + a.gueltig);
        }
    }

    @Test
    void ak7_zuKurzOderZerrissen_keinFund() {
        assertKein(LANR_ART, "L A N " + LANR_ART.gueltig);
        assertKein(LANR_ART, "L A N X R " + LANR_ART.gueltig);
        assertKein(LANR_ART, "L A X N R " + LANR_ART.gueltig);
        assertKein(LANR_ART, "L A N N " + LANR_ART.gueltig);
        assertKein(IK_ART, "I K N " + IK_ART.gueltig);
        assertKein(IK_ART, "I K N X " + IK_ART.gueltig);
    }

    @Test
    void bekannteGrenze14a_teilgruppen_keinFund() {
        assertKein(LANR_ART, "LA NR " + LANR_ART.gueltig);
        assertKein(LANR_ART, "L ANR " + LANR_ART.gueltig);
        assertKein(LANR_ART, "LAN R " + LANR_ART.gueltig);
        assertKein(IK_ART, "I KNR " + IK_ART.gueltig);
    }

    @Test
    void ak7_ohneNummerAmEndeUndAllein_keinFundKeineAusnahme() {
        for (Art a : ARTEN) {
            assertKein(a, sperr(a));
            assertKein(a, sperr(a) + " ");
            assertKein(a, "Text " + sperr(a));
            assertKein(a, sperr(a) + ":");
            assertKein(a, sperr(wort(a), ".") + ".");
            assertKein(a, sperr(a) + " Praxis");
        }
    }

    @Test
    void ak7_nummerOhneKontextwortUndKeineNummerNachDerFolge_keinFund() {
        for (Art a : ARTEN) {
            assertKein(a, a.gueltig);
            assertKein(a, "A B C D " + a.gueltig);
        }
    }

    // ------------------------------------------------------------------ 3.2.4 Grenzfaelle (ab drei Buchstaben)

    @Test
    void grenzfall_kein_zweiTrennerKeinTrennerOderAndererTrenner() {
        for (Art a : ARTEN) {
            String w = wort(a);
            assertKein(a, sperr(w, "  ") + " " + a.gueltig);
            assertKein(a, sperr(w, "\t") + " " + a.gueltig);
            assertKein(a, sperr(w, ",") + " " + a.gueltig);
            assertKein(a, sperr(w, "/") + " " + a.gueltig);
            assertKein(a, sperr(w, "_") + " " + a.gueltig);
            assertKein(a, sperr(w, ". ") + " " + a.gueltig);
            assertKein(a, sperr(w, "\n") + " " + a.gueltig);
            assertKein(a, sperr(w, ":") + " " + a.gueltig);
        }
    }

    @Test
    void grenzfall_einZweiterTrennerInDerMitte_beendetDieFolge() {
        assertKein(LANR_ART, "L A  N R " + LANR_ART.gueltig);
        assertKein(LANR_ART, "L A\tN R " + LANR_ART.gueltig);
    }

    @Test
    void grenzfall_tabOderUmbruchHinterDemLetztenBuchstaben_dieFolgeEndetUndGiltAlsKontextwort() {
        for (Art a : ARTEN) {
            sperrFund(a, sperr(a) + "\t", a.gueltig, "");
            sperrFund(a, sperr(a) + "\n", a.gueltig, "");
            sperrFund(a, sperr(a) + "\r\n", a.gueltig, "");
            sperrFund(a, sperr(a) + "=", a.gueltig, "");
        }
    }

    @Test
    void grenzfall_satzendeHinterDerFolge_beendetDenKontext() {
        for (Art a : ARTEN) {
            assertKein(a, sperr(a) + "! " + a.gueltig);
            assertKein(a, sperr(a) + "? " + a.gueltig);
        }
    }

    @Test
    void grenzfall_mischformInGrossUndKleinschreibung() {
        sperrFund(LANR_ART, "l A n R ", LANR_ART.gueltig, "");
        sperrFund(LANR_ART, "L a N r ", LANR_ART.gueltig, "");
        sperrFund(IK_ART, "i K n R ", IK_ART.gueltig, "");
    }

    @Test
    void grenzfall_nichtAsciiEinzelbuchstabenUndKelvinzeichen_keinKontextwort() {
        char ellStrich = (char) 0x0141;
        char erKurz = (char) 0x0154;
        char kelvin = (char) 0x212A;
        char punktlosesI = (char) 0x0131;
        assertKein(LANR_ART, ellStrich + " A N R " + LANR_ART.gueltig);
        assertKein(LANR_ART, "L A N " + erKurz + " " + LANR_ART.gueltig);
        assertKein(IK_ART, "I " + kelvin + " N R " + IK_ART.gueltig);
        assertKein(IK_ART, punktlosesI + " K N R " + IK_ART.gueltig);
        assertKein(IK_ART, zeichen(0x1D5A0) + " K N R " + IK_ART.gueltig);
    }

    @Test
    void grenzfall_folgeMitVierundzwanzigUndMehrBuchstaben_keinKontextwort() {
        String ik = sperr("INSTITUTIONSKENNZEICHEN", " ");
        sperrFund(IK_ART, ik + " ", IK_ART.gueltig, "");
        assertKein(IK_ART, ik + " X " + IK_ART.gueltig);
        assertKein(IK_ART, "X " + ik + " " + IK_ART.gueltig);
        assertKein(IK_ART, sperr("INSTITUTIONSKENNZEICHENX", " ") + " " + IK_ART.gueltig);
        assertKein(IK_ART, sperr("XINSTITUTIONSKENNZEICHEN", " ") + " " + IK_ART.gueltig);
        assertKein(IK_ART, sperr("A".repeat(30), " ") + " " + IK_ART.gueltig);
        assertKein(LANR_ART, sperr("A".repeat(23) + "LANR", " ") + " " + LANR_ART.gueltig);
        assertKein(LANR_ART, sperr("A".repeat(1000) + "LANR", " ") + " " + LANR_ART.gueltig);
    }

    @Test
    void bekannteGrenze14d_unmittelbarWiederholteSperrschrift_eineFolgeKeinKontextwort() {
        for (Art a : ARTEN) {
            assertKein(a, sperr(a) + " " + sperr(a) + " " + a.gueltig);
        }
    }

    @Test
    void grenzfall_wiederholungMitFuellwortDazwischen_funktioniert() {
        for (Art a : ARTEN) {
            sperrFund(a, sperr(a) + " Praxis " + sperr(a) + " ", a.gueltig, "");
        }
    }

    // ------------------------------------------------------------------ AK-8: volle Kontextregeln

    @Test
    void ak8_fuellwoerterBisDreiDannKeinFund() {
        for (Art a : ARTEN) {
            sperrFund(a, sperr(a) + " des Behandlers ", a.gueltig, "");
            sperrFund(a, sperr(a) + " des guten Behandlers ", a.gueltig, "");
            assertKein(a, sperr(a) + " des guten alten Behandlers " + a.gueltig);
        }
    }

    @Test
    void ak8_aufzaehlung_beideMit080() {
        for (Art a : ARTEN) {
            assertFolge(a, sperr(a) + " " + a.gueltig + ", " + a.gueltig2, new Erw(a.gueltig, SPERR),
                    new Erw(a.gueltig2, SPERR));
            assertFolge(a, sperr(a) + " " + a.gueltig + " und " + a.gueltig2, new Erw(a.gueltig, SPERR),
                    new Erw(a.gueltig2, SPERR));
        }
    }

    @Test
    void ak8_nachgestellt_klammerLeerzeichenUndNaechsteNummer() {
        for (Art a : ARTEN) {
            sperrFund(a, "", a.gueltig, " (" + sperr(a) + ")");
            sperrFund(a, "", a.gueltig, " " + sperr(a));
            sperrFund(a, "", a.gueltig, " [" + sperr(a) + "]");
            sperrFund(a, "", a.gueltig, " (" + sperr(wort(a), ".") + ".)");
            assertFolge(a, a.gueltig + " " + a.gueltig2 + " (" + sperr(a) + ")", new Erw(a.gueltig, SPERR),
                    new Erw(a.gueltig2, SPERR));
        }
    }

    @Test
    void ak8_zeilenumbruchErlaubtLeerzeileNicht() {
        for (Art a : ARTEN) {
            sperrFund(a, sperr(a) + ":\n", a.gueltig, "");
            sperrFund(a, sperr(a) + ":\r\n", a.gueltig, "");
            assertKein(a, sperr(a) + ":\n\n" + a.gueltig);
            assertKein(a, sperr(a) + ":\r\n\r\n" + a.gueltig);
        }
    }

    @Test
    void ak8_nachDemKontextAbgegebeneNummerUndNormalesKontextwortSetztDenDeckelZurueck() {
        for (Art a : ARTEN) {
            assertFolge(a, sperr(a) + " " + a.gueltig + " " + a.kw + " " + a.gueltig2, new Erw(a.gueltig, SPERR),
                    new Erw(a.gueltig2, a.sicher));
            assertFolge(a, a.kw + " " + a.gueltig + " " + sperr(a) + " " + a.gueltig2, new Erw(a.gueltig, a.sicher),
                    new Erw(a.gueltig2, SPERR));
        }
    }

    @Test
    void ak8_gesperrtesKontextwortAlsKlammerDannNummerDirekt() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + " (" + sperr(a) + ") " + a.gueltig2, new Erw(a.gueltig, SPERR),
                    new Erw(a.gueltig2, SPERR));
        }
    }

    @Test
    void grenzfall_gesperrtWortAlsGanzesArztnr_istEinKontextwort() {
        sperrFund(LANR_ART, "A r z t N r ", LANR_ART.gueltig, "");
    }

    // ------------------------------------------------------------------ AK-9: Pruefziffer und Laenge

    @Test
    void ak9_ungueltigePruefzifferUndLaengeUnterSperrschrift() {
        for (Art a : ARTEN) {
            assertEin(a, sperr(a) + " ", a.ungueltig, "", PZ_UNGUELTIG);
            assertEin(a, sperr(a) + " ", LAENGE_7, "", LAENGE);
            assertEin(a, sperr(a) + " ", a.ungueltig, "", PZ_UNGUELTIG, a.quellePruefziffer());
            assertEin(a, sperr(a) + " ", LAENGE_7, "", LAENGE, a.quelleLaenge());
            assertEin(a, sperr(a) + " ", gruppiert(a.gueltig, " "), "", SPERR, a.quelle);
            assertEin(a, sperr(a) + " ", gruppiert(a.ungueltig, "-"), "", PZ_UNGUELTIG, a.quellePruefziffer());
        }
    }

    // ------------------------------------------------------------------ AK-10: Nachsatzwort und Vorwort

    @Test
    void ak10_nachsatzwortHinterDerFolge_direktImKontext() {
        for (Art a : ARTEN) {
            sperrFund(a, sperr(a) + "-Nummer ", a.gueltig, "");
            sperrFund(a, sperr(a) + "-Nr. ", a.gueltig, "");
            sperrFund(a, sperr(a) + " Nr. ", a.gueltig, "");
            sperrFund(a, sperr(a) + " Nummer: ", a.gueltig, "");
            sperrFund(a, sperr(wort(a), ".") + ".-Nr. ", a.gueltig, "");
            sperrFund(a, sperr(a) + HALBGEVIERT + "Nr ", a.gueltig, "");
        }
    }

    @Test
    void ak10_nachsatzwortLaesstDieNaechsteNummerDirekt_keinFuellwort() {
        for (Art a : ARTEN) {
            // mit dem Nachsatzwort bleiben drei Fuellwoerter uebrig, ohne es waeren es vier
            sperrFund(a, sperr(a) + "-Nr. ein guter alter ", a.gueltig, "");
            assertKein(a, sperr(a) + "-Nr. ein sehr guter alter " + a.gueltig);
        }
    }

    @Test
    void bekannteGrenze14b_gesperrtesVorwortUndNachsatzwort() {
        assertKein(LANR_ART, "A r z t - N r " + LANR_ART.gueltig);
        // Hinweis: gesperrtes Nachsatzwort zaehlt als zwei Fuellwoerter, der Kontext lanr bleibt erhalten
        sperrFund(LANR_ART, "L A N R - N r ", LANR_ART.gueltig, "");
        sperrFund(IK_ART, "I K N R - N r ", IK_ART.gueltig, "");
    }

    // ------------------------------------------------------------------ AK-19 (LANR) und offene Segmente

    @Test
    void ak19_nummerKommaGesperrtesKontextwort_060() {
        for (Art a : ARTEN) {
            assertEin(a, "", a.gueltig, ", " + sperr(a), KOMMA);
            assertEin(a, "", a.gueltig, "; " + sperr(a), KOMMA);
            assertEin(a, "", a.gueltig, ", (" + sperr(a) + ")", KOMMA);
            assertEin(a, "", a.gueltig, " ; [" + sperr(wort(a), ".") + ".]", KOMMA);
            assertEin(a, "", a.ungueltig, ", " + sperr(a), PZ_UNGUELTIG);
            assertEin(a, "", LAENGE_7, ", " + sperr(a), LAENGE);
        }
    }

    @Test
    void ak19_folgeOffenerNummernMitKommaVorDerSperrschrift() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + ", " + a.gueltig2 + ", " + sperr(a), new Erw(a.gueltig, KOMMA),
                    new Erw(a.gueltig2, KOMMA));
            assertFolge(a, a.gueltig + " und " + a.gueltig2 + ", " + sperr(a), new Erw(a.gueltig, KOMMA),
                    new Erw(a.gueltig2, KOMMA));
        }
    }

    @Test
    void grenzfall_abbildDerOffenenSegmente_klammerUndOhneKomma() {
        for (Art a : ARTEN) {
            sperrFund(a, "", a.gueltig, " (" + sperr(a) + ")");
            sperrFund(a, "", a.gueltig, " " + sperr(a));
            sperrFund(a, "", a.gueltig, "\t" + sperr(a));
            sperrFund(a, "", a.gueltig, "(" + sperr(a) + ")");
        }
    }

    @Test
    void grenzfall_nachgestelltMitUndOderSchraegstrichOderDoppelKomma_keinFundDerNummerVorher() {
        for (Art a : ARTEN) {
            assertKein(a, a.gueltig + " und " + sperr(a));
            assertKein(a, a.gueltig + " / " + sperr(a));
            assertKein(a, a.gueltig + ", und " + sperr(a));
            assertKein(a, a.gueltig + ",, " + sperr(a));
            assertKein(a, a.gueltig + ", / " + sperr(a));
            assertKein(a, a.gueltig + "(, " + sperr(a) + ")");
            assertKein(a, a.gueltig + ", ((" + sperr(a) + ")");
            assertKein(a, a.gueltig + ": " + sperr(a));
            assertKein(a, a.gueltig + ". " + sperr(a));
            assertKein(a, a.gueltig + ", Fax " + sperr(a));
            assertKein(a, a.gueltig + ",\n" + sperr(a));
        }
    }

    @Test
    void grenzfall_nachgestelltUndDanachNochEineNummerDirekt() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + " " + sperr(a) + " " + a.gueltig2, new Erw(a.gueltig, SPERR),
                    new Erw(a.gueltig2, SPERR));
            assertFolge(a, a.gueltig + ", " + sperr(a) + " " + a.gueltig2, new Erw(a.gueltig, KOMMA),
                    new Erw(a.gueltig2, SPERR));
        }
    }

    @Test
    void grenzfall_umfeld_wortDavorBindestrichPraefixUndSatz() {
        for (Art a : ARTEN) {
            sperrFund(a, "Praxis " + sperr(a) + " ", a.gueltig, "");
            sperrFund(a, "Praxis-" + sperr(wort(a), ".") + ". ", a.gueltig, "");
            sperrFund(a, "Rechnung an " + sperr(wort(a), ".") + ". ", a.gueltig, " gesendet");
        }
    }

    @Test
    void grenzfall_kontextwortDasVorDerFolgeEndet_beendetKeinenFremdenKontext() {
        for (Art a : ARTEN) {
            // normaler Kontext ist durch die vier Einzelbuchstaben schon beendet; die Folge eroeffnet einen neuen
            assertFolge(a, a.kw + " Praxis " + sperr(a) + " " + a.gueltig, new Erw(a.gueltig, SPERR));
        }
    }

    // ------------------------------------------------------------------ AK-12: unsichtbare Zeichen

    @Test
    void ak12_unsichtbareZeichenZwischenDenBuchstaben_bestandBleibtDirekt() {
        String lanrFolge = "L" + ZWSP + "A" + ZWSP + "N" + ZWSP + "R ";
        assertEin(LANR_ART, lanrFolge, LANR_ART.gueltig, "", LANR_ART.sicher);
        assertEin(IK_ART, "I" + SHY + "K ", IK_ART.gueltig, "", IK_ART.sicher);
        assertEin(IK_ART, "I" + ZWSP + "K" + ZWSP + "N" + ZWSP + "R ", IK_ART.gueltig, "", IK_ART.sicher);
    }

    @Test
    void ak12_unsichtbaresZeichenNebenEinemTrenner_fundMit080() {
        sperrFund(LANR_ART, "L" + ZWSP + " A N R ", LANR_ART.gueltig, "");
        sperrFund(LANR_ART, "L" + ZWSP + " " + ZWSP + "A N R ", LANR_ART.gueltig, "");
        sperrFund(LANR_ART, "L " + ZWSP + "A N R ", LANR_ART.gueltig, "");
        sperrFund(LANR_ART, "L A N R" + ZWSP + " ", LANR_ART.gueltig, "");
        sperrFund(LANR_ART, "L" + SHY + "." + "A" + SHY + "." + "N" + SHY + "." + "R" + SHY + ". ",
                LANR_ART.gueltig, "");
        sperrFund(IK_ART, "I K N" + ZWSP + " R ", IK_ART.gueltig, "");
    }

    @Test
    void ak12_durchsichtigerLaufZwischenZweiTrennern_keinFund() {
        assertKein(LANR_ART, "L " + ZWSP + " A N R " + LANR_ART.gueltig);
        assertKein(LANR_ART, "L A " + ZWSP + " N R " + LANR_ART.gueltig);
        assertKein(IK_ART, "I K N " + ZWSP + " R " + IK_ART.gueltig);
    }

    @Test
    void ak12_unsichtbaresZeichenZwischenZweiBuchstaben_teilgruppe_keinFund() {
        assertKein(LANR_ART, "L" + ZWSP + "A N R " + LANR_ART.gueltig);
        assertKein(LANR_ART, "L A N" + ZWSP + "R " + LANR_ART.gueltig);
    }

    @Test
    void ak12_unsichtbaresZeichenVorDemNachsatzwort() {
        for (Art a : ARTEN) {
            sperrFund(a, sperr(a) + ZWSP + "-Nr. ", a.gueltig, "");
            sperrFund(a, sperr(a) + "-" + ZWSP + "Nummer ", a.gueltig, "");
            sperrFund(a, sperr(a) + "-N" + ZWSP + "r ", a.gueltig, "");
        }
    }

    @Test
    void ak12_unsichtbareZeichenVorDerNummerOderAmFolgenende() {
        for (Art a : ARTEN) {
            sperrFund(a, sperr(a) + " " + ZWSP, a.gueltig, "");
            sperrFund(a, sperr(a) + ZWSP + SHY + " ", a.gueltig, "");
            sperrFund(a, ZWSP + sperr(a) + " ", a.gueltig, "");
        }
    }

    @Test
    void ak12_kombinationszeichenAmEinzelbuchstaben_istKeinEinzelbuchstabe() {
        assertKein(LANR_ART, "L" + AKUT + " A N R " + LANR_ART.gueltig);
        assertKein(LANR_ART, "L A N R" + AKUT + " " + LANR_ART.gueltig);
        assertKein(LANR_ART, "L A " + AKUT + " N R " + LANR_ART.gueltig);
    }

    @Test
    void ak12_invarianzGegenEingestreuteUnsichtbareZeichen_gleicheFundwerte() {
        for (Art a : ARTEN) {
            String[] basis = {sperr(a) + " " + a.gueltig, sperr(wort(a), ".") + ". " + a.gueltig + ".",
                a.gueltig + ", " + sperr(a), "X " + sperr(a) + " Praxis " + a.ungueltig,
                sperr(a) + " " + a.gueltig + " " + sperr(a) + " " + a.gueltig2, "A L A N R " + a.gueltig,
                sperr(wort(a) + "X", " ") + " " + a.gueltig};
            for (int t = 0; t < basis.length; t++) {
                String text = basis[t];
                List<Finding> ohne = scan(a, text);
                // an jeder Stelle ausser innerhalb der Ziffernketten ein unsichtbares Zeichen einstreuen
                for (int p = 0; p <= text.length(); p++) {
                    if (p > 0 && p < text.length() && istZiffer(text.charAt(p - 1)) && istZiffer(text.charAt(p))) {
                        continue;
                    }
                    String mit = text.substring(0, p) + ZWSP + text.substring(p);
                    List<Finding> funde = scan(a, mit);
                    assertEquals(ohne.size(), funde.size(), a.name + " Fundzahl bei Einstreuung an Stelle " + p
                            + " in Text " + t);
                    for (int k = 0; k < ohne.size(); k++) {
                        assertEquals(ohne.get(k).originalValue(), funde.get(k).originalValue());
                        assertEquals(ohne.get(k).confidence(), funde.get(k).confidence(), a.name + " Konfidenz");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Aufzaehlung mehrerer Folgen im Text

    @Test
    void grenzfall_mehrereFolgenHintereinanderImText() {
        for (Art a : ARTEN) {
            assertFolge(a, sperr(a) + " " + a.gueltig + "\n" + sperr(a) + " " + a.gueltig2,
                    new Erw(a.gueltig, SPERR), new Erw(a.gueltig2, SPERR));
            assertFolge(a, sperr(a) + " " + a.gueltig + ", X, " + sperr(a) + " " + a.gueltig2,
                    new Erw(a.gueltig, SPERR), new Erw(a.gueltig2, SPERR));
        }
    }

    // ------------------------------------------------------------------ AK-24 Laufzeit (Erkennerebene)

    @Test
    void ak24_laufzeit_einBuchstabeUndLeerzeichenWiederholt_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "", "L ", "", 0, "0 Funde: eine einzige Einzelbuchstabenfolge ohne Kontextwort");
        }
    }

    @Test
    void ak24_laufzeit_initialenMitPunktWiederholt_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "", "I.", "", 0, "0 Funde: eine einzige Folge ohne Kontextwort");
        }
    }

    @Test
    void ak24_laufzeit_gesperrtesWortMitBindestrichenWiederholt_eineFolgeKeinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "", sperr(wort(a), "-") + " ", "", 0, "0 Funde: eine einzige Folge, nie ein Wort");
        }
    }

    @Test
    void ak24_laufzeit_gesperrtesWortMitNummerWiederholt_jedeNummerEinFund() {
        for (Art a : ARTEN) {
            String baustein = sperr(a) + " " + a.gueltig + " ";
            assertEquals(18, baustein.length(), a.name + " Bausteinlaenge");
            int r = wiederholungen(1_000_000, "", baustein, "");
            assertEquals(55_556, r, a.name + " Wiederholungen: aufgerundet 1 000 000 / 18");
            laufzeit(a, "", baustein, "", r, r + " Funde: je Wiederholung eine Nummer, alle 0,80");
            List<Finding> klein = scan(a, baustein.repeat(4));
            assertEquals(4, klein.size());
            for (Finding f : klein) {
                assertEquals(SPERR, f.confidence(), a.name + " Konfidenz");
            }
        }
    }

    @Test
    void ak24_laufzeit_einBuchstabeUndEineMillionLeerzeichen_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "L", " ", "", 0, "0 Funde: Einzelbuchstabe, danach nur Leerraum");
        }
    }

    @Test
    void ak24_laufzeit_gesperrtesWortUndEineMillionLeerzeichen_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, sperr(a), " ", "", 0, "0 Funde: Kontextwort ohne Nummer");
        }
    }

    @Test
    void ak24_laufzeit_einBuchstabeUndPunktBuchstabeWiederholt_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "L", ".A", "", 0, "0 Funde: Einzelbuchstabenkette ohne Wort");
        }
    }

    @Test
    void ak24_laufzeit_vorgriffUeberLaufMitVielenUnsichtbarenZeichen_keinFund() {
        for (Art a : ARTEN) {
            String baustein = "A " + String.valueOf(ZWSP).repeat(1000) + "Bc ";
            laufzeit(a, "", baustein, "", 0, "0 Funde: der Vorgriff liest jeden Lauf hoechstens zweimal");
        }
    }

    @Test
    void ak24_laufzeit_einEinzigerLaufMitEineMillionUnsichtbarenZeichen_keinFund() {
        for (Art a : ARTEN) {
            String text = "A " + String.valueOf(ZWSP).repeat(1_000_000) + "Bc " + sperr(a);
            assertKeinGueltigesNeunerfensterOhne00(text, "Waechter Laufzeit");
            a.erkenner.detect("A " + String.valueOf(ZWSP).repeat(10_000) + "Bc");
            int ist = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> a.erkenner.detect(text).size(),
                    a.name + " Laufzeitgrenze 2 s ueberschritten");
            assertEquals(0, ist, a.name + " 0 Funde");
        }
    }

    @Test
    void ak24_laufzeit_einzelbuchstabenMitUnsichtbarenZeichenWiederholt_keinFund() {
        for (Art a : ARTEN) {
            String baustein = "A" + ZWSP + " ";
            laufzeit(a, "", baustein, "", 0, "0 Funde: eine Folge, nie ein Wort");
            assertTrue(scan(a, baustein.repeat(30)).isEmpty());
        }
    }

    @Test
    void ak24_laufzeit_nummerOhneKontextMitEinzelbuchstabenDavor_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "", "A " + a.gueltig + " ", "", 0, "0 Funde: kein Kontextwort");
        }
    }

    @Test
    void ak24_laufzeit_nummerKommaUndGesperrtesWortAmEnde_alleFunde() {
        for (Art a : ARTEN) {
            String baustein = a.gueltig + ", ";
            String schluss = sperr(a);
            int r = wiederholungen(1_000_000, "", baustein, schluss);
            laufzeit(a, "", baustein, schluss, r, r + " Funde: alle Nummern der offenen Folge, je 0,60");
        }
    }

    // ------------------------------------------------------------------ T-4: Zwei-Buchstaben-Wort I K (D-3)

    private static final double ZWEI = 0.50;
    private static final Art IK = IK_ART;
    private static final String IKN = IK_ART.gueltig;
    private static final String IKN2 = IK_ART.gueltig2;

    /** Genau ein Fund hinter {@code vor} mit 0,50 und der Quelle aus der Nummer. */
    private static void zwei(String vor, String wert, String nach) {
        assertEin(IK, vor, wert, nach, ZWEI, IK.quelleNachNummer(wert));
    }

    /** Funde in Reihenfolge; Quelle aus der Nummer, nicht aus der Konfidenz. */
    private static void folgeNachNummer(String text, Erw... erwartet) {
        List<Finding> funde = scan(IK, text);
        assertEquals(erwartet.length, funde.size(), "Anzahl Funde");
        int ab = 0;
        for (int i = 0; i < erwartet.length; i++) {
            int start = text.indexOf(erwartet[i].wert(), ab);
            Finding f = funde.get(i);
            assertEquals(start, f.start(), "Start " + i);
            assertEquals(start + erwartet[i].wert().length(), f.end(), "Ende " + i);
            assertEquals(erwartet[i].konfidenz(), f.confidence(), "Konfidenz " + i);
            assertEquals(IK.quelleNachNummer(erwartet[i].wert()), f.source(), "Quelle " + i);
            ab = f.end();
        }
    }

    @Test
    void ak4_grundfall_leerzeichenDoppelpunktPunkteBindestrichKleinschreibung_fraglich050() {
        zwei("I K ", IKN, "");
        zwei("I K: ", IKN, "");
        zwei("I.K. ", IKN, "");
        zwei("I.K.: ", IKN, "");
        zwei("I-K ", IKN, "");
        zwei("i k ", IKN, "");
        zwei("I K=", IKN, "");
        zwei("I K", IKN, "");
    }

    @Test
    void ak4_alleAchtTrenner() {
        for (String t : TRENNER) {
            zwei(sperr("IK", t) + " ", IKN, "");
            zwei(sperr("IK", t) + t, IKN, "");
        }
        assertKein(IK, "I.-K " + IKN);
    }

    @Test
    void ak4_stufeQuelleUndBereich_gueltigeIkHatQuelleIkUndFraglich() {
        String vor = "I K ";
        List<Finding> funde = scan(IK, vor + IKN + ".");
        assertEquals(1, funde.size());
        Finding f = funde.get(0);
        assertEquals(ConfidenceLevel.FRAGLICH, f.level());
        assertEquals("ik", f.source());
        assertEquals(ZWEI, f.confidence());
        assertEquals(vor.length(), f.start());
        assertEquals(vor.length() + IKN.length(), f.end(), "kein Punkt im Fund (AK-13)");
    }

    @Test
    void ak9_ungueltigeIkUndLaengeUnterIK_quellenWieImBestand() {
        assertEin(IK, "I K ", IK.ungueltig, "", PZ_UNGUELTIG, IK.quellePruefziffer());
        assertEin(IK, "I.K. ", IK.ungueltig, "", PZ_UNGUELTIG, IK.quellePruefziffer());
        assertEin(IK, "I K ", LAENGE_7, "", LAENGE, IK.quelleLaenge());
        assertEin(IK, "I K ", gruppiert(IKN, " "), "", ZWEI, "ik");
        assertEin(IK, "I K ", gruppiert(IK.ungueltig, "-"), "", PZ_UNGUELTIG, IK.quellePruefziffer());
        // gleiche Konfidenz 0,50, aber unterscheidbar nur ueber die Quelle (Plan Hinweis 1)
        assertEquals(scan(IK, "I K " + IKN).get(0).confidence(), scan(IK, "I K " + IK.ungueltig).get(0).confidence());
        assertNotEquals(scan(IK, "I K " + IKN).get(0).source(), scan(IK, "I K " + IK.ungueltig).get(0).source());
    }

    @Test
    void ak5_nachsatzwortIstErlaubt() {
        zwei("I K-Nr. ", IKN, "");
        zwei("I K Nr. ", IKN, "");
        zwei("I.K.-Nr. ", IKN, "");
        zwei("I K-Nummer ", IKN, "");
        zwei("I K Nummer: ", IKN, "");
        zwei("I K" + HALBGEVIERT + "Nr ", IKN, "");
    }

    @Test
    void ak10_nachsatzwortMitUnsichtbaremZeichen() {
        zwei("I K" + ZWSP + "-Nr. ", IKN, "");
        zwei("I K" + ZWSP + " ", IKN, "");
        zwei("I K-" + ZWSP + "Nummer ", IKN, "");
        zwei("I K-N" + ZWSP + "r ", IKN, "");
    }

    @Test
    void ak5_nurDieUnmittelbarFolgendeNummer_keineAufzaehlung() {
        folgeNachNummer("I K " + IKN + " und " + IKN2, new Erw(IKN, ZWEI));
        folgeNachNummer("I K " + IKN + ", " + IKN2, new Erw(IKN, ZWEI));
        folgeNachNummer("I K " + IKN + " " + IKN2, new Erw(IKN, ZWEI));
        folgeNachNummer("I K " + IKN + "; " + IKN2 + "; " + IK.gueltig3, new Erw(IKN, ZWEI));
    }

    @Test
    void ak5_fuellwoerterBeendenDenKontext_keinFund() {
        assertKein(IK, "I K der Praxis " + IKN);
        assertKein(IK, "I K Praxis " + IKN);
        assertKein(IK, "I K EA " + IKN);
        assertKein(IK, "I K Nr. Praxis " + IKN);
        assertKein(IK, "I K a b c d " + IKN);
    }

    @Test
    void ak5_zeilenumbruchBeendetDenKontext_keinFund() {
        assertKein(IK, "I K\n" + IKN);
        assertKein(IK, "I K\r\n" + IKN);
        assertKein(IK, "I K\r" + IKN);
        assertKein(IK, "I K:\n" + IKN);
        assertKein(IK, "I K" + LS + IKN);
        assertKein(IK, "I K\n\n" + IKN);
    }

    @Test
    void ak5_satzendeBeendetDenKontext_keinFund() {
        assertKein(IK, "I K! " + IKN);
        assertKein(IK, "I K? " + IKN);
    }

    @Test
    void ak5_nachgestellt_keinFund() {
        assertKein(IK, IKN + " (I K)");
        assertKein(IK, IKN + " I K");
        assertKein(IK, IKN + " [I.K.]");
        assertKein(IK, IKN + " I-K");
        assertKein(IK, IKN + " " + IKN2 + " (I K)");
    }

    @Test
    void ak19_nummerKommaIK_keinFund() {
        assertKein(IK, IKN + ", I K");
        assertKein(IK, IKN + "; I K");
        assertKein(IK, IKN + ", (I K)");
        assertKein(IK, IKN + ", " + IKN2 + ", I K");
        assertKein(IK, IKN + " und " + IKN2 + ", I.K.");
    }

    @Test
    void ak6_initialenMitPunktUndLeerzeichen_keinFund() {
        assertKein(IK, "Prof. I. K. Mustermann, Rechnung " + IKN);
        assertKein(IK, "I. K. Mustermann, Rechnung " + IKN);
        assertKein(IK, "I. K. " + IKN);
        assertKein(IK, "I. K " + IKN);
        zwei("I K. ", IKN, "");
        assertKein(IK, "Dr. I. K. " + IKN);
    }

    @Test
    void ak6_maximaleFolge_keinFund() {
        assertKein(IK, "A I K " + IKN);
        assertKein(IK, "I K E A " + IKN);
        assertKein(IK, "I K N " + IKN);
        assertKein(IK, "X Y I K " + IKN);
        assertKein(IK, "Herr A I K " + IKN);
        assertKein(IK, "I K I " + IKN);
        assertKein(IK, "I K K " + IKN);
    }

    @Test
    void ak6_keinOderDoppelterTrenner_keinFund() {
        assertKein(IK, "I  K " + IKN);
        assertKein(IK, "I\tK " + IKN);
        assertKein(IK, "I\nK " + IKN);
        assertKein(IK, "I\r\nK " + IKN);
        assertKein(IK, "I,K " + IKN);
        assertKein(IK, "I/K " + IKN);
        assertKein(IK, "I_K " + IKN);
        assertKein(IK, "I. K " + IKN);
        assertKein(IK, "I .K " + IKN);
        assertKein(IK, "I:K " + IKN);
        assertKein(IK, "I;K " + IKN);
    }

    @Test
    void ak6_nummerNichtUnmittelbarFolgend_keinFund() {
        assertKein(IK, "I K Rechnung Nr " + IKN);
        assertKein(IK, "I K eins zwei drei vier " + IKN);
        assertKein(IK, "I K, danach " + IKN);
        assertKein(IK, "I K: der Praxis " + IKN);
    }

    @Test
    void ak6_keineNummerOderNurTextende_keinFund() {
        assertKein(IK, "I K");
        assertKein(IK, "I K ");
        assertKein(IK, "I.K.");
        assertKein(IK, "Text I K");
        assertKein(IK, "I K:");
        assertKein(IK, "I K Praxis");
    }

    @Test
    void ak6_nichtAsciiBuchstabenUndKelvin_keinKontextwort() {
        char kelvin = (char) 0x212A;
        char punktlosesI = (char) 0x0131;
        assertKein(IK, "I " + kelvin + " " + IKN);
        assertKein(IK, punktlosesI + " K " + IKN);
        assertKein(IK, "I K" + AKUT + " " + IKN);
        assertKein(IK, "I" + AKUT + " K " + IKN);
    }

    @Test
    void ak11_wortDavorBindestrichPraefixUndSatz() {
        zwei("Praxis I K ", IKN, "");
        zwei("Praxis-I.K. ", IKN, "");
        zwei("Rechnung an I.K. ", IKN, " gesendet");
        zwei("Rechnung an I.K. ", IKN, ".");
        zwei("(I K) ", IKN, "");
    }

    @Test
    void ak11_wasHinterDerNummerFolgtBleibtImText() {
        List<Finding> funde = scan(IK, "Rechnung an I.K. " + IKN + " gesendet");
        assertEquals(1, funde.size());
        assertEquals(IKN, funde.get(0).originalValue());
    }

    @Test
    void ak5_nachDerNummerSetztDerNaechsteKontextNeu() {
        folgeNachNummer("I K " + IKN + " " + IK.kw + " " + IKN2, new Erw(IKN, ZWEI), new Erw(IKN2, IK.sicher));
        folgeNachNummer("I K " + IKN + "\nI K " + IKN2, new Erw(IKN, ZWEI), new Erw(IKN2, ZWEI));
        folgeNachNummer("I K " + IKN + " Praxis I.K. " + IKN2, new Erw(IKN, ZWEI), new Erw(IKN2, ZWEI));
    }

    @Test
    void hinweis6_laeuftBereitsEinNormalerKontext_behaeltDieNummerDessenKonfidenz() {
        assertEin(IK, "IK: Praxis I K ", IKN, "", WAHRSCHEINLICH, "ik");
        assertEin(IK, "IK Praxis I K ", IKN, "", WAHRSCHEINLICH, "ik");
        assertEin(IK, "IK I K ", IKN, "", WAHRSCHEINLICH, "ik");
        assertEin(IK, "IK: I.K. ", IKN, "", WAHRSCHEINLICH, "ik");
        assertEin(IK, "Institutionskennzeichen: I K ", IKN, "", WAHRSCHEINLICH, "ik");
    }

    @Test
    void ak20_direkterBestandsfundWirdNieDurchEinDanebenstehendesIKGesenkt() {
        assertEin(IK, "IK ", IKN, " I K", IK.sicher, "ik");
        assertEin(IK, "I K IK ", IKN, "", IK.sicher, "ik");
        assertEin(IK, "I K: IK-Nr. ", IKN, "", IK.sicher, "ik");
        folgeNachNummer("IK " + IKN + ", I K " + IKN2, new Erw(IKN, IK.sicher), new Erw(IKN2, WAHRSCHEINLICH));
        folgeNachNummer("IK " + IKN + " I K " + IKN2, new Erw(IKN, IK.sicher), new Erw(IKN2, WAHRSCHEINLICH));
        folgeNachNummer("I K " + IKN + " IK " + IKN2, new Erw(IKN, ZWEI), new Erw(IKN2, IK.sicher));
    }

    @Test
    void bekannteGrenze14g_nummerVonIKUndNachgestelltemKontextwort_050StattBisher080() {
        String text = "I K " + IKN + " (IK)";
        List<Finding> alt = IK.vorgaenger.detect(text);
        assertEquals(1, alt.size());
        assertEquals(WAHRSCHEINLICH, alt.get(0).confidence(), "Vorgaenger: nachgestellter Kontext 0,80");
        assertEquals("ik", alt.get(0).source());
        zwei("I K ", IKN, " (IK)");
        zwei("I K ", IKN, " IK");
        // ungueltige Nummer: war und bleibt 0,50, gleiche Abdeckung
        assertEin(IK, "I K ", IK.ungueltig, " (IK)", PZ_UNGUELTIG, IK.quellePruefziffer());
    }

    @Test
    void bekannteGrenze14g_weitereFormen_050StattBisher_positionBleibtAbgedeckt() {
        // Aufzaehlung: nur die von I K direkt erschlossene Nummer sinkt, die zweite bleibt 0,80
        String aufzaehlung = "I K " + IKN + " " + IKN2 + " IK";
        folgeNachNummer(aufzaehlung, new Erw(IKN, ZWEI), new Erw(IKN2, WAHRSCHEINLICH));
        assertAbdeckungGleich(aufzaehlung);
        // ein Komma vor dem nachgestellten Kontextwort: Stand nach T-2 war 0,60; der eingefrorene Vorgaenger ist
        // aelter als T-2 und kennt die Komma-Regel nicht; die Position bleibt abgedeckt, jetzt mit 0,50
        String komma = "I K " + IKN + ", IK";
        folgeNachNummer(komma, new Erw(IKN, ZWEI));
        // danach ein weiteres Kontextwort mit eigener Nummer: 0,92
        String folge = "I K " + IKN + " IK " + IKN2;
        folgeNachNummer(folge, new Erw(IKN, ZWEI), new Erw(IKN2, IK.sicher));
        assertAbdeckungGleich(folge);
    }

    @Test
    void normalerKontextNachIKKontextGiltWiederVoll() {
        // I K N1 IK N2, N3: N1 0,50 (I K), N2 0,92 (direkt hinter IK), N3 0,80 (Aufzaehlung)
        folgeNachNummer("I K " + IKN + " IK " + IKN2 + ", " + IK.gueltig3,
                new Erw(IKN, ZWEI), new Erw(IKN2, IK.sicher), new Erw(IK.gueltig3, WAHRSCHEINLICH));
    }

    /** Jede vom Vorgaenger abgedeckte Position bleibt abgedeckt (Fundbereiche gleich, nur Konfidenz anders). */
    private static void assertAbdeckungGleich(String text) {
        List<Finding> alt = IK.vorgaenger.detect(text);
        List<Finding> neu = scan(IK, text);
        assertEquals(alt.size(), neu.size(), "Anzahl Funde alt gegen neu (Textlaenge " + text.length() + ")");
        for (int i = 0; i < alt.size(); i++) {
            assertEquals(alt.get(i).start(), neu.get(i).start(), "Start " + i);
            assertEquals(alt.get(i).end(), neu.get(i).end(), "Ende " + i);
        }
    }

    @Test
    void bekannteGrenze14f_ikOhneFuellwoerterAufzaehlungUmbruchUndNachgestelltenKontext() {
        assertKein(IK, "I K Praxis " + IKN);
        assertKein(IK, "I K\n" + IKN);
        assertKein(IK, IKN + " I K");
        folgeNachNummer("I K " + IKN + ", " + IKN2, new Erw(IKN, ZWEI));
    }

    @Test
    void ak9_imLanrErkennerWirktIKNicht() {
        assertKein(LANR_ART, "I K " + LANR_ART.gueltig);
        assertKein(LANR_ART, "I.K. " + LANR_ART.gueltig);
        assertKein(LANR_ART, "I K-Nr. " + LANR_ART.gueltig);
        assertEin(LANR_ART, "LANR I K ", LANR_ART.gueltig, "", WAHRSCHEINLICH);
    }

    @Test
    void grenzfall_iKFolgtAufEineNummerOhneKontext_keinNachgestellterKontext() {
        assertKein(IK, IKN + " I K");
        folgeNachNummer(IKN + " I K " + IKN2, new Erw(IKN2, ZWEI));
        folgeNachNummer(IKN + ", I K " + IKN2, new Erw(IKN2, ZWEI));
    }

    @Test
    void grenzfall_iKUndUnsichtbareZeichen_invarianzGegenEinstreuen() {
        String[] basis = {"I K " + IKN, "I.K.: " + IKN + ".", IKN + ", I K", "X I K " + IKN2 + " und " + IKN,
            "I K-Nr. " + IKN, "I K Praxis " + IKN, "I K\n" + IKN, "I. K. " + IKN};
        for (int t = 0; t < basis.length; t++) {
            String text = basis[t];
            List<Finding> ohne = scan(IK, text);
            for (int p = 0; p <= text.length(); p++) {
                if (p > 0 && p < text.length() && istZiffer(text.charAt(p - 1)) && istZiffer(text.charAt(p))) {
                    continue;
                }
                List<Finding> funde = scan(IK, text.substring(0, p) + ZWSP + text.substring(p));
                assertEquals(ohne.size(), funde.size(), "Fundzahl bei Einstreuung an Stelle " + p + " in Text " + t);
                for (int k = 0; k < ohne.size(); k++) {
                    assertEquals(ohne.get(k).originalValue(), funde.get(k).originalValue());
                    assertEquals(ohne.get(k).confidence(), funde.get(k).confidence(), "Konfidenz");
                }
            }
        }
    }

    @Test
    void ak24_laufzeit_iKWiederholt_keinFund() {
        laufzeit(IK, "", "I K ", "", 0, "0 Funde: eine einzige Folge ohne Kontextwort (Laenge ueber 23)");
    }

    @Test
    void ak24_laufzeit_iUndKAbwechselndOhneNummer_keinFund() {
        laufzeit(IK, "", "I ", "", 0, "0 Funde: nur I");
        laufzeit(IK, "", "K ", "", 0, "0 Funde: nur K");
        laufzeit(IK, "I ", "K I ", "", 0, "0 Funde: I und K abwechselnd, eine einzige Folge");
    }

    @Test
    void ak24_laufzeit_iKMitNummerWiederholt_jedeNummerEinFundMit050() {
        String baustein = "I K " + IKN + " ";
        assertEquals(14, baustein.length(), "Bausteinlaenge");
        int r = wiederholungen(1_000_000, "", baustein, "");
        assertEquals(71_429, r, "Wiederholungen: aufgerundet 1 000 000 / 14");
        laufzeit(IK, "", baustein, "", r, r + " Funde: je Wiederholung eine Nummer, alle 0,50");
        List<Finding> klein = scan(IK, baustein.repeat(4));
        assertEquals(4, klein.size());
        for (Finding f : klein) {
            assertEquals(ZWEI, f.confidence());
            assertEquals("ik", f.source());
        }
    }

    // ------------------------------------------------------------------ T-7a: Grenze 14h geschlossen

    /** Nachsatzformen hinter {@code IK: Praxis I K}, jede mit gueltiger IK und dem Kontext 0,80 (Pflichtfall a). */
    private static final String[] T7A_NACHSATZ = {" Nr ", "-Nr. ", "-Nummer ", " Nummer ", ": Nr ", "-Nr: ", " Nr.: ",
        " Nr = ", " Nr. Nr "};

    @Test
    void t7a_a_weitereNachsatzformenHinterLaufendemKontext_fundMitKontextKonfidenz() {
        for (String nachsatz : T7A_NACHSATZ) {
            String vor = "IK: Praxis I K" + nachsatz;
            assertEin(IK, vor, IKN, "", WAHRSCHEINLICH, "ik");
            assertEin(IK, "IKNR, der I K" + nachsatz, IKN, "", WAHRSCHEINLICH, "ik");
        }
    }

    @Test
    void t7a_b_punktform() {
        assertEin(IK, "IK: Praxis I.K. Nr. ", IKN, "", WAHRSCHEINLICH, "ik");
        assertEin(IK, "IK: Praxis I.K.-Nr. ", IKN, "", WAHRSCHEINLICH, "ik");
        assertEin(IK, "IK: Praxis I-K Nr ", IKN, "", WAHRSCHEINLICH, "ik");
    }

    @Test
    void t7a_c_kontextOhneDoppelpunktUeberZeileOderAnderesKontextwort() {
        String[] vor = {"IK Praxis I K Nr ", "IK:\nPraxis I K Nr ", "IK-Nr.: Praxis I K Nr ", "IK: Hallo I K Nr ",
            "IK: Praxis i k nr ", "Institutionskennzeichen: Praxis I K Nr ", "IKNR Praxis I K Nr ",
            "I K N R Praxis I K Nr "};
        for (String v : vor) {
            assertEin(IK, v, IKN, "", WAHRSCHEINLICH, "ik");
        }
    }

    @Test
    void t7a_d_nachgestellterKontextDavor() {
        folgeNachNummer(IKN + ", IK\nPraxis I K-Nr. " + IKN2, new Erw(IKN, KOMMA), new Erw(IKN2, WAHRSCHEINLICH));
        folgeNachNummer(IKN + " IK\nPraxis I K-Nr. " + IKN2, new Erw(IKN, WAHRSCHEINLICH),
                new Erw(IKN2, WAHRSCHEINLICH));
    }

    @Test
    void t7a_e_aufzaehlungDahinter_wieInDerBestandsformOhneNachsatzwort() {
        for (String trenner : new String[] {" und ", ", "}) {
            List<Finding> mit = scan(IK, "IK: Praxis I K Nr " + IKN + trenner + IKN2);
            List<Finding> ohne = scan(IK, "IK: Praxis I K " + IKN + trenner + IKN2);
            assertEquals(2, ohne.size(), "Bestandsform");
            assertEquals(2, mit.size(), "mit Nachsatzwort");
            for (int i = 0; i < 2; i++) {
                assertEquals(ohne.get(i).originalValue(), mit.get(i).originalValue());
                assertEquals(WAHRSCHEINLICH, mit.get(i).confidence());
                assertEquals(ohne.get(i).confidence(), mit.get(i).confidence());
                assertEquals(ohne.get(i).source(), mit.get(i).source());
            }
        }
    }

    @Test
    void t7a_f_ohneVorwortMitEinemFuellwort() {
        assertEin(IK, "IK: I K Nr der ", IKN, "", WAHRSCHEINLICH, "ik");
        assertEin(IK, "IK: I K Nr der Firma ", IKN, "", WAHRSCHEINLICH, "ik");
        assertEin(IK, "IK: I K Nr ", IKN, "", WAHRSCHEINLICH, "ik");
        // ohne Vorwort und ohne Nachsatzwort (Hinweis 6): der Kontext behaelt seine Konfidenz
        assertEin(IK, "IK: I K ", IKN, "", WAHRSCHEINLICH, "ik");
    }

    @Test
    void t7a_ungueltigeNummerUndLaenge_behaltenIhreStufe() {
        assertEin(IK, "IK: Praxis I K Nr ", IK.ungueltig, "", PZ_UNGUELTIG, IK.quellePruefziffer());
        assertEin(IK, "IK: Praxis I K Nr ", LAENGE_7, "", LAENGE, IK.quelleLaenge());
        assertEin(IK, "IK: Praxis I K-Nummer ", IK.ungueltig, "", PZ_UNGUELTIG, IK.quellePruefziffer());
        assertEin(IK, "IK: Praxis I K-Nummer ", LAENGE_7, "", LAENGE, IK.quelleLaenge());
    }

    @Test
    void t7a_gegenproben_keinFund() {
        // ein viertes echtes Fuellwort
        assertKein(IK, "IK: Praxis der Firma Nr " + IKN);
        assertKein(IK, "IK: Praxis I K Nr der Firma Hallo " + IKN);
        // die Befreiung gilt nur unmittelbar hinter I K
        assertKein(IK, "IK: Praxis I K der Nr Nr " + IKN);
        assertKein(IK, "IK: Praxis I K! Nr " + IKN);
        assertKein(IK, "IK: Praxis I K Nr\n\n" + IKN);
        // Kontext vor I K erloschen: das Wort hinter dem Nachsatzwort beendet den neuen Kontext
        assertKein(IK, "IK: Praxis der I K Nr der " + IKN);
    }

    @Test
    void t7a_punktHinterIKBeiLaufendemKontext_istKeinSatzende() {
        // wie beim frischen Kontext von I K: der Punkt unmittelbar dahinter beendet den Kontext nicht
        assertEin(IK, "IK: Praxis I K . ", IKN, "", WAHRSCHEINLICH, "ik");
        assertEin(IK, "IK: I.K. . ", IKN, "", WAHRSCHEINLICH, "ik");
        // Fund aus dem Zufallsvergleich gegen den Stand T-6: der Kontext laeuft ueber I K mit ZWSP, mehrere I.K.-Folgen
        assertEin(IK, "I" + ZWSP + "K Nr. I.K. I.K. I.K. . ", IKN, "", WAHRSCHEINLICH, "ik");
        // ein Satzende zwischen I K und der Nummer beendet den Kontext weiterhin
        assertKein(IK, "IK: Praxis I K Nr . x . " + IKN);
    }

    @Test
    void t7a_iKOhneLaufendenKontext_bleibtWieBisher() {
        zwei("der I K Nr ", IKN, "");
        zwei("I K Nr ", IKN, "");
        // Kontext schon vor I K erloschen (viertes Fuellwort): I K beginnt neu, 0,50 (nicht monoton, bekannt)
        zwei("IK: der Praxis I K Nr ", IKN, "");
    }

    @Test
    void t7a_merkerEndetMitDemFund_nachsatzwoerterDanachZaehlenWieder() {
        // nach dem Fund endet der Merker: Nachsatzwoerter dahinter sind Fuellwoerter (vier beenden den Kontext)
        folgeNachNummer("IK: Praxis I K Nr " + IKN + " IK " + IKN2, new Erw(IKN, WAHRSCHEINLICH),
                new Erw(IKN2, IK.sicher));
        folgeNachNummer("IK: Praxis I K Nr " + IKN + " Nr Nr Nr Nr " + IKN2, new Erw(IKN, WAHRSCHEINLICH));
        folgeNachNummer("IK: Praxis I K Nr " + IKN + " Nr Nr Nr " + IKN2, new Erw(IKN, WAHRSCHEINLICH),
                new Erw(IKN2, WAHRSCHEINLICH));
    }

    @Test
    void t7a_imLanrErkennerAendertSichNichts() {
        String g = LANR_ART.gueltig;
        assertKein(LANR_ART, "LANR: Praxis I K Nr " + g);
        assertKein(LANR_ART, "LANR: Praxis I K-Nr. " + g);
        assertKein(LANR_ART, "I K Nr " + g);
        assertEin(LANR_ART, "LANR: Praxis I K ", g, "", WAHRSCHEINLICH);
        assertEin(LANR_ART, "LANR: I K Nr ", g, "", WAHRSCHEINLICH);
    }

    @Test
    void t7a_invarianzGegenEingestreutesZwsp() {
        String[] basis = {"IK: Praxis I K Nr " + IKN, "IK: Praxis I.K. Nr. " + IKN, "IK:\nPraxis I K-Nr. " + IKN,
            "IK: I K Nr der " + IKN, "IK: Praxis I K Nr " + IKN + " und " + IKN2, IKN + ", IK\nPraxis I K-Nr. " + IKN2,
            "IK: Praxis i k nr: " + IKN};
        for (int t = 0; t < basis.length; t++) {
            String text = basis[t];
            List<Finding> ohne = scan(IK, text);
            assertFalse(ohne.isEmpty(), "Grundtext " + t);
            for (int p = 0; p <= text.length(); p++) {
                if (p > 0 && p < text.length() && istZiffer(text.charAt(p - 1)) && istZiffer(text.charAt(p))) {
                    continue;
                }
                List<Finding> funde = scan(IK, text.substring(0, p) + ZWSP + text.substring(p));
                assertEquals(ohne.size(), funde.size(), "Fundzahl bei Einstreuung an Stelle " + p + " in Text " + t);
                for (int k = 0; k < ohne.size(); k++) {
                    assertEquals(ohne.get(k).originalValue(), funde.get(k).originalValue());
                    assertEquals(ohne.get(k).confidence(), funde.get(k).confidence(), "Konfidenz");
                }
            }
        }
    }

    @Test
    void t7a_laufzeit_laufenderKontextMitIKUndNachsatzWiederholt_jedeNummerEinFund() {
        String baustein = "IK: Praxis I K Nr " + IKN + " ";
        int r = wiederholungen(1_000_000, "", baustein, "");
        assertTrue(baustein.length() * r >= 1_000_000, "Mindestlaenge");
        laufzeit(IK, "", baustein, "", r, r + " Funde: je Wiederholung eine Nummer, alle 0,80");
        List<Finding> klein = scan(IK, baustein.repeat(4));
        assertEquals(4, klein.size());
        for (Finding f : klein) {
            assertEquals(WAHRSCHEINLICH, f.confidence());
            assertEquals("ik", f.source());
        }
    }

    // ------------------------------------------------------------------ T-7b: weitere bekannte Grenzen (Plan Kap. 11)

    @Test
    void bekannteGrenze14c_initialenMitPunktUndLeerzeichenTabUmbruchZweiTrenner_keineSperrschrift() {
        for (Art a : ARTEN) {
            String w = wort(a);
            // Initialenform "L. A. N. R.": Punkt und Leerzeichen sind zwei Trenner
            assertKein(a, sperr(w, ". ") + ". " + a.gueltig);
            // Tab und Zeilenumbruch als Trenner
            assertKein(a, sperr(w, "\t") + " " + a.gueltig);
            assertKein(a, sperr(w, "\n") + " " + a.gueltig);
            // zwei Leerzeichen als Trenner
            assertKein(a, sperr(w, "  ") + " " + a.gueltig);
        }
        assertKein(IK, "I. K. " + IKN);
        assertKein(IK, "I\tK " + IKN);
        assertKein(IK, "I\nK " + IKN);
        assertKein(IK, "I  K " + IKN);
    }

    @Test
    void bekannteGrenze14e_einzelbuchstabeDirektVorOderHinterDerFolge_keinKontextwort() {
        // davor: die maximale Folge ist "A I K" bzw. "X Y I K", kein Kontextwort
        assertKein(IK, "Herr A I K " + IKN);
        assertKein(IK, "X Y I K " + IKN);
        assertKein(LANR_ART, "Herr A L A N R " + LANR_ART.gueltig);
        // dahinter, auch hinter einem gesperrten nachgestellten Kontextwort
        assertKein(LANR_ART, "L A N R x " + LANR_ART.gueltig);
        assertKein(LANR_ART, LANR_ART.gueltig + ", L A N R x");
        assertKein(IK, IKN + ", I K x");
        // und mit einem unsichtbaren Zeichen dazwischen
        assertKein(LANR_ART, "L A N R" + ZWSP + " x " + LANR_ART.gueltig);
        assertKein(LANR_ART, LANR_ART.gueltig + ", L A N R" + ZWSP + " x");
        assertKein(LANR_ART, LANR_ART.gueltig + ", L A N R " + ZWSP + "x");
    }

    @Test
    void bekannteGrenze14hRest1_mehrereVorwoerterVorIK_050StattKontext_nummerAbgedeckt() {
        zwei("IK: Praxis Firma I K Nr ", IKN, "");
        zwei("IK: Dr. med. Praxis I K Nr ", IKN, "");
    }

    @Test
    void bekannteGrenze14hRest2_fuellwortZwischenIKUndNachsatzwort_keinFund() {
        assertKein(IK, "IK: Praxis I K der Firma Nr " + IKN);
    }

    @Test
    void bekannteGrenze14hRest3_zweiZeilenumbruecheSeitDemKontextwort_keinFund() {
        assertKein(IK, "IK:\nPraxis I K\nNr " + IKN);
    }

    @Test
    void bekannteGrenze14hRest4_zweiEinzelbuchstabenVorIK_keinFund() {
        assertKein(IK, "IK: a b I K Nr " + IKN);
    }

    @Test
    void bekannteGrenze14hRest5_wiederholtesIKMitNeutralemZeichenVerlaengertDenKontext_fehlalarmMoeglich() {
        // jedes I K gibt seine Fuellwoerter zurueck: der Kontext laeuft beliebig lange weiter, die Nummer bekommt 0,80
        assertEin(IK, "IK: " + "I K, ".repeat(3), IKN, "", WAHRSCHEINLICH, "ik");
        assertEin(IK, "IK: " + "I K, ".repeat(40), IKN, "", WAHRSCHEINLICH, "ik");
        // zum Vergleich: gewoehnliche Woerter statt I K beenden den Kontext nach drei Fuellwoertern
        assertKein(IK, "IK: " + "x, ".repeat(5) + IKN);
    }

    @Test
    void bekannteGrenze15_crUnsichtbaresZeichenLf_zaehltAlsZweiUmbrueche_behebungNichtFreigegeben() {
        // Behebung nicht freigegeben, Entscheidung liegt beim Nutzer: Das Verhalten stammt aus dem Vorfeature
        // (auch im eingefrorenen Vorgaenger). Ohne das unsichtbare Zeichen gibt es den Fund mit 0,80.
        for (Art a : ARTEN) {
            assertEin(a, a.kw + ":\r\n", a.gueltig, "", WAHRSCHEINLICH);
            assertKein(a, a.kw + ":\r" + ZWSP + "\n" + a.gueltig);
            assertEin(a, sperr(a) + ":\r\n", a.gueltig, "", SPERR);
            assertKein(a, sperr(a) + ":\r" + ZWSP + "\n" + a.gueltig);
        }
    }
}
