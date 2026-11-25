package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Prueft die zentrale Testklasse {@link SynthetischeGeheimnisse} (Plan geheimnisse-erkennung, Kapitel 6.2): Marke,
 * Laengen, Formen, und dass kein Wert eine Ziffernfolge ab fuenf Stellen oder eine Kette zu neun Ziffern enthaelt
 * (auch nicht zur Laufzeit gebaute Werte). Meldungen nennen Fall-IDs, nie Werte (Kapitel 6.1).
 */
class SynthetischeGeheimnisseTest {

    /** Alle Werte der zentralen Klasse, die ein Geheimnis sind, unter einer Fall-ID. */
    private static Map<String, String> alleWerte() {
        Map<String, String> werte = new TreeMap<>();
        werte.put("P1", SynthetischeGeheimnisse.P1);
        werte.put("P2", SynthetischeGeheimnisse.P2);
        werte.put("P3", SynthetischeGeheimnisse.P3);
        werte.put("P6", SynthetischeGeheimnisse.P6);
        werte.put("P7", SynthetischeGeheimnisse.P7);
        werte.put("T", SynthetischeGeheimnisse.T);
        werte.put("wert4096", SynthetischeGeheimnisse.wert4096());
        werte.put("wert4097", SynthetischeGeheimnisse.wert4097());
        werte.put("hex7", SynthetischeGeheimnisse.hex7());
        werte.put("hex32", SynthetischeGeheimnisse.hex32());
        werte.put("hex40", SynthetischeGeheimnisse.hex40());
        werte.put("hex64", SynthetischeGeheimnisse.hex64());
        werte.put("fingerprint", SynthetischeGeheimnisse.fingerprint());
        werte.put("base64Zeile60", SynthetischeGeheimnisse.base64Zeile60());
        werte.put("uuid", SynthetischeGeheimnisse.uuid());
        werte.put("jwt", SynthetischeGeheimnisse.jwt());
        werte.put("praefixRest", SynthetischeGeheimnisse.PRAEFIX_REST);
        List<String> praefixe = SynthetischeGeheimnisse.praefixe();
        for (int i = 0; i < praefixe.size(); i++) {
            werte.put("praefixToken" + i, SynthetischeGeheimnisse.praefixToken(praefixe.get(i)));
        }
        for (String art : SynthetischeGeheimnisse.pemPrivateArten()) {
            for (String ze : List.of("\n", "\r\n")) {
                for (boolean ende : List.of(true, false)) {
                    werte.put("pem" + art.length() + ze.length() + ende,
                            SynthetischeGeheimnisse.pemBlock(art, ze, ende));
                }
            }
        }
        werte.put("pemSchraegstrich", SynthetischeGeheimnisse.pemBlock("PRIVATE KEY",
                SynthetischeGeheimnisse.pemKoerperZeilenMitSchraegstrich(), "\n", true));
        return werte;
    }

    private static int laengsterZiffernlauf(String wert) {
        int max = 0;
        int lauf = 0;
        for (int i = 0; i < wert.length(); i++) {
            lauf = Character.isDigit(wert.charAt(i)) ? lauf + 1 : 0;
            max = Math.max(max, lauf);
        }
        return max;
    }

    @Test
    void keinWert_hatEineZiffernfolgeUeberVier() {
        List<String> verstoesse = new ArrayList<>();
        alleWerte().forEach((id, wert) -> {
            if (laengsterZiffernlauf(wert) > 4) {
                verstoesse.add(id);
            }
        });
        assertEquals(List.of(), verstoesse);
    }

    @Test
    void keinWert_hatEineZiffernketteAbNeunZiffern_auchNichtGleitend() {
        List<String> verstoesse = new ArrayList<>();
        alleWerte().forEach((id, wert) -> {
            for (TestdatenSynthetischTest.Kette kette : TestdatenSynthetischTest.ziffernKetten(wert)) {
                if (kette.ziffern() >= 9 || kette.verstossArt() != null) {
                    verstoesse.add(id);
                }
            }
        });
        assertEquals(List.of(), verstoesse);
    }

    @Test
    void keinWert_istEinFundDesZiffernkettenWaechters_ueberDieGanzeProbe() {
        // der Waechter prueft Zeilen; jede Probe als eine Zeile, jeder Wert einzeln
        List<String> verstoesse = new ArrayList<>();
        alleWerte().forEach((id, wert) -> {
            for (String zeile : wert.split("\\R", -1)) {
                TestdatenSynthetischTest.Ergebnis erg = TestdatenSynthetischTest.pruefe("probe/" + id, zeile,
                        Set.of(), Set.of(), true);
                if (!erg.verstoesse().isEmpty()) {
                    verstoesse.add(id);
                }
            }
        });
        assertEquals(List.of(), verstoesse);
    }

    @Test
    void alleTokenWerte_tragenDieMarke() {
        List<String> ohneMarke = new ArrayList<>();
        String[] ids = {"T", "jwt", "praefixRest"};
        Map<String, String> werte = alleWerte();
        for (String id : ids) {
            if (!werte.get(id).contains(SynthetischeGeheimnisse.MARKE)) {
                ohneMarke.add(id);
            }
        }
        for (String praefix : SynthetischeGeheimnisse.praefixe()) {
            if (!SynthetischeGeheimnisse.praefixToken(praefix).contains(SynthetischeGeheimnisse.MARKE)) {
                ohneMarke.add("praefix" + praefix.length());
            }
        }
        if (!String.join("", SynthetischeGeheimnisse.pemKoerperZeilen()).contains(SynthetischeGeheimnisse.MARKE)) {
            ohneMarke.add("pemKoerper");
        }
        if (!String.join("", SynthetischeGeheimnisse.pemKoerperZeilenMitSchraegstrich())
                .contains(SynthetischeGeheimnisse.MARKE)) {
            ohneMarke.add("pemSchraegstrich");
        }
        assertEquals(List.of(), ohneMarke);
        assertEquals("Testwert", SynthetischeGeheimnisse.MARKE);
    }

    @Test
    void passwoerter_habenDieLaengenAusDerAnforderung() {
        assertEquals(11, SynthetischeGeheimnisse.P1.length());
        assertTrue(SynthetischeGeheimnisse.P1.equals("Sommer2026" + "!"), "P1");
        assertEquals(13, SynthetischeGeheimnisse.P2.length());
        assertEquals(13, SynthetischeGeheimnisse.P3.length());
        assertEquals(6, SynthetischeGeheimnisse.P6.length());
        assertEquals(7, SynthetischeGeheimnisse.P7.length());
        assertTrue(SynthetischeGeheimnisse.T.length() >= 24, "T mindestens 24 Zeichen");
        assertEquals(29, SynthetischeGeheimnisse.T.length());
    }

    @Test
    void mischwerte_habenGenauDieLaenge_undErfuellenDieZeichenmischung() {
        assertEquals(4096, SynthetischeGeheimnisse.wert4096().length());
        assertEquals(4097, SynthetischeGeheimnisse.wert4097().length());
        for (String wert : List.of(SynthetischeGeheimnisse.wert4096(), SynthetischeGeheimnisse.wert4097(),
                SynthetischeGeheimnisse.mischwert(8), SynthetischeGeheimnisse.mischwert(40))) {
            assertTrue(Character.isLetter(wert.charAt(0)), "beginnt mit Buchstabe");
            assertTrue(Character.isLetter(wert.charAt(wert.length() - 1)), "endet mit Buchstabe");
            assertTrue(wert.chars().anyMatch(Character::isDigit), "enthaelt Ziffer");
            assertFalse(wert.contains(" "), "ohne Leerraum");
        }
        assertThrows(IllegalArgumentException.class, () -> SynthetischeGeheimnisse.mischwert(0));
    }

    @Test
    void hexProben_habenDieHashLaengen_nurHexZeichen_ziffernEinzeln() {
        assertEquals(7, SynthetischeGeheimnisse.hex7().length());
        assertEquals(32, SynthetischeGeheimnisse.hex32().length());
        assertEquals(40, SynthetischeGeheimnisse.hex40().length());
        assertEquals(64, SynthetischeGeheimnisse.hex64().length());
        for (String hex : List.of(SynthetischeGeheimnisse.hex32(), SynthetischeGeheimnisse.hex64())) {
            assertTrue(hex.chars().allMatch(c -> Character.digit(c, 16) >= 0), "nur Hex-Zeichen");
            assertTrue(laengsterZiffernlauf(hex) <= 1, "Ziffern einzeln");
        }
    }

    @Test
    void base64Zeile_hat60Zeichen_nurBase64Zeichen() {
        String zeile = SynthetischeGeheimnisse.base64Zeile60();
        assertEquals(60, zeile.length());
        assertTrue(zeile.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '+' || c == '/' || c == '='),
                "nur Base64-Zeichen");
        assertTrue(laengsterZiffernlauf(zeile) <= 1, "Ziffern einzeln");
    }

    @Test
    void fingerprint_hatDieFormMitDoppelpunkten() {
        String fp = SynthetischeGeheimnisse.fingerprint();
        String[] paare = fp.split(":", -1);
        assertEquals(20, paare.length);
        for (String paar : paare) {
            assertEquals(2, paar.length());
        }
    }

    @Test
    void uuid_hatDieForm8_4_4_4_12_undInJedemBlockEinenBuchstaben() {
        String uuid = SynthetischeGeheimnisse.uuid();
        String[] bloecke = uuid.split("-", -1);
        assertEquals(5, bloecke.length);
        int[] laengen = {8, 4, 4, 4, 12};
        for (int i = 0; i < bloecke.length; i++) {
            assertEquals(laengen[i], bloecke[i].length(), "Block " + i);
            assertTrue(bloecke[i].chars().anyMatch(Character::isLetter), "Block " + i + " ohne Buchstaben");
            assertTrue(bloecke[i].chars().allMatch(c -> Character.digit(c, 16) >= 0), "Block " + i + " nicht hex");
        }
    }

    @Test
    void jwt_hatDreiTeile_zweiPunkte_ersteZweiMitAnfang_jedesTeilAb8() {
        String jwt = SynthetischeGeheimnisse.jwt();
        String[] teile = jwt.split("\\.", -1);
        assertEquals(3, teile.length);
        String anfang = "ey" + "J";
        assertTrue(teile[0].startsWith(anfang), "Teil 1");
        assertTrue(teile[1].startsWith(anfang), "Teil 2");
        assertFalse(teile[2].startsWith(anfang), "Teil 3");
        for (String teil : teile) {
            assertTrue(teil.length() >= 8, "Teil zu kurz");
            assertTrue(teil.chars().allMatch(c -> (c < 128 && Character.isLetterOrDigit(c)) || c == '_' || c == '-'),
                    "nur JWT-Zeichen");
        }
    }

    @Test
    void pemBlock_aufbauMitLfCrLfUndOhneEnde() {
        for (String art : SynthetischeGeheimnisse.pemPrivateArten()) {
            for (String ze : List.of("\n", "\r\n")) {
                String block = SynthetischeGeheimnisse.pemBlock(art, ze, true);
                String kopf = "-----" + "BEGIN " + art + "-----";
                String fuss = "-----" + "END " + art + "-----";
                assertTrue(block.startsWith(kopf + ze), "Kopf " + art.length());
                assertTrue(block.endsWith(ze + fuss), "Fuss " + art.length());
                String ohneEnde = SynthetischeGeheimnisse.pemBlock(art, ze, false);
                assertFalse(ohneEnde.contains("END"), "keine END-Zeile " + art.length());
                assertTrue(ohneEnde.endsWith("=="), "endet mit der letzten Koerperzeile");
                assertEquals(3, SynthetischeGeheimnisse.pemKoerperZeilen().size());
            }
        }
        assertEquals(6, SynthetischeGeheimnisse.pemPrivateArten().size());
        assertEquals(3, SynthetischeGeheimnisse.pemOeffentlicheArten().size());
    }

    @Test
    void pemKoerper_erstesZeichenIstKeinSchraegstrich_dieSchraegstrichVarianteBeginntDamit() {
        assertFalse(SynthetischeGeheimnisse.pemKoerperZeilen().get(0).startsWith("/"), "Standardkoerper");
        String zeile = SynthetischeGeheimnisse.pemKoerperZeilenMitSchraegstrich().get(0);
        assertTrue(zeile.startsWith("/"), "beginnt mit Schraegstrich");
        assertTrue(zeile.indexOf('/', 1) > 0, "enthaelt einen zweiten Schraegstrich");
        for (String z : SynthetischeGeheimnisse.pemKoerperZeilen()) {
            assertTrue(z.length() <= 100, "Zeilenlaenge");
        }
    }

    @Test
    void praefixToken_hatMindestens20Zeichen_eineZiffer_eineMarke_nurErlaubteZeichen() {
        assertEquals(9, SynthetischeGeheimnisse.praefixe().size());
        for (String praefix : SynthetischeGeheimnisse.praefixe()) {
            String token = SynthetischeGeheimnisse.praefixToken(praefix);
            String rest = token.substring(praefix.length());
            assertTrue(token.length() >= 20, "Laenge " + praefix.length());
            assertTrue(rest.chars().anyMatch(Character::isDigit), "Ziffer im Rest " + praefix.length());
            assertTrue(rest.chars().allMatch(c -> (c < 128 && Character.isLetterOrDigit(c)) || c == '_' || c == '-'),
                    "Zeichen im Rest " + praefix.length());
            assertTrue(token.contains(SynthetischeGeheimnisse.MARKE), "Marke " + praefix.length());
        }
    }

    @Test
    void korpusLeser_fehlendeDatei_meldungNenntNurDenDateinamen() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> SynthetischeGeheimnisse.korpusText("gibt-es-nicht.txt"));
        assertEquals("Korpusdatei fehlt: gibt-es-nicht.txt", e.getMessage());
    }

    @Test
    void korpusLeser_referenzTicket_istLfNormalisiertUndNichtLeer() {
        String ticket = SynthetischeGeheimnisse.korpusText("referenz-ticket.txt");
        assertFalse(ticket.isEmpty(), "Ticket leer");
        assertFalse(ticket.contains("\r"), "CR im gelesenen Ticket");
        assertTrue(ticket.endsWith("\n"), "Ticket endet ohne Zeilenende");
    }

    private static int laengsterZiffernlaufImQuelltext(String quelltext) {
        int max = 0;
        int lauf = 0;
        for (int i = 0; i < quelltext.length(); i++) {
            lauf = Character.isDigit(quelltext.charAt(i)) ? lauf + 1 : 0;
            max = Math.max(max, lauf);
        }
        return max;
    }

    @Test
    void quelltextDerZentralenKlasse_hatKeineZiffernfolgeUeberVier() throws IOException {
        Path quelle = Path.of("src", "test", "java", "de", "skillshield", "core", "SynthetischeGeheimnisse.java");
        String text = Files.readString(quelle, StandardCharsets.ISO_8859_1);
        assertTrue(text.contains("class SynthetischeGeheimnisse"), "falsche Quelldatei gelesen");
        assertTrue(laengsterZiffernlaufImQuelltext(text) <= 4, "Ziffernfolge ueber vier Stellen im Quelltext");
    }

    @Test
    void ziffernlaufImQuelltext_gegenprobe_langeKetteWirdBemerkt() {
        assertEquals(4, laengsterZiffernlaufImQuelltext("a 1234 b 12 \"13\" + \"57\""));
        assertEquals(5, laengsterZiffernlaufImQuelltext("ab 1" + "2345"));
        assertEquals(10, laengsterZiffernlaufImQuelltext("x = " + "7".repeat(10)));
    }

    @Test
    void keinWert_istFuerEinenEchtenAnbieterGueltig_markeBrichtDiePruefsumme() {
        // GitHub-Tokens tragen eine Pruefsumme in den letzten sechs Zeichen; die Marke steht im Rest und der Rest
        // ist kein gueltiges Base62-Ergebnis. Hier gilt: jeder Token-Rest enthaelt die Marke.
        for (String praefix : SynthetischeGeheimnisse.praefixe()) {
            assertTrue(SynthetischeGeheimnisse.praefixToken(praefix).substring(praefix.length())
                    .contains(SynthetischeGeheimnisse.MARKE), "Marke im Rest " + praefix.length());
        }
    }
}
