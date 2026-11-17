# Anforderung: Maven-Grundgerüst

Feature-Kurzname: `maven-grundgeruest`

## User Story
Als Entwicklungsteam (inklusive der Agenten qa und security) möchte ich ein bauffähiges Maven-Grundgerüst mit Parent-pom und fünf Modul-poms, damit `mvn verify` und der OWASP-Dependency-Check im Wurzelverzeichnis ausgeführt werden können und alle folgenden Features auf einer gemeinsamen, prüfbaren Build-Basis entstehen.

## Akzeptanzkriterien
- AK-1: Im Wurzelverzeichnis existiert eine Parent-`pom.xml`, die genau die fünf Module `skillshield-core`, `skillshield-io`, `skillshield-vault`, `skillshield-ui`, `skillshield-app` als Module einbindet. (Ja/Nein)
- AK-2: Jedes der fünf Module hat eine eigene `pom.xml`, die auf die Parent-pom verweist. (Ja/Nein)
- AK-3: Das Projekt ist auf Java 21 festgelegt; ein Build mit einem älteren JDK schlägt mit verständlicher Meldung fehl. (Ja/Nein)
- AK-4: JUnit 5 ist für alle Module als Testframework verfügbar; ein Beispieltest je Modul (ohne Fachlogik, z. B. Existenz-/Smoke-Test) wird von `mvn verify` ausgeführt und läuft grün. (Ja/Nein)
- AK-5: `mvn verify` im Wurzelverzeichnis läuft ohne Fehler durch (Exit-Code 0) und baut alle fünf Module. (Ja/Nein)
- AK-6: Die Modul-Abhängigkeiten entsprechen der Richtung aus Plan.md Kap. 5: core ohne Abhängigkeit auf andere Module; io und vault hängen nur von core ab; ui hängt von core, io und vault ab; app hängt von allen anderen ab. (Ja/Nein)
- AK-7: Eine Abhängigkeit entgegen dieser Richtung (z. B. core auf io) lässt den Build fehlschlagen oder wird anderweitig automatisch erkannt. (Ja/Nein; Umsetzungsweg Sache des architect)
- AK-8: Es gibt keine Modul-Zyklen; der Maven-Reactor ermittelt eine gültige Build-Reihenfolge. (Ja/Nein)
- AK-9: Der OWASP-Dependency-Check ist im Projekt so eingebunden, dass `mvn org.owasp:dependency-check-maven:check` im Wurzelverzeichnis ausführbar ist und einen Bericht erzeugt. (Ja/Nein)
- AK-10: Ist die Schwachstellendatenbank nicht erreichbar (kein Netz) oder fehlt ein erforderlicher API-Key, endet der Check mit einer verständlichen Fehlermeldung und nicht mit einem scheinbar grünen Ergebnis. (Ja/Nein)
- AK-11: Das Grundgerüst enthält keine Fachlogik (keine Erkenner, Pseudonymisierung, Tresor, UI) und keinen Netzwerkzugriffs-Code im Produkt. (Ja/Nein)
- AK-12: Das Grundgerüst enthält keine KI-Abhängigkeit und keine Abhängigkeiten, die zur Laufzeit Netzwerkzugriff erfordern (Plan.md Kap. 2). (Ja/Nein)
- AK-13: Die Paketstruktur `de.skillshield.<modul>` bleibt erhalten; die vorhandene Ordnerstruktur wird nicht verändert. (Ja/Nein)
- AK-14: Ein Aufruf von `mvn verify` auf einem frisch bereinigten Stand (`mvn clean verify`) liefert dasselbe grüne Ergebnis. (Ja/Nein)
- AK-15: Ist Maven oder JDK 21 nicht installiert, ist dies in der Projektdokumentation als Voraussetzung benannt (Ort und Form legt der architect fest). (Ja/Nein)
- AK-16: Nach der Umsetzung sind Aufrufe von `mvn verify` (qa) und `mvn org.owasp:dependency-check-maven:check` (security) für die Agenten dokumentiert nutzbar. (Ja/Nein)

## Abgrenzung
Nicht Teil dieses Features:
- Fachlogik jeder Art (Erkenner, Pseudonymisierung, Re-Identifizierung, Tresor, Oberfläche).
- Auswahl und Einbindung fachlicher Bibliotheken (UI-Framework, YAML, Kryptografie usw.), sofern nicht für das Grundgerüst zwingend.
- Packaging mit jpackage, Code-Signing, Installer.
- Inhalt der Freigabe-Unterlagen (Lizenzliste, Schwachstellen-Bewertung); siehe offene Fragen zu SBOM und Lizenzliste.
- Netzwerkcode im Produkt; der OWASP-Check ist reines Build-Werkzeug mit Netzbedarf zur Build-Zeit.

## Bezug zu Plan.md
- Kap. 2 (Grundprinzipien: offline, keine KI)
- Kap. 4 (Technologie-Stack: Temurin 21, Maven, JUnit 5)
- Kap. 5 (Architektur: Maven-Module und Abhängigkeitsrichtung)
- Kap. 12 (Software-Freigabe: SBOM CycloneDX, OWASP Dependency-Check, Lizenzliste, Code-Signing)

## Offene Fragen
1. Gehört die SBOM-Erzeugung (CycloneDX) schon ins Grundgerüst oder erst in ein späteres Feature (Freigabe/Release)?
2. Gehört die Lizenzprüfung bzw. Lizenzliste (Plan.md Kap. 4/12) schon ins Grundgerüst oder später?
3. Soll ein Maven Wrapper (`mvnw`) Teil des Grundgerüsts sein, oder wird eine lokal installierte Maven-Version vorausgesetzt? Welche Mindestversion?
4. Sind Maven und ein JDK 21 (Temurin) auf dem Entwicklungsrechner installiert? Dies ist nicht geprüft; falls nicht, wer installiert sie?
5. Welche Quelle/Zugriff steht für die Schwachstellendatenbank des OWASP-Checks zur Verfügung (Netzzugriff vom Entwicklungsrechner, NVD-API-Key, ggf. interner Spiegel der KV)? Wer beschafft den Key und wo darf er hinterlegt werden (er darf nicht im Repository stehen)?
6. Ab welcher Schwachstellen-Schwere (CVSS) soll der OWASP-Check den Build fehlschlagen lassen, oder soll er zunächst nur berichten?
7. Soll der OWASP-Check Teil von `mvn verify` sein oder nur separat aufgerufen werden? (Auftrag nennt zwei getrennte Aufrufe; Verbindlichkeit offen.)
8. Soll der Build die Einhaltung der Modul-Abhängigkeitsrichtung aktiv erzwingen (AK-7), oder genügt die Konvention in den poms?
9. Welche Maven-Koordinaten (groupId, Start-Version) sollen verwendet werden? Plan.md-Paketstruktur deutet auf `de.skillshield` hin; nicht bestätigt.
10. Welche Mindest-Qualitätsvorgaben gelten schon jetzt (z. B. Compiler-Warnungen als Fehler, Testabdeckung)? Im Zweifel erst später.
11. Läuft der Build in einer Umgebung mit eingeschränktem Netz (KV-Proxy, Repository-Spiegel für Maven-Abhängigkeiten)? Das beeinflusst, ob der erste Build überhaupt Abhängigkeiten laden kann.
