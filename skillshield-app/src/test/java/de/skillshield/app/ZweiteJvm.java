package de.skillshield.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Kapselt den Start einer zweiten JVM fuer die Nachweise mit der fertigen JAR (nur Testcode). Arbeits-,
 * Temp- und Home-Verzeichnis zeigen auf leere Ordner, die Ausgaben landen in Dateien ausserhalb dieser Ordner
 * (nie auf der Konsole des Testlaufs), der Prozess hat eine Zeitgrenze.
 */
final class ZweiteJvm {

    /** Ergebnis eines Prozesslaufs. */
    record Ergebnis(int code, String standardausgabe, String fehlerausgabe) { }

    private final Path arbeit;
    private final Path temp;
    private final Path heim;
    private final Path ausgaben;

    /** @param wurzel Ordner, in dem die drei leeren Verzeichnisse und der Ausgabeordner angelegt werden */
    ZweiteJvm(Path wurzel) throws IOException {
        this.arbeit = Files.createDirectory(wurzel.resolve("arbeit"));
        this.temp = Files.createDirectory(wurzel.resolve("temp"));
        this.heim = Files.createDirectory(wurzel.resolve("heim"));
        this.ausgaben = Files.createDirectory(wurzel.resolve("ausgaben"));
    }

    /** Startet {@code java} mit den Standardoptionen der Nachweise und den gegebenen weiteren Argumenten. */
    Ergebnis starte(List<String> argumente) throws IOException, InterruptedException {
        List<String> befehl = new ArrayList<>();
        befehl.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        befehl.add("-Djava.awt.headless=true");
        befehl.add("-XX:-UsePerfData");
        befehl.add("-XX:+DisableAttachMechanism");
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            befehl.add("-XX:ErrorFile=NUL"); // NUL gibt es nur unter Windows
        }
        befehl.add("-XX:-CreateCoredumpOnCrash");
        befehl.add("-Dstdout.encoding=UTF-8");
        befehl.add("-Dstderr.encoding=UTF-8");
        befehl.add("-Djava.io.tmpdir=" + temp);
        befehl.add("-Duser.home=" + heim);
        befehl.addAll(argumente);
        Path aus = ausgaben.resolve("aus.txt");
        Path fehler = ausgaben.resolve("fehler.txt");
        ProcessBuilder bauer = new ProcessBuilder(befehl)
                .directory(arbeit.toFile())
                .redirectInput(ProcessBuilder.Redirect.from(nullDatei().toFile()))
                .redirectOutput(aus.toFile())
                .redirectError(fehler.toFile());
        // hsperfdata & Co. richten sich nach TEMP/TMP/USERPROFILE, nicht nach java.io.tmpdir
        bauer.environment().put("TEMP", temp.toString());
        bauer.environment().put("TMP", temp.toString());
        bauer.environment().put("USERPROFILE", heim.toString());
        Process prozess = bauer.start();
        if (!prozess.waitFor(120, TimeUnit.SECONDS)) {
            prozess.destroyForcibly();
            throw new IllegalStateException("Zweite JVM hat die Zeitgrenze ueberschritten");
        }
        return new Ergebnis(prozess.exitValue(), Files.readString(aus, StandardCharsets.UTF_8),
                Files.readString(fehler, StandardCharsets.UTF_8));
    }

    private Path nullDatei() throws IOException {
        return Files.writeString(ausgaben.resolve("eingabe.txt"), "");
    }

    /** Namen aller Dateien und Ordner, die in Arbeits-, Temp- oder Home-Verzeichnis entstanden sind. */
    List<String> spuren() {
        List<String> spuren = new ArrayList<>();
        for (Path ordner : List.of(arbeit, temp, heim)) {
            try (Stream<Path> s = Files.walk(ordner)) {
                s.filter(p -> !p.equals(ordner)).forEach(p -> spuren.add(ordner.getFileName() + "/"
                        + ordner.relativize(p).toString().replace('\\', '/')));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return spuren;
    }
}
