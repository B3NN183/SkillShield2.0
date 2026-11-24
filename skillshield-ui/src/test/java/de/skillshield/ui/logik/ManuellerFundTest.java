package de.skillshield.ui.logik;

import static de.skillshield.ui.logik.Attrappen.scannen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.Reidentifier;
import de.skillshield.ui.logik.Attrappen.TestDialoge;
import de.skillshield.ui.logik.Attrappen.TestZwischenablage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Manuelles Hinzufuegen (D-7 bis D-9; AK-22 bis AK-28), reine Funktion und ueber die Sitzung. */
class ManuellerFundTest {

    private static final String EMOJI = new String(Character.toChars(0x1F600));
    private static final String NBSP = String.valueOf((char) 0xA0);
    private static final String GEVIERT = String.valueOf((char) 0x2003);

    private final TestDialoge dialoge = new TestDialoge();

    private static Finding automatisch(FindingType typ, String text, String teil) {
        int start = text.indexOf(teil);
        assertTrue(start >= 0, "Vorbedingung: Teiltext vorhanden");
        return new Finding(typ, teil, start, start + teil.length(), 0.9, "email");
    }

    private static List<FundZeile> zeilen(boolean angewaehlt, Finding... funde) {
        List<FundZeile> liste = new ArrayList<>();
        for (Finding fund : funde) {
            liste.add(new FundZeile(fund, angewaehlt, null));
        }
        return liste;
    }

    private static ManuellerFund.Ergebnis hinzu(String text, List<FundZeile> bestand, String markiert) {
        int start = text.indexOf(markiert);
        assertTrue(start >= 0, "Vorbedingung: Markierung im Text");
        return ManuellerFund.hinzufuegen(text, bestand, start, start + markiert.length());
    }

    /** Sitzung mit festem Scan-Ergebnis (Funde), bereits gescannt. */
    private Sitzung gescannt(String text, Finding... funde) {
        List<Finding> fest = List.of(funde);
        Sitzung sitzung = new Sitzung(t -> fest, new TestZwischenablage(), dialoge);
        scannen(sitzung, text);
        return sitzung;
    }

    private static void pruefeGueltigUndRoundtrip(String text, List<FundZeile> zeilen) {
        int ende = 0;
        List<Finding> funde = new ArrayList<>();
        for (FundZeile zeile : zeilen) {
            Finding fund = zeile.fund();
            assertTrue(fund.start() >= ende, "sortiert und ueberlappungsfrei");
            assertEquals(text.substring(fund.start(), fund.end()), fund.originalValue());
            ende = fund.end();
            funde.add(fund);
        }
        PseudonymizationResult ergebnis = new Pseudonymizer().pseudonymize(text, funde);
        assertEquals(text, new Reidentifier().restore(ergebnis.text(), ergebnis.mapping()).text());
    }

    // ---------------------------------------------------------------- AK-22: Eigenschaften des Fundes

    @Test
    void einMarkierterNameWirdEinManuellerFundDesTypsTextMitKonfidenzEins() {
        String text = "Max Mustermann ruft an.";

        ManuellerFund.Ergebnis ergebnis = hinzu(text, List.of(), "Max Mustermann");

        assertFalse(ergebnis.abgelehnt());
        assertEquals(1, ergebnis.hinzugefuegt());
        assertEquals(0, ergebnis.bereitsGeschuetzt());
        assertEquals(0, ergebnis.ersetzt());
        assertEquals(1, ergebnis.zeilen().size());
        FundZeile zeile = ergebnis.zeilen().get(0);
        Finding fund = zeile.fund();
        assertEquals(FindingType.TEXT, fund.type());
        assertEquals("Max Mustermann", fund.originalValue());
        assertEquals(0, fund.start());
        assertEquals(14, fund.end());
        assertEquals(1.0, fund.confidence());
        assertEquals(ConfidenceLevel.SICHER, fund.level());
        assertEquals(Finding.SOURCE_MANUAL, fund.source());
        assertEquals("Manuell", Beschriftungen.typ(fund));
        assertTrue(zeile.angewaehlt());
        assertNull(zeile.platzhalter(), "die Platzhalter berechnet erst die Sitzung");
    }

    @Test
    void ueberSitzungErscheintDerFundAngewaehltAnDerRichtigenStelleDerTabelle() {
        String text = "Max Mustermann schreibt an mustermann@example.org.";
        Sitzung sitzung = Sitzung.standard(new TestZwischenablage(), dialoge);
        scannen(sitzung, text);
        assertEquals(1, sitzung.zeilen().size());

        sitzung.markierungHinzufuegen(0, 14);

        List<FundZeile> zeilen = sitzung.zeilen();
        assertEquals(2, zeilen.size());
        assertEquals(FindingType.TEXT, zeilen.get(0).fund().type());
        assertEquals("Manuell", Beschriftungen.typ(zeilen.get(0).fund()));
        assertEquals(FindingType.EMAIL, zeilen.get(1).fund().type());
        assertTrue(zeilen.get(0).angewaehlt());
        assertTrue(zeilen.get(1).angewaehlt());
        List<Finding> funde = zeilen.stream().map(FundZeile::fund).toList();
        PseudonymizationResult kern = new Pseudonymizer().pseudonymize(text, funde);
        assertEquals(kern.text(), sitzung.textFuerKi());
        assertFalse(sitzung.textFuerKi().contains("Mustermann"));
        assertEquals(zeilen.get(0).platzhalter(), kern.text().substring(0, zeilen.get(0).platzhalter().length()));
        assertEquals("Zuordnung: 2 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
    }

    // ---------------------------------------------------------------- AK-23: alle Vorkommen

    @Test
    void jedesVorkommenWirdEinFundMitDemselbenPlatzhalterUndDieMeldungNenntDieZahl() {
        String text = "Beispielname schreibt an Beispielname";
        Sitzung sitzung = gescannt(text);

        sitzung.markierungHinzufuegen(0, "Beispielname".length());

        List<FundZeile> zeilen = sitzung.zeilen();
        assertEquals(2, zeilen.size());
        assertEquals(0, zeilen.get(0).fund().start());
        assertEquals(text.lastIndexOf("Beispielname"), zeilen.get(1).fund().start());
        assertEquals("[[TEXT_1]]", zeilen.get(0).platzhalter());
        assertEquals("[[TEXT_1]]", zeilen.get(1).platzhalter());
        assertEquals("[[TEXT_1]] schreibt an [[TEXT_1]]", sitzung.textFuerKi());
        // Hinzufüge-Meldung und aktuelle Zählung (AK-20) in derselben Statuszeile
        assertEquals("2 Stellen als Fund hinzugefügt. 2 Funde, 2 angewählt, 0 fraglich.",
                sitzung.statusleiste().meldung());
    }

    @Test
    void vorkommenWerdenZeichengenauOhneWortgrenzenUndNichtUeberlappendGesucht() {
        String text = "Meyer Meyerhof meyer aaaa";

        ManuellerFund.Ergebnis meyer = hinzu(text, List.of(), "Meyer");
        assertEquals(List.of(0, 6), meyer.zeilen().stream().map(z -> z.fund().start()).toList());

        ManuellerFund.Ergebnis aa = ManuellerFund.hinzufuegen(text, List.of(), text.indexOf("aaaa"),
                text.indexOf("aaaa") + 2);
        // "aa" in "aaaa": zwei nicht ueberlappende Treffer (der erste ist die Markierung selbst)
        assertEquals(2, aa.hinzugefuegt());
        assertEquals(0, aa.bereitsGeschuetzt());
    }

    @Test
    void dieMarkierungMitteImTextFindetAuchVorkommenDavor() {
        String text = "Beispielname und Beispielname und Beispielname";
        int zweite = text.indexOf("Beispielname", 1);

        ManuellerFund.Ergebnis ergebnis = ManuellerFund.hinzufuegen(text, List.of(), zweite, zweite + 12);

        assertEquals(3, ergebnis.hinzugefuegt());
        assertEquals(List.of(0, zweite, text.lastIndexOf("Beispielname")),
                ergebnis.zeilen().stream().map(z -> z.fund().start()).toList());
    }

    // ---------------------------------------------------------------- AK-24: Ueberschneidung der Markierung

    @Test
    void schneidetDieMarkierungEinenAutomatischenFundEntstehtEinFundUeberDieVereinigung() {
        String text = "Mail an mustermann@example.org heute.";
        Finding mail = automatisch(FindingType.EMAIL, text, "mustermann@example.org");
        String markiert = "an mustermann@exam";

        ManuellerFund.Ergebnis ergebnis = hinzu(text, zeilen(true, mail), markiert);

        assertEquals(1, ergebnis.zeilen().size());
        Finding fund = ergebnis.zeilen().get(0).fund();
        assertEquals(FindingType.TEXT, fund.type());
        assertEquals(text.indexOf("an mustermann"), fund.start());
        assertEquals(mail.end(), fund.end());
        assertEquals("an mustermann@example.org", fund.originalValue());
        assertEquals(1, ergebnis.hinzugefuegt());
        assertEquals(1, ergebnis.ersetzt());
        pruefeGueltigUndRoundtrip(text, ergebnis.zeilen());
    }

    @Test
    void schneidetDieMarkierungZweiAutomatischeFundeEntstehtGenauEinFundUeberAlles() {
        String text = "a@example.org und b@example.net.";
        Finding eins = automatisch(FindingType.EMAIL, text, "a@example.org");
        Finding zwei = automatisch(FindingType.EMAIL, text, "b@example.net");
        String markiert = "org und b@example";

        ManuellerFund.Ergebnis ergebnis = hinzu(text, zeilen(true, eins, zwei), markiert);

        assertEquals(1, ergebnis.zeilen().size());
        Finding fund = ergebnis.zeilen().get(0).fund();
        assertEquals(eins.start(), fund.start());
        assertEquals(zwei.end(), fund.end());
        assertEquals("a@example.org und b@example.net", fund.originalValue());
        assertEquals(2, ergebnis.ersetzt());
        pruefeGueltigUndRoundtrip(text, ergebnis.zeilen());
    }

    @Test
    void eineBenachbarteStelleOhneUeberschneidungBleibtUnberuehrt() {
        String text = "Name:Meyer@example.org";
        Finding mail = automatisch(FindingType.EMAIL, text, "Meyer@example.org");

        ManuellerFund.Ergebnis ergebnis = hinzu(text, zeilen(true, mail), "Name:");

        assertEquals(2, ergebnis.zeilen().size());
        assertEquals(0, ergebnis.ersetzt());
        assertSame(mail, ergebnis.zeilen().get(1).fund());
    }

    @Test
    void auchEinAbgewaehlterFundWirdVonDerErweiterungAufgenommen() {
        String text = "Mail mustermann@example.org Ende";
        Finding mail = automatisch(FindingType.EMAIL, text, "mustermann@example.org");

        ManuellerFund.Ergebnis ergebnis = hinzu(text, zeilen(false, mail), "org Ende");

        assertEquals(1, ergebnis.zeilen().size());
        assertTrue(ergebnis.zeilen().get(0).angewaehlt(), "der neue Fund ist angewaehlt (D-9 e)");
        assertEquals("mustermann@example.org Ende", ergebnis.zeilen().get(0).fund().originalValue());
    }

    // ---------------------------------------------------------------- AK-25: weitere Vorkommen ueberspringen

    @Test
    void einVorkommenGanzInEinemAngewaehltenFundIstBereitsGeschuetztUndDerFundBleibtUnveraendert() {
        String text = "Beispielname und Beispielname@example.org";
        Finding mail = automatisch(FindingType.EMAIL, text, "Beispielname@example.org");

        ManuellerFund.Ergebnis name = ManuellerFund.hinzufuegen(text, zeilen(true, mail), 0, 12);

        assertEquals(1, name.hinzugefuegt());
        assertEquals(1, name.bereitsGeschuetzt());
        assertEquals(0, name.ersetzt());
        assertEquals(2, name.zeilen().size());
        assertEquals(FindingType.TEXT, name.zeilen().get(0).fund().type());
        assertSame(mail, name.zeilen().get(1).fund());
        assertTrue(name.zeilen().get(1).angewaehlt());
        pruefeGueltigUndRoundtrip(text, name.zeilen());
    }

    @Test
    void dieMeldungNenntDieBereitsGeschuetztenStellenUndDieSitzungSchuetztDenRest() {
        String text = "Beispielname und Beispielname@example.org";
        Finding mail = automatisch(FindingType.EMAIL, text, "Beispielname@example.org");
        Sitzung sitzung = gescannt(text, mail);

        sitzung.markierungHinzufuegen(0, 12);

        assertEquals("1 Stelle als Fund hinzugefügt. 1 Stelle war bereits geschützt. "
                + "2 Funde, 2 angewählt, 0 fraglich.", sitzung.statusleiste().meldung());
        assertEquals(2, sitzung.zeilen().size());
        assertEquals("[[TEXT_1]] und [[EMAIL_2]]", sitzung.textFuerKi());
    }

    @Test
    void dieSucheNutztDieMarkierteZeichenfolgeUndNichtDenErweitertenFund() {
        String doppelt = "xAB AB";
        ManuellerFund.Ergebnis zwei = ManuellerFund.hinzufuegen(doppelt, zeilen(true,
                automatisch(FindingType.EMAIL, doppelt, "xA")), 1, 3);
        // Markierung bei 1: Erweiterung auf [0,3); das Vorkommen bei 4 ist frei und wird hinzugefuegt.
        assertEquals(2, zwei.hinzugefuegt());
        assertEquals(0, zwei.bereitsGeschuetzt());
        assertEquals("xAB", zwei.zeilen().get(0).fund().originalValue());
        assertEquals("AB", zwei.zeilen().get(1).fund().originalValue());
        pruefeGueltigUndRoundtrip(doppelt, zwei.zeilen());
    }

    @Test
    void erweitertDieMarkierungDenFundSindVorkommenInDerErweiterungBereitsGeschuetzt() {
        String text = "AAB und A";
        Finding bestand = new Finding(FindingType.EMAIL, "AA", 0, 2, 0.9, "email");

        // Markierung "A" bei 1 schneidet den Fund [0,2): neuer Fund [0,2) ("AA"); weiteres Vorkommen "A" bei 8.
        ManuellerFund.Ergebnis ergebnis = ManuellerFund.hinzufuegen(text, zeilen(true, bestand), 1, 2);

        assertEquals(1, ergebnis.ersetzt());
        // "A" bei 0 liegt im neuen Fund (bereits geschuetzt), bei 1 ist die Markierung, bei 8 ein freies Vorkommen.
        assertEquals(2, ergebnis.hinzugefuegt());
        assertEquals(1, ergebnis.bereitsGeschuetzt());
        pruefeGueltigUndRoundtrip(text, ergebnis.zeilen());
    }

    // ---------------------------------------------------------------- Nachtrag zu T-3: abgewaehlte Funde

    private static void pruefeKeinVorkommenImKlartext(String text, List<FundZeile> zeilen, String markiert) {
        boolean[] gedeckt = new boolean[text.length()];
        for (FundZeile zeile : zeilen) {
            if (zeile.angewaehlt()) {
                Arrays.fill(gedeckt, zeile.fund().start(), zeile.fund().end(), true);
            }
        }
        int treffer = text.indexOf(markiert);
        while (treffer >= 0) {
            for (int p = treffer; p < treffer + markiert.length(); p++) {
                assertTrue(gedeckt[p], "Zeichen " + p + " ungeschuetzt");
            }
            treffer = text.indexOf(markiert, treffer + markiert.length());
        }
    }

    @Test
    void einVorkommenInEinemAbgewaehltenAutomatischenFundWirdGeschuetztUndNichtUebersprungen() {
        String text = "Herr Muster schreibt von Muster@example.org.";
        Finding mail = automatisch(FindingType.EMAIL, text, "Muster@example.org");
        Sitzung sitzung = gescannt(text, mail);
        sitzung.anwaehlen(0, false);
        assertTrue(sitzung.textFuerKi().contains("Muster@example.org"));

        sitzung.markierungHinzufuegen(5, 11);

        assertFalse(sitzung.textFuerKi().contains("Muster"), "kein Muster mehr im Klartext");
        assertEquals("Herr [[TEXT_1]] schreibt von [[TEXT_2]].", sitzung.textFuerKi());
        List<FundZeile> zeilen = sitzung.zeilen();
        assertEquals(2, zeilen.size());
        assertTrue(zeilen.stream().allMatch(FundZeile::angewaehlt));
        assertEquals(FindingType.TEXT, zeilen.get(1).fund().type());
        assertEquals("Muster@example.org", zeilen.get(1).fund().originalValue());
        assertEquals("2 Stellen als Fund hinzugefügt. 1 bestehender Fund wurde dabei ersetzt. "
                + "2 Funde, 2 angewählt, 0 fraglich.", sitzung.statusleiste().meldung());
        pruefeGueltigUndRoundtrip(text, zeilen);
        assertEquals(text, new Reidentifier().restore(sitzung.textFuerKi(),
                new Pseudonymizer().pseudonymize(text, zeilen.stream().map(FundZeile::fund).toList()).mapping())
                .text());
    }

    @Test
    void einVorkommenInEinemAbgewaehltenManuellenFundWirdBeimErneutenHinzufuegenGeschuetzt() {
        String text = "Muster und Mustermann sowie Muster.";
        Sitzung sitzung = gescannt(text);
        sitzung.markierungHinzufuegen(text.indexOf("Mustermann"), text.indexOf("Mustermann") + 10);
        sitzung.anwaehlen(0, false);
        assertEquals(text, sitzung.textFuerKi(), "abgewaehlt: der Text bleibt im Klartext");

        sitzung.markierungHinzufuegen(0, 6);

        assertFalse(sitzung.textFuerKi().contains("Muster"));
        assertEquals("[[TEXT_1]] und [[TEXT_2]] sowie [[TEXT_1]].", sitzung.textFuerKi());
        assertEquals(3, sitzung.zeilen().size());
        assertTrue(sitzung.zeilen().stream().allMatch(FundZeile::angewaehlt));
    }

    @Test
    void einVorkommenGanzInEinemAngewaehltenFundBleibtUnveraendertMitDemTypDesFundes() {
        String text = "Muster schreibt an Muster@example.org";
        Finding mail = automatisch(FindingType.EMAIL, text, "Muster@example.org");

        ManuellerFund.Ergebnis ergebnis = ManuellerFund.hinzufuegen(text, zeilen(true, mail), 0, 6);

        assertEquals(1, ergebnis.hinzugefuegt());
        assertEquals(1, ergebnis.bereitsGeschuetzt());
        assertEquals(0, ergebnis.ersetzt());
        assertSame(mail, ergebnis.zeilen().get(1).fund());
        assertEquals(FindingType.EMAIL, ergebnis.zeilen().get(1).fund().type());
        pruefeKeinVorkommenImKlartext(text, ergebnis.zeilen(), "Muster");
        pruefeGueltigUndRoundtrip(text, ergebnis.zeilen());
    }

    @Test
    void dieMarkierteStelleUndEinWeiteresVorkommenSchneidenDenselbenFundUndWerdenEins() {
        String text = "ab Muster cd Mustermann";
        Finding teil = new Finding(FindingType.IP, "ster cd Mus", text.indexOf("ster cd"),
                text.indexOf("ster cd") + 11, 0.9, "ip");
        // Markierung [3,9) "Muster" schneidet den Fund [6,17); das Vorkommen bei 13 ragt aus dem neuen Fund [3,17).
        ManuellerFund.Ergebnis ergebnis = ManuellerFund.hinzufuegen(text, zeilen(true, teil), 3, 9);

        assertEquals(1, ergebnis.zeilen().size(), "alles beruehrt sich: ein Fund");
        Finding fund = ergebnis.zeilen().get(0).fund();
        assertEquals(FindingType.TEXT, fund.type());
        assertEquals("Muster cd Muster", fund.originalValue());
        assertEquals(2, ergebnis.hinzugefuegt());
        assertEquals(1, ergebnis.ersetzt());
        pruefeKeinVorkommenImKlartext(text, ergebnis.zeilen(), "Muster");
        pruefeGueltigUndRoundtrip(text, ergebnis.zeilen());
    }

    @Test
    void einVorkommenDasAusEinemAngewaehltenFundHerausragtWirdMitIhmVereinigt() {
        String text = "Muster aa Mustermann";
        Finding lang = automatisch(FindingType.EMAIL, text, "termann");

        ManuellerFund.Ergebnis ergebnis = ManuellerFund.hinzufuegen(text, zeilen(true, lang), 0, 6);

        assertEquals(2, ergebnis.zeilen().size());
        assertEquals(2, ergebnis.hinzugefuegt());
        assertEquals(1, ergebnis.ersetzt());
        assertEquals(FindingType.TEXT, ergebnis.zeilen().get(1).fund().type());
        assertEquals("Mustermann", ergebnis.zeilen().get(1).fund().originalValue());
        pruefeKeinVorkommenImKlartext(text, ergebnis.zeilen(), "Muster");
        pruefeGueltigUndRoundtrip(text, ergebnis.zeilen());
    }

    @Test
    void zweiVorkommenImSelbenAbgewaehltenFundErgebenEinenFund() {
        String text = "Muster x Muster-Muster y";
        Finding block = automatisch(FindingType.EMAIL, text, "Muster-Muster");
        List<FundZeile> bestand = zeilen(false, block);

        ManuellerFund.Ergebnis ergebnis = ManuellerFund.hinzufuegen(text, bestand, 0, 6);

        // Das erste Vorkommen im Fund erweitert ihn zu einem neuen Fund, das zweite liegt dann schon darin.
        assertEquals(2, ergebnis.zeilen().size());
        assertEquals("Muster-Muster", ergebnis.zeilen().get(1).fund().originalValue());
        assertTrue(ergebnis.zeilen().get(1).angewaehlt());
        assertEquals(FindingType.TEXT, ergebnis.zeilen().get(1).fund().type());
        assertEquals(2, ergebnis.hinzugefuegt());
        assertEquals(1, ergebnis.bereitsGeschuetzt());
        assertEquals(1, ergebnis.ersetzt());
        pruefeKeinVorkommenImKlartext(text, ergebnis.zeilen(), "Muster");
        pruefeGueltigUndRoundtrip(text, ergebnis.zeilen());
    }

    @Test
    void dasSichSelbstUeberlappendeMusterErgibtEinenFundUeberAlles() {
        String text = "xaaaay";
        ManuellerFund.Ergebnis ergebnis = ManuellerFund.hinzufuegen(text, List.of(), 2, 4);

        assertEquals(1, ergebnis.zeilen().size());
        assertEquals("aaaa", ergebnis.zeilen().get(0).fund().originalValue());
        assertEquals(1, ergebnis.zeilen().get(0).fund().start());
        assertEquals(0, ergebnis.bereitsGeschuetzt());
        pruefeGueltigUndRoundtrip(text, ergebnis.zeilen());
    }

    @Test
    void einBeruehrenderNeuerFundAusEinemVorkommenWirdVerschmolzenEinFreierNachbarNicht() {
        String beruehrt = "MuMuX";
        Finding rest = automatisch(FindingType.EMAIL, beruehrt, "MuX");
        // Markierung [0,2); das Vorkommen [2,4) schneidet den abgewaehlten Fund [2,5) und beruehrt die Markierung.
        ManuellerFund.Ergebnis eins = ManuellerFund.hinzufuegen(beruehrt, zeilen(false, rest), 0, 2);
        assertEquals(1, eins.zeilen().size());
        assertEquals("MuMuX", eins.zeilen().get(0).fund().originalValue());
        assertEquals(2, eins.hinzugefuegt());
        assertEquals(1, eins.ersetzt());
        pruefeGueltigUndRoundtrip(beruehrt, eins.zeilen());

        String frei = "MuMu";
        ManuellerFund.Ergebnis zwei = ManuellerFund.hinzufuegen(frei, List.of(), 0, 2);
        assertEquals(2, zwei.zeilen().size(), "ein freies Vorkommen wird nicht mit der Markierung verschmolzen");
        assertEquals(List.of(0, 2), zwei.zeilen().stream().map(z -> z.fund().start()).toList());
    }

    @Test
    void einVorkommenVorDerMarkierungVerschmilztMitDemFundDerMarkiertenStelle() {
        String text = "Mu Mu_Mu Mu";
        Finding mitte = automatisch(FindingType.EMAIL, text, "u M");
        List<FundZeile> bestand = zeilen(false, mitte);

        ManuellerFund.Ergebnis ergebnis = ManuellerFund.hinzufuegen(text, bestand, 0, 2);

        // N0 = [0,4); das Vorkommen [3,5) ragt heraus: ein Fund [0,5); [6,8) und [9,11) sind frei.
        assertEquals(List.of(0, 6, 9), ergebnis.zeilen().stream().map(z -> z.fund().start()).toList());
        assertEquals("Mu Mu", ergebnis.zeilen().get(0).fund().originalValue());
        assertEquals(4, ergebnis.hinzugefuegt());
        assertEquals(1, ergebnis.ersetzt());
        pruefeKeinVorkommenImKlartext(text, ergebnis.zeilen(), "Mu");
        pruefeGueltigUndRoundtrip(text, ergebnis.zeilen());
    }

    @Test
    void jedesNichtBetroffeneZeilenBehaeltIhreAuswahlBeiVereinigung() {
        String text = "Muster 192.0.2.10 Muster@example.org 203.0.113.5";
        Finding ip1 = automatisch(FindingType.IP, text, "192.0.2.10");
        Finding mail = automatisch(FindingType.EMAIL, text, "Muster@example.org");
        Finding ip2 = automatisch(FindingType.IP, text, "203.0.113.5");
        List<FundZeile> bestand = List.of(new FundZeile(ip1, false, null), new FundZeile(mail, false, null),
                new FundZeile(ip2, true, null));

        ManuellerFund.Ergebnis ergebnis = ManuellerFund.hinzufuegen(text, bestand, 0, 6);

        assertEquals(4, ergebnis.zeilen().size());
        assertEquals(List.of(true, false, true, true), ergebnis.zeilen().stream().map(FundZeile::angewaehlt).toList());
        assertSame(ip1, ergebnis.zeilen().get(1).fund());
        assertSame(ip2, ergebnis.zeilen().get(3).fund());
        pruefeKeinVorkommenImKlartext(text, ergebnis.zeilen(), "Muster");
    }

    @Test
    void eineMillionZeichenMitEinzelzeichenMarkierungBleibtImSekundenbereich() {
        String text = "ab".repeat(500_000);
        List<FundZeile> bestand = new ArrayList<>();
        for (int i = 0; i < text.length(); i += 10) {
            // jeder zehnte Fund ist abgewaehlt und ragt ueber ein "a" hinaus
            bestand.add(new FundZeile(new Finding(FindingType.IP, text.substring(i + 1, i + 3), i + 1, i + 3, 0.9,
                    "ip"), i % 20 != 0, null));
        }

        long beginn = System.nanoTime();
        ManuellerFund.Ergebnis ergebnis = ManuellerFund.hinzufuegen(text, bestand, 0, 1);
        long dauerMs = (System.nanoTime() - beginn) / 1_000_000;

        assertTrue(dauerMs < 5_000, "Dauer in ms: " + dauerMs);
        assertFalse(ergebnis.abgelehnt());
        pruefeKeinVorkommenImKlartext(text, ergebnis.zeilen(), "a");
    }

    // ---------------------------------------------------------------- AK-26: leer und Leerraum

    @Test
    void leereMarkierungUndLeerraumWerdenAbgelehntOhneAusnahme() {
        String text = "Name  " + NBSP + NBSP + GEVIERT + "\t\n Ende";
        Finding vorhanden = automatisch(FindingType.EMAIL, text, "Name");
        List<FundZeile> bestand = zeilen(true, vorhanden);

        int[][] markierungen = {{2, 2}, {4, 6}, {6, 8}, {8, 9}, {9, 11}, {0, 0}, {text.length(), text.length()}};
        for (int[] m : markierungen) {
            ManuellerFund.Ergebnis ergebnis = ManuellerFund.hinzufuegen(text, bestand, m[0], m[1]);
            assertTrue(ergebnis.abgelehnt(), m[0] + ".." + m[1]);
            assertEquals(bestand, ergebnis.zeilen());
            assertEquals(0, ergebnis.hinzugefuegt());
        }
    }

    @Test
    void eineMarkierungInnerhalbEinesEmojiPaaresAnleerRandWirdNichtZuLeer() {
        String text = "a" + EMOJI + "b";

        ManuellerFund.Ergebnis leer = ManuellerFund.hinzufuegen(text, List.of(), 2, 2);

        assertTrue(leer.abgelehnt(), "eine leere Markierung mitten im Emoji bleibt leer");
    }

    @Test
    void ueberSitzungMeldetLeereMarkierungDenFesttextOhneAusnahmeUndAendertNichts() {
        Sitzung sitzung = gescannt("Hallo  Welt", automatisch(FindingType.EMAIL, "Hallo  Welt", "Welt"));
        List<FundZeile> vorher = sitzung.zeilen();
        String textVorher = sitzung.textFuerKi();
        AtomicInteger aufrufe = new AtomicInteger();
        sitzung.beobachten(aufrufe::incrementAndGet);

        sitzung.markierungHinzufuegen(5, 7);
        assertEquals("Bitte zuerst im Text eine Stelle markieren.", sitzung.statusleiste().meldung());
        sitzung.markierungHinzufuegen(3, 3);

        assertEquals(vorher, sitzung.zeilen());
        assertEquals(textVorher, sitzung.textFuerKi());
        assertEquals(ScanZustand.GUELTIG, sitzung.scanZustand());
        assertEquals(2, aufrufe.get());
        assertTrue(dialoge.fehler.isEmpty());
    }

    @Test
    void ohneGueltigenScanIstDieFunktionGesperrtUndTutNichts() {
        Sitzung sitzung = Sitzung.standard(new TestZwischenablage(), dialoge);
        AtomicInteger aufrufe = new AtomicInteger();
        sitzung.beobachten(aufrufe::incrementAndGet);

        sitzung.markierungHinzufuegen(0, 3);

        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.MARKIERUNG_HINZUFUEGEN));
        assertEquals(Texte.ERST_SCANNEN, sitzung.bedienzustand().sperrgrund().get(Funktion.MARKIERUNG_HINZUFUEGEN));
        assertEquals(Texte.BEREIT, sitzung.statusleiste().meldung());
        assertEquals(0, aufrufe.get());

        // nach einer Eingabeaenderung (VERALTET) ebenfalls gesperrt
        scannen(sitzung, "Max Mustermann");
        sitzung.eingabeGeaendert();
        assertEquals(ScanZustand.VERALTET, sitzung.scanZustand());
        String meldung = sitzung.statusleiste().meldung();
        sitzung.markierungHinzufuegen(0, 3);
        assertEquals(List.of(), sitzung.zeilen());
        assertEquals("", sitzung.textFuerKi());
        assertEquals(meldung, sitzung.statusleiste().meldung());
        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.MARKIERUNG_HINZUFUEGEN));

        // gueltiger Scan: aktiv, auch bei 0 Funden
        Sitzung neu = gescannt("Max Mustermann");
        assertTrue(neu.bedienzustand().istAktiv(Funktion.MARKIERUNG_HINZUFUEGEN));
    }

    @Test
    void eineMarkierungAusserhalbDesTextesIstEinProgrammierfehlerOhneKlartextUndAendertNichts() {
        String text = "Max Mustermann";
        Sitzung sitzung = gescannt(text);
        sitzung.markierungHinzufuegen(0, 3);
        List<FundZeile> vorher = sitzung.zeilen();
        String ki = sitzung.textFuerKi();
        String status = sitzung.statusleiste().toString();

        IndexOutOfBoundsException e1 = assertThrows(IndexOutOfBoundsException.class,
                () -> sitzung.markierungHinzufuegen(-1, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> sitzung.markierungHinzufuegen(0, text.length() + 1));
        assertThrows(IndexOutOfBoundsException.class, () -> sitzung.markierungHinzufuegen(5, 2));

        assertFalse(String.valueOf(e1.getMessage()).contains("Max"));
        assertEquals(vorher, sitzung.zeilen());
        assertEquals(ki, sitzung.textFuerKi());
        assertEquals(status, sitzung.statusleiste().toString());
    }

    // ---------------------------------------------------------------- AK-27: Surrogatpaare

    @Test
    void eineMarkierungMitteImEmojiWirdAufDasGanzeZeichenErweitertLinksUndRechts() {
        String text = "ab" + EMOJI + "cd";

        // Start zwischen High- und Low-Surrogat (3), Ende danach (5)
        ManuellerFund.Ergebnis links = ManuellerFund.hinzufuegen(text, List.of(), 3, 5);
        assertEquals(EMOJI + "c", links.zeilen().get(0).fund().originalValue());
        assertEquals(2, links.zeilen().get(0).fund().start());

        // Ende zwischen High- und Low-Surrogat (3), Start davor (1)
        ManuellerFund.Ergebnis rechts = ManuellerFund.hinzufuegen(text, List.of(), 1, 3);
        assertEquals("b" + EMOJI, rechts.zeilen().get(0).fund().originalValue());
        assertEquals(4, rechts.zeilen().get(0).fund().end());

        // nur das Low-Surrogat markiert: ganzes Zeichen
        ManuellerFund.Ergebnis nurHaelfte = ManuellerFund.hinzufuegen(text, List.of(), 3, 4);
        assertEquals(EMOJI, nurHaelfte.zeilen().get(0).fund().originalValue());

        for (ManuellerFund.Ergebnis e : List.of(links, rechts, nurHaelfte)) {
            pruefeGueltigUndRoundtrip(text, e.zeilen());
        }
    }

    @Test
    void ueberSitzungWirftDerPseudonymizerBeiHalberMarkierungNicht() {
        String text = "Hallo " + EMOJI + " und " + EMOJI + ".";
        Sitzung sitzung = gescannt(text);

        sitzung.markierungHinzufuegen(7, 8);

        assertEquals(2, sitzung.zeilen().size());
        assertEquals("Hallo [[TEXT_1]] und [[TEXT_1]].", sitzung.textFuerKi());
        assertTrue(dialoge.fehler.isEmpty());
    }

    @Test
    void einAlleinstehendesLowSurrogatNimmtKeinVorkommenMitteInEinemEmojiMit() {
        String low = String.valueOf((char) 0xDE00);
        String text = low + "|" + EMOJI;
        assertEquals(low, text.substring(0, 1));

        ManuellerFund.Ergebnis ergebnis = ManuellerFund.hinzufuegen(text, List.of(), 0, 1);

        assertEquals(1, ergebnis.hinzugefuegt(), "nur die Markierung; die Haelfte im Emoji wird nicht genommen");
        assertEquals(0, ergebnis.bereitsGeschuetzt());
        new Pseudonymizer().pseudonymize(text, ergebnis.zeilen().stream().map(FundZeile::fund).toList());
    }

    // ---------------------------------------------------------------- AK-28: Auswahlzustand

    @Test
    void abgewaehlteZeilenBehaltenBeimHinzufuegenIhrenZustandUndNeueSindAngewaehlt() {
        String text = "mustermann@example.org Max Mustermann 192.0.2.10 Max Mustermann";
        Finding mail = automatisch(FindingType.EMAIL, text, "mustermann@example.org");
        Finding ip = automatisch(FindingType.IP, text, "192.0.2.10");
        List<FundZeile> bestand = List.of(new FundZeile(mail, false, null), new FundZeile(ip, true, null));

        ManuellerFund.Ergebnis ergebnis = hinzu(text, bestand, "Max Mustermann");

        assertEquals(4, ergebnis.zeilen().size());
        assertEquals(List.of(false, true, true, true), ergebnis.zeilen().stream().map(FundZeile::angewaehlt).toList());
        assertSame(mail, ergebnis.zeilen().get(0).fund());
        assertSame(ip, ergebnis.zeilen().get(2).fund());
        List<Integer> starts = ergebnis.zeilen().stream().map(z -> z.fund().start()).toList();
        assertEquals(starts.stream().sorted().toList(), starts, "nach Start sortiert");
    }

    @Test
    void einManuellerFundLaesstSichAbUndWiederAnwaehlenWieJederAndere() {
        String text = "Beispielname schreibt an Beispielname und mustermann@example.org";
        Sitzung sitzung = Sitzung.standard(new TestZwischenablage(), dialoge);
        scannen(sitzung, text);
        sitzung.markierungHinzufuegen(0, 12);
        String voll = sitzung.textFuerKi();
        assertEquals("[[TEXT_1]] schreibt an [[TEXT_1]] und [[EMAIL_2]]", voll);

        sitzung.anwaehlen(0, false);

        assertFalse(sitzung.zeilen().get(0).angewaehlt());
        assertNull(sitzung.zeilen().get(0).platzhalter());
        assertEquals("Beispielname schreibt an [[TEXT_1]] und [[EMAIL_2]]", sitzung.textFuerKi());

        // weiteres manuelles Hinzufuegen laesst die Abwahl bestehen
        int mail = text.indexOf("mustermann@");
        sitzung.markierungHinzufuegen(text.indexOf("schreibt"), text.indexOf("schreibt") + 8);
        assertFalse(sitzung.zeilen().get(0).angewaehlt());
        assertTrue(sitzung.zeilen().get(1).angewaehlt());
        assertEquals(mail, sitzung.zeilen().get(3).fund().start());

        sitzung.anwaehlen(0, true);
        assertEquals("[[TEXT_1]] [[TEXT_2]] an [[TEXT_1]] und [[EMAIL_3]]", sitzung.textFuerKi());
    }

    @Test
    void dieZuordnungFolgtDemManuellenFundUndDieStatusleisteBleibtOhneKlartext() {
        String text = "Beispielname schreibt an Beispielname";
        Sitzung sitzung = gescannt(text);
        assertEquals("Zuordnung: 0 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());

        sitzung.markierungHinzufuegen(0, 12);

        assertEquals("Zuordnung: 1 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
        assertFalse(sitzung.statusleiste().toString().contains("Beispielname"));
        assertFalse(sitzung.toString().contains("Beispielname"));
    }

    // ---------------------------------------------------------------- Meldungen

    @Test
    void dieMeldungDesHinzufuegensEnthaeltNurFesteWorteUndZahlen() {
        assertEquals("1 Stelle als Fund hinzugefügt.", Texte.hinzugefuegt(1, 0, 0));
        assertEquals("3 Stellen als Fund hinzugefügt.", Texte.hinzugefuegt(3, 0, 0));
        assertEquals("1 Stelle als Fund hinzugefügt. 1 bestehender Fund wurde dabei ersetzt.",
                Texte.hinzugefuegt(1, 0, 1));
        assertEquals("2 Stellen als Fund hinzugefügt. 2 bestehende Funde wurden dabei ersetzt.",
                Texte.hinzugefuegt(2, 0, 2));
        assertEquals("3 Stellen als Fund hinzugefügt. 1 Stelle war bereits geschützt.",
                Texte.hinzugefuegt(3, 1, 0));
        assertEquals("1 Stelle als Fund hinzugefügt. 2 Stellen waren bereits geschützt.",
                Texte.hinzugefuegt(1, 2, 0));
        assertEquals("2 Stellen als Fund hinzugefügt. 2 Stellen waren bereits geschützt. "
                + "3 bestehende Funde wurden dabei ersetzt.", Texte.hinzugefuegt(2, 2, 3));
        assertFalse(Texte.hinzugefuegt(2, 2, 3).contains("übersprungen"));
        assertEquals("Bitte zuerst im Text eine Stelle markieren.", Texte.MARKIERUNG_FEHLT);
    }

    // ---------------------------------------------------------------- Ergebnis und Reinheit

    @Test
    void dieFunktionVeraendertIhreEingabenNichtUndErgebnisUndToStringTragenKeinenText() {
        String text = "Max Mustermann und Max Mustermann";
        Finding mail = automatisch(FindingType.EMAIL, text, "Max");
        List<FundZeile> bestand = new ArrayList<>(zeilen(true, mail));
        List<FundZeile> kopie = List.copyOf(bestand);

        ManuellerFund.Ergebnis ergebnis = hinzu(text, bestand, "Mustermann");

        assertEquals(kopie, bestand);
        assertFalse(ergebnis.toString().contains("Mustermann"));
        assertTrue(ergebnis.toString().contains("hinzugefuegt=2"), ergebnis.toString());
        assertThrows(UnsupportedOperationException.class, () -> ergebnis.zeilen().clear());
    }

    @Test
    void dieMarkierungAufDemStandardScannerErgebnisBleibtMitDemKernKonsistent() {
        String text = "Kontakt Max Mustermann, mustermann@example.org, Server 192.0.2.10, Max Mustermann.";
        List<Finding> kern = TextScanner.standard().scan(text);
        Sitzung sitzung = Sitzung.standard(new TestZwischenablage(), dialoge);
        scannen(sitzung, text);
        assertEquals(kern.size(), sitzung.zeilen().size());

        sitzung.markierungHinzufuegen(text.indexOf("Max"), text.indexOf("Max") + 14);

        assertEquals(kern.size() + 2, sitzung.zeilen().size());
        assertFalse(sitzung.textFuerKi().contains("Mustermann"));
        assertTrue(sitzung.statusleiste().meldung().startsWith("2 Stellen als Fund hinzugefügt."));
    }
}
