package de.skillshield.ui.logik;

/** Gültigkeit der Funde einer {@link Sitzung} (Architektur-Plan Kap. 3.1). */
public enum ScanZustand {
    /** Start oder nach Zurücksetzen: es gibt keinen Scan. */
    KEIN_SCAN,
    /** Ein Scan läuft im Hintergrund. */
    LAEUFT,
    /** Die Funde passen zum Text. */
    GUELTIG,
    /** Der Text wurde nach dem Scan geändert; die Fundpositionen gelten nicht mehr. */
    VERALTET
}
