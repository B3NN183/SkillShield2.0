# Architektur: Erkennung von KVNR und BSNR

Feature-Kurzname: `kvnr-bsnr-erkennung`
Status: **Entwurf zur Freigabe**
Grundlage: `anforderung.md` dieses Features (E-1 bis E-3, D-1 bis D-9, A-1 bis A-7, AK-1 bis AK-36, OF-1 bis OF-4),
Vorgängerpläne `../kv-nummern-erkennung/architektur.md`, `../kv-kontext-luecken/architektur.md`,
`../kv-trenner-und-umbruch/architektur.md` (insbesondere dort Kap. 8 mit 8.2, 9a, 9b, Regel 10 und Kap. 11).
Code-Stand: Ende `kv-trenner-und-umbruch` (`KvNumberScanner` mit Sperr- und Weit-Verfolger, `crOffen`).

Schreibweise wie in der Anforderung: `<gültige KVNR>`, `<BSNR>`, `KW`, `UZ`, `CR`, `LF`. `main/…` =
`skillshield-core/src/main/java/de/skillshield/core/`, `test/…` = `skillshield-core/src/test/java/de/skillshield/core/`.

---

## 0. Zusammenfassung in normaler Sprache

**Was gebaut wird.** SkillShield lernt zwei neue Nummern zu erkennen und durch Platzhalter zu ersetzen:

- die **Krankenversichertennummer (KVNR)** der Patientinnen und Patienten (ein Buchstabe und neun Ziffern), Platzhalter
  `[[KVNR_1]]`, und
- die **Betriebsstättennummer (BSNR)** der Praxen (neun Ziffern), Platzhalter `[[BSNR_1]]`.

Die Erkennung arbeitet wie bei LANR und IK: Ein Wort wie „KVNR“, „Versichertennummer“ oder „BSNR“ in der Nähe zeigt,
dass die folgende Nummer gemeint ist. Alle bisherigen Regeln (bis drei Wörter Abstand, Zeilenumbruch, Aufzählungen,
nachgestelltes Wort, gesperrte Schreibweise „K V N R“) gelten auch für die neuen Nummern.

**In welchen Schritten.** Es gibt drei Abschnitte, nach jedem ist das Programm fertig nutzbar:

1. **Teil 1a – KVNR mit Kontextwort** (Tasks T-1 bis T-8). Vorher wird der heutige Stand als Vergleichskopie
   eingefroren, damit jede Veränderung an LANR, IK, E-Mail, Pfad usw. sofort auffällt.
2. **Teil 1b – KVNR ohne Kontextwort** (T-9 bis T-11). Eine KVNR im Fließtext wird auch ohne das Wort davor gefunden,
   aber nur in sehr enger Form (Großbuchstabe, neun Ziffern, gültige Prüfziffer, freistehend) und nur als „fraglich“.
   Vorher wird ein Prüfstand gebaut, der Fehlalarme misst.
3. **Teil 2 – BSNR** (T-12 bis T-15), nur mit Kontextwort.

**Nebenwirkungen (ehrlich).**

- Teil 1b meldet etwa jede zehnte fremde Nummer aus Buchstabe und neun Ziffern (Artikel-, Vorgangs-, Kennzeichen-
  nummern), bei der die Prüfziffer zufällig passt, als „fraglich“. Steht direkt davor ein Wort wie „Rechnungsnummer“
  oder „Artikelnummer“, wird nichts gemeldet.
- Steht vor einer KVNR das Wort „LANR“ oder „IK“, wurden bisher die neun Ziffern als LANR/IK markiert. Künftig wird
  die ganze KVNR (mit Buchstabe) als KVNR markiert, teils mit niedrigerer Stufe. Die Stelle bleibt immer geschützt.
- Passt eine Nummer gleich gut zu zwei Arten (z. B. BSNR und LANR), entscheidet eine feste Reihenfolge über den Namen
  des Typs; der Typ kann dann „falsch“ sein, geschützt und umkehrbar ist die Stelle trotzdem.

**Wo zurückgenommen wird.** Teil 1b steht unter dem Abbruchkriterium der Anforderung (D-5): Zeigt der Prüfstand mehr
Fehlalarme als erlaubt, wird Teil 1b vollständig wieder entfernt (Task T-R), Teil 1a bleibt unverändert, und Sie
werden informiert. Teil 1b ist dafür in einer eigenen Datei gebaut und mit genau einer Zeile eingebunden.

**Vor dem Start nötig:** die Quelle zur KVNR-Prüfziffer (OF-1, vor T-3) und die Liste der Regionalcodes der BSNR
(OF-2, vor T-12) liefert die Hauptsitzung als Nachtrag zur Anforderung.

---

## 1. Lösungsansatz

**KVNR mit Kontextwort (1a) und BSNR (2)** werden als zwei weitere Arten in den bestehenden `KvNumberScanner`
eingehängt (neue Einträge `KvArt.KVNR` und `KvArt.BSNR`), nicht als eigener Scanner. Begründung:

- Alle Kontextregeln der Vorgänger (Füllwörter, Umbruch inkl. `CR UZ+ LF`, Aufzählung, nachgestellt, Komma-Regel,
  Sperr- und Weit-Verfolger, Vorwort mit Nachsatzwort) sind dort einmal und erprobt umgesetzt. Ein eigener Scanner
  müsste etwa 1 200 Zeilen nachbauen; D-3 und D-8 verlangen ausdrücklich „alle Kontextregeln unverändert“.
- Die BSNR ist reine Konfiguration (neun Ziffern, Kontextwörter, Plausibilität statt Prüfziffer).
- Die KVNR braucht genau drei kleine, art-gebundene Ergänzungen (nur wirksam, wenn die Art die Buchstabenform hat;
  der Code für LANR, IK und BSNR läuft unverändert durch):
  1. **Buchstabenform:** Ein Lauf aus genau einem ASCII-Buchstaben, dem unmittelbar eine Ziffer oder genau ein
     Gruppentrenner und eine Ziffer folgt, ist der Anfang einer Kette (nicht Wort, nicht Einzelbuchstabe der
     Sperrschrift). Die Ziffern dahinter werden mit **derselben** Kettenregel wie bisher zerlegt.
  2. **Angeklebte Ziffern:** Eine reine Ziffernkette, vor der unmittelbar ein Buchstabe steht, ergibt für die KVNR
     kein Segment (`AB` plus Ziffern ist keine KVNR-Form, AK-5).
  3. **`KV-Nr.` nur für die Buchstabenform (D-2):** Ein Kontext aus dem Vorwort `kv` trägt einen Merker; er gibt nur
     Segmente in Buchstabenform aus (direkt und nachgestellt).

**KVNR ohne Kontextwort (1b)** ist ein eigener, kleiner linearer Durchlauf in einer eigenen Klasse
(`KvnrOhneKontext`), den `KvnrRecognizer` mit genau einer Zeile hinzufügt. Er passt nicht in die Kontext-Logik des
Scanners (Wortgrenzen, Negativkontext) und ist so ohne Rest entfernbar (D-5).

**Prüfziffer KVNR und Regionalcodes BSNR** stehen je an genau einer Stelle in einer eigenen kleinen Klasse
(`KvnrPruefziffer`, `BsnrRegionalcodes`) mit Quellenvermerk; `KvCheckDigits` bleibt unberührt.

**Bestandsschutz:** In T-1 wird der heutige Scanner als dritte eingefrorene Kopie abgelegt
(`KvNumberScannerStandTrenner`). Gegen sie gilt auf Erkennerebene für LANR und IK **volle Gleichheit ohne jede
Ausnahme**; auf Scannerebene (alle Erkenner) gilt: keine abgedeckte Position geht verloren, und jeder bisherige Fund
bleibt mit gleichem Typ, Bereich und mindestens gleicher Konfidenz – außer in den vier benannten Kollisionsfällen
K-1 bis K-4 (Kap. 4.3), die nur in Texten mit KVNR-/BSNR-Kontextwort bzw. KVNR-Form zulässig sind.

---

## 2. Klärung der beiden Annahmen des product-owners am Code

### 2.1 Wie der `ConflictResolver` bei verschiedenen Typen über derselben Position entscheidet (D-6, AK-18, AK-30)

Belegt in `main/…/conflict/ConflictResolver.java`:

- **Gleicher Bereich:** Der schwächere Kandidat gilt als im stärkeren enthalten und verschwindet ganz; übernommen wird
  der stärkere **unverändert** (Typ, Konfidenz, Quelle). Stärke (`STAERKE`): Konfidenz absteigend, dann Länge
  absteigend, dann Start aufsteigend, dann Typrang (`URL` 0, `PFAD` 1, `EMAIL` 2, `IP` 3, **alle KV-Typen 4**), dann
  **Typname lexikographisch**, dann Quelle. Bei Gleichstand der Konfidenz zwischen KV-Arten gewinnt also
  `BSNR` vor `IK` vor `KVNR` vor `LANR`. Gegen `IP` (Rang 3) verliert jede KV-Art bei Gleichstand.
- **Verschachtelte Bereiche:** Umschließt ein Kandidat einen anderen, gewinnt der umschließende **unabhängig von der
  Konfidenz**. Das betrifft die KVNR in Buchstabenform: Sie umfasst Buchstabe und Ziffern, ein LANR-/IK-Kandidat nur
  die Ziffern – die KVNR gewinnt auch mit niedrigerer Konfidenz (Kollisionsfall K-1).
- **Teilüberlappung:** Fund über die Vereinigung, Typ des stärksten, Konfidenz des schwächsten Glieds, Quelle
  `zusammengelegt` (Kollisionsfall K-4).

Folge für D-6: „Typ folgt der höheren Konfidenz“ gilt genau bei **gleichem Bereich**. Gleicher Bereich ist
gesichert, weil KVNR (reine Ziffern), BSNR, LANR und IK dieselbe Kettenregel verwenden (Segmente hängen nicht vom
Kontext ab). Die Beispiele aus AK-18 sind gleicher Bereich (IK 0,92 bzw. 0,60 gegen KVNR 0,40: IK gewinnt).

### 2.2 Aufzählung hinter einem Kontextwort einer anderen Art (AK-30)

Belegt in `KvNumberScanner` (`lauf`, `fuellwort`, `segment`, `sonstiges`, `offenNachgestelltAusgeben`): Jede Art läuft
**getrennt** über den Text. Für eine Art ist das Kontextwort einer anderen Art ein **gewöhnliches Füllwort**: Es beendet
`direkt` und zählt zu den drei Füllwörtern, beendet aber den Kontext nicht. Eine Nummer hinter einem fremden
Kontextwort erhält daher von der zuerst genannten Art weiter einen Kandidaten (0,80 gültig, 0,50 ungültig bzw.
unbekannter Regionalcode, 0,40 Länge). Ein `/` zwischen einer offenen Nummer und dem nächsten Kontextwort verhindert
den nachgestellten Kontext (`offenSchraeg`); ein einzelnes `,` erlaubt ihn mit Deckel 0,60.

Ergebnis je AK-30-/AK-18-Fall (`B` = BSNR mit bekanntem Regionalcode, als LANR und IK ungültig; `L` = gültige LANR,
Präfix `00`, also Regionalcode unbekannt; `I` = gültige IK, Präfix `00`):

| Text | Kandidaten an Nummer 1 | Kandidaten an Nummer 2 | Ergebnis |
|------|------------------------|------------------------|----------|
| `BSNR B/LANR L` | BSNR 0,90 | LANR 0,95; BSNR 0,50 | BSNR, LANR |
| `LANR L/BSNR B` | LANR 0,95 | BSNR 0,90; LANR 0,50 | LANR, BSNR |
| `BSNR/LANR B/L` | LANR 0,50; BSNR 0,80 | LANR 0,80; BSNR 0,50 | BSNR, LANR |
| `LANR/BSNR L/B` | LANR 0,80; BSNR 0,50 | BSNR 0,80; LANR 0,50 | LANR, BSNR |
| `LANR L, BSNR L` | LANR 0,95; BSNR 0,50 (Komma) | LANR 0,80; BSNR 0,50 | LANR, LANR |
| `LANR B, BSNR B` | LANR 0,50; BSNR 0,60 (Komma) | LANR 0,50; BSNR 0,90 | BSNR, BSNR |
| `IK I (BSNR)` | IK 0,92; BSNR 0,50 | – | IK |
| `IK I, KVNR` | IK 0,92; KVNR 0,40 | – | IK |
| `Versichertennummer I, IK` | KVNR 0,40; IK 0,60 (Komma) | – | IK |

Hinweis: Eine Zahl, die zugleich gültige LANR ist und einen bekannten Regionalcode hat, ist mit synthetischen
Testdaten nicht bildbar (Wächter); „dieselbe Zahl unter LANR und BSNR“ (AK-30) wird deshalb mit `L` und mit `B`
geprüft; in beiden Fällen tragen die Platzhalter den Typ ihres Fundes und führen zum Originalwert zurück.

---

## 3. Betroffene Module, Pakete, Typen

Nur `skillshield-core`. Kein POM, keine neue Abhängigkeit, kein neues JDK-Paket.

### 3.1 Produktivdateien

| Datei | Änderung | Task |
|-------|----------|------|
| `main/…/recognizer/kv/KvNumberScanner.java` | `FENSTER` 24 → 28 (`MAX_KONTEXTWORT` 27), Javadoc Linearität | T-2 |
| `main/…/recognizer/kv/KvnrPruefziffer.java` | **neu**: Prüfziffer KVNR (A-1, OF-1), Quellenvermerk | T-3 |
| `main/…/model/FindingType.java` | Konstante `KVNR` | T-4 |
| `main/…/recognizer/kv/KvArt.java` | Eintrag `KVNR`, neue Felder (Kap. 3.2) | T-4 |
| `main/…/recognizer/kv/KvNumberScanner.java` | Buchstabenform, angeklebte Ziffern, Merker `nurBuchstabenform`, `ausgeben` | T-4 |
| `main/…/recognizer/kv/KvnrRecognizer.java` | **neu** (1a) | T-4 |
| `main/…/recognizer/TextScanner.java` | `standard()` + `KvnrRecognizer` (Index 6), Javadoc | T-6 |
| `main/…/recognizer/kv/KvnrOhneKontext.java` | **neu** (1b), Negativliste D-4 | T-10 |
| `main/…/recognizer/kv/KvnrRecognizer.java` | eine Zeile + Javadoc-Absatz 1b | T-10 |
| `main/…/recognizer/kv/BsnrRegionalcodes.java` | **neu**: Regionalcode-Liste (A-5, OF-2), Quellenvermerk | T-12 |
| `main/…/model/FindingType.java` | Konstante `BSNR` | T-13 |
| `main/…/recognizer/kv/KvArt.java` | Eintrag `BSNR`, `ä`-Formen | T-13 |
| `main/…/recognizer/kv/KvNumberScanner.java` | Faltung `Ä` (U+00C4) → `ä` (U+00E4) in `vergleichszeichen` | T-13 |
| `main/…/recognizer/kv/BsnrRecognizer.java` | **neu** | T-13 |
| `main/…/recognizer/TextScanner.java` | `standard()` + `BsnrRecognizer` (Index 7) | T-14 |

`KvCheckDigits`, `LanrRecognizer`, `IkRecognizer`, `ConflictResolver`, `Pseudonymizer`, `Reidentifier`,
`PlaceholderSyntax` bleiben unverändert.

### 3.2 Signaturskizzen

```java
// model/FindingType.java (T-4, T-13)
/** Krankenversichertennummer (KVNR), Buchstabe und neun Ziffern. */
public static final FindingType KVNR = new FindingType("KVNR");
/** Betriebsstaettennummer (BSNR), neun Ziffern. */
public static final FindingType BSNR = new FindingType("BSNR");

// recognizer/kv/KvnrPruefziffer.java (T-3) – einzige Stelle des KVNR-Verfahrens
final class KvnrPruefziffer {
    /**
     * @param buchstabe A-Z oder a-z (wird fuer die Rechnung auf A-Z gefaltet)
     * @param ziffern   genau neun Werte 0..9 (acht Ziffern und die Pruefziffer)
     * @throws IllegalArgumentException ohne Werte der Eingabe in der Meldung
     */
    static boolean istGueltig(char buchstabe, int[] ziffern) { ... }
}

// recognizer/kv/BsnrRegionalcodes.java (T-12) – einzige Stelle der Liste
final class BsnrRegionalcodes {
    /** Liste aus OF-2 als boolean[100], Quelle und Abrufdatum im Javadoc. */
    static boolean istBekannt(int ersteZiffer, int zweiteZiffer) { ... }
}

// recognizer/kv/KvArt.java (T-4, T-13) – additiv; LANR und IK liefern in allen bestehenden Methoden dasselbe
enum KvArt {
    LANR(...unveraendert...), IK(...unveraendert...),
    KVNR(FindingType.KVNR, 0.95, "kvnr",
         Set.of("kvnr", "versichertennummer", "versichertennr", "krankenversichertennummer",
                "krankenversichertennr", "krankenversicherungsnummer", "krankenversicherungsnr",
                "egknummer", "egknr"),
         Set.of("versicherten", "krankenversicherten", "egk", "kv"),
         /* nurBuchstabenVorwoerter */ Set.of("kv"), /* buchstabenform */ true,
         "-pruefziffer-ungueltig", "-form-abweichend"),
    BSNR(FindingType.BSNR, 0.90, "bsnr",
         mitUmlaut("bsnr", "bsnrnr", "nbsnr", "betriebsstaettennummer", "betriebsstaettennr",
                   "nebenbetriebsstaettennummer", "nebenbetriebsstaettennr"),
         mitUmlaut("betriebsstaette", "betriebsstaetten", "nebenbetriebsstaette", "nebenbetriebsstaetten"),
         Set.of(), false, "-regionalcode-unbekannt", "-laenge-abweichend");

    // bestehende Methoden unveraendert in Signatur und Wert fuer LANR/IK; neu:
    boolean buchstabenform();
    Set<String> nurBuchstabenVorwoerter();
    /** buchstabe = 0 bei reiner Ziffernfolge. */
    boolean isValid(char buchstabe, int[] d);   // LANR/IK: wie isValid(d); KVNR: KvnrPruefziffer; BSNR: Regionalcode
    boolean isValid(int[] d);                   // bleibt; = isValid((char) 0, d)
    String sourcePruefzifferUngueltig();        // source + Suffix (LANR/IK unveraendert)
    String sourceLaengeAbweichend();            // source + Suffix (KVNR: "-form-abweichend")
    /** Ersetzt in jedem Wort "ae" durch U+00E4 und nimmt beide Formen auf; Quelltext bleibt ASCII. */
    private static Set<String> mitUmlaut(String... woerter);
}
```

Die bisherigen LANR-/IK-Konstruktoraufrufe bleiben wortgleich (zweiter Konstruktor für die neuen Einträge mit den
Zusatzfeldern; der alte setzt die Bestandswerte `Set.of()`, `false`, `-pruefziffer-ungueltig`, `-laenge-abweichend`).

```java
// recognizer/kv/KvnrRecognizer.java (T-4; T-10 eine Zeile)
public final class KvnrRecognizer implements Recognizer {
    public static final String SOURCE = "kvnr";
    public static final String SOURCE_PRUEFZIFFER_UNGUELTIG = "kvnr-pruefziffer-ungueltig";
    public static final String SOURCE_FORM_ABWEICHEND = "kvnr-form-abweichend";
    @Override public List<Finding> detect(String text) {
        List<Finding> mitKontext = KvNumberScanner.detect(KvArt.KVNR, text);
        return KvnrOhneKontext.ergaenze(text, mitKontext);   // T-10, Teil 1b: die einzige Einbindung
    }
}

// recognizer/kv/KvnrOhneKontext.java (T-10) – alles von 1b, nichts sonst
final class KvnrOhneKontext {
    static final String SOURCE = "kvnr-ohne-kontext";
    static final double CONFIDENCE = 0.60;
    /** D-4, pflegbar; ASCII, klein, Vergleich mit Faltung A-Z. */
    static final List<String> NEGATIVKONTEXT = List.of("artikelnummer", "art.-nr.", "rechnungsnummer",
            "rechnungs-nr.", "vorgangsnummer", "aktenzeichen", "az.", "kennzeichen", "bestellnummer",
            "auftragsnummer", "kundennummer", "seriennummer", "sendungsnummer", "ticket");
    /** Fuegt Funde ohne Kontextwort hinzu, die keinen Fund aus {@code mitKontext} ueberlappen; sortiert. */
    static List<Finding> ergaenze(String text, List<Finding> mitKontext) { ... }
}

// recognizer/kv/BsnrRecognizer.java (T-13)
public final class BsnrRecognizer implements Recognizer {
    public static final String SOURCE = "bsnr";
    public static final String SOURCE_REGIONALCODE_UNBEKANNT = "bsnr-regionalcode-unbekannt";
    public static final String SOURCE_LAENGE_ABWEICHEND = "bsnr-laenge-abweichend";
    @Override public List<Finding> detect(String text) { return KvNumberScanner.detect(KvArt.BSNR, text); }
}
```

Alle Quellen erfüllen `[a-z][a-z0-9-]{0,31}` (längste: `bsnr-regionalcode-unbekannt`, 27 Zeichen).

---

## 4. Erkennungsregeln

### 4.1 Gemeinsamer Scanner: Fenster (T-2)

Die längsten Wörter sind `nebenbetriebsstaettennummer` (27), `krankenversicherungsnummer` (26),
`krankenversichertennummer` (25). `FENSTER` wird 28, `MAX_KONTEXTWORT` 27 (Puffer von Sperr- und Weit-Verfolger
ebenso; die Bitmaske der Teilstückanfänge bleibt ein `int`). Für LANR und IK ändert sich nichts: Fenster­suffixe und
Folgen mit 24 bis 27 Buchstaben scheitern an `kontextLaenge` (kein LANR-/IK-Wort hat diese Länge), der längste Treffer
bleibt derselbe. Linearität: höchstens 28 Vergleiche je Teilstückende statt 24.

### 4.2 KVNR mit Kontextwort (Teil 1a, T-4)

**Kontextwörter (D-2):** siehe `KvArt.KVNR` in Kap. 3.2. `eGK-Nummer`, `eGK-Nr.`, `Versicherten-Nr.`,
`Krankenversicherten-Nr.`, `KV-Nr.`, `KV-Nummer`, `KV Nr.` laufen über Vorwort + Nachsatzwort; `Patienten-KVNR` über das
Teilstück `KVNR`; `K V N R`, `K. V. N. R.` über Sperr- und Weit-Verfolger (unverändert). `VKNR`, `KVNRX`,
`Versicherungsnummer`, `KV` allein, `Nr.` allein sind keine Kontextwörter.

**Buchstabenform (nur Art mit `buchstabenform()`):** In `durchlaufen()` wird **vor** `laufMitVerfolger` geprüft:

```java
if (art.buchstabenform() && istBuchstabenAnfang(i)) {   // Zeichen i: A-Z oder a-z
    crOffen = false;
    sperrSchritt(cp);                                     // beendet eine laufende Sperrfolge (K V N R Z…)
    weitSchritt(cp);                                      // beendet eine laufende Weit-Folge
    i = ziffernkette(i);                                  // Kette beginnt beim Buchstaben
    continue;
}
```

`istBuchstabenAnfang(i)`: Zeichen `i` ist ASCII-Buchstabe (A-3), und entweder `i+1` ist ASCII-Ziffer oder `i+1` ist
ein Gruppentrenner (`istTrenner`) und `i+2` eine ASCII-Ziffer. Da die Hauptschleife nur an Laufanfängen steht, ist der
Buchstabe ein Lauf aus genau diesem einen Zeichen. Zwei Buchstaben (`AB1…`) sind ein Lauf mit zwei Buchstaben und
kein Buchstabenanfang.

`ziffernkette(start)` erhält einen Vorspann: Beginnt die Kette mit einem Buchstaben, wird er (und ein Trenner)
übersprungen, dann läuft die **unveränderte** Präfixsuche über die Ziffernblöcke. Segment = Buchstabe bis Ende der
gewählten Blöcke. Gibt es kein Segment (weniger als 6 oder mehr als 12 Ziffern), zählen Buchstabe und erster Block
zusammen als **ein** Füllwort; weiter hinter dem ersten Block (wie bisher). Die Regel „nummerierte Liste“ gilt nicht
für die Buchstabenform.

**Angeklebte Ziffern:** Beginnt eine reine Ziffernkette unmittelbar hinter einem Buchstaben (`istBuchstabe` an
`start - 1`), gibt es für eine Art mit Buchstabenform **kein** Segment; der erste Block zählt als Füllwort.

**`KV-Nr.` (D-2):** Erkennt `lauf()` ein Vorwort aus `nurBuchstabenVorwoerter()` mit Nachsatzwort, beginnt der
Kontext mit `nurBuchstabenform = true` (neuer Parameter von `kontextBeginnen`, sonst `false`). Bei diesem Merker
gilt: Ein Segment ohne Buchstaben wird bei `aktiv` nicht ausgegeben, sondern zählt als Füllwort; beim nachgestellten
Kontext werden nur offene Segmente ausgegeben, deren erstes Zeichen ein Buchstabe ist. Die Buchstabenform ist am
Segment selbst ablesbar (`text.charAt(start)` ist Buchstabe); die offenen Segmente brauchen kein weiteres Feld.

**Konfidenz und Quelle (`ausgeben`, D-3):**

| Segment | Konfidenz | Quelle |
|---------|-----------|--------|
| Buchstabe + 9 Ziffern, Prüfziffer gültig, direkt | 0,95 | `kvnr` |
| ebenso, weiterer Kontext / nachgestellt | 0,80 | `kvnr` |
| ebenso, nachgestellt mit `,`/`;` | 0,60 | `kvnr` |
| gesperrtes Kontextwort (auch Weit-Form) | höchstens 0,80 | nach Nummer |
| Buchstabe + 9 Ziffern, Prüfziffer ungültig | 0,50 | `kvnr-pruefziffer-ungueltig` |
| Buchstabe + 6–12 Ziffern außer 9; reine Ziffernfolge 6–12 (auch 9) | 0,40 | `kvnr-form-abweichend` |

Umsetzung: `boolean neunerForm = anzahl == NEUN && (!art.buchstabenform() || buchstabe != 0)`; Prüfung über
`art.isValid(buchstabe, ziffernwerte(start, end))`. Weder Fund noch Meldung enthält die berechnete Prüfziffer.

**Fundbereich:** Buchstabe, Trenner, Ziffern; nie Kontextwort, Doppelpunkt, Komma, abschließender Trenner.

### 4.3 KVNR ohne Kontextwort (Teil 1b, T-10)

`KvnrOhneKontext.ergaenze(text, mitKontext)` läuft einmal von links nach rechts. Ein Kandidat an Position `i`:

1. `text[i]` ist `A`–`Z` (Großbuchstabe, ASCII), `text[i+1..i+9]` sind neun ASCII-Ziffern (genau zehn Zeichen, keine
   Trenner);
2. **Wortgrenze vorn (AK-13):** `i == 0` oder `text[i-1]` ist weder Buchstabe noch Kombinationszeichen (wie
   `istBuchstabe` des Scanners), noch Ziffer, noch `_`, `-`, `/`, `\`, `@`;
3. **Wortgrenze hinten:** `i+10 == n` oder `text[i+10]` ist weder Buchstabe/Kombinationszeichen, Ziffer, `_`, `-`,
   `/`, `\`, `@`, und nicht `.` oder `,` gefolgt von einer ASCII-Ziffer;
4. `KvnrPruefziffer.istGueltig(text[i], neun Ziffern)`;
5. **Negativkontext (D-4):** Rückwärts ab `i-1` über Leerraum (`Character.isWhitespace`/`isSpaceChar`), `:` und `=`;
   endet der Text dort (Faltung A–Z) auf ein Wort aus `NEGATIVKONTEXT` und steht davor kein Buchstabe und keine Ziffer,
   entsteht kein Kandidat. Kein Übergehen von Füllwörtern.

Kandidat: `Finding(KVNR, Zehnerfolge, i, i+10, 0.60, "kvnr-ohne-kontext")`. Ein Kandidat, der einen Fund aus
`mitKontext` überlappt, entfällt (AK-11: nur der Fund 0,95). Ergebnis: Zusammenführen zweier sortierter Listen,
linear.

**Linearität:** Je Startposition höchstens elf Zeichen vorwärts. Zwei Kandidaten liegen mindestens elf Zeichen
auseinander (Ziffern und Grenze hinten), der Rückwärtslauf über Leerraum/`:`/`=` endet spätestens an der letzten
Ziffer des vorigen Kandidaten; die Rückwärtsläufe sind also disjunkt, der Wortvergleich liest höchstens 15 Zeichen.
Gesamt O(n).

**Interne Markierung:** nicht nötig; die Quelle `kvnr-ohne-kontext` genügt (OF-4).

### 4.4 BSNR (Teil 2, T-13)

Konfiguration `KvArt.BSNR` (Kap. 3.2). `isValid(…)` = `BsnrRegionalcodes.istBekannt(d[0], d[1])`; „ungültig“ heißt
hier „Regionalcode unbekannt“ (0,50, `bsnr-regionalcode-unbekannt`). Konfidenz direkt 0,90, sonst wie LANR/IK nach D-8
(0,80; Komma 0,60; Sperr höchstens 0,80; Länge 0,40 `bsnr-laenge-abweichend`). Keine Buchstabenform, kein
`nurBuchstabenform`.

**`ä`/`ae` (OF-4):** Die Wortlisten stehen in ASCII mit `ae`; `KvArt.mitUmlaut` nimmt zu jedem Wort die Form mit
U+00E4 auf (einzige Stelle). `vergleichszeichen` faltet zusätzlich U+00C4 → U+00E4 (`BETRIEBSSTÄTTE`). Für LANR/IK
ohne Wirkung: deren Wörter sind ASCII, ein Lauf mit `ä`/`Ä` gleicht vor und nach der Faltung keinem davon. Zerlegte
Umlaute (`a` + U+0308) bleiben unerkannt (Grenze).

### 4.5 Linearität (Nachweis im Javadoc, AK-22/AK-33)

Buchstabenanfang: O(1) Vorgriff (zwei Zeichen); Kette wie bisher (jeder Block höchstens 13-mal berührt); Merker
`nurBuchstabenform` O(1). 1b: Kap. 4.3. Grenzlast-Fälle in T-8, T-11, T-15 mit exakter Fundzahl.

---

## 5. Bestandsschutz: dritte eingefrorene Kopie und Kollisionsfälle

### 5.1 Kopie (T-1)

| Stand | Klasse (Testbaum, Paket `recognizer.kv`) | Hüllen |
|-------|------------------------------------------|--------|
| vor `kv-kontext-luecken` (bestehend) | `KvNumberScannerVorgaenger` | `KvVorgaenger` |
| Ende `kv-kontext-luecken` (bestehend) | `KvNumberScannerStandLuecken` | `KvStandLuecken` |
| **Ende `kv-trenner-und-umbruch` (neu)** | `KvNumberScannerStandTrenner` | `KvStandTrenner` (`lanr()`, `ik()`) |

Wortgleiche Kopie des heutigen `KvNumberScanner` (nur Klassenname und Kopfvermerk „eingefroren am Stand Ende
kv-trenner-und-umbruch, nicht ändern“), danach nie geändert. Die Kopien nutzen `KvArt` mit; deshalb gilt für `KvArt`
die Regel 11 (Kap. 9).

### 5.2 Prüfungen (neue Klasse `test/…/KvStandTrennerDifferenzTest.java`)

Textmengen: Fuzz 1, 1b, 1c, 1d, 1e, `KvDifferenzTest.korpus()`, Texte von `RoundtripKorpusTest`,
`KvKontextLueckenEigenschaftTest.differenzTexte()`; später zusätzlich Fuzz 1f, 1g, 1h und die Texte der neuen
Tests.

- **E (Erkennerebene, ohne Ausnahme):** Für jeden Text gilt `new LanrRecognizer().detect(t)` gleich
  `KvStandTrenner.lanr().detect(t)` in **allen** Feldern, ebenso IK. Gilt in jedem Task.
- **S-1 (Abdeckung, ohne Ausnahme):** Jede Position, die `ALT` = `TextScanner[Email, Ipv4, Url, Path,
  KvStandTrenner.lanr(), KvStandTrenner.ik()]` abdeckt, deckt `TextScanner.standard()` ab.
- **S-2 (Fund für Fund):** Für jeden Fund `f` aus `ALT` gibt es in `standard()` einen Fund mit gleichem Start, Ende,
  Typ und Konfidenz ≥ `f` – oder der Fall ist genau einer der Kollisionsfälle K-1 bis K-4 und dessen Bedingung gilt.
- **S-3 (keine zusätzlichen Funde):** Findet die unabhängige Referenz im Text weder ein KVNR-Kontextwort noch ein
  BSNR-Kontextwort noch (ab T-10) eine KVNR-Form, sind beide Fundlisten **gleich** (alle Felder).

**Kollisionsfälle (abschließend):**

| Fall | Beschreibung | Bedingung im Text | Wirkung |
|------|--------------|-------------------|---------|
| K-1 | KVNR umschließt: neuer KVNR-Fund `g` mit `g.end == f.end`, `g.start` 1 oder 2 Zeichen vor `f.start` (Buchstabe, ggf. ein Trenner), `f` vom Typ LANR oder IK | KVNR-Kontextwort oder KVNR-Form | Typ wird KVNR; Konfidenz kann sinken (z. B. LANR 0,80 → KVNR 0,60 bei 1b) |
| K-2 | Gleichstand, gleicher Bereich: `g.conf == f.conf`, `g.type` ∈ {KVNR, BSNR} und namentlich vor `f.type` | Kontextwort der Art `g.type` | Typ wechselt, Konfidenz gleich |
| K-3 | Stärkerer neuer Typ, gleicher Bereich: `g.conf > f.conf`, `g.type` ∈ {KVNR, BSNR} | Kontextwort der Art `g.type` | Typ wechselt, Konfidenz steigt (D-6) |
| K-4 | Teilüberlappung mit einem neuen KVNR-/BSNR-Kandidaten: Fund `zusammengelegt`, der `f` umfasst | KVNR-/BSNR-Kontextwort oder KVNR-Form | Typ des stärksten, Konfidenz des schwächsten Glieds |

Jeder Fall wird je Textmenge gezählt. Auf den Bestandsmengen (Fuzz 1 bis 1e, Korpus, Generatoren) ist die Zahl jedes
Falls **0** (sie enthalten weder KVNR-/BSNR-Kontextwort noch KVNR-Form – geprüft); auf den neuen Fuzz-Teilen ist sie
ein exakter Messwert (fester Seed), K-1 bis K-3 kommen dort mit Mindestzahl vor. K-4 wird nur gezählt, ein Auftreten
auf Bestandsmengen ist ein Befund.

**Unabhängige Referenzen** (neue Klasse `test/…/recognizer/kv/KvnrBsnrHilfen.java`, ohne Produktaufruf, Laufzeit
egal):
- `kvnrKontextReferenz(text)` / `bsnrKontextReferenz(text)`: Buchstabenstrom des Textes (alle Nicht-Buchstaben und
  unsichtbaren Zeichen entfernt, A–Z und Ä gefaltet) enthält ein Kontextwort der Art oder ein Vorwort mit `nr`/`nummer`
  (eigene Wortlisten, `ae`- und `ä`-Formen). Bewusst eine Überschätzung (erfasst Sperr- und Weit-Formen); die Zahl
  streng geprüfter Texte je Menge hat eine Mindestzahl.
- `kvnrFormReferenz(text)`: irgendwo ein Großbuchstabe A–Z, unmittelbar gefolgt von genau neun ASCII-Ziffern (ohne
  Prüfziffer, ohne Grenzen) – Überschätzung der 1b-Kandidaten.
- `kvnrOhneKontextReferenz(text)` (T-9): eigenständige Umsetzung von Kap. 4.3 (zeichenkettenbasiert).
- `kvnrGueltigReferenz(String)` (T-3): Prüfziffer nach A-1, unabhängig von `KvnrPruefziffer` geschrieben.

---

## 6. Wiederverwendung

`KvNumberScanner` (Lauf, Kettenregel, Kontextregeln, Sperr- und Weit-Verfolger, `crOffen`, `ausgeben` mit Deckel,
`kontextBeginnen`, `offenNachgestelltAusgeben`), `KvArt`, `Finding`, `FindingType`, `ConfidenceLevel`, `Recognizer`,
`TextScanner`, `ConflictResolver`, `Pseudonymizer`, `Reidentifier`, `PlaceholderSyntax` (alle Platzhalterregeln
gelten für `KVNR`/`BSNR` ohne Änderung). Testseitig: `SynthetischeKvNummern` (erweitert), `TestdatenSynthetischTest`
(erweitert), `KvTestHilfen` (nur lesend: Zeichenkonstanten, `ceilMillion`, `bauen`, Wächter
`assertKeinGueltigesNeunerfensterOhne00`), `RoundtripFuzzTest` (Lauf, Klassen, Bausteine `kvBausteinC` und der Teile
1d/1e als Mischbausteine mit eigenem `Random`), `RoundtripKorpusTest`, `GrenzlastTest`, `KlartextFreiheitTest`,
`TextScannerKvTest`, Siegel-Bildung aus `KvStandLueckenDifferenzTest` (als Vorlage, nicht geändert).

## 7. Neue Abhängigkeiten

Keine.

---

## 8. Sicherheits- und Datenschutzaspekte

- **Patientendaten:** Die KVNR ist eine Patientenkennung (höchster Schutzbedarf). 1a schützt sie bei Kontextwort,
  1b auch ohne – als „fraglich“, also nur bei Übergabe durch den Menschen ersetzt (AK-20).
- **Offline, kein Klartext:** reine Zeichenverarbeitung; keine neue Meldung mit Text; Prüfziffern- und
  Regionalcodeklasse werfen `IllegalArgumentException` ohne Eingabewerte; `toString()` der Funde unverändert. Weder
  Fund, Quelle noch Meldung enthalten berechnete/erwartete Prüfziffer oder die Regionalcode-Liste (AK-7, AK-27, AK-35).
  Testmeldungen nennen nur Seed, Iteration, Zähler, Fallname.
- **Kein Schutzverlust:** S-1 ohne Ausnahme; LANR/IK auf Erkennerebene identisch. K-1 kann die **Stufe** eines
  bisherigen LANR-/IK-Fundes senken (Risiko R-2); die Position bleibt geschützt und wird um den Buchstaben erweitert.
- **Fehlalarm 1b:** eng gefasst (E-3), Prüfstand vor dem Produktcode, Abbruchkriterium D-5.
- **Laufzeit/DoS:** linear, harte 2-s-Grenzen je Schritt mit exakter Fundzahl (AK-22, AK-33).
- **Testdaten:** KVNR nur mit Ziffern `0000…` an Stelle 2 bis 5, BSNR nur mit `00000` an Stelle 3 bis 7 oder
  Regionalcode `00`; zentrale Klasse; statischer und Laufzeit-Wächter (T-3, T-12).

---

## 9. Verbindliche Regeln für engineer und Prüfer

Die Regeln aus Kap. 8 des Plans `kv-trenner-und-umbruch` gelten weiter, mit folgenden Anpassungen und Ergänzungen:

1. Kein regulärer Ausdruck im Produkt; benannte Tests je AK-Beispiel; Eigenschaftstests mit festem Seed und
   unabhängigem Orakel; keine Ausgabe (`System.out`/`System.err`); kein Netzwerk-, DNS- oder Dateizugriff; keine
   neue Abhängigkeit.
2. Zeilen höchstens 120 Zeichen; Klarstellung 8.2 des Vorgängers gilt: Die schließende `}` eines Blocks steht nie
   hinter einer Anweisung auf derselben Zeile; `…);` am Ende der letzten Argumentzeile ist erlaubt; Maßstab ist der
   umgebende Bestandscode.
3. Keine `\uXXXX`-Escapes in neuen oder geänderten Zeilen; Sonderzeichen als `(char) 0x…`-Konstanten
   (`KvTestHilfen` bzw. `KvnrBsnrHilfen`: neu `AE_KLEIN = (char) 0xE4`, `AE_GROSS = (char) 0xC4`). Im Produkt
   ebenso (`KvArt.mitUmlaut`, `vergleichszeichen`).
4. Keine Ziffernkette ab neun Stellen und keine Folge Buchstabe + neun Ziffern (auch mit Trennern) außerhalb von
   `SynthetischeKvNummern.java` – auch nicht in Kommentaren, Javadoc, Testnamen, Seeds. Hash-/Siegel-Konstanten
   hexadezimal (höchstens acht Hexziffern), Seeds höchstens acht Dezimalstellen. Fremdnummern für Prüfstände werden
   zur Laufzeit von einer Methode in `SynthetischeKvNummern` gebildet.
5. Meldungen ohne Klartext und ohne Ziffern des Textes.
6. **Unverändert in allen Tasks:** `KvNumberScannerVorgaenger`, `KvVorgaenger`, `KvNumberScannerStandLuecken`,
   `KvStandLuecken`, `KvCheckDigits`, `LanrRecognizer`, `IkRecognizer`, `ConflictResolver`, alle bestehenden
   `SIEGEL_*`- und `HASH_TEIL_*`-Konstanten, Generatoren und Seeds der Fuzz-Teile 1 bis 1e, die eingefrorenen
   Prüfstände der Vorgänger (`KvTrennerFehlalarmTest`, `KvTrennerUzFehlalarmTest`, die in 9a des Vorgängers genannten
   Mitglieder von `KvTestHilfen`, die `t5_*`-Tests in `KvStandLueckenDifferenzTest`); nach T-1
   `KvNumberScannerStandTrenner` und `KvStandTrenner`; nach T-9 der Prüfstand 1b (Regel 13); jedes neue
   `SIEGEL_*`/`HASH_TEIL_*` nach dem Task, der es misst.
7. Keine Mindestzahl wird gesenkt, kein Seed geändert, um einen Test grün zu bekommen.
8. **Umstellung bestehender Tests nur wie im Task genannt.** Meldet ein anderer bestehender Test eine Änderung, hält
   der engineer an und meldet Test, Fall (ohne Ziffern) und Grund; keine stille Anpassung. Reine Ergänzungen in
   bestehenden Testdateien (neue Methoden, neue Fälle ohne geänderte Erwartung) sind erlaubt, wenn der Task die Datei
   nennt. Jede Umstellung wird im Bericht einzeln je Assertion genannt.
9. In neuen Tests die Quelle aus der Nummer ableiten (`KvnrBsnrHilfen.quelleNachNummerKvnr/Bsnr`), nicht aus der
   Konfidenz.
10. **Bestandsschutz:** Abweichungen gegenüber `KvStandTrenner` sind nur als K-1 bis K-4 unter ihrer Bedingung
    zulässig (Kap. 5.2). Jede andere Abweichung ist ein Befund: anhalten, melden.
11. **`KvArt` (Ausnahme von Regel 4 des Plans `kv-kontext-luecken`):** Änderungen nur additiv. Die LANR-/IK-Zeilen
    und alle Werte, die die eingefrorenen Kopien abfragen (`type`, `confidenceDirekt`, `source`, `kontextwoerter`,
    `vorwoerter`, `sourcePruefzifferUngueltig`, `sourceLaengeAbweichend`, `isValid(int[])`), bleiben für LANR und IK
    gleich; abgesichert durch `KvArtBestandTest` (T-1) und alle Siegel.
12. **Negativbeispiele in Tests von Teil 1a und Teil 2** verwenden `<ungültige KVNR>` oder Formen, die 1b nicht
    trifft (klein, mit Trenner, zwei Buchstaben). Eine gültige, geschlossene, großgeschriebene KVNR steht in diesen
    Tests nur dort, wo 1a einen Fund liefert. Kein Test außerhalb des 1b-Inventars (Regel 14) prüft auf die Quelle
    `kvnr-ohne-kontext` oder hängt von ihr ab.
13. **Einfrieren des Prüfstands 1b (wie 9a des Vorgängers):** Ab Abnahme von T-9 sind `KvnrOhneKontextFehlalarmTest`
    vollständig und in `KvnrBsnrHilfen` alle Mitglieder, von denen er abhängt (`kvnrOhneKontextReferenz`,
    `kvnrGueltigReferenz`, Fremdtext-Bausteine, Zähler, künstliche Erkenner), eingefroren; in
    `SynthetischeKvNummern` die Fremdnummern-Methode. Erlaubt sind reine Ergänzungen. qa prüft in T-10, T-11, T-R, dass
    im Diff keine Zeile der eingefrorenen Teile entfernt oder geändert ist.
14. **1b-Inventar (Rücknahme ohne Rest):** Alles, was nur 1b dient, steht in: `main/…/KvnrOhneKontext.java`, der einen
    Zeile und dem Javadoc-Absatz in `KvnrRecognizer`, den Testklassen `KvnrOhneKontextTest`,
    `KvnrOhneKontextEigenschaftTest`, Fuzz-Teil 1g (Generator, Seeds, Hash, Testmethoden mit `1g` im Namen), Methoden
    mit `OhneKontext` im Namen in `RoundtripKorpusTest`, `GrenzlastTest`, `KlartextFreiheitTest`,
    `KvStandTrennerDifferenzTest`, `KvnrGrenzenTest` und dem Javadoc-Abschnitt „ohne Kontextwort“ in
    `KvNumberScanner`/`KvnrRecognizer`. Jeder 1b-Task führt die Liste im Bericht nach. Der Prüfstand T-9 gehört
    **nicht** dazu (er bleibt nach einer Rücknahme grün und bewacht sie).
15. **Abbruchkriterium Teil 1b (D-5) – gilt in T-10 und T-11:** Meldet eine Prüfung aus T-9 (AK-16 (a), (b), (c)) oder
    S-2/S-3 auf den Bestandsmengen einen zusätzlichen KVNR-Fund, dann
    - ändert der engineer nichts an Erwartungen, Textlisten, Generatoren, Seeds, Mindestzahlen, Referenzen oder
      Schwellen;
    - darf er das Produkt nur korrigieren, wenn die Ursache nachweislich eine Abweichung des Codes von Kap. 4.3 ist
      (und nennt sie);
    - sonst hält er an, nimmt alle 1b-Änderungen am Produkt zurück (Stand nach T-8; Build grün, T-9 bleibt grün) und
      meldet Fallname bzw. Seed/Iteration und Zahl, ohne Text und Ziffern. Die Hauptsitzung legt den Befund dem
      Nutzer vor; bei Streichung folgt T-R.

---

## 10. Tasks

Nach jedem Task ist `mvn clean verify` im Wurzelverzeichnis grün. „Bestandstests, die sich ändern“ nennt
abschließend die bestehenden Tests mit geänderter Erwartung; alle anderen bestehenden Tests bleiben unverändert.

### Teil 0 – Bestandsschutz

#### T-1: Heutigen Stand einfrieren, strenge Differenz, Kollisionsprüfregel (nur Tests)

- **Ziel:** Kap. 5. Keine Produktänderung.
- **Dateien:** neu `test/…/recognizer/kv/KvNumberScannerStandTrenner.java`, `test/…/recognizer/kv/KvStandTrenner.java`,
  `test/…/recognizer/kv/KvnrBsnrHilfen.java` (Referenzen `kvnrKontextReferenz`, `bsnrKontextReferenz`,
  `kvnrFormReferenz`; `ALT_TRENNER`; Prüfregel `bestandsabweichungen(text, alt, neu)` mit Kategorien K-1 bis K-4 und
  „Verstoß“; Konstanten `AE_KLEIN`, `AE_GROSS`), `test/…/recognizer/kv/KvArtBestandTest.java`,
  `test/…/KvStandTrennerDifferenzTest.java`.
- **AK:** AK-19 und AK-32 (Werkzeug), AK-35 (Determinismus-Werkzeug).
- **Tests:** Siegel der Kopie (`SIEGEL_TRENNER_TEIL_1`, `_1B`, `_1C`, `_1D`, `_1E`, Korpus, Generatoren; hexadezimal,
  gemessen); E über alle Mengen; S-1 bis S-3 über alle Mengen (heute identisch, da `standard()` unverändert);
  Mindestzahlen alter LANR-/IK-Funde je Menge > 0 und streng geprüfter Texte (S-3) je Menge; `KvArtBestandTest`:
  LANR/IK `type`, `confidenceDirekt`, `source`, beide Wortmengen, beide Quellen-Suffixe, `isValid(int[])` an den
  Vektoren aus `SynthetischeKvNummern`. Selbsttests der Prüfregel: fehlender Fund, gesenkte Konfidenz, verschobener
  Bereich, Typwechsel zu LANR/IK oder zu einem Nicht-KV-Typ, Abdeckungsverlust → Verstoß; K-1 mit KVNR-Form im Text
  → zulässig, ohne → Verstoß; K-2/K-3 mit passendem Kontextwort → zulässig, mit Kontextwort der falschen Art →
  Verstoß; K-4 mit Kontextwort → zulässig. Referenz-Selbsttests: `KVNR`, `K V N R`, `K. V. N. R.`, `KV-Nr.`,
  `Versicherten-Nr.`, `eGK-Nummer`, `Patienten-KVNR` → ja; `VKNR`, `Versicherungsnummer`, `Die KV zahlt` → nein;
  `BSNR`, `B S N R`, `Betriebsstätten-Nr.` (mit `ä` und mit `ae`), `NBSNR` → ja; `Betrieb` → nein; KVNR-Form mit
  verketteten Konstanten → ja, mit Trenner/klein → nein.
- **Bestandstests, die sich ändern:** keine.
- **Abnahme:** Diff der Kopie gegen `KvNumberScanner` zeigt nur Klassenname und Kopfvermerk; alle Siegel gemessen.

### Teil 1a – KVNR mit Kontextwort

#### T-2: Fenster auf 28 Zeichen (Produkt, ohne Verhaltensänderung)

- **Ziel:** Kap. 4.1.
- **Dateien:** `main/…/recognizer/kv/KvNumberScanner.java` (`FENSTER`, `MAX_KONTEXTWORT`, Javadoc „längstes
  Kontextwort“ und Linearität); neu `test/…/recognizer/kv/KvFensterTest.java`.
- **AK:** AK-35 (Bestand), Vorbereitung AK-2, AK-25.
- **Tests:** Läufe mit 22 bis 30 Buchstaben, die auf `lanr`/`ik`/`arztnummer` enden, mit und ohne unsichtbares Zeichen
  vor dem Wort; Sperr- und Weit-Folgen mit 23 bis 28 Einzelbuchstaben; je Ergebnis gleich `KvStandTrenner`. Laufzeit
  (Erkenner, exakte Zahl): ein Lauf aus 1 000 000 Buchstaben → 0; `a` + ZWSP wiederholt → 0.
- **Bestandstests, die sich ändern:** keine.
- **Abnahme:** E, S-1 bis S-3 und alle Siegel unverändert grün.

#### T-3: KVNR-Prüfziffer und Testdaten (Voraussetzung: OF-1-Nachtrag liegt vor)

- **Ziel:** einzige Stelle des Verfahrens; zentrale Testnummern; Wächter.
- **Dateien:** neu `main/…/recognizer/kv/KvnrPruefziffer.java` (Quellenvermerk nach AK-24 mit den Angaben aus OF-1);
  neu `test/…/recognizer/kv/KvnrPruefzifferTest.java` (gleicher Vermerk); `test/…/SynthetischeKvNummern.java`
  (Abschnitt KVNR: `KVNR_GUELTIG`, `KVNR_GUELTIG_ZWEI`, mindestens drei ungültige Vektoren nach AK-6, Vektoren für
  Gewichtsbeginn, zweistellige Buchstabenwertung, Produkt über 9, Buchstabe `Z`; `KVNR_NULL` (Buchstabe + Nullfolge),
  Formen mit acht und zehn Ziffern, `KVNR_MARKER`; Rechenweg je Konstante als Kommentar; Restrisiko OF-3);
  `test/…/SynthetischeKvNummernTest.java` (Ergänzung); `test/…/TestdatenSynthetischTest.java`;
  `test/…/recognizer/kv/KvnrBsnrHilfen.java` (`kvnrGueltigReferenz`, Laufzeit-Wächter `assertKvnrSynthetisch`: jede
  Folge Buchstabe + optional ein Trenner + neun Ziffern aus ganzen Blöcken, die nach Referenz gültig ist, hat die
  Ziffern `0000` an Stelle 2 bis 5).
- **AK:** AK-6, AK-23, AK-24.
- **Tests:** jeder Vektor gegen `KvnrPruefziffer` und gegen `kvnrGueltigReferenz` (beide gleich); Groß/klein gleich;
  Fehlerfälle (`null`, falsche Länge, Wert außerhalb, Nicht-Buchstabe) mit Meldung ohne Werte; Wächter-Selbsttests.
- **Bestandstests, die sich ändern:** `TestdatenSynthetischTest`: neue Verstoßart `KVNR_FORM` (Buchstabe, optional ein
  Trenner, dann eine Kette mit neun Ziffern im Sinne der Regeln (1) bis (3)) außerhalb der zentralen Datei; in der
  zentralen Datei müssen KVNR-Formen die Ziffern `0000` an Stelle 2 bis 5 haben; Selbsttests dazu nur verkettet.
  Keine bestehende Erwartung und keine Mindestzahl ändert sich.
- **Abnahme:** Verfahren genau in einer Produktklasse; Hinweis H-1 umgesetzt (Kap. 13).

#### T-4: KVNR-Erkenner, Kern (Produkt, noch nicht in `standard()`)

- **Ziel:** Kap. 3.2, 4.2.
- **Dateien:** `main/…/model/FindingType.java` (`KVNR`); `main/…/recognizer/kv/KvArt.java`;
  `main/…/recognizer/kv/KvNumberScanner.java` (Buchstabenanfang, Vorspann in `ziffernkette`, angeklebte Ziffern,
  `nurBuchstabenform`, `ausgeben`; Javadoc „KVNR“); neu `main/…/recognizer/kv/KvnrRecognizer.java` (ohne 1b-Zeile);
  neu `test/…/recognizer/kv/KvnrRecognizerTest.java`; `test/…/model/FindingTypeTest.java` (Ergänzung `KVNR`,
  `[[KVNR_1]]`); `test/…/recognizer/kv/KvnrBsnrHilfen.java` (`quelleNachNummerKvnr`, `scanKvnr` mit Wächtern und
  Invarianten).
- **AK:** AK-1, AK-2, AK-3, AK-4 (Erkennerebene), AK-7, AK-8, AK-10, AK-35 (Teil).
- **Tests:** jedes Beispiel aus AK-1 bis AK-4, AK-7, AK-8, AK-10 als benannter Test (Typ, Wert, Bereich, Konfidenz,
  Stufe, Quelle); zusätzlich `KV-Nr.` + reine Ziffern nachgestellt (`<LANR> (KV-Nr.)`) → kein Fund;
  `KVNR` + `AB` + Ziffern → kein Fund; `KVNR AB <Ziffern>` → 0,40 (Hinweis H-5); `LANR <gültige KVNR>` im
  LANR-Erkenner unverändert (Ziffern als LANR). `null` → `NullPointerException` mit Meldung `text`.
- **Bestandstests, die sich ändern:** keine (`FindingTypeTest` nur ergänzt).
- **Abnahme:** E (LANR/IK identisch) grün; `KvArtBestandTest` grün; Siegel unverändert.

#### T-5: Schreibweisen und Kontextregeln KVNR (Tests; Produktkorrektur nur bei Abweichung von Kap. 4.2)

- **Dateien:** neu `test/…/recognizer/kv/KvnrKontextTest.java`, neu `test/…/recognizer/kv/KvnrEigenschaftTest.java`;
  ggf. `main/…/recognizer/kv/KvNumberScanner.java` (nur Korrektur, im Bericht begründet).
- **AK:** AK-5, AK-9, AK-35.
- **Tests:** AK-5 (Kleinbuchstabe; Buchstabe, Trenner, Ziffern; Gruppen 3+3+3, 4+5, 1+8 mit je allen fünf Trennern;
  zwei Trenner, Trenner am Ende; zwei Buchstaben); AK-9 je Regel mit Gegenprobe (`<ungültige KVNR>` in allen
  Negativbeispielen, Regel 12), Sperrschrift `K V N R`, `K. V. N. R.`, Weit-Form `K.  V.  N.  R.`, Umbruch `CR UZ LF`;
  Eigenschaftstest (≥ 1 000 Texte, fester Seed): Kontextwort aus D-2 (zufällige Schreibung) + Trenner + Nummer
  (gültig/ungültig/Form, gruppiert, klein) + Füllwörter/Umbrüche → Erwartung aus der Konstruktion; Determinismus,
  Sortierung, Überlappungsfreiheit, Teilstring-Invariante.
- **Bestandstests, die sich ändern:** keine.

#### T-6: Einbindung KVNR in `TextScanner.standard()` (Produkt)

- **Dateien:** `main/…/recognizer/TextScanner.java`; `test/…/recognizer/TextScannerTest.java`;
  `test/…/recognizer/TextScannerKvTest.java` (Ergänzung); `test/…/KvStandTrennerDifferenzTest.java` (Ergänzung:
  Scannerebene mit KVNR, K-Zähler); `test/…/KlartextFreiheitTest.java` (Ergänzung).
- **AK:** AK-4 (Scannerebene), AK-18, AK-19, AK-20, AK-21 (Einbindung), AK-35.
- **Tests:** AK-21 Text mit E-Mail, IPv4, URL, Pfad, IK, LANR, KVNR → sieben Funde; AK-18 beide Beispiele (je ein
  Fund, Typ, Konfidenz, Wiederholung gleich); Tabelle Kap. 2.2 für die KVNR-Zeilen; K-1 mit `LANR <gültige KVNR>` und
  KVNR-Kontextwort (`KVNR LANR <gültige KVNR>`) als belegter Kollisionsfall; AK-20 (`KVNR <ungültige KVNR>`: Fund,
  leere Übergabe → Text unverändert); Klartextfreiheit mit `KVNR_MARKER` (`toString()`, Meldungen); S-1 bis S-3 über
  alle Bestandsmengen mit K-Zählern = 0.
- **Bestandstests, die sich ändern:** `TextScannerTest.ak22_abgleichErkennerlisteStandardEnthaeltAlleErkennerIn
  FesterReihenfolge`: erwartete Liste um `KvnrRecognizer.class` am Ende erweitert (einzige Umstellung).
- **Abnahme:** alle Vorgänger-Differenztests (`KvVorgaengerDifferenzTest`, `KvDifferenzTest`,
  `KvStandLueckenDifferenzTest`) unverändert grün.

#### T-7: Roundtrip, Fuzz-Teil 1f, Korpus (nur Tests)

- **Dateien:** `test/…/RoundtripFuzzTest.java` (neuer **Teil 1f**: `SEED_TEXTE_KV_F`, `SEED_AUSWAHL_KV_F`, eigener
  Generator, `HASH_TEIL_1F` hexadezimal; Blöcke: jedes Kontextwort aus D-2, Sperr-/Weit-Formen, alle Schreibweisen,
  Aufzählung, nachgestellt mit/ohne Komma, Umbruch, gemischt mit LANR-/IK-Blöcken und Bausteinen aus 1c/1d/1e mit
  eigenem `Random`; Gegenproben ohne Block nur nach Regel 12; Grenzfälle bei der Konstruktion markiert);
  `test/…/RoundtripKorpusTest.java` (KVNR-Fälle AK-1 bis AK-10 mit erwartetem pseudonymisiertem Text);
  `test/…/KvStandTrennerDifferenzTest.java` (Siegel 1f; S-1 bis S-3 über 1f; K-1 bis K-4 als exakte Messwerte).
- **AK:** AK-17, AK-19, AK-21 (Fuzz).
- **Tests:** Roundtrip zeichengleich; gleiche KVNR zeichengleich → gleicher Platzhalter, mit/ohne Trenner →
  verschieden; im Ausgabetext keine Folge Buchstabe + ≥ 6 Ziffern aus einem Fund; Schutzgarantie über
  `STANDARD_ERKENNER`; `block-ohne-scan-abdeckung` strikt 0; Klassen-Mindestzahlen je Kontextwortgruppe und Stufe;
  `kvnrOhneKontextReferenz`-Vorgriff: in 1f liegt außerhalb der Blöcke keine KVNR-Form (Zähler 0; sichert, dass 1b
  später nichts an 1f ändert).
- **Bestandstests, die sich ändern:** keine; `HASH_TEIL_1` bis `HASH_TEIL_1E` unverändert.

#### T-8: Grenzlast, bekannte Grenzen, Dokumentation – Abschluss Teil 1a

- **Dateien:** `test/…/GrenzlastTest.java` (neue Methode `ak22_kvnr_eineMillionZeichen_…`); neu
  `test/…/recognizer/kv/KvnrGrenzenTest.java`; Javadoc `KvNumberScanner`, `KvnrRecognizer`, `KvArt`.
- **AK:** AK-22 (1a), AK-23 (Abschluss), AK-36 (1a).
- **Tests:** je 1 000 000 Zeichen, Scan + Pseudonymisierung + Rückübersetzung je < 2 s nach Aufwärmlauf, exakte
  Fundzahl mit Formel: Buchstabe + nur Ziffern → 0; `A1` wiederholt → 0; `KVNR ` + `Z` wiederholt → 0;
  `KVNR ` + (`KVNR_NULL` + ` `) wiederholt → ⌈10⁶ / 11⌉ (über 70 000; Aufzählung) und (`KVNR ` + `KVNR_NULL` + ` `)
  wiederholt → ⌈10⁶ / 16⌉; `K V N R ` wiederholt → 0; `KVNR` + nur Leerzeichen → 0. Je bekannte Grenze aus Kap. 11
  (Teil 1a) ein Test `bekannteGrenze…`.
- **Abnahme:** Teil 1a vollständig (AK-1 bis AK-10, AK-17 bis AK-24 ohne 1b-Anteile, AK-35, AK-36). Bericht an die
  Hauptsitzung: „Teil 1a abgeschlossen“ (eigener gesicherter Stand).

### Teil 1b – KVNR ohne Kontextwort (unter D-5)

#### T-9: Fehlalarm-Prüfstand 1b (nur Tests, vor jeder Produktänderung für 1b; danach eingefroren)

- **Dateien:** neu `test/…/recognizer/kv/KvnrOhneKontextFehlalarmTest.java`; `test/…/recognizer/kv/KvnrBsnrHilfen.java`
  (`kvnrOhneKontextReferenz`, Fremdtext-Bausteine, künstliche Erkenner); `test/…/SynthetischeKvNummern.java`
  (Methode `fremdKvnr(Random, boolean gueltig)`: Buchstabe A–Z, Ziffern `0000` + vier zufällige + Prüfziffer passend
  bzw. sicher falsch; Methode `zufallsKvnr(Random)`: letzte Ziffer gleichverteilt).
- **AK:** AK-16 (Prüfstand).
- **Tests:**
  - `ak16a_bestandsmengen_keinKvnrFund`: Fuzz 1 bis 1e, Korpus, Generatoren, `TestTexte` → Zahl der KVNR-Funde in
    `standard()` = 0 (Liste der Mengen im Bericht).
  - `ak16b_festerSatzVonFremdmustern_keinKvnrFund`: mindestens 40 synthetische Sätze, Textliste als Konstante, im
    Bericht vollständig: Artikel-, Rechnungs-, Vorgangs-, Kennzeichen-, Serien-, Sendungs-, Kunden-, Bestell-,
    Auftrags-, Ticketnummern; je Wort aus D-4 mit gültiger Fremdnummer direkt dahinter (mit ` `, `: `, ` = `, groß,
    klein); Fremdnummern mit falscher Prüfziffer in neutralen Sätzen; gültige Fremdnummern mit verletzter Wortgrenze
    (jedes Zeichen aus AK-13), klein, mit Trenner, mit zwei Buchstaben, mit acht und zehn Ziffern → 0 KVNR-Funde.
  - `ak16c_zufallsFremdnummern_hoechstensZehnKommaFuenfProzent`: fester Seed, 100 000 Texte, je ein Satz aus
    Rauschwörtern (ohne KVNR-Kontextwort laut Referenz, ohne Wort aus D-4 direkt davor) mit `zufallsKvnr` an
    zufälliger Stelle; Zahl der KVNR-Funde von `KvnrRecognizer` ≤ 10 500. Messwert in der Meldung.
  - Selbsttests: Referenz als künstlicher Erkenner: (b) 0, (c) zwischen 9 500 und 10 500; künstlicher Erkenner ohne
    Prüfziffer → (c) schlägt an; ohne Negativkontext → (b) schlägt an; ohne Wortgrenzen → (b) schlägt an; mit
    Kleinbuchstaben → (b) schlägt an.
- **Bestandstests, die sich ändern:** keine.
- **Abnahme:** auf dem Stand nach T-8 grün (0 Funde); Testlaufzeit im Bericht. Danach Regel 13.

#### T-10: KVNR ohne Kontextwort (Produkt) – Fehlalarm-Nachweis

- **Dateien:** neu `main/…/recognizer/kv/KvnrOhneKontext.java`; `main/…/recognizer/kv/KvnrRecognizer.java` (eine
  Zeile, Javadoc-Absatz); neu `test/…/recognizer/kv/KvnrOhneKontextTest.java`, neu
  `test/…/recognizer/kv/KvnrOhneKontextEigenschaftTest.java`; `test/…/KvStandTrennerDifferenzTest.java` (Methode
  `…OhneKontext…`: S-3 jetzt auch mit `kvnrFormReferenz`).
- **AK:** AK-11, AK-12, AK-13, AK-14, AK-15, AK-16 (Nachweis), AK-20 (1b).
- **Tests:** jedes Beispiel aus AK-11 bis AK-15 benannt (Bereich zehn Zeichen, 0,60, fraglich, Quelle
  `kvnr-ohne-kontext`; AK-15 über `standard()`); AK-13 je Zeichen der Liste davor und dahinter; AK-14 je Wort aus
  D-4 mit ` `, `:`, `=`, Groß/klein; AK-20 `<gültige KVNR>` allein: leere Übergabe → Text unverändert;
  Eigenschaftstest (10 000 Texte, fester Seed): Funde mit Quelle `kvnr-ohne-kontext` je Text gleich
  `kvnrOhneKontextReferenz` minus Überlappung mit 1a-Funden; Laufzeit Erkennerebene: `<gültige KVNR> ` wiederholt →
  ⌈10⁶ / 11⌉.
- **Bestandstests, die sich ändern:** keine. Ändert sich ein bestehender Test, gilt Regel 15.
- **Abnahme:** T-9 unverändert grün (Diff der eingefrorenen Teile leer); S-1 bis S-3 auf den Bestandsmengen ohne
  Abweichung; Zahlen (a), (b), (c) im Bericht.

#### T-11: 1b über die ganze Kette – Abschluss Teil 1b (nur Tests)

- **Dateien:** `test/…/RoundtripFuzzTest.java` (neuer **Teil 1g**: eigene Seeds, Generator, `HASH_TEIL_1G`; Blöcke
  KVNR ohne Kontextwort mit allen zulässigen Grenzzeichen, Gegenproben aus AK-12 bis AK-14 ohne Block, LANR-/IK-
  Kontext direkt vor einer KVNR-Form (K-1), Mischbausteine aus 1f mit eigenem `Random`);
  `test/…/RoundtripKorpusTest.java` (Methode `kvnrOhneKontextFaelle`); `test/…/GrenzlastTest.java` (Methode
  `ak22_kvnrOhneKontext_…`: `<gültige KVNR> ` wiederholt → ⌈10⁶ / 11⌉, über 80 000);
  `test/…/KlartextFreiheitTest.java` (Methode `…OhneKontext…`); `test/…/KvStandTrennerDifferenzTest.java` (Siegel 1g,
  K-1 als exakter Messwert mit Mindestzahl); `test/…/recognizer/kv/KvnrGrenzenTest.java` (Methoden
  `bekannteGrenzeOhneKontext…`); Javadoc.
- **AK:** AK-16 (Bericht), AK-17 (1b), AK-22 (1b), AK-36 (1b).
- **Bestandstests, die sich ändern:** keine.
- **Abnahme:** Inventar (Regel 14) im Bericht vollständig; Bericht „Teil 1b abgeschlossen“ mit den Prüfstandszahlen.

#### T-R: Rücknahme Teil 1b (nur bei Streichung nach D-5, auf Entscheidung des Nutzers)

- **Dateien:** alle Einträge des 1b-Inventars entfernen; neu in `KvnrGrenzenTest`:
  `bekannteGrenzeKvnrOhneKontextwort_keinFund` (gültige KVNR allein, im Satz → kein Fund); Javadoc „bekannte Grenze“.
- **Abnahme:** `KvnrRecognizer.detect` = `KvNumberScanner.detect(KvArt.KVNR, text)`; T-9 unverändert grün; alle
  Tests von Teil 1a unverändert grün; `grep` nach `kvnr-ohne-kontext` findet nur den Prüfstand.

### Teil 2 – BSNR

#### T-12: Regionalcodes und BSNR-Testdaten (Voraussetzung: OF-2-Nachtrag liegt vor)

- **Dateien:** neu `main/…/recognizer/kv/BsnrRegionalcodes.java` (Liste aus OF-2, Quellenvermerk nach AK-24
  sinngemäß); neu `test/…/recognizer/kv/BsnrRegionalcodesTest.java` (jeder Listencode bekannt, `00` und mindestens drei
  weitere unbekannt, Grenzwerte, Fehlerfälle ohne Werte in der Meldung); `test/…/SynthetischeKvNummern.java`
  (Abschnitt BSNR: `BSNR_BEKANNT`, `BSNR_BEKANNT_ZWEI`, `BSNR_UNBEKANNT` (Regionalcode `00`), Länge acht und zehn,
  `BSNR_NULL`, `BSNR_MARKER`; Bildung nach A-6; Regionalcode so gewählt, dass die Zahl nach LANR- **und** IK-Verfahren
  ungültig ist (Wächter; z. B. Code mit `4·z1 + 9·z2` nicht durch 10 teilbar, Stellen 8 und 9 verschieden));
  `test/…/SynthetischeKvNummernTest.java`; `test/…/TestdatenSynthetischTest.java`;
  `test/…/recognizer/kv/KvnrBsnrHilfen.java` (`assertBsnrSynthetisch`: in Teil-2-Testtexten hat jede Neunerfolge ohne
  Präfix `00` die Ziffern `00000` an Stelle 3 bis 7).
- **AK:** AK-27 (Liste), AK-34.
- **Bestandstests, die sich ändern:** `TestdatenSynthetischTest`, Zulässigkeit (c): In der zentralen Datei ist eine
  Neunerfolge auch zulässig, wenn sie an Stelle 3 bis 7 `00000` hat (BSNR-Form nach A-6); Verstoßart
  `ZENTRALE_OHNE_PRAEFIX_00` bleibt für alle anderen; Selbsttest dazu. Keine Mindestzahl sinkt.

#### T-13: BSNR-Erkenner (Produkt, noch nicht in `standard()`)

- **Dateien:** `main/…/model/FindingType.java` (`BSNR`); `main/…/recognizer/kv/KvArt.java` (`BSNR`, `mitUmlaut`);
  `main/…/recognizer/kv/KvNumberScanner.java` (Faltung U+00C4 → U+00E4, Javadoc); neu
  `main/…/recognizer/kv/BsnrRecognizer.java`; neu `test/…/recognizer/kv/BsnrRecognizerTest.java`;
  `test/…/model/FindingTypeTest.java` (Ergänzung); `test/…/recognizer/kv/KvFensterTest.java` (Ergänzung: Läufe mit
  `Ä`/`ä` und LANR-/IK-Wort → gleich `KvStandTrenner`).
- **AK:** AK-25, AK-26, AK-27, AK-28, AK-29.
- **Tests:** jedes Beispiel aus AK-25 bis AK-29 benannt (auch `Betriebsstaettennummer`, `BETRIEBSSTÄTTE Nr.`,
  `Nebenbetriebsstaettennummer` mit 27 Buchstaben, Sperrschrift `B S N R`, `B. S. N. R.`, `N B S N R`, Weit-Form);
  Eigenschaftstest (≥ 1 000 Texte, fester Seed) wie T-5.
- **Bestandstests, die sich ändern:** keine.
- **Abnahme:** E grün (LANR/IK identisch trotz Faltung); `KvArtBestandTest` grün.

#### T-14: Einbindung BSNR, Kollisionen (Produkt)

- **Dateien:** `main/…/recognizer/TextScanner.java`; `test/…/recognizer/TextScannerTest.java`;
  `test/…/recognizer/TextScannerKvTest.java` (Ergänzung); `test/…/KvStandTrennerDifferenzTest.java` (Ergänzung);
  `test/…/KlartextFreiheitTest.java` (Ergänzung `BSNR_MARKER`).
- **AK:** AK-30, AK-31, AK-32 (Bestandsschutz, Mensch entscheidet), AK-33 (Einbindung), AK-35.
- **Tests:** jede Zeile der Tabelle Kap. 2.2 mit BSNR; AK-30 Platzhalter je Typ und Rückübersetzung; AK-31 URL,
  Pfad, E-Mail; AK-32 fraglich-Fund nur bei Übergabe ersetzt; Text mit allen Funden der Standard-Erkenner; S-1 bis
  S-3 über alle Mengen, K-Zähler auf Bestandsmengen 0.
- **Bestandstests, die sich ändern:** `TextScannerTest.ak22_abgleichErkennerliste…`: Liste um `BsnrRecognizer.class`
  am Ende erweitert.

#### T-15: Fuzz-Teil 1h, Korpus, Grenzlast, Dokumentation – Abschluss Teil 2 (nur Tests und Javadoc)

- **Dateien:** `test/…/RoundtripFuzzTest.java` (neuer **Teil 1h**: eigene Seeds, Generator, `HASH_TEIL_1H`; BSNR-Blöcke
  mit allen Kontextwörtern, `ä`/`ae`, Schreibweisen, Kontextregeln, Formularfeld `BSNR/LANR`; gemischt mit LANR-,
  IK- und KVNR-Blöcken (nur 1a-Formen, Regel 12); kein Text enthält eine KVNR-Form außerhalb eines KVNR-Blocks mit
  Kontextwort – geprüft); `test/…/RoundtripKorpusTest.java` (BSNR-Fälle); `test/…/GrenzlastTest.java` (Methode
  `ak33_bsnr_…`); `test/…/KvStandTrennerDifferenzTest.java` (Siegel 1h, K-2/K-3 als Messwert mit Mindestzahl);
  neu `test/…/recognizer/kv/BsnrGrenzenTest.java`; Javadoc `KvNumberScanner`, `BsnrRecognizer`, `KvArt`.
- **AK:** AK-32 (Roundtrip), AK-33, AK-34, AK-36 (Teil 2).
- **Tests (Grenzlast, je < 2 s, exakte Zahl):** `BSNR ` + nur Ziffern → 0; `BSNR ` + `1 ` wiederholt → 0; `BSNR` + nur
  `-` → 0; `BSNR ` + (`BSNR_NULL` + ` `) wiederholt → ⌈10⁶ / 10⌉ (über 70 000); `B S N R ` wiederholt → 0;
  (`BSNR_BEKANNT` + ` (BSNR) `) wiederholt → ⌈10⁶ / 17⌉.
- **Bestandstests, die sich ändern:** keine.
- **Abnahme:** Bericht „Teil 2 abgeschlossen“; Javadoc nennt alle Konfidenzen aus D-8 und die Grenzen aus Kap. 11.

---

## 10a. Zuordnung AK → Task

| AK | Task | AK | Task | AK | Task |
|----|------|----|------|----|------|
| AK-1 | T-4 | AK-13 | T-10 | AK-25 | T-13 |
| AK-2 | T-4 | AK-14 | T-10 | AK-26 | T-13 |
| AK-3 | T-4 | AK-15 | T-10 | AK-27 | T-12, T-13 |
| AK-4 | T-4, T-6 | AK-16 | T-9 (Prüfstand), T-10 (Nachweis), T-11 | AK-28 | T-13 |
| AK-5 | T-5 | AK-17 | T-7, T-11 | AK-29 | T-13 |
| AK-6 | T-3 | AK-18 | T-6 | AK-30 | T-14 |
| AK-7 | T-4 | AK-19 | T-1 (Werkzeug), T-6, T-7 | AK-31 | T-14 |
| AK-8 | T-4 | AK-20 | T-6, T-10 | AK-32 | T-1 (Werkzeug), T-14, T-15 |
| AK-9 | T-5 | AK-21 | T-6, T-7 | AK-33 | T-14, T-15 |
| AK-10 | T-4 | AK-22 | T-8, T-10, T-11 | AK-34 | T-12, T-15 |
| AK-11 | T-10 | AK-23 | T-3, T-8 | AK-35 | T-1, T-2, T-4 bis T-6, T-14 |
| AK-12 | T-10 | AK-24 | T-3 | AK-36 | T-8, T-11, T-15 |

Wird 1b nach D-5 gestrichen: T-10 und T-11 entfallen bzw. werden durch T-R zurückgenommen; AK-11 bis AK-16 entfallen
(der Prüfstand T-9 bleibt), die 1b-Anteile von AK-17, AK-20, AK-22 entfallen; Teil 1a und Teil 2 bleiben vollständig.

---

## 11. Bekannte Grenzen nach diesem Feature (je ein Test `bekannteGrenze…`)

**KVNR**
- **G-K1** (nur mit 1b): KVNR ohne Kontextwort mit ungültiger Prüfziffer, kleinem Buchstaben, Trennern, unsichtbarem
  Zeichen in der Nummer oder unmittelbar vorangestelltem Wort aus D-4 bleibt Klartext (E-3, D-4). Ohne 1b: jede KVNR
  ohne Kontextwort.
- **G-K2** (nur mit 1b, Folge): Fremdmuster aus Großbuchstabe + neun Ziffern mit zufällig passender Prüfziffer und
  ohne Negativkontext werden „fraglich“ gemeldet (etwa 10 %).
- **G-K3:** zwei Buchstaben, Nicht-ASCII-Buchstaben oder -Ziffern, Wörter außerhalb D-2 (`KVID`, `Versicherungsnummer`,
  `Krankenversicherungs-Nr.`) → kein KVNR-Fund.
- **G-K4:** Ziffern direkt an einem Wort (`KVNR` + Ziffern ohne Leerzeichen, `AB` + Ziffern) → kein KVNR-Fund.
- **G-K5:** Folgt einem Einzelbuchstaben der Sperrschrift direkt eine Ziffer (`K V N R` ohne Lücke vor den Ziffern),
  gilt er als Buchstabe einer Nummer; die Folge endet vorher (kein Kontextwort).
- **G-K6** (Folge, kein Fehler): `KVNR AB <Ziffern>` (zwei Buchstaben, dann Trenner) ergibt 0,40 über die reinen
  Ziffern (D-3).
- **G-K7:** `KV-Nr.` vor einer reinen Ziffernfolge → kein Fund (D-2, gewollt).
- **G-K8:** Unsichtbare Zeichen zwischen Buchstabe und Ziffern oder in der Nummer beenden die Kette (wie Bestand).

**BSNR**
- **G-B1:** BSNR ohne Kontextwort, in Tabellen unter Spaltenüberschrift oder hinter Leerzeile bleibt Klartext (E-2).
- **G-B2:** zerlegte Umlaute (`a` + U+0308) in `Betriebsstätte…` → kein Kontextwort.
- **G-B3:** Haupt- und Nebenbetriebsstätte sind nicht unterscheidbar (beide `BSNR`).

**Gemeinsam (D-6)**
- **G-D1:** Gleichstand der Konfidenz bei gleichem Bereich: `BSNR` gewinnt vor `IK`, `KVNR`, `LANR`; `KVNR` vor
  `LANR`; `IP` vor allen KV-Arten. Im Formularfeld `BSNR/LANR <B>/<L>` kann eine reale LANR, deren erste zwei Ziffern
  ein bekannter Regionalcode sind, als BSNR typisiert werden (beide 0,80). Die Position bleibt geschützt.
- **G-D2:** K-1: LANR-/IK-Kontextwort vor einer KVNR-Form ergibt einen KVNR-Fund über Buchstabe und Ziffern, mit 1b
  teils „fraglich“ statt bisher „wahrscheinlich“.
- Unverändert gelten alle Grenzen der Vorgänger (12R, 14a, 14b, 14cR, 14d, 14e, 14f, 14g, 14h-Rest, 15R, Grenzen 1 bis
  11 und 13) sinngemäß auch für KVNR und BSNR.

## 12. Risiken

- **R-1 Fehlalarm 1b:** ehrlich etwa 10 % fremder Buchstaben-Ziffern-Muster ohne Negativkontext. Gegenmittel: enge
  Regel, Prüfstand vor dem Code, D-5. Restrisiko: Fremdkontexte außerhalb der D-4-Liste (z. B. `Ticket-Nr.`,
  `Beleg`), die erst im Echtbetrieb auffallen.
- **R-2 Stufenverlust durch K-1:** Ein bisher als LANR 0,80 („wahrscheinlich“) erkannter Wert wird mit 1b zu KVNR 0,60
  („fraglich“), wenn er als KVNR-Form mit gültiger Prüfziffer dasteht. Das betrifft nur Texte mit LANR-/IK-Kontextwort
  direkt vor Buchstabe + neun Ziffern. Die Oberfläche ersetzt „fraglich“ nur nach Bestätigung; der Schutz hängt dann
  am Menschen. Ich halte das für vertretbar (die Form ist eindeutig eine KVNR-Form; die Stelle wird breiter geschützt),
  benenne es aber ausdrücklich.
- **R-3 Gemeinsamer Scanner:** Fenstergröße, Buchstabenform und Faltung ändern gemeinsamen Code. Gegenmittel: E
  (volle Gleichheit LANR/IK) gegen die neue Kopie in jedem Task, eigene Fenster-Tests, alle Siegel.
- **R-4 `KvArt` trotz früherer Sperre:** Die Kopien hängen an `KvArt`. Gegenmittel: Regel 11, `KvArtBestandTest`,
  Siegel.
- **R-5 Quellen (OF-1, OF-2):** Ein Irrtum im Prüfziffernverfahren ändert nur Stufen (1a) bzw. die Reichweite von 1b;
  ein Irrtum in der Regionalcode-Liste nur die Stufe der BSNR. Korrektur jeweils an einer Stelle plus Vektoren.
- **R-6 Gleichstand-Typen (G-D1):** In realen Formularen sind falsch typisierte, aber geschützte Positionen möglich.
- **R-7 Testlaufzeit:** 100 000 Texte (T-9), 10 000 (T-10), drei Fuzz-Teile → geschätzt +40 bis 90 s für
  `mvn clean verify`. Texte kurz halten (≤ 200 Zeichen), in (c) nur `KvnrRecognizer` aufrufen; eine Testklasse über
  60 s wird gemeldet.
- **R-8 Überschätzende Referenzen:** `kvnrKontextReferenz` nimmt auch zufällige Buchstabenfolgen als Kontext an und
  nimmt solche Texte aus S-3 aus. Gegenmittel: Mindestzahlen streng geprüfter Texte je Menge.

---

## 13. Hinweise zur Anforderung (entschieden in Richtung „möglichst viel schützen“, zur Kenntnis)

- **H-1 (AK-6 gegen A-6):** Ein Vektor, der die zweistellige Buchstabenwertung (A = 01) von einer einstelligen
  unterscheidet, braucht einen Buchstaben A bis I; mit `Z` allein ist das nicht prüfbar. Entscheidung: Die
  Prüfziffern-Vektoren dürfen andere Buchstaben verwenden, die Ziffern beginnen immer mit `0000` (A-6 sinngemäß); der
  Wächter prüft genau das. Restrisiko in der Testklasse vermerkt.
- **H-2 (AK-30 „dieselbe Zahl“):** siehe Kap. 2.2 – mit synthetischen Daten ergeben sich zwei gleiche Typen; geprüft
  wird, dass jeder Platzhalter den Typ seines Fundes trägt und zurückführt.
- **H-3 (`<BSNR ungültig>`):** Regionalcode `00` mit `00000` ergibt rechnerisch immer eine gültige LANR (Stellen 1 bis 7
  null). Ohne LANR-Kontextwort ohne Wirkung; in Kollisionstests wird das berücksichtigt.
- **H-4 (AK-16 (a) „alle bestehenden Tests“):** umgesetzt als Messung über die Textmengen aus T-9 plus „alle bestehenden
  Tests laufen unverändert grün“.
- **H-5 (AK-5 „zwei Buchstaben“):** gilt für direkt angeklebte Buchstaben. `KVNR AB <Ziffern>` liefert nach D-3 einen
  Fund 0,40 über die reinen Ziffern (Schutz geht vor), als Folge G-K6 dokumentiert.
- **H-6 (AK-22/AK-33 „über 70 000 Funde“):** Mit wiederholtem Kontextwort ergibt `KVNR <Nullnummer> ` nur 62 500
  Funde je Million Zeichen; die Zahl über 70 000 entsteht als Aufzählung (Kontextwort einmal vorn). Beide Varianten
  werden geprüft, jeweils mit exakter Formel.
- **H-7 (D-6 und Verschachtelung):** „Höhere Konfidenz gewinnt“ gilt nur bei gleichem Bereich; bei KVNR-Form gewinnt
  der umschließende KVNR-Fund (K-1). Die Anforderung schließt Änderungen am `ConflictResolver` aus; daher als
  Kollisionsfall benannt statt umgangen.
- **H-8 (OF-4):** `ä`/`ae` über `KvArt.mitUmlaut` und Faltung `Ä` → `ä`; Negativliste als Konstante in
  `KvnrOhneKontext` (fällt bei Rücknahme mit weg); keine interne Markierung der Quelle; 1b als eigene Klasse mit einer
  Einbindungszeile.
- **H-9 (Reihenfolge):** 1a → 1b → 2 nach E-1 (Patientendaten zuerst). Teil-2-Tests hängen nicht von 1b ab (Regel 12),
  eine spätere Rücknahme von 1b berührt Teil 2 nicht.

## 14. Abweichungen von Plan.md

Keine. (Kein neues Modul, keine neue Abhängigkeit, kein Netzwerk-Code; KVNR und BSNR sind in Plan.md Kap. 1/6 als
KV-Kennnummern vorgesehen.) Abweichung von einer Regel eines Vorgängerplans: `KvArt` wird additiv geändert (Regel 11,
Begründung Kap. 1 und R-4).

## 15. Offene Punkte für die Freigabe

1. **OF-1:** Quelle (Titel/URL, Abrufdatum) der KVNR-Prüfziffer als Nachtrag zur Anforderung **vor T-3**.
2. **OF-2:** Regionalcode-Liste der BSNR mit Quelle und Bestätigung „keine Prüfziffer“ **vor T-12**.
3. **K-1/R-2:** Zustimmung, dass ein LANR-/IK-Fund über den Ziffern einer KVNR-Form zum KVNR-Fund wird, auch wenn die
   Stufe dadurch von „wahrscheinlich“ auf „fraglich“ sinken kann (nur mit 1b).
4. **H-1:** Zustimmung zu Prüfziffer-Vektoren mit anderen Buchstaben als `Z` (Ziffern `0000…`).
5. **Reihenfolge 1a → 1b → 2** (H-9) und die Rücknahmeregel T-R.

## Nachtrag der Hauptsitzung vom 23.11.2026: Entscheidungen zu den offenen Punkten
- **OF-1, OF-2:** erledigt, siehe Nachtrag in `anforderung.md` (Quelle der KVNR-Prüfziffer mit Testvektor; BSNR nach
  KBV-Richtlinie Stand 1. Januar 2024: keine Prüfziffer, 78 Schlüssel aus Anlage 1, Sonderkennungen `35`/`75` mit
  Schlüssel an den Stellen drei und vier, `99` = Netzverbundnummer gilt als unbekannter Code).
- **H-1:** angenommen. Die Prüfziffer-Vektoren (AK-6) dürfen andere Buchstaben als `Z` verwenden; die acht Ziffern
  beginnen weiter mit `0000`. Das Lehrbeispiel der Quelle (Buchstabe `A`, Ziffern eins bis acht aufsteigend,
  Prüfziffer 0) ist als einziger Vektor ohne `0000` zugelassen und steht nur in der zentralen Testklasse.
- **K-1 / R-2:** angenommen. Mit Teil 1b darf ein LANR-/IK-Fund über den Ziffern einer KVNR-Form zum breiteren
  KVNR-Fund werden, auch wenn die Stufe dabei von 0,80 auf 0,60 sinkt; die Position bleibt geschützt. Voraussetzung
  bleibt, dass die Oberfläche „fragliche" Funde nicht automatisch abwählt (Hinweis für das UI-Feature).
- **Reihenfolge:** 1a → 1b → 2 wie vorgeschlagen; Rücknahme von 1b nach D-5 über T-R, der Nutzer wird informiert.
- **Nachtrag zu T-1 (Befund des engineers, für T-9/T-10 verbindlich):** Die Annahme aus Kap. 5.2, die Bestandsmengen
  enthielten keine KVNR-Form, trifft für die weit gefasste `kvnrFormReferenz` (Großbuchstabe direkt vor genau neun
  Ziffern, ohne Wortgrenzen) nicht zu: 173 Texte (Teil 1c 58, 1d 27, 1e 44, Korpus 1, Generatoren 43), meist ein
  einzelnes `K` aus der Sperrschrift vor der Nummer. Entscheidung: Die weite Referenz und ihre Messwerte aus T-1
  bleiben unverändert. T-9 ergänzt in `KvnrBsnrHilfen` eine **enge** Referenz nach E-3/AK-12/AK-13 (Großbuchstabe
  mit Wortgrenze davor, genau neun Ziffern, Wortgrenze dahinter, gültige Prüfziffer, unabhängig vom Produkt
  geschrieben) und hält fest, wie viele Bestandstexte sie trifft. Ab T-10 gilt für S-3 und K-1/K-4 die enge
  Referenz als Bedingung „KVNR-Form"; Texte, die nur die weite Referenz trifft, bleiben streng geprüft. Trifft die
  enge Referenz Bestandstexte, sind das Kandidaten für D-5 (a) („0 zusätzliche KVNR-Funde auf den Bestandskorpora")
  und werden in T-9 einzeln ausgewiesen, nicht ausgenommen.
- **Nachtrag 2 zu T-1 (security-Befund „mittel", Entscheidung der Hauptsitzung; geht dem Nachtrag davor und der
  Tabelle in Kap. 5.2 vor, wo er strenger ist):** Die Prüfregel `bestandsabweichungen` wird verschärft.
  1. **K-4** gilt nur, wenn alle drei Bedingungen erfüllt sind: der neue Fund ist echt größer als der alte
     (`start` kleiner oder `ende` größer); sein Typ ist KVNR, BSNR oder der Typ eines alten Fundes, der ganz im
     neuen liegt; seine Konfidenz ist mindestens `min(alte Konfidenz, 0,40)`.
  2. **K-1** gilt nur, wenn die neue Konfidenz mindestens `min(alte Konfidenz, 0,50)` ist.
  3. **Örtliche Bedingung statt „im Text":** K-1 bis K-4 und die Ausnahme von S-3 verlangen das Kontextwort bzw. die
     KVNR-Form in der Nähe der Fundstelle: innerhalb des neuen Fundes oder höchstens 120 Zeichen vor seinem Anfang
     oder hinter seinem Ende. S-3 (volle Gleichheit) gilt für jeden alten und neuen Fund, in dessen Nähe (gleiches
     Fenster) weder Kontextwort noch Form steht. Erweist sich das Fenster später an einem plangemäßen Fall als zu
     eng, hält der engineer an und berichtet; er ändert es nicht selbst.
  4. **Enge KVNR-Form schon jetzt:** `KvnrBsnrHilfen` bekommt neben der weiten Referenz (die auf **genau** neun
     Ziffern korrigiert wird: keine zehnte Ziffer dahinter) eine enge Referenz nach E-3: Großbuchstabe A–Z, davor
     kein Buchstabe und keine Ziffer, genau neun Ziffern, dahinter kein Buchstabe und keine Ziffer, Prüfziffer nach
     A-1 gültig (eigene, vom Produkt unabhängige Rechnung im Testcode, abgesichert mit dem Lehrbeispiel der Quelle).
     Als Bedingung „KVNR-Form" für K-1, K-4 und S-3 gilt ab sofort die enge Referenz. Die weite Referenz bleibt nur
     als Messwert (Zahl der Bestandstexte) erhalten. Die Treffer der engen Referenz auf den Bestandsmengen werden
     je Menge exakt festgehalten.
  5. **Siegel über `ALT_TRENNER`:** je Bestandsmenge ein Siegel über die vollständige Ausgabe (alle Typen, alle
     Felder), damit eine Änderung an den übrigen Erkennern, am `TextScanner` oder am `ConflictResolver` auffällt.
  6. Die Prüfung „K-1 bis K-4 = 0 auf den Bestandsmengen" bleibt in allen Tasks bestehen. Auf den neuen Fuzz-Teilen
     1f bis 1h werden Kollisionsfälle nicht nur gezählt: jeder gezählte Fall muss zusätzlich die Bedingungen 1 bis 3
     erfüllen (das leistet die Prüfregel selbst).
- **Nachtrag 3 zu T-1 (security-Hinweis „niedrig" und reviewer-Mangel aus Durchlauf 2; verbindlich):**
  1. **K-4, Typ:** Ist der alte Fund kein KV-Typ (EMAIL, IP, URL, PFAD, TEXT …), muss der neue zusammengelegte Fund
     den Typ eines alten Fundes haben, der ganz in ihm liegt; KVNR/BSNR als neuer Typ ist dann kein K-4, sondern ein
     Verstoß. Für alte LANR-/IK-Funde bleibt KVNR/BSNR zulässig.
  2. **K-4, Ausdehnung:** Höchstens 24 Positionen des neuen Fundes dürfen von keinem alten Fund abgedeckt sein
     (Obergrenze für eine KVNR-/BSNR-Schreibweise mit Trennern). Größere Zuwächse sind ein Verstoß.
  3. **Ab dem ersten Task mit neuen Erkennern im Scanner (T-6, T-14) und auf den Fuzz-Teilen 1f bis 1h** wird jeder
     K-4-Fall zusätzlich unabhängig nachgerechnet: Kandidaten des alten Scanners und der neuen Erkenner mit
     `ConflictResolver.resolve` zusammenführen und Bereich, Typ und Konfidenz mit dem Produktfund vergleichen.
     Zusätzlich wird dort gezählt, wie viele Kollisionsfälle nur über verstreute Buchstaben im Fenster als „nahe"
     gelten (Kontextwort nicht als zusammenhängendes Wort im Fenster); der Wert wird exakt festgehalten.
  4. **Siegel über `ALT_TRENNER`:** Der Originalwert jedes Fundes geht in den Hash ein (eigene Hash-Variante, damit
     die Kopie-Siegel unverändert bleiben); zusätzlich wird die Invariante „Originalwert = Textausschnitt" auch für
     die alten Funde geprüft.
  5. Erweist sich eine dieser Grenzen später an einem plangemäßen Fall als zu eng, hält der engineer an und
     berichtet; er ändert sie nicht selbst.
- **Nachtrag zu T-3 (Entscheidung der Hauptsitzung):**
  1. **Einfrieren ab Tag `kb-t1-bestand`:** `KvnrBsnrHilfen`, `KvStandTrennerDifferenzTest`, `KvArtBestandTest`, die
     Kopie `KvNumberScannerStandTrenner` und `KvStandTrenner` bleiben unverändert (wie Regel 13). Wo der Plan
     Ergänzungen in `KvnrBsnrHilfen` vorsieht (`kvnrGueltigReferenz`, `assertKvnrSynthetisch`, spätere Hilfen wie
     die Nachrechnung über `ConflictResolver.resolve`), stehen sie in neuen Testklassen. Als unabhängige Referenz der
     Prüfziffer gilt `KvnrBsnrHilfen.kvnrPruefziffer`; der Laufzeit-Wächter steht in `KvnrSynthetischWaechter`.
     Einzige Ausnahme vom Einfrieren: Der Plan verlangt für einen späteren Task ausdrücklich eine neue Textmenge
     (Fuzz 1f bis 1h) in `KvStandTrennerDifferenzTest` – das geschieht dann nur ergänzend in einer neuen Testklasse
     mit denselben Prüfregeln, nicht durch Ändern der eingefrorenen.
  2. **Lehrbeispiel:** steht in der zentralen Klasse aus Teilen zusammengesetzt, weil der Bestandstest
     `ak28_zentraleKlasse_jedeNeunstelligeFolgeBeginntMit00` unverändert bleibt.
  3. **Wächter-Art `KVNR_FORM`:** greift dort, wo die Ausnahmeliste eine Kette sonst zuließe; eine ohnehin gemeldete
     Kette behält ihre bisherige Art (keine Umstellung bestehender Assertions).
  4. **Absicherung der Wortlänge:** `KvNumberScanner` wirft beim Aufbau eine `IllegalStateException` mit fester
     Meldung, wenn ein Kontext- oder Vorwort länger als `MAX_KONTEXTWORT` ist (security-Hinweis aus T-2).
- **Nachtrag zu T-4 (security-Befund „mittel" und Hinweise, Entscheidung der Hauptsitzung; verbindlich):**
  1. **Unsichtbare Zeichen vor dem Buchstaben der KVNR** (z. B. U+200B, U+00AD, U+FEFF, U+2060, U+200E; dieselbe
     Menge „unsichtbar" wie im Vorgänger) dürfen die Erkennung nicht verhindern: Nur für die Buchstabenform gilt ein
     Lauf aus unsichtbaren Zeichen, auf den genau ein ASCII-Buchstabe und eine Ziffer (bzw. ein Trenner nach D-1 und
     eine Ziffer) folgen, als Buchstabenanfang. Die unsichtbaren Zeichen gehören nicht zum Fund; der Fund beginnt beim
     Buchstaben. Eine laufende Sperr- oder Weit-Folge endet davor. Das gilt hinter jedem Kontextwort aus D-2, hinter
     `KV-Nr.` und hinter gesperrten Kontextwörtern. Der Vorgriff bleibt in der Summe linear.
  2. **G-K5 wird geschlossen, soweit ohne Nebenwirkung möglich:** Steht der Sperr-Verfolger im Zustand „Trenner" und
     ergibt der Sperrpuffer zusammen mit dem aktuellen Buchstaben ein Kontextwort der Art, wird der Buchstabe als
     Sperr-Buchstabe verarbeitet und nicht als Buchstabenanfang; die reine Ziffernfolge dahinter wird dann nach D-3
     ein Fund mit 0,40. Vorrang hat die echte KVNR-Form: `K V N R Z<Ziffern>` und `K V N R Z <Ziffern>` müssen
     weiter als Buchstabenform gefunden werden. Lässt sich beides nicht zugleich sauber erfüllen, bleibt G-K5 als
     dokumentierte Grenze (Test in T-8), und der engineer berichtet.
  3. **Bleiben dokumentierte Grenzen (Tests `bekannteGrenze…` in T-8):** Kontextwort direkt am Buchstaben ohne
     Trenner (`KVNR` unmittelbar gefolgt von Buchstabe und Ziffern); Trenner außerhalb D-1 zwischen Buchstabe und
     Ziffern (Tab, zwei Leerzeichen, Doppelpunkt mit Leerzeichen, `_`, `,`, Halbgeviertstrich, Zeilenumbruch) →
     höchstens ein Fund 0,40 über die Ziffern, hinter `KV-Nr.` kein Fund.
- **Nachtrag zu T-5 (Entscheidung der Hauptsitzung):** Nachgestelltes Kontextwort mit Wort davor
  (`<KVNR> (Patienten-KVNR)`, auch hinter Komma oder Semikolon) ergibt keinen Fund über das nachgestellte
  Kontextwort; das ist dieselbe Regel wie bei LANR/IK („Wort dazwischen: kein Fund") und bleibt unverändert, weil
  eine Lockerung nur für die KVNR die gemeinsame Regel aufspalten würde. Die gültige KVNR-Form wird in diesem Fall
  durch Teil 1b (ohne Kontextwort, 0,60) geschützt; entfällt 1b nach D-5, bleibt es eine dokumentierte Grenze.
  T-8 hält die Form als Test `bekannteGrenze…` fest (Test aus T-5 genügt, wenn er so benannt bzw. dort verwiesen
  wird).
- **Nachtrag 2 zu T-5 (security-Befund „mittel"; ersetzt den Nachtrag zu T-5 davor):** Die Begründung des ersten
  Nachtrags war falsch: Teil 1b schützt nur die geschlossene, großgeschriebene, gültige KVNR-Form (G-K1), nicht die
  gruppierte oder kleingeschriebene und keine Nummer mit falscher Prüfziffer. Entscheidung („möglichst viel
  schützen", Patientendaten):
  1. **Nachgestelltes Kompositum wird Kontextwort – nur für die KVNR (`art.buchstabenform()`):** Steht hinter einer
     offenen Nummer ein Wort, auf das unmittelbar ein Bindestrich und unmittelbar danach ein Kontextwort aus D-2
     (bzw. ein D-2-Vorwort mit `Nr.`/`Nummer`) folgt – `Patienten-KVNR`, `Pat.-KVNR`, `eGK-Versichertennummer` –,
     dann zählt das Ganze als ein nachgestelltes Kontextwort. Das Wort davor ist kein Füllwort und leert die offenen
     Nummern nicht. Es gelten dieselben Stufen wie beim einfachen nachgestellten Kontextwort (Klammer oder ohne:
     0,80; hinter Komma oder Semikolon: 0,60). Genau ein Wort vor dem Bindestrich (Buchstaben, optional ein
     Abkürzungspunkt direkt vor dem Bindestrich); zwei Wörter (`des Patienten-KVNR`) bleiben „Wort dazwischen: kein
     Fund". Der Vorgriff ist auf die Fenstergröße begrenzt (linear). Für LANR und IK ändert sich nichts (volle
     Gleichheit mit der eingefrorenen Kopie bleibt Pflicht); BSNR entscheidet T-13 entsprechend.
  2. Lässt sich Punkt 1 nicht klar abgeschirmt und ohne Nebenwirkung auf die vorangestellte Form umsetzen, hält der
     engineer an; dann wird die Form als G-K-Eintrag in Kap. 11 mit Test `bekannteGrenze…` (gültige geschlossene,
     gruppierte und kleingeschriebene Form) dokumentiert.
  3. **G-K4 ergänzt:** Zwei Buchstaben direkt an gruppierten Ziffern (`AB` + Zifferngruppen ohne Trenner dazwischen)
     liegen außerhalb D-1; es kann ein Teilfund 0,40 über die Gruppen nach der ersten entstehen, der erste Block
     samt Buchstaben bleibt stehen. Tests halten das als `bekannteGrenze…` fest, ohne den Klartextrest als Soll für
     künftige Erweiterungen festzuschreiben (geprüft wird: kein Fund enthält einen Buchstaben; die Fundzahl und der
     Bereich des tatsächlichen Verhaltens stehen im Test, der Kommentar nennt es ausdrücklich eine Grenze).
- **Nachtrag 3 zu T-5 (für T-8, bekannte Grenzen):** T-8 hält zusätzlich als Tests `bekannteGrenze…` fest und nimmt
  in Kap. 11 bzw. das Javadoc auf: (a) nachgestelltes Kontextwort – einfach oder Kompositum – in der **nächsten
  Zeile** hinter der Nummer (`<Nummer>` + Zeilenumbruch + `KVNR` bzw. `Patienten-KVNR`) → kein Fund über das
  nachgestellte Kontextwort; das ist die Regel der Vorgänger (ein Zeilenumbruch leert die offenen Nummern) und gilt
  für alle Arten gleich; (b) die in T-5 als Test festgehaltenen Restgrenzen des Kompositums (unsichtbares Zeichen
  hinter dem Strich, andere Striche U+2010/U+2011/U+2013, Leerraum am Strich, doppelte Zeichen, Variantenwähler
  hinter dem Kontextwort). Die geschlossene, großgeschriebene, gültige Form wird in diesen Stellungen durch Teil 1b
  geschützt, die übrigen Schreibweisen nicht.
- **Nachtrag zu T-6 (Befund des engineers, Entscheidung der Hauptsitzung; verbindlich):** Steht hinter einem
  KVNR-Kontextwort ein Buchstabe unmittelbar (oder mit einem Trenner) vor einer Ziffernfolge in IPv4-Gestalt, darf
  der bisherige IP-Fund weder den Typ wechseln noch schwächer werden (Nachtrag 3 zu T-1 Punkt 1 bleibt: KVNR als
  neuer Typ eines Nicht-KV-Altfundes ist ein Verstoß; K-1 wird NICHT auf IP erweitert).
  1. **Produktregel, nur für die Buchstabenform (`art.buchstabenform()`):** Besteht die Ziffernfolge hinter dem
     Buchstaben aus genau vier durch je einen Punkt getrennten Gruppen von je ein bis drei Ziffern (IPv4-Gestalt),
     gehört der Buchstabe nicht zur Kette: Es entsteht kein Kandidat in Buchstabenform; die Ziffernfolge wird wie
     eine reine Ziffernfolge behandelt (hinter einem KVNR-Kontextwort höchstens 0,40 `kvnr-form-abweichend`, hinter
     `KV-Nr.` kein Kandidat). Bei gleichem Bereich gewinnt dann nach der bestehenden Konfliktregel der IP-Fund
     (0,85). Die Prüfung ist ein Vorgriff fester Länge (höchstens 15 Zeichen), also linear.
  2. Dasselbe gilt, wenn die IPv4-Gestalt nur der Anfang einer längeren Kette ist (weitere Ziffern oder Gruppen
     dahinter): maßgeblich ist, dass direkt hinter dem Buchstaben (bzw. Buchstabe + ein Trenner) vier Punkt-Gruppen
     von je ein bis drei Ziffern stehen.
  3. Nebenwirkung (bewusst): Eine KVNR, deren neun Ziffern mit Punkten in genau vier Gruppen geschrieben sind,
     verliert ihren Buchstaben im Fund (Fund 0,40 nur über die Ziffern bzw. IP-Fund). Diese Schreibweise ist für
     eine KVNR nicht üblich; sie wird als bekannte Grenze dokumentiert (Test `bekannteGrenze…` in T-8, hier schon
     als Direkttest).
  4. Tests: die bisher aus der Kollisionsmenge ausgenommenen Texte (Kontextwort + Buchstabe + IPv4-Gestalt, auch
     längere Ketten) werden aufgenommen; die Prüfregel muss für sie Verstöße 0 und keinen Kollisionsfall melden
     (alter IP-Fund bleibt in Bereich, Typ und Konfidenz erhalten oder wird nur durch K-4 mit Typ IP abgedeckt).
     Der KVNR-Erkenner allein liefert für diese Texte keinen Fund in Buchstabenform.
  5. Geht das nicht klar abgeschirmt (LANR/IK unverändert, übrige KVNR-Formen unverändert): anhalten und berichten.
- **Nachtrag 2 zu T-6 (security-Befund „mittel"; Entscheidung der Hauptsitzung; verbindlich, geht K-4 in Kap. 5.2
  und den Nachträgen 2/3 zu T-1 vor):** Die IPv4-Gestalt-Regel allein reicht nicht: Reine Ziffernfolgen und
  Buchstabenformen hinter einem KVNR-Kontextwort umschließen oder überlappen weiterhin IP-Funde (CIDR-Schreibweise
  `IP/Präfix`, IP + Leerzeichen + Ziffern, IP-Ketten mit langem erstem Block) sowie E-Mail-, Pfad- und URL-Funde;
  diese wechseln dann den Typ zu KVNR oder werden beim Zusammenlegen schwächer.
  1. **Grundsatz „Bestand hat Vorrang":** Funde der Typen EMAIL, IP, URL und PFAD bleiben durch die neuen Arten
     (KVNR, später BSNR) **vollständig unverändert** (Bereich, Typ, Konfidenz, Quelle, Originalwert). Ein
     Zusammenlegen mit oder ein Ersetzen durch einen KVNR-/BSNR-Kandidaten findet nicht statt. K-4 ist damit für
     Nicht-KV-Altfunde kein zulässiger Fall mehr; zulässig bleiben nur K-1 bis K-3 und K-4 **zwischen KV-Arten**
     (LANR, IK, KVNR, BSNR).
  2. **Umsetzung im `TextScanner`, nicht im `ConflictResolver` (Regel 6 bleibt):** Vor der Konfliktauflösung werden
     Kandidaten der „nachrangigen" Erkenner (derzeit nur `KvnrRecognizer`; die Eigenschaft wird am Erkenner bzw. in
     `standard()` festgelegt, sodass T-14 den BSNR-Erkenner nur eintragen muss) verworfen, wenn sie sich mit
     mindestens einem Kandidaten der Typen EMAIL, IP, URL oder PFAD überschneiden (mindestens eine gemeinsame
     Position). Kandidaten von LANR und IK werden nicht gefiltert (Bestandsverhalten bleibt exakt). Aufwand:
     Sortieren der Kandidaten und ein gemeinsamer Durchlauf, also O(n log n) in der Zahl der Kandidaten, kein
     wiederholtes Abtasten des Textes; die Laufzeittests (1 Mio. Zeichen < 2 s) gelten weiter.
  3. **Bewusste Nebenwirkung (bekannte Grenze, Test in T-8, hier schon als Direkttest):** Überschneidet eine echte
     KVNR mit Kontextwort teilweise einen E-Mail-, IP-, URL- oder Pfad-Fund, entfällt der KVNR-Fund; der Teil der
     Nummer außerhalb des anderen Fundes bleibt dann stehen. Liegt die Nummer ganz im anderen Fund (z. B. in einem
     Dateinamen oder Pfad), bleibt sie über diesen geschützt, nur mit dessen Typ.
  4. Die IPv4-Gestalt-Regel aus dem ersten Nachtrag zu T-6 bleibt bestehen (sie verhindert, dass der Buchstabe vor
     einer IP als Teil einer Nummer gelesen wird; mit Punkt 2 ist sie für den Bestandsschutz nicht mehr allein
     tragend).
  5. **Tests:** (a) gezielte Texte für IP mit `/Präfix`, IP + Leerzeichen + Ziffern, IP-Kette mit langem erstem
     Block, E-Mail/URL/Pfad/Dateiname mit Buchstabe + Ziffern davor, darin und dahinter, jeweils hinter KVNR-
     Kontextwort, `KV-Nr.`, gesperrtem Kontextwort und mit nachgestelltem Kompositum – erwartet: jeder alte
     Nicht-KV-Fund steht unverändert in der neuen Liste; Prüfregel: Verstöße 0. (b) ein Eigenschaftstest über
     zufällige Mischtexte (fester Seed, mindestens 20 000 Texte, Bausteine aller Typen und KVNR-Kontext in der
     Nähe): für jeden Text ist die Teilliste der Nicht-KV-Funde von `TextScanner.standard()` **gleich** der des
     alten Scanners (`ALT_TRENNER`); Zähler mit Mindestzahlen für „Text mit verworfenem KVNR-Kandidaten",
     Selbsttest (Scanner ohne Filter wird gemeldet). (c) Typwechsel bei Nicht-KV-Alttypen wird unabhängig von K-4
     gezählt und ist strikt 0. (d) Die Nachrechnung `KvnrKollisionsNachrechnung` wird an die Filterstufe angepasst
     und im Javadoc ehrlich beschrieben (sie sichert die Zusammensetzung von `standard()` und die Gleichheit der
     LANR-/IK-Erkenner mit der Kopie, nicht die Zulässigkeit eines Falls).
  6. **Fenster der Prüfregel (security-Hinweis 3):** LANR-/IK-Funde können bei langen Aufzählungen auch dann nach
     K-1/K-2 zu KVNR werden, wenn das Kontextwort mehr als 120 Zeichen entfernt steht; die eingefrorene Prüfregel
     meldet das als Verstoß. Die Prüfregel bleibt eingefroren. Für die Fuzz-Teile 1f bis 1h (T-7 ff.) gilt: Ein
     von `bestandsabweichungen` gemeldeter Verstoß wird in einer neuen Testhilfe nachgeprüft; ist er ein K-1- oder
     K-2-Fall nach allen übrigen Bedingungen (alter Typ LANR/IK, Bereich wie K-1/K-2, Konfidenzgrenzen) und steht
     ein Kontextwort der neuen Art irgendwo davor im selben Absatz (keine Leerzeile dazwischen), zählt er als
     „fensterfern zulässig" (exakt festgehalten, Mindest- und Höchstzahl), sonst bleibt er ein Verstoß. Auf den
     acht Bestandsmengen bleibt alles strikt 0.
  7. **Für Kap. 11 (T-8):** Der Pseudonymizer ersetzt nur gefundene Stellen; steht derselbe Wert an einer zweiten,
     nicht erkannten Stelle (z. B. Kontextwort an das Vorwort geklebt), bleibt er dort stehen. Das ist der im
     Blueprint für eine spätere Ausbaustufe vorgesehene „Leak-Check" und nicht Teil dieses Features.
- **Nachtrag 3 zu T-6 (security-Befund „mittel" zur Nebenwirkung aus Nachtrag 2 Punkt 3; Entscheidung der
  Hauptsitzung; verbindlich, ersetzt Nachtrag 2 Punkt 2 Satz 1 und Punkt 3):** Das vollständige Verwerfen eines
  überschneidenden KVNR-Kandidaten lässt in realistischen Schreibweisen Ziffern einer echten KVNR im Klartext
  (z. B. Kontextwort, dann ein Pfad ohne Anführungszeichen, dessen Dateiname `Buchstabe Leerzeichen neun Ziffern`
  enthält: der Pfad-Fund endet am Buchstaben, die Ziffern dahinter bleiben stehen). Der Grundsatz „Bestand hat
  Vorrang" (Nachtrag 2 Punkt 1) bleibt unverändert; geändert wird nur, was mit dem nachrangigen Kandidaten
  geschieht:
  1. **Zuschneiden statt Verwerfen:** Ein Kandidat eines nachrangigen Erkenners, der Bestandsbereiche (vereinigte
     Bereiche der Kandidaten EMAIL/IP/URL/PFAD) überschneidet, wird auf die Teile außerhalb dieser Bereiche
     zugeschnitten. Jedes Reststück wird an beiden Enden auf ASCII-Buchstaben und Ziffern gekürzt (Trenner,
     Leerraum, Satzzeichen und unsichtbare Zeichen am Rand entfallen). Ein Reststück wird als Fund behalten, wenn
     es **mindestens drei Ziffern** enthält; sonst entfällt es (ein einzelner Buchstabe oder eine ein- bis
     zweistellige Zahl wie die Präfixlänge hinter einer IP ist kein Fund). Liegt der Kandidat ganz im Bestand,
     entfällt er wie bisher.
  2. **Felder des Restfunds:** Typ der Art (KVNR), Originalwert = Textausschnitt, Quelle `kvnr-rest`, Konfidenz =
     Konfidenz des Kandidaten, höchstens 0,80 (die vollständige Form ist nicht mehr belegt). Restfunde grenzen an
     Bestandsfunde nur an und überschneiden sie nie; die Bestandsfunde bleiben in allen Feldern unverändert.
     Zwischen Restfunden und LANR-/IK-Kandidaten gilt die normale Konfliktauflösung.
  3. Aufwand weiter O(n log n) in der Zahl der Kandidaten (ein Kandidat kann höchstens so viele Reststücke haben,
     wie Bestandsbereiche in ihm liegen; die Summe ist durch die Zahl der Bestandsbereiche plus Kandidaten
     beschränkt).
  4. **Bekannte Grenze (Test `bekannteGrenze…`, T-8 übernimmt sie nach Kap. 11):** Der Teil einer Nummer, der im
     Bestandsfund liegt, trägt dessen Typ; ein einzelner Buchstabe oder ein Rest mit weniger als drei Ziffern
     außerhalb bleibt stehen.
  5. **Tests:** Direkttests mit Pfad (Laufwerk, UNC, Unix), `file:`-URL und URL-Query, in denen die Nummer als
     Buchstabe + Leerzeichen bzw. geschütztes Leerzeichen + neun Ziffern (auch gruppiert) steht, hinter
     KVNR-Kontext: der alte Fund bleibt unverändert, die Ziffern außerhalb sind durch einen Restfund abgedeckt;
     zusätzlich die Form „Kontextwort Nummer, Datei <Pfad mit derselben Nummer>". Test für den **angrenzenden**
     Kandidaten (Ende des Bestandsfunds = Anfang des Kandidaten und umgekehrt): bleibt vollständig erhalten
     (security: ein Off-by-one-Mutant blieb bisher unentdeckt). Test, dass für `TextScanner.standard()` genau der
     KVNR-Erkenner nachrangig ist (T-14 ergänzt BSNR). Der Eigenschaftstest prüft zusätzlich: jede Position, die
     der KVNR-Erkenner allein in einem Kandidaten mit mindestens drei Ziffern außerhalb des Bestands abdeckt, ist
     von irgendeinem Fund abgedeckt, soweit das Reststück die Drei-Ziffern-Schwelle erreicht (Zähler, Mindestzahl,
     Selbsttest mit der Verwerfen-Variante).
  6. **Testdaten:** Frei erfundene Ziffernfolgen, auch über verkettete Literale verteilt, sind unzulässig
     (reviewer/security zu `KvnrKollisionsTexte`): Texte werden aus Konstanten oder Teilstücken von
     `SynthetischeKvNummern` gebildet; fehlt eine passende Konstante, wird sie dort angelegt.
- **Nachtrag 4 zu T-6 (Präzisierung zu Nachtrag 3 Punkt 1, vom engineer vorgelegt und von der Hauptsitzung
  angenommen):** „Bestandsbereiche" sind die Bereiche der **Funde** der Typen EMAIL, IP, URL und PFAD, die die
  Konfliktauflösung über die Kandidaten der nicht nachrangigen Erkenner liefert (also genau die Funde des Scans
  ohne die neuen Arten, einschließlich der dort zusammengelegten), nicht die rohen Kandidaten. Grund: Ein Restfund
  konnte sonst über einen LANR-/IK-Kandidaten, der selbst einen Bestandskandidaten überlappt, in eine Kette
  geraten und den zusammengelegten Bestandsfund verlängern oder schwächen (15 von 24 000 Zufallstexten). Der
  `TextScanner` löst dafür zuerst die nicht nachrangigen Kandidaten auf, schneidet die nachrangigen Kandidaten an
  diesen Funden zu und löst dann alle zusammen auf; der Aufwand bleibt O(n log n). `Recognizer` hat neben
  `nachrangig()` die Default-Methode `restQuelle()` (für die KVNR `kvnr-rest`; T-14: `bsnr-rest`).
- **Nachtrag 5 zu T-6 (security-Hinweise „niedrig" aus der Abschlussprüfung von T-6; für T-7/T-8):**
  1. **Fensterfernes K-3 durch Restfund:** Ein Restfund (höchstens 0,80) kann einen alten LANR-/IK-Fund mit
     niedrigerer Konfidenz im selben Bereich übernehmen, auch wenn das KVNR-Kontextwort (als Listenkontext) mehr
     als 120 Zeichen entfernt steht; die eingefrorene Prüfregel zählt das als Verstoß (1 Fall in 500 000
     Kettentexten). Es bleibt kein Klartext. Die Regel aus Nachtrag 2 zu T-6 Punkt 6 („fensterfern zulässig") gilt
     deshalb für K-1, K-2 **und K-3**.
  2. **Drei-Ziffern-Schwelle:** ein Buchstabe oder ein bis zwei Ziffern einer Nummer können neben einem
     Bestandsfund stehen bleiben (bekannte Grenze, Nachtrag 3 Punkt 4); T-8 hält dazu auch den Fall „hinterer Teil
     einer gruppierten Nummer wird vom lokalen Teil einer E-Mail geschluckt" fest.
  3. **Bekannte Grenze der Kontextregel (nicht durch T-6 entstanden, Kap. 11 / T-8):** Steht zwischen Kontextwort
     und Nummer ein langer Pfad oder eine lange URL (ab etwa 24 Zeichen), entsteht kein KVNR-Kandidat; die Nummer
     dahinter bleibt ungeschützt, solange Teil 1b sie nicht findet (geschlossene, großgeschriebene, gültige Form).
     T-8 legt dazu einen Test `bekannteGrenze…` an und stellt die genaue Ursache fest (Füllwortzählung oder
     Fenster).
  4. Javadoc von `Recognizer#nachrangig` und `KvnrRecognizer` sprechen noch von „Kandidaten" statt von den
     aufgelösten Bestandsfunden (Nachtrag 4); T-7 gleicht das an. T-8 ergänzt Direkttests für das Kürzen der
     Ränder (Nicht-ASCII-Ziffer, Surrogatpaar, unsichtbares Zeichen).
- **Nachtrag zu T-7 (Befund des engineers im Fuzz 1f, Entscheidung der Hauptsitzung):** Folge von G-K7 (Plan
  Kap. 4.2: hinter `KV-Nr.`/`KV-Nummer` zählt eine reine Ziffernfolge als Füllwort): Ist der Kontext eines
  KV-Vorworts noch aktiv und steht hinter einer reinen Ziffernfolge (6 bis 12 Ziffern) ein nachgestelltes
  vollwertiges KVNR-Kontextwort (`KV-Nr. … <Ziffern> (KVNR)`), bleibt die Ziffernfolge ohne Fund, weil sie schon
  als Füllwort verbraucht ist. Entscheidung: **dokumentierte Grenze, keine Produktänderung.** Begründung: Betroffen
  ist nur eine reine Ziffernfolge ohne Buchstaben, also keine KVNR-Form; ohne das `KV-Nr.` davor entstünde ein Fund
  der niedrigsten Stufe (0,40). Die Regel „`KV-Nr.` gilt nur für die Buchstabenform" (D-2, G-K7) soll verhindern,
  dass Nummern der KV selbst als Versichertennummer gelten; sie für diesen seltenen Fall aufzuweichen, würde die
  Scannerlogik in einem Test-Task ändern. Im Fuzz 1f wird der Fall als Markierungsklasse `kv-vorwort-ziffern`
  geführt (Regel aus dem Text: Block ist eine reine Ziffernfolge, und im selben Absatz davor steht ein KV-Vorwort
  mit `Nr`/`Nummer`), exakt gezählt. T-8 legt den Test `bekannteGrenze…` dazu an und nimmt den Fall in Kap. 11 und
  das Javadoc auf. K-3 kommt im Fuzz 1f nicht vor (exakt 0, kein Mindestwert).
- **Nachtrag 2 zu T-7 (aus der Prüfung von T-7; Entscheidungen der Hauptsitzung):**
  1. **Genaue Ursache der Grenze „Pfad/URL zwischen Kontextwort und Nummer" (korrigiert Nachtrag 5 zu T-6 Punkt 3;
     für T-8 verbindlich):** Nicht die Länge ist maßgeblich, sondern zwei Regeln des Scanners: (a) jedes Pfad- oder
     URL-Segment zählt als Füllwort, ab dem vierten ist der Kontext erloschen; (b) der Punkt der Dateiendung hinter
     einem Wort mit mehr als vier Buchstaben gilt als Satzende. Ohne Fund bleiben deshalb auch kurze Fälle (Pfad mit
     vier Segmenten, Pfad mit zwei Segmenten und Dateiendung, jede URL mit Schema); abgedeckt bleiben Pfade mit
     höchstens drei Segmenten ohne Endungspunkt. T-8 dokumentiert das so in Kap. 11 und im Javadoc und legt den
     Test `bekannteGrenze…` mit kurzem Pfad, Dateiendung und URL an. Die geschlossene, großgeschriebene, gültige
     Form fängt Teil 1b; gruppierte und kleingeschriebene Formen bleiben in dieser Stellung ungeschützt.
  2. **Keine Behebung in diesem Feature.** Ein Prototyp von security (zweiter Lauf des KVNR-Erkenners mit durch ein
     neutrales Wort ersetzten Bestandsbereichen) deckt die Fälle ab, verletzt aber den Bestandsschutz (sechs Tests
     rot). Die Grenze wird als Kandidat für ein eigenes Folgefeature („Kontext über Pfade und URLs hinweg", mit
     Prüfung durch security) im Abschlussbericht des Features genannt; dasselbe gilt für „Kontextwort direkt am
     Buchstaben der Nummer" (Nachtrag zu T-4 Punkt 3), bei der ebenfalls eine vollständige KVNR-Form stehen bleibt.
  3. **Markierungsklasse `kv-vorwort-ziffern` enger fassen (reviewer):** zusätzlich muss gelten, dass zwischen dem
     letzten KV-Vorwort und dem Block kein vollwertiges KVNR-Kontextwort steht und höchstens drei Füllwörter sowie
     höchstens ein Zeilenumbruch liegen (das Füllwortfenster des Scanners; Nummern und Satzzeichen dazwischen sind
     wie im Scanner zu behandeln, aus dem Text bestimmt und nicht aus dem Erkenner), und das nachgestellte
     Kontextwort muss zum Block gehören (unmittelbar hinter der Nummer in Klammern bzw. hinter Komma/Semikolon,
     ohne weitere Nummer dazwischen).
  4. **Ausnahme der Schutzgarantie im Fuzz (reviewer):** In Texten, in denen ein nachrangiger Kandidat einen
     Bestandsfund schneidet, ist nicht jede Lücke befreit, sondern nur, was Nachtrag 3 zu T-6 zusagt: Jede
     zusammenhängende, von keinem Fund abgedeckte Strecke innerhalb des Kandidaten und außerhalb des Bestands
     enthält weniger als drei ASCII-Ziffern.
  5. **Für T-11 (Fuzz 1g):** Bestand-Bausteine auch mit gültiger Nummer und mit einem Reststück von genau drei
     Ziffern, damit Mutanten der Restfund-Schwelle (3 → 4) und des Konfidenzdeckels (0,80 → 0,95) auch im Fuzz
     auffallen (bisher nur in den T-6-Tests).
- **Nachtrag 3 zu T-7 (security, Richtigstellung zu Nachtrag 2 Punkt 1/2; für T-8 und den Abschlussbericht
  verbindlich):** Der Satz „die geschlossene, großgeschriebene, gültige Form fängt Teil 1b" gilt nur bedingt.
  Ohne Einschränkung zu dokumentieren ist: (a) **Pfad/URL zwischen Kontextwort und Nummer:** Solange Teil 1b nicht
  gebaut ist oder falls er nach D-5 zurückgenommen wird, bleibt in dieser Stellung jede KVNR-Form ohne Fund, auch
  die geschlossene; mit Teil 1b bleiben die gruppierte und die kleingeschriebene Form sowie Nummern mit falscher
  Prüfziffer ohne Fund. (b) **Kontextwort direkt am Buchstaben der Nummer:** Hier schützt auch Teil 1b nicht, weil
  er vor dem Buchstaben eine Wortgrenze verlangt (Kap. 4.3); jede KVNR-Form bleibt ohne Fund. T-8 schreibt beides
  so in Kap. 11, ins Javadoc und in die Tests `bekannteGrenze…`. Der Abschlussbericht legt dem Nutzer beide Grenzen
  ausdrücklich als Restrisiko vor (mit dem Vorschlag eines Folgefeatures); die Abnahme des Features setzt voraus,
  dass der Nutzer dieses Restrisiko kennt.
- **Vermerk (23.11.2026):** Der Nutzer hat das in Nachtrag 3 zu T-7 beschriebene Restrisiko (Pfad/URL zwischen
  Kontextwort und Nummer; Kontextwort direkt am Buchstaben) zur Kenntnis genommen („ja das ist ok so dann bitte
  weiter"). Es bleibt Gegenstand des Abschlussberichts samt Vorschlag eines Folgefeatures.

## Nachtrag zu Kap. 11 (T-8): Bekannte Grenzen der KVNR nach Teil 1a
Tests in `KvnrGrenzenTest`, sofern nicht anders genannt. Diese Liste ergänzt und präzisiert Kap. 11.
- **G-K1** (Teil 1a): Eine KVNR ohne Kontextwort ist in jeder Schreibweise kein Fund. Test `bekannteGrenzeGK1_…`.
- **G-K2:** gilt nur mit Teil 1b; in Teil 1a nicht testbar, belegt durch den Fehlalarm-Prüfstand von Teil 1b (bzw.
  T-R, falls 1b entfällt).
- **G-K3:** zwei Buchstaben, Nicht-ASCII-Buchstaben, vollbreite/arabische Ziffern, `KVID`, `VKNR`,
  `Versicherungsnummer`, `Krankenversicherungs-Nr.` → kein Fund. Test `bekannteGrenzeGK3_…`.
- **G-K4** (ergänzt): Ziffern direkt am Wort → kein Fund; zwei Buchstaben direkt an gruppierten Ziffern → Teilfund
  0,40 über die Gruppen nach der ersten. Tests `KvnrKontextTest#bekannteGrenzeGK4_…`, `bekannteGrenzeGK4_…`.
- **G-K5** (Rest): `K V N R` direkt vor Ziffern sowie Sperrung mit Punkt + Leerzeichen oder mehreren Leerzeichen,
  gefolgt von einem Leerzeichen und einer reinen Ziffernfolge → kein Fund. Test `bekannteGrenzeGK5_…`.
- **G-K6, G-K7:** Tests `bekannteGrenzeGK6_…`, `bekannteGrenzeGK7_…`. **G-K7-Folge:** `KV-Nr. … <Ziffern> (KVNR)`
  bleibt ohne Fund (Nachtrag zu T-7). Test `bekannteGrenzeGK7_folge_…`.
- **G-K8** (ergänzt): unsichtbares Format-Zeichen zwischen Buchstabe und Ziffern → höchstens 0,40 über die Ziffern;
  ein Variantenwähler direkt am Buchstaben → gar kein Fund. Test `bekannteGrenzeGK8_…`.
- **G-K9** (neu, Restrisiko): Kontextwort direkt am Buchstaben der Nummer (auch mit unsichtbarem Zeichen dazwischen)
  → jede Schreibweise ohne Fund bzw. höchstens 0,40 über die Ziffern der gruppierten Form; auch Teil 1b schützt
  hier nicht (Wortgrenze vor dem Buchstaben). Test `bekannteGrenzeGK9_…`.
- **G-K10** (neu): Trenner außerhalb D-1 zwischen Buchstabe und Ziffern → höchstens 0,40 über die Ziffern, hinter
  `KV-Nr.` kein Fund. Test `bekannteGrenzeGK10_…`.
- **G-K11** (neu): nachgestelltes Kontextwort (einfach oder Kompositum) in der nächsten Zeile → kein Fund.
  Test `bekannteGrenzeGK11_…`.
- **G-K12** (neu): Restgrenzen des Kompositums. Test `KvnrKontextTest#bekannteGrenze_kompositum_restgrenzen…`.
- **G-K13** (neu): KVNR in genau vier Punkt-Gruppen verliert den Buchstaben im Fund.
  Test `KvnrKontextTest#bekannteGrenze_kvnrInVierPunktGruppen…`.
- **G-K14** (neu): Bestand hat Vorrang – der Teil der Nummer im anderen Fund trägt dessen Typ; ein Buchstabe oder ein
  Rest mit weniger als drei Ziffern bleibt stehen. Tests `TextScannerKvnrTest#bekannteGrenze_nummerUeberschneidet…`,
  `bekannteGrenzeGK14_…`.
- **G-K15** (neu, Restrisiko): Pfad oder URL zwischen Kontextwort und Nummer → kein Fund in jeder Schreibweise.
  Ursache: jedes Segment zählt als Füllwort (ab dem vierten ist der Kontext erloschen), und der Punkt hinter einem
  Wort mit mehr als vier Buchstaben (Dateiendung, Host) gilt als Satzende. Abgedeckt bleiben Pfade mit höchstens
  drei Segmenten ohne solchen Punkt und sehr kurze URLs ohne Punkt im Host mit höchstens drei Segmenten.
  Tests `bekannteGrenzeGK15_pfadOderUrl…`, `bekannteGrenzeGK15_ursachen_…`.
- **G-K16** (neu): derselbe Wert an zweiter, nicht erkannter Stelle bleibt stehen (kein Leak-Check).
  Test `bekannteGrenzeGK16_…`.
- **G-K17** (neu): U+034F, U+180B, U+E0100 bis U+E01EF, U+115F, U+3164, U+FFA0 vor dem Buchstaben → kein Fund.
  Test `bekannteGrenzeGK17_…`.
- **G-D2** (präzisiert): `LANR`/`IK` vor einer KVNR-Form schützt nur die Ziffern; mit KVNR-Kontextwort davor umfasst
  der Fund Buchstabe und Ziffern (K-1). Test `bekannteGrenzeGD2_…`.
- **Restrisiko, vom Nutzer zur Kenntnis genommen:** G-K15 und G-K9 (Kandidaten für ein Folgefeature mit Prüfung
  durch security).
- **Präzisierungen zum Nachtrag zu Kap. 11 (security, T-8):**
  - **G-K10:** Satzzeichen mit Satzende-Wirkung (`!`, `?`) zwischen Buchstabe und Ziffern oder zwischen Kontextwort
    und Nummer ergeben gar keinen Fund (nicht „höchstens 0,40").
  - **G-K15:** Bei URLs zählen das Schema und jedes Host-Label als Segment; abgedeckt bleibt nur eine sehr kurze URL
    ohne Punkt im Host mit Schema und höchstens zwei weiteren Segmenten. Jede URL mit Domain (Punkt im Host), mit
    zwei Pfadsegmenten oder mit Query bleibt ohne Fund. Mit Teil 1b bleiben weiterhin die gruppierte und die
    kleingeschriebene Form sowie Nummern mit falscher Prüfziffer ohne Fund.
  - **G-K17 (allgemeiner):** Jedes Kombinationszeichen (Kategorien Mn, Mc, Me – darunter U+034F, U+180B, die
    Variantenwähler, U+E0100 bis U+E01EF) sowie die Füllzeichen U+115F, U+1160, U+3164 und U+FFA0 direkt vor dem
    Buchstaben der Nummer oder zwischen Buchstabe und Ziffern verhindern den Fund in Buchstabenform; Buchstabe und
    Ziffern können dann vollständig stehen bleiben.
  - **G-K15, Richtigstellung (am Produkt gemessen, T-8 Durchlauf 2):** „Jede URL mit Domain bleibt ohne Fund" war
    zu streng formuliert. Maßgeblich sind allein die beiden Ursachen: Abgedeckt bleibt eine URL aus Schema und
    höchstens zwei weiteren Segmenten (Host-Labels und Pfadsegmente zählen einzeln), ohne Punkt hinter einem Wort
    mit mehr als vier Buchstaben und ohne Query – also etwa ein Host aus zwei kurzen Labels ohne Pfad. Jede URL mit
    einem längeren Host-Label vor einem Punkt, mit insgesamt mehr als drei Segmenten oder mit Query bleibt ohne
    Fund; das trifft praktisch alle realen URLs.
  - **G-K15, Geltung der Richtigstellung (security, T-8 Durchlauf 2):** Die Richtigstellung ersetzt **beide**
    älteren Formulierungen weiter oben in diesem Nachtrag – „ohne Punkt im Host" ebenso wie „Jede URL mit Domain
    bleibt ohne Fund". Gemessen ergeben auch Hosts aus zwei kurzen Labels (bis vier Buchstaben vor dem Punkt), mit
    oder ohne abschließenden Schrägstrich, einen Fund. Maßgeblich ist allein die Richtigstellung.
  - **Vorgemerkt aus security T-8 (niedrig, kein FAIL-Grund), beim nächsten Task mit Änderung an diesen Dateien
    mitzunehmen:** (a) Javadoc des ersten G-K15-Tests in `KvnrGrenzenTest` präzisieren („URL mit Schema" →
    Fassung der Richtigstellung); (b) zwei eigene Lastfälle in `GrenzlastTest`: „gesperrtes Kontextwort +
    Buchstabe + IP" und „Lauf unsichtbarer Zeichen + IP" (bisher nur indirekt abgedeckt, Laufzeit linear gemessen).
- **Nachtrag zu T-9 (Entscheidung der Hauptsitzung, 23.11.2026; geht der Task-Beschreibung T-9 vor):**
  1. `KvnrBsnrHilfen` ist seit T-1 eingefroren (Nachtrag zu T-3). Alles, was T-9 dort ergänzen sollte
     (`kvnrOhneKontextReferenz`, Fremdtext-Bausteine, künstliche Erkenner), steht in der **neuen** Klasse
     `test/…/recognizer/kv/KvnrOhneKontextHilfen.java`. Die enge Referenz `kvnrFormEng` aus `KvnrBsnrHilfen` wird
     nur gelesen. Regel 13 (Einfrieren) gilt ab Abnahme für `KvnrOhneKontextFehlalarmTest` und
     `KvnrOhneKontextHilfen`.
  2. **AK-16 (a), Fassung des Prüfstands:** Die enge Referenz trifft laut T-1 einzelne Bestandstexte (zufällig
     gültige Form). „0 zusätzliche KVNR-Funde" wird deshalb so geprüft: Auf den Bestandsmengen (Fuzz 1 bis 1e,
     Korpus ohne `kvnrFaelle()`, Generatoren, `TestTexte`) liegt **kein** KVNR-Fund von `standard()` außerhalb der
     Stellen, die `kvnrOhneKontextReferenz` trifft. Die Zahl der Referenztreffer wird je Menge exakt festgehalten
     (Konstante mit Istwert) und im Bericht ausgewiesen, getrennt nach „Ziffern schon durch einen Bestandsfund
     abgedeckt" und „bisher frei". Auf dem Stand nach T-8 ist die Zahl der KVNR-Funde zusätzlich genau 0.
     Begründung: Ein Fund auf einer vollständig gültigen KVNR-Form ist kein Fehlalarm im Sinn von D-5, sondern
     die gewollte Wirkung von Teil 1b; die Hauptsitzung bewertet die Zahlen in T-10 gegen die Nutzervorgabe
     („bei vermehrten Fehlalarmen lassen").
  3. Mengen, die absichtlich KVNR enthalten (Fuzz 1f, `kvnrFaelle()`, KVNR-Testklassen), gehören nicht zu den
     Bestandsmengen von (a).
- **Nachtrag 2 zu T-9 (Entscheidung der Hauptsitzung nach Bericht des engineers):**
  1. `KvnrOhneKontextFehlalarmTest` liegt im Paket `de.skillshield.core` (nicht `recognizer/kv`), weil er die
     paketprivaten Generatoren der Bestandsmengen braucht und bestehende Dateien nicht geöffnet werden sollen.
     Angenommen; `KvnrOhneKontextHilfen` bleibt in `recognizer/kv`.
  2. Die Aussage „auf dem Stand nach T-8 genau 0 KVNR-Funde" steht nicht im eingefrorenen Test (sie gilt ab T-10
     nicht mehr), sondern wurde einmalig gemessen und wird von security/qa bei der Abnahme von T-9 nachgemessen.
  3. Messwerte T-9 (enge Referenz auf den Bestandsmengen): 13 Treffer (Fuzz 1c 5, 1d 1, 1e 5, Generatoren 2),
     davon 12 mit bereits durch einen Bestandsfund abgedeckten Ziffern und 1 bisher frei. Das ist die
     Obergrenze der zusätzlichen KVNR-Funde, die T-10 auf dem Bestand erzeugen darf.
- **Nachtrag 3 zu T-9 (zweiter Durchlauf, Entscheidung der Hauptsitzung):** Der Prüfstand wurde vor dem Einfrieren
  um die security-Hinweise erweitert (155 Fremdmuster; Achter-/Zehnerformen mit passender Prüfziffer;
  Nicht-ASCII-Buchstaben und Kombinationszeichen an der Wortgrenze; Nicht-ASCII-Großbuchstabe als Anfang; Tab,
  Zeilenumbruch und geschütztes Leerzeichen im Negativkontext; (c) auch über `standard()`; Ausnahme in (a) nur für
  Quelle `kvnr-ohne-kontext` mit 0,60; Funde je Menge ≤ Referenztreffer). **Bewusst nicht festgelegt:** eine
  Nicht-ASCII-Ziffer direkt neben der Form. Kap. 4.3 sagt nur „Ziffer", A-3 nennt Nicht-ASCII-Ziffern „keine
  KVNR-Zeichen"; beide Lesarten (sperrt / sperrt nicht) sind in T-10 zulässig, der Prüfstand prüft den Fall nicht.
  T-10 dokumentiert die gewählte Lesart im Javadoc.
- **Nachtrag zu T-10 (Entscheidung der Hauptsitzung, 23.11.2026; geht der Task-Beschreibung T-10 vor):**
  1. `KvStandTrennerDifferenzTest` und `KvnrBsnrHilfen` bleiben eingefroren (Tag `kb-t1-bestand`); die in T-10
     vorgesehene Methode `…OhneKontext…` (S-1 bis S-3 mit enger Referenz) steht in der **neuen** Klasse
     `test/…/KvnrOhneKontextBestandTest.java` (Paket `de.skillshield.core`) und gehört zum 1b-Inventar (Regel 14).
  2. Ab Tag `kb-t9-pruefstand` eingefroren: `KvnrOhneKontextFehlalarmTest`, `KvnrOhneKontextHilfen`, in
     `SynthetischeKvNummern` die Methoden `fremdKvnr`/`zufallsKvnr` (Regel 13, sinngemäß für die neue Hilfsklasse).
  3. **D-5 (a) in der Fassung des Nachtrags zu T-9:** zulässig sind auf den Bestandsmengen höchstens die 13
     Funde auf den Referenzstellen (Quelle `kvnr-ohne-kontext`, 0,60). Wird dadurch ein eingefrorener oder
     bestehender Test rot (z. B. `KvStandTrennerDifferenzTest`, Siegel/Hashes der Fuzz-Teile 1c/1d/1e, Korpus-,
     Generator-Tests), ändert der engineer **nichts** an diesen Tests, sondern hält an und meldet Testname und
     Zahl; die Hauptsitzung entscheidet (Regel 15).
  4. Nicht-ASCII-Ziffer als Nachbar der Form: Lesart frei (Nachtrag 3 zu T-9), im Javadoc zu dokumentieren.
- **Nachtrag 2 zu T-10 – Entscheidung zu D-5 / Regel 15 (Hauptsitzung, 23.11.2026):**
  Der engineer hat nach Regel 15 angehalten: Produkt 1b fertig, 9 bestehende Tests rot. Messwerte gegen den
  eingefrorenen Prüfstand (alle grün): (a) 13 KVNR-Funde auf den Bestandsmengen, alle auf den Referenzstellen
  (vollständig gültige Form, Quelle `kvnr-ohne-kontext`, 0,60), 0 außerhalb; 12 davon ersetzen einen LANR-/IK-Fund
  auf denselben Ziffern (K-1/R-2, in Kap. 2 angenommen), 1 Stelle war bisher frei; (b) 0 Funde auf 159
  Fremdmustern; (c) 9 947 von 100 000 (Grenze 10 500, rechnerische Erwartung 10 000).
  **Entscheidung: Teil 1b bleibt.** Begründung nach der Nutzervorgabe „wenn es vermehrt zu Fehlalarmen führt,
  sollten wir es lassen": Es gibt keine vermehrten Fehlalarme – kein Fund auf Fremdmustern, kein Fund außerhalb
  einer vollständig gültigen Form, die Zufallsquote liegt im rechnerisch unvermeidbaren Bereich und unter der
  Grenze. Die 9 roten Tests halten den alten Zustand exakt fest und schlagen genau wegen der gewollten Wirkung an.
  Vom Wortlaut von D-5 (a) („0 zusätzliche KVNR-Funde") wird damit bewusst abgewichen; dem Nutzer wird das
  berichtet.
  **Zulässige Anpassung der 9 Tests (eng, sonst nichts):**
  1. Nur Erwartungswerte bzw. eine eng gefasste Ausnahme; keine Textliste, kein Generator, kein Seed, kein Hash,
     keine Referenz, keine Prüfregel in `KvnrBsnrHilfen` wird geändert.
  2. Die Ausnahme gilt ausschließlich für Abweichungen, deren neuer Fund Typ KVNR, Quelle `kvnr-ohne-kontext` und
     Konfidenz 0,60 hat und zehn Zeichen umfasst (ein Großbuchstabe + die neun Ziffern des alten Fundes); ihre Zahl
     wird je Menge **exakt** festgehalten (K-1: Teil 1c 5, 1d 1, 1e 5, Generatoren 1; frei: Generatoren 1). Jede
     andere Abweichung bleibt ein Fehler.
  3. Fuzz 1f: exakte Messwerte nachziehen (K-1 1532 → 1533; markierte Blöcke ohne Abdeckung 77 → 60, mit
     Aufschlüsselung je Markierung); Seeds und `HASH_TEIL_1F` bleiben.
  4. Für `KvStandTrennerDifferenzTest` (Tag `kb-t1-bestand`) ist dies die einzige zugelassene Änderung an einer
     eingefrorenen Datei; alle anderen eingefrorenen Dateien bleiben unverändert. security prüft den Diff der 9
     Tests Zeile für Zeile.
  5. Jede geänderte Stelle trägt einen Kommentar mit Verweis auf diesen Nachtrag und dem alten Wert.
- **Nachtrag 3 zu T-10 (zweiter Durchlauf, angenommen):** Die Ausnahme-Logik steht in der neuen Testhilfe
  `recognizer/kv/KvnrOhneKontextAusnahme` (gehört zum 1b-Inventar). In `KvStandLueckenDifferenzTest` ist die
  Vergleichsbasis der ältere Stand ohne Trenner; dort teilen sich die 5 Funde aus Teil 1e in 1 „ersetzt" und 4
  „frei" (dieselben 5 Funde, gegen `ALT_TRENNER` 5 „ersetzt"). Fuzz 1f: K-1 1533; markierte Blöcke ohne
  Abdeckung 60 (`kw-am-buchstaben` 20, `langer-pfad` 38, `kv-vorwort-ziffern` 2), mit Abdeckung 22 (alle
  `langer-pfad`) – 1b schließt damit G-K15 teilweise, wenn die Nummer geschlossen und großgeschrieben ist.
- **Nachtrag 4 zu T-10 (Ergänzung des 1b-Inventars, Regel 14, auf Hinweis des reviewers):** Zum 1b-Inventar gehören
  zusätzlich `test/…/recognizer/kv/KvnrOhneKontextAusnahme.java`, `test/…/KvnrOhneKontextBestandTest.java` und die
  mit „Nachtrag 2 zu T-10" markierten Stellen in `KvStandTrennerDifferenzTest`, `KvnrScannerBestandTest`,
  `KvStandLueckenDifferenzTest`, `KvnrFuzzBestandTest`, `RoundtripFuzzTest`. Bei einer Rücknahme (T-R) werden
  diese Stellen auf den im Kommentar genannten alten Wert zurückgesetzt. Regel 12 Satz 3 gilt mit dieser
  erweiterten Liste.
- **Nachtrag zu T-11 (Entscheidung der Hauptsitzung, 23.11.2026; geht der Task-Beschreibung T-11 vor):**
  1. `KvStandTrennerDifferenzTest` bleibt eingefroren. Siegel/Messwerte für Teil 1g (K-1 als exakter Messwert mit
     Mindestzahl, S-1 bis S-3) stehen in der **neuen** Klasse `test/…/KvnrOhneKontextFuzzBestandTest.java`
     (Paket `de.skillshield.core`, 1b-Inventar); Muster wie `KvnrFuzzBestandTest`.
  2. Teil 1g enthält zusätzlich die seit T-6 vorgemerkten **Bestand-Bausteine**: gültige KVNR-Form unmittelbar an
     bzw. in einem EMAIL-/IP-/URL-/PFAD-Fund, darunter der Fall „Rest genau drei Ziffern" (`MIN_REST_ZIFFERN`);
     Erwartung nach dem Grundsatz „Bestand hat Vorrang" (Bestandsfund unverändert, Roundtrip zeichengleich, kein
     Klartext der Nummer außerhalb eines Fundes, soweit die Zuschneide-Regel einen Restfund vorsieht).
  3. Mitzunehmen aus security T-8 (vorgemerkt): (a) Javadoc des ersten G-K15-Tests in `KvnrGrenzenTest` auf die
     Fassung der Richtigstellung bringen; (b) zwei Lastfälle in `GrenzlastTest`: „gesperrtes Kontextwort +
     Buchstabe + IP" und „Lauf unsichtbarer Zeichen + IP". G-K1 und G-K15 in `KvnrGrenzenTest` an den Stand mit
     1b anpassen (Regel 12: betroffene „kein Fund"-Zeilen wurden so gebaut, dass 1b sie nicht trifft – prüfen),
     dazu die neuen `bekannteGrenzeOhneKontext…`-Tests (klein, Trenner, Wortgrenze, D-4-Wort davor, ungültige
     Prüfziffer, Nicht-ASCII-Ziffer als Nachbar).
  4. Eingefroren bleiben außerdem der Prüfstand T-9 (Tag `kb-t9-pruefstand`) und alle bestehenden
     Seeds/Hashes/Siegel/Mindestzahlen. Wird ein bestehender Test rot: nicht anpassen, anhalten, melden.
