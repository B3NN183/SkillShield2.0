package de.skillshield.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;

/**
 * Inhalt der gebauten startbaren JAR (OF-1, AK-52, AK-55): Manifest, Gruppen, Lizenzen, keine Test-, Signatur- oder
 * Modulbeschreibungs-Reste, keine nativen Bibliotheken. Laeuft nach {@code package} (Surefire-Lauf
 * {@code jar-tests}).
 */
class StartbareJarIT {

    private static Path pfad(String eigenschaft) {
        String wert = System.getProperty(eigenschaft);
        assertTrue(wert != null && Files.isRegularFile(Path.of(wert)),
                eigenschaft + " fehlt (nur im Lauf jar-tests nach package vorhanden)");
        return Path.of(wert);
    }

    private static List<String> eintraege() throws IOException {
        List<String> namen = new ArrayList<>();
        try (JarFile jar = new JarFile(pfad("skillshield.jar").toFile())) {
            jar.stream().map(ZipEntry::getName).forEach(namen::add);
        }
        return namen;
    }

    @Test
    void manifestNenntStartklasseUndMultiRelease() throws IOException {
        try (JarFile jar = new JarFile(pfad("skillshield.jar").toFile())) {
            Manifest manifest = jar.getManifest();
            assertEquals("de.skillshield.app.SkillShieldApp", manifest.getMainAttributes().getValue("Main-Class"));
            assertEquals("true", manifest.getMainAttributes().getValue("Multi-Release"));
        }
    }

    @Test
    void enthaeltKernOberflaecheStartklasseUndFlatLaf() throws IOException {
        List<String> namen = eintraege();
        for (String erwartet : List.of("de/skillshield/app/SkillShieldApp.class",
                "de/skillshield/ui/swing/HauptFenster.class", "de/skillshield/ui/swing/HauptPanel.class",
                "de/skillshield/ui/logik/Sitzung.class", "de/skillshield/core/recognizer/TextScanner.class",
                "com/formdev/flatlaf/FlatLightLaf.class")) {
            assertTrue(namen.contains(erwartet), erwartet);
        }
    }

    @Test
    void enthaeltDieLizenzVonFlatLafUndKeineDoppeltenEintraege() throws IOException {
        List<String> namen = eintraege();
        assertTrue(namen.contains("META-INF/LICENSE"), "META-INF/LICENSE (Apache 2.0 von FlatLaf)");
        assertEquals(namen.size(), namen.stream().distinct().count(), "doppelte Eintraege");
        try (JarFile jar = new JarFile(pfad("skillshield.jar").toFile())) {
            String lizenz = new String(jar.getInputStream(jar.getEntry("META-INF/LICENSE")).readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(lizenz.contains("Apache License"), "Lizenztext fehlt");
        }
    }

    @Test
    void enthaeltKeineTestklassenKeinJUnitKeineSignaturenKeineModulbeschreibungKeineNativen() throws IOException {
        List<String> verboten = new ArrayList<>();
        for (String name : eintraege()) {
            boolean eigene = name.startsWith("de/skillshield/");
            boolean test = eigene && (name.endsWith("Test.class") || name.endsWith("IT.class")
                    || name.contains("Test$")) || name.startsWith("org/junit/") || name.startsWith("org/opentest4j/")
                    || name.startsWith("org/apiguardian/") || name.contains("SynthetischeKvNummern")
                    || name.contains("SitzungsProbe") || name.contains("ZweiteJvm");
            boolean signatur = name.startsWith("META-INF/")
                    && (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"));
            boolean modul = name.equals("module-info.class") || name.endsWith("/module-info.class");
            boolean nativ = name.startsWith("com/formdev/flatlaf/natives/");
            if (test || signatur || modul || nativ) {
                verboten.add(name);
            }
        }
        assertEquals(List.of(), verboten);
    }

    @Test
    void startskriptLiegtNebenDerJarUndStartetRelativZuSichSelbst() throws IOException {
        Path cmd = pfad("skillshield.cmd");
        Path jar = pfad("skillshield.jar");
        assertEquals(jar.getParent().toAbsolutePath().normalize(), cmd.getParent().toAbsolutePath().normalize());
        String text = Files.readString(cmd, StandardCharsets.UTF_8);
        assertTrue(text.contains("start \"\" \"%JAVAW%\" -XX:-UsePerfData -XX:+DisableAttachMechanism "
                + "-XX:ErrorFile=NUL -XX:-CreateCoredumpOnCrash -jar \"%~dp0skillshield.jar\""),
                "Startzeile mit JVM-Optionen");
        assertTrue(text.contains("Java 21 wurde nicht gefunden"), "Meldung ohne Java");
        assertTrue(text.contains("zu alt"), "Meldung bei zu altem Java");
        for (int i = 0; i < text.length(); i++) {
            char zeichen = text.charAt(i);
            if (zeichen == '\n') {
                assertTrue(i > 0 && text.charAt(i - 1) == '\r', "Zeilenende ohne CR bei Zeichen " + i);
            } else if (zeichen == '\r') {
                assertTrue(i + 1 < text.length() && text.charAt(i + 1) == '\n', "einzelnes CR bei Zeichen " + i);
            }
        }
        assertTrue(text.contains("\r\n"), "CRLF-Zeilenenden fuer die Eingabeaufforderung");
        assertTrue(text.contains("setlocal DisableDelayedExpansion"), "keine verzoegerte Expansion");
        for (String variable : List.of("_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS")) {
            assertTrue(text.contains("set \"" + variable + "=\"\r\n"), variable + " wird geleert");
        }
        assertTrue(text.contains("set \"NoDefaultCurrentDirectoryInExePath=1\""), "kein Suchen im aktuellen Ordner");
        assertTrue(text.contains("\"%SystemRoot%\\System32\\where.exe\" $PATH:javaw.exe"), "javaw nur im PATH");
        assertFalse(text.contains("%*"), "keine Argumentweitergabe");
        for (String verbotenes : List.of("curl", "http:", "https:", "powershell", "bitsadmin", "certutil", " > ",
                ">>")) {
            assertFalse(text.contains(verbotenes), "Startskript enthaelt " + verbotenes);
        }
        for (char zeichen : text.toCharArray()) {
            assertTrue(zeichen < 128, "nur ASCII im Startskript");
        }
    }

    @Test
    void startskriptRuftKeinWerkzeugOhneVollenPfadAuf() throws IOException {
        String text = Files.readString(pfad("skillshield.cmd"), StandardCharsets.UTF_8);
        List<String> unqualifiziert = new ArrayList<>();
        for (String zeile : text.split("\r\n")) {
            if (zeile.startsWith("rem ") || zeile.startsWith("echo")) {
                continue;
            }
            for (String werkzeug : List.of("where", "findstr", "java", "javaw")) {
                // ein Aufruf steht nach Anfuehrungszeichen, "start " oder "in ('" und nicht als Teil eines Pfades
                Matcher treffer = Pattern.compile("(^|[ '(])" + werkzeug + "(\\.exe)?( |$)").matcher(zeile);
                if (treffer.find()) {
                    unqualifiziert.add(werkzeug + " in: " + zeile);
                }
            }
        }
        assertEquals(List.of(), unqualifiziert);
    }
}
