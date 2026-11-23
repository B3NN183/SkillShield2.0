package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.kv.KvTestHilfen.Art;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests zu gesperrt geschriebenen Kontextwoertern ab vier Buchstaben mit erweiterten Trennern (Feature
 * kv-trenner-und-umbruch, Task T-6, Teil 2, D-2 bis D-7, D-9; AK-7 bis AK-14, AK-17, AK-20, AK-21 (Bereich, Quelle),
 * Grenzfaelle aus Plan Kap. 3.2.7, Laufzeit AK-26 auf Erkennerebene). Je Art wird das Kontextwort {@code LANR}
 * bzw. {@code IKNR} benutzt. Jeder Text geht durch den Waechter, die Differenzpruefungen gegen beide eingefrorenen
 * Staende und den Fehlalarm-Pruefstand in {@link KvTestHilfen#scan}. Nummern stammen aus
 * {@code SynthetischeKvNummern}; die Quelle wird aus der Nummer abgeleitet.
 */
class KvTrennerTest {

    private static final double SPERR = 0.80;
    private static final double KOMMA = 0.60;
    /** Deckel fuer eine Nummer unmittelbar hinter dem Zwei-Buchstaben-Wort {@code I K}. */
    private static final double ZWEI = 0.50;

    private static String wort(Art a) {
        return a.lanr ? "LANR" : "IKNR";
    }

    /** Die Buchstaben des Wortes, die Luecken reihum aus {@code luecken} (eine Luecke gilt fuer alle). */
    private static String folge(String wort, String... luecken) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < wort.length(); i++) {
            if (i > 0) {
                b.append(luecken[(i - 1) % luecken.length]);
            }
            b.append(wort.charAt(i));
        }
        return b.toString();
    }

    /** Genau ein Fund 0,80 ueber die gueltige Nummer direkt hinter {@code vor}. */
    private static void fund(Art a, String vor) {
        assertEin(a, vor, a.gueltig, "", SPERR, a.quelle);
    }

    private static void fund(Art a, String vor, String wert, double konfidenz) {
        assertEin(a, vor, wert, "", konfidenz, a.quelleNachNummer(wert));
    }

    private static void kein(Art a, String vor) {
        assertKein(a, vor + a.gueltig);
    }

    // ------------------------------------------------------------------ AK-7 bis AK-10

    @Test
    void ak7_punktUndLeerzeichen_einFundMit080() {
        for (Art a : ARTEN) {
            String w = wort(a);
            fund(a, folge(w, ". ") + ". ");
            fund(a, folge(w, ". ") + ".: ");
            fund(a, folge(w.toLowerCase(), ". ") + ". ");
            fund(a, folge(w, ". ") + " ");
            fund(a, folge(w, ".  ") + ". ");
            fund(a, folge(w, "." + NBSP, "." + DUENN, "." + SCHMAL_NBSP) + ". ");
            fund(a, folge(w, ".   ") + ".   ");
        }
    }

    @Test
    void ak8_mehrereLeerraumzeichen_einFundMit080() {
        for (Art a : ARTEN) {
            String w = wort(a);
            fund(a, folge(w, "  ") + " ");
            fund(a, folge(w, "   ") + " ");
            fund(a, folge(w, "" + NBSP + NBSP) + " ");
            fund(a, folge(w, " " + NBSP, "" + DUENN + DUENN, "" + SCHMAL_NBSP + " ") + " ");
            fund(a, folge(w, "" + DUENN + SCHMAL_NBSP + NBSP) + "" + SCHMAL_NBSP);
        }
    }

    @Test
    void ak9_tab_einFundMit080() {
        for (Art a : ARTEN) {
            String w = wort(a);
            fund(a, folge(w, "\t") + "\t");
            fund(a, folge(w, "\t") + " ");
            fund(a, folge(w, "\t") + ": ");
        }
    }

    @Test
    void ak10_gemischteFormenUndSchlusspunkt_einFundMit080() {
        for (Art a : ARTEN) {
            String w = wort(a);
            fund(a, folge(w, ". ", " ", "  ") + " ");
            fund(a, folge(w, ".  ", "\t", ". ") + ". ");
            fund(a, folge(w, ".", ". ", " ") + ". ");
            fund(a, folge(w, ". ", "-", " ") + " ");
            fund(a, folge(w, "\t", "  ", ". ") + ". ");
        }
    }

    // ------------------------------------------------------------------ AK-11, AK-12

    @Test
    void ak11_laengereKontextwoerterUndIk_einFundMit080() {
        fund(LANR_ART, folge("ARZTNR", ". ") + ". ");
        fund(LANR_ART, folge("ARZTNUMMER", "  ") + " ");
        fund(LANR_ART, folge("LANRNR", ". ") + ". ");
        fund(LANR_ART, folge("ARZTNUMMER", ". ", "\t") + ". ");
        fund(IK_ART, folge("IKNR", ". ") + ". ");
        fund(IK_ART, folge("IKNUMMER", "  ") + " ");
        fund(IK_ART, folge("IKNUMMER", ". ") + ". ");
        fund(IK_ART, folge("IKNR", "\t") + " ");
        fund(IK_ART, folge("INSTITUTIONSKENNZEICHEN", ". ") + ". ");
        fund(IK_ART, folge("INSTITUTIONSKENNZEICHEN", "  ", "\t") + " ");
    }

    @Test
    void ak12_pruefzifferUndLaenge_deckelHebtNieAn() {
        for (Art a : ARTEN) {
            String w = wort(a);
            for (String vor : new String[] {folge(w, ". ") + ". ", folge(w, "  ") + " ", folge(w, "\t") + "\t"}) {
                fund(a, vor, a.ungueltig, PZ_UNGUELTIG);
                fund(a, vor, LAENGE_7, LAENGE);
            }
        }
    }

    @Test
    void ak12_quelleFolgtDerNummer() {
        for (Art a : ARTEN) {
            String vor = folge(wort(a), ". ") + ". ";
            assertEquals(a.quelle, scan(a, vor + a.gueltig).get(0).source(), a.name + " Quelle gueltig");
            assertEquals(a.quellePruefziffer(), scan(a, vor + a.ungueltig).get(0).source(), a.name + " Quelle Pz");
            assertEquals(a.quelleLaenge(), scan(a, vor + LAENGE_7).get(0).source(), a.name + " Quelle Laenge");
        }
    }

    // ------------------------------------------------------------------ AK-13, AK-14

    private static List<String> zahlenFuerKeinFund() {
        return List.of(IK_GUELTIG, LANR_GUELTIG, LAENGE_7);
    }

    @Test
    void ak13_zweiBuchstabenWortMitErweitertenTrennern_keinFund() {
        String[] vorlagen = {"I. K. #", "I. K.: #", "i. k. #", "I. K #", "I  K #", "I   K #", "I\tK #", "I.  K. #",
            "I" + NBSP + NBSP + "K #", "Dr. I. K. #", "Prof. I. K. #", "I. K.-Nr. #", "I. K. Mustermann #",
            "I. K. Mustermann, Rechnung #", "Prof. I. K. Mustermann, Rechnung #", "# (I. K.)", "#, I  K"};
        for (String vorlage : vorlagen) {
            for (String zahl : zahlenFuerKeinFund()) {
                for (Art a : ARTEN) {
                    assertKein(a, vorlage.replace("#", zahl));
                }
            }
        }
    }

    @Test
    void ak13_bestandMitEinemTrennerBleibtUnveraendert() {
        for (String vor : new String[] {"I K ", "I.K. ", "I-K ", "I K: "}) {
            fund(IK_ART, vor, IK_GUELTIG, ZWEI);
        }
        assertKein(IK_ART, "I K Mustermann " + IK_GUELTIG);
        assertKein(IK_ART, "I K der Praxis " + IK_GUELTIG);
    }

    @Test
    void ak14_maximaleFolge_keinFund() {
        for (Art a : ARTEN) {
            String w = wort(a);
            String z = a.gueltig;
            assertKein(a, "A. " + folge(w, ". ") + ". " + z);
            assertKein(a, folge(w, ". ") + ". X " + z);
            assertKein(a, folge(w.substring(0, 3), ". ") + ". " + z);
            assertKein(a, folge(w.substring(0, 3), ". ") + ". X. " + w.charAt(3) + ". " + z);
            assertKein(a, folge(w + w, ". ") + ". " + z);
            assertKein(a, "Herr A. " + folge(w, ". ") + ". " + z);
            assertKein(a, folge(w, "  ") + "  X " + z);
            assertKein(a, folge(w, "\t") + "\tX " + z);
            if (a.lanr) {
                assertKein(a, w.substring(0, 2) + ". " + w.substring(2, 3) + ". " + w.charAt(3) + ". " + z);
            }
            assertKein(a, w.charAt(0) + ". " + w.substring(1) + ". " + z);
        }
        assertKein(IK_ART, "I. K. N. " + IK_GUELTIG);
        assertKein(IK_ART, "A. I. K. N. R. X " + IK_GUELTIG);
    }

    @Test
    void ak14_teilgruppenMitPunktUndLeerzeichen_keinFund() {
        // AK-14: Teilgruppen "LA. NR." und "L. ANR" (nur LANR; "IK. NR." ist ueber das Bestands-Kontextwort "IK" ein
        // Fund)
        String w = wort(LANR_ART);
        String z = LANR_ART.gueltig;
        assertKein(LANR_ART, w.substring(0, 2) + ". " + w.substring(2) + ". " + z);
        assertKein(LANR_ART, w.charAt(0) + ". " + w.substring(1) + " " + z);
    }

    @Test
    void ak14_puffergrenze23Und24Buchstaben_nurDasWortSelbstIstEinFund() {
        String ik = "INSTITUTIONSKENNZEICHEN";
        assertTrue(ik.length() == 23, "Laenge des laengsten Kontextworts");
        // 23 Buchstaben, genau das laengste Kontextwort: Fund (Grenze liegt bei 23, nicht darunter)
        fund(IK_ART, folge(ik, ". ") + ". ");
        // 24 Buchstaben: ein Einzelbuchstabe vor dem Wort, der Puffer laeuft ueber, kein Kontextwort mehr
        assertKein(IK_ART, "A. " + folge(ik, ". ") + ". " + IK_GUELTIG);
        assertKein(IK_ART, "A  " + folge(ik, "  ") + " " + IK_GUELTIG);
        assertKein(IK_ART, folge(ik + "A", ". ") + ". " + IK_GUELTIG);
        for (Art a : ARTEN) {
            // 24 Einzelbuchstaben in erweiterter Sperrung, die auf das Kontextwort enden
            String lang = "ABCDEFGHIJKLMNOPQRST" + wort(a);
            assertTrue(lang.length() == 24, "Laenge der Buchstabenfolge");
            assertKein(a, folge(lang, ". ") + ". " + a.gueltig);
            assertKein(a, folge(lang, "  ") + " " + a.gueltig);
        }
    }

    @Test
    void ak14_ohneNummerOderAmTextende_keinFund() {
        for (Art a : ARTEN) {
            String f = folge(wort(a), ". ") + ".";
            assertKein(a, f);
            assertKein(a, f + " ");
            assertKein(a, f + " Praxis");
            assertKein(a, folge(wort(a), "  "));
            assertKein(a, folge(wort(a), "\t") + "\t");
        }
    }

    // ------------------------------------------------------------------ AK-17 und Restgrenze 14cR(2)

    private static String[] ausserhalbD2(String w) {
        return new String[] {folge(w, "    "), folge(w, " " + NBSP + DUENN + SCHMAL_NBSP), folge(w, "\t\t"),
            folge(w, "\t "), folge(w, " \t"), folge(w, ".\t"), folge(w, " - "), folge(w, " " + HALBGEVIERT + " "),
            folge(w, "\n"), folge(w, "\r\n"), folge(w, ", "), folge(w, "; "), folge(w, ": "), folge(w, "/"),
            folge(w, "_"), folge(w, " ."), folge(w, ".."), folge(w, "" + EN_SPACE), folge(w, ".    "),
            folge(w, ". ", ".    ", ". ")};
    }

    @Test
    void ak17_bekannteGrenze14cRest_erweiterteTrennerAusserhalbD2_keinFund() {
        for (Art a : ARTEN) {
            for (String f : ausserhalbD2(wort(a))) {
                assertKein(a, f + " " + a.gueltig);
                assertKein(a, f + ". " + a.gueltig);
            }
        }
        // Nachgestellt: die Folge ausserhalb von D-2 erschliesst auch hier nichts
        for (Art a : ARTEN) {
            for (String f : ausserhalbD2(wort(a))) {
                assertKein(a, a.gueltig + ", " + f);
            }
        }
    }

    @Test
    void ak17_doppelPunktMitLeerzeichenUndUmbruchInDerFolge_keinFund() {
        for (Art a : ARTEN) {
            String w = wort(a);
            String z = a.gueltig;
            assertKein(a, folge(w, ".. ") + ".. " + z);
            assertKein(a, folge(w, ".. ") + ". " + z);
            // Zeilenumbruch mitten in der Folge, sonst Punkt mit Leerzeichen
            assertKein(a, w.charAt(0) + ". " + w.charAt(1) + ".\n" + w.charAt(2) + ". " + w.charAt(3) + ". " + z);
            assertKein(a, w.charAt(0) + ". " + w.charAt(1) + ". \n" + w.charAt(2) + ". " + w.charAt(3) + ". " + z);
        }
    }

    @Test
    void bekannteGrenze14cRest_zweiBuchstabenMitErweitertenTrennern_keinFund() {
        String[] luecken = {". ", "  ", "   ", "\t", ".  ", "" + NBSP + NBSP, ". " + NBSP};
        for (String l : luecken) {
            kein(IK_ART, "I" + l + "K" + l.substring(0, 1).replace("\t", "") + " ");
            kein(IK_ART, "i" + l + "k ");
            kein(LANR_ART, "I" + l + "K ");
        }
        kein(IK_ART, "IK: Praxis I. K. Nr ");
        kein(IK_ART, "Prof. I. K. Mustermann, Rechnung ");
    }

    @Test
    void bekannteGrenze14cFolge_initialenDieGenauEinKontextwortErgeben_fund() {
        // Dokumentierte Folge (Plan Kap. 11, 14cF): Initialen, die ein Kontextwort ergeben, erschliessen eine Zahl.
        for (Art a : ARTEN) {
            String vor = "Prof. " + folge(wort(a), ". ") + ". Beispiel, Rechnung ";
            fund(a, vor, LAENGE_7, LAENGE);
            fund(a, vor, a.gueltig, SPERR);
        }
    }

    // ------------------------------------------------------------------ AK-20: unsichtbare Zeichen

    private static boolean leerraum4(char c) {
        return c == ' ' || c == NBSP || c == DUENN || c == SCHMAL_NBSP;
    }

    private static boolean buchstabeAn(String s, int i) {
        return i >= 0 && i < s.length() && Character.isLetter(s.charAt(i));
    }

    /**
     * Alle Texte, in die {@code uz} an genau einer Stelle eingefuegt ist: direkt vor oder hinter einem Buchstaben,
     * zwischen {@code .} und Leerraum, zwischen zwei Leerraumzeichen (nie zwischen zwei Buchstaben).
     */
    private static List<String> mitUz(String vor, String uz) {
        List<String> texte = new ArrayList<>();
        for (int p = 0; p <= vor.length(); p++) {
            boolean amBuchstaben = buchstabeAn(vor, p - 1) || buchstabeAn(vor, p);
            boolean punktLeer = p > 0 && p < vor.length() && vor.charAt(p - 1) == '.' && leerraum4(vor.charAt(p));
            boolean zwischenLeer = p > 0 && p < vor.length() && leerraum4(vor.charAt(p - 1))
                    && leerraum4(vor.charAt(p));
            if (amBuchstaben || punktLeer || zwischenLeer) {
                texte.add(vor.substring(0, p) + uz + vor.substring(p));
            }
        }
        return texte;
    }

    @Test
    void ak20_unsichtbareZeichenAnJederStelle_gleicherFund() {
        for (Art a : ARTEN) {
            String w = wort(a);
            List<String> vorlagen = List.of(folge(w, ". ") + ". ", folge(w, ".  ") + ". ", folge(w, "  ") + " ",
                    folge(w, "   ") + " ", folge(w, "\t") + "\t", folge(w, ". ", " ", "  ") + " ",
                    folge(w, ".", ". ", " ") + ". ", folge(w, "" + NBSP + DUENN) + " ");
            for (String uz : uzAk1Texte()) {
                for (String vor : vorlagen) {
                    for (String text : mitUz(vor, uz)) {
                        List<Finding> funde = scan(a, text + a.gueltig);
                        assertEquals(1, funde.size(), a.name + " Zahl der Funde mit einem unsichtbaren Zeichen");
                        Finding f = funde.get(0);
                        assertEquals(text.length(), f.start(), a.name + " Start");
                        assertEquals(text.length() + a.gueltig.length(), f.end(), a.name + " Ende");
                        assertEquals(SPERR, f.confidence(), a.name + " Konfidenz");
                        assertEquals(a.quelle, f.source(), a.name + " Quelle");
                    }
                }
            }
        }
    }

    @Test
    void ak20_mehrereUnsichtbareZeichenHintereinanderUndInDerLuecke() {
        String uz = "" + ZWSP + SHY + BOM;
        for (Art a : ARTEN) {
            String w = wort(a);
            fund(a, w.charAt(0) + uz + ". " + uz + w.charAt(1) + ". " + w.charAt(2) + ". " + w.charAt(3) + ". ");
            fund(a, folge(w, "." + uz + " ") + ". ");
            fund(a, folge(w, " " + uz + " ") + " ");
            fund(a, folge(w, " " + ZWSP + " " + BOM + " ") + " ");
        }
    }

    /** Die Buchstaben des Wortes; zwischen Buchstabe {@code p} und {@code p + 1} steht nur {@code uz}. */
    private static String gebrochen(String w, int p, String luecke, String uz) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length(); i++) {
            if (i > 0) {
                b.append(i - 1 == p ? uz : luecke);
            }
            b.append(w.charAt(i));
        }
        return b.toString();
    }

    @Test
    void ak20_unsichtbaresZeichenZwischenZweiBuchstaben_teilgruppeKeinFund() {
        for (Art a : ARTEN) {
            String w = wort(a);
            for (int p = 0; p < w.length() - 1; p++) {
                if (w.substring(p, p + 2).equals("IK")) {
                    continue; // I ZWSP K sind zwei Teilstuecke: das normale Kontextwort IK, kein Nicht-Fall
                }
                for (String luecke : new String[] {". ", "  ", "\t"}) {
                    kein(a, gebrochen(w, p, luecke, String.valueOf(ZWSP)) + luecke);
                }
            }
        }
        kein(LANR_ART, "L" + ZWSP + "A. N. R. ");
    }

    @Test
    void ak20_bestandMitUnsichtbaremZeichenZwischenAllenBuchstaben_unveraendert() {
        for (Art a : ARTEN) {
            String w = wort(a);
            String vor = w.charAt(0) + "" + ZWSP + w.charAt(1) + ZWSP + w.charAt(2) + ZWSP + w.charAt(3) + " ";
            assertEin(a, vor, a.gueltig, "", a.sicher, a.quelle);
        }
    }

    // ------------------------------------------------------------------ AK-21: Bereich und Quelle

    @Test
    void ak21_bereichOhneKontextwortTrennerUndSatzzeichen() {
        for (Art a : ARTEN) {
            String w = wort(a);
            for (String vor : new String[] {folge(w, ". ") + ". ", folge(w, "  ") + " ", folge(w, "\t") + "\t"}) {
                assertEin(a, vor, a.gueltig, ".", SPERR, a.quelle);
                assertEin(a, vor, a.gueltig, ".\r\n", SPERR, a.quelle);
                assertEin(a, vor, a.gueltig, ", Rest", SPERR, a.quelle);
            }
            List<Finding> funde = scan(a, folge(w, ". ") + ". " + a.gueltig + ".");
            assertTrue(a.gueltig.equals(funde.get(0).originalValue()), a.name + " Wert");
            assertEquals(a.typ, funde.get(0).type(), a.name + " Typ");
        }
    }

    // ------------------------------------------------------------------ Grenzfaelle Plan Kap. 3.2.7

    @Test
    void grenzfall_nachgestelltUndAbbildDerOffenenSegmente() {
        for (Art a : ARTEN) {
            String w = wort(a);
            assertEin(a, "", a.gueltig, " (" + folge(w, ". ") + ".)", SPERR, a.quelle);
            assertEin(a, "", a.gueltig, " " + folge(w, "  "), SPERR, a.quelle);
            assertEin(a, "", a.gueltig, " " + folge(w, "\t"), SPERR, a.quelle);
            assertEin(a, "", a.gueltig, ", " + folge(w, ". ") + ".", KOMMA, a.quelle);
            assertEin(a, "", a.gueltig, "; " + folge(w, "  "), KOMMA, a.quelle);
            assertEin(a, "", a.gueltig, " [" + folge(w, ". ") + ".]", SPERR, a.quelle);
        }
        assertKein(LANR_ART, LANR_GUELTIG + ",, " + folge("LANR", ". "));
        assertKein(LANR_ART, LANR_GUELTIG + ", und " + folge("LANR", ". "));
        assertKein(LANR_ART, LANR_GUELTIG + " / " + folge("LANR", ". "));
    }

    @Test
    void grenzfall_nachgestelltOhneDavorstehendeNummerKeinFund() {
        for (Art a : ARTEN) {
            assertKein(a, folge(wort(a), ". ") + ". ");
            assertKein(a, "Rechnung, " + folge(wort(a), "  "));
        }
    }

    @Test
    void grenzfall_lanrKontextNachVierFuellwoertern_wieVergleichsform() {
        assertEin(LANR_ART, "LANR: " + folge("LANR", ". ") + ". ", LANR_GUELTIG, "", SPERR, "lanr");
        assertEin(LANR_ART, "LANR: " + folge("LANR", " ") + " ", LANR_GUELTIG, "", SPERR, "lanr");
        assertKein(IK_ART, "IK: Praxis I. K. Nr " + IK_GUELTIG);
    }

    @Test
    void grenzfall_unsichtbaresZeichenInDerLueckeDurchsichtig_aberNichtZwischenBuchstaben() {
        assertEin(LANR_ART, "L. " + ZWSP + " A. N. R. ", LANR_GUELTIG, "", SPERR, "lanr");
        kein(LANR_ART, "L" + ZWSP + "A. N. R. ");
        assertEin(LANR_ART, "L. A. N. R" + ZWSP + ". ", LANR_GUELTIG, "", SPERR, "lanr");
    }

    @Test
    void grenzfall_einBuchstabeDavorOderDahinter_keinFund() {
        for (Art a : ARTEN) {
            String w = wort(a);
            kein(a, "A. " + folge(w, ". ") + ". ");
            kein(a, folge(w, ". ") + ". X ");
            kein(a, folge(w, ". ") + ". x. ");
            kein(a, folge(w, ". ") + ". " + folge(w, ". ") + ". ");
        }
    }

    @Test
    void grenzfall_wortBeiNachfolgendemWort_kontextBeginntVorDemWort() {
        for (Art a : ARTEN) {
            String w = wort(a);
            fund(a, folge(w, ". ") + ". des Behandlers ");
            fund(a, folge(w, ".  ") + ".-Nr. ");
            fund(a, folge(w, ". ") + ".-Nummer ");
            fund(a, folge(w, "\t") + " Nr. ");
            kein(a, folge(w, ". ") + ". des der die dann ");
        }
    }

    // ------------------------------------------------------------------ Laufzeit (AK-26, Erkennerebene)

    @Test
    void ak26a_punktUndLeerzeichenWiederholt_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "", "L. ", "", 0, "0 Funde: kein Kontextwort");
        }
    }

    @Test
    void ak26b_zweiBuchstabenMitPunktUndZweiLeerzeichen_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "", "I.  K.  ", "", 0, "0 Funde: kein Kontextwort");
        }
    }

    @Test
    void ak26c_einBuchstabeUndEineMillionLeerzeichen_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "L", " ", "", 0, "0 Funde: die Luecke endet nach drei Leerraumzeichen");
        }
    }

    @Test
    void ak26d_einBuchstabeUndPunktZweiLeerzeichenA_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "L", ".  A", "", 0, "0 Funde: kein Kontextwort");
        }
    }

    @Test
    void ak26e_initialenformMitNummerWiederholt_jedeNummerEinFund() {
        for (Art a : ARTEN) {
            String baustein = folge(wort(a), ". ") + ". " + a.gueltig + " ";
            assertEquals(22, baustein.length(), a.name + " Bausteinlaenge");
            int r = wiederholungen(1_000_000, "", baustein, "");
            assertEquals(45_455, r, a.name + " Wiederholungen: aufgerundet 1 000 000 / 22");
            laufzeit(a, "", baustein, "", r, r + " Funde: je Wiederholung eine Nummer");
            List<Finding> klein = scan(a, baustein.repeat(4));
            assertEquals(4, klein.size(), a.name + " Anzahl");
            for (Finding f : klein) {
                assertEquals(SPERR, f.confidence(), a.name + " Konfidenz");
            }
        }
    }

    @Test
    void ak26f_tabMitNummerWiederholt_jedeNummerEinFund() {
        for (Art a : ARTEN) {
            String baustein = folge(wort(a), "\t") + "\t" + a.gueltig + " ";
            assertEquals(18, baustein.length(), a.name + " Bausteinlaenge");
            int r = wiederholungen(1_000_000, "", baustein, "");
            assertEquals(55_556, r, a.name + " Wiederholungen: aufgerundet 1 000 000 / 18");
            laufzeit(a, "", baustein, "", r, r + " Funde: je Wiederholung eine Nummer");
        }
    }

    @Test
    void ak26i_vierLeerzeichenWiederholt_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "", folge(wort(a), "    ") + " " + a.gueltig + " ", "", 0, "0 Funde: vier Leerzeichen");
        }
    }

    @Test
    void ak26j_einBuchstabeMitPunktWiederholt_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "", "A. ", "", 0, "0 Funde: kein Kontextwort");
        }
    }

    @Test
    void ak26k_zweiBuchstabenMitNummerWiederholt_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "", "I. K. " + a.gueltig + " ", "", 0, "0 Funde: das Zwei-Buchstaben-Wort ist ausgenommen");
        }
    }

    @Test
    void ak26_vorgriffUeberDurchsichtigeLaeufe_keinFund() {
        String baustein = "L. " + String.valueOf(ZWSP).repeat(1_000) + " A ";
        for (Art a : ARTEN) {
            laufzeit(a, "", baustein, "", 0, "0 Funde: kein Kontextwort, Vorgriff je Lauf hoechstens einmal");
        }
    }

    @Test
    void ak26_unsichtbareZeichenInDerLueckeSindDurchsichtig_nummerWiederholt() {
        for (Art a : ARTEN) {
            String w = wort(a);
            String baustein = w.charAt(0) + ". " + ZWSP + w.charAt(1) + ". " + w.charAt(2) + "." + ZWSP + " "
                    + w.charAt(3) + ". " + a.gueltig + " ";
            int r = wiederholungen(1_000_000, "", baustein, "");
            laufzeit(a, "", baustein, "", r, r + " Funde: je Wiederholung eine Nummer");
        }
    }
}
