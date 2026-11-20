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
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests der Formen RELATIV, EINZEL_BACKSLASH, BENUTZER_WURZEL und WURZEL_ZWEI_SEGMENTE (T-14, AK-14; Plan 2.4.2
 * Zeile RELATIV, Nachtrag T-12 Punkt 8, Nachtrag T-14 Punkt 5). Pfade werden mit {@code bs(...)} geschrieben (Marker
 * U+00A6 steht fuer einen Backslash, Plan Kap. 2.10). Alle Namen sind synthetisch. Nicht-ASCII-Zeichen stehen nur als
 * Escapes im Quelltext.
 *
 * <p>Gliederung: (1) Zeile RELATIV positiv und negativ (laut Plan); (2) Startgrenze (auch {@code :} nur fuer RELATIV
 * und die Wurzel-Formen) und Satzzeichen; (3) Anfuehrungszeichen; (4) einzelner fuehrender Backslash
 * (Nutzerentscheidung 20.11.2026) mit den bekannten Folgen; (4b) Benutzer-Wurzel ({@code Users}, {@code Benutzer},
 * {@code home/}; {@code home/} und {@code Benutzer/} brauchen zwei Segmente) mit Gendern-Ausnahme und bekannten
 * Fehltreffern; (5) Abgrenzung zu Laufwerk, UNC, Unix und Scanner; (6) Formvertrag; (7) Grenzlast (AK-49), je unter
 * 2 s.
 *
 * <p>Jeder Test prueft je Fund: Teilstring gleich Originalwert, Typ PFAD, Quelle, Konfidenz, sortiert,
 * ueberlappungsfrei, keine Grenze im Surrogatpaar.
 */
class PathRecognizerRelativTest {

    private static final PathRecognizer R = new PathRecognizer();
    private static final TextScanner SCANNER = TextScanner.standard();
    private static final Duration LIMIT = Duration.ofSeconds(2);
    private static final int N = 1_000_000;

    /** Die 13 Anfuehrungszeichen-Arten aus T-11 (Escapes), woertlich. */
    private static final String[] ANF = {"\"", "'", "`", "\u201E", "\u201C", "\u201D", "\u00BB", "\u00AB", "\u2018",
            "\u2019", "\u201A", "\u2039", "\u203A"};

    // ------------------------------------------------------------------ Hilfen

    private static List<Finding> funde(String text) {
        List<Finding> funde = R.detect(text);
        pruefeStruktur(text, funde);
        return funde;
    }

    private static void pruefeStruktur(String text, List<Finding> funde) {
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring");
            assertEquals(FindingType.PFAD, f.type());
            assertTrue(f.start() >= vorigesEnde, "sortiert und ueberlappungsfrei");
            assertFalse(f.start() > 0 && Character.isLowSurrogate(text.charAt(f.start()))
                    && Character.isHighSurrogate(text.charAt(f.start() - 1)), "Start im Surrogatpaar");
            assertFalse(f.end() < text.length() && Character.isLowSurrogate(text.charAt(f.end()))
                    && Character.isHighSurrogate(text.charAt(f.end() - 1)), "Ende im Surrogatpaar");
            vorigesEnde = f.end();
        }
    }

    private static List<String> werte(String text) {
        return funde(text).stream().map(Finding::originalValue).collect(Collectors.toList());
    }

    /** Genau ein Fund {@code erwartet}, Quelle pfad-relativ, Konfidenz 0.80 (WAHRSCHEINLICH, nie SICHER). */
    private static Finding relativ(String text, String erwartet) {
        return fund(text, erwartet, "pfad-relativ", 0.80, ConfidenceLevel.WAHRSCHEINLICH);
    }

    /** Genau ein Fund {@code erwartet}, Quelle pfad-relativ, Konfidenz 0.75 (WAHRSCHEINLICH): einzelner Backslash. */
    private static Finding backslash(String text, String erwartet) {
        return fund(text, erwartet, "pfad-relativ", 0.75, ConfidenceLevel.WAHRSCHEINLICH);
    }

    private static Finding fund(String text, String erwartet, String quelle, double konfidenz, ConfidenceLevel stufe) {
        List<Finding> f = funde(text);
        assertEquals(List.of(erwartet), f.stream().map(Finding::originalValue).collect(Collectors.toList()),
                "Text: " + text);
        assertEquals(quelle, f.get(0).source(), "Quelle, Text: " + text);
        assertEquals(konfidenz, f.get(0).confidence(), "Konfidenz, Text: " + text);
        assertEquals(stufe, f.get(0).level(), "Stufe, Text: " + text);
        return f.get(0);
    }

    private static void keinFund(String text) {
        assertEquals(List.of(), werte(text), "Text: " + text);
    }

    // ================================================================== (1) Zeile RELATIV laut Plan

    @ParameterizedTest
    @ValueSource(strings = {".¦tools¦tool.exe", "../konf/app.yml", "./skript.sh", ".¦tool.exe", "..¦konfig¦a.yml",
            "../../daten/akte.pdf", "./a", "..¦..¦x", "..¦..¦..¦a", ".¦.¦a", "./.git/config", "./a¦b/c", "../a"})
    void zeileRelativPositivAlleVierKoepfeEinFundMitGanzemWert(String roh) {
        String pfad = bs(roh);
        relativ(pfad, pfad);
        relativ("Starte " + pfad + " jetzt", pfad);
    }

    @Test
    void zeileRelativBeispieleAusDemPlanTabelle() {
        relativ(bs(".¦tools¦tool.exe"), bs(".¦tools¦tool.exe"));
        relativ("../konf/app.yml", "../konf/app.yml");
    }

    @Test
    void einSegmentHinterDemKopfGenuegt() {
        relativ("./a", "./a");
        relativ(bs("..¦a"), bs("..¦a"));
        relativ("Datei ./skript.sh laden", "./skript.sh");
    }

    @Test
    void stufeNichtSicherSondernWahrscheinlich() {
        Finding f = relativ("./a/b/c/d", "./a/b/c/d");
        assertEquals(0.80, f.confidence());
        assertEquals(ConfidenceLevel.WAHRSCHEINLICH, f.level());
        assertNotEquals(ConfidenceLevel.SICHER, f.level());
        assertTrue(f.confidence() < 0.90, "unter der Schwelle SICHER");
    }

    @Test
    void konfidenzHaengtNichtVonSegmentzahlOderLeerzeichenAb() {
        for (String t : new String[] {"./a", "./a/b", "./a/b/c", "./a/b/c/d/e/f"}) {
            relativ(t, t);
        }
        relativ("\"./Mein Ordner/a.txt\"", "./Mein Ordner/a.txt");
    }

    @Test
    void mehrereRelativeBackslashSegmenteDoppelPunktPunkt() {
        relativ(bs("..¦..¦x"), bs("..¦..¦x"));
        relativ(bs("cd ..¦..¦daten"), bs("..¦..¦daten"));
    }

    @Test
    void punktKopfMitDotDirektHinterDemKopfIstSegment() {
        relativ("./.git/config", "./.git/config");
        relativ(bs(".¦.¦a"), bs(".¦.¦a"));
    }

    @Test
    void gemischteTrennerImKopfUndImRest() {
        relativ(bs("./a¦b/c¦d"), bs("./a¦b/c¦d"));
        relativ(bs(".¦a/b"), bs(".¦a/b"));
    }

    @Test
    void zweiRelativePfadeImSatzSindZweiFunde() {
        assertEquals(List.of(bs(".¦a.txt"), bs("..¦b.txt")), werte(bs("copy .¦a.txt ..¦b.txt")));
        assertEquals(List.of("./a", "../b"), werte("a ./a, b ../b."));
    }

    // ---- Negativbeispiele der Zeile und AK-14

    @ParameterizedTest
    @ValueSource(strings = {".¦", "../", "./", "..", ".", "..¦..", "ordner¦datei.txt", "ordner/datei.txt", "Files¦Mein",
            "Tool¦tool.exe", "1.", "1.1.1.", "...", "....¦x", "Ende../x", "...¦x", ".../x", "a./b", "1./2.",
            "usw./etc.", "Satz./Neu", "siehe oben./unten", "1.0/2.0", "\u2026/x", ".. x", "tool ../", "./ a", ".¦ a",
            "./.", "../..", "1./home/x", "v1.0../x", "x../x"})
    void zeileRelativNegativKeinFund(String roh) {
        keinFund(bs(roh));
    }

    @Test
    void ak14PfadeOhnePraefixSindKeinFundAuchMitDateiendung() {
        keinFund(bs("ordner¦datei.txt"));
        keinFund("ordner/datei.txt");
        keinFund(bs("Files¦Mein"));
        keinFund(bs("Tool¦tool.exe"));
        keinFund(bs("Ordner¦Datei.txt"));
        keinFund(bs("Starte Files¦Mein Tool¦tool.exe"));
    }

    @Test
    void ak14NurDasPraefixAlleinIstKeinFund() {
        keinFund(bs(".¦"));
        keinFund("../");
        keinFund("./");
        keinFund(bs("..¦"));
        keinFund(bs("Wechsle nach .¦ und dann nach ../"));
    }

    @Test
    void satzpunktDavorOhneLeerzeichenIstKeinFund() {
        keinFund("Ende../x");
        keinFund(bs("Ende..¦x"));
        keinFund("Ende./x");
        keinFund("siehe oben./unten");
    }

    @Test
    void dreiPunkteSindKeinKopfAuchNichtAmZweitenPunkt() {
        keinFund(bs("...¦x"));
        keinFund(".../x");
    }

    @Test
    void aufzaehlungDezimalUndAbkuerzungsPunkteSindKeinFund() {
        keinFund("1./2.");
        keinFund("1.0/2.0");
        keinFund("usw./etc.");
        keinFund("z.B./d.h.");
        keinFund("\u2026/x");
        keinFund("Satz./Neu");
    }

    @Test
    void ohneStartgrenzeAnPunktFolgeVonPunkten() {
        // jeder Punkt hat einen Punkt davor: keine Startgrenze (Plan: "1." mal n)
        keinFund("1.".repeat(1000));
        keinFund(".".repeat(1000));
    }

    // ================================================================== (2) Startgrenze und Satzzeichen

    @Test
    void startgrenzeZeichenDavorGibtEinenFund() {
        relativ("x=./a/b", "./a/b");
        relativ("(./a/b)", "./a/b");
        relativ("[./a/b]", "./a/b");
        relativ("{./a/b}", "./a/b");
        relativ(">./a/b", "./a/b");
        relativ(",./a", "./a");
        relativ(";../b", "../b");
        relativ("--cfg=../b", "../b");
        relativ("-> ./a", "./a");
        relativ("\t./a", "./a");
        relativ("\n./a", "./a");
        relativ("a\u00A0./a", "./a");
        relativ("cmd|./a/b", "./a/b");
    }

    @Test
    void startgrenzeAlleDreizehnAnfuehrungszeichenArtenDavor() {
        for (String q : ANF) {
            // unpaarig: das Zeichen davor ist eine Startgrenze
            relativ(q + "./a/b", "./a/b");
            relativ(q + bs("..¦a"), bs("..¦a"));
        }
    }

    @Test
    void keineStartgrenzeNachBuchstabeZifferDoppelpunktMinusUnterstrich() {
        for (String vor : new String[] {"a", "Z", "5", "-", "_", "~", "%", "@", "#", "e" + (char) 0x0301}) {
            keinFund(vor + "./a/b");
            keinFund(vor + "../a/b");
        }
    }

    // ---- Doppelpunkt als zusaetzliche Startgrenze NUR fuer RELATIV (Plan Nachtrag T-14 Punkt 5 (a))

    @Test
    void doppelpunktDavorIstStartgrenzeFuerRelativ() {
        relativ(bs("Pfad:.¦erika¦akte.pdf"), bs(".¦erika¦akte.pdf"));
        relativ("Pfad:./erika/akte.pdf", "./erika/akte.pdf");
        relativ("Pfad:../erika/akte.pdf", "../erika/akte.pdf");
        relativ(bs("Pfad:..¦erika¦akte.pdf"), bs("..¦erika¦akte.pdf"));
        relativ("host.example:./a/b", "./a/b");
        relativ("host:./a/b", "./a/b");
        relativ("Pfad:../b", "../b");
    }

    @Test
    void doppelpunktUmgebungsvariablePathListe() {
        // Ist-Verhalten: Der erste Punkt allein ist kein Kopf (dahinter steht ":"). Ab dem zweiten Punkt
        // (Startgrenze ":") beginnt ein Fund bis zum Ende des Laufs; "../erika/bin" ist damit abgedeckt.
        relativ("PATH=.:./bin:../erika/bin", "./bin:../erika/bin");
        Finding f = funde("PATH=.:./bin:../erika/bin").get(0);
        assertEquals(7, f.start());
        assertEquals("PATH=.:./bin:../erika/bin".length(), f.end());
    }

    @Test
    void doppelpunktLaufwerkOhneTrennerGibtRelativAbDemPunkt() {
        // "C:" ohne Trenner ist kein Laufwerkskopf; RELATIV beginnt hinter dem Doppelpunkt
        relativ("C:./a", "./a");
        relativ(bs("C:.¦a"), bs(".¦a"));
    }

    @Test
    void doppelpunktBekannteFehltreffer() {
        // Abkuerzung und Verhaeltnis mit Doppelpunkt: zu viel ersetzt, kein Leck (bekannte Folge, Nutzerentscheidung)
        relativ("usw.:./x", "./x");
        relativ("12:./30", "./30");
        relativ("Verhaeltnis 3:./4", "./4");
    }

    @Test
    void doppelpunktUhrzeitMitPunktSchraegstrichPunktIstKeinFund() {
        keinFund("12:30./.");
        keinFund("12:30./");
        keinFund("Um 12:30./ bitte");
    }

    @Test
    void doppelpunktIstFuerAndereFormenKeineStartgrenze() {
        // UNIX/HOST: "Pfad:/etc/hosts" ist ein Host-Fund (Plan Nachtrag Punkt 3), kein Unix- oder Relativ-Fund
        fund("Pfad:/etc/hosts", "Pfad:/etc/hosts", "pfad-host", 0.90, ConfidenceLevel.SICHER);
        // HOME: bleibt bekannte Grenze
        keinFund("Pfad:~/a");
        keinFund("Pfad:~erika/a");
        // EINZEL_BACKSLASH: kein Fund hinter dem Doppelpunkt ohne Laufwerksbuchstaben
        keinFund(bs("12:¦a¦b"));
        keinFund(bs("Pfad::¦a¦b"));
        // Laufwerk bleibt Laufwerk
        fund(bs("x:¦a¦b"), bs("x:¦a¦b"), "pfad-laufwerk", 0.95, ConfidenceLevel.SICHER);
    }

    @Test
    void doppelpunktImZitatPfadVerhaeltSichWieImLauf() {
        relativ("\"Pfad:./Mein Ordner/a.txt\"", "./Mein Ordner/a.txt");
        relativ("'host:../Mein Ordner/a.txt'", "../Mein Ordner/a.txt");
        // erste Inhaltsposition ist immer eine Startgrenze
        relativ("\"./Mein Ordner/a.txt\"", "./Mein Ordner/a.txt");
    }

    @Test
    void doppelpunktPunktOhneTrennerDahinterIstKeinKopf() {
        keinFund("Pfad:.a/b");
        keinFund("Pfad:...x");
        keinFund("Pfad:..");
    }

    @Test
    void satzzeichenAmEndeWerdenGekuerzt() {
        relativ("siehe ../README.md.", "../README.md");
        relativ("siehe ./a/b, danach", "./a/b");
        relativ("(siehe ./a/b)", "./a/b");
        relativ("./a/b!", "./a/b");
        relativ("./a/b?", "./a/b");
        relativ("./a/b;", "./a/b");
        relativ("./a/b:", "./a/b");
        relativ("./a/b'", "./a/b");
        relativ("./a/b...", "./a/b");
    }

    @Test
    void klammerAmEndeNurBeiUeberzahl() {
        relativ("./a/b(x86)", "./a/b(x86)");
        relativ("(./a/b(x86))", "./a/b(x86)");
        relativ("./a/b)", "./a/b");
    }

    @Test
    void abschliessenderTrennerBleibtImFund() {
        relativ("./a/b/", "./a/b/");
        relativ(bs(".¦ordner¦"), bs(".¦ordner¦"));
    }

    @Test
    void kopfOhneSegmentNachKuerzungIstKeinFund() {
        keinFund("cd ../.");
        keinFund(bs("cd ..¦.."));
        keinFund("./.)");
        keinFund("./,");
    }

    @Test
    void klammerAlsEinzigesSegmentHinterDemKopfNachKuerzung() {
        // ausgeglichene Klammern bleiben, damit ist "()" ein Segment (Plan 2.4.1, Kuerzung nach Klammerzaehlung)
        relativ("./()", "./()");
        keinFund("./)");
    }

    @Test
    void leerzeichenBeendenDenPfadOhneAnfuehrungD5() {
        relativ("./Mein Ordner/a.txt", "./Mein");
    }

    @Test
    void unicodeImNamenOhneGrenzeImSurrogatpaarOderKombinationsfolge() {
        relativ("./\uD83D\uDE00/x", "./\uD83D\uDE00/x");
        relativ("./e\u0301cole/\u00DCbersicht.txt", "./e\u0301cole/\u00DCbersicht.txt");
    }

    @Test
    void unsichtbaresZeichenImKopfVerhindertDenFundNurDort() {
        // bekannte Grenze wie bei den anderen Formen: ein Zeichen zwischen Punkt und Trenner trennt den Kopf
        keinFund("." + (char) 0x200B + "/x");
    }

    // ================================================================== (3) Anfuehrungszeichen

    @Test
    void zitatRelativMitLeerzeichenGanz() {
        relativ("\"./Mein Ordner/a.txt\"", "./Mein Ordner/a.txt");
        relativ("'./Mein Ordner/a.txt'", "./Mein Ordner/a.txt");
        relativ("`./Mein Ordner/a.txt`", "./Mein Ordner/a.txt");
        relativ(bs("\"..¦Mein Ordner¦a.txt\""), bs("..¦Mein Ordner¦a.txt"));
        relativ(bs("Starte \".¦Mein Tool¦tool.exe\" jetzt"), bs(".¦Mein Tool¦tool.exe"));
    }

    @Test
    void zitatAlleDreizehnArtenMitEigenemSchliesszeichen() {
        for (String q : ANF) {
            relativ(q + "./Mein Ordner/a.txt" + q, "./Mein Ordner/a.txt");
            relativ("Datei " + q + "../Mein Ordner/a.txt." + q + " ok", "../Mein Ordner/a.txt.");
        }
    }

    @Test
    void zitatOhneKuerzungDerInhaltIstDerFund() {
        relativ("\"./Mein Ordner/a.txt.\"", "./Mein Ordner/a.txt.");
        relativ("\"./Mein Ordner/a.txt \"", "./Mein Ordner/a.txt ");
    }

    @Test
    void zitatZuWenigSegmenteRueckfallOhneZitat() {
        keinFund("\"./\"");
        keinFund("\"../\"");
        keinFund("\"./ \"");
        keinFund("\"..\"");
    }

    @Test
    void zitatNieGeschlossenRueckfallBisZumLeerzeichen() {
        relativ("\"./Mein Ordner/a.txt", "./Mein");
        relativ("Starte \"./Mein Ordner/x", "./Mein");
    }

    @Test
    void zitatUeberZeilenendeWirdNichtGepaart() {
        assertEquals(List.of("./Mein"), werte("\"./Mein\nOrdner/a.txt\""));
    }

    @Test
    void zitatMitKopfInnerhalbDesInhalts() {
        // erster Kopf im Inhalt gilt, Text davor gehoert nicht zum Fund
        relativ("'Datei ./a/b x'", "./a/b x");
    }

    // ================================================================== (4) einzelner fuehrender Backslash

    @ParameterizedTest
    @ValueSource(strings = {"¦srv¦freigabe¦a.txt", "¦Users¦erika.mustermann¦Desktop¦x", "¦a¦b", "¦srv¦freigabe",
            "¦opt¦beispiel/a", "¦a/b", "¦Users¦erika.mustermann¦"})
    void backslashPositivMitMindestensZweiSegmentenKonfidenz075(String roh) {
        String pfad = bs(roh);
        backslash(pfad, pfad);
        backslash("Pfad " + pfad + " ok", pfad);
    }

    @Test
    void backslashNutzerentscheidungBeispiele() {
        backslash(bs("¦srv¦freigabe¦a.txt"), bs("¦srv¦freigabe¦a.txt"));
        backslash(bs("¦Users¦erika.mustermann¦Desktop¦x"), bs("¦Users¦erika.mustermann¦Desktop¦x"));
    }

    @Test
    void backslashStufeWahrscheinlichUndQuelleRelativ() {
        Finding f = backslash(bs("¦srv¦freigabe¦a.txt"), bs("¦srv¦freigabe¦a.txt"));
        assertEquals(0.75, f.confidence());
        assertEquals(ConfidenceLevel.WAHRSCHEINLICH, f.level());
        assertEquals("pfad-relativ", f.source());
    }

    @ParameterizedTest
    @ValueSource(strings = {"¦", "¦a", "¦¦", "¦¦¦", "¦/a/b", "¦ a¦b", "¦n", "¦t", "Zeile¦nNeu", "x¦Users¦erika¦a",
            "HKEY_LOCAL_MACHINE¦SOFTWARE¦x", "Ordner¦Datei.txt", "praxis¦erika.mustermann", "a¦b¦c", "¦.", "¦a¦",
            "¦a/", "Pfad:x¦a¦b"})
    void backslashNegativKeinFund(String roh) {
        keinFund(bs(roh));
    }

    @Test
    void backslashEinSegmentIstKeinFundAuchMitSatzzeichen() {
        keinFund(bs("¦a."));
        keinFund(bs("siehe ¦srv."));
        keinFund(bs("¦srv¦"));
        keinFund(bs("¦srv¦."));
        keinFund(bs("(¦srv)"));
    }

    @Test
    void backslashAmZweitenBackslashEinesDoppelBackslashsEntstehtKeinKopf() {
        // \\srv ist ausschliesslich UNC (0.60); kein zusaetzlicher oder verlaengerter Fund
        Finding f = fund(bs("¦¦srv"), bs("¦¦srv"), "pfad-unc", 0.60, ConfidenceLevel.FRAGLICH);
        assertEquals(0, f.start());
        fund(bs("¦¦srv¦freigabe¦a.txt"), bs("¦¦srv¦freigabe¦a.txt"), "pfad-unc", 0.95, ConfidenceLevel.SICHER);
        fund(bs("¦¦¦srv¦freigabe"), bs("¦¦¦srv¦freigabe"), "pfad-unc", 0.95, ConfidenceLevel.SICHER);
        fund(bs("¦¦¦¦srv¦freigabe¦x"), bs("¦¦¦¦srv¦freigabe¦x"), "pfad-unc", 0.95, ConfidenceLevel.SICHER);
        keinFund(bs("¦¦"));
        keinFund(bs("¦¦¦¦"));
        // einzelner Backslash-Kopf nie am zweiten Backslash
        assertEquals(-1, PathForm.EINZEL_BACKSLASH.headEnd(bs("¦¦srv¦a"), 1));
        assertEquals(-1, PathForm.EINZEL_BACKSLASH.headEnd(bs("¦¦srv¦a"), 0));
        assertEquals(1, PathForm.EINZEL_BACKSLASH.headEnd(bs("¦srv¦a"), 0));
    }

    @Test
    void backslashKopfHinterTrennerGibtKeinenKopf() {
        assertEquals(-1, PathForm.EINZEL_BACKSLASH.headEnd(bs("¦/a"), 0));
        assertEquals(-1, PathForm.EINZEL_BACKSLASH.headEnd(bs("¦¦a"), 0));
        assertEquals(-1, PathForm.EINZEL_BACKSLASH.headEnd(bs("¦"), 0));
        assertEquals(-1, PathForm.EINZEL_BACKSLASH.headEnd("", 0));
    }

    @Test
    void backslashStartgrenzeAmWortanfang() {
        backslash(bs("x=¦a¦b"), bs("¦a¦b"));
        backslash(bs("(¦a¦b)"), bs("¦a¦b"));
        backslash(bs("[¦a¦b]"), bs("¦a¦b"));
        backslash(bs(",¦a¦b"), bs("¦a¦b"));
        backslash(bs("cmd|¦a¦b"), bs("¦a¦b"));
        backslash(bs("-> ¦a¦b"), bs("¦a¦b"));
        for (String q : ANF) {
            backslash(q + bs("¦a¦b"), bs("¦a¦b"));
        }
    }

    @Test
    void backslashOhneStartgrenzeKeinTeilfundInWortMitBackslash() {
        // eigene Entscheidung: "fuehrend" = am Wortanfang; sonst Teilfund ab dem ersten Backslash
        keinFund(bs("Ordner¦Datei¦x.txt"));
        keinFund(bs("x¦Users¦erika¦a"));
        keinFund(bs("HKEY_LOCAL_MACHINE¦SOFTWARE¦Beispiel¦x"));
        keinFund(bs("a¦b¦c¦d"));
    }

    @Test
    void backslashNachLaufwerkDoppelpunktIstLaufwerksfundAbBuchstabe() {
        // "Pfad:" + Backslash: Laufwerksregel ab "d:", Fund ab dem Buchstaben vor dem Doppelpunkt
        fund(bs("Pfad:¦Users¦erika"), bs("d:¦Users¦erika"), "pfad-laufwerk", 0.95, ConfidenceLevel.SICHER);
    }

    @Test
    void backslashZitatGanzMitLeerzeichen() {
        backslash(bs("\"¦srv¦freigabe¦a b.txt\""), bs("¦srv¦freigabe¦a b.txt"));
        backslash(bs("'¦srv¦Mein Ordner¦x'"), bs("¦srv¦Mein Ordner¦x"));
        keinFund(bs("\"¦a\""));
    }

    @Test
    void backslashOhneAnfuehrungEndetAmLeerzeichen() {
        backslash(bs("¦srv¦Mein Ordner¦x"), bs("¦srv¦Mein"));
    }

    @Test
    void backslashKurzesEscapeAlleinKeinFund() {
        keinFund(bs("¦n"));
        keinFund(bs("Zeile1¦nZeile2"));
        keinFund(bs("a ¦t b"));
    }

    // ---- bekannte Folgen (Nutzerentscheidung, Fehltreffer)

    @Test
    void bekannteFolgeRegexSchreibweiseIstFundMit075() {
        backslash(bs("¦d+¦s"), bs("¦d+¦s"));
        backslash(bs("¦d¦w¦s"), bs("¦d¦w¦s"));
        backslash(bs("Muster ¦d+¦s* gilt"), bs("¦d+¦s*"));
    }

    @Test
    void bekannteFolgeLatexBefehleSindFundMit075() {
        backslash(bs("¦textbf{a}¦cite{b}"), bs("¦textbf{a}¦cite{b}"));
        backslash(bs("¦begin{document}¦section{x}"), bs("¦begin{document}¦section{x}"));
        keinFund(bs("¦textbf{a}"));
    }

    @Test
    void bekannteFolgeEscapeFolgeImZitatIstFundMit075() {
        backslash(bs("\"¦r¦n\""), bs("¦r¦n"));
        backslash(bs("'¦n¦t'"), bs("¦n¦t"));
        backslash(bs("a ¦n¦t b"), bs("¦n¦t"));
    }

    @Test
    void bekannteFolgeLaufwerkMitEscapeBleibtLaufwerksfund() {
        fund(bs("C:¦n"), bs("C:¦n"), "pfad-laufwerk", 0.95, ConfidenceLevel.SICHER);
        fund(bs("C:¦n¦t"), bs("C:¦n¦t"), "pfad-laufwerk", 0.95, ConfidenceLevel.SICHER);
    }

    @Test
    void bekannteFolgeLaufwerkspfadHinterEinzelBackslashPfadWirdMitGrosserFundMit075() {
        // vorher: nur "C:\Tools\a" (0.95), "\Users\erika" im Klartext; jetzt ein Fund ueber den ganzen Lauf, 0.75
        backslash(bs("¦Users¦erika;C:¦Tools¦a"), bs("¦Users¦erika;C:¦Tools¦a"));
        backslash(bs("¦srv¦freigabe;C:¦a¦b"), bs("¦srv¦freigabe;C:¦a¦b"));
        // in umgekehrter Reihenfolge bleibt es ein Laufwerksfund (der Einzel-Backslash-Kopf liegt ganz darin)
        fund(bs("C:¦Tools¦a;¦Users¦erika"), bs("C:¦Tools¦a;¦Users¦erika"), "pfad-laufwerk", 0.95,
                ConfidenceLevel.SICHER);
    }

    @Test
    void bekannteFolgeUnausgeglicheneKlammerVorSpaeteremKopfSenktKonfidenzAuf075() {
        // Regel 7a (ii) (Nachtrag T-12) und T-13 Nachtrag 3b: Ab "\b" (Startgrenze ",") bleibt die Klammer am Ende im
        // Fund, weil dort "(" und ")" ausgeglichen sind; ab "C:" ist das ")" ueberzaehlig und wird gekuerzt. Der
        // spaetere Fund verlaengert den Bereich: es gilt die niedrigste Konfidenz (D-8). Der Pfad ist vollstaendig
        // abgedeckt.
        fund(bs("C:¦a),¦b¦c(x)"), bs("C:¦a),¦b¦c(x)"), "pfad-laufwerk", 0.75, ConfidenceLevel.WAHRSCHEINLICH);
        fund(bs("¦¦srv¦a),¦b¦c(x)"), bs("¦¦srv¦a),¦b¦c(x)"), "pfad-unc", 0.75, ConfidenceLevel.WAHRSCHEINLICH);
        // ohne spaeteren Kopf mit Startgrenze bleibt es bei 0.95 (kein Fund einer neuen Form verlaengert)
        fund(bs("C:¦Tools¦a)¦b¦c(x)"), bs("C:¦Tools¦a)¦b¦c(x"), "pfad-laufwerk", 0.95, ConfidenceLevel.SICHER);
    }

    @Test
    void domaenenkontoOhneFuehrendenBackslashIstKeinFund() {
        keinFund(bs("praxis¦erika.mustermann"));
        keinFund(bs("Anmeldung praxis¦erika.mustermann ok"));
    }

    @Test
    void domaenenkontoNachSchalterIstZufaelligUnixFundDerTrennerBeiden() {
        // T-13: "/user:praxis\erika.mustermann" ist ein Unix-Fund (0.80); die Form EINZEL_BACKSLASH ist nicht beteiligt
        fund(bs("/user:praxis¦erika.mustermann"), bs("/user:praxis¦erika.mustermann"), "pfad-unix", 0.80,
                ConfidenceLevel.WAHRSCHEINLICH);
    }

    @Test
    void scpMitRelativemPfadOhneKopfBleibtBekannteGrenzeKeinPfadFund() {
        for (String t : new String[] {"git@host.example:erika/repo.git", "host.example:akte.pdf", "host:erika/repo.git",
                "erika@srv.example:daten/akte.pdf"}) {
            assertTrue(SCANNER.scan(t).stream().noneMatch(f -> f.type() == FindingType.PFAD), t);
            keinFund(t);
        }
    }

    // ================================================================== (4b) Benutzer-Wurzel (Nachtrag T-14 5 (b))

    /** Genau ein Fund {@code erwartet}, Quelle pfad-relativ, Konfidenz 0.60 (FRAGLICH): BENUTZER_WURZEL/WURZEL_ZWEI_SEGMENTE. */
    private static Finding wurzel(String text, String erwartet) {
        return fund(text, erwartet, "pfad-relativ", 0.60, ConfidenceLevel.FRAGLICH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Users¦erika.mustermann¦Desktop¦akte.pdf", "users/erika/Desktop", "USERS¦erika",
            "Benutzer¦erika¦Desktop", "benutzer/erika/x", "BENUTZER/Erika/x", "uSeRs¦e", "home/erika/akte.pdf",
            "home/a/b", "Users¦a", "Users/a", "Benutzer¦in", "Benutzer¦innen", "Benutzer¦erika", "Benutzer/erika/Desktop"})
    void wurzelPositivKonfidenz060Fraglich(String roh) {
        String pfad = bs(roh);
        wurzel(pfad, pfad);
        wurzel("Pfad " + pfad + " ok", pfad);
    }

    @Test
    void wurzelSatzpunktHinterDemPfadWirdGekuerzt() {
        wurzel(bs("liegt unter Users¦erika.mustermann¦Desktop."), bs("Users¦erika.mustermann¦Desktop"));
        wurzel("siehe home/erika/akte.pdf, danach", "home/erika/akte.pdf");
    }

    @Test
    void wurzelFundBeginntAmErstenBuchstabenDesWurzelnamens() {
        Finding f = wurzel(bs("Datei Users¦erika¦x"), bs("Users¦erika¦x"));
        assertEquals(6, f.start());
        Finding h = wurzel("Datei home/erika/x", "home/erika/x");
        assertEquals(6, h.start());
    }

    @Test
    void wurzelStartgrenzeZeichenDavor() {
        wurzel(bs(" Users¦erika¦x"), bs("Users¦erika¦x"));
        wurzel(bs("(Users¦erika¦x)"), bs("Users¦erika¦x"));
        wurzel(bs("x=Users¦erika¦x"), bs("Users¦erika¦x"));
        wurzel("[home/erika/x]", "home/erika/x");
        wurzel(bs("cmd|Users¦erika"), bs("Users¦erika"));
        for (String q : ANF) {
            wurzel(q + bs("Users¦erika¦x"), bs("Users¦erika¦x"));
            wurzel(q + "home/erika/x", "home/erika/x");
        }
    }

    @Test
    void wurzelOhneStartgrenzeKeinFund() {
        keinFund(bs("xUsers¦a"));
        keinFund("xhome/a/b");
        keinFund("xBenutzer/erika/x");
        keinFund(bs(".Users¦a"));
        keinFund(bs("-Users¦a"));
        keinFund(bs("a.Users¦a"));
        keinFund(bs("e" + (char) 0x0301 + "Users¦a"));
    }

    @Test
    void wurzelZitatMitLeerzeichenGanz() {
        wurzel(bs("\"Users¦Erika Mustermann¦Desktop\""), bs("Users¦Erika Mustermann¦Desktop"));
        wurzel("'home/erika/Mein Ordner/x'", "home/erika/Mein Ordner/x");
        wurzel(bs("Datei \"Benutzer¦Erika Mustermann¦a b.txt\" ok"), bs("Benutzer¦Erika Mustermann¦a b.txt"));
    }

    @Test
    void wurzelOhneZitatEndetAmLeerzeichen() {
        wurzel(bs("Users¦Erika Mustermann¦Desktop"), bs("Users¦Erika"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Users", "Users¦", "Users/", "users/ ", "Users and groups", "Benutzer", "Benutzer/",
            "Benutzer¦", "home", "home/", "home/ ", "home/office", "home/office.", "home/¦", "Home/erika/x",
            "HOME/erika/x", "homepage/a/b", "homes/a/b", "xUsers¦a", "usersa¦b", "Benutzerin/x", "Users.¦a",
            "Users ¦a", "Users¦ a", "\u017Fers¦a", "U\u017Fers¦a", "Benutzer/x", "Benutzer/x/", "benutzer/ x/y",
            "Benutzer/\u017F", "\u017Fenutzer/a/b"})
    void wurzelNegativKeinFund(String roh) {
        keinFund(bs(roh));
    }

    @Test
    void wurzelDoppelpunktHinterUsersIstKeinKopfDerWurzel() {
        // "Users:\a": kein Wurzel-Kopf; der Laufwerkskopf "s:\" (Laufwerk ohne Startgrenze) gibt den Fund ab "s"
        fund(bs("Users:¦a"), bs("s:¦a"), "pfad-laufwerk", 0.95, ConfidenceLevel.SICHER);
    }

    @Test
    void wurzelEinSegmentGiltFuerUsersAberHomeUndBenutzerSchraegstrichBrauchenZwei() {
        wurzel("users/erika", "users/erika");
        wurzel(bs("Users¦erika"), bs("Users¦erika"));
        wurzel(bs("Benutzer¦erika"), bs("Benutzer¦erika"));
        keinFund("home/erika");
        keinFund("Benutzer/erika");
        wurzel("home/erika/x", "home/erika/x");
        wurzel("Benutzer/erika/x", "Benutzer/erika/x");
    }

    @Test
    void bekannteGrenzeBenutzerSchraegstrichMitEinemSegmentOderLeerzeichenImNamenIstDokumentiert() {
        // ein Segment hinter "Benutzer/" ist kein Fund (auch nach ":"); ein Leerzeichen im Namen beendet den Lauf
        keinFund("Benutzer/erika");
        keinFund("Pfad:Benutzer/erika");
        keinFund("Benutzer/erika mustermann/Desktop");
        // mit Anfuehrungszeichen gehoert das Leerzeichen zum Pfad
        wurzel("\"Benutzer/erika mustermann/Desktop\"", "Benutzer/erika mustermann/Desktop");
        // die Backslash-Variante braucht nur ein Segment: der Fund endet am Leerzeichen
        wurzel(bs("Benutzer¦erika mustermann¦Desktop"), bs("Benutzer¦erika"));
    }

    // ---- Regelkorrektur nach security-Pruefung Durchlauf 2 (Plan Nachtrag T-14 Punkt 5 (b), Absatz "Korrektur")

    /** Standard-Scanner: kein PFAD-Fund, der Text bleibt beim Pseudonymisieren unveraendert. */
    private static void keinFundUeberScanner(String text) {
        List<Finding> s = SCANNER.scan(text);
        assertTrue(s.stream().noneMatch(f -> f.type() == FindingType.PFAD), "Scanner, Text: " + text);
        assertEquals(text, new Pseudonymizer().pseudonymize(text, s).text(), "Text bleibt unveraendert");
    }

    /**
     * Genau ein Fund {@code erwartet} (0.60, pfad-relativ) im Erkenner und im Standard-Scanner; der Fund steht nicht
     * mehr im pseudonymisierten Text, der Roundtrip ist zeichengleich.
     */
    private static void wurzelUeberScanner(String text, String erwartet) {
        wurzel(text, erwartet);
        List<Finding> s = SCANNER.scan(text);
        assertEquals(List.of(erwartet), s.stream().map(Finding::originalValue).collect(Collectors.toList()),
                "Scanner, Text: " + text);
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, s);
        assertFalse(p.text().contains(erwartet), "Fund im pseudonymisierten Text");
        ReidentificationResult back = new Reidentifier().restore(p.text(), p.mapping());
        assertEquals(text, back.text(), "Roundtrip");
        assertTrue(back.unresolved().isEmpty(), "Meldung");
    }

    /** Kopf {@code Benutzer} mit Schraegstrich und nur einem Segment: haeufige Fehltreffer in deutschen Texten. */
    @ParameterizedTest
    @ValueSource(strings = {"Benutzer/Passwort", "Benutzer/Kennwort", "Benutzer/Gruppen", "Benutzer/Administratoren",
            "Benutzer/Benutzerinnen", "Benutzer/Mitarbeiter", "Benutzer/-innen", "Benutzer/-gruppen", "Benutzer/PW",
            "Benutzer/Kennw.", "Benutzer/in-Konto", "\u201EBenutzer/innen\u201C", "\u00BBBenutzer/in\u00AB",
            "Benutzer/innen\u2026", "Benutzer/in....", "Benutzer/ina", "Benutzer/in.pdf", "Benutzer/inn",
            "Benutzer/innens", "Benutzer/in,x", "Benutzer/\u0131n", "Benutzer/\u0130n"})
    void benutzerSchraegstrichMitEinemSegmentIstKeinFund(String roh) {
        keinFund(roh);
        keinFundUeberScanner(roh);
        keinFund("Siehe " + roh + " im Text");
    }

    /** Gendern-Ausnahme: erstes Segment hinter {@code Benutzer/} ist genau {@code in} oder {@code innen}. */
    @ParameterizedTest
    @ValueSource(strings = {"Benutzer/in", "Benutzer/innen", "Benutzer/in/x", "Benutzer/Innen/y", "benutzer/IN/z",
            "Benutzer/innen/erika/a", "BENUTZER/INNEN/x/y", "Benutzer/iN/x", "Benutzer/in//x", "Benutzer/in/",
            "Benutzer/in/innen/x", "Benutzer/in¦x"})
    void benutzerSchraegstrichGendernIstKeinFund(String roh) {
        String text = bs(roh);
        keinFund(text);
        keinFundUeberScanner(text);
        keinFund("Liebe " + text + " und");
    }

    @Test
    void benutzerSchraegstrichGendernMitSatzzeichenUndKlammernIstKeinFund() {
        for (String t : new String[] {"Liebe Benutzer/innen.", "(Benutzer/in)", "Benutzer/in,", "Benutzer/innen;",
                "Benutzer/in!", "Benutzer/in...", "Benutzer/in)", "[Benutzer/in]", "\"Benutzer/innen\"",
                "Benutzer/in\n"}) {
            keinFund(t);
            keinFundUeberScanner(t);
        }
    }

    /** Die Ausnahme gilt nur, wenn {@code in}/{@code innen} das GANZE erste Segment ist: dann zwei Segmente. */
    @Test
    void benutzerSchraegstrichErstesSegmentNurMitInImNamenIstFundMitZweiSegmenten() {
        wurzelUeberScanner("Benutzer/in-Konto/x", "Benutzer/in-Konto/x");
        wurzelUeberScanner("Benutzer/ina/x", "Benutzer/ina/x");
        wurzelUeberScanner("Benutzer/ini/x", "Benutzer/ini/x");
        wurzelUeberScanner("Benutzer/innens/x", "Benutzer/innens/x");
        wurzelUeberScanner("Benutzer/in.pdf/x", "Benutzer/in.pdf/x");
        wurzelUeberScanner("Benutzer/in,x/y", "Benutzer/in,x/y");
        // ASCII-Vergleich: U+0131 (i ohne Punkt) und U+0130 sind nicht "i"
        wurzelUeberScanner("Benutzer/\u0131n/x", "Benutzer/\u0131n/x");
        wurzelUeberScanner("Benutzer/\u0130n/x", "Benutzer/\u0130n/x");
        // das Segment "in" an zweiter Stelle ist keine Ausnahme
        wurzelUeberScanner("Benutzer/erika/in", "Benutzer/erika/in");
    }

    @Test
    void benutzerSchraegstrichZweiSegmenteSindFundMit060() {
        wurzelUeberScanner("Benutzer/erika/Desktop", "Benutzer/erika/Desktop");
        wurzelUeberScanner("benutzer/erika/akte.pdf", "benutzer/erika/akte.pdf");
        wurzelUeberScanner("BENUTZER/Erika/x", "BENUTZER/Erika/x");
        wurzelUeberScanner("Benutzer/Rollen/Rechte", "Benutzer/Rollen/Rechte");
        wurzelUeberScanner("Benutzer/Gruppen/Admins.", "Benutzer/Gruppen/Admins");
    }

    @Test
    void benutzerBackslashBleibtBeiEinemSegment() {
        wurzelUeberScanner(bs("Benutzer¦erika"), bs("Benutzer¦erika"));
        wurzelUeberScanner(bs("Benutzer¦in"), bs("Benutzer¦in"));
        wurzelUeberScanner(bs("Benutzer¦innen"), bs("Benutzer¦innen"));
        wurzelUeberScanner(bs("Benutzer¦Gruppen"), bs("Benutzer¦Gruppen"));
        wurzelUeberScanner(bs("Benutzer¦in¦x"), bs("Benutzer¦in¦x"));
        // gemischte Trenner: der Kopf mit Backslash genuegt, ein Schraegstrich dahinter ist ein gewoehnlicher Trenner
        wurzelUeberScanner(bs("Benutzer¦erika/Desktop"), bs("Benutzer¦erika/Desktop"));
        // Schraegstrich-Kopf mit Backslash im zweiten Segment: zwei Segmente
        wurzelUeberScanner(bs("Benutzer/erika¦x"), bs("Benutzer/erika¦x"));
    }

    @Test
    void usersBleibtBeiEinemSegmentMitBeidenTrennern() {
        wurzelUeberScanner("Users/erika", "Users/erika");
        wurzelUeberScanner("users/groups", "users/groups");
        wurzelUeberScanner("Users/Admins", "Users/Admins");
        wurzelUeberScanner(bs("Users¦erika"), bs("Users¦erika"));
        wurzelUeberScanner("Users/in", "Users/in");
        wurzelUeberScanner("users/innen", "users/innen");
        wurzelUeberScanner(bs("Users¦erika.mustermann¦Desktop¦akte.pdf"), bs("Users¦erika.mustermann¦Desktop¦akte.pdf"));
    }

    @Test
    void homeBleibtBeiZweiSegmenten() {
        keinFund("home/office");
        keinFund("home/");
        wurzelUeberScanner("home/erika/akte.pdf", "home/erika/akte.pdf");
        wurzelUeberScanner("home/a/b", "home/a/b");
    }

    @Test
    void unveraendertLaufwerkEinzelBackslashUnixUndLaufwerksfundHinterUsers() {
        fund(bs("C:¦Users¦erika¦x"), bs("C:¦Users¦erika¦x"), "pfad-laufwerk", 0.95, ConfidenceLevel.SICHER);
        backslash(bs("¦Users¦erika¦x"), bs("¦Users¦erika¦x"));
        fund("/home/erika/x", "/home/erika/x", "pfad-unix", 0.90, ConfidenceLevel.SICHER);
        fund(bs("Users:¦a"), bs("s:¦a"), "pfad-laufwerk", 0.95, ConfidenceLevel.SICHER);
    }

    // ---- Zitat: Der Kopf liest nie ueber das Zitatende hinaus, was das Ergebnis aendert

    @Test
    void zitatBenutzerInUndInnenDirektHinterDemSchliesszeichenIstKeinFundImZitat() {
        // Im Zitat hat der Inhalt nur ein Segment; das Zeichen an der Stelle des Inhaltsendes ist das
        // Anfuehrungszeichen (weder Buchstabe noch Trenner). Der Text dahinter aendert nichts.
        for (String t : new String[] {"\"Benutzer/in\"", "\"Benutzer/in\"/x", "\"Benutzer/innen\"/a/b",
                "\"Benutzer/in\"/", "'Benutzer/innen'/x/y", "`Benutzer/in`/x"}) {
            assertEquals(List.of(), PathRecognizer.collectQuoted(t, PathForm.values()), "Zitatpfad, Text: " + t);
        }
        // ohne Rest-Pfad dahinter ist der ganze Text kein Fund (ein Segment im Zitat)
        for (String t : new String[] {"\"Benutzer/in\"", "\"Benutzer/in\"/x", "\"Benutzer/in\"/", "`Benutzer/in`/x"}) {
            keinFund(t);
            keinFundUeberScanner(t);
        }
    }

    @Test
    void zitatBenutzerInnenTypografischMitPfadDahinterFundKommtAusDemLaufNichtAusDemZitat() {
        // U+201E Benutzer/innen U+201C /a/b: Im Zitat ein Segment, kein Fund. Der Lauf ohne Zitat (das typografische
        // Zeichen ist kein Endzeichen) hat aber das erste Segment "innen" plus U+201C, also nicht genau "innen": der
        // Kopf gilt, es folgen drei Segmente (Ist-Verhalten, 0.60, zu viel ersetzt, kein Leck).
        String text = "\u201EBenutzer/innen\u201C/a/b";
        assertEquals(List.of(), PathRecognizer.collectQuoted(text, PathForm.values()), "Zitatpfad");
        wurzelUeberScanner(text, "Benutzer/innen\u201C/a/b");
        // ohne Pfad dahinter: kein Fund
        keinFund("\u201EBenutzer/innen\u201C");
        keinFund("\u201EBenutzer/innen\u201C.");
    }

    @Test
    void zitatPfadAendertSichDurchTextHinterDemSchliesszeichenNie() {
        String[] inhalte = {"Benutzer/in", "Benutzer/innen", "Benutzer/inn", "Benutzer/i", "Benutzer/in/x",
                "Benutzer/innen/a", "Benutzer/erika/x", "Benutzer/in-Konto", "Benutzer/in x/y", "home/a", "home/a/b",
                "Benutzer", "Benutzer/", "Users/a", "x Benutzer/in"};
        String[] dahinter = {"", "/", "/x", "/a/b", "x", "n", "nen", "\\x", ".", "/in/x", "\\a"};
        for (String q : ANF) {
            for (String inhalt : inhalte) {
                String kurz = q + inhalt + q;
                List<Finding> erwartet = PathRecognizer.collectQuoted(kurz, PathForm.values());
                for (String d : dahinter) {
                    List<Finding> ist = PathRecognizer.collectQuoted(kurz + d, PathForm.values());
                    assertEquals(erwartet, ist, "Zitatpfad aendert sich durch den Text dahinter: " + kurz + d);
                }
            }
        }
    }

    @Test
    void kopfLiestNieUeberDasTextendeHinaus() {
        for (PathForm form : new PathForm[] {PathForm.BENUTZER_WURZEL, PathForm.WURZEL_ZWEI_SEGMENTE}) {
            for (String t : new String[] {"Benutzer/innen/x", "benutzer/IN/x", "Users/in", "home/a", "Benutzer\\a"}) {
                for (int len = 0; len <= t.length(); len++) {
                    String prefix = t.substring(0, len);
                    for (int i = 0; i <= len + 1; i++) {
                        int e = form.headEnd(prefix, i);
                        assertTrue(e == -1 || (e > i && e <= len), form + " Text: " + prefix + " Position " + i);
                    }
                }
            }
        }
    }

    // ---- ":" als zusaetzliche Startgrenze auch fuer die Wurzel-Formen (nicht fuer UNIX, HOME, EINZEL_BACKSLASH)

    @Test
    void doppelpunktDavorIstStartgrenzeAuchFuerDieWurzelFormen() {
        wurzelUeberScanner(bs("Pfad:Users¦erika¦akte.pdf"), bs("Users¦erika¦akte.pdf"));
        wurzelUeberScanner("Hinweis:home/erika/x", "home/erika/x");
        wurzelUeberScanner(bs("Pfad:Benutzer¦erika"), bs("Benutzer¦erika"));
        wurzelUeberScanner("Pfad:Benutzer/erika/x", "Benutzer/erika/x");
        wurzelUeberScanner("a:Users/b", "Users/b");
        wurzelUeberScanner(bs("C:Users¦a"), bs("Users¦a"));
        wurzelUeberScanner(bs("x::Users¦a"), bs("Users¦a"));
        wurzel(bs("\"Pfad:Users¦erika¦Mein Ordner¦x\""), bs("Users¦erika¦Mein Ordner¦x"));
        wurzel("\"Hinweis:home/erika/Mein Ordner/x\"", "home/erika/Mein Ordner/x");
        // ein Doppelpunkt DAHINTER macht den Kopf nicht
        keinFund("Benutzer:erika/x/y");
        // Gendern-Ausnahme gilt auch hinter dem Doppelpunkt
        keinFund("Pfad:Benutzer/in/x");
        keinFund("Pfad:Benutzer/Passwort");
    }

    @Test
    void doppelpunktWechselwirkungMitHostPfadUndLaufwerkIstDasBisherigeVerhalten() {
        // Kopf "Users:/" gehoert HOST_PFAD (Host-Lauf "Users"), nicht der Wurzel; das Wort davor bleibt stehen
        fund("Pfad:Users:/x", "Users:/x", "pfad-host", 0.80, ConfidenceLevel.WAHRSCHEINLICH);
        fund("Pfad:Users:/x/y", "Users:/x/y", "pfad-host", 0.90, ConfidenceLevel.SICHER);
        fund("Pfad:Benutzer:/a/b", "Benutzer:/a/b", "pfad-host", 0.90, ConfidenceLevel.SICHER);
        fund("Pfad:home:/a/b", "home:/a/b", "pfad-host", 0.90, ConfidenceLevel.SICHER);
        // Laufwerkskopf: "s:\" aus "Users:\"
        fund(bs("Pfad:Users:¦a"), bs("s:¦a"), "pfad-laufwerk", 0.95, ConfidenceLevel.SICHER);
        keinFund("Pfad:Users:/");
    }

    @Test
    void doppelpunktIstFuerUnixHomeUndEinzelBackslashAuchMitWurzelKeineStartgrenze() {
        keinFund("Pfad:~/a");
        keinFund(bs("Pfad::¦a¦b"));
        // /home/x:Users\erika: ein Unix-Fund ueber den ganzen Lauf, kein eigener Wurzel-Fund
        fund(bs("/home/x:Users¦erika"), bs("/home/x:Users¦erika"), "pfad-unix", 0.90, ConfidenceLevel.SICHER);
    }

    @Test
    void wurzelNachGendernAusnahmeLaesstSpaetereFundeInDemSatzUnberuehrt() {
        wurzel(bs("Benutzer/in und Users¦erika¦x"), bs("Users¦erika¦x"));
        assertEquals(List.of(bs("Users¦erika¦x")), werte(bs("Liebe Benutzer/innen, siehe Users¦erika¦x.")));
    }

    @Test
    void wurzelNachLaufwerkOderFuehrendemBackslashOderSchraegstrichGiltDerAeltereFund() {
        // C:\Users\erika\x: die Startgrenze fehlt an "Users" (davor Backslash); es bleibt der Laufwerksfund (0.95)
        fund(bs("C:¦Users¦erika¦x"), bs("C:¦Users¦erika¦x"), "pfad-laufwerk", 0.95, ConfidenceLevel.SICHER);
        // \Users\erika\x: EINZEL_BACKSLASH (0.75)
        backslash(bs("¦Users¦erika¦x"), bs("¦Users¦erika¦x"));
        // /home/erika/x: UNIX (0.90, drei Segmente); ~/home/x: HOME
        fund("/home/erika/x", "/home/erika/x", "pfad-unix", 0.90, ConfidenceLevel.SICHER);
        fund("~/home/x", "~/home/x", "pfad-home", 0.90, ConfidenceLevel.SICHER);
        fund("/opt/home/erika/x", "/opt/home/erika/x", "pfad-unix", 0.90, ConfidenceLevel.SICHER);
    }

    @Test
    void bekannteFolgeUsersGroupsUndHomeImSatzSindFehltreffer() {
        // zu viel ersetzt, kein Leck (Nutzerentscheidung 20.11.2026: lieber zu viel schuetzen)
        wurzel("Users/Groups", "Users/Groups");
        wurzel("users/groups", "users/groups");
        wurzel("Users/Admins", "Users/Admins");
        wurzel(bs("Benutzer¦Gruppen"), bs("Benutzer¦Gruppen"));
        wurzel("Benutzer/Rollen/Rechte", "Benutzer/Rollen/Rechte");
        wurzel("at home/work/x", "home/work/x");
        wurzel("Arbeit at home/work/x erledigt", "home/work/x");
    }

    @Test
    void bekannteFolgeFrueherBeginnenderWurzelFundVerschluecktSpaeterenLaufwerksFund() {
        // Regel 7a (ii): Der Fund ab "Users" (0.60) reicht ueber den ganzen Lauf; der spaetere Laufwerksfund (0.95)
        // geht darin auf, die Konfidenz ist die niedrigste der Beteiligten. Kein Fund geht verloren.
        wurzel(bs("Users¦erika;C:¦Tools¦a"), bs("Users¦erika;C:¦Tools¦a"));
        wurzel(bs("home/erika/x;C:¦Tools¦a"), bs("home/erika/x;C:¦Tools¦a"));
        // umgekehrte Reihenfolge: der fruehere Laufwerksfund (0.95) umfasst den Wurzel-Kopf, die Konfidenz bleibt
        fund(bs("C:¦Tools¦a;Users¦erika"), bs("C:¦Tools¦a;Users¦erika"), "pfad-laufwerk", 0.95,
                ConfidenceLevel.SICHER);
    }

    @Test
    void wurzelPseudonymisierungOhneNamenImErgebnisMitRoundtrip() {
        String[][] faelle = {
            {bs("Users¦erika.mustermann¦Desktop¦akte.pdf"), "erika.mustermann", "akte.pdf"},
            {"users/erika/Desktop", "erika"},
            {bs("liegt unter Users¦erika.mustermann¦Desktop."), "erika.mustermann"},
            {bs("Benutzer¦erika¦Desktop"), "erika"},
            {"home/erika/akte.pdf", "erika", "akte.pdf"},
            {"Benutzer/erika/Desktop", "erika"},
            {bs("Pfad:Users¦erika¦akte.pdf"), "erika", "akte.pdf"},
            {bs("\"Users¦Erika Mustermann¦Desktop\""), "Erika Mustermann"},
        };
        Pseudonymizer pseudo = new Pseudonymizer();
        Reidentifier rueck = new Reidentifier();
        for (String[] fall : faelle) {
            String text = fall[0];
            List<Finding> s = SCANNER.scan(text);
            assertEquals(1, s.size(), "Text: " + text);
            assertEquals("pfad-relativ", s.get(0).source(), "Text: " + text);
            PseudonymizationResult p = pseudo.pseudonymize(text, s);
            for (int i = 1; i < fall.length; i++) {
                assertFalse(p.text().contains(fall[i]), "Namensbestandteil im Ergebnis, Name Nr. " + i);
            }
            ReidentificationResult back = rueck.restore(p.text(), p.mapping());
            assertEquals(text, back.text(), "Roundtrip");
            assertTrue(back.unresolved().isEmpty(), "Meldung");
        }
    }

    @Test
    void doppelpunktUndRelativPseudonymisierungMitRoundtrip() {
        String[] faelle = {bs("Pfad:.¦erika¦akte.pdf"), "Pfad:./erika/akte.pdf", "PATH=.:./bin:../erika/bin"};
        Pseudonymizer pseudo = new Pseudonymizer();
        Reidentifier rueck = new Reidentifier();
        for (String text : faelle) {
            PseudonymizationResult p = pseudo.pseudonymize(text, SCANNER.scan(text));
            assertFalse(p.text().contains("erika"), "Name im Ergebnis");
            assertEquals(text, rueck.restore(p.text(), p.mapping()).text());
        }
    }

    // ================================================================== (5) Abgrenzung

    @Test
    void laufwerkPfadMitBackslashesBleibtEinFundMit095() {
        for (String t : new String[] {"C:¦Tools¦a¦b", "C:¦Tools¦BeispielTool¦tool.exe", "x=C:¦Tools¦a¦b",
                "C:¦Tools¦a¦b¦", "\"C:¦Tools¦a b¦c\"", "C:¦a;¦b¦c", "C:¦a,¦b¦c", "C:¦a=¦b¦c"}) {
            for (Finding f : funde(bs(t))) {
                assertEquals(0.95, f.confidence(), t);
                assertEquals("pfad-laufwerk", f.source(), t);
            }
            assertEquals(1, funde(bs(t)).size(), t);
        }
    }

    @Test
    void uncPfadBleibtEinFundMit095UndWirdNichtAbgesenkt() {
        for (String t : new String[] {"¦¦srv¦freigabe¦a¦b", "¦¦srv¦freigabe¦a b¦c", "x=¦¦srv¦freigabe¦a¦b",
                "\"¦¦srv¦freigabe¦a b¦c\"", "¦¦srv¦freigabe,¦a¦b", "¦¦srv¦a;¦b¦c"}) {
            List<Finding> f = funde(bs(t));
            assertTrue(f.stream().anyMatch(x -> x.confidence() == 0.95 && x.source().equals("pfad-unc")), t);
        }
    }

    @Test
    void relativerPfadImLaufwerksUndUnixPfadAendertKeineKonfidenz() {
        fund("/opt/a/../b/c", "/opt/a/../b/c", "pfad-unix", 0.90, ConfidenceLevel.SICHER);
        fund("~/a/./b", "~/a/./b", "pfad-home", 0.90, ConfidenceLevel.SICHER);
        fund(bs("C:¦Tools¦..¦a"), bs("C:¦Tools¦..¦a"), "pfad-laufwerk", 0.95, ConfidenceLevel.SICHER);
    }

    @Test
    void urlMitRelativenAnteilenBleibtEinUrlFundUeberDenScanner() {
        for (String t : new String[] {"https://example.invalid/a/../b", "https://example.invalid/./a/b",
                "http://example.invalid/?p=../a/b"}) {
            List<Finding> s = SCANNER.scan(t);
            assertEquals(1, s.size(), t);
            assertEquals(FindingType.URL, s.get(0).type(), t);
            assertEquals(t, s.get(0).originalValue(), t);
        }
    }

    @Test
    void scannerLiefertRelativeFundeAlsPfad() {
        List<Finding> s = SCANNER.scan(bs("Nutze ./skript.sh und ..¦konfig¦a.yml sowie ¦srv¦freigabe¦a.txt"));
        assertEquals(3, s.size());
        for (Finding f : s) {
            assertEquals(FindingType.PFAD, f.type());
            assertEquals("pfad-relativ", f.source());
        }
        assertEquals(0.80, s.get(0).confidence());
        assertEquals(0.80, s.get(1).confidence());
        assertEquals(0.75, s.get(2).confidence());
    }

    @Test
    void emailInRelativemPfadBleibtEinPfadFund() {
        List<Finding> s = SCANNER.scan("./erika.mustermann@example.invalid/ablage/x");
        assertEquals(1, s.size());
        assertEquals(FindingType.PFAD, s.get(0).type());
    }

    @Test
    void obermengeFuerKonkreteTricktexteGegenStandT13() {
        PathForm[] alt = {PathForm.LAUFWERK, PathForm.UNC, PathForm.UNIX, PathForm.HOME, PathForm.HOST_PFAD};
        String[] texte = {"C:¦Tools¦a¦b", "¦¦srv¦a)¦C:¦Beispiel(x86)", "¦¦srv¦freigabe¦a b", "x=\"¦¦srv¦a b¦c\"",
                "C:¦a ¦b¦c", "/opt/a/b ¦b¦c", "~/a ¦b¦c", "host:/a/b ¦c¦d", "C:¦Akten'Pfad:/x y'", "¦¦srv¦a;¦b¦c)"};
        for (String roh : texte) {
            String t = bs(roh);
            boolean[] neu = abdeckung(t, R.detect(t));
            boolean[] vorher = abdeckung(t, R.detect(t, alt));
            for (int i = 0; i < t.length(); i++) {
                assertTrue(!vorher[i] || neu[i], "Position " + i + " nicht mehr abgedeckt in: " + t);
            }
        }
    }

    private static boolean[] abdeckung(String text, List<Finding> funde) {
        boolean[] a = new boolean[text.length()];
        for (Finding f : funde) {
            for (int i = f.start(); i < f.end(); i++) {
                a[i] = true;
            }
        }
        return a;
    }

    // ================================================================== (6) Formvertrag

    @Test
    void formRelativVertrag() {
        assertEquals(1, PathForm.RELATIV.minSegments());
        assertEquals("pfad-relativ", PathForm.RELATIV.source());
        assertFalse(PathForm.RELATIV.isStartBoundary("a./b", 1));
        assertTrue(PathForm.RELATIV.isStartBoundary(" ./b", 1));
        assertTrue(PathForm.RELATIV.isStartBoundary(":./b", 1));
        assertTrue(PathForm.RELATIV.isStartBoundary("./b", 0));
        assertEquals(2, PathForm.RELATIV.headEnd("./a", 0));
        assertEquals(2, PathForm.RELATIV.headEnd(bs(".¦a"), 0));
        assertEquals(3, PathForm.RELATIV.headEnd("../a", 0));
        assertEquals(3, PathForm.RELATIV.headEnd(bs("..¦a"), 0));
        assertEquals(2, PathForm.RELATIV.headEnd("./", 0));
        assertEquals(-1, PathForm.RELATIV.headEnd("...", 0));
        assertEquals(-1, PathForm.RELATIV.headEnd(".../a", 0));
        assertEquals(-1, PathForm.RELATIV.headEnd("..", 0));
        assertEquals(-1, PathForm.RELATIV.headEnd(".", 0));
        assertEquals(-1, PathForm.RELATIV.headEnd("a./b", 0));
        assertEquals(-1, PathForm.RELATIV.headEnd("", 0));
        assertEquals(-1, PathForm.RELATIV.headEnd("./a", 3));
        assertEquals(4, PathForm.RELATIV.headEnd("x ./a", 2));
        assertEquals(-1, PathForm.RELATIV.headEnd(".a/b", 0));
    }

    @Test
    void formEinzelBackslashVertrag() {
        assertEquals(2, PathForm.EINZEL_BACKSLASH.minSegments());
        assertEquals("pfad-relativ", PathForm.EINZEL_BACKSLASH.source());
        assertFalse(PathForm.EINZEL_BACKSLASH.isStartBoundary("a\\b", 1));
        assertFalse(PathForm.EINZEL_BACKSLASH.isStartBoundary(":\\b", 1));
        assertTrue(PathForm.EINZEL_BACKSLASH.isStartBoundary(" \\b", 1));
    }

    @Test
    void formBenutzerWurzelVertrag() {
        PathForm f = PathForm.BENUTZER_WURZEL;
        assertEquals(1, f.minSegments());
        assertEquals("pfad-relativ", f.source());
        assertEquals(6, f.headEnd(bs("Users¦a"), 0));
        assertEquals(6, f.headEnd("users/a", 0));
        assertEquals(6, f.headEnd("USERS/a", 0));
        assertEquals(9, f.headEnd(bs("Benutzer¦a"), 0));
        assertEquals(9, f.headEnd(bs("benutzer¦a"), 0));
        assertEquals(8, f.headEnd("x Users/a", 2));
        // Benutzer mit Schraegstrich gehoert der Form WURZEL_ZWEI_SEGMENTE
        assertEquals(-1, f.headEnd("benutzer/a", 0));
        assertEquals(-1, f.headEnd("Benutzer/erika/x", 0));
        assertEquals(-1, f.headEnd("Users", 0));
        assertEquals(-1, f.headEnd("Users:", 0));
        assertEquals(-1, f.headEnd("Usersa/", 0));
        assertEquals(-1, f.headEnd("Benutzer", 0));
        assertEquals(-1, f.headEnd(bs("Benutzer"), 0));
        assertEquals(-1, f.headEnd("", 0));
        assertEquals(-1, f.headEnd("Users/a", 1));
        assertEquals(-1, f.headEnd("Users/a", 99));
        assertEquals(-1, f.headEnd("\u017Fers/a", 0));
        // Gendern betrifft diese Form nicht: Benutzer mit Backslash und Users mit beiden Trennern
        assertEquals(9, f.headEnd(bs("Benutzer¦in"), 0));
        assertEquals(6, f.headEnd("Users/in", 0));
        assertTrue(f.isStartBoundary(" Users/a", 1));
        assertFalse(f.isStartBoundary("xUsers/a", 1));
        assertTrue(f.isStartBoundary(":Users/a", 1));
        assertEquals(0.60, f.confidence(6, 1));
        assertEquals(0.60, f.confidence(9, 3));
    }

    @Test
    void formWurzelZweiSegmenteVertrag() {
        PathForm f = PathForm.WURZEL_ZWEI_SEGMENTE;
        assertEquals(2, f.minSegments());
        assertEquals("pfad-relativ", f.source());
        // home/
        assertEquals(5, f.headEnd("home/a/b", 0));
        assertEquals(7, f.headEnd("x home/a/b", 2));
        assertEquals(-1, f.headEnd("Home/a/b", 0));
        assertEquals(-1, f.headEnd(bs("home¦a¦b"), 0));
        assertEquals(-1, f.headEnd("home", 0));
        assertEquals(-1, f.headEnd("homes/a/b", 0));
        assertEquals(-1, f.headEnd("", 0));
        assertEquals(-1, f.headEnd("home/a", 99));
        // Benutzer/ (nur Schraegstrich, Gross-/Kleinschreibung egal)
        assertEquals(9, f.headEnd("Benutzer/a/b", 0));
        assertEquals(9, f.headEnd("benutzer/a", 0));
        assertEquals(9, f.headEnd("BENUTZER/a", 0));
        assertEquals(11, f.headEnd("x Benutzer/a", 2));
        assertEquals(-1, f.headEnd(bs("Benutzer¦a"), 0));
        assertEquals(-1, f.headEnd("Benutzer", 0));
        assertEquals(-1, f.headEnd("Benutzera/b", 0));
        assertEquals(-1, f.headEnd("\u017Fenutzer/a", 0));
        assertEquals(-1, f.headEnd("Users/a/b", 0));
        // Gendern: erstes Segment genau in/innen und direkt ein Trenner dahinter
        assertEquals(-1, f.headEnd("Benutzer/in/x", 0));
        assertEquals(-1, f.headEnd("Benutzer/innen/x", 0));
        assertEquals(-1, f.headEnd("benutzer/IN/", 0));
        assertEquals(-1, f.headEnd(bs("Benutzer/in¦x"), 0));
        assertEquals(9, f.headEnd("Benutzer/in", 0));
        assertEquals(9, f.headEnd("Benutzer/innen", 0));
        assertEquals(9, f.headEnd("Benutzer/in\"", 0));
        assertEquals(9, f.headEnd("Benutzer/innen.", 0));
        assertEquals(9, f.headEnd("Benutzer/in-Konto/x", 0));
        assertEquals(9, f.headEnd("Benutzer/ina/x", 0));
        assertEquals(9, f.headEnd("Benutzer/i", 0));
        assertEquals(9, f.headEnd("Benutzer/inn", 0));
        assertEquals(9, f.headEnd("Benutzer/\u0131n/x", 0));
        assertEquals(9, f.headEnd("Benutzer/", 0));
        assertEquals(-1, f.headEnd("Benutzer/", 99));
        assertTrue(f.isStartBoundary("(home/a/b", 1));
        assertFalse(f.isStartBoundary("xhome/a/b", 1));
        assertTrue(f.isStartBoundary(":home/a/b", 1));
        assertTrue(f.isStartBoundary(":Benutzer/a/b", 1));
        assertEquals(0.60, f.confidence(5, 2));
        assertEquals(0.60, f.confidence(9, 3));
    }

    @Test
    void jedeFormHatDefinierteStartgrenzeAnPosition0() {
        for (PathForm form : PathForm.values()) {
            assertTrue(form.isStartBoundary("x", 0), form.name());
            assertTrue(form.isStartBoundary(" x", 1), form.name());
        }
    }

    @Test
    void konfidenzMonotonNichtFallendUndWerte() {
        for (PathForm form : new PathForm[] {PathForm.RELATIV, PathForm.EINZEL_BACKSLASH, PathForm.BENUTZER_WURZEL,
                PathForm.WURZEL_ZWEI_SEGMENTE}) {
            for (int kopf : new int[] {1, 2, 3, 7, 66}) {
                double vorige = form.confidence(kopf, 0);
                for (int segmente = 1; segmente <= 6; segmente++) {
                    double jetzt = form.confidence(kopf, segmente);
                    assertTrue(jetzt >= vorige, form + " faellt bei " + segmente + " Segmenten, Kopf " + kopf);
                    vorige = jetzt;
                }
            }
        }
        assertEquals(0.80, PathForm.RELATIV.confidence(2, 1));
        assertEquals(0.80, PathForm.RELATIV.confidence(3, 3));
        assertEquals(0.75, PathForm.EINZEL_BACKSLASH.confidence(1, 2));
        assertEquals(0.75, PathForm.EINZEL_BACKSLASH.confidence(1, 3));
    }

    // ================================================================== (7) Grenzlast (AK-49), je < 2 s

    /** Erkenner und Standard-Scanner; die Fundzahl wird exakt geprueft, beide liefern dieselbe Zahl (keine URL). */
    private static void laufzeit(String text, int erwartet) {
        List<Finding> r = assertTimeoutPreemptively(LIMIT, () -> R.detect(text));
        pruefeStruktur(text, r);
        assertEquals(erwartet, r.size(), "Fundzahl Erkenner");
        List<Finding> s = assertTimeoutPreemptively(LIMIT, () -> SCANNER.scan(text));
        assertEquals(erwartet, s.size(), "Fundzahl Scanner");
    }

    @Test
    void laufzeitPunkteHunderttausendFach() {
        laufzeit(".".repeat(N), 0);
    }

    @Test
    void laufzeitPunktSchraegstrichNMal() {
        laufzeit("./".repeat(N / 2), 1);
    }

    @Test
    void laufzeitPunktPunktSchraegstrichNMal() {
        laufzeit("../".repeat(N / 3), 1);
    }

    @Test
    void laufzeitPunktSchraegstrichAMitLeerzeichenNMal() {
        laufzeit("./a ".repeat(N / 4), N / 4);
    }

    @Test
    void laufzeitPunktPunktBackslashNMal() {
        laufzeit(bs("..¦").repeat(N / 3), 1);
        laufzeit(bs("..¦..¦").repeat(N / 6), 1);
    }

    @Test
    void laufzeitNurBackslashesKeinFund() {
        laufzeit(bs("¦").repeat(N), 0);
    }

    @Test
    void laufzeitBackslashAEinLauf() {
        laufzeit(bs("¦a").repeat(N / 2), 1);
    }

    @Test
    void laufzeitBackslashABackslashBMitLeerzeichenNMal() {
        laufzeit(bs("¦a¦b ").repeat(N / 5), N / 5);
    }

    @Test
    void laufzeitABackslashBMitLeerzeichenKeinFund() {
        laufzeit(bs("a¦b ").repeat(N / 4), 0);
    }

    @Test
    void laufzeitAnfuehrungszeichenNieGeschlossenMitRelativemKopf() {
        // ein einziges oeffnendes Zeichen, danach sehr viel Inhalt ohne Schluss: Rueckfall ohne Zitat
        laufzeit("\"./a b".repeat(1) + " x".repeat(N / 2), 1);
    }

    @Test
    void laufzeitZitatRelativNMal() {
        // k Wiederholungen von Anfuehrungszeichen + "./a b": k-1 Zitat-Funde (bis zum naechsten Zeichen), der letzte
        // Rest ohne Zitat ist ein Fund "./a" (Rueckfall)
        laufzeit("\"./a b".repeat(N / 6), N / 6);
    }

    @Test
    void laufzeitJedeAnfuehrungszeichenArtMitRelativemKopf() {
        for (String q : ANF) {
            int k = N / (q.length() + 5);
            laufzeit((q + "./a b").repeat(k), k);
        }
    }

    @Test
    void laufzeitJedeAnfuehrungszeichenArtMitBackslashKopf() {
        for (String q : ANF) {
            int k = N / (q.length() + 6);
            laufzeit((q + bs("¦a¦b c")).repeat(k), k);
        }
    }

    @Test
    void laufzeitAufzaehlungPunktSchraegstrichKeinFund() {
        laufzeit("1./2. ".repeat(N / 6), 0);
    }

    @Test
    void laufzeitDichteKoepfeMitKlammerSegmentAmEnde() {
        // Koepfe, deren Segmentzahl erst nach der Kuerzung stimmt: ein Lauf, ein Fund
        laufzeit("(./)".repeat(N / 4), 1);
        laufzeit(bs("(¦a)¦").repeat(N / 5), 1);
    }

    @Test
    void laufzeitBenutzerWurzelBackslashAMitLeerzeichenNMal() {
        laufzeit(bs("Users¦a ").repeat(N / 8), N / 8);
    }

    @Test
    void laufzeitUsersSchraegstrichNMal() {
        laufzeit("users/".repeat(N / 6), 1);
        laufzeit("Users/ ".repeat(N / 7), 0);
    }

    @Test
    void laufzeitHomeWurzelNMal() {
        laufzeit("home/a/b ".repeat(N / 9), N / 9);
        laufzeit("home/".repeat(N / 5), 1);
        laufzeit("home/a ".repeat(N / 7), 0);
    }

    @Test
    void laufzeitBenutzerGendernAusnahmeNMal() {
        laufzeit("Benutzer/in ".repeat(N / 12), 0);
        laufzeit("Benutzer/innen. ".repeat(N / 16), 0);
        laufzeit("Benutzer/in/x ".repeat(N / 14), 0);
        // ein Lauf: das erste Segment ist "inBenutzer", also kein Gendern; ein Fund
        laufzeit("Benutzer/in".repeat(N / 11), 1);
    }

    @Test
    void laufzeitBenutzerSchraegstrichEinSegmentUndZweiSegmenteNMal() {
        laufzeit("Benutzer/a ".repeat(N / 11), 0);
        laufzeit("Benutzer/a/b ".repeat(N / 13), N / 13);
        laufzeit("Benutzer/in/a ".repeat(N / 14), 0);
        laufzeit("Benutzer/ina/b ".repeat(N / 15), N / 15);
    }

    @Test
    void laufzeitDoppelpunktUsersBackslashAMitLeerzeichenNMal() {
        laufzeit(bs(":Users¦a ").repeat(N / 9), N / 9);
        laufzeit(":home/a/b ".repeat(N / 10), N / 10);
        laufzeit(":Benutzer/a/b ".repeat(N / 14), N / 14);
        laufzeit(":Benutzer/a ".repeat(N / 12), 0);
    }

    @Test
    void laufzeitZitatBenutzerWurzelNMal() {
        laufzeit(("\"" + bs("Users¦a b")).repeat(N / 10), N / 10);
    }

    @Test
    void laufzeitJedeAnfuehrungszeichenArtMitBenutzerWurzel() {
        for (String q : ANF) {
            int k = N / (q.length() + 9);
            laufzeit((q + bs("Users¦a b")).repeat(k), k);
            laufzeit((q + "home/a/b c").repeat(N / (q.length() + 10)), N / (q.length() + 10));
        }
    }

    @Test
    void laufzeitDoppelpunktPunktSchraegstrichNMal() {
        laufzeit(":./a ".repeat(N / 5), N / 5);
        laufzeit(":./".repeat(N / 3), 1);
        laufzeit("12:30 ".repeat(N / 6), 0);
    }

    @Test
    void laufzeitGemischterRealistischerText() {
        String block = bs("Siehe ./skript.sh und ..¦konfig¦a.yml, dann ¦srv¦freigabe¦a.txt; Ende../x 1./2. usw./etc. "
                + "C:¦Tools¦a.exe ok. Zeile¦nNeu 1.0/2.0 und/oder km/h.\n");
        int k = N / block.length();
        laufzeit(block.repeat(k), 4 * k);
    }
}
