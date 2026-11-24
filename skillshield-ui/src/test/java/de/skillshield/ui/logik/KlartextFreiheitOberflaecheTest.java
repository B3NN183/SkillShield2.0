package de.skillshield.ui.logik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.SynthetischeKvNummern;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.ui.logik.Attrappen.TestDialoge;
import de.skillshield.ui.logik.Attrappen.TestZwischenablage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Kein Klartext in Meldungen (AK-19, AK-49, AK-50, AK-51): Ein scripted Durchlauf aller Zustaende mit eindeutigen
 * Markern in Eingabe, Fundwerten, manuell markiertem Namen und KI-Antwort, auch mit erzwungenen Fehlern. In
 * Statusleiste, Rueckfragen, Fehlertexten, Sperrgruenden, Listenzeilen, {@code toString()} der Zustandsobjekte und
 * Ausnahmetexten darf kein Marker vorkommen; die Standardstroeme bleiben leer. Platzhalter sind erlaubt.
 */
class KlartextFreiheitOberflaecheTest {

    private static final String MAIL = "quarzmarker@example.org";
    private static final String NAME = "Quarzfalke";
    private static final String IP = "192.0.2.10";
    private static final String KVNR = SynthetischeKvNummern.KVNR_GUELTIG;
    private static final String ANTWORT_MARKER = "Antwortmarker";
    private static final String FEHLER_MARKER = "Fehlermarker";
    private static final List<String> MARKER = List.of("quarzmarker", NAME, IP, KVNR, ANTWORT_MARKER, FEHLER_MARKER);

    private static final String EINGABE = "Kontakt: " + MAIL + ", Name " + NAME + ", Server " + IP + ", KVNR " + KVNR
            + ". " + NAME + " ruft an.";
    private static final String ANTWORT =
            ANTWORT_MARKER + " [[EMAIL_1]] [[TEXT_1]] [[EMAIL_9]] [[Name_1]] [[FreiGewaehlt_7]] "
                    + "[[Frei_Gewaehlt_7]] [[EMAIL_ "
                    + ANTWORT_MARKER;

    private final TestDialoge dialoge = new TestDialoge();
    private final TestZwischenablage zwischenablage = new TestZwischenablage();
    private final StringBuilder gesehen = new StringBuilder();

    /** Sammelt alles, was die Oberflaeche ausser dem Eingabe- und Ergebnistext selbst anzeigen oder ausgeben kann. */
    private void sammeln(Sitzung s) {
        Statusleiste status = s.statusleiste();
        gesehen.append(status.meldung()).append('|').append(status.zuordnung()).append('|').append(status).append('\n');
        Bedienzustand bedienzustand = s.bedienzustand();
        gesehen.append(bedienzustand).append(bedienzustand.sperrgrund().values()).append('\n');
        gesehen.append(s).append('\n');
        for (FundZeile zeile : s.zeilen()) {
            gesehen.append(zeile).append(zeile.platzhalter()).append('\n');
        }
        s.rueckuebersetzung().ifPresent(r -> gesehen.append(r).append(r.listenzeilen()).append('\n'));
        gesehen.append(dialoge.fehler).append('\n');
        dialoge.fragen.forEach(f -> gesehen.append(f).append(f.text()).append('\n'));
        dialoge.erneutFragen.forEach(f -> gesehen.append(f).append(f.text()).append('\n'));
    }

    private void ausnahmeSammeln(Runnable aufruf) {
        try {
            aufruf.run();
        } catch (RuntimeException e) {
            gesehen.append(e.getClass().getName()).append(e.getMessage()).append(e)
                    .append(Fehlerbehandlung.meldung(e)).append('\n');
        }
    }

    private static String ausgabeVon(Runnable aktion) {
        PrintStream out = System.out;
        PrintStream err = System.err;
        ByteArrayOutputStream puffer = new ByteArrayOutputStream();
        PrintStream umleitung = new PrintStream(puffer, true, StandardCharsets.UTF_8);
        System.setOut(umleitung);
        System.setErr(umleitung);
        try {
            aktion.run();
        } finally {
            System.setOut(out);
            System.setErr(err);
        }
        return puffer.toString(StandardCharsets.UTF_8);
    }

    private void keinMarkerSichtbar(String konsole) {
        String alles = gesehen.toString();
        assertFalse(alles.isBlank(), "es wurde etwas gesammelt");
        for (String marker : MARKER) {
            assertFalse(alles.contains(marker), "Marker in Meldungen oder toString: " + marker);
            assertFalse(konsole.contains(marker), "Marker auf der Konsole: " + marker);
        }
        assertEquals("", konsole, "die Standardstroeme bleiben leer");
    }

    /** Der Durchlauf aller Zustaende mit dem Standard-Scanner. */
    private void durchlauf(Sitzung s) {
        sammeln(s);
        s.kiAntwortGeaendert(false);
        s.zurueckuebersetzen(ANTWORT);
        s.ergebnisKopieren();
        sammeln(s);
        s.kiAntwortGeaendert(true);
        s.scanAnfordern("   ");
        sammeln(s);
        s.scanAnfordern("x".repeat(Sitzung.MAX_ZEICHEN + 1));
        sammeln(s);

        ScanAuftrag auftrag = s.scanAnfordern(EINGABE).orElseThrow();
        sammeln(s);
        gesehen.append(auftrag).append('\n');
        ausnahmeSammeln(() -> s.scanAbgeschlossen(auftrag, auftrag.ausfuehren()));
        sammeln(s);

        // Auswahl, Hinzufuegen (auch ueberschneidend, leer, ungueltig), Kopieren.
        s.anwaehlen(0, false);
        sammeln(s);
        s.alleAnwaehlen();
        int start = EINGABE.indexOf(NAME);
        s.markierungHinzufuegen(start, start + NAME.length());
        sammeln(s);
        s.markierungHinzufuegen(EINGABE.indexOf("quarz"), EINGABE.indexOf("quarz") + 5);
        sammeln(s);
        s.markierungHinzufuegen(3, 3);
        sammeln(s);
        ausnahmeSammeln(() -> s.markierungHinzufuegen(-1, 5));
        ausnahmeSammeln(() -> s.markierungHinzufuegen(0, EINGABE.length() + 10));
        ausnahmeSammeln(() -> s.anwaehlen(999, true));
        for (int i = 0; i < s.zeilen().size(); i++) {
            s.anwaehlen(i, false);
        }
        sammeln(s);
        dialoge.antwort = false;
        s.fuerKiKopieren();
        sammeln(s);
        dialoge.antwort = true;
        s.fuerKiKopieren();
        sammeln(s);
        s.alleAnwaehlen();
        s.fuerKiKopieren();
        sammeln(s);

        // Rueckuebersetzung in allen Varianten.
        s.zurueckuebersetzen("");
        sammeln(s);
        s.zurueckuebersetzen("y".repeat(Sitzung.MAX_ZEICHEN + 1));
        sammeln(s);
        ausnahmeSammeln(() -> s.zurueckuebersetzen(ANTWORT));
        sammeln(s);
        s.zurueckuebersetzen(ANTWORT_MARKER + " ganz ohne Platzhalter");
        sammeln(s);
        ausnahmeSammeln(s::ergebnisKopieren);
        sammeln(s);

        // Eingabe geaendert, Kopieren bei gesperrter Zwischenablage, neuer Scan mit Rueckfrage und Abbruch.
        s.eingabeGeaendert();
        sammeln(s);
        zwischenablage.gesperrt = true;
        s.textKopieren(NAME);
        s.ergebnisKopieren();
        sammeln(s);
        zwischenablage.gesperrt = false;
        dialoge.antwort = false;
        s.scanAnfordern(EINGABE);
        sammeln(s);
        dialoge.antwort = true;
        ScanAuftrag zweiter = s.scanAnfordern(EINGABE + " Nachtrag " + FEHLER_MARKER).orElseThrow();
        s.scanFehlgeschlagen(zweiter, new IllegalStateException(FEHLER_MARKER));
        sammeln(s);

        // Zuruecksetzen mit nicht leerbarer Zwischenablage, danach Beenden mit Rueckfrage "erneut versuchen".
        zwischenablage.nurLeerenGesperrt = true;
        ausnahmeSammeln(s::zuruecksetzenAnfordern);
        sammeln(s);
        scannen(s, EINGABE);
        s.fuerKiKopieren();
        dialoge.erneutAntworten.add(true);
        dialoge.beiErneutFrage = () -> zwischenablage.nurLeerenGesperrt = false;
        ausnahmeSammeln(s::beendenAnfordern);
        sammeln(s);
    }

    private static void scannen(Sitzung s, String eingabe) {
        Attrappen.scannen(s, eingabe);
    }

    @Test
    void keinMarkerInMeldungenRueckfragenFehlertextenToStringUndKonsole() {
        Sitzung s = Sitzung.standard(zwischenablage, dialoge);
        // Hinweis zur Erwartung: ein Scan ist vor dem Durchlauf noetig, damit "Kopieren" etwas bewirkt.
        dialoge.antwort = true;

        String konsole = ausgabeVon(() -> {
            scannen(s, EINGABE);
            s.fuerKiKopieren();
            durchlauf(s);
        });

        assertTrue(zwischenablage.gesetzt.size() >= 1);
        keinMarkerSichtbar(konsole);
    }

    @Test
    void keinMarkerBeiTrotzdemBeendenMitNichtLeerbarerZwischenablage() {
        Sitzung s = Sitzung.standard(zwischenablage, dialoge);
        dialoge.antwort = true;

        String konsole = ausgabeVon(() -> {
            scannen(s, EINGABE);
            s.fuerKiKopieren();
            s.zurueckuebersetzen(ANTWORT);
            s.ergebnisKopieren();
            s.textKopieren(NAME);
            zwischenablage.nurLeerenGesperrt = true;
            ausnahmeSammeln(s::beendenAnfordern);
            sammeln(s);
            ausnahmeSammeln(s::beendenAnfordern);
            sammeln(s);
        });

        assertEquals(1, dialoge.erneutFragen.size(), "einmal gefragt, dann 'Trotzdem beenden'");
        assertTrue(s.zeilen().isEmpty());
        keinMarkerSichtbar(konsole);
    }

    @Test
    void einPlatzhalterMitFreiGewaehltemTypnamenBringtKeinenMarkerInDieListenzeilen() {
        Sitzung s = Sitzung.standard(zwischenablage, dialoge);

        ausgabeVon(() -> {
            scannen(s, EINGABE);
            s.fuerKiKopieren();
            s.zurueckuebersetzen(ANTWORT);
        });

        List<String> zeilen = s.rueckuebersetzung().orElseThrow().listenzeilen();
        assertFalse(zeilen.isEmpty());
        // Beide Token sind gueltig, im Mapping unbekannt und tragen einen frei gewaehlten Typnamen ohne Marker.
        for (String token : List.of("[[Name_1]]", "[[FreiGewaehlt_7]]")) {
            assertEquals(1, zeilen.stream().filter(z -> z.contains(token) && z.contains("unbekannt")).count(),
                    "genau eine Zeile 'unbekannt' zu " + token);
        }
        // Unterstrich im Typnamen: kein Token, nie gelistet.
        assertFalse(zeilen.stream().anyMatch(z -> z.contains("Frei_Gewaehlt")));
        for (String zeile : zeilen) {
            for (String marker : MARKER) {
                assertFalse(zeile.contains(marker), "Marker in Listenzeile: " + marker);
            }
        }
    }

    @Test
    void keinMarkerBeiGescheitertemScanMitAusnahmeMeldungMarker() {
        Function<String, List<Finding>> scanner = text -> {
            throw new IllegalStateException(FEHLER_MARKER + " " + text);
        };
        Sitzung s = new Sitzung(scanner, zwischenablage, dialoge);

        String konsole = ausgabeVon(() -> {
            ScanAuftrag auftrag = s.scanAnfordern(EINGABE).orElseThrow();
            try {
                s.scanAbgeschlossen(auftrag, auftrag.ausfuehren());
            } catch (IllegalStateException e) {
                s.scanFehlgeschlagen(auftrag, e);
            }
            sammeln(s);
        });

        assertEquals(List.of(Texte.FEHLER + " (java.lang.IllegalStateException)"), dialoge.fehler);
        keinMarkerSichtbar(konsole);
    }

    @Test
    void keinMarkerBeiUeberlappendenFundenDesScanners() {
        Sitzung s = new Sitzung(text -> List.of(Finding.manual(FindingType.TEXT, text.substring(0, 12), 0, 12),
                Finding.manual(FindingType.TEXT, text.substring(6, 20), 6, 20)), zwischenablage, dialoge);

        String konsole = ausgabeVon(() -> {
            ScanAuftrag auftrag = s.scanAnfordern(EINGABE).orElseThrow();
            s.scanAbgeschlossen(auftrag, auftrag.ausfuehren());
            sammeln(s);
        });

        assertEquals(List.of(Texte.FEHLER + " (de.skillshield.core.pseudonymize.InvalidFindingException)"),
                dialoge.fehler);
        keinMarkerSichtbar(konsole);
    }

    @Test
    void keinMarkerWennDieRueckuebersetzungMitMarkerAusnahmeScheitert() {
        Sitzung s = new Sitzung(TextScanner.standard()::scan, zwischenablage, dialoge, (antwort, zuordnung) -> {
            throw new IllegalStateException(FEHLER_MARKER + " " + antwort);
        });

        String konsole = ausgabeVon(() -> {
            scannen(s, EINGABE);
            s.fuerKiKopieren();
            s.zurueckuebersetzen(ANTWORT);
            sammeln(s);
        });

        assertEquals(List.of(Texte.RUECKUEBERSETZEN_FEHLGESCHLAGEN + " (java.lang.IllegalStateException)"),
                dialoge.fehler);
        keinMarkerSichtbar(konsole);
    }

    @Test
    void keinMarkerWennDieZwischenablageMitMarkerAusnahmeWirft() {
        Zwischenablage kaputt = new Zwischenablage() {
            @Override
            public boolean setzeText(String text) {
                throw new IllegalStateException(FEHLER_MARKER + " " + text);
            }

            @Override
            public java.util.Optional<String> leseText() {
                throw new IllegalStateException(FEHLER_MARKER);
            }

            @Override
            public boolean leeren() {
                throw new IllegalStateException(FEHLER_MARKER);
            }
        };
        Sitzung s = Sitzung.standard(kaputt, dialoge);

        String konsole = ausgabeVon(() -> {
            scannen(s, EINGABE);
            s.fuerKiKopieren();
            s.textKopieren(NAME);
            sammeln(s);
            dialoge.antwort = true;
            s.zuruecksetzenAnfordern();
            sammeln(s);
            s.beendenAnfordern();
            sammeln(s);
        });

        keinMarkerSichtbar(konsole);
    }

    @Test
    void dieFestenTexteDerRueckfragenUndMeldungenEnthaltenKeineMarker() {
        StringBuilder alle = new StringBuilder();
        for (Rueckfrage frage : Rueckfrage.values()) {
            alle.append(frage.text());
        }
        alle.append(Texte.ZWISCHENABLAGE_NICHT_GELEERT).append(Texte.ERNEUT_VERSUCHEN).append(Texte.TROTZDEM_BEENDEN);

        for (String marker : MARKER) {
            assertFalse(alle.toString().contains(marker));
        }
    }
}
