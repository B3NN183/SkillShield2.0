package de.skillshield.core.recognizer.geheimnis;

import de.skillshield.core.model.FindingType;

/**
 * Art der Kontextregel (Plan geheimnisse-erkennung, Kapitel 2.2): bestimmt Typ und Quelle der Funde. {@link #PASSWORT}
 * gibt es seit T-4, {@link #TOKEN} seit T-6 (Token-Erkenner).
 */
enum Art {

    /** Passwort nach Kontextwort (D-4), Quelle {@code passwort-kontext}. */
    PASSWORT(GeheimnisTypen.PASSWORT, PasswortRecognizer.QUELLE_KONTEXT),

    /** Token nach Kontextwort oder Schema-Wort (D-5, D-11), Quelle {@code token-kontext}. */
    TOKEN(GeheimnisTypen.TOKEN, TokenRecognizer.QUELLE_KONTEXT);

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
