package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import java.util.List;

/**
 * Erkennt Betriebsstaettennummern (BSNR, neun Ziffern) hinter einem Kontextwort (Feature kvnr-bsnr-erkennung, Teil 2,
 * Anforderung D-8, AK-25 bis AK-29). Eine Zahl ohne Kontextwort ist nie eine BSNR (E-2).
 *
 * <p>Kontextwoerter (Gross- und Kleinschreibung gleichgueltig, ganze Woerter, {@code ae} und der Umlaut gleichwertig):
 * {@code BSNR}, {@code BSNRNr}, {@code NBSNR}, {@code Betriebsstaettennummer}, {@code Betriebsstaettennr},
 * {@code Nebenbetriebsstaettennummer}, {@code Nebenbetriebsstaettennr}; mit Nachsatzwort {@code Nr} oder
 * {@code Nummer}: {@code Betriebsstaette}, {@code Betriebsstaetten}, {@code Nebenbetriebsstaette},
 * {@code Nebenbetriebsstaetten} ({@code Betriebsstaetten-Nr.}). {@code NBSNR} ergibt denselben Typ BSNR; der Review
 * unterscheidet Haupt- und Nebenbetriebsstaette nicht. Keine Kontextwoerter sind {@code BSNRX}, {@code Betriebsstaette}
 * ohne Nachsatzwort, {@code Betrieb}, {@code KV-Nr.} und {@code Tel.}.
 *
 * <p>Alle Kontextregeln von LANR und IK gelten unveraendert: bis drei Fuellwoerter, ein Zeilenumbruch (auch
 * {@code CR}, unsichtbare Zeichen, {@code LF}), Aufzaehlung, nachgestelltes Kontextwort, Komma-Regel und gesperrte
 * Schreibweise ({@code B S N R}, {@code B. S. N. R.}, {@code N B S N R}, Weit-Form). Ziffern duerfen in Gruppen mit
 * genau einem Trenner (Leerzeichen, geschuetztes Leerzeichen, {@code -}, {@code .}, {@code /}) stehen; zwei Trenner
 * hintereinander oder ein Trenner am Ende gehoeren nicht zum Fund. Der Fund umfasst nur die Ziffern und die inneren
 * Trenner, nie das Kontextwort.
 *
 * <p>Konfidenz (D-8): bekannter Regionalcode ({@link BsnrRegionalcodes}, bei 35 und 75 gelten die Stellen 3 und 4) und
 * direktes Kontextwort 0.90 (sicher, Quelle {@value #SOURCE}); bekannter Regionalcode bei weiterem Kontext 0.80
 * (wahrscheinlich), nachgestellt hinter genau einem Komma oder Semikolon 0.60, gesperrtes Kontextwort hoechstens 0.80;
 * unbekannter Regionalcode 0.50 (Quelle {@value #SOURCE_REGIONALCODE_UNBEKANNT}), darunter die Netzverbundnummern mit
 * Praefix 99; Laenge 6 bis 12 Ziffern ausser neun 0.40 (Quelle {@value #SOURCE_LAENGE_ABWEICHEND}); weniger als 6 oder
 * mehr als 12 Ziffern ergeben keinen Fund. Der Regionalcode ist nur ein schwaches Plausibilitaetsmerkmal: Er
 * bestimmt Stufe und Quelle, nie, ob ein Fund entsteht (Schutz geht vor). Weder Fund noch Meldung enthalten die Liste
 * der Regionalcodes.
 *
 * <p>Nachgestelltes Kompositum (Entscheidung T-13): Wie bei der KVNR zaehlt ein Wort mit unmittelbar folgendem
 * {@code -} und einem Kontextwort ({@code N (Praxis-BSNR)}, {@code N, Pat.-BSNR}) als nachgestelltes Kontextwort
 * (0.80, hinter Komma oder Semikolon 0.60). LANR und IK kennen diese Form weiterhin nicht
 * ({@link KvArt#nachgestelltesKompositum()}).
 *
 * <p>Bekannte Grenzen (Einzelheiten im Javadoc von {@link KvNumberScanner}): eine BSNR ohne Kontextwort, in Tabellen
 * unter einer Spaltenueberschrift oder hinter einer Leerzeile bleibt Klartext; zerlegte Umlaute ({@code a} plus
 * U+0308) sind Kombinationszeichen und ergeben kein Kontextwort; bei Gleichstand der Konfidenz mit einer anderen
 * Art kann der Typ "falsch" sein, die Position ist geschuetzt (D-6).
 *
 * <p>Vorrang des Bestands: Der Erkenner ist {@linkplain #nachrangig() nachrangig} wie der KVNR-Erkenner; der
 * {@code TextScanner} schneidet seine Kandidaten, die einen aufgeloesten Bestandsfund (E-Mail, IP, URL, Pfad)
 * ueberschneiden, auf die Teile ausserhalb zu (Reststuecke mit Quelle {@value #SOURCE_REST}). Auf Erkennerebene
 * liefert er die Kandidaten unveraendert.
 *
 * <p>Der Erkenner ist zustandslos und deterministisch; Regeln und Linearitaetsbeleg stehen im Javadoc von
 * {@link KvNumberScanner}. Es entsteht kein Netz- oder Dateizugriff, und Meldungen enthalten keinen Text und keine
 * Ziffern. {@code null} fuehrt zu einer {@link NullPointerException} mit der Meldung {@code text}.
 */
public final class BsnrRecognizer implements Recognizer {

    /** Quelle der Funde mit bekanntem Regionalcode. */
    public static final String SOURCE = "bsnr";

    /** Quelle der Funde mit neun Ziffern und unbekanntem Regionalcode. */
    public static final String SOURCE_REGIONALCODE_UNBEKANNT = "bsnr-regionalcode-unbekannt";

    /** Quelle der Funde mit einer Laenge von 6 bis 12 Ziffern ausser neun. */
    public static final String SOURCE_LAENGE_ABWEICHEND = "bsnr-laenge-abweichend";

    /** Quelle der Reststuecke, die der {@code TextScanner} beim Zuschneiden an Bestandsfunden behaelt. */
    public static final String SOURCE_REST = "bsnr-rest";

    @Override
    public List<Finding> detect(String text) {
        return KvNumberScanner.detect(KvArt.BSNR, text);
    }

    /** Der BSNR-Erkenner ist nachrangig: Bestandsfunde (E-Mail, IP, URL, Pfad) haben Vorrang (Zuschneiden). */
    @Override
    public boolean nachrangig() {
        return true;
    }

    @Override
    public String restQuelle() {
        return SOURCE_REST;
    }
}
