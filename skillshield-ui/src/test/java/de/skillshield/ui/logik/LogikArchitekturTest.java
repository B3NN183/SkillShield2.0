package de.skillshield.ui.logik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Architektur-Plan Kap. 1 und 2.1: Der Quelltext der Fachlogik ({@code ui/main/logik}) importiert nur {@code java.*}
 * ausser {@code java.awt} und {@code java.net} sowie {@code de.skillshield.core.*}; Swing, AWT und Netzwerk kommen
 * auch nicht ueber voll qualifizierte Namen vor.
 */
class LogikArchitekturTest {

    private static final Path LOGIK = Path.of("src", "main", "java", "de", "skillshield", "ui", "logik");
    private static final List<String> VERBOTENE_PRAEFIXE = List.of("javax.", "java.awt", "java.net", "java.beans");
    private static final List<String> ERLAUBTE_PRAEFIXE = List.of("java.", "de.skillshield.core.");

    static List<String> verstoesse(String datei, String quelltext) {
        List<String> liste = new ArrayList<>();
        for (String zeile : quelltext.split("\\R")) {
            String bereinigt = zeile.strip();
            if (bereinigt.startsWith("import ")) {
                String ziel = bereinigt.substring("import ".length()).replaceFirst("^static\\s+", "");
                boolean erlaubt = ERLAUBTE_PRAEFIXE.stream().anyMatch(ziel::startsWith)
                        && VERBOTENE_PRAEFIXE.stream().noneMatch(ziel::startsWith);
                if (!erlaubt) {
                    liste.add(datei + ": Import " + ziel);
                }
            }
        }
        for (String verboten : List.of("javax.swing", "java.awt", "java.net")) {
            if (quelltext.contains(verboten)) {
                liste.add(datei + ": " + verboten);
            }
        }
        return liste;
    }

    @Test
    void fachlogikImportiertNurJavaOhneAwtNetUndDenKern() throws IOException {
        List<Path> dateien;
        try (Stream<Path> s = Files.list(LOGIK)) {
            dateien = s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(dateien.size() >= 10, "Quelltext der Fachlogik nicht gefunden: Test laeuft am falschen Ort");

        List<String> alle = new ArrayList<>();
        for (Path datei : dateien) {
            alle.addAll(verstoesse(datei.getFileName().toString(),
                    Files.readString(datei, StandardCharsets.UTF_8)));
        }
        assertEquals(List.of(), alle);
    }

    @Test
    void selbsttest_verbotenesWirdErkannt() {
        assertFalse(verstoesse("A.java", "import javax.swing.JPanel;").isEmpty());
        assertFalse(verstoesse("A.java", "import java.awt.Color;").isEmpty());
        assertFalse(verstoesse("A.java", "import java.net.URI;").isEmpty());
        assertFalse(verstoesse("A.java", "import static javax.swing.SwingUtilities.invokeLater;").isEmpty());
        assertFalse(verstoesse("A.java", "import de.skillshield.io.Etwas;").isEmpty());
        assertFalse(verstoesse("A.java", "import org.junit.Assert;").isEmpty());
        assertFalse(verstoesse("A.java", "class A { javax.swing.JLabel l; }").isEmpty());
    }

    @Test
    void selbsttest_erlaubtesWirdNichtBeanstandet() {
        assertTrue(verstoesse("A.java", "import java.util.List;\nimport de.skillshield.core.model.Finding;\n"
                + "import static java.util.Objects.requireNonNull;\nclass A { }").isEmpty());
    }
}
