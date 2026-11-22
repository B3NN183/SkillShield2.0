package de.skillshield.core;

import static de.skillshield.core.SynthetischeKvNummern.IK_FORM_DOKU_IP;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.IK_UNGUELTIG_KOMPLEMENT;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.RoundtripFuzzTest.Fall;
import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.general.EmailRecognizer;
import de.skillshield.core.recognizer.general.Ipv4Recognizer;
import de.skillshield.core.recognizer.general.PathRecognizer;
import de.skillshield.core.recognizer.general.UrlRecognizer;
import de.skillshield.core.recognizer.kv.IkRecognizer;
import de.skillshield.core.recognizer.kv.KvTestHilfen;
import de.skillshield.core.recognizer.kv.LanrRecognizer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Differenztest (T-7; AK-19, AK-22, AK-26): Scan mit den Erkennern vor dem Feature ({@code alt}: E-Mail, IPv4, URL,
 * Pfad) gegen {@code TextScanner.standard()} ({@code neu}) f&uuml;r die Texte aus Fuzz-Teil 1 (Altfolge), Fuzz-Teil 1b
 * (KV-Bausteine) und einen Korpus.
 *
 * <p>Pr&uuml;fungen je Text: (1) jede von {@code alt} abgedeckte Position ist in {@code neu} abgedeckt (Lesart AK-19:
 * keine bisher abgedeckte Position geht verloren; Typ und Stufe eines zusammengelegten Fundes d&uuml;rfen sich
 * &auml;ndern, Nachtrag unter T-6); (2) liefern weder LANR- noch IK-Erkenner einen Kandidaten, ist
 * {@code neu.equals(alt)}.
 *
 * <p>Meldungen nennen nur Startwert, erste Iteration und Anzahl, nie Textinhalte. Teil 1b und der Korpus werden vor dem
 * Scan mit dem W&auml;chter der Testdatenregel gepr&uuml;ft ({@link KvTestHilfen#neunerfenster}); Teil 1 ist die
 * unver&auml;nderte Altfolge und wird nicht ver&auml;ndert oder gepr&uuml;ft.
 */
class KvDifferenzTest {

    private static final TextScanner NEU = TextScanner.standard();
    private static final TextScanner ALT = new TextScanner(List.of(new EmailRecognizer(), new Ipv4Recognizer(),
            new UrlRecognizer(), new PathRecognizer()));
    private static final LanrRecognizer LANR = new LanrRecognizer();
    private static final IkRecognizer IK = new IkRecognizer();

    /** Feste Zahl der Waechtertreffer in Fuzz-Teil 1 (Ausnahme zur Testdatenregel, Nachtrag T-7). */
    private static final int WAECHTERTREFFER_ALTFOLGE = 11;

    private static final String MAIL = "erika.beispiel@example.invalid";
    /** U+1F600 als Surrogatpaar. */
    private static final String EMOJI = "\uD83D\uDE00";

    /** Ergebnis eines Vergleichs ueber eine Textfolge: erste Iteration und Anzahl je Verstoss, Klassenzaehler. */
    private static final class Ergebnis {
        int waechter;
        int waechterErste = -1;
        int verloren;
        int verlorenErste = -1;
        int ungleich;
        int ungleichErste = -1;
        int ohneKvKandidat;
        int mitKvKandidat;
        int mitKvUndAltFund;
        int neuWeichtAb;

        String meldung(long seed, String eigenschaft, int erste, int anzahl) {
            return "Startwert " + seed + ", Eigenschaft " + eigenschaft + ", erste Iteration " + erste
                    + ", betroffene Iterationen " + anzahl;
        }
    }

    private static boolean[] abdeckung(int laenge, List<Finding> funde) {
        boolean[] a = new boolean[laenge];
        for (Finding f : funde) {
            for (int i = f.start(); i < f.end(); i++) {
                a[i] = true;
            }
        }
        return a;
    }

    private static Ergebnis vergleiche(List<String> texte, boolean mitWaechter) {
        Ergebnis e = new Ergebnis();
        for (int it = 0; it < texte.size(); it++) {
            String text = texte.get(it);
            if (mitWaechter && KvTestHilfen.neunerfenster(text, "00")[1] != 0) {
                e.waechter++;
                if (e.waechterErste < 0) {
                    e.waechterErste = it;
                }
                continue;
            }
            List<Finding> alt = ALT.scan(text);
            List<Finding> neu = NEU.scan(text);
            boolean[] imNeuen = abdeckung(text.length(), neu);
            boolean verloren = false;
            for (Finding f : alt) {
                for (int i = f.start(); i < f.end(); i++) {
                    verloren |= !imNeuen[i];
                }
            }
            if (verloren) {
                e.verloren++;
                if (e.verlorenErste < 0) {
                    e.verlorenErste = it;
                }
            }
            boolean kvKandidat = !LANR.detect(text).isEmpty() || !IK.detect(text).isEmpty();
            if (kvKandidat) {
                e.mitKvKandidat++;
                if (!alt.isEmpty()) {
                    e.mitKvUndAltFund++;
                }
            } else {
                e.ohneKvKandidat++;
                if (!neu.equals(alt)) {
                    e.ungleich++;
                    if (e.ungleichErste < 0) {
                        e.ungleichErste = it;
                    }
                }
            }
            if (!neu.equals(alt)) {
                e.neuWeichtAb++;
            }
        }
        return e;
    }

    private static void pruefe(long seed, List<String> texte, boolean mitWaechter, int mindestOhneKv,
            int mindestMitKv, int mindestMitKvUndAltFund, int mindestAbweichend) {
        Ergebnis e = vergleiche(texte, mitWaechter);
        assertEquals(0, e.waechter, e.meldung(seed, "waechter", e.waechterErste, e.waechter));
        assertEquals(0, e.verloren, e.meldung(seed, "alt-position-nicht-abgedeckt", e.verlorenErste, e.verloren));
        assertEquals(0, e.ungleich, e.meldung(seed, "ohne-kv-kandidat-neu-ungleich-alt", e.ungleichErste,
                e.ungleich));
        // nicht ins Leere: beide Zweige der Pruefung und der Fall "KV-Kandidat bei bisherigem Fund" kommen vor
        assertTrue(e.ohneKvKandidat >= mindestOhneKv, "Startwert " + seed + ", Texte ohne KV-Kandidat: "
                + e.ohneKvKandidat + ", erwartet mindestens " + mindestOhneKv);
        assertTrue(e.mitKvKandidat >= mindestMitKv, "Startwert " + seed + ", Texte mit KV-Kandidat: "
                + e.mitKvKandidat + ", erwartet mindestens " + mindestMitKv);
        assertTrue(e.mitKvUndAltFund >= mindestMitKvUndAltFund, "Startwert " + seed
                + ", Texte mit KV-Kandidat und bisherigem Fund: " + e.mitKvUndAltFund + ", erwartet mindestens "
                + mindestMitKvUndAltFund);
        assertTrue(e.neuWeichtAb >= mindestAbweichend, "Startwert " + seed + ", Texte mit neu ungleich alt: "
                + e.neuWeichtAb + ", erwartet mindestens " + mindestAbweichend);
    }

    private static List<String> texte(List<Fall> faelle) {
        List<String> texte = new ArrayList<>(faelle.size());
        for (Fall f : faelle) {
            texte.add(f.text());
        }
        return texte;
    }

    // ------------------------------------------------------------------ Tests

    @Test
    void ak19_teil1Altfolge_neuGleichAlt() {
        // Altfolge ohne KV-Bausteine: ohne KV-Kandidat ist neu = alt, keine Position geht verloren.
        List<String> altfolge = texte(RoundtripFuzzTest.erzeuge(RoundtripFuzzTest.SEED_TEXTE));
        pruefe(RoundtripFuzzTest.SEED_TEXTE, altfolge, false, 900, 0, 0, 0);
    }

    /**
     * Dokumentierte Ausnahme (Plan, Nachtrag T-7 Punkt 1): Die Altfolge enthaelt genau 11 Texte, in denen der Waechter
     * der Testdatenregel anschlaegt (gueltige Neunerfenster aus Dokumentations-IPs). Die Zahl ist fest; waechst sie,
     * ist die Folge veraendert oder die Ausnahme ausgeweitet. Die Meldung nennt nur die Anzahl.
     */
    @Test
    void ak19_teil1Altfolge_waechterTrifftGenauElfTexte() {
        int treffer = 0;
        for (String text : texte(RoundtripFuzzTest.erzeuge(RoundtripFuzzTest.SEED_TEXTE))) {
            if (KvTestHilfen.neunerfenster(text, "00")[1] != 0) {
                treffer++;
            }
        }
        assertEquals(WAECHTERTREFFER_ALTFOLGE, treffer, "Waechtertreffer in Teil 1: " + treffer);
    }

    @Test
    void ak19_ak22_teil1bKvBausteine_keinePositionVerlorenUndGleichheitOhneKvKandidat() {
        pruefe(RoundtripFuzzTest.SEED_TEXTE_KV,
                texte(RoundtripFuzzTest.erzeugeKv(RoundtripFuzzTest.SEED_TEXTE_KV)), true, 200, 500, 100, 400);
    }

    @Test
    void ak19_ak26_korpus_keinePositionVerlorenUndGleichheitOhneKvKandidat() {
        pruefe(0L, korpus(), true, 25, 10, 3, 5);
    }

    /**
     * Beispiele aus AK-19: E-Mail, IPv4, URL, Pfad, IK und LANR in einem Text; KV-Kandidat mit gleichem Bereich wie die
     * IP ({@code IK 198.51.100.1}), neben einem Pfad und in einer E-Mail. Hier ist der Unterschied zwischen
     * {@code alt} und {@code neu} ausdruecklich erwartet: genau die zusaetzlichen KV-Funde.
     */
    @Test
    void ak19_beispiel_sechsFundeStattVier() {
        String text = "Mail " + MAIL + ", Server 192.0.2.10, Link https://example.invalid/a, Pfad C:\\Tools\\a.exe, IK "
                + IK_GUELTIG + ", LANR " + LANR_GUELTIG;
        assertEquals(0, KvTestHilfen.neunerfenster(text, "00")[1], "Waechter");
        assertEquals(4, ALT.scan(text).size(), "alt: E-Mail, IP, URL, Pfad");
        assertEquals(6, NEU.scan(text).size(), "neu: dazu IK und LANR");
    }

    // ------------------------------------------------------------------ Korpus

    /**
     * Korpus: Texte der Bauart des Roundtrip-Korpus (alle Pfadformen, E-Mail, IP, URL, Anfuehrungszeichen,
     * Zeilenenden, Unicode, AK-7 (g)) und KV-Texte der Akzeptanzkriterien, dazu alle KV-Texte des Roundtrip-Korpus
     * ({@code RoundtripKorpusTest.kvTexte()}, T-9).
     */
    static List<String> korpus() {
        List<String> t = new ArrayList<>();
        // Pfadformen
        t.add("Start C:\\Tools\\BeispielTool\\tool.exe jetzt");
        t.add("Start C:/Tools/BeispielTool/tool.exe jetzt");
        t.add("Starte \"C:\\Program Files\\Mein Tool\\tool.exe\" jetzt");
        t.add("Starte C:\\Program Files\\Mein Tool\\tool.exe und pr\u00FCfe das Log.");
        t.add("Ablage \\\\srv-beispiel\\freigabe\\ordner\\datei.txt");
        t.add("Ablage \"\\\\srv-beispiel\\Mein Freigabe\\Ordner x\\datei.txt\" ok");
        t.add("Ablage \\\\192.0.2.10\\freigabe\\ordner ok");
        t.add("Datei /opt/beispiel-tool/bin/tool laden");
        t.add("Zugriff //srv-beispiel/freigabe/x ok");
        t.add("Lade ~/beispiel/konfig.yml jetzt");
        t.add("Lade ~erika/akte.pdf jetzt");
        t.add("Sichere host.example:/export/home/erika jetzt");
        t.add("Starte .\\tools\\tool.exe jetzt");
        t.add("Lade ../konf/app.yml jetzt");
        t.add("Zugriff auf \\srv\\freigabe\\a.txt pr\u00FCfen");
        t.add("Datei Users\\erika.mustermann\\Desktop\\akte.pdf lesen");
        t.add("Datei home/erika/akte.pdf lesen");
        t.add("Lade %APPDATA%\\BeispielTool\\konfig.ini jetzt");
        t.add("Lade ${HOME}/beispiel/konfig.yml jetzt");
        t.add("Oeffne file:///C:/Tools/BeispielTool/tool.exe jetzt");
        t.add("Oeffne \"file:///C:/Mein Ordner/x.txt\" jetzt");
        t.add("Kopiere C:\\Tools\\a.exe nach D:\\Ablage\\b.exe");
        // E-Mail, IP, URL, AK-7 (g)
        t.add("Mail an " + MAIL + " bitte");
        t.add("Server 192.0.2.10 antwortet");
        t.add("Siehe https://example.invalid/a/b?x=1 jetzt");
        t.add("Mail " + MAIL + ", Server 192.0.2.10, Tool C:\\Tools\\a.exe");
        t.add("Link https://intranet.invalid/?f='C:/Akte 2025/erika.beispiel@example.invalid/x.pdf' ende");
        t.add("https://intranet.invalid/?f=\"C:/Akte 2025/x.pdf\"");
        t.add("Original [[PFAD_1]] und C:\\Tools\\a.exe");
        // Zeilenenden, Leerraum, Unicode
        t.add("Zeile C:\\Tools\\a.exe\nMail " + MAIL + "\r\nIP 192.0.2.10\rEnde\n");
        t.add("  \t\n C:\\Tools\\a.exe  \n\n");
        t.add(EMOJI + " C:\\Tools\\a.exe " + EMOJI + " 192.0.2.10 " + EMOJI);
        t.add("\uFEFFStart C:\\Tools\\a.exe jetzt");
        t.add("\u05E9\u05DC\u05D5\u05DD C:\\Tools\\a.exe 192.0.2.10 \u200F\u05E9\u05DC\u05D5\u05DD");
        // KV-Texte (Konstanten aus SynthetischeKvNummern)
        t.add("LANR " + LANR_GUELTIG);
        t.add("IK " + IK_GUELTIG + ", LANR " + LANR_GUELTIG);
        t.add("LANR " + LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN + " und IK " + IK_UNGUELTIG_KOMPLEMENT);
        t.add("IK " + IK_FORM_DOKU_IP);
        t.add("Server " + IK_FORM_DOKU_IP + " und IK " + IK_FORM_DOKU_IP);
        t.add("IK 2 " + IK_FORM_DOKU_IP);
        t.add("Mail " + MAIL + ", Server 192.0.2.10, Link https://example.invalid/a, Pfad C:\\Tools\\a.exe, IK "
                + IK_GUELTIG + ", LANR " + LANR_GUELTIG);
        t.add("https://intranet.invalid/praxis?LANR=" + LANR_GUELTIG + "&x=1");
        t.add("Datei \"C:\\Praxis\\LANR " + LANR_GUELTIG + "\\x.txt\" ok");
        t.add("IK-" + IK_GUELTIG + "@example.invalid schrieb");
        t.add("LANR\n" + LANR_GUELTIG + "\nIK\r\n" + IK_GUELTIG);
        t.add(LANR_GUELTIG + " (LANR)");
        // KV-Texte des Roundtrip-Korpus (gemeinsame Liste, T-9)
        t.addAll(RoundtripKorpusTest.kvTexte());
        return t;
    }
}
