# Anhang: Entwurf IK/LANR aus der Vorfassung (für das Folgefeature)

Status: **nicht Teil des Features `text-pseudonymisierung-kern`.** Gesichert aus der Vorfassung von `architektur.md`
(15 Tasks, damals T-10/T-11), weil die Vorfassung nicht im Git-Repository lag. Wird im Folgefeature IK/LANR als
Ausgangspunkt wiederverwendet und dort neu bewertet. Nutzerentscheidung für das Folgefeature (aus der Anforderung,
Abgrenzung): beide Verfahren mit Sekundärquellen-Stand und Vermerk "nicht offiziell verifiziert"; ungültige Prüfziffer
ergibt Stufe "fraglich" (nicht verwerfen).

Abhängigkeiten zum Kern dieses Features: `Finding`, `FindingType` (neue Konstanten `IK`, `LANR`), `Recognizer`,
`TextScanner.standard()`, Konfidenzschwellen aus `ConfidenceLevel`. Für den Hinweis "Prüfziffer passt nicht" braucht
das Folgefeature ein zusätzliches Feld `note` in `Finding` (im Kern bewusst weggelassen, keine Vorratsabstraktion).

## Klassen (Paket `de.skillshield.core.recognizer.kv`)

```java
@FunctionalInterface
public interface CheckDigit {
    /** @param digits genau 9 ASCII-Ziffern */
    boolean isValid(String digits);
}
public final class LanrCheckDigit implements CheckDigit { … }  // Quellenvermerk siehe unten
public final class IkCheckDigit   implements CheckDigit { … }  // Quellenvermerk siehe unten
public final class KvNumberRecognizer implements Recognizer {
    public static KvNumberRecognizer lanr();       // Label "LANR", LanrCheckDigit, gültig 0.95
    public static KvNumberRecognizer ik();         // Label "IK",   IkCheckDigit,   gültig 0.92
    static final double CONFIDENCE_PRUEFZIFFER_UNGUELTIG = 0.50;   // -> FRAGLICH, note "Prüfziffer passt nicht …"
    KvNumberRecognizer(FindingType type, String label, CheckDigit checkDigit, double confidenceValid);
}
```

Muster: Lookbehind `(?<![\p{L}\p{N}])`, Label `(?i:LANR)` bzw. `(?i:IK)`, `[ \t]{0,5}+(?:[:=][ \t]{0,5}+)?`,
Wert `[0-9]{9}` in eigener Gruppe, Lookahead `(?![0-9])`. Fund = nur die 9 Ziffern; Label bleibt im Text
(`IK [[IK_1]]`). Label ist Pflicht.

## Prüfziffern (Sekundärquellen-Stand)

| Verfahren | Stand laut Sekundärquelle | Offizielle Quelle (nicht eingesehen) |
|-----------|---------------------------|--------------------------------------|
| LANR | 9 Stellen; 1–6 Arztnummer, 7 Prüfziffer, 8–9 Fachgruppe. Ziffern 1–6 von links abwechselnd × 4 und × 9, Summe mod 10, Prüfziffer = 10 − Rest, Ergebnis 10 → 0. Stellen 8–9 werden nicht geprüft. | KBV-Richtlinie nach § 75 Abs. 7 SGB V zur Vergabe der Arzt-, Betriebsstätten- und Praxisnetznummern |
| IK | 9 Stellen; 1–2 Klassifikation, 3–8 gehen in die Berechnung ein, 9 Prüfziffer. Gewichte ab Stelle 3: 2,1,2,1,2,1; je Produkt die Quersumme; Summe mod 10; **Rest = Prüfziffer** (Rechenbeispiel der Quelle). Lesart "Komplement" ist in anderen Sekundärquellen nicht ausgeschlossen. | "Gemeinsames Rundschreiben Institutionskennzeichen" der ARGE IK (DGUV) |

Pflichtvermerk in Klassen-Javadoc und Testklassen: "Quelle: <Sekundärquelle mit Titel/URL>, abgerufen am <Datum>.
Status: Sekundärquelle, nicht gegen die offizielle Spezifikation verifiziert. Offizielle Quelle: <Titel>, nicht
eingesehen." Titel/URL und Abrufdatum liefert die Hauptsitzung; der engineer recherchiert nicht selbst.

## Testvektoren

Zentral in einer Testklasse `SynthetischeKvNummern` (einzige Datei mit 9-stelligen Ziffernliteralen). Je Typ mindestens
1 gültiger und 3 ungültige Vektoren (Prüfziffer verschoben, Nachbarziffern vertauscht, eine Ziffer geändert), Rechenweg
als Kommentar. IK: mindestens ein Vektor, bei dem "Rest" und "Komplement" verschiedene Prüfziffern ergeben (Stellen 3–8
= `345678` ergibt Summe 30, beide Lesarten fallen zusammen, ungeeignet). Restrisiko: Eine konstruierte Nummer mit
gültiger Prüfziffer kann real vergeben sein; nie mit Namen, Adressen oder Diagnosen kombinieren.

## Damalige Tasks (Kurzform)

- Prüfziffernverfahren: `CheckDigit`, `LanrCheckDigit`, `IkCheckDigit`, Tests mit Vektoren und Vermerk.
- KV-Erkenner: `KvNumberRecognizer`, Aufnahme in `TextScanner.standard()`; Tests: Label-Varianten, ungültige Prüfziffer
  → `FRAGLICH`, 9 Ziffern ohne Label / 10 Ziffern / Label als Wortteil → kein Fund; `IK ` + 100 000 Ziffern unter 2 s.
- Offene Punkte damals: Quellenangaben (Titel/URL/Datum), Beschaffung der offiziellen Spezifikationen, Wortlaut des
  Prüfziffer-AK ("gegen benannte Sekundärquelle geprüft").
