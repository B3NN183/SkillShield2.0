# SkillShield 2.0 – Build und Voraussetzungen

Offline-Werkzeug zur umkehrbaren Pseudonymisierung (Fachliches und Architektur: [Plan.md](Plan.md),
Agentensteuerung: [CLAUDE.md](CLAUDE.md)). Diese Datei beschreibt, was zum Bauen und Prüfen nötig ist.

## Voraussetzungen

| Werkzeug | Version | Hinweis |
|----------|---------|---------|
| JDK | **21** (Eclipse Temurin), genau Major 21 | Der Build prüft `[21,22)` per Maven Enforcer. |
| Maven | **>= 3.9.0** (getestet mit 3.9.16) | Es gibt bewusst **keinen Maven Wrapper**; Maven wird lokal installiert. |

Prüfen: `java -version` zeigt 21, `mvn -version` zeigt Maven 3.9.x und Java 21.

### Installation unter Windows

- JDK: `winget install --id EclipseAdoptium.Temurin.21.JDK`
- Maven ist nicht bei winget verfügbar: Binary-ZIP von <https://maven.apache.org/download.cgi> laden, die SHA-512-Prüfsumme
  mit der dort veröffentlichten vergleichen, entpacken und das Verzeichnis `bin` in den `PATH` aufnehmen.
- `JAVA_HOME` (Benutzervariable) auf das JDK-21-Verzeichnis setzen und `%JAVA_HOME%\bin` vorne in den `PATH`.
  Danach ein neues Terminal öffnen.

### Falsches JDK

Bricht der Build in `validate` mit „SkillShield benötigt ein JDK 21 (Temurin) … JAVA_HOME auf ein JDK 21 setzen“ ab,
läuft Maven mit einem anderen JDK. Abhilfe: `JAVA_HOME` auf ein JDK 21 setzen (`mvn -version` zeigt, welches Java Maven
nutzt). Entsprechend meldet der Build bei Maven < 3.9.0 „SkillShield benötigt Maven 3.9 oder neuer“.

## Build und Tests

```
mvn verify
mvn clean verify
```

Baut alle Module und führt die JUnit-5-Tests aus (jedes Modul bringt eigene Tests mit).
Der OWASP-Dependency-Check läuft dabei **bewusst nicht** mit (kein Netzzugriff nötig).

## Module und Abhängigkeitsrichtung

| Modul | darf abhängen von | verboten (Enforcer-Regel `modulrichtung`) |
|-------|-------------------|--------------------------------------------|
| `skillshield-core` | nichts | alle anderen SkillShield-Module |
| `skillshield-io` | core | vault, ui, app |
| `skillshield-vault` | core | io, ui, app |
| `skillshield-ui` | core, io, vault | app |
| `skillshield-app` | core, io, vault, ui | – |

Ein Verstoß ohne Zyklus (z. B. io -> vault) scheitert in `validate` am Enforcer mit „Modulrichtung verletzt …“.
Ein Verstoß mit Zyklus (z. B. core -> io) scheitert bereits am Reactor („cyclic reference“). Zusätzlich prüfen die
Klassenpfad-Tests je Modul die Richtung.

## OWASP Dependency-Check (Version 13.0.0)

Aufruf (Wurzelverzeichnis):

```
mvn clean compile org.owasp:dependency-check-maven:check
```

Die Phase `compile` ist nötig: Ohne sie scheitert der Lauf im Multi-Modul-Projekt mit
`Could not find artifact de.skillshield:skillshield-core:jar`.

Der Check ist nicht Teil von `mvn verify`. Er bricht ab bei Befunden ab CVSS 7 (`failBuildOnCVSS`) und bei Fehlern beim
Aktualisieren der Datenbank (`failOnError`).

### Was der Standardlauf prüft (und was nicht)

Standardmäßig werden nur Compile-/Runtime-Abhängigkeiten geprüft. Solange das Produkt keine Fremdbibliotheken
enthält, prüft der Lauf **0 Abhängigkeiten** und ist trotzdem grün; das ist kein Nachweis für die Test-Bibliotheken.
Diese (JUnit) werden mit zusätzlichem Parameter geprüft:

```
mvn clean compile org.owasp:dependency-check-maven:check -Dodc.skipTestScope=false
```

Stand 17.11.2026: 8 Artefakte, 0 Befunde.

### NVD-API-Key

1. Kostenlos beantragen: <https://nvd.nist.gov/developers/request-an-api-key>. Ein neuer Key wird von der NVD erst nach
   einigen Minuten akzeptiert (bis dahin „Invalid API Key“).
2. Als Benutzer-Umgebungsvariable `NVD_API_KEY` setzen (Windows: `setx NVD_API_KEY "<key>"`), danach ein neues Terminal
   öffnen.
3. Der Key steht **nie** im Repository, nie in einer `.env`-Datei und nie als `-D`-Argument. Maven mit Key nicht mit `-X`
   starten bzw. Debug-Logs vor dem Teilen prüfen.

### Erstbefüllung der Datenbank

Einmalig, dauert mehrere Minuten und lädt mehrere hundert MB nach `~/.m2/repository/org/owasp/dependency-check-data`:

```
mvn org.owasp:dependency-check-maven:update-only -N
```

### Berichte

`target/dependency-check-report.html` und `target/dependency-check-report.json` im Wurzelverzeichnis und in jedem Modul.
Von Git ignoriert.

### Verhalten, das man kennen muss

- Ist die lokale Datenbank jünger als 4 Stunden, überspringt das Plugin das Update und läuft auch ohne Key und ohne Netz
  grün (mit den lokalen Daten). Ist sie älter, wird ein Update versucht; schlägt es fehl, bricht der Lauf ab.

### Typische Fehlermeldungen

| Meldung | Ursache und Abhilfe |
|---------|---------------------|
| `Invalid API Key, length of 0 …` | `NVD_API_KEY` nicht gesetzt oder Terminal nach `setx` nicht neu geöffnet. |
| `Invalid API Key: xxxxx-*****-xxxxx` | Key falsch oder noch nicht aktiv (nach Beantragung einige Minuten warten). |
| `NVD Update Failed: attempted to retrieve data from the NVD unsuccessfully five times` | NVD nicht erreichbar: Netz oder Proxy prüfen. |
| `Could not find artifact de.skillshield:skillshield-core:jar` | Phase `compile` fehlt im Aufruf (siehe oben). |

### Netzziele des Checks (für Proxy-/Firewall-Freigaben)

- NVD-API: `services.nvd.nist.gov`
- CISA-KEV-Katalog: `www.cisa.gov`
- Hosted Suppressions und Versionsprüfung: `dependency-check.github.io`
- Maven Central für Plugin-Downloads

Abgeschaltet sind OSS Index, Central-Analyzer (`search.maven.org`), Node Audit, RetireJS und Assembly; es gehen keine
Koordinaten oder Hashes des Projekts an Dritte. Das betrifft nur das Build-Werkzeug; das Produkt selbst enthält keinen
Netzwerk-Code.

### Proxy oder Repository-Spiegel

Wird im Netz ein Proxy oder Spiegel gebraucht, gehört die Konfiguration in `~/.m2/settings.xml` (Windows:
`%USERPROFILE%\.m2\settings.xml`), nie ins Repository.

## Hinweis für die Agenten (qa, security)

- qa: `mvn verify` im Wurzelverzeichnis (bei Bedarf `mvn clean verify`).
- security: `mvn clean compile org.owasp:dependency-check-maven:check`, für die Test-Bibliotheken mit
  `-Dodc.skipTestScope=false`. Der Key kommt aus der Umgebung (`NVD_API_KEY`) und wird nicht ausgegeben.
- Die Erstbefüllung der Datenbank macht der Nutzer (sie überschreitet das Zeitlimit eines Agenten-Aufrufs).
- Ein Lauf ohne Key oder Netz ist nur dann ein gültiger Nachweis, wenn das Update nicht übersprungen wurde; bei
  jüngerer Datenbank (< 4 Stunden) wird es übersprungen (siehe oben).
- Eine Shell muss `JAVA_HOME` (JDK 21) und Maven im `PATH` kennen; sonst bricht der Build mit der Enforcer-Meldung
  ab oder `mvn` wird nicht gefunden.
