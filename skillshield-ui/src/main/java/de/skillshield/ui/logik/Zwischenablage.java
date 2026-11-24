package de.skillshield.ui.logik;

import java.util.Optional;

/**
 * Zugriff auf die Zwischenablage; im Test durch eine Attrappe ersetzbar. Keine Methode wirft eine Ausnahme.
 * Die Umsetzung legt nur ein einziges Textformat ab (AK-33).
 */
public interface Zwischenablage {

    /**
     * Legt genau einen reinen Text ab.
     *
     * @return {@code true}, wenn der Text abgelegt wurde; {@code false}, wenn die Zwischenablage nicht verfügbar
     *         oder gesperrt war. Der Aufrufer meldet das dem Anwender, damit er nicht einen alten Inhalt der
     *         Zwischenablage für den kopierten Text hält (Abweichung vom Plan: Rückgabewert statt {@code void}).
     */
    boolean setzeText(String text);

    /**
     * Liefert den Text der Zwischenablage; leer, wenn dort kein Text liegt, sie nicht lesbar ist oder der Inhalt
     * nicht sicher bekannt ist (etwa nach einem gescheiterten Ablegen). "Kein Text" und "unbekannt" sind bewusst
     * nicht getrennt: Für die Leeren-Regel ist beides "nichts tun und den Vergleichstext behalten".
     */
    Optional<String> leseText();

    /**
     * Leert die Zwischenablage; ist sie nicht erreichbar, geschieht nichts (benannte Grenze G-8).
     *
     * @return {@code true}, wenn die Zwischenablage geleert wurde; {@code false}, wenn das Leeren gescheitert ist
     *         (der Aufrufer behält dann seinen Vergleichstext für einen späteren Versuch)
     */
    boolean leeren();
}
