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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.geheimnis.GeheimnisTypen;
import de.skillshield.core.recognizer.geheimnis.PasswortRecognizer;
import de.skillshield.core.recognizer.geheimnis.PasswortRecognizerStandT4;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Kein Verlust gegenueber T-4 (Plan geheimnisse-erkennung, Nachtrag "T-5 verkleinert", Punkt 5): Auf jedem Text steht
 * jeder Kandidat des Stands {@code ge-t4-passwort} feldgleich im Ergebnis des neuen {@link PasswortRecognizer}, und
 * jeder weitere Kandidat ueberlappt keinen T-4-Kandidaten. Der Massstab ist die woertliche Kopie der T-4-Kontextregel
 * im Testbaum ({@link PasswortRecognizerStandT4}). Geprueft werden alle Bestandsmengen (die 15 Mengen des
 * Differenztests), Negativ- und Positivkorpus, die drei Generatoren, das Referenz-Ticket und ein Zufallslauf mit
 * mehr als 100 000 Texten und festem Seed, der K1 bis K4 mischt. Je Menge stehen die Zahlen fest. Meldungen nennen nur
 * Menge, Index und Zahlen, nie Text oder Wert.
 */
class PasswortVergleichT4Test {

    private static final Recognizer NEU = new PasswortRecognizer();
    private static final Recognizer T4 = new PasswortRecognizerStandT4();

    /** Ergebnis des Vergleichs einer Menge. */
    record Zahlen(int texte, int t4Kandidaten, int weitere, int textMitWeiteren, int textMitBeiden,
                  List<String> verstoesse) {
    }

    private static boolean ueberlappt(Finding a, Finding b) {
        return a.start() < b.end() && b.start() < a.end();
    }

    static Zahlen vergleiche(String menge, List<String> texte) {
        return vergleiche(menge, texte, NEU, T4);
    }

    /** Vergleicht {@code neu} gegen {@code t4} auf allen Texten; die Erkenner sind Parameter (Gegenproben). */
    static Zahlen vergleiche(String menge, List<String> texte, Recognizer neuErkenner, Recognizer t4Erkenner) {
        int t4Kandidaten = 0;
        int weitere = 0;
        int mitWeiteren = 0;
        int mitBeiden = 0;
        List<String> verstoesse = new ArrayList<>();
        for (int i = 0; i < texte.size(); i++) {
            String text = texte.get(i);
            List<Finding> alt = t4Erkenner.detect(text);
            List<Finding> neu = neuErkenner.detect(text);
            t4Kandidaten += alt.size();
            for (Finding f : alt) {
                if (!neu.contains(f)) {
                    verstoesse.add(menge + "#" + i + ": T-4-Kandidat fehlt oder ist veraendert");
                }
            }
            int zusatz = 0;
            for (Finding g : neu) {
                if (alt.contains(g)) {
                    continue;
                }
                zusatz++;
                for (Finding f : alt) {
                    if (ueberlappt(f, g)) {
                        verstoesse.add(menge + "#" + i + ": weiterer Kandidat ueberlappt einen T-4-Kandidaten");
                    }
                }
            }
            for (int k = 1; k < neu.size(); k++) {
                if (neu.get(k - 1).end() > neu.get(k).start()) {
                    verstoesse.add(menge + "#" + i + ": Ergebnis nicht sortiert oder ueberlappend");
                }
            }
            weitere += zusatz;
            if (zusatz > 0) {
                mitWeiteren++;
                if (!alt.isEmpty()) {
                    mitBeiden++;
                }
            }
        }
        return new Zahlen(texte.size(), t4Kandidaten, weitere, mitWeiteren, mitBeiden, verstoesse);
    }

    // ------------------------------------------------------------------ Bestandsmengen und Pruefstand

    @Test
    void bestandsmengen_alleFuenfzehn_keinT4KandidatVerlorenUndKeinWeitererKandidat() {
        List<List<String>> mengen = new ArrayList<>(DatumMengen.mengen());
        List<String> namen = new ArrayList<>(DatumMengen.NAMEN);
        namen.add("Teil 1i");
        mengen.add(DatumFuzzMengen.erzeugeKvI(DatumFuzzMengen.SEED_TEXTE_KV_I));
        assertEquals(15, mengen.size(), "Zahl der Bestandsmengen");
        int texte = 0;
        for (int i = 0; i < mengen.size(); i++) {
            Zahlen z = vergleiche(namen.get(i), mengen.get(i));
            assertEquals(List.of(), z.verstoesse(), namen.get(i));
            assertEquals(0, z.weitere(), namen.get(i) + ": weitere Kandidaten");
            texte += z.texte();
        }
        assertTrue(texte > 70_000, "zu wenige Texte: " + texte);
    }

    @Test
    void negativkorpusUndGrenzfaelle_keinVerlustKeineWeiteren() {
        List<String> texte = new ArrayList<>();
        GeheimnisFehlalarmTest.korpus().values().forEach(texte::addAll);
        Zahlen z = vergleiche("Negativkorpus", texte);
        assertEquals(List.of(), z.verstoesse());
        assertEquals(372, z.texte(), "Texte");
        assertEquals(0, z.t4Kandidaten(), "T-4-Kandidaten");
        assertEquals(0, z.weitere(), "weitere Kandidaten");
    }

    @Test
    void positivkorpus_keinVerlust_dieWeiterenSindDieErwartetenFundeVonK2BisK4() {
        List<String> texte = new ArrayList<>();
        int erwartetK2K4 = 0;
        for (GeheimnisPositivKorpus.Fall f : GeheimnisPositivKorpus.faelle()) {
            texte.add(f.text());
            if (GeheimnisPositivKorpus.K2.equals(f.kategorie()) || GeheimnisPositivKorpus.K3.equals(f.kategorie())
                    || GeheimnisPositivKorpus.K4.equals(f.kategorie())) {
                erwartetK2K4 += f.funde().size();
            }
        }
        Zahlen z = vergleiche("Positivkorpus", texte);
        assertEquals(List.of(), z.verstoesse());
        assertEquals(199, z.texte(), "Texte");
        assertEquals(erwartetK2K4, z.weitere(), "weitere Kandidaten = erwartete Funde von K2, K3, K4");
        assertTrue(z.t4Kandidaten() > 100, "T-4-Kandidaten " + z.t4Kandidaten());
    }

    @Test
    void generatoren_negativVerletzendPositiv_keinVerlust() {
        Zahlen n = vergleiche("Negativ-Generator", GeheimnisNegativGenerator.lauf().texte());
        assertEquals(List.of(), n.verstoesse());
        assertEquals(100_000, n.texte(), "Texte Negativ");
        assertEquals(0, n.weitere(), "weitere Kandidaten Negativ");
        Zahlen v = vergleiche("Verletzend-Generator", GeheimnisPositivGenerator.verletzend().texte());
        assertEquals(List.of(), v.verstoesse());
        assertEquals(100_000, v.texte(), "Texte Verletzend");
        assertEquals(0, v.weitere(), "weitere Kandidaten Verletzend");
        List<String> positiv = new ArrayList<>();
        GeheimnisPositivGenerator.lauf().texte().forEach(t -> positiv.add(t.text()));
        Zahlen p = vergleiche("Positiv-Generator", positiv);
        assertEquals(List.of(), p.verstoesse());
        assertEquals(100_000, p.texte(), "Texte Positiv");
        assertEquals(0, p.weitere(), "weitere Kandidaten Positiv");
        assertTrue(p.t4Kandidaten() >= 66_663, "T-4-Kandidaten Positiv " + p.t4Kandidaten());
    }

    @Test
    void referenzTicket_keinVerlustKeineWeiteren() {
        Zahlen z = vergleiche("Referenz-Ticket", List.of(SynthetischeGeheimnisse.korpusText("referenz-ticket.txt")));
        assertEquals(List.of(), z.verstoesse());
        assertEquals(1, z.t4Kandidaten(), "T-4-Kandidaten");
        assertEquals(0, z.weitere(), "weitere Kandidaten");
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
        Zahlen z = probe(liste, liste);
        assertEquals(List.of(), z.verstoesse());
        assertEquals(2, z.t4Kandidaten(), "T-4-Kandidaten");
        assertEquals(0, z.weitere(), "weitere Kandidaten");
        Zahlen echt = vergleiche("Probe echt", List.of("Passwort: " + P1), NEU, NEU);
        assertEquals(List.of(), echt.verstoesse());
        assertEquals(1, echt.t4Kandidaten(), "echter Erkenner gegen sich selbst");
    }

    @Test
    void gegenprobe_t4KandidatFehlt_einVerstoss() {
        Zahlen z = probe(List.of(kandidat(30, 40, 0.80)), List.of(kandidat(10, 22, 0.95), kandidat(30, 40, 0.80)));
        assertEquals(1, z.verstoesse().size(), "Zahl der Verstoesse");
        assertTrue(z.verstoesse().get(0).contains("T-4-Kandidat fehlt"), "Art des Verstosses");
        Zahlen leer = probe(List.of(), List.of(kandidat(10, 22, 0.95)));
        assertEquals(1, leer.verstoesse().size(), "Zahl der Verstoesse bei leerem Ergebnis");
    }

    @Test
    void gegenprobe_t4KandidatFeldveraendert_konfidenzOderEnde_jeEinVerstoss() {
        // Der feldveraenderte Kandidat ist zugleich ein "weiterer" Kandidat, der den T-4-Kandidaten ueberlappt:
        // je ein Verstoss "fehlt oder ist veraendert" und einer "ueberlappt", sonst keiner.
        Zahlen konfidenz = probe(List.of(kandidat(10, 22, 0.80)), List.of(kandidat(10, 22, 0.95)));
        assertEquals(2, konfidenz.verstoesse().size(), "Konfidenz");
        assertEquals(1, konfidenz.verstoesse().stream().filter(s -> s.contains("fehlt oder ist veraendert")).count(),
                "Art bei Konfidenz");
        Zahlen ende = probe(List.of(kandidat(10, 21, 0.95)), List.of(kandidat(10, 22, 0.95)));
        assertEquals(2, ende.verstoesse().size(), "Ende");
        assertEquals(1, ende.verstoesse().stream().filter(s -> s.contains("fehlt oder ist veraendert")).count(),
                "Art bei Ende");
    }

    @Test
    void gegenprobe_weitererKandidatUeberlapptEinenT4Kandidaten_wirdGemeldet() {
        Finding t4 = kandidat(10, 22, 0.95);
        Zahlen z = probe(List.of(t4, kandidat(20, 30, 0.80)), List.of(t4));
        assertEquals(0, z.verstoesse().stream().filter(s -> s.contains("fehlt")).count(), "kein fehlender Kandidat");
        assertEquals(1, z.verstoesse().stream().filter(s -> s.contains("ueberlappt einen")).count(), "Ueberlappung");
        assertEquals(1, z.weitere(), "weitere Kandidaten");
    }

    @Test
    void gegenprobe_unsortiertesErgebnis_einVerstoss() {
        Finding a = kandidat(10, 22, 0.95);
        Finding b = kandidat(30, 40, 0.80);
        Zahlen z = probe(List.of(b, a), List.of(a, b));
        assertEquals(1, z.verstoesse().size(), "Zahl der Verstoesse");
        assertTrue(z.verstoesse().get(0).contains("nicht sortiert"), "Art des Verstosses");
    }

    @Test
    void gegenprobe_weitererKandidatOhneUeberlappung_keinVerstoss() {
        Finding a = kandidat(10, 22, 0.95);
        Zahlen z = probe(List.of(a, kandidat(30, 40, 0.80)), List.of(a));
        assertEquals(List.of(), z.verstoesse());
        assertEquals(1, z.weitere(), "weitere Kandidaten");
        assertEquals(1, z.textMitBeiden(), "Text mit beiden");
    }

    // ------------------------------------------------------------------ Zufallslauf

    private static final long SEED_ZUFALL = 20261205L;
    private static final int ZUFALLSTEXTE = 120_000;

    private static final List<String> KONTEXTWOERTER = List.of("Passwort", "Kennwort", "password", "Initialpasswort",
            "DB_PASSWORD", "dbPassword", "PASSWORT", "Admin-Passwort", "passphrase", "pw", "pwd", "PW");
    private static final List<String> ZWISCHENTEILE = List.of("", "", " f" + (char) 0xFC + "r svc_x",
            " for db", " von SRV-APP07", " f" + (char) 0xFC + "r den Benutzer svc_x", " des Kontos svc_x",
            " zum Dienstkonto svc_x", " fuer den alten Benutzer svc_x");
    private static final List<String> K1_FORMEN = List.of(": ", ":", " = ", "=", " : ", ":\n  ", ":\r\n", " =\n");
    private static final List<String> K2_FORMEN = List.of(" ist ", " lautet ", " is ", " ist\n", " Ist  ", " is\t");
    private static final List<String> K3_FORMEN = List.of(" | ", "|", "\t", " |", "| ", "\t ", " \t");
    private static final List<String> RAUSCHEN = List.of("Dienst gestartet", "siehe Tresor", "x", "Ticket",
            "bitte " + (char) 0xE4 + "ndern", ".", ",", "(neu)", "->", "=>", ":=", "|", "<b>", "</b>");

    /** Zaehler der Konstellationen, die der Zufallslauf wirklich erzeugen muss (Vorbedingung). */
    static final class Zaehler {
        int k1;
        int k2;
        int k3;
        int k4;
        int fuerIstJetzt;
        int kontextwortHinterK2;
        int kontextwortHinterK3;
        int kontextwortHinterK4;
        int maskiert;
        int verdoppelt;
        int ohneSchluss;
        int mehrWerteJeZeile;
    }

    private static String wert(Random r, Zaehler z) {
        switch (r.nextInt(16)) {
            case 0:
                return P1;
            case 1:
                return P2;
            case 2:
                return P3;
            case 3:
                return P1_OHNE_AUSRUF;
            case 4:
                return P_HUNTER;
            case 5:
                return P6;
            case 6:
                return "\"" + P_MIT_LEERZEICHEN + "\"";
            case 7:
                return "'" + P2 + "'";
            case 8:
                z.maskiert++;
                return "\"Ab3#x\\\"Yz7\"";
            case 9:
                z.verdoppelt++;
                return r.nextBoolean() ? "'Ab3#x''Yz7'" : "\"Ab3#x\"\"Yz7\"";
            case 10:
                z.ohneSchluss++;
                return "\"" + P2;
            case 11:
                return P_BINDESTRICH_NEU;
            case 12:
                return P_FACHBEGRIFF_VERSCHLUESSELT;
            case 13:
                return "**" + P1 + "**";
            case 14:
                return "jetzt";
            default:
                return "";
        }
    }

    private static String kontextwort(Random r) {
        return KONTEXTWOERTER.get(r.nextInt(KONTEXTWOERTER.size()));
    }

    /** Ein Wert-Teil: Kontextwort direkt als Wert hinter K2, K3 oder K4 (mit K1 oder ohne). */
    private static String kontextwortAlsWert(Random r) {
        String k1 = K1_FORMEN.get(r.nextInt(K1_FORMEN.size()));
        switch (r.nextInt(3)) {
            case 0:
                return kontextwort(r) + k1 + P1;
            case 1:
                return "x-" + kontextwort(r) + ":" + P2;
            default:
                return kontextwort(r);
        }
    }

    private static String klausel(Random r, Zaehler z) {
        String kw = kontextwort(r);
        String zw = ZWISCHENTEILE.get(r.nextInt(ZWISCHENTEILE.size()));
        switch (r.nextInt(12)) {
            case 0:
            case 1:
                z.k1++;
                return kw + zw + K1_FORMEN.get(r.nextInt(K1_FORMEN.size())) + wert(r, z);
            case 2:
            case 3:
                z.k2++;
                return kw + zw + K2_FORMEN.get(r.nextInt(K2_FORMEN.size())) + wert(r, z);
            case 4:
                z.k3++;
                return kw + zw + K3_FORMEN.get(r.nextInt(K3_FORMEN.size())) + wert(r, z);
            case 5:
                z.k4++;
                return "<" + kw + ">" + wert(r, z) + "</" + kw + ">";
            case 6:
                z.fuerIstJetzt++;
                return kw + (r.nextBoolean() ? " f" + (char) 0xFC + "r svc_x ist jetzt: " : " for svc_x is now: ")
                        + wert(r, z);
            case 7:
                z.kontextwortHinterK2++;
                return kw + K2_FORMEN.get(r.nextInt(K2_FORMEN.size())) + kontextwortAlsWert(r);
            case 8:
                z.kontextwortHinterK3++;
                return kw + K3_FORMEN.get(r.nextInt(K3_FORMEN.size())) + kontextwortAlsWert(r);
            case 9:
                z.kontextwortHinterK4++;
                return "<" + kw + ">" + kontextwortAlsWert(r) + "</" + kw + ">";
            case 10:
                z.k1++;
                return kw + "\"" + K1_FORMEN.get(r.nextInt(K1_FORMEN.size())) + wert(r, z);
            default:
                return RAUSCHEN.get(r.nextInt(RAUSCHEN.size()));
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
    void zufallslauf_k1BisK4Gemischt_keinT4KandidatVerlorenUndKeinWeitererUeberlapptEinen() {
        Zaehler z = new Zaehler();
        List<String> texte = zufallstexte(z);
        Zahlen zahlen = vergleiche("Zufall", texte);
        assertEquals(List.of(), zahlen.verstoesse());
        assertEquals(ZUFALLSTEXTE, zahlen.texte(), "Texte");
        // Vorbedingung: die Konstellationen kommen wirklich vor (Zaehler der Erzeugung und des Ergebnisses)
        assertTrue(z.k1 > 10_000 && z.k2 > 10_000 && z.k3 > 5_000 && z.k4 > 5_000,
                "K1 " + z.k1 + " K2 " + z.k2 + " K3 " + z.k3 + " K4 " + z.k4);
        assertTrue(z.fuerIstJetzt > 5_000, "fuer ... ist jetzt " + z.fuerIstJetzt);
        assertTrue(z.kontextwortHinterK2 > 3_000 && z.kontextwortHinterK3 > 3_000 && z.kontextwortHinterK4 > 3_000,
                "Kontextwort hinter K2 " + z.kontextwortHinterK2 + " K3 " + z.kontextwortHinterK3 + " K4 "
                        + z.kontextwortHinterK4);
        assertTrue(z.maskiert > 3_000 && z.verdoppelt > 3_000 && z.ohneSchluss > 3_000,
                "maskiert " + z.maskiert + " verdoppelt " + z.verdoppelt + " ohne Schluss " + z.ohneSchluss);
        assertTrue(z.mehrWerteJeZeile > 30_000, "mehrere Werte je Zeile " + z.mehrWerteJeZeile);
        assertTrue(zahlen.t4Kandidaten() > 30_000, "T-4-Kandidaten " + zahlen.t4Kandidaten());
        assertTrue(zahlen.weitere() > 10_000, "weitere Kandidaten " + zahlen.weitere());
        assertTrue(zahlen.textMitBeiden() > 3_000, "Texte mit T-4-Kandidat und weiterem " + zahlen.textMitBeiden());
    }
}
