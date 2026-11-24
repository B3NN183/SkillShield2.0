package de.skillshield.ui.logik;

import java.util.Objects;
import java.util.Optional;

/**
 * Zwischenablage-Regel (D-6, AK-34): merkt den zuletzt vom Programm abgelegten Text und leert die Zwischenablage nur,
 * wenn der Inhalt nicht nachweislich fremd ist. Die Klasse meldet nichts an den Anwender; Meldungen und Dialoge
 * bleiben bei {@link Sitzung}. Der gemerkte Text liegt nur im Arbeitsspeicher, wird beim Leeren oder bei fremdem
 * Inhalt verworfen und nie ausgegeben. Nicht threadsicher (Ereignis-Thread).
 *
 * <p>Zwei Fassungen der Regel, beide ohne Zugriff, wenn nie etwas abgelegt wurde:
 * <ul>
 *   <li>{@link #eigenenInhaltLeeren()} (Zurücksetzen): nur nachweislich eigener Inhalt wird geleert.</li>
 *   <li>{@link #beimBeendenLeeren()} (Beenden, V-2): Schutz geht vor; geleert wird alles, was nicht nachweislich
 *       fremd ist, auch wenn der Inhalt nicht lesbar ist.</li>
 * </ul>
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
     * Fassung für das Zurücksetzen: Enthält die Zwischenablage noch genau den zuletzt abgelegten Text, wird sie
     * geleert, sonst bleibt sie unberührt. Ist sie nicht lesbar, gesperrt oder enthält keinen (oder einen
     * unbekannten) Text, geschieht nichts; der gemerkte Text bleibt dann für einen späteren Versuch erhalten (G-8).
     * Ebenso bleibt er erhalten, wenn das Leeren scheitert (S-1, Durchlauf 3). Verworfen wird er nur nach
     * erfolgreichem Leeren oder bei nachweislich fremdem Inhalt.
     *
     * @return {@code true}, wenn nichts mehr zu leeren bleibt (nie etwas abgelegt, geleert oder fremder Inhalt);
     *         {@code false}, wenn der gemerkte Text erhalten blieb, die Zwischenablage also noch Text aus dem
     *         Programm enthalten kann
     */
    boolean eigenenInhaltLeeren() {
        if (letzterText == null) {
            return true;
        }
        try {
            Optional<String> aktuell = zwischenablage.leseText();
            if (aktuell.isEmpty()) {
                return false;
            }
            if (!aktuell.get().equals(letzterText) || zwischenablage.leeren()) {
                letzterText = null;
                return true;
            }
        } catch (RuntimeException e) {
            // Die Zwischenablage darf nie werfen; falls doch, gibt das Programm für jetzt auf und behält den Text.
        }
        return false;
    }

    /**
     * Fassung für das Beenden (V-2, Entscheidung der Hauptsitzung: Schutz geht vor, Datenverlust beim Anwender wird
     * bewusst in Kauf genommen): Hat das Programm etwas abgelegt, wird geleert, es sei denn, der Inhalt ist
     * nachweislich fremd (lesbar und ungleich dem gemerkten Text). Ein unbekannter oder nicht lesbarer Inhalt gilt
     * nicht als fremd. Ohne gemerkten Text geschieht nichts und die Zwischenablage wird nicht berührt. Verworfen
     * wird der gemerkte Text nur nach erfolgreichem Leeren oder bei nachweislich fremdem Inhalt.
     *
     * @return {@code true}, wenn nichts mehr zu leeren bleibt; {@code false}, wenn das Leeren gescheitert ist
     */
    boolean beimBeendenLeeren() {
        if (letzterText == null) {
            return true;
        }
        Optional<String> aktuell;
        try {
            aktuell = zwischenablage.leseText();
        } catch (RuntimeException e) {
            aktuell = Optional.empty();
        }
        try {
            if (aktuell.isPresent() && !aktuell.get().equals(letzterText)) {
                letzterText = null;
                return true;
            }
            if (zwischenablage.leeren()) {
                letzterText = null;
                return true;
            }
        } catch (RuntimeException e) {
            // Wie oben: gescheitert, der Text bleibt gemerkt.
        }
        return false;
    }

    /** Verwirft den gemerkten Text, ohne die Zwischenablage zu berühren (Programmende nach "Trotzdem beenden"). */
    void vergleichstextVerwerfen() {
        letzterText = null;
    }
}
