package de.skillshield.core.recognizer.geheimnis;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Eingefrorene Kopie des {@code PasswortRecognizer} im Stand {@code ge-t4-passwort} (Plan geheimnisse-erkennung,
 * Nachtrag "T-5 verkleinert", Punkt 5): ruft die woertliche Kopie {@link KontextRegelStandT4} auf. Sie ist oeffentlich,
 * damit Tests ausserhalb des Pakets den Stand T-4 als Massstab nutzen koennen. Nicht aendern.
 */
public final class PasswortRecognizerStandT4 implements Recognizer {

    @Override
    public List<Finding> detect(String text) {
        Objects.requireNonNull(text, "text");
        List<Finding> kandidaten = new ArrayList<>();
        KontextRegelStandT4.finde(text, Art.PASSWORT, kandidaten);
        return List.copyOf(kandidaten);
    }
}
