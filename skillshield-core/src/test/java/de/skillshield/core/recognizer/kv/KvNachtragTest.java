package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.LAENGE_8;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.*;

import de.skillshield.core.recognizer.kv.KvTestHilfen.Art;
import org.junit.jupiter.api.Test;

/**
 * Tests des Nachtrags der Hauptsitzung vom 22.11.2026 (Feature kv-nummern-erkennung, Task T-5, Durchlauf 2) auf
 * Erkennerebene fuer LANR und IK: R-1 (unsichtbare Zeichen trennen Teilstuecke, Nachbarwoerter verschmelzen nicht),
 * R-2 (mehrere Nummern vor einem nachgestellten Kontextwort), R-3 (nummerierte Listen), R-4 (Abkuerzungspunkt), die
 * bekannten Grenzen 10 und 11 und die Laufzeit der neuen Regeln. Der Eigenschaftstest zu R-1 steht in
 * {@code KvErkennungEigenschaftTest}. Jeder Test laeuft fuer beide Nummernarten; Nummern stammen aus
 * {@code SynthetischeKvNummern}, Texte entstehen per Verkettung, und jeder Text geht durch den Waechter der
 * Testdatenregel (T-1 Punkt 10) in {@code KvTestHilfen}.
 */
class KvNachtragTest {

    private static final char LRM = (char) 0x200E;
    private static final char[] UNSICHTBAR = {ZWSP, SHY, WORD_JOINER, BOM, LRM};

    /** Das Kontextwort {@code kw}, hinter dem {@code k}-ten Zeichen durch {@code x} geteilt. */
    private static String geteilt(String kw, int k, char x) {
        return mitZeichen(kw, k, String.valueOf(x));
    }

    private static String mitZeichen(String kw, int k, String x) {
        return kw.substring(0, k) + x + kw.substring(k);
    }

    // ------------------------------------------------------------------ R-1: angeklebtes Wort vor dem Kontextwort

    @Test
    void r1_vorangestelltesWortMitUnsichtbaremZeichen_kontextwortBleibtKontextwort_direkt() {
        for (char x : UNSICHTBAR) {
            for (Art a : ARTEN) {
                sicher(a, "Praxis" + x + a.kw + " ", a.gueltig, "");
                sicher(a, "Die" + x + a.kw + ": ", a.gueltig, "");
                sicher(a, "Die" + x + a.kwLang + " ", a.gueltig, "");
            }
        }
    }

    @Test
    void r1_vorangestelltesWortMitZweiUnsichtbarenZeichen_kontextwortBleibtKontextwort() {
        for (Art a : ARTEN) {
            sicher(a, "Praxis" + ZWSP + SHY + a.kw + " ", a.gueltig, "");
            sicher(a, "Praxis" + ZWSP + a.kw + SHY + " ", a.gueltig, "");
        }
    }

    @Test
    void r1_zweiTeilstueckeErgebenZusammenDasKontextwort_davorEinAngeklebtesWort() {
        for (char x : UNSICHTBAR) {
            for (Art a : ARTEN) {
                sicher(a, "Praxis" + x + geteilt(a.kw, 1, SHY) + " ", a.gueltig, "");
                sicher(a, "das" + x + geteilt(a.kwLang, a.kwLang.length() - 4, SHY) + " ", a.gueltig, "");
            }
        }
        // das Beispiel der Regel: zwei Teilstuecke von "Institutionskennzeichen"
        sicher(IK_ART, "das" + ZWSP + "Institutions" + SHY + "kennzeichen ", IK_ART.gueltig, "");
    }

    @Test
    void r1_arztMitUnsichtbaremZeichenVorNr_istKontextwortFuerLanr_nichtFuerIk() {
        sicher(LANR_ART, "Arzt" + ZWSP + "Nr ", LANR_ART.gueltig, "");
        sicher(LANR_ART, "Arzt" + SHY + "Nummer ", LANR_ART.gueltig, "");
        // fuer IK sind "Arzt" und "Nr" zwei Teilstuecke ohne Kontextwort: ein Fuellwort, aber kein Kontext
        assertKein(IK_ART, "Arzt" + ZWSP + "Nr " + IK_ART.gueltig);
    }

    @Test
    void r1_vorwortAlsLetzteTeilstueckeEinesLaufs_nachsatzwortAlsErsteTeilstueckeDesNaechstenLaufs() {
        sicher(LANR_ART, "Praxis" + ZWSP + "Arzt-Nr ", LANR_ART.gueltig, "");
        sicher(LANR_ART, "Praxis" + ZWSP + "Arzt-Nr ", LANR_ART.gueltig, "");
        sicher(LANR_ART, "Arzt-" + ZWSP + "N" + SHY + "r ", LANR_ART.gueltig, "");
        sicher(LANR_ART, "Arzt" + ZWSP + "-" + ZWSP + "Nummer ", LANR_ART.gueltig, "");
        // hinter dem Nachsatzwort desselben Laufs ein weiteres Teilstueck: genau ein Fuellwort, Fund 0.80
        wahrscheinlich(LANR_ART, "Arzt-Nr" + ZWSP + "x ", LANR_ART.gueltig, "");
    }

    // ------------------------------------------------------------------ R-1: angeklebtes Nachsatzwort

    @Test
    void r1_angeklebtesNachsatzwort_alleBeispieleAusDemFund_direkterKontext() {
        // Arztnummer + U+200B + Nr, LANR + U+00AD + Nummer, Institutionskennzeichen + U+00AD + Nr., IKNR + U+200B + Nr
        sicher(LANR_ART, "LANR" + SHY + "Nummer ", LANR_ART.gueltig, "");
        sicher(LANR_ART, "Arztnummer" + ZWSP + "Nr ", LANR_ART.gueltig, "");
        sicher(IK_ART, "Institutionskennzeichen" + SHY + "Nr. ", IK_ART.gueltig, "");
        sicher(IK_ART, "IKNR" + ZWSP + "Nr ", IK_ART.gueltig, "");
    }

    @Test
    void r1_angeklebtesNachsatzwort_jedeArtMitJedemUnsichtbarenZeichen_direkterKontext() {
        for (char x : UNSICHTBAR) {
            for (Art a : ARTEN) {
                sicher(a, a.kw + x + "Nummer ", a.gueltig, "");
                sicher(a, a.kwLang + x + "Nr ", a.gueltig, "");
                sicher(a, a.kwLang + x + "Nr. ", a.gueltig, "");
                sicher(a, a.kw + "NR" + x + "Nr ", a.gueltig, "");
                sicher(a, a.kw + x + "Nr" + x + "Nummer ", a.gueltig, "");
            }
        }
    }

    @Test
    void r1_nachsatzwortAusMehrerenTeilstuecken_direkterKontext() {
        for (Art a : ARTEN) {
            sicher(a, a.kw + ZWSP + "N" + SHY + "r ", a.gueltig, "");
            sicher(a, a.kw + ZWSP + "Nu" + SHY + "mm" + ZWSP + "er ", a.gueltig, "");
            // Nachsatzwort im folgenden Lauf: auch dort aus mehreren Teilstuecken
            sicher(a, a.kw + " N" + SHY + "r ", a.gueltig, "");
        }
    }

    // ------------------------------------------------------------------ R-1: Teilstuecke hinter dem Kontextwort

    @Test
    void r1_kontextwortMitUnsichtbaremZeichenUndAnderemTeilstueck_fund080() {
        for (char x : UNSICHTBAR) {
            for (Art a : ARTEN) {
                wahrscheinlich(a, a.kw + x + "x ", a.gueltig, "");
                wahrscheinlich(a, a.kw + x + "Nrx ", a.gueltig, "");
                wahrscheinlich(a, a.kw + x + "Nr" + x + "x ", a.gueltig, "");
                wahrscheinlich(a, a.kw + x + "Nu" + x + "x ", a.gueltig, "");
            }
        }
    }

    @Test
    void r1_teilstueckeHinterDemKontextwortZaehlenZusammenAlsEinFuellwort() {
        for (Art a : ARTEN) {
            // sechs Teilstuecke in einem Lauf: ein Fuellwort, der Kontext bleibt
            String lauf = "a" + ZWSP + "b" + ZWSP + "c" + ZWSP + "d" + ZWSP + "e" + ZWSP + "f";
            wahrscheinlich(a, a.kw + " " + lauf + " ", a.gueltig, "");
            wahrscheinlich(a, a.kw + ZWSP + lauf + " ", a.gueltig, "");
            // drei Woerter plus ein Lauf aus vielen Teilstuecken: vier Fuellwoerter, der Kontext endet
            assertKein(a, a.kw + " das ist die " + lauf + " " + a.gueltig);
            // zwei Woerter plus ein Lauf: drei Fuellwoerter, noch erlaubt
            wahrscheinlich(a, a.kw + " das ist " + lauf + " ", a.gueltig, "");
        }
    }

    @Test
    void r1_laufOhneKontextwortZaehltAlsGenauEinFuellwort() {
        for (Art a : ARTEN) {
            String lauf = "Praxis" + ZWSP + "Beispiel" + SHY + "Muster" + ZWSP + "Frau";
            wahrscheinlich(a, a.kw + " " + lauf + " ", a.gueltig, "");
            wahrscheinlich(a, a.kw + " das ist " + lauf + " ", a.gueltig, "");
            assertKein(a, a.kw + " das ist die " + lauf + " " + a.gueltig);
        }
    }

    @Test
    void r1_teilstueckeVorDemKontextwortDesselbenLaufsLeerenOffeneSegmente() {
        for (Art a : ARTEN) {
            // das Wort vor dem Kontextwort steht im selben Lauf: wirkt wie ein Fuellwort, die offene Zahl faellt weg
            assertKein(a, a.gueltig + " Praxis" + ZWSP + a.kw + " ");
            // ohne Buchstaben im Lauf davor bleibt die offene Zahl erhalten (nur ein unsichtbares Zeichen)
            assertFolge(a, a.gueltig + " " + ZWSP + a.kw, new Erw(a.gueltig, WAHRSCHEINLICH));
            assertFolge(a, a.gueltig + " (" + SHY + a.kw + ")", new Erw(a.gueltig, WAHRSCHEINLICH));
        }
    }

    @Test
    void r1_langesTeilstueckVorDemKontextwort_fensterVerliertDenAnfangOhneFolgen() {
        for (Art a : ARTEN) {
            String lang = "x".repeat(60);
            sicher(a, lang + ZWSP + a.kw + " ", a.gueltig, "");
            sicher(a, lang + ZWSP + geteilt(a.kwLang, 5, SHY) + " ", a.gueltig, "");
        }
        // das laengste Kontextwort (23 Zeichen) liegt am Rand des Fensters von 24 Buchstaben
        sicher(IK_ART, "x".repeat(40) + ZWSP + "Institutionskennzeichen ", IK_ART.gueltig, "");
        sicher(IK_ART, "x".repeat(40) + ZWSP + "Institutions" + SHY + "kennzeichen ", IK_ART.gueltig, "");
        assertKein(IK_ART, "x".repeat(40) + ZWSP + "Institutionskennzeichenx " + IK_ART.gueltig);
    }

    // ------------------------------------------------------------------ R-1: Gegenproben ohne unsichtbares Zeichen

    @Test
    void r1_gegenproben_teilstueckgrenzenSindWortgrenzen_keinKontext() {
        for (Art a : ARTEN) {
            assertKein(a, "Praxis" + a.kw + " " + a.gueltig);
            assertKein(a, a.kw + "X " + a.gueltig);
            assertKein(a, "x" + a.kw + " " + a.gueltig);
            assertKein(a, a.kw + "x: " + a.gueltig);
            assertKein(a, "Praxis" + AKUT + a.kw + " " + a.gueltig);
            // eine Buchstabenfolge bildet ein Teilstueck: das Kontextwort steckt mitten in einem Teilstueck
            assertKein(a, "Prax" + ZWSP + "is" + a.kw + " " + a.gueltig);
            assertKein(a, a.kw + "is" + ZWSP + "x " + a.gueltig);
        }
        assertKein(IK_ART, "Technik " + IK_ART.gueltig);
        assertKein(IK_ART, "IKEA " + IK_ART.gueltig);
        assertKein(IK_ART, "Tech" + ZWSP + "nik " + IK_ART.gueltig);
    }

    // ------------------------------------------------------------------ R-2: mehrere Nummern vor dem Kontextwort

    @Test
    void r2_zweiNummernMitLeerraum_beideSindFund() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + " " + a.gueltig2 + " (" + a.kw + ")", new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
        }
    }

    @Test
    void r2_zweiNummernMitKomma_beideSindFund() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + ", " + a.gueltig2 + " (" + a.kw + ")", new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
        }
    }

    @Test
    void r2_zweiNummernMitUnd_beideSindFund_auchGrossgeschrieben() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + " und " + a.gueltig2 + " (" + a.kw + ")", new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
            assertFolge(a, a.gueltig + " UND " + a.gueltig2 + " [" + a.kw + "]", new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
            assertFolge(a, a.gueltig + ", und " + a.gueltig2 + " (" + a.kw + ")", new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
        }
    }

    @Test
    void r2_zweiNummernMitSchraegstrich_beideSindFund() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + "/" + a.gueltig2 + " (" + a.kw + ")", new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
            assertFolge(a, a.gueltig + " / " + a.gueltig2 + " (" + a.kw + ")", new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
        }
    }

    @Test
    void r2_zweiNummernMitSemikolonUndEckigerKlammer_beideSindFund() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + "; " + a.gueltig2 + " [" + a.kw + "]", new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
        }
    }

    @Test
    void r2_dreiNummern_alleSindFund_ohneKlammerUndGemischteTrenner() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + " " + a.gueltig2 + " " + a.gueltig3 + " (" + a.kw + ")",
                    new Erw(a.gueltig, WAHRSCHEINLICH), new Erw(a.gueltig2, WAHRSCHEINLICH),
                    new Erw(a.gueltig3, WAHRSCHEINLICH));
            assertFolge(a, a.gueltig + ", " + a.gueltig2 + " und " + a.gueltig3 + " " + a.kw,
                    new Erw(a.gueltig, WAHRSCHEINLICH), new Erw(a.gueltig2, WAHRSCHEINLICH),
                    new Erw(a.gueltig3, WAHRSCHEINLICH));
            assertFolge(a, a.gueltig + " ,;/ " + a.gueltig2 + "\t" + a.gueltig3 + " (" + a.kw + ")",
                    new Erw(a.gueltig, WAHRSCHEINLICH), new Erw(a.gueltig2, WAHRSCHEINLICH),
                    new Erw(a.gueltig3, WAHRSCHEINLICH));
        }
    }

    @Test
    void r2_nummernMitUngueltigerPruefzifferUndLaenge_stufeJeNummer() {
        for (Art a : ARTEN) {
            assertFolge(a, a.ungueltig + ", " + LAENGE_8 + " und " + a.gueltig + " (" + a.kw + ")",
                    new Erw(a.ungueltig, PZ_UNGUELTIG), new Erw(LAENGE_8, LAENGE), new Erw(a.gueltig, WAHRSCHEINLICH));
        }
    }

    @Test
    void r2_nummernMitTrennerform_ganzeFolgenSindDieFunde() {
        for (Art a : ARTEN) {
            String n1 = gruppiert(a.gueltig, " ");
            String n2 = gruppiert(a.gueltig2, "-");
            assertFolge(a, n1 + ", " + n2 + " (" + a.kw + ")", new Erw(n1, WAHRSCHEINLICH),
                    new Erw(n2, WAHRSCHEINLICH));
        }
    }

    @Test
    void r2_kontextwortGiltAuchFuerDieFolgendeZahl_undJedesSegmentWirdNurEinmalAusgegeben() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + " " + a.gueltig2 + " (" + a.kw + ") " + a.gueltig3,
                    new Erw(a.gueltig, WAHRSCHEINLICH), new Erw(a.gueltig2, WAHRSCHEINLICH),
                    new Erw(a.gueltig3, a.sicher));
            // ein zweites Kontextwort gibt nichts mehr aus
            assertFolge(a, a.gueltig + " " + a.gueltig2 + " (" + a.kw + ") (" + a.kw + ")",
                    new Erw(a.gueltig, WAHRSCHEINLICH), new Erw(a.gueltig2, WAHRSCHEINLICH));
        }
    }

    @Test
    void r2_gegenprobe_wortZwischenDenNummern_nurDieNummerHinterDerUnterbrechung() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + " dann " + a.gueltig2 + " (" + a.kw + ")", new Erw(a.gueltig2, WAHRSCHEINLICH));
            assertFolge(a, a.gueltig + " " + a.gueltig2 + " dann " + a.gueltig3 + " (" + a.kw + ")",
                    new Erw(a.gueltig3, WAHRSCHEINLICH));
        }
    }

    @Test
    void r2_gegenprobe_undUndOderUndMitKlammer_nurDieNummerHinterDerUnterbrechung() {
        for (Art a : ARTEN) {
            assertFolge(a, a.gueltig + " und und " + a.gueltig2 + " (" + a.kw + ")",
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
            assertFolge(a, a.gueltig + " (und " + a.gueltig2 + " (" + a.kw + ")", new Erw(a.gueltig2, WAHRSCHEINLICH));
            assertFolge(a, a.gueltig + " (" + a.gueltig2 + " (" + a.kw + ")", new Erw(a.gueltig2, WAHRSCHEINLICH));
            assertFolge(a, a.gueltig + " undx " + a.gueltig2 + " (" + a.kw + ")", new Erw(a.gueltig2, WAHRSCHEINLICH));
        }
    }

    @Test
    void r2_gegenprobe_zeilenumbruchZwischenDenNummern_nurDieNummerHinterDerUnterbrechung() {
        for (Art a : ARTEN) {
            for (String umbruch : new String[] {"\n", "\r\n", "\r"}) {
                assertFolge(a, a.gueltig + umbruch + a.gueltig2 + " (" + a.kw + ")",
                        new Erw(a.gueltig2, WAHRSCHEINLICH));
                assertFolge(a, a.gueltig + "," + umbruch + a.gueltig2 + " (" + a.kw + ")",
                        new Erw(a.gueltig2, WAHRSCHEINLICH));
            }
        }
    }

    @Test
    void r2_gegenprobe_doppelpunktUndAndereZeichenZwischenDenNummern_nurDieNummerHinterDerUnterbrechung() {
        for (Art a : ARTEN) {
            for (String zeichen : new String[] {": ", " - ", ". ", "! ", "? ", " = ", " # ", ") "}) {
                assertFolge(a, a.gueltig + zeichen + a.gueltig2 + " (" + a.kw + ")",
                        new Erw(a.gueltig2, WAHRSCHEINLICH));
            }
        }
    }

    @Test
    void bekannteGrenze12Rest_undOderSchraegstrichDirektVorDemKontextwort_keinFund() {
        // Grenze 12 ist fuer ein einzelnes Komma oder Semikolon geschlossen (kv-kontext-luecken D-5, siehe
        // KvKommaTest). Fuer "/" und "und" dort gilt unveraendert: Die ganze Folge wird verworfen.
        for (Art a : ARTEN) {
            assertKein(a, a.gueltig + " und " + a.kw);
            assertKein(a, a.gueltig + " / (" + a.kw + ")");
        }
    }

    @Test
    void r2_nachgestelltesKontextwortDerAnderenArtErschliesstNicht() {
        assertKein(IK_ART, LANR_ART.gueltig + " " + LANR_ART.gueltig2 + " (LANR)");
        assertKein(LANR_ART, IK_ART.gueltig + ", " + IK_ART.gueltig2 + " (IK)");
    }

    // ------------------------------------------------------------------ R-3: nummerierte Listen

    @Test
    void r3_nummerierteListe_beideNummernSindFund() {
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + ":\n1. " + a.gueltig + "\n2. " + a.gueltig2, new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
            assertFolge(a, a.kw + ":\n1. " + a.gueltig + "\n2. " + a.gueltig2 + "\n3. " + a.gueltig3,
                    new Erw(a.gueltig, WAHRSCHEINLICH), new Erw(a.gueltig2, WAHRSCHEINLICH),
                    new Erw(a.gueltig3, WAHRSCHEINLICH));
        }
    }

    @Test
    void r3_nummerierteListe_crLfEinrueckungUndZweistelligeNummern() {
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + ":\r\n1. " + a.gueltig + "\r\n2. " + a.gueltig2, new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
            assertFolge(a, a.kw + ":\n   1. " + a.gueltig + "\n\t2. " + a.gueltig2, new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
            assertFolge(a, a.kw + ":\n10. " + a.gueltig + "\n11. " + a.gueltig2, new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
            assertFolge(a, a.kw + ":\r1. " + a.gueltig + "\r2. " + a.gueltig2, new Erw(a.gueltig, WAHRSCHEINLICH),
                    new Erw(a.gueltig2, WAHRSCHEINLICH));
        }
    }

    @Test
    void r3_listenzeichenAmTextanfang_istNeutral_kontextwortDahinterBleibtDirekt() {
        for (Art a : ARTEN) {
            sicher(a, "1. " + a.kw + " ", a.gueltig, "");
            sicher(a, "  12. " + a.kw + ": ", a.gueltig, "");
        }
    }

    @Test
    void r3_listenzeichenZaehlenNichtAlsFuellwortUndBeendenKeinenSatz() {
        for (Art a : ARTEN) {
            // drei Fuellwoerter plus mehrere Listenzeichen: weiterhin im Kontext
            assertFolge(a, a.kw + " eins zwei drei\n1. " + a.gueltig + "\n2. " + a.gueltig2,
                    new Erw(a.gueltig, WAHRSCHEINLICH), new Erw(a.gueltig2, WAHRSCHEINLICH));
            // vier Fuellwoerter: Kontext beendet, Listenzeichen aendern nichts daran
            assertKein(a, a.kw + " eins zwei drei vier\n1. " + a.gueltig);
        }
    }

    @Test
    void r3_gegenprobe_dreistelligerBlock_istFuellwortUndSatzende_keinFund() {
        for (Art a : ARTEN) {
            assertKein(a, a.kw + ":\n100. " + a.gueltig);
            assertKein(a, a.kw + ":\n123. " + a.gueltig + "\n124. " + a.gueltig2);
        }
    }

    @Test
    void r3_gegenprobe_blockNichtAmZeilenanfang_istFuellwortUndSatzende_keinFund() {
        for (Art a : ARTEN) {
            assertKein(a, a.kw + ": Nr 1. " + a.gueltig);
            assertKein(a, a.kw + ": x 1. " + a.gueltig);
            assertKein(a, a.kw + ":\nx 1. " + a.gueltig);
        }
    }

    @Test
    void r3_gegenprobe_leerzeileDazwischen_kontextEndet() {
        for (Art a : ARTEN) {
            assertKein(a, a.kw + ":\n\n1. " + a.gueltig);
            assertKein(a, a.kw + ":\r\n\r\n1. " + a.gueltig);
            assertFolge(a, a.kw + ":\n1. " + a.gueltig + "\n\n2. " + a.gueltig2, new Erw(a.gueltig, WAHRSCHEINLICH));
        }
    }

    @Test
    void r3_punktAlsGruppentrennerEinerKette_istKeinListenzeichen() {
        // "1." unmittelbar vor einer Ziffer ist Teil einer Kette: zehn Ziffern, Laengenabweichung
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + ":\n1." + a.gueltig, new Erw("1." + a.gueltig, LAENGE));
        }
    }

    // ------------------------------------------------------------------ R-4: Abkuerzungspunkt

    @Test
    void r4_abkuerzungenInKlammernZwischenNummern_beideNummernSindFund() {
        for (Art a : ARTEN) {
            assertFolge(a, a.kw + ": " + a.gueltig + " (Dr. A), " + a.gueltig2 + " (Dr. B)",
                    new Erw(a.gueltig, a.sicher), new Erw(a.gueltig2, WAHRSCHEINLICH));
        }
    }

    @Test
    void r4_abkuerzungenVorDerNummer_fund080() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + " e.V. ", a.gueltig, "");
            wahrscheinlich(a, a.kw + " z.B. ", a.gueltig, "");
            wahrscheinlich(a, a.kw + " Prof. ", a.gueltig, "");
            wahrscheinlich(a, a.kw + " Dr. ", a.gueltig, "");
            wahrscheinlich(a, a.kw + ": Dr.med. ", a.gueltig, "");
        }
    }

    @Test
    void r4_gegenprobe_wortMitFuenfBuchstabenBeendetDenSatz() {
        for (Art a : ARTEN) {
            assertKein(a, a.kw + " Hallo. " + a.gueltig);
            assertKein(a, a.kw + " Herrn. " + a.gueltig);
            assertKein(a, a.kw + " Prof.s. Hallo. " + a.gueltig);
        }
    }

    @Test
    void r4_gegenprobe_dieGrenzeVonDreiFuellwoerternGiltUnveraendert() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + " Dr. A. B. ", a.gueltig, "");
            assertKein(a, a.kw + " Dr. A. B. C. " + a.gueltig);
            assertKein(a, a.kw + " e.V. z.B. " + a.gueltig);
        }
    }

    @Test
    void r4_gegenprobe_punktNachZahlOderMitLeerzeichenDavorBleibtSatzende() {
        for (Art a : ARTEN) {
            assertKein(a, a.kw + " Dr . " + a.gueltig);
            assertKein(a, a.kw + " 12. " + a.gueltig);
        }
    }

    // ------------------------------------------------------------------ bekannte Grenzen 10 und 11

    @Test
    void bekannteGrenze10_unsichtbareKombinationszeichenUndBuchstabenImKontextwort_keinKontext() {
        // Unicode fuehrt sie als Kombinationszeichen (U+034F, U+180B-U+180D, U+E0100) bzw. Buchstaben (U+3164) oder
        // Symbol (U+2800); sie werden nicht uebergangen (anders als Formatzeichen, vgl. R-1)
        int[] zeichen = {0x34F, 0x180B, 0x180C, 0x180D, 0xE0100, 0xE01EF, 0x3164, 0x2800};
        for (int cp : zeichen) {
            String x = zeichen(cp);
            for (Art a : ARTEN) {
                assertKein(a, mitZeichen(a.kw, 1, x) + " " + a.gueltig);
                assertKein(a, mitZeichen(a.kwLang, 3, x) + ": " + a.gueltig);
            }
        }
    }

    @Test
    void bekannteGrenze10_kombinationszeichenUndBuchstabeAmKontextwort_keinKontext() {
        int[] zeichen = {0x34F, 0x180B, 0x180C, 0x180D, 0xE0100, 0xE01EF, 0x3164};
        for (int cp : zeichen) {
            String x = zeichen(cp);
            for (Art a : ARTEN) {
                assertKein(a, a.kw + x + " " + a.gueltig);
                assertKein(a, x + a.kw + ": " + a.gueltig);
                assertKein(a, "Praxis" + x + a.kw + " " + a.gueltig);
            }
        }
    }

    @Test
    void bekannteGrenze10_braille_leerzeichenAmKontextwort_istSonstigesZeichenKontextBleibt() {
        // U+2800 ist ein Symbol, kein Buchstabe: am Rand des Kontextworts trennt es nur (im Wort: kein Kontext)
        for (Art a : ARTEN) {
            sicher(a, a.kw + zeichen(0x2800) + " ", a.gueltig, "");
            sicher(a, zeichen(0x2800) + a.kw + " ", a.gueltig, "");
        }
    }

    @Test
    void bekannteGrenze11_satzMitKurzemWortAmEnde_beendetDenKontextNicht() {
        for (Art a : ARTEN) {
            wahrscheinlich(a, a.kw + " liegt vor. ", a.gueltig, "");
            wahrscheinlich(a, a.kw + " ist ok. Ja. ", a.gueltig, "");
            // die Grenze von drei Fuellwoertern begrenzt die Reichweite
            assertKein(a, a.kw + " liegt vor. Wir bitten. " + a.gueltig);
            assertKein(a, a.kw + " a. b. c. d. " + a.gueltig);
        }
    }

    // ------------------------------------------------------------------ Laufzeit der neuen Regeln (AK-21, OF-8)

    @Test
    void laufzeit_r1_einLaufAusSehrVielenKurzenTeilstuecken_einFund() {
        // Formel: Kontextwort, ein Lauf aus ~500 000 Teilstuecken "x"+U+200B (ein Fuellwort), dann die Zahl: 1 Fund
        for (Art a : ARTEN) {
            laufzeit(a, a.kw + " ", "x" + ZWSP, " " + a.gueltig, 1, "1 Fund");
        }
    }

    @Test
    void laufzeit_r1_einLaufMitSehrVielenUnsichtbarenZeichenZwischenZweiTeilstuecken_einFund() {
        // Formel: "L" + eine Million U+200B + "ANR " + Zahl: die beiden Teilstuecke ergeben das Kontextwort, 1 Fund
        for (Art a : ARTEN) {
            laufzeit(a, a.kw.substring(0, 1), String.valueOf(ZWSP), a.kw.substring(1) + " " + a.gueltig, 1, "1 Fund");
        }
    }

    @Test
    void laufzeit_r1_vieleTeilstueckeDieFastEinKontextwortErgeben_keinFund() {
        // Formel: Teilstuecke "<KW ohne letzten Buchstaben>" bzw. "<KW>x" zu hunderttausenden: nie ein Kontextwort,
        // keine Funde (die Zahl am Ende wird nur offen, kein Kontextwort folgt)
        for (Art a : ARTEN) {
            String fast = a.kw.substring(0, a.kw.length() - 1);
            laufzeit(a, "", fast + ZWSP, " " + a.gueltig, 0, "0 Funde");
            laufzeit(a, "", a.kw + "x" + ZWSP, " " + a.gueltig, 0, "0 Funde");
            laufzeit(a, "", a.kw.substring(0, 1) + ZWSP, " " + a.gueltig, 0, "0 Funde");
        }
    }

    @Test
    void laufzeit_r1_kontextwortMitSehrVielenAngeklebtenNachsatzwoertern_einFund() {
        // Formel: Kontextwort + eine Folge "U+200B Nr" (je ein Nachsatzwort, direkt bleibt) + Zahl: 1 Fund (sicher)
        for (Art a : ARTEN) {
            laufzeit(a, a.kw, ZWSP + "Nr", " " + a.gueltig, 1, "1 Fund");
        }
    }

    @Test
    void laufzeit_r1_vieleKontextwoerterMitUnsichtbarenZeichenHintereinander_einFundJeZahl() {
        // Baustein "<KW>U+200B<Zahl> ": je Wiederholung ein Kontextwort (Zahl direkt dahinter). Formel: r Funde.
        for (Art a : ARTEN) {
            String baustein = a.kw + ZWSP + a.gueltig + " ";
            int r = ceilMillion(baustein);
            laufzeit(a, "", baustein, "", r, r + " Funde");
        }
    }

    @Test
    void laufzeit_r2_sehrVieleNummernMitKommaOhneKontextwort_keinFund() {
        // Formel: "<Zahl>, " zu zehntausenden ohne Kontextwort: die Folge der offenen Segmente waechst, wird nie
        // ausgegeben: 0 Funde
        for (Art a : ARTEN) {
            laufzeit(a, "", a.gueltig + ", ", a.gueltig, 0, "0 Funde");
        }
    }

    @Test
    void laufzeit_r2_sehrVieleNummernMitKommaUndKontextwortAmEnde_alleSindFund() {
        // Formel: r Bausteine "<Zahl>, " plus Schluss "<Zahl> (<KW>)": r + 1 Funde (alle 0.80)
        for (Art a : ARTEN) {
            String baustein = a.gueltig + ", ";
            String schluss = a.gueltig + " (" + a.kw + ")";
            int r = wiederholungen(1_000_000, "", baustein, schluss);
            laufzeit(a, "", baustein, schluss, r + 1, (r + 1) + " Funde");
        }
    }

    @Test
    void laufzeit_r2_sehrVieleNummernMitUndUndLeerraum_alleSindFund() {
        // Baustein "<Zahl> und ": nach jedem "und" folgt eine Zahl, die Luecke ist sauber; Schluss "<Zahl> (<KW>)"
        for (Art a : ARTEN) {
            String baustein = a.gueltig + " und ";
            String schluss = a.gueltig + " (" + a.kw + ")";
            int r = wiederholungen(1_000_000, "", baustein, schluss);
            laufzeit(a, "", baustein, schluss, r + 1, (r + 1) + " Funde");
        }
    }

    @Test
    void laufzeit_r3_sehrVieleNummerierteZeilen_einFundJeZeile() {
        // Baustein "1. <Zahl>\n" hinter "<KW>:\n": je Zeile ein Fund (0.80). Formel: r Funde.
        for (Art a : ARTEN) {
            String baustein = "1. " + a.gueltig + "\n";
            int r = wiederholungen(1_000_000, a.kw + ":\n", baustein, "");
            laufzeit(a, a.kw + ":\n", baustein, "", r, r + " Funde");
        }
    }

    @Test
    void laufzeit_r4_sehrVieleAbkuerzungspunkte_keinFundUndLinear() {
        // Baustein "e.V. " zu hunderttausenden: jede Abkuerzung ist ein Fuellwort, nach dem vierten endet der Kontext;
        // keine Ziffer: 0 Funde
        for (Art a : ARTEN) {
            laufzeit(a, a.kw + " ", "e.V. ", "", 0, "0 Funde");
        }
    }
}
