package de.skillshield.ui.logik;

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

    private Texte() {
    }

    /** Meldung bei zu langem Text (AK-7), mit der Obergrenze in deutscher Schreibweise (1.000.000). */
    public static String zuLang(int hoechstens) {
        return String.format(Locale.GERMANY,
                "Der Text ist zu lang (höchstens %,d Zeichen). Es wurde nicht gescannt.", hoechstens);
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
}
