package de.skillshield.core.recognizer.geheimnis;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Eingefrorene Kopie des {@code PasswortRecognizer} im Stand {@code ge-t5-schwache-verbinder} (Plan
 * geheimnisse-erkennung, T-6): ruft die woertlichen Kopien {@link KontextRegelStandT5} und
 * {@link SchwacheVerbinderStandT5} auf (nur die Klassennamen sind geaendert) und fuehrt die Ergebnisse so zusammen
 * wie der Erkenner am Tag {@code ge-t5-schwache-verbinder}. Sie ist oeffentlich, damit Tests ausserhalb des Pakets den
 * Stand T-5 als Massstab nutzen koennen. Nicht aendern.
 */
public final class PasswortRecognizerStandT5 implements Recognizer {

    @Override
    public List<Finding> detect(String text) {
        Objects.requireNonNull(text, "text");
        List<Finding> erster = new ArrayList<>();
        KontextRegelStandT5.finde(text, Art.PASSWORT, erster);
        List<Finding> zweiter = new ArrayList<>();
        SchwacheVerbinderStandT5.finde(text, Art.PASSWORT, erster, zweiter);
        if (zweiter.isEmpty()) {
            return List.copyOf(erster);
        }
        List<Finding> kandidaten = new ArrayList<>(erster.size() + zweiter.size());
        int a = 0;
        int b = 0;
        while (a < erster.size() || b < zweiter.size()) {
            boolean ersterZuerst = b >= zweiter.size()
                    || (a < erster.size() && erster.get(a).start() < zweiter.get(b).start());
            kandidaten.add(ersterZuerst ? erster.get(a++) : zweiter.get(b++));
        }
        return List.copyOf(kandidaten);
    }
}
