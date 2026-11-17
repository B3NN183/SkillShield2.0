package de.skillshield.core.model;

/**
 * Stufe einer Konfidenz. Zuordnung: {@code confidence >= 0.90} sicher, {@code 0.70 <= confidence < 0.90}
 * wahrscheinlich, darunter fraglich. Funde jeder Stufe werden geliefert, keine wird verworfen.
 */
public enum ConfidenceLevel {
    SICHER,
    WAHRSCHEINLICH,
    FRAGLICH;

    /** Untergrenze (inklusive) für {@link #SICHER}. */
    public static final double SCHWELLE_SICHER = 0.90;
    /** Untergrenze (inklusive) für {@link #WAHRSCHEINLICH}. */
    public static final double SCHWELLE_WAHRSCHEINLICH = 0.70;

    /**
     * Ordnet eine Konfidenz einer Stufe zu.
     *
     * @param confidence Wert im Bereich 0.0 bis 1.0
     * @throws IllegalArgumentException bei NaN oder Wert außerhalb von [0, 1]
     */
    public static ConfidenceLevel of(double confidence) {
        if (!(confidence >= 0.0 && confidence <= 1.0)) {
            throw new IllegalArgumentException("Konfidenz muss im Bereich 0.0 bis 1.0 liegen");
        }
        if (confidence >= SCHWELLE_SICHER) {
            return SICHER;
        }
        if (confidence >= SCHWELLE_WAHRSCHEINLICH) {
            return WAHRSCHEINLICH;
        }
        return FRAGLICH;
    }
}
