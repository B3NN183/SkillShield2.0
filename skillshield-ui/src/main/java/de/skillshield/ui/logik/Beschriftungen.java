package de.skillshield.ui.logik;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.geheimnis.GeheimnisTypen;
import java.util.Map;

/** Deutsche Klartext-Beschriftung von Typ und Stufe (D-13); nie nur über Farbe. */
public final class Beschriftungen {

    private static final Map<String, String> TYPEN = Map.ofEntries(
            Map.entry(FindingType.PFAD.name(), "Pfad"),
            Map.entry(FindingType.EMAIL.name(), "E-Mail"),
            Map.entry(FindingType.IP.name(), "IP-Adresse"),
            Map.entry(FindingType.URL.name(), "Internetadresse"),
            Map.entry(FindingType.IK.name(), "IK"),
            Map.entry(FindingType.LANR.name(), "LANR"),
            Map.entry(FindingType.KVNR.name(), "KVNR"),
            Map.entry(FindingType.BSNR.name(), "BSNR"),
            Map.entry(FindingType.TEXT.name(), "Manuell"),
            Map.entry(GeheimnisTypen.PASSWORT.name(), "Passwort"),
            Map.entry(GeheimnisTypen.TOKEN.name(), "Token oder Schl" + (char) 0xFC + "ssel"));

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
