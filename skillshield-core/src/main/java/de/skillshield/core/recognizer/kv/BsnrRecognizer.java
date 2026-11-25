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
 * <p>Bekannte Grenzen (Einzelheiten im Javadoc von {@link KvNumberScanner}; je Grenze ein Test
 * {@code bekannteGrenze...} in {@code BsnrGrenzenTest}):
 * <ul>
 * <li>G-B1: Eine BSNR ohne Kontextwort, in Tabellen unter einer Spaltenueberschrift (mehr als drei Woerter dazwischen)
 * oder hinter einer Leerzeile bleibt Klartext; es gibt keine Pruefziffer, die eine Zahl als BSNR auswiese.</li>
 * <li>G-B2: Zerlegte Umlaute ({@code a} plus U+0308) sind Kombinationszeichen und ergeben kein Kontextwort.</li>
 * <li>G-B3: Haupt- und Nebenbetriebsstaette sind nicht unterscheidbar (beide Typ BSNR).</li>
 * <li>Kein Kontextwort sind Abkuerzungen und Schreibweisen ausserhalb von D-8: {@code Betriebsst.-Nr.},
 * {@code BS-Nr.}, {@code BSN}, {@code BSt-Nr.}, {@code HBSNR}, Pluralformen, Tippfehler und ein Trennstrich im Wort;
 * ebenso {@code Betriebsstaette} ohne Nachsatzwort. Folgefeature-Vorschlag: der Abkuerzungspunkt als Nachsatz-Trenner
 * fuer {@code Betriebsst.-Nr.}.</li>
 * <li>Eine Leerzeile zwischen Kontextwort und Nummer beendet den Kontext (Regel der Vorgaenger).</li>
 * <li>G-B4 "Zeichen in der Nummer" (gilt auch fuer LANR, IK und den Ziffernteil der KVNR): Gruppentrenner sind
 * ausschliesslich Leerzeichen, U+00A0, U+2009, U+202F, {@code -}, {@code .} und {@code /}. <b>Jedes andere
 * Zeichen</b> in der Nummer unterbricht sie, zum Beispiel unsichtbare und Formatzeichen (Nullbreitenzeichen, U+00AD,
 * U+2060, Variantenwaehler), Strich-Varianten (U+2011, U+2013, U+2212), andere Leerraeume (U+2007, Tab) und
 * ASCII-Zeichen wie {@code _} und {@code ;}; die genannten Zeichen sind Beispiele, keine vollstaendige Liste.
 * Ein Fund entsteht nur ueber einen zusammenhaengenden Teil mit mindestens sechs Ziffern: Bei der Teilung 6+3 oder
 * 3+6 bleiben drei Ziffern im Klartext; hat kein Teil sechs Ziffern (4+5, 5+4, 3+3+3, 2+2+2+3), bleibt die ganze
 * Nummer im Klartext, ohne Fund (bei der KVNR dann auch der Buchstabe; vgl. G-K8, G-K10, G-K17 im Javadoc von
 * {@code KvNumberScanner}). Nicht geaendert; Folgefeature-Vorschlag: weitere Gruppentrenner bzw. Ueberspringen
 * unsichtbarer Zeichen (betrifft alle vier Arten, braucht Bestandsschutz und Fehlalarm-Messung).</li>
 * <li>{@code <Kontextwort> <IPv4>/<gruppierte Nummer>} (bestand schon bei der LANR): Bilden die IP-Ziffern und die
 * gruppierte Nummer zusammen eine Kette von mehr als zwoelf Ziffern, liefert der Scan den IP-Fund und einen
 * Restfund ueber die erste Gruppe; die letzte Gruppe bleibt im Klartext. Eine geschlossene Nummer ist abgedeckt.</li>
 * <li>G-K14 fuer {@code bsnr-rest}: Ein Rest von einer oder zwei Ziffern neben einem Bestandsfund bleibt stehen.</li>
 * <li>Ein unbekannter Regionalcode (auch Netzverbund 99 und Sonderkennungen 35, 75 mit unbekanntem Schluessel) ergibt
 * nur die Stufe "fraglich", nie "sicher".</li>
 * <li>G-D1: Bei Gleichstand der Konfidenz mit einer anderen Art kann der Typ "falsch" sein, die Position ist
 * geschuetzt (D-6; BSNR vor IK, KVNR und LANR, IP vor allen).</li>
 * </ul>
 *
 * <p>Datumsangaben sind keine Nummern (Feature datum-und-kontextvorrang, Teil A): Ein Segment, das genau ein gueltiges
 * Datum ist (zum Beispiel {@code 14.03.1958}) oder ein Zeitraum aus zwei Daten beginnt, ist kein Fund; Einzelheiten,
 * Formen und Grenzen im Javadoc von {@link KvNumberScanner} und {@code KvDatum}. Alles andere bleibt unveraendert.
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
