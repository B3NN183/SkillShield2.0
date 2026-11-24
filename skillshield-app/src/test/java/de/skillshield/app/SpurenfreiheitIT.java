package de.skillshield.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Nachweis in einer zweiten JVM mit der gebauten JAR (laeuft nach {@code package}, siehe Surefire-Lauf
 * {@code jar-tests}): Start ohne Bildschirm (AK-1), nichts auf der Platte (AK-44), keine Konsolenausgabe ausser dem
 * festen Text (AK-51), ganze Sitzung ueber den zentralen Handler ohne Spuren (AK-44, AK-50, AK-51).
 */
class SpurenfreiheitIT {

    private static final String FESTER_TEXT = "SkillShield benötigt eine grafische Oberfläche."
            + System.lineSeparator();

    private static Path jar() {
        String pfad = System.getProperty("skillshield.jar");
        assertTrue(pfad != null && Files.isRegularFile(Path.of(pfad)),
                "skillshield.jar fehlt (nur im Lauf jar-tests nach package vorhanden)");
        return Path.of(pfad);
    }

    @Test
    void ohneBildschirmEndetDieJarMitCode2UndDemFestenTextUndHinterlaesstNichts(@TempDir Path wurzel)
            throws Exception {
        ZweiteJvm jvm = new ZweiteJvm(wurzel);

        ZweiteJvm.Ergebnis ergebnis = jvm.starte(List.of("-jar", jar().toString()));

        assertEquals(2, ergebnis.code());
        assertEquals("", ergebnis.standardausgabe());
        assertEquals(FESTER_TEXT, ergebnis.fehlerausgabe());
        assertEquals(List.of(), jvm.spuren());
    }

    @Test
    void zusaetzlicheArgumenteAendernNichtsAmVerhaltenOhneBildschirm(@TempDir Path wurzel)
            throws Exception {
        ZweiteJvm jvm = new ZweiteJvm(wurzel);

        ZweiteJvm.Ergebnis ergebnis = jvm.starte(List.of("-jar", jar().toString(), "x", "y"));

        assertEquals(2, ergebnis.code());
        assertEquals(FESTER_TEXT, ergebnis.fehlerausgabe());
        assertEquals(List.of(), jvm.spuren());
    }

    @Test
    void vollstaendigeSitzungMitMarkernUndErzwungenenFehlernHinterlaesstNichtsUndSchweigt(@TempDir Path wurzel)
            throws Exception {
        ZweiteJvm jvm = new ZweiteJvm(wurzel);
        String testklassen = System.getProperty("skillshield.testclasses");
        assertTrue(testklassen != null && Files.isDirectory(Path.of(testklassen)), "Testklassen fehlen");
        String klassenpfad = jar() + File.pathSeparator + testklassen;

        ZweiteJvm.Ergebnis ergebnis = jvm.starte(List.of("-cp", klassenpfad, "de.skillshield.app.SitzungsProbe"));

        assertEquals(0, ergebnis.code(), ergebnis.fehlerausgabe());
        assertEquals("", ergebnis.standardausgabe());
        assertEquals("", ergebnis.fehlerausgabe());
        assertFalse(ergebnis.standardausgabe().contains(SitzungsProbe.MARKER));
        assertFalse(ergebnis.fehlerausgabe().contains(SitzungsProbe.MARKER));
        assertEquals(List.of(), jvm.spuren());
    }

    @Test
    void hilfsklasseLiefertSpurenInAllenDreiVerzeichnissenAuchVerschachtelt(@TempDir Path wurzel)
            throws IOException {
        ZweiteJvm jvm = new ZweiteJvm(wurzel);
        assertEquals(List.of(), jvm.spuren());
        Files.writeString(wurzel.resolve("heim").resolve("x.txt"), "x");
        Files.writeString(wurzel.resolve("arbeit").resolve("hs_err_pid1.log"), "x");
        Files.writeString(Files.createDirectory(wurzel.resolve("temp").resolve("hsperfdata_u")).resolve("1"), "x");

        assertEquals(List.of("arbeit/hs_err_pid1.log", "temp/hsperfdata_u", "temp/hsperfdata_u/1", "heim/x.txt"),
                jvm.spuren());
    }
}
