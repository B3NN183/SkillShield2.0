# Anforderung: Gesperrte Kontextwörter mit Mehrfachtrennern und CR-Unsichtbar-LF

Feature-Kurzname: `kv-trenner-und-umbruch`

Status: Entwurf zur Freigabe. Fortsetzung von `kv-nummern-erkennung` und `kv-kontext-luecken` (beide fertig,
abgenommen). Alle Begriffe, Entscheidungen, Regeln und Konfidenzwerte dieser Vorgänger gelten weiter, soweit hier nicht
anders gesagt (insbesondere D-1 bis D-7 von `kv-kontext-luecken`, die Regel „laufender Kontext" aus T-7a und die
Konfidenzstufen aus Plan Kap. 6: SICHER ab 0,90, WAHRSCHEINLICH ab 0,70, sonst FRAGLICH). Betroffen ist nur
`skillshield-core`, Paket `de.skillshield.core.recognizer.kv`.

Das Feature hat zwei Teile: **Teil 1** (Grenze 15, CR + unsichtbares Zeichen + LF; kein Fehlalarm-Risiko) und **Teil 2**
(Grenze 14c, Mehrfachtrenner, nur für gesperrte Kontextwörter ab vier Buchstaben). Vorgabe des Nutzers: „Wenn es vermehrt
zu Fehlalarmen führt, sollten wir es lassen." Das gilt für Teil 2 (siehe D-8, Abbruchkriterium).

Schreibweise: `<gültige LANR>`, `<gültige IK>` stehen für je eine rechnerisch gültige synthetische Nummer aus
`SynthetischeKvNummern` (Präfix `00`), `<ungültige LANR>` und `<ungültige IK>` für je eine mit falscher Prüfziffer,
`<Nummer7>` für eine Ziffernfolge mit sieben Ziffern (Nullfolge oder `00`-Präfix), `<Zahl>` für eine beliebige Zahl mit
6 bis 12 Ziffern. In Tests werden die Texte per Verkettung mit diesen Konstanten gebildet; keine Nummer steht in diesem
Dokument. `KW` steht für ein beliebiges Kontextwort der jeweiligen Art. `UZ` steht für ein unsichtbares Zeichen im Sinne
des Bestands (Formatzeichen wie U+200B, U+00AD, U+2060, U+FEFF, U+200E, Variantenwähler U+FE00 bis U+FE0F). `CR` ist
U+000D, `LF` U+000A, `NBSP` U+00A0, `THIN` U+2009, `NNBSP` U+202F, `<Tab>` U+0009. „Leerraumzeichen der Sperrschrift"
sind genau: Leerzeichen, NBSP, THIN, NNBSP.

## Hintergrund (Ist-Stand laut Javadoc von `KvNumberScanner` und den Tests `KvSperrschriftTest`)

In zwei Fällen bleibt heute eine Nummer trotz eines erkennbaren Kontextworts im Klartext stehen:

1. **Grenze 15:** `KW:` CR UZ LF `<Nummer>`. Der Scanner zählt CR und LF als zwei Zeilenumbrüche, weil das
   unsichtbare Zeichen dazwischen steht; der Kontext erlischt (Leerzeile). Ohne das UZ (`KW:` CR LF `<Nummer>`) gibt es den
   Fund (0,80). Gilt auch hinter gesperrt geschriebenen Kontextwörtern. Festgehalten im Test `bekannteGrenze15_…`.
2. **Grenze 14c:** Gesperrt geschriebene Kontextwörter gelten heute nur mit genau einem Trenner zwischen den Buchstaben
   (D-1 von `kv-kontext-luecken`). Nicht erkannt werden `L. A. N. R. <gültige LANR>`, `L  A  N  R <gültige LANR>`
   (mehrere Leerzeichen, typisch für Text aus Screenshots/OCR und Blocksatz) und Tab zwischen den Buchstaben. Festgehalten
   in `bekannteGrenze14c_…`. Das Produkt verarbeitet ausdrücklich auch Screenshots.

## Entscheidungen (ohne Rückfrage, Richtung „im Zweifel schützen", Fehlalarme begrenzt und prüfbar)

- **D-1: CR + UZ + LF ist ein Zeilenumbruch (Teil 1).** Eine Folge aus CR, danach null oder mehr UZ, danach LF zählt als
  **ein** Zeilenumbruch (wie CR LF). Das gilt an allen Stellen, an denen der Scanner Zeilenumbrüche behandelt: Zähler seit
  Kontextwort und Leerzeilen-Erkennung, Abbruch von `direkt` (auch bei `I K`), Folge offener Segmente und Lücke vor
  nachgestelltem Kontextwort (inkl. Komma-Regel D-5 des Vorgängers), Ende einer Sperrfolge, Zeilenanfang-Merker (nummerierte
  Liste). Unverändert zwei Umbrüche bleiben: CR UZ CR, LF UZ LF, LF UZ CR, CR LF UZ LF, CR UZ LF UZ LF, CR + Leerraum/sichtbares
  Zeichen + LF sowie jede Kombination mit U+0085, U+2028, U+2029. Begründung: klarer Fehler ohne Fehlalarm-Risiko; ein
  unsichtbares Zeichen darf nie den Schutz verringern. Der Fundbereich ändert sich nicht; kein Zeichen des Textes wird verändert.
- **D-2: Erweiterte Trennerfolgen in gesperrter Schreibweise (Teil 2).** Zwischen zwei aufeinanderfolgenden Einzelbuchstaben
  einer Sperrfolge gilt je Lücke **eine** der Folgen:
  (a) genau ein Sperr-Trenner nach D-1 des Vorgängers (Bestand: Leerzeichen, NBSP, THIN, NNBSP, `.`, `-`, U+2011, U+2013);
  (b) **zwei oder drei** Leerraumzeichen der Sperrschrift, beliebig gemischt (`L  A`, `L` + NBSP + Leerzeichen + `A`);
  (c) ein `.` gefolgt von **einem bis drei** Leerraumzeichen der Sperrschrift (`L. A`, `L.  A`);
  (d) **genau ein** `<Tab>`.
  Die Lücken eines Wortes dürfen frei gemischt sein (`L. A N  R`, `L<Tab>A. N R`). Ein Trenner hinter dem letzten Buchstaben
  bleibt erlaubt (`L. A. N. R.`) und gehört nicht zum Vergleich. UZ werden an jeder Stelle der Folge übergangen und zählen nicht
  zu den drei Leerraumzeichen, auch nicht zwischen zwei Zeichen einer Trennerfolge (`.` UZ Leerzeichen). Nicht zulässig (Folge
  endet, kein Kontextwort): Zeilenumbruch, Komma, Semikolon, Doppelpunkt, `/`, `_`, vier oder mehr Leerraumzeichen, zwei oder mehr
  Tabs, Tab zusammen mit Leerzeichen, Leerzeichen vor dem Punkt (`L .A`), zwei Punkte, `-` oder U+2013 mit zusätzlichem Leerraum
  (`L - A`), jedes andere Zeichen. Begründung: Mehrere Leerzeichen und Punkt + Leerzeichen sind die Schreibweisen aus OCR,
  Blocksatz und Initialenform, Tab der Spaltenabstand von Tabellen- und Screenshot-Text. Die Obergrenze von drei Leerraumzeichen
  begrenzt Laufzeit und Fehlalarme; Zeilenumbruch, Komma, Schrägstrich und Unterstrich sind Gliederungszeichen, mit denen einzelne
  Buchstaben in Listen und Tabellen stehen. Bindestrich mit Leerraum ist auch ein Bereichszeichen (`I - K`) und bleibt Restgrenze.
- **D-3: Erweiterte Trenner gelten nur für gesperrte Kontextwörter ab vier Buchstaben.** Maßgeblich ist die **maximale** Folge von
  Einzelbuchstaben (alle Lücken nach D-2); sie ist ein Kontextwort, wenn die Buchstaben zusammengelesen (A–Z gefaltet) genau einem
  Kontextwort der Art mit **mindestens vier Buchstaben** entsprechen. Das Fehlalarm-Risiko ist gering, weil die Buchstabenfolge
  exakt ein Kontextwort ergeben muss und die Folge nicht durch weitere Einzelbuchstaben verlängert sein darf (`A. L. A. N. R.`,
  `L. A. N. R. X`, `L. A. N.` sind kein Kontextwort; Grenze 14e, D-2 des Vorgängers).
- **D-4: Das Zwei-Buchstaben-Wort bleibt von den erweiterten Trennern ausgeschlossen (Nutzervorgabe).** `I. K. <Nummer>`,
  `I  K <Nummer>` (mehrere Leerzeichen), `I<Tab>K <Nummer>` und jede andere Folge aus genau zwei Einzelbuchstaben mit einer
  Lücke nach D-2 (b) bis (d) ergeben **weiterhin keinen Fund**. Begründung: `I. K.` sieht exakt aus wie die Initialen einer
  Person (`Prof. I. K. Mustermann, Rechnung <Zahl>`); ein Fund würde bei Zahlen direkt hinter Initialen Fehlalarme erzeugen, und der
  Nutzer wünscht bei vermehrten Fehlalarmen ausdrücklich keine Erkennung. Das bestehende `I K`, `I.K.`, `I-K`, `I K:` (genau ein
  Trenner nach D-1 des Vorgängers, D-3 dort: nur unmittelbar folgende Nummer, höchstens 0,50) bleibt **unverändert wie heute**. Das
  Wort `I. K. N. R.` (vier Buchstaben, `iknr`) und `I. K. N. U. M. M. E. R.` fallen unter D-3 und werden erkannt. Die Lücke bleibt
  bewusst: eine Nummer hinter `I. K.` bleibt im Klartext (Restgrenze 14c).
- **D-5: Verhalten der erweiterten Formen = Verhalten der Vergleichsform.** Die **Vergleichsform** eines Textes entsteht, indem jede
  erweiterte Trennerfolge nach D-2 (b) bis (d) durch ein einzelnes Leerzeichen ersetzt wird. Für Zustand, Füllwörter, Nachsatzwort,
  Zeilenumbruch, Aufzählung, nachgestellten Kontext und Komma-Regel gelten für die erweiterte Form dieselben Regeln wie für die
  Vergleichsform; Fundstellen, Typ und Fundbereich sind gleich.
- **D-6: Konfidenz.** Deckel **0,80** (Stufe „wahrscheinlich"), also der Deckel der sauberen Sperrschrift, nicht niedriger. Ungültige
  Prüfziffer (0,50) und Längenabweichung (0,40) bleiben; nachgestellt mit Komma bleibt 0,60. Der Deckel hebt nie an. Begründung für
  nicht niedriger: Ein niedrigerer Deckel würde Nummern schwächer bewerten, die heute über ein nachgestelltes Kontextwort mit 0,80
  erkannt werden (`<Nummer> (L. A. N. R.)`, `L. A. N. R. <Nummer> (LANR)`); das widerspräche dem Bestandsschutz (D-7).
  Unterscheidbarkeit zur sauberen Sperrschrift in der Oberfläche wird nicht verlangt.
- **D-7: Bestandsschutz.** Alles, was heute erkannt wird, bleibt erkannt, mit mindestens gleicher Konfidenz; **keine neue Ausnahme**.
  Die einzige bestehende Ausnahme 14g (und die vom Differenzwerkzeug heute schon geführte Zusammenlegung auf Scanner-Ebene) bleibt
  wie sie ist. Bei laufendem Kontext gilt der Bestand (T-7a) unverändert: Sperrschrift ab vier Buchstaben wirkt dort nur, wenn kein
  Kontext (mehr) aktiv ist; es gibt keinen Deckel für Funde des laufenden Kontexts.
- **D-8: Fehlalarm-Grenze und Abbruchkriterium für Teil 2.** Das Feature darf auf Texten ohne gesperrtes Kontextwort ab vier
  Buchstaben der Art **keinen zusätzlichen Fund** erzeugen (AK-15, AK-16). Zeigt sich bei Architektur oder Umsetzung, dass Teil 2 nur mit
  spürbar mehr Fehlalarmen erreichbar ist (ein zusätzlicher Fund in den Prüfungen AK-15 oder AK-16, der nicht durch ein erkanntes
  Kontextwort ab vier Buchstaben nach D-3 gedeckt ist), wird Teil 2 **gestrichen**: Dann bleibt nur Teil 1 (Grenze 15) umgesetzt, die
  Grenze 14c bleibt offen, und die AK-7 bis AK-21 entfallen. Teil 1 ist davon unberührt.
- **D-9: Fundbereich und Quelle.** Wie im Bestand nur die Nummer, nie Kontextwort, Trenner oder Satzzeichen; die Quelle bleibt
  `ik`/`lanr` bzw. die Quellen der Prüfziffer- und Längenabweichung. Das Kontextwort samt Trennern bleibt im Text stehen.

## User Story

Als Mitarbeitende oder Mitarbeitender der KV (stellvertretend: die spätere Oberfläche und die Entwicklung, die den Fachkern
aufrufen) möchte ich, dass IK- und LANR-Nummern auch dann erkannt werden, wenn das Kontextwort mit mehreren Leerzeichen, mit Punkt
und Leerzeichen oder mit Tab gesperrt geschrieben ist (`L. A. N. R.`, `L  A  N  R`) oder wenn zwischen Kontextwort und Nummer ein
Zeilenumbruch CR + unsichtbares Zeichen + LF steht, damit auch in Text aus Screenshots, Blocksatz und kopierten Dokumenten keine
Nummer im Klartext an eine KI gelangt, ohne dass Initialen, Abkürzungen oder Aufzählungen mit Einzelbuchstaben zu zusätzlichen
Fehlalarmen führen.

## Akzeptanzkriterien

Jedes AK gilt, soweit nicht anders genannt, für LANR und IK gleichermaßen (mit den Kontextwörtern der jeweiligen Art mit mindestens
vier Buchstaben für Teil 2). „Genau ein Fund" heißt: Typ, Originalwert, Bereich (`text.substring(start, end).equals(originalValue)`),
Konfidenz und Stufe wie genannt. Ja/Nein je Beispiel.

### A. Teil 1, Grenze 15: CR + unsichtbares Zeichen + LF

- AK-1 (Grundfall): `KW:` CR UZ LF `<gültige Nummer>` liefert genau einen Fund über die Nummer mit derselben Konfidenz und Stufe wie
  `KW:` CR LF `<gültige Nummer>` (0,80, „wahrscheinlich"), je mit U+200B, U+00AD, U+FEFF, U+2060, U+200E und U+FE0F als UZ.
- AK-2 (mehrere UZ): `KW:` CR UZ UZ UZ LF `<gültige Nummer>` (z. B. U+200B, U+FEFF, U+00AD) liefert denselben Fund wie AK-1.
- AK-3 (gesperrte Kontextwörter): `L A N R:` CR UZ LF `<gültige LANR>` und `I K N R:` CR UZ LF `<gültige IK>` liefern je genau einen
  Fund 0,80. `I K:` CR UZ LF `<gültige IK>` liefert **keinen** Fund (ein Umbruch beendet `direkt`; wie `I K:` CR LF).
- AK-4 (zwei Umbrüche bleiben zwei, kein Fund): Nach `KW:` und vor `<gültige Nummer>` steht jeweils: CR UZ CR; LF UZ LF; LF UZ CR;
  CR LF UZ LF; CR UZ LF UZ LF; CR Leerzeichen LF; CR NBSP LF; CR U+0085; CR U+2028; CR UZ LF gefolgt von LF; CR UZ LF CR UZ LF.
- AK-5 (alle Stellen, D-1): Für die folgenden Texte ist die Fundliste mit CR UZ LF **gleich** der Fundliste mit CR LF an derselben
  Stelle (Typ, Bereich, Konfidenz): (a) Leerzeile: `KW:` CR UZ LF CR UZ LF `<Nummer>` kein Fund, `KW:` CR UZ LF `<Nummer>` Fund;
  (b) nachgestellt: `<Nummer>` CR UZ LF `KW` kein Fund; (c) Komma-Regel: `<Nummer>,` CR UZ LF `KW` kein Fund; (d) Aufzählung
  zeilenweise: `KW <Nummer>` CR UZ LF `<Nummer>` zwei Funde; (e) Sperrfolge: `L` CR UZ LF `A N R <gültige LANR>` kein Fund,
  `L A N R` CR UZ LF `<gültige LANR>` ein Fund; (f) nummerierte Liste: `KW:` CR UZ LF `1. <Nummer>` gleiche Funde wie mit CR LF.
- AK-6 (Invarianz, Eigenschaftstest): In allen Eigenschaftstexten der Vorgänger-Features, in denen UZ eingestreut werden, ist die Stelle
  zwischen CR und LF **nicht mehr ausgenommen**; die Fundliste bleibt gleich der des Textes ohne eingestreute UZ.

### B. Teil 2, Grenze 14c: Mehrfachtrenner in gesperrten Kontextwörtern ab vier Buchstaben

- AK-7 (Punkt + Leerzeichen, LANR, D-2c): Genau ein Fund Typ LANR über die Nummer, Konfidenz 0,80, „wahrscheinlich", bei
  `L. A. N. R. <gültige LANR>`, `L. A. N. R.: <gültige LANR>`, `l. a. n. r. <gültige LANR>`, `L. A. N. R <gültige LANR>` (ohne
  Schlusspunkt), `L.  A.  N.  R. <gültige LANR>` (Punkt + zwei Leerzeichen), `L.` + NBSP + `A.` + THIN + `N.` + NNBSP + `R. <gültige LANR>`.
- AK-8 (mehrere Leerraumzeichen, LANR, D-2b): Genau ein Fund 0,80 bei `L  A  N  R <gültige LANR>` (zwei Leerzeichen),
  `L   A   N   R <gültige LANR>` (drei Leerzeichen), `L` + NBSP + NBSP + `A` + NBSP + NBSP + `N` + NBSP + NBSP + `R`,
  `L` + Leerzeichen + NBSP + `A` + THIN + THIN + `N` + NNBSP + Leerzeichen + `R` (gemischt), jeweils gefolgt von Leerzeichen und `<gültige LANR>`.
- AK-9 (Tab, D-2d): Genau ein Fund 0,80 bei `L<Tab>A<Tab>N<Tab>R<Tab><gültige LANR>` und `L<Tab>A<Tab>N<Tab>R <gültige LANR>`.
- AK-10 (gemischte Formen, Schlusspunkt): Genau ein Fund 0,80 bei `L. A N  R <gültige LANR>`, `L.  A<Tab>N. R. <gültige LANR>`,
  `L.A. N R. <gültige LANR>`, `L. A-N R <gültige LANR>`.
- AK-11 (längere Kontextwörter und IK): Genau ein Fund 0,80 bei `A. R. Z. T. N. R. <gültige LANR>`,
  `A  R  Z  T  N  U  M  M  E  R <gültige LANR>`, `I. K. N. R. <gültige IK>`, `I  K  N  U  M  M  E  R <gültige IK>`,
  `I. K. N. U. M. M. E. R. <gültige IK>`, `L. A. N. R. N. R. <gültige LANR>` (gesperrt `lanrnr`) und `I<Tab>K<Tab>N<Tab>R <gültige IK>`.
- AK-12 (Prüfziffer und Länge, D-6): `L. A. N. R. <ungültige LANR>` und `I  K  N  R <ungültige IK>` liefern je einen Fund 0,50;
  `L. A. N. R. <Nummer7>` und `I. K. N. R. <Nummer7>` je einen Fund 0,40; Quellen wie im Bestand (`…-pruefziffer-ungueltig`,
  `…-laenge-abweichend`). Der Deckel hebt nie an.
- AK-13 (Zwei-Buchstaben-Wort bleibt ausgeschlossen, D-4, nur IK): **Kein** Fund bei `I. K. <gültige IK>`, `I. K.: <gültige IK>`,
  `i. k. <gültige IK>`, `I. K <gültige IK>`, `I  K <gültige IK>`, `I   K <gültige IK>`, `I<Tab>K <gültige IK>`, `I.  K. <gültige IK>`,
  `I` + NBSP + NBSP + `K <gültige IK>`, `Dr. I. K. <gültige IK>`, `Prof. I. K. <gültige IK>`, `I. K.-Nr. <gültige IK>`,
  `I. K. Mustermann <gültige IK>`, `I. K. Mustermann, Rechnung <gültige IK>`, `Prof. I. K. Mustermann, Rechnung <Zahl>`,
  `<gültige IK> (I. K.)`, `<gültige IK>, I  K`. Der Bestand mit genau einem Trenner bleibt unverändert: `I K <gültige IK>`,
  `I.K. <gültige IK>`, `I-K <gültige IK>` liefern weiter genau einen Fund 0,50 („fraglich"); `I K: <gültige IK>` ebenso;
  `I K Mustermann <gültige IK>` und `I K der Praxis <gültige IK>` weiter keinen.
- AK-14 (Fehlalarmschutz durch maximale Folge, D-3): Kein Fund bei `A. L. A. N. R. <gültige LANR>`, `L. A. N. R. X <gültige LANR>`,
  `L. A. N. <gültige LANR>`, `L. A. N. X. R. <gültige LANR>`, `L. A. N. R. L. A. N. R. <gültige LANR>` (unmittelbar wiederholt, 14d),
  `LA. NR. <gültige LANR>` und `L. ANR <gültige LANR>` (Teilgruppen, 14a), `Herr A. L. A. N. R. <gültige LANR>`,
  `I. K. N. <gültige IK>`, `A. I. K. N. R. X <gültige IK>`, `L. A. N. R.` ohne Nummer und am Textende.
- AK-15 (ausdrückliches Fehlalarm-AK, festgelegter Satz von Nicht-Fällen, D-8): Für jeden der folgenden Texte (je einmal mit
  `<gültige LANR>`, einmal mit `<gültige IK>`, einmal mit `<Zahl>` direkt dahinter, einmal ohne Zahl) liefert das Feature **keinen
  zusätzlichen Fund** gegenüber dem Stand vorher (eingefrorene Kopie; gleiche Fundliste): Initialen (`I. K.`, `Prof. I. K. Mustermann`,
  `Dr. M. A. Beispiel`, `J. R. R.`), Aufzählungen mit Einzelbuchstaben (`a) b) c)`, `a. b. c.`, `A, B, C`, `Teil I K L`,
  `Anlage A  B  C`), Abkürzungen (`z. B.`, `u. a.`, `i. d. R.`, `d. h.`, `Dr. med.`, `e. V.`, `u. v. m.`, `i. V. m.`, `m. E.`, `z. T.`),
  Tabellen- und Formularzeilen mit Einzelbuchstaben (`A<Tab>B<Tab>C<Tab>D`, `X  Y  Z`, `Ja  Nein  k. A.`, `Spalte L<Tab>Spalte A`),
  Text mit doppelten und dreifachen Leerzeichen (`Rechnung  Nummer  <Zahl>`, `Der  Arzt  hat  <Zahl>  gesehen`), die Buchstabenfolgen
  `L A N`, `I K E A`, `L. A. N.`, `I K N`, `N. R. L. A.`, `R. N. A. L.`. Ein „zusätzlicher Fund" ist jeder Fund, der im alten Ergebnis nicht
  mit gleichem Typ und Bereich enthalten ist.
- AK-16 (Fehlalarm im Zufallsvergleich alt gegen neu, D-8): In einem Zufallsvergleich (fester Seed, mindestens 100 000 Texte) auf Texten,
  die **kein** gesperrtes Kontextwort der Art enthalten (die maximale Einzelbuchstabenfolge nach D-2 ergibt kein Kontextwort ab vier
  Buchstaben; erzeugt aus Wörtern, Initialen, Einzelbuchstaben, Punkten, Leerzeichen, Tabs, Zahlen, Nummern, Zeilenumbrüchen), ist die
  Zahl der Funde, die das neue Ergebnis zusätzlich zum alten enthält, **genau 0**. Dasselbe gilt für alle Texte der bestehenden Tests
  und des Roundtrip-Korpus, die kein gesperrtes Kontextwort ab vier Buchstaben mit erweiterter Trennerfolge enthalten. Zusätzliche
  Funde auf Texten **mit** gesperrtem Kontextwort ab vier Buchstaben nach AK-7 bis AK-12 sind erlaubt.
- AK-17 (nicht zulässige Trenner, D-2): Kein Fund bei: vier Leerzeichen (`L    A    N    R <gültige LANR>`), vier Leerraumzeichen gemischt,
  `L<Tab><Tab>A<Tab><Tab>N<Tab><Tab>R <gültige LANR>`, `L<Tab> A<Tab> N<Tab> R <gültige LANR>`, Zeilenumbruch zwischen den Buchstaben
  (`L. A.` + LF + `N. R. <gültige LANR>`, `L` + CR LF + `A N R …`), `L, A, N, R <gültige LANR>`, `L; A; N; R`, `L/A/N/R`, `L_A_N_R`,
  `L: A: N: R`, `L .A .N .R`, `L.. A.. N.. R..`, `L - A - N - R <gültige LANR>`, `L – A – N – R` (U+2013 mit Leerzeichen).
- AK-18 (volle Kontextregeln, D-5): `L. A. N. R. des Behandlers <gültige LANR>` liefert einen Fund 0,80; mit vier Füllwörtern keinen.
  `L. A. N. R. <gültige LANR>, <gültige LANR>` liefert zwei Funde (Aufzählung, je 0,80). `L. A. N. R.:` + LF + `<gültige LANR>` liefert
  einen Fund 0,80; mit Leerzeile dazwischen keinen. `L. A. N. R. Nr. <gültige LANR>`, `L. A. N. R.-Nummer <gültige LANR>` und
  `I. K. N. R.-Nr. <gültige IK>` liefern je einen Fund 0,80.
- AK-19 (nachgestellt, laufender Kontext, Bestand): `<gültige LANR> (L. A. N. R.)` und `<gültige LANR> L  A  N  R` liefern je einen
  Fund 0,80; `<gültige LANR>, L. A. N. R.` einen Fund 0,60; `<gültige IK> (I. K. N. R.)` einen Fund 0,80. Bei laufendem Kontext gilt
  der Bestand: `LANR: L. A. N. R. <gültige LANR>` verhält sich wie `LANR: L A N R <gültige LANR>`; `IK: Praxis I. K. Nr <gültige IK>`
  bleibt kein Fund (wie heute, D-4).
- AK-20 (unsichtbare Zeichen): Fügt man in einen der Texte aus AK-7 bis AK-11 an beliebiger Stelle direkt an einem Buchstaben (davor oder
  dahinter) oder zwischen zwei Zeichen einer Trennerfolge (`.` UZ Leerzeichen) ein oder mehrere UZ ein, bleibt die Fundliste gleich. Ein
  UZ **zwischen** zwei Buchstaben ohne Trenner (`L` UZ `A. N. R.`) bildet eine Teilgruppe (14a) und liefert wie im Bestand keinen Fund.
  Bestandsfälle bleiben unverändert (`L` + U+200B + `A` + U+200B + `N` + U+200B + `R` mit Leerzeichen und Nummer: 0,92 bzw. 0,95).
- AK-21 (Bereich, Quelle und Gleichheit mit der Vergleichsform, D-5, D-9): In AK-7 bis AK-20 umfasst der Fund genau die Nummer, nie
  Buchstaben, Trenner oder Satzzeichen des Kontextworts; `L. A. N. R. <gültige LANR>.` ergibt keinen Punkt im Fund; die Quelle ist
  `ik`/`lanr` bzw. die der Prüfziffer- oder Längenabweichung. Für Texte mit erweiterten Trennerfolgen sind Typ, Bereich und Zahl der
  Funde gleich denen der Vergleichsform; die Konfidenz ist das Minimum aus 0,80 und der Konfidenz der Vergleichsform (Eigenschaft,
  fester Seed).

### C. Nichtfunktionale Anforderungen und Bestandsschutz

- AK-22 (kein Verlust, Differenz alt gegen neu, D-7): Für alle Texte der bestehenden Tests, des Roundtrip-Korpus, der Fuzz-Läufe und der
  Eigenschaftstests dieses Features gilt: Jeder Fund des Stands vor diesem Feature ist im neuen Ergebnis mit gleichem Typ und Bereich
  vorhanden und hat mindestens die gleiche Konfidenz. Ausnahmen sind nur die, die das Differenzwerkzeug heute schon führt (14g,
  Zusammenlegung auf Scanner-Ebene); keine neue. Verglichen wird gegen die eingefrorene Kopie des Vorgängerstands.
- AK-23 (Umstellung bestehender Tests): Alle bestehenden Tests laufen grün. Nur die Tests, die Grenze 14c und 15 als „kein Fund"
  festhalten, werden umgestellt, je Assertion einzeln im Bericht genannt: `bekannteGrenze15_…`; in `bekannteGrenze14c_…` die Anteile für
  Wörter ab vier Buchstaben mit `. `, mehreren Leerzeichen und Tab (jetzt Fund); die Anteile für das Zwei-Buchstaben-Wort (`I. K. <IK>`,
  `I  K <IK>`, `I<Tab>K <IK>`) und der Zeilenumbruch bleiben kein Fund. **Nicht** umgestellt werden `ak6_…` (Initialen, Doppeltrenner,
  Tab). Für jeden weiteren Test, den die Differenz als geändert meldet, gilt dasselbe.
- AK-24 (Eigenschaftstest, fester Seed, mindestens 1 000 Texte je Teil): (a) Texte aus Blöcken „gesperrtes Kontextwort ab vier Buchstaben mit
  zufälligen Trennerfolgen nach D-2 (jede Lücke einzeln gewürfelt), zufällige Schreibweise, optional Schlusspunkt, zufällig eingestreute UZ,
  Direktzeichen, Nummer" ergeben genau diese Funde mit den erwarteten Konfidenzen; (b) Texte mit zufälligen CR/UZ/LF-Folgen: der Befund
  folgt der Zählung aus D-1 (ein Umbruch für CR UZ* LF, sonst zwei) gegen eine unabhängige Referenz im Test; (c) Rauschtexte ohne
  Kontextwort, auch mit Einzelbuchstabenfolgen, Initialen, Tabs, mehreren Leerzeichen und Nummern, enthalten keinen Fund; (d) Gegenproben
  aus AK-13, AK-14, AK-17 werden zufällig aus denselben Bausteinen erzeugt und liefern keinen Fund; (e) die Gleichheit mit der
  Vergleichsform (AK-21).
- AK-25 (Roundtrip und Fuzz): Der Roundtrip-Korpus und der Fuzz-Test (beide um Texte mit den neuen Trennerfolgen und CR UZ LF, auch in Mischung
  mit den Vorgänger-Formen, erweitert; fester Seed) liefern nach Pseudonymisierung aller Funde und Rückübersetzung zeichengleich den
  Originaltext; im pseudonymisierten Text steht keine Ziffernkette mit 6 oder mehr Ziffern aus einer als Fund erkannten Nummer. Die
  Schutzgarantie bleibt belegt; der Zähler „Block ohne Scan-Abdeckung" des Fuzz-Teils 1c ist 0 für die neuen Formen.
- AK-26 (Laufzeit, Grenzlast): Für jede der folgenden Eingaben mit 1 000 000 Zeichen braucht der Scan mit `TextScanner.standard()`,
  Pseudonymisierung und Rückübersetzung je weniger als 2 Sekunden (nach Aufwärmlauf) und liefert die **exakte** erwartete Fundzahl (Formel mit
  Rechenweg im Test): (a) `L. ` wiederholt; (b) `I.  K.  ` wiederholt ohne Nummer; (c) `L` + 1 000 000 Leerzeichen; (d) `L` + `.  A` wiederholt;
  (e) `L. A. N. R. <Nullnummer> ` wiederholt (mehr als 40 000 Funde); (f) `L<Tab>A<Tab>N<Tab>R<Tab><Nullnummer> ` wiederholt;
  (g) `LANR:` CR UZ LF `<Nullnummer>` Leerzeichen wiederholt; (h) CR gefolgt von 1 000 000 UZ, und `CR UZ LF` wiederholt ohne Kontextwort;
  (i) `L    A    N    R ` (vier Leerzeichen) wiederholt, kein Fund; (j) `A. ` wiederholt; (k) `I. K. <Nullnummer> ` wiederholt, kein Fund. Der
  Verlauf ist linear, ohne regulären Ausdruck, ohne Backtracking und ohne unbegrenzten Vorgriff (Leerraumzeichen je Lücke und Vorgriff
  begrenzt, D-2); der Linearitätsbeleg im Javadoc von `KvNumberScanner` ist fortgeschrieben.
- AK-27 (Testdaten): Keine echten Nummern. Neunstellige Ziffernliterale stehen nur in `SynthetischeKvNummern.java` (Präfix `00`); der Test auf
  synthetische Testdaten bleibt grün; Laufzeittexte verwenden Nullfolgen oder `00`-Nummern. Diese Anforderung enthält keine Ziffernkette ab
  neun Stellen.
- AK-28 (Offline, kein Klartext, keine KI): Keine neue Abhängigkeit, kein Netzwerk-, DNS- oder Dateizugriff, keine verbotene Klasse
  (`ArchitekturRegelnTest` grün, Positivliste unverändert). `toString()`, Meldungen und Ausnahmen enthalten keine Nummer; der
  Klartext-Freiheits-Test bleibt grün, es wird nichts geloggt. Die Erkennung ist regelbasiert und deterministisch; Funde sind nach Position
  sortiert, überlappungsfrei, jeder Fund erfüllt die Teilstring-Invariante.
- AK-29 (Mensch entscheidet, umkehrbar, strukturerhaltend): Alle neuen Funde werden vom Scan geliefert, aber nur ersetzt, wenn der Aufrufer sie
  übergibt; ohne Übergabe bleibt der Text unverändert (Prüfung an `L. A. N. R. <gültige LANR>` und `L A N R:` CR UZ LF `<gültige LANR>`).
  Kontextwort, Trenner und Zeilenumbruch bleiben im pseudonymisierten Text zeichengleich stehen.
- AK-30 (Dokumentation nachgeführt): Die Javadoc von `KvNumberScanner` (Zeichenklassen, Zeilenumbruch, Trennerfolgen, Konfidenz, Linearität)
  ist um D-1 bis D-9 ergänzt; die bekannten Grenzen 15 und 14c sind als geschlossen gekennzeichnet (14c mit der Restgrenze Zwei-Buchstaben-Wort);
  die Restgrenzen der Abgrenzung stehen je als Test `bekannteGrenze…` fest (z. B. `bekannteGrenze14cRest_…` für `I. K.`, vier Leerzeichen, zwei
  Tabs, Bindestrich mit Leerraum, Zeilenumbruch, Komma, `/`, `_`; `bekannteGrenze15Rest_…` für CR + Leerraum + LF und CR + U+0085). Wird
  Teil 2 nach D-8 gestrichen, bleibt 14c als offen gekennzeichnet.

## Abgrenzung

Nicht Teil dieses Features:
- **Das Zwei-Buchstaben-Wort mit erweiterten Trennern** (`I. K.`, `I  K`, `I<Tab>K`): bewusst kein Fund (D-4, Nutzervorgabe). Das bestehende
  `I K`, `I.K.`, `I-K` mit einem Trenner bleibt unverändert.
- **Erkennung ohne Kontextwort (E-2)**: Eine Nummer ohne erkennbares Kontextwort bleibt ohne Fund. Keine weiteren Kontextwörter, keine
  Wortlisten, keine Schreibfehler- oder Ähnlichkeitssuche. **BSNR, KVNR** und jede weitere Kennnummer.
- **Trennerfolgen außerhalb von D-2**: mehr als drei Leerraumzeichen, zwei oder mehr Tabs, Tab mit Leerzeichen, Zeilenumbruch, Komma,
  Semikolon, Doppelpunkt, `/`, `_`, Bindestrich oder Halbgeviertstrich mit Leerraum, Leerzeichen vor dem Punkt (Restgrenze 14c).
- **Zeilenumbruch-Kombinationen außerhalb von D-1**: CR + Leerraum + LF, jede Kombination mit U+0085, U+2028, U+2029 (Restgrenze 15).
- **Die übrigen Grenzen**: 12R, 14a, 14b, 14d, 14e, 14f, 14g (unverändert), die Restformen nach T-7a und die Grenzen 1 bis 11 und 13 des ersten
  Features. Ihr Verhalten ändert sich nicht.
- **Änderungen** an Pseudonymisierung, Rückübersetzung, Konfliktauflösung, Escaping, Prüfziffer und an Konfidenzwerten des Bestands.
- **Oberfläche**, Darstellung der Stufe „fraglich", Dateiformate, OCR selbst, Abbruch eines Scans, Thread-Sicherheit.

## Bezug zu Plan.md

- Kap. 2: Grundprinzipien (offline, keine KI im Produkt; Mensch entscheidet; umkehrbar: Roundtrip zeichengenau; strukturerhaltend: Kontextwort,
  Trenner und Umbruch bleiben stehen).
- Kap. 6: Erkennungs-Engine, KV-spezifische Erkenner (LANR, IK), Konfidenzstufen. Kap. 7 und 8: Pseudonymisierung und Rückübersetzung
  (unverändert genutzt). Kap. 11: kein Netzwerk, kein Klartext in Logs. Kap. 13: Roundtrip, Fuzzing, synthetische Testdaten, Laufzeit.
  Screenshots (Text aus OCR) als Eingabequelle.
- Vorgänger: `docs/features/kv-kontext-luecken/anforderung.md` (D-1 bis D-7, AK-6, AK-12, AK-22 bis AK-24) und `architektur.md` (Kap. 3, Kap. 11
  mit Grenzen 14a bis 14h, Restformen nach T-7a und 15, Kap. 13).

## Offene Fragen

Keine fachlichen Fragen an den Nutzer; alles Fachliche ist in D-1 bis D-9 entschieden. Technisch für den architect (ohne Änderung der
Akzeptanzkriterien):
1. **Fehlalarm-Nachweis (D-8, AK-15, AK-16):** Wie wird der Vergleich alt gegen neu auf zufälligen Texten ohne Kontextwort ab vier Buchstaben
   technisch erzeugt und ausgewertet (Generator, eingefrorene Kopie)? Der architect nennt in der Architektur, bei welchem Befund Teil 2 nach D-8
   gestrichen wird, und trennt die Tasks so, dass Teil 1 (Grenze 15) ohne Teil 2 auslieferbar ist.
2. **UZ zwischen zwei Zeichen einer Trennerfolge (AK-20):** Im Bestand bildet ein UZ zwischen zwei Nicht-Buchstaben einen eigenen, die Folge
   beendenden Lauf. AK-20 verlangt, dass es hier übergangen wird. Die Mittel legt der architect fest; er meldet, falls der Aufwand
   unverhältnismäßig ist.
3. Ob die Fundquellen intern zusätzlich markiert werden, ist Sache des architect, solange die Quelle im Fund `ik`/`lanr` bzw. die bekannten
   Prüfziffer- und Längenquellen bleibt.
