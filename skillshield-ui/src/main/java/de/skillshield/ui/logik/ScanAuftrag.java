package de.skillshield.ui.logik;

import de.skillshield.core.model.Finding;
import java.util.List;
import java.util.function.Function;

/** Momentaufnahme für den Hintergrund-Scan; unveränderlich und threadsicher, solange der Scanner es ist. */
public final class ScanAuftrag {

    private final Function<String, List<Finding>> scanner;
    private final String text;
    private final long generation;

    ScanAuftrag(Function<String, List<Finding>> scanner, String text, long generation) {
        this.scanner = scanner;
        this.text = text;
        this.generation = generation;
    }

    /** Scannt den Text der Momentaufnahme; darf auf einem Hintergrund-Thread laufen. */
    public List<Finding> ausfuehren() {
        return List.copyOf(scanner.apply(text));
    }

    String text() {
        return text;
    }

    long generation() {
        return generation;
    }

    /** Nur die Länge, nie der Text. */
    @Override
    public String toString() {
        return "ScanAuftrag[zeichen=" + text.length() + "]";
    }
}
