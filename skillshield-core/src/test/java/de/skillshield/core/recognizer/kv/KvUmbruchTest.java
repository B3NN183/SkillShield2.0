package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.kv.KvTestHilfen.Art;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Tests zu CR, unsichtbarem Zeichen, LF als ein Zeilenumbruch (Feature kv-trenner-und-umbruch, Task T-2, D-1; AK-1 bis
 * AK-5, Laufzeit AK-26 (g), (h) auf Erkennerebene). Jeder Text geht durch den Waechter und die Differenzpruefungen
 * gegen beide eingefrorenen Staende in {@link KvTestHilfen#scan}. Nummern stammen aus {@code SynthetischeKvNummern}.
 * Die Restformen (zwei Umbrueche) stehen zusaetzlich in
 * {@code KvSperrschriftTest.bekannteGrenze15Rest_crLeerraumLfUndNel_zweiUmbrueche}.
 */
class KvUmbruchTest {

    private static final double SPERR = 0.80;
    /** Obergrenze der Konfidenz fuer eine Nummer unmittelbar hinter dem Zwei-Buchstaben-Wort {@code I K}. */
    private static final double ZWEI_BUCHSTABEN_DECKEL = 0.50;
    private static final char MARKE = '#';

    /** Die sechs unsichtbaren Zeichen aus AK-1 und der Variantenwaehler U+FE00 (gemeinsam in KvTestHilfen). */
    private static final String[] UZ = uzAk1Texte();

    private static String wort(Art a) {
        return a.lanr ? "LANR" : "IKNR";
    }

    /** Das gesperrt geschriebene Kontextwort ab drei Buchstaben, mit Leerzeichen getrennt. */
    private static String sperr(Art a) {
        StringBuilder b = new StringBuilder();
        for (char c : wort(a).toCharArray()) {
            b.append(b.length() > 0 ? " " : "").append(c);
        }
        return b.toString();
    }

    private static String umbruch(String uz) {
        return "\r" + uz + "\n";
    }

    /** Ersetzt jede {@code #} der Vorlage durch CR, {@code uz}, LF. */
    private static String mitUmbruch(String vorlage, String uz) {
        return vorlage.replace(String.valueOf(MARKE), umbruch(uz));
    }

    /** Um wie viele Zeichen liegt die Position {@code pos} des CR-LF-Textes im Text mit {@code uz} weiter rechts? */
    private static int verschiebung(String ohne, int pos, int uzLaenge) {
        int zaehler = 0;
        for (int i = ohne.indexOf("\r\n"); i >= 0 && i + 2 <= pos; i = ohne.indexOf("\r\n", i + 2)) {
            zaehler++;
        }
        return zaehler * uzLaenge;
    }

    /**
     * AK-5: Die Fundliste mit CR UZ LF ist gleich der mit CR LF (Typ wie Wert, Bereich um die Zahl der unsichtbaren
     * Zeichen davor verschoben, Konfidenz, Quelle). Liefert die Funde mit unsichtbarem Zeichen.
     */
    private static List<Finding> gleichWieCrLf(Art a, String vorlage, String uz) {
        String ohne = mitUmbruch(vorlage, "");
        List<Finding> alt = scan(a, ohne);
        List<Finding> neu = scan(a, mitUmbruch(vorlage, uz));
        assertEquals(alt.size(), neu.size(), a.name + " Zahl der Funde mit " + uz.length() + " unsichtbaren Zeichen");
        for (int i = 0; i < alt.size(); i++) {
            Finding x = alt.get(i);
            Finding y = neu.get(i);
            assertEquals(x.originalValue(), y.originalValue(), a.name + " Wert " + i);
            assertEquals(x.type(), y.type(), a.name + " Typ " + i);
            assertEquals(x.start() + verschiebung(ohne, x.start(), uz.length()), y.start(), a.name + " Start " + i);
            assertEquals(x.end() + verschiebung(ohne, x.end(), uz.length()), y.end(), a.name + " Ende " + i);
            assertEquals(x.confidence(), y.confidence(), a.name + " Konfidenz " + i);
            assertEquals(x.source(), y.source(), a.name + " Quelle " + i);
        }
        return neu;
    }

    // ------------------------------------------------------------------ AK-1, AK-2

    @Test
    void ak1_grundfall_jedesUnsichtbareZeichen_einFundWieMitCrLf() {
        for (Art a : ARTEN) {
            for (String uz : UZ) {
                assertEin(a, a.kw + ":" + umbruch(uz), a.gueltig, "", WAHRSCHEINLICH);
                assertEin(a, a.kwLang + ":" + umbruch(uz), a.gueltig, "", WAHRSCHEINLICH);
                assertEin(a, a.kw + umbruch(uz), a.gueltig, "", WAHRSCHEINLICH);
                gleichWieCrLf(a, a.kw + ":#" + a.gueltig, uz);
            }
        }
    }

    @Test
    void ak1_stufeQuelleUndBereichOhneUnsichtbaresZeichenImFund() {
        for (Art a : ARTEN) {
            String vor = a.kw + ":" + umbruch(String.valueOf(ZWSP));
            List<Finding> funde = scan(a, vor + a.gueltig + ".");
            assertEquals(1, funde.size(), a.name + " Anzahl");
            assertEquals(vor.length(), funde.get(0).start(), a.name + " Start");
            assertEquals(vor.length() + a.gueltig.length(), funde.get(0).end(), a.name + " Ende");
            assertEquals(a.quelle, funde.get(0).source(), a.name + " Quelle");
        }
    }

    @Test
    void ak1_nachNummerOderUngueltigOderLaenge_wieMitCrLf() {
        for (Art a : ARTEN) {
            for (String uz : UZ) {
                assertEin(a, a.kw + ":" + umbruch(uz), a.ungueltig, "", PZ_UNGUELTIG, a.quellePruefziffer());
                assertEin(a, a.kw + ":" + umbruch(uz), LAENGE_7, "", LAENGE, a.quelleLaenge());
            }
        }
    }

    @Test
    void ak2_mehrereUnsichtbareZeichenZwischenCrUndLf_einFund() {
        String[] folgen = {"" + ZWSP + BOM + SHY, "" + ZWSP + ZWSP + ZWSP + ZWSP, "" + SHY + WORD_JOINER + LRM + VS16,
            "" + VS1 + ZWSP + BOM + SHY + WORD_JOINER + LRM + VS16};
        for (Art a : ARTEN) {
            for (String uz : folgen) {
                assertEin(a, a.kw + ":" + umbruch(uz), a.gueltig, "", WAHRSCHEINLICH);
                gleichWieCrLf(a, a.kw + ":#" + a.gueltig, uz);
            }
        }
    }

    // ------------------------------------------------------------------ AK-3

    @Test
    void ak3_gesperrteKontextwoerter_einFundMit080() {
        for (Art a : ARTEN) {
            for (String uz : UZ) {
                assertEin(a, sperr(a) + ":" + umbruch(uz), a.gueltig, "", SPERR);
                assertEin(a, sperr(a) + umbruch(uz), a.gueltig, "", SPERR);
            }
        }
    }

    @Test
    void ak3_zweiBuchstabenWortIK_keinFundWieMitCrLf() {
        for (String uz : UZ) {
            assertKein(IK_ART, "I K:" + umbruch(uz) + IK_ART.gueltig);
            assertKein(IK_ART, "I K" + umbruch(uz) + IK_ART.gueltig);
        }
        assertKein(IK_ART, "I K:\r\n" + IK_ART.gueltig);
        // Gegenprobe ohne Umbruch: I K erschliesst die unmittelbar folgende Nummer (0,50)
        assertEin(IK_ART, "I K: ", IK_ART.gueltig, "", ZWEI_BUCHSTABEN_DECKEL, IK_ART.quelle);
    }

    // ------------------------------------------------------------------ AK-4 (zwei Umbrueche bleiben zwei)

    @Test
    void ak4_zweiUmbrueche_keinFund() {
        String uz = String.valueOf(ZWSP);
        String[] folgen = {"\r" + uz + "\r", "\n" + uz + "\n", "\n" + uz + "\r", "\r\n" + uz + "\n",
            "\r" + uz + "\n" + uz + "\n", "\r \n", "\r" + NBSP + "\n", "\r" + NEL, "\r" + LS, "\r" + PS,
            "\r" + uz + "\n\n", "\r" + uz + "\n\r" + uz + "\n", "\r" + uz + NEL, "\r" + uz + LS, "\r" + uz + PS};
        for (Art a : ARTEN) {
            for (String folge : folgen) {
                assertKein(a, a.kw + ":" + folge + a.gueltig);
                assertKein(a, sperr(a) + ":" + folge + a.gueltig);
            }
        }
    }

    @Test
    void ak4_gegenprobe_genauEinUmbruchBleibtFund() {
        String uz = String.valueOf(ZWSP);
        String[] folgen = {"\r\n", "\n", "\r", "\r" + uz + "\n", NEL + "", LS + "", PS + ""};
        for (Art a : ARTEN) {
            for (String folge : folgen) {
                assertEin(a, a.kw + ":" + folge, a.gueltig, "", WAHRSCHEINLICH);
                assertEin(a, sperr(a) + ":" + folge, a.gueltig, "", SPERR);
            }
        }
    }

    @Test
    void ak4_crUnsichtbareZeichenOhneLf_einUmbruch_zweiCrMitZeichenDazwischen_zwei() {
        for (Art a : ARTEN) {
            assertEin(a, a.kw + ":\r" + ZWSP + ZWSP, a.gueltig, "", WAHRSCHEINLICH);
            assertKein(a, a.kw + ":\r" + ZWSP + "\r" + ZWSP + a.gueltig);
        }
    }

    @Test
    void ak4_crUnsichtbaresZeichenZiffernLf_zweiUmbrueche_keinFund() {
        String uz = String.valueOf(ZWSP);
        for (Art a : ARTEN) {
            assertKein(a, a.kw + ":\r" + uz + "7\n" + a.gueltig);
            assertKein(a, sperr(a) + ":\r" + uz + "7\n" + a.gueltig);
        }
    }

    @Test
    void ak2_unsichtbaresZeichenAusserhalbDerBmp_einFundWieMitCrLf() {
        String[] folgen = {new String(Character.toChars(0xE0020)), new String(Character.toChars(0x1D173)),
            ZWSP + new String(Character.toChars(0xE0020)) + BOM};
        for (Art a : ARTEN) {
            for (String uz : folgen) {
                assertEin(a, a.kw + ":" + umbruch(uz), a.gueltig, "", WAHRSCHEINLICH);
                gleichWieCrLf(a, a.kw + ":#" + a.gueltig, uz);
            }
        }
    }

    // ------------------------------------------------------------------ AK-5 (alle Stellen)

    @Test
    void ak5a_leerzeile_zweiMalCrUzLfKeinFund_einMalFund() {
        for (Art a : ARTEN) {
            for (String uz : UZ) {
                assertEquals(List.of(), gleichWieCrLf(a, a.kw + ":##" + a.gueltig, uz));
                assertEquals(1, gleichWieCrLf(a, a.kw + ":#" + a.gueltig, uz).size(), a.name + " ein Umbruch");
                assertEquals(List.of(), gleichWieCrLf(a, sperr(a) + ":##" + a.gueltig, uz));
            }
        }
    }

    @Test
    void ak5b_nachgestellterKontext_nummerUmbruchKontextwort_keinFund() {
        for (Art a : ARTEN) {
            for (String uz : UZ) {
                assertEquals(List.of(), gleichWieCrLf(a, a.gueltig + "#" + a.kw, uz));
                assertEquals(List.of(), gleichWieCrLf(a, a.gueltig + "#" + sperr(a), uz));
            }
        }
    }

    @Test
    void ak5c_kommaRegel_nummerKommaUmbruchKontextwort_keinFund() {
        for (Art a : ARTEN) {
            for (String uz : UZ) {
                assertEquals(List.of(), gleichWieCrLf(a, a.gueltig + ",#" + a.kw, uz));
                assertEquals(List.of(), gleichWieCrLf(a, a.gueltig + ";#" + sperr(a), uz));
            }
        }
    }

    @Test
    void ak5d_aufzaehlungZeilenweise_zweiFunde() {
        for (Art a : ARTEN) {
            for (String uz : UZ) {
                assertEquals(2, gleichWieCrLf(a, a.kw + " " + a.gueltig + "#" + a.gueltig2, uz).size(), a.name);
                assertEquals(2, gleichWieCrLf(a, sperr(a) + " " + a.gueltig + "#" + a.gueltig2, uz).size(), a.name);
            }
        }
    }

    @Test
    void ak5e_sperrfolge_umbruchBeendetDieFolge_einFundBeiVollemWortVorDemUmbruch() {
        for (Art a : ARTEN) {
            String w = wort(a);
            String rest = sperr(a).substring(2);
            for (String uz : UZ) {
                assertEquals(List.of(), gleichWieCrLf(a, w.charAt(0) + "#" + rest + " " + a.gueltig, uz));
                List<Finding> funde = gleichWieCrLf(a, sperr(a) + "#" + a.gueltig, uz);
                assertEquals(1, funde.size(), a.name + " Fund hinter dem vollen Wort");
                assertEquals(SPERR, funde.get(0).confidence(), a.name + " Konfidenz");
            }
        }
    }

    @Test
    void ak5f_nummerierteListeHinterDemUmbruch_gleicheFunde() {
        for (Art a : ARTEN) {
            for (String uz : UZ) {
                List<Finding> funde = gleichWieCrLf(a, a.kw + ":#1. " + a.gueltig, uz);
                assertEquals(1, funde.size(), a.name + " Fund hinter der nummerierten Liste");
                assertEquals(SPERR, funde.get(0).confidence(), a.name + " Konfidenz");
            }
        }
    }

    // ------------------------------------------------------------------ Restformen (zusaetzlich zu KvSperrschriftTest)

    @Test
    void bekannteGrenze15Rest_leerraumOderSichtbaresZeichenZwischenCrUndLf_zweiUmbrueche() {
        String uz = String.valueOf(ZWSP);
        String[] folgen = {"\r" + uz + " \n", "\r \n", "\r " + uz + "\n", "\r" + uz + "x\n", "\r" + uz + ".\n",
            "\r\t\n", "\r" + uz + AKUT + "\n", "\r" + AKUT + "\n"};
        for (Art a : ARTEN) {
            for (String folge : folgen) {
                assertKein(a, a.kw + ":" + folge + a.gueltig);
            }
        }
    }

    // ------------------------------------------------------------------ Zusaetzliche Funde nur mit CR UZ+ LF

    private static List<String> bausteine() {
        String uz = String.valueOf(ZWSP);
        // Doppelt aufgefuehrte Bausteine (CR UZ LF, LANR_GUELTIG, IK_GUELTIG) sind Absicht: Sie erhoehen die
        // Ziehwahrscheinlichkeit. Reihenfolge und Anzahl nicht aendern (sonst aendert sich die Zufallsfolge).
        return List.of("LANR", "IKNR", "IK", "Arztnummer", "L A N R", "I K", "L", "A N R", ":", ", ", ". ", " ",
                "\r", "\n", "\r\n", uz, String.valueOf(SHY), String.valueOf(BOM), String.valueOf(NBSP),
                String.valueOf(NEL), String.valueOf(LS), "Nr", "und", "x", "1. ", "\r" + uz + "\n",
                "\r" + uz + "\n", "\r" + uz + SHY + "\n", LANR_GUELTIG, IK_GUELTIG, LANR_GUELTIG, IK_GUELTIG,
                LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, LAENGE_7);
    }

    @Test
    void zusaetzlicheFundeGegenDenEingefrorenenStand_nurInTextenMitCrUnsichtbarLf() {
        List<String> bausteine = bausteine();
        Random zufall = new Random(31_415_926);
        int ohneFolge = 0;
        int mitFolge = 0;
        int mitFolgeAbweichend = 0;
        for (int t = 0; t < 40_000; t++) {
            StringBuilder b = new StringBuilder();
            int teile = 3 + zufall.nextInt(10);
            for (int k = 0; k < teile; k++) {
                b.append(bausteine.get(zufall.nextInt(bausteine.size())));
                b.append(zufall.nextInt(3) == 0 ? " " : "");
            }
            String text = b.toString();
            boolean folge = enthaeltCrUnsichtbarLf(text);
            if (folge) {
                mitFolge++;
            } else {
                ohneFolge++;
            }
            for (Art a : ARTEN) {
                List<Finding> neu = scan(a, text);
                boolean gleich = neu.equals(a.standLuecken.detect(text));
                if (folge) {
                    mitFolgeAbweichend += gleich ? 0 : 1;
                } else {
                    assertTrue(gleich, a.name + " Abweichung ohne CR UZ+ LF, Textnummer " + t);
                }
            }
        }
        // Gemessen (fester Seed): 20 720 Texte ohne und 19 280 Texte mit der Folge; bei 1 063 Paaren (Text, Art)
        // mit der Folge weicht die Fundliste ab. Die Mindestzahlen liegen bewusst darunter.
        assertTrue(ohneFolge >= 5_000, "Texte ohne die Folge: " + ohneFolge);
        assertTrue(mitFolge >= 5_000, "Texte mit der Folge: " + mitFolge);
        assertTrue(mitFolgeAbweichend >= 200, "Texte mit Folge und anderer Fundliste: " + mitFolgeAbweichend);
    }

    // ------------------------------------------------------------------ Laufzeit (AK-26 g, h; Erkennerebene)

    @Test
    void ak26h_crUndEineMillionUnsichtbareZeichen_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "\r", String.valueOf(ZWSP), "", 0, "0 Funde: ein einziger Lauf nach dem CR");
        }
    }

    @Test
    void ak26h_crUnsichtbaresZeichenLfWiederholt_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "", umbruch(String.valueOf(ZWSP)), "", 0, "0 Funde: kein Kontextwort");
        }
    }

    @Test
    void ak26g_kontextwortCrUnsichtbaresZeichenLfNummerWiederholt_jedeNummerEinFund() {
        for (Art a : ARTEN) {
            String baustein = wort(a) + ":" + umbruch(String.valueOf(ZWSP)) + a.gueltig + " ";
            assertEquals(18, baustein.length(), a.name + " Bausteinlaenge");
            int r = wiederholungen(1_000_000, "", baustein, "");
            assertEquals(55_556, r, a.name + " Wiederholungen: aufgerundet 1 000 000 / 18");
            laufzeit(a, "", baustein, "", r, r + " Funde: je Wiederholung eine Nummer");
            List<Finding> klein = scan(a, baustein.repeat(4));
            assertEquals(4, klein.size(), a.name + " Anzahl");
            for (Finding f : klein) {
                assertEquals(SPERR, f.confidence(), a.name + " Konfidenz");
            }
        }
    }
}
