package de.skillshield.core.pseudonymize;

import java.util.Objects;

/**
 * Eindeutiger Fehler für eine ungültige Fundliste der Pseudonymisierung (Architektur-Plan Kap. 2.2, 2.6).
 *
 * <p>Die Meldung hat das Format {@code Ungültiger Fund an Listenindex i: GRUND}. Der Index bezieht sich auf die
 * übergebene Liste. Die Exception hält ausschließlich Grund und Index, weder den Fund noch den Text noch einen
 * Originalwert; es gibt keine Ursache ({@code getCause()} ist {@code null}).
 */
public final class InvalidFindingException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /** Grund der Ablehnung. */
    public enum Reason {
        /** Listenelement ist {@code null}. */
        NULL_ELEMENT,
        /** {@code end} liegt hinter dem Textende. */
        OUT_OF_RANGE,
        /** Der Fund überlappt einen in der Textreihenfolge davor liegenden Fund. */
        OVERLAP,
        /** Der Originalwert steht nicht an der Fundposition im Text. */
        VALUE_MISMATCH,
        /** Start oder Ende des Fundes trennt ein Surrogatpaar. */
        SPLITS_SURROGATE_PAIR
    }

    private final Reason reason;
    private final int index;

    /**
     * @param reason Grund, nicht {@code null}
     * @param index  Listenindex des betroffenen Elements in der übergebenen Liste
     */
    public InvalidFindingException(Reason reason, int index) {
        super("Ungültiger Fund an Listenindex " + index + ": " + Objects.requireNonNull(reason, "reason"));
        this.reason = reason;
        this.index = index;
    }

    public Reason reason() {
        return reason;
    }

    public int index() {
        return index;
    }
}
