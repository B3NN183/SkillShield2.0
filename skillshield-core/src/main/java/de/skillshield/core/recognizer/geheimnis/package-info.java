/**
 * Erkennung von Passwoertern und Geheimnissen (Feature geheimnisse-erkennung).
 *
 * <p>Stand nach Task T-4: Das Paket enthaelt die Typkonstanten {@link
 * de.skillshield.core.recognizer.geheimnis.GeheimnisTypen#PASSWORT} und {@link
 * de.skillshield.core.recognizer.geheimnis.GeheimnisTypen#TOKEN} und den {@link
 * de.skillshield.core.recognizer.geheimnis.PasswortRecognizer} (Passwort nach Kontextwort mit {@code :} oder
 * {@code =}); dessen Javadoc nennt die Regeln und die bekannten Grenzen. Seit T-6 und T-7 kommt der
 * {@link de.skillshield.core.recognizer.geheimnis.TokenRecognizer} dazu (Token nach Kontextwort, Schema-Formen und die
 * kontextfreien Formen JWT, PEM-Koerper und Praefix-Token); sein Javadoc nennt Regeln und Grenzen. Die weiteren
 * Erkenner folgen in spaeteren Tasks; die Uebersicht der bekannten Grenzen wird dort ergaenzt.
 *
 * <p>Einordnung in den Scan (Lueckenregel): Ein Kandidat dieser Typen veraendert nie einen Fund der uebrigen
 * Erkenner. Er schuetzt nur die Positionen, die ausserhalb aller uebrigen Funde liegen; die Einzelheiten und der
 * Beweis stehen im Javadoc von {@code de.skillshield.core.recognizer.TextScanner}.
 */
package de.skillshield.core.recognizer.geheimnis;
