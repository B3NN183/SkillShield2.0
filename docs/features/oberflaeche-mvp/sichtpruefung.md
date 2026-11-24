# Sichtprüfung Oberfläche (MVP)

Diese Liste ist für Sie, nicht für ein Werkzeug: Alles hier lässt sich nur am echten Bildschirm mit echter Tastatur und
Maus beurteilen. Planen Sie etwa 45 Minuten für die 18 Punkte ein. Haken Sie jeden Punkt ab, wenn Sie gesehen haben, was dort steht. Wenn
etwas **anders** aussieht oder sich anders verhält, notieren Sie die Nummer und was Sie gesehen haben (ein Foto vom
Bildschirm hilft) und geben Sie das weiter.

**Vorbereitung**

- Java 21 ist installiert. Im Ordner `skillshield-app\target` liegen `skillshield.jar` und `SkillShield.cmd`
  (entsteht durch `mvn verify`).
- Benutzen Sie **nur diese erfundenen Beispieltexte**, keine echten Daten:
  - Beispieltext A: `Bitte antworten an max.mustermann@example.org. Der Server hat die Adresse 192.0.2.10 und der
    Ordner heißt C:\Beispiel\Ordner\Bericht.txt.`
  - Beispieltext B (lang): Beispieltext A etwa 4.000-mal hintereinander, also rund 560.000 Zeichen. Der Text muss
    **unter 1.000.000 Zeichen** bleiben, sonst lehnt das Programm ihn ab. So erzeugen Sie ihn mit dem Editor
    (Notepad): Beispieltext A hineinschreiben, dann **zwölfmal** nacheinander: Strg+A (alles markieren), Strg+C
    (kopieren), Strg+Ende (ans Textende), Strg+V (einfügen). Jede Runde verdoppelt den Text; nach zwölf Runden sind es
    4.096 Kopien (rund 565.000 Zeichen). Eine dreizehnte Runde wäre zu viel. Den fertigen Text mit Strg+A, Strg+C in
    Bereich 1 einfügen.
  - Zum Prüfen der Zwischenablage brauchen Sie einen Editor (Notepad).
- Die Zwischenablage kann während der Prüfung geleert werden: Legen Sie nichts Wichtiges dort ab.

---

## S-1 Start und Fenstertitel

- [ ] `SkillShield.cmd` doppelklicken. **Sie sollten sehen:** ein Fenster mit dem Titel genau „SkillShield“ (ohne
  weiteren Text); die schwarze Eingabeaufforderung schließt sich sofort wieder.
- [ ] Der Cursor blinkt direkt im großen Eingabefeld (Bereich 1), ohne dass Sie klicken müssen.
- [ ] (Nur wenn Sie wollen) Starten Sie die Datei auf einem Rechner ohne Java 21: **Sie sollten sehen:** ein Textfenster,
  das sagt, dass Java 21 fehlt, und den Installationsbefehl.

## S-2 Alle Texte deutsch

- [ ] Lesen Sie alle sichtbaren Beschriftungen: Überschriften der vier Bereiche, Schaltflächen, Statusleiste, Hinweise.
  **Sie sollten sehen:** alles auf Deutsch, keine englischen Reste.
- [ ] Geben Sie Beispieltext A ein, wählen Sie „Scannen“, dann „Zurücksetzen“. **Sie sollten sehen:** eine Rückfrage mit
  den Schaltflächen „Ja“ und „Abbrechen“ (nicht „Yes“/„Cancel“); „Abbrechen“ ist die vorgewählte Schaltfläche.
- [ ] **Esc-Taste im Dialog:** Zurücksetzen erneut wählen und im Dialog Esc drücken. **Sie sollten sehen:** der Dialog
  schließt sich wie bei „Abbrechen“, es wird nichts gelöscht.

## S-3 Größe und Layout

- [ ] Bringen Sie das Fenster auf etwa 1024 × 768 Pixel (oder lassen Sie es so, wie es startet). **Sie sollten sehen:**
  vier Bereiche (zwei oben, zwei unten), darunter „Zurücksetzen“ und „Beenden“ und ganz unten die Statusleiste; nichts
  verdeckt sich, kein Text ist abgeschnitten.
- [ ] Ziehen Sie das Fenster größer und kleiner. **Sie sollten sehen:** die Bereiche wachsen und schrumpfen mit. Kleiner
  als die Mindestgröße lässt sich das Fenster nicht ziehen.
- [ ] Falls Ihr Bildschirm auf 125 % oder 150 % Skalierung steht: Gleiche Prüfung, nichts darf überlappen.

## S-4 Startzustand

- [ ] Frisch gestartet: **Sie sollten sehen:** erklärende Texte in den noch leeren Bereichen (z. B. „Noch kein Scan …“),
  die Statusleiste zeigt „Bereit“ und „Keine Zuordnung“.
- [ ] Scannen Sie einen Text ohne Fundstelle (z. B. „Guten Tag, das Wetter ist schön.“). **Sie sollten sehen:** den
  Hinweis, dass keine Fundstelle erkannt wurde, dass das nicht heißt, der Text sei unbedenklich, und dass Sie fehlende
  Stellen von Hand markieren sollen.

## S-5 Langer Text, Fenster bleibt bedienbar

- [ ] Fügen Sie Beispieltext B in Bereich 1 ein und wählen Sie „Scannen“. **Sie sollten sehen:** In der Statusleiste steht
  „Scan läuft …“; das Fenster lässt sich währenddessen verschieben und neu zeichnen (z. B. kurz in eine Ecke ziehen).
  „Scannen“ ist gesperrt; nach dem Scan ist alles wieder frei.
- [ ] **Sie sollten sehen:** Das Fenster friert zu keinem Zeitpunkt ein (kein „Keine Rückmeldung“ in der Titelleiste).

## S-6 Tabelle der Funde

- [ ] Scannen Sie Beispieltext A. **Sie sollten sehen:** eine Tabelle mit Spaltenköpfen; die Sicherheit steht als Wort
  („sicher“, „wahrscheinlich“, „fraglich“) und nicht nur als Farbe; die Kontrollkästchen sind auch ohne Farbe erkennbar.
- [ ] **Buchstabentaste auf der Tabelle:** Klicken Sie in die Tabelle und drücken Sie eine Buchstabentaste (z. B. „x“).
  **Sie sollten sehen:** Es öffnet sich **kein** Eingabefeld in einer Zelle.
- [ ] **Bearbeitung endet bei Fokuswechsel:** Klicken Sie mit der Maus ein Kontrollkästchen in der Tabelle an und
  wechseln Sie dann mit Tab oder mit einem Klick ins Eingabefeld. **Sie sollten sehen:** Es bleibt keine offene
  Zellbearbeitung zurück (keine Zelle mit Eingabecursor oder Rahmen), und der Haken in der Tabelle passt zum Text in
  Bereich 3 (angewählt: der Platzhalter steht dort; abgewählt: der Originalwert steht dort).

## S-7 Fundstelle zeigen

- [ ] Wählen Sie in der Tabelle eine Zeile (mit der Maus oder den Pfeiltasten). **Sie sollten sehen:** Die Fundstelle im
  Eingabefeld ist hervorgehoben und ins Bild gescrollt (bei Beispieltext B testen). Der Text bleibt unverändert, und der
  Cursor bleibt in der Tabelle (das Eingabefeld holt sich den Fokus nicht).

## S-8 Nur mit der Tastatur bedienen

- [ ] Legen Sie die Maus beiseite. Fügen Sie Beispieltext A mit Strg+V in Bereich 1 ein.
- [ ] **Strg+Enter im Eingabefeld:** **Sie sollten sehen:** Der Text wird gescannt. Es entsteht **kein Zeilenumbruch** im
  Eingabefeld.
- [ ] Mit **Tab** und **Umschalt+Tab** durch alle Elemente laufen. **Sie sollten sehen:** Die Reihenfolge ist
  Eingabefeld, Tabelle, Text für die KI, KI-Antwort und die Schaltflächen; gesperrte Schaltflächen werden
  übersprungen; **der Fokus ist an jedem Element deutlich sichtbar** (Rahmen oder Markierung).
- [ ] In der Tabelle: **Leertaste** auf einer einzelnen Zeile schaltet das Kontrollkästchen um. Mit Strg+A mehrere Zeilen
  wählen und Leertaste drücken: Alle gewählten Zeilen werden **angewählt**, keine wird abgewählt.
- [ ] **Strg+Umschalt+C:** Der Text für die KI wird kopiert (Statusleiste meldet es). Fügen Sie ihn in Notepad ein:
  Er enthält Platzhalter wie `[[EMAIL_1]]`, nicht die E-Mail-Adresse.
- [ ] Fügen Sie den Text aus Notepad als „KI-Antwort“ in Bereich 4 ein (Alt+4, dann Strg+V) und drücken Sie
  **Strg+Umschalt+R**. **Sie sollten sehen:** Das Ergebnis zeigt wieder die erfundene E-Mail-Adresse.
- [ ] **Strg+M:** Markieren Sie in Bereich 1 das Wort „Bericht“ und drücken Sie Strg+M. **Sie sollten sehen:** Die Tabelle
  bekommt einen neuen Fund vom Typ „Manuell“.
- [ ] **Alt-Kürzel:** Drücken Sie die Alt-Taste allein und halten Sie sie. **Sie sollten sehen:** Unterstriche unter den
  Buchstaben der Schaltflächen (Windows und FlatLaf zeigen sie erst nach Alt). Alt+S, Alt+A, Alt+M, Alt+K, Alt+R,
  Alt+T, Alt+Z und Alt+E lösen die jeweilige Schaltfläche aus (Alt+E beendet erst nach der Rückfrage, siehe S-10).
  **Alt+1 bis Alt+4** springen in den jeweiligen Bereich.
- [ ] **Tooltip an gesperrter Schaltfläche:** Starten Sie neu (oder wählen Sie „Zurücksetzen“) und zeigen Sie mit der Maus
  auf eine gesperrte Schaltfläche (z. B. „Für KI kopieren“ ohne Scan). **Sie sollten sehen:** einen kleinen Hinweis mit
  dem Grund (z. B. „Erst Text scannen.“). Nach dem Scan ist die Schaltfläche frei und der Hinweis verschwunden.

## S-9 Screenreader (optional)

Nur wenn Sie NVDA oder JAWS nutzen. Lesen Sie vorher den Hinweis zur Java Access Bridge im README
(Abschnitt „Barrierefreiheit“): Sie schaltet den Inhalt der Felder für jede Software mit Zugriff lesbar.

- [ ] `jabswitch -enable` ausführen, Screenreader und SkillShield neu starten. **Sie sollten hören:** Namen der Felder
  (z. B. „Eingabetext“), der Tabelle, der Spaltenköpfe und der Schaltflächen, wenn Sie mit Tab darüber laufen.
- [ ] Danach `jabswitch -disable`, wenn Sie die Bridge nicht dauerhaft brauchen.

## S-10 Beenden auf allen drei Wegen, Zwischenablage leer

Wiederholen Sie diesen Block **dreimal**: einmal mit dem **Schließen-Kreuz** des Fensters, einmal mit **Alt+F4**, einmal
mit der Schaltfläche **„Beenden“**.

- [ ] Beispieltext A scannen, „Für KI kopieren“ (Strg+Umschalt+C).
- [ ] Beenden wählen (Kreuz, Alt+F4 oder Schaltfläche). **Sie sollten sehen:** eine Rückfrage mit „Ja“ und „Abbrechen“.
- [ ] „Abbrechen“ wählen: Das Fenster bleibt offen, nichts ist verloren, die Zuordnung steht weiter in der Statusleiste.
- [ ] Beenden erneut wählen und „Ja“ wählen. **Sie sollten sehen:** Das Fenster verschwindet. Im Task-Manager
  (Strg+Umschalt+Esc, Reiter „Details“) läuft **kein** `java.exe`/`javaw.exe` von SkillShield mehr.
- [ ] Öffnen Sie Notepad und drücken Sie **Strg+V**. **Sie sollten sehen:** Es wird **nichts** eingefügt, die
  Zwischenablage ist leer.
- [ ] Beenden ohne vorherigen Scan: **Sie sollten sehen:** keine Rückfrage, das Fenster schließt sofort.

## S-11 Rechtsklick und Kopierwege

- [ ] Rechtsklick in Eingabefeld, Text für die KI und KI-Antwort. **Sie sollten sehen:** **kein** Kontextmenü mit
  „Kopieren/Ausschneiden“, das an der Logik des Programms vorbeiläuft (oder, falls doch eines erscheint: Kopieren
  funktioniert nur über das Programm, und nach dem Beenden ist die Zwischenablage leer). Notieren Sie, was erscheint.
- [ ] Markieren Sie in Bereich 3 („Text für die KI“) nur ein Wort und drücken Sie Strg+C. **Sie sollten sehen:** Es wird
  der **ganze** Text für die KI kopiert (Rückfrage nur, wenn kein Fund angewählt ist), nicht nur das Wort. Prüfen Sie es in
  Notepad mit Strg+V.
- [ ] Wählen Sie in der Tabelle eine Zeile und drücken Sie Strg+C. **Sie sollten sehen:** Es wird nichts kopiert (Notepad
  bekommt nichts Neues).

## S-12 AltGr-Eingaben auf deutscher Tastatur

Mit einer **deutschen** Tastatur (auch über Remote-Desktop oder Citrix, wenn Sie das dort nutzen). Klicken Sie ins
Eingabefeld und drücken Sie nacheinander:

- [ ] **AltGr+E** → es erscheint „€“.
- [ ] **AltGr+M** → es erscheint „µ“.
- [ ] **AltGr+Q** → es erscheint „@“.
- [ ] **AltGr+2** → „²“ und **AltGr+3** → „³“.
- [ ] **AltGr+7, +8, +9, +0** → „{“, „[“, „]“, „}“.
- [ ] **AltGr+ß** → „\“.
- [ ] **AltGr++** (AltGr und die Plus-Taste) → „~“.
- [ ] **AltGr+<** → „|“.

**Bei jeder dieser Eingaben sollten Sie sehen:** Es wird das Zeichen eingegeben, es erscheint **keine** Rückfrage zum
Beenden, es wird **keine** Markierung als Fund hinzugefügt und der Fokus springt in kein anderes Feld.

## S-13 Zwischenablage-Dialog und Fehlerfall (optional)

- [ ] Halten Sie die Zwischenablage mit einem anderen Programm gesperrt oder legen Sie ein Bild hinein, kopieren Sie mit
  SkillShield und wählen Sie „Zurücksetzen“. **Sie sollten sehen:** ggf. einen Dialog, dass die Zwischenablage nicht
  geleert werden konnte, mit „Erneut versuchen“ und „Trotzdem beenden“. Notieren Sie, wie sich das anfühlt.

## S-14 Spuren auf der Platte (Ordnervergleich)

Ziel: Nach einer Sitzung darf nichts Neues von SkillShield oder Java auf dem Rechner liegen.

- [ ] **Vorher** (vor dem Start): Öffnen Sie im Explorer nacheinander den Temp-Ordner (tippen Sie `%TEMP%` in die
  Adresszeile), Ihren Benutzerordner (auch die Unterordner `AppData\Local` und `AppData\Roaming`; ggf. „Ausgeblendete
  Elemente“ einblenden) und den Ordner mit `skillshield.jar`. Sortieren Sie jeweils nach „Änderungsdatum“ und merken
  Sie sich den neuesten Eintrag (oder machen Sie ein Foto).
- [ ] Führen Sie eine **vollständige Sitzung** durch: scannen, „Für KI kopieren“, Antwort einfügen, zurückübersetzen,
  dann „Beenden“ (mit „Ja“).
- [ ] **Nachher:** Sortieren Sie dieselben drei Ordner erneut nach Änderungsdatum. **Sie sollten sehen:** nichts Neues
  von SkillShield oder Java, insbesondere **keinen** Ordner `hsperfdata_…`, **keine** Datei `hs_err_pid….log` oder `hs_err_pid….mdmp` und **keine**
  Datei `.accessibility.properties`. (Andere Programme dürfen Neues anlegen; ignorieren Sie das.)

## S-15 Registry unverändert

- [ ] Öffnen Sie eine Eingabeaufforderung und geben Sie `reg query HKCU\Software\JavaSoft\Prefs /s` ein, **vor** dem
  Start und **nach** der Sitzung aus S-14. **Sie sollten sehen:** beide Male dieselbe Ausgabe (oder beide Male die
  Meldung, dass der Schlüssel nicht gefunden wurde). Ändert sich die Ausgabe, melden Sie das.

## S-16 Neustart merkt sich nichts

- [ ] Verschieben Sie das Fenster, ändern Sie die Größe, beenden Sie und starten Sie `SkillShield.cmd` erneut.
  **Sie sollten sehen:** Das Fenster öffnet sich wieder in der Standardgröße und an der Standardposition, nicht dort,
  wo Sie es zuletzt hatten.

## S-17 Zwischenablageverlauf (bekannte Grenze)

- [ ] Nur wenn der Zwischenablageverlauf von Windows eingeschaltet ist (Einstellungen, System, Zwischenablage):
  Kopieren Sie mit „Für KI kopieren“, beenden Sie das Programm und drücken Sie **Win+V**. Notieren Sie, ob der kopierte
  Text im Verlauf noch auftaucht. Das ist die im README dokumentierte Grenze und **kein Fehler** des Programms; wer
  den Verlauf nutzt, löscht ihn selbst.

## S-18 Start aus Ordner mit Leerzeichen und vom Netzlaufwerk

- [ ] Kopieren Sie `skillshield.jar` und `SkillShield.cmd` in einen Ordner, dessen Name ein Leerzeichen enthält (z. B.
  `Mein Test Ordner`), und doppelklicken Sie `SkillShield.cmd` dort. **Sie sollten sehen:** Das Fenster öffnet sich.
- [ ] Wenn möglich, dasselbe von einem Netzlaufwerk (Laufwerksbuchstabe). **Sie sollten sehen:** Das Fenster öffnet sich.
  (Windows kann bei Netzlaufwerken zuerst eine Sicherheitswarnung zeigen; notieren Sie das.)

---

**Wenn alles abgehakt ist:** Die Oberfläche ist aus Sicht der Sichtprüfung abgenommen. Offene Punkte (andere Anzeige,
fehlender Hinweis, unerwartetes Verhalten) bitte mit der Punkt-Nummer melden.
