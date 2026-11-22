# Anforderung: Lücken im Kontext der KV-Nummern-Erkennung schließen

Feature-Kurzname: `kv-kontext-luecken`

Status: Entwurf zur Freigabe. Fortsetzung von `kv-nummern-erkennung` (fertig, abgenommen). Alle Begriffe, Entscheidungen
(E-1, E-2), Regeln (R-1 bis R-4) und Konfidenzwerte dieses Vorgängers gelten weiter, soweit hier nicht anders gesagt.
Betroffen ist nur `skillshield-core`, Paket `de.skillshield.core.recognizer.kv`.

Schreibweise: `<gültige LANR>`, `<gültige IK>` stehen für je eine rechnerisch gültige synthetische Nummer aus
`SynthetischeKvNummern` (Präfix `00`), `<ungültige LANR>` und `<ungültige IK>` für je eine mit falscher Prüfziffer. In
Tests werden die Texte per Verkettung mit diesen Konstanten gebildet; keine Nummer steht in diesem Dokument. `KW` steht
für ein beliebiges Kontextwort der jeweiligen Art. `NBSP` ist U+00A0, `THIN` U+2009, `NNBSP` U+202F.

## Hintergrund (Ist-Stand, im Code geprüft)

Die Erkennung verlangt ein Kontextwort (E-2, bleibt). In zwei Fällen bleibt eine Nummer heute trotz eines erkennbaren
Kontextworts im Klartext stehen:

1. **Grenze 14: gesperrt geschriebenes Kontextwort.** `L A N R <gültige LANR>`, `I K: <gültige IK>`, `I.K. <gültige IK>`,
   `L-A-N-R <gültige LANR>`: Jeder Buchstabe ist ein eigenes Wort und zählt als Füllwort, das Kontextwort wird nicht
   gefunden. Schon heute erkannt (Bestand, R-1, darf nicht schlechter werden): Buchstaben, zwischen denen nur
   unsichtbare Zeichen stehen (Formatzeichen wie U+200B, U+00AD, U+2060, U+FEFF, U+200E sowie Variantenwähler
   U+FE00 bis U+FE0F), z. B. `L` + U+200B + `A` + U+200B + `N` + U+200B + `R`. **Lücke** sind alle Trenner, die ein
   sichtbares Zeichen oder Leerraum sind: Leerzeichen, NBSP, THIN, NNBSP, `.`, `-` und ähnliche.
2. **Grenze 12: Komma oder Semikolon zwischen Nummer und nachgestelltem Kontextwort.** `<gültige IK>, IK` und
   `<gültige LANR>; LANR` liefern heute keinen Fund, weil nach der Regel R-2 zwischen letzter Nummer und Kontextwort nur
   Leerraum und höchstens eine öffnende Klammer stehen dürfen (`<Nummer> (IK)` wird erkannt).

## Entscheidungen (ohne Rückfrage, Richtung „möglichst viel schützen“, Fehlalarme begrenzt)

- **D-1: Zulässige Trenner in gesperrter Schreibweise.** Zwischen zwei Einzelbuchstaben genau ein Zeichen aus:
  Leerzeichen, NBSP, THIN, NNBSP, `.`, `-`, U+2011 (geschützter Bindestrich), U+2013 (Halbgeviertstrich). Die Trenner
  dürfen innerhalb eines Wortes gemischt vorkommen (`L.A-N R`). Nach dem letzten Buchstaben ist ein Punkt erlaubt
  (`I.K.`). Nicht zulässig: Tab, Zeilenumbruch, zwei Trenner hintereinander (`L  A N R`), Trenner außerhalb der Liste,
  Buchstaben- oder Ziffernzeichen dazwischen. Begründung: Das entspricht den Gruppentrennern der Ziffernketten und den
  Nachsatztrennern des Vorgängers; Tab und Zeilenumbruch sind Spalten- bzw. Zeilentrenner und würden zu viele
  Fehlalarme erzeugen (Tabellenzellen mit einzelnen Buchstaben).
- **D-2: Was eine gesperrte Schreibweise ist.** Eine Folge von mindestens zwei Einzelbuchstaben (ein Einzelbuchstabe ist
  ein Buchstabe, an den auf beiden Seiten kein weiterer Buchstabe grenzt), je durch genau einen zulässigen Trenner
  verbunden. Sie ist ein Kontextwort, wenn die Buchstaben zusammengelesen (ohne Trenner, A–Z ohne Beachtung der
  Groß-/Kleinschreibung, wie im Bestand) genau einem Kontextwort der Art entsprechen: `lanr`, `lanrnr`, `arztnummer`,
  `arztnr` bzw. `ik`, `iknr`, `iknummer`, `institutionskennzeichen`. Maßgeblich ist immer die **maximale** Folge von
  Einzelbuchstaben: `I K N` ergibt kein Kontextwort (auch nicht `I K`), `A L A N R` und `L A N R X` ebenso nicht. Eine
  Teilgruppe aus mehreren Buchstaben (`LA NR`, `L ANR`) zählt nicht (bekannte Grenze). Das Vorwort `Arzt` + `Nr` wird
  nicht gesperrt geschrieben erkannt (bekannte Grenze); `arztnr` und `arztnummer` als Ganzes in Sperrschrift schon.
  Ein Nachsatzwort (`Nr`, `Nummer`) hinter dem gesperrten Kontextwort ist normal geschrieben (`I K-Nr.`, `L A N R Nr.`)
  und wird wie im Bestand behandelt; gesperrt geschriebenes Nachsatzwort ist nicht Teil dieses Features.
  Unsichtbare Zeichen (R-1) innerhalb der Folge werden wie im Bestand übergangen, auch neben einem Trenner.
- **D-3: Fehlalarmbegrenzung für das Zwei-Buchstaben-Wort (`I K`, `I.K.`, `I-K`).** Ein gesperrt geschriebenes
  Kontextwort aus genau zwei Buchstaben gilt **nur für die unmittelbar folgende Nummer** (Zustand „direkt“ des Bestands:
  zwischen Wort und Nummer nur Leerraum, Satzzeichen wie `:` `=`, und ein normal geschriebenes Nachsatzwort). Es gibt
  keine Füllwörter, keine Aufzählung dahinter und keinen nachgestellten Kontext (`<Nummer> (I K)`). Die Konfidenz ist
  höchstens 0,50 (Stufe „fraglich“). Begründung: Initialen (`I. K. Mustermann`), Abkürzungen und Aufzählungen
  einzelner Buchstaben kommen häufig vor; so kann ein einzelnes „I K“ nie Zahlen in weiter Ferne markieren.
- **D-4: Gesperrte Wörter ab drei Buchstaben (`L A N R`, `I K N R`, `A R Z T N R`, …).** Es gelten alle Kontextregeln des
  Bestands (Füllwörter bis 3, Zeilenumbruch, Aufzählung, nachgestellt, Komma-Regel aus D-5). Die Konfidenz ist
  höchstens 0,80 (Stufe „wahrscheinlich“, nie „sicher“), weil die Schreibweise ungewöhnlich ist. Ungültige Prüfziffer
  (0,50) und Längenabweichung (0,40) bleiben wie im Bestand. Begründung: Vier Einzelbuchstaben in dieser Reihenfolge sind
  kaum Zufall, das Fehlalarmrisiko ist klein.
- **D-5: Komma/Semikolon vor nachgestelltem Kontextwort.** Zwischen der letzten offenen Nummer und dem Kontextwort darf
  zusätzlich **genau ein** `,` oder `;` stehen, mit beliebigem Leerraum (ohne Zeilenumbruch) davor und danach und
  weiterhin höchstens einer öffnenden Klammer danach (`<Nummer>, (IK)`). Gilt für jede Nummernform des Bestands
  (neun Ziffern, Längenabweichung, mit Trennern) und für die ganze Folge offener Nummern aus R-2
  (`N1, N2, KW`). Die Konfidenz ist bei gültiger Prüfziffer **0,60** (Stufe „fraglich“, nicht 0,80 wie beim
  nachgestellten Kontextwort ohne Komma), weil `Nummer, IK …` auch ein Satzbau mit einem fremden Kontextwort sein kann;
  ungültige Prüfziffer 0,50 und Längenabweichung 0,40 bleiben. Nicht zulässig bleiben: zwei Zeichen (`,,`, `,;`),
  Komma plus `und` oder `/`, Wort dazwischen, Zeilenumbruch dazwischen, andere Satzzeichen (`:`, `.`).
  Begründung: Der Auftrag nennt nur Komma und Semikolon; `und` und `/` bleiben die bekannte Grenze 12 (Abgrenzung).
- **D-6: Konfidenz ist Obergrenze, nie Anhebung.** Die Deckel aus D-3, D-4 und D-5 wirken als Minimum mit dem
  Bestandswert (gültig direkt 0,95/0,92, gültig sonst 0,80, ungültig 0,50, Länge 0,40). Kein Fall wird dadurch höher
  bewertet als heute.
- **D-7: Fundbereich.** Wie im Bestand nur die Nummer (Ziffern samt inneren Trennern), nie das gesperrte Kontextwort,
  nie das Komma. Das Kontextwort samt Sperrschrift bleibt im Text stehen (`L A N R [[LANR_1]]`).

## User Story

Als Mitarbeitende oder Mitarbeitender der KV (stellvertretend: die spätere Oberfläche und die Entwicklung, die den
Fachkern aufrufen) möchte ich, dass IK- und LANR-Nummern auch dann erkannt werden, wenn das Kontextwort gesperrt
geschrieben ist (`L A N R`, `I.K.`) oder die Nummer vor dem Kontextwort mit Komma oder Semikolon steht
(`<Nummer>, IK`), damit in diesen häufigen Schreibweisen keine Nummer im Klartext an eine KI gelangt, ohne dass
beliebige einzelne Buchstaben oder Zahlen zu Fehlalarmen führen.

## Akzeptanzkriterien

Jedes AK gilt, soweit nicht anders genannt, für LANR und IK gleichermaßen (mit den Kontextwörtern der jeweiligen Art).
„Genau ein Fund“ heißt: Typ, Originalwert, Bereich (`text.substring(start, end).equals(originalValue)`), Konfidenz und
Stufe wie genannt. Ja/Nein je Beispiel.

### A. Gesperrt geschriebene Kontextwörter (Grenze 14)

- AK-1 (LANR, Grundfall): `L A N R <gültige LANR>`, `L A N R: <gültige LANR>`, `l a n r <gültige LANR>` liefern je
  genau einen Fund Typ LANR über die Nummer; Konfidenz 0,80, Stufe „wahrscheinlich“ (D-4).
- AK-2 (Trenner, LANR): Genau ein Fund Typ LANR über die Nummer, Konfidenz 0,80, bei je `L.A.N.R.`, `L.A.N.R`,
  `L-A-N-R`, `L` + NBSP + `A` + NBSP + `N` + NBSP + `R`, die gleiche Form mit THIN, mit NNBSP, mit U+2011, mit U+2013,
  gemischt (`L.A-N R`), jeweils gefolgt von Leerzeichen und `<gültige LANR>`.
- AK-3 (längere Kontextwörter): `A R Z T N R <gültige LANR>`, `A R Z T N U M M E R <gültige LANR>` (LANR),
  `I K N R <gültige IK>`, `I K N U M M E R <gültige IK>`,
  `I N S T I T U T I O N S K E N N Z E I C H E N <gültige IK>` (IK) liefern je genau einen Fund, Konfidenz 0,80.
  `L A N R N R <gültige LANR>` (gesperrt `lanrnr`) ebenso.
- AK-4 (IK, Zwei-Buchstaben-Wort, Grundfall, D-3): `I K <gültige IK>`, `I K: <gültige IK>`, `I.K. <gültige IK>`,
  `I.K.: <gültige IK>`, `I-K <gültige IK>`, `i k <gültige IK>` und die Trennerformen aus AK-2 (NBSP, THIN, NNBSP,
  U+2011, U+2013) liefern je genau einen Fund Typ IK über die Nummer, Konfidenz **0,50**, Stufe „fraglich“, auch bei
  gültiger Prüfziffer.
- AK-5 (Zwei-Buchstaben-Wort bleibt auf die unmittelbar folgende Nummer begrenzt, D-3): Kein Fund bei
  `I K der Praxis <gültige IK>` (Füllwörter), `I K <gültige IK>` plus ` und <gültige IK>` ergibt nur **einen** Fund
  (die erste Nummer; die zweite nicht), `<gültige IK> (I K)` und `<gültige IK> I K` (nachgestellt), `I K` + Zeilenumbruch +
  `<gültige IK>`. Ein normal geschriebenes Nachsatzwort ist erlaubt: `I K-Nr. <gültige IK>` und `I K Nr. <gültige IK>`
  liefern genau einen Fund (Konfidenz 0,50).
- AK-6 (Fehlalarmschutz „I K“, D-2/D-3): Kein Fund bei: `Prof. I. K. Mustermann` gefolgt in derselben Zeile erst nach
  mehr als einem Wort von `<gültige IK>` (z. B. `I. K. Mustermann, Rechnung <gültige IK>`), `A I K <gültige IK>`,
  `I K E A <gültige IK>`, `I K N <gültige IK>`, `X Y I K <gültige IK>`, `I  K <gültige IK>` (zwei Leerzeichen),
  `I<Tab>K <gültige IK>`, `I` + Zeilenumbruch + `K <gültige IK>`, `I,K <gültige IK>`, `I/K <gültige IK>`, `I_K <gültige IK>`.
  Der Text `I K` mit einer Nummer erst nach vier oder mehr Wörtern ergibt keinen Fund.
- AK-7 (Fehlalarmschutz allgemein, D-2): Kein Fund bei `A L A N R <gültige LANR>`, `L A N R X <gültige LANR>`,
  `L A N <gültige LANR>`, `L A N X R <gültige LANR>`, `LA NR <gültige LANR>` und `L ANR <gültige LANR>` (bekannte
  Grenze), `L A N R` ohne Nummer (kein Fund, keine Ausnahme), `L A N R` am Textende, nur `L A N R` allein.
- AK-8 (Sperrschrift mit vollen Kontextregeln, D-4, LANR): `L A N R des Behandlers <gültige LANR>` (Füllwörter) liefert
  einen Fund Konfidenz 0,80; mit vier Füllwörtern kein Fund. `L A N R <gültige LANR>, <gültige LANR>` liefert zwei Funde
  (Konfidenz je 0,80, Aufzählung). `<gültige LANR> (L A N R)` liefert einen Fund Konfidenz 0,80 (nachgestellt).
  `L A N R:` + Zeilenumbruch + `<gültige LANR>` liefert einen Fund 0,80; mit Leerzeile dazwischen keinen.
- AK-9 (Prüfziffer und Länge unter Sperrschrift): `L A N R <ungültige LANR>` und `I K <ungültige IK>` liefern je einen
  Fund Konfidenz 0,50; `L A N R 0012351` (7 Ziffern) und `I K 0012351` je einen Fund Konfidenz 0,40; mit Quellen wie im
  Bestand (`…-pruefziffer-ungueltig`, `…-laenge-abweichend`). Der Deckel hebt nie an (D-6).
- AK-10 (Nachsatzwort und Vorwort): `I K-Nr. <gültige IK>`, `I.K.-Nr. <gültige IK>`, `L A N R-Nummer <gültige LANR>`
  liefern je einen Fund. Gesperrte Schreibweise des Vorworts (`A r z t - N r <gültige LANR>`) liefert keinen Fund
  (bekannte Grenze).
- AK-11 (Wortgrenze und Umfeld): Die Sperrschrift gilt auch hinter einem normalen Wort oder Bindestrich-Präfix:
  `Praxis I K <gültige IK>` und `Praxis-I.K. <gültige IK>` liefern je einen Fund. In einem Satz ohne Umbruch:
  `Rechnung an I.K. <gültige IK> gesendet` liefert genau einen Fund über die Nummer, das Wort `gesendet` und die
  Buchstaben bleiben im Text.
- AK-12 (unsichtbare Zeichen, Bestand bleibt): Unverändert genau ein Fund mit direkter Konfidenz (0,95/0,92) bei
  `L` + U+200B + `A` + U+200B + `N` + U+200B + `R` + Leerzeichen + `<gültige LANR>` und bei
  `I` + U+00AD + `K` + Leerzeichen + `<gültige IK>` (nicht gesperrt, sondern unsichtbare Trenner; der Deckel aus D-3/D-4
  gilt nur für sichtbare Trenner nach D-1). Mit unsichtbarem Zeichen neben einem Trenner (`L` + U+200B + Leerzeichen +
  `A N R <gültige LANR>`) ist ein Fund erlaubt und hat Konfidenz höchstens 0,80.
- AK-13 (Bereich): In AK-1 bis AK-12 umfasst der Fund genau die Nummer (Ziffern samt inneren Trennern, AK-6 des
  Vorgängers), nie Buchstaben, Trenner oder Satzzeichen des Kontextworts; `I.K. <gültige IK>.` ergibt keinen Punkt im
  Fund.

### B. Komma/Semikolon vor nachgestelltem Kontextwort (Grenze 12)

- AK-14 (Grundfall): `<gültige IK>, IK`, `<gültige IK>; IK`, `<gültige LANR>, LANR`, `<gültige LANR>; LANR` liefern je
  genau einen Fund über die Nummer, Konfidenz **0,60**, Stufe „fraglich“, Quelle `ik` bzw. `lanr`.
- AK-15 (Leerraum und Klammer): Gleicher Fund (0,60) bei `<gültige IK> , IK`, `<gültige IK>,IK`, `<gültige IK>, (IK)`,
  `<gültige IK> ; [IK]`, `<gültige LANR>,<Tab>LANR`, `<gültige LANR>, LANR-Nr.`, `<gültige LANR>, Arzt-Nr.`,
  `<gültige IK>, Institutionskennzeichen`, `<gültige IK>, IK-Nummer`.
- AK-16 (Nummernformen): `001 235 101`-Form (mit Trennern), Längenabweichung und ungültige Prüfziffer vor `, KW`
  liefern Funde über die ganze Nummer mit 0,40 bzw. 0,50; bei gültiger Prüfziffer 0,60 (D-5, D-6).
- AK-17 (Folge von Nummern, R-2): `<gültige LANR>, <gültige LANR>, LANR` liefert zwei Funde (je 0,60);
  `<gültige IK> und <gültige IK>, IK` und `<gültige IK>; <gültige IK>; IK` liefern je zwei Funde.
- AK-18 (kein Fund, Fehlalarmgrenze, D-5): `<gültige IK>,, IK`, `<gültige IK>,; IK`, `<gültige IK>, und IK`,
  `<gültige IK>, / IK`, `<gültige IK>: IK`, `<gültige IK>. IK`, `<gültige IK>, Fax IK` (Wort dazwischen),
  `<gültige IK>,` + Zeilenumbruch + `IK`, `<gültige IK>, ((IK)` (zwei Klammern), `<gültige IK>(, IK)` liefern **keinen**
  Fund der Nummer vor dem Komma (Bestand: `<Nummer> und IK` und `<Nummer> / IK` bleiben ohne Fund). Ein Fund
  **hinter** dem Kontextwort bleibt davon unberührt (`<gültige IK>, IK <gültige IK>` liefert einen Fund für die zweite Nummer
  wie bisher und dazu den neuen Fund für die erste).
- AK-19 (Zwei-Buchstaben-Sperrschrift nicht nachgestellt, D-3): `<gültige IK>, I K` liefert keinen Fund.
  `<gültige LANR>, L A N R` liefert einen Fund (Konfidenz 0,60; Cap aus D-4 0,80 greift nicht, D-5 ist niedriger).
- AK-20 (Zusammenspiel mit dem Bestand): `<gültige IK> (IK)` (ohne Komma) bleibt Konfidenz 0,80; `IK <gültige IK>`
  bleibt 0,92; Aufzählung `IK <gültige IK>, <gültige IK>` bleibt 0,92 und 0,80. Das Komma vor einem Kontextwort, das
  selbst schon eine vorangehende Nummer erschließt (`IK <gültige IK>, IK <gültige IK>`), ändert die Funde des Bestands nicht;
  der erste Kontext gewinnt, es entsteht keine Überlappung.
- AK-21 (Konflikt zwischen den Arten): `IK <gültige IK>, LANR` liefert vom LANR-Erkenner einen Kandidaten über die Nummer
  (0,60); im Gesamtergebnis (`TextScanner.standard()`) bleibt genau ein Fund über diese Position, der Typ folgt der
  bestehenden Konfliktregel (höhere Konfidenz: IK 0,92). Die Position ist abgedeckt.

### C. Nichtfunktionale Anforderungen und Bestandsschutz

- AK-22 (kein Verlust): Für alle Texte der bestehenden Tests, des Roundtrip-Korpus und der Fuzz-Läufe gilt: Jede Position,
  die vor diesem Feature von einem Fund abgedeckt war, ist danach mindestens von einem Fund abgedeckt (Differenztest
  gegen die Fundliste des Vorgängerstands; zusätzliche Funde sind erlaubt). Alle bestehenden Tests laufen grün; **nur** die
  Tests, die Grenze 12 und Grenze 14 als „kein Fund“ festhalten, werden auf die neuen Erwartungen umgestellt und je
  einzeln im Bericht genannt. Die übrigen bekannten Grenzen (2 bis 11, 13) bleiben mit unveränderter Erwartung bestehen.
- AK-23 (Roundtrip): Für den Roundtrip-Korpus und den Fuzz-Test (beide um Texte mit gesperrten Kontextwörtern und
  `Nummer, KW` erweitert, fester Seed) ist die Rückübersetzung nach Pseudonymisierung aller Funde zeichengleich zum
  Original; im pseudonymisierten Text steht keine Ziffernkette mit 6 oder mehr Ziffern aus einer als Fund erkannten Nummer.
  Die Schutzgarantie (Abdeckung aller Kandidaten aller Standard-Erkenner) bleibt belegt.
- AK-24 (Laufzeit): Für jede der folgenden Eingaben mit 1 000 000 Zeichen braucht der Scan mit
  `TextScanner.standard()`, Pseudonymisierung und Rückübersetzung je weniger als 2 Sekunden (nach Aufwärmlauf) und
  liefert die **exakte** erwartete Fundzahl (Formel mit Rechenweg im Test): (a) `L ` wiederholt; (b) `I ` + `K ` abwechselnd
  ohne Nummer (`I K I K …`); (c) `I.` wiederholt; (d) `L-A-N-R ` wiederholt ohne Nummer; (e) `L A N R <gültige LANR> `
  wiederholt (über 50 000 Funde); (f) `I K <gültige IK> ` wiederholt; (g) `<gültige IK>, ` wiederholt ohne Kontextwort
  (kein Fund) und mit `IK` am Ende (alle Nummern ein Fund); (h) `<gültige IK>, IK ` wiederholt; (i) `L A N R` + sehr viele
  Trenner (`L` + 1 000 000 Leerzeichen) und `L` + `.A` wiederholt (Einzelbuchstabenketten ohne Wort). Verhalten linear,
  kein Regex, kein katastrophales Backtracking; der Linearitätsbeleg im Javadoc von `KvNumberScanner` wird für die
  neuen Regeln fortgeschrieben (Einzelbuchstabenkette: Fenster fester Größe, kein Zurücksetzen über eine feste Zahl
  Zeichen hinaus).
- AK-25 (Testdaten): Keine echten Nummern. Neunstellige Ziffernliterale stehen nur in `SynthetischeKvNummern.java`
  (Präfix `00`); der Test auf synthetische Testdaten bleibt grün; kein Test bildet zur Laufzeit eine gültige Nummer ohne
  Präfix `00`. Laufzeittexte mit Nummern verwenden Nullfolgen oder `00`-Nummern (Nachtrag des Vorgängers).
- AK-26 (Offline, kein Klartext, keine KI): Keine neue Abhängigkeit, kein Netzwerk-, DNS- oder Dateizugriff, keine
  verbotene Klasse (`ArchitekturRegelnTest` grün, keine Änderung der Positivliste ohne Begründung). `toString()`,
  Meldungen und Ausnahmen enthalten keine Nummer; der Klartext-Freiheits-Test bleibt grün. Die Erkennung ist
  regelbasiert und deterministisch (derselbe Text, dieselbe Fundliste in derselben Reihenfolge); Funde sind nach Position
  sortiert, überlappungsfrei, jeder Fund erfüllt die Teilstring-Invariante.
- AK-27 (Mensch entscheidet): Alle neuen Funde, auch „fraglich“ (AK-4, AK-14), werden vom Scan geliefert, aber nur
  ersetzt, wenn der Aufrufer sie an die Pseudonymisierung übergibt; ohne Übergabe bleibt der Text unverändert (Prüfung an
  `I K <gültige IK>` und `<gültige IK>, IK`).
- AK-28 (Eigenschaftstest, fester Seed, 1 000 Texte): (a) Texte aus Blöcken „gesperrtes Kontextwort (zufällige Trenner nach
  D-1, zufällige Schreibweise der Buchstaben) + Direktzeichen + Nummer“ mit bekannter Position ergeben genau diese Funde
  mit den erwarteten Konfidenzen; (b) Texte aus „Nummer + zufälliges Zeichen aus {`,`, `;`} + zufälliger Leerraum +
  Kontextwort“ ergeben genau diese Funde mit 0,60/0,50/0,40; (c) Rauschtexte ohne Kontextwort, auch mit zufälligen
  Einzelbuchstabenfolgen, Initialen und Nummern, enthalten keinen Fund; (d) Gegenproben aus AK-6/AK-7/AK-18 werden zufällig
  mit denselben Bausteinen erzeugt und liefern keinen Fund.
- AK-29 (Pflege der Dokumentation): Die Javadoc-Beschreibung des Scanners (Zeichenklassen, Zustand und Ereignisse,
  Konfidenz, Linearität) ist um die neuen Regeln ergänzt; die bekannten Grenzen 12 und 14 sind als geschlossen
  gekennzeichnet; neu entstehende Restgrenzen (z. B. Teilgruppen `LA NR`, gesperrtes Vorwort, `und`/`/` vor
  nachgestelltem Kontextwort) sind je als Test `bekannteGrenze…` festgehalten.

## Abgrenzung

Nicht Teil dieses Features:
- **Erkennung ohne Kontextwort (E-2)**: Eine Nummer ohne erkennbares Kontextwort bleibt ohne Fund. Das gesperrte Kontextwort
  ist ein Kontextwort im Sinne von E-2, keine Aufweichung.
- **BSNR und KVNR (E-1)** und jede weitere Kennnummer.
- **Die übrigen bekannten Grenzen** des Vorgängers: 1 (Tabelle mit Spaltenüberschrift), 2, 3, 4, 5, 6 (Nicht-ASCII-Ziffern),
  7 (Nullbreitenzeichen in der Nummer), 8 und 10 (Kombinationszeichen und unsichtbar wirkende Buchstaben am Kontextwort),
  9 (Fehltreffer `datei.ik`), 11 (kurzes Wort am Satzende), 13 (wörtlicher Platzhalter). Ihr Verhalten ändert sich nicht.
- **Aufzählungszeichen `und` und `/` vor einem nachgestellten Kontextwort** (`<Nummer> und IK`, `<Nummer> / IK`): bleibt
  die Restgrenze 12; dieses Feature öffnet nur Komma und Semikolon.
- **Gesperrte Schreibweise von Vorwort und Nachsatzwort** (`A r z t - N r`, `L A N R - N r`) und gemischte Gruppen
  (`LA NR`); Sperrschrift mit Tab, Zeilenumbruch oder zwei Trennern hintereinander; Leerraum-Varianten außerhalb von D-1.
- **Weitere Kontextwörter**, vom Nutzer konfigurierbare Wortlisten, Schreibfehler- oder Ähnlichkeitssuche.
- **Änderungen an Pseudonymisierung, Rückübersetzung, Konfliktauflösung, Escaping**, an der Prüfziffer, an Konfidenzwerten des
  Bestands (nur zusätzliche Deckel nach D-3 bis D-6).
- **Oberfläche**, Darstellung der Stufe „fraglich“, Dateiformate, Abbruch eines Scans, Thread-Sicherheit.

## Bezug zu Plan.md

- Kap. 2: Grundprinzipien (offline, keine KI im Produkt, Mensch entscheidet: neue Funde sind „fraglich“/„wahrscheinlich“ und
  werden vom Aufrufer übergeben; umkehrbar: Roundtrip zeichengenau; strukturerhaltend: Kontextwort und Sperrschrift bleiben
  stehen).
- Kap. 6: Erkennungs-Engine, KV-spezifische Erkenner (LANR, IK), Konfidenzstufen. Kap. 7 und 8: Pseudonymisierung und
  Rückübersetzung (unverändert genutzt). Kap. 11: kein Netzwerk, kein Klartext in Logs. Kap. 13: Roundtrip, Fuzzing,
  synthetische Testdaten, Laufzeit.
- Vorgänger: `docs/features/kv-nummern-erkennung/anforderung.md` (E-2, AK-11 bis AK-15, AK-21, AK-28) und
  `architektur.md` (Kap. 3.1 bis 3.5, R-1 bis R-4, bekannte Grenzen 12 und 14).

## Offene Fragen

Keine fachlichen Fragen an den Nutzer; alles Fachliche ist in „Entscheidungen“ (D-1 bis D-7) begründet entschieden.
Festzulegen durch den architect (technisch, keine Anforderung): ob die Quelle der Funde aus Sperrschrift und
Komma-Regel eine eigene Quellenkonstante erhält (Format `[a-z][a-z0-9-]{0,31}`, ohne Ziffern; die Anforderung verlangt
nur die Konfidenzwerte aus D-3 bis D-5, AK-14 nennt die unveränderte Quelle `ik`/`lanr`).
