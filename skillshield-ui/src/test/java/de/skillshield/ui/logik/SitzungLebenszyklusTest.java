package de.skillshield.ui.logik;

import static de.skillshield.ui.logik.Attrappen.DREI_MAILS;
import static de.skillshield.ui.logik.Attrappen.scannen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.ui.logik.Attrappen.TestDialoge;
import de.skillshield.ui.logik.Attrappen.TestZwischenablage;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Lebenszyklus (T-5): Rueckfragen vor neuem Scan, Zuruecksetzen und Beenden (AK-34, AK-41, AK-42, AK-43), das
 * Verwerfen allen Klartextes und die Zwischenablage-Regel beim Beenden (V-2).
 */
class SitzungLebenszyklusTest {

    private static final String TEXT_B = "c: carla@example.org, d: dora@example.org";
    private static final String CARLA = "carla@example.org";
    private static final String FREMD = "Etwas, das der Anwender selbst kopiert hat";

    private final TestZwischenablage zwischenablage = new TestZwischenablage();
    private final TestDialoge dialoge = new TestDialoge();
    private final Sitzung sitzung = Sitzung.standard(zwischenablage, dialoge);

    /** Alles, was die Oberflaeche von der Sitzung sehen kann, ausser der Meldung (die ist eigens geprueft). */
    private static List<Object> sichten(Sitzung s) {
        return List.of(s.scanZustand(), s.zeilen(), s.textFuerKi(), s.bedienzustand(),
                s.statusleiste().zuordnung(), s.rueckuebersetzung(), s.toString());
    }

    private Sitzung neueSitzung() {
        return Sitzung.standard(new TestZwischenablage(), new TestDialoge());
    }

    /** Scan, Kopieren, Rueckuebersetzen und Ergebnis kopieren: die Sitzung haelt Klartext in jeder Ecke. */
    private void vollBeladen() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        sitzung.kiAntwortGeaendert(false);
        sitzung.zurueckuebersetzen("[[EMAIL_1]] und [[EMAIL_2]]");
        sitzung.ergebnisKopieren();
        sitzung.anwaehlen(0, false);
    }

    private static Object feld(Sitzung s, String name) throws ReflectiveOperationException {
        Field f = Sitzung.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(s);
    }

    /** Vergleichstext der {@link ZwischenablageRegel} der Sitzung (per Reflexion). */
    private static Object letzterText(Sitzung s) throws ReflectiveOperationException {
        Object regel = feld(s, "zwischenablage");
        assertTrue(regel instanceof ZwischenablageRegel, "Feld zwischenablage ist die Regel");
        Field f = ZwischenablageRegel.class.getDeclaredField("letzterText");
        f.setAccessible(true);
        return f.get(regel);
    }

    /**
     * Die einzigen Felder von {@link Sitzung}, die beim Verwerfen nicht mit einer neuen Sitzung uebereinstimmen
     * muessen, je mit Begruendung. Jedes andere nicht statische Feld wird verglichen; ein neues Feld bricht den Test,
     * bis es hier eingeordnet oder (zurueckgesetzt) gleich ist.
     */
    private static final Map<String, String> AUSNAHMEN = Map.ofEntries(
            Map.entry("scanner", "Konstruktorargument, kein Zustand und kein Klartext"),
            Map.entry("zwischenablage", "Regel mit Vergleichstext; eigens in den Zwischenablage-Tests geprueft"),
            Map.entry("dialoge", "Konstruktorargument, kein Zustand"),
            Map.entry("pseudonymizer", "zustandsloser Kern-Baustein ohne Gedaechtnis zwischen Aufrufen"),
            Map.entry("restore", "Konstruktorargument, kein Zustand"),
            Map.entry("beobachter", "angemeldete Ansicht, Verdrahtung statt Zustand und ohne Klartext"),
            Map.entry("generation", "Zaehler, der absichtlich weiterlaeuft, damit alte Rueckrufe ungueltig bleiben"),
            Map.entry("meldung", "ohne Klartext, Wert je nach Zwischenablage; eigens geprueft"));

    private static Object normalisiert(Object wert) {
        return wert instanceof boolean[] bits ? Arrays.toString(bits) : wert;
    }

    private static void alleFelderWieNeu(Sitzung verworfen, Sitzung neu) throws ReflectiveOperationException {
        for (Field f : Sitzung.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic() || AUSNAHMEN.containsKey(f.getName())) {
                continue;
            }
            assertEquals(normalisiert(feld(neu, f.getName())), normalisiert(feld(verworfen, f.getName())),
                    "Feld nicht verworfen: " + f.getName());
        }
    }

    @Test
    void dieAusnahmelisteNenntNurExistierendeFelder() {
        Set<String> vorhanden = new HashSet<>();
        for (Field f : Sitzung.class.getDeclaredFields()) {
            vorhanden.add(f.getName());
        }
        assertTrue(vorhanden.containsAll(AUSNAHMEN.keySet()), "veraltete Ausnahme in der Liste");
        assertTrue(vorhanden.size() > AUSNAHMEN.size(), "es bleiben zu vergleichende Felder");
    }

    @Test
    void zuruecksetzenSetztAlleFelderWieBeiEinerNeuenSitzung() throws Exception {
        vollBeladen();
        dialoge.antwort = true;

        assertNotNull(letzterText(sitzung), "Vorbedingung: Vergleichstext vorhanden");
        assertTrue(sitzung.zuruecksetzenAnfordern());

        alleFelderWieNeu(sitzung, neueSitzung());
        assertNull(letzterText(sitzung), "Vergleichstext der Zwischenablage-Regel verworfen");
        assertEquals(Texte.BEREIT, feld(sitzung, "meldung"));
    }

    @Test
    void beendenSetztAlleFelderWieBeiEinerNeuenSitzung() throws Exception {
        vollBeladen();
        dialoge.antwort = true;

        assertNotNull(letzterText(sitzung), "Vorbedingung: Vergleichstext vorhanden");
        assertTrue(sitzung.beendenAnfordern());

        alleFelderWieNeu(sitzung, neueSitzung());
        assertNull(letzterText(sitzung), "Vergleichstext der Zwischenablage-Regel verworfen");
        assertEquals(Texte.BEREIT, feld(sitzung, "meldung"));
    }

    @Test
    void beendenMitLaufendemScanSetztAlleFelderWieBeiEinerNeuenSitzung() throws Exception {
        vollBeladen();
        dialoge.antwort = true;
        sitzung.scanAnfordern(TEXT_B).orElseThrow();

        assertNotNull(letzterText(sitzung), "Vorbedingung: Vergleichstext vorhanden");
        assertTrue(sitzung.beendenAnfordern());

        alleFelderWieNeu(sitzung, neueSitzung());
        assertNull(letzterText(sitzung), "Vergleichstext der Zwischenablage-Regel verworfen");
        assertEquals(Texte.BEREIT, feld(sitzung, "meldung"));
    }

    // ---------------------------------------------------------------- neuer Scan (AK-41)

    @Test
    void dieRueckfrageVorEinemNeuenScanKommtNurBeiMindestensEinemPlatzhalter() {
        scannen(sitzung, "Hallo Welt ohne Fund");
        scannen(sitzung, "Noch ein Text ohne Fund");
        assertEquals(List.of(), dialoge.fragen, "Zuordnung ohne Platzhalter: keine Rueckfrage");

        scannen(sitzung, DREI_MAILS);
        assertEquals(List.of(), dialoge.fragen, "auch beim Scan mit Funden noch keine Zuordnung mit Platzhaltern");
        dialoge.antwort = true;
        assertTrue(sitzung.scanAnfordern(TEXT_B).isPresent());
        assertEquals(List.of(Rueckfrage.NEUER_SCAN), dialoge.fragen);
    }

    @Test
    void abbrechenBeimNeuenScanAendertNichts() {
        vollBeladen();
        List<Object> vorher = sichten(sitzung);
        Statusleiste status = sitzung.statusleiste();
        dialoge.antwort = false;

        assertTrue(sitzung.scanAnfordern(TEXT_B).isEmpty());

        assertEquals(vorher, sichten(sitzung));
        assertEquals(status, sitzung.statusleiste());
    }

    @Test
    void nachNeuemScanSindDiePlatzhalterDesVorigenScansUnbekannt() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        dialoge.antwort = true;
        scannen(sitzung, TEXT_B);
        sitzung.fuerKiKopieren();

        sitzung.zurueckuebersetzen("[[EMAIL_1]] [[EMAIL_3]]");

        Rueckuebersetzung ergebnis = sitzung.rueckuebersetzung().orElseThrow();
        assertEquals(CARLA + " [[EMAIL_3]]", ergebnis.text());
        assertEquals(1, ergebnis.offen().size());
    }

    @Test
    void dasErgebnisEinesAltenScansIstNachEinemNeuenScanNichtMehrKopierbar() {
        vollBeladen();
        assertTrue(sitzung.bedienzustand().istAktiv(Funktion.ERGEBNIS_KOPIEREN));
        dialoge.antwort = true;

        scannen(sitzung, TEXT_B);

        assertTrue(sitzung.rueckuebersetzung().isEmpty(), "Ergebnis mit Originalwerten des alten Scans verworfen");
        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.ERGEBNIS_KOPIEREN));
        assertEquals(Texte.ERST_ZURUECKUEBERSETZEN,
                sitzung.bedienzustand().sperrgrund().get(Funktion.ERGEBNIS_KOPIEREN));
        int gesetzt = zwischenablage.gesetzt.size();
        sitzung.ergebnisKopieren();
        assertEquals(gesetzt, zwischenablage.gesetzt.size(), "kein Kopieren des alten Ergebnisses");
    }

    @Test
    void einAbgebrochenerOderGescheiterterScanBehaeltDasErgebnis() {
        vollBeladen();
        dialoge.antwort = false;
        assertTrue(sitzung.scanAnfordern(TEXT_B).isEmpty());
        assertTrue(sitzung.rueckuebersetzung().isPresent(), "abgebrochen: nichts geaendert");

        dialoge.antwort = true;
        ScanAuftrag auftrag = sitzung.scanAnfordern(TEXT_B).orElseThrow();
        sitzung.scanFehlgeschlagen(auftrag, new IllegalStateException());

        assertTrue(sitzung.rueckuebersetzung().isPresent(), "gescheitert: wie vor dem Scan (AK-50)");
        assertTrue(sitzung.bedienzustand().istAktiv(Funktion.ERGEBNIS_KOPIEREN));
    }

    // ---------------------------------------------------------------- Zuruecksetzen (AK-42)

    @Test
    void zuruecksetzenOhneZuordnungFragtNichtUndStelltDenStartzustandHer() {
        sitzung.kiAntwortGeaendert(false);
        assertTrue(sitzung.zuruecksetzenAnfordern());

        assertEquals(List.of(), dialoge.fragen);
        assertEquals(sichten(neueSitzung()), sichten(sitzung));
        assertEquals(Texte.BEREIT, sitzung.statusleiste().meldung());
    }

    @Test
    void zuruecksetzenMitPlatzhalternFragtMitDerRueckfrageZuruecksetzen() {
        scannen(sitzung, DREI_MAILS);
        dialoge.antwort = true;

        assertTrue(sitzung.zuruecksetzenAnfordern());

        assertEquals(List.of(Rueckfrage.ZURUECKSETZEN), dialoge.fragen);
    }

    @Test
    void abbrechenBeimZuruecksetzenAendertNichtsUndLeertDieZwischenablageNicht() {
        vollBeladen();
        List<Object> vorher = sichten(sitzung);
        Statusleiste status = sitzung.statusleiste();
        int zugriffe = zwischenablage.zugriffe;
        String inhalt = zwischenablage.inhalt;
        dialoge.antwort = false;

        assertFalse(sitzung.zuruecksetzenAnfordern());

        assertEquals(List.of(Rueckfrage.ZURUECKSETZEN), dialoge.fragen);
        assertEquals(vorher, sichten(sitzung));
        assertEquals(status, sitzung.statusleiste());
        assertEquals(zugriffe, zwischenablage.zugriffe);
        assertEquals(inhalt, zwischenablage.inhalt);
    }

    @Test
    void zuruecksetzenVerwirftAllenKlartextUndStelltDenStartzustandHer() throws Exception {
        vollBeladen();
        dialoge.antwort = true;

        assertTrue(sitzung.zuruecksetzenAnfordern());

        assertEquals(sichten(neueSitzung()), sichten(sitzung));
        assertEquals(new Statusleiste(Texte.BEREIT, Texte.KEINE_ZUORDNUNG), sitzung.statusleiste());
        assertNull(feld(sitzung, "zuordnung"));
        assertNull(feld(sitzung, "zuletztKopierteZuordnung"));
        assertNull(feld(sitzung, "rueckuebersetzung"));
        assertNull(feld(sitzung, "laufenderAuftrag"));
        assertEquals("", feld(sitzung, "text"));
        assertEquals(List.of(), feld(sitzung, "funde"));
        assertEquals("Live[zeichen=0, platzhalter=0]", String.valueOf(feld(sitzung, "live")));
        assertEquals(Set.of(Funktion.SCANNEN, Funktion.EINGABE_BEARBEITEN, Funktion.ZURUECKSETZEN, Funktion.BEENDEN),
                sitzung.bedienzustand().aktiv());
    }

    @Test
    void nachDemZuruecksetzenGiltDerHinweisPlatzhalterGeaendertNichtMehr() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        dialoge.antwort = true;
        assertTrue(sitzung.zuruecksetzenAnfordern());

        scannen(sitzung, "b: bernd@example.org");
        sitzung.fuerKiKopieren();

        assertFalse(sitzung.statusleiste().meldung().contains(Texte.PLATZHALTER_GEAENDERT),
                "die frueher kopierte Zuordnung (mit Originalwerten) wurde verworfen");
    }

    @Test
    void zuruecksetzenWaehrendDesScansIstGesperrtUndTutNichts() {
        scannen(sitzung, DREI_MAILS);
        dialoge.antwort = true;
        sitzung.scanAnfordern(TEXT_B).orElseThrow();
        List<Object> vorher = sichten(sitzung);

        assertFalse(sitzung.zuruecksetzenAnfordern());

        assertEquals(vorher, sichten(sitzung));
        assertEquals(List.of(Rueckfrage.NEUER_SCAN), dialoge.fragen, "keine weitere Rueckfrage");
    }

    @Test
    void zuruecksetzenBenachrichtigtDieBeobachter() {
        scannen(sitzung, DREI_MAILS);
        int[] aufrufe = {0};
        sitzung.beobachten(() -> aufrufe[0]++);
        dialoge.antwort = true;

        sitzung.zuruecksetzenAnfordern();

        assertEquals(1, aufrufe[0]);
    }

    // ---------------------------------------------------------------- Zuruecksetzen und Zwischenablage (AK-34)

    @Test
    void zuruecksetzenLeertEigenenInhaltOhneDialogUndMeldetBereit() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        dialoge.antwort = true;

        sitzung.zuruecksetzenAnfordern();

        assertNull(zwischenablage.inhalt);
        assertEquals(Texte.BEREIT, sitzung.statusleiste().meldung());
        assertEquals(List.of(), dialoge.fehler);
        assertEquals(List.of(), dialoge.erneutFragen);
    }

    @Test
    void zuruecksetzenLaesstFremdenInhaltUnberuehrt() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        zwischenablage.inhalt = FREMD;
        dialoge.antwort = true;

        sitzung.zuruecksetzenAnfordern();

        assertEquals(FREMD, zwischenablage.inhalt);
        assertEquals(0, zwischenablage.leerAufrufe);
        assertEquals(Texte.BEREIT, sitzung.statusleiste().meldung());
    }

    @Test
    void zuruecksetzenOhneVorherigesAblegenGreiftNichtAufDieZwischenablageZu() {
        scannen(sitzung, DREI_MAILS);
        zwischenablage.inhalt = FREMD;
        dialoge.antwort = true;

        sitzung.zuruecksetzenAnfordern();

        assertEquals(0, zwischenablage.zugriffe);
        assertEquals(FREMD, zwischenablage.inhalt);
    }

    @Test
    void scheitertDasLeerenBeimZuruecksetzenSagtDieMeldungEsOhneDialogUndDerVergleichstextBleibt() {
        sitzung.textKopieren("Ausschnitt");
        zwischenablage.nurLeerenGesperrt = true;

        assertTrue(sitzung.zuruecksetzenAnfordern());

        assertEquals(Texte.ZWISCHENABLAGE_NICHT_GELEERT, sitzung.statusleiste().meldung());
        assertEquals(sichten(neueSitzung()), sichten(sitzung), "sonst Startzustand");
        assertEquals(List.of(), dialoge.fehler);
        assertEquals(List.of(), dialoge.erneutFragen);
        assertEquals("Ausschnitt", zwischenablage.inhalt);
        // Der Vergleichstext blieb: ein spaeterer Versuch leert.
        zwischenablage.nurLeerenGesperrt = false;
        assertTrue(sitzung.zuruecksetzenAnfordern());
        assertNull(zwischenablage.inhalt);
        assertEquals(Texte.BEREIT, sitzung.statusleiste().meldung());
    }

    @Test
    void istDieZwischenablageBeimZuruecksetzenNichtLesbarSagtDieMeldungEsAuch() {
        sitzung.textKopieren("Ausschnitt");
        zwischenablage.nichtLesbar = true;

        sitzung.zuruecksetzenAnfordern();

        assertEquals(0, zwischenablage.leerAufrufe, "Zuruecksetzen leert nur nachweislich Eigenes");
        assertEquals(Texte.ZWISCHENABLAGE_NICHT_GELEERT, sitzung.statusleiste().meldung());
    }

    // ---------------------------------------------------------------- Beenden (AK-43)

    @Test
    void beendenOhneZuordnungBeendetOhneRueckfrage() {
        assertTrue(sitzung.beendenAnfordern());

        assertEquals(List.of(), dialoge.fragen);
        assertEquals(List.of(), dialoge.erneutFragen);
        assertEquals(0, zwischenablage.zugriffe, "nie etwas abgelegt: kein Zugriff auf die Zwischenablage");
    }

    @Test
    void beendenNachScanOhnePlatzhalterBeendetOhneRueckfrage() {
        scannen(sitzung, "Hallo Welt ohne Fund");

        assertTrue(sitzung.beendenAnfordern());

        assertEquals(List.of(), dialoge.fragen);
    }

    @Test
    void beendenMitPlatzhalternFragtMitDerRueckfrageBeenden() {
        scannen(sitzung, DREI_MAILS);
        dialoge.antwort = true;

        assertTrue(sitzung.beendenAnfordern());

        assertEquals(List.of(Rueckfrage.BEENDEN), dialoge.fragen);
    }

    @Test
    void abbrechenBeimBeendenAendertNichtsUndFasstDieZwischenablageNichtAn() {
        vollBeladen();
        List<Object> vorher = sichten(sitzung);
        Statusleiste status = sitzung.statusleiste();
        int zugriffe = zwischenablage.zugriffe;
        dialoge.antwort = false;

        assertFalse(sitzung.beendenAnfordern());

        assertEquals(vorher, sichten(sitzung));
        assertEquals(status, sitzung.statusleiste());
        assertEquals(zugriffe, zwischenablage.zugriffe);
        assertEquals(List.of(), dialoge.erneutFragen);
    }

    @Test
    void beendenVerwirftAllenKlartext() throws Exception {
        vollBeladen();
        dialoge.antwort = true;

        assertTrue(sitzung.beendenAnfordern());

        assertEquals(sichten(neueSitzung()), sichten(sitzung));
        assertNull(feld(sitzung, "zuordnung"));
        assertNull(feld(sitzung, "zuletztKopierteZuordnung"));
        assertNull(feld(sitzung, "rueckuebersetzung"));
        assertEquals(List.of(), feld(sitzung, "funde"));
        assertEquals("", feld(sitzung, "text"));
        assertNull(zwischenablage.inhalt, "eigener Klartext geleert");
    }

    @Test
    void beendenWaehrendDesScansVerwirftDenLaufendenAuftrag() {
        ScanAuftrag auftrag = sitzung.scanAnfordern(DREI_MAILS).orElseThrow();
        assertEquals(ScanZustand.LAEUFT, sitzung.scanZustand());

        assertTrue(sitzung.beendenAnfordern());
        sitzung.scanAbgeschlossen(auftrag, auftrag.ausfuehren());

        assertEquals(ScanZustand.KEIN_SCAN, sitzung.scanZustand());
        assertEquals(List.of(), sitzung.zeilen());
        assertEquals(sichten(neueSitzung()), sichten(sitzung));
    }

    @Test
    void beendenIstAuchWaehrendDesScansBedienbar() {
        sitzung.scanAnfordern(DREI_MAILS).orElseThrow();

        assertTrue(sitzung.bedienzustand().istAktiv(Funktion.BEENDEN));
    }

    // ---------------------------------------------------------------- Beenden und Zwischenablage (V-2)

    @Test
    void beimBeendenWirdAuchEinUnbekannterInhaltGeleertWennEtwasAbgelegtWurde() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        zwischenablage.nichtLesbar = true;
        dialoge.antwort = true;

        assertTrue(sitzung.beendenAnfordern());

        assertNull(zwischenablage.inhalt);
        assertEquals(1, zwischenablage.leerAufrufe);
        assertEquals(List.of(), dialoge.erneutFragen);
    }

    @Test
    void beimBeendenWirdEigenerLesbarerInhaltGeleert() {
        sitzung.textKopieren("Ausschnitt");

        assertTrue(sitzung.beendenAnfordern());

        assertNull(zwischenablage.inhalt);
        assertEquals(1, zwischenablage.leerAufrufe);
    }

    @Test
    void beimBeendenBleibtNachweislichFremderInhaltUnberuehrt() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        zwischenablage.inhalt = FREMD;
        dialoge.antwort = true;

        assertTrue(sitzung.beendenAnfordern());

        assertEquals(FREMD, zwischenablage.inhalt);
        assertEquals(0, zwischenablage.leerAufrufe);
        assertEquals(List.of(), dialoge.erneutFragen);
    }

    @Test
    void scheitertDasLeernBeimBeendenFragtDasProgrammMitFestemTextOhneInhalt() {
        sitzung.textKopieren("Ausschnitt");
        zwischenablage.nurLeerenGesperrt = true;

        assertTrue(sitzung.beendenAnfordern());

        assertEquals(List.of(Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT), dialoge.erneutFragen);
        assertEquals("Die Zwischenablage konnte nicht geleert werden. "
                + "Sie kann noch Text aus SkillShield enthalten.", Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT.text());
        assertFalse(Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT.text().contains("Ausschnitt"));
        assertEquals("Erneut versuchen", Texte.ERNEUT_VERSUCHEN);
        assertEquals("Trotzdem beenden", Texte.TROTZDEM_BEENDEN);
    }

    @Test
    void erneutVersuchenLeertBeiFreiGewordenerZwischenablageUndBeendet() {
        sitzung.textKopieren("Ausschnitt");
        zwischenablage.nurLeerenGesperrt = true;
        dialoge.erneutAntworten.add(true);
        dialoge.beiErneutFrage = () -> zwischenablage.nurLeerenGesperrt = false;

        assertTrue(sitzung.beendenAnfordern());

        assertEquals(1, dialoge.erneutFragen.size());
        assertNull(zwischenablage.inhalt);
        assertEquals(2, zwischenablage.leerAufrufe);
    }

    @Test
    void dieZahlDerErneutenVersucheIstNichtBegrenzt() {
        sitzung.textKopieren("Ausschnitt");
        zwischenablage.nurLeerenGesperrt = true;
        for (int i = 0; i < 5; i++) {
            dialoge.erneutAntworten.add(true);
        }
        dialoge.beiErneutFrage = () -> {
            if (dialoge.erneutFragen.size() == 5) {
                zwischenablage.nurLeerenGesperrt = false;
            }
        };

        assertTrue(sitzung.beendenAnfordern());

        assertEquals(5, dialoge.erneutFragen.size());
        assertEquals(6, zwischenablage.leerAufrufe);
        assertNull(zwischenablage.inhalt);
    }

    @Test
    void trotzdemBeendenBeendetAuchWennDieZwischenablageNichtGeleertWurdeUndVerwirftAllesAndere() {
        vollBeladen();
        zwischenablage.nurLeerenGesperrt = true;
        dialoge.antwort = true;

        assertTrue(sitzung.beendenAnfordern());

        assertEquals(1, dialoge.erneutFragen.size());
        assertEquals(sichten(neueSitzung()), sichten(sitzung));
        int zugriffe = zwischenablage.zugriffe;
        assertTrue(sitzung.beendenAnfordern());
        assertEquals(zugriffe, zwischenablage.zugriffe, "der Vergleichstext ist verworfen: kein weiterer Zugriff");
        assertEquals(1, dialoge.erneutFragen.size());
    }

    @Test
    void auchEineAusnahmeDerZwischenablageBeimLeerenFuehrtZurRueckfrageOhneAusnahmetext() {
        Zwischenablage kaputt = new Zwischenablage() {
            @Override
            public boolean setzeText(String text) {
                return true;
            }

            @Override
            public Optional<String> leseText() {
                throw new IllegalStateException("GEHEIM-MARKER");
            }

            @Override
            public boolean leeren() {
                throw new IllegalStateException("GEHEIM-MARKER");
            }
        };
        Sitzung s = Sitzung.standard(kaputt, dialoge);
        s.textKopieren("Ausschnitt");

        assertTrue(s.beendenAnfordern());

        assertEquals(List.of(Rueckfrage.ZWISCHENABLAGE_NICHT_GELEERT), dialoge.erneutFragen);
        assertEquals(List.of(), dialoge.fehler);
    }

    @Test
    void dieReihenfolgeBeimBeendenIstRueckfrageDannZwischenablageDannVerwerfen() {
        List<String> ablauf = new ArrayList<>();
        Sitzung[] ziel = new Sitzung[1];
        int[] zeilenBeimLeeren = {-1};
        Zwischenablage protokoll = new Zwischenablage() {
            @Override
            public boolean setzeText(String text) {
                return true;
            }

            @Override
            public Optional<String> leseText() {
                ablauf.add("lesen");
                return Optional.empty();
            }

            @Override
            public boolean leeren() {
                ablauf.add("leeren");
                zeilenBeimLeeren[0] = ziel[0].zeilen().size();
                return true;
            }
        };
        Dialoge fragend = new Dialoge() {
            @Override
            public boolean bestaetigt(Rueckfrage frage) {
                ablauf.add("frage");
                return true;
            }

            @Override
            public boolean erneutVersuchen(Rueckfrage frage) {
                return false;
            }

            @Override
            public void fehler(String text) {
            }
        };
        Sitzung s = Sitzung.standard(protokoll, fragend);
        ziel[0] = s;
        scannen(s, DREI_MAILS);
        s.textKopieren("Ausschnitt");

        assertTrue(s.beendenAnfordern());

        assertEquals(List.of("frage", "lesen", "leeren"), ablauf);
        assertTrue(zeilenBeimLeeren[0] > 0, "beim Leeren war noch nichts verworfen");
        assertEquals(0, s.zeilen().size());
    }
}
