package de.skillshield.core.recognizer;

import static de.skillshield.core.TestTexte.bs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;

/**
 * Abgrenzung von URL, Pfad, IP und E-Mail im Standard-Scanner (T-16, AK-24 (a) bis (e), AK-5, AK-7 (d), AK-8). Die
 * Kandidaten der Erkenner werden einzeln berechnet; der Scan-Fund ist bei vollstaendigem Enthaltensein der
 * UNVERAENDERTE Kandidat des umschliessenden Erkenners (Quelle {@code url} bzw. {@code pfad-...}, nicht
 * {@code zusammengelegt}; dessen Konfidenz, auch wenn der enthaltene Kandidat eine hoehere haette). Testdaten sind
 * synthetisch ({@code *.invalid}, {@code erika.beispiel}, 192.0.2.0/24); Backslashes ueber {@code bs(...)},
 * Nicht-ASCII-Zeichen nur als Escapes.
 */
class TextScannerAbgrenzungTest {

    private static final TextScanner SCANNER = TextScanner.standard();

    // ------------------------------------------------------------------ Hilfen

    private static List<Finding> scan(String text) {
        List<Finding> funde = SCANNER.scan(text);
        int vorigesEnde = 0;
        for (Finding f : funde) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring");
            assertTrue(f.start() >= vorigesEnde, "sortiert, ueberlappungsfrei");
            vorigesEnde = f.end();
        }
        return funde;
    }

    private static Finding einziger(String text) {
        List<Finding> funde = scan(text);
        assertEquals(1, funde.size(), "genau ein Fund, Text: " + text);
        return funde.get(0);
    }

    /** Alle Kandidaten der vier Standard-Erkenner einzeln. */
    private static List<Finding> kandidaten(String text) {
        List<Finding> alle = new ArrayList<>();
        alle.addAll(new EmailRecognizer().detect(text));
        alle.addAll(new Ipv4Recognizer().detect(text));
        alle.addAll(new UrlRecognizer().detect(text));
        alle.addAll(new PathRecognizer().detect(text));
        return alle;
    }

    private static boolean enthaelt(Finding gross, Finding klein) {
        return gross.start() <= klein.start() && gross.end() >= klein.end();
    }

    private static Finding kandidat(String text, FindingType typ, String wert) {
        for (Finding f : kandidaten(text)) {
            if (f.type() == typ && f.originalValue().equals(wert)) {
                return f;
            }
        }
        throw new AssertionError("kein Kandidat vom Typ " + typ + " mit dem erwarteten Wert, Text: " + text);
    }

    private static boolean hatKandidat(String text, FindingType typ) {
        return kandidaten(text).stream().anyMatch(f -> f.type() == typ);
    }

    private static String pseudonymisiert(String text) {
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, scan(text));
        ReidentificationResult back = new Reidentifier().restore(p.text(), p.mapping());
        assertEquals(text, back.text(), "Roundtrip");
        assertTrue(back.unresolved().isEmpty(), "keine Meldung");
        return p.text();
    }

    // ================================================================== AK-24 (a): URL, kein Pfad fuer /a/b

    @Test
    void ak24aUrlIstEinUrlFundUndKeinPfadFundAuchNichtFuerSlashAB() {
        String text = "https://example.invalid/a/b";
        Finding f = einziger(text);
        assertEquals(FindingType.URL, f.type());
        assertEquals(text, f.originalValue());
        assertEquals("url", f.source());
        assertEquals(kandidat(text, FindingType.URL, text), f, "der unveraenderte URL-Kandidat");
        assertTrue(scan(text).stream().noneMatch(x -> x.type() == FindingType.PFAD));
    }

    @Test
    void ak5UrlMitPfadUndQueryIstGenauEinFundKeinPfadFundFuerSlashPfad() {
        String text = "Siehe https://example.invalid/pfad?x=1 f\u00FCr Details";
        Finding f = einziger(text);
        assertEquals(FindingType.URL, f.type());
        assertEquals("https://example.invalid/pfad?x=1", f.originalValue());
        assertEquals("Siehe [[URL_1]] f\u00FCr Details", pseudonymisiert(text));
    }

    // ================================================================== AK-24 (b): Pfad im Query

    @Test
    void ak24bPfadImQueryIstEinUnveraenderterUrlFund() {
        String text = bs("https://example.invalid/?datei=C:¦Tools¦a.exe");
        Finding f = einziger(text);
        assertEquals(FindingType.URL, f.type());
        assertEquals(text, f.originalValue());
        assertEquals("url", f.source());
        assertEquals(kandidat(text, FindingType.URL, text), f);
        assertTrue(hatKandidat(text, FindingType.PFAD), "der Pfad-Kandidat existiert und liegt im URL-Fund");
        assertFalse(pseudonymisiert(text).contains("Tools"));
    }

    @Test
    void ak24bFileUriImQueryIstEinUnveraenderterUrlFund() {
        for (String text : List.of("https://example.invalid/?f=file:///C:/x",
                "https://example.invalid/?f=FILE:///C:/x/y",
                "https://example.invalid/a/b?f=file://srv-beispiel/freigabe/x#t", "https://example.invalid/#file:/a/b",
                "ftp://host.example/x?u=file:C:/a/b")) {
            Finding f = einziger(text);
            assertEquals(FindingType.URL, f.type(), text);
            assertEquals(text, f.originalValue(), text);
            assertEquals("url", f.source(), text);
            assertEquals(0.95, f.confidence(), text);
            assertEquals(kandidat(text, FindingType.URL, text), f, text);
        }
    }

    @Test
    void ak24bPfadMitPfadImQueryUndVielenPfadformenBleibtEinUrlFund() {
        for (String text : List.of(
                bs("https://example.invalid/?f=C:¦Tools¦a.exe"),
                "https://example.invalid/?f=/opt/beispiel/a",
                bs("https://example.invalid/?f=¦¦srv¦freigabe¦x"),
                "https://example.invalid/?f=~/a/b",
                "https://example.invalid/?f=./a/b",
                bs("https://example.invalid/?f=%APPDATA%¦x"),
                "https://example.invalid/?f=host.example:/export/home")) {
            Finding f = einziger(text);
            assertEquals(FindingType.URL, f.type(), text);
            assertEquals(text, f.originalValue(), text);
            assertEquals("url", f.source(), text);
        }
    }

    // ================================================================== AK-24 (c): IP im UNC-Pfad

    @Test
    void ak24cIpImUncPfadIstEinPfadFundOhneIpFund() {
        String text = bs("¦¦192.0.2.10¦freigabe¦ordner");
        Finding f = einziger(text);
        assertEquals(FindingType.PFAD, f.type());
        assertEquals(text, f.originalValue());
        assertEquals("pfad-unc", f.source());
        assertEquals(0.95, f.confidence());
        assertEquals(kandidat(text, FindingType.PFAD, text), f, "der unveraenderte Pfad-Kandidat");
        Finding ip = kandidat(text, FindingType.IP, "192.0.2.10");
        assertEquals(0.85, ip.confidence(), "der IP-Kandidat hat die niedrigere Konfidenz und gewinnt nicht");
        assertTrue(enthaelt(f, ip));
        assertFalse(pseudonymisiert(text).contains("192.0.2.10"));
    }

    // ================================================================== AK-24 (d): E-Mail im Pfad

    @Test
    void ak24dEmailImPfadIstEinPfadFundOhneEmailFund() {
        String text = bs("C:¦Benutzer¦erika.beispiel@example.invalid¦ablage");
        Finding f = einziger(text);
        assertEquals(FindingType.PFAD, f.type());
        assertEquals(text, f.originalValue());
        assertEquals("pfad-laufwerk", f.source());
        assertEquals(kandidat(text, FindingType.PFAD, text), f, "der unveraenderte Pfad-Kandidat");
        Finding mail = kandidat(text, FindingType.EMAIL, "erika.beispiel@example.invalid");
        assertTrue(enthaelt(f, mail));
        assertFalse(pseudonymisiert(text).contains("erika.beispiel"));
    }

    // ================================================================== AK-24 (e): file:

    @Test
    void ak24eFileUriIstGenauEinPfadFundUeberDenGanzenAusdruck() {
        String text = "file:///C:/Tools/BeispielTool/tool.exe";
        Finding f = einziger(text);
        assertEquals(FindingType.PFAD, f.type());
        assertEquals(text, f.originalValue());
        assertEquals(0, f.start());
        assertEquals(text.length(), f.end());
        assertEquals("pfad-file-uri", f.source());
        assertEquals(0.95, f.confidence());
        assertEquals(kandidat(text, FindingType.PFAD, text), f, "der unveraenderte Pfad-Kandidat");
        assertTrue(kandidaten(text).stream().noneMatch(x -> x.type() == FindingType.URL), "kein URL-Kandidat");
        // weder "file:" plus Pfad noch /C: noch C: als Teilfunde, und "fil" ist nicht mehr sichtbar
        assertEquals("[[PFAD_1]]", pseudonymisiert(text));
    }

    @Test
    void ak24eFileUriImSatzMitIpUndEmailAusserhalbDesPfads() {
        String text =
                "Mail erika.beispiel@example.invalid, Server 192.0.2.10, Tool file:///C:/Tools/BeispielTool/tool.exe.";
        List<Finding> funde = scan(text);
        assertEquals(3, funde.size());
        assertEquals(FindingType.EMAIL, funde.get(0).type());
        assertEquals("erika.beispiel@example.invalid", funde.get(0).originalValue());
        assertEquals(FindingType.IP, funde.get(1).type());
        assertEquals("192.0.2.10", funde.get(1).originalValue());
        assertEquals(FindingType.PFAD, funde.get(2).type());
        assertEquals("file:///C:/Tools/BeispielTool/tool.exe", funde.get(2).originalValue());
        assertEquals("pfad-file-uri", funde.get(2).source());
        assertEquals("Mail [[EMAIL_1]], Server [[IP_2]], Tool [[PFAD_3]].", pseudonymisiert(text));
    }

    @Test
    void ak24eFileUriMitZugangsdatenOderIpIstEinPfadFundOhneTeilfund() {
        for (String text : List.of("file://nutzer:geheim@srv.example/x", "file://192.0.2.10/freigabe/a",
                "file://erika.beispiel@example.invalid/ablage/x.pdf", "file://nutzer:geheim@192.0.2.10/freigabe/a")) {
            Finding f = einziger(text);
            assertEquals(FindingType.PFAD, f.type(), text);
            assertEquals(text, f.originalValue(), text);
            assertEquals("pfad-file-uri", f.source(), text);
            assertEquals(0.95, f.confidence(), text);
            assertEquals("[[PFAD_1]]", pseudonymisiert(text), text);
        }
        // der enthaltene E-Mail- bzw. IP-Kandidat existiert und geht im Pfad-Fund auf
        assertTrue(hatKandidat("file://nutzer:geheim@srv.example/x", FindingType.EMAIL));
        assertTrue(hatKandidat("file://192.0.2.10/freigabe/a", FindingType.IP));
    }

    @Test
    void ak24eAlleSchreibweisenVonFileSindEinFundOhneUrlFund() {
        for (String text : List.of("file:///C:/Tools/a.exe", "FILE:///C:/Tools/a.exe", "File://srv-beispiel/freigabe/a",
                "file:/C:/x", "file:C:/x", bs("file:C:¦x"), "file://localhost/C:/x", bs("file:¦¦srv¦freigabe¦x"),
                bs("file:///¦¦srv¦x"), "file:./a", bs("file:..¦x"), "file:%USERPROFILE%/x", bs("file:%APPDATA%¦x"),
                "file:///C:/Mein%20Ordner/Akte%20M%C3%BCller.pdf", "file:/home/erika/a.txt", "file:///home/erika/a.txt",
                "file:///C:/a.html#abschnitt", "file:///C:/a.html?x=1", "x:file://srv.example/x",
                "siehe:file:///C:/x/y")) {
            List<Finding> funde = scan(text);
            assertEquals(1, funde.size(), text);
            assertEquals(FindingType.PFAD, funde.get(0).type(), text);
            assertEquals("pfad-file-uri", funde.get(0).source(), text);
            assertTrue(funde.get(0).originalValue().toLowerCase().startsWith("file:"), text);
            assertTrue(text.endsWith(funde.get(0).originalValue()), text);
        }
    }

    @Test
    void ak24eFileUriImZitatMitLeerzeichenIstEinFund() {
        String text = "Datei \"file:///C:/Mein Ordner/a.txt\" offen";
        Finding f = einziger(text);
        assertEquals("file:///C:/Mein Ordner/a.txt", f.originalValue());
        assertEquals("pfad-file-uri", f.source());
        assertEquals("Datei \"[[PFAD_1]]\" offen", pseudonymisiert(text));
    }

    // ================================================================== Pfad und URL nebeneinander

    @Test
    void urlUndFileUriDurchLeerraumGetrenntSindZweiFundeInTextreihenfolge() {
        String text = "https://example.invalid/a/b file:///C:/x/y";
        List<Finding> funde = scan(text);
        assertEquals(2, funde.size());
        assertEquals(FindingType.URL, funde.get(0).type());
        assertEquals("https://example.invalid/a/b", funde.get(0).originalValue());
        assertEquals(FindingType.PFAD, funde.get(1).type());
        assertEquals("file:///C:/x/y", funde.get(1).originalValue());
        assertEquals("[[URL_1]] [[PFAD_2]]", pseudonymisiert(text));
    }

    @Test
    void ohneLeerraumGehtDerKleinereKandidatImUmschliessendenFundAufAk24() {
        // Pfad vor URL: der Pfad-Lauf reicht ueber die URL (Komma ist kein Endzeichen), der PFAD-Fund umfasst die URL
        // (Typ PFAD, unveraenderter Pfad-Kandidat, Konfidenz 0.95); URL vor Pfad: der URL-Rumpf reicht bis zum
        // Leerraum, der URL-Fund umfasst den Pfad. Kein Teil bleibt im Klartext (AK-7 (a), AK-24).
        String pfadZuerst = "file:///C:/a.txt,https://example.invalid/x";
        Finding f1 = einziger(pfadZuerst);
        assertEquals(FindingType.PFAD, f1.type());
        assertEquals(pfadZuerst, f1.originalValue());
        assertEquals("pfad-file-uri", f1.source());
        String urlZuerst = "https://example.invalid/x,file:///C:/a.txt";
        Finding f2 = einziger(urlZuerst);
        assertEquals(FindingType.URL, f2.type());
        assertEquals(urlZuerst, f2.originalValue());
        assertEquals("url", f2.source());
        assertEquals("[[PFAD_1]]", pseudonymisiert(pfadZuerst));
        assertEquals("[[URL_1]]", pseudonymisiert(urlZuerst));
    }

    @Test
    void zusammengeklebteUrlsMitUnausgeglichenenKlammernBleibenEinFundOhneLeck() {
        // Security-Hinweis T-13/T-14/T-15 (a): Der Rumpf der URL reicht bis zum Leerraum, auch ueber ein ")" und "]"
        // hinweg; mit T-16 ist das Ist-Verhalten dasselbe (file: liegt im Rumpf), nichts bleibt im Klartext.
        String[][] faelle = {{"https://a.invalid/x)file:///C:/y", "https://a.invalid/x)file:///C:/y"},
            {"https://a.invalid/x]file:///C:/y", "https://a.invalid/x]file:///C:/y"},
            {"https://a.invalid/(x)file:///C:/y)", "https://a.invalid/(x)file:///C:/y"},
            {"https://a.invalid/x)https://b.invalid/y)", "https://a.invalid/x)https://b.invalid/y"}};
        for (String[] fall : faelle) {
            Finding f = einziger(fall[0]);
            assertEquals(FindingType.URL, f.type(), fall[0]);
            assertEquals(fall[1], f.originalValue(), fall[0]);
            assertEquals("url", f.source(), fall[0]);
        }
        // Pfad zuerst: der Pfad-Fund umfasst die URL, die schliessende Klammer wird gekuerzt (ueberzaehlig)
        Finding g = einziger("file:///C:/y)https://a.invalid/x)");
        assertEquals("file:///C:/y)https://a.invalid/x", g.originalValue());
        assertEquals(FindingType.PFAD, g.type());
        assertEquals("pfad-file-uri", g.source());
    }

    // ================================================================== AK-8, Determinismus, Reihenfolge

    @Test
    void ak8TextOhneSensibleAngabenLiefertLeereListe() {
        assertEquals(List.of(), SCANNER.scan("Das Wetter ist heute sch\u00F6n."));
        assertEquals(List.of(), SCANNER.scan("Das Profil: file: ist leer, auch file:/// und profile: nicht."));
    }

    @Test
    void ak8DreiFundeInTextreihenfolgeUndZweimalScannenGleicheListe() {
        String mail = "erika.beispiel@example.invalid";
        String ip = "192.0.2.10";
        FindingType[] mipTypen = {FindingType.EMAIL, FindingType.IP, FindingType.PFAD};
        FindingType[] pimTypen = {FindingType.PFAD, FindingType.IP, FindingType.EMAIL};
        Object[][] faelle = {
            {bs("Mail erika.beispiel@example.invalid, Server 192.0.2.10, Tool C:¦Tools¦a.exe"), mipTypen,
                new String[] {mail, ip, bs("C:¦Tools¦a.exe")}},
            {"Mail erika.beispiel@example.invalid, Server 192.0.2.10, Tool file:///C:/Tools/a.exe", mipTypen,
                new String[] {mail, ip, "file:///C:/Tools/a.exe"}},
            {"Tool file:///C:/Tools/a.exe, Server 192.0.2.10, Mail erika.beispiel@example.invalid", pimTypen,
                new String[] {"file:///C:/Tools/a.exe", ip, mail}}};
        for (Object[] fall : faelle) {
            String text = (String) fall[0];
            FindingType[] typen = (FindingType[]) fall[1];
            String[] werte = (String[]) fall[2];
            List<Finding> a = scan(text);
            assertEquals(3, a.size(), text);
            for (int i = 0; i < a.size(); i++) {
                assertEquals(typen[i], a.get(i).type(), "Typ " + i + ", Text: " + text);
                assertEquals(werte[i], a.get(i).originalValue(), "Wert " + i + ", Text: " + text);
            }
            for (int i = 1; i < a.size(); i++) {
                assertTrue(a.get(i - 1).end() <= a.get(i).start(), "Textreihenfolge");
            }
            assertEquals(a, scan(text), "zweimal scannen: gleiche Liste");
            assertEquals(a, TextScanner.standard().scan(text), "neuer Scanner: gleiche Liste");
        }
    }

    @Test
    void ak7gGleicheListeBeiWiederholung() {
        String ak7g = "Link https://intranet.invalid/?f='C:/Akte 2025/erika.beispiel@example.invalid/x.pdf' ende";
        assertEquals(scan(ak7g), scan(ak7g));
        assertEquals(1, scan(ak7g).size());
    }

    @Test
    void ak7dMitEchtenErkennernUmschliessenderKandidatGewinntUnveraendert() {
        // AK-7 (d): vollstaendiges Enthaltensein, der umschliessende Kandidat gewinnt unveraendert
        String text =
                "Siehe file://erika.beispiel@example.invalid/x/y und https://example.invalid/?f=file:///C:/a/b dort";
        List<Finding> funde = scan(text);
        assertEquals(2, funde.size());
        assertEquals(kandidat(text, FindingType.PFAD, "file://erika.beispiel@example.invalid/x/y"), funde.get(0));
        assertEquals(kandidat(text, FindingType.URL, "https://example.invalid/?f=file:///C:/a/b"), funde.get(1));
    }

    // ================================================================== Schemata, Ist-Verhalten

    @Test
    void urlMitSchemaDasAufFileEndetBleibtUrlFundUndEnthaeltDenFileUriKandidaten() {
        // xfile:// und profile:// sind eigene URL-Schemata: ein URL-Fund (der FILE_URI-Kandidat ab file: liegt darin)
        for (String text : List.of("xfile://srv.example/x/y", "profile:///C:/x/y", "Profile://srv.example/x")) {
            Finding f = einziger(text);
            assertEquals(FindingType.URL, f.type(), text);
            assertEquals(text, f.originalValue(), text);
            assertEquals("url", f.source(), text);
        }
        // nur ein Schraegstrich: kein URL-Schema (kein "://"), Host-Fund ab dem Laufanfang wie bis T-15
        Finding h = einziger("profile:/x/y");
        assertEquals(FindingType.PFAD, h.type());
        assertEquals("pfad-host", h.source());
        assertEquals("profile:/x/y", h.originalValue());
    }

    @Test
    void jdbcUndSchemaPraefixMitFileBleibtPfadFundNurFuerDasFileSchema() {
        // "jdbc:file://..." ist laut UrlRecognizer kein URL-Fund (Schema file); der Pfad-Fund beginnt am f
        Finding f = einziger("jdbc:file://srv.example/x/y");
        assertEquals(FindingType.PFAD, f.type());
        assertEquals("file://srv.example/x/y", f.originalValue());
        assertEquals("pfad-file-uri", f.source());
    }

    // ================================================================== Bekannte Grenzen im Standard-Scanner

    @Test
    void bekannteGrenzeUnsichtbaresZeichenInFileGibtEinenUrlFundMitRestSchemaUndLaesstFiSichtbar() {
        // U+200B in "file": kein FILE_URI-Kopf; der URL-Erkenner nimmt "le" als Schema (Fund ab "le:///"), "fi" und
        // U+200B bleiben sichtbar. Unveraendert gegenueber T-15 (dort war es genauso).
        String text = "fi\u200Ble:///C:/x/y";
        Finding f = einziger(text);
        assertEquals(FindingType.URL, f.type());
        assertEquals("le:///C:/x/y", f.originalValue());
        assertTrue(pseudonymisiert(text).startsWith("fi\u200B"));
    }

    @Test
    void bekannteGrenzeVollbreiterDoppelpunktLeerzeichenUndKodierterDoppelpunktLassenFileSichtbar() {
        for (String text : List.of("file\uFF1A///C:/x/y", "file :///C:/x/y", "file%3A///C:/x/y")) {
            Finding f = einziger(text);
            assertEquals(FindingType.PFAD, f.type(), text);
            assertEquals("C:/x/y", f.originalValue(), text);
            assertEquals("pfad-laufwerk", f.source(), text);
            assertTrue(pseudonymisiert(text).startsWith("file"), text);
        }
    }

    @Test
    void bekannteGrenzeSehrLangerHostHinterFileIstVollstaendigGeschuetzt() {
        String text = "file://" + "h".repeat(200_000) + "/freigabe/x";
        Finding f = einziger(text);
        assertEquals(0, f.start());
        assertEquals(text.length(), f.end());
        assertEquals("pfad-file-uri", f.source());
    }
}
