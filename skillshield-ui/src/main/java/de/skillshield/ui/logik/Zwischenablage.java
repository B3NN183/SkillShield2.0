package de.skillshield.ui.logik;

import java.util.Optional;

/** Zugriff auf die Zwischenablage; im Test durch eine Attrappe ersetzbar. */
public interface Zwischenablage {

    /** Legt genau einen reinen Text ab. */
    void setzeText(String text);

    /** Liefert den Text der Zwischenablage; leer, wenn dort kein Text liegt oder sie nicht lesbar ist. Wirft nie. */
    Optional<String> leseText();

    /** Leert die Zwischenablage. Wirft nie. */
    void leeren();
}
