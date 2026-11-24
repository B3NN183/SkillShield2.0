package de.skillshield.app;

import com.formdev.flatlaf.FlatLightLaf;
import de.skillshield.ui.logik.Dialoge;
import de.skillshield.ui.logik.Fehlerbehandlung;
import de.skillshield.ui.logik.Sitzung;
import de.skillshield.ui.logik.Texte;
import de.skillshield.ui.swing.AwtZwischenablage;
import de.skillshield.ui.swing.HauptFenster;
import de.skillshield.ui.swing.HauptPanel;
import de.skillshield.ui.swing.SwingDialoge;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.io.PrintStream;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.UIManager;

/**
 * Startpunkt des Programms (OF-1). Der Start richtet nur ein und öffnet das Fenster auf dem Ereignis-Thread; er
 * schreibt nichts auf die Platte (FlatLaf ohne native Bibliothek, keine gespeicherten Einstellungen) und nichts auf
 * die Konsole, mit einer Ausnahme: Ohne Bildschirm erscheint genau ein fester Text und der Prozess endet mit Code 2.
 *
 * <p>Beenden läuft auf jedem Weg (Schließen-Kreuz, Alt+F4, Schaltfläche) über denselben Ablauf
 * ({@link #beendenAblauf}): erst {@code Sitzung.beendenAnfordern()}, nur bei Erfolg Felder verwerfen und den Prozess
 * wirklich beenden, damit kein Prozess mit Klartext im Speicher weiterlebt.
 */
public final class SkillShieldApp {

    /** Fester Text ohne Bildschirm (OF-1, AK-51). */
    static final String KEIN_BILDSCHIRM = "SkillShield benötigt eine grafische Oberfläche.";

    /** Exit-Code ohne Bildschirm. */
    static final int CODE_KEIN_BILDSCHIRM = 2;

    /** Das Fenster, sobald es besteht; Elternfenster der Dialoge. */
    private static volatile Component fenster;

    private SkillShieldApp() {
    }

    /** Startet das Programm; endet nur im Fehlerfall von selbst (sonst durch "Beenden"). */
    public static void main(String[] args) {
        int code = starte(System.err);
        if (code != 0) {
            prozessBeendenMit(code);
        }
    }

    /**
     * Startet das Programm.
     *
     * @param fehlerausgabe Ziel des festen Textes ohne Bildschirm
     * @return 0, wenn das Öffnen des Fensters eingeplant wurde; 2 ohne Bildschirm
     */
    static int starte(PrintStream fehlerausgabe) {
        Objects.requireNonNull(fehlerausgabe, "fehlerausgabe");
        System.setProperty("flatlaf.useNativeLibrary", "false");
        Locale.setDefault(Locale.GERMANY);
        fehlerhandlerInstallieren(() -> fenster);
        if (GraphicsEnvironment.isHeadless()) {
            fehlerausgabe.println(KEIN_BILDSCHIRM);
            return CODE_KEIN_BILDSCHIRM;
        }
        EventQueue.invokeLater(SkillShieldApp::fensterOeffnen);
        return 0;
    }

    /**
     * Installiert den zentralen Fehler-Handler (AK-50, AK-51): Er ersetzt die Standardausgabe der JVM (Stacktrace),
     * zeigt nur den festen Text mit dem Klassennamen und verwirft still, wenn die Anzeige scheitert.
     */
    static Fehlerbehandlung fehlerhandlerInstallieren(Supplier<Component> eltern) {
        Fehlerbehandlung handler = new Fehlerbehandlung(new SwingDialoge(eltern)::fehler);
        Thread.setDefaultUncaughtExceptionHandler(handler);
        return handler;
    }

    /**
     * FlatLaf (hell) und deutsche Texte der Standarddialoge. Setzt voraus, dass {@link #starte} bereits
     * {@code flatlaf.useNativeLibrary=false} gesetzt hat (vor dem ersten Laden von FlatLaf).
     */
    static void oberflaecheEinrichten() {
        FlatLightLaf.setup();
        UIManager.put("OptionPane.yesButtonText", Texte.JA);
        UIManager.put("OptionPane.noButtonText", "Nein");
        UIManager.put("OptionPane.cancelButtonText", Texte.ABBRECHEN);
        UIManager.put("OptionPane.okButtonText", Texte.OK);
        UIManager.put("OptionPane.titleText", "Auswahl");
        UIManager.put("OptionPane.messageDialogTitle", "Meldung");
        UIManager.put("OptionPane.inputDialogTitle", "Eingabe");
    }

    /** Das Ergebnis des Zusammenbaus: Sitzung, Panel und der gemeinsame Beenden-Ablauf. */
    static final class Bausatz {
        private final Sitzung sitzung;
        private final HauptPanel panel;
        private final Runnable beenden;

        private Bausatz(Sitzung sitzung, HauptPanel panel, Runnable beenden) {
            this.sitzung = sitzung;
            this.panel = panel;
            this.beenden = beenden;
        }

        Sitzung sitzung() {
            return sitzung;
        }

        HauptPanel panel() {
            return panel;
        }

        /** Der Ablauf von Schaltfläche "Beenden", Schließen-Kreuz und Alt+F4. */
        Runnable beenden() {
            return beenden;
        }

        @Override
        public String toString() {
            return "Bausatz";
        }
    }

    /**
     * Baut Zwischenablage-Adapter, Dialoge, Sitzung und Panel zusammen (ohne Fenster, ohne Bildschirm aufrufbar).
     *
     * @param clipboard   die Zwischenablage (im Programm die des Systems)
     * @param dialoge     Rückfragen und Fehlerdialog (im Programm {@link SwingDialoge} mit dem Fenster als Eltern)
     * @param prozessEnde wird nach erfolgreichem Beenden-Ablauf gerufen und beendet den Prozess
     */
    static Bausatz bauen(Clipboard clipboard, Dialoge dialoge, Runnable prozessEnde) {
        Objects.requireNonNull(dialoge, "dialoge");
        Objects.requireNonNull(prozessEnde, "prozessEnde");
        AwtZwischenablage zwischenablage = new AwtZwischenablage(clipboard);
        Sitzung sitzung = Sitzung.standard(zwischenablage, dialoge);
        AtomicReference<HauptPanel> panelRef = new AtomicReference<>();
        Runnable beenden = beendenAblauf(sitzung, () -> panelRef.get().felderVerwerfen(), prozessEnde);
        HauptPanel panel = new HauptPanel(sitzung, zwischenablage, dialoge, beenden);
        panelRef.set(panel);
        return new Bausatz(sitzung, panel, beenden);
    }

    /**
     * Der gemeinsame Beenden-Ablauf: {@code sitzung.beendenAnfordern()}; nur bei {@code true} werden die Felder
     * verworfen und danach der Prozess beendet.
     */
    static Runnable beendenAblauf(Sitzung sitzung, Runnable felderVerwerfen, Runnable prozessEnde) {
        return () -> {
            if (sitzung.beendenAnfordern()) {
                felderVerwerfen.run();
                prozessEnde.run();
            }
        };
    }

    /** Die einzige Stelle des Produkts, die die Systemzwischenablage holt. */
    private static Clipboard systemZwischenablage() {
        return Toolkit.getDefaultToolkit().getSystemClipboard();
    }

    /** Schließt das Fenster und beendet den Prozess (Code 0). */
    private static void prozessBeenden() {
        if (fenster instanceof Window w) {
            w.dispose();
        }
        prozessBeendenMit(0);
    }

    /** Die einzige Stelle des Produkts, die den Prozess beendet. */
    private static void prozessBeendenMit(int code) {
        System.exit(code);
    }

    /** Öffnet das Fenster; nur auf dem Ereignis-Thread. */
    private static void fensterOeffnen() {
        fensterGeschuetztOeffnen(SkillShieldApp::fensterErzeugen, SkillShieldApp::prozessBeendenMit);
    }

    /**
     * Führt den Schritt aus, der das Fenster erzeugt und zeigt. Scheitert er, zeigt der zentrale Handler den festen
     * Text (nie die Meldung) und der Prozess endet mit Code 1: Ohne Fenster gäbe es nichts zu bedienen.
     *
     * @param fensterSchritt erzeugt und zeigt das Fenster (im Test ersetzbar)
     * @param prozessEnde    beendet den Prozess mit dem Code (im Test ersetzbar)
     */
    static void fensterGeschuetztOeffnen(Runnable fensterSchritt, IntConsumer prozessEnde) {
        try {
            fensterSchritt.run();
        } catch (RuntimeException | Error e) {
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
            prozessEnde.accept(1);
        }
    }

    private static void fensterErzeugen() {
        oberflaecheEinrichten();
        Bausatz bausatz = bauen(systemZwischenablage(), new SwingDialoge(() -> fenster),
                SkillShieldApp::prozessBeenden);
        HauptFenster hauptFenster = new HauptFenster(bausatz.panel(), bausatz.beenden());
        fenster = hauptFenster;
        hauptFenster.anzeigen();
    }
}
