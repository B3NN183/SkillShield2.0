package de.skillshield.ui.logik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.SynthetischeKvNummern;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.Reidentifier;
import de.skillshield.ui.logik.Attrappen.TestDialoge;
import de.skillshield.ui.logik.Attrappen.TestZwischenablage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Eigenschaftstest gegen den {@link Pseudonymizer} des Kerns (Architektur-Plan Kap. 3.4, Schritt 7): Fester Seed,
 * viele zufaellige Markierungen ueber synthetische Texte mit E-Mail, IP, KVNR, Emoji, Leerraum und Wiederholungen.
 * Nach jedem Hinzufuegen gilt: Liste sortiert und ueberlappungsfrei, jeder Fund passt zum Text, der Pseudonymizer
 * wirft nie, die Rueckuebersetzung liefert den Eingabetext zeichengenau, jedes Vorkommen der markierten
 * Zeichenfolge liegt vollstaendig in angewaehlten Funden (Nachtrag zu T-3), kein vorher angewaehlter Bereich wird
 * ungeschuetzt, und die Auswahl der uebrigen Zeilen bleibt. Laufzeit: wenige Sekunden.
 */
class ManuellerFundEigenschaftTest {

    private static final long SEED = 424_242L;
    private static final int DURCHLAEUFE = 2_400;
    private static final int SITZUNGSDURCHLAEUFE = 400;

    private static final String EMOJI = new String(Character.toChars(0x1F600));

    private static final List<String> TEXTE = List.of(
            "Kontakt mustermann@example.org, Server 192.0.2.10 und Max Mustermann. Max Mustermann schreibt erneut.",
            "Gruss " + EMOJI + " an Max " + EMOJI + " Mustermann, Mail a@example.net, a@example.net, "
                    + EMOJI + EMOJI,
            "aaaa bbbb aaaa Aaaa ab ab ab, Host 203.0.113.5, aaaaaaaa",
            "KVNR " + SynthetischeKvNummern.KVNR_GUELTIG + "\tMuster   Muster" + (char) 0xA0
                    + "Muster\n\nMuster-Muster",
            "x");

    private record Lauf(String text, List<Finding> basis) { }

    private static List<Lauf> laeufe() {
        List<Lauf> laeufe = new ArrayList<>();
        TextScanner scanner = TextScanner.standard();
        for (String text : TEXTE) {
            laeufe.add(new Lauf(text, scanner.scan(text)));
        }
        return laeufe;
    }

    private static void pruefeListe(String text, List<FundZeile> zeilen, Random zufall) {
        int ende = 0;
        List<Finding> alle = new ArrayList<>();
        for (FundZeile zeile : zeilen) {
            Finding fund = zeile.fund();
            assertTrue(fund.start() >= ende, "sortiert und ueberlappungsfrei");
            assertEquals(text.substring(fund.start(), fund.end()), fund.originalValue());
            ende = fund.end();
            alle.add(fund);
        }
        // Pseudonymizer wirft nie: mit allen Funden und mit einer zufaelligen Auswahl.
        roundtrip(text, alle);
        List<Finding> auswahl = new ArrayList<>();
        for (Finding fund : alle) {
            if (zufall.nextBoolean()) {
                auswahl.add(fund);
            }
        }
        roundtrip(text, auswahl);
    }

    private static void roundtrip(String text, List<Finding> funde) {
        PseudonymizationResult ergebnis = new Pseudonymizer().pseudonymize(text, funde);
        assertEquals(text, new Reidentifier().restore(ergebnis.text(), ergebnis.mapping()).text());
    }

    /** Je Zeichen des Textes: liegt es in einem angewaehlten Fund? Linear in Text und Fundzahl. */
    private static boolean[] geschuetzt(String text, List<FundZeile> zeilen) {
        boolean[] gedeckt = new boolean[text.length()];
        for (FundZeile zeile : zeilen) {
            if (zeile.angewaehlt()) {
                Arrays.fill(gedeckt, zeile.fund().start(), zeile.fund().end(), true);
            }
        }
        return gedeckt;
    }

    /**
     * Nach dem Hinzufuegen liegt jedes Vorkommen der markierten Zeichenfolge (auch sich ueberlappende; ohne
     * Treffer, die ein Surrogatpaar trennen) vollstaendig in angewaehlten Funden. Die Pruefung geht ueber
     * Positionen im Originaltext und kann deshalb nicht an Platzhaltertexten scheitern.
     */
    private static void pruefeAlleVorkommenGeschuetzt(String text, int start, int ende, boolean[] gedeckt) {
        int von = start > 0 && Character.isHighSurrogate(text.charAt(start - 1))
                && Character.isLowSurrogate(text.charAt(start)) ? start - 1 : start;
        int bis = ende < text.length() && ende > 0 && Character.isHighSurrogate(text.charAt(ende - 1))
                && Character.isLowSurrogate(text.charAt(ende)) ? ende + 1 : ende;
        String wert = text.substring(von, bis);
        for (int p = von; p < bis; p++) {
            assertTrue(gedeckt[p], "markierte Stelle vollstaendig geschuetzt");
        }
        int treffer = text.indexOf(wert);
        while (treffer >= 0) {
            int trefferEnde = treffer + wert.length();
            boolean trenntPaar = treffer > 0 && Character.isHighSurrogate(text.charAt(treffer - 1))
                    && Character.isLowSurrogate(text.charAt(treffer))
                    || trefferEnde < text.length() && Character.isHighSurrogate(text.charAt(trefferEnde - 1))
                    && Character.isLowSurrogate(text.charAt(trefferEnde));
            if (!trenntPaar) {
                for (int p = treffer; p < trefferEnde; p++) {
                    assertTrue(gedeckt[p], "Vorkommen an " + treffer + " steht im Klartext");
                }
            }
            // ab treffer + 1: auch sich ueberlappende Vorkommen (Nachtrag 2 zu T-3)
            treffer = text.indexOf(wert, treffer + 1);
        }
    }

    /** Zufaellige Markierung: oft kurz, manchmal lang, manchmal leer, auch mitten in Surrogatpaaren. */
    private static int[] markierung(Random zufall, int laenge) {
        int start = zufall.nextInt(laenge + 1);
        int breite = switch (zufall.nextInt(6)) {
            case 0 -> 0;
            case 1, 2 -> 1 + zufall.nextInt(3);
            case 3, 4 -> 1 + zufall.nextInt(8);
            default -> zufall.nextInt(laenge + 1);
        };
        return new int[] {start, Math.min(laenge, start + breite)};
    }

    @Test
    void zufaelligeMarkierungenErgebenImmerEineGueltigeListeUndEinenZeichengenauenRoundtrip() {
        Random zufall = new Random(SEED);
        List<Lauf> laeufe = laeufe();
        int markierungen = 0;
        int angenommen = 0;
        for (int i = 0; i < DURCHLAEUFE; i++) {
            Lauf lauf = laeufe.get(i % laeufe.size());
            String text = lauf.text();
            List<FundZeile> zeilen = new ArrayList<>();
            for (Finding fund : lauf.basis()) {
                zeilen.add(new FundZeile(fund, zufall.nextInt(4) != 0, null));
            }
            int schritte = 1 + zufall.nextInt(3);
            for (int s = 0; s < schritte; s++) {
                int[] m = markierung(zufall, text.length());
                ManuellerFund.Ergebnis ergebnis = ManuellerFund.hinzufuegen(text, zeilen, m[0], m[1]);
                markierungen++;
                if (ergebnis.abgelehnt()) {
                    assertEquals(zeilen, ergebnis.zeilen());
                    assertEquals(0, ergebnis.hinzugefuegt());
                    continue;
                }
                angenommen++;
                // Verschmelzen kann neue Funde zusammenfassen: hoechstens eine Zeile je hinzugefuegter Stelle.
                assertTrue(ergebnis.zeilen().size() <= zeilen.size() - ergebnis.ersetzt() + ergebnis.hinzugefuegt());
                assertTrue(ergebnis.zeilen().size() >= zeilen.size() - ergebnis.ersetzt() + 1);
                assertTrue(ergebnis.hinzugefuegt() >= 1);
                boolean[] gedeckt = geschuetzt(text, ergebnis.zeilen());
                pruefeAlleVorkommenGeschuetzt(text, m[0], m[1], gedeckt);
                // Kein vorher angewaehlter Bereich ist danach ungeschuetzt.
                boolean[] vorher = geschuetzt(text, zeilen);
                for (int p = 0; p < vorher.length; p++) {
                    assertTrue(!vorher[p] || gedeckt[p], "vorher geschuetztes Zeichen " + p + " ungeschuetzt");
                }
                // Auswahl der uebrigen Zeilen bleibt, neue Zeilen sind angewaehlt.
                for (FundZeile zeile : ergebnis.zeilen()) {
                    if (zeile.fund().type() == FindingType.TEXT && Finding.SOURCE_MANUAL.equals(zeile.fund().source())
                            && !zeilen.contains(zeile)) {
                        assertTrue(zeile.angewaehlt());
                    }
                }
                for (FundZeile alt : zeilen) {
                    if (ergebnis.zeilen().stream().anyMatch(z -> z.fund() == alt.fund())) {
                        assertTrue(ergebnis.zeilen().contains(alt), "bestehende Zeile unveraendert");
                    }
                }
                pruefeListe(text, ergebnis.zeilen(), zufall);
                zeilen = new ArrayList<>(ergebnis.zeilen());
            }
        }
        assertTrue(markierungen >= 2_000, "Vorbedingung: genug Markierungen, war " + markierungen);
        assertTrue(angenommen > markierungen / 2, "Vorbedingung: die meisten Markierungen werden angenommen");
    }

    @Test
    void ueberDieSitzungWirftDerPseudonymizerNieUndTextFuerKiPasstZumKern() {
        Random zufall = new Random(SEED + 1);
        List<Lauf> laeufe = laeufe();
        for (int i = 0; i < SITZUNGSDURCHLAEUFE; i++) {
            Lauf lauf = laeufe.get(i % laeufe.size());
            String text = lauf.text();
            Sitzung sitzung = new Sitzung(t -> lauf.basis(), new TestZwischenablage(), new TestDialoge());
            Attrappen.scannen(sitzung, text);
            int schritte = 1 + zufall.nextInt(3);
            for (int s = 0; s < schritte; s++) {
                int[] m = markierung(zufall, text.length());
                sitzung.markierungHinzufuegen(m[0], m[1]);
                if (!sitzung.zeilen().isEmpty() && zufall.nextInt(3) == 0) {
                    sitzung.anwaehlen(zufall.nextInt(sitzung.zeilen().size()), zufall.nextBoolean());
                }
            }
            List<FundZeile> zeilen = sitzung.zeilen();
            List<Finding> gewaehlt = zeilen.stream().filter(FundZeile::angewaehlt).map(FundZeile::fund).toList();
            PseudonymizationResult kern = new Pseudonymizer().pseudonymize(text, gewaehlt);
            assertEquals(kern.text(), sitzung.textFuerKi());
            assertEquals(text, new Reidentifier().restore(sitzung.textFuerKi(), kern.mapping()).text());
            assertFalse(sitzung.statusleiste().meldung().contains("Mustermann"));
            for (FundZeile zeile : zeilen) {
                assertEquals(zeile.angewaehlt(), zeile.platzhalter() != null);
            }
        }
    }

    @Test
    void selbsttest_dieTexteHabenFundeUndEmojiDamitDerTestWirklichWasPruft() {
        List<Lauf> laeufe = laeufe();
        assertTrue(laeufe.get(0).basis().size() >= 2, "E-Mail und IP erkannt");
        assertTrue(laeufe.get(3).basis().size() >= 1, "KVNR erkannt");
        assertTrue(TEXTE.get(1).codePoints().anyMatch(Character::isSupplementaryCodePoint));
    }
}
