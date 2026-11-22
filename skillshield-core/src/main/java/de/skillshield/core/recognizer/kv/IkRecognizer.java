package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import java.util.List;

/**
 * Erkennt Institutionskennzeichen (IK, neun Ziffern) hinter einem Kontextwort ({@code IK}, {@code IK-Nr.},
 * {@code IKNR}, {@code IK-Nummer}, {@code Institutionskennzeichen} ...). Eine Zahl ohne Kontextwort ist kein Fund.
 * Die Pruefziffer bestimmt nur Stufe und Quelle: gueltig und direkter Kontext 0.92 (Quelle {@value #SOURCE}),
 * gueltig bei weiterem Kontext (Fuellwoerter, Zeilenumbruch, Aufzaehlung, nachgestellt) 0.80 (gleiche Quelle),
 * gueltig und nachgestellt mit genau einem Komma oder Semikolon vor dem Kontextwort 0.60 (gleiche Quelle),
 * gueltig mit gesperrt geschriebenem Kontextwort ab drei Buchstaben ({@code I K N R}) hoechstens 0.80 (gleiche
 * Quelle),
 * ungueltig 0.50 (Quelle {@value #SOURCE_PRUEFZIFFER_UNGUELTIG}), Laenge 6 bis 12 ausser neun 0.40 (Quelle
 * {@value #SOURCE_LAENGE_ABWEICHEND}). Der Fund umfasst nur die Ziffern samt inneren Trennern.
 *
 * <p>Der Erkenner ist zustandslos und deterministisch; die Regeln und der Linearitaetsbeleg stehen im Javadoc von
 * {@link KvNumberScanner}. Es entsteht kein Netz- oder Dateizugriff, und Meldungen enthalten keinen Text.
 */
public final class IkRecognizer implements Recognizer {

    /** Quelle der Funde mit gueltiger Pruefziffer. */
    public static final String SOURCE = "ik";

    /** Quelle der Funde mit neun Ziffern und ungueltiger Pruefziffer. */
    public static final String SOURCE_PRUEFZIFFER_UNGUELTIG = "ik-pruefziffer-ungueltig";

    /** Quelle der Funde mit einer Ziffernzahl von 6 bis 12 ausser neun. */
    public static final String SOURCE_LAENGE_ABWEICHEND = "ik-laenge-abweichend";

    @Override
    public List<Finding> detect(String text) {
        return KvNumberScanner.detect(KvArt.IK, text);
    }
}
