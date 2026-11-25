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
 * beginnt oder endet in einem Surrogatpaar. Meldungen nennen nur Menge, Index und Zahlen.
 */
class GeheimnisKandidatenTest {

    private static final long SEED_ZUFALL = 20261128L;
    private static final int ZUFALLSTEXTE = 60_000;
    private static final int GENERATORTEXTE = 20_000;

    private static final Recognizer PASSWORT = new PasswortRecognizer();

    /** Prueft die Kandidaten eines Texts; liefert die Zahl der Kandidaten. */
    private static int pruefe(String menge, int index, String text) {
        List<Finding> k = PASSWORT.detect(text);
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
