# Übersicht: Erkennung von IT-Infrastrukturdaten in Tickets und Dokumentation

Status: Entwurf zur Freigabe durch den Nutzer. Alles unter "Vorschlag" ist nicht entschieden. Geschrieben vom product-owner; die technische Umsetzung plant der architect je Feature.

## 1. Anlass und Leitlinie

Wichtigster Einsatzzweck ist vorerst nicht die Abrechnung, sondern interne IT-Tickets und Betriebsdokumentation. Eine KI soll an pseudonymisierten Beispielen lernen, wie die KV Tickets und Dokumentation schreibt. Daraus folgt:

- **Strukturerhaltung.** Ein ersetzter Wert muss als Server, Konto, Gruppe, Pfad usw. erkennbar bleiben (Platzhalter `[[TYP_n]]` mit sprechendem Typnamen). Satzbau, Fachbegriffe und alles, was kein Name ist, bleiben unverändert.
- **Fehlalarme sind fast so schädlich wie fehlende Funde.** Eine Regel, die viele Fehlalarme erzeugt, wird enger gefasst oder weggelassen und als Grenze dokumentiert. Jedes Feature muss seine Fehlalarme an einem synthetischen Ticket-Korpus messen.
- Weiter gelten die Grundprinzipien aus Plan.md Kap. 2: offline, keine KI im Produkt, der Mensch entscheidet, umkehrbar.
- KV-Nummern (KVNR, BSNR, LANR, IK) bleiben bestehen, werden aber nicht weiterentwickelt (Abschnitt 4).

## 2. Der Schnitt in Features (Reihenfolge der Umsetzung)

| Nr | Ordner | Kurz | Hängt ab von |
|----|--------|------|--------------|
| 0 | `datum-und-kontextvorrang` (nur Teil A) | Datumsangaben sind keine KV-Nummern | nichts |
| 1 | `geheimnisse-erkennung` | Passwörter, Tokens, Schlüssel | nichts |
| 2 | `profil-grundlage` | Profildatei laden, konfigurierbare Muster, Programmnamen | nichts |
| 3 | `server-hosts-erkennung` | Server, Hosts, Verbindungszeichenfolgen, IPv6 | 2 |
| 4 | `konten-rollen-gruppen` | Konten, Dienstkonten, Rollen, AD-Gruppen | 2, sinnvoll nach 3 |
| 5 | `datenbankobjekte` | Schemas, Tabellen, Views | 2 |
| 6 | `telefonnummern-erkennung` | Telefonnummern | nichts (sinnvoll nach 0) |
| 7 | `personennamen-erkennung` | Melder, Bearbeiter | 2, sinnvoll nach 4 und 6 |
| 8 | `mitziehen-gleicher-werte` (Vorschlag, nicht beauftragt) | alle Vorkommen eines gefundenen Wertes | 1 bis 7 |

Abweichungen von der Reihenfolge des Nutzers (jeweils begründet):

- **Profil-Fundament (2) steht vor "Server und Hosts" (3).** Ein nacktes `SRV-APP07` oder `dbsrv-ora01` lässt sich ohne Namenskonvention nicht von `COVID-19`, `UTF-8`, `SHA-256` oder `Windows-10` unterscheiden. Die Präfixe `srv-` und `dbsrv-` sind organisationsabhängig und gehören ins Profil. Ohne Profil bleibt für Feature 3 nur der organisationsunabhängige Teil (FQDN mit internem Suffix, Verbindungszeichenfolgen, IPv6).
- **"Interne Programmnamen über eine Begriffsliste" (Priorität 6) wird mit Feature 2 erledigt.** Eine Wortliste ist die einfachste Art des Profils; sie dient als erster Beleg, dass das Profil funktioniert (`ArvisPro`). Ein eigenes Feature wäre fast leer.
- **Telefonnummern (6) und Personennamen (7) werden getrennt.** Telefonnummern sind organisationsunabhängig und gut erkennbar; Personennamen sind das schwierigste Thema (viele Fehlalarme, keine KI erlaubt) und sollen die Telefonnummern nicht aufhalten.
- Feature 6 hat keine Abhängigkeit und könnte früher laufen; es steht an Position 6, weil der Nutzer es so priorisiert hat.

### Feature 0: `datum-und-kontextvorrang` (Teil A)
- Ziel: Datumsangaben wie 12.02.2026 oder 14.03.1958 werden nicht mehr als BSNR oder KVNR (fraglich) erkannt.
- Erkannt wird nichts Neues; ein Fehlalarm entfällt. Nicht dazu: Geburtsdaten als eigener Schutztyp, Datumsangaben in Worten.
- Fehlalarm-Risiko: ein ungültiges oder ungewöhnlich geschriebenes Datum bleibt Fund (Schutz geht vor).
- Abhängigkeiten: keine. Unabhängig von Teil B und von allen anderen Features.

### Feature 1: `geheimnisse-erkennung`
- Ziel: Passwörter und Zugangsgeheimnisse verlassen die Maschine nicht. Erkannt: Passwörter nach Kontextwort, API-Keys und Tokens nach Kontextwort, wenige eindeutige Formen ohne Kontext (JWT, PEM-Privatschlüssel, Präfix-Tokens), Passwörter in ODBC-/ADO-Zeichenfolgen, Oracle-Form `user/passwort@dienst` und `--password=`.
- Nicht erkannt: Passwörter ohne Kontextwort, Hashes, GUIDs, Commit-IDs, Passwörter nur aus Buchstaben, Hosts und Ports in Verbindungszeichenfolgen (Feature 3), Benutzernamen (Feature 4).
- Fehlalarm-Risiko (Kern des Features): Sätze über Passwörter ohne Wert ("Passwort zurückgesetzt", "Token abgelaufen"), Zahlwörter wie "8-stellig", Fachbegriffe wie "SHA-256".
- Grenze zu Feature 3 (festgelegt): Feature 1 ersetzt in einer Verbindungszeichenfolge nur das Geheimnis. Host, Port und Dienstname sind Feature 3, der Benutzername ist Feature 4.
- URLs mit Zugangsdaten (`https://nutzer:geheim@host/...`) bleiben ein ganzer URL-Fund wie bisher; das Passwort ist darin geschützt, die Struktur der URL geht verloren (siehe dort, D-12). Eine strukturerhaltende Zerlegung von URLs ist ausdrücklich nicht Teil von Feature 1; Feature 3 entscheidet, ob sie kommt.
- Abhängigkeiten: keine. Braucht kein Profil (Geheimnisse sind organisationsunabhängig).

### Feature 2: `profil-grundlage`
- Ziel: konfigurierbare Muster ohne neue Bibliothek (Vorschlag in Abschnitt 5): Profildatei lesen, prüfen, daraus Erkenner bauen; Anzeige des Profilzustands in der Oberfläche.
- Erkannt wird, was im Profil steht: Wortlisten, Präfixe, einfache Platzhaltermuster, Domänen. Als erster Beleg: interne Programmnamen (`ArvisPro`).
- Nicht dazu: fachliche Regeln für Server, Konten usw. (Features 3 bis 5), reguläre Ausdrücke, Namenslisten von Personen.
- Fehlalarm-Risiko: zu weite Muster (`T_*` trifft `T_Shirt`); Gegenmittel sind Stufe, Kontextwörter, Schreibweise und Ausnahmeliste je Eintrag.
- Neu im Produkt: erstmals wird eine Datei gelesen (Sicherheitsfolgen in Abschnitt 5.5).

### Feature 3: `server-hosts-erkennung`
- Ziel: Hostnamen und FQDN auch ohne Pfad davor, Datenbank-Verbindungszeichenfolgen (JDBC, ODBC, Oracle), IPv6.
- Erkannt: FQDN mit internem Suffix (`.local`, `.intern`, `.lan`, `.corp`, `.internal`; weitere im Profil), Hostnamen nach Profil-Präfix (`srv-`, `dbsrv-`) oder nach Kontextwort, Host und Dienstname in JDBC-/ODBC-/Oracle-Zeichenfolgen, IPv6-Adressen. Vorschlag: Der Port bleibt stehen (`:1521` ist Fachbegriff und identifiziert nichts); Host und Dienstname werden ersetzt.
- Nicht erkannt: öffentliche Domänen ohne Kontext (zu viele Fehlalarme), Dateinamen.
- Fehlalarm-Risiko: Dateinamen (`export.log`, `batch.log`) und Bezeichner (`Node.js`, `ASP.NET`, `z.B.`) sehen wie FQDN aus; deshalb nur mit internem Suffix oder Kontext.
- Löst auch den Konflikt der Oracle-Form `user/passwort@host.domäne` (heute vermutlich E-Mail-Fund, siehe Feature 1, G-6).

### Feature 4: `konten-rollen-gruppen`
- Ziel: Benutzer mit Domäne (`DOMÄNE\benutzer`), Dienstkonten (`svc_...`), Rollen und AD-Gruppen.
- Erkannt: nur nach Profil (Domäne, Präfixe, Muster, Listen), dazu der Benutzername in Verbindungszeichenfolgen (`User Id=`, `user/` der Oracle-Form). Ohne Profil keine Funde: Namenskonventionen sind organisationsabhängig und dürfen nicht fest verdrahtet werden.
- Nicht erkannt: Standardkonten und -begriffe (`root`, `admin`, `Administrator`, `SYSTEM`) bleiben Fachbegriffe, außer sie stehen im Profil.
- Fehlalarm-Risiko: Präfixe, die auch in Fachtext vorkommen (`GG-`, `svc_`), und Rollenmuster, die Fachbegriffe treffen.
- Abhängigkeit: 2; sinnvoll nach 3, weil beide Zeichenfolgen der Verbindungszeichenfolge lesen.

### Feature 5: `datenbankobjekte`
- Ziel: Schemas, Tabellen, Views (`ABR_STAGE`, `T_EXPORT_QUEUE`).
- Erkannt: nur nach Profil (Listen und Muster, mit Kontextwörtern wie `Schema`, `Tabelle`, `View`). Ohne Profil keine Funde.
- Nicht erkannt: SQL-Schlüsselwörter und gewöhnliche Wörter hinter `Tabelle`.
- Fehlalarm-Risiko: Muster wie `T_*` oder `ABR_*` überschneiden sich mit Rollen und Gruppen; die Profil-Beispiele trennen sie durch Schreibweise (nur Großbuchstaben) und Kontextwort.
- Abhängigkeit: 2.

### Feature 6: `telefonnummern-erkennung`
- Ziel: Telefon-, Fax- und Mobilnummern in Tickets (Melder).
- Erkannt: Nummern mit Kontextwort (`Tel.`, `Telefon`, `Fax`, `Mobil`, `Durchwahl`) und mit Landesvorwahl (`+49`, `0049`); organisationsunabhängig.
- Nicht erkannt: Ziffernfolgen ohne Kontext und ohne Landesvorwahl.
- Fehlalarm-Risiko: Datumsangaben, Ticket- und Change-Nummern, Versionsnummern, Beträge, Postleitzahlen, KV-Nummern (diese behalten Vorrang).
- Abhängigkeit: keine; Feature 0 sollte vorher laufen, damit Datumsangaben sicher nicht anschlagen.

### Feature 7: `personennamen-erkennung`
- Ziel: Namen von Meldern und Bearbeitern, ohne KI und ohne Wörterbuch.
- Erkannt: Namen hinter Kontextwörtern (`Melder`, `Bearbeiter`, `Ansprechpartner`, `Herr`, `Frau`, `Dr.`), Namensbestandteile, die schon in einer erkannten E-Mail-Adresse oder einem Konto stehen (Vorschlag, der architect prüft die Machbarkeit).
- Nicht erkannt: Namen ohne Kontext (jedes deutsche Substantiv ist großgeschrieben; eine Großschreibungs-Heuristik hätte unzumutbar viele Fehlalarme). Keine Namenslisten im Profil: das wären selbst personenbezogene Daten in einer Konfigurationsdatei.
- Fehlalarm-Risiko: höchstes aller Features; deshalb zuletzt, mit der engsten Regel.
- Abhängigkeit: 2; sinnvoll nach 4 und 6.

### Feature 8 (Vorschlag, nicht beauftragt): `mitziehen-gleicher-werte`
- Ziel: Wird ein Wert an einer Stelle erkannt, werden auch alle weiteren Vorkommen derselben Zeichenfolge ersetzt (Leak-Check, bekannte Grenze G-K16 der KV-Features). Für Geheimnisse ist das besonders wichtig: Das Passwort steht oft ein zweites Mal ohne Kontextwort im Text.
- Wirkt über alle Typen, daher ein eigenes Feature nach den Typ-Features. Vom Nutzer nur zu entscheiden, ob es gewünscht ist.

## 3. Einordnung von `datum-und-kontextvorrang` und der ruhenden KV-Themen

- **Teil A (Datumsangaben) vorziehen und als Feature 0 vor den Geheimnissen umsetzen. Empfehlung: ja.** Begründung: (1) Der Fehler ist am Scanner nachgemessen und trifft Texte mit KV-Kontextwort und Datum; Tickets enthalten viele Daten, und Features 6 und 7 bauen auf dem Datumsschutz auf. (2) Der Teil ist klein, bleibt im Paket `recognizer.kv` und ändert `TextScanner` und `ConflictResolver` nicht. (3) Er hat eine eigene Differenzmessung gegen den Stand vor dem Feature; läuft sie vor Feature 1, bleiben beide Messungen sauber getrennt. (4) Ehrlich dazu: Der Fehler tritt nur auf, wenn ein KV-Kontextwort (`BSNR`, `KVNR`, ...) in Reichweite steht; in reinen IT-Tickets ist das selten. Die Dringlichkeit ist deshalb moderat. Feature 0 und Feature 1 sind unabhängig und ohne Folgen vertauschbar.
- **Teil B (Kontextvorrang) zurückstellen.** Er betrifft nur Texte mit Kontextwörtern mehrerer KV-Arten untereinander, kommt in IT-Tickets kaum vor, ändert den `TextScanner` und senkt in einem Fall bewusst die Konfidenz. Das ist Aufwand und Risiko ohne Nutzen für den neuen Hauptzweck. Die Datei `docs/features/datum-und-kontextvorrang/anforderung.md` bleibt unverändert liegen; die Teile sind dort schon unabhängig lieferbar.
- **Ruhende KV-Themen** (weitere KV-Nummern, Geburtsdaten als eigener Typ, Arzt-/Patientennamen, ICD-10 aus Plan.md Kap. 6): bleiben wie sind, kein Auftrag. Der Schutz der bestehenden Erkenner (LANR, IK, KVNR, BSNR) darf durch kein neues Feature sinken (Differenzmessung alt/neu in jedem Feature).

## 4. Typnamen und Oberfläche

Neue Typnamen (Format `[A-Z][A-Z0-9]{0,31}`, kein Unterstrich): `PASSWORT`, `TOKEN` (Feature 1), `HOST`, `DBDIENST` (3), `KONTO`, `ROLLE`, `GRUPPE` (4), `SCHEMA`, `TABELLE` (5), `TELEFON` (6), `NAME` (7). Typen aus dem Profil sind frei wählbar (z. B. `PROGRAMM`).

Abhängigkeit Oberfläche: `Beschriftungen` in `skillshield-ui` kennt heute neun Typen in einer `Map.of(...)`; diese Schreibweise erlaubt höchstens zehn Einträge. Jedes Feature braucht für seine Typen einen deutschen Namen dort, und ab dem elften Eintrag eine andere Map-Schreibweise. Unbekannte Typen zeigt die Oberfläche bereits mit dem Rohnamen an (kein Absturz). Vorschlag für Feature 2: Ein Profileintrag kann einen deutschen Anzeigenamen mitbringen (`anzeige = Programm`), damit profilgebundene Typen keine Codeänderung brauchen.

## 5. Vorschlag: konfigurierbare Muster (Profil) ohne neue Bibliothek

**Kennzeichnung: Vorschlag zur Freigabe, nicht entschieden.** Plan.md Kap. 4 und 6 nennen YAML mit SnakeYAML; das ist eine Fremdbibliothek und nicht freigegeben. Der Vorschlag weicht deshalb von Plan.md ab; Plan.md müsste nach der Freigabe angepasst werden.

### 5.1 Format: einfache Textdatei, mit dem JDK gelesen

Abgewogen wurden drei Möglichkeiten:

| Format | Lesbarkeit für Administratoren | Fehlermeldungen | Umlaute, Backslash, Kommentare | Urteil |
|--------|-------------------------------|-----------------|-------------------------------|--------|
| `.properties` (JDK) | mäßig; ein Schlüssel kann nicht mehrfach vorkommen, Listen sind umständlich | schlecht (keine Zeilenfehler) | Backslash ist Escape-Zeichen: `KVBSP\` müsste `KVBSP\\` geschrieben werden, ein ständiger Fehler bei Domänen; Doppelpunkt und Gleichheitszeichen in Schlüsseln müssen maskiert werden | ungeeignet |
| JSON/XML aus dem JDK | JSON hat im JDK keinen Parser; XML ist geschwätzig und schlecht von Hand zu pflegen | gut (XML) | gut | zu umständlich |
| **Eigene zeilenbasierte Datei im Stil einer INI-Datei** | gut; Abschnitt je Typ, eine Zeile je Eintrag | frei gestaltbar: "Zeile 12: unbekannte Art" | UTF-8, Kommentare mit `#`, Backslash ist ein gewöhnliches Zeichen | **Vorschlag** |

Das Lesen ist mit Bordmitteln (`java.nio.file`, UTF-8-Decoder mit strenger Fehlerbehandlung) in wenigen Dutzend Zeilen möglich. Eine spätere Umstellung auf YAML bliebe möglich, weil die Inhalte gleich strukturiert sind.

### 5.2 Was ein Eintrag festlegt

Ein Abschnitt `[TYPNAME]` legt den Typ fest (Platzhalter `[[TYPNAME_n]]`). Darunter stehen Zeilen der Form `schlüssel = wert`. Es gibt zwei Gruppen von Schlüsseln.

**Arten (was erkannt wird), mehrfach erlaubt:**

| Art | Bedeutung | Beispiel |
|-----|-----------|----------|
| `wort` | genau dieses Wort (ganzes Wort) | `wort = ArvisPro` |
| `praefix` | Wort beginnt mit dieser Zeichenfolge, mindestens ein weiteres Zeichen folgt | `praefix = svc_` |
| `muster` | einfaches Platzhaltermuster: `*` = eine oder mehr Wortzeichen (Buchstaben, Ziffern, `_`, `-`), `?` = genau ein Wortzeichen, `#` = genau eine Ziffer | `muster = ABR_*_RW` |
| `domaene` | Sonderform für Konten: `DOMÄNE\name` | `domaene = KVBSP` |

**Optionen (wie streng erkannt wird), je Abschnitt höchstens einmal:**

| Option | Bedeutung | Vorgabe |
|--------|-----------|---------|
| `stufe` | `sicher`, `wahrscheinlich` oder `fraglich` (feste Konfidenz je Stufe, bestimmt der architect) | `wahrscheinlich` |
| `kontext` | Kontextwörter; ohne eines davon unmittelbar davor (nur Leerraum, `:`, `=`) kein Fund | keine Bedingung |
| `schreibweise` | `genau` (wie angegeben), `egal` oder `gross` (nur Großbuchstaben, Ziffern, `_`, `-`) | `genau` |
| `ausser` | Wörter, die nie ein Fund sind (nur für diesen Abschnitt) | leer |
| `anzeige` | deutscher Name des Typs für die Oberfläche | der Typname |

Jede `muster`- und `praefix`-Zeile braucht mindestens zwei feste Zeichen (kein `*` allein); sonst lehnt die Prüfung die Datei ab.

**Warum keine regulären Ausdrücke in der ersten Version.** Das JDK enthält zwar `java.util.regex` (keine neue Bibliothek), aber es arbeitet mit Rückverfolgung und kennt keine Zeitgrenze; ein Muster wie `(a+)+b` kann einen Scan beliebig lange blockieren, und ein Administrator schreibt so etwas leicht unbemerkt. Alle Beispiele des Referenz-Tickets sind mit `wort`, `praefix`, `muster` und `domaene` abdeckbar. Falls reguläre Ausdrücke später nötig werden: nur gegen einzelne Wörter bis 128 Zeichen angewendet, Musterlänge höchstens 200, verschachtelte Wiederholungen und Rückverweise verboten, zusätzlich ein Schrittzähler, der die Auswertung nach einer festen Zahl von Zeichenzugriffen abbricht. Das wäre ein eigenes Feature mit eigener Freigabe.

### 5.3 Beispielprofil (alle Angaben erfunden, aus dem Referenz-Ticket)

```
# SkillShield-Profil
profil-version = 1
profil-name = Beispiel-KV

[PROGRAMM]
anzeige = Programm
stufe = sicher
wort = ArvisPro

[KONTO]
anzeige = Konto
stufe = sicher
domaene = KVBSP
praefix = svc_

[ROLLE]
anzeige = Rolle
stufe = wahrscheinlich
muster = ABR_*_RW
muster = ABR_*_RO

[GRUPPE]
anzeige = Gruppe
stufe = sicher
praefix = GG-

[HOST]
anzeige = Server
stufe = wahrscheinlich
schreibweise = egal
praefix = srv-
praefix = dbsrv-

[SCHEMA]
anzeige = Datenbankschema
stufe = wahrscheinlich
kontext = Schema
schreibweise = gross
wort = ABR_STAGE

[TABELLE]
anzeige = Datenbanktabelle
stufe = wahrscheinlich
kontext = Tabelle, View
schreibweise = gross
muster = T_*
```

Die Aufteilung zwischen `ROLLE` (`ABR_*_RW`, mit Kleinbuchstaben) und `SCHEMA` (nur Großbuchstaben, nur mit Kontextwort) verhindert, dass `ABR_Sachbearbeitung_RW` als Schema und `ABR_STAGE` als Rolle erkannt wird. Wo zwei Einträge denselben Bereich treffen, gilt die bestehende Konfliktregel (höhere Konfidenz, dann Typname).

### 5.4 Wo liegt das Profil und wie wird es geladen

- **Vorschlag:** genau eine Datei mit festem Namen (`skillshield-profil.txt`) im Anwendungsordner neben der JAR bzw. im jpackage-App-Image. Die IT verteilt sie zusammen mit der Anwendung und schützt sie per Dateirechte. Es gibt in der ersten Version keinen Auswahldialog: Ein Dialog würde es erlauben, ein Profil aus einer E-Mail oder von einem Netzlaufwerk zu laden.
- Geladen wird einmal beim Start. Die Oberfläche zeigt in der Statuszeile: Profilname, Zahl der Einträge und eine SHA-256-Prüfsumme (`MessageDigest` aus dem JDK), damit Datenschutz- und Informationssicherheitsbeauftragte die Version prüfen können. Der Inhalt des Profils wird nie angezeigt oder protokolliert.
- Ohne Datei läuft das Produkt wie bisher mit den eingebauten Erkennern; die Statuszeile sagt "kein Profil geladen".

### 5.5 Sicherheitsfolgen (das Produkt liest heute nichts und schreibt nichts)

- Es entsteht die erste Lesefähigkeit des Produkts. Sie bleibt eng: **nur lesen**, nur der feste Dateiname im Anwendungsordner (kein Pfad aus Text, Eingabe oder Einstellung), nur eine normale Datei (kein Ordner, kein Gerät, symbolische Links nicht verfolgen), **feste Obergrenzen** (Dateigröße höchstens 256 KiB, Zeilenlänge höchstens 512 Zeichen, höchstens 2 000 Einträge), kein `include`, keine Skripte, keine Ausführung, kein Netzwerk.
- **Ein Profil kann den Schutz nur erweitern, nie verringern.** Es kann keinen eingebauten Erkenner abschalten, keinen eingebauten Typ (`PFAD`, `EMAIL`, `IP`, `URL`, `IK`, `LANR`, `KVNR`, `BSNR`, `PASSWORT`, `TOKEN`, `TEXT`) neu belegen und keine Ausnahme für fremde Funde eintragen; `ausser` wirkt nur auf Einträge desselben Abschnitts. Wer die Datei manipuliert, kann so höchstens zusätzliche Funde erzeugen, nicht Funde unterdrücken.
- **Fehlerhaftes Profil.** Eine Datei, die nicht UTF-8 ist, Grenzen überschreitet oder einen Fehler enthält, wird als Ganzes abgelehnt (kein halb geladenes Profil). Die Meldung nennt Zeilennummer und Art des Fehlers, nie den Zeileninhalt. Vorschlag: Der Scan ist gesperrt, bis die Datei korrigiert ist oder der Anwender ausdrücklich "ohne Profil fortfahren" wählt. Begründung: Ein stilles Weiterarbeiten ohne Profil würde Server- und Kontonamen unbemerkt ungeschützt lassen.
- Der Inhalt des Profils ist vertraulich (interne Namen), aber kein Personenbezug; deshalb keine Namenslisten.

### 5.6 Was bewusst nicht konfigurierbar ist

Platzhalterform `[[TYP_n]]`, Rückübersetzung, Konfliktregel, die Konfidenzwerte der eingebauten Erkenner, die eingebauten Erkenner selbst und ihre Typnamen, die Geheimnis-Erkennung (Feature 1: auch kein Profil-Eintrag kann Kontextwörter für Geheimnisse ändern), reguläre Ausdrücke, das Nachladen weiterer Dateien und der Ort der Datei.

## 6. Zielbild am Referenz-Ticket

Das Ticket liegt für Tests in einer wächterkonformen Fassung vor (Abschnitt 7): E-Mail-Domäne `kv-beispiel.example`, IP-Adresse `192.0.2.47`. Alles andere ist wie vom Nutzer geschrieben. Die Rufnummer ist hier als `<Rufnummer>` geschrieben (Vorwahl, Anschluss, Durchwahl).

"Heute" stützt sich auf das Lesen des Quelltexts; mit (V) markierte Angaben sind Vermutung, die der architect am Scanner nachmisst.

| Stelle im Ticket | Typ / Platzhalter | liefert | heute |
|------------------|-------------------|---------|-------|
| `Thomas Krüger` (Melder) | `[[NAME_n]]` | Feature 7 | bleibt stehen |
| `t.krueger@kv-beispiel.example` | `[[EMAIL_n]]` | bestehend | ersetzt |
| `<Rufnummer>` | `[[TELEFON_n]]` | Feature 6 | bleibt stehen (V) |
| `KVBSP\tkrueger` | `[[KONTO_n]]` | Feature 4 (Profil) | bleibt stehen (V: der Pfad-Erkenner beginnt Backslash-Pfade nur an einer Startgrenze) |
| `ABR_Sachbearbeitung_RW` | `[[ROLLE_n]]` | Feature 4 (Profil) | bleibt stehen |
| `GG-ABR-Export-Lesen`, `GG-ABR-Export-Schreiben` | `[[GRUPPE_n]]`, `[[GRUPPE_m]]` | Feature 4 (Profil) | bleiben stehen |
| `ArvisPro` (im Satz) | `[[PROGRAMM_n]]` | Feature 2 (Profil, Wortliste) | bleibt stehen |
| `\\srv-fs02.kvbsp.local\Abrechnung\Export\Q3` | `[[PFAD_n]]` (ganzer UNC-Pfad, samt Server, Freigabe, `Export`, `Q3`) | bestehend | ersetzt |
| `svc_abr_export` (zweimal) | `[[KONTO_n]]` (zweimal derselbe) | Feature 4 (Profil) | bleibt stehen |
| `SRV-APP07`, `dbsrv-ora01` (frei im Satz) | `[[HOST_n]]`, `[[HOST_m]]` | Feature 3 (Profil-Präfixe) | bleiben stehen |
| `192.0.2.47` | `[[IP_n]]` | bestehend | ersetzt |
| `D:\Apps\ArvisPro\logs\export.log`, `/var/log/arvis/batch.log` | `[[PFAD_n]]`, `[[PFAD_m]]` | bestehend | ersetzt |
| `jdbc:oracle:thin:@dbsrv-ora01.kvbsp.local:1521/ABRPROD` | `jdbc:oracle:thin:@[[HOST_n]]:1521/[[DBDIENST_n]]` | Feature 3 | bleibt stehen (V: kein `://`, kein `:/` hinter dem Host, und `/ABRPROD` hat nur ein Segment und keine Startgrenze) |
| `ABR_STAGE` | `[[SCHEMA_n]]` | Feature 5 (Profil) | bleibt stehen |
| `T_EXPORT_QUEUE` | `[[TABELLE_n]]` | Feature 5 (Profil) | bleibt stehen |
| `Sommer2026!` | `[[PASSWORT_n]]` | Feature 1 | bleibt stehen |
| `https://wiki.kvbsp.local/display/IT/Abrechnungsexport` | `[[URL_n]]` | bestehend | ersetzt |

**Muss stehen bleiben:** `Ticket #48213` (die Nummer ist eine Vorgangsnummer, kein Name), `Version 4.2`, `CHG0012345`, das Wort `Export` und der Titel `Abrechnungsexport` im Fließtext, `Dienst neu gestartet`, `Berechtigungsfehler`, `Benutzer`, `Rolle`, `AD-Gruppe`, `Schema`, `Tabelle`, `Verbindung`, `Doku`, `Lösung`, `Update`, `Temporäres Passwort für` (das Kontextwort bleibt), `jdbc:oracle:thin:@` (Fachbegriff, zeigt die Struktur), der Port `:1521`.

**Entscheidungen zu Grenzfällen (begründet):**

- `Export` und `Q3` innerhalb des UNC-Pfads werden mit dem Pfad ersetzt (der Pfad ist ein Bestandsfund und wird als Ganzes ersetzt). Als freies Wort bleiben beide stehen: `Export` ist Fachbegriff, `Q3` eine Quartalsangabe.
- `ABRPROD` wird ersetzt, solange es als Dienstname in der Verbindungszeichenfolge steht (Feature 3): Der Name identifiziert die produktive Datenbank der Organisation. Als freies Wort an anderer Stelle nur, wenn es im Profil steht.
- Der Port `1521` bleibt: Er ist kein Geheimnis und kein Name und gehört zur erkennbaren Struktur.
- Server in einem Pfad und derselbe Server frei im Satz haben verschiedene Platzhalter (`[[PFAD_n]]` und `[[HOST_m]]`), weil der Pfad als Ganzes ersetzt wird; ebenso der Kurzname `dbsrv-ora01` und der FQDN `dbsrv-ora01.kvbsp.local`. Das ist eine bekannte Grenze; "gleicher Wert, gleicher Platzhalter" gilt für gleiche Zeichenfolgen.
- Steht ein erkannter Wert an einer zweiten Stelle ohne Kontext, bleibt er dort stehen, bis Feature 8 geliefert ist.
- Zwei Funde, die dieselbe Zeichenfolge haben, bekommen denselben Platzhalter (bestehende Eigenschaft des Pseudonymisierers).

## 7. Das Referenz-Ticket in Tests

**Entscheidung: wächterkonforme Fassung, die Wächter bleiben unverändert.**

- Die Wächter sind das Sicherheitsnetz gegen echte Daten. `TestdatenSynthetischTest` im Kern lässt E-Mail-Adressen nur mit reservierter Endung (`example`, `invalid`, `test`, `localhost`, `example.com/.org/.net`) zu und IPv4 nur aus den drei Dokumentationsnetzen; die Wächter in ui und app sind entsprechend streng. Eine Ausnahme für `kv-beispiel.de` müsste in einer geschlossenen Liste stehen und würde den Zweck des Wächters aufweichen: `kv-beispiel.de` könnte eine registrierbare Domäne sein.
- Geändert werden nur zwei Werte, ohne die fachliche Aussage zu berühren: `t.krueger@kv-beispiel.de` wird `t.krueger@kv-beispiel.example` (die Endung `.example` ist reserviert, die E-Mail-Erkennung hängt nicht an der Endung); `10.20.30.47` wird `192.0.2.47` (Dokumentationsnetz; für den IPv4-Erkenner gibt es keinen Unterschied zwischen privatem und Dokumentationsnetz). `kvbsp.local` bleibt (`.local` ist für lokale Netze reserviert und der E-Mail-Wächter betrifft es nicht), ebenso `Sommer2026!` und alle Namen.
- Die Rufnummer des Nutzers hat Blöcke, in denen keine neun Ziffern aufeinanderfolgen (4, 3 und 4 Ziffern); der Wächter für Ziffernketten schlägt nicht an. Sie bleibt unverändert.
- Ablage: eine einzige Textdatei als Testressource im Kern (Backslashes und Umlaute lassen sich dort ohne Escape-Zeichen schreiben). Ein Test prüft zusätzlich, dass die Datei keine E-Mail-Adresse und keine IPv4 außerhalb der erlaubten Bereiche enthält, weil die Wächter diese beiden Regeln nur für Java-Quelltext anwenden.
- Beispielpasswörter und -tokens in Tests sind eindeutig erfunden und stehen zentral in einer Testklasse; Tokens, JWT und PEM-Köpfe werden zur Laufzeit aus Teilen zusammengesetzt (kein zusammenhängendes Literal), damit Geheimnis-Scanner in Entwicklungswerkzeugen nicht anschlagen.

## 8. Entscheidungen, die der Nutzer treffen muss

1. **Reihenfolge: Datumsfehler (Feature 0, Teil A) zuerst, dann Geheimnisse?** Empfehlung: Ja. (Nein heißt: Geheimnisse zuerst, Datumsfehler danach; ohne Nachteil für beide.)
2. **Profil als einfache Textdatei im Anwendungsordner, ohne neue Bibliothek und ohne reguläre Ausdrücke (A), oder SnakeYAML freigeben (B)?** Empfehlung: A. B würde eine Fremdbibliothek mit eigener Freigabeprüfung bedeuten.
3. **Bei fehlerhaftem Profil: Scan sperren, bis die Datei korrigiert ist oder der Anwender ausdrücklich "ohne Profil fortfahren" wählt (A), oder still ohne Profil weiterarbeiten (B)?** Empfehlung: A.

Nicht gefragt, sondern entschieden (Details in den Anforderungen): zwei Geheimnis-Typen `PASSWORT` und `TOKEN`; Geheimnisse werden wie jeder Fund zurückübersetzt und sind wie jeder Fund abwählbar; Ports bleiben stehen; Programmnamen mit Feature 2; Telefon und Namen getrennt.
