package de.skillshield.ui.swing;

import de.skillshield.ui.logik.Zwischenablage;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Zwischenablage über AWT (Architektur-Plan OF-3, D-6, AK-33). Die Klasse bekommt die {@link Clipboard}-Instanz
 * von außen: im Programm die des Systems, im Test eine selbst erzeugte lokale.
 *
 * <p>Abgelegt wird ein eigenes {@link Transferable}, das <em>nur</em> {@link DataFlavor#stringFlavor} anbietet (nicht
 * {@code StringSelection}, das zusätzlich veraltete Formate anbietet). Geleert wird durch Ablegen eines
 * {@code Transferable} ohne jedes Format. Ist die Zwischenablage von einem anderen Programm gesperrt (die AWT meldet
 * das als {@link IllegalStateException}), wird bis zu dreimal im Abstand von 50 ms versucht; danach gibt die Klasse
 * still auf (benannte Grenze G-8). Jede andere Ausnahme beendet den Zugriff sofort. Keine Methode wirft; keine
 * Ausgabe, kein Text in Meldungen.
 *
 * <p>Besitz und Vertrauen (S-1): Die AWT trägt einen neuen Inhalt Java-intern ein, <em>bevor</em> sie die native
 * Zwischenablage öffnet. Scheitert das Ablegen, liefern {@code isDataFlavorAvailable} und {@code getData} danach
 * trotzdem den nie abgelegten Text. Deshalb verfolgt die Klasse mit einem {@link ClipboardOwner} je Ablegeversuch,
 * ob das Programm die Zwischenablage noch besitzt, und merkt den zuletzt <em>erfolgreich</em> abgelegten Text. Nach
 * einem gescheiterten Ablegen oder Leeren vertraut {@link #leseText()} dem Java-internen Stand nicht mehr: Solange
 * das Programm Besitzer ist, liefert es den gemerkten Text. Ein erfolgreiches Ablegen oder Leeren stellt das
 * Vertrauen wieder her, ebenso der Besitzverlust des jüngsten Versuchs (die AWT hat den internen Stand dann
 * verworfen, H-5). Der gemerkte Text wird beim Besitzverlust und nach dem Leeren verworfen. Der Besitzverlust kommt
 * auf einem anderen Thread; der Zustand ist synchronisiert; trifft er vor der Auswertung des Ablegens ein, wird er
 * nicht überschrieben (H-2). {@link #leeren()} meldet, ob das Leeren gelang (S-1, Durchlauf 3). Bleibende
 * Grenze: Zwischen einem fremden Kopieren und der Benachrichtigung über den Besitzverlust liefert die Klasse im
 * Zustand "nicht vertrauenswürdig" noch den gemerkten Text.
 */
public final class AwtZwischenablage implements Zwischenablage {

    /** Höchstzahl der Versuche bei gesperrter Zwischenablage (der erste eingeschlossen). */
    static final int VERSUCHE = 3;
    /** Wartezeit zwischen zwei Versuchen in Millisekunden. */
    static final long ABSTAND_MS = 50;

    /** Wartet zwischen zwei Versuchen; im Test ersetzbar, damit nicht wirklich geschlafen wird. */
    @FunctionalInterface
    interface Pause {
        void warte(long millis) throws InterruptedException;
    }

    private final Clipboard clipboard;
    private final Pause pause;
    private final Object sperre = new Object();
    /** Besitz des jüngsten Ablegeversuchs; Meldungen älterer Besitzer sind ohne Wirkung. */
    private Besitz aktuellerBesitz;
    /** {@code true}, wenn das Programm die Zwischenablage (laut Verfolgung) besitzt. */
    private boolean besitzer;
    /** {@code true}, wenn der Java-interne Stand wegen eines gescheiterten Ablegens nicht vertrauenswürdig ist. */
    private boolean unsicher;
    /** Der zuletzt erfolgreich abgelegte Text; {@code null} nach Besitzverlust und nach dem Leeren. */
    private String abgelegterText;

    /**
     * Besitzer eines Ablegeversuchs; merkt den Verlust, solange er der jüngste ist. {@code verloren} (H-2) hält fest,
     * dass der Verlust schon eingetroffen ist, bevor das Ablegen ausgewertet wurde; Zugriff nur unter {@code sperre}.
     * Verliert der jüngste Versuch den Besitz, hat die AWT den Java-internen Stand verworfen: Die Zwischenablage ist
     * wieder vertrauenswürdig lesbar (H-5). Ein nie abgelegter Text wird dadurch nicht gemeldet, denn der interne
     * Stand ist dann der des neuen, fremden Besitzers.
     */
    private final class Besitz implements ClipboardOwner {
        private boolean verloren;

        @Override
        public void lostOwnership(Clipboard c, Transferable inhalt) {
            synchronized (sperre) {
                verloren = true;
                if (aktuellerBesitz == this) {
                    besitzer = false;
                    abgelegterText = null;
                    unsicher = false;
                }
            }
        }
    }

    /** Zwischenablage über die übergebene {@link Clipboard} (System-Zwischenablage oder lokale im Test). */
    public AwtZwischenablage(Clipboard clipboard) {
        this(clipboard, Thread::sleep);
    }

    AwtZwischenablage(Clipboard clipboard, Pause pause) {
        this.clipboard = Objects.requireNonNull(clipboard, "clipboard");
        this.pause = Objects.requireNonNull(pause, "pause");
    }

    @Override
    public boolean setzeText(String text) {
        Objects.requireNonNull(text, "text");
        return ablegen(new NurText(text), text);
    }

    @Override
    public Optional<String> leseText() {
        synchronized (sperre) {
            if (unsicher) {
                return besitzer ? Optional.ofNullable(abgelegterText) : Optional.empty();
            }
        }
        for (int versuch = 1; versuch <= VERSUCHE; versuch++) {
            try {
                if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    return Optional.empty();
                }
                Object daten = clipboard.getData(DataFlavor.stringFlavor);
                return daten instanceof String text ? Optional.of(text) : Optional.empty();
            } catch (IllegalStateException gesperrt) {
                if (versuch == VERSUCHE || !warten()) {
                    return Optional.empty();
                }
            } catch (UnsupportedFlavorException | IOException | RuntimeException anderes) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean leeren() {
        return ablegen(new OhneFormat(), null);
    }

    /**
     * Legt den Inhalt ab; {@code true}, wenn die Zwischenablage ihn angenommen hat.
     *
     * @param merken der Text, der bei Erfolg gemerkt wird; {@code null} beim Leeren
     */
    private boolean ablegen(Transferable inhalt, String merken) {
        Besitz neuerBesitz = new Besitz();
        synchronized (sperre) {
            aktuellerBesitz = neuerBesitz;
        }
        boolean abgelegt = versuchen(inhalt, neuerBesitz);
        synchronized (sperre) {
            if (abgelegt) {
                if (!neuerBesitz.verloren) {
                    besitzer = true;
                    abgelegterText = merken;
                }
                unsicher = false;
            } else {
                // Auch bei schon eingetroffenem Verlust bleibt es sicherheitshalber beim Misstrauen (fail-closed).
                unsicher = true;
            }
        }
        return abgelegt;
    }

    private boolean versuchen(Transferable inhalt, ClipboardOwner neuerBesitz) {
        for (int versuch = 1; versuch <= VERSUCHE; versuch++) {
            try {
                clipboard.setContents(inhalt, neuerBesitz);
                return true;
            } catch (IllegalStateException gesperrt) {
                if (versuch == VERSUCHE || !warten()) {
                    return false;
                }
            } catch (RuntimeException anderes) {
                return false;
            }
        }
        return false;
    }

    /** {@code false}, wenn das Warten unterbrochen wurde (das Unterbrechungszeichen bleibt gesetzt). */
    private boolean warten() {
        try {
            pause.warte(ABSTAND_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Ein einziger Inhalt in einem einzigen Format: reiner Text ({@code stringFlavor}). */
    private static final class NurText implements Transferable {
        private static final DataFlavor[] FORMATE = {DataFlavor.stringFlavor};
        private final String text;

        NurText(String text) {
            this.text = text;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return FORMATE.clone();
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.stringFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return text;
        }

        /** Nur die Länge, nie der Text. */
        @Override
        public String toString() {
            return "NurText[zeichen=" + text.length() + "]";
        }
    }

    /** Inhalt ohne jedes Format: leert die Zwischenablage. */
    private static final class OhneFormat implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[0];
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return false;
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
