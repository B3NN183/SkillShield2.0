package de.skillshield.core.pseudonymize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Unveränderliche Zuordnung kanonischer Platzhalter ({@code [[TYP_n]]}) zu Originalwerten. Die Originalwerte sind
 * die sensibelsten Daten des Programms: {@link #toString()}, Ausnahmemeldungen und die zurückgegebenen
 * Sichten ({@link #placeholders()}, {@link #typeNames()}) enthalten sie nicht.
 */
public final class PlaceholderMapping {

    private static final PlaceholderMapping EMPTY = new PlaceholderMapping(Map.of(), Set.of());

    private final Map<String, String> values;
    private final Set<String> typeNames;

    private PlaceholderMapping(Map<String, String> values, Set<String> typeNames) {
        this.values = values;
        this.typeNames = typeNames;
    }

    /** Leere Zuordnung. */
    public static PlaceholderMapping empty() {
        return EMPTY;
    }

    /**
     * Erzeugt eine Zuordnung als defensive Kopie. Die Reihenfolge von {@link #placeholders()} folgt der Nummer
     * (aufsteigend), bei gleicher Nummer dem Platzhaltertext.
     *
     * @throws NullPointerException     wenn die Map, ein Schlüssel oder ein Wert {@code null} ist
     * @throws IllegalArgumentException wenn ein Schlüssel kein kanonischer Platzhalter ist (Meldung ohne Wert)
     */
    public static PlaceholderMapping of(Map<String, String> placeholderToOriginal) {
        Objects.requireNonNull(placeholderToOriginal, "placeholderToOriginal");
        if (placeholderToOriginal.isEmpty()) {
            return EMPTY;
        }
        record Entry(long number, String placeholder, String type, String original) {
        }
        List<Entry> entries = new ArrayList<>(placeholderToOriginal.size());
        for (Map.Entry<String, String> e : placeholderToOriginal.entrySet()) {
            String key = Objects.requireNonNull(e.getKey(), "placeholderToOriginal enthält null");
            String value = Objects.requireNonNull(e.getValue(), "placeholderToOriginal enthält null");
            String[] parts = PlaceholderSyntax.parseCanonical(key);
            if (parts == null) {
                throw new IllegalArgumentException("Schlüssel ist kein kanonischer Platzhalter");
            }
            entries.add(new Entry(Long.parseLong(parts[1]), key, parts[0], value));
        }
        entries.sort(Comparator.comparingLong(Entry::number).thenComparing(Entry::placeholder));
        Map<String, String> ordered = new LinkedHashMap<>();
        Set<String> types = new LinkedHashSet<>();
        for (Entry e : entries) {
            ordered.put(e.placeholder(), e.original());
            types.add(e.type());
        }
        return new PlaceholderMapping(Collections.unmodifiableMap(ordered), Collections.unmodifiableSet(types));
    }

    /** Originalwert zu einem kanonischen Platzhalter; leer, wenn unbekannt. */
    public Optional<String> resolve(String canonicalPlaceholder) {
        Objects.requireNonNull(canonicalPlaceholder, "canonicalPlaceholder");
        return Optional.ofNullable(values.get(canonicalPlaceholder));
    }

    /** Alle Platzhalter (ohne Originalwerte), in Nummernfolge; unveränderliche Sicht. */
    public Set<String> placeholders() {
        return Collections.unmodifiableSet(values.keySet());
    }

    /** Alle Typnamen der Platzhalter (z. B. {@code PFAD}); unveränderliche Sicht. */
    public Set<String> typeNames() {
        return typeNames;
    }

    public int size() {
        return values.size();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PlaceholderMapping other && values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    /** Nur die Anzahl, nie ein Platzhalterinhalt oder Originalwert. */
    @Override
    public String toString() {
        return "PlaceholderMapping[size=" + values.size() + "]";
    }
}
