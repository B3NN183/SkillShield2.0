package de.skillshield.app;

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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Quelltext-Waechter (AK-52, AK-44 statisch): Der Produktivcode des Moduls enthaelt weder Netzwerk-APIs noch
 * Schreibzugriffe auf die Platte noch Konsolenausgaben. Geprueft wird jedes Vorkommen als Teilstring, auch in
 * Kommentaren. Besteht auch auf leerem Quelltext. Einzige Ausnahme: der feste Headless-Text in SkillShieldApp.
 * Unicode-Escapes werden vor dem Abgleich aufgeloest. Grenzen: bewusste Verschleierung und zusammengesetzte
 * Zeichenketten erkennt der Test nicht; dafuer gilt die Review-Pflicht.
 */
class QuelltextWaechterTest {

    private static final Path QUELLE = Path.of("src", "main", "java");

    /** Verbotene Zeichenfolgen (Netzwerk, Platte, Konsole, Einstellungen). */
    static final List<String> VERBOTEN = List.of(
            "java.net.", "javax.net.", "java.net.http", "java.awt.Desktop", ".browse(", "Socket", "URLConnection",
            "HttpClient", "java.rmi", "javax.naming",
            "FileOutputStream", "FileWriter", "Files.write", "Files.newOutputStream", "Files.newBufferedWriter",
            "Files.createFile", "Files.createTempFile", "Files.createTempDirectory", "RandomAccessFile",
            "java.util.prefs", "java.util.logging.FileHandler", "printStackTrace", "System.out", "System.err",
            "ProcessBuilder", "Runtime.getRuntime", ".exec(", "Desktop.getDesktop", "FileHandler",
            "java.util.logging", "System.getLogger", "File.createTempFile", "Files.copy", "Files.move",
            "newByteChannel", "FileChannel", "ImageIO.write", "new PrintWriter(", "new PrintStream(",
            "newFileSystem", "dumpStack", "Class.forName", "MethodHandles", "getDeclaredMethod");

    private static final Path POM = Path.of("pom.xml");
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u+([0-9a-fA-F]{4})");

    /** Loest Unicode-Escapes im Quelltext auf, damit sie den Abgleich nicht umgehen. */
    static String ohneUnicodeEscapes(String text) {
        Matcher m = UNICODE_ESCAPE.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            char zeichen = (char) Integer.parseInt(m.group(1), 16);
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(zeichen)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Erlaubte Ausnahmen: Datei, Wort und hoechstens erlaubte Zahl an Vorkommen. */
    static final Map<String, Map<String, Integer>> AUSNAHMEN = Map.of(
            "SkillShieldApp.java", Map.of("System.err", 1));

    static List<String> verstoesse(String datei, String text) {
        List<String> liste = new ArrayList<>();
        Map<String, Integer> erlaubt = AUSNAHMEN.getOrDefault(datei, Map.of());
        String klartext = ohneUnicodeEscapes(text);
        for (String wort : VERBOTEN) {
            int anzahl = zaehle(klartext, wort);
            if (anzahl > erlaubt.getOrDefault(wort, 0)) {
                liste.add(datei + ": " + wort + " (" + anzahl + "x)");
            }
        }
        return liste;
    }

    private static int zaehle(String text, String wort) {
        int anzahl = 0;
        for (int i = text.indexOf(wort); i >= 0; i = text.indexOf(wort, i + wort.length())) {
            anzahl++;
        }
        return anzahl;
    }

    private static List<Path> quelldateien() {
        assertTrue(Files.isRegularFile(POM), "pom.xml nicht im Arbeitsverzeichnis: Test laeuft am falschen Ort");
        if (!Files.isDirectory(QUELLE)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(QUELLE)) {
            return s.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Stream<String> verboteneWoerter() {
        return VERBOTEN.stream();
    }

    @Test
    void produktivcodeEnthaeltKeineVerbotenenApis() throws IOException {
        List<String> alle = new ArrayList<>();
        for (Path datei : quelldateien()) {
            String text = Files.readString(datei, StandardCharsets.UTF_8);
            alle.addAll(verstoesse(datei.getFileName().toString(), text));
        }
        assertEquals(List.of(), alle);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("verboteneWoerter")
    void selbsttest_jedesVerboteneWortWirdErkannt(String wort) {
        String text = "class A {\n    Object o = " + wort + ";\n}\n";
        assertTrue(verstoesse("A.java", text).stream().anyMatch(z -> z.contains(wort)), wort);
        assertTrue(verstoesse("A.java", "// siehe " + wort).stream().anyMatch(z -> z.contains(wort)),
                "Kommentar: " + wort);
    }

    @Test
    void selbsttest_unicodeEscapesWerdenAufgeloest() {
        String escape = "\\" + "u0053";
        assertFalse(verstoesse("A.java", escape + "ystem.out.println();").isEmpty());
        assertFalse(verstoesse("A.java", "Class.for" + escape.replace("0053", "004e") + "ame(x)").isEmpty());
        assertEquals("System", ohneUnicodeEscapes(escape + "ystem"));
    }

    @Test
    void selbsttest_harmloserTextWirdNichtBeanstandet() {
        assertTrue(verstoesse("A.java", "class A { int x = 1; String s = \"Hallo\"; }").isEmpty());
        assertTrue(verstoesse("A.java", "").isEmpty());
    }

    @Test
    void selbsttest_ausnahmeGiltNurFuerDieStartklasseUndEinmal() {
        String einmal = "System" + ".err.println();";
        assertTrue(verstoesse("SkillShieldApp.java", einmal).isEmpty());
        assertFalse(verstoesse("SkillShieldApp.java", einmal + einmal).isEmpty());
        assertFalse(verstoesse("Anderes.java", einmal).isEmpty());
        assertFalse(verstoesse("SkillShieldApp.java", "System" + ".out.println();").isEmpty());
    }
}
