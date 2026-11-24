# SkillShield 2.0

Offline-Werkzeug zur umkehrbaren Pseudonymisierung von Texten, Dateien und Screenshots für eine
Kassenärztliche Vereinigung, bevor Inhalte an eine KI gegeben werden. Fachliche und technische Grundlage
ist [Plan.md](Plan.md).

**Schwerpunkt seit 24.11.2026:** interne IT-Tickets und Betriebsdokumentation (eine KI soll an
pseudonymisierten Beispielen lernen, wie Tickets und Dokumentation geschrieben werden), nicht mehr
Abrechnungsfälle. Die KV-Erkenner (KVNR, BSNR, LANR, IK) bleiben bestehen und werden vorerst nicht
weiterentwickelt. Feature-Schnitt, Reihenfolge und Zielbild stehen in
[docs/features/uebersicht-it-infrastruktur.md](docs/features/uebersicht-it-infrastruktur.md).

- **Stack**: Java 21 (Temurin), Maven-Multi-Modul, Swing + FlatLaf, JUnit 5.
- **Module**: `skillshield-core`, `skillshield-io`, `skillshield-vault`, `skillshield-ui`, `skillshield-app`.
- **Basispaket**: `de.skillshield`.
- **Build und Tests**: `mvn verify`.
- **Grundsätze**: kein Netzwerk-Code im Produkt, kein Klartext in Logs, Testdaten nur synthetisch.
- **Leitlinien für Erkenner**: Strukturerhaltung (ein ersetzter Wert bleibt als Server, Konto, Pfad usw.
  erkennbar; Satzbau und Fachbegriffe bleiben unverändert). Fehlalarme sind fast so schädlich wie fehlende
  Funde: Versionsnummern, Datumsangaben, Ticket- und Change-Nummern und Fachbegriffe werden nicht ersetzt.
  Jedes Erkenner-Feature misst Fehlalarme an einem synthetischen Korpus und weist nach, dass bestehende
  Funde unverändert bleiben (Vergleich alt gegen neu).
- **Fremdbibliotheken**: keine neue Bibliothek und kein neues Build-Plugin ohne ausdrückliche Freigabe
  des Nutzers.

## Ablauf pro Feature

Die Hauptsitzung steuert den Ablauf und ruft die Subagenten aus `.claude/agents/` auf. Subagenten rufen sich
nicht gegenseitig auf. Ein Feature wird nur auf ausdrücklichen Auftrag des Nutzers gestartet.

```
product-owner -> architect -> FREIGABE DURCH DEN NUTZER -> je Task: engineer
              -> reviewer + security (parallel) -> qa
```

1. **product-owner** schreibt `docs/features/<feature>/anforderung.md` (User Story, Akzeptanzkriterien).
2. **architect** schreibt `docs/features/<feature>/architektur.md` (Lösungsansatz, Task-Liste T-1, T-2, ...).
3. **Stopp.** Anforderung und Architektur-Plan dem Nutzer vorlegen und auf die Freigabe warten.
   Ohne ausdrückliche Freigabe startet kein engineer.
4. Für jeden Task einzeln, in der Reihenfolge des Plans:
   1. **engineer** setzt den Task um.
   2. **reviewer** und **security** prüfen die Änderung parallel (beide Aufrufe in derselben Nachricht).
   3. Nur wenn beide `PASS` melden: **qa** nimmt den Task ab.
   4. Meldet qa `PASS`, ist der Task fertig; weiter mit dem nächsten Task.
5. Nach dem letzten Task: dem Nutzer berichten, was umgesetzt wurde und was offen ist.

## Regeln bei FAIL

- **FAIL von reviewer, security oder qa**: zurück zum engineer mit der vollständigen Mängelliste aller
  Prüfer. Danach erneut reviewer + security parallel, dann qa. Es wird immer neu geprüft, auch wenn nur
  einer der Prüfer FAIL gemeldet hatte.
- **Maximal 3 Engineer-Durchläufe pro Task**, die Erstumsetzung zählt mit. Ist der Task danach nicht
  durchgehend PASS: anhalten und den Nutzer fragen, mit den offenen Mängeln und dem bisherigen Verlauf.
- **Security-Fund mit Schwere „hoch“**: sofort anhalten. Nicht zurück zum engineer, keine weiteren Agenten
  starten. Den Fund direkt dem Nutzer melden (Fundstelle, Auswirkung, empfohlene Behebung) und auf seine
  Entscheidung warten. Das gilt unabhängig davon, im wievielten Durchlauf der Fund auftritt.
- Ein Prüfergebnis „nicht ausführbar“ oder „nicht geprüft“ ist kein PASS. Dem Nutzer melden.
- Die Ergebnisse der Prüfer werden nicht umgedeutet: Was als FAIL gemeldet wurde, bleibt FAIL, bis der
  jeweilige Prüfer PASS meldet.

## Schutzregeln

`.claude/settings.json` verweigert das Lesen und Schreiben von `.env`-Dateien, Secret-Ordnern und
Schlüsseldateien sowie gefährliche Befehle (`rm -rf`, `git push --force`, `curl | sh`). Diese Sperren werden
nicht umgangen, auch nicht über Umwege wie Skripte oder andere Befehlsschreibweisen. Wird ein gesperrter
Zugriff für eine Aufgabe gebraucht: den Nutzer fragen.
