package de.skillshield.core;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Architektur-Regeln des Fachkerns (T-20; AK-51, AK-52, AK-54): Der Produktivcode unter {@code src/main/java}
 * enthält kein Netzwerk, keinen Dateisystem- und Prozesszugriff, keine Ausgabe auf Standardausgabe/-fehler und
 * kein Logging. Die Prüfung ist textbasiert und gilt auch für Kommentare: ein verbotenes Wort darf im
 * Produktivcode nirgends stehen. Wörter gelten mit Wortgrenzen; die Punkte in den Wörtern sind wörtliche Punkte.
 * {@code RuntimeException} enthält {@code Runtime} nur als Wortteil und ist deshalb erlaubt.
 *
 * <p>Der Test liest die Quelldateien (Surefire-Arbeitsverzeichnis ist das Modulverzeichnis); das ist Testcode und
 * von den Verboten für den Produktivcode nicht betroffen. Er prüft nicht ins Leere: Er verlangt eine Mindestzahl
 * gelesener Dateien samt bekannter Dateien und belegt die Prüffunktion in Selbsttests je verbotenem Wort.
 */
class ArchitekturRegelnTest {

    private static final Path QUELLE = Path.of("src", "main", "java");
    private static final String BASIS_PFAD = "de/skillshield/core/";
    private static final String BASIS_PAKET = "de.skillshield.core";

    /** Mindestzahl gelesener Dateien (gemessen: 21 zum Stand T-19). */
    private static final int MIN_DATEIEN = 18;

    /** Verbotene Wörter (Plan T-20); Punkte sind wörtliche Punkte. */
    private static final List<String> VERBOTEN = List.of("java.net", "javax.net", "java.nio", "java.io",
            "InetAddress", "Socket", "URLConnection", "HttpClient", "FileSystem", "Files", "Paths", "ProcessBuilder",
            "Runtime", "System.out", "System.err", "printStackTrace", "java.util.logging", "System.Logger");

    /** Bekannte Dateien, die gelesen worden sein müssen (Pfad unter {@code src/main/java}). */
    private static final List<String> BEKANNT = List.of(
            "de/skillshield/core/model/Finding.java",
            "de/skillshield/core/recognizer/general/PathRecognizer.java",
            "de/skillshield/core/pseudonymize/Pseudonymizer.java",
            "de/skillshield/core/reidentify/Reidentifier.java");

    private static final Pattern PAKET = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)\\s*;");

    private static final List<Pattern> MUSTER = muster();

    private static List<Pattern> muster() {
        List<Pattern> m = new ArrayList<>();
        for (String wort : VERBOTEN) {
            m.add(Pattern.compile("(?<![\\p{L}\\p{N}_$])" + Pattern.quote(wort) + "(?![\\p{L}\\p{N}_$])"));
        }
        return m;
    }

    // ------------------------------------------------------------------ Prüffunktionen

    /** Verstöße in einem Text: je Fundstelle {@code Datei:Zeile: Wort}. */
    static List<String> verbotenInText(String datei, String text) {
        List<String> verstoesse = new ArrayList<>();
        String[] zeilen = text.split("\\R", -1);
        for (int i = 0; i < zeilen.length; i++) {
            for (int w = 0; w < MUSTER.size(); w++) {
                if (MUSTER.get(w).matcher(zeilen[i]).find()) {
                    verstoesse.add(datei + ":" + (i + 1) + ": " + VERBOTEN.get(w));
                }
            }
        }
        return verstoesse;
    }

    /** Verstöße gegen die Paketregel: Datei unter {@code de/skillshield/core} mit passender Paketdeklaration. */
    static List<String> paketVerstoesse(String relativerPfad, String text) {
        List<String> verstoesse = new ArrayList<>();
        if (!relativerPfad.startsWith(BASIS_PFAD)) {
            verstoesse.add(relativerPfad + ": liegt nicht unter " + BASIS_PFAD);
        }
        Matcher m = PAKET.matcher(text);
        if (!m.find()) {
            verstoesse.add(relativerPfad + ": keine Paketdeklaration");
        } else {
            String paket = m.group(1);
            if (!(paket.equals(BASIS_PAKET) || paket.startsWith(BASIS_PAKET + "."))) {
                verstoesse.add(relativerPfad + ": Paket " + paket + " beginnt nicht mit " + BASIS_PAKET);
            }
        }
        return verstoesse;
    }

    private static List<Path> quelldateien() {
        assertTrue(Files.isDirectory(QUELLE), "Quellverzeichnis fehlt: " + QUELLE.toAbsolutePath());
        try (Stream<Path> s = Files.walk(QUELLE)) {
            return s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".java")).sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String relativ(Path datei) {
        return QUELLE.relativize(datei).toString().replace('\\', '/');
    }

    private static String lies(Path datei) {
        try {
            return Files.readString(datei, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ Prüfung des Produktivcodes

    @Test
    void ak51_produktivcodeEnthaeltKeinVerbotenesWort_auchNichtInKommentaren() {
        List<String> verstoesse = new ArrayList<>();
        for (Path datei : quelldateien()) {
            verstoesse.addAll(verbotenInText(relativ(datei), lies(datei)));
        }
        assertTrue(verstoesse.isEmpty(), "Verbotene Woerter im Produktivcode: " + verstoesse);
    }

    @Test
    void ak52_alleDateienLiegenUnterDemKernpaketMitPassenderPaketdeklaration() {
        List<String> verstoesse = new ArrayList<>();
        for (Path datei : quelldateien()) {
            verstoesse.addAll(paketVerstoesse(relativ(datei), lies(datei)));
        }
        assertTrue(verstoesse.isEmpty(), "Paketverstoesse: " + verstoesse);
    }

    @Test
    void pruefungLiestPlausibleMengeUndBekannteDateien() {
        List<String> gelesen = quelldateien().stream().map(ArchitekturRegelnTest::relativ).toList();
        assertTrue(gelesen.size() >= MIN_DATEIEN, "Zu wenige Dateien gelesen: " + gelesen.size());
        for (String bekannt : BEKANNT) {
            assertTrue(gelesen.contains(bekannt), "Bekannte Datei nicht gelesen: " + bekannt);
        }
        // Es werden keine Testdateien gelesen
        for (String datei : gelesen) {
            assertFalse(datei.endsWith("Test.java"), "Testdatei im Produktivcode: " + datei);
        }
    }

    // ------------------------------------------------------------------ Selbsttests der Prüffunktion

    static Stream<String> verboteneWoerter() {
        return VERBOTEN.stream();
    }

    @Test
    void selbsttest_verbotsliste_enthaeltAlleWoerterAusDemPlan() {
        assertEquals(18, VERBOTEN.size());
        assertTrue(VERBOTEN.containsAll(List.of("java.net", "javax.net", "java.nio", "java.io", "InetAddress",
                "Socket", "URLConnection", "HttpClient", "FileSystem", "Files", "Paths", "ProcessBuilder", "Runtime",
                "System.out", "System.err", "printStackTrace", "java.util.logging", "System.Logger")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("verboteneWoerter")
    void selbsttest_jedesVerboteneWortWirdErkannt_imCodeUndImKommentar(String wort) {
        String code = "class A {\n    Object o = " + wort + ";\n}\n";
        assertEquals(List.of("A.java:2: " + wort), verbotenInText("A.java", code), "im Code: " + wort);

        String zeilenKommentar = "class A {\n    // siehe " + wort + " hier\n}\n";
        assertEquals(List.of("A.java:2: " + wort), verbotenInText("A.java", zeilenKommentar),
                "im Zeilenkommentar: " + wort);

        String blockKommentar = "/**\n * Beispiel {@code " + wort + "} im Javadoc.\n */\nclass A {\n}\n";
        assertEquals(List.of("A.java:2: " + wort), verbotenInText("A.java", blockKommentar),
                "im Blockkommentar: " + wort);

        String amAnfangUndEnde = wort;
        assertEquals(List.of("A.java:1: " + wort), verbotenInText("A.java", amAnfangUndEnde),
                "ohne Umgebung: " + wort);
    }

    @Test
    void selbsttest_erlaubteNachbarnWerdenNichtBeanstandet() {
        String text = String.join("\n",
                "throw new RuntimeException(\"x\");",
                "class MyRuntime { }",
                "Object o = new IllegalStateException();",
                "String s = \"javaxnet und javaXnet und java_net\";",
                "int Files2 = 0; int MyFiles = 1; int FilesX = 2;",
                "boolean b = SocketLike.ok(); boolean c = ServerSockets.ok();",
                "String t = \"java.iox\"; String u = \"xjava.io\";",
                "Object p = PathsX.get(); Object q = Pathsa.get();",
                "String v = \"System.outer\"; String w = \"System_out\"; String x = \"SystemXout\";",
                "String y = \"java.util.loggingx\"; String z = \"javaXutil.logging\";",
                "String r = \"FileSystems\"; String m = \"HttpClients\"; String l = \"URLConnections\";",
                "String k = \"InetAddresses\"; String j = \"ProcessBuilders\"; String i = \"printStackTraces\";",
                "String h = \"System.Loggers\"; String g = \"System.errors\"; String f = \"javaxXnet\";");
        assertEquals(List.of(), verbotenInText("A.java", text));
    }

    @Test
    void selbsttest_meldungNenntDateiZeileUndWort() {
        String text = "a\nb System.out.println(1);\nc\n// Files und Paths\n";
        assertEquals(List.of("D.java:2: System.out", "D.java:4: Paths", "D.java:4: Files").stream().sorted().toList(),
                verbotenInText("D.java", text).stream().sorted().toList());
    }

    @Test
    void selbsttest_punkteInDenWoerternSindWoertlich() {
        assertEquals(List.of(), verbotenInText("A.java", "javaXnet javaXio javaxXnet SystemXout SystemXerr"));
        assertEquals(List.of(), verbotenInText("A.java", "System.Xlogger java.utilXlogging"));
    }

    @Test
    void selbsttest_paketpruefung() {
        String ok = "package de.skillshield.core.model;\n\nclass A { }\n";
        assertEquals(List.of(), paketVerstoesse("de/skillshield/core/model/A.java", ok));
        assertEquals(List.of(), paketVerstoesse("de/skillshield/core/A.java", "package de.skillshield.core;\n"));
        assertEquals(1, paketVerstoesse("de/other/A.java", "package de.skillshield.core;\n").size());
        assertEquals(1, paketVerstoesse("de/skillshield/core/A.java", "package de.other;\n").size());
        assertEquals(1, paketVerstoesse("de/skillshield/core/A.java", "package de.skillshield.corex;\n").size());
        assertEquals(1, paketVerstoesse("de/skillshield/core/A.java", "class A { }\n").size());
        assertEquals(2, paketVerstoesse("A.java", "class A { }\n").size());
    }
}
