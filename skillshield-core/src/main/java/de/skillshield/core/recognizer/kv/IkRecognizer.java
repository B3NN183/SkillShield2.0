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
 * Quelle), ebenso mit erweiterten Trennern ab vier Buchstaben ({@code I. K. N. R.}, {@code I  K  N  R},
 * {@code I<Tab>K<Tab>N<Tab>R}; Deckel 0.80), mit gesperrt geschriebenem Zwei-Buchstaben-Wort ({@code I K},
 * {@code I.K.}, {@code I-K}) hoechstens 0.50,
 * nur fuer die unmittelbar folgende Nummer (gleiche Quelle, auch bei gueltiger Pruefziffer; bekannte Grenze 14g:
 * auch eine Nummer, die {@code I K} direkt erschliesst und die bisher nur ueber ein nachgestelltes Kontextwort
 * erkannt wurde, hat nun hoechstens 0.50 statt 0.80 bzw. 0.60, z. B. {@code I K N (IK)}, {@code I K N, IK},
 * {@code I K N1 N2 IK} (nur N1), {@code I K N IK M} (N 0.50, M 0.92); die Position bleibt abgedeckt),
 * ungueltig 0.50 (Quelle {@value #SOURCE_PRUEFZIFFER_UNGUELTIG}), Laenge 6 bis 12 ausser neun 0.40 (Quelle
 * {@value #SOURCE_LAENGE_ABWEICHEND}). Bei bereits laufendem Kontext ist {@code I K} kein Fuellwort, und ein
 * Nachsatzwort dahinter ist es ebenfalls nicht ({@code IK: Praxis I K Nr N}: 0.80, Grenze 14h geschlossen). Das
 * Zwei-Buchstaben-Wort mit erweiterten Trennern ({@code I. K.}, {@code I  K}, {@code I<Tab>K}) ist kein Kontextwort
 * (Rest 14cR). Der Fund umfasst nur die Ziffern samt inneren Trennern.
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
