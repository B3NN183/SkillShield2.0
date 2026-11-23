package de.skillshield.core.recognizer;

import de.skillshield.core.model.Finding;
import java.util.List;

/**
 * Ein Erkenner für eine Art sensibler Angaben.
 *
 * <p>Implementierungen sind zustandslos und deterministisch, enthalten keinen Netzwerk- und keinen
 * Dateisystemzugriff und geben keinen Fundwert in Meldungen aus.
 */
public interface Recognizer {

    /**
     * Liefert Kandidaten für den Text. Die Kandidaten dürfen sich überlappen; die Auswahl übernimmt der
     * {@link TextScanner}. Für jeden Kandidaten gilt {@code text.substring(start, end).equals(originalValue)}
     * (Positionen als UTF-16-Indizes, {@code start} inklusive, {@code end} exklusiv). Der Text wird nicht
     * verändert.
     *
     * <p>Die Quelle {@code zusammengelegt} ({@link Finding#SOURCE_MERGED}) ist dem Konfliktauflöser vorbehalten.
     * Wirft ein Erkenner eine {@link RuntimeException}, gibt der {@link TextScanner} sie ohne Meldung und ohne
     * Ursache weiter; Erkenner sollen trotzdem keinen Text in Meldungen schreiben.
     *
     * @param text der zu durchsuchende Text, nie {@code null}
     * @return die Kandidaten, nie {@code null}
     */
    List<Finding> detect(String text);

    /**
     * Ob dieser Erkenner nachrangig ist (Grundsatz "Bestand hat Vorrang", Plan kvnr-bsnr-erkennung, Nachtrag 2 und 3
     * zu T-6). Der {@link TextScanner} schneidet Kandidaten eines nachrangigen Erkenners, die sich mit einem
     * aufgeloesten Bestandsfund ueberschneiden (einem Fund der Typen E-Mail, IP, URL oder Pfad im Scan ohne die
     * nachrangigen Erkenner, nicht mit rohen Kandidaten), auf die Teile ausserhalb zu, bevor er alle Konflikte
     * aufloest (Zuschneiden statt Verwerfen). Ein Reststueck bleibt nur mit mindestens drei Ziffern, hat die Quelle
     * {@link #restQuelle()} und hoechstens die Konfidenz 0,80; ein einzelner Buchstabe oder ein Rest mit weniger
     * Ziffern bleibt stehen (bekannte Grenze G-K14, Einzelheiten im Javadoc des {@link TextScanner}). Standard:
     * {@code false}.
     *
     * @return {@code true} fuer die Erkenner der neuen Arten (KVNR, spaeter BSNR)
     */
    default boolean nachrangig() {
        return false;
    }

    /**
     * Quelle der Restfunde, die beim Zuschneiden eines Kandidaten dieses (nachrangigen) Erkenners entstehen
     * (Format wie {@link Finding#source()}, z. B. {@code kvnr-rest}). Ein nachrangiger Erkenner ueberschreibt sie
     * mit seiner eigenen Quelle. Ohne Bedeutung, solange {@link #nachrangig()} {@code false} ist.
     *
     * @return die Quelle, nie {@code null}
     */
    default String restQuelle() {
        return "rest";
    }
}
