package de.skillshield.core;

import java.util.List;

/**
 * Zentrale Testhilfe fuer erfundene Kalenderdaten (Feature datum-und-kontextvorrang, D-7, AK-32). Alle Werte sind
 * frei erfunden und haben keinen Bezug zu realen Personen oder Leistungen; ein Datum steht nie zusammen mit Namen
 * oder Adressen. Datumsangaben in Tests stehen nur hier. Texte, die ein Datum mit weiteren Ziffern verbinden
 * (zum Beispiel ein Datum, ein Trenner und eine KV-Nummer), entstehen erst zur Laufzeit durch Verketten mit Werten
 * aus {@link SynthetischeKvNummern}.
 *
 * <p>Die Listen sind unveraenderlich. Welche Werte ein Datum im Sinne des Features sind, entscheidet die
 * unabhaengige {@link DatumReferenz}; {@code DatumReferenzTest} haelt beides gegeneinander fest.
 */
public final class SynthetischeDaten {

    private SynthetischeDaten() {
    }

    /** Ein Datum in der Punktform mit vierstelligem Jahr (D-2 a); das Beispiel der Anforderung. */
    public static final String DATUM_PUNKT = "14.03.1958";
    /** Zahl der Kalendertage von 01.01.1900 bis 31.12.2099 (200 Jahre, 49 Schaltjahre). */
    public static final int KALENDERTAGE_1900_BIS_2099 = 73_049;
    /** Ein Zeitraum aus zwei Daten (D-2 e). */
    public static final String ZEITRAUM = "01.03.2026-31.03.2026";
    /** Das fruehere Leistungsdatum im Anlasstext; die ersten beiden Leistungszeilen tragen es (Nachtrag 2). */
    public static final String DATUM_LEISTUNG = "12.02.2026";
    /** Das spaetere Leistungsdatum im Anlasstext; die dritte Leistungszeile traegt es. */
    public static final String DATUM_LEISTUNG_SPAETER = "05.03.2026";
    /** Ein Datum aus dem Anlasstext, hinter dem kein Kontextwort steht. */
    public static final String DATUM_GENEHMIGUNG = "01.03.2026";

    /** AK-2: Formen, die ein Datum sind (hinter jedem Kontextwort ohne Fund). */
    public static final List<String> GUELTIGE_FORMEN = List.of("14.03.1958", "5.3.2026", "05.3.2026", "5.03.2026",
            "14.03.58", "14/03/1958", "5/3/2026", "14-03-1958", "5-3-2026", "1958-03-14", "01.03.2026-31.03.2026");

    /** AK-3: Formen, die kein Datum im Sinne des Features sind (bleiben Fund, wenn ein Kontextwort sie erschliesst). */
    public static final List<String> KEINE_DATUMSFORM = List.of("14031958", "14 03 1958", "14.03-1958", "1958.03.14",
            "1958/03/14", "14/03/58", "14-03-58");

    /** AK-4: Werte, die kalendarisch unmoeglich oder ausserhalb 1900 bis 2099 sind (bleiben Fund). */
    public static final List<String> KALENDERUNGUELTIG = List.of("45.13.2026", "31.04.2026", "29.02.2025",
            "00.03.2026", "14.00.2026", "14.13.2026", "01.01.1850", "01.01.2100", "14.03.0000");

    /** AK-4: Schaltjahre (Datum, kein Fund). */
    public static final List<String> SCHALTTAGE_GUELTIG = List.of("29.02.2024", "29.02.2000");

    /** AK-4: 29.02. in einem Jahr, das kein Schaltjahr ist (bleibt Fund). */
    public static final String SCHALTTAG_1900 = "29.02.1900";

    /** AK-5: Jahresgrenzen, die noch Datum sind. */
    public static final List<String> JAHRESGRENZEN_GUELTIG = List.of("01.01.1900", "31.12.2099", "01.01.00",
            "31.12.99");

    /** AK-5: Jahresgrenzen, die schon ausserhalb liegen (bleiben Fund). */
    public static final List<String> JAHRESGRENZEN_UNGUELTIG = List.of("31.12.1899", "01.01.2100");

    /** Zweistelliges Jahr 00: 29.02. ist dort kein Datum (1900 ist kein Schaltjahr; Architektur Kap. 13 Punkt 7). */
    public static final String SCHALTTAG_ZWEISTELLIG_NULL = "29.02.00";

    /** D-5: Datum mit weiterem Block ueber genau einen Trenner, Kette bis 12 Ziffern (bleibt Fund). */
    public static final List<String> DATUM_MIT_WEITEREM_BLOCK = List.of("14.03.1958 1234", "14.03.1958 08.30");

    /** AK-10: Datum mit Uhrzeit und Doppelpunkt (der Doppelpunkt beendet die Kette, das Datum ist frei). */
    public static final String DATUM_MIT_UHRZEIT_DOPPELPUNKT = "14.03.1958 08:30";

    /** D-2: Datum mit Buchstabe davor in der Buchstabenform der KVNR (bleibt Fund). */
    public static final String BUCHSTABE_UND_DATUM = "B 14.03.1958";

    /** Zeitraum, dessen Praefixe genau neun Ziffern erreichen (Grenze aus A-2, bleibt teilweise Fund). */
    public static final String ZEITRAUM_NEUN_ZIFFERN = "01.03.2026-1.3.2026";

    /** Zeitraum mit ein- und zweistelligen Werten, dessen Praefix genau neun Ziffern erreicht. */
    public static final String ZEITRAUM_EINSTELLIG = "1.3.2026-15.3.2026";

    private static final String GEDANKENSTRICH = String.valueOf((char) 0x2013);

    /**
     * Der Anlasstext des Features in synthetischer Fassung (Architektur Kap. 6 Punkt 4). Die Nummern des Anlasses sind
     * ersetzt: die erste BSNR durch {@code bsnrEins}, die zweite durch {@code bsnrZwei}, die Nummer hinter
     * {@code LANR} (im Anlass eine BSNR-Form mit ungueltiger Pruefziffer) durch {@code lanrForm}, die KVNR durch
     * {@code kvnr}; die Auslassungen sind erfundene Woerter; die Datumswerte sind die des Anlasses. Der Text hat drei
     * Leistungszeilen: zweimal das fruehere Leistungsdatum mit der zweiten BSNR, einmal das spaetere mit der ersten.
     *
     * @param bsnrEins erste BSNR (Hauptbetriebsstaette)
     * @param bsnrZwei zweite BSNR (Nebenbetriebsstaette)
     * @param lanrForm Zahl hinter {@code LANR} mit BSNR-Gestalt, nach LANR und IK ungueltig
     * @param kvnr     KVNR mit absichtlich ungueltiger Pruefziffer
     */
    public static String anlassText(String bsnrEins, String bsnrZwei, String lanrForm, String kvnr) {
        return "BSNR " + bsnrEins + " (Hauptbetriebsstaette), NBSNR " + bsnrZwei
                + " (Nebenbetriebsstaette Beispielort)\n"
                + "LANR " + lanrForm + "\n"
                + "\n"
                + "Patientin: Beispielfall, geb. " + DATUM_PUNKT + ", KVNR " + kvnr + ", Beispielkasse\n"
                + "\n"
                + GEDANKENSTRICH + " " + DATUM_LEISTUNG + ", NBSNR " + bsnrZwei + ": GOP 33042 (Beispielleistung)\n"
                + GEDANKENSTRICH + " " + DATUM_LEISTUNG + ", NBSNR " + bsnrZwei + ": GOP 33042 (Beispielleistung)\n"
                + GEDANKENSTRICH + " " + DATUM_LEISTUNG_SPAETER + ", BSNR " + bsnrEins
                + ": GOP 33042 (Beispielleistung)\n"
                + "\n"
                + "Stammdaten LANR " + lanrForm + ": Genehmigung Beispiel gueltig ab " + DATUM_GENEHMIGUNG
                + ", erteilt fuer BSNR " + bsnrEins + ".";
    }

    /**
     * Der Anlasstext mit den festen Ersetzungen aus {@link SynthetischeKvNummern}: {@code BSNR_BEKANNT},
     * {@code BSNR_BEKANNT_ZWEI}, {@code BSNR_MARKER} (BSNR-Form mit bekanntem Regionalcode, nach LANR und IK
     * ungueltig) und {@code KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN}.
     */
    public static String anlassText() {
        return anlassText(SynthetischeKvNummern.BSNR_BEKANNT, SynthetischeKvNummern.BSNR_BEKANNT_ZWEI,
                SynthetischeKvNummern.BSNR_MARKER, SynthetischeKvNummern.KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN);
    }
}
