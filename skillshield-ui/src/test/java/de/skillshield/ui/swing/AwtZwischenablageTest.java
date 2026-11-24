package de.skillshield.ui.swing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.EventQueue;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Der AWT-Adapter der Zwischenablage (AK-33, OF-3). Getestet wird nur gegen selbst erzeugte, lokale
 * {@link Clipboard}-Instanzen, nie gegen die Zwischenablage des Systems; das Warten ist durch eine Attrappe
 * ersetzt, es wird nicht wirklich geschlafen.
 */
class AwtZwischenablageTest {

    private static final String EMOJI = new String(Character.toChars(0x1F600));

    private final List<Long> pausen = new ArrayList<>();

    @AfterEach
    void unterbrechungszeichenLoeschen() {
        Thread.interrupted();
    }

    private AwtZwischenablage adapter(Clipboard clipboard) {
        return new AwtZwischenablage(clipboard, pausen::add);
    }

    /** Lokale Zwischenablage, die die ersten {@code ausfaelle} Zugriffe mit der Ausnahme scheitern laesst. */
    private static final class Stoerer extends Clipboard {
        private final RuntimeException ausnahme;
        private int ausfaelle;
        int zugriffe;

        Stoerer(int ausfaelle, RuntimeException ausnahme) {
            super("test");
            this.ausfaelle = ausfaelle;
            this.ausnahme = ausnahme;
        }

        private void stoeren() {
            zugriffe++;
            if (ausfaelle > 0) {
                ausfaelle--;
                throw ausnahme;
            }
        }

        @Override
        public synchronized void setContents(Transferable contents, ClipboardOwner owner) {
            stoeren();
            super.setContents(contents, owner);
        }

        @Override
        public boolean isDataFlavorAvailable(DataFlavor flavor) {
            stoeren();
            return super.isDataFlavorAvailable(flavor);
        }
    }

    /** Inhalt mit frei waehlbaren Formaten (Bild, HTML ...). */
    private static final class Fremd implements Transferable {
        private final DataFlavor flavor;
        private final Object daten;

        Fremd(DataFlavor flavor, Object daten) {
            this.flavor = flavor;
            this.daten = daten;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {flavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor f) {
            return flavor.equals(f);
        }

        @Override
        public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(f)) {
                throw new UnsupportedFlavorException(f);
            }
            if (daten instanceof IOException e) {
                throw e;
            }
            return daten;
        }
    }

    // ---------------------------------------------------------------- Ablegen

    @Test
    @SuppressWarnings("deprecation") // plainTextFlavor: gerade das veraltete Format darf nicht angeboten werden
    void legtGenauEinenInhaltInGenauEinemTextformatAb() throws Exception {
        Clipboard clipboard = new Clipboard("test");
        AwtZwischenablage zwischenablage = adapter(clipboard);

        assertTrue(zwischenablage.setzeText("Hallo Welt"));

        assertArrayEquals(new DataFlavor[] {DataFlavor.stringFlavor}, clipboard.getAvailableDataFlavors());
        Transferable inhalt = clipboard.getContents(null);
        assertFalse(inhalt.isDataFlavorSupported(DataFlavor.plainTextFlavor), "kein veraltetes Textformat");
        assertFalse(inhalt.isDataFlavorSupported(DataFlavor.fragmentHtmlFlavor));
        assertFalse(inhalt.isDataFlavorSupported(DataFlavor.javaFileListFlavor));
        assertEquals("Hallo Welt", inhalt.getTransferData(DataFlavor.stringFlavor));
        assertEquals(List.of(), pausen);
    }

    @Test
    void textUndLeseTextLaufenZeichengenauHinUndZurueck() {
        AwtZwischenablage zwischenablage = adapter(new Clipboard("test"));
        String text = "Zeile 1\r\nZeile\t2 " + EMOJI + " [[TEXT_1]]\n";

        assertTrue(zwischenablage.setzeText(text));

        assertEquals(Optional.of(text), zwischenablage.leseText());
    }

    @Test
    void dieZwischenablageDesAdaptersGehoertNieZurSystemZwischenablage() {
        // Der Adapter benutzt ausschliesslich die uebergebene Instanz.
        Clipboard lokal = new Clipboard("lokal");
        Clipboard andere = new Clipboard("andere");

        adapter(lokal).setzeText("nur lokal");

        assertEquals(Optional.empty(), adapter(andere).leseText());
    }

    // ---------------------------------------------------------------- Lesen: nur Text

    @Test
    void liestNurTextBildUndHtmlErgebenLeer() {
        Clipboard clipboard = new Clipboard("test");
        AwtZwischenablage zwischenablage = adapter(clipboard);

        assertEquals(Optional.empty(), zwischenablage.leseText(), "leere Zwischenablage");

        clipboard.setContents(new Fremd(DataFlavor.imageFlavor, new Object()), null);
        assertEquals(Optional.empty(), zwischenablage.leseText(), "Bild");

        clipboard.setContents(new Fremd(DataFlavor.fragmentHtmlFlavor, "<b>fett</b>"), null);
        assertEquals(Optional.empty(), zwischenablage.leseText(), "nur HTML");
        assertEquals(List.of(), pausen);
    }

    @Test
    void eineAusnahmeBeimLesenDerDatenErgibtLeerOhneWiederholung() {
        Clipboard clipboard = new Clipboard("test");
        clipboard.setContents(new Fremd(DataFlavor.stringFlavor, new IOException("GEHEIM-MARKER")), null);

        assertEquals(Optional.empty(), adapter(clipboard).leseText());
        assertEquals(List.of(), pausen);
    }

    @Test
    void liefertDerInhaltKeinenStringGiltDasAlsKeinText() {
        Clipboard clipboard = new Clipboard("test");
        clipboard.setContents(new Fremd(DataFlavor.stringFlavor, 42), null);

        assertEquals(Optional.empty(), adapter(clipboard).leseText());
    }

    // ---------------------------------------------------------------- Leeren

    @Test
    void leerenLegtEinenInhaltOhneJedesFormatAb() {
        Clipboard clipboard = new Clipboard("test");
        AwtZwischenablage zwischenablage = adapter(clipboard);
        zwischenablage.setzeText("Text");

        assertTrue(zwischenablage.leeren());

        assertEquals(0, clipboard.getAvailableDataFlavors().length);
        assertEquals(Optional.empty(), zwischenablage.leseText());
        assertTrue(zwischenablage.leeren(), "auch eine leere Zwischenablage laesst sich leeren");
    }

    // ---------------------------------------------------------------- gesperrte Zwischenablage (OF-3, G-8)

    @Test
    void beiSperreGibtLesenNachDreiVersuchenImAbstandVon50MsAufOhneAusnahme() {
        Stoerer clipboard = new Stoerer(100, new IllegalStateException("GEHEIM-MARKER"));

        Optional<String> ergebnis = adapter(clipboard).leseText();

        assertEquals(Optional.empty(), ergebnis);
        assertEquals(3, clipboard.zugriffe);
        assertEquals(List.of(50L, 50L), pausen, "zwei Pausen zwischen drei Versuchen");
    }

    @Test
    void beiSperreGibtAblegenNachDreiVersuchenAufUndMeldetFalse() {
        Stoerer clipboard = new Stoerer(100, new IllegalStateException());

        assertFalse(adapter(clipboard).setzeText("Text"));

        assertEquals(3, clipboard.zugriffe);
        assertEquals(List.of(50L, 50L), pausen);
    }

    @Test
    void beiSperreGibtLeerenNachDreiVersuchenStillAuf() {
        Stoerer clipboard = new Stoerer(100, new IllegalStateException());

        assertFalse(adapter(clipboard).leeren());

        assertEquals(3, clipboard.zugriffe);
        assertEquals(List.of(50L, 50L), pausen);
    }

    @Test
    void endetDieSperreVorDemDrittenVersuchGelingtDerZugriff() {
        Stoerer ablegen = new Stoerer(2, new IllegalStateException());
        assertTrue(adapter(ablegen).setzeText("Text"));
        assertEquals(3, ablegen.zugriffe);
        assertEquals(List.of(50L, 50L), pausen);

        pausen.clear();
        Stoerer lesen = new Stoerer(0, new IllegalStateException());
        lesen.setContents(new Fremd(DataFlavor.stringFlavor, "Inhalt"), null);
        lesen.zugriffe = 0;
        lesen.ausfaelle = 1;
        assertEquals(Optional.of("Inhalt"), adapter(lesen).leseText());
        assertEquals(List.of(50L), pausen);
    }

    @Test
    void jedeAndereAusnahmeBeendetDenZugriffSofortOhneWiederholung() {
        Stoerer lesen = new Stoerer(100, new SecurityException("GEHEIM-MARKER"));
        assertEquals(Optional.empty(), adapter(lesen).leseText());
        assertEquals(1, lesen.zugriffe);

        Stoerer ablegen = new Stoerer(100, new UnsupportedOperationException("GEHEIM-MARKER"));
        assertFalse(adapter(ablegen).setzeText("Text"));
        assertEquals(1, ablegen.zugriffe);

        Stoerer leeren = new Stoerer(100, new IllegalArgumentException("GEHEIM-MARKER"));
        assertFalse(adapter(leeren).leeren());
        assertEquals(1, leeren.zugriffe);
        assertEquals(List.of(), pausen);
    }

    @Test
    void wirdDasWartenUnterbrochenGibtDerAdapterAufUndBehaeltDasUnterbrechungszeichen() {
        Stoerer clipboard = new Stoerer(100, new IllegalStateException());
        AwtZwischenablage zwischenablage = new AwtZwischenablage(clipboard, millis -> {
            throw new InterruptedException();
        });

        assertFalse(zwischenablage.setzeText("Text"));

        assertEquals(1, clipboard.zugriffe);
        assertTrue(Thread.currentThread().isInterrupted());
    }

    @Test
    void dieKonstanten() {
        assertEquals(3, AwtZwischenablage.VERSUCHE);
        assertEquals(50L, AwtZwischenablage.ABSTAND_MS);
    }

    // ---------------------------------------------------------------- Besitz und Vertrauen (S-1)

    /**
     * Verhaelt sich wie {@code SunClipboard.setContents}: Der neue Inhalt wird Java-intern eingetragen, bevor die
     * (hier gesperrte) native Zwischenablage geoeffnet wird; ist sie gesperrt, bleibt der Inhalt stehen.
     */
    private static final class SunNah extends Clipboard {
        volatile boolean nativGesperrt;
        /** Meldet den Besitzverlust des gerade abgelegten Besitzers synchron waehrend des Ablegens. */
        volatile boolean sofortVerlust;

        SunNah() {
            super("test");
        }

        @Override
        public synchronized void setContents(Transferable contents, ClipboardOwner owner) {
            super.setContents(contents, owner);
            if (sofortVerlust && owner != null) {
                owner.lostOwnership(this, contents);
            }
            if (nativGesperrt) {
                throw new IllegalStateException();
            }
        }
    }

    /** Die AWT meldet den Besitzverlust ueber die Ereigniswarteschlange: warten, bis sie abgearbeitet ist. */
    private static void besitzverlustAbwarten() throws Exception {
        EventQueue.invokeAndWait(() -> { });
    }

    private static Object stringInhalt(Clipboard clipboard) throws Exception {
        return clipboard.getData(DataFlavor.stringFlavor);
    }

    @Test
    void nachGescheitertemAblegenLiefertLeseTextDenZuletztErfolgreichAbgelegtenText() throws Exception {
        SunNah clipboard = new SunNah();
        AwtZwischenablage zwischenablage = adapter(clipboard);
        assertTrue(zwischenablage.setzeText("Klartext-Ergebnis"));
        clipboard.nativGesperrt = true;

        assertFalse(zwischenablage.setzeText("Text fuer die KI"));

        assertEquals("Text fuer die KI", stringInhalt(clipboard), "Vorbedingung: der Java-interne Stand ist falsch");
        assertEquals(Optional.of("Klartext-Ergebnis"), zwischenablage.leseText());
    }

    @Test
    void auchBeiMehrerenErfolgreichenAblegenVorherZaehltDerLetzteErfolgreiche() throws Exception {
        SunNah clipboard = new SunNah();
        AwtZwischenablage zwischenablage = adapter(clipboard);
        assertTrue(zwischenablage.setzeText("erster"));
        assertTrue(zwischenablage.setzeText("zweiter"));
        clipboard.nativGesperrt = true;

        assertFalse(zwischenablage.setzeText("dritter"));
        besitzverlustAbwarten();

        assertEquals(Optional.of("zweiter"), zwischenablage.leseText(),
                "die verspaetete Meldung des ersten Besitzers darf den Besitz nicht beenden");
    }

    @Test
    void nachBesitzverlustWirdWiederDerFremdeInhaltGelesenNieDerNichtAbgelegteText() throws Exception {
        SunNah clipboard = new SunNah();
        AwtZwischenablage zwischenablage = adapter(clipboard);
        assertTrue(zwischenablage.setzeText("Klartext-Ergebnis"));
        clipboard.nativGesperrt = true;
        assertFalse(zwischenablage.setzeText("Text fuer die KI"));

        clipboard.nativGesperrt = false;
        clipboard.setContents(new Fremd(DataFlavor.stringFlavor, "fremder Text"), null);
        besitzverlustAbwarten();

        // H-5: Der interne Stand ist verworfen, die Zwischenablage wieder vertrauenswuerdig lesbar.
        assertEquals(Optional.of("fremder Text"), zwischenablage.leseText());
    }

    @Test
    void nachBesitzverlustMitFremdemBildIstDerNichtAbgelegteTextNichtZuLesen() throws Exception {
        SunNah clipboard = new SunNah();
        AwtZwischenablage zwischenablage = adapter(clipboard);
        assertTrue(zwischenablage.setzeText("Klartext-Ergebnis"));
        clipboard.nativGesperrt = true;
        assertFalse(zwischenablage.setzeText("Text fuer die KI"));

        clipboard.nativGesperrt = false;
        clipboard.setContents(new Fremd(DataFlavor.imageFlavor, new Object()), null);
        besitzverlustAbwarten();

        assertEquals(Optional.empty(), zwischenablage.leseText());
    }

    @Test
    void trifftDerBesitzverlustVorDerAuswertungDesAblegensEinWirdErNichtUeberschrieben() {
        SunNah clipboard = new SunNah();
        AwtZwischenablage zwischenablage = adapter(clipboard);
        clipboard.sofortVerlust = true;
        assertTrue(zwischenablage.setzeText("Klartext-Ergebnis"), "H-2: Verlust schon waehrend des Ablegens");
        clipboard.sofortVerlust = false;
        clipboard.nativGesperrt = true;

        assertFalse(zwischenablage.setzeText("Text fuer die KI"));

        // Der Besitz am Klartext war schon verloren: er darf nicht nachtraeglich als besessen gelten.
        assertEquals(Optional.empty(), zwischenablage.leseText());
    }

    @Test
    void scheitertDasLeerenMeldetLeerenFalseUndDerTextBleibtGemerkt() {
        SunNah clipboard = new SunNah();
        AwtZwischenablage zwischenablage = adapter(clipboard);
        assertTrue(zwischenablage.setzeText("Klartext-Ergebnis"));
        clipboard.nativGesperrt = true;

        assertFalse(zwischenablage.leeren());
        assertEquals(Optional.of("Klartext-Ergebnis"), zwischenablage.leseText());

        clipboard.nativGesperrt = false;
        assertTrue(zwischenablage.leeren());
        assertEquals(Optional.empty(), zwischenablage.leseText());
    }

    @Test
    void derBesitzverlustVonEinemAnderenThreadWirdGesehen() throws Exception {
        SunNah clipboard = new SunNah();
        AwtZwischenablage zwischenablage = adapter(clipboard);
        assertTrue(zwischenablage.setzeText("Klartext-Ergebnis"));
        clipboard.nativGesperrt = true;
        assertFalse(zwischenablage.setzeText("Text fuer die KI"));
        clipboard.nativGesperrt = false;

        Thread fremd = new Thread(
                () -> clipboard.setContents(new Fremd(DataFlavor.stringFlavor, "fremder Text"), null));
        fremd.start();
        fremd.join();
        besitzverlustAbwarten();

        assertEquals(Optional.of("fremder Text"), zwischenablage.leseText());
    }

    @Test
    void einErfolgreichesAblegenStelltDasVertrauenWiederHer() {
        SunNah clipboard = new SunNah();
        AwtZwischenablage zwischenablage = adapter(clipboard);
        assertTrue(zwischenablage.setzeText("alt"));
        clipboard.nativGesperrt = true;
        assertFalse(zwischenablage.setzeText("gescheitert"));
        clipboard.nativGesperrt = false;

        assertTrue(zwischenablage.setzeText("neu"));
        assertEquals(Optional.of("neu"), zwischenablage.leseText());
        clipboard.setContents(new Fremd(DataFlavor.stringFlavor, "fremd"), null);
        assertEquals(Optional.of("fremd"), zwischenablage.leseText(), "ohne Fehlschlag wird wirklich gelesen");
    }

    @Test
    void auchEinGescheitertesLeerenMachtDenInternenStandUnglaubwuerdig() {
        SunNah clipboard = new SunNah();
        AwtZwischenablage zwischenablage = adapter(clipboard);
        assertTrue(zwischenablage.setzeText("Klartext-Ergebnis"));
        clipboard.nativGesperrt = true;

        assertFalse(zwischenablage.leeren());

        assertEquals(0, clipboard.getAvailableDataFlavors().length, "Vorbedingung: intern ist sie leer");
        assertEquals(Optional.of("Klartext-Ergebnis"), zwischenablage.leseText());
    }

    @Test
    void nachErfolgreichemLeerenIstDerGemerkteTextVerworfen() {
        SunNah clipboard = new SunNah();
        AwtZwischenablage zwischenablage = adapter(clipboard);
        assertTrue(zwischenablage.setzeText("Klartext-Ergebnis"));
        zwischenablage.leeren();
        clipboard.nativGesperrt = true;

        assertFalse(zwischenablage.setzeText("Text fuer die KI"));

        assertEquals(Optional.empty(), zwischenablage.leseText());
    }

    @Test
    void ohneVorherigesErfolgreichesAblegenIstNachEinemFehlschlagNichtsBekannt() {
        SunNah clipboard = new SunNah();
        clipboard.nativGesperrt = true;
        AwtZwischenablage zwischenablage = adapter(clipboard);

        assertFalse(zwischenablage.setzeText("gescheitert"));

        assertEquals(Optional.empty(), zwischenablage.leseText());
    }

    @Test
    void derOeffentlicheKonstruktorBrauchtKeineSperreUndSchlaeftNicht() {
        Clipboard clipboard = new Clipboard("test");
        AwtZwischenablage zwischenablage = new AwtZwischenablage(clipboard);

        assertTrue(zwischenablage.setzeText("Text"));
        assertEquals(Optional.of("Text"), zwischenablage.leseText());
    }
}
