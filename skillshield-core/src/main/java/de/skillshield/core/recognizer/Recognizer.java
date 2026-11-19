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
}
