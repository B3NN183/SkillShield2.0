# Architektur: Maven-Grundgerüst

Feature-Kurzname: `maven-grundgeruest`
Grundlage: `docs/features/maven-grundgeruest/anforderung.md` (AK-1 bis AK-16), `Plan.md` Kap. 2, 4, 5, 12, `CLAUDE.md`.

Stand der Umgebung (geprüft): Windows 11, nur JDK 17 (Microsoft OpenJDK 17.0.16, `JAVA_HOME` zeigt darauf),
kein Maven, kein Git, keine `pom.xml`, kein Java-Code. Es existiert nur die leere Ordnerstruktur der fünf Module.
Downloads per `curl`/`wget`/`Invoke-WebRequest`/`Invoke-RestMethod` sind für Agenten gesperrt.

---

## 1. Lösungsansatz

Ein Parent-POM (`packaging pom`) im Wurzelverzeichnis bindet die fünf Module ein, legt Java 21 fest, verwaltet alle
Plugin-Versionen zentral und stellt JUnit 5 allen Modulen im Test-Scope bereit. Der `maven-enforcer-plugin` lässt
den Build mit verständlicher Meldung scheitern, wenn das JDK nicht 21 ist, Maven zu alt ist oder ein Modul eine
Abhängigkeit entgegen der Richtung aus Plan.md Kap. 5 erhält (`bannedDependencies` je Modul). Zyklen erkennt der
Maven-Reactor selbst. Je Modul belegt ein kleiner JUnit-Test, dass das Modul gebaut wird und nur die erlaubten
Nachbarmodule auf seinem Klassenpfad liegen. Der OWASP-Dependency-Check wird im Parent-POM ohne Lifecycle-Bindung
konfiguriert, sodass er nur bei separatem Aufruf läuft, bei Fehlern scheitert und den NVD-API-Key aus einer
Umgebungsvariable liest. Voraussetzungen und Aufrufe werden in einer `README.md` im Wurzelverzeichnis dokumentiert.

Kein Maven Wrapper, keine fachlichen Bibliotheken, keine Laufzeit-Abhängigkeiten außer den Modulen untereinander.

---

## 2. Voraussetzungen (vom Nutzer auszuführen, nicht Teil eines Engineer-Tasks)

Ohne diese Schritte kann kein Task gebaut und keine Abnahme durchgeführt werden. Agenten dürfen JDK und Maven nicht
selbst beschaffen (Download-Sperre, CLAUDE.md „Schutzregeln“).

| Nr. | Schritt | Hinweis |
|-----|---------|---------|
| V-1 | Eclipse Temurin JDK 21 installieren, z. B. `winget install EclipseAdoptium.Temurin.21.JDK` | winget-Paket-ID vor Ausführung mit `winget search Temurin` prüfen. JDK 17 kann installiert bleiben (wird für den Negativtest von AK-3 genutzt). |
| V-2 | `JAVA_HOME` (Benutzervariable) auf das Temurin-21-Verzeichnis setzen, `%JAVA_HOME%\bin` vorne in `PATH` | Neue Shell/Claude-Code-Sitzung starten, damit Agenten die Variable erben. |
| V-3 | Apache Maven 3.9.x installieren: Binär-ZIP von maven.apache.org laden, SHA-512 gegen die veröffentlichte Prüfsumme vergleichen, entpacken, `bin` in `PATH` aufnehmen. Alternativ ein winget-/Scoop-Paket, falls vorhanden (vor Ausführung prüfen). | Mindestversion siehe E-3. |
| V-4 | Kontrolle: `java -version` zeigt 21, `mvn -version` zeigt Maven 3.9.x und Java 21. | |
| V-5 | (für T-4/security) NVD-API-Key beantragen (kostenlos bei nvd.nist.gov) und als Benutzer-Umgebungsvariable `NVD_API_KEY` setzen, z. B. über „Umgebungsvariablen bearbeiten“ oder `setx NVD_API_KEY <key>` in einer eigenen Konsole. Nicht ins Repository, nicht in `.env`, nicht als `-D`-Argument auf der Kommandozeile. | Siehe E-5. |
| V-6 | (für T-4/security) Erstbefüllung der Schwachstellendatenbank einmal selbst ausführen: `mvn org.owasp:dependency-check-maven:update-only` im Wurzelverzeichnis (nach T-4). | Die Erstbefüllung dauert typischerweise deutlich länger als das Zeitlimit eines Agenten-Bash-Aufrufs. |
| V-7 | Falls im KV-Netz ein Proxy oder Repository-Spiegel nötig ist: in `%USERPROFILE%\.m2\settings.xml` eintragen, nicht im Repository. | Siehe E-11. |

---

## 3. Betroffene Module und Pakete

Keine neuen Klassen, Records oder Interfaces mit Fachbedeutung (AK-11). Die zentralen Typen aus Plan.md Kap. 5
(`Recognizer`, `Finding`, `Pseudonymizer`, `Reidentifier`, `Vault`) entstehen erst in späteren Features.

### 3.1 Neue Dateien

| Datei | Inhalt |
|-------|--------|
| `pom.xml` (Wurzel) | Parent-POM, Koordinaten, Module, Properties, `dependencyManagement` (JUnit-BOM), `dependencies` (JUnit, test), `pluginManagement` (Versionen), `plugins` (Enforcer, Dependency-Check) |
| `skillshield-core/pom.xml` | `parent` = Wurzel-POM, keine Modul-Abhängigkeit, Enforcer-Regel |
| `skillshield-io/pom.xml` | Abhängigkeit `skillshield-core`, Enforcer-Regel |
| `skillshield-vault/pom.xml` | Abhängigkeit `skillshield-core`, Enforcer-Regel |
| `skillshield-ui/pom.xml` | Abhängigkeiten `skillshield-core`, `-io`, `-vault`, Enforcer-Regel |
| `skillshield-app/pom.xml` | Abhängigkeiten `skillshield-core`, `-io`, `-vault`, `-ui` |
| `skillshield-<m>/src/main/java/de/skillshield/<m>/package-info.java` (5x) | nur Paketdeklaration und Javadoc mit dem Modulzweck aus Plan.md Kap. 5 |
| `skillshield-<m>/src/test/java/de/skillshield/<m>/<M>ModulTest.java` (5x) | Smoke- und Richtungstest |
| `README.md` (Wurzel) | Voraussetzungen, Build-Aufrufe, OWASP-Check, NVD-Key |

`<m>` ∈ {`core`, `io`, `vault`, `ui`, `app`}; `<M>` ∈ {`Core`, `Io`, `Vault`, `Ui`, `App`}.
Vorhandene Ordner werden weder umbenannt noch verschoben noch gelöscht (AK-13). Fehlen die Ordner
`src/main/java/de/skillshield/<m>` oder `src/test/java/de/skillshield/<m>`, legt der engineer nur diese an.

### 3.2 Parent-POM (Skizze)

```xml
<groupId>de.skillshield</groupId>            <!-- E-9 -->
<artifactId>skillshield-parent</artifactId>
<version>0.1.0-SNAPSHOT</version>            <!-- E-9 -->
<packaging>pom</packaging>

<modules>
  <module>skillshield-core</module>
  <module>skillshield-io</module>
  <module>skillshield-vault</module>
  <module>skillshield-ui</module>
  <module>skillshield-app</module>
</modules>

<properties>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
  <maven.compiler.release>21</maven.compiler.release>
  <!-- Plugin-/BOM-Versionen als Properties, siehe Abschnitt 5 -->
</properties>
```

- `dependencyManagement`: `org.junit:junit-bom` (`type pom`, `scope import`).
- `dependencies`: `org.junit.jupiter:junit-jupiter`, `scope test` (wird an alle Module vererbt, AK-4).
- `pluginManagement`: feste Versionen für `maven-clean-plugin`, `maven-resources-plugin`, `maven-compiler-plugin`,
  `maven-surefire-plugin`, `maven-jar-plugin`, `maven-enforcer-plugin`, `dependency-check-maven`
  (reproduzierbarer Build, AK-14).
- `maven-compiler-plugin`-Konfiguration: `<compilerArgs><arg>-Xpkginfo:always</arg></compilerArgs>` (ab T-2),
  damit `package-info.class` auch ohne Annotationen entsteht und im Test geladen werden kann.
- `plugins` → `maven-enforcer-plugin`, Execution `basis-pruefung` (Phase `validate`, Ziel `enforce`):
  - `requireJavaVersion` `version=[21,22)` (E-12), `message`:
    „SkillShield benötigt ein JDK 21 (Temurin). Gefunden: ${java.version}. JAVA_HOME auf ein JDK 21 setzen, siehe README.md.“
  - `requireMavenVersion` `version=[3.9.0,)` (E-3), `message`: „SkillShield benötigt Maven 3.9 oder neuer, siehe README.md.“
- `plugins` → `dependency-check-maven` **ohne** `executions` (keine Bindung an `verify`, E-7), nur `configuration`
  (siehe 3.4). Durch die Deklaration im Parent gelten Version und Konfiguration auch beim Direktaufruf
  `mvn org.owasp:dependency-check-maven:check` (AK-9).

### 3.3 Modul-POMs und Abhängigkeitsrichtung (AK-6, AK-7, AK-8)

Interne Abhängigkeiten mit `<version>${project.version}</version>`, Scope `compile`.

| Modul | Abhängigkeiten | `bannedDependencies` (Enforcer-Execution `modulrichtung`, Phase `validate`) |
|-------|----------------|------------------------------------------------------------------------------|
| core  | keine          | `de.skillshield:*` |
| io    | core           | `de.skillshield:skillshield-vault`, `de.skillshield:skillshield-ui`, `de.skillshield:skillshield-app` |
| vault | core           | `de.skillshield:skillshield-io`, `de.skillshield:skillshield-ui`, `de.skillshield:skillshield-app` |
| ui    | core, io, vault| `de.skillshield:skillshield-app` |
| app   | core, io, vault, ui | keine Regel nötig |

- `searchTransitive` bleibt `true` (Default), damit auch transitive Verstöße auffallen.
- `message` je Modul, z. B. „Modulrichtung verletzt (Plan.md Kap. 5): skillshield-io darf nur von skillshield-core abhängen.“
- Ein Verstoß, der einen Zyklus erzeugt (z. B. core → io), wird bereits vom Reactor abgewiesen
  („The projects in the reactor contain a cyclic reference“) – AK-8. Ein Verstoß ohne Zyklus (z. B. io → vault)
  wird vom Enforcer abgewiesen – AK-7.

### 3.4 OWASP-Dependency-Check (AK-9, AK-10)

Konfiguration im Parent (`<configuration>`), Parameternamen vor Umsetzung gegen die Plugin-Doku der gewählten
Version prüfen:

| Parameter | Wert | Zweck |
|-----------|------|-------|
| `nvdApiKeyEnvironmentVariable` | `NVD_API_KEY` | Key aus Umgebungsvariable, nie aus dem Repository (E-5) |
| `failOnError` | `true` | Update-/Analysefehler (kein Netz, ungültiger Key) beenden den Lauf mit Fehler statt grün (AK-10) |
| `failBuildOnCVSS` | `7` | Befund ab CVSS 7.0 lässt den Check fehlschlagen (E-6) |
| `formats` | `HTML`, `JSON` | HTML für Menschen, JSON für den security-Agenten (grep-bar) |
| `ossindexAnalyzerEnabled` | `false` | OSS Index verlangt inzwischen ein Konto; sonst Fehler bzw. Übertragung der Koordinaten an Dritte |
| `nodeAuditAnalyzerEnabled`, `retireJsAnalyzerEnabled`, `assemblyAnalyzerEnabled` | `false` | Für ein reines Java-Projekt irrelevant; vermeidet zusätzliche Netz- bzw. .NET-Abhängigkeiten |
| `skipTestScope` | `true` (Default) | JUnit ist nicht Teil des ausgelieferten Produkts |

Berichte: `target/dependency-check-report.html|json` im Wurzelverzeichnis und in jedem Modul.

### 3.5 Tests (Signaturskizze)

Beispiel `skillshield-io/src/test/java/de/skillshield/io/IoModulTest.java`:

```java
package de.skillshield.io;

class IoModulTest {

    @Test
    void eigenesModulpaketIstKompiliert() {
        assertDoesNotThrow(() -> Class.forName("de.skillshield.io.package-info"));
    }

    @Test
    void erlaubteModuleLiegenAufDemKlassenpfad() {
        assertDoesNotThrow(() -> Class.forName("de.skillshield.core.package-info"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vault", "ui", "app"})
    void verboteneModuleLiegenNichtAufDemKlassenpfad(String modul) {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("de.skillshield." + modul + ".package-info"));
    }
}
```

Erlaubt/verboten je Modul entsprechend Tabelle 3.3. `junit-jupiter` enthält `junit-jupiter-params`.

---

## 4. Wiederverwendung

Kein vorhandener Code. Wiederverwendet wird nur die vorhandene Ordnerstruktur `skillshield-<m>/…/de/skillshield/<m>`
sowie die Build-Konventionen aus `CLAUDE.md` (`mvn verify`) und `.claude/agents/security.md`
(`mvn -q org.owasp:dependency-check-maven:check`).

---

## 5. Neue Abhängigkeiten

Alle Versionen sind der letzte mir bekannte Stand und **vor Umsetzung zu prüfen** (Maven Central bzw. Projektseite).
Der engineer nimmt jeweils die neueste stabile Patch-Version der genannten Linie und meldet die gewählte Version.

### 5.1 Werkzeuge (nicht im Produkt)

| Werkzeug | Version | Lizenz | Begründung |
|----------|---------|--------|------------|
| Eclipse Temurin JDK | 21 (aktuelles 21.0.x-Update) | GPL-2.0 mit Classpath Exception | Plan.md Kap. 4 |
| Apache Maven | 3.9.x (zuletzt bekannt 3.9.11, vor Umsetzung zu prüfen), Minimum 3.9.0 | Apache-2.0 | Plan.md Kap. 4 |

### 5.2 Maven-Plugins (Build-Zeit)

| Plugin | Version | Lizenz | Begründung |
|--------|---------|--------|------------|
| `org.apache.maven.plugins:maven-enforcer-plugin` | 3.6.x (zuletzt bekannt 3.6.1, vor Umsetzung zu prüfen) | Apache-2.0 | AK-3 (JDK-Prüfung mit Meldung), AK-7 (Modulrichtung) |
| `org.apache.maven.plugins:maven-compiler-plugin` | 3.14.x (zuletzt bekannt 3.14.0, vor Umsetzung zu prüfen) | Apache-2.0 | `release 21`; Versionsfixierung |
| `org.apache.maven.plugins:maven-surefire-plugin` | 3.5.x (zuletzt bekannt 3.5.3, vor Umsetzung zu prüfen) | Apache-2.0 | führt JUnit-5-Tests in `verify` aus (AK-4) |
| `org.apache.maven.plugins:maven-resources-plugin` | 3.3.1 (vor Umsetzung zu prüfen) | Apache-2.0 | Versionsfixierung Lifecycle (AK-14) |
| `org.apache.maven.plugins:maven-jar-plugin` | 3.4.x (zuletzt bekannt 3.4.2, vor Umsetzung zu prüfen) | Apache-2.0 | Versionsfixierung Lifecycle (AK-14) |
| `org.apache.maven.plugins:maven-clean-plugin` | 3.4.x/3.5.x (vor Umsetzung zu prüfen) | Apache-2.0 | `mvn clean verify` reproduzierbar (AK-14) |
| `org.owasp:dependency-check-maven` | 12.1.x (aktuelle Version vor Umsetzung zu prüfen; ältere Linien können die NVD-Daten nicht mehr laden) | Apache-2.0 | AK-9, AK-10, Plan.md Kap. 12 |

### 5.3 Bibliotheken

| Artefakt | Version | Scope | Lizenz | Begründung |
|----------|---------|-------|--------|------------|
| `org.junit:junit-bom` → `org.junit.jupiter:junit-jupiter` | neueste 5.x (zuletzt bekannt 5.13.4, eine 5.14.x ist möglich, vor Umsetzung zu prüfen) | test | EPL-2.0 | AK-4, Plan.md Kap. 4. Transitiv: `junit-platform-*` (EPL-2.0), `opentest4j` (Apache-2.0), `apiguardian-api` (Apache-2.0). |

Hinweis: JUnit 6 ist erschienen; Plan.md und CLAUDE.md nennen JUnit 5, daher bleibt es bei der 5.x-Linie.

Keine Laufzeit-Abhängigkeit außer den SkillShield-Modulen untereinander (AK-12). Keine KI-, Netzwerk- oder
fachlichen Bibliotheken (AK-11, AK-12).

---

## 6. Sicherheits- und Datenschutzaspekte

- **Kein Netzwerk-Code im Produkt**: Es entsteht nur `package-info.java`; Tests nutzen nur `Class.forName`.
  Prüfbar mit einer Suche nach `java.net`, `HttpClient`, `Socket`, `URL` in `src/` (AK-11).
- **Netz nur zur Build-Zeit**: Maven Central (Plugins, JUnit) über HTTPS (Maven ≥ 3.8.1 blockiert HTTP-Repositories);
  NVD-API für den OWASP-Check. Es werden keine Projektinhalte übertragen. Der Central-Analyzer des Dependency-Checks
  fragt SHA-1-Prüfsummen öffentlicher Artefakte ab; OSS Index ist deaktiviert, damit keine Koordinaten an einen
  weiteren Dritten gehen.
- **NVD-API-Key**: nur als Benutzer-Umgebungsvariable `NVD_API_KEY`, nie im Repository, nie in `.env`/`secrets/`
  (für Agenten gesperrt und vom Build nicht genutzt), nie als `-DnvdApiKey=…` auf der Kommandozeile (landet in
  Prozesslisten und Agenten-Protokollen). Restrisiko: Agenten mit Shell-Zugriff können Umgebungsvariablen grundsätzlich
  lesen; das gilt ebenso für `~/.m2/settings.xml`. Der Key ist ein kostenloser, rate-begrenzter Lese-Key ohne Zugriff
  auf KV-Daten; das Risiko wird als niedrig eingeschätzt. Agenten dürfen den Key nicht ausgeben.
- **Kein Maven Wrapper**: `mvnw.cmd` lädt Maven per PowerShell-Download nach; das würde die Download-Sperre aus
  `.claude/settings.json` über ein Skript umgehen (CLAUDE.md „Schutzregeln“). Maven wird vom Nutzer installiert.
- **Lieferkette**: alle Plugin-Versionen fest in `pluginManagement`; Maven-ZIP nur mit SHA-512-Prüfung installieren.
- **Kein Klartext in Logs**: Es gibt keine Logausgaben; Testdaten sind keine Personendaten (nur Paketnamen).
- **Berichte** des Dependency-Checks liegen unter `target/` und enthalten nur Abhängigkeitsinformationen.

---

## 7. Tasks

Vor T-1 müssen V-1 bis V-4 erledigt sein, vor dem Abnahmeschritt von T-4 auch V-5 und V-6.
Nach jedem Task ist `mvn verify` grün.

### T-1: Parent-POM und fünf Modul-POMs

- **Dateien**: `pom.xml`, `skillshield-core/pom.xml`, `skillshield-io/pom.xml`, `skillshield-vault/pom.xml`,
  `skillshield-ui/pom.xml`, `skillshield-app/pom.xml`.
- **Inhalt**: Koordinaten, Module, Properties (UTF-8, `release 21`), `pluginManagement` mit allen Versionen aus 5.2
  außer Dependency-Check, Enforcer-Execution `basis-pruefung` (`requireJavaVersion`, `requireMavenVersion`),
  Modul-Abhängigkeiten gemäß Tabelle 3.3 (noch ohne `bannedDependencies`). Noch kein JUnit, kein Java-Code.
- **AK**: AK-1, AK-2, AK-3, AK-5, AK-6 (POM-Ebene), AK-8, AK-12, AK-13.
- **Nachweis/Tests** (Kommandos, da noch kein Code):
  - `mvn verify` → Exit 0, „Reactor Summary“ listet Parent + 5 Module als SUCCESS (AK-1, AK-2, AK-5);
    Reactor-Reihenfolge core vor io/vault vor ui vor app (AK-8).
  - Mit `JAVA_HOME` auf JDK 17 für einen Aufruf: `mvn validate` → Fehlschlag mit der Enforcer-Meldung aus 3.2 (AK-3).
  - `mvn dependency:tree` → je Modul nur die erlaubten `de.skillshield`-Module, keine Fremdbibliothek (AK-6, AK-12).
  - Keine vorhandenen Ordner verändert (AK-13).

### T-2: JUnit 5, `package-info` und Modultests

- **Dateien**: `pom.xml` (JUnit-BOM, `junit-jupiter` test, `-Xpkginfo:always`), je Modul
  `src/main/java/de/skillshield/<m>/package-info.java` und `src/test/java/de/skillshield/<m>/<M>ModulTest.java`.
- **AK**: AK-4, AK-5, AK-6 (Klassenpfad-Nachweis), AK-11, AK-13, AK-14.
- **Tests**: `CoreModulTest`, `IoModulTest`, `VaultModulTest`, `UiModulTest`, `AppModulTest` mit
  `eigenesModulpaketIstKompiliert`, `erlaubteModuleLiegenAufDemKlassenpfad` (nicht für core),
  `verboteneModuleLiegenNichtAufDemKlassenpfad` (nicht für app).
  `mvn clean verify` → grün, Surefire meldet Tests in allen fünf Modulen, 0 Fehler, 0 übersprungen (AK-4, AK-14).
  Suche nach `java.net|HttpClient|Socket` in `src/` ohne Treffer (AK-11).

### T-3: Erzwingen der Modulrichtung

- **Dateien**: `skillshield-core/pom.xml`, `skillshield-io/pom.xml`, `skillshield-vault/pom.xml`,
  `skillshield-ui/pom.xml` (Enforcer-Execution `modulrichtung` mit `bannedDependencies` gemäß Tabelle 3.3).
- **AK**: AK-7, AK-8.
- **Tests/Nachweis**:
  - `mvn verify` bleibt grün.
  - Negativprobe gemäß E-8b: in einer Kopie des Projekts im Scratchpad (nicht im Repository) einmal
    `skillshield-io` → `skillshield-vault` eintragen → `mvn validate` scheitert mit der Enforcer-Meldung (AK-7);
    einmal `skillshield-core` → `skillshield-io` eintragen → Reactor meldet zyklische Referenz (AK-8).
    Der engineer dokumentiert beide Ausgaben (gekürzt) in seinem Bericht.
  - Zusätzlich schlagen die Tests aus T-2 bei einem solchen Verstoß fehl (zweite, automatische Erkennung).

### T-4: OWASP-Dependency-Check

- **Dateien**: `pom.xml` (`dependency-check-maven` in `pluginManagement` mit Version und in `plugins` ohne Execution,
  Konfiguration gemäß 3.4).
- **AK**: AK-9, AK-10, AK-12.
- **Tests/Nachweis**:
  - `mvn verify` bleibt grün und führt den Check nicht aus (kein NVD-Zugriff bei `verify`).
  - Mit gesetztem `NVD_API_KEY` und befüllter Datenbank (V-6): `mvn org.owasp:dependency-check-maven:check` →
    Exit 0, Berichte `target/dependency-check-report.html` und `.json` im Wurzelverzeichnis und in jedem Modul (AK-9).
  - Fehlerfall ohne Netz / mit ungültigem Endpunkt und erzwungenem Update (genaue Parameter, z. B. `nvdValidForHours=0`
    und ein ungültiger NVD-Endpunkt, vor Umsetzung in der Plugin-Doku prüfen): Exit ≠ 0 mit Fehlermeldung (AK-10).
  - Fehlerfall ohne `NVD_API_KEY`: Verhalten der gewählten Plugin-Version feststellen und dokumentieren; siehe offener Punkt P-2.

### T-5: Dokumentation der Voraussetzungen und Aufrufe

- **Dateien**: `README.md` (Wurzel, neu).
- **Inhalt**: Voraussetzungen V-1 bis V-7 (JDK 21, Maven ≥ 3.9, `JAVA_HOME`, NVD-Key als Umgebungsvariable,
  Erstbefüllung der Datenbank, Proxy/Spiegel in `~/.m2/settings.xml`); Aufrufe `mvn verify`, `mvn clean verify`,
  `mvn org.owasp:dependency-check-maven:check`; Ablageort der Berichte; typische Fehlermeldungen
  (falsches JDK, Modulrichtung, fehlender Key/kein Netz) mit Abhilfe; Hinweis, dass der Key nie auf der
  Kommandozeile oder im Repository stehen darf.
- **AK**: AK-15, AK-16.
- **Nachweis**: Durchsicht; die in `README.md` genannten Befehle sind identisch mit denen in `CLAUDE.md`,
  `.claude/agents/qa.md` und `.claude/agents/security.md`.

### Zuordnung AK → Task

| AK | Task | AK | Task |
|----|------|----|------|
| AK-1 | T-1 | AK-9  | T-4 |
| AK-2 | T-1 | AK-10 | T-4 |
| AK-3 | T-1 | AK-11 | T-2 |
| AK-4 | T-2 | AK-12 | T-1, T-4 |
| AK-5 | T-1, T-2 | AK-13 | T-1, T-2 |
| AK-6 | T-1, T-2 | AK-14 | T-2 |
| AK-7 | T-3 | AK-15 | T-5 |
| AK-8 | T-1, T-3 | AK-16 | T-5 |

---

## 8. Entscheidungen für die Freigabe

Jede Zeile kann einzeln bestätigt oder geändert werden. Der Plan ist mit den Empfehlungen umsetzbar.

| Nr. | Frage (Anforderung) | Empfehlung (Default) | Begründung |
|-----|---------------------|----------------------|------------|
| E-1 | SBOM (CycloneDX) im Grundgerüst? (OF 1) | **Nein**, eigenes Feature „Freigabe/Release“ | Kein AK verlangt sie; ohne Fachbibliotheken wäre die SBOM leer. Ein Plugin mehr ist ohne Nutzen. |
| E-2 | Lizenzprüfung/Lizenzliste im Grundgerüst? (OF 2) | **Nein**, später mit E-1 | Wie E-1; Lizenzen der Build-Werkzeuge stehen in Abschnitt 5. |
| E-3 | Maven Wrapper? Mindestversion? (OF 3) | **Kein Wrapper**; lokal installiertes Maven, Minimum **3.9.0** per Enforcer | Der Wrapper lädt Maven per Skript-Download nach und würde die Download-Sperre umgehen; Maven 3.9 ist die aktuelle stabile Linie. |
| E-4 | Wer installiert JDK 21 und Maven? (OF 4) | **Der Nutzer** gemäß V-1 bis V-4 | Geprüft: beides fehlt; Agenten dürfen nicht herunterladen. |
| E-5 | Quelle für die Schwachstellendatenbank, Ablage des NVD-Keys (OF 5) | Direkter NVD-Zugriff vom Entwicklungsrechner; Key beschafft der Nutzer; Ablage als Benutzer-Umgebungsvariable **`NVD_API_KEY`** | Wird vom Build-Prozess der Agenten geerbt, liegt nicht im Repository und nicht in einer gesperrten Datei. Alternative: `<server>` in `~/.m2/settings.xml` mit `nvdApiServerId` (optional mit Maven-Passwortverschlüsselung). Ein interner Spiegel der KV kann später über `nvdDatafeedUrl` angebunden werden. |
| E-6 | Ab welcher CVSS-Schwere scheitert der Check? (OF 6) | **failBuildOnCVSS = 7** (hoch und kritisch) | Passt zur Schwere „hoch“ des security-Agenten; mittlere Befunde erscheinen im Bericht. Alternative: 11 = nur berichten. |
| E-7 | OWASP-Check Teil von `mvn verify`? (OF 7) | **Nein, nur separat** | `verify` bleibt schnell und offline-fähig (nach dem ersten Abhängigkeits-Download); der Check braucht Netz und ggf. lange Updates. |
| E-8a | Modulrichtung aktiv erzwingen? (OF 8) | **Ja**, Enforcer `bannedDependencies` je Modul plus Klassenpfad-Tests | AK-7 verlangt automatische Erkennung; Enforcer greift auch bei `-DskipTests` und liefert eine klare Meldung. |
| E-8b | Wie wird AK-7 negativ nachgewiesen? (eigene Ergänzung) | **Negativprobe in einer Kopie im Scratchpad** durch den engineer, Ausgabe im Bericht; qa prüft Konfiguration und die Tests aus T-2 | Kein Testcode im Produktiv-POM. Alternative: Profil `-Parchitektur-negativtest` in `skillshield-io/pom.xml`, das eine verbotene Abhängigkeit hinzufügt, damit qa den Fehlschlag ohne Dateiänderung selbst auslösen kann (vor Umsetzung prüfen, ob der Enforcer Reactor-Geschwister in `validate` auflöst). |
| E-9 | Maven-Koordinaten (OF 9) | groupId **`de.skillshield`**, Parent `skillshield-parent`, Version **`0.1.0-SNAPSHOT`** | Entspricht dem Basispaket; Start vor dem MVP. Falls die KV ein eigenes Namensschema hat (z. B. Domäne der KV), jetzt ändern – später ist es aufwendiger. |
| E-10 | Qualitätsvorgaben jetzt? (OF 10) | **Keine** zusätzlichen (kein `-Werror`, keine Abdeckungsschwelle) | Ohne Code nicht sinnvoll prüfbar; mit dem ersten Fach-Feature festlegen. |
| E-11 | Eingeschränktes Netz (Proxy, Spiegel)? (OF 11) | Annahme: **direkter Zugriff auf Maven Central und NVD**; falls nicht, Proxy/Spiegel in `~/.m2/settings.xml` (V-7), nie im Repository | Bitte bestätigen; sonst kann bereits T-1 keine Plugins laden. |
| E-12 | JDK-Bereich (eigene Ergänzung) | Enforcer **`[21,22)`**: genau JDK 21 | Plan.md legt Temurin 21 fest; ein JDK 22+ würde unterschiedliche Entwicklerstände erlauben. Alternative `[21,)` (z. B. Bau mit JDK 25, Bytecode bleibt 21). |
| E-13 | Ort der Dokumentation (AK-15/16, eigene Ergänzung) | **`README.md` im Wurzelverzeichnis** | Üblicher Einstiegspunkt. Ein Verweis darauf in `CLAUDE.md` sollte der Nutzer selbst ergänzen, da `CLAUDE.md` die Agentensteuerung ist. |

---

## 9. Sonstige offene Punkte

- **P-1 Nachweisform für Build-AKs**: `.claude/agents/qa.md` verlangt je AK eine Testklasse/-methode. AK-1 bis AK-3,
  AK-5, AK-7 bis AK-10, AK-13, AK-15 und AK-16 sind Build- bzw. Dokumentationseigenschaften; sie werden hier über
  die in Abschnitt 7 genannten Kommandos und deren Ausgabe belegt. qa sollte das als gültigen Beleg akzeptieren,
  sonst müssen diese AKs als FAIL gewertet werden. Bitte bestätigen.
- **P-2 Fehlender NVD-Key (AK-10)**: Je nach Plugin-Version läuft der Check ohne Key mit Warnung weiter (sehr langsam,
  oft Abbruch durch Rate-Limit) statt sofort zu scheitern. Wenn das so ist: Akzeptieren (Ergebnis ist dann nicht
  „scheinbar grün“, sondern entweder vollständig oder ein Fehler) oder zusätzlich eine harte Prüfung verlangen
  (würde einen anderen Aufruf als `mvn org.owasp:dependency-check-maven:check` erfordern, z. B. mit vorgeschaltetem
  `validate` und `requireEnvironmentVariable`). Klärung in T-4, Entscheidung beim Nutzer.
- **P-3 Veraltete Datenbank ohne Netz (AK-10)**: Ist die lokale Datenbank jünger als `nvdValidForHours`, läuft der
  Check ohne Netz bewusst mit den vorhandenen Daten durch. Ob ein fehlgeschlagenes Update bei älterer Datenbank mit
  `failOnError=true` zum Abbruch führt, ist in T-4 zu verifizieren.
- **P-4 Laufzeit für Agenten**: Die Erstbefüllung der NVD-Datenbank sprengt das Zeitlimit von Agenten-Aufrufen;
  daher V-6 durch den Nutzer. Danach dauert ein Check nur noch kurz.
- **P-5 AK-3-Negativtest**: Setzt voraus, dass JDK 17 neben JDK 21 installiert bleibt. Wird es deinstalliert, ist
  AK-3 nur noch über die Enforcer-Konfiguration belegbar.
- **P-6 Kein Git-Repository**: reviewer/security nutzen laut Agentenbeschreibung `git diff`. Ohne Repository müssen
  geänderte Dateien explizit übergeben werden. `git init` ist nicht Teil dieses Features; ggf. vorab durch den Nutzer.
- **P-7 Versionsnummern**: Alle in Abschnitt 5 genannten Versionen sind vor der Umsetzung zu prüfen; der engineer
  meldet die tatsächlich eingetragenen Versionen.

---

## 10. Abweichungen von Plan.md

Keine. SBOM, Lizenzliste und Code-Signing (Plan.md Kap. 12) werden nicht verworfen, sondern gemäß E-1/E-2 einem
späteren Freigabe-Feature zugeordnet; die Anforderung grenzt sie für dieses Feature ebenfalls aus.
