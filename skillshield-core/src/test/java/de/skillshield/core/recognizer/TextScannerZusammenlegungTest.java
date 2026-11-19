package de.skillshield.core.recognizer;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import de.skillshield.core.model.ConfidenceLevel;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.general.EmailRecognizer;
import de.skillshield.core.recognizer.general.Ipv4Recognizer;
import de.skillshield.core.recognizer.general.PathRecognizer;
import de.skillshield.core.recognizer.general.UrlRecognizer;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Zusammenlegung (D-8, AK-7) und Enthaltensein (AK-24) mit den echten Erkennern über
 * {@link TextScanner#standard()} (T-11). Testdaten sind synthetisch ({@code *.invalid}, {@code erika.mustermann},
 * 192.0.2.0/24); Backslashes über {@code bs(...)}, Nicht-ASCII-Zeichen nur als Escapes.
 */
class TextScannerZusammenlegungTest {

    private static final TextScanner SCANNER = TextScanner.standard();
    private static final String AK7G =
            "Link https://intranet.invalid/?f='C:/Akte 2025/erika.beispiel@example.invalid/x.pdf' ende";

    // ------------------------------------------------------------------ Hilfen

    private static List<Finding> scan(String text) {
        List<Finding> funde = SCANNER.scan(text);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring");
            assertTrue(f.start() >= vorigesEnde, "sortiert, überlappungsfrei");
            vorigesEnde = f.end();
        }
        return funde;
    }

    private static String pseudonymisiert(String text) {
        List<Finding> funde = scan(text);
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, funde);
        ReidentificationResult back = new Reidentifier().restore(p.text(), p.mapping());
        assertEquals(text, back.text(), "Roundtrip");
        assertTrue(back.unresolved().isEmpty(), "keine Meldung");
        return p.text();
    }

    private static Finding einziger(String text) {
        List<Finding> funde = scan(text);
        assertEquals(1, funde.size(), "genau ein Fund");
        return funde.get(0);
    }

    // ------------------------------------------------------------------ AK-7 (g) mit echten Erkennern

    @Test
    void ak7gPraemisseUrlErkennerLiefertDenKandidatenBisZumLeerzeichen() {
        List<Finding> url = new UrlRecognizer().detect(AK7G);
        assertEquals(1, url.size());
        assertEquals("https://intranet.invalid/?f='C:/Akte", url.get(0).originalValue());
        assertEquals(5, url.get(0).start());
        assertEquals(41, url.get(0).end());
    }

    @Test
    void ak7gPraemisseEmailErkennerLiefertDieAdresse() {
        List<Finding> email = new EmailRecognizer().detect(AK7G);
        // Plan-Annahme: genau die Adresse [47,77). Der echte E-Mail-Erkenner (T-3) lässt "/" im lokalen Teil zu und
        // liefert "2025/erika.beispiel@example.invalid" [42,77); er endet gleich und liegt ganz im Pfad-Kandidaten.
        assertEquals(1, email.size());
        assertTrue(email.get(0).originalValue().endsWith("erika.beispiel@example.invalid"));
        assertEquals("2025/erika.beispiel@example.invalid", email.get(0).originalValue());
        assertEquals(42, email.get(0).start());
        assertEquals(77, email.get(0).end());
    }

    @Test
    void ak7gPraemissePfadErkennerLiefertDenPfadMitLeerzeichenBisZumSchliessendenApostroph() {
        // Plan-Annahme: Kandidat ab C:/Akte. Seit dem Nachtrag zu T-10 (keine Startgrenze für Laufwerkspfade) liegt
        // im Lauf davor schon ein Kopf (s:/ aus "https://"): Die Vereinigung mit dem Zitat-Fund beginnt dort. Der
        // Kandidat enthält in jedem Fall C:/Akte 2025/erika.beispiel@example.invalid/x.pdf ganz und endet vor dem '.
        List<Finding> pfad = new PathRecognizer().detect(AK7G);
        assertEquals(1, pfad.size());
        Finding f = pfad.get(0);
        assertEquals("s://intranet.invalid/?f='C:/Akte 2025/erika.beispiel@example.invalid/x.pdf", f.originalValue());
        assertEquals(9, f.start());
        assertEquals(83, f.end());
        assertTrue(f.originalValue().endsWith("C:/Akte 2025/erika.beispiel@example.invalid/x.pdf"));
        assertEquals('\'', AK7G.charAt(f.end()));
    }

    @Test
    void ak7gStandardScannerLiefertGenauEinenZusammengelegtenFund() {
        List<Finding> funde = scan(AK7G);
        assertEquals(1, funde.size());
        Finding f = funde.get(0);
        assertEquals(5, f.start());
        assertEquals(83, f.end());
        assertEquals(FindingType.PFAD, f.type());
        assertEquals(0.95, f.confidence());
        assertEquals(ConfidenceLevel.SICHER, f.level());
        assertEquals(Finding.SOURCE_MERGED, f.source());
        assertEquals('\'', AK7G.charAt(83));
        assertEquals("https://intranet.invalid/?f='C:/Akte 2025/erika.beispiel@example.invalid/x.pdf",
                f.originalValue());
    }

    @Test
    void ak7gPseudonymisierungExaktUndKeinBestandteilImKlartextRoundtripGleich() {
        String p = pseudonymisiert(AK7G);
        assertEquals("Link [[PFAD_1]]' ende", p);
        for (String teil : new String[] {"intranet.invalid", "Akte", "2025", "erika.beispiel@example.invalid",
                "x.pdf", "https"}) {
            assertFalse(p.contains(teil), "Bestandteil im Klartext: " + teil);
        }
        assertTrue(p.startsWith("Link "));
        assertTrue(p.endsWith(" ende"));
    }

    @Test
    void ak7gZweimalScannenUndUmgekehrteErkennerReihenfolgeLiefernGleicheListe() {
        List<Finding> a = scan(AK7G);
        List<Finding> b = scan(AK7G);
        assertEquals(a, b);
        TextScanner umgekehrt = new TextScanner(List.of(new PathRecognizer(), new UrlRecognizer(),
                new Ipv4Recognizer(), new EmailRecognizer()));
        assertEquals(a, umgekehrt.scan(AK7G));
    }

    // ------------------------------------------------------------------ AK-7 (e): angrenzend, keine Zusammenlegung

    @Test
    void ak7eUrlMitGerademAnfuehrungszeichenUndPfadSindZweiFundeOhneZusammenlegung() {
        String text = "https://intranet.invalid/?f=\"C:/Akte 2025/x.pdf\"";
        List<Finding> funde = scan(text);
        assertEquals(2, funde.size());
        assertEquals(FindingType.URL, funde.get(0).type());
        assertEquals("url", funde.get(0).source());
        assertEquals(0, funde.get(0).start());
        assertEquals(text.indexOf('"'), funde.get(0).end());
        assertEquals(FindingType.PFAD, funde.get(1).type());
        assertEquals("pfad-laufwerk", funde.get(1).source());
        assertEquals("C:/Akte 2025/x.pdf", funde.get(1).originalValue());
        assertEquals("[[URL_1]]\"[[PFAD_2]]\"", pseudonymisiert(text));
    }

    @Test
    void ak7eAngrenzendeFundeVerschiedenerErkennerBleibenGetrennt() {
        // IP und Laufwerkspfad ohne Zwischenraum: kein Zeichen gemeinsam, also zwei Funde.
        String text = bs("192.0.2.10C:¦x¦y");
        List<Finding> funde = scan(text);
        assertEquals(2, funde.size());
        assertEquals(FindingType.IP, funde.get(0).type());
        assertEquals("192.0.2.10", funde.get(0).originalValue());
        assertEquals(FindingType.PFAD, funde.get(1).type());
        assertEquals(bs("C:¦x¦y"), funde.get(1).originalValue());
        assertEquals(funde.get(0).end(), funde.get(1).start());
    }

    // ------------------------------------------------------------------ Teilüberlappung: ein zusammengelegter Fund

    @Test
    void teilueberlappungEmailUndPfadErgebenEinenFundUeberDenGanzenBereichTypEmail() {
        String text = bs("Kontakt erika.mustermann@example.invalidC:¦Ordner¦a.txt hier");
        String email = "erika.mustermann@example.invalidC";
        String pfad = bs("C:¦Ordner¦a.txt");
        assertEquals(List.of(email), werteVon(new EmailRecognizer().detect(text)));
        assertEquals(List.of(pfad), werteVon(new PathRecognizer().detect(text)));
        Finding f = einziger(text);
        assertEquals(text.indexOf(email), f.start());
        assertEquals(text.indexOf(pfad) + pfad.length(), f.end());
        assertEquals(email + pfad.substring(1), f.originalValue());
        assertEquals(FindingType.EMAIL, f.type()); // längerer Kandidat bei gleicher Konfidenz
        assertEquals(0.95, f.confidence());
        assertEquals(Finding.SOURCE_MERGED, f.source());
        assertEquals("Kontakt [[EMAIL_1]] hier", pseudonymisiert(text));
    }

    @Test
    void teilueberlappungPfadLaenger() {
        String text = bs("Kontakt a@b.invalidC:¦Ordner¦Unterordner¦datei.txt hier");
        String email = "a@b.invalidC";
        String pfad = bs("C:¦Ordner¦Unterordner¦datei.txt");
        assertEquals(List.of(email), werteVon(new EmailRecognizer().detect(text)));
        Finding f = einziger(text);
        assertEquals(FindingType.PFAD, f.type());
        assertEquals(text.indexOf(email), f.start());
        assertEquals(text.indexOf(pfad) + pfad.length(), f.end());
        assertEquals(Finding.SOURCE_MERGED, f.source());
        assertEquals("Kontakt [[PFAD_1]] hier", pseudonymisiert(text));
    }

    @Test
    void teilueberlappungUrlUndPfadImZitatOhneEmail() {
        String text = "Siehe https://intranet.invalid/?f='C:/Akte 2025/x.pdf' dort";
        Finding f = einziger(text);
        assertEquals(Finding.SOURCE_MERGED, f.source());
        assertEquals(text.indexOf("https"), f.start());
        assertEquals(text.indexOf("x.pdf") + "x.pdf".length(), f.end());
        assertEquals("Siehe [[PFAD_1]]' dort", pseudonymisiert(text));
    }

    @Test
    void teilueberlappungMitDoppeltenApostrophenInDerKetteAlleBestandteileGeschuetzt() {
        String text = "Link https://intranet.invalid/?f='C:/Akte 2025/erika.beispiel@example.invalid/x.pdf' und "
                + "https://zwei.invalid/?g='D:/Mein Ordner/y z.txt' ende";
        List<Finding> funde = scan(text);
        assertEquals(2, funde.size());
        for (Finding f : funde) {
            assertEquals(Finding.SOURCE_MERGED, f.source());
        }
        String p = pseudonymisiert(text);
        assertEquals("Link [[PFAD_1]]' und [[PFAD_2]]' ende", p);
    }

    // ------------------------------------------------------------------ Enthaltensein: äußerer Fund, unverändert

    @Test
    void enthaltenPfadInUrlLiefertDenUnveraendertenUrlFund() {
        String text = bs("https://example.invalid/?datei=C:¦Tools¦a.exe");
        Finding f = einziger(text);
        assertEquals(FindingType.URL, f.type());
        assertEquals("url", f.source());
        assertEquals(new UrlRecognizer().detect(text).get(0), f);
    }

    @Test
    void enthaltenEmailImPfadLiefertDenUnveraendertenPfadFund() {
        String text = bs("C:¦Benutzer¦erika.beispiel@example.invalid¦ablage");
        Finding f = einziger(text);
        assertEquals(FindingType.PFAD, f.type());
        assertEquals("pfad-laufwerk", f.source());
        assertEquals(new PathRecognizer().detect(text).get(0), f);
        assertEquals("[[PFAD_1]]", pseudonymisiert(text));
    }

    @Test
    void enthaltenIpImPfadLiefertDenUnveraendertenPfadFund() {
        String text = bs("C:¦Logs¦192.0.2.10¦a.txt");
        Finding f = einziger(text);
        assertEquals(FindingType.PFAD, f.type());
        assertEquals(new PathRecognizer().detect(text).get(0), f);
    }

    @Test
    void enthaltenEmailImPfadMitLeerzeichenInAnfuehrungszeichen() {
        String text = bs("Siehe \"C:¦Mein Ordner¦erika.mustermann@example.invalid¦a b.txt\" dort");
        Finding f = einziger(text);
        assertEquals(FindingType.PFAD, f.type());
        assertEquals("pfad-laufwerk", f.source());
        assertEquals(bs("C:¦Mein Ordner¦erika.mustermann@example.invalid¦a b.txt"), f.originalValue());
        assertEquals("Siehe \"[[PFAD_1]]\" dort", pseudonymisiert(text));
    }

    @Test
    void enthaltenUrlImPfadMitLeerzeichenInAnfuehrungszeichen() {
        String text = bs("\"C:¦Mein Ordner¦https://example.invalid/a¦b.txt\"");
        // Der Pfad im Zitat (mit Leerzeichen) umfasst die URL ganz: genau ein Fund, Typ PFAD, kein URL-Fund.
        Finding f = einziger(text);
        assertEquals(FindingType.PFAD, f.type());
        assertEquals("pfad-laufwerk", f.source());
        assertEquals(bs("C:¦Mein Ordner¦https://example.invalid/a¦b.txt"), f.originalValue());
        assertEquals(1, f.start());
        assertEquals(text.length() - 1, f.end());
        assertEquals("\"[[PFAD_1]]\"", pseudonymisiert(text));
    }

    @Test
    void einMailEineIpUndEinPfadImSelbenSatzWerdenJeEinzelnErkannt() {
        String text = bs("Mail erika.mustermann@example.invalid, Server 192.0.2.10, Tool \"C:¦Program Files¦a b.exe\".");
        List<Finding> funde = scan(text);
        assertEquals(3, funde.size());
        assertEquals(FindingType.EMAIL, funde.get(0).type());
        assertEquals(FindingType.IP, funde.get(1).type());
        assertEquals(FindingType.PFAD, funde.get(2).type());
        assertEquals(bs("C:¦Program Files¦a b.exe"), funde.get(2).originalValue());
    }

    // ------------------------------------------------------------------ Schutzgarantie (Eigenschaftstest)

    private static final String[] FRAGMENTE = {"https://intranet.invalid/?f=", "https://example.invalid/a/b",
            "erika.mustermann@example.invalid", "192.0.2.10", "C:\\Mein Ordner\\a.txt", "C:/Akte 2025/x.pdf",
            "C:\\", "?datei=", "x@y.invalid", "@", "invalidC", "\"", "'", "`", "\u201E", "\u201C", "\u00BB", "\u2018",
            " ", " ", " ", "\n", "und", ".", ",", "(", ")", "/", "\\", ":", "a", "1", "-"};

    @Test
    void schutzgarantieJedeVonEinemErkennerAbgedeckteStelleIstImScanErgebnisAbgedeckt() {
        long seed = 20261120L;
        Random rnd = new Random(seed);
        List<Recognizer> erkenner = List.of(new EmailRecognizer(), new Ipv4Recognizer(), new UrlRecognizer(),
                new PathRecognizer());
        TextScanner umgekehrt = new TextScanner(List.of(erkenner.get(3), erkenner.get(2), erkenner.get(1),
                erkenner.get(0)));
        int mitMehrerenQuellen = 0;
        for (int it = 0; it < 20_000; it++) {
            StringBuilder sb = new StringBuilder();
            int tokens = 1 + rnd.nextInt(14);
            for (int k = 0; k < tokens; k++) {
                sb.append(FRAGMENTE[rnd.nextInt(FRAGMENTE.length)]);
            }
            String text = sb.toString();
            boolean[] kandidaten = new boolean[text.length()];
            int quellen = 0;
            for (Recognizer r : erkenner) {
                List<Finding> k = r.detect(text);
                if (!k.isEmpty()) {
                    quellen++;
                }
                for (Finding f : k) {
                    for (int i = f.start(); i < f.end(); i++) {
                        kandidaten[i] = true;
                    }
                }
            }
            if (quellen > 1) {
                mitMehrerenQuellen++;
            }
            List<Finding> funde = SCANNER.scan(text);
            boolean[] ergebnis = new boolean[text.length()];
            int vorigesEnde = 0;
            for (Finding f : funde) {
                assertEquals(text.substring(f.start(), f.end()), f.originalValue(),
                        "Teilstring, Startwert " + seed + ", Iteration " + it);
                assertTrue(f.start() >= vorigesEnde, "Überlappung, Startwert " + seed + ", Iteration " + it);
                vorigesEnde = f.end();
                for (int i = f.start(); i < f.end(); i++) {
                    ergebnis[i] = true;
                }
            }
            for (int i = 0; i < text.length(); i++) {
                if (kandidaten[i] && !ergebnis[i]) {
                    fail("Schutzgarantie verletzt, Startwert " + seed + ", Iteration " + it);
                }
                if (ergebnis[i] && !kandidaten[i]) {
                    fail("Ergebnis über die Kandidaten hinaus, Startwert " + seed + ", Iteration " + it);
                }
            }
            assertEquals(funde, umgekehrt.scan(text), "Reihenfolge, Startwert " + seed + ", Iteration " + it);
        }
        assertTrue(mitMehrerenQuellen > 1_000, "zu wenige Texte mit mehreren Erkennern: " + mitMehrerenQuellen);
    }

    private static List<String> werteVon(List<Finding> funde) {
        List<String> werte = new ArrayList<>();
        for (Finding f : funde) {
            werte.add(f.originalValue());
        }
        return werte;
    }
}
