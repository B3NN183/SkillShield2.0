/**
 * Erkennung von Passwoertern und Geheimnissen (Feature geheimnisse-erkennung).
 *
 * <p>Stand nach Task T-3: Das Paket enthaelt nur die Typkonstanten {@link
 * de.skillshield.core.recognizer.geheimnis.GeheimnisTypen#PASSWORT} und {@link
 * de.skillshield.core.recognizer.geheimnis.GeheimnisTypen#TOKEN}. Die Erkenner folgen in spaeteren Tasks; die
 * Uebersicht der bekannten Grenzen wird dort ergaenzt.
 *
 * <p>Einordnung in den Scan (Lueckenregel): Ein Kandidat dieser Typen veraendert nie einen Fund der uebrigen
 * Erkenner. Er schuetzt nur die Positionen, die ausserhalb aller uebrigen Funde liegen; die Einzelheiten und der
 * Beweis stehen im Javadoc von {@code de.skillshield.core.recognizer.TextScanner}.
 */
package de.skillshield.core.recognizer.geheimnis;
