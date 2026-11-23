package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.ZWSP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.Recognizer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T-2 (kvnr-bsnr-erkennung): Das Fenster des Scanners ist auf 28 Zeichen vergroessert. Fuer LANR und IK darf sich
 * nichts aendern; jedes Ergebnis wird mit der eingefrorenen Kopie {@link KvStandTrenner} verglichen. Vorbereitung auf
 * die Kontextwoerter mit bis zu 27 Buchstaben (AK-2, AK-25).
 */
class KvFensterTest {

    private static final int MIN_LAUF = 22;
    private static final int MAX_LAUF = 30;
    private static final int MIN_FOLGE = 23;
    private static final int MAX_FOLGE = 28;
    private static final int MIN_LANGE_FOLGE = 24;
    private static final String FUELL = "x";
    private static final String[] ENDEN_LANR = {"lanr", "arztnummer"};
    private static final String[] ENDEN_IK = {"ik"};
    private static final Duration GRENZE = Duration.ofSeconds(2);

    private static void gleichWieStand(Recognizer neu, Recognizer alt, String text, String fall) {
        List<Finding> ist = neu.detect(text);
        List<Finding> soll = alt.detect(text);
        assertTrue(soll.equals(ist), "Abweichung vom eingefrorenen Stand: " + fall);
    }

    private static String wiederholt(String baustein, int anzahl) {
        return baustein.repeat(anzahl);
    }

    private static String gesperrt(String buchstaben, String luecke) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < buchstaben.length(); i++) {
            if (i > 0) {
                sb.append(luecke);
            }
            sb.append(buchstaben.charAt(i));
        }
        return sb.toString();
    }

    private static void laeufe(Recognizer neu, Recognizer alt, String[] enden, String nummer, String name) {
        for (String ende : enden) {
            for (int laenge = MIN_LAUF; laenge <= MAX_LAUF; laenge++) {
                int vorne = laenge - ende.length();
                if (vorne < 0) {
                    continue;
                }
                String lauf = wiederholt(FUELL, vorne) + ende;
                String fall = name + " Lauf " + laenge + " " + ende.length();
                gleichWieStand(neu, alt, lauf + " " + nummer, fall + " ohne");
                gleichWieStand(neu, alt, lauf + ": " + nummer, fall + " ohne Doppelpunkt");
                String mitZeichen = wiederholt(FUELL, vorne) + ZWSP + ende;
                gleichWieStand(neu, alt, mitZeichen + " " + nummer, fall + " unsichtbar");
                gleichWieStand(neu, alt, mitZeichen + ": " + nummer, fall + " unsichtbar Doppelpunkt");
            }
        }
    }

    @Test
    void lanrLaeufe22Bis30BuchstabenGleichWieStand() {
        laeufe(new LanrRecognizer(), KvStandTrenner.lanr(), ENDEN_LANR, LANR_GUELTIG, "LANR");
    }

    @Test
    void ikLaeufe22Bis30BuchstabenGleichWieStand() {
        laeufe(new IkRecognizer(), KvStandTrenner.ik(), ENDEN_IK, IK_GUELTIG, "IK");
    }

    private static void folgen(Recognizer neu, Recognizer alt, String ende, String nummer, String name) {
        String[] luecken = {" ", "  ", ". ", "-"};
        for (String luecke : luecken) {
            for (int laenge = MIN_FOLGE; laenge <= MAX_FOLGE; laenge++) {
                String buchstaben = wiederholt(FUELL, laenge - ende.length()) + ende;
                String folge = gesperrt(buchstaben, luecke);
                String fall = name + " Folge " + laenge + " Luecke " + luecke.length();
                gleichWieStand(neu, alt, folge + " " + nummer, fall);
                gleichWieStand(neu, alt, folge + ": " + nummer, fall + " Doppelpunkt");
                gleichWieStand(neu, alt, nummer + " " + folge, fall + " nachgestellt");
            }
        }
    }

    @Test
    void lanrSperrUndWeitFolgen23Bis28BuchstabenGleichWieStand() {
        folgen(new LanrRecognizer(), KvStandTrenner.lanr(), "lanr", LANR_GUELTIG, "LANR");
        folgen(new LanrRecognizer(), KvStandTrenner.lanr(), "arztnummer", LANR_GUELTIG, "LANR Arztnummer");
    }

    @Test
    void ikSperrUndWeitFolgen23Bis28BuchstabenGleichWieStand() {
        folgen(new IkRecognizer(), KvStandTrenner.ik(), "ik", IK_GUELTIG, "IK");
    }

    @Test
    void langesWortOhneKontextwortPasstInsFensterUndWirdKeinKontext() {
        // 27 Buchstaben (laengstes geplantes Kontextwort) und 28 Buchstaben: kein Eintrag von LANR oder IK
        for (int laenge = 26; laenge <= 28; laenge++) {
            String wort = wiederholt(FUELL, laenge);
            String lanr = wort + " " + LANR_GUELTIG;
            String ik = wort + " " + IK_GUELTIG;
            assertEquals(0, new LanrRecognizer().detect(lanr).size(), "LANR Wort " + laenge);
            assertEquals(0, new IkRecognizer().detect(ik).size(), "IK Wort " + laenge);
            gleichWieStand(new LanrRecognizer(), KvStandTrenner.lanr(), lanr, "LANR Wort " + laenge);
            gleichWieStand(new IkRecognizer(), KvStandTrenner.ik(), ik, "IK Wort " + laenge);
        }
    }

    @Test
    void kontextwortBleibtNachLangemVorlaufErkannt() {
        // 27 Buchstaben vor dem Wort, davon getrennt durch ein unsichtbares Zeichen: das Wort wird weiter erkannt
        String text = wiederholt(FUELL, 27) + ZWSP + "LANR " + LANR_GUELTIG;
        assertEquals(1, new LanrRecognizer().detect(text).size(), "LANR nach langem Vorlauf");
        gleichWieStand(new LanrRecognizer(), KvStandTrenner.lanr(), text, "LANR nach langem Vorlauf");
        String ik = wiederholt(FUELL, 27) + ZWSP + "IK " + IK_GUELTIG;
        assertEquals(1, new IkRecognizer().detect(ik).size(), "IK nach langem Vorlauf");
        gleichWieStand(new IkRecognizer(), KvStandTrenner.ik(), ik, "IK nach langem Vorlauf");
    }

    @Test
    void laufAusEinerMillionBuchstabenHatKeinenFund() {
        String text = wiederholt("a", 1_000_000);
        int lanr = assertTimeoutPreemptively(GRENZE, () -> new LanrRecognizer().detect(text).size(),
                "LANR Laufzeitgrenze 2 s ueberschritten");
        int ik = assertTimeoutPreemptively(GRENZE, () -> new IkRecognizer().detect(text).size(),
                "IK Laufzeitgrenze 2 s ueberschritten");
        assertEquals(0, lanr, "LANR Fundzahl");
        assertEquals(0, ik, "IK Fundzahl");
    }

    @Test
    void buchstabeUndUnsichtbaresZeichenWiederholtHatKeinenFund() {
        String text = wiederholt("a" + ZWSP, 500_000);
        int lanr = assertTimeoutPreemptively(GRENZE, () -> new LanrRecognizer().detect(text).size(),
                "LANR Laufzeitgrenze 2 s ueberschritten");
        int ik = assertTimeoutPreemptively(GRENZE, () -> new IkRecognizer().detect(text).size(),
                "IK Laufzeitgrenze 2 s ueberschritten");
        assertEquals(0, lanr, "LANR Fundzahl");
        assertEquals(0, ik, "IK Fundzahl");
    }

    // ------------------------------------------------------------------ Zustand nach einer langen Folge (T-3, c)

    private static void nachLangerFolge(Recognizer neu, Recognizer alt, String kontext, String nummer, String name) {
        String[] folgeLuecken = {" ", "  ", ". ", "-"};
        String[] enden = {", ", "\n", ",\n"};
        for (String luecke : folgeLuecken) {
            for (int laenge = MIN_LANGE_FOLGE; laenge <= MAX_FOLGE; laenge++) {
                String folge = gesperrt(wiederholt(FUELL, laenge), luecke);
                for (String ende : enden) {
                    String text = folge + ende + gesperrt(kontext, " ") + " " + nummer;
                    String fall = name + " Folge " + laenge + " Luecke " + luecke.length() + " Ende " + ende.length();
                    assertEquals(1, neu.detect(text).size(), "genau ein Fund nach langer Folge: " + fall);
                    gleichWieStand(neu, alt, text, fall);
                }
            }
        }
    }

    @Test
    void lanrGesperrtesKontextwortNachLangerFolgeFindetGenauEineNummer() {
        nachLangerFolge(new LanrRecognizer(), KvStandTrenner.lanr(), "LANR", LANR_GUELTIG, "LANR");
    }

    @Test
    void ikGesperrtesKontextwortNachLangerFolgeFindetGenauEineNummer() {
        nachLangerFolge(new IkRecognizer(), KvStandTrenner.ik(), "IKNR", IK_GUELTIG, "IK");
    }

    // ------------------------------------------------------------------ Umlaut-Faltung (T-13)

    /** Wie {@code gleichWieStand}, der Text geht vorher durch den Waechter fuer synthetische Teil-2-Texte. */
    private static void umlautGleich(Recognizer neu, Recognizer alt, String text, String fall) {
        BsnrSynthetischWaechter.assertBsnrSynthetisch(text);
        gleichWieStand(neu, alt, text, fall);
    }

    /** Die Faltung von U+00C4 zu U+00E4 (T-13) darf kein Ergebnis fuer LANR und IK aendern. */
    private static void umlautLaeufe(Recognizer neu, Recognizer alt, String[] enden, String nummer, String name) {
        char[] umlaute = {KvnrBsnrHilfen.AE_KLEIN, KvnrBsnrHilfen.AE_GROSS};
        for (char umlaut : umlaute) {
            for (String ende : enden) {
                for (int laenge = MIN_LAUF; laenge <= MAX_LAUF; laenge++) {
                    int vorne = laenge - ende.length() - 1;
                    if (vorne < 0) {
                        continue;
                    }
                    String fall = name + " Umlaut Lauf " + laenge + " " + ende.length();
                    String vorn = wiederholt(FUELL, vorne) + umlaut + ende;
                    String mitte = ende.substring(0, 1) + umlaut + ende.substring(1);
                    String hinten = wiederholt(FUELL, vorne) + ende + umlaut;
                    String getrennt = wiederholt(FUELL, vorne) + umlaut + ZWSP + ende;
                    for (String lauf : new String[] {vorn, mitte, hinten, getrennt, umlaut + ende}) {
                        umlautGleich(neu, alt, lauf + " " + nummer, fall + " ohne");
                        umlautGleich(neu, alt, lauf + ": " + nummer, fall + " Doppelpunkt");
                        umlautGleich(neu, alt, nummer + " (" + lauf + ")", fall + " nachgestellt");
                    }
                }
            }
        }
    }

    @Test
    void lanrLaeufeMitUmlautGleichWieStand() {
        umlautLaeufe(new LanrRecognizer(), KvStandTrenner.lanr(), ENDEN_LANR, LANR_GUELTIG, "LANR");
    }

    @Test
    void ikLaeufeMitUmlautGleichWieStand() {
        umlautLaeufe(new IkRecognizer(), KvStandTrenner.ik(), ENDEN_IK, IK_GUELTIG, "IK");
    }

    @Test
    void lanrUndIkMitUmlautImKontextwortOderAlsEinzelbuchstabeGleichWieStand() {
        String[] woerter = {"L" + KvnrBsnrHilfen.AE_GROSS + "NR", "L" + KvnrBsnrHilfen.AE_KLEIN + "NR",
            "I" + KvnrBsnrHilfen.AE_GROSS, "Arztnummer" + KvnrBsnrHilfen.AE_GROSS,
            KvnrBsnrHilfen.AE_GROSS + "rztnummer", "Arzt" + KvnrBsnrHilfen.AE_GROSS + " Nr."};
        for (String wort : woerter) {
            umlautGleich(new LanrRecognizer(), KvStandTrenner.lanr(), wort + " " + LANR_GUELTIG, "LANR Wort");
            umlautGleich(new IkRecognizer(), KvStandTrenner.ik(), wort + " " + IK_GUELTIG, "IK Wort");
        }
        char[] umlaute = {KvnrBsnrHilfen.AE_KLEIN, KvnrBsnrHilfen.AE_GROSS};
        for (char umlaut : umlaute) {
            String lanr = gesperrt("LANR", " ") + " " + umlaut + " " + LANR_GUELTIG;
            String sperr = "L " + umlaut + " N R " + LANR_GUELTIG;
            String ik = "I K " + umlaut + " " + IK_GUELTIG;
            umlautGleich(new LanrRecognizer(), KvStandTrenner.lanr(), lanr, "LANR Sperr");
            umlautGleich(new LanrRecognizer(), KvStandTrenner.lanr(), sperr, "LANR Sperr Umlaut");
            umlautGleich(new IkRecognizer(), KvStandTrenner.ik(), ik, "IK Sperr Umlaut");
            assertEquals(0, new LanrRecognizer().detect(sperr).size(), "Umlaut ersetzt keinen Buchstaben");
        }
    }

    // ------------------------------------------------------------------ Wortlaengen (T-3, a)

    @Test
    void alleKontextUndVorwoerterAllerArtenPassenInsFenster() {
        for (KvArt art : KvArt.values()) {
            for (String wort : art.kontextwoerter()) {
                assertTrue(!wort.isEmpty() && wort.length() <= KvNumberScanner.MAX_KONTEXTWORT,
                        "Kontextwort ausserhalb 1 bis MAX_KONTEXTWORT bei Art " + art);
            }
            for (String wort : art.vorwoerter()) {
                assertTrue(!wort.isEmpty() && wort.length() <= KvNumberScanner.MAX_KONTEXTWORT,
                        "Vorwort ausserhalb 1 bis MAX_KONTEXTWORT bei Art " + art);
            }
            assertTrue(KvNumberScanner.detect(art, "x").isEmpty(), "Scanner baut sich nicht fuer Art " + art);
        }
        assertTrue(KvNumberScanner.MAX_KONTEXTWORT == 27, "MAX_KONTEXTWORT weicht vom Plan (27) ab");
    }

    @Test
    void pruefeWortLaenge_wirftNurFuerWorteUeberMaxKontextwort_mitFesterMeldungOhneDasWort() {
        int max = KvNumberScanner.MAX_KONTEXTWORT;
        KvNumberScanner.pruefeWortLaenge(wiederholt(FUELL, max));
        KvNumberScanner.pruefeWortLaenge(FUELL);
        KvNumberScanner.pruefeWortLaenge("");
        String zuLang = wiederholt("q", max + 1);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> KvNumberScanner.pruefeWortLaenge(zuLang));
        String meldung = e.getMessage();
        assertTrue(meldung != null && !meldung.isEmpty(), "Meldung fehlt");
        assertTrue(!meldung.contains(zuLang) && !meldung.contains("q"), "Die Meldung enthaelt das Wort");
        assertTrue(meldung.chars().noneMatch(Character::isDigit), "Die Meldung enthaelt Ziffern");
        // dieselbe feste Meldung unabhaengig von Wort und Laenge
        IllegalStateException laenger = assertThrows(IllegalStateException.class,
                () -> KvNumberScanner.pruefeWortLaenge(wiederholt("w", max + 10)));
        assertEquals(meldung, laenger.getMessage(), "Die Meldung haengt vom Wort ab");
    }
}
