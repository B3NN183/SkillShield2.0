package de.skillshield.ui.logik;

import static de.skillshield.ui.logik.Attrappen.ALLE_STUFEN;
import static de.skillshield.ui.logik.Attrappen.scannen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.ui.logik.Attrappen.TestDialoge;
import de.skillshield.ui.logik.Attrappen.TestZwischenablage;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Scan-Zustand, Ablehnungen, Gueltigkeit, Statusleiste, Bedienzustand (AK-4, 6-10, 12, 20, 30, 40, 53). */
class SitzungScanTest {

    private static final String MARKER = "mustermann@example.org";

    private final TestDialoge dialoge = new TestDialoge();
    private final TestZwischenablage zwischenablage = new TestZwischenablage();
    private final Sitzung sitzung = Sitzung.standard(zwischenablage, dialoge);

    private static List<Finding> funde(Sitzung s) {
        return s.zeilen().stream().map(FundZeile::fund).toList();
    }

    // ---------------------------------------------------------------- Start (AK-4)

    @Test
    void startzustandIstKeinScanBereitUndKeineZuordnung() {
        assertEquals(ScanZustand.KEIN_SCAN, sitzung.scanZustand());
        assertEquals(new Statusleiste("Bereit", "Keine Zuordnung"), sitzung.statusleiste());
        assertEquals(List.of(), sitzung.zeilen());
        assertEquals("", sitzung.textFuerKi());
        Bedienzustand b = sitzung.bedienzustand();
        assertEquals(Set.of(Funktion.SCANNEN, Funktion.EINGABE_BEARBEITEN, Funktion.ZURUECKSETZEN, Funktion.BEENDEN),
                b.aktiv());
        assertEquals(Texte.ERST_SCANNEN, b.sperrgrund().get(Funktion.FUER_KI_KOPIEREN));
        assertEquals(Texte.ERST_SCANNEN, b.sperrgrund().get(Funktion.MARKIERUNG_HINZUFUEGEN));
        assertEquals(Texte.ERST_SCANNEN, b.sperrgrund().get(Funktion.ZURUECKUEBERSETZEN));
        assertFalse(b.istAktiv(Funktion.ERGEBNIS_KOPIEREN));
        assertTrue(b.sperrgrund().containsKey(Funktion.ERGEBNIS_KOPIEREN));
    }

    @Test
    void jedeGesperrteFunktionHatEinenSperrgrundUndJedeAktiveKeinen() {
        List<Sitzung> zustaende = List.of(sitzung, gescannt(ALLE_STUFEN), gescannt("Hallo Welt"));
        for (Sitzung s : zustaende) {
            Bedienzustand b = s.bedienzustand();
            for (Funktion f : Funktion.values()) {
                assertEquals(!b.istAktiv(f), b.sperrgrund().containsKey(f), f.name());
            }
        }
    }

    // ---------------------------------------------------------------- Ablehnungen (AK-6, AK-7)

    /** Leere Eingaben; die unsichtbaren Zeichen werden hier sichtbar über ihren Codepunkt erzeugt. */
    static Stream<String> leereEingaben() {
        String nbsp = Character.toString(0x00A0);
        String emSpace = Character.toString(0x2003);
        String ideographic = Character.toString(0x3000);
        String zeilentrenner = Character.toString(0x2028);
        return Stream.of("", " ", "\n\t \r\n", nbsp, emSpace + " " + ideographic, zeilentrenner,
                nbsp + "\n" + ideographic);
    }

    @ParameterizedTest
    @MethodSource("leereEingaben")
    void leererOderLeerraumTextWirdAbgelehntUndAendertNichts(String eingabe) {
        assertTrue(sitzung.scanAnfordern(eingabe).isEmpty());

        assertEquals(new Statusleiste("Bitte zuerst Text eingeben.", "Keine Zuordnung"), sitzung.statusleiste());
        assertEquals(ScanZustand.KEIN_SCAN, sitzung.scanZustand());
        assertEquals(List.of(), dialoge.fragen);
        assertEquals(List.of(), dialoge.fehler);
        assertEquals(0, zwischenablage.zugriffe);
    }

    @Test
    void ablehnungLaesstFundeZuordnungUndTextFuerKiUnveraendert() {
        scannen(sitzung, ALLE_STUFEN);
        List<FundZeile> zeilen = sitzung.zeilen();
        String text = sitzung.textFuerKi();
        String zuordnung = sitzung.statusleiste().zuordnung();

        assertTrue(sitzung.scanAnfordern("  ").isEmpty());
        assertEquals("Bitte zuerst Text eingeben.", sitzung.statusleiste().meldung());
        assertTrue(sitzung.scanAnfordern("x".repeat(Sitzung.MAX_ZEICHEN + 1)).isEmpty());
        assertEquals(Texte.zuLang(Sitzung.MAX_ZEICHEN), sitzung.statusleiste().meldung());

        assertEquals(ScanZustand.GUELTIG, sitzung.scanZustand());
        assertEquals(zeilen, sitzung.zeilen());
        assertEquals(text, sitzung.textFuerKi());
        assertEquals(zuordnung, sitzung.statusleiste().zuordnung());
        assertEquals(List.of(), dialoge.fragen);
        assertEquals(0, zwischenablage.zugriffe);
    }

    @Test
    void genauEineMillionZeichenWerdenGescanntEinZeichenMehrNicht() {
        AtomicInteger scans = new AtomicInteger();
        Sitzung s = new Sitzung(t -> {
            scans.incrementAndGet();
            return List.of();
        }, zwischenablage, dialoge);

        assertTrue(s.scanAnfordern("x".repeat(Sitzung.MAX_ZEICHEN + 1)).isEmpty());
        assertEquals(ScanZustand.KEIN_SCAN, s.scanZustand());
        ScanAuftrag auftrag = s.scanAnfordern("x".repeat(Sitzung.MAX_ZEICHEN)).orElseThrow();
        assertEquals(ScanZustand.LAEUFT, s.scanZustand());
        s.scanAbgeschlossen(auftrag, auftrag.ausfuehren());

        assertEquals(1, scans.get());
        assertEquals(ScanZustand.GUELTIG, s.scanZustand());
    }

    @Test
    void obergrenzeSteht1000000InDeutscherSchreibweiseInDerMeldung() {
        assertEquals(1_000_000, Sitzung.MAX_ZEICHEN);
        assertEquals("Der Text ist zu lang (höchstens 1.000.000 Zeichen). Es wurde nicht gescannt.",
                Texte.zuLang(Sitzung.MAX_ZEICHEN));
    }

    // ---------------------------------------------------------------- Scan, Funde, Stufen (AK-8, AK-9, AK-53)

    @Test
    void tabelleZeigtGenauDieFundeDesKernsInPositionsreihenfolge() {
        scannen(sitzung, ALLE_STUFEN);

        List<Finding> erwartet = TextScanner.standard().scan(ALLE_STUFEN);
        assertEquals(erwartet, funde(sitzung));
        assertTrue(erwartet.size() >= 4, "Vorbedingung: je Typ ein Fund");
        for (int i = 1; i < erwartet.size(); i++) {
            assertTrue(erwartet.get(i - 1).start() < erwartet.get(i).start());
        }
        assertEquals(Set.of(FindingType.EMAIL, FindingType.IP, FindingType.KVNR, FindingType.LANR),
                Set.copyOf(erwartet.stream().map(Finding::type).toList()));
    }

    @Test
    void nachDemScanSindAlleFundeAngewaehltAuchFraglichUndWahrscheinlich() {
        scannen(sitzung, ALLE_STUFEN);

        Set<ConfidenceLevel> stufen = EnumSet.noneOf(ConfidenceLevel.class);
        for (FundZeile zeile : sitzung.zeilen()) {
            stufen.add(ConfidenceLevel.of(zeile.fund().confidence()));
            assertTrue(zeile.angewaehlt());
            assertTrue(sitzung.textFuerKi().contains(zeile.platzhalter()));
        }
        assertEquals(EnumSet.allOf(ConfidenceLevel.class), stufen, "Vorbedingung: je Stufe mindestens ein Fund");
    }

    @Test
    void sitzungVeraendertKeinenFundDesScanners() {
        Finding a = Finding.manual(FindingType.TEXT, "Beispiel", 0, 8);
        Finding b = Finding.manual(FindingType.TEXT, "Text", 13, 17);
        Sitzung s = new Sitzung(t -> List.of(b, a), zwischenablage, dialoge);

        scannen(s, "Beispiel und Text");

        // Reihenfolge und Inhalt wie vom Scanner geliefert; die Oberflaeche ergaenzt, streicht, bewertet nichts.
        assertEquals(List.of(b, a), funde(s));
    }

    @Test
    void statusleisteNenntFundeAngewaehlteUndFragliche() {
        scannen(sitzung, ALLE_STUFEN);

        assertEquals("4 Funde, 4 angewählt, 1 fraglich.", sitzung.statusleiste().meldung());
        assertEquals("Zuordnung: 4 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
    }

    @Test
    void zaehlungMitEinzahlUndMehrzahl() {
        assertEquals("5 Funde, 4 angewählt, 1 fraglich. 1 Fund wird nicht ersetzt.", Texte.zaehlung(5, 4, 1));
        assertEquals("3 Funde, 1 angewählt, 0 fraglich. 2 Funde werden nicht ersetzt.", Texte.zaehlung(3, 1, 0));
        assertEquals("1 Fund, 1 angewählt, 1 fraglich.", Texte.zaehlung(1, 1, 1));
        assertEquals("0 Funde, 0 angewählt, 0 fraglich.", Texte.zaehlung(0, 0, 0));
    }

    // ---------------------------------------------------------------- Scan laeuft (AK-10, Logikteil)

    @Test
    void waehrendDesScansIstNurBeendenAktivUndDieMeldungSagtScanLaeuft() {
        ScanAuftrag auftrag = sitzung.scanAnfordern(ALLE_STUFEN).orElseThrow();

        assertEquals(ScanZustand.LAEUFT, sitzung.scanZustand());
        assertEquals("Scan läuft …", sitzung.statusleiste().meldung());
        Bedienzustand b = sitzung.bedienzustand();
        assertEquals(Set.of(Funktion.BEENDEN), b.aktiv());
        assertEquals(Texte.SCAN_LAEUFT, b.sperrgrund().get(Funktion.SCANNEN));
        assertEquals(Texte.SCAN_LAEUFT, b.sperrgrund().get(Funktion.EINGABE_BEARBEITEN));

        assertTrue(sitzung.scanAnfordern(ALLE_STUFEN).isEmpty(), "kein zweiter Scan parallel");
        assertEquals(List.of(), dialoge.fragen);

        sitzung.scanAbgeschlossen(auftrag, auftrag.ausfuehren());
        assertTrue(sitzung.bedienzustand().istAktiv(Funktion.SCANNEN));
        assertTrue(sitzung.bedienzustand().istAktiv(Funktion.EINGABE_BEARBEITEN));
    }

    @Test
    void derAuftragIstEineMomentaufnahmeUndEnthaeltKeinenTextInToString() {
        ScanAuftrag auftrag = sitzung.scanAnfordern(ALLE_STUFEN).orElseThrow();

        assertEquals(TextScanner.standard().scan(ALLE_STUFEN), auftrag.ausfuehren());
        assertFalse(auftrag.toString().contains("example"));
        assertTrue(auftrag.toString().contains(String.valueOf(ALLE_STUFEN.length())));
    }

    // ---------------------------------------------------------------- Eingabe geaendert (AK-12, D-4)

    @Test
    void eingabeAenderungMachtDenScanVeraltetLeertTabelleUndBereich3AberNichtDieZuordnung() {
        scannen(sitzung, ALLE_STUFEN);
        String zuordnung = sitzung.statusleiste().zuordnung();

        sitzung.eingabeGeaendert();

        assertEquals(ScanZustand.VERALTET, sitzung.scanZustand());
        assertEquals(List.of(), sitzung.zeilen());
        assertEquals("", sitzung.textFuerKi());
        assertEquals(new Statusleiste("Der Text wurde geändert. Bitte erneut scannen.", zuordnung),
                sitzung.statusleiste());
        Bedienzustand b = sitzung.bedienzustand();
        assertFalse(b.istAktiv(Funktion.FUER_KI_KOPIEREN));
        assertFalse(b.istAktiv(Funktion.MARKIERUNG_HINZUFUEGEN));
        assertFalse(b.istAktiv(Funktion.ALLE_ANWAEHLEN));
        assertEquals(Texte.NEUER_SCAN_NOETIG, b.sperrgrund().get(Funktion.FUER_KI_KOPIEREN));
        assertTrue(b.istAktiv(Funktion.SCANNEN));
    }

    @Test
    void eingabeAenderungVorDemScanUndWiederholteAenderungAendernNichts() {
        AtomicInteger aufrufe = new AtomicInteger();
        sitzung.beobachten(aufrufe::incrementAndGet);

        sitzung.eingabeGeaendert();
        assertEquals(ScanZustand.KEIN_SCAN, sitzung.scanZustand());
        assertEquals("Bereit", sitzung.statusleiste().meldung());

        scannen(sitzung, ALLE_STUFEN);
        sitzung.eingabeGeaendert();
        int nachErsterAenderung = aufrufe.get();
        sitzung.eingabeGeaendert();

        assertEquals(nachErsterAenderung, aufrufe.get());
        assertEquals(ScanZustand.VERALTET, sitzung.scanZustand());
    }

    @Test
    void nachVeraltetemScanIstNeuerScanMoeglichUndErsetztDieZuordnung() {
        scannen(sitzung, ALLE_STUFEN);
        sitzung.eingabeGeaendert();
        dialoge.antwort = true;

        scannen(sitzung, "nur " + MARKER);

        assertEquals(List.of(Rueckfrage.NEUER_SCAN), dialoge.fragen);
        assertEquals(ScanZustand.GUELTIG, sitzung.scanZustand());
        assertEquals(1, sitzung.zeilen().size());
        assertEquals("Zuordnung: 1 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
    }

    @Test
    void eineAenderungWaehrendDesScansVerwirftDasErgebnis() {
        ScanAuftrag auftrag = sitzung.scanAnfordern(ALLE_STUFEN).orElseThrow();
        sitzung.eingabeGeaendert();

        sitzung.scanAbgeschlossen(auftrag, auftrag.ausfuehren());

        assertEquals(ScanZustand.KEIN_SCAN, sitzung.scanZustand());
        assertEquals(List.of(), sitzung.zeilen());
        assertEquals("Keine Zuordnung", sitzung.statusleiste().zuordnung());
        assertTrue(sitzung.bedienzustand().istAktiv(Funktion.SCANNEN));
    }

    @Test
    void eineAenderungWaehrendEinesZweitenScansMachtDenVorherigenScanVeraltet() {
        scannen(sitzung, ALLE_STUFEN);
        dialoge.antwort = true;
        ScanAuftrag auftrag = sitzung.scanAnfordern(ALLE_STUFEN).orElseThrow();
        sitzung.eingabeGeaendert();

        sitzung.scanAbgeschlossen(auftrag, auftrag.ausfuehren());

        assertEquals(ScanZustand.VERALTET, sitzung.scanZustand());
        assertEquals(List.of(), sitzung.zeilen());
        assertEquals(Texte.NEUER_SCAN_NOETIG, sitzung.statusleiste().meldung());
        assertNotEquals("Keine Zuordnung", sitzung.statusleiste().zuordnung());
    }

    // ---------------------------------------------------------------- Null Funde (AK-30)

    @Test
    void fuerKiKopierenIstNachGueltigemScanAuchBeiNullFundenAktiv() {
        scannen(sitzung, "Hallo Welt");

        assertEquals(ScanZustand.GUELTIG, sitzung.scanZustand());
        assertEquals(List.of(), sitzung.zeilen());
        assertEquals("Hallo Welt", sitzung.textFuerKi());
        Bedienzustand b = sitzung.bedienzustand();
        assertTrue(b.istAktiv(Funktion.FUER_KI_KOPIEREN));
        assertTrue(b.istAktiv(Funktion.MARKIERUNG_HINZUFUEGEN));
        assertFalse(b.istAktiv(Funktion.ALLE_ANWAEHLEN));
        assertEquals(Texte.KEINE_FUNDE, b.sperrgrund().get(Funktion.ALLE_ANWAEHLEN));
        assertEquals("0 Funde, 0 angewählt, 0 fraglich.", sitzung.statusleiste().meldung());
        assertEquals("Zuordnung: 0 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
    }

    @Test
    void neuerScanOhnePlatzhalterFragtNichtNach() {
        scannen(sitzung, "Hallo Welt");
        dialoge.antwort = false;

        assertTrue(sitzung.scanAnfordern("Noch ein Text").isPresent());
        assertEquals(List.of(), dialoge.fragen);
    }

    // ---------------------------------------------------------------- Rueckfrage neuer Scan (AK-41)

    @Test
    void neuerScanMitPlatzhalternFragtNachUndAbbrechenAendertNichts() {
        scannen(sitzung, ALLE_STUFEN);
        List<FundZeile> zeilen = sitzung.zeilen();
        Statusleiste status = sitzung.statusleiste();
        dialoge.antwort = false;

        assertTrue(sitzung.scanAnfordern("anderer Text mit " + MARKER).isEmpty());

        assertEquals(List.of(Rueckfrage.NEUER_SCAN), dialoge.fragen);
        assertEquals(ScanZustand.GUELTIG, sitzung.scanZustand());
        assertEquals(zeilen, sitzung.zeilen());
        assertEquals(status, sitzung.statusleiste());
    }

    @Test
    void bestaetigterNeuerScanErsetztDieZuordnungVollstaendig() {
        scannen(sitzung, ALLE_STUFEN);
        dialoge.antwort = true;

        scannen(sitzung, "nur " + MARKER);

        assertEquals(List.of(Rueckfrage.NEUER_SCAN), dialoge.fragen);
        assertEquals("Zuordnung: 1 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
        assertEquals("nur [[EMAIL_1]]", sitzung.textFuerKi());
    }

    // ---------------------------------------------------------------- Fehler (AK-30/AK-50, A-5)

    private static final class SchaltbarerScanner implements Function<String, List<Finding>> {
        final AtomicBoolean scheitert = new AtomicBoolean();

        @Override
        public List<Finding> apply(String text) {
            if (scheitert.get()) {
                throw new IllegalStateException(MARKER);
            }
            return TextScanner.standard().scan(text);
        }
    }

    private void scanFehlschlagen(Sitzung s, String eingabe) {
        ScanAuftrag auftrag = s.scanAnfordern(eingabe).orElseThrow();
        try {
            s.scanAbgeschlossen(auftrag, auftrag.ausfuehren());
        } catch (RuntimeException e) {
            s.scanFehlgeschlagen(auftrag, e);
        }
    }

    @Test
    void scannerFehlerLaesstDenStartzustandUndZeigtFestenTextMitKlassennamen() {
        SchaltbarerScanner scanner = new SchaltbarerScanner();
        scanner.scheitert.set(true);
        Sitzung s = new Sitzung(scanner, zwischenablage, dialoge);

        scanFehlschlagen(s, ALLE_STUFEN);

        assertEquals(List.of("Beim Verarbeiten ist ein Fehler aufgetreten. Es wurde nichts kopiert. "
                + "(java.lang.IllegalStateException)"), dialoge.fehler);
        assertEquals(ScanZustand.KEIN_SCAN, s.scanZustand());
        assertEquals(List.of(), s.zeilen());
        assertEquals("Keine Zuordnung", s.statusleiste().zuordnung());
        assertFalse(s.bedienzustand().istAktiv(Funktion.FUER_KI_KOPIEREN));
        assertTrue(s.bedienzustand().istAktiv(Funktion.SCANNEN));
        assertEquals(0, zwischenablage.zugriffe);
    }

    @Test
    void ausnahmeMeldungUndFundtextErscheinenNieInDenAusgaben() {
        SchaltbarerScanner scanner = new SchaltbarerScanner();
        scanner.scheitert.set(true);
        Sitzung s = new Sitzung(scanner, zwischenablage, dialoge);

        scanFehlschlagen(s, "Mail " + MARKER);

        for (String ausgabe : List.of(String.join("|", dialoge.fehler), s.statusleiste().toString(),
                s.bedienzustand().toString(), s.toString())) {
            assertFalse(ausgabe.contains(MARKER), ausgabe);
        }
    }

    @Test
    void fehlgeschlagenerNeuerScanLaesstEinenGueltigenScanGueltig() {
        SchaltbarerScanner scanner = new SchaltbarerScanner();
        Sitzung s = new Sitzung(scanner, zwischenablage, dialoge);
        scannen(s, ALLE_STUFEN);
        List<FundZeile> zeilen = s.zeilen();
        String text = s.textFuerKi();
        String zuordnung = s.statusleiste().zuordnung();
        dialoge.antwort = true;
        scanner.scheitert.set(true);

        scanFehlschlagen(s, ALLE_STUFEN);

        assertEquals(ScanZustand.GUELTIG, s.scanZustand());
        assertEquals(zeilen, s.zeilen());
        assertEquals(text, s.textFuerKi());
        assertEquals(zuordnung, s.statusleiste().zuordnung());
        assertTrue(s.bedienzustand().istAktiv(Funktion.FUER_KI_KOPIEREN));
        assertEquals(1, dialoge.fehler.size());
    }

    @Test
    void ueberlappendeFundeScheiternBeimBerechnenVorDemFestschreiben() {
        Sitzung s = new Sitzung(t -> List.of(Finding.manual(FindingType.TEXT, t.substring(0, 5), 0, 5),
                Finding.manual(FindingType.TEXT, t.substring(3, 8), 3, 8)), zwischenablage, dialoge);

        scannen(s, "Beispieltext");

        assertEquals(List.of("Beim Verarbeiten ist ein Fehler aufgetreten. Es wurde nichts kopiert. "
                + "(de.skillshield.core.pseudonymize.InvalidFindingException)"), dialoge.fehler);
        assertEquals(ScanZustand.KEIN_SCAN, s.scanZustand());
        assertEquals(List.of(), s.zeilen());
        assertEquals("Keine Zuordnung", s.statusleiste().zuordnung());
    }

    /** Scanner, der auf Wunsch überlappende Funde liefert; der Kern lehnt sie beim Berechnen ab. */
    private static final class UmschaltbarerScanner implements Function<String, List<Finding>> {
        final AtomicBoolean ueberlappend = new AtomicBoolean();

        @Override
        public List<Finding> apply(String text) {
            if (ueberlappend.get()) {
                return List.of(Finding.manual(FindingType.TEXT, text.substring(0, 5), 0, 5),
                        Finding.manual(FindingType.TEXT, text.substring(3, 8), 3, 8));
            }
            return TextScanner.standard().scan(text);
        }
    }

    @Test
    void scheitertDieBerechnungBleibtDerVorigeZustandVollstaendigErhalten() {
        UmschaltbarerScanner scanner = new UmschaltbarerScanner();
        Sitzung s = new Sitzung(scanner, zwischenablage, dialoge);
        scannen(s, ALLE_STUFEN);
        s.anwaehlen(0, false);
        List<FundZeile> zeilen = s.zeilen();
        String text = s.textFuerKi();
        String zuordnung = s.statusleiste().zuordnung();
        String sitzungText = s.toString();
        assertFalse(zeilen.get(0).angewaehlt());
        dialoge.antwort = true;
        scanner.ueberlappend.set(true);

        scanFehlschlagen(s, ALLE_STUFEN);

        assertEquals(1, dialoge.fehler.size());
        assertEquals(ScanZustand.GUELTIG, s.scanZustand());
        assertEquals(zeilen, s.zeilen());
        assertEquals(text, s.textFuerKi());
        assertEquals(zuordnung, s.statusleiste().zuordnung());
        assertEquals(sitzungText, s.toString());
        s.alleAnwaehlen();
        assertTrue(s.zeilen().stream().allMatch(FundZeile::angewaehlt));
        assertNotEquals(text, s.textFuerKi());
    }

    // ---------------------------------------------------------------- Anzeige waehrend LAEUFT, Verwerfen (A-5)

    @Test
    void waehrendDesScansSindZeilenUndTextFuerKiLeerUndNachFehlschlagWiederDa() {
        scannen(sitzung, ALLE_STUFEN);
        List<FundZeile> zeilen = sitzung.zeilen();
        String text = sitzung.textFuerKi();
        dialoge.antwort = true;

        ScanAuftrag auftrag = sitzung.scanAnfordern(ALLE_STUFEN).orElseThrow();

        assertEquals(ScanZustand.LAEUFT, sitzung.scanZustand());
        assertEquals(List.of(), sitzung.zeilen());
        assertEquals("", sitzung.textFuerKi());

        sitzung.scanFehlgeschlagen(auftrag, new IllegalStateException());

        assertEquals(ScanZustand.GUELTIG, sitzung.scanZustand());
        assertEquals(zeilen, sitzung.zeilen());
        assertEquals(text, sitzung.textFuerKi());
    }

    @Test
    void abweichenderTextAusGueltigemScanFuehrtNachFehlschlagZuVeraltet() {
        scannen(sitzung, ALLE_STUFEN);
        String zuordnung = sitzung.statusleiste().zuordnung();
        dialoge.antwort = true;

        ScanAuftrag auftrag = sitzung.scanAnfordern("anderer Text " + MARKER).orElseThrow();
        assertEquals(List.of(), sitzung.zeilen());
        sitzung.scanFehlgeschlagen(auftrag, new IllegalStateException());

        assertEquals(ScanZustand.VERALTET, sitzung.scanZustand());
        assertEquals(List.of(), sitzung.zeilen());
        assertEquals("", sitzung.textFuerKi());
        assertEquals(zuordnung, sitzung.statusleiste().zuordnung());
        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.FUER_KI_KOPIEREN));
        assertEquals(1, dialoge.fehler.size());
    }

    @Test
    void abweichenderTextAusGueltigemScanFuehrtNachVerwerfenZuVeraltet() {
        scannen(sitzung, ALLE_STUFEN);
        String zuordnung = sitzung.statusleiste().zuordnung();
        dialoge.antwort = true;

        String neuerText = "anderer Text " + MARKER;
        ScanAuftrag auftrag = sitzung.scanAnfordern(neuerText).orElseThrow();
        sitzung.eingabeGeaendert();
        sitzung.scanAbgeschlossen(auftrag, auftrag.ausfuehren());

        assertEquals(ScanZustand.VERALTET, sitzung.scanZustand());
        assertEquals(List.of(), sitzung.zeilen());
        assertEquals("", sitzung.textFuerKi());
        assertEquals(zuordnung, sitzung.statusleiste().zuordnung());
        assertEquals(Texte.NEUER_SCAN_NOETIG, sitzung.statusleiste().meldung());
        assertEquals(List.of(), dialoge.fehler);
    }

    // ---------------------------------------------------------------- Nur der laufende Auftrag zaehlt

    @Test
    void rueckrufEinesAltenAuftragsWaehrendEinNeuerLaeuftAendertNichts() {
        String alterText = "Mail " + MARKER;
        ScanAuftrag alt = sitzung.scanAnfordern(alterText).orElseThrow();
        List<Finding> altErgebnis = alt.ausfuehren();
        sitzung.scanAbgeschlossen(alt, altErgebnis);
        dialoge.antwort = true;
        ScanAuftrag neu = sitzung.scanAnfordern("nur " + MARKER).orElseThrow();
        Statusleiste status = sitzung.statusleiste();

        sitzung.scanAbgeschlossen(alt, altErgebnis);
        sitzung.scanFehlgeschlagen(alt, new IllegalStateException());

        assertEquals(ScanZustand.LAEUFT, sitzung.scanZustand());
        assertEquals(status, sitzung.statusleiste());
        assertEquals(List.of(), dialoge.fehler);
        assertEquals(List.of(), sitzung.zeilen());

        sitzung.scanAbgeschlossen(neu, neu.ausfuehren());

        assertEquals(ScanZustand.GUELTIG, sitzung.scanZustand());
        assertEquals("nur [[EMAIL_1]]", sitzung.textFuerKi());
        assertEquals(1, sitzung.zeilen().size());
    }

    @Test
    void auftragEinerAnderenSitzungWirdIgnoriert() {
        Sitzung andere = Sitzung.standard(new TestZwischenablage(), new TestDialoge());
        ScanAuftrag fremd = andere.scanAnfordern(ALLE_STUFEN).orElseThrow();
        ScanAuftrag eigen = sitzung.scanAnfordern(ALLE_STUFEN).orElseThrow();

        sitzung.scanAbgeschlossen(fremd, fremd.ausfuehren());
        sitzung.scanFehlgeschlagen(fremd, new IllegalStateException());

        assertEquals(ScanZustand.LAEUFT, sitzung.scanZustand());
        assertEquals(List.of(), dialoge.fehler);
        sitzung.scanAbgeschlossen(eigen, eigen.ausfuehren());
        assertEquals(ScanZustand.GUELTIG, sitzung.scanZustand());
    }

    // ---------------------------------------------------------------- Zuordnungsanzeige (AK-40 Logikteil)

    @Test
    void zuordnungsanzeigeFolgtDemScanUndBleibtBeiAenderungDerEingabe() {
        assertEquals("Keine Zuordnung", sitzung.statusleiste().zuordnung());
        scannen(sitzung, "Mail " + MARKER);
        assertEquals("Zuordnung: 1 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
        sitzung.eingabeGeaendert();
        assertEquals("Zuordnung: 1 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
    }

    // ---------------------------------------------------------------- Beobachter, toString (AK-49 Vorarbeit)

    @Test
    void beobachterWirdNachZustandsaenderungenGerufen() {
        AtomicInteger aufrufe = new AtomicInteger();
        sitzung.beobachten(aufrufe::incrementAndGet);

        sitzung.scanAnfordern(" ");
        assertEquals(1, aufrufe.get());
        ScanAuftrag auftrag = sitzung.scanAnfordern(ALLE_STUFEN).orElseThrow();
        assertEquals(2, aufrufe.get());
        sitzung.scanAbgeschlossen(auftrag, auftrag.ausfuehren());
        assertEquals(3, aufrufe.get());
        sitzung.anwaehlen(0, false);
        assertEquals(4, aufrufe.get());
        sitzung.anwaehlen(0, false);
        assertEquals(4, aufrufe.get(), "unveraenderte Auswahl ist keine Aenderung");
    }

    @Test
    void toStringEnthaeltNurZustandUndZahlen() {
        scannen(sitzung, "Mail " + MARKER);

        assertEquals("Sitzung[zustand=GUELTIG, funde=1, zuordnung=1]", sitzung.toString());
        FundZeile zeile = sitzung.zeilen().get(0);
        assertFalse(zeile.toString().contains(MARKER));
        assertTrue(zeile.toString().contains("[[EMAIL_1]]"));
        assertFalse(sitzung.statusleiste().toString().contains(MARKER));
        assertFalse(sitzung.bedienzustand().toString().contains(MARKER));
    }

    // ---------------------------------------------------------------- Rueckfragen und Texte

    @Test
    void rueckfragenHabenDieWortlauteDerAnforderung() {
        assertEquals("Es ist kein Fund zum Ersetzen angewählt. Der Text wird unverändert kopiert. Fortfahren?",
                Rueckfrage.UNVERAENDERT_KOPIEREN.text());
        assertEquals("Die bisherige Zuordnung geht beim neuen Scan verloren. KI-Antworten zum vorigen Text können "
                + "dann nicht mehr zurückübersetzt werden. Fortfahren?", Rueckfrage.NEUER_SCAN.text());
        assertTrue(Rueckfrage.ZURUECKSETZEN.text().endsWith("Fortfahren?"));
        assertEquals("Beim Beenden geht die Zuordnung verloren. Danach können KI-Antworten nicht mehr "
                + "zurückübersetzt werden. Beenden?", Rueckfrage.BEENDEN.text());
    }

    private Sitzung gescannt(String eingabe) {
        Sitzung s = Sitzung.standard(new TestZwischenablage(), new TestDialoge());
        scannen(s, eingabe);
        return s;
    }
}
