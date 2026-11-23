package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import java.util.List;

/**
 * Erkennt Krankenversichertennummern (KVNR, ein Buchstabe und neun Ziffern) hinter einem Kontextwort ({@code KVNR},
 * {@code Versichertennummer}, {@code eGK-Nummer}, {@code KV-Nr.} ...). Eine Nummer ohne Kontextwort ist nur dann ein
 * Fund, wenn sie die enge Form von Teil 1b hat (siehe den Absatz "ohne Kontextwort" weiter unten). Alle
 * Kontextregeln von LANR und IK gelten unveraendert: bis drei Fuellwoerter, ein Zeilenumbruch (auch {@code CR},
 * unsichtbare Zeichen, {@code LF}), Aufzaehlung, nachgestelltes Kontextwort, Komma-Regel und gesperrte Schreibweise
 * ({@code K V N R}, {@code K. V. N. R.}).
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
 * <p>KVNR ohne Kontextwort (Teil 1b, {@link KvnrOhneKontext}): Eine geschlossene Form aus einem
 * ASCII-Grossbuchstaben und neun ASCII-Ziffern (genau zehn Zeichen, keine Trenner) mit gueltiger Pruefziffer, mit
 * Wortgrenze davor und dahinter (kein Buchstabe, keine Ziffer, kein {@code _ - / \ @}, kein {@code .} oder {@code ,}
 * mit Ziffer dahinter) und ohne Wort der Negativliste (D-4, etwa {@code Rechnungsnummer}, {@code Aktenzeichen})
 * unmittelbar davor ist ein Fund ueber die zehn Zeichen mit 0.60 (fraglich) und der Quelle
 * {@code kvnr-ohne-kontext}. Ein Fund mit Kontextwort hat Vorrang; es entsteht nie ein zweiter. Eine
 * Nicht-ASCII-Ziffer direkt neben der Form sperrt den Fund (Lesart im Javadoc von {@link KvnrOhneKontext}).
 *
 * <p>Bekannte Grenzen (je Grenze ein Test {@code bekannteGrenze...}, Tabelle im Javadoc von {@code KvnrGrenzenTest};
 * Einzelheiten und alle Kennungen im Javadoc von {@link KvNumberScanner}). "Kein Fund" heisst: kein KVNR-Fund, die
 * Nummer bleibt im Klartext, sofern kein anderer Erkenner sie deckt.
 * <ul>
 * <li>G-K1 (berichtigt nach Nachtrag 2 zu T-10): Eine KVNR ohne Kontextwort ist nur als geschlossene,
 * grossgeschriebene Form mit gueltiger Pruefziffer ein Fund (Teil 1b, 0.60). Kein Fund bleiben Kleinschreibung,
 * jede Schreibweise mit Trenner, eine verletzte Wortgrenze, ein Wort der Negativliste (D-4) davor und eine
 * ungueltige Pruefziffer.</li>
 * <li>G-K3: Zwei Buchstaben, Nicht-ASCII-Zeichen in der Nummer und Woerter ausserhalb der Liste ({@code KVID},
 * {@code Versicherungsnummer}, {@code Krankenversicherungs-Nr.}) erschliessen nichts.</li>
 * <li>G-K4: Ziffern direkt an einem Wort ({@code KVNR} plus Ziffern ohne Leerzeichen, {@code AB} plus Ziffern) sind
 * keine Nummer; zwei Buchstaben direkt an gruppierten Ziffern ergeben 0.40 ueber die Gruppen nach der ersten, der
 * erste Block samt Buchstaben bleibt stehen.</li>
 * <li>G-K5: {@code K V N R} mit Ziffern ohne Trenner dahinter und eine Sperrung mit Punkt und Leerzeichen oder mit
 * mehreren Leerzeichen, auf die ein Leerzeichen und eine reine Ziffernfolge folgen, ergeben keinen Fund (der letzte
 * Buchstabe gilt als Buchstabe einer Nummer); {@code K V N R} mit genau einem Trenner vor einer reinen Ziffernfolge
 * ist dagegen ein Fund 0.40.</li>
 * <li>G-K6: {@code KVNR AB} plus Trenner plus Ziffern ergibt 0.40 ueber die reinen Ziffern.</li>
 * <li>G-K7: {@code KV-Nr.} vor einer reinen Ziffernfolge ergibt keinen Fund; auch nicht, wenn danach ein
 * nachgestelltes {@code (KVNR)} folgt.</li>
 * <li>G-K8: Unsichtbare Zeichen zwischen Buchstabe und Ziffern oder in der Nummer beenden die Kette (hoechstens 0.40
 * ueber die Ziffern; unsichtbare Zeichen vor dem Buchstaben sind dagegen kein Hindernis, solange zwischen Kontextwort
 * und unsichtbarem Zeichen ein Trenner steht; ein Variantenwaehler direkt am Buchstaben ergibt gar keinen
 * Fund).</li>
 * <li>G-K9: Ein Kontextwort unmittelbar am Buchstaben ohne Trenner (auch mit einem unsichtbaren Zeichen dazwischen)
 * ergibt keinen Fund; auch eine Erkennung ohne Kontextwort (Teil 1b) schuetzt hier nicht, weil sie vor dem
 * Buchstaben eine Wortgrenze verlangt.</li>
 * <li>G-K10: Ein Trenner ausserhalb der Gruppentrenner zwischen Buchstabe und Ziffern (Tab, zwei Leerzeichen,
 * Doppelpunkt, {@code _}, Komma, Halbgeviertstrich, Zeilenumbruch) ergibt hoechstens 0.40 ueber die Ziffern, hinter
 * {@code KV-Nr.} keinen Fund. {@code !} und {@code ?} (Satzende) zwischen Buchstabe und Ziffern oder zwischen
 * Kontextwort samt Leerzeichen und Buchstabe ergeben gar keinen Fund.</li>
 * <li>G-K11 und G-K12: Ein nachgestelltes Kontextwort in der naechsten Zeile und die Restformen des Kompositums
 * ergeben keinen Fund.</li>
 * <li>G-K13: Eine KVNR in genau vier Punkt-Gruppen verliert den Buchstaben im Fund.</li>
 * <li>G-K15: Ein Pfad oder eine URL zwischen Kontextwort und Nummer (Fuellwortzaehlung ab dem vierten Segment,
 * Satzende am Dateiendungspunkt) laesst die Nummer ohne Fund, auch in kurzen Faellen; bei URLs zaehlen Schema und
 * jedes Host-Label als Segment, abgedeckt bleibt nur eine sehr kurze URL (Schema und hoechstens zwei weitere
 * Segmente, kein Punkt hinter einem langen Wort, keine Query); jede URL mit einem laengeren Host-Label vor einem
 * Punkt, mit mehr als drei Segmenten oder mit Query bleibt ohne Fund. Mit Teil 1b ist die geschlossene,
 * grossgeschriebene, gueltige Form in dieser Stellung ein Fund (0.60); die gruppierte und die kleingeschriebene Form
 * sowie Nummern mit falscher Pruefziffer bleiben ohne Fund.</li>
 * <li>G-K18 (Restrisiko, Nachtrag 2 zu T-11): Eine gueltige, geschlossene Nummer ohne Kontextwort mit {@code _},
 * {@code -}, {@code /}, {@code \} oder {@code @} direkt daran (Dateiname ohne Pfad, URL ohne Schema, Nummer per
 * {@code /} an einer IP) bleibt im Klartext, wenn kein E-Mail-, URL-, Pfad- oder IP-Fund sie umschliesst; Einzelheiten
 * im Javadoc von {@link KvnrOhneKontext}.</li>
 * <li>G-K16: Derselbe Wert an einer zweiten, nicht erkannten Stelle bleibt stehen (kein Leak-Check).</li>
 * <li>G-K17: Jedes Kombinationszeichen (Kategorien Mn, Mc, Me) und die Fuellzeichen U+115F, U+1160, U+3164 und
 * U+FFA0 direkt vor dem Buchstaben oder zwischen Buchstabe und Ziffern verhindern den Fund in Buchstabenform.</li>
 * </ul>
 * Ein Buchstabe, der ein gesperrt geschriebenes Kontextwort zu einem Kontextwort ergaenzt, gilt nur dann als dessen
 * Ende, wenn das gesperrte Kontextwort einen einzeichigen Sperr-Trenner hat und hinter dem Buchstaben ein Trenner
 * folgt.
 *
 * <p>IPv4-Gestalt (Nachtrag zu T-6): Stehen direkt hinter dem Buchstaben (oder Buchstabe und einem Gruppentrenner)
 * vier durch je einen Punkt getrennte Gruppen von je ein bis drei Ziffern, ist der Buchstabe kein Nummernanfang; es
 * gibt keinen Fund in Buchstabenform, die Ziffern gelten wie eine reine Ziffernfolge (hoechstens 0.40, hinter
 * {@code KV-Nr.} kein Fund). Ein bestehender IP-Fund bleibt so erhalten. Bekannte Grenze: Eine KVNR, deren neun
 * Ziffern mit Punkten in genau vier Gruppen geschrieben sind, verliert den Buchstaben im Fund; in zwei oder drei
 * Gruppen und mit anderen Trennern bleibt sie unveraendert.
 *
 * <p>Nachgestelltes Kompositum (nur KVNR): Steht hinter einer Nummer ein Wort mit unmittelbar folgendem {@code -} und
 * einem Kontextwort ({@code N (Patienten-KVNR)}, {@code N, Pat.-KVNR}, {@code N (eGK-Versichertennummer)}), zaehlt das
 * Ganze als nachgestelltes Kontextwort: 0.80, hinter Komma oder Semikolon 0.60 (ungueltige Pruefziffer 0.50). Zwei
 * Woerter davor, ein Strich mit Leerraum und ein Wort, das kein Kontextwort bildet ({@code Patienten-VKNR}), ergeben
 * keinen Fund. LANR und IK kennen diese Form nicht.
 *
 * <p>Vorrang des Bestands (Nachtrag 2 und 3 zu T-6): Der Erkenner ist {@linkplain #nachrangig() nachrangig}. Der
 * {@code TextScanner} schneidet jeden seiner Kandidaten, der sich mit den aufgeloesten Bestandsfunden (den Funden der
 * Typen E-Mail, IP, URL oder Pfad im Scan ohne die nachrangigen Erkenner, Einzelheiten im Javadoc des
 * {@code TextScanner}) ueberschneidet, auf die Teile ausserhalb zu; diese Funde bleiben so unveraendert. Ein
 * Reststueck mit mindestens drei Ziffern bleibt als Fund (Quelle {@value #SOURCE_REST}, hoechstens 0,80). Bekannte
 * Grenze G-K14: Der Teil einer Nummer im anderen Fund traegt dessen Typ; ein Buchstabe oder ein Rest mit weniger als
 * drei Ziffern ausserhalb bleibt stehen. Bekannte Grenze G-D2: Ein LANR- oder IK-Kontextwort vor einer KVNR-Form
 * schuetzt nur die Ziffern (Fund LANR bzw. IK); mit einem KVNR-Kontextwort davor umfasst der Fund auch den
 * Buchstaben, und bei einer gueltigen, geschlossenen, grossgeschriebenen Form (Teil 1b, Kollisionsfall K-1) ist der
 * Fund ein KVNR-Fund 0.60 ueber Buchstabe und Ziffern, der den LANR- oder IK-Fund umschliesst. Die bekannten Grenzen
 * von Teil 1b stehen vollstaendig im Javadoc von {@link KvnrOhneKontext}. Auf Erkennerebene (ohne Scanner) liefert
 * der Erkenner die Kandidaten unveraendert.
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

    /** Quelle der Reststuecke, die der {@code TextScanner} beim Zuschneiden an Bestandsfunden behaelt. */
    public static final String SOURCE_REST = "kvnr-rest";

    @Override
    public List<Finding> detect(String text) {
        return KvnrOhneKontext.ergaenze(text, KvNumberScanner.detect(KvArt.KVNR, text));
    }

    /** Der KVNR-Erkenner ist nachrangig: Der {@code TextScanner} schneidet seine Kandidaten, die einen aufgeloesten
     * Bestandsfund (E-Mail, IP, URL oder Pfad im Scan ohne die nachrangigen Erkenner) schneiden, auf die Teile
     * ausserhalb zu. */
    @Override
    public boolean nachrangig() {
        return true;
    }

    @Override
    public String restQuelle() {
        return SOURCE_REST;
    }
}
