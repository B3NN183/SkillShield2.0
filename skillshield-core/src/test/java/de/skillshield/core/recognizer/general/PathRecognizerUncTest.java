package de.skillshield.core.recognizer.general;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.TextScanner;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests der Form UNC (T-12). Pfade in Testtexten werden mit {@code bs(...)} geschrieben (Plan Kap. 2.10, Marker
 * U+00A6 steht fuer einen Backslash); alle Namen sind synthetisch ({@code srv-beispiel}, {@code srv01.praxis.example},
 * IPs aus 192.0.2.0/24, {@code erika.mustermann}). Nicht-ASCII-Zeichen und unsichtbare Zeichen stehen nur als
 * Escapes im Quelltext.
 *
 * <p>Gliederung: (1) Zeile UNC des Plans (Kap. 2.4.2), positiv und negativ; (2) Anfuehrungszeichen; (3) eigene
 * Entscheidungen (Startgrenze, Backslash-Zahl, Praefixe), jeweils getrennt von den Plan-Faellen; (4) Abgrenzung zu
 * Laufwerkspfaden und Obermengen; (5) Unicode; (6) Grenzlast (AK-49).
 *
 * <p>Jeder Test prueft ueber {@link #funde} je Fund: Teilstring = Originalwert, Typ PFAD, Quelle {@code pfad-unc}
 * (bzw. {@code pfad-laufwerk} dort, wo ein Laufwerkskopf den Fund eroeffnet), Konfidenz 0.95 (SICHER; 0.60 FRAGLICH
 * nur bei UNC mit einem Segment, Plan Nachtrag T-12 Punkt 7), sortiert,
 * ueberlappungsfrei, Grenzen nicht in Surrogatpaar oder Kombinationsfolge.
 */
class PathRecognizerUncTest {

    private static final PathRecognizer R = new PathRecognizer();
    private static final TextScanner SCANNER = TextScanner.standard();
    private static final Duration LIMIT = Duration.ofSeconds(2);

    // ------------------------------------------------------------------ Hilfen

    private static List<Finding> funde(String text) {
        return pruefe(text, R.detect(text), Set.of("pfad-unc"));
    }

    /** Wie {@link #funde}, aber Funde ab einem Laufwerkskopf (Quelle pfad-laufwerk) sind ebenfalls zulaessig. */
    private static List<Finding> fundeGemischt(String text) {
        return pruefe(text, R.detect(text), Set.of("pfad-unc", "pfad-laufwerk"));
    }

    private static List<Finding> pruefe(String text, List<Finding> funde, Set<String> quellen) {
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring");
            assertEquals(FindingType.PFAD, f.type());
            assertTrue(quellen.contains(f.source()), "Quelle " + f.source());
            // 0.60 (FRAGLICH) nur bei UNC mit genau einem Segment bzw. Zusammenlegung mit einem solchen (Doppel-Backslash
            // im Fund), sonst 0.95 (SICHER)
            if (f.confidence() == 0.60) {
                assertEquals(ConfidenceLevel.FRAGLICH, f.level());
                assertTrue(f.originalValue().contains("\\\\"), "0.60 nur mit Doppel-Backslash");
            } else {
                assertEquals(0.95, f.confidence());
                assertEquals(ConfidenceLevel.SICHER, f.level());
            }
            assertTrue(f.start() >= vorigesEnde, "sortiert und ueberlappungsfrei");
            assertGrenzenSauber(text, f);
            vorigesEnde = f.end();
        }
        return funde;
    }

    private static List<String> werte(String text) {
        return funde(text).stream().map(Finding::originalValue).collect(Collectors.toList());
    }

    private static List<String> werteGemischt(String text) {
        return fundeGemischt(text).stream().map(Finding::originalValue).collect(Collectors.toList());
    }

    private static void assertGrenzenSauber(String text, Finding f) {
        int s = f.start();
        int e = f.end();
        assertFalse(s > 0 && Character.isLowSurrogate(text.charAt(s)) && Character.isHighSurrogate(text.charAt(s - 1)),
                "Start im Surrogatpaar");
        assertFalse(e < text.length() && Character.isLowSurrogate(text.charAt(e))
                && Character.isHighSurrogate(text.charAt(e - 1)), "Ende im Surrogatpaar");
        assertFalse(isKombinierend(text.charAt(s)), "Start auf kombinierendem Zeichen");
        assertFalse(e < text.length() && isKombinierend(text.charAt(e)), "Ende vor kombinierendem Zeichen");
    }

    private static boolean isKombinierend(char c) {
        int t = Character.getType(c);
        return t == Character.NON_SPACING_MARK || t == Character.ENCLOSING_MARK
                || t == Character.COMBINING_SPACING_MARK;
    }

    private static void assertEinFund(String text, String erwartet) {
        assertEquals(List.of(erwartet), werte(text), "Text: " + text);
    }

    /** Genau ein Fund mit dem erwarteten Wert und der erwarteten Konfidenz. */
    private static Finding assertEinFund(String text, String erwartet, double konfidenz) {
        List<Finding> f = funde(text);
        assertEquals(List.of(erwartet), f.stream().map(Finding::originalValue).collect(Collectors.toList()),
                "Text: " + text);
        assertEquals(konfidenz, f.get(0).confidence(), "Konfidenz, Text: " + text);
        return f.get(0);
    }

    private static void assertKeinFund(String text) {
        assertEquals(List.of(), werte(text), "Text: " + text);
    }

    private static long backslashes(String s) {
        return s.chars().filter(c -> c == '\\').count();
    }

    /** Ein Fund, der {@code pfad} ganz enthaelt und am Ende von {@code pfad} endet (Anfang ggf. davor). */
    private static Finding assertEinFundMitSchwanz(String text, String pfad) {
        List<Finding> f = fundeGemischt(text);
        assertEquals(1, f.size(), "Text: " + text);
        assertTrue(f.get(0).originalValue().endsWith(pfad), "Fund endet mit dem Pfad: " + f.get(0).originalValue());
        assertEquals(text.length(), f.get(0).end(), "Fund reicht bis zum Textende");
        return f.get(0);
    }

    // ================================================================== (1) Zeile UNC laut Plan

    // ---- Positiv laut Plan

    @Test
    void planPositivAk11MitAblagePrefix() {
        String pfad = bs("¦¦srv-beispiel¦freigabe¦ordner¦datei.txt");
        List<Finding> f = funde("Ablage " + pfad);
        assertEquals(1, f.size());
        assertEquals(pfad, f.get(0).originalValue());
        assertEquals(7, f.get(0).start());
        assertEquals(7 + pfad.length(), f.get(0).end());
    }

    @Test
    void planPositivAk11ServerUndFreigabe() {
        assertEinFund(bs("¦¦srv-beispiel¦freigabe"), bs("¦¦srv-beispiel¦freigabe"));
        assertEinFund(bs("Ziel ¦¦srv-beispiel¦freigabe jetzt"), bs("¦¦srv-beispiel¦freigabe"));
    }

    @Test
    void planPositivVierfachBackslashMitGepruefterAnzahl() {
        String pfad = bs("¦¦¦¦srv-beispiel¦¦freigabe¦¦x");
        assertEquals(8, backslashes(pfad));
        List<Finding> f = funde("Start " + pfad + " jetzt");
        assertEquals(1, f.size());
        assertEquals(pfad, f.get(0).originalValue());
        assertEquals(8, backslashes(f.get(0).originalValue()), "keine Normalisierung der Backslashes");
    }

    @Test
    void planPositivIpAlsServer() {
        assertEinFund(bs("¦¦192.0.2.10¦freigabe¦ordner"), bs("¦¦192.0.2.10¦freigabe¦ordner"));
        assertEinFund(bs("Siehe ¦¦192.0.2.10¦freigabe¦a.txt."), bs("¦¦192.0.2.10¦freigabe¦a.txt"));
    }

    @Test
    void planPositivAk17bPfadEndetAmErstenLeerzeichen() {
        String text = bs("Ablage ¦¦srv-beispiel¦Mein Ordner¦datei.txt");
        List<Finding> f = funde(text);
        assertEquals(1, f.size());
        assertEquals(bs("¦¦srv-beispiel¦Mein"), f.get(0).originalValue());
        // der Rest liefert keinen weiteren Fund
        assertFalse(text.substring(f.get(0).end()).isBlank());
        assertEquals(List.of(bs("¦¦srv-beispiel¦Mein")), werte(text));
    }

    @Test
    void planPositivGeraetepraefixFragezeichenN2() {
        assertEinFund(bs("¦¦?¦C:¦Tools¦a.exe"), bs("¦¦?¦C:¦Tools¦a.exe"));
        assertEinFund(bs("Start ¦¦?¦C:¦Tools¦a.exe jetzt"), bs("¦¦?¦C:¦Tools¦a.exe"));
    }

    // ---- Negativ laut Plan

    /**
     * Umgestellt (vorher planNegativNurServerOf7, kein Fund): Nutzerentscheidung 20.11.2026, Plan Nachtrag T-12 Punkt 7:
     * Server ohne Freigabe ist ein Fund mit 0.60.
     */
    @Test
    void planPositivNurServerOhneFreigabeMitKonfidenz060() {
        assertEinFund(bs("¦¦srv-beispiel"), bs("¦¦srv-beispiel"), 0.60);
        assertEinFund(bs("Server ¦¦srv-beispiel ok"), bs("¦¦srv-beispiel"), 0.60);
        assertEinFund(bs("¦¦srv-beispiel¦"), bs("¦¦srv-beispiel¦"), 0.60);
        assertEinFund(bs("¦¦srv-beispiel¦¦"), bs("¦¦srv-beispiel¦¦"), 0.60);
    }

    @Test
    void planNegativNurBackslashes() {
        assertKeinFund(bs("¦¦"));
        assertKeinFund(bs("¦¦¦¦"));
        assertKeinFund(bs("a ¦¦ b"));
        assertKeinFund(bs("a ¦¦¦¦ b"));
    }

    /** Umgestellt (vorher planNegativKeinZweitesSegmentNachKuerzung): ein Segment genuegt jetzt, 0.60. */
    @Test
    void planPositivKeinZweitesSegmentNachKuerzungIstEinSegmentFund() {
        assertEinFund(bs("¦¦srv-beispiel¦."), bs("¦¦srv-beispiel¦"), 0.60);
        assertEinFund(bs("¦¦srv-beispiel¦,"), bs("¦¦srv-beispiel¦"), 0.60);
        assertEinFund(bs("¦¦srv-beispiel¦)"), bs("¦¦srv-beispiel¦"), 0.60);
    }

    // ---- Konfidenz und Quelle

    @Test
    void konfidenzQuelleUndStufe() {
        for (String text : List.of(bs("¦¦srv-beispiel¦freigabe"), bs("¦¦srv-beispiel¦freigabe¦a¦b¦c¦d"),
                bs("¦¦¦¦srv-beispiel¦¦freigabe"), bs("\"¦¦srv-beispiel¦Mein Ordner¦a\""))) {
            List<Finding> f = funde(text);
            assertEquals(1, f.size(), text);
            assertEquals(0.95, f.get(0).confidence());
            assertEquals(ConfidenceLevel.SICHER, f.get(0).level());
            assertEquals("pfad-unc", f.get(0).source());
            assertEquals(FindingType.PFAD, f.get(0).type());
        }
    }

    // ---- Satzzeichen, Klammern, Zeilenenden (Endregel gilt fuer alle Formen)

    @Test
    void endeSatzzeichenKlammernUndZeilenenden() {
        assertEinFund(bs("Siehe ¦¦srv-beispiel¦freigabe¦a.txt."), bs("¦¦srv-beispiel¦freigabe¦a.txt"));
        assertEinFund(bs("Siehe ¦¦srv-beispiel¦freigabe¦a.txt, dann"), bs("¦¦srv-beispiel¦freigabe¦a.txt"));
        assertEinFund(bs("Siehe ¦¦srv-beispiel¦freigabe¦a.txt;"), bs("¦¦srv-beispiel¦freigabe¦a.txt"));
        assertEinFund(bs("Siehe ¦¦srv-beispiel¦freigabe¦a.txt!?"), bs("¦¦srv-beispiel¦freigabe¦a.txt"));
        assertEinFund(bs("(siehe ¦¦srv-beispiel¦freigabe¦a.txt)"), bs("¦¦srv-beispiel¦freigabe¦a.txt"));
        assertEinFund(bs("<¦¦srv-beispiel¦freigabe¦a.txt>"), bs("¦¦srv-beispiel¦freigabe¦a.txt"));
        assertEinFund(bs("¦¦srv-beispiel¦freigabe¦Beispiel(x86)"), bs("¦¦srv-beispiel¦freigabe¦Beispiel(x86)"));
        assertEinFund(bs("¦¦srv-beispiel¦freigabe¦"), bs("¦¦srv-beispiel¦freigabe¦"));
        assertEquals(List.of(bs("¦¦srv-beispiel¦freigabe¦a"), bs("¦¦srv-beispiel¦freigabe¦b")),
                werte(bs("¦¦srv-beispiel¦freigabe¦a\n¦¦srv-beispiel¦freigabe¦b")));
        assertEquals(List.of(bs("¦¦srv-beispiel¦freigabe¦a"), bs("¦¦srv-beispiel¦freigabe¦b")),
                werte(bs("¦¦srv-beispiel¦freigabe¦a\r\n¦¦srv-beispiel¦freigabe¦b")));
        assertEquals(List.of(bs("¦¦srv-beispiel¦freigabe¦a"), bs("¦¦srv-beispiel¦freigabe¦b")),
                werte(bs("¦¦srv-beispiel¦freigabe¦a\t¦¦srv-beispiel¦freigabe¦b")));
    }

    @Test
    void zweiUncPfadeHintereinander() {
        assertEquals(List.of(bs("¦¦srv-beispiel¦a¦x"), bs("¦¦srv-beispiel¦b¦y")),
                werte(bs("Kopiere ¦¦srv-beispiel¦a¦x nach ¦¦srv-beispiel¦b¦y")));
        assertEquals(List.of(bs("¦¦srv-beispiel¦a¦x"), bs("¦¦srv-beispiel¦a¦y")),
                werte(bs("¦¦srv-beispiel¦a¦x ¦¦srv-beispiel¦a¦y")));
        // ohne Leerzeichen, nur durch Komma/Semikolon verbunden: ein Fund (wie bei Laufwerkspfaden, Plan 2.4.2)
        assertEquals(List.of(bs("¦¦srv-beispiel¦a¦x;¦¦srv-beispiel¦b¦y")),
                werte(bs("¦¦srv-beispiel¦a¦x;¦¦srv-beispiel¦b¦y")));
    }

    @Test
    void uncDirektNachLaufwerkspfadOhneLeerzeichenIstEinFundUndGanzAbgedeckt() {
        String text = bs("C:¦a¦¦srv-beispiel¦freigabe¦x");
        List<Finding> f = fundeGemischt(text);
        assertEquals(1, f.size());
        assertEquals(text, f.get(0).originalValue());
        assertEquals(List.of(bs("C:¦a"), bs("¦¦srv-beispiel¦freigabe¦x")),
                werteGemischt(bs("C:¦a ¦¦srv-beispiel¦freigabe¦x")));
    }

    // ---- gemischte Trenner, FQDN, administrative Freigabe, Benutzername

    @Test
    void gemischteTrennerHinterDenBackslashes() {
        assertEinFund(bs("¦¦srv-beispiel/freigabe¦a.txt"), bs("¦¦srv-beispiel/freigabe¦a.txt"));
        assertEinFund(bs("¦¦srv-beispiel¦freigabe/a.txt"), bs("¦¦srv-beispiel¦freigabe/a.txt"));
        assertEinFund(bs("¦¦srv-beispiel/freigabe"), bs("¦¦srv-beispiel/freigabe"));
        assertEinFund(bs("x ¦¦srv-beispiel/freigabe/ordner/a.txt y"), bs("¦¦srv-beispiel/freigabe/ordner/a.txt"));
    }

    @Test
    void servernameMitPunktenFqdn() {
        assertEinFund(bs("¦¦srv01.praxis.example¦daten¦akte.pdf"), bs("¦¦srv01.praxis.example¦daten¦akte.pdf"));
        assertEinFund(bs("Siehe ¦¦srv01.praxis.example¦daten¦akte.pdf."), bs("¦¦srv01.praxis.example¦daten¦akte.pdf"));
        assertEinFund(bs("¦¦srv01.beispiel.invalid¦daten"), bs("¦¦srv01.beispiel.invalid¦daten"));
    }

    @Test
    void administrativeFreigabeUndBenutzername() {
        assertEinFund(bs("¦¦srv-beispiel¦c$¦Users¦erika.mustermann"), bs("¦¦srv-beispiel¦c$¦Users¦erika.mustermann"));
        assertEinFund(bs("Profil ¦¦srv-beispiel¦c$¦Users¦erika.mustermann¦Desktop ok"),
                bs("¦¦srv-beispiel¦c$¦Users¦erika.mustermann¦Desktop"));
        assertEinFund(bs("¦¦srv-beispiel¦ADMIN$"), bs("¦¦srv-beispiel¦ADMIN$"));
        assertEinFund(bs("¦¦srv-beispiel¦ipc$"), bs("¦¦srv-beispiel¦ipc$"));
    }

    // ================================================================== (2) Anfuehrungszeichen

    @Test
    void anfuehrungszeichenGanzerPfadMitLeerzeichen() {
        String pfad = bs("¦¦srv-beispiel¦Gemeinsame Daten¦Mein Ordner¦a.txt");
        assertEinFund("Starte \"" + pfad + "\" jetzt", pfad);
        assertEinFund("Starte '" + pfad + "' jetzt", pfad);
        assertEinFund("Starte `" + pfad + "` jetzt", pfad);
        assertEinFund("Starte \u201E" + pfad + "\u201C jetzt", pfad);
        assertEinFund("Starte \u00BB" + pfad + "\u00AB jetzt", pfad);
        assertEinFund("Starte \u2018" + pfad + "\u2019 jetzt", pfad);
        // ohne Kuerzung: auch abschliessender Punkt und Leerzeichen gehoeren zum Inhalt
        assertEinFund("Starte \"" + pfad + ".\" jetzt", pfad + ".");
        assertEinFund("Starte \"" + pfad + " \" jetzt", pfad + " ");
    }

    @Test
    void anfuehrungszeichenAnfangDesZitatsMitTextVorDemPfad() {
        String pfad = bs("¦¦srv-beispiel¦Gemeinsame Daten¦Mein Ordner¦a.txt");
        assertEinFund("Starte \"Siehe " + pfad + "\" jetzt", pfad);
        assertEinFund("Starte '" + "Siehe " + pfad + "' jetzt", pfad);
    }

    @Test
    void anfuehrungszeichenZweiPfadeImZitatSindEinFund() {
        String inhalt = bs("¦¦srv-beispiel¦a Ordner¦x.txt und ¦¦srv-beispiel¦Mein Ordner¦b.txt");
        assertEinFund("Kopiere \"" + inhalt + "\" fertig", inhalt);
    }

    @Test
    void anfuehrungszeichenUnpaarigesZeichenDavorUndZweiterPfadDazwischen() {
        String zweiter = bs("¦¦srv-beispiel¦Mein Ordner¦a.txt");
        String text = bs("Peter's ¦¦srv-beispiel¦a und '") + zweiter + "'";
        List<Finding> f = funde(text);
        int start = text.indexOf(zweiter);
        boolean gedeckt = f.stream().anyMatch(x -> x.start() <= start && x.end() >= start + zweiter.length());
        assertTrue(gedeckt, "zweiter Pfad ganz in einem Fund");
        // der Pfad im richtigen Zitat beginnt trotz unpaarigem Zeichen davor genau am Zitat
        assertTrue(f.stream().anyMatch(x -> x.originalValue().equals(zweiter)), "Fund = Inhalt des Zitats");
    }

    @Test
    void anfuehrungszeichenUnpaarigesZeichenDavorOhneZweitenPfad() {
        String pfad = bs("¦¦srv-beispiel¦Mein Ordner¦a.txt");
        String text = "Monitor 24\" und \"" + pfad + "\" fertig";
        List<Finding> f = funde(text);
        int start = text.indexOf(pfad);
        assertTrue(f.stream().anyMatch(x -> x.start() <= start && x.end() >= start + pfad.length()));
    }

    @Test
    void anfuehrungszeichenNieGeschlossenRueckfallAufErstesLeerzeichen() {
        assertEinFund(bs("Starte \"¦¦srv-beispiel¦Gemeinsame Daten¦a.txt"), bs("¦¦srv-beispiel¦Gemeinsame"));
        assertEinFund(bs("Starte '¦¦srv-beispiel¦Gemeinsame Daten¦a.txt"), bs("¦¦srv-beispiel¦Gemeinsame"));
    }

    @Test
    void anfuehrungszeichenZeilenumbruchOderPipeImZitatFuehrtZumRueckfall() {
        assertEinFund(bs("\"¦¦srv-beispiel¦Mein\nOrdner¦a\""), bs("¦¦srv-beispiel¦Mein"));
        assertEinFund(bs("\"¦¦srv-beispiel¦Mein|Ordner¦a\""), bs("¦¦srv-beispiel¦Mein"));
    }

    @Test
    void anfuehrungszeichenZuWenigeSegmente() {
        // umgestellt: Kopf + ein Segment genuegt jetzt (0.60), vorher kein Fund
        assertEinFund(bs("\"¦¦srv-beispiel\""), bs("¦¦srv-beispiel"), 0.60);
        assertEinFund(bs("\"¦¦srv-beispiel¦\""), bs("¦¦srv-beispiel¦"), 0.60);
        assertKeinFund(bs("\"¦¦\""));
        assertEinFund(bs("\"¦¦srv-beispiel¦a\""), bs("¦¦srv-beispiel¦a"));
        // Leerzeichen im Server zaehlt nicht als zweites Segment, aber der Inhalt hat zwei Segmente
        assertEinFund(bs("\"¦¦srv beispiel¦a\""), bs("¦¦srv beispiel¦a"));
    }

    @Test
    void anfuehrungszeichenVierfachBackslashImZitat() {
        String pfad = bs("¦¦¦¦srv-beispiel¦¦Gemeinsame Daten¦¦x");
        assertEquals(8, backslashes(pfad));
        assertEinFund("Start \"" + pfad + "\" jetzt", pfad);
    }

    @Test
    void anfuehrungszeichenVerschraenkteArtenBleibenSortiert() {
        // ' und Backtick ueberlappen sich: Paar 1 = 'x `\\srv\a y', Paar 2 = `\\srv\a y' z`
        String text = bs("'x `¦¦srv-beispiel¦a y' z`");
        List<Finding> f = funde(text);
        assertFalse(f.isEmpty());
        boolean[] gedeckt = new boolean[text.length()];
        for (Finding x : f) {
            for (int i = x.start(); i < x.end(); i++) {
                gedeckt[i] = true;
            }
        }
        // von den Backslashes bis vor das letzte Anfuehrungszeichen ist alles abgedeckt
        for (int i = text.indexOf('\\'); i < text.length() - 1; i++) {
            assertTrue(gedeckt[i], "Position " + i + " abgedeckt");
        }
    }

    // ================================================================== (3) eigene Entscheidungen

    // ---- Startgrenze: keine (Grundsatz "zu gross oder Teilfund besser als kein Fund")

    @Test
    void eigeneEntscheidungKeineStartgrenzeTextDirektDavor() {
        String pfad = bs("¦¦srv-beispiel¦freigabe¦akte.pdf");
        assertEinFund("x=" + pfad, pfad);
        assertEinFund("(" + pfad + ")", pfad);
        assertEinFund("[" + pfad + "]", pfad);
        assertEinFund("siehe" + pfad, pfad);
        assertEinFund("abc" + pfad + " ok", pfad);
        assertEinFund("....." + pfad, pfad);
        assertEinFund("Datei/" + pfad, pfad);
        assertEinFund("-" + pfad, pfad);
        assertEinFund("\u00FCber" + pfad, pfad);
        assertEinFund("12" + pfad, pfad);
    }

    @Test
    void eigeneEntscheidungKeineStartgrenzePfadDoppelpunktDavorIstGanzAbgedeckt() {
        // "Pfad:" endet auf einen Buchstaben vor ":" und "\": der Laufwerkskopf (T-10, ohne Startgrenze) beginnt am
        // "d"; der Fund ist groesser, aber der UNC-Pfad ist ganz abgedeckt (kein Leck).
        String pfad = bs("¦¦srv-beispiel¦freigabe¦akte.pdf");
        Finding f = assertEinFundMitSchwanz("Pfad:" + pfad, pfad);
        assertTrue(f.start() <= "Pfad:".length());
        assertEinFundMitSchwanz("Datei:" + pfad, pfad);
        // ein Doppelpunkt hinter einer Ziffer oder einem Leerzeichen: der Fund beginnt an den Backslashes
        assertEinFund("Pfad 1:" + pfad, pfad);
        assertEinFund("Pfad: " + pfad, pfad);
    }

    // ---- Backslash-Zahl: mehr als im Plan ("genau 2 oder 4")

    @Test
    void eigeneEntscheidungBeliebigeBackslashZahlAbZwei() {
        for (int n : new int[] {2, 3, 4, 5, 6, 8}) {
            String pfad = "\\".repeat(n) + "srv-beispiel" + "\\".repeat(Math.max(1, n / 2)) + "freigabe";
            assertEquals(n, pfad.indexOf('s'), "Backslashes vor dem Server");
            assertEinFund("Pfad " + pfad + " ok", pfad);
        }
        // genau ein Backslash ist kein UNC-Pfad
        assertKeinFund(bs("¦srv-beispiel¦freigabe¦a"));
        assertKeinFund(bs("Ordner ¦srv¦freigabe¦a"));
    }

    @Test
    void eigeneEntscheidungDreiBackslashesAbgedeckt() {
        String pfad = bs("¦¦¦srv-beispiel¦freigabe¦a.txt");
        assertEquals(3, pfad.indexOf('s'));
        assertEinFund(pfad, pfad);
    }

    @Test
    void eigeneEntscheidungBackslashLaufGefolgtVonSchraegstrichIstKeinKopf() {
        // kein Kopf: hinter dem Backslash-Lauf folgt ein Schraegstrich (Plan: "danach ein Nicht-Trenner")
        assertKeinFund(bs("¦¦/srv-beispiel¦freigabe"));
    }

    // ---- Praefixe \\?\ und \\.\

    @Test
    void eigeneEntscheidungGeraetepraefixUncUndPipe() {
        assertEinFund(bs("¦¦?¦UNC¦srv-beispiel¦freigabe¦a.txt"), bs("¦¦?¦UNC¦srv-beispiel¦freigabe¦a.txt"));
        assertEinFund(bs("Pipe ¦¦.¦pipe¦beispiel-name jetzt"), bs("¦¦.¦pipe¦beispiel-name"));
        assertEinFund(bs("¦¦.¦pipe¦name"), bs("¦¦.¦pipe¦name"));
        assertEinFund(bs("¦¦.¦C:¦Tools¦a.exe"), bs("¦¦.¦C:¦Tools¦a.exe"));
        // Praefix mit Trenner: "?" bzw. "." zaehlt als erstes Segment (Plan Punkt 3), ein Segment = 0.60 (Punkt 7);
        // umgestellt, vorher kein Fund
        assertEinFund(bs("¦¦?¦"), bs("¦¦?¦"), 0.60);
        assertEinFund(bs("¦¦.¦"), bs("¦¦.¦"), 0.60);
        // Praefix allein: Satzzeichen wird gekuerzt, kein Segment bleibt
        assertKeinFund(bs("¦¦?"));
        assertKeinFund(bs("¦¦."));
    }

    @Test
    void eigeneEntscheidungGeraetepraefixMitNurLaufwerkIstGeschuetztObwohlCAlleinKeinFundIst() {
        // zwei Segmente ("?" und "C:") genuegen; harmlos, weil nur mehr geschuetzt wird
        assertEinFund(bs("¦¦?¦C:¦"), bs("¦¦?¦C:¦"));
        assertKeinFund(bs("C:¦"));
    }

    @Test
    void geraetepraefixMitVierfachBackslash() {
        String pfad = bs("¦¦¦¦?¦¦C:¦¦Tools¦¦a.exe");
        assertEinFund(pfad, pfad);
    }

    // ---- Schraegstrich-UNC gehoert nach dem Plan zu UNIX (T-13)

    @Test
    void schraegstrichUncIstAufgabeVonT13NurInvarianten() {
        // Plan 2.4.2 / N-2: "//srv-beispiel/freigabe" gehoert zur Form UNIX (T-13), nicht zu UNC: bis dahin kein Fund
        // (wird in T-13 bewusst angepasst). Die Backslash-Form wird dadurch nicht beeinflusst.
        assertKeinFund("//srv-beispiel/freigabe/a.txt");
        fundeGemischt("https://example.invalid/a/b");
        assertEinFund(bs("Siehe ¦¦srv-beispiel/freigabe/a.txt ende"), bs("¦¦srv-beispiel/freigabe/a.txt"));
    }

    @Test
    void urlOhneBackslashLiefertKeinenUncFund() {
        // "s://..." aus "https://" ist ein bekannter Laufwerkskandidat (T-10 Nachtrag, Punkt 2) und liegt in der URL;
        // eine UNC-Form darf es nicht geben (keine Backslashes)
        for (String text : List.of("https://example.invalid/a", "Link https://example.invalid/a/b/c ende")) {
            for (Finding f : R.detect(text)) {
                assertNotEquals("pfad-unc", f.source(), text);
            }
        }
    }

    // ================================================================== (4) Laufwerk, maskierte Pfade, Obermengen

    @Test
    void maskierteLaufwerkspfadeBleibenEinFundOhneNormalisierung() {
        String pfad = bs("C:¦¦Tools¦¦BeispielTool¦¦tool.exe");
        assertEquals(6, backslashes(pfad));
        List<Finding> f = fundeGemischt("Start " + pfad + " jetzt");
        assertEquals(1, f.size());
        assertEquals(pfad, f.get(0).originalValue());
        assertEquals("pfad-laufwerk", f.get(0).source());
        assertEquals(6, backslashes(f.get(0).originalValue()));
        // derselbe Pfad mit Text davor/dahinter ohne Leerzeichen
        assertEquals(List.of(pfad), werteGemischt("(" + pfad + ")"));
        assertEquals(List.of(pfad), werteGemischt("\"" + pfad + "\""));
        assertEquals(List.of(pfad), werteGemischt("x=" + pfad + ";"));
    }

    @Test
    void maskierterLaufwerkspfadEnthaeltKeinenZusaetzlichenUncFund() {
        String pfad = bs("C:¦¦Tools¦¦a.txt");
        assertEquals(1, R.detect(pfad).size());
        assertEquals(R.detect(pfad, new PathForm[] {PathForm.LAUFWERK}), R.detect(pfad));
        String mitZitat = "\"" + pfad + "\"";
        assertEquals(R.detect(mitZitat, new PathForm[] {PathForm.LAUFWERK}), R.detect(mitZitat));
    }

    @Test
    void obermengeLaufwerkspfadInUncPfadBleibtGanzAbgedeckt() {
        String text = bs("¦¦srv-beispiel¦freigabe¦C:¦Tools¦a.exe");
        assertEinFund(text, text);
    }

    @Test
    void obermengeKlammerzaehlungKuerztNichtDenLaufwerksfund() {
        // UNC beginnt frueher und zaehlt die ueberzaehlige ")" mit; ohne Vereinigung mit dem Laufwerksdurchlauf bliebe
        // die letzte ")" von "Beispiel(x86)" ausserhalb des Fundes.
        String text = bs("¦¦srv-beispiel¦a)¦C:¦Tools¦Beispiel(x86)");
        List<Finding> alle = fundeGemischt(text);
        assertEquals(1, alle.size());
        assertEquals(text, alle.get(0).originalValue());
        List<Finding> nurLaufwerk = R.detect(text, new PathForm[] {PathForm.LAUFWERK});
        assertEquals(1, nurLaufwerk.size());
        assertTrue(alle.get(0).start() <= nurLaufwerk.get(0).start() && alle.get(0).end() >= nurLaufwerk.get(0).end());
    }

    @Test
    void nurUncDurchlaufEnthaeltNurUncFunde() {
        String text = bs("C:¦a ¦¦srv-beispiel¦freigabe¦x D:¦b");
        List<Finding> f = R.detect(text, new PathForm[] {PathForm.UNC});
        assertEquals(1, f.size());
        assertEquals("pfad-unc", f.get(0).source());
        assertEquals(bs("¦¦srv-beispiel¦freigabe¦x"), f.get(0).originalValue());
    }

    @Test
    void detectWithoutQuotesUndDetectOhneAnfuehrungszeichenSindGleich() {
        String text = bs("Ablage ¦¦srv-beispiel¦freigabe¦a.txt und C:¦x¦y");
        assertEquals(R.detectWithoutQuotes(text), R.detect(text));
    }

    // ================================================================== (5) Unicode und unsichtbare Zeichen

    @Test
    void umlauteUndNichtAsciiInServerUndFreigabe() {
        String pfad = bs("¦¦srv-b\u00E4r¦Gr\u00F6\u00DFe¦\u00DCbersicht¦a\u00DF.txt");
        assertEinFund("Pfad " + pfad + " ok", pfad);
        String emoji = bs("¦¦srv\uD83D\uDE00¦freigabe\uD83D\uDE00¦x");
        assertEinFund("Pfad " + emoji + " ok", emoji);
        String kombi = bs("¦¦srve\u0301¦freigabe¦x\u0301");
        assertEinFund("Pfad " + kombi + " ok", kombi);
    }

    @Test
    void fundGrenzenTrennenKeinSurrogatpaar() {
        String pfad = bs("¦¦srv-beispiel¦freigabe¦a");
        assertEinFund("\uD83D\uDE00" + pfad + "\uD83D\uDE00", pfad + "\uD83D\uDE00");
        assertEinFund("e\u0301" + pfad, pfad);
    }

    @Test
    void unsichtbareFormatzeichenBeendenDenPfadNicht() {
        String pfad = bs("¦¦srv\u200B-beispiel¦frei\uFEFFgabe¦a\u200D.txt");
        assertEinFund("Pfad " + pfad + " ok", pfad);
        // am Ende des Laufs bleibt ein unsichtbares Zeichen im Fund
        String ende = bs("¦¦srv-beispiel¦freigabe¦a\u200B");
        assertEinFund("Pfad " + ende + " ok", ende);
    }

    @Test
    void nbspUndAndereLeerraeumeBeendenDenPfadWieEinLeerzeichen() {
        String pfad = bs("¦¦srv-beispiel¦Mein");
        assertEinFund(bs("Pfad ¦¦srv-beispiel¦Mein\u00A0Ordner¦a"), pfad);
        assertEinFund(bs("Pfad ¦¦srv-beispiel¦Mein\u3000Ordner¦a"), pfad);
        assertEinFund(bs("Pfad ¦¦srv-beispiel¦Mein\u202FOrdner¦a"), pfad);
    }

    @Test
    void unsichtbaresZeichenImKopfVerhindertDenFundNurDort() {
        // bekannte Grenze analog T-10 (3a a): ein unsichtbares Zeichen zwischen den Backslashes trennt den Kopf
        assertKeinFund("\\\u200B\\srv-beispiel\\freigabe");
        // der zweite Teil ist ein Backslash-Lauf der Laenge 1: kein UNC-Kopf
    }

    // ================================================================== (6) Grenzlast (AK-49), je < 2 s

    /** Erkenner und Standard-Scanner, Fundzahl wird geprueft ({@code erwartet < 0}: nur Zeit und Gueltigkeit). */
    private static void laufzeit(String text, int erwartet) {
        List<Finding> r = assertTimeoutPreemptively(LIMIT, () -> R.detect(text));
        pruefe(text, r, Set.of("pfad-unc", "pfad-laufwerk"));
        List<Finding> s = assertTimeoutPreemptively(LIMIT, () -> SCANNER.scan(text));
        int vorigesEnde = 0;
        for (Finding f : s) {
            assertTrue(f.start() >= vorigesEnde, "Scanner: sortiert, ueberlappungsfrei");
            vorigesEnde = f.end();
        }
        if (erwartet >= 0) {
            assertEquals(erwartet, r.size(), "Fundzahl Erkenner");
            assertEquals(erwartet, s.size(), "Fundzahl Scanner");
        }
    }

    @Test
    void ak49DoppelBackslashWoertlich100000() {
        laufzeit("\\\\".repeat(100_000), 0);
    }

    @Test
    void ak49EinBackslashEineMillion() {
        laufzeit("\\".repeat(1_000_000), 0);
    }

    @Test
    void ak49DoppelBackslashEineMillionZeichen() {
        laufzeit("\\\\".repeat(500_000), 0);
    }

    @Test
    void ak49DoppelBackslashUndBuchstabeVielfach() {
        // "\\a" * n: ein einziger Lauf "\\a\\a\\a...", ein Fund
        String text = bs("¦¦a").repeat(333_334);
        laufzeit(text, 1);
        assertEquals(text, R.detect(text).get(0).originalValue());
    }

    @Test
    void ak49DoppelBackslashBuchstabeBackslashVielfach() {
        // "\\a\" * n: Backslash-Laeufe der Laenge 3 zwischen den Buchstaben, ein Fund
        String text = bs("¦¦a¦").repeat(250_000);
        laufzeit(text, 1);
    }

    @Test
    void ak49ServerOhneFreigabeUndLeerzeichenVielfach() {
        // "\\a " * n: jeder Kopf liegt durch das Leerzeichen in einem eigenen Lauf, also n Funde mit je einem Segment
        // (0.60); dagegen "\\a" * n (oben) ist ein einziger Lauf und damit ein Fund
        String text = bs("¦¦a ").repeat(250_000);
        laufzeit(text, 250_000);
        assertEquals(0.60, R.detect(text).get(0).confidence());
    }

    @Test
    void ak49ServerFreigabeUndLeerzeichenVielfach() {
        // "\\a\b " * n: n Funde
        String text = bs("¦¦a¦b ").repeat(200_000);
        laufzeit(text, 200_000);
        assertEquals(bs("¦¦a¦b"), R.detect(text).get(0).originalValue());
    }

    @Test
    void ak49SehrVieleSegmenteInEinemLauf() {
        String text = bs("¦¦a¦b¦c") + bs("¦d").repeat(400_000);
        laufzeit(text, 1);
        assertEquals(text, R.detect(text).get(0).originalValue());
    }

    @Test
    void ak49SchraegstricheAllein() {
        laufzeit("/".repeat(1_000_000), 0);
    }

    @Test
    void ak49SchraegstrichUncVielfachNurZeitUndGueltigkeit() {
        // Schraegstrich-UNC gehoert zu T-13; hier nur Laufzeit und Gueltigkeit
        laufzeit("//a/b ".repeat(170_000), -1);
    }

    @Test
    void ak49ZitatMitUncKopfNieGeschlossen() {
        // ein einziges oeffnendes Zeichen, danach sehr viele Woerter: Rueckfall auf die Regel ohne Zeichen
        String text = bs("\"¦¦a¦b c") + " x".repeat(400_000);
        laufzeit(text, 1);
        assertEquals(bs("¦¦a¦b"), R.detect(text).get(0).originalValue());
    }

    @Test
    void ak49ZitatWiederholtWortlich() {
        // "\\a\b c" * n (Zitate schliessen einander): n - 1 Zitatfunde plus der Rest ohne schliessendes Zeichen
        String text = bs("\"¦¦a¦b c").repeat(120_000);
        laufzeit(text, 120_000);
    }

    @Test
    void ak49GeschlossenesZitatMitLeerzeichenVielfach() {
        String text = bs("\"¦¦a¦b c\" ").repeat(100_000);
        laufzeit(text, 100_000);
    }

    @Test
    void ak49SehrLangerServername() {
        laufzeit("\\\\" + "s".repeat(1_000_000) + "\\a", 1);
        // ein Segment (Server ohne Freigabe) ist jetzt ein Fund mit 0.60: ein Lauf, ein Fund
        laufzeit("\\\\" + "s".repeat(1_000_000), 1);
        laufzeit("\\\\" + "s".repeat(1_000_000) + "\\", 1);
    }

    @Test
    void ak49SehrVieleKoepfeOhneGenugSegmente() {
        // Servername wird je Kopf nie bis zum Laufende gelesen: Kopf + langer Servername, durch Leerzeichen getrennt
        // fachlich: jeder Kopf mit Servername ist durch das Leerzeichen ein eigener Lauf, also ein eigener Fund (0.60)
        String text = ("\\\\" + "s".repeat(1_000) + " ").repeat(1_000);
        laufzeit(text, 1_000);
        // viele Koepfe in einem Lauf, jeder durch einen Schraegstrich geschlossen: kein Kopf hat ein Segment
        laufzeit(bs("¦¦/").repeat(300_000), 0);
        // viele Koepfe in einem Lauf, nur die letzten Segmente zaehlen: ein Fund ab dem ersten Kopf
        String viele = (bs("¦¦s") + "x".repeat(10)).repeat(60_000);
        laufzeit(viele, 1);
    }

    @Test
    void ak49KoepfeMitSchraegstrichDahinterUndLangerBackslashLauf() {
        laufzeit("\\".repeat(500_000) + "/a\\b", 0);
        laufzeit("\\".repeat(500_000) + "a\\b", 1);
    }

    @Test
    void ak49DreizehnAnfuehrungszeichenArtenMitUncKoepfen() {
        String[] arten = {"\"", "'", "`", "\u201E", "\u201C", "\u201D", "\u00BB", "\u00AB", "\u2018", "\u2019",
                "\u201A", "\u2039", "\u203A"};
        // je Art: viele Zitate, deren Inhalt UNC-Koepfe ohne Segment (Schraegstrich dahinter) enthaelt
        for (String q : arten) {
            String text = (q + bs("¦¦/¦¦/ ¦¦a¦b c") + q + " ").repeat(40_000);
            List<Finding> r = assertTimeoutPreemptively(LIMIT, () -> R.detect(text));
            pruefe(text, r, Set.of("pfad-unc", "pfad-laufwerk"));
            assertFalse(r.isEmpty(), "Art " + q.codePointAt(0));
        }
        // alle 13 Arten verschraenkt in einem Text
        String alle = String.join("", arten) + bs("¦¦a¦b c ");
        String text = alle.repeat(25_000);
        List<Finding> r = assertTimeoutPreemptively(LIMIT, () -> R.detect(text));
        pruefe(text, r, Set.of("pfad-unc", "pfad-laufwerk"));
        assertFalse(r.isEmpty());
    }

    @Test
    void ak49EinZitatMitVielenUncKoepfenImInhalt() {
        String inhalt = bs("¦¦/¦¦/").repeat(300_000);
        laufzeit("\"" + inhalt + "\"", 0);
        String mitPfad = "\"" + inhalt + bs("¦¦a¦b") + "\"";
        laufzeit(mitPfad, 1);
    }

    // ================================================================== (7) Ein Segment (Plan Nachtrag T-12 Punkt 7)

    @Test
    void einSegmentServerOhneFreigabeImSatz() {
        // Satzpunkt gehoert nicht zum Fund
        assertEinFund(bs("liegt auf ¦¦fileserver01."), bs("¦¦fileserver01"), 0.60);
        assertEinFund(bs("liegt auf ¦¦fileserver01, dann"), bs("¦¦fileserver01"), 0.60);
        assertEinFund(bs("(¦¦fileserver01)"), bs("¦¦fileserver01"), 0.60);
        Finding f = assertEinFund(bs("¦¦fileserver01"), bs("¦¦fileserver01"), 0.60);
        assertEquals(ConfidenceLevel.FRAGLICH, f.level());
        assertEquals("pfad-unc", f.source());
        assertEquals(FindingType.PFAD, f.type());
    }

    @Test
    void einSegmentAbschliessenderTrennerGehoertZumFund() {
        assertEinFund(bs("¦¦fileserver01¦"), bs("¦¦fileserver01¦"), 0.60);
        assertEinFund(bs("auf ¦¦fileserver01¦ ok"), bs("¦¦fileserver01¦"), 0.60);
    }

    @Test
    void einSegmentZweiServerZweiFunde() {
        List<Finding> f = funde(bs("Server ¦¦abrechnung-srv und ¦¦fs01"));
        assertEquals(List.of(bs("¦¦abrechnung-srv"), bs("¦¦fs01")),
                f.stream().map(Finding::originalValue).collect(Collectors.toList()));
        assertEquals(0.60, f.get(0).confidence());
        assertEquals(0.60, f.get(1).confidence());
    }

    @Test
    void einSegmentImZitatKopfPlusEinSegmentGenuegt() {
        // der Inhalt "\\srv mit leerzeichen" hat Kopf + ein Segment (Leerzeichen trennen keine Segmente): ganzer Inhalt
        assertEinFund(bs("\"¦¦srv mit leerzeichen\""), bs("¦¦srv mit leerzeichen"), 0.60);
        assertEinFund(bs("Starte \"¦¦srv mit leerzeichen\" jetzt"), bs("¦¦srv mit leerzeichen"), 0.60);
    }

    @Test
    void einSegmentWeiterhinKeinFund() {
        assertKeinFund(bs("¦¦"));
        assertKeinFund(bs("¦¦¦¦"));
        assertKeinFund(bs("¦¦ x"));
        assertKeinFund(bs("¦¦."));
        assertKeinFund(bs("¦¦?"));
        assertKeinFund(bs("¦¦/srv"));
        assertKeinFund(bs("¦¦.."));
        assertKeinFund(bs("¦¦,"));
    }

    @Test
    void einSegmentGeraetepraefixOhneUndMitZweitemSegment() {
        // "\\?\" und "\\.\": "?" bzw. "." ist das erste Segment (Plan Punkt 3), ein Segment = 0.60
        assertEinFund(bs("¦¦?¦"), bs("¦¦?¦"), 0.60);
        // "\\.\pipe": zwei Segmente = 0.95
        assertEinFund(bs("¦¦.¦pipe"), bs("¦¦.¦pipe"), 0.95);
        assertEinFund(bs("¦¦?¦C:¦"), bs("¦¦?¦C:¦"), 0.95);
    }

    @Test
    void konfidenzHaengtVonDerSegmentzahlDesFundesAb() {
        assertEinFund(bs("¦¦srv"), bs("¦¦srv"), 0.60);
        assertEinFund(bs("¦¦srv¦freigabe"), bs("¦¦srv¦freigabe"), 0.95);
        assertEinFund(bs("¦¦srv¦freigabe¦a"), bs("¦¦srv¦freigabe¦a"), 0.95);
        assertEinFund(bs("¦¦srv¦freigabe¦a¦b¦c"), bs("¦¦srv¦freigabe¦a¦b¦c"), 0.95);
        assertEinFund(bs("¦¦¦¦srv¦¦freigabe"), bs("¦¦¦¦srv¦¦freigabe"), 0.95);
        // der Kandidat "\\srv" mit einem Segment existiert in "\\srv\freigabe\a" nur als Teil des Fundes und drueckt
        // dessen Konfidenz nicht: je Kopf wird ein Fund mit seiner tatsaechlichen Segmentzahl gebildet
        assertEinFund(bs("x ¦¦srv¦freigabe¦a y"), bs("¦¦srv¦freigabe¦a"), 0.95);
    }

    @Test
    void konfidenzImZitatDerLaengerenDeutungDesselbenKopfesGilt() {
        // Ohne Zitatregel waere "\\srv" (ein Segment, 0.60) der Fund, mit ihr der ganze Inhalt mit zwei Segmenten
        // (0.95); die kuerzere Deutung desselben Kopfes darf die Konfidenz nicht auf 0.60 druecken.
        assertEquals(0.60, R.detectWithoutQuotes(bs("\"¦¦srv mit¦freigabe\"")).get(0).confidence());
        assertEinFund(bs("\"¦¦srv mit¦freigabe\""), bs("¦¦srv mit¦freigabe"), 0.95);
        assertEinFund(bs("Starte '¦¦srv mit¦Mein Ordner¦a.txt' jetzt"), bs("¦¦srv mit¦Mein Ordner¦a.txt"), 0.95);
    }

    // ---- bekannte Folge: maskierte Zeichenfolgen, zu grosse oder fragliche Funde (Plan Nachtrag T-12 Punkt 2 und 7)

    @Test
    void bekannteFolgeMaskierteZeichenketteAnbBcIstEinFundAbDenBackslashes() {
        // Text a\\nb\\nc (Zeichenkette mit zwei Backslash-Paaren): ein Fund \\nb\\nc ab Position 1, "a" davor nicht dabei
        String text = bs("a¦¦nb¦¦nc");
        List<Finding> f = funde(text);
        assertEquals(1, f.size());
        assertEquals(bs("¦¦nb¦¦nc"), f.get(0).originalValue());
        assertEquals(1, f.get(0).start());
        assertEquals(text.length(), f.get(0).end());
        assertEquals("pfad-unc", f.get(0).source());
    }

    @Test
    void bekannteFolgeLaufwerkMitDoppelBackslashBleibtEinLaufwerksfund() {
        String text = bs("C:¦¦Tools¦¦a");
        List<Finding> f = fundeGemischt(text);
        assertEquals(1, f.size());
        assertEquals(text, f.get(0).originalValue());
        assertEquals(0, f.get(0).start());
        assertEquals("pfad-laufwerk", f.get(0).source());
        assertEquals(0.95, f.get(0).confidence());
        // auch mit nur einem Segment hinter dem Doppel-Backslash: der UNC-Kandidat "\\Tools" liegt im Laufwerksfund
        List<Finding> g = fundeGemischt(bs("C:¦¦Tools"));
        assertEquals(1, g.size());
        assertEquals(bs("C:¦¦Tools"), g.get(0).originalValue());
        assertEquals("pfad-laufwerk", g.get(0).source());
        assertEquals(0.95, g.get(0).confidence());
    }

    @Test
    void bekannteFolgeMaskierteZeichenfolgenErgebenFraglicheFunde() {
        // Ist-Verhalten: Regex-/Escape-Schreibweise und LaTeX ergeben Funde (zu gross, fraglich, kein Leck)
        assertEinFund(bs("Zeilenumbruch ¦¦n im Text"), bs("¦¦n"), 0.60);
        assertEinFund(bs("Tab ¦¦t hier"), bs("¦¦t"), 0.60);
        // zwei Segmente (d+ und s) in einem Lauf: 0.95
        assertEinFund(bs("Muster ¦¦d+¦¦s"), bs("¦¦d+¦¦s"), 0.95);
        assertEinFund(bs("LaTeX ¦¦begin"), bs("¦¦begin"), 0.60);
        assertEinFund(bs("LaTeX ¦¦begin{document} ok"), bs("¦¦begin{document}"), 0.60);
        assertEinFundMitSchwanz(bs("Pfad C:¦¦Tools"), bs("C:¦¦Tools"));
    }

    @Test
    void einSegmentMitDreiUndVierBackslashes() {
        assertEinFund(bs("¦¦¦¦srv"), bs("¦¦¦¦srv"), 0.60);
        assertEinFund(bs("¦¦¦srv"), bs("¦¦¦srv"), 0.60);
    }

    // ================================================================== Zusammenlegen (Plan 7a) und Klammer-Segmente

    /** Plan 7a (ii): Ein Fund mit spaeterem Start verlaengert den Bereich, es gilt die niedrigste Konfidenz. */
    @Test
    void spaeterBeginnenderFundVerlaengertBereichNiedrigsteKonfidenz() {
        // Zitatfund [1,13) mit 0.95; der Durchlauf ohne Zitat liefert zusaetzlich "\srv'y" ab 8 (0.60, bis 15)
        String text = bs("'C:¦q x ¦¦srv'y");
        List<Finding> f = fundeGemischt(text);
        assertEquals(1, f.size(), "Text: " + text);
        assertEquals(1, f.get(0).start());
        assertEquals(15, f.get(0).end());
        assertEquals(0.60, f.get(0).confidence());
        assertEquals("pfad-laufwerk", f.get(0).source());
    }

    @Test
    void spaeterBeginnenderFundOhneAbsenkungBehaeltKonfidenz() {
        // Ist-Verhalten: Der Zitatfund beginnt am ersten Kopf (3), also am selben Start wie "\srv\a'y" ohne Zitat
        // (Regel (i)); Ergebnis [3,11) mit 0.95
        String text = bs("'x ¦¦srv¦a'y");
        List<Finding> f = fundeGemischt(text);
        assertEquals(1, f.size(), "Text: " + text);
        assertEquals(bs("¦¦srv¦a'y"), f.get(0).originalValue());
        assertEquals(3, f.get(0).start());
        assertEquals(0.95, f.get(0).confidence());
        // spaeterer Start (Regel (ii)) ohne Absenkung: Zitatfund ab "C:\x", ohne Zitat zusaetzlich "\srv\a'y"
        // ab 6 (zwei Segmente, 0.95) bis 15; Ergebnis [1,15) mit 0.95
        String text2 = bs("'C:¦x ¦¦srv¦a'y");
        List<Finding> g = fundeGemischt(text2);
        assertEquals(1, g.size(), "Text: " + text2);
        assertEquals(1, g.get(0).start());
        assertEquals(15, g.get(0).end());
        assertEquals(0.95, g.get(0).confidence());
    }

    @Test
    void fundImBereichAendertKonfidenzNicht() {
        // Zitat ueber den Inhalt (0.95), der Fund "\srv" (0.60) liegt vollstaendig darin
        String text = bs("\"C:¦a ¦¦srv\"");
        List<Finding> f = fundeGemischt(text);
        assertEquals(1, f.size(), "Text: " + text);
        assertEquals(bs("C:¦a ¦¦srv"), f.get(0).originalValue());
        assertEquals(1, f.get(0).start());
        assertEquals(0.95, f.get(0).confidence());
    }

    @Test
    void geraetepraefixMitTrennerOhneWeiteresSegmentIstFraglich() {
        assertEinFund(bs("¦¦.¦"), bs("¦¦.¦"), 0.60);
        assertEinFund(bs("¦¦?¦"), bs("¦¦?¦"), 0.60);
        assertKeinFund(bs("¦¦."));
        assertKeinFund(bs("¦¦?"));
    }

    @Test
    void bekannteFolgeMehrSegmentPfadMitSpaeteremEinSegmentZitatWirdFraglich() {
        // Plan-gemaess (D-8): "C:\Daten\" (0.95) + U+201E "\srv Ordner" U+201C reicht ueber das Ende des Bereichs
        // hinaus und beginnt spaeter, also gilt die niedrigste Konfidenz: 0.60 fuer den ganzen Fund
        String text = bs("C:¦Daten¦\u201E¦¦srv Ordner\u201C");
        List<Finding> f = fundeGemischt(text);
        assertEquals(1, f.size(), "Text: " + text);
        assertEquals(0.60, f.get(0).confidence());
    }

    @Test
    void schliessendeKlammerAlsLetztesSegmentWirdGezaehlt() {
        assertEinFund(bs("¦¦(srv¦)"), bs("¦¦(srv¦)"), 0.95);
        assertEinFund(bs("¦¦srv(¦)"), bs("¦¦srv(¦)"), 0.95);
        assertEinFund(bs("¦¦[$¦]"), bs("¦¦[$¦]"), 0.95);
    }

    @Test
    void ausgeglicheneKlammernBeiZweiSegmentenBleibenSicher() {
        assertEinFund(bs("¦¦srv¦[x¦]"), bs("¦¦srv¦[x¦]"), 0.95);
        assertEinFund(bs("¦¦srv¦a(b)"), bs("¦¦srv¦a(b)"), 0.95);
    }

    @Test
    void einSegmentMitKlammernBleibtFraglich() {
        assertEinFund(bs("¦¦srv)"), bs("¦¦srv"), 0.60);
        assertEinFund(bs("(¦¦srv)"), bs("¦¦srv"), 0.60);
        assertEinFund(bs("¦¦srv(x)"), bs("¦¦srv(x)"), 0.60);
    }

    @Test
    void klammerSegmenteBeiLaufwerkUndKurzenFundenVerlierenNichts() {
        // Ist-Verhalten der Faelle aus dem Auftrag: kein Fund geht durch die pessimistische Zaehlung verloren
        assertEquals(List.of(bs("C:¦(a)")), werteGemischt(bs("C:¦(a)")));
        assertEquals(List.of(bs("C:¦(a)¦")), werteGemischt(bs("C:¦(a)¦")));
        assertEquals(List.of(bs("¦¦(a)")), werteGemischt(bs("¦¦(a)")));
        assertEquals(List.of(bs("C:¦()")), werteGemischt(bs("C:¦()")));
        assertEquals(List.of(), werteGemischt(bs("C:¦)")));
    }

    // ================================================================== Zusatz: Meldungen ohne Klartext

    @ParameterizedTest
    @ValueSource(strings = {"\\\\srv-beispiel\\freigabe", "\\\\?\\C:\\a", "\"\\\\srv-beispiel\\a b\\c\""})
    void findToStringNenntKeinenOriginalwert(String text) {
        for (Finding f : R.detect(text)) {
            assertFalse(f.toString().contains("srv-beispiel"));
            assertNotEquals("", f.toString());
        }
    }
}
