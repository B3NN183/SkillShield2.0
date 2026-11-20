package de.skillshield.core.recognizer.general;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.Recognizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Eigenschaftstests zu FILE_URI und zur Abgrenzung (T-16) mit festem Startwert; Fehlermeldungen nennen nur Startwert
 * und Iteration, keine Texte. Namen sind synthetisch, Nicht-ASCII-Zeichen stehen nur als Escapes, Backslashes ueber
 * {@code bs(...)}.
 *
 * <p>(1) Zufaellige {@code file:}-Ausdruecke aller Schreibweisen (Standard-Scanner und Erkenner): jeder Ausdruck ist
 * genau ein Fund der Form FILE_URI ueber den ganzen Ausdruck, kein URL-, IP- oder E-Mail-Fund daneben. (2) Jede
 * allein stehende URL beliebigen Schemas ist genau ein unveraenderter URL-Fund, auch mit Pfaden und {@code file:} im
 * Query. (3) Pipeline Scanner, Pseudonymizer, Reidentifier mit allen Pfadformen, {@code file:}, URLs, E-Mails und IPs:
 * jeder erzeugte Pfad liegt ganz in einem Fund, kein Name im pseudonymisierten Text, Roundtrip zeichengleich. (4)
 * Zusammengeklebte URLs und {@code file:}-Pfade mit unausgeglichenen Klammern: Schutzgarantie und Vergleich mit dem
 * Stand T-15 (ohne die Form FILE_URI).
 */
class PathRecognizerFileUriEigenschaftTest {

    private static final long SEED = 20261127L;
    /** Ein Backslash. */
    private static final String B = bs("¦");
    private static final PathRecognizer R = new PathRecognizer();
    private static final TextScanner SCANNER = TextScanner.standard();

    /** Die zehn Formen des Stands T-15 (alle ausser FILE_URI). */
    private static final PathForm[] ZEHN_FORMEN = Arrays.stream(PathForm.values())
            .filter(f -> f != PathForm.FILE_URI).toArray(PathForm[]::new);

    private static final String[] BASIS = {"Tools", "Akten", "Projekt", "Dokumente", "BeispielTool", "Daten",
            "Gr\u00F6\u00DFe", "\u00DCbersicht", "Ablage", "Berichte"};
    private static final String[] DATEI = {"akte.pdf", "tool.exe", "notiz.txt", "konfig.yml", "a.tar.gz", "x"};
    private static final String[] RECHNER = {"srv-beispiel", "srv01.praxis.example", "srv.beispiel.invalid",
            "localhost"};
    private static final String[] FUELL = {"Bitte", "pruefe", "das", "Ergebnis", "und", "melde", "kurz", "Danach",
            "gilt", "Hinweis", "oben", "folgt", "nichts", "weiter", "und/oder", "km/h", "19/11/2026", "n/a"};

    // ------------------------------------------------------------------ Hilfen

    private static String name(Random rnd) {
        return BASIS[rnd.nextInt(BASIS.length)] + rnd.nextInt(100_000);
    }

    private static String datei(Random rnd) {
        return name(rnd) + "." + DATEI[rnd.nextInt(DATEI.length)] + rnd.nextInt(100);
    }

    private static String segmente(Random rnd, List<String> namen, int anzahl, String trenner, boolean mitLeerzeichen) {
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < anzahl; s++) {
            String n = s == anzahl - 1 && rnd.nextBoolean() ? datei(rnd) : name(rnd);
            if (mitLeerzeichen && rnd.nextInt(3) == 0) {
                n = "Mein Ordner" + rnd.nextInt(100_000);
            }
            namen.add(n);
            sb.append(s > 0 ? trenner : "").append(n);
        }
        return sb.toString();
    }

    private static String laufwerk(Random rnd) {
        return String.valueOf((char) ('A' + rnd.nextInt(26)));
    }

    private static String rechner(Random rnd, List<String> namen) {
        int art = rnd.nextInt(6);
        String r;
        if (art == 4) {
            r = "192.0.2." + (1 + rnd.nextInt(250));
        } else if (art == 5) {
            String nutzer = "nutzer" + rnd.nextInt(100_000);
            String geheim = "geheim" + rnd.nextInt(100_000);
            namen.add(nutzer);
            namen.add(geheim);
            r = nutzer + ":" + geheim + "@srv.example";
        } else if (art == 3) {
            String m = "erika.beispiel" + rnd.nextInt(100_000);
            namen.add(m);
            r = m + "@example.invalid";
        } else {
            r = RECHNER[rnd.nextInt(RECHNER.length)] + (art == 0 ? "" : String.valueOf(rnd.nextInt(1000)));
        }
        namen.add(r);
        return r;
    }

    private static String schema(Random rnd) {
        String[] s = {"file", "file", "FILE", "File", "fIlE"};
        return s[rnd.nextInt(s.length)];
    }

    /**
     * Ein {@code file:}-Ausdruck in einer von vierzehn Schreibweisen (Kopf mit und ohne Trenner, Laufwerk, Rechner,
     * UNC, relativ, Home, Umgebung, Query, Fragment, kodierte Leerzeichen); die Namen (Rechner, Ordner, Dateien)
     * kommen in {@code namen}. Mit {@code zitat} duerfen Namen Leerzeichen enthalten.
     */
    private static String fileAusdruck(Random rnd, List<String> namen, boolean zitat) {
        String k = schema(rnd) + ":";
        String s = "/";
        int anzahl = 1 + rnd.nextInt(4);
        String ende = "";
        if (!zitat && rnd.nextInt(5) == 0) {
            ende = "?x=" + rnd.nextInt(10) + "&y=" + rnd.nextInt(10);
        } else if (!zitat && rnd.nextInt(5) == 0) {
            ende = "#abschnitt" + rnd.nextInt(10);
        }
        switch (rnd.nextInt(14)) {
            case 0:
                return k + "///" + laufwerk(rnd) + ":/" + segmente(rnd, namen, anzahl, s, zitat) + ende;
            case 1:
                return k + "///home/" + segmente(rnd, namen, anzahl + 1, s, zitat) + ende;
            case 2:
                return k + "//" + rechner(rnd, namen) + "/" + segmente(rnd, namen, anzahl, s, zitat) + ende;
            case 3:
                return k + "/" + segmente(rnd, namen, anzahl + 1, s, zitat) + ende;
            case 4:
                return k + "/" + laufwerk(rnd).toLowerCase() + ":/" + segmente(rnd, namen, anzahl, s, zitat);
            case 5:
                return k + laufwerk(rnd) + ":" + B + segmente(rnd, namen, anzahl, B, zitat);
            case 6:
                return k + laufwerk(rnd) + ":/" + segmente(rnd, namen, anzahl, s, zitat) + ende;
            case 7:
                return k + (rnd.nextBoolean() ? "./" : ".." + B) + segmente(rnd, namen, anzahl, s, zitat);
            case 8:
                return k + B + B + rechner(rnd, namen) + B + segmente(rnd, namen, anzahl, B, zitat);
            case 9:
                return k + "///" + B + B + rechner(rnd, namen) + B + segmente(rnd, namen, anzahl, B, zitat);
            case 10:
                return k + (rnd.nextBoolean() ? "%USERPROFILE%/" : "$HOME/") + segmente(rnd, namen, anzahl, s, zitat);
            case 11:
                return k + "~/" + segmente(rnd, namen, anzahl, s, zitat);
            case 12:
                return k + "//localhost/C:/" + segmente(rnd, namen, anzahl, s, zitat) + ende;
            default:
                String n1 = name(rnd);
                String n2 = datei(rnd).replace('.', '_');
                namen.add(n1);
                namen.add(n2);
                return k + "///C:/" + n1 + "%20Ordner/" + n2 + "%20M%C3%BCller.pdf";
        }
    }

    private static boolean deckt(List<Finding> funde, int start, int ende) {
        for (Finding f : funde) {
            if (f.start() <= start && f.end() >= ende) {
                return true;
            }
        }
        return false;
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

    private static void pruefeFunde(String text, List<Finding> funde, String wo) {
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring" + wo);
            assertTrue(f.start() >= vorigesEnde, "sortiert und ueberlappungsfrei" + wo);
            assertFalse(f.start() > 0 && Character.isLowSurrogate(text.charAt(f.start()))
                    && Character.isHighSurrogate(text.charAt(f.start() - 1)), "Start im Surrogatpaar" + wo);
            assertFalse(f.end() < text.length() && Character.isLowSurrogate(text.charAt(f.end()))
                    && Character.isHighSurrogate(text.charAt(f.end() - 1)), "Ende im Surrogatpaar" + wo);
            vorigesEnde = f.end();
        }
    }

    // ------------------------------------------------------------------ (1) file:-Ausdruecke

    @Test
    void zufaelligeFileAusdruecke() {
        Random rnd = new Random(SEED);
        int gesamt = 0;
        int mitHostArt = 0;
        for (int it = 0; it < 40_000; it++) {
            String wo = ", Startwert " + SEED + ", Iteration " + it;
            StringBuilder sb = new StringBuilder();
            List<int[]> bereiche = new ArrayList<>();
            List<String> namen = new ArrayList<>();
            int anzahl = 1 + rnd.nextInt(3);
            boolean zitatVorhanden = false;
            for (int k = 0; k < anzahl; k++) {
                sb.append(FUELL[rnd.nextInt(FUELL.length)]).append(' ');
                // hoechstens ein Zitat je Text: das schliessende Zeichen eines Zitats wird auch als oeffnendes probiert
                // (Plan 2.4.3, Folgen), zwei Zitate in einem Text koennen sich zu einem groesseren Fund verbinden
                boolean zitat = !zitatVorhanden && rnd.nextInt(4) == 0;
                zitatVorhanden |= zitat;
                String ausdruck = fileAusdruck(rnd, namen, zitat);
                String[] huelle = zitat ? new String[] {"\"", "\""}
                        : new String[][] {{"", ""}, {"", "."}, {"", ","}, {"(", ")"}, {"", "!"}, {"", ";"}, {"x=", ""},
                            {"--f=", ""}, {"", "?"}}[rnd.nextInt(9)];
                sb.append(huelle[0]);
                int start = sb.length();
                sb.append(ausdruck);
                bereiche.add(new int[] {start, sb.length()});
                sb.append(huelle[1]).append(rnd.nextBoolean() ? " " : "\n");
                gesamt++;
            }
            String text = sb.toString();
            List<Finding> scan = SCANNER.scan(text);
            List<Finding> erkenner = R.detect(text);
            pruefeFunde(text, scan, wo);
            pruefeFunde(text, erkenner, wo);
            assertEquals(bereiche.size(), scan.size(), "Fundzahl Scanner" + wo);
            assertEquals(bereiche.size(), erkenner.size(), "Fundzahl Erkenner" + wo);
            for (int k = 0; k < bereiche.size(); k++) {
                int[] b = bereiche.get(k);
                Finding f = scan.get(k);
                assertEquals(b[0], f.start(), "Start" + wo);
                assertEquals(b[1], f.end(), "Ende" + wo);
                assertEquals(FindingType.PFAD, f.type(), "Typ" + wo);
                assertEquals("pfad-file-uri", f.source(), "Quelle" + wo);
                assertEquals(0.95, f.confidence(), "Konfidenz" + wo);
                assertEquals(f, erkenner.get(k), "Erkenner gleich Scanner" + wo);
                mitHostArt += text.substring(b[0], b[1]).contains("@") ? 1 : 0;
            }
            PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, scan);
            ReidentificationResult back = new Reidentifier().restore(p.text(), p.mapping());
            assertEquals(text, back.text(), "Roundtrip" + wo);
            assertTrue(back.unresolved().isEmpty(), "Meldung" + wo);
            assertFalse(p.text().toLowerCase().contains("file:"), "file: im Klartext" + wo);
            for (String n : namen) {
                assertFalse(p.text().contains(n), "Name im Klartext" + wo);
            }
        }
        assertTrue(gesamt > 60_000, "zu wenige Ausdruecke: " + gesamt);
        assertTrue(mitHostArt > 2_000, "zu wenige Ausdruecke mit Zugangsdaten oder E-Mail: " + mitHostArt);
    }

    // ------------------------------------------------------------------ (2) URLs bleiben genau ein URL-Fund

    private static final String[] SCHEMEN = {"http", "https", "ftp", "sftp", "smb", "ldap", "jdbc:postgresql", "wss",
            "svn+ssh", "mongodb+srv", "xfile", "profile", "a.b-c", "HTTPS"};
    private static final String[] URL_HOSTS = {"example.invalid", "srv-beispiel.example", "192.0.2.10",
            "db.example:5432", "nutzer:geheim@host.example", "nutzer@host.example:21",
            "erika.beispiel@example.invalid"};
    private static final String[] URL_QUERY = {"", "", "?f=C:" + B + "Tools" + B + "a.exe", "?f=file:///C:/x/y",
            "?f=/opt/beispiel/a", "?datei=" + B + B + "srv" + B + "freigabe", "?a=1&b=2#t", "#file:///C:/x",
            "?f=~/a/b", "?f=%APPDATA%" + B + "x", "?f=FILE:///C:/Mein%20Ordner/x.pdf", "?f=./a/b"};

    private static String url(Random rnd) {
        StringBuilder sb = new StringBuilder();
        sb.append(SCHEMEN[rnd.nextInt(SCHEMEN.length)]).append("://").append(URL_HOSTS[rnd.nextInt(URL_HOSTS.length)]);
        int segs = rnd.nextInt(4);
        for (int i = 0; i < segs; i++) {
            sb.append('/').append(name(rnd));
        }
        sb.append(URL_QUERY[rnd.nextInt(URL_QUERY.length)]);
        return sb.toString();
    }

    @Test
    void jedeAlleinStehendeUrlIstGenauEinUnveraenderterUrlFundAuchMitPfadenImQuery() {
        Random rnd = new Random(SEED + 1);
        String[][] huellen = {{"", ""}, {"Siehe ", " dort"}, {"Siehe ", "."}, {"(", ")"}, {"x ", ", y"}, {"", "\n"},
            {"Link: ", ""}};
        int mitFileImQuery = 0;
        for (int it = 0; it < 40_000; it++) {
            String wo = ", Startwert " + (SEED + 1) + ", Iteration " + it;
            String url = url(rnd);
            String[] h = huellen[rnd.nextInt(huellen.length)];
            String text = h[0] + url + h[1];
            List<Finding> erwartet = new UrlRecognizer().detect(text);
            assertEquals(1, erwartet.size(), "Kandidat" + wo);
            List<Finding> scan = SCANNER.scan(text);
            pruefeFunde(text, scan, wo);
            assertEquals(1, scan.size(), "genau ein Fund" + wo);
            assertEquals(erwartet.get(0), scan.get(0), "unveraenderter URL-Kandidat" + wo);
            assertEquals(FindingType.URL, scan.get(0).type(), "Typ" + wo);
            assertEquals("url", scan.get(0).source(), "Quelle" + wo);
            mitFileImQuery += url.toLowerCase().contains("file:") ? 1 : 0;
        }
        assertTrue(mitFileImQuery > 2_000, "zu wenige URLs mit file: im Text: " + mitFileImQuery);
    }

    // ------------------------------------------------------------------ (3) Pipeline mit allen Pfadformen

    private record Gen(String text, List<int[]> pfade, List<int[]> urls, List<int[]> weitere, List<String> namen) {
    }

    private static String pfadAllerFormen(Random rnd, List<String> namen, boolean zitat) {
        String bs = B;
        switch (rnd.nextInt(12)) {
            case 0:
                return (char) ('A' + rnd.nextInt(26)) + ":" + bs + segmente(rnd, namen, 1 + rnd.nextInt(3), bs, zitat);
            case 1:
                return bs("¦¦") + rechner(rnd, namen) + bs + segmente(rnd, namen, 1 + rnd.nextInt(3), bs, zitat);
            case 2:
                return "/" + segmente(rnd, namen, 2 + rnd.nextInt(3), "/", zitat);
            case 3:
                return "~/" + segmente(rnd, namen, 1 + rnd.nextInt(3), "/", zitat);
            case 4:
                return (rnd.nextBoolean() ? "./" : bs("..¦")) + segmente(rnd, namen, 1 + rnd.nextInt(3), "/", zitat);
            case 5:
                return bs + segmente(rnd, namen, 2 + rnd.nextInt(3), bs, zitat);
            case 6:
                return (rnd.nextBoolean() ? "Users" + bs : "home/")
                        + segmente(rnd, namen, 2 + rnd.nextInt(2), "/", zitat);
            case 7:
                return (rnd.nextBoolean() ? "%APPDATA%" + bs : "$HOME/") + segmente(rnd, namen, 1 + rnd.nextInt(3), "/",
                        zitat);
            case 8:
                String h = RECHNER[rnd.nextInt(RECHNER.length)] + rnd.nextInt(1000);
                namen.add(h);
                return h + ":/" + segmente(rnd, namen, 1 + rnd.nextInt(3), "/", zitat);
            default:
                return fileAusdruck(rnd, namen, zitat);
        }
    }

    private static Gen erzeuge(Random rnd) {
        StringBuilder sb = new StringBuilder();
        List<int[]> pfade = new ArrayList<>();
        List<int[]> urls = new ArrayList<>();
        List<int[]> weitere = new ArrayList<>();
        List<String> namen = new ArrayList<>();
        int anzahl = 2 + rnd.nextInt(4);
        for (int p = 0; p < anzahl; p++) {
            sb.append(FUELL[rnd.nextInt(FUELL.length)]).append(' ');
            int art = rnd.nextInt(8);
            if (art == 0) {
                String u = url(rnd);
                int start = sb.length();
                sb.append(u);
                urls.add(new int[] {start, sb.length()});
                namen.add(u.substring(u.indexOf("://") + 3).split("[/?#]")[0]);
            } else if (art == 1) {
                String m = "erika.beispiel" + rnd.nextInt(100_000) + "@example.invalid";
                int start = sb.length();
                sb.append(m);
                weitere.add(new int[] {start, sb.length()});
                namen.add(m);
            } else if (art == 2) {
                String ip = "192.0.2." + (1 + rnd.nextInt(250));
                int start = sb.length();
                sb.append(ip);
                weitere.add(new int[] {start, sb.length()});
            } else {
                boolean zitat = rnd.nextInt(4) == 0;
                String[][] paare = {{"\"", "\""}, {"'", "'"}, {"`", "`"}, {"\u201E", "\u201C"}, {"\u00BB", "\u00AB"}};
                String[] paar = paare[rnd.nextInt(paare.length)];
                String[] vor = {"", "", " ", "x=", "(", "[", "--config=", ">"};
                String[] nach = {"", "", ".", ",", ")", ";", "!", "?"};
                String[] huelle = zitat ? paar : new String[] {vor[rnd.nextInt(vor.length)], ""};
                String pfad = pfadAllerFormen(rnd, namen, zitat);
                sb.append(huelle[0]);
                int start = sb.length();
                sb.append(pfad);
                pfade.add(new int[] {start, sb.length()});
                sb.append(zitat ? huelle[1] : nach[rnd.nextInt(nach.length)]);
            }
            sb.append(rnd.nextBoolean() ? " " : "\n");
        }
        return new Gen(sb.toString(), pfade, urls, weitere, namen);
    }

    @Test
    void pipelineMitAllenPfadformenFileUrlsEmailsUndIps() {
        Random rnd = new Random(SEED + 2);
        Pseudonymizer pseudo = new Pseudonymizer();
        Reidentifier rueck = new Reidentifier();
        long fileFunde = 0;
        long urlsGeprueft = 0;
        int iterationen = 25_000;
        for (int it = 0; it < iterationen; it++) {
            Gen g = erzeuge(rnd);
            String text = g.text();
            String wo = ", Startwert " + (SEED + 2) + ", Iteration " + it;
            List<Finding> funde = SCANNER.scan(text);
            pruefeFunde(text, funde, wo);
            boolean ohneAnfuehrung = true;
            for (int i = 0; i < text.length(); i++) {
                ohneAnfuehrung &= PathChars.quoteKind(text.charAt(i)) < 0;
            }
            for (Finding f : funde) {
                fileFunde += f.source().equals("pfad-file-uri") ? 1 : 0;
            }
            for (int[] pfad : g.pfade()) {
                assertTrue(deckt(funde, pfad[0], pfad[1]), "Pfad nicht ganz in einem Fund" + wo);
            }
            for (int[] w : g.weitere()) {
                assertTrue(deckt(funde, w[0], w[1]), "E-Mail oder IP nicht ganz in einem Fund" + wo);
            }
            for (int[] url : g.urls()) {
                int anzahl = 0;
                for (Finding f : funde) {
                    if (f.start() < url[1] && url[0] < f.end()) {
                        anzahl++;
                        if (ohneAnfuehrung) {
                            assertEquals(FindingType.URL, f.type(), "URL-Typ" + wo);
                            assertEquals(url[0], f.start(), "URL-Start" + wo);
                            assertEquals(url[1], f.end(), "URL-Ende" + wo);
                        } else {
                            // Anfuehrungszeichen im Text koennen Funde verbinden (Plan 2.4.3): die URL liegt ganz darin
                            assertTrue(f.start() <= url[0] && f.end() >= url[1], "URL ganz in einem Fund" + wo);
                        }
                    }
                }
                assertEquals(1, anzahl, "genau ein Fund je URL" + wo);
                urlsGeprueft += ohneAnfuehrung ? 1 : 0;
            }
            PseudonymizationResult p = pseudo.pseudonymize(text, funde);
            ReidentificationResult back = rueck.restore(p.text(), p.mapping());
            assertEquals(text, back.text(), "Roundtrip" + wo);
            assertTrue(back.unresolved().isEmpty(), "Meldung" + wo);
            for (String n : g.namen()) {
                assertFalse(p.text().contains(n), "Name im Klartext" + wo);
            }
        }
        System.out.println("T-16 Pipeline (Startwert " + (SEED + 2) + "): Texte " + iterationen
                + ", Funde der Quelle pfad-file-uri " + fileFunde + ", geprueft URLs " + urlsGeprueft);
        assertTrue(fileFunde > 10_000, "zu wenige file:-Funde: " + fileFunde);
        assertTrue(urlsGeprueft > 5_000, "zu wenige URLs: " + urlsGeprueft);
    }

    // ------------------------------------------------------------------ (4) zusammengeklebt, Klammern, Stand T-15

    private static final String[] URL_TEILE = {"https://a.invalid/x", "https://a.invalid/x)", "https://a.invalid/(x",
        "https://a.invalid/x]", "https://a.invalid/[x", "xfile://a.invalid/x", "ftp://nutzer:geheim@a.invalid/x)"};
    private static final String[] FILE_TEILE = {"file:///C:/y", "file:///C:/y)", "(file:///C:/y", "file:///C:/y]",
        "file://srv.example/y/z", "file:/y/z)", "file:C:/y", "FILE:///C:/y)"};
    private static final String[] KLEBER = {"", ",", ")", "(", "]", "[", "|", "`", ";", "=", "'"};

    private static boolean mitZusammenlegung(List<Finding> funde) {
        return funde.stream().anyMatch(x -> x.source().equals(Finding.SOURCE_MERGED));
    }

    @Test
    void zusammengeklebteUrlsUndFileAusdruecheSchuetzenAllesUndVerschlechternDenStandT15Nicht() {
        Recognizer standT15 = t -> R.detect(t, ZEHN_FORMEN);
        TextScanner scannerT15 = new TextScanner(List.of(new EmailRecognizer(), new Ipv4Recognizer(),
                new UrlRecognizer(), standT15));
        int faelle = 0;
        int anders = 0;
        int zusammengelegtT16 = 0;
        int zusammengelegtT15 = 0;
        int urlGroesser = 0;
        List<Recognizer> erkenner = List.of(new EmailRecognizer(), new Ipv4Recognizer(), new UrlRecognizer(), R);
        for (String u : URL_TEILE) {
            for (String f : FILE_TEILE) {
                for (String k : KLEBER) {
                    for (String text : new String[] {u + k + f, f + k + u, "Siehe " + u + k + f + " Ende",
                        "Siehe " + f + k + u + " Ende"}) {
                        faelle++;
                        String wo = ", Fall " + faelle;
                        List<Finding> neu = SCANNER.scan(text);
                        List<Finding> alt = scannerT15.scan(text);
                        pruefeFunde(text, neu, wo);
                        boolean[] a = abdeckung(text.length(), neu);
                        boolean[] b = abdeckung(text.length(), alt);
                        for (Recognizer r : erkenner) {
                            boolean[] c = abdeckung(text.length(), r.detect(text));
                            for (int i = 0; i < text.length(); i++) {
                                assertTrue(!c[i] || a[i], "Schutzgarantie verletzt" + wo);
                            }
                        }
                        for (int i = 0; i < text.length(); i++) {
                            assertTrue(!b[i] || a[i], "Position gegenueber Stand T-15 nicht mehr abgedeckt" + wo);
                        }
                        anders += neu.equals(alt) ? 0 : 1;
                        zusammengelegtT16 += mitZusammenlegung(neu) ? 1 : 0;
                        zusammengelegtT15 += mitZusammenlegung(alt) ? 1 : 0;
                        for (Finding x : neu) {
                            if (x.type() == FindingType.URL) {
                                for (Finding y : alt) {
                                    if (y.type() == FindingType.URL && y.start() == x.start() && x.end() > y.end()) {
                                        urlGroesser++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println("T-16 geklebte URL/file: Faelle " + faelle + ", Ergebnis anders als Stand T-15 " + anders
                + ", mit zusammengelegtem Fund T-16 " + zusammengelegtT16 + " (T-15: " + zusammengelegtT15 + ")");
        assertTrue(zusammengelegtT16 <= zusammengelegtT15, "T-16 erzeugt mehr zusammengelegte Funde als T-15");
        assertEquals(0, urlGroesser, "URL-Fund durch T-16 verlaengert");
    }
}
