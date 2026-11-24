package de.skillshield.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.FlatLightLaf;
import de.skillshield.ui.logik.Dialoge;
import de.skillshield.ui.logik.Fehlerbehandlung;
import de.skillshield.ui.logik.Rueckfrage;
import de.skillshield.ui.logik.ScanAuftrag;
import de.skillshield.ui.logik.Sitzung;
import de.skillshield.ui.logik.Texte;
import de.skillshield.ui.swing.AwtZwischenablage;
import de.skillshield.ui.swing.HauptPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Startklasse und Verdrahtung ohne sichtbares Fenster (AK-1, AK-2, AK-43, AK-50, AK-51, AK-52). */
class SkillShieldAppTest {

    private static final String MARKER = "klartext-marker-zeta@example.org";

    private static final String FLATLAF_EIGENSCHAFT = "flatlaf.useNativeLibrary";

    private Thread.UncaughtExceptionHandler vorherigerHandler;
    private Locale vorherigeSprache;
    private String vorherigeEigenschaft;

    @BeforeEach
    void globalenZustandMerken() {
        vorherigerHandler = Thread.getDefaultUncaughtExceptionHandler();
        vorherigeSprache = Locale.getDefault();
        vorherigeEigenschaft = System.getProperty(FLATLAF_EIGENSCHAFT);
    }

    @AfterEach
    void globalenZustandZuruecksetzen() {
        Thread.setDefaultUncaughtExceptionHandler(vorherigerHandler);
        Locale.setDefault(vorherigeSprache);
        if (vorherigeEigenschaft == null) {
            System.clearProperty(FLATLAF_EIGENSCHAFT);
        } else {
            System.setProperty(FLATLAF_EIGENSCHAFT, vorherigeEigenschaft);
        }
    }

    /** Dialoge mit fester Antwort; merkt sich die Fragen. */
    private static final class Antwort implements Dialoge {
        final boolean ja;
        final List<Rueckfrage> fragen = new ArrayList<>();

        Antwort(boolean ja) {
            this.ja = ja;
        }

        @Override
        public boolean bestaetigt(Rueckfrage frage) {
            fragen.add(frage);
            return ja;
        }

        @Override
        public boolean erneutVersuchen(Rueckfrage frage) {
            return false;
        }

        @Override
        public void fehler(String text) {
        }
    }

    private static void imEreignisThread(Runnable aktion) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(aktion);
    }

    private static List<Component> alle(Component wurzel) {
        List<Component> liste = new ArrayList<>();
        liste.add(wurzel);
        if (wurzel instanceof Container c) {
            for (Component kind : c.getComponents()) {
                liste.addAll(alle(kind));
            }
        }
        return liste;
    }

    private static Sitzung sitzungMitZuordnung(Dialoge dialoge, Clipboard ablage) {
        Sitzung sitzung = Sitzung.standard(new AwtZwischenablage(ablage), dialoge);
        ScanAuftrag auftrag = sitzung.scanAnfordern("Mail " + MARKER).orElseThrow();
        sitzung.scanAbgeschlossen(auftrag, auftrag.ausfuehren());
        sitzung.alleAnwaehlen();
        return sitzung;
    }

    // ------------------------------------------------------------------ Start ohne Bildschirm

    @Test
    void starteOhneBildschirmLiefertCode2UndGenauDenFestenText() {
        Assumptions.assumeTrue(GraphicsEnvironment.isHeadless(), "nur ohne Bildschirm");
        ByteArrayOutputStream puffer = new ByteArrayOutputStream();

        int code = SkillShieldApp.starte(new PrintStream(puffer, true, StandardCharsets.UTF_8));

        assertEquals(2, code);
        assertEquals("SkillShield benötigt eine grafische Oberfläche." + System.lineSeparator(),
                puffer.toString(StandardCharsets.UTF_8));
    }

    @Test
    void starteInstalliertDenZentralenFehlerHandlerAuchOhneBildschirm() {
        Assumptions.assumeTrue(GraphicsEnvironment.isHeadless(), "nur ohne Bildschirm");

        SkillShieldApp.starte(new PrintStream(new ByteArrayOutputStream()));

        assertInstanceOf(Fehlerbehandlung.class, Thread.getDefaultUncaughtExceptionHandler());
        assertEquals("false", System.getProperty("flatlaf.useNativeLibrary"));
    }

    @Test
    void zentralerHandlerSchreibtNichtsAufDieKonsoleAuchNichtMitMarkerInDerAusnahme() throws Exception {
        PrintStream aus = System.out;
        PrintStream fehler = System.err;
        ByteArrayOutputStream ausPuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream fehlerPuffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(ausPuffer, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(fehlerPuffer, true, StandardCharsets.UTF_8));
            SkillShieldApp.fehlerhandlerInstallieren(() -> null);
            Thread thread = new Thread(() -> {
                throw new IllegalStateException(MARKER);
            });
            thread.start();
            thread.join();
            imEreignisThread(() -> { });
        } finally {
            System.setOut(aus);
            System.setErr(fehler);
        }

        assertEquals("", ausPuffer.toString(StandardCharsets.UTF_8));
        assertEquals("", fehlerPuffer.toString(StandardCharsets.UTF_8));
    }

    @Test
    void handlerMeldetNurFestenTextMitKlassennameOhneMarker() {
        List<String> angezeigt = new ArrayList<>();
        Fehlerbehandlung handler = new Fehlerbehandlung(angezeigt::add);

        handler.uncaughtException(Thread.currentThread(), new IllegalStateException(MARKER));

        assertEquals(List.of(Texte.FEHLER + " (java.lang.IllegalStateException)"), angezeigt);
        assertFalse(angezeigt.get(0).contains(MARKER));
    }

    // ------------------------------------------------------------------ Oberflaeche einrichten

    @Test
    void oberflaecheEinrichtenSetztFlatLafUndDeutscheTexte() {
        System.setProperty(FLATLAF_EIGENSCHAFT, "false"); // wie im Start, vor dem Laden von FlatLaf
        SkillShieldApp.oberflaecheEinrichten();

        assertInstanceOf(FlatLightLaf.class, UIManager.getLookAndFeel());
        assertEquals("Ja", UIManager.getString("OptionPane.yesButtonText"));
        assertEquals("Nein", UIManager.getString("OptionPane.noButtonText"));
        assertEquals("Abbrechen", UIManager.getString("OptionPane.cancelButtonText"));
        assertEquals("OK", UIManager.getString("OptionPane.okButtonText"));
    }

    @Test
    void fenstertitelIstGenauSkillShield() {
        assertEquals("SkillShield", Texte.FENSTERTITEL);
    }

    // ------------------------------------------------------------------ Fenster scheitert

    @Test
    void scheitertDasOeffnenDesFenstersZeigtDerHandlerNurFestenTextUndDerProzessEndetMitCode1() {
        List<String> angezeigt = new ArrayList<>();
        Thread.setDefaultUncaughtExceptionHandler(new Fehlerbehandlung(angezeigt::add));
        List<Integer> codes = new ArrayList<>();

        SkillShieldApp.fensterGeschuetztOeffnen(() -> {
            throw new IllegalStateException(MARKER);
        }, codes::add);

        assertEquals(List.of(1), codes);
        assertEquals(List.of(Texte.FEHLER + " (java.lang.IllegalStateException)"), angezeigt);
        assertFalse(angezeigt.get(0).contains(MARKER));
    }

    @Test
    void scheitertDasOeffnenMitEinemErrorEndetDerProzessEbenfallMitCode1() {
        List<String> angezeigt = new ArrayList<>();
        Thread.setDefaultUncaughtExceptionHandler(new Fehlerbehandlung(angezeigt::add));
        List<Integer> codes = new ArrayList<>();

        SkillShieldApp.fensterGeschuetztOeffnen(() -> {
            throw new AssertionError(MARKER);
        }, codes::add);

        assertEquals(List.of(1), codes);
        assertEquals(1, angezeigt.size());
        assertFalse(angezeigt.get(0).contains(MARKER));
    }

    @Test
    void gelingtDasOeffnenDesFensterBleibtDerProzessUnberuehrt() {
        List<Integer> codes = new ArrayList<>();
        AtomicInteger schritte = new AtomicInteger();

        SkillShieldApp.fensterGeschuetztOeffnen(schritte::incrementAndGet, codes::add);

        assertEquals(1, schritte.get());
        assertEquals(List.of(), codes);
    }

    // ------------------------------------------------------------------ Verdrahtung

    @Test
    void bauenLiefertPanelMitAllenBindungenOhneFenster() throws Exception {
        AtomicInteger ende = new AtomicInteger();
        SkillShieldApp.Bausatz[] bausatz = new SkillShieldApp.Bausatz[1];

        imEreignisThread(() -> bausatz[0] = SkillShieldApp.bauen(new Clipboard("test"), new Antwort(true),
                ende::incrementAndGet));

        HauptPanel panel = bausatz[0].panel();
        assertNotNull(bausatz[0].sitzung());
        assertEquals("hauptPanel", panel.getName());
        assertEquals(HauptPanel.MINDESTGROESSE, panel.getMinimumSize());
        assertEquals(0, ende.get());
        List<String> namen = alle(panel).stream().map(Component::getName).toList();
        assertTrue(namen.contains("beenden"));
        assertTrue(namen.contains("zuruecksetzen"));
        assertEquals("Bausatz", bausatz[0].toString());
    }

    @Test
    void schaltflaecheBeendenRuftDenGemeinsamenAblaufUndBeendetDenProzess() throws Exception {
        AtomicInteger ende = new AtomicInteger();
        SkillShieldApp.Bausatz[] bausatz = new SkillShieldApp.Bausatz[1];
        imEreignisThread(() -> bausatz[0] = SkillShieldApp.bauen(new Clipboard("test"), new Antwort(true),
                ende::incrementAndGet));
        JButton knopf = alle(bausatz[0].panel()).stream().filter(c -> "beenden".equals(c.getName()))
                .map(JButton.class::cast).findFirst().orElseThrow();

        imEreignisThread(knopf::doClick);

        assertEquals(1, ende.get());
    }

    @Test
    void beendenAblaufMitRueckfrageAbbrechenBeendetNichtUndVerwirftNichts() throws Exception {
        Antwort nein = new Antwort(false);
        Clipboard ablage = new Clipboard("test");
        Sitzung sitzung = sitzungMitZuordnung(nein, ablage);
        List<String> ablauf = new ArrayList<>();

        Runnable beenden = SkillShieldApp.beendenAblauf(sitzung, () -> ablauf.add("felder"),
                () -> ablauf.add("ende"));
        beenden.run();

        assertEquals(List.of(Rueckfrage.BEENDEN), nein.fragen);
        assertEquals(List.of(), ablauf);
    }

    @Test
    void beendenAblaufMitJaVerwirftFelderDannBeendetUndLeertEigenenInhaltDerAblage() {
        Antwort ja = new Antwort(true);
        Clipboard ablage = new Clipboard("test");
        Sitzung sitzung = sitzungMitZuordnung(ja, ablage);
        sitzung.fuerKiKopieren();
        assertTrue(ablage.isDataFlavorAvailable(DataFlavor.stringFlavor));
        List<String> ablauf = new ArrayList<>();

        SkillShieldApp.beendenAblauf(sitzung, () -> ablauf.add("felder"), () -> ablauf.add("ende")).run();

        assertEquals(List.of("felder", "ende"), ablauf);
        assertFalse(ablage.isDataFlavorAvailable(DataFlavor.stringFlavor));
        assertEquals("", sitzung.textFuerKi());
    }

    @Test
    void beendenAblaufOhneZuordnungBeendetOhneRueckfrage() {
        Antwort ja = new Antwort(true);
        Sitzung sitzung = Sitzung.standard(new AwtZwischenablage(new Clipboard("t")), ja);
        AtomicInteger ende = new AtomicInteger();

        SkillShieldApp.beendenAblauf(sitzung, () -> { }, ende::incrementAndGet).run();

        assertEquals(1, ende.get());
        assertEquals(List.of(), ja.fragen);
    }

    @Test
    void beendenAblaufWirftDieAusnahmeWeiterUndBeendetDannNicht() {
        Dialoge kaputt = new Dialoge() {
            @Override
            public boolean bestaetigt(Rueckfrage frage) {
                throw new IllegalStateException(MARKER);
            }

            @Override
            public boolean erneutVersuchen(Rueckfrage frage) {
                return false;
            }

            @Override
            public void fehler(String text) {
            }
        };
        Sitzung sitzung = sitzungMitZuordnung(kaputt, new Clipboard("test"));
        AtomicInteger ende = new AtomicInteger();

        Runnable beenden = SkillShieldApp.beendenAblauf(sitzung, () -> { }, ende::incrementAndGet);

        assertThrows(IllegalStateException.class, beenden::run);
        assertEquals(0, ende.get());
    }

    // ------------------------------------------------------------------ Quelltext-Einstieg

    @Test
    void dieSystemzwischenablageWirdImGanzenProduktivcodeAnGenauEinerStelleGeholt() throws IOException {
        int anzahl = 0;
        for (String modul : List.of("core", "io", "vault", "ui")) {
            anzahl += zaehle(Path.of("..", "skillshield-" + modul, "src", "main", "java"));
        }
        anzahl += zaehle(Path.of("src", "main", "java"));

        assertEquals(1, anzahl);
        assertTrue(Files.readString(Path.of("src", "main", "java", "de", "skillshield", "app", "SkillShieldApp.java"))
                .contains("getSystemClipboard"));
    }

    private static int zaehle(Path wurzel) throws IOException {
        if (!Files.isDirectory(wurzel)) {
            return 0;
        }
        List<Path> dateien;
        try (Stream<Path> s = Files.walk(wurzel)) {
            dateien = s.filter(p -> p.toString().endsWith(".java")).toList();
        }
        int summe = 0;
        for (Path datei : dateien) {
            String text = Files.readString(datei, StandardCharsets.UTF_8);
            for (int i = text.indexOf("getSystemClipboard"); i >= 0; i = text.indexOf("getSystemClipboard", i + 1)) {
                summe++;
            }
        }
        return summe;
    }
}
