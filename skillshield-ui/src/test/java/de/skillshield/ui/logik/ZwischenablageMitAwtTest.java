package de.skillshield.ui.logik;

import static de.skillshield.ui.logik.Attrappen.DREI_MAILS;
import static de.skillshield.ui.logik.Attrappen.scannen;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import de.skillshield.ui.logik.Attrappen.TestDialoge;
import de.skillshield.ui.swing.AwtZwischenablage;
import java.awt.EventQueue;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import org.junit.jupiter.api.Test;

/**
 * Die Zwischenablage-Regel (AK-34) zusammen mit dem echten AWT-Adapter auf einer selbst erzeugten lokalen
 * {@link Clipboard}, die wie die JDK-Zwischenablage den Inhalt Java-intern einträgt, bevor die native Ablage
 * scheitern kann (S-1). Nie die Zwischenablage des Systems.
 */
class ZwischenablageMitAwtTest {

    private static final String ANNA = "anna@example.org";

    /** Trägt den Inhalt zuerst Java-intern ein und wirft dann, wenn die "native" Zwischenablage gesperrt ist. */
    private static final class SunNah extends Clipboard {
        volatile boolean nativGesperrt;
        /** Zahl der Versuche, die Zwischenablage zu leeren (Inhalt ohne jedes Format), auch gescheiterter. */
        int leerVersuche;

        SunNah() {
            super("test");
        }

        @Override
        public synchronized void setContents(Transferable contents, ClipboardOwner owner) {
            super.setContents(contents, owner);
            if (contents.getTransferDataFlavors().length == 0) {
                leerVersuche++;
            }
            if (nativGesperrt) {
                throw new IllegalStateException();
            }
        }
    }

    /** Fremder Inhalt: ein Text, den ein anderes Programm kopiert hat. */
    private static final class FremderText implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {DataFlavor.stringFlavor};
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
            return "fremder Text";
        }
    }

    private final SunNah clipboard = new SunNah();
    private final TestDialoge dialoge = new TestDialoge();
    private final Sitzung sitzung = Sitzung.standard(new AwtZwischenablage(clipboard), dialoge);

    /** Ergebnis im Klartext kopieren, dann ein gescheitertes "Für KI kopieren". */
    private void ergebnisKopierenUndDanachKiKopierenScheitern() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        sitzung.zurueckuebersetzen("[[EMAIL_1]]");
        sitzung.ergebnisKopieren();
        assertEquals(ANNA, text(), "Vorbedingung: der Klartext liegt in der Zwischenablage");
        sitzung.anwaehlen(0, false);
        clipboard.nativGesperrt = true;
        sitzung.fuerKiKopieren();
        assertEquals(Texte.ZWISCHENABLAGE_NICHT_VERFUEGBAR, sitzung.statusleiste().meldung());
        clipboard.nativGesperrt = false;
    }

    private String text() {
        try {
            return (String) clipboard.getData(DataFlavor.stringFlavor);
        } catch (Exception e) {
            return null;
        }
    }

    @Test
    void nachGescheitertemKopierenWirdDerKlartextBeimLeerenTrotzdemEntfernt() {
        ergebnisKopierenUndDanachKiKopierenScheitern();

        sitzung.eigenenZwischenablageInhaltLeeren();

        assertEquals(0, clipboard.getAvailableDataFlavors().length, "die Zwischenablage ist leer");
    }

    @Test
    void scheitertDasLeerenBeiGesperrterAblageWirdSpaeterBeiFreierAblageTrotzdemGeleert() {
        scannen(sitzung, DREI_MAILS);
        sitzung.fuerKiKopieren();
        sitzung.zurueckuebersetzen("[[EMAIL_1]]");
        sitzung.ergebnisKopieren();
        clipboard.nativGesperrt = true;

        sitzung.eigenenZwischenablageInhaltLeeren();

        // Der Adapter versucht es bei Sperre dreimal (G-8).
        assertEquals(3, clipboard.leerVersuche, "Vorbedingung: das Leeren wurde versucht und ist gescheitert");
        clipboard.nativGesperrt = false;
        sitzung.eigenenZwischenablageInhaltLeeren();
        assertEquals(4, clipboard.leerVersuche, "der gemerkte Text blieb erhalten: weiterer Versuch");
        assertEquals(0, clipboard.getAvailableDataFlavors().length);
        sitzung.eigenenZwischenablageInhaltLeeren();
        assertEquals(4, clipboard.leerVersuche, "nach erfolgreichem Leeren ist der Text verworfen");
    }

    @Test
    void dieselbeFolgeNachGescheitertemFuerKiKopierenLeertSpaeterBeiFreierAblage() {
        ergebnisKopierenUndDanachKiKopierenScheitern();
        clipboard.nativGesperrt = true;

        sitzung.eigenenZwischenablageInhaltLeeren();

        assertEquals(3, clipboard.leerVersuche);
        clipboard.nativGesperrt = false;
        sitzung.eigenenZwischenablageInhaltLeeren();
        assertEquals(4, clipboard.leerVersuche);
        assertEquals(0, clipboard.getAvailableDataFlavors().length);
    }

    @Test
    void hatEinAnderesProgrammInzwischenKopiertBleibtSeinInhaltUnberuehrt() throws Exception {
        ergebnisKopierenUndDanachKiKopierenScheitern();
        clipboard.setContents(new FremderText(), null);
        // Die AWT meldet den Besitzverlust ueber die Ereigniswarteschlange.
        EventQueue.invokeAndWait(() -> { });

        sitzung.eigenenZwischenablageInhaltLeeren();

        assertEquals("fremder Text", text());
        assertFalse(clipboard.getAvailableDataFlavors().length == 0);
    }
}
