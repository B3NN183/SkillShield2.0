package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import java.util.List;

/**
 * Erkennt Lebenslange Arztnummern (LANR, neun Ziffern) hinter einem Kontextwort ({@code LANR}, {@code Arztnummer},
 * {@code Arzt-Nr.} ...). Eine Zahl ohne Kontextwort ist kein Fund. Die Pruefziffer bestimmt nur Stufe und Quelle:
 * gueltig und direkter Kontext 0.95 (Quelle {@value #SOURCE}), gueltig bei weiterem Kontext (Fuellwoerter,
 * Zeilenumbruch, Aufzaehlung, nachgestellt) 0.80 (gleiche Quelle), gueltig und nachgestellt mit genau einem
 * Komma oder Semikolon vor dem Kontextwort 0.60 (gleiche Quelle), gueltig mit gesperrt geschriebenem Kontextwort
 * ab drei Buchstaben ({@code L A N R}, {@code L.A.N.R.}) hoechstens 0.80 (gleiche Quelle), ebenso mit erweiterten
 * Trennern ab vier Buchstaben ({@code L. A. N. R.}, {@code L  A  N  R}, {@code L<Tab>A<Tab>N<Tab>R}; Deckel 0.80 in
 * jedem Zustand, nachgestellt mit Komma 0.60), ungueltig 0.50 (Quelle
 * {@value #SOURCE_PRUEFZIFFER_UNGUELTIG}), Laenge 6 bis 12 ausser neun 0.40 (Quelle
 * {@value #SOURCE_LAENGE_ABWEICHEND}). Ein Zeilenumbruch aus {@code CR}, unsichtbaren Zeichen und {@code LF} zaehlt als
 * einer. Der Fund umfasst nur die Ziffern samt inneren Trennern.
 *
 * <p>Datumsangaben sind keine Nummern (Feature datum-und-kontextvorrang, Teil A): Ein Segment, das genau ein gueltiges
 * Datum ist (zum Beispiel {@code 14.03.1958}) oder ein Zeitraum aus zwei Daten beginnt, ist kein Fund; Einzelheiten,
 * Formen und Grenzen im Javadoc von {@link KvNumberScanner} und {@code KvDatum}. Alles andere bleibt unveraendert.
 *
 * <p>Bekannte Grenzen (je ein Test {@code bekannteGrenze...}): 15R (CR mit Leerraum, Buchstabe oder nicht
 * unsichtbarem Zeichen vor LF sind zwei Umbrueche), 14cR (erweiterte Trenner ausserhalb von zwei bis drei
 * Leerraumzeichen, Punkt mit Tab), 14d und 14e mit erweiterten Trennern (ein davor- oder
 * dahinterstehender Einzelbuchstabe verlaengert die Folge, auch bei der nachgestellten Form {@code N L. A. N. R. B.}),
 * Folge 14cF (Initialen, die ein Kontextwort ergeben, erschliessen eine Zahl).
 *
 * <p>Der Erkenner ist zustandslos und deterministisch; die Regeln und der Linearitaetsbeleg stehen im Javadoc von
 * {@link KvNumberScanner}. Es entsteht kein Netz- oder Dateizugriff, und Meldungen enthalten keinen Text.
 */
public final class LanrRecognizer implements Recognizer {

    /** Quelle der Funde mit gueltiger Pruefziffer. */
    public static final String SOURCE = "lanr";

    /** Quelle der Funde mit neun Ziffern und ungueltiger Pruefziffer. */
    public static final String SOURCE_PRUEFZIFFER_UNGUELTIG = "lanr-pruefziffer-ungueltig";

    /** Quelle der Funde mit einer Ziffernzahl von 6 bis 12 ausser neun. */
    public static final String SOURCE_LAENGE_ABWEICHEND = "lanr-laenge-abweichend";

    @Override
    public List<Finding> detect(String text) {
        return KvNumberScanner.detect(KvArt.LANR, text);
    }
}
