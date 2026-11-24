package de.skillshield.ui.swing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import de.skillshield.ui.logik.Texte;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JFrame;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Fenster ohne Sichtbarmachen (AK-1, AK-43): braucht einen Bildschirm, wird ohne Bildschirm als uebersprungen
 * gemeldet (AK-55). Das Fenster wird in keinem Test sichtbar gemacht.
 */
class HauptFensterTest {

    @BeforeEach
    void nurMitBildschirm() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "braucht einen Bildschirm (-Dui.headless=false)");
    }

    @Test
    void titelGroesseUndSchliessverhalten() {
        SwingAttrappen.Aufbau aufbau = new SwingAttrappen.Aufbau();
        SwingAttrappen.edt(() -> {
            HauptFenster fenster = new HauptFenster(aufbau.panel, () -> { });

            assertEquals(Texte.FENSTERTITEL, fenster.getTitle());
            assertEquals("SkillShield", fenster.getTitle());
            assertEquals(JFrame.DO_NOTHING_ON_CLOSE, fenster.getDefaultCloseOperation());
            assertEquals(HauptPanel.MINDESTGROESSE, fenster.getMinimumSize());
            assertEquals(HauptFenster.STARTGROESSE, fenster.getSize());
            assertSame(aufbau.panel, fenster.getContentPane());
            assertFalse(fenster.isVisible());
            fenster.dispose();
        });
    }

    @Test
    void schliessenKreuzUndAltF4RufenDenBeendenAblaufUndSchliessenNichtSelbst() {
        SwingAttrappen.Aufbau aufbau = new SwingAttrappen.Aufbau();
        AtomicInteger beenden = new AtomicInteger();
        SwingAttrappen.edt(() -> {
            HauptFenster fenster = new HauptFenster(aufbau.panel, beenden::incrementAndGet);

            fenster.dispatchEvent(new WindowEvent(fenster, WindowEvent.WINDOW_CLOSING));

            assertEquals(1, beenden.get());
            assertEquals(JFrame.DO_NOTHING_ON_CLOSE, fenster.getDefaultCloseOperation());
            assertEquals("HauptFenster", fenster.toString());
            fenster.dispose();
        });
    }
}
