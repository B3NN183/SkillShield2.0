package de.skillshield.core;

import static de.skillshield.core.SynthetischeGeheimnisse.P1;
import static de.skillshield.core.SynthetischeGeheimnisse.P1_OHNE_AUSRUF;
import static de.skillshield.core.SynthetischeGeheimnisse.P2;
import static de.skillshield.core.SynthetischeGeheimnisse.P3;
import static de.skillshield.core.SynthetischeGeheimnisse.P6;
import static de.skillshield.core.SynthetischeGeheimnisse.P_BINDESTRICH_NEU;
import static de.skillshield.core.SynthetischeGeheimnisse.P_FACHBEGRIFF_VERSCHLUESSELT;
import static de.skillshield.core.SynthetischeGeheimnisse.P_HUNTER;
import static de.skillshield.core.SynthetischeGeheimnisse.P_MIT_LEERZEICHEN;
import static de.skillshield.core.SynthetischeGeheimnisse.T;
import static de.skillshield.core.SynthetischeGeheimnisse.T_15;
import static de.skillshield.core.SynthetischeGeheimnisse.T_16;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.geheimnis.GeheimnisTypen;
import de.skillshield.core.recognizer.geheimnis.PasswortRecognizer;
import de.skillshield.core.recognizer.geheimnis.PasswortRecognizerStandT4;
import de.skillshield.core.recognizer.geheimnis.PasswortRecognizerStandT5;
import de.skillshield.core.recognizer.geheimnis.TokenRecognizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Kein Unterschied zu T-5 beim Passwort (Plan geheimnisse-erkennung, T-6): Auf jedem Text liefert der
 * {@link PasswortRecognizer} (Stand T-6) feldgleich dieselbe Liste wie die eingefrorene Kopie
 * {@link PasswortRecognizerStandT5} (woertliche Kopie von {@code KontextRegel} und {@code SchwacheVerbinder} am Tag
 * {@code ge-t5-schwache-verbinder}, nur die Klassennamen sind geaendert). Verglichen werden die 15 Bestandsmengen
 * (die des Differenztests), Negativ- und Positivkorpus, die drei Generatoren, das Referenz-Ticket und ein Zufallslauf
 * mit mehr als 100 000 Texten und festem Seed, der Passwort- und Token-Formen (alle Kontextwoerter, Verbinder K1 bis
 * K4, Schema-Formen) mischt. Je Menge stehen die Zahlen fest. Meldungen nennen nur Menge, Index und Zahlen, nie Text
 * oder Wert.
 *
 * <p>Die Gegenproben belegen, dass der Vergleich anschlaegt: der Token-Erkenner gegen die T-5-Kopie liefert auf
 * Token-Texten Unterschiede, der Stand T-4 gegen den Stand T-5 auf Texten mit {@code ist}, {@code |} und Elementen,
 * und kuenstliche Erkenner mit fehlendem, verschobenem oder zusaetzlichem Kandidaten werden gemeldet.
 */
class PasswortVergleichT5Test {

    private static final Recognizer NEU = new PasswortRecognizer();
    private static final Recognizer T5 = new PasswortRecognizerStandT5();

    /** Ergebnis des Vergleichs einer Menge. */
    record Zahlen(int texte, int t5Kandidaten, int ungleich, List<String> verstoesse) {
    }

    static Zahlen vergleiche(String menge, List<String> texte) {
        return vergleiche(menge, texte, NEU, T5);
    }

    /** Vergleicht {@code neu} gegen {@code t5} auf allen Texten; die Erkenner sind Parameter (Gegenproben). */
    static Zahlen vergleiche(String menge, List<String> texte, Recognizer neuErkenner, Recognizer t5Erkenner) {
        int kandidaten = 0;
        int ungleich = 0;
        List<String> verstoesse = new ArrayList<>();
        for (int i = 0; i < texte.size(); i++) {
            String text = texte.get(i);
            List<Finding> alt = t5Erkenner.detect(text);
            List<Finding> neu = neuErkenner.detect(text);
            kandidaten += alt.size();
            if (!alt.equals(neu)) {
                ungleich++;
                verstoesse.add(menge + "#" + i + ": Liste ungleich (T-5 " + alt.size() + ", neu " + neu.size() + ")");
            }
        }
        return new Zahlen(texte.size(), kandidaten, ungleich, verstoesse);
    }

    // ------------------------------------------------------------------ Mengen (auch fuer den Scanner-Vergleich)

    /** Alle Mengen ausser dem Zufallslauf, in fester Reihenfolge: Name und Texte. */
    static Map<String, List<String>> mengen() {
        Map<String, List<String>> alle = new LinkedHashMap<>();
        List<List<String>> bestand = new ArrayList<>(DatumMengen.mengen());
        List<String> namen = new ArrayList<>(DatumMengen.NAMEN);
        namen.add("Teil 1i");
        bestand.add(DatumFuzzMengen.erzeugeKvI(DatumFuzzMengen.SEED_TEXTE_KV_I));
        for (int i = 0; i < bestand.size(); i++) {
            alle.put(namen.get(i), bestand.get(i));
        }
        List<String> negativ = new ArrayList<>();
        GeheimnisFehlalarmTest.korpus().values().forEach(negativ::addAll);
        alle.put("Negativkorpus", negativ);
        List<String> positiv = new ArrayList<>();
        for (GeheimnisPositivKorpus.Fall f : GeheimnisPositivKorpus.faelle()) {
            positiv.add(f.text());
        }
        alle.put("Positivkorpus", positiv);
        alle.put("Negativ-Generator", GeheimnisNegativGenerator.lauf().texte());
        alle.put("Verletzend-Generator", GeheimnisPositivGenerator.verletzend().texte());
        List<String> generator = new ArrayList<>();
        GeheimnisPositivGenerator.lauf().texte().forEach(t -> generator.add(t.text()));
        alle.put("Positiv-Generator", generator);
        alle.put("Referenz-Ticket", List.of(SynthetischeGeheimnisse.korpusText("referenz-ticket.txt")));
        return alle;
    }

    // ------------------------------------------------------------------ Bestandsmengen und Pruefstand

    @Test
    void bestandsmengen_alleFuenfzehn_derPasswortErkennerIstFeldgleichZuT5() {
        Map<String, List<String>> mengen = mengen();
        int texte = 0;
        int kandidaten = 0;
        int gezaehlt = 0;
        for (Map.Entry<String, List<String>> e : mengen.entrySet()) {
            if (gezaehlt >= 15) {
                break;
            }
            gezaehlt++;
            Zahlen z = vergleiche(e.getKey(), e.getValue());
            assertEquals(List.of(), z.verstoesse(), e.getKey());
            texte += z.texte();
            kandidaten += z.t5Kandidaten();
        }
        assertEquals(15, gezaehlt, "Zahl der Bestandsmengen");
        assertTrue(texte > 70_000, "zu wenige Texte: " + texte);
        assertEquals(0, kandidaten, "Passwort-Kandidaten in den Bestandsmengen (wie in T-5)");
    }

    @Test
    void negativkorpusUndPositivkorpus_feldgleichZuT5() {
        Map<String, List<String>> mengen = mengen();
        Zahlen n = vergleiche("Negativkorpus", mengen.get("Negativkorpus"));
        assertEquals(List.of(), n.verstoesse());
        assertEquals(372, n.texte(), "Texte Negativkorpus");
        assertEquals(0, n.t5Kandidaten(), "T-5-Kandidaten im Negativkorpus");
        Zahlen p = vergleiche("Positivkorpus", mengen.get("Positivkorpus"));
        assertEquals(List.of(), p.verstoesse());
        assertEquals(199, p.texte(), "Texte Positivkorpus");
        assertTrue(p.t5Kandidaten() > 100, "T-5-Kandidaten im Positivkorpus " + p.t5Kandidaten());
    }

    @Test
    void generatorenUndReferenzTicket_feldgleichZuT5() {
        Map<String, List<String>> mengen = mengen();
        Zahlen n = vergleiche("Negativ-Generator", mengen.get("Negativ-Generator"));
        assertEquals(List.of(), n.verstoesse());
        assertEquals(100_000, n.texte(), "Texte Negativ");
        assertEquals(0, n.t5Kandidaten(), "T-5-Kandidaten Negativ");
        Zahlen v = vergleiche("Verletzend-Generator", mengen.get("Verletzend-Generator"));
        assertEquals(List.of(), v.verstoesse());
        assertEquals(100_000, v.texte(), "Texte Verletzend");
        assertEquals(0, v.t5Kandidaten(), "T-5-Kandidaten Verletzend");
        Zahlen p = vergleiche("Positiv-Generator", mengen.get("Positiv-Generator"));
        assertEquals(List.of(), p.verstoesse());
        assertEquals(100_000, p.texte(), "Texte Positiv");
        assertEquals(66_663, p.t5Kandidaten(), "T-5-Kandidaten Positiv (genau die PASSWORT-Texte)");
        Zahlen t = vergleiche("Referenz-Ticket", mengen.get("Referenz-Ticket"));
        assertEquals(List.of(), t.verstoesse());
        assertEquals(1, t.t5Kandidaten(), "T-5-Kandidaten im Referenz-Ticket");
    }

    // ------------------------------------------------------------------ Gegenproben des Vergleichs

    private static final String PROBE_TEXT = "Passwort: abcdefghijkl und noch mehr Text dahinter";

    private static Finding kandidat(int start, int ende, double konfidenz) {
        return new Finding(GeheimnisTypen.PASSWORT, PROBE_TEXT.substring(start, ende), start, ende, konfidenz,
                PasswortRecognizer.QUELLE_KONTEXT);
    }

    private static Recognizer fest(List<Finding> funde) {
        return t -> funde;
    }

    private static Zahlen probe(List<Finding> neu, List<Finding> alt) {
        return vergleiche("Probe", List.of(PROBE_TEXT), fest(neu), fest(alt));
    }

    @Test
    void gegenprobe_identischeErkenner_keinVerstoss() {
        List<Finding> liste = List.of(kandidat(10, 22, 0.95), kandidat(30, 40, 0.80));
        assertEquals(List.of(), probe(liste, liste).verstoesse());
        Zahlen echt = vergleiche("Probe echt", List.of("Passwort: " + P1), NEU, NEU);
        assertEquals(List.of(), echt.verstoesse());
        assertEquals(1, echt.t5Kandidaten(), "echter Erkenner gegen sich selbst");
    }

    @Test
    void gegenprobe_fehlenderVerschobenerOderZusaetzlicherKandidat_wirdGemeldet() {
        Finding a = kandidat(10, 22, 0.95);
        Finding b = kandidat(30, 40, 0.80);
        assertEquals(1, probe(List.of(b), List.of(a, b)).verstoesse().size(), "fehlt");
        assertEquals(1, probe(List.of(), List.of(a)).verstoesse().size(), "leer");
        assertEquals(1, probe(List.of(a, b), List.of(a)).verstoesse().size(), "zusaetzlich");
        assertEquals(1, probe(List.of(kandidat(10, 21, 0.95)), List.of(a)).verstoesse().size(), "Ende verschoben");
        assertEquals(1, probe(List.of(kandidat(10, 22, 0.80)), List.of(a)).verstoesse().size(), "Konfidenz");
        assertEquals(1, probe(List.of(b, a), List.of(a, b)).verstoesse().size(), "Reihenfolge");
    }

    @Test
    void gegenprobe_derTokenErkennerGegenDieT5Kopie_unterscheidetSichAufTokenTexten() {
        List<String> texte = List.of("Token: " + T, "Authorization: Bearer " + T, "Bearer " + T,
                "Passwort: " + P1 + ", Token: " + T);
        Zahlen z = vergleiche("Token gegen T-5", texte, new TokenRecognizer(), T5);
        assertTrue(z.ungleich() >= 4, "Unterschiede " + z.ungleich());
        Zahlen gleich = vergleiche("Passwort gegen T-5", texte, NEU, T5);
        assertEquals(List.of(), gleich.verstoesse());
        assertEquals(1, gleich.t5Kandidaten(), "nur der Fund mit Passwort");
    }

    @Test
    void gegenprobe_derStandT4GegenDenStandT5_unterscheidetSichAufK2BisK4() {
        List<String> texte = List.of("Passwort ist " + P1, "| Passwort | " + P1 + " |",
                "<password>" + P2 + "</password>", "Passwort: " + P1);
        Zahlen z = vergleiche("T-4 gegen T-5", texte, new PasswortRecognizerStandT4(), T5);
        assertEquals(3, z.ungleich(), "Unterschiede");
    }

    // ------------------------------------------------------------------ Zufallslauf

    private static final long SEED_ZUFALL = 20261206L;
    private static final int ZUFALLSTEXTE = 120_000;

    private static final List<String> PASSWORT_WOERTER = List.of("Passwort", "Kennwort", "password", "Initialpasswort",
            "DB_PASSWORD", "dbPassword", "PASSWORT", "Admin-Passwort", "passphrase", "pw", "pwd", "PW");
    private static final List<String> TOKEN_WOERTER = List.of("Token", "access_token", "accessToken", "API Key",
            "API-Key", "apikey", "X-API-Key", "Secret", "client_secret", "secretkey", "accesskey", "privatekey",
            "Bearer-Token", "id_token");
    private static final List<String> SCHEMA_WOERTER = List.of("Authorization", "Bearer", "authorization", "bearer");
    private static final List<String> ZWISCHENTEILE = List.of("", "", " f" + (char) 0xFC + "r svc_x", " for db",
            " von SRV-APP07", " f" + (char) 0xFC + "r den Benutzer svc_x", " des Kontos svc_x",
            " fuer den alten Benutzer svc_x");
    private static final List<String> K1_FORMEN = List.of(": ", ":", " = ", "=", " : ", ":\n  ", ":\r\n", " =\n");
    private static final List<String> K2_FORMEN = List.of(" ist ", " lautet ", " is ", " ist\n", " Ist  ", " is\t");
    private static final List<String> K3_FORMEN = List.of(" | ", "|", "\t", " |", "| ", "\t ", " \t");
    private static final List<String> RAUSCHEN = List.of("Dienst gestartet", "siehe Tresor", "x", "Ticket",
            "bitte " + (char) 0xE4 + "ndern", ".", ",", "(neu)", "->", "=>", ":=", "|", "<b>", "</b>", "Basic",
            "Authorization failed", "Bearer Authentifizierung");

    /** Zaehler der Konstellationen, die der Zufallslauf wirklich erzeugen muss (Vorbedingung). */
    static final class Zaehler {
        int passwortK1;
        int passwortK2K4;
        int tokenK1;
        int tokenK2K4;
        int schemaMitVerbinder;
        int schemaWortAlsSchemaForm;
        int bearerOhneVerbinder;
        int tokenKontextImPasswortWert;
        int passwortKontextImTokenWert;
        int apiKeyMitLeerzeichen;
        int mehrWerteJeZeile;
    }

    private static String wert(Random r) {
        switch (r.nextInt(16)) {
            case 0:
                return P1;
            case 1:
                return P2;
            case 2:
                return P3;
            case 3:
                return T;
            case 4:
                return T_16;
            case 5:
                return T_15;
            case 6:
                return P6;
            case 7:
                return "\"" + P_MIT_LEERZEICHEN + "\"";
            case 8:
                return "'" + T + "'";
            case 9:
                return "\"" + T;
            case 10:
                return P_BINDESTRICH_NEU;
            case 11:
                return P_FACHBEGRIFF_VERSCHLUESSELT;
            case 12:
                return "**" + T + "**";
            case 13:
                return P1_OHNE_AUSRUF + P_HUNTER;
            case 14:
                return "jetzt";
            default:
                return "";
        }
    }

    private static String aus(Random r, List<String> liste) {
        return liste.get(r.nextInt(liste.size()));
    }

    private static String klausel(Random r, Zaehler z) {
        boolean token = r.nextInt(5) < 2;
        String kw = token ? aus(r, TOKEN_WOERTER) : aus(r, PASSWORT_WOERTER);
        String zw = aus(r, ZWISCHENTEILE);
        if (token && kw.equals("API Key")) {
            z.apiKeyMitLeerzeichen++;
        }
        switch (r.nextInt(14)) {
            case 0:
            case 1:
                if (token) {
                    z.tokenK1++;
                } else {
                    z.passwortK1++;
                }
                return kw + zw + aus(r, K1_FORMEN) + wert(r);
            case 2:
            case 3:
                z.tokenK2K4 += token ? 1 : 0;
                z.passwortK2K4 += token ? 0 : 1;
                return kw + zw + aus(r, K2_FORMEN) + wert(r);
            case 4:
                z.tokenK2K4 += token ? 1 : 0;
                z.passwortK2K4 += token ? 0 : 1;
                return kw + zw + aus(r, K3_FORMEN) + wert(r);
            case 5:
                z.tokenK2K4 += token ? 1 : 0;
                z.passwortK2K4 += token ? 0 : 1;
                return "<" + kw + ">" + wert(r) + "</" + kw + ">";
            case 6:
                z.schemaMitVerbinder++;
                return aus(r, SCHEMA_WOERTER) + aus(r, K1_FORMEN) + aus(r, List.of("Bearer ", "Basic ", "Token ", ""))
                        + wert(r);
            case 7:
                z.bearerOhneVerbinder++;
                return aus(r, SCHEMA_WOERTER) + " " + wert(r);
            case 8:
                z.tokenKontextImPasswortWert++;
                return aus(r, PASSWORT_WOERTER) + aus(r, K1_FORMEN) + aus(r, TOKEN_WOERTER) + aus(r, K1_FORMEN)
                        + wert(r);
            case 9:
                z.passwortKontextImTokenWert++;
                return aus(r, TOKEN_WOERTER) + aus(r, K1_FORMEN) + aus(r, PASSWORT_WOERTER) + aus(r, K1_FORMEN)
                        + wert(r);
            case 10:
                z.schemaWortAlsSchemaForm++;
                return "Authorization: " + aus(r, List.of("Bearer", "Basic", "Token")) + " " + wert(r);
            case 11:
                return kw + "\"" + aus(r, K1_FORMEN) + wert(r);
            default:
                return aus(r, RAUSCHEN);
        }
    }

    private static String zeile(Random r, Zaehler z) {
        int klauseln = 1 + r.nextInt(3);
        if (klauseln >= 2) {
            z.mehrWerteJeZeile++;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < klauseln; i++) {
            if (i > 0) {
                sb.append(r.nextBoolean() ? " " : ", ");
            }
            sb.append(klausel(r, z));
        }
        return sb.toString();
    }

    /** Die Texte des Zufallslaufs (fester Seed); {@code z} zaehlt die erzeugten Konstellationen. */
    static List<String> zufallstexte(Zaehler z) {
        Random r = new Random(SEED_ZUFALL);
        List<String> texte = new ArrayList<>(ZUFALLSTEXTE);
        for (int i = 0; i < ZUFALLSTEXTE; i++) {
            StringBuilder sb = new StringBuilder();
            int zeilen = 1 + r.nextInt(3);
            for (int k = 0; k < zeilen; k++) {
                if (k > 0) {
                    sb.append(r.nextInt(5) == 0 ? "\r\n" : "\n");
                }
                sb.append(zeile(r, z));
            }
            texte.add(sb.toString());
        }
        return texte;
    }

    @Test
    void zufallslauf_passwortUndTokenFormenGemischt_derPasswortErkennerIstFeldgleichZuT5() {
        Zaehler z = new Zaehler();
        List<String> texte = zufallstexte(z);
        Zahlen zahlen = vergleiche("Zufall", texte);
        assertEquals(List.of(), zahlen.verstoesse());
        assertEquals(ZUFALLSTEXTE, zahlen.texte(), "Texte");
        assertEquals(0, zahlen.ungleich(), "Texte mit Unterschied");
        // Vorbedingung: die Konstellationen kommen wirklich vor
        assertTrue(z.passwortK1 > 5_000 && z.passwortK2K4 > 8_000, "Passwort K1 " + z.passwortK1 + " K2 bis K4 "
                + z.passwortK2K4);
        assertTrue(z.tokenK1 > 3_000 && z.tokenK2K4 > 5_000, "Token K1 " + z.tokenK1 + " K2 bis K4 " + z.tokenK2K4);
        assertTrue(z.schemaMitVerbinder > 10_000 && z.schemaWortAlsSchemaForm > 8_000 && z.bearerOhneVerbinder > 10_000,
                "Schema " + z.schemaMitVerbinder + " " + z.schemaWortAlsSchemaForm + " " + z.bearerOhneVerbinder);
        assertTrue(z.tokenKontextImPasswortWert > 8_000 && z.passwortKontextImTokenWert > 8_000,
                "Kontext im Wert " + z.tokenKontextImPasswortWert + " " + z.passwortKontextImTokenWert);
        assertTrue(z.apiKeyMitLeerzeichen > 1_000, "API Key " + z.apiKeyMitLeerzeichen);
        assertTrue(z.mehrWerteJeZeile > 30_000, "mehrere Werte je Zeile " + z.mehrWerteJeZeile);
        assertTrue(zahlen.t5Kandidaten() > 30_000, "T-5-Kandidaten " + zahlen.t5Kandidaten());
        // der Token-Erkenner findet im selben Lauf viel (er ist nicht leer), und beide Erkenner treffen im selben Text
        int tokenTexte = 0;
        int beide = 0;
        Recognizer token = new TokenRecognizer();
        for (String text : texte) {
            boolean t = !token.detect(text).isEmpty();
            tokenTexte += t ? 1 : 0;
            beide += t && !T5.detect(text).isEmpty() ? 1 : 0;
        }
        assertTrue(tokenTexte > 30_000, "Texte mit Token-Kandidat " + tokenTexte);
        assertTrue(beide > 10_000, "Texte mit beiden " + beide);
    }
}
