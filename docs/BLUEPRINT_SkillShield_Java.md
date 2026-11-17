# Blueprint: SkillShield Java-Version

## 1. Zweck & Geltungsbereich

SkillShield ist ein lokales, datenschutzorientiertes Prüfwerkzeug für Dokumente und Bilddaten, das sensible Informationen vor dem Einsatz einer KI-gestützten Verarbeitung identifiziert, strukturerhaltend pseudonymisiert und nach der KI-Nutzung wieder in die ursprüngliche Form zurückübersetzt.

Das Ziel ist die sichere, nachvollziehbare und nachvollziehbare Aufbereitung von Inhalten aus der kassenärztlichen Versorgung (KV), insbesondere aus Arztpraxen, Verwaltungsvorgängen und medizinischen Dokumenten, ohne vertrauliche Informationen in KI-Workflows zu übertragen.

Das Werkzeug unterscheidet sich bewusst von einer anonymen Verarbeitung:

- Pseudonymisierung ersetzt sensible Informationen durch nachvollziehbare, aber nicht personenbezogene Platzhalter.
- Anonymisierung entfernt die Zuordnung vollständig und macht eine Rückübersetzung unmöglich.
- Im Sinne von Art. 4 Nr. 5 DSGVO bleiben bei Pseudonymisierung die identifizierenden Merkmale zwar maskiert, aber unter einer kontrollierten, sicheren Zuordnung verwaltbar.

Der Anwendungsbereich umfasst:

- Textdokumente (z. B. PDF, DOCX, TXT, CSV)
- Bilddateien mit OCR-gestützter Erkennung
- Eingaben aus der KV-Praxis, Verwaltung oder Fachabteilung
- Unterstützende Checklisten, Meldungen und Exportformate für interne Freigaben

Der Fokus liegt auf einem klaren Grundsatz: Mensch entscheidet, KI unterstützt nicht automatisch.

## 2. Grundprinzipien

SkillShield folgt den folgenden Kernprinzipien:

1. Offline-First: Keine Nutzung externer Netzwerk-APIs für Erkennung, Pseudonymisierung oder Rückübersetzung.
2. Keine KI-Autonomie: Die KI erhält nur vorstrukturierte, bereits angepasste Inhalte. Sie darf keine Originaldaten sehen.
3. Menschliche Freigabe: Ein Prüfer entscheidet über die Freigabe, Ausnahmen, Zweifel und finale Übernahme.
4. Umkehrbarkeit: Die Zuordnung zwischen Pseudonym und Original bleibt in einem gesicherten, lokal verwalteten Tresor gespeichert.
5. Strukturerhalt: Formate, Layout, Semantik und Belegstruktur bleiben so weit wie möglich erhalten.
6. Datenschutz als Designprinzip: Minimale Speicherung, keine Klartextprotokolle, keine auffälligen Logdaten.
7. Auditierbarkeit: Jede Transformation und Rückübersetzung bleibt nachvollziehbar durch Prüfprotokolle und Ausgabeberichte.

## 3. Ablauf

Der Ablauf besteht aus fünf Schritten:

1. Eingabe
2. Scan
3. Prüfen
4. Export
5. Rückübersetzung

ASCII-Darstellung:

```text
+-------------------+      +-------------------+      +-------------------+
| 1. Eingabe        | ---> | 2. Scan           | ---> | 3. Prüfen         |
| Dokument/Bild      |      | Erkenner + OCR    |      | Treffer + Review  |
+-------------------+      +-------------------+      +-------------------+
                                                           |
                                                           v
                                               +-------------------+
                                               | 4. Export         |
                                               | Pseudonymisiert   |
                                               +-------------------+
                                                           |
                                                           v
                                               +-------------------+
                                               | 5. Rückübersetzung|
                                               | Original wiederher |
                                               +-------------------+
```

Inhaltlich:

- Der Nutzer lädt ein Dokument oder Bild hoch.
- Das System erkennt potenziell sensible Inhalte lokal und strukturiert.
- Ein Review-Prozess prüft Treffer, Bestätigungen und Ausnahmen.
- Eine KI-gestützte Bearbeitung erhält nur die pseudonymisierte Fassung.
- Nach Abschluss wird die ursprüngliche Fassung durch den sicheren Tresor wiederhergestellt.

## 4. Technologie-Stack Java

Das Projekt wird als Java-Anwendung realisiert, mit Fokus auf Stabilität, Sicherung und einfache Bereitstellung in verwaltungsnahen Umgebungen.

### Standard-Stack

- Java: Temurin 21 LTS
- UI: Swing + FlatLaf
- OCR: Tess4J (Tesseract)
- Office-Dateien: Apache POI
- PDF: PDFBox
- Datenbank: sqlite-jdbc
- Kryptographie: Java Cryptography Architecture (JCA) mit AES-256-GCM
- Passwort-Hashing / KDF: Bouncy Castle + Argon2id
- YAML: SnakeYAML
- Build: Maven
- Installer/Distribution: jpackage
- Tests: JUnit 5

### Begründung der Auswahl

- Java 21 LTS ist für langfristige Support- und Sicherheitsanforderungen geeignet.
- Swing + FlatLaf ist der Standard-Default für eine verwaltungsfreundliche Desktop-Anwendung mit guter Barrierefreiheit und einfacher Verteilung.
- JavaFX wird als verworfene Alternative erwähnt, aber nicht als Standard ausgewählt.
- OCR- und Dokumentenbibliotheken sind für lokale Erkennung und Bearbeitung direkt nutzbar, ohne externe Services zu benötigen.
- sqlite-jdbc ermöglicht eine lokale, portable und leicht kontrollierbare Datenhaltung ohne zentrale Serverstruktur.

### Lizenzen und Governance

| Komponente | Zweck | Lizenz | Bemerkung |
| --- | --- | --- | --- |
| Temurin 21 | Java Runtime | GPLv2 + Classpath Exception | Standard-Laufzeit |
| Swing + FlatLaf | UI | Apache 2.0 / LGPL | Desktop-UI |
| Tess4J | OCR | Apache 2.0 | Bindet Tesseract ein |
| Apache POI | Office-Dateien | Apache 2.0 | DOCX, XLSX |
| PDFBox | PDF | Apache 2.0 | Parsing/Export |
| sqlite-jdbc | Lokale DB | SQLite License / MIT-style | Portable DB |
| Bouncy Castle | Kryptographie | MIT | Verschlüsselung + KDF |
| SnakeYAML | YAML | Apache 2.0 | Konfiguration |
| JUnit 5 | Tests | EPL 2.0 | Testframework |

Hinweis: Die finale Auswahl und Freigabe der verwendeten Bibliotheken muss in der Einführungs- und Freigabephase mit Datenschutz, ISB und beschaffungsrechtlichen Vorgaben abgestimmt werden.

## 5. Architektur

Die Anwendung wird in Maven-Modulen aufgebaut, um klare Verantwortungsbereiche zu trennen und die Wartbarkeit zu erhöhen.

### Maven-Module

- skillshield-core: Logik, Erkennung, Pseudonymisierung, Rückübersetzung, Datenmodelle
- skillshield-io: Datei-Handling, OCR, PDF/Office-Import, Export
- skillshield-vault: Tresor, Schlüsselmanagement, Zuordnungen, Auto-Lock
- skillshield-ui: Swing-UI, Dialoge, Statusanzeige
- skillshield-app: Zusammensetzung und Startklasse

### Paketstruktur

```text
de.kv.skillshield
├── app
│   └── SkillShieldApp.java
├── core
│   ├── model
│   │   ├── Finding.java
│   │   └── PseudonymMatch.java
│   ├── recognizer
│   │   ├── Recognizer.java
│   │   ├── RegexRecognizer.java
│   │   └── KvRecognizer.java
│   ├── pseudonymization
│   │   ├── Pseudonymizer.java
│   │   └── PlaceholderService.java
│   ├── reidentification
│   │   ├── Reidentifier.java
│   │   └── ReconstructionService.java
│   └── vault
│       ├── Vault.java
│       └── VaultKeyStore.java
├── io
│   ├── DocumentLoader.java
│   ├── OcrService.java
│   └── ExportService.java
├── ui
│   ├── MainWindow.java
│   ├── ReviewTableModel.java
│   └── StatusBar.java
└── config
    └── AppConfig.java
```

### Zentrale Interfaces und Datentypen

```java
public interface Recognizer {
    List<Finding> detect(String text, RecognitionContext context);
}

public record Finding(
    FindingType type,
    String originalValue,
    String replacement,
    int startIndex,
    int endIndex,
    double confidence,
    String source,
    String note
) {}

public interface Pseudonymizer {
    PseudonymizedDocument pseudonymize(String input, List<Finding> findings);
}

public interface Reidentifier {
    String restore(String pseudonymizedText, Vault vault);
}

public interface Vault {
    void storeMapping(String token, String originalValue, String category);
    Optional<String> resolve(String token);
    void lock();
    boolean isLocked();
}
```

## 6. Erkennungs-Engine

Die Erkennungs-Engine basiert auf einer Kombination aus generischen Mustern und domänenspezifischen KV-Detektoren.

### Allgemeine Erkenner

Die generische Erkennung soll gängige personenbezogene oder vertrauliche Informationen erfassen:

- Dateipfade: Windows/UNC-, Unix- und Netzwerkpfade
- Benutzer-/Benutzergruppen-Namen
- Servernamen und FQDNs
- IP-Adressen
- URLs und Domainnamen
- E-Mail-Adressen
- Tokens / Secrets / Schlüssel mit Entropiebewertung
- Identifikationsnummern und Vertrags-/Kennnummern
- Kundenspezifische Mappings als Konfigurations- und Profileinträge

### KV-spezifische Erkenner

Für den KV-Anwendungsfall werden zudem folgende Datentypen betreut:

- LANR (Lebensartnummer / Arztnummer)
- BSNR (Berufs- und Standortnummer, inklusive Regionalcode)
- KVNR (Krankenversicherungsnummer / KV-Nummer)
- IK-Nummer (Institutionskennzeichen)
- ICD-10-Codes
- Arzt-/Praxis-/Patientennamen
- Geburtsdaten
- Interne Identifikations- und Vorgangsmerkmale

Wichtig: Die Prüfziffernalgorithmen müssen gegen aktuelle offizielle KBV-/GKV-Spezifikationen verifiziert werden. Die Erkennung sollte nicht nur auf Musterbasis erfolgen, sondern auch die Prüfregeln validieren.

### Konfliktauflösung und Konfidenz

Mehrfachtreffer müssen in einer kontrollierten Reihenfolge aufgelöst werden:

- Exakte Treffer mit hoher Sicherheit vor unsicheren Fallbacks
- Überschneidende Treffer werden konsolidiert, nicht doppelt pseudonymisiert
- Mehrdeutige Muster werden mit Hinweis und Review-Flag markiert
- Zuverlässigkeitsstufen: sicher, wahrscheinlich, fraglich

Die Erkennung sollte als konfigurierbare Profile bestehen, damit unterschiedliche KV-Umgebungen unterschiedliche Regeln definieren können.

### Beispiel für ein Profil (YAML)

```yaml
profile: kv-standard
rules:
  - type: lanr
    regex: "\\b(?:LANR|LAnr|laNr)\\s*[:=]?\\s*\\d{9}\\b"
    confidence: 0.95
    validate: true

  - type: ik
    regex: "\\bIK\\s*[:=]?\\s*\\d{9}\\b"
    confidence: 0.92
    validate: true

  - type: kvnr
    regex: "\\bKVNR\\s*[:=]?\\s*[A-Z0-9]{8,12}\\b"
    confidence: 0.90
    validate: false

  - type: birthplace
    regex: "\\bGeburtsort\\s*[:=]?\\s*[A-Za-zÄÖÜäöü\\- ]{2,80}\\b"
    confidence: 0.80
```

## 7. Pseudonymisierung

Die Pseudonymisierung erfolgt strukturerhaltend und konsistent. Eine sensible Stelle wird durch einen eindeutigen, semantisch lesbaren Platzhalter ersetzt:

- `[[PERSON_1]]`
- `[[KVNR_2]]`
- `[[ARZT_3]]`
- `[[GEBURTSDATUM_4]]`

Beispiele:

- `Müller, Anna` -> `[[PERSON_1]]`
- `KVNR: A123456789` -> `[[KVNR_2]]`
- `IK 123456789` -> `[[IK_3]]`

### Grundsätze

- Konsistenz: gleiche Originalwerte erhalten denselben Token, sofern sie in derselben Verarbeitungseinheit identisch sind
- Strukturerhalt: Ausrichtung, Listenstruktur, Tabellen und Dokumentlayout bleiben erhalten
- Leak-Check: Vor dem Export wird geprüft, ob noch Klartextwerte im pseudonymisierten Dokument verbleiben
- Deduplizierung: Mehrfacherkennung derselben Ursache wird nicht mehrfach ersetzt, sondern konsolidiert

### Beispiel einer Ersetzung

```text
Vorher:
Dr. Mustermann, Lisa, geb. 12.05.1988, KVNR A123456789

Nachher:
Dr. [[PERSON_1]], [[PERSON_2]], geb. [[GEBURTSDATUM_3]], KVNR [[KVNR_4]]
```

## 8. Prompt-Hülle & Rückübersetzung

Die KI erhält keine Originaldaten. Stattdessen wird ein robuster Prompt-Container erzeugt, der den pseudonymisierten Text und den Kontext des Vorhabens enthält.

Wichtige Maßnahmen:

- Tolerante Platzhaltersuche: Nicht nur exakte Token, sondern auch nahe Varianten werden erkannt
- Escaping: Sonderzeichen und mehrdeutige Formate werden sicher behandelt
- Bericht: Es wird ein Ergebnisbericht mit geänderten/ersetzten Bereichen erzeugt
- Skill-Ordner / ZIP: Die freigegebene Arbeitsdatei kann als abgegrenzter Package-Bestand zusammengefasst werden

Die Rückübersetzung erfolgt in umgekehrter Reihenfolge mit Hilfe des sicheren Tresors:

1. Alle Platzhalter werden im pseudonymisierten Dokument identifiziert
2. Die Zuordnung wird aus dem Tresor geladen
3. Originalwerte werden nur an den zuvor markierten Positionen wieder eingetragen
4. Der Verlauf wird mit Metadaten und Prüfprotokoll dokumentiert

## 9. Bilder, Screenshots & OCR

Bei Bilddateien wird zusätzlich OCR eingesetzt. Die Anwendung kann sensible Bereiche im Bild identifizieren, z. B.:

- Name, Geburtsdatum, Versicherungsnummern
- Adressen und Kliniken
- Unstrukturierte Texteinträge auf Screenshots

### Bildverarbeitung

- OCR mit Tess4J / Tesseract
- Maskierung sensibler Bereiche direkt im Bild
- Ersatz mit semantisch passenden Platzhaltern oder Rechtecken
- EXIF- und Metadatenbereinigung, sofern das Bildformate erlaubt
- Schutz vor ungewollten Metadatenlecks in Bilddateien

Wichtig: Tesseract arbeitet mit lokaler OCR-Analyse und ist damit im Kern ein lokaler, nicht netzwerkbasierter Prozess. Die Anwendung bleibt im lokalen Betrieb.

## 10. Oberfläche

Die Oberfläche wird als Desktop-Programm mit klarer Arbeitslogik und Benutzerführung gestaltet.

### Layout

- Linke Seite: Dateiverwaltung und importierte Dokumente
- Mitte: Vorschau / Prüfansicht / OCR-Ergebnis
- Rechte Seite: Details zu markierten Treffern und deren Kategorien
- Untere Bereich: Statusleiste mit Tresorstatus, Leak-Check und Gesamtaktualisierung

### Arbeitsablaufansicht

Die Tabelle zeigt in einer übersichtlichen Darstellung:

- Dokument / File
- Trefferart
- Originalwert
- Platzhalter
- Status (geprüft / akzeptiert / übersprungen)
- Aktion (Freigeben / ablehnen / manuell korrigieren)

### UX-Details

- Primärbutton: „Scan starten“ bzw. „Für KI freigeben"
- Die Funktion „Für KI kopieren“ wird erst nach erfolgreichem Scan aktiviert
- Empty State: Wenn keine Dateien oder Treffer vorliegen, klare Hinweisanzeige
- Tastaturbedienung: Standard-Tastenkürzel für Import, Prüfung und Freigabe
- Statusleiste: Anzeige, ob Tresor aktiv ist, ob ein Leak-Check läuft oder ob ein Dokument geschützt wurde

## 11. Sicherheit (technisch)

Technisch muss die Anwendung die Risikofaktoren im Handlungsraum klar minimieren.

### Kernmaßnahmen

- Kein Netzwerkcode für die eigentliche Erkennungs- und Pseudonymisierungslogik
- Lokaler Tresor für die Zuordnungen und Schlüssel
- Auto-Lock bei Inaktivität oder nach Freigabezeitraum
- Clipboard-Clear für Zwischenablagen nach dem Export oder nach dem Zugriff
- Keine Speicherung von Klartextwerten in Logs, Reports oder allgemeinen Debug-Ausgaben
- Verschlüsselung sensibler Metadaten mit AES-256-GCM
- Passwort-Hashing mit Argon2id als geeignetes KDF-Verfahren
- Gültigkeit und Ablaufzeiten für Token, Vault-Beziehungen und Exportpakete

### Prinzipien

- Rückübersetzung nur durch eine sichere, lokale, autorisierte Aktion
- Keine systemweite Freigabe an unkontrollierte Server oder KI-Dienste
- Zugriff nur über die kontrollierte Anwendung, nicht durch Dateisystemmanipulation

## 12. Compliance-Kapitel KV

Die Umsetzung muss in der KV-Umgebung als belastbare Datenschutz- und Sicherheitslösung verstanden werden.

### DSGVO und Datenschutz

- Einhaltung der Grundlage des Datenschutzes, insbesondere bei personenbezogenen Daten und Gesundheitsdaten
- Unterscheidung zwischen identifizierenden, pseudonymisierten und anonymisierenden Verfahren
- Beachtung von Art. 9 DSGVO bei Gesundheitsdaten
- Dokumentation der Verarbeitungstätigkeit, Verantwortlichkeiten und Freigabewege

### DSB / ISB / VVT / Software-Freigabe

- DSB bzw. ISB müssen die Anwendung als sicherheitsrelevantes System prüfen
- Ein Data Protection Impact Assessment (DPIA) bzw. DSFA ist je nach Nutzungskontext erforderlich
- Vorbereitete Verfahrensdokumentation, Notfall- und Verantwortlichkeitsbeschreibung
- Software-Freigabe mit
  - SBOM (CycloneDX)
  - OWASP Dependency-Check
  - Lizenzliste
  - Code-Signing
- Prüfung der externen Entwicklung nach Beschaffungs- und Qualitätskriterien

### KI-bezogene Einordnung

Bei der Freigabe einer KI-Zielumgebung gelten weitere Anforderungen:

- Freigabe der Ziel-KI nach AVV bzw. interner Governance
- Prüfung bei Drittlandbezug und Datenweitergabe
- Einhaltung der EU AI Act, insbesondere Art. 4, soweit relevant
- Sicherheits- und Zweckbindung für derartige KI-Workflows

### BSI IT-Grundschutz

- Bezug auf relevante Sicherheitsmaßnahmen im Bereich Konfiguration, Sicherung, Zugriff und Verfahren
- Als Leitprinzip: CON.8 und ähnliche Grundschutzbausteine für IT-Sicherheit und sichere Verarbeitung

### Barrierefreiheit und Behördengutachten

- BITV 2.0: Die Oberfläche muss barrierefrei und nutzerfreundlich sein
- Anwenderfreundlichkeit für Mitarbeitende mit unterschiedlicher fachlicher und technischer Erfahrung

Hinweis: Dieses Kapitel ersetzt keine Rechtsberatung. Die finale rechtliche Einordnung muss durch die zuständigen Datenschutz- und IT-Stellen erfolgen.

## 13. Qualitätssicherung

Die Anwendung muss auf Zuverlässigkeit, Wiederholbarkeit und Stabilität im KV-Umfeld ausgelegt sein.

### Teststrategie

- Roundtrip-Tests: Dokument -> Pseudonymisierung -> KI-Weiterverarbeitung -> Rückübersetzung
- Testkorpus aus synthetischen KV-Daten, niemals mit realen Patientendaten
- Precision- und Recall-Analyse je Erkennertyp
- Grenzfälle für Mehrdeutigkeiten, Überschneidungen, Formatregeln und Randbedingungen
- Fuzzing der Rückübersetzung, um fehlerhafte Platzhalter- und Token-Umsetzungen zu erkennen

### Qualitätsziele

- geringe Fehlalarme bei normalen Namen, Adressen und Praxisbezeichnungen
- hohe Erkennung bei KV-relevanten Identifikatoren
- belastbare Rückübersetzung ohne Umsortierung oder fehlerhafte Ersetzung
- nachvollziehbare Prüfberichte für jede Freigabe und jeden Export

## 14. Roadmap

### MVP

- Lokale Dokumentenerfassung
- Basis-Erkennung von Namen, Personen, E-Mail, Kennnummern
- Pseudonymisierung mit einfacher Trefferreview-Funktion
- Rückübersetzung über lokal verwalteten Tresor

### v0.2

- Erweiterter Tresor mit sicheren Zuordnungen
- Profil- und Regelverwaltung
- Leak-Check und Schutzprüfungen

### v0.3

- Unterstützung für zusätzliche Dateiformate und OCR für Bilddateien
- Export- und ZIP-Container für KI-Workflows

### v0.4

- Verbesserte UI und Startbildschirm
- Erweiterte Bild-/Screenshot-Analyse
- Status- und Audit-Leitfaden im VVT

### v1.0

- Freigabe, Paketierung und Installation mit jpackage
- Dokumentation, SBOM und Freigabeunterlagen
- Interne Sicherheits- und Compliance-Prüfung

## 15. Annahmen & offene Entscheidungen

Im Dokument werden die folgenden Annahmen mit Default-Wert und Entscheidungsliste geführt, damit die Entwicklung nicht blockiert wird und die Gesamtentscheidung für die Freigabe transparent bleibt.

### 15.1 Java-Version

- Default: Java 21 LTS (Temurin)
- Entscheidung: Java 21 wird als Standard-Wert empfohlen, weil LTS-Support, Stabilität und verbreitete Betriebssicherheit bestehen.

### 15.2 Client-Umgebung

- Default: Windows-basierte Verwaltungspc / interne Arbeitsplatz-Umgebung
- Entscheidung: Standalone-Desktop-Anwendung mit lokaler Verarbeitung, kein Server-Backend, kein externes zentrale Verarbeitungssystem.

### 15.3 UI-Framework

- Default: Swing + FlatLaf
- Entscheidung: Swing + FlatLaf wird als Standard gewählt. JavaFX wird als alternative, aber verworfene Option dokumentiert.

### 15.4 KI-Anbieter / Zielsystem

- Default: Keiner, da die Anwendung bewusst lokal arbeitet und die KI nicht mit Originaldaten beliefert
- Entscheidung: Nur sogenannte pseudonymisierte Einhüllungen bzw. bereitgestellte Skill-Container dürfen an eine freigegebene KI übergeben werden. Der konkrete Anbieter muss durch Governance und Freigabe freigegeben werden.

### 15.5 Interne vs. externe Entwicklung

- Default: Externe Entwicklung bzw. kooperierende technische Partner können mitwirken, sofern Governance, Code-Signing, Lizenzprüfung und Zugriffskontrollen eingehalten werden
- Entscheidung: Die eigentliche Governance und Freigabe bleiben intern, auch wenn der technische Aufbau extern umgesetzt wird.

## Verifikation

Vor Abgabe des Dokuments sollten die folgenden Prüfungen erfolgen:

- Vollständigkeit: alle 15 Kapitel sind vorhanden
- Keine Python-Reste: Der Text beschreibt ausschließlich Java- und Desktop-Architektur
- Java-Skizzen syntaktisch plausibel: Interfaces, Record und Module sind konsistent
- Abschlussprüfung: Dokument soll für den DSB/ISB, die Entwicklung und die KV-Umfeld-Governance verständlich und freigabefähig sein
- Freigabe: Das Dokument wird strukturiert als Deliverable an den Benutzer zur Weiterverwendung übergeben

## Abschluss

SkillShield in der Java-Version ist als lokales, rechts- und datenschutzgerechtes Desktop-Tool konzipiert, das sensible KV-Daten vor einem KI-gestützten Prozess schützt, sie strukturerhaltend pseudonymisiert und nach dem Verarbeitungsvorgang mit hoher Sicherung wieder in den Originalzustand zurückführt. Die Architektur, das Sicherheitsmodell und die Governance-Kriterien sind bewusst so gewählt, dass die Anwendung im KV-Kontext mit interner Freigabe, Datenschutz- und Sicherheitsprüfungen nutzbar und nachvollziehbar bleibt.
