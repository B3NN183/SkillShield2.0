package de.skillshield.core.recognizer.general;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Tests zu Plan Nachtrag 3b (T-13): Startgrenze fuer UNIX und HOME sind alle 13 Anfuehrungszeichen-Arten aus T-11,
 * auch unpaarig oder ueber das Zeilenende hinaus. Pruefung am Erkenner und am Standard-Scanner mit
 * {@code Pseudonymizer}. Zeichen stehen nur als Escapes ({@code \u201E} usw.); Pfade sind synthetisch. Benennung:
 * {@code plan...} (so im Nachtrag festgelegt), {@code bekannteFolge...} (Fehltreffer mit Konfidenz).
 */
class PathRecognizerStartgrenzeTest {

    private static final PathRecognizer R = new PathRecognizer();
    private static final TextScanner SCANNER = TextScanner.standard();

    /** Die 13 Anfuehrungszeichen aus T-11 (gerade, typografisch, Backtick), als Escapes. */
    private static final String[] ANFUEHRUNG = {"\"", "\u201C", "\u201D", "\u201E", "\u00AB", "\u00BB", "'", "\u2018",
            "\u2019", "\u201A", "\u2039", "\u203A", "`"};

    private static List<String> werte(String text) {
        List<Finding> r = R.detect(text);
        int vorigesEnde = 0;
        for (Finding f : r) {
            assertEquals(text.substring(f.start(), f.end()), f.originalValue(), "Teilstring");
            assertEquals(FindingType.PFAD, f.type());
            assertTrue(f.start() >= vorigesEnde, "sortiert und ueberlappungsfrei");
            vorigesEnde = f.end();
        }
        return r.stream().map(Finding::originalValue).collect(Collectors.toList());
    }

    private static Finding fund(String text, String wert, String quelle, double konfidenz) {
        assertEquals(List.of(wert), werte(text), "Erkenner, Zeichen vor dem Pfad: " + (int) text.charAt(0));
        Finding f = R.detect(text).get(0);
        assertEquals(quelle, f.source(), "Quelle");
        assertEquals(konfidenz, f.confidence(), "Konfidenz");
        // Standard-Scanner: derselbe eine Fund
        List<Finding> s = SCANNER.scan(text);
        assertEquals(List.of(wert), s.stream().map(Finding::originalValue).collect(Collectors.toList()), "Scanner");
        return f;
    }

    private static void keinFund(String text) {
        assertEquals(List.of(), werte(text), "Erkenner: " + text);
        assertEquals(List.of(), SCANNER.scan(text), "Scanner: " + text);
    }

    private static String pseudonymisiert(String text) {
        PseudonymizationResult p = new Pseudonymizer().pseudonymize(text, SCANNER.scan(text));
        ReidentificationResult back = new Reidentifier().restore(p.text(), p.mapping());
        assertEquals(text, back.text(), "Roundtrip");
        assertTrue(back.unresolved().isEmpty(), "keine Meldung");
        return p.text();
    }

    // ================================================================== Plan 3b: Fund hinter jedem der 13 Zeichen

    @Test
    void planUnpaariges201EVorUnixPfadIstEinFund() {
        String q = "\u201E";
        fund(q + "/home/erika/a.pdf", "/home/erika/a.pdf", "pfad-unix", 0.90);
        assertEquals(q + "[[PFAD_1]]", pseudonymisiert(q + "/home/erika/a.pdf"));
    }

    @Test
    void planTypografischesZeichenUeberDasZeilenendeHinausIstEinFund() {
        String text = "\u201E/home/erika/a.pdf\nweiter\u201C";
        fund(text, "/home/erika/a.pdf", "pfad-unix", 0.90);
        assertEquals("\u201E[[PFAD_1]]\nweiter\u201C", pseudonymisiert(text));
    }

    @Test
    void planOeffnendesGuillemetMitSpitzerKlammerBrichtDasPaarAbDerPfadIstTrotzdemEinFund() {
        String text = "\u00BB/home/erika/a.pdf <Anhang>\u00AB";
        fund(text, "/home/erika/a.pdf", "pfad-unix", 0.90);
        assertEquals("\u00BB[[PFAD_1]] <Anhang>\u00AB", pseudonymisiert(text));
    }

    @Test
    void planHomePfadHinterTypografischemZeichenIstEinFund() {
        fund("\u201E~/akte.pdf", "~/akte.pdf", "pfad-home", 0.90);
        fund("\u201E~erika/akte.pdf", "~erika/akte.pdf", "pfad-home", 0.80);
        assertEquals("\u201E[[PFAD_1]]", pseudonymisiert("\u201E~/akte.pdf"));
    }

    @Test
    void planRechtesEinfachesAnfuehrungszeichenAlsApostrophVorDemPfadIstEinFund() {
        String text = "\u2019/opt/x/y";
        fund(text, "/opt/x/y", "pfad-unix", 0.90);
        assertEquals("\u2019[[PFAD_1]]", pseudonymisiert(text));
    }

    @Test
    void planJedesDerDreizehnZeichenIstStartgrenzeFuerUnixUndHome() {
        for (String q : ANFUEHRUNG) {
            String wo = " Zeichen " + (int) q.charAt(0);
            fund(q + "/opt/x/y", "/opt/x/y", "pfad-unix", 0.90);
            fund(q + "/etc/hosts", "/etc/hosts", "pfad-unix", 0.80);
            fund(q + "~/akte.pdf", "~/akte.pdf", "pfad-home", 0.90);
            fund(q + "~erika/akte.pdf", "~erika/akte.pdf", "pfad-home", 0.80);
            fund("Datei " + q + "/opt/x/y ok", "/opt/x/y", "pfad-unix", 0.90);
            assertEquals(q + "[[PFAD_1]]", pseudonymisiert(q + "/opt/x/y"), wo);
            assertEquals(q + "[[PFAD_1]]", pseudonymisiert(q + "~/akte.pdf"), wo);
        }
    }

    @Test
    void planNurEinSegmentHinterDemZeichenBleibtKeinFund() {
        for (String q : ANFUEHRUNG) {
            keinFund(q + "/opt");
            keinFund(q + "~/");
            keinFund(q + "~erika");
        }
    }

    // ================================================================== Gewoehnlicher Text bleibt ohne Fund

    @Test
    void planGewoehnlicherTextMitTypografischemZeichenVorSchraegstrichBleibtOhneFund() {
        for (String q : ANFUEHRUNG) {
            keinFund("\u00C4rzte" + q + "/innen");
            keinFund("und" + q + "/oder");
            keinFund("Zoll" + q + "/cm");
            keinFund("Patient" + q + "/in");
        }
        keinFund("\u201EJa\u201C/\u201ENein\u201C");
        keinFund("\u00BBja\u00AB/\u00BBnein\u00AB");
    }

    // ================================================================== Bekannte Folgen (Fehltreffer), mit Konfidenz

    @Test
    void bekannteFolgeAufzaehlungMitDreiAlternativenInTypografischenZeichenIstFundMitNullAcht() {
        // Zwei Segmente hinter "/" genuegen; das Zeichen davor ist Startgrenze: Fehltreffer, kein Leck.
        fund("\u201EJa\u201C/\u201ENein\u201C/\u201EVielleicht\u201C", "/\u201ENein\u201C/\u201EVielleicht\u201C",
                "pfad-unix", 0.80);
        fund("\u00BBja\u00AB/\u00BBnein\u00AB/\u00BBweit\u00AB", "/\u00BBnein\u00AB/\u00BBweit\u00AB", "pfad-unix", 0.80);
    }

    @Test
    void bekannteFolgeWortMitTypografischemZeichenUndZweiAlternativenIstFundMitNullAcht() {
        fund("Wert\u201C/Einheit/Faktor", "/Einheit/Faktor", "pfad-unix", 0.80);
        fund("und\u201C/oder/beides", "/oder/beides", "pfad-unix", 0.80);
        fund("Soll\u2019/Ist/Plan", "/Ist/Plan", "pfad-unix", 0.80);
    }

    @Test
    void bekannteFolgeMassreihenMitZollUndMinutenzeichenSindFundMitNullAcht() {
        fund("24\u201D/27\u201D/32\u201D", "/27\u201D/32\u201D", "pfad-unix", 0.80);
        // das letzte "'" ist Endzeichen und gehoert nicht zum Fund
        fund("5'/10'/15'", "/10'/15", "pfad-unix", 0.80);
        fund("3\u2019/5\u2019/10\u2019", "/5\u2019/10\u2019", "pfad-unix", 0.80);
    }

    @Test
    void bekannteFolgeZitatMitDreierAlternativeDahinterIstFundMitNullAcht() {
        fund("\u201Eoffen\u201C/erledigt/storniert", "/erledigt/storniert", "pfad-unix", 0.80);
    }

    @Test
    void bekannteFolgeTypografischesSchlusszeichenAmFundendeGehoertZumFund() {
        // Im Zitat endet der zweite Pfad nicht vor dem Schlusszeichen U+201C: Fehltreffer, kein Leck.
        String text = "\u201E/home/erika/a > /tmp/b\u201C";
        assertEquals(List.of("/home/erika/a", "/tmp/b\u201C"), werte(text));
        List<Finding> r = R.detect(text);
        assertEquals(0.90, r.get(0).confidence());
        assertEquals(0.80, r.get(1).confidence());
    }
}
