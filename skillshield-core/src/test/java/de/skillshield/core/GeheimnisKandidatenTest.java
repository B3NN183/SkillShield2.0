package de.skillshield.core;

import static de.skillshield.core.SynthetischeGeheimnisse.P1;
import static de.skillshield.core.SynthetischeGeheimnisse.P2;
import static de.skillshield.core.SynthetischeGeheimnisse.P3;
import static de.skillshield.core.SynthetischeGeheimnisse.P_MIT_LEERZEICHEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.geheimnis.PasswortRecognizer;
import de.skillshield.core.recognizer.geheimnis.TokenRecognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Kandidaten der Geheimnis-Erkenner untereinander ueberlappungsfrei (Plan geheimnisse-erkennung, Nachtrag der
 * Hauptsitzung, Entscheidung 8; security-Hinweis zu T-3). Die Lueckenregel des {@code TextScanner} ist nur linear,
 * solange jeder Geheimnis-Erkenner ueberlappungsfreie Kandidaten liefert. Der Test prueft den
 * {@code PasswortRecognizer} ueber den Pruefstand (Negativkorpus, Positivkorpus, beide Generatoren in Teilen, das
 * Referenz-Ticket) und einen Zufallslauf mit festem Seed aus Bausteinen, die jede Regel der Kontextregel anstossen.
 * Je Text gilt: aufsteigend nach Beginn, {@code ende <= naechster Beginn}, Teilstring-Invariante, und kein Fund
 * beginnt oder endet in einem Surrogatpaar. Seit T-5 (K2 bis K4, zwei Durchgaenge) gilt das auch fuer einen zweiten
 * Zufallslauf mit Bausteinen fuer {@code ist}, {@code |}, Tabulator und Elemente. Meldungen nennen nur Menge, Index
 * und Zahlen.
 */
class GeheimnisKandidatenTest {

    private static final long SEED_ZUFALL = 20261128L;
    private static final long SEED_ZUFALL_K2_K4 = 20261205L;
    private static final int ZUFALLSTEXTE = 60_000;
    private static final int GENERATORTEXTE = 20_000;

    private static final Recognizer PASSWORT = new PasswortRecognizer();

    /** Prueft die Kandidaten eines Texts; liefert die Zahl der Kandidaten. */
    private static int pruefe(String menge, int index, String text) {
        return pruefe(PASSWORT, menge, index, text);
    }

    /** Wie oben fuer einen beliebigen Geheimnis-Erkenner (seit T-6 auch den {@code TokenRecognizer}). */
    private static int pruefe(Recognizer erkenner, String menge, int index, String text) {
        List<Finding> k = erkenner.detect(text);
        for (int i = 0; i < k.size(); i++) {
            Finding f = k.get(i);
            assertTrue(text.substring(f.start(), f.end()).equals(f.originalValue()),
                    menge + "#" + index + ": Teilstring-Invariante bei " + i);
            assertTrue(f.start() >= 0 && f.end() <= text.length(), menge + "#" + index + ": Bereich");
            assertTrue(!Character.isLowSurrogate(text.charAt(f.start())), menge + "#" + index + ": Beginn im Paar");
            assertTrue(!Character.isHighSurrogate(text.charAt(f.end() - 1)), menge + "#" + index + ": Ende im Paar");
            if (i > 0) {
                assertTrue(k.get(i - 1).end() <= f.start(),
                        menge + "#" + index + ": Kandidaten ueberlappen oder sind nicht sortiert, " + i);
            }
        }
        return k.size();
    }

    @Test
    void negativkorpus_kandidatenUeberlappungsfrei() {
        int summe = 0;
        for (Map.Entry<String, List<String>> e : GeheimnisFehlalarmTest.korpus().entrySet()) {
            for (int i = 0; i < e.getValue().size(); i++) {
                summe += pruefe(e.getKey(), i, e.getValue().get(i));
            }
        }
        assertEquals(0, summe, "Kandidaten im Negativkorpus");
    }

    @Test
    void positivkorpus_kandidatenUeberlappungsfrei() {
        List<GeheimnisPositivKorpus.Fall> faelle = GeheimnisPositivKorpus.faelle();
        int summe = 0;
        for (int i = 0; i < faelle.size(); i++) {
            summe += pruefe(faelle.get(i).id(), i, faelle.get(i).text());
        }
        assertTrue(summe >= 100, "zu wenige Kandidaten im Positivkorpus: " + summe);
    }

    @Test
    void referenzTicket_kandidatenUeberlappungsfrei() {
        assertEquals(1, pruefe("Ticket", 0, SynthetischeGeheimnisse.korpusText("referenz-ticket.txt")));
    }

    @Test
    void generatoren_ersteTexte_kandidatenUeberlappungsfrei() {
        List<String> negativ = GeheimnisNegativGenerator.lauf(GeheimnisNegativGenerator.SEED, GENERATORTEXTE).texte();
        for (int i = 0; i < negativ.size(); i++) {
            assertEquals(0, pruefe("Negativ-Generator", i, negativ.get(i)), "Kandidat im Negativ-Generator " + i);
        }
        List<GeheimnisPositivGenerator.Text> positiv = GeheimnisPositivGenerator.lauf(GeheimnisPositivGenerator.SEED,
                GENERATORTEXTE).texte();
        int summe = 0;
        for (int i = 0; i < positiv.size(); i++) {
            summe += pruefe("Positiv-Generator", i, positiv.get(i).text());
        }
        assertTrue(summe >= GENERATORTEXTE / 2, "zu wenige Kandidaten im Positiv-Generator: " + summe);
        List<String> verletzend = GeheimnisPositivGenerator.verletzend(GENERATORTEXTE).texte();
        for (int i = 0; i < verletzend.size(); i++) {
            assertEquals(0, pruefe("Verletzend-Generator", i, verletzend.get(i)), "Kandidat im Verletzend-Generator");
        }
    }

    private static List<String> bausteine() {
        String emoji = new String(Character.toChars(0x1F600));
        return List.of("Passwort", " Passwort", "Kennwort", "pw", "pwd", "Passwort: ", "Passwort:", "password=", " : ",
                "=", ":", " ", "  ", "\n", "\r\n", "\r", "\t", "\"", "'", "`", "``", "**", "***", "*", "__", "_", "-",
                "(", ")", "[", "]", "{", "}", ".", ",", ";", "?", "!", "f" + (char) 0xFC + "r ", "for ", "des ",
                "svc_x", "x", "a1", P1, P2, P3, P_MIT_LEERZEICHEN, SynthetischeGeheimnisse.P6, emoji,
                (char) 0xA0 + "", (char) 0x2009 + "", (char) 0x201E + "", (char) 0x201C + "", (char) 0xBB + "",
                (char) 0xAB + "", "**Passwort:** ", "__Passwort__: ", "$", "%", "<", "/", "\\", "://", "@");
    }

    /** Bausteine fuer K2 bis K4 (T-5): Verbinder, Elemente, Tabellenzellen, Kontextwoerter und Werte gemischt. */
    private static List<String> bausteineK2BisK4() {
        return List.of("Passwort", "Kennwort", "password", "pw", "pwd", "Initialpasswort", "DB_PASSWORD", " ist ",
                " lautet ", " is ", " ist", "ist", "|", " | ", "| ", " |", "\t", "<password>", "</password>",
                "<Kennwort>", "</Kennwort>", "<pw>", "<", ">", "</", "Passwort ist ", "| Passwort | ", "Passwort\t",
                ": ", "=", "\n", "\r\n", " ", "  ", "f" + (char) 0xFC + "r ", "svc_x ", "den ", "Benutzer ", "\"", "'",
                "\\", "**", "__", "-", "-neu", "AES256-verschl" + (char) 0xFC + "sselt", P1, P2, P3, P_MIT_LEERZEICHEN,
                SynthetischeGeheimnisse.P_HUNTER, "x-Passwort:", "Ab3!xPasswort:", "Passwort: ");
    }

    @Test
    void zufallslaufK2BisK4_festerSeed_kandidatenUeberlappungsfrei_undKeinAusnahmefall() {
        List<String> teile = bausteineK2BisK4();
        Random r = new Random(SEED_ZUFALL_K2_K4);
        int kandidaten = 0;
        int mitSchwachemVerbinder = 0;
        for (int i = 0; i < ZUFALLSTEXTE; i++) {
            StringBuilder sb = new StringBuilder();
            int n = 1 + r.nextInt(30);
            for (int k = 0; k < n; k++) {
                sb.append(teile.get(r.nextInt(teile.size())));
            }
            String text = sb.toString();
            int anzahl = pruefe("Zufall K2 bis K4", i, text);
            kandidaten += anzahl;
            for (Finding f : PASSWORT.detect(text)) {
                if (f.confidence() < 0.90) {
                    mitSchwachemVerbinder++;
                    break;
                }
            }
        }
        assertTrue(kandidaten >= ZUFALLSTEXTE / 4, "der Zufallslauf trifft zu selten: " + kandidaten);
        assertTrue(mitSchwachemVerbinder >= ZUFALLSTEXTE / 20,
                "zu wenige Texte mit Kandidat 0,80: " + mitSchwachemVerbinder);
    }

    @Test
    void k2BisK4_ausgewaehlteTexte_kandidatenUeberlappungsfrei() {
        List<String> texte = List.of(
                "Passwort ist " + P1 + " Kennwort lautet " + P2 + " <password>" + P3 + "</password>",
                "| Passwort | " + P1 + " | Kennwort | " + P2 + " |", "Passwort\t" + P1 + "\tKennwort\t" + P2,
                "<password>pw=" + P1 + "</password><Kennwort>Passwort: " + P2 + "</Kennwort>",
                "Das Passwort f" + (char) 0xFC + "r svc_x ist jetzt: " + P1);
        for (int i = 0; i < texte.size(); i++) {
            assertTrue(pruefe("K2 bis K4", i, texte.get(i)) >= 1, "Kandidat " + i);
        }
        assertEquals(0, pruefe("K2 bis K4 Kontextwort als Wert", 0, "Passwort ist Passwort ist " + P1), "Zahl");
    }

    // ------------------------------------------------------------------ TokenRecognizer (T-6)

    private static final Recognizer TOKEN = new TokenRecognizer();
    private static final long SEED_ZUFALL_TOKEN = 20261207L;

    @Test
    void token_pruefstand_kandidatenUeberlappungsfrei() {
        int summe = 0;
        for (Map.Entry<String, List<String>> e : GeheimnisFehlalarmTest.korpus().entrySet()) {
            for (int i = 0; i < e.getValue().size(); i++) {
                summe += pruefe(TOKEN, e.getKey(), i, e.getValue().get(i));
            }
        }
        assertEquals(0, summe, "Token-Kandidaten im Negativkorpus");
        List<GeheimnisPositivKorpus.Fall> faelle = GeheimnisPositivKorpus.faelle();
        int positiv = 0;
        for (int i = 0; i < faelle.size(); i++) {
            positiv += pruefe(TOKEN, faelle.get(i).id(), i, faelle.get(i).text());
        }
        assertTrue(positiv >= 22, "zu wenige Token-Kandidaten im Positivkorpus: " + positiv);
        assertEquals(0, pruefe(TOKEN, "Ticket", 0, SynthetischeGeheimnisse.korpusText("referenz-ticket.txt")));
    }

    @Test
    void token_generatoren_ersteTexte_kandidatenUeberlappungsfrei() {
        List<GeheimnisPositivGenerator.Text> positiv = GeheimnisPositivGenerator.lauf(GeheimnisPositivGenerator.SEED,
                GENERATORTEXTE).texte();
        int summe = 0;
        for (int i = 0; i < positiv.size(); i++) {
            summe += pruefe(TOKEN, "Positiv-Generator", i, positiv.get(i).text());
        }
        assertTrue(summe >= GENERATORTEXTE / 4, "zu wenige Token-Kandidaten: " + summe);
        for (String t : GeheimnisNegativGenerator.lauf(GeheimnisNegativGenerator.SEED, GENERATORTEXTE).texte()) {
            assertEquals(0, TOKEN.detect(t).size(), "Token-Kandidat im Negativ-Generator");
        }
    }

    @Test
    void token_zufallslauf_festerSeed_kandidatenUeberlappungsfrei() {
        List<String> teile = List.of("Token", "Secret", "API Key", "api_key", "access_token", "Bearer", "Bearer ",
                "Authorization", "Authorization: ", "Basic ", " ist ", " lautet ", "|", " | ", "\t", "<token>",
                "</token>", "<secret>", ": ", "=", ":", "\n", "\r\n", " ", "  ", "\"", "'", "`", "**", "__", "-",
                "f" + (char) 0xFC + "r ", "svc_x ", "x-", "-Token", "_token", SynthetischeGeheimnisse.T,
                SynthetischeGeheimnisse.T_16, SynthetischeGeheimnisse.T_15, P1, P2, P_MIT_LEERZEICHEN, "$", "<",
                "/", "Passwort: ", new String(Character.toChars(0x1F600)), (char) 0xA0 + "");
        Random r = new Random(SEED_ZUFALL_TOKEN);
        int kandidaten = 0;
        int mitSchwach = 0;
        for (int i = 0; i < ZUFALLSTEXTE; i++) {
            StringBuilder sb = new StringBuilder();
            int n = 1 + r.nextInt(30);
            for (int k = 0; k < n; k++) {
                sb.append(teile.get(r.nextInt(teile.size())));
            }
            String text = sb.toString();
            int anzahl = pruefe(TOKEN, "Zufall Token", i, text);
            kandidaten += anzahl;
            for (Finding f : TOKEN.detect(text)) {
                if (f.confidence() < 0.90) {
                    mitSchwach++;
                    break;
                }
            }
        }
        assertTrue(kandidaten >= ZUFALLSTEXTE / 8, "der Zufallslauf trifft zu selten: " + kandidaten);
        assertTrue(mitSchwach >= ZUFALLSTEXTE / 20, "zu wenige Texte mit Kandidat 0,80: " + mitSchwach);
    }

    // ------------------------------------------------------------------ Formen (T-7)

    private static final long SEED_ZUFALL_FORMEN = 20261209L;

    private static List<String> bausteineFormen() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String fuss = SynthetischeGeheimnisse.pemFusszeile("PRIVATE KEY");
        List<String> teile = new ArrayList<>(List.of(SynthetischeGeheimnisse.jwt(), SynthetischeGeheimnisse.jwt() + ".",
                "Token: ", "Bearer ", "Authorization: Bearer ", "Token ist ", "<token>", "</token>",
                "https://x.invalid/", kopf, fuss, SynthetischeGeheimnisse.pemFusszeile("CERTIFICATE"),
                SynthetischeGeheimnisse.pemKopfzeile("CERTIFICATE"), "\n", "\r\n", "\r", " ", "  ", ".", ",", "-", "_",
                "\"", "ey" + "J", "ey" + "J" + SynthetischeGeheimnisse.MARKE, "Proc-Type: 4,ENCRYPTED", "x", "7",
                SynthetischeGeheimnisse.base64Zeile(64), SynthetischeGeheimnisse.base64Zeile(22) + "==",
                SynthetischeGeheimnisse.T, P1, new String(Character.toChars(0x1F600)), (char) 0xE4 + ""));
        teile.addAll(SynthetischeGeheimnisse.praefixe());
        teile.add("\n" + SynthetischeGeheimnisse.pemBlock("PRIVATE KEY", "\n", true) + "\n");
        teile.add("\n" + SynthetischeGeheimnisse.pemBlock("RSA PRIVATE KEY", "\r\n", false) + "\r\n");
        teile.add("\n" + SynthetischeGeheimnisse.pemKopfzeile("EC PRIVATE KEY") + "\n");
        for (String praefix : SynthetischeGeheimnisse.praefixe()) {
            teile.add(SynthetischeGeheimnisse.praefixToken(praefix));
        }
        return teile;
    }

    @Test
    void formen_zufallslauf_festerSeed_kandidatenUeberlappungsfrei_jedeQuelleKommtVor() {
        List<String> teile = bausteineFormen();
        Random r = new Random(SEED_ZUFALL_FORMEN);
        Map<String, Integer> quellen = new java.util.TreeMap<>();
        for (int i = 0; i < ZUFALLSTEXTE; i++) {
            StringBuilder sb = new StringBuilder();
            int n = 1 + r.nextInt(25);
            for (int k = 0; k < n; k++) {
                sb.append(teile.get(r.nextInt(teile.size())));
            }
            String text = sb.toString();
            pruefe(TOKEN, "Zufall Formen", i, text);
            for (Finding f : TOKEN.detect(text)) {
                quellen.merge(f.source(), 1, Integer::sum);
            }
        }
        assertTrue(quellen.getOrDefault(TokenRecognizer.QUELLE_JWT, 0) >= ZUFALLSTEXTE / 20, "JWT " + quellen);
        assertTrue(quellen.getOrDefault(TokenRecognizer.QUELLE_PEM, 0) >= ZUFALLSTEXTE / 50, "PEM " + quellen);
        assertTrue(quellen.getOrDefault(TokenRecognizer.QUELLE_PRAEFIX, 0) >= ZUFALLSTEXTE / 20, "Praefix " + quellen);
        assertTrue(quellen.getOrDefault(TokenRecognizer.QUELLE_KONTEXT, 0) >= ZUFALLSTEXTE / 50, "Kontext " + quellen);
    }

    @Test
    void formen_ausgewaehlteTexte_kandidatenUeberlappungsfrei_undUnveraendertBeiWiederholung() {
        String kopf = SynthetischeGeheimnisse.pemKopfzeile("PRIVATE KEY");
        String jwt = SynthetischeGeheimnisse.jwt();
        String token = SynthetischeGeheimnisse.praefixToken(SynthetischeGeheimnisse.praefixe().get(0));
        List<String> texte = List.of(
                SynthetischeGeheimnisse.pemBlock("PRIVATE KEY", List.of(jwt, token,
                        SynthetischeGeheimnisse.base64Zeile(64)), "\n", true),
                SynthetischeGeheimnisse.pemBlock("PRIVATE KEY", List.of("Token: " + SynthetischeGeheimnisse.T,
                        SynthetischeGeheimnisse.base64Zeile(64)), "\n", false),
                "Token: " + jwt + " Bearer " + token + " Authorization: Bearer " + jwt,
                kopf + "\n" + kopf + "\n" + SynthetischeGeheimnisse.base64Zeile(64) + "\n" + kopf,
                "a." + jwt + "." + token, jwt + "." + token, token + "." + jwt);
        for (int i = 0; i < texte.size(); i++) {
            assertTrue(pruefe(TOKEN, "Formen", i, texte.get(i)) >= 1, "Kandidat " + i);
            assertEquals(TOKEN.detect(texte.get(i)), TOKEN.detect(texte.get(i)), "Determinismus " + i);
        }
    }

    @Test
    void zufallslauf_festerSeed_kandidatenUeberlappungsfrei_undGleichBeiWiederholung() {
        List<String> teile = bausteine();
        Random r = new Random(SEED_ZUFALL);
        int kandidaten = 0;
        List<String> texte = new ArrayList<>(ZUFALLSTEXTE);
        for (int i = 0; i < ZUFALLSTEXTE; i++) {
            StringBuilder sb = new StringBuilder();
            int n = 1 + r.nextInt(40);
            for (int k = 0; k < n; k++) {
                sb.append(teile.get(r.nextInt(teile.size())));
            }
            String text = sb.toString();
            texte.add(text);
            kandidaten += pruefe("Zufall", i, text);
        }
        assertTrue(kandidaten >= ZUFALLSTEXTE / 10, "der Zufallslauf trifft zu selten: " + kandidaten);
        for (int i = 0; i < 2000; i++) {
            assertEquals(PASSWORT.detect(texte.get(i)), PASSWORT.detect(texte.get(i)), "Determinismus " + i);
        }
    }
}
