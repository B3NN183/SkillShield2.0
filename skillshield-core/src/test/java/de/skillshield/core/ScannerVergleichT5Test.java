package de.skillshield.core;

import static de.skillshield.core.SynthetischeGeheimnisse.P1;
import static de.skillshield.core.SynthetischeGeheimnisse.P2;
import static de.skillshield.core.SynthetischeGeheimnisse.T;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.geheimnis.GeheimnisTypen;
import de.skillshield.core.recognizer.geheimnis.PasswortRecognizerStandT5;
import de.skillshield.core.recognizer.geheimnis.TokenRecognizer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Der {@code TextScanner} (Stand T-6) gegen den Scanner am Tag {@code ge-t5-schwache-verbinder}
 * (Plan geheimnisse-erkennung, T-6): Der T-5-Scanner besteht aus den acht bisherigen Erkennern von
 * {@code standard()} und der eingefrorenen Kopie {@link PasswortRecognizerStandT5}. Je Text und auf Positionen-Ebene
 * gilt:
 * <ol>
 * <li>keine Position, die der T-5-Scanner schuetzt, wird frei (Abdeckung T-5 ist eine Teilmenge der neuen);</li>
 * <li>die Funde der uebrigen Typen (nicht PASSWORT, nicht TOKEN) sind in beiden Scannern gleich;</li>
 * <li>was neu hinzukommt, liegt ganz in Kandidaten des {@link TokenRecognizer} ausserhalb dieser Funde;</li>
 * <li>liefert der {@code TokenRecognizer} fuer den Text keinen Kandidaten, ist die neue Fundliste feldgleich zu der
 * des T-5-Scanners.</li>
 * </ol>
 * Verglichen werden dieselben Mengen wie in {@link PasswortVergleichT5Test} (Bestandsmengen, Korpora, Generatoren,
 * Referenz-Ticket, Zufallslauf). Je Menge stehen die Zahlen der Texte mit Token-Kandidaten fest. Meldungen nennen nur
 * Menge, Index und Zahlen.
 */
class ScannerVergleichT5Test {

    private static final TextScanner NEU = TextScanner.standard();
    private static final TextScanner T5 = scannerOhneToken(new PasswortRecognizerStandT5());

    private static TextScanner scannerOhneToken(Recognizer passwort) {
        List<Recognizer> liste = new ArrayList<>(NEU.recognizers().subList(0, 8));
        liste.add(passwort);
        return new TextScanner(liste);
    }

    /** Ergebnis des Vergleichs einer Menge. */
    record Zahlen(int texte, int mitTokenKandidat, int mitUnterschied, int freiePositionen, List<String> verstoesse) {
    }

    private static BitSet abdeckung(List<Finding> funde) {
        BitSet bits = new BitSet();
        for (Finding f : funde) {
            bits.set(f.start(), f.end());
        }
        return bits;
    }

    private static List<Finding> ohneGeheimnis(List<Finding> funde) {
        return funde.stream().filter(f -> !GeheimnisTypen.istGeheimnistyp(f.type())).toList();
    }

    static Zahlen vergleiche(String menge, List<String> texte, TextScanner neu, TextScanner t5) {
        Recognizer token = new TokenRecognizer();
        int mitToken = 0;
        int mitUnterschied = 0;
        int frei = 0;
        List<String> verstoesse = new ArrayList<>();
        for (int i = 0; i < texte.size(); i++) {
            String text = texte.get(i);
            List<Finding> a = t5.scan(text);
            List<Finding> n = neu.scan(text);
            List<Finding> kandidaten = token.detect(text);
            BitSet abdA = abdeckung(a);
            BitSet abdN = abdeckung(n);
            BitSet verloren = (BitSet) abdA.clone();
            verloren.andNot(abdN);
            if (!verloren.isEmpty()) {
                frei += verloren.cardinality();
                verstoesse.add(menge + "#" + i + ": " + verloren.cardinality() + " Positionen frei geworden");
            }
            if (!ohneGeheimnis(a).equals(ohneGeheimnis(n))) {
                verstoesse.add(menge + "#" + i + ": Funde der uebrigen Typen unterscheiden sich");
            }
            BitSet neuHinzu = (BitSet) abdN.clone();
            neuHinzu.andNot(abdA);
            BitSet erlaubt = abdeckung(kandidaten);
            neuHinzu.andNot(erlaubt);
            if (!neuHinzu.isEmpty()) {
                verstoesse.add(menge + "#" + i + ": " + neuHinzu.cardinality() + " Positionen ohne Token-Kandidat neu");
            }
            if (kandidaten.isEmpty()) {
                if (!a.equals(n)) {
                    verstoesse.add(menge + "#" + i + ": ohne Token-Kandidat nicht feldgleich");
                }
            } else {
                mitToken++;
            }
            if (!a.equals(n)) {
                mitUnterschied++;
            }
        }
        return new Zahlen(texte.size(), mitToken, mitUnterschied, frei, verstoesse);
    }

    private static Zahlen vergleiche(String menge, List<String> texte) {
        return vergleiche(menge, texte, NEU, T5);
    }

    // ------------------------------------------------------------------ Mengen

    @Test
    void bestandsmengen_keinePositionWirdFrei_dieFundeDerUebrigenTypenSindGleich() {
        int gezaehlt = 0;
        int texte = 0;
        int mitToken = 0;
        int mitUnterschied = 0;
        for (Map.Entry<String, List<String>> e : PasswortVergleichT5Test.mengen().entrySet()) {
            if (gezaehlt >= 15) {
                break;
            }
            gezaehlt++;
            Zahlen z = vergleiche(e.getKey(), e.getValue());
            assertEquals(List.of(), z.verstoesse(), e.getKey());
            assertEquals(0, z.freiePositionen(), e.getKey() + ": freie Positionen");
            texte += z.texte();
            mitToken += z.mitTokenKandidat();
            mitUnterschied += z.mitUnterschied();
        }
        assertEquals(15, gezaehlt, "Zahl der Bestandsmengen");
        assertTrue(texte > 70_000, "zu wenige Texte: " + texte);
        assertEquals(0, mitToken, "Texte mit Token-Kandidat in den Bestandsmengen");
        assertEquals(0, mitUnterschied, "Texte mit Unterschied in den Bestandsmengen");
    }

    @Test
    void korporaGeneratorenUndTicket_keinePositionWirdFrei_zahlenJeMenge() {
        Map<String, List<String>> mengen = PasswortVergleichT5Test.mengen();
        Zahlen negativ = vergleiche("Negativkorpus", mengen.get("Negativkorpus"));
        assertEquals(List.of(), negativ.verstoesse());
        assertEquals(372, negativ.texte(), "Texte Negativkorpus");
        assertEquals(0, negativ.mitUnterschied(), "Unterschiede im Negativkorpus");
        Zahlen positiv = vergleiche("Positivkorpus", mengen.get("Positivkorpus"));
        assertEquals(List.of(), positiv.verstoesse());
        assertEquals(199, positiv.texte(), "Texte Positivkorpus");
        assertEquals(0, positiv.freiePositionen(), "freie Positionen im Positivkorpus");
        assertTrue(positiv.mitTokenKandidat() >= 22, "Texte mit Token-Kandidat " + positiv.mitTokenKandidat());
        Zahlen ng = vergleiche("Negativ-Generator", mengen.get("Negativ-Generator"));
        assertEquals(List.of(), ng.verstoesse());
        assertEquals(0, ng.mitUnterschied(), "Unterschiede Negativ-Generator");
        Zahlen vg = vergleiche("Verletzend-Generator", mengen.get("Verletzend-Generator"));
        assertEquals(List.of(), vg.verstoesse());
        assertEquals(0, vg.mitUnterschied(), "Unterschiede Verletzend-Generator");
        Zahlen pg = vergleiche("Positiv-Generator", mengen.get("Positiv-Generator"));
        assertEquals(List.of(), pg.verstoesse());
        assertEquals(100_000, pg.texte(), "Texte Positiv-Generator");
        assertEquals(33_337, pg.mitTokenKandidat(), "Texte mit Token-Kandidat im Positiv-Generator");
        assertEquals(33_337, pg.mitUnterschied(), "Unterschiede im Positiv-Generator (genau die TOKEN-Texte)");
        Zahlen ticket = vergleiche("Referenz-Ticket", mengen.get("Referenz-Ticket"));
        assertEquals(List.of(), ticket.verstoesse());
        assertEquals(0, ticket.mitUnterschied(), "Unterschiede im Referenz-Ticket");
    }

    @Test
    void zufallslauf_passwortUndTokenFormenGemischt_keinePositionWirdFrei() {
        PasswortVergleichT5Test.Zaehler z = new PasswortVergleichT5Test.Zaehler();
        List<String> texte = PasswortVergleichT5Test.zufallstexte(z);
        Zahlen zahlen = vergleiche("Zufall", texte);
        assertEquals(List.of(), zahlen.verstoesse());
        assertEquals(120_000, zahlen.texte(), "Texte");
        assertEquals(0, zahlen.freiePositionen(), "freie Positionen");
        assertTrue(zahlen.mitTokenKandidat() > 30_000, "Texte mit Token-Kandidat " + zahlen.mitTokenKandidat());
        assertTrue(zahlen.mitUnterschied() > 20_000, "Texte mit Unterschied " + zahlen.mitUnterschied());
        assertTrue(zahlen.mitUnterschied() <= zahlen.mitTokenKandidat(), "Unterschiede nur mit Token-Kandidat");
    }

    // ------------------------------------------------------------------ Gegenproben

    @Test
    void gegenprobe_ohnePasswortErkennerWerdenPositionenFrei_derVergleichMeldetSie() {
        TextScanner ohnePasswort = new TextScanner(NEU.recognizers().subList(0, 8));
        List<String> texte = List.of("Passwort: " + P1, "Token: " + T, "Passwort ist " + P2);
        Zahlen z = vergleiche("Probe", texte, ohnePasswort, T5);
        assertEquals(2, z.verstoesse().stream().filter(s -> s.contains("frei geworden")).count(),
                "Texte mit freien Positionen");
        assertEquals(P1.length() + P2.length(), z.freiePositionen(), "freie Positionen");
    }

    @Test
    void gegenprobe_einErkennerDerPositionenOhneTokenKandidatHinzufuegt_wirdGemeldet() {
        Recognizer fremd = text -> text.isEmpty() ? List.of()
                : List.of(new Finding(GeheimnisTypen.PASSWORT, text.substring(0, 1), 0, 1, 0.95, "x"));
        List<Recognizer> liste = new ArrayList<>(NEU.recognizers().subList(0, 8));
        liste.add(fremd);
        Zahlen z = vergleiche("Probe", List.of("abcdef"), new TextScanner(liste), T5);
        assertTrue(z.verstoesse().stream().anyMatch(s -> s.contains("ohne Token-Kandidat")), "gemeldet");
        assertTrue(z.verstoesse().stream().anyMatch(s -> s.contains("nicht feldgleich")), "gemeldet");
    }

    @Test
    void gegenprobe_identischeScanner_keinVerstoss() {
        Zahlen z = vergleiche("Probe", List.of("Passwort: " + P1 + " Token: " + T), T5, T5);
        assertEquals(List.of(), z.verstoesse());
        Zahlen echt = vergleiche("Probe", List.of("Passwort: " + P1), NEU, T5);
        assertEquals(List.of(), echt.verstoesse());
        assertEquals(0, echt.mitUnterschied(), "ohne Token gleich");
        Zahlen mitToken = vergleiche("Probe", List.of("Passwort: " + P1 + " Token: " + T), NEU, T5);
        assertEquals(List.of(), mitToken.verstoesse());
        assertEquals(1, mitToken.mitUnterschied(), "mit Token verschieden");
    }
}
