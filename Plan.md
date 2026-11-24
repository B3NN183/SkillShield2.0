# Plan: Blueprint „SkillShield“ auf Java umschreiben (KV-Version)

## Context
Der Nutzer will den bisherigen Blueprint (Python/PySide6) als **Java-Version** für den Einsatz bei einer
**Kassenärztlichen Vereinigung (KV)**. Das Dokument soll so aufgebaut sein, dass es Entwicklung UND
Datenschutzbeauftragtem (DSB) / Informationssicherheitsbeauftragtem (ISB) vorgelegt werden kann.
Es gibt keinen bestehenden Code im Arbeitsverzeichnis (`/home/user` ist leer) – reine Dokumentarbeit,
keine Exploration nötig. Offene Fragen aus dem Chat (Java-Version, Clients, UI-Framework, KI-Anbieter)
werden im Dokument als **Annahmen mit Default** + Entscheidungsliste geführt, nicht blockierend.

## Ergebnis
Eine Markdown-Datei `/home/user/BLUEPRINT_SkillShield_Java.md` (vom Nutzer bestätigt).
UI-Standard: **Swing + FlatLaf** (vom Nutzer bestätigt); JavaFX nur als verworfene Alternative erwähnt.

## Gliederung des Dokuments
1. **Zweck & Geltungsbereich** – Problem, Ziel, Abgrenzung (Pseudonymisierung ≠ Anonymisierung, Art. 4 Nr. 5 DSGVO).
2. **Grundprinzipien** – offline, keine KI, Mensch entscheidet, umkehrbar, strukturerhaltend.
3. **Ablauf** – 5 Schritte (Eingabe → Scan → Prüfen → Export → Rückübersetzung) + ASCII-Diagramm.
4. **Technologie-Stack Java** – Temurin 21 LTS, Swing + FlatLaf (Default wegen Barrierefreiheit; JavaFX als Alternative),
   java.util.regex, Tess4J, Apache POI, PDFBox, sqlite-jdbc, JCA AES-256-GCM + Bouncy Castle Argon2id,
   SnakeYAML, Maven, jpackage (App-Image ohne Admin-Rechte), JUnit 5. Tabelle mit Lizenzen.
5. **Architektur** – Maven-Module (`skillshield-core`, `-io`, `-vault`, `-ui`, `-app`), Paketstruktur,
   zentrale Interfaces als Java-Skizzen: `Recognizer`, `Finding` (record), `Pseudonymizer`, `Reidentifier`, `Vault`.
6. **Erkennungs-Engine** – allgemeine Erkenner (Pfade Win/UNC/Unix, Benutzer, Server/FQDN, IP, URL, E-Mail,
   Secrets/Entropie, IDs, Custom) + **KV-Erkenner** mit Prüfziffernvalidierung:
   LANR, BSNR (KV-Regionalcode), KVNR, IK-Nummer, ICD-10-Codes, Arzt-/Patienten-/Praxisnamen, Geburtsdaten.
   Hinweis: Prüfziffernalgorithmen gegen offizielle KBV-/GKV-Spezifikation verifizieren.
   Konfliktauflösung, Konfidenzstufen, Profile (YAML-Beispiel mit KV-Begriffen).
7. **Pseudonymisierung** – Platzhalter `[[TYP_n]]`, Konsistenz, strukturerhaltende Pfade, Leak-Check.
8. **Prompt-Hülle & Rückübersetzung** – tolerante Platzhaltersuche, Escaping, Bericht, Skill-Ordner/ZIP.
9. **Bilder/Screenshots** – Tess4J-Boxen, Schwärzen, EXIF entfernen; Hinweis Tesseract = lokales neuronales Netz.
10. **Oberfläche** – überarbeitetes Layout inkl. Feedback zum Screenshot: Spaltenköpfe, Checkbox-/Platzhalter-Spalte,
    Primärbutton, „Für KI kopieren“ erst nach Scan aktiv, Empty States, Statusleiste (Tresor/Leak-Check), Tastaturbedienung.
11. **Sicherheit (technisch)** – kein Netzwerk-Code, Tresor, Auto-Lock, Clipboard-Clear, Logs ohne Klartext.
12. **Compliance-Kapitel KV** – DSGVO/Art. 9, DSB/DSFA/VVT, Freigabe der Ziel-KI (AVV, Drittland, KI-Richtlinie, EU AI Act Art. 4),
    BSI IT-Grundschutz (CON.8), Software-Freigabe (SBOM CycloneDX, OWASP Dependency-Check, Lizenzliste, Code-Signing),
    Personalrat (keine nutzerbezogenen Logs), BITV 2.0, Beschaffung bei externer Entwicklung.
    Klarer Hinweis: keine Rechtsberatung.
13. **Qualitätssicherung** – Roundtrip-Tests, Testkorpus mit synthetischen KV-Daten (keine Echtdaten!), Recall/Precision je Typ,
    Fuzzing der Rückübersetzung.
14. **Roadmap** – MVP → v0.2 Tresor/Profile/Leak-Check → v0.3 Dateien → v0.4 Screenshots → v1.0 Paket + Freigabeunterlagen.
15. **Annahmen & offene Entscheidungen** – Java-Version, Client-Umgebung, UI-Framework, KI-Anbieter, interne vs. externe Entwicklung.

## Nachtrag vom 24.11.2026: Prioritätswechsel und Abweichungen vom ursprünglichen Plan

Dieser Abschnitt geht dem Text darüber vor, wo beide sich widersprechen.

- **Einsatzzweck (zu Kap. 1 und 6):** Wichtigster Einsatzzweck ist vorerst die Pseudonymisierung interner
  IT-Tickets und Betriebsdokumentation, nicht die Abrechnung. Die KV-Erkenner LANR, IK, KVNR und BSNR sind
  umgesetzt und bleiben bestehen; ICD-10-Codes, Arzt-/Patienten-/Praxisnamen und Geburtsdaten als eigener
  Typ ruhen.
- **Reihenfolge der Erkenner (zu Kap. 6 und 14):** 1. Passwörter und Geheimnisse, 2. Server, Hosts,
  Datenbank-Verbindungszeichenfolgen, IPv6, 3. Konten, Rollen und Gruppen, 4. Datenbankobjekte,
  5. Telefonnummern und Personennamen, 6. interne Programmnamen. Der genaue Schnitt in Features mit
  Abhängigkeiten steht in `docs/features/uebersicht-it-infrastruktur.md`.
- **Leitlinien (zu Kap. 2 und 7):** Strukturerhaltung hat Gewicht, weil die KI an realistischen Texten lernen
  soll. Fehlalarme sind fast so schädlich wie fehlende Funde: Fachbegriffe, Versionsnummern, Datumsangaben,
  Ticket- und Change-Nummern bleiben stehen. Für IT-Texte ersetzt das die frühere Linie „im Zweifel
  ersetzen“.
- **Profile (zu Kap. 4 und 6):** Die Namenskonventionen für Konten, Rollen, Gruppen, Server und
  Datenbankobjekte sind organisationsabhängig und müssen konfigurierbar sein. SnakeYAML ist eine
  Fremdbibliothek und nicht freigegeben. Vorgeschlagen, aber noch nicht vom Nutzer freigegeben, ist eine
  einfache Textdatei, die mit Bordmitteln des JDK gelesen wird, ohne reguläre Ausdrücke (Übersicht,
  Abschnitt 5).
- **Stand der Umsetzung (zu Kap. 14):** Erkennungskern für Pfade, URLs, E-Mail, IPv4 und die vier
  KV-Nummern; Pseudonymisierung und Rückübersetzung; Oberfläche (Swing + FlatLaf) mit Scan, Prüfen,
  Markieren von Hand, Kopieren und Rückübersetzen; startbare JAR mit Startskript. Noch nicht umgesetzt:
  Tresor, Profile, Leak-Check, Datei-Import/-Export, Screenshots/OCR, Installationspaket.
- **Fremdbibliotheken (zu Kap. 4):** Bisher ist nur FlatLaf im Produkt. Jede weitere Bibliothek braucht
  die ausdrückliche Freigabe des Nutzers.

## Verifikation
- Dokument einmal komplett gegenlesen: alle 15 Kapitel vorhanden, keine Python-Reste.
- Java-Codeskizzen syntaktisch plausibel (records, Interfaces).
- Datei dem Nutzer per SendUserFile zustellen.