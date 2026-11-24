package de.skillshield.ui.logik;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.Map;

/** Deutsche Klartext-Beschriftung von Typ und Stufe (D-13); nie nur über Farbe. */
public final class Beschriftungen {

    private static final Map<String, String> TYPEN = Map.of(
            FindingType.PFAD.name(), "Pfad",
            FindingType.EMAIL.name(), "E-Mail",
            FindingType.IP.name(), "IP-Adresse",
            FindingType.URL.name(), "Internetadresse",
            FindingType.IK.name(), "IK",
            FindingType.LANR.name(), "LANR",
            FindingType.KVNR.name(), "KVNR",
            FindingType.BSNR.name(), "BSNR",
            FindingType.TEXT.name(), "Manuell");

    private Beschriftungen() {
    }

    /** Typ des Fundes; ein unbekannter Typname (Namensformat des Kerns, kein Fundtext) wird unverändert gezeigt. */
    public static String typ(Finding fund) {
        String name = fund.type().name();
        return TYPEN.getOrDefault(name, name);
    }

    /** "sicher", "wahrscheinlich" oder "fraglich". */
    public static String stufe(ConfidenceLevel stufe) {
        return switch (stufe) {
            case SICHER -> "sicher";
            case WAHRSCHEINLICH -> "wahrscheinlich";
            case FRAGLICH -> "fraglich";
        };
    }
}
