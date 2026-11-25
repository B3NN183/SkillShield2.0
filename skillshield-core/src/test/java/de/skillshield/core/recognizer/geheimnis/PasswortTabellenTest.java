package de.skillshield.core.recognizer.geheimnis;

import static de.skillshield.core.SynthetischeGeheimnisse.P1;
import static de.skillshield.core.SynthetischeGeheimnisse.P2;
import static de.skillshield.core.SynthetischeGeheimnisse.P3;
import static de.skillshield.core.SynthetischeGeheimnisse.P_MIT_STRICH;
import static de.skillshield.core.SynthetischeGeheimnisse.P_MIT_STRICH_KURZ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tabellen mit senkrechtem Strich (K3, Plan geheimnisse-erkennung, Nachtrag "T-5 verkleinert" Punkte 6 (a) und 7):
 * Beginnt der Wert unmittelbar hinter dem {@code |} (Tabelle ohne Leerraum), liest er ueber die Spaltengrenzen, und
 * {@code |} zaehlt als Sonderzeichen. Deshalb prueft der Wert-Test nur den Teil bis zum naechsten {@code |}
 * (fuehrende {@code |} uebersprungen); besteht er, reicht der Fund unveraendert bis zum Wortende. Steht Leerraum
 * hinter dem {@code |}, gilt das ganze Wort.
 *
 * <p>Messung an je 40 kompakten und normalen Tabellenzeilen ({@link #kompakteZeilen()}, {@link #normaleZeilen()}),
 * Trefferzahl vor und nach der Aenderung (gleiche Zeilen, gleiche Quelle ausser dieser einen Regel):
 * kompakt 25 vorher, 20 nachher (10 Kopfzeilen: 10 Fehlalarme vorher, 0 nachher; 10 Statuszeilen: 5 vorher, 0
 * nachher; 10 Wertzeilen: 10 und 10; 10 Wertzeilen mit Adresse in der Folgespalte: 0 vorher, 10 nachher, vorher
 * verwarf die Adresse im Wort den ganzen Wert); normal 20 vorher, 20 nachher (die Zellen stehen mit Leerraum, jede
 * Zelle ist ein eigenes Wort, dort aendert sich nichts). Jira-Form ({@link #jiraZeilen()}, 10 Kopfzeilen
 * {@code ||Passwort||Spalte||Spalte||}, 10 Wertzeilen): 10 Treffer vorher bei den Kopfzeilen, 0 nachher; Wertzeilen
 * 10 und 10.
 *
 * <p>Meldungen nennen nur Fall-Nummern und Zahlen, nie einen Wert.
 */
class PasswortTabellenTest {

    private static final PasswortRecognizer NEU = new PasswortRecognizer();
    private static final List<String> KONTEXT = List.of("Passwort", "Kennwort", "Initialpasswort");
    private static final List<String> SPALTEN = List.of("Benutzer", "Rolle", "Gueltigkeit", "Status", "Dienst",
            "Ablauf", "Hinweis", "Server", "Besitzer", "Quelle");
    private static final List<String> STATUS = List.of("nein", "ja", "offen", "alt", "neu", "aktiv", "gesperrt",
            "frei", "intern", "extern");
    private static final List<String> WERTE = List.of(P1, P2, P3);

    /** 40 Zeilen ohne Leerraum: 10 Kopfzeilen, 10 Statuszeilen, 10 Wertzeilen, 10 Wertzeilen mit Adresse. */
    static List<String> kompakteZeilen() {
        List<String> zeilen = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            zeilen.add("|" + KONTEXT.get(i % 3) + "|" + SPALTEN.get(i) + "|" + SPALTEN.get((i + 3) % 10) + "|");
        }
        for (int i = 0; i < 10; i++) {
            zeilen.add("|" + KONTEXT.get(i % 3) + "|" + STATUS.get(i) + "|" + (i + 2) + "|");
        }
        for (int i = 0; i < 10; i++) {
            zeilen.add("|" + KONTEXT.get(i % 3) + "|" + WERTE.get(i % 3) + "|" + SPALTEN.get(i) + "|");
        }
        for (int i = 0; i < 10; i++) {
            zeilen.add("|" + KONTEXT.get(i % 3) + "|" + WERTE.get(i % 3) + "|https://host" + i + ".example/x|");
        }
        return zeilen;
    }

    /** 40 Zeilen mit Leerraum um die Zellen: 20 Kopf- und Statuszeilen, 20 Wertzeilen. */
    static List<String> normaleZeilen() {
        List<String> zeilen = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            zeilen.add("| " + KONTEXT.get(i % 3) + " | " + SPALTEN.get(i) + " | " + SPALTEN.get((i + 3) % 10) + " |");
        }
        for (int i = 0; i < 10; i++) {
            zeilen.add("| " + KONTEXT.get(i % 3) + " | " + STATUS.get(i) + " | " + (i + 2) + " |");
        }
        for (int i = 0; i < 10; i++) {
            zeilen.add("| " + KONTEXT.get(i % 3) + " | " + WERTE.get(i % 3) + " | " + SPALTEN.get(i) + " |");
        }
        for (int i = 0; i < 10; i++) {
            zeilen.add("| " + KONTEXT.get(i % 3) + " | " + WERTE.get(i % 3) + " | https://host" + i
                    + ".example/x |");
        }
        return zeilen;
    }

    private static int treffer(List<String> zeilen, int von, int bis) {
        int summe = 0;
        for (int i = von; i < bis; i++) {
            summe += NEU.detect(zeilen.get(i)).size();
        }
        return summe;
    }

    @Test
    void messung_kompakteTabellenzeilen_kopfUndStatusKeinFund_wertzeilenAlleGeschuetzt() {
        List<String> zeilen = kompakteZeilen();
        assertEquals(40, zeilen.size(), "Zeilen");
        assertEquals(0, treffer(zeilen, 0, 10), "Kopfzeilen");
        assertEquals(0, treffer(zeilen, 10, 20), "Statuszeilen");
        assertEquals(10, treffer(zeilen, 20, 30), "Wertzeilen");
        assertEquals(10, treffer(zeilen, 30, 40), "Wertzeilen mit Adresse");
        for (int i = 20; i < 40; i++) {
            List<Finding> funde = NEU.detect(zeilen.get(i));
            String wert = WERTE.get(((i - 20) % 10) % 3);
            assertEquals(zeilen.get(i).indexOf(wert), funde.get(0).start(), "Beginn " + i);
        }
    }

    @Test
    void messung_normaleTabellenzeilen_wieVorher() {
        List<String> zeilen = normaleZeilen();
        assertEquals(40, zeilen.size(), "Zeilen");
        assertEquals(0, treffer(zeilen, 0, 20), "Kopf- und Statuszeilen");
        assertEquals(10, treffer(zeilen, 20, 30), "Wertzeilen");
        assertEquals(10, treffer(zeilen, 30, 40), "Wertzeilen mit Adresse");
    }

    @Test
    void kopfzeilenUndStatusspaltenOhneLeerraum_keinFund() {
        List<String> texte = List.of("|Passwort|Benutzer|Rolle|", "|Passwort|nein|12|", "|Kennwort|Gueltigkeit|Status|",
                "|Passwort|---|---|", "|Passwort|Benutzer|\n|Passwort|Rolle|");
        for (int i = 0; i < texte.size(); i++) {
            assertEquals(List.of(), NEU.detect(texte.get(i)), "Fall " + i);
        }
    }

    @Test
    void wertZwischenStrichen_istEinFund_auchMitFolgespalteUndAdresse() {
        String kompakt = "|Passwort|" + P1 + "|https://host.example/x|";
        List<Finding> k = NEU.detect(kompakt);
        assertEquals(1, k.size(), "kompakt Zahl");
        assertEquals(kompakt.indexOf(P1), k.get(0).start(), "kompakt Beginn");
        assertEquals(kompakt.length(), k.get(0).end(), "kompakt reicht bis zum Ende des Worts");
        assertEquals(0.80, k.get(0).confidence(), "kompakt Konfidenz");
        String normal = "| Passwort | " + P1 + " |";
        List<Finding> n = NEU.detect(normal);
        assertEquals(1, n.size(), "normal Zahl");
        assertEquals(normal.indexOf(P1), n.get(0).start(), "normal Beginn");
        assertEquals(normal.indexOf(P1) + P1.length(), n.get(0).end(), "normal Ende");
    }

    @Test
    void wertMitStrichDarin_istGanzGedeckt_wennDerErsteTeilBesteht() {
        String text = "|Passwort|" + P_MIT_STRICH + "|";
        List<Finding> funde = NEU.detect(text);
        assertEquals(1, funde.size(), "Zahl");
        assertEquals(text.indexOf(P_MIT_STRICH), funde.get(0).start(), "Beginn");
        assertTrue(funde.get(0).end() >= text.indexOf(P_MIT_STRICH) + P_MIT_STRICH.length(), "deckt den Wert");
    }

    @Test
    void bekannteGrenze_wertMitStrichDarin_bestehtDerErsteTeilNicht_bleibtDerWertLesbar() {
        // Vor der Aenderung war dieser Wert ganz gedeckt (das Wort bestand den Test als Ganzes). Der kurze erste Teil
        // ist von einer Spaltenkopf-Zelle nicht zu unterscheiden; Fehlalarme gehen hier vor (Plan, Nachtrag 6 (a)).
        assertEquals(List.of(), NEU.detect("|Passwort|" + P_MIT_STRICH_KURZ + "|"), "kein Fund");
        // Ein Wert, der selbst mit | beginnt: geprueft wird der Teil nach den fuehrenden |.
        assertEquals(List.of(), NEU.detect("|Passwort||" + P_MIT_STRICH_KURZ + "|"), "Wert beginnt mit Strich");
        assertEquals(1, NEU.detect("|Passwort||" + P_MIT_STRICH + "|").size(), "Wert mit Strich, erster Teil besteht");
    }

    @Test
    void mitLeerraumHinterDemStrich_giltDasGanzeWort_derWertMitStrichIstGanzGedeckt() {
        for (String wert : List.of(P_MIT_STRICH_KURZ, P_MIT_STRICH)) {
            String text = "| Passwort | " + wert + " |";
            List<Finding> funde = NEU.detect(text);
            assertEquals(1, funde.size(), "Zahl");
            assertEquals(text.indexOf(wert), funde.get(0).start(), "Beginn");
            assertEquals(text.indexOf(wert) + wert.length(), funde.get(0).end(), "Ende");
        }
        String ohneLeerraumDavor = "Passwort | " + P_MIT_STRICH_KURZ;
        assertEquals(1, NEU.detect(ohneLeerraumDavor).size(), "Leerraum nur dahinter");
    }

    @Test
    void fuehrendeStriche_werdenFuerDiePruefungUebersprungen() {
        List<String> keine = List.of("||Passwort||Benutzer||Rolle||", "|Passwort||gesperrt|", "|Passwort||nein||12||",
                "|Passwort||||||||", "|Passwort|" + "|".repeat(40), "|Passwort|a||", "||Passwort|||---||");
        for (int i = 0; i < keine.size(); i++) {
            assertEquals(List.of(), NEU.detect(keine.get(i)), "kein Fund " + i);
        }
        String text = "||Passwort||" + P1 + "||";
        List<Finding> funde = NEU.detect(text);
        assertEquals(1, funde.size(), "Jira Zahl");
        assertEquals(text.indexOf("|" + P1), funde.get(0).start(), "Jira Beginn unveraendert");
        assertEquals(text.length(), funde.get(0).end(), "Jira Ende unveraendert");
        assertEquals(0.80, funde.get(0).confidence(), "Jira Konfidenz");
    }

    /** Jira-Form: 10 Kopfzeilen {@code ||Passwort||Spalte||Spalte||}, 10 Wertzeilen mit Wert in Spalte 1. */
    static List<String> jiraZeilen() {
        List<String> zeilen = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            zeilen.add("||" + KONTEXT.get(i % 3) + "||" + SPALTEN.get(i) + "||" + SPALTEN.get((i + 3) % 10) + "||");
        }
        for (int i = 0; i < 10; i++) {
            zeilen.add("||" + KONTEXT.get(i % 3) + "||" + WERTE.get(i % 3) + "||" + SPALTEN.get(i) + "||");
        }
        return zeilen;
    }

    @Test
    void messung_jiraForm_kopfzeilenKeinFund_wertzeilenAlleGeschuetzt() {
        List<String> zeilen = jiraZeilen();
        assertEquals(20, zeilen.size(), "Zeilen");
        assertEquals(0, treffer(zeilen, 0, 10), "Kopfzeilen");
        assertEquals(10, treffer(zeilen, 10, 20), "Wertzeilen");
    }

    @Test
    void wertInAnfuehrungszeichenMitStrich_wirdGanzGeprueft_auchOhneLeerraum() {
        for (String text : List.of("|Passwort|\"" + P_MIT_STRICH_KURZ + "\"|", "| Passwort | \"" + P_MIT_STRICH_KURZ
                + "\" |")) {
            List<Finding> funde = NEU.detect(text);
            assertEquals(1, funde.size(), "Zahl");
            assertEquals(text.indexOf(P_MIT_STRICH_KURZ), funde.get(0).start(), "Beginn");
            assertEquals(text.indexOf(P_MIT_STRICH_KURZ) + P_MIT_STRICH_KURZ.length(), funde.get(0).end(), "Ende");
        }
    }

    @Test
    void bekannteGrenze_kompakteZellen_restfehlalarmeUndStrichFolgeVorDemKontextwort() {
        // Restfehlalarme: der erste Teil besteht den Wert-Test, obwohl es keine Passwoerter sind.
        assertEquals(1, NEU.detect("|Passwort|*Status1!*|nein|").size(), "Auszeichnung in der Zelle");
        assertEquals(1, NEU.detect("|Passwort|Ab3<br>Fuchs7x|nein|").size(), "Zeilenumbruch-Marke in der Zelle");
        // Der Strich folgt auf ein Wort des Zwischenteils: das Wort reicht bis zum Leerraum, kein Verbinder.
        assertEquals(List.of(), NEU.detect("|Passwort fuer svc_x|" + P1 + "|"), "Strich hinter dem Zwischenteil");
        // Strich-Folge mit Leerraum dahinter: das Wort hinter dem Kontextwort besteht nur aus |, kein Fund.
        assertEquals(List.of(), NEU.detect("|| Passwort || " + P1 + " ||"), "Leerraum um die Strich-Folge");
    }

    @Test
    void tabulatorOhneStrich_unveraendert_dasGanzeWortWirdGeprueft() {
        assertEquals(1, NEU.detect("Passwort\t" + P_MIT_STRICH_KURZ).size(), "Tabulator ganzes Wort");
        assertEquals(1, NEU.detect("Passwort ist " + P_MIT_STRICH_KURZ).size(), "K2 ganzes Wort");
    }
}
