package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import java.util.List;

/**
 * Oeffentliche Huellen um die eingefrorene Kopie {@link KvNumberScannerVorgaenger}, damit Tests ausserhalb des Pakets
 * (zum Beispiel der Scanner-Vergleich in {@code KvVorgaengerDifferenzTest}) den Stand vor kv-kontext-luecken als
 * {@link Recognizer} verwenden koennen. Nur Testcode.
 */
public final class KvVorgaenger {

    private KvVorgaenger() {
    }

    /** LANR-Erkenner des eingefrorenen Standes. */
    public static Recognizer lanr() {
        return new Huelle(KvArt.LANR);
    }

    /** IK-Erkenner des eingefrorenen Standes. */
    public static Recognizer ik() {
        return new Huelle(KvArt.IK);
    }

    private record Huelle(KvArt art) implements Recognizer {
        @Override
        public List<Finding> detect(String text) {
            return KvNumberScannerVorgaenger.detect(art, text);
        }
    }
}
