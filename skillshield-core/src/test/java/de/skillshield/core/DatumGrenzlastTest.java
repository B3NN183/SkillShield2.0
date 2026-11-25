package de.skillshield.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.pseudonymize.PseudonymizationResult;
import de.skillshield.core.pseudonymize.Pseudonymizer;
import de.skillshield.core.recognizer.TextScanner;
import de.skillshield.core.recognizer.kv.KvStandDatum;
import de.skillshield.core.reidentify.ReidentificationResult;
import de.skillshield.core.reidentify.Reidentifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntToLongFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Grenzlast fuer Teil A des Features datum-und-kontextvorrang (T-3, AK-30): Die bestehende Zeitgrenze von
 * {@code GrenzlastTest} (zwei Sekunden je Schritt fuer eine Million Zeichen) gilt auch fuer Texte mit sehr vielen
 * Datumsangaben und fuer unguenstige Eingaben der Datumspruefung: lange Ziffernlaeufe, sehr viele Zeitraeume, sehr
 * viele Fast-Datumsformen. Scan, Pseudonymisierung und Rueckuebersetzung laufen je Schritt unter
 * {@code assertTimeoutPreemptively}; jeder Text hat mindestens eine Million Zeichen und entsteht zur Laufzeit
 * deterministisch (die Zeit der Erzeugung zaehlt nicht). Wo die Fundzahl aus der Regel folgt, wird sie exakt
 * geprueft. Der bestehende {@code GrenzlastTest} bleibt unveraendert.
 */
class DatumGrenzlastTest {

    private static final Duration GRENZE = Duration.ofSeconds(2);
    private static final Duration GRENZE_AUFWAERMEN = Duration.ofSeconds(10);
    private static final int MIN_ZEICHEN = 1_000_000;
    private static final int AUFWAERM_ZEICHEN = 10_000;

    private static final String D = SynthetischeDaten.DATUM_PUNKT;
    private static final TextScanner ALT = KvStandDatum.ALT_DATUM;
    private static final TextScanner SIMULIERT = KvStandDatum.simuliert(
            (text, f) -> DatumReferenz.istDatumssegment(text, f.start(), f.end()));
    private static final Pseudonymizer PSEUDONYMIZER = new Pseudonymizer();
    private static final Reidentifier REIDENTIFIER = new Reidentifier();

    /** Ergebnis der drei Schritte eines Texts. */
    private record Durchlauf(List<Finding> funde, PseudonymizationResult pseudo, ReidentificationResult zurueck) {
    }

    private static <T> T zeitbegrenzt(Duration grenze, Supplier<T> schritt) {
        return assertTimeoutPreemptively(grenze, schritt::get);
    }

    private static Durchlauf drei(String text, Duration grenze) {
        TextScanner scanner = TextScanner.standard();
        List<Finding> funde = zeitbegrenzt(grenze, () -> scanner.scan(text));
        PseudonymizationResult pseudo = zeitbegrenzt(grenze, () -> PSEUDONYMIZER.pseudonymize(text, funde));
        ReidentificationResult zurueck = zeitbegrenzt(grenze,
                () -> REIDENTIFIER.restore(pseudo.text(), pseudo.mapping()));
        return new Durchlauf(funde, pseudo, zurueck);
    }

    private static void pruefeRoundtrip(String text, Durchlauf d) {
        assertTrue(text.equals(d.zurueck().text()), "Roundtrip nicht zeichengleich (Textlaenge " + text.length() + ")");
        assertTrue(d.zurueck().unresolved().isEmpty(),
                "Rueckuebersetzung meldet " + d.zurueck().unresolved().size() + " Platzhalter");
    }

    private static int wiederholungen(String baustein, int mindestlaenge) {
        return (mindestlaenge + baustein.length() - 1) / baustein.length();
    }

    private static long kvFunde(List<Finding> funde) {
        return funde.stream().filter(f -> f.type() == FindingType.LANR || f.type() == FindingType.IK
                || f.type() == FindingType.KVNR || f.type() == FindingType.BSNR).count();
    }

    // ------------------------------------------------------------------ Texte aus Baustein und Wiederholung

    private static Arguments fall(String name, String praefix, String baustein, IntToLongFunction fundzahl) {
        return Arguments.of(name, praefix, baustein, fundzahl);
    }

    /**
     * Gemessener Istwert (nicht aus der Regel hergeleitet): Die Eingaben 03, 04, 05 und 07 sind je eine einzige
     * zusammenhaengende Kette ohne Leerzeichen. Es entsteht insgesamt genau ein Fund, unabhaengig von der
     * Wiederholungszahl. Typ und Bereich prueft {@code ak30_festwertFaelle_typUndBereichDesEinenFunds}: bei 03, 04
     * und 07 ein IP-Fund (03 ab Beginn, 04 und 07 ab Position 5, je bis vor den letzten Punkt), bei 05 ein BSNR-Fund
     * (Position 5 bis 21); der Rest der Kette von 05 besteht nur aus Datumsangaben und bleibt ohne Fund.
     */
    private static final long EIN_FUND = 1;

    /**
     * Die ungeunstigen Eingaben der Datumspruefung: Praefix, Baustein (so oft wiederholt, bis eine Million) und die
     * erwartete Gesamtzahl aller Funde als Formel der Wiederholungszahl n.
     * Formel aus der Regel: 01, 06, 11, 12 und 13 keine Funde (Datum hinter dem Kontextwort, Zeitraum oder
     * Ziffernblock ohne KV-Nummer entfaellt); 02 ein BSNR-Fund je Wiederholung (n).
     * Gemessener Istwert: 08, 09 und 10 je Wiederholung ein Fund (n; Neun-Ziffern-, Ziffernblock- und Uhrzeitform,
     * dort bleibt je Zeile ein Rest als Fund); 03, 04, 05 und 07 der Festwert 1.
     */
    static Stream<Arguments> eingaben() {
        return Stream.of(
            fall("01 BSNR Datum wiederholt", "", "BSNR " + D + " ", n -> 0),
            fall("02 BSNR Nummer Datum wiederholt", "", "BSNR " + SynthetischeKvNummern.BSNR_BEKANNT + " " + D + " ",
                n -> n),
            fall("03 Datumsanfang 14.03. wiederholt (ohne Kontext)", "", "14.03.", n -> EIN_FUND),
            fall("04 LANR 14.03. wiederholt (eine lange Kette)", "LANR ", "14.03.", n -> EIN_FUND),
            fall("05 BSNR Datum und Minus wiederholt (eine lange Kette aus Zeitraumanfaengen)", "BSNR ", D + "-",
                n -> EIN_FUND),
            fall("06 BSNR Zeitraum wiederholt", "", "BSNR " + SynthetischeDaten.ZEITRAUM + " ", n -> 0),
            fall("07 LANR 1. wiederholt (viele Ziffernbloecke)", "LANR ", "1.", n -> EIN_FUND),
            fall("08 LANR Datum mit Neun-Ziffern-Zeitraum wiederholt", "",
                "LANR " + SynthetischeDaten.ZEITRAUM_NEUN_ZIFFERN + " ", n -> n),
            fall("09 Datum mit weiterem Ziffernblock wiederholt", "", "IK " + SynthetischeDaten.DATUM_MIT_WEITEREM_BLOCK
                .get(0) + "\n", n -> n),
            fall("10 Datum mit Uhrzeit wiederholt", "", "KVNR " + SynthetischeDaten.DATUM_MIT_UHRZEIT_DOPPELPUNKT
                + "\n", n -> n),
            fall("11 LANR Datum und eine Million Ziffern", "LANR " + D, "0", n -> 0),
            fall("12 LANR Datum, Leerzeichen und eine Million Ziffern", "LANR " + D + " ", "7", n -> 0),
            fall("13 Datum und Ziffernblock ohne Kontext", D, "9", n -> 0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eingaben")
    void ak30_datumsfaelle_dreiSchritteJeUnterZweiSekunden(String name, String praefix, String baustein,
            IntToLongFunction fundzahl) {
        String warm = praefix + baustein.repeat(wiederholungen(baustein, AUFWAERM_ZEICHEN));
        drei(warm, GRENZE_AUFWAERMEN);

        int n = wiederholungen(baustein, MIN_ZEICHEN);
        String text = praefix + baustein.repeat(n);
        assertTrue(text.length() >= MIN_ZEICHEN, "Textlaenge " + text.length());
        Durchlauf d = drei(text, GRENZE);
        // Die Fundzahl wird nach der Zeitmessung geprueft; der Scan ist dann schon gelaufen.
        assertEquals(fundzahl.applyAsLong(n), d.funde().size(), "Gesamtzahl der Funde bei n=" + n);
        pruefeRoundtrip(text, d);
    }

    /** Festwert-Faelle: Typ, Anfang und Ende des einen Funds (Ende als Formel aus der Textlaenge). */
    static Stream<Arguments> festwertFaelle() {
        return Stream.of(
            Arguments.of("03", "", "14.03.", FindingType.IP, 0, 1),
            Arguments.of("04", "LANR ", "14.03.", FindingType.IP, 5, 1),
            Arguments.of("05", "BSNR ", D + "-", FindingType.BSNR, 5, -1),
            Arguments.of("07", "LANR ", "1.", FindingType.IP, 5, 1));
    }

    @ParameterizedTest(name = "Fall {0}")
    @MethodSource("festwertFaelle")
    void ak30_festwertFaelle_typUndBereichDesEinenFunds(String name, String praefix, String baustein,
            FindingType typ, int start, int abstandVomEnde) {
        String text = praefix + baustein.repeat(wiederholungen(baustein, MIN_ZEICHEN));
        List<Finding> funde = TextScanner.standard().scan(text);
        assertEquals(1, funde.size(), "Fundzahl");
        Finding f = funde.get(0);
        assertEquals(typ, f.type(), "Typ");
        assertEquals(start, f.start(), "Anfang");
        // Abstand 1: Ende vor dem letzten Punkt; -1: festes Ende bei Position 21 (16 Zeichen ab Position 5).
        int erwartetEnde = abstandVomEnde > 0 ? text.length() - abstandVomEnde : 21;
        assertEquals(erwartetEnde, f.end(), "Ende");
    }

    /** Fall 1: Datum hinter dem Kontextwort ist nie ein Fund; der Text bleibt ohne jeden Fund. */
    @Test
    void ak30_bsnrDatumWiederholt_keinFund() {
        String baustein = "BSNR " + D + " ";
        String text = baustein.repeat(wiederholungen(baustein, MIN_ZEICHEN));
        Durchlauf d = drei(text, GRENZE);
        assertEquals(0, d.funde().size(), "Fundzahl");
        assertTrue(text.equals(d.pseudo().text()), "ohne Fund bleibt der Text unveraendert");
        pruefeRoundtrip(text, d);
    }

    /** Fall 2: Nummer vor dem Datum bleibt je Wiederholung genau ein BSNR-Fund, das Datum nie. */
    @Test
    void ak30_bsnrNummerDatumWiederholt_genauEinFundJeWiederholung() {
        String baustein = "BSNR " + SynthetischeKvNummern.BSNR_BEKANNT + " " + D + " ";
        int anzahl = wiederholungen(baustein, MIN_ZEICHEN);
        String text = baustein.repeat(anzahl);
        Durchlauf d = drei(text, GRENZE);
        assertEquals(anzahl, d.funde().size(), "Fundzahl");
        for (Finding f : d.funde()) {
            assertEquals(FindingType.BSNR, f.type(), "Typ");
            assertEquals(SynthetischeKvNummern.BSNR_BEKANNT.length(), f.end() - f.start(), "Laenge");
        }
        assertTrue(d.pseudo().text().contains(D), "Datum bleibt im Klartext");
        pruefeRoundtrip(text, d);
    }

    /** Fall 3: Ohne Kontextwort entsteht aus Datumsanfaengen kein KV-Fund. */
    @Test
    void ak30_datumsanfaengeOhneKontext_keinKvFund() {
        String baustein = "14.03.";
        String text = baustein.repeat(wiederholungen(baustein, MIN_ZEICHEN));
        Durchlauf d = drei(text, GRENZE);
        assertEquals(0, kvFunde(d.funde()), "KV-Funde");
        pruefeRoundtrip(text, d);
    }

    // ------------------------------------------------------------------ Mischungen

    /** Sehr viele Datumsangaben aller Formen hinter Kontextwoertern; Fundzahl nur durch Zeitraeume (Neun-Ziffern). */
    @Test
    void ak30_vieleDatenAllerFormen_dreiSchritteJeUnterZweiSekunden() {
        Random r = new Random(20261205L);
        StringBuilder sb = new StringBuilder(MIN_ZEICHEN + 100);
        String[] woerter = {"LANR ", "IK ", "KVNR ", "BSNR ", "NBSNR "};
        int n = 0;
        while (sb.length() < MIN_ZEICHEN) {
            sb.append(woerter[n % woerter.length]).append(DatumFormen.gueltig(r, r.nextInt(DatumFormen.ANZAHL)));
            sb.append(n % 7 == 0 ? "\n" : ", ");
            n++;
        }
        String text = sb.toString();
        Durchlauf d = drei(text, GRENZE);
        // Das Produkt ist auch hier gleich der Simulation von Teil A; Funde bleiben nur als Neun-Ziffern-Reste von
        // Zeitraeumen (samt Rest hinter einem nachgestellten Kontextwort), nie ueber einem ganzen Datum.
        assertTrue(d.funde().equals(SIMULIERT.scan(text)), "Produkt gleich Simulation");
        for (Finding f : d.funde()) {
            assertFalse(DatumReferenz.istDatumssegment(text, f.start(), f.end()), "Fund ueber einem Datumssegment");
        }
        pruefeRoundtrip(text, d);
    }

    /** Sehr viele Fast-Datumsformen (ungueltig oder keine Datumsform) hinter Kontextwoertern. */
    @Test
    void ak30_vieleFastDatumsformen_dreiSchritteJeUnterZweiSekunden() {
        Random r = new Random(20261206L);
        StringBuilder sb = new StringBuilder(MIN_ZEICHEN + 100);
        String[] woerter = {"LANR ", "IK ", "KVNR ", "BSNR "};
        int n = 0;
        List<String> abschluesse = new ArrayList<>(List.of(", ", "\n", " ", "; "));
        while (sb.length() < MIN_ZEICHEN) {
            String wert = n % 3 == 0 ? DatumFormen.keinDatum(r)
                    : DatumFormen.ungueltig(r, r.nextInt(DatumFormen.ANZAHL));
            sb.append(woerter[n % woerter.length]).append(wert).append(abschluesse.get(n % abschluesse.size()));
            n++;
        }
        String text = sb.toString();
        Durchlauf d = drei(text, GRENZE);
        assertTrue(d.funde().equals(ALT.scan(text)), "ohne gueltiges Datum ist das Produkt gleich ALT_DATUM");
        assertTrue(kvFunde(d.funde()) > n / 2, "die meisten Fast-Daten bleiben Fund: " + d.funde().size());
        pruefeRoundtrip(text, d);
    }
}
