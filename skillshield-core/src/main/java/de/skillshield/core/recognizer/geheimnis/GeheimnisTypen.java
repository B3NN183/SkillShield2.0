package de.skillshield.core.recognizer.geheimnis;

import de.skillshield.core.model.FindingType;
import java.util.Objects;

/**
 * Typen der Geheimnis-Funde (Feature geheimnisse-erkennung, D-1): Passwort und Token oder Schluessel. Die Typen
 * stehen bewusst nicht in {@link FindingType}; diese Klasse ist die einzige Stelle, an der der
 * {@code TextScanner} und die Oberflaeche sie kennen.
 *
 * <p>Der {@code TextScanner} ordnet Kandidaten dieser Typen nach der Lueckenregel ein (siehe dessen Javadoc): Sie
 * fuellen nur die Teile ausserhalb der Funde aller anderen Erkenner und veraendern keinen bestehenden Fund. Das gilt
 * unabhaengig davon, welcher Erkenner sie liefert; allein der Typ entscheidet.
 */
public final class GeheimnisTypen {

    /** Passwort (D-1). */
    public static final FindingType PASSWORT = new FindingType("PASSWORT");

    /** Token oder Schluessel (D-1). */
    public static final FindingType TOKEN = new FindingType("TOKEN");

    private GeheimnisTypen() {
    }

    /**
     * Ob der Typ ein Geheimnistyp ist.
     *
     * @param type der Typ, nicht {@code null}
     * @return {@code true} fuer {@link #PASSWORT} und {@link #TOKEN}, sonst {@code false}
     * @throws NullPointerException bei {@code null} (Meldung {@code type})
     */
    public static boolean istGeheimnistyp(FindingType type) {
        Objects.requireNonNull(type, "type");
        return type.equals(PASSWORT) || type.equals(TOKEN);
    }
}
