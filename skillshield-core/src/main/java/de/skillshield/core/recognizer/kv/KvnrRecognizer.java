package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import java.util.List;

/**
 * Erkennt Krankenversichertennummern (KVNR, ein Buchstabe und neun Ziffern) hinter einem Kontextwort ({@code KVNR},
 * {@code Versichertennummer}, {@code eGK-Nummer}, {@code KV-Nr.} ...). Eine Nummer ohne Kontextwort ist kein Fund
 * (die Erkennung ohne Kontextwort ist nicht Teil dieser Klasse). Alle Kontextregeln von LANR und IK gelten
 * unveraendert: bis drei Fuellwoerter, ein Zeilenumbruch (auch {@code CR}, unsichtbare Zeichen, {@code LF}),
 * Aufzaehlung, nachgestelltes Kontextwort, Komma-Regel und gesperrte Schreibweise ({@code K V N R},
 * {@code K. V. N. R.}).
 *
 * <p>Die Pruefziffer ({@link KvnrPruefziffer}) bestimmt nur Stufe und Quelle: Buchstabe und neun Ziffern mit
 * gueltiger Pruefziffer und direktem Kontext 0.95 (Quelle {@value #SOURCE}), gueltig bei weiterem Kontext 0.80
 * (gleiche Quelle), gueltig und nachgestellt mit genau einem Komma oder Semikolon 0.60, gueltig mit gesperrt
 * geschriebenem Kontextwort hoechstens 0.80, ungueltige Pruefziffer 0.50 (Quelle
 * {@value #SOURCE_PRUEFZIFFER_UNGUELTIG}), abweichende Form 0.40 (Quelle {@value #SOURCE_FORM_ABWEICHEND}):
 * Buchstabe und 6 bis 12 Ziffern ausser neun oder eine reine Ziffernfolge von 6 bis 12 Ziffern. Der Fund umfasst
 * Buchstabe, Trenner und Ziffern, nie das Kontextwort. Kleinbuchstaben und innere Trenner (ein Trenner zwischen
 * Buchstabe und Ziffern, Gruppen mit genau einem Trenner) gelten ohne Besonderheit; die Pruefziffer wird ueber die
 * zehn Zeichen ohne Trenner gebildet. Das Vorwort {@code KV} ({@code KV-Nr.}, {@code KV-Nummer}) erschliesst nur die
 * Buchstabenform; {@code VKNR}, {@code Versicherungsnummer}, {@code Rentenversicherungsnummer}, {@code KV} allein und
 * {@code Nr.} allein sind keine Kontextwoerter, und {@code KV-Nr.} ist fuer LANR und IK kein Kontextwort.
 *
 * <p>Bekannte Grenzen (je ein Test {@code bekannteGrenze...} folgt in T-8): zwei Buchstaben, Nicht-ASCII-Zeichen und
 * Woerter ausserhalb der Liste ({@code KVID}) erschliessen nichts; Ziffern direkt an einem Wort
 * ({@code KVNR} plus Ziffern ohne Leerzeichen) sind keine Nummer; {@code KVNR AB} plus Ziffern ergibt ohne Trenner
 * keinen Fund und mit Trenner 0.40 ueber die reinen Ziffern, zwei Buchstaben direkt an gruppierten Ziffern ergeben 0.40
 * ueber die Gruppen nach der ersten (G-K4; der erste Block samt Buchstaben bleibt ohne Fund); {@code KV-Nr.} vor
 * einer reinen Ziffernfolge ergibt keinen Fund; unsichtbare Zeichen zwischen
 * Buchstabe und Ziffern oder in der Nummer beenden die Kette (unsichtbare Zeichen vor dem Buchstaben sind dagegen
 * kein Hindernis, sofern zwischen Kontextwort und unsichtbarem Zeichen ein Trenner oder Direktzeichen steht; haengt
 * das unsichtbare Zeichen direkt am Kontextwort, Buchstabe und Ziffern folgen ohne sichtbaren Trenner, gilt die
 * folgende Grenze "Kontextwort direkt am Buchstaben"); ein Kontextwort unmittelbar am Buchstaben ohne Trenner
 * ({@code KVNR} und Buchstabe sind ein Wort) und ein Trenner ausserhalb der Gruppentrenner zwischen Buchstabe und
 * Ziffern (Tab, zwei Leerzeichen, Doppelpunkt, {@code _}, Komma, Halbgeviertstrich, Zeilenumbruch) ergeben
 * hoechstens 0,40 ueber die Ziffern, hinter {@code KV-Nr.} keinen Fund. G-K5: Bei gesperrtem Kontextwort mit genau
 * einem Trenner vor einer reinen Ziffernfolge
 * ({@code K V N R 1234...}) ist der letzte Sperr-Buchstabe kein Nummernbuchstabe; die Folge hat 0,40. Ein
 * Buchstabe, der ein gesperrt geschriebenes Kontextwort zu einem Kontextwort ergaenzt, gilt nur dann als dessen
 * Ende, wenn das gesperrte Kontextwort einen einzeichigen Sperr-Trenner hat und hinter dem Buchstaben ein Trenner
 * folgt; nicht bei einer Sperrung mit Punkt und Leerzeichen oder mehreren Leerzeichen (Weit-Folge) und nicht bei
 * {@code K V N R} mit Ziffern ohne Trenner dahinter (dann Buchstabe einer Nummer).
 *
 * <p>Nachgestelltes Kompositum (nur KVNR): Steht hinter einer Nummer ein Wort mit unmittelbar folgendem {@code -} und
 * einem Kontextwort ({@code N (Patienten-KVNR)}, {@code N, Pat.-KVNR}, {@code N (eGK-Versichertennummer)}), zaehlt das
 * Ganze als nachgestelltes Kontextwort: 0.80, hinter Komma oder Semikolon 0.60 (ungueltige Pruefziffer 0.50). Zwei
 * Woerter davor, ein Strich mit Leerraum und ein Wort, das kein Kontextwort bildet ({@code Patienten-VKNR}), ergeben
 * keinen Fund. LANR und IK kennen diese Form nicht.
 *
 * <p>Der Erkenner ist zustandslos und deterministisch; die Regeln und der Linearitaetsbeleg stehen im Javadoc von
 * {@link KvNumberScanner}. Es entsteht kein Netz- oder Dateizugriff, und Meldungen enthalten keinen Text, keine
 * Ziffern und nie die berechnete Pruefziffer. {@code null} fuehrt zu einer {@link NullPointerException} mit der
 * Meldung {@code text}.
 */
public final class KvnrRecognizer implements Recognizer {

    /** Quelle der Funde mit gueltiger Pruefziffer. */
    public static final String SOURCE = "kvnr";

    /** Quelle der Funde mit Buchstabe, neun Ziffern und ungueltiger Pruefziffer. */
    public static final String SOURCE_PRUEFZIFFER_UNGUELTIG = "kvnr-pruefziffer-ungueltig";

    /** Quelle der Funde mit abweichender Form (6 bis 12 Ziffern ausser neun mit Buchstabe, reine Ziffernfolge). */
    public static final String SOURCE_FORM_ABWEICHEND = "kvnr-form-abweichend";

    @Override
    public List<Finding> detect(String text) {
        return KvNumberScanner.detect(KvArt.KVNR, text);
    }
}
