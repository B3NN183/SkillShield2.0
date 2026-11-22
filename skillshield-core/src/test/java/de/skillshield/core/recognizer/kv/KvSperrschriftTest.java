package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.kv.KvTestHilfen.Art;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests zu gesperrt geschriebenen Kontextwoertern ab drei Buchstaben (Feature kv-kontext-luecken, Task T-3, D-1, D-2,
 * D-4, D-6, D-7, Grenze 14; AK-1 bis AK-3, AK-7 bis AK-10, AK-12, AK-13, AK-19 (LANR), AK-24 Laufzeit). Das
 * Zwei-Buchstaben-Wort {@code I K} gehoert nicht zu diesem Task. Nummern stammen aus {@code SynthetischeKvNummern};
 * jeder Text geht durch den Waechter und die Differenzpruefung gegen den Vorgaenger in {@link KvTestHilfen#scan}.
 * Je Art wird das kuerzeste gesperrt schreibbare Kontextwort ab drei Buchstaben benutzt ({@code LANR}, {@code IKNR}).
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
}
