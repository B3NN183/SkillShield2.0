package de.skillshield.ui.logik;

import static de.skillshield.ui.logik.Attrappen.ALLE_STUFEN;
import static de.skillshield.ui.logik.Attrappen.DREI_MAILS;
import static de.skillshield.ui.logik.Attrappen.scannen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.pseudonymize.PlaceholderMapping;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import de.skillshield.core.reidentify.UnresolvedPlaceholder;
import de.skillshield.ui.logik.Attrappen.TestDialoge;
import de.skillshield.ui.logik.Attrappen.TestZwischenablage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Bereich 4: Aktivierung, Rueckuebersetzung, Liste "Nicht zurueckuebersetzt", Ergebnis kopieren (AK-36 bis AK-39). */
class SitzungRueckuebersetzungTest {

    private static final String ANNA = "anna@example.org";
    private static final String BERND = "bernd@example.org";

    private final TestZwischenablage zwischenablage = new TestZwischenablage();
    private final TestDialoge dialoge = new TestDialoge();
    private final Sitzung sitzung = Sitzung.standard(zwischenablage, dialoge);

    private static PlaceholderMapping mapping(String text, Sitzung s) {
        List<Finding> funde = s.zeilen().stream().filter(FundZeile::angewaehlt).map(FundZeile::fund).toList();
        return new Pseudonymizer().pseudonymize(text, funde).mapping();
    }

    /** Uebersetzt zurueck; kopiert vorher fuer die KI, falls im aktuellen Scan noch nicht kopiert wurde (S-2). */
    private Rueckuebersetzung zurueck(String antwort) {
        if (Texte.ZUORDNUNG_ERST_NACH_KOPIEREN.equals(sitzung.statusleiste().zuordnung())) {
            sitzung.fuerKiKopieren();
        }
        sitzung.kiAntwortGeaendert(antwort.isEmpty());
        sitzung.zurueckuebersetzen(antwort);
        return sitzung.rueckuebersetzung().orElseThrow();
    }

    // ---------------------------------------------------------------- AK-36: Aktivierung

    @Test
    void zurueckuebersetzenIstErstNachEinemScanUndMitNichtLeererAntwortAktiv() {
        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.ZURUECKUEBERSETZEN));
        assertEquals(Texte.ERST_SCANNEN, sitzung.bedienzustand().sperrgrund().get(Funktion.ZURUECKUEBERSETZEN));
        sitzung.kiAntwortGeaendert(false);
        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.ZURUECKUEBERSETZEN), "ohne Scan keine Zuordnung");

        scannen(sitzung, DREI_MAILS);
        sitzung.kiAntwortGeaendert(false);
        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.ZURUECKUEBERSETZEN), "erst kopieren (S-2)");
        assertEquals(Texte.ERST_KOPIEREN, sitzung.bedienzustand().sperrgrund().get(Funktion.ZURUECKUEBERSETZEN));
        sitzung.fuerKiKopieren();
        sitzung.kiAntwortGeaendert(true);
        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.ZURUECKUEBERSETZEN));
        assertEquals(Texte.ANTWORT_FEHLT, sitzung.bedienzustand().sperrgrund().get(Funktion.ZURUECKUEBERSETZEN));

        sitzung.kiAntwortGeaendert(false);
        assertTrue(sitzung.bedienzustand().istAktiv(Funktion.ZURUECKUEBERSETZEN));

        sitzung.kiAntwortGeaendert(true);
        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.ZURUECKUEBERSETZEN));
    }

    @Test
    void auchNachEinemScanOhneFundIstEineZuordnungVorhandenUndZurueckuebersetzenAktiv() {
        scannen(sitzung, "Ein Text ohne erkennbare Stelle.");
        sitzung.kiAntwortGeaendert(false);
        dialoge.antwort = true;
        sitzung.fuerKiKopieren();

        assertTrue(sitzung.bedienzustand().istAktiv(Funktion.ZURUECKUEBERSETZEN));
        assertEquals("Zuordnung: 0 Platzhalter (nur im Arbeitsspeicher)", sitzung.statusleiste().zuordnung());
    }

    @Test
    void waehrendDesScansIstZurueckuebersetzenGesperrt() {
        scannen(sitzung, DREI_MAILS);
        sitzung.kiAntwortGeaendert(false);
        dialoge.antwort = true;
        sitzung.scanAnfordern("Neuer Text").orElseThrow();

        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.ZURUECKUEBERSETZEN));
        assertEquals(Texte.SCAN_LAEUFT, sitzung.bedienzustand().sperrgrund().get(Funktion.ZURUECKUEBERSETZEN));
        sitzung.zurueckuebersetzen("[[EMAIL_1]]");
        assertTrue(sitzung.rueckuebersetzung().isEmpty());
    }

    @Test
    void beiLeererAntwortMeldetDieStatusleisteDenFesttext() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();

        sitzung.zurueckuebersetzen("");

        assertEquals("Bitte die Antwort der KI einfügen.", sitzung.statusleiste().meldung());
        assertTrue(sitzung.rueckuebersetzung().isEmpty());
    }

    @Test
    void ohneZuordnungTutZurueckuebersetzenNichts() {
        sitzung.zurueckuebersetzen("[[EMAIL_1]]");

        assertTrue(sitzung.rueckuebersetzung().isEmpty());
        assertEquals(Texte.BEREIT, sitzung.statusleiste().meldung());
    }

    // ---------------------------------------------------------------- AK-37: Ergebnis

    @Test
    void dasErgebnisIstIdentischMitDemReidentifierDesKerns() {
        scannen(sitzung, DREI_MAILS);
        String antwort = "Hallo " + sitzung.textFuerKi() + " Danke, [[EMAIL_2]] bleibt.";

        Rueckuebersetzung ergebnis = zurueck(antwort);

        ReidentificationResult kern = new Reidentifier().restore(antwort, mapping(DREI_MAILS, sitzung));
        assertEquals(kern.text(), ergebnis.text());
        assertEquals(kern.unresolved(), ergebnis.offen());
    }

    @Test
    void roundtripDesUnveraendertenTextesLiefertDieEingabeZeichengenau() {
        scannen(sitzung, ALLE_STUFEN);
        String kopiert = sitzung.textFuerKi();
        assertFalse(kopiert.equals(ALLE_STUFEN), "Vorbedingung: es wurde ersetzt");

        sitzung.fuerKiKopieren();
        assertEquals(kopiert, zwischenablage.inhalt);
        assertEquals(ALLE_STUFEN, zurueck(zwischenablage.inhalt).text(), "nach dem Kopieren");
        assertEquals("Alle Platzhalter wurden zurückübersetzt.", sitzung.statusleiste().meldung());
        assertEquals(List.of("Alle Platzhalter wurden zurückübersetzt."),
                sitzung.rueckuebersetzung().orElseThrow().listenzeilen());
    }

    // ---------------------------------------------------------------- AK-38: Liste und Meldung

    @Test
    void eineAntwortOhnePlatzhalterNenntDenSammeltextDerKeinePlatzhalterSagt() {
        scannen(sitzung, DREI_MAILS);

        Rueckuebersetzung ergebnis = zurueck("Ganz ohne Platzhalter.");

        assertEquals("Ganz ohne Platzhalter.", ergebnis.text());
        assertEquals(List.of("Die Antwort enthält keine Platzhalter."), ergebnis.listenzeilen());
        assertEquals("Die Antwort enthält keine Platzhalter.", sitzung.statusleiste().meldung());
    }

    @Test
    void einUnbekannterPlatzhalterBleibtStehenUndStehtMitPositionAbEinsInDerListe() {
        scannen(sitzung, DREI_MAILS);
        String antwort = "Hallo [[EMAIL_9999]] und [[EMAIL_1]].";

        Rueckuebersetzung ergebnis = zurueck(antwort);

        assertEquals("Hallo [[EMAIL_9999]] und " + ANNA + ".", ergebnis.text());
        assertEquals(List.of("[[EMAIL_9999]] an Position 7: unbekannt"), ergebnis.listenzeilen());
        assertEquals(2, ergebnis.platzhalterGesamt());
        assertEquals("1 Platzhalter konnte nicht zurückübersetzt werden.", sitzung.statusleiste().meldung());
    }

    @Test
    void einBeschaedigterPlatzhalterBleibtStehenUndWirdAlsBeschaedigtGemeldet() {
        scannen(sitzung, DREI_MAILS);
        String antwort = "Hallo [[EMAIL_1] und [[EMAIL_2]] und [[EMAIL_7";

        Rueckuebersetzung ergebnis = zurueck(antwort);

        assertEquals(2, ergebnis.offen().size());
        assertEquals(UnresolvedPlaceholder.Reason.BESCHAEDIGT, ergebnis.offen().get(0).reason());
        assertTrue(ergebnis.text().startsWith("Hallo [[EMAIL_1] und " + BERND));
        List<String> zeilen = ergebnis.listenzeilen();
        assertEquals("[[EMAIL_1] an Position 7: beschädigt", zeilen.get(0));
        assertTrue(zeilen.get(1).endsWith(": beschädigt"), zeilen.get(1));
        assertEquals("2 Platzhalter konnten nicht zurückübersetzt werden.", sitzung.statusleiste().meldung());
    }

    @Test
    void ohneZuordnungsEintraegeSindAllePlatzhalterUnbekanntNachEinemScanOhneFund() {
        scannen(sitzung, "Ein Text ohne erkennbare Stelle.");
        dialoge.antwort = true;

        Rueckuebersetzung ergebnis = zurueck("[[EMAIL_1]]");

        assertEquals("[[EMAIL_1]]", ergebnis.text());
        assertEquals(List.of("[[EMAIL_1]] an Position 1: unbekannt"), ergebnis.listenzeilen());
    }

    @Test
    void fehlenderUndDoppelterPlatzhalterSindKeinFehler() {
        scannen(sitzung, DREI_MAILS);

        assertEquals(BERND, zurueck("[[EMAIL_2]]").text());
        assertEquals("Alle Platzhalter wurden zurückübersetzt.", sitzung.statusleiste().meldung());
        assertEquals(ANNA + " " + ANNA, zurueck("[[EMAIL_1]] [[EMAIL_1]]").text());
        assertEquals("kein Platzhalter", zurueck("kein Platzhalter").text());
    }

    @Test
    void auchDieVomKernToleriertenSchreibweisenWerdenZurueckuebersetzt() {
        scannen(sitzung, DREI_MAILS);

        Rueckuebersetzung ergebnis = zurueck("[[email_1]] / [[ EMAIL_2 ]]");

        assertEquals(ANNA + " / " + BERND, ergebnis.text());
        assertTrue(ergebnis.offen().isEmpty());
    }

    @Test
    void nachEinemNeuenScanSindDiePlatzhalterDesVorigenScansUnbekannt() {
        scannen(sitzung, DREI_MAILS);
        dialoge.antwort = true;
        scannen(sitzung, "c: carla@example.org");

        Rueckuebersetzung ergebnis = zurueck("[[EMAIL_1]] [[EMAIL_2]]");

        assertEquals("carla@example.org [[EMAIL_2]]", ergebnis.text());
        assertEquals(List.of("[[EMAIL_2]] an Position 13: unbekannt"), ergebnis.listenzeilen());
    }

    @Test
    void dieMeldungenEnthaltenKeinenOriginalwert() {
        scannen(sitzung, DREI_MAILS);
        zurueck("[[EMAIL_1]] [[EMAIL_9]] [[EMAIL_2");

        assertFalse(sitzung.statusleiste().meldung().contains("example.org"));
        assertFalse(sitzung.toString().contains("example.org"));
        assertFalse(sitzung.rueckuebersetzung().orElseThrow().toString().contains("example.org"));
        assertFalse(sitzung.bedienzustand().toString().contains("example.org"));
    }

    // ---------------------------------------------------------------- AK-39: Ergebnis kopieren

    @Test
    void ergebnisKopierenIstVorDerRueckuebersetzungGesperrtUndDanachAktiv() {
        scannen(sitzung, DREI_MAILS);
        assertFalse(sitzung.bedienzustand().istAktiv(Funktion.ERGEBNIS_KOPIEREN));
        assertEquals(Texte.ERST_ZURUECKUEBERSETZEN,
                sitzung.bedienzustand().sperrgrund().get(Funktion.ERGEBNIS_KOPIEREN));
        sitzung.ergebnisKopieren();
        assertEquals(0, zwischenablage.zugriffe);

        zurueck("[[EMAIL_1]]");
        zwischenablage.gesetzt.clear();

        assertTrue(sitzung.bedienzustand().istAktiv(Funktion.ERGEBNIS_KOPIEREN));
        sitzung.ergebnisKopieren();
        assertEquals(List.of(ANNA), zwischenablage.gesetzt);
        assertEquals("Rückübersetzter Text kopiert.", sitzung.statusleiste().meldung());
    }

    @Test
    void ergebnisKopierenLegtGenauDenErgebnistextAb() {
        scannen(sitzung, ALLE_STUFEN);
        Rueckuebersetzung ergebnis = zurueck(sitzung.textFuerKi());
        zwischenablage.gesetzt.clear();

        sitzung.ergebnisKopieren();

        assertEquals(List.of(ergebnis.text()), zwischenablage.gesetzt);
        assertEquals(ALLE_STUFEN, zwischenablage.inhalt);
    }

    @Test
    void nimmtDieZwischenablageDasErgebnisNichtAnWirdEsGemeldet() {
        scannen(sitzung, DREI_MAILS);
        zurueck("[[EMAIL_1]]");
        zwischenablage.gesperrt = true;

        sitzung.ergebnisKopieren();

        assertEquals(Texte.ZWISCHENABLAGE_NICHT_VERFUEGBAR, sitzung.statusleiste().meldung());
        assertEquals(List.of(Texte.ZWISCHENABLAGE_NICHT_VERFUEGBAR), dialoge.fehler);
    }

    @Test
    void dieFeldAenderungDerAntwortLaesstDasLetzteErgebnisStehen() {
        scannen(sitzung, DREI_MAILS);
        Rueckuebersetzung ergebnis = zurueck("[[EMAIL_1]]");

        sitzung.kiAntwortGeaendert(true);

        assertEquals(ergebnis, sitzung.rueckuebersetzung().orElseThrow());
    }


    // ---------------------------------------------------------------- Eigenschaftstest gegen den Kern

    private static final long SEED = 20_260_411L;
    private static final String[] WOERTER = {"Hallo", "Danke", "Bitte", "Ende"};

    private enum Art { KANONISCH, KLEIN, MIT_LEERZEICHEN, UNBEKANNT, BESCHAEDIGT, TEXT }

    /** Synthetischer Eingabetext aus E-Mails, IP-Adressen des Dokumentationsnetzes und Woertern. */
    private static String zufallsText(Random zufall) {
        StringBuilder text = new StringBuilder();
        int teile = 2 + zufall.nextInt(6);
        for (int i = 0; i < teile; i++) {
            switch (zufall.nextInt(3)) {
                case 0 -> text.append("n").append(zufall.nextInt(4)).append("@example.org");
                case 1 -> text.append("192.0.2.").append(1 + zufall.nextInt(4));
                default -> text.append(WOERTER[zufall.nextInt(WOERTER.length)]);
            }
            text.append(' ');
        }
        return text.toString();
    }

    @Test
    void zufaelligVeraenderteAntwortenErgebenDasselbeWieDerKern() {
        Random zufall = new Random(SEED);
        for (int lauf = 0; lauf < 300; lauf++) {
            String text = zufallsText(zufall);
            TestDialoge d = new TestDialoge();
            d.antwort = true;
            Sitzung s = Sitzung.standard(new TestZwischenablage(), d);
            scannen(s, text);
            for (int i = 0; i < s.zeilen().size(); i++) {
                if (zufall.nextInt(5) == 0) {
                    s.anwaehlen(i, false);
                }
            }
            // Zurueckuebersetzt wird nur nach dem Kopieren (S-2).
            s.fuerKiKopieren();
            String kopierterText = s.textFuerKi();
            PlaceholderMapping mapping = mapping(text, s);
            if (!s.zeilen().isEmpty() && zufall.nextBoolean()) {
                // Auswahl nach dem Kopieren geaendert: die eingefrorene Zuordnung muss weiter gelten (A-2).
                int zeile = zufall.nextInt(s.zeilen().size());
                s.anwaehlen(zeile, !s.zeilen().get(zeile).angewaehlt());
            }

            // Roundtrip des kopierten, unveraenderten Textes: zeichengenau die Eingabe
            s.zurueckuebersetzen(kopierterText);
            assertEquals(text, s.rueckuebersetzung().orElseThrow().text(), "Roundtrip, Lauf " + lauf);

            // Veraenderte Antwort aus Bausteinen mit unabhaengig berechnetem Erwartungswert
            List<String> platzhalter = new ArrayList<>(mapping.placeholders());
            StringBuilder antwort = new StringBuilder();
            StringBuilder erwartet = new StringBuilder();
            int unaufgeloest = 0;
            int gesamt = 0;
            int bausteine = 1 + zufall.nextInt(8);
            for (int i = 0; i < bausteine; i++) {
                Art art = Art.values()[zufall.nextInt(Art.values().length)];
                if (platzhalter.isEmpty() && art != Art.UNBEKANNT && art != Art.TEXT) {
                    art = Art.TEXT;
                }
                switch (art) {
                    case KANONISCH, KLEIN, MIT_LEERZEICHEN -> {
                        String p = platzhalter.get(zufall.nextInt(platzhalter.size()));
                        String innen = p.substring(2, p.length() - 2);
                        antwort.append(switch (art) {
                            case KANONISCH -> p;
                            case KLEIN -> "[[" + innen.toLowerCase(Locale.ROOT) + "]]";
                            default -> "[[ " + innen + "  ]]";
                        });
                        erwartet.append(mapping.resolve(p).orElseThrow());
                        gesamt++;
                    }
                    case UNBEKANNT -> {
                        antwort.append("[[EMAIL_9999]]");
                        erwartet.append("[[EMAIL_9999]]");
                        unaufgeloest++;
                        gesamt++;
                    }
                    case BESCHAEDIGT -> {
                        String p = platzhalter.get(zufall.nextInt(platzhalter.size()));
                        String kaputt = p.substring(0, p.length() - 1);
                        antwort.append(kaputt);
                        erwartet.append(kaputt);
                        unaufgeloest++;
                        gesamt++;
                    }
                    case TEXT -> {
                        String wort = WOERTER[zufall.nextInt(WOERTER.length)];
                        antwort.append(wort);
                        erwartet.append(wort);
                    }
                }
                antwort.append(" - ");
                erwartet.append(" - ");
            }

            s.zurueckuebersetzen(antwort.toString());

            Rueckuebersetzung ergebnis = s.rueckuebersetzung().orElseThrow();
            ReidentificationResult kern = new Reidentifier().restore(antwort.toString(), mapping);
            assertEquals(kern.text(), ergebnis.text(), "Lauf " + lauf);
            assertEquals(kern.unresolved(), ergebnis.offen(), "Lauf " + lauf);
            assertEquals(erwartet.toString(), ergebnis.text(), "Lauf " + lauf);
            assertEquals(unaufgeloest, ergebnis.offen().size(), "Lauf " + lauf);
            assertEquals(gesamt, ergebnis.platzhalterGesamt(), "Lauf " + lauf);
        }
    }
}
