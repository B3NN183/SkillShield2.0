# SkillShield 2.0 – Build und Voraussetzungen

Offline-Werkzeug zur umkehrbaren Pseudonymisierung (Fachliches und Architektur: [Plan.md](Plan.md),
Agentensteuerung: [CLAUDE.md](CLAUDE.md)). Diese Datei beschreibt, was zum Bauen und Prüfen nötig ist.

## Stand und Schwerpunkt (24.11.2026)

- **Einsatzzweck:** Der Schwerpunkt liegt auf internen IT-Tickets und Betriebsdokumentation. Sie werden
  pseudonymisiert, damit eine KI an Beispielen lernt, wie Tickets und Dokumentation geschrieben werden.
  Abrechnungsfälle sind vorerst nachrangig.
- **Was heute erkannt wird:** Pfade, Webadressen, E-Mail-Adressen, IPv4-Adressen sowie die KV-Nummern
  LANR, IK, KVNR und BSNR. Alles andere markiert der Anwender von Hand.
- **Was noch nicht erkannt wird:** Passwörter und Tokens, Servernamen ohne Pfad, Datenbank-Verbindungen,
  IPv6, Konten, Rollen und Gruppen, Datenbankobjekte, Telefonnummern, Personennamen, Programmnamen.
- **Geplante Reihenfolge und Zielbild:** siehe
  [docs/features/uebersicht-it-infrastruktur.md](docs/features/uebersicht-it-infrastruktur.md).
  Die KV-Erkenner bleiben bestehen und werden vorerst nicht weiterentwickelt.
- **Datumsangaben (behoben für eindeutig geschriebene, gültige Daten):** Daten wie `12.02.2026`,
  `5.3.2026`, `14/03/1958`, `2026-03-01` und Zeiträume wie `01.03.2026-31.03.2026` werden auch hinter einem
  KV-Kontextwort nicht mehr als BSNR oder KVNR ersetzt. Verbleibende Grenzen: Ein Datum mit angehängter Zahl
  oder Uhrzeit (`14.03.1958 1234`) bleibt ein Fund, bei einem Zeitraum mit einstelligem Tag oder Monat hinter
  einem KV-Kontextwort (erfundenes Beispiel `1.3.2026-15.3.2026`) wird weiterhin teilweise ein neunstelliger
  Teil ersetzt, eine Nummer in Datumsschreibweise (sechs bis acht Ziffern)
  bleibt lesbar, und Geburtsdaten werden nicht geschützt (kein eigener Typ); sie bei Bedarf von Hand markieren.

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

Die Tests der Module `skillshield-ui` und `skillshield-app` laufen standardmäßig **ohne Bildschirm** (headless,
`ui.headless=true`); Tests mit echtem Fenster werden dabei als übersprungen gemeldet. Mit Bildschirm:
`mvn verify -Dui.headless=false`.

Aus `mvn verify` entsteht außerdem die startbare Datei `skillshield-app\target\skillshield.jar` (siehe
„Programm starten“). Die Tests, die diese JAR in einer zweiten JVM prüfen (Klassen `*IT`), laufen in einem zweiten
Surefire-Lauf (`jar-tests`, Phase `integration-test`) nach dem Packen; dafür gibt es kein zusätzliches Plugin.

## Programm starten

**Voraussetzung:** Java 21 oder neuer, empfohlen Eclipse Temurin 21 (die JRE genügt):

```
winget install EclipseAdoptium.Temurin.21.JRE
```

**Start per Doppelklick:** Nach `mvn verify` liegen im Ordner `skillshield-app\target` die Dateien `skillshield.jar`
und `SkillShield.cmd`. `SkillShield.cmd` doppelklicken. Das Fenster „SkillShield“ öffnet sich; die Eingabeaufforderung
soll sich dabei sofort wieder schließen (erwartetes Verhalten, siehe Sichtprüfung). Das Skript nimmt keine Argumente an
und setzt die Optionen `-XX:-UsePerfData -XX:+DisableAttachMechanism -XX:ErrorFile=NUL -XX:-CreateCoredumpOnCrash`
(siehe unten); die Umgebungsvariablen `_JAVA_OPTIONS`, `JAVA_TOOL_OPTIONS` und `JDK_JAVA_OPTIONS` leert es vorher, weil
sie diese Optionen aufheben oder ergänzen könnten. Sind mehrere Java-Versionen installiert, nimmt es die erste `javaw.exe`
im PATH mit Java 21 oder neuer. Die Datei funktioniert auch ohne Dateizuordnung für `.jar` und findet die JAR relativ zu
sich selbst. Für die Weitergabe genügt es, **beide Dateien in denselben Ordner** zu kopieren. Ist kein Java 21 oder neuer
installiert, erklärt `SkillShield.cmd` das in einem Textfenster und nennt den Installationsbefehl.

**Start von der Eingabeaufforderung (ein Befehl):** Empfohlen ist `SkillShield.cmd`. Wer die JAR selbst startet, nimmt
dieselben Optionen:

```
java -XX:-UsePerfData -XX:+DisableAttachMechanism -XX:ErrorFile=NUL -XX:-CreateCoredumpOnCrash -jar skillshield-app\target\skillshield.jar
```

Ist Java älter als 21, meldet die JVM selbst einen Fehler (englisch, vor dem Start des Programms; das kann
SkillShield nicht abfangen). Ohne Bildschirm (z. B. auf einem Server oder in einem Prüfprozess) beendet sich das
Programm mit Exit-Code 2 und der einzigen Zeile „SkillShield benötigt eine grafische Oberfläche.“.

Das Programm selbst legt keine Dateien an und speichert keine Einstellungen (auch nicht die Fenstergröße), führt kein
Protokoll und entpackt keine Bibliotheken; auf die Konsole schreibt es nichts. Alles, was es sich merkt, liegt im
Arbeitsspeicher. Unabhängig davon können Java und Windows Spuren hinterlassen: Die JVM legt beim Start sonst einen
Ordner `hsperfdata_<Benutzer>` im Temp-Ordner an (die Option `-XX:-UsePerfData` verhindert das), und Windows kann
Teile des Arbeitsspeichers in Auslagerungs- und Ruhezustandsdatei schreiben (siehe Grenzen).
Plattform ist Windows. Unter Linux/X11 schreibt Swing jede Markierung eines Textfeldes in die Systemauswahl (PRIMARY);
das lässt sich in der Oberfläche nicht abfangen, deshalb ist Linux nicht unterstützt.

### Anleitung für Anwender

SkillShield ersetzt vertrauliche Angaben in einem Text (zum Beispiel E-Mail-Adressen, IP-Adressen, Pfade,
Versichertennummern) durch Platzhalter wie `[[EMAIL_1]]`, **bevor** Sie den Text an eine KI geben. Die Antwort der KI
übersetzt das Programm danach zurück. Das Programm arbeitet ohne Netzzugriff.

**Ablauf in fünf Schritten**

1. **Text eingeben:** Text in Bereich 1 einfügen oder tippen, dann „Scannen“ (Strg+Enter).
2. **Funde prüfen:** Bereich 2 listet, was das Programm erkannt hat, mit Typ und Sicherheit (sicher, wahrscheinlich,
   fraglich). Jeder Fund ist ein Kontrollkästchen: Angewählte Funde werden ersetzt. Fehlt eine Stelle, markieren Sie
   sie in Bereich 1 und wählen „Markierung als Fund hinzufügen“ (Strg+M).
3. **Für KI kopieren:** Bereich 3 zeigt den Text mit Platzhaltern. „Für KI kopieren“ (Strg+Umschalt+C) legt genau diesen
   Text in die Zwischenablage. **Lesen Sie ihn vorher.**
4. **KI fragen:** Den kopierten Text in die KI einfügen und deren Antwort kopieren.
5. **Zurückübersetzen:** Die Antwort in Bereich 4 einfügen, „Zurückübersetzen“ (Strg+Umschalt+R). Das Ergebnis darunter
   enthält wieder die Originalwerte; „Rückübersetzten Text kopieren“ legt es in die Zwischenablage. Das geht erst nach
   „Für KI kopieren“.

**Tastatur**

| Taste | Wirkung |
|-------|---------|
| Strg+Enter | Scannen |
| Strg+Umschalt+C | Für KI kopieren (immer der ganze Text aus Bereich 3) |
| Strg+Umschalt+R | Zurückübersetzen |
| Strg+M | Markierung als Fund hinzufügen |
| Alt+S, A, M, K, R, T, Z, E | Scannen, Alle anwählen, Markierung als Fund hinzufügen, Für KI kopieren, Zurückübersetzen, Rückübersetzten Text kopieren, Zurücksetzen, Beenden (der Buchstabe ist in der Beschriftung unterstrichen; Windows zeigt die Unterstriche erst, wenn Sie Alt drücken) |
| Alt+1 bis Alt+4 | Springt in Bereich 1 bis 4 |
| Tab / Umschalt+Tab | Nächstes / voriges Element |
| Leertaste in der Tabelle | Ist **eine** Zeile gewählt, schaltet sie das Kontrollkästchen um. Sind **mehrere** Zeilen gewählt, wählt sie alle gewählten Zeilen **an**, nie ab (Abwählen nur einzeln). |

Gesperrte Schaltflächen nennen den Grund als Hinweis, wenn Sie mit der Maus darauf zeigen.

**Was geschützt wird und was nicht**

- Das Programm erkennt, was seine Regeln kennen: E-Mail-Adressen, IP-Adressen, Pfade, Versicherten-, Betriebsstätten-
  und Arztnummern und Ähnliches. **Es erkennt nicht alles.** Namen, Adressen, Geburtsdaten und ICD-10-Codes werden noch
  nicht erkannt. Nummern mit ungewöhnlichen Trennzeichen oder Zeilenumbrüchen mittendrin und Nummern ohne passendes
  Kontextwort können unerkannt bleiben. Ein Wert, der an einer nicht erkannten Stelle steht, bleibt im Klartext, auch wenn
  derselbe Wert an anderer Stelle ersetzt wird. **Lesen Sie deshalb vor dem Kopieren immer den Text für die KI (Bereich 3)
  und markieren Sie fehlende Stellen von Hand.**
- **Markieren von Hand:** Jedes Vorkommen der markierten Zeichenfolge im Text wird geschützt (auch „Meyer“ in
  „Meyerhof“). Schreibvarianten (Groß-/Kleinschreibung, andere Trennzeichen, andere Unicode-Normalform) sind eine eigene
  Zeichenfolge und müssen einzeln markiert werden. Das Hinzufügen kann bereits abgewählte Funde zu einem Fund vom Typ
  „Manuell“ zusammenfassen; wer diesen Fund wieder abwählt, gibt auch den darin enthaltenen markierten Namen frei.
- **Die Zuordnung** (welcher Platzhalter für welchen Originalwert steht) liegt nur im Arbeitsspeicher. Sie endet beim
  Beenden, bei einem neuen Scan und bei „Zurücksetzen“ (jeweils mit Rückfrage, wenn sie noch gebraucht würde).
  **Übersetzen Sie Antworten immer nur zum zuletzt kopierten Text zurück.** Haben Sie nach dem Kopieren die Auswahl
  geändert, kopieren Sie erneut. Platzhalter in der Antwort, die das Programm nicht kennt, erscheinen in der Liste
  „Nicht zurückübersetzt“ (höchstens 500 Zeilen werden gezeigt, der Rest wird nur gezählt).
- **Grenzen der Größe:** Eingabe und KI-Antwort dürfen höchstens 1.000.000 Zeichen lang sein. Zeilen mit mehr als 50.000
  Zeichen werden nicht umbrochen (waagerechter Rollbalken); ein mehrzeiliger Text, der nur insgesamt darüber liegt,
  bleibt nach dem ersten Überschreiten ohne Umbruch.

**Zwischenablage**

- „Für KI kopieren“ legt genau den Text aus Bereich 3 als reinen Text ab, sonst nichts. Eine Teilmarkierung in Bereich
  3 mit Strg+C kopiert trotzdem den **ganzen** Text für die KI (jedes Kopieren dort ist „Für KI kopieren“). Aus der
  Tabelle kann nichts kopiert werden. Jedes Kopieren aus einem Feld über die Tastatur läuft über die Programmlogik; ob
  ein Rechtsklick in einem Feld an SkillShield vorbei kopieren lässt, wird in der Sichtprüfung (Punkt S-11) geprüft.
- Beim **Beenden** und beim **Zurücksetzen** leert das Programm die Zwischenablage, wenn noch Text aus SkillShield
  darin liegt. Beim Beenden wird auch ein nicht lesbarer Inhalt (z. B. ein kopiertes Bild) mitgeleert; er geht dann
  verloren. Lässt sich die Zwischenablage nicht leeren (z. B. weil ein anderes Programm sie sperrt), fragt ein Dialog
  „konnte nicht geleert werden“ nach: „Erneut versuchen“ oder „Trotzdem beenden“. Beim Zurücksetzen kann diese Meldung
  auch erscheinen, wenn Sie die Zwischenablage selbst geleert haben oder ein Bild darin liegt.
- **Grenzen:** Den Zwischenablageverlauf von Windows (Win+V) und die Cloud-Zwischenablage kann das Programm nicht
  leeren. Abmelden, Herunterfahren und Beenden über den Task-Manager umgehen das Leeren und verwerfen die Zuordnung ohne
  Rückfrage. Klartext liegt im Arbeitsspeicher. Stürzt die JVM ab, schreibt sie normalerweise ein Absturzprotokoll
  `hs_err_pid*.log` und unter Windows Server (z. B. Terminalserver-Sitzungen) auch ein Speicherabbild
  `hs_err_pid*.mdmp` in den Startordner; beides kann Klartext enthalten. `-XX:ErrorFile=NUL` unterdrückt das Protokoll,
  `-XX:-CreateCoredumpOnCrash` das Speicherabbild; beides gilt nur beim Start über `SkillShield.cmd` oder mit allen
  oben genannten Optionen. Außerhalb des Programms bleiben: eine vom Administrator eingerichtete
  Windows-Fehlerberichterstattung (WER-LocalDumps), die Auslagerungsdatei, die Ruhezustandsdatei (`hiberfil.sys`) und
  Starts ohne das Skript bzw. ohne diese Optionen.

**Beenden** läuft immer auf demselben Weg (Schaltfläche „Beenden“, Schließen-Kreuz, Alt+F4): Rückfrage, wenn noch eine
Zuordnung besteht, Zwischenablage leeren, alle Felder verwerfen, Prozess beenden.

**Barrierefreiheit (Screenreader):** Alle Bedienelemente haben zugängliche Namen. Windows reicht sie nur über die
Java Access Bridge an NVDA oder JAWS weiter, und die ist standardmäßig aus. Einschalten einmalig je Benutzer mit
`jabswitch -enable` (liegt im `bin`-Ordner des Java-Verzeichnisses), danach Screenreader und SkillShield neu starten.
**Wichtig:** Danach kann jede Software, die auf die Java Access Bridge zugreift, den Inhalt der Felder und der Tabelle
lesen, also auch den Klartext. Schalten Sie sie nur ein, wenn Sie das brauchen und dem Rechner vertrauen
(`jabswitch -disable` schaltet sie wieder aus).

## Lizenzen von Fremdbibliotheken

| Bibliothek | Version | Lizenz | Verwendung |
|------------|---------|--------|------------|
| FlatLaf (`com.formdev:flatlaf`) | 3.6.1 | Apache License 2.0 | Look-and-Feel der Oberfläche, nur Laufzeit von `skillshield-app` |
| JUnit 5 | 5.14.4 | Eclipse Public License 2.0 | nur Tests |

Apache 2.0 verlangt bei Weitergabe die Lizenz- und NOTICE-Datei: Die startbare JAR enthält `META-INF/LICENSE` (Lizenztext
von FlatLaf). FlatLaf 3.6.1 liefert keine NOTICE-Datei, also gibt es auch keine zusammenzuführen. FlatLaf wird ohne seine nativen
Bibliotheken eingepackt und ohne native Bibliothek betrieben (`flatlaf.useNativeLibrary=false`), damit nichts auf die
Platte entpackt wird; der Fensterrahmen kommt vom Betriebssystem.

## Build-Plugins

Alle Plugin-Versionen stehen in der Wurzel-`pom.xml` und sind fest gepinnt. Für die startbare JAR kam das
**maven-shade-plugin 3.6.0** (Apache License 2.0) hinzu; es läuft nur im Build und ist nicht Teil des Produkts. Für die
`*IT`-Tests wird das bereits vorhandene maven-surefire-plugin ein zweites Mal ausgeführt, `SkillShield.cmd` kopiert das
maven-resources-plugin neben die JAR. Neben `skillshield.jar` legt Shade die unveränderte Fassung als
`original-skillshield.jar` ab; weitergegeben wird nur `skillshield.jar`. Für die Weitergabe bauen Sie mit
`mvn clean verify`: Nur nach `clean` ist sicher, dass keine Reste eines früheren Builds im Ordner `target` liegen.

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
mvn clean package -DskipTests org.owasp:dependency-check-maven:check
```

Die Phase `package` ist nötig: Ohne sie scheitert der Lauf im Multi-Modul-Projekt mit
`Could not find artifact de.skillshield:skillshield-core:jar` bzw. (seit der Oberfläche, Test-JAR des Kerns)
`Could not find artifact de.skillshield:skillshield-core:jar:tests`. `-DskipTests` baut die JAR-Dateien, führt aber
keine Tests aus. Der frühere Aufruf mit `compile` genügt nicht mehr (A-6).

Der Check ist nicht Teil von `mvn verify`. Er bricht ab bei Befunden ab CVSS 7 (`failBuildOnCVSS`) und bei Fehlern beim
Aktualisieren der Datenbank (`failOnError`).

### Was der Standardlauf prüft (und was nicht)

Standardmäßig werden nur Compile-/Runtime-Abhängigkeiten geprüft. Seit der Oberfläche ist das FlatLaf 3.6.1
(nur in `skillshield-app`), alle übrigen Module haben keine Fremdbibliothek. Die Test-Bibliotheken (JUnit) werden mit
zusätzlichem Parameter geprüft. Build-Plugins (Maven-Plugins) prüft der Check nicht (bekannte Grenze):

```
mvn clean package -DskipTests org.owasp:dependency-check-maven:check -Dodc.skipTestScope=false
```

Stand 24.11.2026 (Lauf mit `mvn -B clean package -DskipTests org.owasp:dependency-check-maven:check
-Dodc.skipTestScope=false`): 0 Befunde. Geprüfte Artefakte je Bericht: Wurzel 7, core 7, io 8, vault 8, ui 11,
app 12 (darunter FlatLaf 3.6.1). Das NVD-Update wurde in diesem Lauf übersprungen, weil die Datenbank vom selben
Tag war (jünger als 4 Stunden); geprüft wurde also gegen den lokalen Stand dieser Datenbank.

### Grenzen der automatischen Wächter (ui und app)

- **Abhängigkeits-Positivliste** (Enforcer, Phase `validate`): erfasst nur Abhängigkeiten, keine Build-Plugins und
  keine Maven-Extensions. SkillShield-Module sind einzeln aufgezählt, JUnit nur mit Scope `test`, FlatLaf nur in
  der festen Version 3.6.1.
- **Quelltext-Wächter**: sucht verbotene Zeichenfolgen als Teilstring (auch in Kommentaren) und löst
  Unicode-Escapes vor dem Abgleich auf. Leerraum und Kommentar-Einschübe um Punkt und `::` sowie vor `(` werden
  normalisiert (`Files::write`, `System .out`, `System/**/.err` schlagen an); geprüft wird der Quelltext roh
  (konservativ) und ohne Kommentare. Die Kommentare entfernt ein kleiner Zustandsautomat, der Zeichenketten,
  Zeichen und Textblöcke (mit Escapes) kennt; ein `//` in einer Zeichenkette verdeckt also keine Stelle mehr.
  Statische Sternchen-Importe aus `java.lang.System`, `java.nio.file.Files` und `java.lang.Runtime` sind verboten,
  ebenso `System.console`, `getMethod(`, `loadClass(`, `System.load`, `createImageOutputStream`, `PrinterJob`,
  `PrintWriter(`, `PrintStream(`, `newOutputStream`, `getField(`, `getDeclaredField`, `Formatter(` und
  `createNewFile`; Drucken ist im Produkt nicht vorgesehen und deshalb mit `.print(` und `getPrintJob` verboten
  (`println` und `printf` sind davon nicht betroffen). **Der Wächter ist ein Sicherheitsnetz gegen Versehen, kein Beweis.** Nicht erkannt werden alles,
  was nicht auf der Liste steht, Reflection mit zusammengesetzten Zeichenketten und Aufrufe über Hilfsklassen
  fremder Bibliotheken; dafür gilt die Prüfung durch reviewer und security.
- **Testdaten-Wächter**: prüft `src/test/java` und `src/test/resources` auf Ziffernketten ab neun Ziffern (auch mit
  Leerzeichen, Punkt, Bindestrich, Schrägstrich, Unterstrich gruppiert), fremde E-Mail-Domains und IPv4-Adressen.
  Nicht erkannt werden zusammengesetzte Zeichenketten, andere Trenner, IPv6-Adressen sowie Namen, Geburtsdaten und
  Adressen.

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
| `Could not find artifact de.skillshield:skillshield-core:jar` | Phase `package` fehlt im Aufruf (siehe oben). |
| `Could not find artifact de.skillshield:skillshield-core:jar:tests` | Phase `package` fehlt im Aufruf: das Test-JAR des Kerns entsteht erst dort (siehe oben). |

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
- security: `mvn clean package -DskipTests org.owasp:dependency-check-maven:check`, für die Test-Bibliotheken mit
  `-Dodc.skipTestScope=false`. Der Key kommt aus der Umgebung (`NVD_API_KEY`) und wird nicht ausgegeben.
- Die Erstbefüllung der Datenbank macht der Nutzer (sie überschreitet das Zeitlimit eines Agenten-Aufrufs).
- Ein Lauf ohne Key oder Netz ist nur dann ein gültiger Nachweis, wenn das Update nicht übersprungen wurde; bei
  jüngerer Datenbank (< 4 Stunden) wird es übersprungen (siehe oben).
- Eine Shell muss `JAVA_HOME` (JDK 21) und Maven im `PATH` kennen; sonst bricht der Build mit der Enforcer-Meldung
  ab oder `mvn` wird nicht gefunden.
