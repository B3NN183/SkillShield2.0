package de.skillshield.core.recognizer.general;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Tests der Anführungszeichenregel (T-11, Plan Kap. 2.4.3). Pfade in Testtexten werden mit {@code bs(...)}
 * geschrieben (Plan Kap. 2.10); alle Namen sind synthetisch. Nicht-ASCII-Zeichen stehen nur als Escapes im Quelltext.
 *
 * <p>Jeder Test prüft über {@link #werte} je Fund: Teilstring = Originalwert, Typ PFAD, Quelle pfad-laufwerk,
 * Konfidenz 0.95 (SICHER), sortiert, überlappungsfrei, Grenzen nicht in Surrogatpaar oder Kombinationsfolge.
 */
class PathRecognizerAnfuehrungTest {

    private static final PathRecognizer R = new PathRecognizer();
    private static final Duration LIMIT = Duration.ofSeconds(2);

    // typografische Anführungszeichen (nur als Escapes)
    private static final String DE_AUF = "\u201E";
    private static final String DE_ZU = "\u201C";
    private static final String EN_AUF = "\u201C";
    private static final String EN_ZU = "\u201D";
    private static final String FR_AUF = "\u00BB";
    private static final String FR_ZU = "\u00AB";
    private static final String S_AUF = "\u2018";
    private static final String S_ZU = "\u2019";
    private static final String S_DE_AUF = "\u201A";

    // ------------------------------------------------------------------ Hilfen

    private static List<Finding> funde(String text) {
        List<Finding> funde = R.detect(text);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring");
            assertEquals(FindingType.PFAD, f.type());
            assertEquals("pfad-laufwerk", f.source());
            assertEquals(0.95, f.confidence());
            assertEquals(ConfidenceLevel.SICHER, f.level());
            assertTrue(f.start() >= vorigesEnde, "sortiert und überlappungsfrei");
            assertGrenzenSauber(text, f);
            vorigesEnde = f.end();
        }
        return funde;
    }

    private static List<String> werte(String text) {
        return funde(text).stream().map(Finding::originalValue).collect(Collectors.toList());
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

    private static void assertKeinFund(String text) {
        assertEquals(List.of(), werte(text), "Text: " + text);
    }

    private static int zaehle(String text) {
        return werte(text).size();
    }

    private static String pseudonymisiert(String text) {
        List<Finding> f = TextScanner.standard().scan(text);
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, f);
        ReidentificationResult back = new Reidentifier().restore(p.text(), p.mapping());
        assertEquals(text, back.text(), "Roundtrip");
        assertTrue(back.unresolved().isEmpty());
        return p.text();
    }

    // ------------------------------------------------------------------ AK-16 / AK-17 (c): Pfad mit Leerzeichen

    @Test
    void ak16PfadMitLeerzeichenInGeradenAnfuehrungszeichen() {
        assertEinFund(bs("Starte \"C:¦Program Files¦Mein Tool¦tool.exe\" jetzt"), bs("C:¦Program Files¦Mein Tool¦tool.exe"));
    }

    @Test
    void ak16PfadMitLeerzeichenInEinfachenAnfuehrungszeichen() {
        assertEinFund(bs("Starte 'C:¦Program Files¦Mein Tool¦tool.exe' jetzt"), bs("C:¦Program Files¦Mein Tool¦tool.exe"));
    }

    @Test
    void ak16PfadMitLeerzeichenInBackticks() {
        assertEinFund(bs("Starte `C:¦Program Files¦Mein Tool¦tool.exe` jetzt"), bs("C:¦Program Files¦Mein Tool¦tool.exe"));
    }

    @Test
    void ak17cDerselbePfadOhneAnfuehrungszeichenEndetAmErstenLeerzeichen() {
        assertEinFund(bs("Starte C:¦Program Files¦Mein Tool¦tool.exe jetzt"), bs("C:¦Program"));
    }

    @Test
    void ak16FundHatDieKonfidenzDerFormNichtWegenAnfuehrungszeichenEineAndere() {
        Finding ohne = funde(bs("Starte C:¦Program Files jetzt")).get(0);
        Finding mit = funde(bs("Starte \"C:¦Program Files\" jetzt")).get(0);
        assertEquals(ohne.confidence(), mit.confidence());
        assertEquals(ohne.source(), mit.source());
        assertEquals(ohne.type(), mit.type());
    }

    // ------------------------------------------------------------------ AK-20: Anführungszeichen bleiben stehen

    @Test
    void ak20AnfuehrungszeichenBleibenImPseudonymisiertenText() {
        assertEquals("\"[[PFAD_1]]\"", pseudonymisiert(bs("\"C:¦Tools¦BeispielTool¦tool.exe\"")));
        assertEquals("'[[PFAD_1]]'", pseudonymisiert(bs("'C:¦Tools¦BeispielTool¦tool.exe'")));
        assertEquals("`[[PFAD_1]]`", pseudonymisiert(bs("`C:¦Tools¦BeispielTool¦tool.exe`")));
    }

    @Test
    void ak20AnfuehrungszeichenGehoerenNichtZumFundAuchMitLeerzeichen() {
        String text = bs("\"C:¦Mein Ordner¦a.txt\"");
        Finding f = funde(text).get(0);
        assertEquals(1, f.start());
        assertEquals(text.length() - 1, f.end());
    }

    @Test
    void ak20PseudonymisierungMitLeerzeichenImPfad() {
        String text = bs("Starte \"C:¦Program Files¦Mein Tool¦tool.exe\" jetzt");
        assertEquals("Starte \"[[PFAD_1]]\" jetzt", pseudonymisiert(text));
    }

    // ------------------------------------------------------------------ Nutzerfall (Scanner, Roundtrip)

    @Test
    void nutzerfallStandardScannerEinFundKeinTeilImKlartextRoundtripGleich() {
        String text = bs("Starte \"C:¦Program Files¦Mein Tool¦tool.exe\" und pr\u00FCfe das Log.");
        List<Finding> funde = TextScanner.standard().scan(text);
        assertEquals(1, funde.size());
        Finding f = funde.get(0);
        assertEquals(FindingType.PFAD, f.type());
        assertEquals(bs("C:¦Program Files¦Mein Tool¦tool.exe"), f.originalValue());
        String p = pseudonymisiert(text);
        assertEquals("Starte \"[[PFAD_1]]\" und pr\u00FCfe das Log.", p);
        for (String teil : new String[] {"Program", "Files", "Mein Tool", "tool.exe"}) {
            assertFalse(p.contains(teil), "Bestandteil im Klartext: " + teil);
        }
    }

    @Test
    void nutzerfallOhneAnfuehrungszeichenBleibtBekannteGrenzeNurCProgram() {
        String text = bs("Starte C:¦Program Files¦Mein Tool¦tool.exe und pr\u00FCfe das Log.");
        List<Finding> funde = TextScanner.standard().scan(text);
        assertEquals(1, funde.size());
        assertEquals(bs("C:¦Program"), funde.get(0).originalValue());
        assertEquals(bs("Starte [[PFAD_1]] Files¦Mein Tool¦tool.exe und pr\u00FCfe das Log."), pseudonymisiert(text));
    }

    // ------------------------------------------------------------------ AK-23: Unicode in Anführungszeichen

    @Test
    void ak23UmlauteSonderzeichenUndLeerzeichenInAnfuehrungszeichenGanz() {
        String pfad = bs("C:¦Users¦mustermann¦\u00DCbersicht¦Gr\u00F6\u00DFe \u2713.txt");
        assertEinFund("\"" + pfad + "\"", pfad);
        assertEinFund("Siehe '" + pfad + "' dort", pfad);
    }

    @Test
    void ak23EmojiAlsSurrogatpaarInAnfuehrungszeichenGanz() {
        String pfad = bs("C:¦Users¦mustermann¦\uD83D\uDE00 Ordner¦a \uD83D\uDE00.txt");
        assertEinFund("\"" + pfad + "\"", pfad);
        // Emoji unmittelbar vor dem schließenden Zeichen
        String pfad2 = bs("C:¦Users¦mustermann¦Mein Ordner¦a\uD83D\uDE00");
        assertEinFund("\"" + pfad2 + "\"", pfad2);
    }

    @Test
    void ak23KombinierendesZeichenVorDemSchliessendenZeichenBleibtImFund() {
        String pfad = bs("C:¦Users¦mustermann¦Mein Ordner¦e\u0301");
        assertEinFund("\"" + pfad + "\"", pfad);
    }

    // ------------------------------------------------------------------ Rückfall ohne schließendes Zeichen

    @Test
    void nieGeschlossenFaelltAufDieRegelOhneAnfuehrungszeichenZurueck() {
        assertEinFund(bs("Starte \"C:¦Program Files¦x"), bs("C:¦Program"));
        assertEinFund(bs("Starte 'C:¦Program Files¦x"), bs("C:¦Program"));
        assertEinFund(bs("Starte `C:¦Program Files¦x"), bs("C:¦Program"));
    }

    @Test
    void nurSchliessendesZeichenOhneOeffnendesFaelltZurueck() {
        assertEinFund(bs("Starte C:¦Program Files¦x\" Ende"), bs("C:¦Program"));
    }

    @Test
    void anfuehrungszeichenErstNachZeilenendeFaelltZurueck() {
        assertEinFund(bs("Starte \"C:¦Program Files¦x\nund so\" Ende"), bs("C:¦Program"));
        assertEinFund(bs("Starte \"C:¦Program Files¦x\rund so\" Ende"), bs("C:¦Program"));
        assertEinFund(bs("Starte \"C:¦Program Files¦x\r\nund so\" Ende"), bs("C:¦Program"));
    }

    @Test
    void anfuehrungszeichenErstNachTabFaelltZurueck() {
        assertEinFund(bs("Starte \"C:¦Program Files¦x\tund so\" Ende"), bs("C:¦Program"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"|", "<", ">", "\u2028", "\u2029", "\u0000"})
    void abbruchzeichenVorDemSchlussFaelltZurueck(String abbruch) {
        assertEinFund(bs("\"C:¦Program Files") + abbruch + "x\"", bs("C:¦Program"));
    }

    @Test
    void pipeVorDemSchlussFaelltZurueck() {
        assertEinFund(bs("Starte \"C:¦Program Files|x\" Ende"), bs("C:¦Program"));
    }

    @Test
    void einfachesZeichenWirdVomGeradenDoppeltenZeichenAbgebrochen() {
        // "'" und "`" suchen ihr Schlusszeichen nicht über ein gerades " hinweg
        assertEinFund(bs("'C:¦Program Files \"Rest\" x'"), bs("C:¦Program"));
        assertEinFund(bs("`C:¦Program Files \"Rest\" x`"), bs("C:¦Program"));
    }

    @Test
    void geradesDoppeltesZeichenSuchtUeberApostrophHinweg() {
        assertEinFund(bs("\"C:¦Program Files's x\" y"), bs("C:¦Program Files's x"));
    }

    // ------------------------------------------------------------------ leer, ohne Pfad, zu wenige Segmente

    @Test
    void ak49LaufwerkWurzelInAnfuehrungszeichenIstKeinFund() {
        assertKeinFund(bs("\"C:¦\""));
        assertKeinFund(bs("'C:¦'"));
        assertKeinFund(bs("`C:¦`"));
        assertKeinFund(bs("\"C:¦\" und \"D:/\""));
    }

    @Test
    void leeresZitatUndZitatOhnePfadSindKeinFund() {
        assertKeinFund("\"\"");
        assertKeinFund("''");
        assertKeinFund("\"\" ''  ``");
        assertKeinFund("Er sagte \"Hallo Welt\" und 'x y' und `z`.");
        assertKeinFund("\"C: Beschreibung\"");
        assertKeinFund("\"Uhrzeit 10:30\"");
    }

    @Test
    void leeresZitatVorEinemPfadStoertNicht() {
        assertEinFund(bs("\"\" C:¦a¦b"), bs("C:¦a¦b"));
    }

    // ------------------------------------------------------------------ mehrere Pfade

    @Test
    void einfacheZitateErgebenZweiFunde() {
        assertEquals(List.of(bs("C:¦a"), bs("C:¦b")), werte(bs("'C:¦a' und 'C:¦b'")));
    }

    @Test
    void zweiPfadeInGeradenZitatenMitLeerzeichen() {
        assertEquals(List.of(bs("C:¦Mein Ordner¦a.txt"), bs("D:¦Meine Daten¦b.txt")),
                werte(bs("Kopiere \"C:¦Mein Ordner¦a.txt\" nach \"D:¦Meine Daten¦b.txt\".")));
    }

    @Test
    void kommandozeileMitProgrammUndArgumentIstJeGanzerFund() {
        String text = bs("\"C:¦Program Files¦Mein Tool¦tool.exe\" /s \"D:¦Meine Daten¦x y.txt\"");
        assertEquals(List.of(bs("C:¦Program Files¦Mein Tool¦tool.exe"), bs("D:¦Meine Daten¦x y.txt")), werte(text));
        assertEquals("\"[[PFAD_1]]\" /s \"[[PFAD_2]]\"", pseudonymisiert(text));
    }

    @Test
    void mehrerePfadeInEinemZitatSindEinFundUeberDenGanzenInhalt() {
        // Eigene Entscheidung: Der ganze Inhalt ab dem ersten Kopf bis zum schließenden Zeichen ist ein Fund.
        assertEinFund(bs("\"C:¦a b C:¦c d\""), bs("C:¦a b C:¦c d"));
    }

    @Test
    void zitatMitZweiPfadenOhneLeerzeichenIstEinFund() {
        assertEinFund(bs("\"C:¦a C:¦b\""), bs("C:¦a C:¦b"));
    }

    // ------------------------------------------------------------------ Pfad beginnt nicht direkt nach dem Zeichen

    @Test
    void pfadBeginntNichtDirektNachDemOeffnendenZeichenReichtBisZumSchliessenden() {
        // Plan: Rückfall (nur C:\Mein). Eigene Entscheidung nach dem Grundsatz (zu groß besser als zu klein): Der
        // Pfad beginnt am Laufwerkskopf im Zitat und reicht bis zum schließenden Zeichen; alle Teile sind im Fund.
        assertEinFund(bs("\"Siehe C:¦Mein Ordner¦a.txt\""), bs("C:¦Mein Ordner¦a.txt"));
        assertEinFund(bs("'Siehe C:¦Mein Ordner¦a.txt'"), bs("C:¦Mein Ordner¦a.txt"));
        assertEinFund(bs("`Siehe C:¦Mein Ordner¦a.txt`"), bs("C:¦Mein Ordner¦a.txt"));
    }

    @Test
    void benutzernameUndAllePfadteileBisZumSchliessendenZeichenLiegenImFund() {
        String text = bs("Datei \"Pfad: C:¦Users¦erika.mustermann¦Meine Dokumente¦Akte 2025.pdf\" liegt dort.");
        List<Finding> funde = funde(text);
        assertEquals(1, funde.size());
        String wert = funde.get(0).originalValue();
        assertTrue(wert.contains("erika.mustermann"));
        assertTrue(wert.contains("Meine Dokumente"));
        assertTrue(wert.endsWith("Akte 2025.pdf"));
        String p = pseudonymisiert(text);
        assertEquals("Datei \"Pfad: [[PFAD_1]]\" liegt dort.", p);
        assertFalse(p.contains("erika.mustermann"));
        assertFalse(p.contains("Meine Dokumente"));
        assertFalse(p.contains("Akte 2025.pdf"));
    }

    @Test
    void pfadMitTextDavorImZitatOhneSchlussFaelltAufDieRegelOhneAnfuehrungszeichenZurueck() {
        assertEinFund(bs("\"Siehe C:¦Mein Ordner¦a.txt"), bs("C:¦Mein"));
    }

    // ------------------------------------------------------------------ Zitat in Zitat

    @Test
    void zitatInZitatEinfachAussenDoppeltInnen() {
        assertEinFund(bs("'\"C:¦a b¦c\"'"), bs("C:¦a b¦c"));
    }

    @Test
    void zitatInZitatDoppeltAussenEinfachInnenNimmtDenGanzenInhaltOhneKuerzung() {
        // Das äußere Paar gewinnt; der Inhalt wird nicht gekürzt, das innere ' bleibt daher im Fund (zu groß, kein Leck).
        assertEinFund(bs("\"'C:¦a b¦c'\""), bs("C:¦a b¦c'"));
    }

    @Test
    void zitatInZitatTypografischAussenDoppeltInnenEinfach() {
        assertEinFund(bs(DE_AUF + "Er sagte " + S_DE_AUF + "C:¦a b" + S_AUF + " ok" + DE_ZU), bs("C:¦a b" + S_AUF + " ok"));
    }

    // ------------------------------------------------------------------ Apostroph

    @Test
    void apostrophImPfadInDoppeltenAnfuehrungszeichen() {
        assertEinFund(bs("\"C:¦Users¦O'Mustermann¦Meine Akte.pdf\""), bs("C:¦Users¦O'Mustermann¦Meine Akte.pdf"));
    }

    @Test
    void apostrophImPfadInBackticks() {
        assertEinFund(bs("`C:¦Users¦O'Mustermann¦Meine Akte.pdf`"), bs("C:¦Users¦O'Mustermann¦Meine Akte.pdf"));
    }

    @Test
    void apostrophImPfadInEinfachenZeichenSchliesstFrueh() {
        // Bekannte Grenze (Ist-Zustand): Das ' im Namen schließt das Paar, der Inhalt C:\Users\O ist ein Fund; der
        // Durchlauf ohne Anführungszeichen reicht bis zum ersten Leerzeichen, die Vereinigung ergibt den größeren
        // Bereich. Der Benutzername liegt im Fund; der Rest hinter dem Leerzeichen (Akte.pdf) bleibt ungeschützt.
        assertEinFund(bs("'C:¦Users¦O'Mustermann¦Meine Akte.pdf'"), bs("C:¦Users¦O'Mustermann¦Meine"));
    }

    @Test
    void apostrophInNormalemTextMitPfadDazwischenMachtDenFundGroesserKeinLeck() {
        // Eigene Entscheidung: zwei Apostrophe auf einer Zeile bilden ein Paar; der Pfad darin ist ganz im Fund,
        // der Fund kann etwas mehr Text mitnehmen als nötig.
        List<Finding> funde = funde(bs("Peter's Datei C:¦a¦b.txt und Pauls' Haus"));
        assertEquals(1, funde.size());
        assertTrue(funde.get(0).originalValue().startsWith(bs("C:¦a¦b.txt")));
        assertEquals(bs("C:¦a¦b.txt und Pauls"), funde.get(0).originalValue());
    }

    // ------------------------------------------------------------------ fremdes Zeichen davor verschiebt die Paare nicht

    /**
     * Erwartet über den Erkenner genau einen Fund {@code fund} und über den Standard-Scanner ebenfalls genau diesen
     * einen Fund; im pseudonymisierten Text (Roundtrip gleich) darf keines der {@code teile} mehr stehen.
     */
    private static void assertGanzGeschuetzt(String text, String fund, String... teile) {
        assertEinFund(text, fund);
        List<Finding> scan = TextScanner.standard().scan(text);
        assertEquals(1, scan.size(), "Scanner: genau ein Fund, Text: " + text);
        assertEquals(fund, scan.get(0).originalValue());
        assertEquals(FindingType.PFAD, scan.get(0).type());
        String p = pseudonymisiert(text);
        for (String teil : teile) {
            assertFalse(p.contains(teil), "Bestandteil im Klartext: " + teil);
        }
    }

    private static final String[] TEILE_ORDNER = {"Mein", "Ordner", "a.txt"};

    @Test
    void einzelnesDoppeltesZeichenDavorVerschiebtDieNaechstePaarbildungNicht() {
        String pfad = bs("C:¦Mein Ordner¦a.txt");
        assertGanzGeschuetzt("Er sagte \" und dann \"" + pfad + "\"", pfad, TEILE_ORDNER);
    }

    @Test
    void zollZeichenDavorVerschiebtDiePaarbildungNicht() {
        String pfad = bs("C:¦Mein Ordner¦a.txt");
        assertGanzGeschuetzt("Monitor 24\" und Pfad \"" + pfad + "\"", pfad, TEILE_ORDNER);
    }

    @Test
    void mehrereUnpaarigeDoppelteZeichenDavorVerschiebenDiePaarbildungNicht() {
        String pfad = bs("C:¦Mein Ordner¦a.txt");
        assertGanzGeschuetzt("\"a\" \"b \"" + pfad + "\"", pfad, TEILE_ORDNER);
    }

    @Test
    void apostrophDavorVerschiebtDiePaarbildungEinfacherZeichenNicht() {
        String pfad = bs("C:¦Mein Ordner¦a.txt");
        assertGanzGeschuetzt("Peter's Datei '" + pfad + "'", pfad, TEILE_ORDNER);
        String pfad2 = bs("C:¦Program Files¦Mein Tool¦a.exe");
        assertGanzGeschuetzt("Windows can't find '" + pfad2 + "'.", pfad2, "Program", "Files", "Mein Tool", "a.exe");
    }

    @Test
    void typografischesZeichenDavorVerschiebtDiePaarbildungNicht() {
        String pfad = bs("C:¦Mein Ordner¦a.txt");
        assertGanzGeschuetzt("gibt" + S_ZU + "s " + S_DE_AUF + pfad + S_AUF, pfad, TEILE_ORDNER);
        assertGanzGeschuetzt(DE_AUF + "ja und " + DE_AUF + pfad + DE_ZU, pfad, TEILE_ORDNER);
    }

    @Test
    void jsonMitMaskiertemAnfuehrungszeichenSchuetztAllePfadteile() {
        // Im Text stehen echte Backslash-Anführungszeichen und doppelte Backslashes.
        String text = bs("{\"cmd\":\"¦\"C:¦¦Program Files¦¦Mein Tool¦¦a.exe¦\" -x\"}");
        assertTrue(text.contains(bs("¦\"C:¦¦Program")));
        List<Finding> funde = funde(text);
        assertEquals(1, funde.size());
        // das maskierende Backslash vor dem schließenden Zeichen ist Teil des Inhalts (zu groß, kein Leck)
        assertEquals(bs("C:¦¦Program Files¦¦Mein Tool¦¦a.exe¦"), funde.get(0).originalValue());
        String p = pseudonymisiert(text);
        for (String teil : new String[] {"Program", "Files", "Mein Tool", "a.exe"}) {
            assertFalse(p.contains(teil), "Bestandteil im Klartext: " + teil);
        }
    }

    @Test
    void zitatMitVorangehendemZitatOhnePfadUndPfadDahinterBleibtGetrennt() {
        // Kein Paar wird verbraucht; Paare ohne Pfadkopf (x, y, die Lücken) liefern nichts: zwei Pfade bleiben zwei Funde.
        String text = bs("\"x\" \"C:¦a b\" \"y\" \"D:¦c d\"");
        assertEquals(List.of(bs("C:¦a b"), bs("D:¦c d")), werte(text));
    }

    @Test
    void bekannteFolgeUnpaarigesZitatDavorKannDenFundVergroessernKeinLeck() {
        // Das erste Paar (Inhalt x) hat keinen Pfadkopf und liefert nichts; das zweite " öffnet ein Paar mit
        // dem dritten, der Fund ist der Inhalt C:\a b dazwischen. Zu groß, kein Leck.
        assertEinFund(bs("\"x\" C:¦a b \"y\""), bs("C:¦a b "));
    }

    // ------------------------------------------------------------------ Regression: unpaariges Zeichen vor dem Zitat

    private static boolean deckt(List<Finding> funde, int start, int ende) {
        for (Finding f : funde) {
            if (f.start() <= start && f.end() >= ende) {
                return true;
            }
        }
        return false;
    }

    /**
     * Das letzte Vorkommen von {@code pfad} im Text (der Pfad im Zitat) liegt über den Erkenner und über den
     * Standard-Scanner ganz in einem Fund; im pseudonymisierten Text (Roundtrip gleich) steht keines der {@code teile}.
     */
    private static void assertZitatPfadGedeckt(String text, String pfad, String... teile) {
        int start = text.lastIndexOf(pfad);
        assertTrue(start >= 0, "Pfad im Text: " + text);
        int ende = start + pfad.length();
        assertTrue(deckt(funde(text), start, ende), "Erkenner deckt den Pfad nicht, Text: " + text);
        assertTrue(deckt(TextScanner.standard().scan(text), start, ende), "Scanner deckt den Pfad nicht, Text: " + text);
        String p = pseudonymisiert(text);
        assertFalse(p.contains(pfad), "Pfad im Klartext, Text: " + text);
        for (String teil : teile) {
            assertFalse(p.contains(teil), "Bestandteil im Klartext: " + teil + ", Text: " + text);
        }
    }

    private static final String PFAD_MIT_LEERZEICHEN = bs("D:¦Mein Ordner¦a.txt");

    @Test
    void apostrophVorEinemPfadkopfNimmtDemSpaeterenEinfachenZitatNichtDasOeffnendeZeichen() {
        assertZitatPfadGedeckt(bs("Peter's C:¦Temp und '") + PFAD_MIT_LEERZEICHEN + "'", PFAD_MIT_LEERZEICHEN,
                TEILE_ORDNER);
    }

    @Test
    void zollZeichenVorEinemPfadkopfNimmtDemSpaeterenDoppeltenZitatNichtDasOeffnendeZeichen() {
        assertZitatPfadGedeckt(bs("Monitor 24\" an C:¦Temp, Datei \"") + PFAD_MIT_LEERZEICHEN + "\"",
                PFAD_MIT_LEERZEICHEN, TEILE_ORDNER);
    }

    @Test
    void oeffnendesTypografischesZeichenMitPfadkopfDahinterNimmtDemSpaeterenZitatNichts() {
        assertZitatPfadGedeckt(DE_AUF + bs("Bitte C:¦Temp pruefen und dann \"") + PFAD_MIT_LEERZEICHEN + "\" oeffnen",
                PFAD_MIT_LEERZEICHEN, TEILE_ORDNER);
    }

    @Test
    void unpaarigesDoppeltesZeichenMitPfadkopfDahinterNimmtDemSpaeterenZitatNichts() {
        assertZitatPfadGedeckt(bs("Er sagte \" C:¦Temp und dann \"") + PFAD_MIT_LEERZEICHEN + "\"",
                PFAD_MIT_LEERZEICHEN, TEILE_ORDNER);
    }

    @Test
    void unpaarigesZeichenNachEinemErstenPfadZitatNimmtDemZweitenZitatNichts() {
        assertZitatPfadGedeckt(DE_AUF + bs(" x 'C:¦a b¦x' y \"") + PFAD_MIT_LEERZEICHEN + "\"", PFAD_MIT_LEERZEICHEN,
                TEILE_ORDNER);
    }

    @Test
    void zitatApostrophUndPfadOhneZitatVorDemLetztenZitatSchuetztDenLetztenPfad() {
        assertZitatPfadGedeckt(bs("'C:¦a b¦x' Peter's C:¦Temp '") + PFAD_MIT_LEERZEICHEN + "'", PFAD_MIT_LEERZEICHEN,
                TEILE_ORDNER);
    }

    @Test
    void minimalbeispielBacktickGefolgtVonEinfachemZitat() {
        assertZitatPfadGedeckt("'`E:/1`'D:/ z'", "D:/ z", "D:/");
    }

    @Test
    void minimalbeispielDeutschesZeichenGefolgtVonGeradenZitat() {
        assertZitatPfadGedeckt("\"" + DE_AUF + "C:/z" + DE_AUF + "\"C:/ z\"", "C:/ z", "C:/");
    }

    @Test
    void minimalbeispielGeradesUndEinfachesZeichenMitTypografischemApostroph() {
        assertZitatPfadGedeckt(bs("\"'D:¦d be") + S_ZU + "\"D:/ z\"", "D:/ z", "D:/");
    }

    @Test
    void minimalbeispielTypografischeZeichenGefolgtVonGeradenZitat() {
        assertZitatPfadGedeckt(DE_AUF + S_DE_AUF + "C:/1'\"D:/ z\"", "D:/ z", "D:/");
    }

    @Test
    void typografischerApostrophAlsUnpaarigesZeichenNimmtDemEinfachenZitatNichts() {
        assertZitatPfadGedeckt(bs("Peter") + S_ZU + bs("s C:¦Temp und '") + PFAD_MIT_LEERZEICHEN + "'",
                PFAD_MIT_LEERZEICHEN, TEILE_ORDNER);
        assertZitatPfadGedeckt(bs("Peter") + S_ZU + bs("s C:¦Temp und ") + S_AUF + PFAD_MIT_LEERZEICHEN + S_ZU,
                PFAD_MIT_LEERZEICHEN, TEILE_ORDNER);
    }

    @Test
    void franzoesischesZeichenAlsUnpaarigesZeichenNimmtDemZitatNichts() {
        assertZitatPfadGedeckt(bs("Siehe ") + FR_ZU + bs(" C:¦Temp und ") + FR_AUF + PFAD_MIT_LEERZEICHEN + FR_ZU,
                PFAD_MIT_LEERZEICHEN, TEILE_ORDNER);
        assertZitatPfadGedeckt(bs("Siehe ") + FR_AUF + bs(" C:¦Temp und \"") + PFAD_MIT_LEERZEICHEN + "\"",
                PFAD_MIT_LEERZEICHEN, TEILE_ORDNER);
        assertZitatPfadGedeckt(bs("Siehe ") + FR_ZU + bs(" C:¦Temp und \"") + PFAD_MIT_LEERZEICHEN + "\"",
                PFAD_MIT_LEERZEICHEN, TEILE_ORDNER);
    }

    // ------------------------------------------------------------------ Zitate ohne Verbrauch: Ist-Zustand der Funde

    @Test
    void zweiZitateMitPfadDazwischenOhneZitatSindDreiFundeDieGrenzen() {
        // Das mittlere Paar (schließendes Zeichen des ersten, öffnendes des zweiten) hat den Kopf C:\y und liefert
        // den Fund " x C:\y " ohne die Zeichen; er grenzt an beide Nachbarn, überlappt sie aber nicht.
        String text = bs("\"C:¦a b\" x C:¦y \"D:¦c d\"");
        assertEquals(List.of(bs("C:¦a b"), bs("C:¦y "), bs("D:¦c d")), werte(text));
    }

    @Test
    void zweiZitateMitPfadlosemMittelstueckBleibenZweiFunde() {
        assertEquals(List.of(bs("C:¦a b"), bs("D:¦c d")), werte(bs("\"C:¦a b\" und \"D:¦c d\"")));
        assertEquals(List.of(bs("C:¦a"), bs("C:¦b")), werte(bs("'C:¦a' und 'C:¦b'")));
    }

    // ------------------------------------------------------------------ bekannte Grenzen (Ist-Zustand, kein Schutz)

    @Test
    void bekannteGrenzeZeilenumbruchImZitatFaelltAufDieRegelOhneAnfuehrungszeichenZurueck() {
        assertEinFund(bs("\"C:¦Program Files¦Mein\nTool¦a.exe\""), bs("C:¦Program"));
    }

    @Test
    void bekannteGrenzeGemischteAsciiPaareBildenKeinPaar() {
        assertEinFund(bs("\"C:¦Mein Ordner¦a.txt'"), bs("C:¦Mein"));
        assertEinFund(bs("'C:¦Mein Ordner¦a.txt\""), bs("C:¦Mein"));
        assertEinFund(bs("`C:¦Mein Ordner¦a.txt'"), bs("C:¦Mein"));
    }

    @Test
    void bekannteGrenzeTabImZitatFaelltAufDieRegelOhneAnfuehrungszeichenZurueck() {
        assertEinFund(bs("\"C:¦Mein\tOrdner¦a.txt\""), bs("C:¦Mein"));
    }

    @Test
    void einzelnerApostrophVorDemPfadOhneSchlussBleibtBeiDerRegelOhneAnfuehrungszeichen() {
        assertEinFund(bs("Peter's Datei C:¦a b"), bs("C:¦a"));
    }

    // ------------------------------------------------------------------ typografische Anführungszeichen (Escapes)

    @Test
    void typografischDeutscheDoppelteZeichen() {
        String pfad = bs("C:¦Mein Ordner¦a.txt");
        assertEinFund("Siehe " + DE_AUF + pfad + DE_ZU + " dort", pfad);
    }

    @Test
    void typografischEnglischeDoppelteZeichen() {
        String pfad = bs("C:¦Mein Ordner¦a.txt");
        assertEinFund("Siehe " + EN_AUF + pfad + EN_ZU + " dort", pfad);
    }

    @Test
    void typografischFranzoesischeDoppelteZeichen() {
        String pfad = bs("C:¦Mein Ordner¦a.txt");
        assertEinFund("Siehe " + FR_AUF + pfad + FR_ZU + " dort", pfad);
        assertEinFund("Siehe " + FR_ZU + pfad + FR_AUF + " dort", pfad);
    }

    @Test
    void typografischEinfacheZeichen() {
        String pfad = bs("C:¦Mein Ordner¦a.txt");
        assertEinFund("Siehe " + S_AUF + pfad + S_ZU + " dort", pfad);
        assertEinFund("Siehe " + S_DE_AUF + pfad + S_AUF + " dort", pfad);
    }

    @Test
    void gemischtePaareGeradUndTypografisch() {
        String pfad = bs("C:¦Mein Ordner¦a.txt");
        assertEinFund("Siehe " + DE_AUF + pfad + "\" dort", pfad);
        assertEinFund("Siehe \"" + pfad + EN_ZU + " dort", pfad);
        assertEinFund("Siehe '" + pfad + S_ZU + " dort", pfad);
        assertEinFund("Siehe " + S_AUF + pfad + "' dort", pfad);
        assertEinFund("Siehe " + FR_AUF + pfad + "\" dort", pfad);
    }

    @Test
    void typografischesPaarOhneLeerzeichenImPfadSchliesstDasSchlusszeichenEinWieInT10() {
        // Obermengen-Garantie gegenüber T-10: Dort endet der Lauf erst am Leerzeichen und schließt ein typografisches
        // Schlusszeichen ein (es ist kein Endzeichen und keine Kürzungszeichen). Der Fund darf nicht kleiner werden.
        assertEinFund("Siehe " + DE_AUF + bs("C:¦a¦b.txt") + DE_ZU + " dort", bs("C:¦a¦b.txt") + DE_ZU);
    }

    @Test
    void typografischesZeichenOhneSchlusszeichenFaelltZurueck() {
        assertEinFund("Siehe " + DE_AUF + bs("C:¦Mein Ordner¦a.txt dort"), bs("C:¦Mein"));
    }

    @Test
    void typografischePaareMitPseudonymisierung() {
        String text = "Siehe " + DE_AUF + bs("C:¦Mein Ordner¦a.txt") + DE_ZU + " dort";
        assertEquals("Siehe " + DE_AUF + "[[PFAD_1]]" + DE_ZU + " dort", pseudonymisiert(text));
    }

    // ------------------------------------------------------------------ Anführungszeichen nach = / Kommandozeilen / CSV / JSON

    @Test
    void anfuehrungszeichenDirektNachGleichheitszeichen() {
        assertEinFund(bs("tool.exe --log=\"C:¦Mein Ordner¦a.log\""), bs("C:¦Mein Ordner¦a.log"));
        assertEinFund(bs("--log='C:¦Mein Ordner¦a.log'"), bs("C:¦Mein Ordner¦a.log"));
    }

    @Test
    void jsonSchluesselWertMitDoppeltenBackslashes() {
        String text = bs("{\"pfad\": \"C:¦¦Mein Ordner¦¦a.txt\", \"x\": \"y\"}");
        List<Finding> funde = funde(text);
        assertEquals(1, funde.size());
        assertEquals(bs("C:¦¦Mein Ordner¦¦a.txt"), funde.get(0).originalValue());
        assertEquals(4, funde.get(0).originalValue().chars().filter(c -> c == '\\').count());
        assertEquals("{\"pfad\": \"[[PFAD_1]]\", \"x\": \"y\"}", pseudonymisiert(text));
    }

    @Test
    void jsonOhneLeerzeichenNachDemDoppelpunkt() {
        assertEinFund(bs("{\"pfad\":\"C:/Mein Ordner/a.txt\"}"), "C:/Mein Ordner/a.txt");
    }

    @Test
    void csvMitPfadInAnfuehrungszeichen() {
        String text = bs("id;pfad;bemerkung\n1;\"C:¦Mein Ordner¦a.txt\";ok\n2;\"D:¦Meine Daten¦b.txt\";ok\n");
        assertEquals(List.of(bs("C:¦Mein Ordner¦a.txt"), bs("D:¦Meine Daten¦b.txt")), werte(text));
    }

    @Test
    void csvMitDoppeltenAnfuehrungszeichenAlsEscape() {
        // RFC-4180-Form: "" im Feld; die Pfade bleiben ganz im Fund (Obermenge), Namen sind geschützt.
        String text = bs("1,\"C:¦Mein Ordner¦a\"\"b.txt\",ok");
        List<Finding> funde = funde(text);
        assertFalse(funde.isEmpty());
        String p = pseudonymisiert(text);
        assertFalse(p.contains("Mein"));
    }

    // ------------------------------------------------------------------ Kürzung entfällt im Zitat

    @Test
    void imZitatGibtEsKeineKuerzungDerGanzeInhaltIstDerFund() {
        assertEinFund(bs("\"C:¦a b.\""), bs("C:¦a b."));
        assertEinFund(bs("\"C:¦a b \""), bs("C:¦a b "));
        assertEinFund(bs("\"C:¦a b)\""), bs("C:¦a b)"));
    }

    @Test
    void ohneAnfuehrungszeichenBleibtDieKuerzungWieInT10() {
        assertEinFund(bs("Siehe C:¦a¦b."), bs("C:¦a¦b"));
    }

    @Test
    void einfachesSchlusszeichenDirektAmPfadendeGehoertNichtZumFund() {
        assertEinFund(bs("'C:¦a¦b'"), bs("C:¦a¦b"));
        assertEinFund(bs("(siehe 'C:¦a¦b')"), bs("C:¦a¦b"));
    }

    @Test
    void obermengeGegenT10WennTextDirektHinterDemSchlusszeichenFolgt() {
        // T-10 liefert C:\a'xyz (der Lauf reicht über das '); T-11 darf nicht kleiner werden.
        assertEinFund(bs("'C:¦a'xyz"), bs("C:¦a'xyz"));
    }

    // ------------------------------------------------------------------ alle drei bekannten Familien Unicode: Position

    @Test
    void fundGrenzenLiegenNichtImSurrogatpaarWennDasZitatEmojiUmschliesst() {
        String text = "\uD83D\uDE00\"" + bs("C:¦Mein Ordner¦a") + "\"\uD83D\uDE00";
        assertEinFund(text, bs("C:¦Mein Ordner¦a"));
    }

    // ------------------------------------------------------------------ mehrzeilig

    @Test
    void zitateAufVerschiedenenZeilenWerdenJeZeileGepaart() {
        String text = bs("\"C:¦Mein Ordner¦a.txt\"\n\"D:¦Meine Daten¦b.txt\"\r\n'E:¦x y¦c'");
        assertEquals(List.of(bs("C:¦Mein Ordner¦a.txt"), bs("D:¦Meine Daten¦b.txt"), bs("E:¦x y¦c")), werte(text));
    }

    @Test
    void unbalanciertesZitatAufEinerZeileVerschiebtDieNaechsteNichtUeberDenZeilenumbruch() {
        String text = bs("x \"C:¦a b\nund \"D:¦c d\" y");
        assertEquals(List.of(bs("C:¦a"), bs("D:¦c d")), werte(text));
    }

    // ------------------------------------------------------------------ Grenzlast (AK-49), je < 2 s

    private static void assertLinear(String text) {
        assertTimeoutPreemptively(LIMIT, () -> {
            R.detect(text);
        });
        assertTimeoutPreemptively(LIMIT, () -> {
            TextScanner.standard().scan(text);
        });
    }

    @Test
    void ak49GeradeAnfuehrungszeichenVielfach() {
        String text = "\"".repeat(1_000_000);
        assertLinear(text);
        assertEquals(0, zaehle(text));
    }

    @Test
    void ak49NieGeschlossenesZitatMitVielenLeerzeichenWoertern() {
        String text = bs("\"C:¦ ") + "a b c ".repeat(170_000);
        assertTrue(text.length() >= 1_000_000);
        assertLinear(text);
    }

    @Test
    void ak49PfadanfangImZitatMitVielenWoerternNieGeschlossen() {
        String text = bs("\"C:¦a ") + "w ".repeat(500_000);
        assertLinear(text);
        assertEquals(1, zaehle(text));
    }

    @Test
    void ak49VieleOeffnendeZitateOhneSchluss() {
        // Jedes Zitat wird vom nächsten geschlossen; es gibt keine quadratische Suche.
        String text = bs("\"C:¦a b").repeat(150_000);
        assertTrue(text.length() >= 1_000_000);
        assertLinear(text);
    }

    @Test
    void ak49GeschlosseneZitateVielfach() {
        String text = bs("\"C:¦a b\" ").repeat(125_000);
        assertTrue(text.length() >= 1_000_000);
        assertEquals(125_000, assertTimeoutPreemptively(LIMIT, () -> zaehle(text)));
    }

    @Test
    void ak49EinfacheAnfuehrungszeichenVielfach() {
        String text = "'".repeat(1_000_000);
        assertLinear(text);
        assertEquals(0, zaehle(text));
    }

    @Test
    void ak49EinfachePfadanfaengeMitLeerzeichenVielfach() {
        String text = bs("'C:¦a ").repeat(166_667);
        assertLinear(text);
    }

    @Test
    void ak49PipeVorDemSchlussVielfach() {
        String text = bs("\"C:¦a|").repeat(170_000);
        assertLinear(text);
    }

    @Test
    void ak49AbwechselndOeffnendUndSchliessend() {
        assertLinear("\"'`\u201E\u201C\u00BB\u00AB\u2018\u2019".repeat(110_000));
        assertLinear("\"'".repeat(500_000));
        assertLinear("'\"".repeat(500_000));
        assertLinear(bs("\"C:¦a '").repeat(150_000));
    }

    @Test
    void ak49VerschachtelteAnfuehrungszeichen() {
        String text = "\u201E".repeat(100_000) + bs("C:¦a b") + "\u201C".repeat(100_000);
        assertLinear(text);
        assertEquals(1, zaehle(text));
        String gemischt = "\"'`".repeat(100_000) + bs("C:¦a b") + "`'\"".repeat(100_000);
        assertLinear(gemischt);
    }

    @Test
    void ak49AlleDreizehnArtenOeffnendOhneSchlussInEinerZeile() {
        // Jede Art sucht höchstens bis zum nächsten Zeichen derselben Art: 13 Arten, je einmal weit offen.
        String text = "\"\u201C\u201D\u201E\u00AB\u00BB'\u2018\u2019\u201A\u2039\u203A`" + "x ".repeat(450_000);
        assertLinear(text);
    }

    private static void assertLinearMitFundzahl(String text, int erwartet) {
        assertTrue(text.length() >= 1_000_000, "Länge " + text.length());
        assertEquals(erwartet, (int) assertTimeoutPreemptively(LIMIT, () -> zaehle(text)), "Fundzahl erkenner");
        assertEquals(erwartet, (int) assertTimeoutPreemptively(LIMIT, () -> TextScanner.standard().scan(text).size()),
                "Fundzahl scanner");
    }

    private static String wiederhole(String baustein) {
        return baustein.repeat(1_000_000 / baustein.length() + 1);
    }

    private static final String ALLE_ARTEN = "\"\u201C\u201D\u201E\u00AB\u00BB'\u2018\u2019\u201A\u2039\u203A`";

    @Test
    void ak49ZitateOhnePfadkopfWerdenNichtVerbrauchtUndBleibenLinear() {
        // Ohne Verbrauch probiert jedes Zeichen ein Paar; die Suchbereiche einer Art überlappen nicht.
        assertLinearMitFundzahl(wiederhole("\"a"), 0);
        assertLinearMitFundzahl(wiederhole("\"a\" "), 0);
        assertLinearMitFundzahl(wiederhole("\"x 'y "), 0);
        assertLinearMitFundzahl(wiederhole("'a"), 0);
        assertLinearMitFundzahl(wiederhole("`a "), 0);
    }

    @Test
    void ak49PfadkopfNurAusserhalbDesPaarsJeZitatNurEinmalGesucht() {
        // " C:\a b " x n: Jedes " paart mit dem nächsten (kein Verbrauch): n - 1 Paare mit je dem Fund C:\a b (Inhalt
        // ab dem Kopf bis vor das nächste "). Die Funde sind durch das " getrennt, grenzen also nicht aneinander. Das
        // letzte " hat kein Gegenstück, dort liefert nur der Durchlauf ohne Anführungszeichen den Fund C:\a.
        // Zusammen n Funde.
        int n = 112_000;
        String text = bs("\" C:¦a b ").repeat(n);
        assertLinearMitFundzahl(text, n);
    }

    @Test
    void ak49VieleZitatFundeOhneLeerzeichenVorDemKopf() {
        // "C:\a b  x n: dieselbe Paarbildung, 7 Zeichen je Baustein; n - 1 Paar-Funde (Inhalt C:\a b ) plus der
        // Durchlauf-Fund C:\a des letzten Bausteins, der kein schließendes " hat.
        int n = 143_000;
        String text = bs("\"C:¦a b ").repeat(n);
        assertLinearMitFundzahl(text, n);
    }

    @Test
    void ak49SichUeberlappendeZitatFundeVerschiedenerArtenWerdenLinearVereinigt() {
        // Je Baustein "»C:\a b : Das " paart mit dem " des nächsten Bausteins (Fund ab C:\ bis davor), das » mit dem
        // » des nächsten (Fund ab C:\ bis davor). Beide Funde eines Bausteins überlappen sich und werden zu einem
        // Fund [C:\ des Bausteins, » des nächsten); der nächste Fund beginnt erst hinter diesem » und ist durch das
        // Zeichen getrennt. Je Baustein außer dem letzten ein Fund, im letzten nur C:\a vom Durchlauf: n Funde.
        int n = 125_000;
        String text = bs("\"\u00BBC:¦a b ").repeat(n);
        assertLinearMitFundzahl(text, n);
    }

    @Test
    void ak49AlleDreizehnArtenZyklischOhneUndMitKopf() {
        assertLinearMitFundzahl(wiederhole(ALLE_ARTEN + "a "), 0);
        assertLinearMitFundzahl(wiederhole(ALLE_ARTEN), 0);
        // Je Baustein genau ein Fund. Die zwölf Zeichen vor dem Backtick schließen ihre Paare unmittelbar am
        // Nachbarzeichen (leerer Inhalt, kein Fund); nur U+00BB paart erst mit dem " des nächsten Bausteins (Fund ab
        // C:\ bis dahin), U+203A wird vom geraden " abgebrochen. Der Backtick paart mit dem des nächsten Bausteins
        // (Fund ab C:\ bis dort), enthält den Fund von U+00BB und ist vom Fund des nächsten Bausteins nur durch das
        // Backtick-Zeichen getrennt. Das ergibt n - 1 Funde; im letzten Baustein liefert nur der Durchlauf C:\a.
        String mitKopf = ALLE_ARTEN + bs("C:¦a b ");
        assertLinearMitFundzahl(mitKopf.repeat(50_000), 50_000);
    }

    @Test
    void ak49LangerInhaltOhneKopfZwischenVielenAnfuehrungszeichen() {
        // Das gerade " sucht über viele U+00BB hinweg (es wird nur von ", U+201C, U+201D geschlossen), jedes U+00BB sucht bis
        // zum nächsten Zeichen der doppelten Familie: Jeder Inhalt wird je Art einmal durchsucht.
        String block = "\"" + ("\u00BB" + "x ".repeat(5)).repeat(500);
        assertLinearMitFundzahl(wiederhole(block), 0);
        assertLinearMitFundzahl(wiederhole("\"" + "word ".repeat(200)), 0);
        assertLinearMitFundzahl(wiederhole("'" + "word ".repeat(200)), 0);
    }

    @Test
    void ak49UeberlappungsketteInDerVereinigungBleibtLinear() {
        // Reviewer-Kette: 'C:\a bC:\x' x n. Das Paar aus schließendem und öffnendem ' hat leeren Inhalt und keinen Fund;
        // jedes Zitat liefert C:\a bC:\x, der Durchlauf ohne Anführungszeichen C:\x'C:\a über die Bausteingrenze. Beide
        // Listen überlappen kettenartig zu einem einzigen Fund; die Vereinigung führt nur Zahlen mit und baut den Fund
        // einmal (vorher quadratisch: ca. 7 s bei n = 83 000).
        String text = bs("'C:¦a bC:¦x'").repeat(83_000);
        assertTrue(text.length() >= 990_000);
        assertEquals(1, (int) assertTimeoutPreemptively(LIMIT, () -> zaehle(text)), "Fundzahl erkenner");
        assertEquals(1, (int) assertTimeoutPreemptively(LIMIT, () -> TextScanner.standard().scan(text).size()),
                "Fundzahl scanner");
    }

    @Test
    void ak49EineMillionZeichenGemischterTextMitZitaten() {
        String baustein = bs("Siehe \"C:¦Mein Ordner¦a.txt\" und 'D:¦x y¦b', danach E:¦Ablage¦c.txt. ");
        String text = baustein.repeat(1_000_000 / baustein.length() + 1);
        // Je Baustein: Fund C:\...a.txt (Zitat), Fund D:\... (Zitat) und E:\... (Durchlauf). Das Paar aus dem
        // schließenden " eines Bausteins und dem öffnenden " des nächsten hat den Inhalt " und 'D:\x y\b', danach
        // E:\Ablage\c.txt. Siehe " mit Kopf; sein Fund verschmilzt D und E zu einem. Ein Baustein liefert daher zwei
        // Funde (C, D+E), der letzte ohne folgendes " drei (C, D, E): 2 * Bausteine + 1.
        int erwartet = 2 * (1_000_000 / baustein.length() + 1) + 1;
        assertTrue(text.length() >= 1_000_000);
        assertEquals(erwartet, (int) assertTimeoutPreemptively(LIMIT, () -> zaehle(text)));
    }
}
