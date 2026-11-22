package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.LAENGE_10;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_7;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_8;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.TELEFON_PROBE;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.kv.KvTestHilfen.Art;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Tests der erweiterten Kontextregel (Feature kv-nummern-erkennung, Task T-5, Kap. 3.3 des Architektur-Plans) auf
 * Erkennerebene fuer LANR und IK: Fuellwoerter (AK-12), nachgestellter Kontext (AK-13), Zeilenumbruch (AK-14),
 * Aufzaehlung (AK-15), Fund am Textrand (AK-16 b), Laufzeit (AK-21), Determinismus (AK-26), die Erweiterungen aus
 * den Nachtraegen (Trenner, Vorwort-Trenner, unsichtbare Zeichen, Kombinationszeichen) und die bekannten Grenzen
 * (Kap. 3.5). Jeder Test laeuft fuer beide Nummernarten ({@code ARTEN}); die Meldungen nennen die Art.
 *
 * <p>Alle Nummern stammen aus {@code SynthetischeKvNummern}; hier steht keine neunstellige Ziffernfolge als
 * Literal, Texte entstehen per Verkettung.
 *
 * <p><b>Regel aus T-1 (Punkt 10):</b> Kein Test bildet zur Laufzeit eine neunstellige Nummer, die nach LANR- oder
 * IK-Verfahren gueltig ist und nicht mit {@code 00} beginnt. Jeder hier gebildete Text geht durch
 * {@link KvTestHilfen#assertKeinGueltigesNeunerfensterOhne00(String, String)}: Die Methode liest unabhaengig vom
 * Produkt alle
 * Ziffernketten (Bloecke mit einem Gruppentrenner dazwischen) und prueft jedes aus ganzen Bloecken gebildete Fenster
 * mit genau neun Ziffern, das also ein Segment des Scanners sein koennte. Ein Fenster ohne Praefix {@code 00} muss
 * nach beiden Referenzverfahren (aus {@code SynthetischeKvNummern}) ungueltig sein. Aufzaehlungen und Ketten aus
 * mehreren Konstanten sind damit ueber Blockgrenzen hinweg abgesichert.
 */
class KvKontextTest {

    // ------------------------------------------------------------------ Selbsttest des Waechters

    @Test
    void waechter_zaehltFensterUndErkenntGueltigeUnabhaengigVomPraefix() {
        // Ohne Ausnahme des Praefixes 00 ist jede Konstante ein Fenster, und beide gueltigen sind gueltig
        assertEquals(1, neunerfenster(LANR_GUELTIG, null)[0]);
        assertEquals(1, neunerfenster(LANR_GUELTIG, null)[1]);
        assertEquals(1, neunerfenster(IK_GUELTIG, null)[1]);
        // Mit Ausnahme des Praefixes 00 zaehlen sie nicht
        assertEquals(0, neunerfenster(LANR_GUELTIG, "00")[0]);
        assertEquals(0, neunerfenster("LANR " + gruppiert(IK_GUELTIG, " "), "00")[0]);
        // Zusammengesetzt aus Bloecken: "12" + Stellen 3 bis 9 einer Konstante (nach beiden Verfahren ungueltig)
        String ungueltig = "12" + LANR_GUELTIG.substring(2);
        assertEquals(1, neunerfenster(ungueltig, "00")[0]);
        assertEquals(0, neunerfenster(ungueltig, "00")[1]);
        // Fenster ueber Blockgrenzen: "12" und eine Neunerkonstante bilden elf Ziffern, das Fenster aus dem zweiten
        // Block allein beginnt mit 00; zwei Neuner-Ketten hintereinander sind zwei Fenster
        assertEquals(0, neunerfenster("12 " + LANR_GUELTIG, "00")[0]);
        assertEquals(2, neunerfenster(LANR_GUELTIG + "/" + IK_GUELTIG, null)[0]);
        // Fenster aus mehreren Bloecken (3-3-3) werden gefunden, getrennte Ketten (Komma) nicht verbunden
        assertEquals(1, neunerfenster(gruppiert(LANR_GUELTIG, " "), null)[0]);
        assertEquals(0, neunerfenster(LANR_GUELTIG.substring(0, 5) + ", " + LANR_GUELTIG.substring(5), null)[0]);
    }

    // ------------------------------------------------------------------ AK-12 (Fuellwoerter)

    @Test
    void ak12_kontextwortDesBehandlers_zweiFuellwoerter_wahrscheinlich() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + " des Behandlers ", a.gueltig, "");
        }
    }

    @Test
    void ak12_langesKontextwortDerPraxisDoppelpunkt_zweiFuellwoerter_wahrscheinlich() {
        // AK-12: "Institutionskennzeichen der Praxis: <Zahl>"; fuer LANR sinngemaess mit "Arztnummer"
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kwLang + " der Praxis: ", a.gueltig, "");
        }
    }

    @Test
    void ak12_einFuellwort_wahrscheinlich_nichtMehrSicher() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + " Nummer des ", a.gueltig, "");
            wahrscheinlich(a, a.kw + " heute ", a.gueltig, "");
        }
    }

    @Test
    void ak12_genauDreiWoerterSindErlaubt_vierWoerterBeendenDenKontext() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + " das ist die ", a.gueltig, "");
            assertKein(a, a.kw + " das ist die eine " + a.gueltig);
        }
    }

    @Test
    void ak12_vierOderMehrWoerter_keinFund() {
        for (Art a : ARTEN) {
            assertKein(a, a.kw + " der Praxis von Frau Beispiel " + a.gueltig);
            assertKein(a, a.kw + " eins zwei drei vier fuenf sechs " + a.gueltig);
        }
    }

    @Test
    void ak12_satzendeZwischenKontextwortUndZahl_keinFund() {
        for (Art a : ARTEN) {
            assertKein(a, "Die " + a.kw + " fehlt. Vorgang " + a.gueltig);
            assertKein(a, "Die " + a.kw + " fehlt! Vorgang " + a.gueltig);
            assertKein(a, "Die " + a.kw + " fehlt? Vorgang " + a.gueltig);
        }
    }

    @Test
    void ak12_ungueltigePruefziffer_mitFuellwoertern_fraglich() {
        for (Art a : ARTEN) {
            assertEin(a, a.kw + " des Behandlers ", a.ungueltig, "", PZ_UNGUELTIG);
        }
    }

    @Test
    void ak12_laengenabweichung_mitFuellwoertern_fraglich() {
        for (Art a : ARTEN) {
            assertEin(a, a.kw + " des Behandlers ", LAENGE_8, "", LAENGE);
            assertEin(a, a.kw + " des Behandlers ", LAENGE_10, "", LAENGE);
        }
    }

    @Test
    void ak12_fuellwoerterMitTrennerform_ganzeFolgeIstDerFund() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + " des Behandlers ", gruppiert(a.gueltig, "-"), "");
            wahrscheinlich(a, a.kw + " des Behandlers ", gruppiert(a.gueltig, String.valueOf(NBSP)), "");
        }
    }

    // ------------------------------------------------------------------ Grenzfaelle der Fuellwortregel

    @Test
    void grenzfall_punktNachFuellwort_beendetDenKontext() {
        for (Art a : ARTEN) {
            assertKein(a, a.kw + " Vorgang. " + a.gueltig);
            assertKein(a, a.kw + " des Vorgangs. " + a.gueltig);
        }
    }

    @Test
    void grenzfall_punktDirektHinterKontextwort_istAbkuerzungspunkt_direktBleibt() {
        for (Art a : ARTEN) {
            sicher(a, a.kw + ". ", a.gueltig, "");
            sicher(a, a.kw + "-Nr. ", a.gueltig, "");
            sicher(a, a.kw + " Nr. ", a.gueltig, "");
        }
    }

    @Test
    void grenzfall_nachsatzwortDirektHinterKontextwort_istKeinFuellwort() {
        for (Art a : ARTEN) {
            sicher(a, a.kw + " Nr ", a.gueltig, "");
            sicher(a, a.kw + " Nr Nr ", a.gueltig, "");
            sicher(a, a.kw + "-Nummer: ", a.gueltig, "");
        }
    }

    @Test
    void grenzfall_nachsatzwortNachFuellwort_zaehltAlsFuellwort() {
        for (Art a : ARTEN) {
            // "das" und "Nr" sind zwei Fuellwoerter: noch im Kontext, aber nicht mehr direkt
            wahrscheinlich(a, a.kw + " das Nr ", a.gueltig, "");
            // a, b, Nr sind drei Fuellwoerter: noch erlaubt; mit c sind es vier: Kontext beendet
            wahrscheinlich(a, a.kw + " a b Nr ", a.gueltig, "");
            assertKein(a, a.kw + " a b c Nr " + a.gueltig);
        }
    }

    @Test
    void grenzfall_ziffernblockOhneSegmentZaehltAlsFuellwort_auslegung() {
        // AK-12 nennt nur "Woerter"; Kap. 3.3 zaehlt auch einen Ziffernblock ohne Segment als Fuellwort. Die Variante,
        // die mehr schuetzt, gilt: kurze Ziffernbloecke (Hausnummer, Jahr) zwischen Kontextwort und Zahl schaden nicht.
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + " 12, ", a.gueltig, "");
            wahrscheinlich(a, a.kw + " 12, 34, 56, ", a.gueltig, "");
            assertKein(a, a.kw + " 12, 34, 56, 78, " + a.gueltig);
        }
    }

    @Test
    void grenzfall_fuellwortUndEinZeilenumbruch_auslegungMehrSchuetzen() {
        // AK-12 sagt "ohne Zeilenumbruch", Kap. 3.3 erlaubt Fuellwoerter und einen Umbruch zugleich (AK-14). Die
        // Variante, die mehr schuetzt, gilt: ein Umbruch zwischen Kontextwort und Zahl ist auch mit Fuellwort erlaubt.
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + " des\n", a.gueltig, "");
            wahrscheinlich(a, a.kw + ":\ndes Behandlers ", a.gueltig, "");
            assertKein(a, a.kw + "\ndes\n" + a.gueltig);
        }
    }

    @Test
    void grenzfall_fuellwortNachDemKontextwortBeendetDirekt_zahlDannNichtMehrSicher() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + ": die ", a.gueltig, "");
            sicher(a, a.kw + ": ", a.gueltig, "");
        }
    }

    // ------------------------------------------------------------------ AK-13 (nachgestelltes Kontextwort, offen)

    @Test
    void ak13_nummerZahlKlammerKontextwort_wahrscheinlich() {
        // AK-13: "Nummer <Zahl> (LANR)" und "Kennung <Zahl> (IK)"; das Kontextwort in Klammern bleibt im Text
        for (Art a : ARTEN) {
            wahrscheinlich(a, "Nummer ", a.gueltig, " (" + a.kw + ")");
            wahrscheinlich(a, "Kennung ", a.gueltig, " (" + a.kw + ")");
        }
    }

    @Test
    void ak13_kontextwortInKlammernWirdNichtZumFund() {
        for (Art a : ARTEN) {
            String text = "Kennung " + a.gueltig + " (" + a.kw + ")";
            List<Finding> funde = scan(a, text);
            assertEquals(1, funde.size(), a.name);
            assertTrue(funde.get(0).end() <= text.indexOf('('), a.name + " Fund reicht in die Klammer");
        }
    }

    @Test
    void ak13_eckigeKlammer_wahrscheinlich() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, "Nummer ", a.gueltig, " [" + a.kw + "]");
        }
    }

    @Test
    void ak13_ohneKlammer_kontextwortNachLeerraum_wahrscheinlich() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, "Nummer ", a.gueltig, " " + a.kw);
            wahrscheinlich(a, "Nummer ", a.gueltig, "  \t" + a.kw + ":");
        }
    }

    @Test
    void ak13_keinLeerraumZwischenZahlUndKlammer_wahrscheinlich() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, "Nummer ", a.gueltig, "(" + a.kw + ")");
            wahrscheinlich(a, "Nummer ", a.gueltig, "[" + a.kw + "]");
        }
    }

    @Test
    void ak13_andereLeerraumzeichenVorDerKlammer_wahrscheinlich() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, "Nummer ", a.gueltig, NBSP + "(" + a.kw + ")");
            wahrscheinlich(a, "Nummer ", a.gueltig, DUENN + "(" + a.kw + ")");
            wahrscheinlich(a, "Nummer ", a.gueltig, "\t(" + a.kw + ")");
        }
    }

    @Test
    void ak13_zweiKlammernLeerenOffen_keinFund() {
        for (Art a : ARTEN) {
            assertKein(a, "Nummer " + a.gueltig + " ((" + a.kw + "))");
            assertKein(a, "Nummer " + a.gueltig + " ([" + a.kw + "])");
            assertKein(a, "Nummer " + a.gueltig + " [(" + a.kw + ")]");
            assertKein(a, "Nummer " + a.gueltig + " ( ( " + a.kw + " ) )");
        }
    }

    @Test
    void ak13_wortZwischenZahlUndKontextwort_leertOffen_keinFund() {
        for (Art a : ARTEN) {
            assertKein(a, "Nummer " + a.gueltig + " (der " + a.kw + ")");
            assertKein(a, "Nummer " + a.gueltig + " siehe " + a.kw);
        }
    }

    @Test
    void ak13_anderesZeichenZwischenZahlUndKontextwort_leertOffen_keinFund() {
        for (String z : new String[] {",", ";", ":", "-", "/", "=", "#", ")", "\"", "!", "?"}) {
            for (Art a : ARTEN) {
                assertKein(a, "Nummer " + a.gueltig + " " + z + " (" + a.kw + ")");
            }
        }
        for (Art a : ARTEN) {
            // Punkt und Ziffernblock leeren offen ebenfalls
            assertKein(a, "Nummer " + a.gueltig + ". (" + a.kw + ")");
            assertKein(a, "Nummer " + a.gueltig + ", 12, (" + a.kw + ")");
        }
    }

    @Test
    void ak13_zeilenumbruchZwischenZahlUndKontextwort_leertOffen_keinFund() {
        for (Art a : ARTEN) {
            assertKein(a, "Nummer " + a.gueltig + "\n(" + a.kw + ")");
            assertKein(a, "Nummer " + a.gueltig + "\r\n" + a.kw);
        }
    }

    @Test
    void ak13_nachgestelltUngueltigePruefziffer_fraglich() {
        for (Art a : ARTEN) {
            assertEin(a, "Nummer ", a.ungueltig, " (" + a.kw + ")", PZ_UNGUELTIG);
        }
    }

    @Test
    void ak13_nachgestelltLaengenabweichung_fraglich() {
        for (Art a : ARTEN) {
            assertEin(a, "Nummer ", LAENGE_7, " (" + a.kw + ")", LAENGE);
        }
    }

    @Test
    void ak13_nachgestelltMitTrennerform_ganzeFolgeIstDerFund() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, "Nummer ", gruppiert(a.gueltig, " "), " (" + a.kw + ")");
        }
    }

    @Test
    void ak13_nachgestelltMitVorwortArztNr_wahrscheinlich() {
        wahrscheinlich(LANR_ART, "Nummer ", LANR_GUELTIG, " (Arzt-Nr.)");
        wahrscheinlich(LANR_ART, "Nummer ", LANR_GUELTIG, " (Arzt Nummer)");
        wahrscheinlich(IK_ART, "Nummer ", IK_GUELTIG, " (IK-Nr.)");
    }

    @Test
    void ak13_nachgestelltesKontextwortDerAnderenArt_erschliesstNicht() {
        assertKein(LANR_ART, "Nummer " + LANR_GUELTIG + " (IK)");
        assertKein(IK_ART, "Nummer " + IK_GUELTIG + " (LANR)");
    }

    @Test
    void ak13_nachgestelltesKontextwortAktiviertAuchDieFolgendeZahl_direkt() {
        // Kap. 3.3: Nach dem Kontextwort gilt aktiv und direkt; ")" ist neutral, die folgende Zahl ist "sicher"
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + " (" + a.kw + ") " + a.gueltig2, new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, a.sicher));
        }
    }

    @Test
    void ak13_zweiNummernHintereinanderVorDemKontextwort_beideSindFund() {
        // Angepasst durch R-2 (Nachtrag 22.11.2026): offen ist eine Folge von Segmenten; bisher (Kap. 3.3) ersetzte ein
        // neues Segment das offene, jetzt wird es angehaengt, wenn nur Leerraum dazwischen steht.
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + " " + a.gueltig2 + " (" + a.kw + ")", new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
        }
    }

    @Test
    void ak13_zahlOhneNachfolgendesKontextwort_keinFund() {
        for (Art a : ARTEN) {
            assertKein(a, "Nummer " + a.gueltig + " (");
            assertKein(a, "Nummer " + a.gueltig + " (Praxis)");
            assertKein(a, "Nummer " + a.gueltig);
        }
    }

    @Test
    void ak13_kontextwortOhneZahlDavor_keinFundKeineAusnahme() {
        for (Art a : ARTEN) {
            assertKein(a, "(" + a.kw + ")");
            assertKein(a, "Nummer (" + a.kw + ")");
            assertKein(a, "x (" + a.kw + ") y");
        }
    }

    // ------------------------------------------------------------------ AK-14 (Zeilenumbruch)

    @Test
    void ak14_einLfZwischenKontextwortMitTrennerUndZahl_wahrscheinlich() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + ":\n", a.gueltig, "");
        }
    }

    @Test
    void ak14_einCrLfZwischenKontextwortUndZahl_wahrscheinlich() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + ":\r\n", a.gueltig, "");
        }
    }

    @Test
    void ak14_einCrZwischenKontextwortUndZahl_wahrscheinlich() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + ":\r", a.gueltig, "");
        }
    }

    @Test
    void ak14_weitereZeilenumbrueche_nelUndZeilenUndAbsatztrenner_wahrscheinlich() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + NEL, a.gueltig, "");
            wahrscheinlich(a, a.kw + LS, a.gueltig, "");
            wahrscheinlich(a, a.kw + PS, a.gueltig, "");
        }
    }

    @Test
    void ak14_einUmbruchMitLeerraumDavorUndDanach_wahrscheinlich() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + ": \t\n    ", a.gueltig, "");
            wahrscheinlich(a, a.kw + "\r\n\t", a.gueltig, "");
        }
    }

    @Test
    void ak14_leerzeileZweiUmbrueche_keinFund() {
        for (Art a : ARTEN) {
            assertKein(a, a.kw + ":\n\n" + a.gueltig);
            assertKein(a, a.kw + ":\r\n\r\n" + a.gueltig);
            assertKein(a, a.kw + ":\r\r" + a.gueltig);
            assertKein(a, a.kw + ":\n\r\n" + a.gueltig);
            assertKein(a, a.kw + LS + LS + a.gueltig);
            assertKein(a, a.kw + NEL + PS + a.gueltig);
        }
    }

    @Test
    void ak14_leerzeileMitLeerraumDazwischen_keinFund() {
        for (Art a : ARTEN) {
            assertKein(a, a.kw + ":\n \n" + a.gueltig);
            assertKein(a, a.kw + ":\r\n\t\r\n" + a.gueltig);
        }
    }

    @Test
    void ak14_lfGefolgtVonCrIstNichtCrLf_zweiUmbrueche_keinFund() {
        // Nur CR LF (in dieser Reihenfolge) zaehlt als ein Umbruch; LF CR sind zwei
        for (Art a : ARTEN) {
            assertKein(a, a.kw + ":\n\r" + a.gueltig);
            wahrscheinlich(a, a.kw + ":\r\n", a.gueltig, "");
        }
    }

    @Test
    void ak14_umbruchVorDemKontextwort_stoertNicht() {
        for (Art a : ARTEN) {
            sicher(a, "Zeile eins\n\n" + a.kw + " ", a.gueltig, "");
            sicher(a, "Zeile eins\r\n" + a.kw + ": ", a.gueltig, "\r\nNaechste Zeile");
        }
    }

    @Test
    void ak14_nachLeerzeileNeuesKontextwort_gilt() {
        for (Art a : ARTEN) {
            sicher(a, a.kw + ":\n\n" + a.kw + " ", a.gueltig, "");
        }
    }

    @Test
    void ak14_ungueltigePruefziffer_nachUmbruch_fraglich() {
        for (Art a : ARTEN) {
            assertEin(a, a.kw + ":\n", a.ungueltig, "", PZ_UNGUELTIG);
        }
    }

    // ------------------------------------------------------------------ AK-15 (Aufzaehlung)

    @Test
    void ak15_aufzaehlungKommaUndUnd_dreiFunde_sicherWahrscheinlichWahrscheinlich() {
        // AK-15: "LANR <1>, <2> und <3>" ist sicher, wahrscheinlich, wahrscheinlich; ebenso fuer IK
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + " " + a.gueltig + ", " + a.gueltig2 + " und " + a.gueltig3,
                    new Erw(a.gueltig, a.sicher), new Erw(a.gueltig2, WAHRSCHEINLICH),
                    new Erw(a.gueltig3, WAHRSCHEINLICH));
        }
    }

    @Test
    void ak15_aufzaehlungSemikolon_zweiFunde() {
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + " " + a.gueltig + "; " + a.gueltig2, new Erw(a.gueltig, a.sicher),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
        }
    }

    @Test
    void ak15_aufzaehlungSchraegstrichMitLeerzeichen_ikSicherUndFraglich() {
        // AK-15: "IK <gueltig> / <ungueltig>" liefert zwei Funde (sicher, fraglich); ebenso fuer LANR
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + " " + a.gueltig + " / " + a.ungueltig, new Erw(a.gueltig, a.sicher),
                    new Erw(a.ungueltig, PZ_UNGUELTIG));
        }
    }

    @Test
    void ak15_aufzaehlungSchraegstrichOhneLeerzeichen_zweiFunde() {
        // Die zweite Kette beginnt hinter dem Segment der ersten (Kap. 3.2: Rest wird als neue Kette gelesen)
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + " " + a.gueltig + "/" + a.gueltig2, new Erw(a.gueltig, a.sicher),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
        }
    }

    @Test
    void ak15_aufzaehlungMitUndOderSowie_zweiBisDreiWoerterDazwischen() {
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + " " + a.gueltig + " oder " + a.gueltig2 + " sowie auch " + a.gueltig3,
                    new Erw(a.gueltig, a.sicher), new Erw(a.gueltig2, WAHRSCHEINLICH),
                    new Erw(a.gueltig3, WAHRSCHEINLICH));
        }
    }

    @Test
    void ak15_aufzaehlungMitUngueltigenUndLaengenabweichung() {
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + " " + a.gueltig + ", " + a.ungueltig + ", " + LAENGE_8,
                    new Erw(a.gueltig, a.sicher), new Erw(a.ungueltig, PZ_UNGUELTIG), new Erw(LAENGE_8, LAENGE));
        }
    }

    @Test
    void ak15_aufzaehlungMitTrennerformen_ganzeFolgenSindDieFunde() {
        for (Art a : ARTEN) {
            String erste = gruppiert(a.gueltig, " ");
            String zweite = gruppiert(a.gueltig2, "-");
            assertFolge(a, a.kw + " " + erste + ", " + zweite, new Erw(erste, a.sicher),
                    new Erw(zweite, WAHRSCHEINLICH));
        }
    }

    @Test
    void ak15_aufzaehlungZeilenweiseBisZurLeerzeile() {
        // Kap. 3.3: jedes ausgegebene Segment setzt die Umbruchzaehlung zurueck; die Leerzeile beendet die Liste
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + ":\n" + a.gueltig + "\n" + a.gueltig2 + "\n" + a.gueltig3 + "\n\n" + a.gueltig,
                    new Erw(a.gueltig, WAHRSCHEINLICH), new Erw(a.gueltig2, WAHRSCHEINLICH),
                    new Erw(a.gueltig3, WAHRSCHEINLICH));
        }
    }

    @Test
    void ak15_aufzaehlungZeilenweiseMitCrLfUndEinrueckung() {
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + " " + a.gueltig + "\r\n  " + a.gueltig2 + "\r\n\t" + a.gueltig3,
                    new Erw(a.gueltig, a.sicher), new Erw(a.gueltig2, WAHRSCHEINLICH),
                    new Erw(a.gueltig3, WAHRSCHEINLICH));
        }
    }

    @Test
    void ak15_aufzaehlungEndetMitSatzende_derRestIstKeinFund() {
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + " " + a.gueltig + ", " + a.gueltig2 + ". " + a.gueltig3,
                    new Erw(a.gueltig, a.sicher), new Erw(a.gueltig2, WAHRSCHEINLICH));
            assertFolge(a, a.kw + " " + a.gueltig + "! " + a.gueltig2, new Erw(a.gueltig, a.sicher));
        }
    }

    @Test
    void ak15_aufzaehlungEndetNachDreiVsVierWoertern() {
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + " " + a.gueltig + " das ist die " + a.gueltig2, new Erw(a.gueltig, a.sicher),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
            assertFolge(a, a.kw + " " + a.gueltig + " das ist die eine " + a.gueltig2, new Erw(a.gueltig, a.sicher));
        }
    }

    @Test
    void ak15_fuellwortzaehlungBeginntNachJedemFundNeu() {
        // zwei Woerter vor dem ersten Fund, drei vor dem zweiten: insgesamt fuenf, aber je Segment zurueckgesetzt
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + " des Behandlers " + a.gueltig + " und der Praxis " + a.gueltig2,
                    new Erw(a.gueltig, WAHRSCHEINLICH), new Erw(a.gueltig2, WAHRSCHEINLICH));
        }
    }

    // ------------------------------------------------------------------ AK-16 (b) (Fund am Textrand)

    @Test
    void ak16b_fundAmTextendeOhneFolgezeichen_direkterKontext() {
        for (Art a : ARTEN) {
            sicher(a, a.kw + " ", a.gueltig, "");
            sicher(a, "Praxis: " + a.kw + "=", a.gueltig, "");
        }
    }

    @Test
    void ak16b_fundAmTextanfangBeiNachgestelltemKontext() {
        // AK-16 (b): "<Zahl> (LANR)" und "<Zahl> (IK)" am Textanfang
        for (Art a : ARTEN) {
            wahrscheinlich(a, "", a.gueltig, " (" + a.kw + ")");
            wahrscheinlich(a, "", a.gueltig, " [" + a.kw + "]");
            wahrscheinlich(a, "", a.gueltig, " " + a.kw);
        }
    }

    @Test
    void ak16b_nachgestelltesKontextwortAmTextendeOhneFolgezeichen() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, "Nummer ", a.gueltig, " (" + a.kw);
            wahrscheinlich(a, "Nummer ", a.gueltig, " " + a.kw);
        }
    }

    @Test
    void ak16b_fundAmTextanfangUndTextendeZugleich() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, "", a.gueltig, "(" + a.kw + ")");
            sicher(a, a.kw, a.gueltig, "");
        }
    }

    // ------------------------------------------------------------------ Randfaelle aus T-3

    @Test
    void randfall_vorwortArztMitZeilenumbruchVorNr_keinKontext() {
        for (Art a : ARTEN) {
            assertKein(a, "Arzt\nNr " + a.gueltig);
            assertKein(a, "Arzt\r\nNummer " + a.gueltig);
            assertKein(a, "Arzt-\nNr " + a.gueltig);
        }
    }

    @Test
    void randfall_nachsatzwortMitBuchstabeAusserhalbBmp_keinKontext() {
        // U+1D42B (mathematisches fettes kleines r) statt "r": anderes Wort
        String r = zeichen(0x1D42B);
        for (Art a : ARTEN) {
            assertKein(a, "Arzt-N" + r + " " + a.gueltig);
            assertKein(a, "Arzt-" + r + "r " + a.gueltig);
        }
    }

    @Test
    void randfall_buchstabeAusserhalbBmpImFuellwort_zaehltAlsFuellwort() {
        String a1 = zeichen(0x1D400);
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + " " + a1 + " " + a1 + " " + a1 + " ", a.gueltig, "");
            assertKein(a, a.kw + " " + a1 + " " + a1 + " " + a1 + " " + a1 + " " + a.gueltig);
        }
    }

    // ------------------------------------------------------------------ Erweiterung 1: schmale Leerzeichen als Trenner

    @Test
    void erweiterung1_schmalesLeerzeichenU2009_istGruppentrenner() {
        for (Art a : ARTEN) {
            sicher(a, a.kw + " ", gruppiert(a.gueltig, String.valueOf(DUENN)), "");
            assertEin(a, a.kw + " ", gruppiert(a.ungueltig, String.valueOf(DUENN)), "", PZ_UNGUELTIG);
        }
    }

    @Test
    void erweiterung1_schmalesGeschuetztesLeerzeichenU202F_istGruppentrenner() {
        for (Art a : ARTEN) {
            sicher(a, a.kw + " ", gruppiert(a.gueltig, String.valueOf(SCHMAL_NBSP)), "");
            assertEin(a, a.kw + " ", gruppiert(a.ungueltig, String.valueOf(SCHMAL_NBSP)), "", PZ_UNGUELTIG);
        }
    }

    @Test
    void erweiterung1_gemischteTrennerUndTrennerAmEndeGehoertNichtZumFund() {
        for (Art a : ARTEN) {
            String gemischt = a.gueltig.substring(0, 3) + DUENN + a.gueltig.substring(3, 6) + SCHMAL_NBSP
                    + a.gueltig.substring(6);
            sicher(a, a.kw + " ", gemischt, "");
            sicher(a, a.kw + " ", a.gueltig, String.valueOf(DUENN));
            sicher(a, a.kw + " ", a.gueltig, String.valueOf(SCHMAL_NBSP) + "2025");
        }
    }

    @Test
    void erweiterung1_tabulatorBleibtKeinTrenner() {
        for (Art a : ARTEN) {
            assertKein(a, a.kw + " " + gruppiert(a.gueltig, "\t"));
        }
    }

    // ------------------------------------------------------------------ Erweiterung 2: Trenner Vorwort/Nachsatzwort

    @Test
    void erweiterung2_geschuetzterBindestrichU2011_zwischenVorwortUndNachsatzwort() {
        sicher(LANR_ART, "Arzt" + (char) 0x2011 + "Nr. ", LANR_GUELTIG, "");
        sicher(LANR_ART, "Arzt" + (char) 0x2011 + "Nummer: ", LANR_GUELTIG, "");
        // Fuer IK ist "IK" selbst Kontextwort; das Nachsatzwort bleibt hinter dem Trenner direkt
        sicher(IK_ART, "IK" + (char) 0x2011 + "Nr. ", IK_GUELTIG, "");
    }

    @Test
    void erweiterung2_halbgeviertstrichU2013_zwischenVorwortUndNachsatzwort() {
        sicher(LANR_ART, "Arzt" + (char) 0x2013 + "Nr. ", LANR_GUELTIG, "");
        sicher(LANR_ART, "Arzt" + (char) 0x2013 + "Nummer ", LANR_GUELTIG, "");
        sicher(IK_ART, "IK" + (char) 0x2013 + "Nr. ", IK_GUELTIG, "");
    }

    @Test
    void erweiterung2_andereStricheSindKeineTrenner_geviertstrichUndMinuszeichen() {
        // Grenze der Erweiterung: U+2014 (Geviertstrich) und U+2212 (Minuszeichen) gehoeren nicht dazu
        assertKein(LANR_ART, "Arzt" + (char) 0x2014 + "Nr " + LANR_GUELTIG);
        assertKein(LANR_ART, "Arzt" + (char) 0x2212 + "Nr " + LANR_GUELTIG);
    }

    @Test
    void erweiterung2_nurEinTrennerZwischenVorwortUndNachsatzwort() {
        assertKein(LANR_ART, "Arzt" + (char) 0x2011 + (char) 0x2013 + "Nr " + LANR_GUELTIG);
        assertKein(LANR_ART, "Arzt-" + (char) 0x2011 + "Nr " + LANR_GUELTIG);
    }

    // ------------------------------------------------------------------ Erweiterung 3: unsichtbare Zeichen

    private static final char[] FORMATZEICHEN = {SHY, ZWSP, ZWNJ, ZWJ, WORD_JOINER, BOM};

    /** Kontextwort {@code kw} mit {@code x} hinter dem ersten Zeichen und vor dem letzten. */
    private static String mitX(String kw, char x) {
        return kw.charAt(0) + String.valueOf(x) + kw.substring(1, kw.length() - 1) + x + kw.charAt(kw.length() - 1);
    }

    @Test
    void erweiterung3_formatzeichenMitteImKontextwort_werdenUebergangen() {
        for (char x : FORMATZEICHEN) {
            for (Art a : ARTEN) {
                sicher(a, mitX(a.kw, x) + " ", a.gueltig, "");
                sicher(a, mitX(a.kwLang, x) + ": ", a.gueltig, "");
            }
        }
    }

    @Test
    void erweiterung3_formatzeichenAmAnfangUndEndeDesKontextworts_werdenUebergangen() {
        for (char x : FORMATZEICHEN) {
            for (Art a : ARTEN) {
                sicher(a, x + a.kw + " ", a.gueltig, "");
                sicher(a, a.kw + x + " ", a.gueltig, "");
                sicher(a, "" + x + x + a.kw + x + x + ": ", a.gueltig, "");
                // ohne Abstand zur Zahl: das Formatzeichen gehoert nicht zum Fund
                sicher(a, a.kw + x, a.gueltig, "");
            }
        }
    }

    @Test
    void erweiterung3_variantenwaehlerU_FE00BisFE0F_werdenUebergangen() {
        for (int cp = 0xFE00; cp <= 0xFE0F; cp++) {
            char vs = (char) cp;
            for (Art a : ARTEN) {
                sicher(a, a.kw + vs + " ", a.gueltig, "");
                sicher(a, mitX(a.kw, vs) + " ", a.gueltig, "");
                sicher(a, vs + a.kw + " ", a.gueltig, "");
            }
        }
        sicher(LANR_ART, "LANR" + VS16 + ": ", LANR_GUELTIG, "");
    }

    @Test
    void erweiterung3_formatzeichenImVorwortUndNachsatzwort_werdenUebergangen() {
        for (char x : FORMATZEICHEN) {
            sicher(LANR_ART, "Arzt" + x + "-Nr ", LANR_GUELTIG, "");
            sicher(LANR_ART, "Arzt-" + x + "Nr ", LANR_GUELTIG, "");
            sicher(LANR_ART, "Ar" + x + "zt-N" + x + "r. ", LANR_GUELTIG, "");
            sicher(LANR_ART, "Arzt-Nr" + x + ". ", LANR_GUELTIG, "");
            sicher(LANR_ART, "Arzt" + x + (char) 0x2011 + x + "Nummer ", LANR_GUELTIG, "");
        }
    }

    @Test
    void erweiterung3_formatzeichenNachKontextwort_gehoerenNieZumFund() {
        for (Art a : ARTEN) {
            // Fund genau die Ziffern; unsichtbare Zeichen davor und danach veraendern keinen Index
            sicher(a, a.kw + " " + ZWSP + SHY + " ", a.gueltig, "" + ZWSP + SHY);
            sicher(a, a.kw + BOM + ":" + BOM, a.gueltig, "" + BOM);
        }
    }

    @Test
    void erweiterung3_nurFormatzeichenZwischenKontextwortUndZahl_zaehlenNichtAlsFuellwort() {
        for (Art a : ARTEN) {
            String viele = ("" + ZWSP + SHY + ZWJ).repeat(5);
            sicher(a, a.kw + " " + viele + " ", a.gueltig, "");
            sicher(a, a.kw + " " + ZWSP + " " + SHY + " " + ZWNJ + " " + BOM + " " + ZWJ + " ", a.gueltig, "");
        }
    }

    @Test
    void erweiterung3_formatzeichenTrenntTeilstuecke_kontextwortBleibtKontextwort() {
        // Umgedreht durch R-1 (Nachtrag 22.11.2026, Sicherheitsfund): Das Formatzeichen trennt Teilstuecke; ein
        // Teilstueck, das ein Kontextwort ist, bleibt eines. Vorher: "lanrx" bzw. "xlanr" ohne Kontext, kein Fund.
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + ZWSP + "x ", a.gueltig, "");
            sicher(a, "x" + SHY + a.kw + " ", a.gueltig, "");
        }
    }

    @Test
    void erweiterung3_fremdeBuchstabenMitFormatzeichen_keinKontext() {
        char kyrillischA = (char) 0x0410;
        assertKein(LANR_ART, "L" + kyrillischA + SHY + "NR " + LANR_GUELTIG);
        assertKein(IK_ART, "I" + (char) 0x212A + SHY + " " + IK_GUELTIG);
    }

    // ------------------------------------------------------------------ Erweiterung 4: Kombinationszeichen

    @Test
    void erweiterung4_alleinstehendesKombinationszeichen_zaehltNichtAlsFuellwort() {
        for (Art a : ARTEN) {
            String viele = (" " + AKUT).repeat(6);
            sicher(a, a.kw + viele + " ", a.gueltig, "");
            sicher(a, a.kw + " " + AKUT + AKUT + AKUT + AKUT + AKUT + " ", a.gueltig, "");
        }
    }

    @Test
    void erweiterung4_kombinationszeichenMitBasisbuchstabe_istEinFuellwort() {
        // "a" + U+0301 ist ein Wort mit Basisbuchstabe: drei erlaubt, das vierte beendet den Kontext
        String wort = "a" + AKUT;
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + " " + wort + " " + wort + " " + wort + " ", a.gueltig, "");
            assertKein(a, a.kw + " " + wort + " " + wort + " " + wort + " " + wort + " " + a.gueltig);
        }
    }

    @Test
    void erweiterung4_kombinationszeichenAmAnfangDerFolge_ohneBuchstabe_laesstNachgestelltenKontextZu() {
        // Auslegung: eine Folge ohne Buchstabe ist neutral und leert offen nicht (mehr schuetzen)
        for (Art a : ARTEN) {
            wahrscheinlich(a, "Nummer ", a.gueltig, " " + AKUT + " (" + a.kw + ")");
            wahrscheinlich(a, "Nummer ", a.gueltig, " " + ZWSP + "(" + a.kw + ")");
        }
    }

    // ------------------------------------------------------------------ bekannte Grenzen (Kap. 3.5)

    @Test
    void bekannteGrenze1_tabelleMitSpaltenueberschriftLeerzeileUndNummernDarunter_keinFund() {
        for (Art a : ARTEN) {
            assertKein(a, a.kw + "\n\n" + a.gueltig + "\n" + a.gueltig2 + "\n" + a.gueltig3);
            assertKein(a, a.kw + "\r\n\r\n" + a.gueltig + "\r\n" + a.gueltig2);
        }
    }

    @Test
    void bekannteGrenze3_punktDirektHinterKontextwortGiltAlsAbkuerzung_fundWahrscheinlich() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + ". Vorgang ", a.gueltig, "");
            wahrscheinlich(a, a.kw + ".\nVorgang ", a.gueltig, "");
        }
    }

    @Test
    void bekannteGrenze4_weitereNummerBisDreiWoerterHinterEinemFund_wirdFundFraglich() {
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + " " + a.gueltig + " Fax " + LAENGE_10, new Erw(a.gueltig, a.sicher),
                    new Erw(LAENGE_10, LAENGE));
            assertFolge(a, a.kw + " " + a.gueltig + " Fax " + TELEFON_PROBE, new Erw(a.gueltig, a.sicher),
                    new Erw(TELEFON_PROBE, LAENGE));
        }
    }

    @Test
    void bekannteGrenze5_nummerZwischenZweiKontextwoertern_derNachgestellteKandidatEntstehtJeErkenner() {
        // "IK <ik> LANR <lanr>": der LANR-Erkenner liest "LANR" als Kontextwort fuer die zuvor stehende IK-Zahl
        // (nachgestellt, Stufe nach LANR-Pruefziffer) und fuer die LANR; der IK-Erkenner liest die LANR-Zahl als
        // Aufzaehlung (Stufe nach IK-Pruefziffer). Der Resolver waehlt spaeter nach Konfidenz (T-6); hier der Kandidat.
        String text = "IK " + IK_GUELTIG + " LANR " + LANR_GUELTIG;
        // Feste Erwartung, von Hand gerechnet: Die IK-Zahl (Stellen 1-6 = 001234) ergibt nach LANR-Verfahren die Summe
        // 0+0+4+18+12+36 = 70, Pruefziffer 0, Stelle 7 ist aber 5: ungueltig, fraglich. Die LANR-Zahl (Stellen 3-8 =
        // 1,2,3,5,1,0) ergibt nach IK-Verfahren 2+2+6+5+2+0 = 17, Pruefziffer 7, Stelle 9 ist aber 1: ungueltig.
        assertFolge(LANR_ART, text, new Erw(IK_GUELTIG, PZ_UNGUELTIG), new Erw(LANR_GUELTIG, 0.95));
        assertFolge(IK_ART, text, new Erw(IK_GUELTIG, 0.92), new Erw(LANR_GUELTIG, PZ_UNGUELTIG));
    }

    @Test
    void bekannteGrenze7_nullbreitenzeichenInDerNummer_fundNurUeberDieErstenSechsZiffern() {
        for (Art a : ARTEN) {
            String nummer = a.gueltig.substring(0, 6) + ZWSP + a.gueltig.substring(6);
            List<Finding> funde = scan(a, a.kw + " " + nummer);
            assertEquals(1, funde.size(), a.name);
            Finding f = funde.get(0);
            assertEquals(a.gueltig.substring(0, 6), f.originalValue(), a.name);
            assertEquals(a.kw.length() + 1, f.start(), a.name);
            assertEquals(a.kw.length() + 7, f.end(), a.name);
            assertEquals(LAENGE, f.confidence(), a.name);
            assertEquals(a.quelleLaenge(), f.source(), a.name);
        }
    }

    @Test
    void bekannteGrenze8_kombinationszeichenDirektAmKontextwort_ergibtAnderesWortKeinKontext() {
        for (Art a : ARTEN) {
            assertKein(a, a.kw + AKUT + " " + a.gueltig);
            assertKein(a, a.kw + AKUT + ": " + a.gueltig);
            assertKein(a, AKUT + a.kw + " " + a.gueltig);
        }
    }

    @Test
    void bekannteGrenze9_kontextwortAlsEigenesWortHinterPunktOderBindestrich_giltAlsKontext_fehltreffer() {
        // "datei.ik" und "Mus-ik" (analog "datei.lanr"): das Wort hinter dem Punkt bzw. Bindestrich ist ein eigenes
        // Wort und damit Kontextwort; die Zahl dahinter wird (fraglich) ein Fund. Kein Schutzverlust, nur Fehltreffer.
        for (Art a : ARTEN) {
            String klein = a.kw.toLowerCase(Locale.ROOT);
            assertEin(a, "datei." + klein + " ", LAENGE_8, "", LAENGE);
            assertEin(a, "Mus-" + klein + " ", LAENGE_8, "", LAENGE);
            assertEin(a, "datei." + klein + " ", a.gueltig, "", a.sicher);
        }
    }

    // ------------------------------------------------------------------ AK-26 (Determinismus)

    @Test
    void ak26_gleicherTextLiefertGleicheFundlisteInGleicherReihenfolge_erweiterteKontextregel() {
        for (Art a : ARTEN) {
            String text = a.kw + " des Behandlers " + a.gueltig + ", " + a.gueltig2 + "\nNummer " + a.gueltig3 + " ("
                    + a.kw + ") " + a.ungueltig + "\r\n" + a.kw + ":\n" + LAENGE_7 + "\n\n" + a.gueltig;
            List<Finding> erste = scan(a, text);
            assertTrue(erste.size() >= 5, a.name + " Fundzahl " + erste.size());
            for (int i = 0; i < 5; i++) {
                assertEquals(erste, a.erkenner.detect(text), a.name);
            }
        }
    }

    // ------------------------------------------------------------------ AK-21 (Laufzeit, Erkennerebene, OF-8)

    @Test
    void ak21_laufzeit_kontextwortGefolgtVonWoerternOhneSatzende_keinFund() {
        // Formel: ein Kontextwort, danach 200 000 Woerter ohne Ziffer: kein Segment, 0 Funde (nach drei Woertern ist
        // der Kontext beendet, die uebrigen Woerter werden nur gezaehlt)
        for (Art a : ARTEN) {
            laufzeit(a, a.kw + " ", "wort ", "", 0, "0 Funde");
        }
    }

    @Test
    void ak21_laufzeit_kontextwortMitDreiWoertern_undZahlWiederholt_einFundJeWiederholung() {
        // Baustein "<KW> das ist die <Zahl> " mit drei Fuellwoertern; jede Wiederholung beginnt mit einem Kontextwort.
        // Formel: Wiederholungen r = ceil(1 000 000 / Bausteinlaenge); je Wiederholung ein Fund (0.80): r Funde.
        for (Art a : ARTEN) {
            String baustein = a.kw + " das ist die " + a.gueltig + " ";
            int r = ceilMillion(baustein);
            laufzeit(a, "", baustein, "", r, r + " Funde");
        }
    }

    @Test
    void ak21_laufzeit_kontextwortGefolgtVonNurZeilenumbruechen_keinFund() {
        // Formel: ein Kontextwort, danach eine Million Umbrueche: der zweite Umbruch beendet den Kontext; keine Ziffer:
        // 0 Funde
        for (Art a : ARTEN) {
            laufzeit(a, a.kw, "\n", "", 0, "0 Funde");
            laufzeit(a, a.kw, "\r\n", "", 0, "0 Funde");
        }
    }

    @Test
    void ak21_laufzeit_kontextwortUmbruchZahlUmbruchWiederholt_einFundJeWiederholung() {
        // Baustein "<KW>\n<Zahl>\n": je Wiederholung ein Kontextwort, ein Umbruch, die Zahl (Fund, 0.80), ein Umbruch.
        // Formel: r = ceil(1 000 000 / Bausteinlaenge) Funde.
        for (Art a : ARTEN) {
            String baustein = a.kw + "\n" + a.gueltig + "\n";
            int r = ceilMillion(baustein);
            laufzeit(a, "", baustein, "", r, r + " Funde");
        }
    }

    @Test
    void ak21_laufzeit_zahlKlammerKontextwortWiederholt_jedeZahlIstEinFund() {
        // Baustein "<Zahl> (<KW>) ": Die erste Zahl wird vom ersten Kontextwort nachgestellt erschlossen; jedes
        // Kontextwort macht danach die folgende Zahl direkt (Kap. 3.3: ")" ist neutral). Jede Zahl ist ein Fund.
        // Formel: r = ceil(1 000 000 / Bausteinlaenge) Zahlen = r Funde.
        for (Art a : ARTEN) {
            String baustein = a.gueltig + " (" + a.kw + ") ";
            int r = ceilMillion(baustein);
            laufzeit(a, "", baustein, "", r, r + " Funde");
        }
    }

    @Test
    void ak21_laufzeit_kontextwortGefolgtVonEinerMillionFormatzeichen_keinFund() {
        // Formel: ein Wort aus "KW" und einer Million U+00AD (unsichtbar, uebersprungen): Vergleichstext "lanr"/"ik"
        // ist Kontextwort, keine Ziffer folgt: 0 Funde. Die Wortlesung ist ein Zug ueber alle Zeichen.
        for (Art a : ARTEN) {
            laufzeit(a, a.kw, String.valueOf(SHY), "", 0, "0 Funde");
        }
    }

    @Test
    void ak21_laufzeit_eineMillionFormatzeichenOhneKontextwort_keinFund() {
        for (Art a : ARTEN) {
            laufzeit(a, "", String.valueOf(ZWSP), "", 0, "0 Funde");
            laufzeit(a, "", "" + ZWJ + SHY, "", 0, "0 Funde");
        }
    }

    @Test
    void ak21_laufzeit_kontextwortMitFormatzeichenUndZahlWiederholt_einFundJeWiederholung() {
        // Baustein "L<SHY>ANR <Zahl> ": je Wiederholung ein Kontextwort (mit Formatzeichen) und eine Zahl.
        // Formel: r = ceil(1 000 000 / Bausteinlaenge) Funde.
        for (Art a : ARTEN) {
            String baustein = a.kw.charAt(0) + String.valueOf(SHY) + a.kw.substring(1) + " " + a.gueltig + " ";
            int r = ceilMillion(baustein);
            laufzeit(a, "", baustein, "", r, r + " Funde");
        }
    }

    @Test
    void ak21_laufzeit_kontextwortGefolgtVonEinerMillionKombinationszeichenUndZahl_einFund() {
        // Eine Folge aus nur Kombinationszeichen ist neutral (kein Fuellwort): Kontext bleibt direkt.
        // Formel: genau ein Fund (die Zahl am Ende).
        for (Art a : ARTEN) {
            laufzeit(a, a.kw + " ", String.valueOf(AKUT), " " + a.gueltig, 1, "1 Fund");
        }
    }

    @Test
    void ak21_laufzeit_vorwortMitVielenFormatzeichenVorNachsatzwort_einFundNurFuerLanr() {
        // "Arzt-" + eine Million U+00AD + "Nr " + Zahl: Der Nachsatz-Vorgriff liest die Folge einmal, verbraucht sie.
        // Formel: LANR 1 Fund (Kontextwort Arzt-Nr, Zahl direkt); IK 0 Funde ("Arzt" ist kein Kontextwort der Art IK,
        // "Nr" bei nicht direkt ein Fuellwort, die Zahl wird nur offen und nie ausgegeben).
        for (Art a : ARTEN) {
            laufzeit(a, "Arzt-", String.valueOf(SHY), "Nr " + a.gueltig, a.lanr ? 1 : 0, a.lanr ? "1 Fund" : "0 Funde");
        }
    }

    @Test
    void ak21_laufzeit_vorwortWiederholtOhneNachsatzwort_keinFund() {
        // "Arzt " wiederholt: jede Wiederholung schaut das folgende Wort einmal voraus (es ist wieder "Arzt"), liest es
        // dann regulaer noch einmal: linear. Keine Ziffer: 0 Funde.
        for (Art a : ARTEN) {
            laufzeit(a, "", "Arzt ", "", 0, "0 Funde");
            laufzeit(a, "", "Arzt-", "", 0, "0 Funde");
        }
    }

    @Test
    void ak21_laufzeit_vorwortGefolgtVonEinemSehrLangenWort_keinFund() {
        // "Arzt-" + eine Million Buchstaben: Der Vorgriff liest das lange Wort, es ist kein Nachsatzwort; die Haupt-
        // schleife liest es danach noch einmal (zweimal, linear). Keine Ziffer: 0 Funde.
        for (Art a : ARTEN) {
            laufzeit(a, "Arzt-", "x", "", 0, "0 Funde");
        }
    }
}
