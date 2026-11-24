package de.skillshield.ui.logik;

/**
 * Inhalt der Statusleiste.
 *
 * @param meldung   Meldung links (feste Texte und Zahlen, nie Eingabe- oder Fundtext)
 * @param zuordnung Zuordnungsanzeige rechts (AK-40)
 */
public record Statusleiste(String meldung, String zuordnung) {
}
