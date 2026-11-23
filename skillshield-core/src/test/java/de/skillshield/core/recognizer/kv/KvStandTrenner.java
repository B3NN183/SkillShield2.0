package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import java.util.List;

/**
 * Oeffentliche Huellen um die eingefrorene Kopie {@link KvNumberScannerStandTrenner}, damit Tests ausserhalb des
 * Pakets (zum Beispiel {@code KvStandTrennerDifferenzTest}) den Stand am Ende von kv-trenner-und-umbruch als
 * {@link Recognizer} verwenden koennen. Nur Testcode; wird nie geaendert.
 */
public final class KvStandTrenner {

    private KvStandTrenner() {
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
            return KvNumberScannerStandTrenner.detect(art, text);
        }
    }
}
