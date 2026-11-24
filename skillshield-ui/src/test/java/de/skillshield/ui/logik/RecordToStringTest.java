package de.skillshield.ui.logik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
import org.opentest4j.AssertionFailedError;

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
            return List.of(MARKER);
        }
        if (typ == Map.class) {
            return Map.of(MARKER, MARKER);
        }
        if (typ == Set.class) {
            return Set.of(MARKER);
        }
        return fail("Typ nicht unterstuetzt: " + typ.getName() + " (Komponente " + komponente.getName() + ")");
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
    void selbsttest_nichtUnterstuetzteKomponententypenSchlagenFehl() {
        record Fremd(java.util.Optional<String> wert) { }

        assertThrows(AssertionFailedError.class, () -> ausgabe(Fremd.class));
    }

    @Test
    void statusleisteUndBedienzustandEnthaltenNachScanMitMarkerTextKeineMarkierung() {
        String eingabe = "Zeile " + MARKER + " mail mustermann@example.org " + MARKER + " Ende";
        Sitzung sitzung = Sitzung.standard(new Attrappen.TestZwischenablage(), new Attrappen.TestDialoge());
        Attrappen.scannen(sitzung, eingabe);
        sitzung.anwaehlen(0, false);
        sitzung.anwaehlen(0, true);
        sitzung.markierungHinzufuegen(eingabe.indexOf(MARKER), eingabe.indexOf(MARKER) + MARKER.length());

        assertFalse(sitzung.statusleiste().toString().contains(MARKER), "Statusleiste");
        assertFalse(sitzung.statusleiste().meldung().contains(MARKER), "Statusmeldung");
        assertFalse(sitzung.statusleiste().zuordnung().contains(MARKER), "Zuordnungsanzeige");
        assertFalse(sitzung.bedienzustand().toString().contains(MARKER), "Bedienzustand");
        assertFalse(sitzung.toString().contains(MARKER), "Sitzung");
        assertTrue(sitzung.textFuerKi().length() > 0 && !sitzung.textFuerKi().contains(MARKER),
                "Vorbedingung: Der Marker ist im Text fuer die KI ersetzt");
    }

    @Test
    void dieAusnahmenGeltenNurFuerRecordsOhneNutzertext() {
        assertEquals(Set.of("Statusleiste", "Bedienzustand"), NUR_FESTE_TEXTE);
    }
}
