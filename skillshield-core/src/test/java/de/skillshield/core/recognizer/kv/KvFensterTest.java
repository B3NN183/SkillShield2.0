package de.skillshield.core.recognizer.kv;

import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;
import static de.skillshield.core.recognizer.kv.KvTestHilfen.ZWSP;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
