package de.skillshield.ui;

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
 * Kommentaren. Besteht auch auf leerem Quelltext. Vor dem Abgleich werden Unicode-Escapes aufgeloest sowie
 * Leerraum und Kommentar-Einschuebe um Punkt und {@code ::} und vor {@code (} normalisiert; geprueft wird der
 * Text roh (konservativ) und ohne Kommentare, wobei ein Zustandsautomat Zeichenketten, Zeichen und Textbloecke
 * beachtet (hoechster Treffer zaehlt). Der Test ist ein Sicherheitsnetz gegen Versehen, kein Beweis: Was nicht
 * auf der Liste steht, Reflection mit zusammengesetzten Zeichenketten und Aufrufe ueber Hilfsklassen fremder
 * Bibliotheken erkennt er nicht; dafuer gilt die Review-Pflicht.
 */
class QuelltextWaechterTest {

    private static final Path QUELLE = Path.of("src", "main", "java");

    /** Verbotene Zeichenfolgen (Netzwerk, Platte, Konsole, Einstellungen). */
    static final List<String> VERBOTEN = List.of(
            "java.net.", "javax.net.", "java.net.http", "java.awt.Desktop", ".browse(", "Socket", "URLConnection",
            "HttpClient", "java.rmi", "javax.naming",
            "FileOutputStream", "FileWriter", "Files.write", "newOutputStream", "Files.newBufferedWriter",
            "Files.createFile", "Files.createTempFile", "Files.createTempDirectory", "RandomAccessFile",
            "java.util.prefs", "java.util.logging.FileHandler", "printStackTrace", "System.out", "System.err",
            "ProcessBuilder", "Runtime.getRuntime", ".exec(", "Desktop.getDesktop", "FileHandler",
            "java.util.logging", "System.getLogger", "File.createTempFile", "Files.copy", "Files.move",
            "newByteChannel", "FileChannel", "ImageIO.write", "PrintWriter(", "PrintStream(",
            "newFileSystem", "dumpStack", "Class.forName", "MethodHandles", "getDeclaredMethod",
            "import static java.lang.System.*", "import static java.nio.file.Files.*",
            "import static java.lang.Runtime.*", "System.console", "getMethod(", "loadClass(", "System.load",
            "createImageOutputStream", "PrinterJob", "getField(", "getDeclaredField", "Formatter(",
            "createNewFile", ".print(", "getPrintJob");

    private static final Path POM = Path.of("pom.xml");
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u+([0-9a-fA-F]{4})");
    private static final Pattern BLOCKKOMMENTAR_RAND = Pattern.compile("/\\*|\\*/");
    private static final Pattern PUNKT_ODER_REFERENZ = Pattern.compile("\\s*(\\.|::)\\s*");
    private static final Pattern LEERRAUM = Pattern.compile("\\s+");

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

    /** Erlaubte Ausnahmen: Datei, Wort und hoechstens erlaubte Zahl an Vorkommen (hier keine). */
    static final Map<String, Map<String, Integer>> AUSNAHMEN = Map.of();

    static List<String> verstoesse(String datei, String text) {
        List<String> liste = new ArrayList<>();
        Map<String, Integer> erlaubt = AUSNAHMEN.getOrDefault(datei, Map.of());
        List<String> varianten = varianten(text);
        for (String wort : VERBOTEN) {
            int anzahl = 0;
            for (String variante : varianten) {
                anzahl = Math.max(anzahl, zaehle(variante, wort));
            }
            if (anzahl > erlaubt.getOrDefault(wort, 0)) {
                liste.add(datei + ": " + wort + " (" + anzahl + "x)");
            }
        }
        return liste;
    }

    /**
     * Normalisiert Quelltext fuer den Abgleich: Kommentar-Begrenzer werden zu Leerraum, Leerraum um Punkt und
     * {@code ::} entfaellt ({@code Files::write} und {@code System .out} werden zu {@code Files.write} und
     * {@code System.out}), Leerraum wird zusammengezogen und vor {@code (} entfernt.
     */
    static String normalisiert(String text) {
        String s = BLOCKKOMMENTAR_RAND.matcher(text).replaceAll(" ");
        s = PUNKT_ODER_REFERENZ.matcher(s).replaceAll(".");
        s = LEERRAUM.matcher(s).replaceAll(" ");
        return s.replace(" (", "(");
    }

    /**
     * Zu pruefende Fassungen: Quelltext mit Kommentartext (konservativ, nur normalisiert) und Quelltext ohne
     * Kommentare (Einschuebe); die Kommentare entfernt {@link #ohneKommentare}, das Zeichenketten, Zeichen und
     * Textbloecke beachtet.
     */
    static List<String> varianten(String text) {
        String klartext = ohneUnicodeEscapes(text);
        return List.of(normalisiert(klartext), normalisiert(ohneKommentare(klartext)));
    }

    private enum Modus { CODE, ZEILE, BLOCK, ZEICHENKETTE, ZEICHEN, TEXTBLOCK }

    /**
     * Entfernt Zeilen- und Blockkommentare (durch ein Leerzeichen ersetzt) mit einem kleinen Zustandsautomaten:
     * Zeichenketten, Zeichen- und Textblock-Literale bleiben samt Inhalt erhalten, ein Kommentarbeginn darin zaehlt
     * nicht, ein Anfuehrungszeichen in einem Kommentar ebenfalls nicht. Backslash-Escapes werden uebersprungen;
     * eine nicht beendete Zeichenkette oder ein Zeichen endet am Zeilenende.
     */
    static String ohneKommentare(String quelle) {
        StringBuilder aus = new StringBuilder(quelle.length());
        Modus modus = Modus.CODE;
        int i = 0;
        while (i < quelle.length()) {
            char c = quelle.charAt(i);
            switch (modus) {
                case CODE -> {
                    if (quelle.startsWith("//", i)) {
                        modus = Modus.ZEILE;
                        aus.append(' ');
                        i += 2;
                    } else if (quelle.startsWith("/*", i)) {
                        modus = Modus.BLOCK;
                        aus.append(' ');
                        i += 2;
                    } else if (quelle.startsWith("\"\"\"", i)) {
                        modus = Modus.TEXTBLOCK;
                        aus.append("\"\"\"");
                        i += 3;
                    } else {
                        if (c == '"') {
                            modus = Modus.ZEICHENKETTE;
                        } else if (c == '\'') {
                            modus = Modus.ZEICHEN;
                        }
                        aus.append(c);
                        i++;
                    }
                }
                case ZEILE -> {
                    if (c == '\n' || c == '\r') {
                        modus = Modus.CODE;
                        aus.append(c);
                    }
                    i++;
                }
                case BLOCK -> {
                    if (quelle.startsWith("*/", i)) {
                        modus = Modus.CODE;
                        i += 2;
                    } else {
                        i++;
                    }
                }
                case ZEICHENKETTE, ZEICHEN -> {
                    char ende = modus == Modus.ZEICHENKETTE ? '"' : '\'';
                    aus.append(c);
                    i++;
                    if (c == '\\' && i < quelle.length()) {
                        aus.append(quelle.charAt(i));
                        i++;
                    } else if (c == ende || c == '\n' || c == '\r') {
                        modus = Modus.CODE;
                    }
                }
                case TEXTBLOCK -> {
                    if (c == '\\' && i + 1 < quelle.length()) {
                        aus.append(c).append(quelle.charAt(i + 1));
                        i += 2;
                    } else if (quelle.startsWith("\"\"\"", i)) {
                        modus = Modus.CODE;
                        aus.append("\"\"\"");
                        i += 3;
                    } else {
                        aus.append(c);
                        i++;
                    }
                }
            }
        }
        return aus.toString();
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
    void selbsttest_leerraumKommentareUndMethodenreferenzenUmgehenDenWaechterNicht() {
        List<String> umgehungen = List.of(
                "Files::write", "Files :: write", "Files\n::\nwrite", "System .out.println();",
                "System. out.println();", "System/**/.err.println();", "System /* x */ . /* y */ out",
                "System.// x\n out", "System // x\n .out", "new  PrintWriter (x)", "new /* x */ PrintWriter(x)",
                "Class\n.forName(x)", "Runtime/**/.getRuntime()", "x.getMethod (n)", "l.loadClass (n)",
                "System.\tload (p)", "System :: console", "ImageIO.createImageOutputStream(f)",
                "java.awt.print.PrinterJob.getPrinterJob()");
        for (String umgehung : umgehungen) {
            assertFalse(verstoesse("A.java", "class A { Object o = " + umgehung + "; }").isEmpty(), umgehung);
        }
    }

    @Test
    void selbsttest_statischeSternchenImportsSindVerboten() {
        List<String> importe = List.of(
                "import static java.lang.System.*;", "import static java.nio.file.Files.*;",
                "import static java.lang.Runtime.*;", "import   static   java.lang.System . * ;",
                "import static java.lang.System/**/.*;", "import static java.nio.file.Files\n.*;");
        for (String zeile : importe) {
            assertFalse(verstoesse("A.java", zeile + "\nclass A { }").isEmpty(), zeile);
        }
        assertTrue(verstoesse("A.java", "import static java.lang.Math.*;\nclass A { }").isEmpty());
    }

    @Test
    void selbsttest_normalisierung() {
        assertEquals("a.b.c(d) e", normalisiert("a . b :: c (d)\n\t e"));
        assertEquals("System.out", normalisiert("System/**/.out"));
        assertEquals(List.of(), verstoesse("A.java", "class A { }"));
    }

    @Test
    void selbsttest_zeichenkettenVerdeckenKeinenKommentarbeginn() {
        String dq = "\"";
        String tb = dq + dq + dq;
        String stelle = "; Object o = System/*x*/.out;";
        List<String> quellen = List.of(
                "String u = " + dq + "//" + dq + stelle,
                "String u = " + dq + "/*" + dq + stelle + " /* ende */",
                "String u = " + dq + "\\" + dq + "//" + dq + stelle,
                "char c = '" + dq + "'" + stelle,
                "char c = '\\''; String s = " + dq + "//" + dq + stelle,
                "String t = " + tb + "\n// kein Kommentar " + tb + stelle,
                "String t = " + tb + "\n  \\" + dq + dq + dq + " //\n" + tb + stelle,
                "// it's ein Kommentar\nObject o = System/*x*/.out;",
                "/* \" */ Object o = System/*x*/.out;");
        for (String quelle : quellen) {
            assertFalse(verstoesse("A.java", "class A { " + quelle + " }").isEmpty(), quelle);
        }
    }

    @Test
    void selbsttest_ohneKommentareBehandeltLiteraleKorrekt() {
        assertEquals("a   b", ohneKommentare("a /*x*/ b"));
        assertEquals("a  \nb", ohneKommentare("a //x\nb"));
        assertEquals("\"//\"  x", ohneKommentare("\"//\" /* y */x"));
        assertEquals("'\"'  ", ohneKommentare("'\"' //\""));
        assertEquals("\"a\\\"//b\"", ohneKommentare("\"a\\\"//b\""));
        assertEquals("\"\"\"//\"\"\" ", ohneKommentare("\"\"\"//\"\"\"/**/"));
        assertEquals("\"unbeendet\nx ", ohneKommentare("\"unbeendet\nx/*"));
    }

    @Test
    void selbsttest_vollQualifizierteUndZusaetzlicheFormenWerdenErkannt() {
        List<String> formen = List.of(
                "new java.io.PrintWriter(x)", "new java.io.PrintStream(x)", "java.io.PrintWriter (x)",
                "newOutputStream(p)", "java.nio.file.Files.newOutputStream(p)", "c.getField(n)",
                "c.getDeclaredField(n)", "new java.util.Formatter(x)", "new Formatter (x)", "f.createNewFile()");
        for (String form : formen) {
            assertFalse(verstoesse("A.java", "class A { Object o = " + form + "; }").isEmpty(), form);
        }
        assertTrue(verstoesse("A.java", "class A { String s = String.format(\"%s\", 1); }").isEmpty());
    }

    @Test
    void selbsttest_druckenIstVerbotenAberPrintlnUndPrintfSindEsHierNichtDoppelt() {
        List<String> drucken = List.of(
                "w.print(x)", "w.print (x)", "w .print(x)", "w.\nprint(x)", "w/**/.print(x)",
                "Toolkit.getDefaultToolkit().getPrintJob(f, t, p)");
        for (String form : drucken) {
            assertFalse(verstoesse("A.java", "class A { Object o = " + form + "; }").isEmpty(), form);
        }
        assertEquals(1, verstoesse("A.java", "class A { Object o = w.print(x); }").size());
        // Diese Formen treffen nur ihre eigenen Eintraege, nicht ".print(".
        assertTrue(verstoesse("A.java", "class A { Object o = w.println(x); }").isEmpty());
        assertTrue(verstoesse("A.java", "class A { Object o = w.printf(x); }").isEmpty());
        assertTrue(verstoesse("A.java", "class A { Object o = blueprint(x); String s = \"print(\"; }").isEmpty());
        assertEquals(1, verstoesse("A.java", "class A { void f(Throwable t) { t.printStackTrace(); } }").size());
    }

    @Test
    void selbsttest_harmloserTextWirdNichtBeanstandet() {
        assertTrue(verstoesse("A.java", "class A { int x = 1; String s = \"Hallo\"; }").isEmpty());
        assertTrue(verstoesse("A.java", "").isEmpty());
        assertTrue(verstoesse("A.java", "class A { Object o = Files::exists; String s = \"a . b\"; int[] a; "
                + "void f(Object... m) { m.getClass (); } /* Block */ // Zeile\n}").isEmpty());
    }

    @Test
    void selbsttest_dasModulHatKeineAusnahmen() {
        assertTrue(AUSNAHMEN.isEmpty());
        assertFalse(verstoesse("SkillShieldApp.java", "System" + ".err.println();").isEmpty());
    }
}
