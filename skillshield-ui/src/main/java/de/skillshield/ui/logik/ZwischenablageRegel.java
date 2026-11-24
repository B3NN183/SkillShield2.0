package de.skillshield.ui.logik;

import java.util.Objects;
import java.util.Optional;

/**
 * Zwischenablage-Regel (D-6, AK-34): merkt den zuletzt vom Programm abgelegten Text und leert die Zwischenablage nur,
 * wenn sie noch genau diesen Text enthält. Die Klasse meldet nichts an den Anwender; Meldungen und Dialoge bleiben bei
 * {@link Sitzung}. Der gemerkte Text liegt nur im Arbeitsspeicher, wird beim Leeren oder bei fremdem Inhalt verworfen
 * und nie ausgegeben. Nicht threadsicher (Ereignis-Thread).
 */
final class ZwischenablageRegel {

    private final Zwischenablage zwischenablage;
    /** Der zuletzt vom Programm abgelegte Text; {@code null}, wenn keiner gemerkt ist. */
    private String letzterText;

    ZwischenablageRegel(Zwischenablage zwischenablage) {
        this.zwischenablage = Objects.requireNonNull(zwischenablage, "zwischenablage");
    }

    /**
     * Legt Text ab und merkt ihn bei Erfolg. Eine Ausnahme der Zwischenablage (Vertragsbruch) gilt als nicht abgelegt
     * und wird nicht weitergegeben.
     *
     * @return {@code true}, wenn die Zwischenablage den Text angenommen hat
     */
    boolean ablegen(String inhalt) {
        boolean abgelegt;
        try {
            abgelegt = zwischenablage.setzeText(inhalt);
        } catch (RuntimeException e) {
            abgelegt = false;
        }
        if (abgelegt) {
            letzterText = inhalt;
        }
        return abgelegt;
    }

    /**
     * Enthält die Zwischenablage noch genau den zuletzt abgelegten Text, wird sie geleert, sonst bleibt sie
     * unberührt. Ist sie nicht lesbar, gesperrt oder enthält keinen (oder einen unbekannten) Text, geschieht nichts;
     * der gemerkte Text bleibt dann für einen späteren Versuch erhalten (G-8). Ebenso bleibt er erhalten, wenn das
     * Leeren scheitert (S-1, Durchlauf 3): sonst bliebe der Klartext liegen, obwohl die Zwischenablage später wieder
     * erreichbar ist. Verworfen wird er nur nach erfolgreichem Leeren oder bei nachweislich fremdem Inhalt.
     */
    void eigenenInhaltLeeren() {
        if (letzterText == null) {
            return;
        }
        try {
            Optional<String> aktuell = zwischenablage.leseText();
            if (aktuell.isEmpty()) {
                return;
            }
            if (!aktuell.get().equals(letzterText) || zwischenablage.leeren()) {
                letzterText = null;
            }
        } catch (RuntimeException e) {
            // Die Zwischenablage darf nie werfen; falls doch, gibt das Programm für jetzt auf und behält den Text.
        }
    }
}
