package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.skillshield.core.KvnrFensterfernPruefung.Ergebnis;
import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Selbsttests der Nachpruefung {@link KvnrFensterfernPruefung} (Feature kvnr-bsnr-erkennung, T-7): Ein fensterfern
 * zulaessiger Fall (K-1, K-2, K-3 mit KVNR-Kontextwort weiter als 120 Zeichen davor im selben Absatz) wird als solcher
 * gezaehlt; jeder echte Verstoss (Typwechsel eines Nicht-KV-Altfundes, gesenkte Konfidenz, Typwechsel zu einem anderen
 * KV-Typ, verlorene Abdeckung, zu niedrige Konfidenz bei K-1, Leerzeile zwischen Kontextwort und Fund, Kontextwort nur
 * dahinter, K-2 gegen einen Typ, der vor KVNR steht, Abstand ueber zwei Zeichen) bleibt ein echter Verstoss. Die
 * Funde sind synthetisch (nur Buchstaben, keine Ziffern); der Text entscheidet nur ueber die Kontextwort-Referenz.
 * Meldungen nennen nur Zahlen.
 */
class KvnrFensterfernPruefungTest {

    private static final String NUMMER = "abcdefghi";
    private static final String FUELLUNG = "x".repeat(150);
    private static final String KONTEXT = "KVNR: ";

    private static Finding fund(FindingType typ, String text, int start, int ende, double konfidenz) {
        return new Finding(typ, text.substring(start, ende), start, ende, konfidenz, "test");
    }

    /** Kontextwort, 150 Zeichen Fuellung, Zeilenumbruch, LANR-Wort und die Nummer: das Kontextwort ist fensterfern. */
    private static String text(String trenner) {
        return KONTEXT + FUELLUNG + trenner + "LANR " + NUMMER + " Ende";
    }

    private static int start(String text) {
        return text.indexOf(NUMMER);
    }

    private static Ergebnis pruefe(String text, List<Finding> alt, List<Finding> neu) {
        return KvnrFensterfernPruefung.pruefe(text, alt, neu);
    }

    // ------------------------------------------------------------------ fensterfern zulaessig

    @Test
    void fensterfernK3_hoehereKonfidenzImGleichenBereich_kontextwortImAbsatz() {
        String text = text("\n");
        int s = start(text);
        Finding alt = fund(FindingType.LANR, text, s, s + NUMMER.length(), 0.40);
        Finding neu = fund(FindingType.KVNR, text, s, s + NUMMER.length(), 0.80);
        Ergebnis e = pruefe(text, List.of(alt), List.of(neu));
        assertEquals(1, e.fensterfernK3(), "fensterfern K-3; " + e.kurz());
        assertEquals(0, e.echteVerstoesse(), "echte Verstoesse; " + e.kurz());
        assertEquals(1, e.fensterfern(), "Summe; " + e.kurz());
    }

    @Test
    void fensterfernK2_gleicheKonfidenzImGleichenBereich_kvnrStehtVorLanr() {
        String text = text("\n");
        int s = start(text);
        Finding alt = fund(FindingType.LANR, text, s, s + NUMMER.length(), 0.40);
        Finding neu = fund(FindingType.KVNR, text, s, s + NUMMER.length(), 0.40);
        Ergebnis e = pruefe(text, List.of(alt), List.of(neu));
        assertEquals(1, e.fensterfernK2(), "fensterfern K-2; " + e.kurz());
        assertEquals(0, e.echteVerstoesse(), "echte Verstoesse; " + e.kurz());
    }

    @Test
    void fensterfernK1_kvnrUmschliesstMitBuchstabeDenAltenFund() {
        String text = KONTEXT + FUELLUNG + "\nLANR Z" + NUMMER + " Ende";
        int s = start(text);
        Finding alt = fund(FindingType.LANR, text, s, s + NUMMER.length(), 0.80);
        Finding neu = fund(FindingType.KVNR, text, s - 1, s + NUMMER.length(), 0.80);
        Ergebnis e = pruefe(text, List.of(alt), List.of(neu));
        assertEquals(1, e.fensterfernK1(), "fensterfern K-1; " + e.kurz());
        assertEquals(0, e.echteVerstoesse(), "echte Verstoesse; " + e.kurz());
    }

    @Test
    void fensterfernK1_buchstabeUndEinGruppentrennerVorDemAltenFund() {
        String text = KONTEXT + FUELLUNG + "\nLANR Z " + NUMMER + " Ende";
        int s = start(text);
        Finding alt = fund(FindingType.LANR, text, s, s + NUMMER.length(), 0.80);
        Finding neu = fund(FindingType.KVNR, text, s - 2, s + NUMMER.length(), 0.80);
        Ergebnis e = pruefe(text, List.of(alt), List.of(neu));
        assertEquals(1, e.fensterfernK1(), "fensterfern K-1 mit Trenner; " + e.kurz());
        assertEquals(0, e.echteVerstoesse(), "echte Verstoesse; " + e.kurz());
    }

    @Test
    void kontextwortImFensterIstEinZulaessigerFallDerPruefregelUndKeinFensterfernerFall() {
        String text = "KVNR: LANR " + NUMMER + " Ende";
        int s = start(text);
        Finding alt = fund(FindingType.LANR, text, s, s + NUMMER.length(), 0.40);
        Finding neu = fund(FindingType.KVNR, text, s, s + NUMMER.length(), 0.80);
        Ergebnis e = pruefe(text, List.of(alt), List.of(neu));
        assertEquals(1, e.k3(), "zulaessiger K-3-Fall; " + e.kurz());
        assertEquals(0, e.fensterfern(), "kein fensterferner Fall; " + e.kurz());
        assertEquals(0, e.echteVerstoesse(), "echte Verstoesse; " + e.kurz());
    }

    // ------------------------------------------------------------------ echte Verstoesse bleiben Verstoesse

    @Test
    void typwechselEinesNichtKvAltfundes_bleibtEinEchterVerstoss() {
        String text = text("\n");
        int s = start(text);
        Finding alt = fund(FindingType.EMAIL, text, s, s + NUMMER.length(), 0.90);
        Finding neu = fund(FindingType.KVNR, text, s, s + NUMMER.length(), 0.95);
        Ergebnis e = pruefe(text, List.of(alt), List.of(neu));
        assertEquals(0, e.fensterfern(), "kein fensterferner Fall; " + e.kurz());
        assertEquals(1, e.echteVerstoesse(), "echter Verstoss; " + e.kurz());
    }

    @Test
    void gesenkteKonfidenz_bleibtEinEchterVerstoss() {
        String text = text("\n");
        int s = start(text);
        Finding alt = fund(FindingType.LANR, text, s, s + NUMMER.length(), 0.80);
        Finding neu = fund(FindingType.LANR, text, s, s + NUMMER.length(), 0.60);
        Ergebnis e = pruefe(text, List.of(alt), List.of(neu));
        assertEquals(0, e.fensterfern(), "kein fensterferner Fall; " + e.kurz());
        assertEquals(1, e.echteVerstoesse(), "echter Verstoss; " + e.kurz());
    }

    @Test
    void typwechselZuEinemAnderenKvTyp_bleibtEinEchterVerstoss() {
        String text = text("\n");
        int s = start(text);
        Finding alt = fund(FindingType.LANR, text, s, s + NUMMER.length(), 0.40);
        Finding neu = fund(FindingType.IK, text, s, s + NUMMER.length(), 0.80);
        Ergebnis e = pruefe(text, List.of(alt), List.of(neu));
        assertEquals(0, e.fensterfern(), "kein fensterferner Fall; " + e.kurz());
        assertEquals(1, e.echteVerstoesse(), "echter Verstoss; " + e.kurz());
    }

    @Test
    void verlorenerFund_undVerlorenAbdeckung_bleibenEchteVerstoesse() {
        String text = text("\n");
        int s = start(text);
        Finding alt = fund(FindingType.LANR, text, s, s + NUMMER.length(), 0.40);
        Ergebnis e = pruefe(text, List.of(alt), List.of());
        assertEquals(0, e.fensterfern(), "kein fensterferner Fall; " + e.kurz());
        assertEquals(2, e.echteVerstoesse(), "Abdeckung verloren und Fund ohne Entsprechung; " + e.kurz());
        Finding teilweise = fund(FindingType.LANR, text, s, s + 4, 0.40);
        Ergebnis t = pruefe(text, List.of(alt), List.of(teilweise));
        assertEquals(0, t.fensterfern(), "kein fensterferner Fall; " + t.kurz());
        assertEquals(2, t.echteVerstoesse(), "Abdeckung verloren und Bereich verschoben; " + t.kurz());
    }

    @Test
    void k1MitZuNiedrigerKonfidenz_oderMitDreiZeichenAbstand_bleibtEinEchterVerstoss() {
        String text = KONTEXT + FUELLUNG + "\nLANR Z" + NUMMER + " Ende";
        int s = start(text);
        Finding alt = fund(FindingType.LANR, text, s, s + NUMMER.length(), 0.80);
        Finding zuNiedrig = fund(FindingType.KVNR, text, s - 1, s + NUMMER.length(), 0.40);
        Ergebnis e = pruefe(text, List.of(alt), List.of(zuNiedrig));
        assertEquals(0, e.fensterfern(), "kein fensterferner Fall; " + e.kurz());
        assertEquals(1, e.echteVerstoesse(), "echter Verstoss; " + e.kurz());
        String drei = KONTEXT + FUELLUNG + "\nLANR ZZ " + NUMMER + " Ende";
        int d = start(drei);
        Finding altDrei = fund(FindingType.LANR, drei, d, d + NUMMER.length(), 0.80);
        Finding zuWeit = fund(FindingType.KVNR, drei, d - 3, d + NUMMER.length(), 0.80);
        Ergebnis w = pruefe(drei, List.of(altDrei), List.of(zuWeit));
        assertEquals(0, w.fensterfern(), "kein fensterferner Fall; " + w.kurz());
        assertEquals(1, w.echteVerstoesse(), "echter Verstoss; " + w.kurz());
    }

    /**
     * Der letzte Trenner der Liste ist der Absatztrenner U+2029, ausdruecklich als {@code (char) 0x2029} geschrieben
     * (frueher stand das unsichtbare Zeichen selbst im Quelltext und sah aus wie ein Leerzeichen); er beendet den
     * Absatz allein. Ein einfaches Leerzeichen ist dagegen keine Absatzgrenze
     * ({@link #einzelnerUmbruchMitUnsichtbaremZeichen_istKeineLeerzeile}).
     */
    @Test
    void leerzeileZwischenKontextwortUndFund_bleibtEinEchterVerstoss() {
        for (String trenner : List.of("\n\n", "\r\n\r\n", "\n  \n", "\n" + (char) 0x200B + "\n", "\r" + (char) 0x200B
                + "\n\r" + (char) 0x200B + "\n", String.valueOf((char) 0x2029))) {
            String text = text(trenner);
            int s = start(text);
            Finding alt = fund(FindingType.LANR, text, s, s + NUMMER.length(), 0.40);
            Finding neu = fund(FindingType.KVNR, text, s, s + NUMMER.length(), 0.80);
            Ergebnis e = pruefe(text, List.of(alt), List.of(neu));
            assertEquals(0, e.fensterfern(), "kein fensterferner Fall; " + e.kurz());
            assertEquals(1, e.echteVerstoesse(), "echter Verstoss; " + e.kurz());
        }
    }

    @Test
    void einzelnerUmbruchMitUnsichtbaremZeichen_istKeineLeerzeile() {
        String zw = String.valueOf((char) 0x200B);
        for (String trenner : List.of("\r" + zw + "\n", "\r\n", "\n", "\r", "\n ", " ")) {
            String text = text(trenner);
            int s = start(text);
            Finding alt = fund(FindingType.LANR, text, s, s + NUMMER.length(), 0.40);
            Finding neu = fund(FindingType.KVNR, text, s, s + NUMMER.length(), 0.80);
            Ergebnis e = pruefe(text, List.of(alt), List.of(neu));
            assertEquals(1, e.fensterfern(), "fensterferner Fall; " + e.kurz());
            assertEquals(0, e.echteVerstoesse(), "echte Verstoesse; " + e.kurz());
        }
    }

    @Test
    void kontextwortNurDahinter_bleibtEinEchterVerstoss() {
        String text = "LANR " + NUMMER + " " + FUELLUNG + " " + FUELLUNG + " KVNR";
        int s = start(text);
        Finding alt = fund(FindingType.LANR, text, s, s + NUMMER.length(), 0.40);
        Finding neu = fund(FindingType.KVNR, text, s, s + NUMMER.length(), 0.80);
        Ergebnis e = pruefe(text, List.of(alt), List.of(neu));
        assertEquals(0, e.fensterfern(), "kein fensterferner Fall; " + e.kurz());
        assertEquals(1, e.echteVerstoesse(), "echter Verstoss; " + e.kurz());
    }

    @Test
    void k2GegenEinenTypDerVorKvnrSteht_bleibtEinEchterVerstoss() {
        String text = text("\n");
        int s = start(text);
        Finding alt = fund(FindingType.IK, text, s, s + NUMMER.length(), 0.40);
        Finding neu = fund(FindingType.KVNR, text, s, s + NUMMER.length(), 0.40);
        Ergebnis e = pruefe(text, List.of(alt), List.of(neu));
        assertEquals(0, e.fensterfern(), "kein fensterferner Fall; " + e.kurz());
        assertEquals(1, e.echteVerstoesse(), "echter Verstoss; " + e.kurz());
    }

    @Test
    void einFensterfernerUndEinEchterVerstossInEinemText_werdenGetrenntGezaehlt() {
        String text = KONTEXT + FUELLUNG + "\nLANR " + NUMMER + " und " + NUMMER + " Ende";
        int erste = text.indexOf(NUMMER);
        int zweite = text.indexOf(NUMMER, erste + 1);
        Finding altLanr = fund(FindingType.LANR, text, erste, erste + NUMMER.length(), 0.40);
        Finding altMail = fund(FindingType.EMAIL, text, zweite, zweite + NUMMER.length(), 0.90);
        Finding neuKvnr = fund(FindingType.KVNR, text, erste, erste + NUMMER.length(), 0.80);
        Finding neuTypwechsel = fund(FindingType.KVNR, text, zweite, zweite + NUMMER.length(), 0.95);
        Ergebnis e = pruefe(text, List.of(altLanr, altMail), List.of(neuKvnr, neuTypwechsel));
        assertEquals(1, e.fensterfernK3(), "ein fensterferner K-3-Fall; " + e.kurz());
        assertEquals(1, e.echteVerstoesse(), "ein echter Verstoss; " + e.kurz());
    }

    // ------------------------------------------------------------------ Absatz

    @Test
    void absatzAnfang_leerzeilenUndUmbruecheMitUnsichtbarenZeichen() {
        String zw = String.valueOf((char) 0x200B);
        assertEquals(3, KvnrFensterfernPruefung.absatzAnfang("a\n\nb", 3), "LF LF");
        assertEquals(5, KvnrFensterfernPruefung.absatzAnfang("a\r\n\r\nb", 5), "CR LF CR LF");
        assertEquals(4, KvnrFensterfernPruefung.absatzAnfang("a\n \nb", 4), "Leerzeichen in der Leerzeile");
        assertEquals(0, KvnrFensterfernPruefung.absatzAnfang("a\r" + zw + "\nb", 4), "CR UZ LF ist ein Umbruch");
        assertEquals(7, KvnrFensterfernPruefung.absatzAnfang("a\r" + zw + "\n\r" + zw + "\nb", 7),
                "CR UZ LF zweimal ist eine Leerzeile");
        assertEquals(0, KvnrFensterfernPruefung.absatzAnfang("a\nb\nc", 4), "einzelne Umbrueche");
        assertEquals(0, KvnrFensterfernPruefung.absatzAnfang("abc", 2), "ohne Umbruch");
        assertEquals(0, KvnrFensterfernPruefung.absatzAnfang("a b", 2), "ein Leerzeichen ist keine Absatzgrenze");
        assertEquals(2, KvnrFensterfernPruefung.absatzAnfang("a" + (char) 0x2029 + "b", 2), "Absatztrenner allein");
        assertEquals(2, KvnrFensterfernPruefung.absatzAnfang("\n\nb", 2), "Leerzeile am Textanfang");
    }
}
