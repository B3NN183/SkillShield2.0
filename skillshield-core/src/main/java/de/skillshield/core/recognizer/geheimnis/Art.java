package de.skillshield.core.recognizer.geheimnis;

import de.skillshield.core.model.FindingType;

/**
 * Art der Kontextregel (Plan geheimnisse-erkennung, Kapitel 2.2): bestimmt Typ und Quelle der Funde. Bis T-4 gibt es
 * nur {@link #PASSWORT}; die Art TOKEN kommt mit dem Token-Erkenner (T-6) dazu.
 */
enum Art {

    /** Passwort nach Kontextwort (D-4), Quelle {@code passwort-kontext}. */
    PASSWORT(GeheimnisTypen.PASSWORT, PasswortRecognizer.QUELLE_KONTEXT);

    private final FindingType typ;
    private final String quelle;

    Art(FindingType typ, String quelle) {
        this.typ = typ;
        this.quelle = quelle;
    }

    /** Typ der Funde dieser Art. */
    FindingType typ() {
        return typ;
    }

    /** Quelle der Funde dieser Art. */
    String quelle() {
        return quelle;
    }
}
