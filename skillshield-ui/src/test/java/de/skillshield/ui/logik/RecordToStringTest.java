package de.skillshield.ui.logik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PlaceholderMapping;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Kein Record im Paket {@code logik} gibt per {@code toString()} Text oder Funde aus (Standard-{@code toString()}
 * eines Records druckt alle Komponenten). Die Records werden aus den Quelldateien des Pakets ermittelt (auch
 * verschachtelte, private), mit einer Markierung als Text und Fundwert gefuellt, und die Ausgabe darf die
 * Markierung nicht enthalten. Ausgenommen sind Records, die nur feste Anzeigetexte tragen.
 */
class RecordToStringTest {

    private static final Path LOGIK = Path.of("src", "main", "java", "de", "skillshield", "ui", "logik");
    private static final String PAKET = "de.skillshield.ui.logik.";
    private static final String MARKER = "GEHEIM-TESTWERT-4711";

    /** Records mit ausschliesslich festen Anzeigetexten aus {@link Texte} (kein Nutzertext). */
    private static final Set<String> NUR_FESTE_TEXTE = Set.of("Statusleiste", "Bedienzustand");

    /** Komponenten mit Platzhalter oder Typname (kein Originaltext); sie duerfen ausgegeben werden. */
    private static final Set<String> PLATZHALTER_KOMPONENTEN = Set.of("platzhalter", "typ");

    private static List<Class<?>> records() throws IOException, ClassNotFoundException {
        List<Class<?>> gefunden = new ArrayList<>();
        List<Path> dateien;
        try (Stream<Path> s = Files.list(LOGIK)) {
            dateien = s.filter(p -> p.toString().endsWith(".java") && !p.endsWith("package-info.java"))
                    .sorted().toList();
        }
        assertTrue(dateien.size() >= 10, "Quelltext der Fachlogik nicht gefunden: Test laeuft am falschen Ort");
        for (Path datei : dateien) {
            String name = datei.getFileName().toString();
            sammle(Class.forName(PAKET + name.substring(0, name.length() - ".java".length())), gefunden);
        }
        return gefunden;
    }

    private static void sammle(Class<?> klasse, List<Class<?>> ziel) {
        if (klasse.isRecord()) {
            ziel.add(klasse);
        }
        for (Class<?> innere : klasse.getDeclaredClasses()) {
            sammle(innere, ziel);
        }
    }

    private static Object beispiel(RecordComponent komponente) {
        Class<?> typ = komponente.getType();
        if (typ == String.class) {
            return PLATZHALTER_KOMPONENTEN.contains(komponente.getName()) ? "[[TEXT_1]]" : MARKER;
        }
        if (typ == Finding.class) {
            return Finding.manual(FindingType.TEXT, MARKER, 0, MARKER.length());
        }
        if (typ == PlaceholderMapping.class) {
            return PlaceholderMapping.of(Map.of("[[TEXT_1]]", MARKER));
        }
        if (typ == boolean.class) {
            return false;
        }
        if (typ == int.class) {
            return 0;
        }
        if (typ == long.class) {
            return 0L;
        }
        if (typ == List.class) {
            return List.of();
        }
        if (typ == Map.class) {
            return Map.of();
        }
        if (typ == Set.class) {
            return Set.of();
        }
        return null;
    }

    private static String ausgabe(Class<?> record) throws ReflectiveOperationException {
        RecordComponent[] komponenten = record.getRecordComponents();
        Class<?>[] typen = new Class<?>[komponenten.length];
        Object[] werte = new Object[komponenten.length];
        for (int i = 0; i < komponenten.length; i++) {
            typen[i] = komponenten[i].getType();
            werte[i] = beispiel(komponenten[i]);
        }
        Constructor<?> konstruktor = record.getDeclaredConstructor(typen);
        konstruktor.setAccessible(true);
        return konstruktor.newInstance(werte).toString();
    }

    @Test
    void keinRecordDerFachlogikGibtTextOderFundwerteAus() throws Exception {
        List<Class<?>> records = records();
        List<String> namen = records.stream().map(Class::getSimpleName).toList();
        assertTrue(namen.containsAll(List.of("Live", "FundZeile", "FundSchluessel", "Statusleiste")),
                namen.toString());

        List<String> verstoesse = new ArrayList<>();
        for (Class<?> record : records) {
            if (NUR_FESTE_TEXTE.contains(record.getSimpleName())) {
                continue;
            }
            if (ausgabe(record).contains(MARKER)) {
                verstoesse.add(record.getName());
            }
        }
        assertEquals(List.of(), verstoesse);
    }

    @Test
    void selbsttest_einRecordMitStandardToStringWuerdeAngeschlagen() throws Exception {
        record Schlecht(String text, Finding fund) { }

        assertTrue(ausgabe(Schlecht.class).contains(MARKER));
        assertFalse(NUR_FESTE_TEXTE.contains(Schlecht.class.getSimpleName()));
    }

    @Test
    void dieAusnahmenGeltenNurFuerRecordsOhneNutzertext() {
        assertEquals(Set.of("Statusleiste", "Bedienzustand"), NUR_FESTE_TEXTE);
    }
}
