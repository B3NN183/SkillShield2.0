package de.skillshield.ui.logik;

import java.util.List;
import java.util.Locale;

/**
 * Feste deutsche Texte und Formatierer der Fachlogik (Architektur-Plan Kap. 4). Kein Text enthält Eingabe-, Fund-
 * oder Originalwerte; erlaubt sind nur feste Wörter, Zahlen und Klassennamen von Ausnahmen.
 */
public final class Texte {

    /** Statusmeldung nach dem Start. */
    public static final String BEREIT = "Bereit";
    /** Zuordnungsanzeige, solange keine Zuordnung besteht. */
    public static final String KEINE_ZUORDNUNG = "Keine Zuordnung";
    /** Meldung bei leerem Eingabetext (AK-6). */
    public static final String TEXT_FEHLT = "Bitte zuerst Text eingeben.";
    /** Meldung während des Hintergrund-Scans (AK-10). */
    public static final String SCAN_LAEUFT = "Scan läuft …";
    /** Meldung nach Änderung des Eingabetextes (AK-12). */
    public static final String NEUER_SCAN_NOETIG = "Der Text wurde geändert. Bitte erneut scannen.";
    /** Anzeige in der Spalte "Platzhalter" für einen abgewählten Fund (AK-14). */
    public static final String NICHT_ERSETZT = "nicht ersetzt";
    /** Fester Fehlertext (AK-50); der Klassenname der Ausnahme wird in {@link #fehlerMeldung} angehängt. */
    public static final String FEHLER = "Beim Verarbeiten ist ein Fehler aufgetreten. Es wurde nichts kopiert.";
    /** Sperrgrund für Funktionen, die einen gültigen Scan brauchen (AK-48). */
    public static final String ERST_SCANNEN = "Erst Text scannen.";
    /** Sperrgrund, wenn der Scan zwar gültig ist, aber keine Funde enthält. */
    public static final String KEINE_FUNDE = "Kein Fund vorhanden.";
    /** Sperrgrund, solange die KI-Antwort fehlt (AK-36). */
    public static final String ANTWORT_FEHLT = "Bitte die Antwort der KI einfügen.";
    /** Sperrgrund für das Kopieren des Ergebnisses vor der Rückübersetzung. */
    public static final String ERST_ZURUECKUEBERSETZEN = "Erst zurückübersetzen.";

    /** Meldung bei leerer oder nur aus Leerraum bestehender Markierung (AK-26). */
    public static final String MARKIERUNG_FEHLT = "Bitte zuerst im Text eine Stelle markieren.";

    /** Meldung nach dem Kopieren ohne ersetzte Stelle, nach bestätigter Rückfrage (AK-35). */
    public static final String UNVERAENDERT_KOPIERT = "Text unverändert kopiert";
    /** Meldung nach "Rückübersetzten Text kopieren" (AK-39). */
    public static final String ERGEBNIS_KOPIERT = "Rückübersetzter Text kopiert.";
    /** Meldung und Fehlertext, wenn die Zwischenablage nichts angenommen hat (gesperrt oder nicht verfügbar). */
    public static final String ZWISCHENABLAGE_NICHT_VERFUEGBAR =
            "Die Zwischenablage ist nicht verfügbar. Es wurde nichts kopiert.";
    /** Hinweis, wenn die Auswahl nach dem Kopieren geändert wurde (Abweichung A-2, Kap. 3.5). */
    public static final String AUSWAHL_NACH_KOPIEREN_GEAENDERT = "Die Auswahl wurde nach dem Kopieren geändert. "
            + "Die Rückübersetzung gilt für den zuletzt kopierten Text; für den neuen Stand bitte erneut kopieren.";
    /** Sperrgrund für "Zurückübersetzen", solange im aktuellen Scan noch nicht für die KI kopiert wurde (S-2). */
    public static final String ERST_KOPIEREN =
            "Erst den Text für die KI kopieren. Zurückübersetzt wird immer die Antwort zum zuletzt kopierten Text.";
    /** Zuordnungsanzeige nach einem Scan, solange noch nicht für die KI kopiert wurde (S-2). */
    public static final String ZUORDNUNG_ERST_NACH_KOPIEREN = "Zuordnung: erst nach dem Kopieren für die KI";
    /** Hinweis, wenn sich die Platzhalter gegenüber dem zuvor kopierten Text geändert haben (S-3). */
    public static final String PLATZHALTER_GEAENDERT = "Achtung: Die Platzhalter haben sich gegenüber dem zuvor "
            + "kopierten Text geändert. Antworten auf den früheren Text nicht mehr zurückübersetzen.";
    /** Meldung und Fehlertext, wenn die Rückübersetzung selbst scheitert. */
    public static final String RUECKUEBERSETZEN_FEHLGESCHLAGEN =
            "Beim Zurückübersetzen ist ein Fehler aufgetreten. Die Antwort wurde nicht zurückübersetzt.";
    /** Liste "Nicht zurückübersetzt": alle Platzhalter der Antwort wurden aufgelöst (AK-38). */
    public static final String ALLE_ZURUECK = "Alle Platzhalter wurden zurückübersetzt.";
    /** Liste "Nicht zurückübersetzt": die Antwort enthält keinen Platzhalter (AK-38). */
    public static final String KEINE_PLATZHALTER = "Die Antwort enthält keine Platzhalter.";
    /** Sperrgrund für "Rückübersetzten Text kopieren" bei leerem Ergebnis (AK-39). */
    public static final String ERGEBNIS_LEER = "Das Ergebnis ist leer.";
    /**
     * Meldung nach dem Zurücksetzen und Text der Rückfrage beim Beenden, wenn die Zwischenablage nicht geleert werden
     * konnte (V-2). Fester Text, nie der Inhalt der Zwischenablage.
     */
    public static final String ZWISCHENABLAGE_NICHT_GELEERT =
            "Die Zwischenablage konnte nicht geleert werden. Sie kann noch Text aus SkillShield enthalten.";
    /** Antwort der Rückfrage {@link Rueckfrage#ZWISCHENABLAGE_NICHT_GELEERT} (Standard). */
    public static final String ERNEUT_VERSUCHEN = "Erneut versuchen";
    /** Antwort der Rückfrage {@link Rueckfrage#ZWISCHENABLAGE_NICHT_GELEERT}: beenden, ohne zu leeren. */
    public static final String TROTZDEM_BEENDEN = "Trotzdem beenden";
    /** Grund in der Liste "Nicht zurückübersetzt": wohlgeformt, aber nicht in der Zuordnung. */
    public static final String GRUND_UNBEKANNT = "unbekannt";
    /** Grund in der Liste "Nicht zurückübersetzt": beschädigter Platzhalter. */
    public static final String GRUND_BESCHAEDIGT = "beschädigt";

    // ---- Beschriftungen der Ansicht (T-6); feste Texte, nie Eingabe- oder Fundtext

    /** Überschrift Bereich 1 (D-1). */
    public static final String BEREICH_1 = "1 Text eingeben";
    /** Überschrift Bereich 2 (D-1). */
    public static final String BEREICH_2 = "2 Funde prüfen";
    /** Überschrift Bereich 3 (D-1). */
    public static final String BEREICH_3 = "3 Für KI kopieren";
    /** Überschrift Bereich 4 (D-1). */
    public static final String BEREICH_4 = "4 KI-Antwort zurückübersetzen";
    /** Leer-Text der Tabelle vor dem ersten Scan (AK-5). */
    public static final String LEER_VOR_SCAN = "Noch kein Scan. Text eingeben und Scannen wählen.";
    /** Leer-Text der Tabelle nach einem Scan ohne Fund (D-16, AK-5). */
    public static final String LEER_NACH_SCAN = "Keine Fundstelle erkannt. Das heißt nicht, dass der Text keine "
            + "personenbezogenen Daten enthält: Namen und Adressen werden noch nicht erkannt. "
            + "Bitte prüfen Sie den Text und markieren Sie fehlende Stellen von Hand.";
    /** Dauerhinweis in Bereich 2 (AK-21). */
    public static final String HINWEIS_ERKENNUNG = "Die Erkennung ist nicht vollständig. Namen und Adressen werden "
            + "noch nicht erkannt. Bitte prüfen Sie den Text und markieren Sie fehlende Stellen von Hand.";
    /** Dauerhinweis in Bereich 4 (AK-40, D-5). */
    public static final String HINWEIS_ZUORDNUNG = "Die Rückübersetzung ist nur möglich, solange dieses Programm "
            + "geöffnet ist und kein neuer Scan gestartet wurde. Die Zuordnung wird nicht gespeichert.";
    /** Leer-Text von Bereich 3 (AK-5). */
    public static final String LEER_BEREICH_3 = "Hier erscheint der Text für die KI, sobald gescannt wurde.";
    /** Leer-Text von Bereich 4 (AK-5). */
    public static final String LEER_BEREICH_4 = "Hier erscheint der rückübersetzte Text, sobald eine KI-Antwort "
            + "zurückübersetzt wurde.";
    /** Beschriftung der Schaltfläche in Bereich 1. */
    public static final String SCANNEN = "Scannen";
    /** Beschriftung der Schaltfläche in Bereich 2 (AK-16: es gibt kein "alle abwählen"). */
    public static final String ALLE_ANWAEHLEN = "Alle anwählen";
    /** Beschriftung der Schaltfläche in Bereich 2 (D-7). */
    public static final String MARKIERUNG_HINZUFUEGEN = "Markierung als Fund hinzufügen";
    /** Beschriftung der Schaltfläche in Bereich 3. */
    public static final String FUER_KI_KOPIEREN = "Für KI kopieren";
    /** Beschriftung der Schaltfläche in Bereich 4 (AK-36). */
    public static final String ZURUECKUEBERSETZEN = "Zurückübersetzen";
    /** Beschriftung der Schaltfläche in Bereich 4 (AK-39). */
    public static final String ERGEBNIS_KOPIEREN_SCHALTFLAECHE = "Rückübersetzten Text kopieren";
    /** Beschriftung der Schaltfläche in der Fußleiste (AK-42). */
    public static final String ZURUECKSETZEN = "Zurücksetzen";
    /** Beschriftung der Schaltfläche in der Fußleiste (AK-43). */
    public static final String BEENDEN = "Beenden";
    /** Beschriftung des Eingabefeldes in Bereich 4 (AK-36). */
    public static final String KI_ANTWORT = "KI-Antwort";
    /** Beschriftung des Ergebnisfeldes in Bereich 4 (AK-37). */
    public static final String RUECKUEBERSETZTER_TEXT = "Rückübersetzter Text";
    /** Beschriftung der Liste in Bereich 4 (AK-38). */
    public static final String NICHT_ZURUECKUEBERSETZT = "Nicht zurückübersetzt";

    // ---- Zugängliche Namen (T-7, AK-47); feste Texte, nie Eingabe-, Fund- oder KI-Text

    /** Zugänglicher Name des Eingabefeldes. */
    public static final String NAME_EINGABE = "Eingabetext";
    /** Zugänglicher Name der Fundtabelle. */
    public static final String NAME_FUNDE = "Fundtabelle";
    /** Zugänglicher Name der Spaltenköpfe der Fundtabelle. */
    public static final String NAME_SPALTENKOEPFE = "Spaltenköpfe der Fundtabelle";
    /** Zugänglicher Name des Feldes mit dem Text für die KI. */
    public static final String NAME_TEXT_FUER_KI = "Text für die KI";
    /** Zugänglicher Name der Statusleiste. */
    public static final String NAME_STATUSLEISTE = "Statusleiste";
    /** Zugänglicher Name der Statusmeldung (links in der Statusleiste). */
    public static final String NAME_STATUSMELDUNG = "Statusmeldung";
    /** Zugänglicher Name der Zuordnungsanzeige (rechts in der Statusleiste). */
    public static final String NAME_ZUORDNUNGSANZEIGE = "Zuordnungsanzeige";
    /** Zugänglicher Name der Fußleiste mit "Zurücksetzen" und "Beenden". */
    public static final String NAME_FUSSLEISTE = "Fußleiste";
    /** Zugänglicher Name des Hinweises zur Erkennung (Bereich 2). */
    public static final String NAME_HINWEIS_ERKENNUNG = "Hinweis zur Erkennung";
    /** Zugänglicher Name des Hinweises zur Rückübersetzung (Bereich 4). */
    public static final String NAME_HINWEIS_ZUORDNUNG = "Hinweis zur Rückübersetzung";
    /** Zugänglicher Name des Leer-Hinweises der Fundtabelle. */
    public static final String NAME_LEER_FUNDE = "Hinweis zur Fundtabelle";
    /** Zugänglicher Name des Leer-Hinweises von Bereich 3. */
    public static final String NAME_LEER_TEXT_FUER_KI = "Hinweis zum Text für die KI";
    /** Zugänglicher Name des Leer-Hinweises des Ergebnisses. */
    public static final String NAME_LEER_ERGEBNIS = "Hinweis zum rückübersetzten Text";
    /** Spaltenköpfe der Fundtabelle in dieser Reihenfolge (AK-13). */
    public static final List<String> SPALTENKOEPFE = List.of("Übernehmen", "Typ", "Fundtext", "Stufe", "Platzhalter");
    /** Antwort "Ja" der Rückfragen (AK-2). */
    public static final String JA = "Ja";
    /** Antwort "Abbrechen" der Rückfragen, Standard (AK-2). */
    public static final String ABBRECHEN = "Abbrechen";
    /** Schaltfläche des Fehlerdialogs (AK-2). */
    public static final String OK = "OK";
    /** Titel der Rückfragedialoge. */
    public static final String TITEL_RUECKFRAGE = "Rückfrage";
    /** Titel des Fehlerdialogs. */
    public static final String TITEL_FEHLER = "Fehler";
    /** Titel des Hauptfensters (AK-1): genau dieser Text, nie mit Inhalt. */
    public static final String FENSTERTITEL = "SkillShield";

    /** Höchstzahl der angezeigten Zeilen der Liste "Nicht zurückübersetzt"; der Rest wird nur gezählt. */
    public static final int MAX_LISTENZEILEN = 500;

    private Texte() {
    }

    /** Letzte Zeile der gekürzten Liste "Nicht zurückübersetzt": "… und 124.500 weitere". Nur eine Zahl. */
    public static String undWeitere(int anzahl) {
        return String.format(Locale.GERMANY, "… und %,d weitere", anzahl);
    }

    /** Meldung nach dem Kopieren für die KI (AK-35): "Text für KI kopiert (3 Stellen ersetzt)". */
    public static String kopiert(int stellen) {
        return "Text für KI kopiert (" + stellen + (stellen == 1 ? " Stelle" : " Stellen") + " ersetzt)";
    }

    /** Meldung nach der Rückübersetzung mit nicht auflösbaren Platzhaltern (AK-38); nur die Zahl, nie ein Text. */
    public static String nichtZurueckuebersetzt(int anzahl) {
        return anzahl == 1 ? "1 Platzhalter konnte nicht zurückübersetzt werden."
                : anzahl + " Platzhalter konnten nicht zurückübersetzt werden.";
    }

    /**
     * Zeile der Liste "Nicht zurückübersetzt" (AK-38): "[[KVNR_7]] an Position 121: unbekannt". Der Platzhalter ist der
     * Ausschnitt aus der KI-Antwort (nur Klammern, ASCII-Zeichen und Unterstrich), nie ein Originalwert.
     *
     * @param platzhalter Ausschnitt aus der Antwort
     * @param position    Position ab 1 gezählt
     * @param grund       {@link #GRUND_UNBEKANNT} oder {@link #GRUND_BESCHAEDIGT}
     */
    public static String listenzeile(String platzhalter, int position, String grund) {
        return platzhalter + " an Position " + position + ": " + grund;
    }

    /**
     * Meldung nach dem manuellen Hinzufügen (AK-23, AK-24, AK-25), etwa "3 Stellen als Fund hinzugefügt. 1 Stelle
     * war bereits geschützt. 2 bestehende Funde wurden dabei ersetzt." Die beiden letzten Sätze stehen nur, wenn die
     * Zahl größer als null ist.
     *
     * @param hinzugefuegt      Zahl der neu geschützten Stellen (die markierte Stelle eingeschlossen)
     * @param bereitsGeschuetzt Zahl der weiteren Vorkommen, die schon vollständig in angewählten Funden lagen
     * @param ersetzt           Zahl der bestehenden Funde, die in neuen Funden aufgegangen sind
     */
    public static String hinzugefuegt(int hinzugefuegt, int bereitsGeschuetzt, int ersetzt) {
        StringBuilder text = new StringBuilder();
        text.append(hinzugefuegt).append(hinzugefuegt == 1 ? " Stelle" : " Stellen").append(" als Fund hinzugefügt.");
        if (bereitsGeschuetzt > 0) {
            text.append(bereitsGeschuetzt == 1 ? " 1 Stelle war bereits geschützt."
                    : " " + bereitsGeschuetzt + " Stellen waren bereits geschützt.");
        }
        if (ersetzt > 0) {
            text.append(ersetzt == 1 ? " 1 bestehender Fund wurde dabei ersetzt."
                    : " " + ersetzt + " bestehende Funde wurden dabei ersetzt.");
        }
        return text.toString();
    }

    /** Meldung bei zu langem Text (AK-7), mit der Obergrenze in deutscher Schreibweise (1.000.000). */
    public static String zuLang(int hoechstens) {
        return String.format(Locale.GERMANY,
                "Der Text ist zu lang (höchstens %,d Zeichen). Es wurde nicht gescannt.", hoechstens);
    }

    /** Meldung bei zu langer KI-Antwort (S-4), mit der Obergrenze in deutscher Schreibweise. */
    public static String antwortZuLang(int hoechstens) {
        return String.format(Locale.GERMANY,
                "Die Antwort ist zu lang (höchstens %,d Zeichen). Es wurde nicht zurückübersetzt.", hoechstens);
    }

    /** Zuordnungsanzeige (AK-40); {@code n = 0} nach einem Scan ohne Platzhalter. */
    public static String zuordnung(int platzhalter) {
        return "Zuordnung: " + platzhalter + " Platzhalter (nur im Arbeitsspeicher)";
    }

    /**
     * Zählung nach dem Scan und nach jeder Auswahländerung (AK-20), etwa "5 Funde, 4 angewählt, 1 fraglich. 1 Fund
     * wird nicht ersetzt."
     */
    public static String zaehlung(int funde, int angewaehlt, int fraglich) {
        StringBuilder text = new StringBuilder();
        text.append(funde).append(funde == 1 ? " Fund, " : " Funde, ");
        text.append(angewaehlt).append(" angewählt, ").append(fraglich).append(" fraglich.");
        int nichtErsetzt = funde - angewaehlt;
        if (nichtErsetzt > 0) {
            text.append(' ').append(nichtErsetzt)
                    .append(nichtErsetzt == 1 ? " Fund wird" : " Funde werden").append(" nicht ersetzt.");
        }
        return text.toString();
    }

    /** Fester Text mit dem Klassennamen der Ausnahme, nie mit deren Meldung oder Stacktrace (AK-50). */
    public static String fehlerMeldung(Throwable fehler) {
        return FEHLER + " (" + fehler.getClass().getName() + ")";
    }

    /** Wie {@link #fehlerMeldung(Throwable)}, aber mit dem festen Text der Rückübersetzung. */
    public static String rueckuebersetzenFehlerMeldung(Throwable fehler) {
        return RUECKUEBERSETZEN_FEHLGESCHLAGEN + " (" + fehler.getClass().getName() + ")";
    }
}
