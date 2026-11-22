package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import java.util.List;

/**
 * Oeffentliche Huellen um die eingefrorene Kopie {@link KvNumberScannerStandLuecken}, damit Tests ausserhalb des
 * Pakets (zum Beispiel der Scanner-Vergleich in {@code KvStandLueckenDifferenzTest}) den Stand am Ende von
 * kv-kontext-luecken als {@link Recognizer} verwenden koennen. Nur Testcode.
 */
public final class KvStandLuecken {

    private KvStandLuecken() {
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
            return KvNumberScannerStandLuecken.detect(art, text);
        }
    }
}
