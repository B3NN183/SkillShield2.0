package de.skillshield.core.reidentify;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PlaceholderMapping;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.reidentify.UnresolvedPlaceholder.Reason;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReidentifierTest {

    private static final String MARKER = "GEHEIM-mustermann-4711";
    private static final Duration GRENZE = Duration.ofSeconds(2);

    private final Reidentifier r = new Reidentifier();

    private static PlaceholderMapping mapping(String... paare) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < paare.length; i += 2) {
            m.put(paare[i], paare[i + 1]);
        }
        return PlaceholderMapping.of(m);
    }

    private static PlaceholderMapping ein(String ph) {
        return mapping(ph, MARKER);
    }

    private static void erwarte(ReidentificationResult res, String text, UnresolvedPlaceholder... gemeldet) {
        assertEquals(text, res.text());
        assertEquals(List.of(gemeldet), res.unresolved());
    }

    // ----- AK-40: Fremdbearbeitung -----

    @Test
    void platzhalterUmgestelltAk40() {
        PlaceholderMapping m = mapping("[[PFAD_1]]", "A1", "[[IP_2]]", "B2");
        erwarte(r.restore("zuerst [[IP_2]] dann [[PFAD_1]]", m), "zuerst B2 dann A1");
    }

    @Test
    void platzhalterVerdoppeltAk40() {
        PlaceholderMapping m = mapping("[[PFAD_1]]", "A1");
        erwarte(r.restore("[[PFAD_1]] und nochmal [[PFAD_1]] und [[PFAD_1]]", m), "A1 und nochmal A1 und A1");
    }

    @Test
    void platzhalterEingebettetAk40() {
        PlaceholderMapping m = mapping("[[PFAD_1]]", "A1");
        erwarte(r.restore("(\"[[PFAD_1]]\"), *[[PFAD_1]]*, x[[PFAD_1]]y, [[PFAD_1]]!", m),
                "(\"A1\"), *A1*, xA1y, A1!");
    }

    @Test
    void platzhalterWeggelassenIstKeinFehlerAk40() {
        PlaceholderMapping m = mapping("[[PFAD_1]]", "A1", "[[IP_2]]", "B2", "[[URL_3]]", "C3");
        erwarte(r.restore("nur [[IP_2]] ist uebrig", m), "nur B2 ist uebrig");
    }

    @Test
    void zwoelfPlatzhalterEinsUndElfZweiUndZwoelfAk40() {
        Map<String, String> werte = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder();
        StringBuilder erwartet = new StringBuilder();
        for (int n = 1; n <= 12; n++) {
            werte.put("[[PFAD_" + n + "]]", "wert-" + n + "-ende");
            text.append("[[PFAD_").append(n).append("]];");
            erwartet.append("wert-").append(n).append("-ende;");
        }
        erwarte(r.restore(text.toString(), PlaceholderMapping.of(werte)), erwartet.toString());
        erwarte(r.restore("[[PFAD_1]] [[PFAD_11]] [[PFAD_2]] [[PFAD_12]]", PlaceholderMapping.of(werte)),
                "wert-1-ende wert-11-ende wert-2-ende wert-12-ende");
    }

    @Test
    void nurNummerEinsOhneNummerElfWirdNichtVerwechseltAk40() {
        PlaceholderMapping m = mapping("[[PFAD_1]]", "A1");
        ReidentificationResult res = r.restore("[[PFAD_11]]", m);
        erwarte(res, "[[PFAD_11]]", new UnresolvedPlaceholder("[[PFAD_11]]", 0, Reason.UNBEKANNT));
    }

    // ----- AK-41: unbekannt -----

    @Test
    void unbekannterPlatzhalterBleibtUndWirdGemeldetAk41() {
        PlaceholderMapping m = ein("[[PFAD_1]]");
        erwarte(r.restore("a [[PFAD_99]] b", m), "a [[PFAD_99]] b",
                new UnresolvedPlaceholder("[[PFAD_99]]", 2, Reason.UNBEKANNT));
    }

    @Test
    void unbekannterTypBleibtUndWirdGemeldetAk41() {
        erwarte(r.restore("[[FOO_1]]", ein("[[PFAD_1]]")), "[[FOO_1]]",
                new UnresolvedPlaceholder("[[FOO_1]]", 0, Reason.UNBEKANNT));
    }

    @Test
    void unbekannterPlatzhalterBeiLeererZuordnungAk41() {
        erwarte(r.restore("[[PFAD_1]]", PlaceholderMapping.empty()), "[[PFAD_1]]",
                new UnresolvedPlaceholder("[[PFAD_1]]", 0, Reason.UNBEKANNT));
    }

    @Test
    void meldungEnthaeltKeinenOriginalwertAk41() {
        ReidentificationResult res = r.restore("[[PFAD_1]] [[PFAD_9]]", ein("[[PFAD_1]]"));
        for (UnresolvedPlaceholder u : res.unresolved()) {
            assertFalse(u.toString().contains(MARKER));
            assertFalse(u.token().contains(MARKER));
        }
        assertEquals(1, res.unresolved().size());
    }

    @Test
    void mehrereMeldungenInTextreihenfolgeMitPosition() {
        ReidentificationResult res = r.restore("[[IP_7]] x [[PFAD_1]] [[PFAD_8]", ein("[[PFAD_1]]"));
        assertEquals("[[IP_7]] x " + MARKER + " [[PFAD_8]", res.text());
        assertEquals(List.of(new UnresolvedPlaceholder("[[IP_7]]", 0, Reason.UNBEKANNT),
                new UnresolvedPlaceholder("[[PFAD_8]", 22, Reason.BESCHAEDIGT)), res.unresolved());
        assertEquals("[[IP_7]] x [[PFAD_1]] ".length(), res.unresolved().get(1).position());
    }

    // ----- AK-42: beschaedigt -----

    @Test
    void beschaedigtEineSchliessendeKlammerAk42() {
        erwarte(r.restore("[[PFAD_1]", ein("[[PFAD_1]]")), "[[PFAD_1]",
                new UnresolvedPlaceholder("[[PFAD_1]", 0, Reason.BESCHAEDIGT));
    }

    @Test
    void beschaedigtEinfacheKlammernAk42() {
        erwarte(r.restore("[PFAD_1]", ein("[[PFAD_1]]")), "[PFAD_1]",
                new UnresolvedPlaceholder("[PFAD_1]", 0, Reason.BESCHAEDIGT));
    }

    @Test
    void beschaedigtOhneNummerAk42() {
        erwarte(r.restore("[[PFAD_]]", ein("[[PFAD_1]]")), "[[PFAD_]]",
                new UnresolvedPlaceholder("[[PFAD_]]", 0, Reason.BESCHAEDIGT));
    }

    @Test
    void codeMitArrayIndexOhneMeldungAk42() {
        erwarte(r.restore("arr[idx_0] = x[i_1];", ein("[[PFAD_1]]")), "arr[idx_0] = x[i_1];");
    }

    @Test
    void beschaedigterPlatzhalterWirdNichtGeratenAk42() {
        String res = r.restore("[[PFAD_1] und [PFAD_1] und [[PFAD_]]", ein("[[PFAD_1]]")).text();
        assertFalse(res.contains(MARKER));
    }

    // ----- AK-43: tolerierte Varianten, je ein Test -----

    @Test
    void kleinschreibungAk43() {
        erwarte(r.restore("[[pfad_1]]", ein("[[PFAD_1]]")), MARKER);
    }

    @Test
    void leerzeichenVornAk43() {
        erwarte(r.restore("[[ PFAD_1]]", ein("[[PFAD_1]]")), MARKER);
    }

    @Test
    void leerzeichenHintenAk43() {
        erwarte(r.restore("[[PFAD_1 ]]", ein("[[PFAD_1]]")), MARKER);
    }

    @Test
    void leerzeichenBeidseitigAk43() {
        erwarte(r.restore("[[ PFAD_1 ]]", ein("[[PFAD_1]]")), MARKER);
    }

    @Test
    void gemischteGrossKleinschreibungUndLeerzeichenAk43() {
        erwarte(r.restore("[[  Pfad_1   ]]", ein("[[PFAD_1]]")), MARKER);
    }

    // ----- AK-38/AK-44: einpassig, keine Umdeutung -----

    @Test
    void sonderzeichenInOriginalwertenZeichengleichAk38() {
        String[] werte = {"$", "$1", bs("¦1"), "${HOME}", "%APPDATA%", bs("¦¦"), bs("C:¦¦Tools¦¦x"), "a/b\\c",
            "$$", "&", bs("¦$1¦")};
        for (String wert : werte) {
            PlaceholderMapping m = mapping("[[PFAD_1]]", wert);
            erwarte(r.restore("a [[PFAD_1]] b", m), "a " + wert + " b");
        }
    }

    @Test
    void sonderzeichenWerteGemeinsamAk38() {
        PlaceholderMapping m = mapping("[[PFAD_1]]", "$1", "[[PFAD_2]]", bs("¦1"), "[[PFAD_3]]", "${HOME}",
                "[[PFAD_4]]", "%APPDATA%", "[[PFAD_5]]", bs("¦¦"));
        String text = "[[PFAD_1]]|[[PFAD_2]]|[[PFAD_3]]|[[PFAD_4]]|[[PFAD_5]]";
        String erwartet = "$1|" + bs("¦1") + "|${HOME}|%APPDATA%|" + bs("¦¦");
        ReidentificationResult res = r.restore(text, m);
        erwarte(res, erwartet);
        assertEquals(3, res.text().chars().filter(c -> c == '\\').count(), "Backslashes: 1 + 2");
    }

    @Test
    void manuellerWertWieEinPlatzhalterWirdNichtErneutAufgeloestAk44() {
        PlaceholderMapping m = mapping("[[IP_1]]", "[[IP_2]]", "[[IP_2]]", MARKER);
        ReidentificationResult res = r.restore("a [[IP_1]] b", m);
        erwarte(res, "a [[IP_2]] b");
        assertFalse(res.text().contains(MARKER));
    }

    @Test
    void manuellerWertMitSelbemPlatzhalterAk44() {
        PlaceholderMapping m = mapping("[[IP_1]]", "[[IP_1]]");
        erwarte(r.restore("[[IP_1]] [[IP_1]]", m), "[[IP_1]] [[IP_1]]");
    }

    @Test
    void wertKetteWirdNurEinmalAufgeloestAk44() {
        PlaceholderMapping m = mapping("[[IP_1]]", "[[IP_2]]", "[[IP_2]]", "[[IP_3]]", "[[IP_3]]", MARKER);
        erwarte(r.restore("[[IP_1]]", m), "[[IP_2]]");
    }

    @Test
    void originalwerteMitDollarUndBackslashUndPlatzhalterAk44() {
        PlaceholderMapping m = mapping("[[IP_1]]", "$1", "[[IP_2]]", bs("¦1"), "[[IP_3]]", "[[IP_2]]");
        erwarte(r.restore("[[IP_1]][[IP_2]][[IP_3]]", m), "$1" + bs("¦1") + "[[IP_2]]");
    }

    @Test
    void wertMitBeschaedigtemPlatzhalterWirdNichtGemeldetAk44() {
        PlaceholderMapping m = mapping("[[IP_1]]", "[[IP_");
        erwarte(r.restore("[[IP_1]]", m), "[[IP_");
    }

    // ----- AK-46 / AK-47 -----

    @Test
    void leererTextAk46() {
        erwarte(r.restore("", PlaceholderMapping.empty()), "");
        erwarte(r.restore("", ein("[[PFAD_1]]")), "");
    }

    @Test
    void nurLeerraumAk46() {
        erwarte(r.restore(" \t\r\n ", ein("[[PFAD_1]]")), " \t\r\n ");
    }

    @Test
    void textOhnePlatzhalterMitLeererZuordnungAk46() {
        String text = "Ganz normaler Text, [x] und a[1] und äöü.";
        ReidentificationResult res = r.restore(text, PlaceholderMapping.empty());
        erwarte(res, text);
        assertSame(text, res.text());
    }

    @Test
    void textOhnePlatzhalterMitZuordnungAk46() {
        erwarte(r.restore("nichts zu tun", ein("[[PFAD_1]]")), "nichts zu tun");
    }

    @Test
    void nullTextAk47() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> r.restore(null, PlaceholderMapping.empty()));
        assertEquals("text", e.getMessage());
    }

    @Test
    void nullZuordnungAk47() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> r.restore("x", null));
        assertEquals("mapping", e.getMessage());
    }

    // ----- AK-50: Klartextfreiheit -----

    @Test
    void toStringEnthaeltKeinenKlartextAk50() {
        ReidentificationResult res = r.restore("x [[PFAD_1]] [[PFAD_7]]", ein("[[PFAD_1]]"));
        assertTrue(res.text().contains(MARKER));
        assertFalse(res.toString().contains(MARKER));
        assertFalse(res.toString().contains("[[PFAD_7]]"));
        assertEquals("ReidentificationResult[textLength=" + res.text().length() + ", unresolved=1]", res.toString());
    }

    @Test
    void ausnahmemeldungenOhneMarkerAk50() {
        ReidentificationResult res = r.restore(MARKER, ein("[[PFAD_1]]"));
        assertEquals(MARKER, res.text());
        for (Throwable t : new Throwable[] {
            assertThrows(NullPointerException.class, () -> r.restore(null, ein("[[PFAD_1]]"))),
            assertThrows(NullPointerException.class, () -> r.restore(MARKER, null)),
            assertThrows(NullPointerException.class, () -> new ReidentificationResult(null, List.of())),
            assertThrows(NullPointerException.class, () -> new ReidentificationResult(MARKER, null)),
            assertThrows(IllegalArgumentException.class, () -> new UnresolvedPlaceholder("x", -1, Reason.UNBEKANNT)),
            assertThrows(NullPointerException.class, () -> new UnresolvedPlaceholder(null, 0, Reason.UNBEKANNT))}) {
            assertFalse(String.valueOf(t.getMessage()).contains(MARKER));
        }
    }

    @Test
    void unresolvedListeIstUnveraenderlich() {
        ReidentificationResult res = r.restore("[[PFAD_9]]", PlaceholderMapping.empty());
        assertThrows(UnsupportedOperationException.class, () -> res.unresolved().clear());
        List<UnresolvedPlaceholder> quelle = new java.util.ArrayList<>(res.unresolved());
        ReidentificationResult kopie = new ReidentificationResult("x", quelle);
        quelle.clear();
        assertEquals(1, kopie.unresolved().size());
    }

    // ----- feindliche Eingaben (KI-Antwort): Platzhalter-aehnliche Bruchstuecke -----

    private static final String EIN_PH = "[[EMAIL_1]]";

    private ReidentificationResult hostile(String text) {
        return r.restore(text, ein(EIN_PH));
    }

    @Test
    void bruchstueckNurOeffnendeKlammern() {
        erwarte(hostile("[["), "[[");
    }

    @Test
    void bruchstueckTypOhneNummer() {
        erwarte(hostile("[[EMAIL_"), "[[EMAIL_", new UnresolvedPlaceholder("[[EMAIL_", 0, Reason.BESCHAEDIGT));
    }

    @Test
    void bruchstueckEineSchliessendeKlammer() {
        erwarte(hostile("[[EMAIL_1]"), "[[EMAIL_1]",
                new UnresolvedPlaceholder("[[EMAIL_1]", 0, Reason.BESCHAEDIGT));
    }

    @Test
    void bruchstueckVierSchliessendeKlammern() {
        erwarte(hostile("[[EMAIL_1]]]]"), MARKER + "]]");
    }

    @Test
    void bruchstueckDreiOeffnendeKlammern() {
        erwarte(hostile("[[[EMAIL_1]]"), "[" + MARKER);
    }

    @Test
    void bruchstueckFuehrendeNull() {
        erwarte(hostile("[[EMAIL_01]]"), "[[EMAIL_01]]",
                new UnresolvedPlaceholder("[[EMAIL_01]]", 0, Reason.BESCHAEDIGT));
    }

    @Test
    void bruchstueckNummerNull() {
        erwarte(hostile("[[EMAIL_0]]"), "[[EMAIL_0]]",
                new UnresolvedPlaceholder("[[EMAIL_0]]", 0, Reason.BESCHAEDIGT));
    }

    @Test
    void bruchstueckKleinschreibung() {
        erwarte(hostile("[[email_1]]"), MARKER);
    }

    @Test
    void bruchstueckZahlUeberlaufKeineAusnahmeKeinAbschneiden() {
        String text = "[[EMAIL_99999999999999999999]]";
        ReidentificationResult res = hostile(text);
        assertEquals(text, res.text());
        assertEquals(1, res.unresolved().size());
        assertEquals(Reason.BESCHAEDIGT, res.unresolved().get(0).reason());
        // auch 10 Ziffern und Int-Grenzen werden weder ersetzt noch fuehren sie zu einer Ausnahme
        for (String n : new String[] {"1000000000", "2147483648", "4294967297", "9223372036854775808"}) {
            ReidentificationResult x = hostile("[[EMAIL_" + n + "]]");
            assertEquals("[[EMAIL_" + n + "]]", x.text());
        }
    }

    @Test
    void bruchstueckNeunStelligeNummerIstUnbekanntNichtBeschaedigt() {
        erwarte(hostile("[[EMAIL_999999999]]"), "[[EMAIL_999999999]]",
                new UnresolvedPlaceholder("[[EMAIL_999999999]]", 0, Reason.UNBEKANNT));
    }

    @Test
    void bruchstueckLeerzeichenInnen() {
        erwarte(hostile("[[ EMAIL_1 ]]"), MARKER);
    }

    @Test
    void bruchstueckVollbreiteKlammern() {
        String text = "\uFF3B\uFF3BEMAIL_1\uFF3D\uFF3D";
        erwarte(hostile(text), text);
        String gemischt = "\uFF3B\uFF3BEMAIL_1]]";
        erwarte(hostile(gemischt), gemischt);
    }

    @Test
    void bruchstueckUnsichtbareZeichenImPlatzhalter() {
        int[] unsichtbar = {0x00AD, 0x200B, 0x200C, 0x200D, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
            0x2060, 0xFEFF};
        for (int cp : unsichtbar) {
            String z = new String(Character.toChars(cp));
            for (String text : new String[] {"[[EMAIL" + z + "_1]]", "[[EMAIL_" + z + "1]]", "[[EMAIL_1" + z + "]]",
                "[[" + z + "EMAIL_1]]", "[" + z + "[EMAIL_1]]", "[[EMAIL_1]" + z + "]", "[[E" + z + "MAIL_1]]"}) {
                assertFalse(hostile(text).text().contains(MARKER), "U+" + Integer.toHexString(cp) + " " + text.length());
            }
        }
    }

    @Test
    void unsichtbaresZeichenAusserhalbBleibtUndPlatzhalterWirdAufgeloest() {
        String z = "\u200B";
        erwarte(hostile(z + EIN_PH + z), z + MARKER + z);
    }

    @Test
    void bruchstueckUnterstrichStattNummer() {
        erwarte(hostile("[[EMAIL__1]]"), "[[EMAIL__1]]", new UnresolvedPlaceholder("[[EMAIL_", 0, Reason.BESCHAEDIGT));
    }

    @Test
    void verschachtelteKlammern() {
        erwarte(hostile("[[[[EMAIL_1]]]]"), "[[" + MARKER + "]]");
        erwarte(hostile("[[ [[EMAIL_1]] ]]"), "[[ " + MARKER + " ]]");
    }

    @Test
    void platzhalterImPlatzhalter() {
        erwarte(hostile("[[EMAIL_[[EMAIL_1]]]]"), "[[EMAIL_" + MARKER + "]]",
                new UnresolvedPlaceholder("[[EMAIL_", 0, Reason.BESCHAEDIGT));
    }

    @Test
    void zeilenumbruchImPlatzhalterWirdNichtAufgeloest() {
        erwarte(hostile("[[EMAIL_1\n]]"), "[[EMAIL_1\n]]",
                new UnresolvedPlaceholder("[[EMAIL_1", 0, Reason.BESCHAEDIGT));
        erwarte(hostile("[[\nEMAIL_1]]"), "[[\nEMAIL_1]]");
    }

    @Test
    void neunLeerzeichenWerdenNichtToleriert() {
        String text = "[[" + " ".repeat(9) + "EMAIL_1]]";
        erwarte(hostile(text), text);
    }

    // ----- Zusammenspiel mit Pseudonymizer -----

    @Test
    void roundtripUeberProduktApi() {
        String text = "Mail erika@example.invalid, Server 192.0.2.7, nochmal erika@example.invalid.";
        int p1 = text.indexOf("erika@example.invalid");
        int p2 = text.indexOf("192.0.2.7");
        Finding f1 = new Finding(FindingType.EMAIL, "erika@example.invalid", p1, p1 + 21, 1.0, Finding.SOURCE_MANUAL);
        Finding f2 = new Finding(FindingType.IP, "192.0.2.7", p2, p2 + 9, 1.0, Finding.SOURCE_MANUAL);
        int p3 = text.lastIndexOf("erika@example.invalid");
        Finding f3 = new Finding(FindingType.EMAIL, "erika@example.invalid", p3, p3 + 21, 1.0, Finding.SOURCE_MANUAL);
        PseudonymizationResult pr = new Pseudonymizer().pseudonymize(text, List.of(f1, f2, f3));
        assertEquals("Mail [[EMAIL_1]], Server [[IP_2]], nochmal [[EMAIL_1]].", pr.text());
        erwarte(r.restore(pr.text(), pr.mapping()), text);
    }

    @Test
    void fremdbearbeiteterTextUeberProduktApi() {
        String text = "A=erika@example.invalid B=192.0.2.7";
        int p2 = text.indexOf("192.0.2.7");
        Finding f1 = new Finding(FindingType.EMAIL, "erika@example.invalid", 2, 23, 1.0, Finding.SOURCE_MANUAL);
        Finding f2 = new Finding(FindingType.IP, "192.0.2.7", p2, p2 + 9, 1.0, Finding.SOURCE_MANUAL);
        PseudonymizationResult pr = new Pseudonymizer().pseudonymize(text, List.of(f1, f2));
        String ki = "Antwort: [[IP_2]] zuerst, dann [[ email_1 ]]. Ausserdem [[IP_9]].";
        erwarte(r.restore(ki, pr.mapping()),
                "Antwort: 192.0.2.7 zuerst, dann erika@example.invalid. Ausserdem [[IP_9]].",
                new UnresolvedPlaceholder("[[IP_9]]", ki.indexOf("[[IP_9]]"), Reason.UNBEKANNT));
    }

    // ----- Laufzeit und Speicher -----

    private ReidentificationResult zeitlich(String text, PlaceholderMapping m) {
        return assertTimeoutPreemptively(GRENZE, () -> r.restore(text, m));
    }

    @Test
    void pathologischEckigeKlammern() {
        String text = "[".repeat(1_000_000);
        erwarte(zeitlich(text, ein(EIN_PH)), text);
    }

    @Test
    void pathologischDoppelteOeffnendeKlammern() {
        String text = "[[".repeat(500_000);
        erwarte(zeitlich(text, ein(EIN_PH)), text);
    }

    @Test
    void pathologischSchliessendeKlammern() {
        String text = "]".repeat(1_000_000);
        erwarte(zeitlich(text, ein(EIN_PH)), text);
    }

    @Test
    void pathologischTypanfangWiederholt() {
        String text = "[[A_".repeat(250_000);
        erwarte(zeitlich(text, ein(EIN_PH)), text);
        ReidentificationResult res = zeitlich(text, mapping("[[A_1]]", MARKER));
        assertEquals(text, res.text());
        assertEquals(250_000, res.unresolved().size());
        assertTrue(res.unresolved().stream().allMatch(u -> u.reason() == Reason.BESCHAEDIGT));
    }

    @Test
    void pathologischUnvollstaendigeNummerWiederholt() {
        String text = "[[EMAIL_1".repeat(110_000);
        ReidentificationResult res = zeitlich(text, ein(EIN_PH));
        assertEquals(text, res.text());
        assertEquals(110_000, res.unresolved().size());
    }

    @Test
    void hunderttausendGueltigePlatzhalter() {
        String text = "[[EMAIL_1]]".repeat(100_000);
        ReidentificationResult res = zeitlich(text, mapping(EIN_PH, "x@example.invalid"));
        assertEquals("x@example.invalid".repeat(100_000), res.text());
        assertTrue(res.unresolved().isEmpty());
    }

    @Test
    void hunderttausendUnbekanntePlatzhalter() {
        String text = "[[EMAIL_2]]".repeat(100_000);
        ReidentificationResult res = zeitlich(text, ein(EIN_PH));
        assertEquals(text, res.text());
        assertEquals(100_000, res.unresolved().size());
        assertEquals(11 * 99_999, res.unresolved().get(99_999).position());
    }

    @Test
    void eineMillionZeichenGemischt() {
        StringBuilder sb = new StringBuilder();
        StringBuilder erwartet = new StringBuilder();
        while (sb.length() < 1_000_000) {
            sb.append("text [[EMAIL_1]] [ [[x [[IP_ 2]] [[EMAIL_1] ]] ");
            erwartet.append("text ").append(MARKER).append(" [ [[x [[IP_ 2]] [[EMAIL_1] ]] ");
        }
        ReidentificationResult res = zeitlich(sb.toString(), ein(EIN_PH));
        assertEquals(erwartet.toString(), res.text());
    }

    @Test
    void sehrLangeOriginalwerteEinmalEingesetzt() {
        String lang = "[[EMAIL_1]]".repeat(1_000);
        String text = "[[EMAIL_1]]".repeat(200);
        ReidentificationResult res = zeitlich(text, mapping(EIN_PH, lang));
        assertEquals(lang.repeat(200), res.text());
    }
}
