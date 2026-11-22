package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
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
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.helpers.DefaultHandler;

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
            "de/skillshield/core/reidentify/Reidentifier.java",
            "de/skillshield/core/recognizer/kv/IkRecognizer.java",
            "de/skillshield/core/recognizer/kv/KvArt.java",
            "de/skillshield/core/recognizer/kv/KvCheckDigits.java",
            "de/skillshield/core/recognizer/kv/KvNumberScanner.java",
            "de/skillshield/core/recognizer/kv/LanrRecognizer.java");

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

    // ------------------------------------------------------------------ JDK-Pakete (T-21; AK-51, AK-54)

    /**
     * Erlaubte JDK-Pakete (Positivliste, T-21): genau diese Paketnamen und nichts darunter. Der Kern nutzt heute nur
     * {@code java.util} und {@code java.util.regex}; {@code java.lang} ist implizit. Andere Unterpakete von
     * {@code java.util} ({@code prefs}, {@code logging}, {@code jar}, {@code zip}, {@code concurrent}, {@code stream},
     * {@code function} ...) sind nicht erlaubt; wer eines braucht, erweitert diese Liste bewusst.
     */
    private static final List<String> ERLAUBTE_JDK_PAKETE = List.of("java.lang", "java.util", "java.util.regex");

    /** Erlaubte Anfänge einer Importzeile (nach {@code import} und optional {@code static}). */
    private static final List<String> ERLAUBTE_IMPORTE = List.of("java.util.", BASIS_PAKET + ".");

    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([^\\s;]+)");

    /** Paketanfang {@code java.} bzw. {@code javax.}: Wortgrenze davor, danach Kleinbuchstaben-Paketnamen. */
    private static final Pattern JDK_PAKET = Pattern
            .compile("(?<![\\p{L}\\p{N}_$.])javax?(?:\\.[a-z][A-Za-z0-9_]*)+");

    /** Verstöße gegen die Positivliste: je Fundstelle {@code Datei:Zeile: Paket}. */
    static List<String> jdkPaketVerstoesse(String datei, String text) {
        List<String> verstoesse = new ArrayList<>();
        String[] zeilen = text.split("\\R", -1);
        for (int i = 0; i < zeilen.length; i++) {
            Matcher imp = IMPORT.matcher(zeilen[i]);
            if (imp.find()) {
                String ziel = imp.group(1);
                if (ERLAUBTE_IMPORTE.stream().noneMatch(ziel::startsWith)) {
                    verstoesse.add(datei + ":" + (i + 1) + ": Import " + ziel);
                }
            }
            Matcher m = JDK_PAKET.matcher(zeilen[i]);
            while (m.find()) {
                if (!ERLAUBTE_JDK_PAKETE.contains(m.group())) {
                    verstoesse.add(datei + ":" + (i + 1) + ": Paket " + m.group());
                }
            }
        }
        return verstoesse;
    }

    // ------------------------------------------------------------------ Abhaengigkeiten (T-21; AK-52)

    private static final Path MODUL_POM = Path.of("pom.xml");
    private static final Path PARENT_POM = Path.of("..", "pom.xml");

    /** Elemente, unter denen {@code <dependency>} keine Abhaengigkeit des Moduls ist. */
    private static final List<String> POM_AUSGENOMMEN = List.of("dependencyManagement", "build", "reporting");

    private static Document pomParsen(String datei, String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(new DefaultHandler());
            return b.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("POM nicht lesbar: " + datei, e);
        }
    }

    private static String kindText(Element e, String name) {
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element k && k.getTagName().equals(name)) {
                return k.getTextContent().trim();
            }
        }
        return "";
    }

    private static boolean ausgenommen(Element dependency) {
        for (Node n = dependency.getParentNode(); n instanceof Element e; n = n.getParentNode()) {
            if (POM_AUSGENOMMEN.contains(e.getTagName())) {
                return true;
            }
        }
        return false;
    }

    /** Alle {@code <dependency>} außerhalb von dependencyManagement, build (Plugins) und reporting. */
    private static List<Element> abhaengigkeiten(Document pom) {
        List<Element> liste = new ArrayList<>();
        NodeList alle = pom.getElementsByTagName("dependency");
        for (int i = 0; i < alle.getLength(); i++) {
            Element d = (Element) alle.item(i);
            if (!ausgenommen(d)) {
                liste.add(d);
            }
        }
        return liste;
    }

    private static String koordinate(Element d) {
        return kindText(d, "groupId") + ":" + kindText(d, "artifactId");
    }

    /** Verstoesse: je Abhaengigkeit ohne Scope {@code test}; mit {@code keineErlaubt} jede Abhaengigkeit. */
    static List<String> pomVerstoesse(String datei, String xml, boolean keineErlaubt) {
        List<String> verstoesse = new ArrayList<>();
        for (Element d : abhaengigkeiten(pomParsen(datei, xml))) {
            String scope = kindText(d, "scope");
            if (keineErlaubt) {
                verstoesse.add(datei + ": Abh\u00E4ngigkeit " + koordinate(d) + " nicht erlaubt (Modul-POM)");
            } else if (!scope.equals("test")) {
                String s = scope.isEmpty() ? "(fehlt, also compile)" : scope;
                verstoesse.add(datei + ": Abh\u00E4ngigkeit " + koordinate(d) + " hat Scope " + s
                        + ", erlaubt ist test");
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

    // ------------------------------------------------------------------ T-21: JDK-Positivliste

    @Test
    void ak51ak54_produktivcodeNenntNurErlaubteJdkPakete_auchInKommentaren() {
        List<String> verstoesse = new ArrayList<>();
        for (Path datei : quelldateien()) {
            verstoesse.addAll(jdkPaketVerstoesse(relativ(datei), lies(datei)));
        }
        assertTrue(verstoesse.isEmpty(), "JDK-Pakete ausserhalb der Positivliste: " + verstoesse);
    }

    @Test
    void ak51ak54_positivlisteTrifftImProduktivcodeAufBekannteNutzung() {
        // Nicht ins Leere: Der Kern importiert heute java.util und java.util.regex (EmailRecognizer).
        String email = lies(QUELLE.resolve("de/skillshield/core/recognizer/general/EmailRecognizer.java"));
        assertTrue(JDK_PAKET.matcher(email).results().anyMatch(r -> r.group().equals("java.util.regex")));
        assertTrue(email.lines().anyMatch(z -> IMPORT.matcher(z).find()), "Importzeilen werden erkannt");
    }

    @Test
    void selbsttest_jdkPositivliste_erlaubteBeispiele() {
        String text = String.join("\n",
                "import java.util.List;",
                "import static java.util.Objects.requireNonNull;",
                "import java.util.regex.Pattern;",
                "import de.skillshield.core.model.Finding;",
                "import static de.skillshield.core.TestTexte.bs;",
                "/** Siehe java.util.regex.Matcher und java.lang.String sowie java.util.Map.Entry. */",
                "String s = java.lang.String.valueOf(1); java.util.List<String> l = java.util.List.of();",
                "Object o = de.skillshield.core.model.Finding.class; // kein javaXutil und kein xjava.io",
                "Satzende java. N\u00E4chster Satz; java.Foo; javax.Foo");
        assertEquals(List.of(), jdkPaketVerstoesse("A.java", text));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unerlaubteJdkPakete")
    void selbsttest_jdkPositivliste_unerlaubtesPaketWirdErkannt(String paket) {
        // Unterpakete von java.util bestehen die Importregel (a) (Anfang java.util.); Regel (b) faengt sie ab.
        boolean importOk = paket.startsWith("java.util.");
        List<String> importMeldung = importOk ? List.of() : List.of("A.java:1: Import " + paket + ".X");
        List<String> staticMeldung = importOk ? List.of() : List.of("A.java:1: Import " + paket + ".X.y");
        assertEquals(concat(importMeldung, "A.java:1: Paket " + paket),
                jdkPaketVerstoesse("A.java", "import " + paket + ".X;"), "Import: " + paket);
        assertEquals(concat(staticMeldung, "A.java:1: Paket " + paket),
                jdkPaketVerstoesse("A.java", "import static " + paket + ".X.y;"), "Static-Import: " + paket);
        assertEquals(List.of("A.java:2: Paket " + paket),
                jdkPaketVerstoesse("A.java", "class A {\n    Object o = new " + paket + ".X();\n}"),
                "voll qualifiziert im Code: " + paket);
        assertEquals(List.of("A.java:2: Paket " + paket),
                jdkPaketVerstoesse("A.java", "/**\n * Siehe {@link " + paket + ".X}.\n */\nclass A {\n}"),
                "im Kommentar: " + paket);
        assertEquals(List.of("A.java:1: Paket " + paket), jdkPaketVerstoesse("A.java", paket), "ohne Umgebung");
    }

    private static List<String> concat(List<String> vorne, String letzte) {
        List<String> l = new ArrayList<>(vorne);
        l.add(letzte);
        return l;
    }

    static Stream<String> unerlaubteJdkPakete() {
        return Stream.of("java.sql", "java.rmi", "java.awt", "javax.swing", "javax.crypto", "java.time",
                "java.text", "java.security", "java.lang.reflect", "java.lang.invoke", "java.lang.management",
                "java.util.prefs", "java.util.logging", "java.util.jar", "java.util.zip", "java.util.concurrent",
                "java.util.stream", "java.util.function", "java.util.regex.sub");
    }

    @Test
    void selbsttest_jdkPositivliste_nurJavaUtilUndRegexSindErlaubt() {
        assertEquals(List.of("java.lang", "java.util", "java.util.regex"), ERLAUBTE_JDK_PAKETE);
        assertEquals(List.of(), jdkPaketVerstoesse("A.java", "java.util.regex.Pattern java.util.Map java.lang.Object"));
        assertEquals(List.of("A.java:1: Paket java.util.prefs"),
                jdkPaketVerstoesse("A.java", "java.util.Map java.util.prefs.Preferences"));
    }

    @Test
    void selbsttest_jdkPositivliste_importAusserhalbDerListe() {
        assertEquals(List.of("A.java:1: Import org.junit.Test", "A.java:2: Import de.skillshield.coreX.Y",
                "A.java:3: Import javax.swing.JFrame", "A.java:3: Paket javax.swing"),
                jdkPaketVerstoesse("A.java", "import org.junit.Test;\nimport de.skillshield.coreX.Y;\n"
                        + "  import   javax.swing.JFrame;"));
        assertEquals(List.of("A.java:1: Import java.utilx.X", "A.java:1: Paket java.utilx"),
                jdkPaketVerstoesse("A.java", "import java.utilx.X;"));
    }

    // ------------------------------------------------------------------ T-21: Abhaengigkeiten (AK-52)

    private static final String POM_KOPF = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
            + "<modelVersion>4.0.0</modelVersion><groupId>x</groupId><artifactId>p</artifactId>\n";

    private static String dep(String g, String a, String scope) {
        return "<dependency><groupId>" + g + "</groupId><artifactId>" + a + "</artifactId>"
                + (scope == null ? "" : "<scope>" + scope + "</scope>") + "</dependency>\n";
    }

    private static String pom(String koerper) {
        return POM_KOPF + koerper + "</project>\n";
    }

    @Test
    void ak52_kernHatKeineAbhaengigkeitAusserhalbVonTest() {
        assertTrue(Files.isRegularFile(MODUL_POM), "Modul-POM fehlt: " + MODUL_POM.toAbsolutePath());
        assertTrue(Files.isRegularFile(PARENT_POM), "Parent-POM fehlt: " + PARENT_POM.toAbsolutePath());
        String modul = lies(MODUL_POM);
        String parent = lies(PARENT_POM);
        assertTrue(modul.contains("<artifactId>skillshield-core</artifactId>"), "falsche Modul-POM gelesen");
        assertTrue(parent.contains("<artifactId>skillshield-parent</artifactId>"), "falsches Parent-POM gelesen");

        assertEquals(List.of(), pomVerstoesse("skillshield-core/pom.xml", modul, true));
        assertEquals(List.of(), pomVerstoesse("pom.xml (Parent)", parent, false));

        // Nicht ins Leere: Das Parent-POM deklariert mindestens die JUnit-Abhaengigkeit.
        List<Element> parentAbhaengigkeiten = abhaengigkeiten(pomParsen("pom.xml (Parent)", parent));
        assertTrue(parentAbhaengigkeiten.stream().anyMatch(d -> kindText(d, "artifactId").equals("junit-jupiter")),
                "JUnit-Abhaengigkeit im Parent-POM nicht gefunden");
    }

    @Test
    void selbsttest_pom_ohneAbhaengigkeitKeinVerstoss() {
        assertEquals(List.of(), pomVerstoesse("m.xml", pom(""), true));
        assertEquals(List.of(), pomVerstoesse("p.xml", pom(""), false));
    }

    @Test
    void selbsttest_pom_testScopeIstErlaubt_imModulPomAberNicht() {
        String xml = pom("<dependencies>" + dep("org.beispiel", "lib", "test") + "</dependencies>");
        assertEquals(List.of(), pomVerstoesse("p.xml", xml, false));
        assertEquals(List.of("m.xml: Abh\u00E4ngigkeit org.beispiel:lib nicht erlaubt (Modul-POM)"),
                pomVerstoesse("m.xml", xml, true));
    }

    @Test
    void selbsttest_pom_fehlenderScopeIstVerstoss() {
        String xml = pom("<dependencies>" + dep("org.beispiel", "lib", null) + "</dependencies>");
        assertEquals(List.of(
                "p.xml: Abh\u00E4ngigkeit org.beispiel:lib hat Scope (fehlt, also compile), erlaubt ist test"),
                pomVerstoesse("p.xml", xml, false));
    }

    @Test
    void selbsttest_pom_compileUndRuntimeSindVerstoss() {
        String xml = pom("<dependencies>" + dep("org.beispiel", "a", "compile") + dep("org.beispiel", "b", "runtime")
                + dep("org.beispiel", "c", "provided") + dep("org.beispiel", "d", "test") + "</dependencies>");
        assertEquals(List.of("p.xml: Abh\u00E4ngigkeit org.beispiel:a hat Scope compile, erlaubt ist test",
                "p.xml: Abh\u00E4ngigkeit org.beispiel:b hat Scope runtime, erlaubt ist test",
                "p.xml: Abh\u00E4ngigkeit org.beispiel:c hat Scope provided, erlaubt ist test"),
                pomVerstoesse("p.xml", xml, false));
    }

    @Test
    void selbsttest_pom_dependencyManagementUndPluginAbhaengigkeitSindKeinVerstoss() {
        String xml = pom("<dependencyManagement><dependencies>" + dep("org.beispiel", "bom", "import")
                + dep("org.beispiel", "verwaltet", null) + "</dependencies></dependencyManagement>\n"
                + "<build><pluginManagement><plugins><plugin><artifactId>pm</artifactId><dependencies>"
                + dep("org.beispiel", "pm-dep", null) + "</dependencies></plugin></plugins></pluginManagement>"
                + "<plugins><plugin><artifactId>pl</artifactId><dependencies>" + dep("org.beispiel", "pl-dep", null)
                + "</dependencies></plugin></plugins></build>\n"
                + "<dependencies>" + dep("org.beispiel", "lib", "test") + "</dependencies>");
        assertEquals(List.of(), pomVerstoesse("p.xml", xml, false));
        // Die Plugin-Abhaengigkeit ist im Modul-POM ebenfalls keine Modulabhaengigkeit.
        assertEquals(1, pomVerstoesse("m.xml", xml, true).size());
    }

    @Test
    void selbsttest_pom_abhaengigkeitInProfilUndNichtWohlgeformtesPom() {
        String xml = pom("<profiles><profile><dependencies>" + dep("org.beispiel", "prof", "compile")
                + "</dependencies></profile></profiles>");
        assertEquals(1, pomVerstoesse("p.xml", xml, false).size());
        assertThrows(IllegalStateException.class, () -> pomVerstoesse("kaputt.xml", "<project>", false));
    }
}
