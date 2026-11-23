package de.skillshield.core;

import de.skillshield.core.recognizer.kv.KvnrBsnrHilfen;
import java.util.List;
import java.util.Random;

/**
 * Zentrale Klasse aller synthetischen IK- und LANR-Testnummern (Feature kv-nummern-erkennung, AK-28, AK-29).
 * Nur hier stehen Nummern mit neun Ziffern (und alle weiteren IK/LANR-Testnummern) als Literal; andere Tests bilden
 * ihre Texte per Verkettung mit diesen Konstanten. {@code TestdatenSynthetischTest} (Regel "Ziffernketten")
 * erzwingt das und verlangt, dass jede neunstellige Ziffernfolge in dieser Datei mit {@code 00} beginnt.
 *
 * <p>Seit dem Feature kvnr-bsnr-erkennung steht hier auch der Abschnitt KVNR (Buchstabe und neun Ziffern, AK-6, AK-23,
 * AK-24); fuer KVNR-Formen verlangt der Waechter statt {@code 00} die Ziffern {@code 0000} an den Stellen 2 bis 5.
 *
 * <p><b>Quellenvermerk Pr\u00FCfverfahren LANR (AK-29).</b> Quelle: Wikipedia, "Lebenslange Arztnummer", Abschnitt
 * "Aufbau", https://de.wikipedia.org/wiki/Lebenslange_Arztnummer; gleichlautend: krankenkasseninfo.de,
 * Krankenkassenlexikon, Eintrag "Arztnummer",
 * https://www.krankenkasseninfo.de/zahlen-fakten/lexikon/arztnummer, abgerufen am 21.11.2026. Status:
 * Sekund\u00E4rquelle, nicht gegen die offizielle Spezifikation verifiziert. Offizielle Quelle: Richtlinie
 * der KBV nach \u00A7 75 Absatz 7 SGB V (Stand 1. Januar 2024), eingesehen; sie best\u00E4tigt den Aufbau,
 * enth\u00E4lt aber das Rechenverfahren nicht (Verweis auf die Vereinbarung ANRV, nicht eingesehen).
 *
 * <p><b>Quellenvermerk Pr\u00FCfverfahren IK (AK-29).</b> Quelle: Wikipedia, "Institutionskennzeichen", Abschnitt
 * "Pr\u00FCfverfahren", https://de.wikipedia.org/wiki/Institutionskennzeichen, abgerufen am 21.11.2026.
 * Status: Sekund\u00E4rquelle, nicht gegen die offizielle Spezifikation verifiziert. Offizielle Quelle:
 * Gemeinsames Rundschreiben Institutionskennzeichen der ARGE IK, nicht eingesehen.
 *
 * <p><b>Verfahren (Sekund\u00E4rquellen-Stand, Lesart "Rest").</b> LANR: neun Stellen; Stellen 1 bis 6 von
 * links abwechselnd mit 4 und 9 gewichtet, Summe modulo 10, Pr\u00FCfziffer (Stelle 7) = (10 - Rest) modulo 10;
 * Stellen 8 und 9 (Fachgruppe) werden nicht gepr\u00FCft. IK: neun Stellen; Stellen 1 und 2 (Klassifikation)
 * gehen nicht ein; Stellen 3 bis 8 mit 2, 1, 2, 1, 2, 1 gewichtet, je Produkt die Quersumme, Summe modulo 10
 * ist die Pr\u00FCfziffer (Stelle 9).
 *
 * <p><b>Bildungsregel (A-6, OF-2).</b> Alle Nummern sind rechnerisch gebildet und beginnen mit {@code 00}
 * (LANR: Arztnummer beginnt mit 00; IK: Klassifikation 00). Das Beispiel-IK der Sekund\u00E4rquelle hat eine
 * echte Klassifikation und steht deshalb nirgends im Code. Es gibt keine Ersatz- oder Sammelnummern (Muster
 * aus lauter Neunen) und keine Nummern aus echten Quellen; die Nummern werden nie mit Namen, Adressen oder
 * Diagnosen kombiniert.
 *
 * <p><b>Restrisiko.</b> Es ist nicht belegt, dass Nummern mit f\u00FChrendem {@code 00} real nie vergeben
 * werden. Eine zuf\u00E4llige \u00DCbereinstimmung mit einer real vergebenen Nummer ist deshalb nicht
 * ausgeschlossen; sie w\u00E4re ohne Bezug zu einer Person oder Praxis und ohne Kontext im Test.
 */
public final class SynthetischeKvNummern {

    private SynthetischeKvNummern() {
    }

    // ------------------------------------------------------------------ LANR gueltig (AK-7)

    // Rechenweg LANR: Stellen 1-6 mit 4,9,4,9,4,9 gewichten, Summe S, Pruefziffer = (10 - S mod 10) mod 10 = Stelle 7.

    /** Stellen 1-6 = 001235: 0*4 + 0*9 + 1*4 + 2*9 + 3*4 + 5*9 = 79, Rest 9, Pruefziffer 1 = Stelle 7. */
    public static final String LANR_GUELTIG = "001235101";

    /** Wie oben, Fachgruppe (Stellen 8 und 9) = 99 wird nicht geprueft: Summe 79, Pruefziffer 1. */
    public static final String LANR_GUELTIG_FACHGRUPPE = "001235199";

    /** Stellen 1-6 = 001234: 4 + 18 + 12 + 36 = 70, Rest 0, 10 - 0 = 10 ergibt Pruefziffer 0 = Stelle 7. */
    public static final String LANR_GUELTIG_PRUEFZIFFER_NULL = "001234001";

    /** Stellen 1-6 = 001236: 4 + 18 + 12 + 54 = 88, Rest 8, Pruefziffer 2 = Stelle 7. */
    public static final String LANR_GUELTIG_ZWEI = "001236201";

    // ------------------------------------------------------------------ LANR ungueltig (AK-7)

    /** Summe 79, erwartet 1, Stelle 7 ist 2 (Pruefziffer verschoben). */
    public static final String LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN = "001235201";

    /** Stellen 1-6 = 001325: 4 + 27 + 8 + 45 = 84, Rest 4, erwartet 6, Stelle 7 ist 1 (Nachbarziffern vertauscht). */
    public static final String LANR_UNGUELTIG_VERTAUSCHT = "001325101";

    /** Summe 79, erwartet 1, Stelle 7 ist 0 (eine Ziffer geaendert). */
    public static final String LANR_UNGUELTIG_ZIFFER_GEAENDERT = "001235001";

    // ------------------------------------------------------------------ IK (AK-8)

    // Rechenweg IK: Stellen 3-8 mit 2,1,2,1,2,1 gewichten, je Produkt die Quersumme, Summe S, S mod 10 = Stelle 9.

    /** Stellen 3-8 = 123456: 2 + 2 + 6 + 4 + (10 -> 1) + 6 = 21, Rest 1 = Stelle 9. */
    public static final String IK_GUELTIG = "001234561";

    /** Summe 21, Rest 1; Stelle 9 ist 9 = 10 - 1 (Lesart "Komplement"), hier ausdruecklich ungueltig. */
    public static final String IK_UNGUELTIG_KOMPLEMENT = "001234569";

    /** Summe 21, Rest 1; Stelle 9 ist 0. */
    public static final String IK_UNGUELTIG_NULL = "001234560";

    /** Stellen 3-8 = 124356: 2 + 2 + 8 + 3 + (10 -> 1) + 6 = 22, Rest 2; Stelle 9 ist 1 (Ziffern vertauscht). */
    public static final String IK_UNGUELTIG_VERTAUSCHT = "001243561";

    /** Stellen 3-8 = 123457: 2 + 2 + 6 + 4 + (10 -> 1) + 7 = 22, Rest 2 = Stelle 9 (fuer Aufzaehlungen, T-5). */
    public static final String IK_GUELTIG_ZWEI = "001234572";

    /** Stellen 3-8 = 123458: 2 + 2 + 6 + 4 + (10 -> 1) + 8 = 23, Rest 3 = Stelle 9 (fuer Aufzaehlungen, T-5). */
    public static final String IK_GUELTIG_DREI = "001234583";

    // ------------------------------------------------------------------ Marker-Nummern (T-9)

    /**
     * Marker-LANR, gueltig. Stellen 1-6 = 007382: 0 + 0 + 28 + 27 + 32 + 18 = 105, Rest 5, Pruefziffer 5 = Stelle 7;
     * Fachgruppe 07. Von allen anderen Nummern dieser Klasse verschieden, auch in den ersten sechs Ziffern.
     */
    public static final String LANR_MARKER = "007382507";

    /**
     * Marker-IK, gueltig. Stellen 3-8 = 591648: 10 -> 1, 9, 2, 6, 8, 8 ergibt 1 + 9 + 2 + 6 + 8 + 8 = 34, Rest 4 =
     * Stelle 9. Von allen anderen Nummern dieser Klasse verschieden, auch in den ersten sechs Ziffern.
     */
    public static final String IK_MARKER = "005916484";

    // ------------------------------------------------------------------ KVNR (AK-6, AK-23)

    // Quellenvermerk Pruefverfahren KVNR (AK-24): Quelle: Wikipedia, Artikel "Krankenversichertennummer",
    // https://de.wikipedia.org/wiki/Krankenversichertennummer, abgerufen am 23.11.2026. Status: Sekundaerquelle, nicht
    // gegen die offizielle Spezifikation verifiziert. Offizielle Quelle: GKV-Datenaustausch, "Pruefziffernberechnung
    // fuer die Krankenversichertennummer nach Paragraph 290 SGB V" (Anlage 1, 2. Januar 2023), nicht eingesehen.
    //
    // Rechenweg KVNR: Buchstabe zur zweistelligen Alphabetposition (A = 01 bis Z = 26), dazu die ersten acht Ziffern:
    // zehn Ziffern, von links abwechselnd mit 1, 2, 1, 2 ... gewichtet, je Produkt die Quersumme, Summe S; die
    // neunte Ziffer nach dem Buchstaben ist S modulo 10.
    //
    // Bildungsregel (A-6, H-1): Buchstabe Z, die ersten vier der acht Ziffern 0000, Rest und Pruefziffer rechnerisch.
    // Nach H-1 verwenden einzelne Vektoren andere Buchstaben (zweistellige gegen einstellige Buchstabenwertung ist nur
    // mit A bis I pruefbar); die vier Ziffern 0000 bleiben. Einzige Ausnahme ist das Lehrbeispiel der Quelle.
    //
    // Restrisiko (OF-3): Es ist nicht belegt, dass Nummern mit den Ziffern 0000 an den Stellen 2 bis 5 real nie
    // vergeben werden; eine zufaellige Uebereinstimmung waere ohne Bezug zu einer Person und ohne Kontext im Test.

    /**
     * Lehrbeispiel der Quelle (keine vergebene Nummer): Buchstabe A, danach die Ziffern eins bis acht aufsteigend, dann
     * die Pruefziffer. A = 01: 0*1 + 1*2 = 2; Ziffern 1..8: 1, 4, 3, 8, 5, (12 -> 3), 7, (16 -> 7) = 38; Summe 40,
     * Pruefziffer 0. Einziger Vektor ohne die Ziffern 0000 (H-1); aus Teilen zusammengesetzt, damit die Folge in
     * dieser Datei nicht als zusammenhaengende neunstellige Zeichenfolge ohne Praefix 00 steht.
     */
    public static final String KVNR_LEHRBEISPIEL = "A" + "12345678" + "0";

    /** Z = 26: 2*1 + 6*2 (12 -> 3) = 5; Ziffern 0,0,0,0,1,2,3,4: 1 + 4 + 3 + 8 = 16; Summe 21, Pruefziffer 1. */
    public static final String KVNR_GUELTIG = "Z000012341";

    /** Z = 26: 5; Ziffern 0,0,0,0,4,3,2,1: 4 + 6 + 2 + 2 = 14; Summe 19, Pruefziffer 9. */
    public static final String KVNR_GUELTIG_ZWEI = "Z000043219";

    /**
     * C = 03: 0*1 + 3*2 = 6; Ziffern 0,0,0,0,5,6,7,8: 5, (12 -> 3), 7, (16 -> 7) = 22; Summe 28, Pruefziffer 8.
     * Unterscheidet bei den Varianten: Gewichtsbeginn 2 ergibt 3, einstellige Buchstabenwertung ergibt 3, Summe ohne
     * Quersummen ergibt 6; enthaelt Produkte ueber 9 (H-1: Buchstabe aus A bis I).
     */
    public static final String KVNR_GUELTIG_BUCHSTABE_C = "C000056788";

    /** Marker, gueltig. M = 13: 1*1 + 3*2 = 7; Ziffern 0,0,0,0,7,3,9,1: 7 + 6 + 9 + 2 = 24; Summe 31, Pruefziffer 1. */
    public static final String KVNR_MARKER = "M000073911";

    /**
     * Buchstabe S und nur Nullen: S = 19: 1*1 + 9*2 (18 -> 9) = 10; Summe 10, Pruefziffer 0 (gueltig); Grenzlast und
     * Aufzaehlungen mit Nullfolge (zehn Zeichen, lauter gleiche Ziffern).
     */
    public static final String KVNR_NULL = "S" + "000000000";

    // ------------------------------------------------------------------ KVNR ungueltig (AK-6)

    /** Wie {@link #KVNR_GUELTIG}: Summe 21, erwartet 1, die neunte Ziffer ist 2 (Pruefziffer verschoben). */
    public static final String KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN = "Z000012342";

    /**
     * Ziffern 0,0,0,0,1,2,4,3 statt 1,2,3,4 (Nachbarziffern vertauscht): 1 + 4 + 4 + 6 = 15; Summe 20, erwartet 0;
     * die neunte Ziffer ist 1.
     */
    public static final String KVNR_UNGUELTIG_VERTAUSCHT = "Z000012431";

    /** Ziffern 0,0,0,0,1,2,3,5 (Ziffer geaendert): 1 + 4 + 3 + (10 -> 1) = 9; Summe 14, erwartet 4, Stelle 9 ist 1. */
    public static final String KVNR_UNGUELTIG_ZIFFER_GEAENDERT = "Z000012351";

    // ------------------------------------------------------------------ KVNR Formabweichungen (AK-8)

    /** Buchstabe und acht Ziffern: zu kurz fuer die Neunerform. */
    public static final String KVNR_ACHT_ZIFFERN = "Z" + "00001234";

    /** Buchstabe und zehn Ziffern: zu lang fuer die Neunerform. */
    public static final String KVNR_ZEHN_ZIFFERN = "Z0000123412";

    // ------------------------------------------------------------------ BSNR (kvnr-bsnr-erkennung, AK-27, AK-34)

    // Quellenvermerk (AK-24, sinngemaess): Quelle: Richtlinie der KBV nach Paragraph 75 Absatz 7 SGB V zur Vergabe der
    // Arzt-, Betriebsstaetten-, Praxisnetz- sowie der Netzverbundnummern, Stand 1. Januar 2024 (kbv.de), Paragraph 6
    // und Anlage 1, abgerufen am 23.11.2026 (durch die Hauptsitzung). Status: nicht vom Entwickler gegen die
    // Richtlinie verifiziert. Die BSNR ist neunstellig, die Stellen 1 und 2 sind der Regionalcode, eine Pruefziffer
    // gibt es nicht. Die Liste der Regionalcodes fuehrt allein das Produkt (BsnrRegionalcodes).
    //
    // Bildungsregel (A-6): bekannter Regionalcode (Liste der Anlage 1), Stellen 3 bis 7 gleich 00000, Stellen 8 und 9
    // frei und verschieden. Der Regionalcode ist so gewaehlt, dass die Zahl nach dem LANR-Verfahren ungueltig ist
    // (Stelle 7 ist 0, ungueltig, wenn 4*Stelle1 + 9*Stelle2 nicht durch 10 teilbar ist) und nach dem IK-Verfahren
    // ungueltig (hier geht nur Stelle 8 ein; ungueltig, wenn Stelle 8 ungleich Stelle 9 ist). So bleibt jede
    // Testnummer ohne Kontextwort der anderen Arten ein reiner BSNR-Fall (D-6, AK-30). Der unbekannte Regionalcode
    // der Negativvektoren ist 00 (kein Schluessel der Anlage 1); damit er nach dem LANR-Verfahren nicht zufaellig
    // gueltig ist, steht dort Stelle 7 ungleich 0. Eine BSNR-Testnummer steht nie direkt hinter einem einzelnen
    // Grossbuchstaben (KVNR-Form).
    //
    // Restrisiko (OF-3): Es ist nicht belegt, dass Nummern mit 00000 an den Stellen 3 bis 7 real nie vergeben werden;
    // eine zufaellige Uebereinstimmung waere ohne Bezug zu einer Praxis und ohne Kontext im Test.

    /**
     * Bekannter Regionalcode 17 (Anlage 1: 06 bis 21), Stellen 3 bis 7 gleich 00000, Stellen 8 und 9 gleich 3 und 1.
     * LANR: Summe 4*1 + 9*7 = 67, erwartete Stelle 7 waere 3, ist 0 (ungueltig). IK: Quersummensumme 3, Stelle 9 ist 1
     * (ungueltig).
     */
    public static final String BSNR_BEKANNT = "170000031";

    /**
     * Bekannter Regionalcode 52 (Anlage 1: 37 bis 73), Stellen 8 und 9 gleich 4 und 7. LANR: Summe 4*5 + 9*2 = 38,
     * erwartete Stelle 7 waere 2, ist 0 (ungueltig). IK: Quersummensumme 4, Stelle 9 ist 7 (ungueltig).
     */
    public static final String BSNR_BEKANNT_ZWEI = "520000047";

    /**
     * Unbekannter Regionalcode 00, Stellen 3 bis 6 gleich 0000, Stelle 7 gleich 5, Stellen 8 und 9 gleich 3 und 7.
     * LANR: Summe 0, erwartete Stelle 7 waere 0, ist 5 (ungueltig). IK: Stelle 7 mal 2 ergibt 10, Quersumme 1, dazu
     * Stelle 8 ergibt 4, Stelle 9 ist 7 (ungueltig).
     */
    public static final String BSNR_UNBEKANNT = "000000537";

    /** Bekannter Regionalcode 17 wie {@link #BSNR_BEKANNT}, aber nur acht Ziffern (Stelle 8 gleich 3): zu kurz. */
    public static final String BSNR_ACHT_ZIFFERN = "17000003";

    /** Bekannter Regionalcode 52 wie {@link #BSNR_BEKANNT_ZWEI}, aber zehn Ziffern (zusaetzlich eine 1): zu lang. */
    public static final String BSNR_ZEHN_ZIFFERN = "5200000471";

    /**
     * Nullnummer: neun Nullen (Regionalcode 00, unbekannt) fuer Grenzlast und Aufzaehlungen mit gleichen Ziffern.
     * Nach LANR- und IK-Verfahren zufaellig gueltig (Summe 0); fuer BSNR-Faelle ohne Folge, weil dort nur das BSNR-
     * Kontextwort gilt.
     */
    public static final String BSNR_NULL = "000000000";

    /**
     * Marker, bekannter Regionalcode 43 (Anlage 1: 37 bis 73), Stellen 8 und 9 gleich 8 und 2. LANR: Summe 4*4 + 9*3 =
     * 43, erwartete Stelle 7 waere 7, ist 0 (ungueltig). IK: Quersummensumme 8, Stelle 9 ist 2 (ungueltig). Von allen
     * anderen Nummern dieser Klasse verschieden, auch in den ersten sechs Ziffern.
     */
    public static final String BSNR_MARKER = "430000082";

    /**
     * Netzverbundnummer-Form (T-13, Anforderung D-8): Praefix 99 (kein Schluessel der Anlage 1, hinter einem
     * BSNR-Kontextwort ein Fund niedriger Stufe), Stellen 3 bis 7 gleich 00000, Stellen 8 und 9 gleich 1 und 2. LANR:
     * Summe 4*9 + 9*9 = 117, erwartete Stelle 7 waere 3, ist 0 (ungueltig). IK: Stelle 8 ungleich Stelle 9 (ungueltig).
     */
    public static final String BSNR_NETZVERBUND = "990000012";

    /**
     * Sonderkennung 35 (Paragraph 6 Absatz 3), Stellen 3 und 4 gleich 00 (kein Schluessel der Anlage 1, also
     * unbekannt), Stellen 8 und 9 gleich 1 und 2. LANR: Summe 4*3 + 9*5 = 57, erwartete Stelle 7 waere 3, ist 0
     * (ungueltig). IK: Stelle 8 ungleich Stelle 9 (ungueltig).
     */
    public static final String BSNR_SONDER_35 = "350000012";

    /**
     * Sonderkennung 75 wie {@link #BSNR_SONDER_35}. LANR: Summe 4*7 + 9*5 = 73, erwartete Stelle 7 waere 7, ist 0
     * (ungueltig). IK: Stelle 8 ungleich Stelle 9 (ungueltig).
     */
    public static final String BSNR_SONDER_75 = "750000012";

    /**
     * Sonderkennung 35 mit bekanntem Schluessel (Paragraph 6 Absatz 3): Praefix 35, Schluessel 01 an Stelle 3 und 4
     * (Anlage 1, also bekannt), Stellen 5 bis 9 gleich 00000. LANR: Summe 4*3 + 9*5 + 4*0 + 9*1 = 66, erwartete
     * Stelle 7 waere 4, ist 0 (ungueltig). IK: Summe aus Stelle 3 bis 8 (0, 1, 0, 0, 0, 0 mit Gewichten 2, 1, 2, 1, 2,
     * 1) gleich 1, erwartete Stelle 9 waere 1, ist 0 (ungueltig).
     */
    public static final String BSNR_SONDER_35_SCHLUESSEL = "350100000";

    /**
     * Sonderkennung 75 mit bekanntem Schluessel wie {@link #BSNR_SONDER_35_SCHLUESSEL}. LANR: Summe 4*7 + 9*5 + 4*0 +
     * 9*1 = 82, erwartete Stelle 7 waere 8, ist 0 (ungueltig). IK: wie oben, Summe 1, erwartet 1, ist 0 (ungueltig).
     */
    public static final String BSNR_SONDER_75_SCHLUESSEL = "750100000";

    // ------------------------------------------------------------------ Trennerformen (AK-6)

    /** LANR mit Leerzeichen, Gruppen 3-3-3; ohne Trenner {@link #LANR_GUELTIG}. */
    public static final String LANR_TRENNER_LEERZEICHEN = "001 235 101";

    /** LANR mit Leerzeichen, ungleiche Gruppen 7-2; ohne Trenner {@link #LANR_GUELTIG}. */
    public static final String LANR_TRENNER_UNGLEICHE_GRUPPEN = "0012351 01";

    /** LANR mit geschuetztem Leerzeichen (NBSP), Gruppen 3-3-3; ohne Trenner {@link #LANR_GUELTIG}. */
    public static final String LANR_TRENNER_NBSP = "001\u00A0235\u00A0101";

    /** LANR mit Schraegstrich, Gruppen 3-3-3; ohne Trenner {@link #LANR_GUELTIG}. */
    public static final String LANR_TRENNER_SCHRAEGSTRICH = "001/235/101";

    /** IK mit Bindestrich, Gruppen 3-3-3; ohne Trenner {@link #IK_GUELTIG}. */
    public static final String IK_TRENNER_BINDESTRICH = "001-234-561";

    /** IK mit Punkt, Gruppen 3-3-3; ohne Trenner {@link #IK_GUELTIG}. */
    public static final String IK_TRENNER_PUNKT = "001.234.561";

    /** IK mit Leerzeichen, Gruppen 6-3; ohne Trenner {@link #IK_GUELTIG}. */
    public static final String IK_TRENNER_GRUPPE_6_3 = "001234 561";

    /** IK mit geschuetztem Leerzeichen (NBSP), Gruppen 3-3-3; ohne Trenner {@link #IK_GUELTIG}. */
    public static final String IK_TRENNER_NBSP = "001\u00A0234\u00A0561";

    /** Neun Ziffern mit Punkten, zugleich gueltige IPv4 im Dokumentationsbereich 198.51.100.0/24 (AK-18). */
    public static final String IK_FORM_DOKU_IP = "198.51.100.1";

    // ------------------------------------------------------------------ Laengenproben (AK-11)

    /** Fuenf Ziffern: zu kurz (unter 6). */
    public static final String LAENGE_5 = "12345";

    /** Sechs Ziffern: untere Grenze der Laengenregel. */
    public static final String LAENGE_6 = "001235";

    /** Sieben Ziffern (AK-11, LANR). */
    public static final String LAENGE_7 = "0012351";

    /** Acht Ziffern (AK-11, IK). */
    public static final String LAENGE_8 = "00123456";

    /** Zehn Ziffern (AK-11, LANR). */
    public static final String LAENGE_10 = "0012351012";

    /** Zwoelf Ziffern: obere Grenze der Laengenregel. */
    public static final String LAENGE_12 = "001235101234";

    /** Dreizehn Ziffern: zu lang (ueber 12). */
    public static final String LAENGE_13 = "1234567890123";

    /** Vier Ziffern (AK-11: {@code IK 1234}). */
    public static final String LAENGE_4 = "1234";

    /** Telefonnummer-Probe mit Trennern, zehn Ziffern (AK-10); keine KV-Nummer. */
    public static final String TELEFON_PROBE = "0123 456 789";

    // ------------------------------------------------------------------ Gesamtliste

    /** Alle Nummern mit neun Ziffern (ohne Trennerformen und Doku-IP), gueltige und ungueltige, samt Markern. */
    public static List<String> alleNeunstelligenNummern() {
        return List.of(LANR_GUELTIG, LANR_GUELTIG_FACHGRUPPE, LANR_GUELTIG_PRUEFZIFFER_NULL, LANR_GUELTIG_ZWEI,
                LANR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, LANR_UNGUELTIG_VERTAUSCHT, LANR_UNGUELTIG_ZIFFER_GEAENDERT,
                IK_GUELTIG, IK_GUELTIG_ZWEI, IK_GUELTIG_DREI, IK_UNGUELTIG_KOMPLEMENT, IK_UNGUELTIG_NULL,
                IK_UNGUELTIG_VERTAUSCHT, LANR_MARKER, IK_MARKER);
    }

    /** Alle gueltigen KVNR-Vektoren (Buchstabe und neun Ziffern) samt Marker und Lehrbeispiel. */
    public static List<String> alleKvnrGueltig() {
        return List.of(KVNR_LEHRBEISPIEL, KVNR_GUELTIG, KVNR_GUELTIG_ZWEI, KVNR_GUELTIG_BUCHSTABE_C, KVNR_MARKER,
                KVNR_NULL);
    }

    /** Alle ungueltigen KVNR-Vektoren (Buchstabe und neun Ziffern, Pruefziffer falsch). */
    public static List<String> alleKvnrUngueltig() {
        return List.of(KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN, KVNR_UNGUELTIG_VERTAUSCHT,
                KVNR_UNGUELTIG_ZIFFER_GEAENDERT);
    }

    /** BSNR mit bekanntem Regionalcode (Zahl der Liste im Produkt: 17, 52, 43), neun Ziffern. */
    public static List<String> alleBsnrBekannt() {
        return List.of(BSNR_BEKANNT, BSNR_BEKANNT_ZWEI, BSNR_MARKER);
    }

    /** BSNR-Vektoren ohne bekannten Regionalcode oder mit Laengenabweichung (unbekannt, acht und zehn Ziffern). */
    public static List<String> alleBsnrUngueltig() {
        return List.of(BSNR_UNBEKANNT, BSNR_ACHT_ZIFFERN, BSNR_ZEHN_ZIFFERN);
    }

    /** Alle BSNR-Nummern mit neun Ziffern: bekannte, unbekannte (auch die Nullnummer) und der Marker. */
    public static List<String> alleBsnrNeunstellig() {
        return List.of(BSNR_BEKANNT, BSNR_BEKANNT_ZWEI, BSNR_UNBEKANNT, BSNR_NULL, BSNR_MARKER);
    }

    // ------------------------------------------------------------------ unabhaengige Referenzpruefungen

    /** Gewicht je Stelle (Index 0 = Stelle 1) fuer LANR; Stellen 7 bis 9 gehen nicht in die Summe ein. */
    private static final int[] LANR_GEWICHTE = {4, 9, 4, 9, 4, 9, 0, 0, 0};

    /** Gewicht je Stelle (Index 0 = Stelle 1) fuer IK; Klassifikation und Pruefziffer gehen nicht ein. */
    private static final int[] IK_GEWICHTE = {0, 0, 2, 1, 2, 1, 2, 1, 0};

    /** Genau neun ASCII-Ziffern? */
    private static boolean istNeunZiffern(String s) {
        if (s == null || s.length() != 9) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static void verlangeNeunZiffern(String s) {
        if (!istNeunZiffern(s)) {
            throw new IllegalArgumentException("genau neun Ziffern erwartet");
        }
    }

    /** Gewichtete Summe der Stellen 1 bis 6 (LANR): Beispiel {@link #LANR_GUELTIG} ergibt 79. */
    public static int lanrGewichtsSumme(String nummer) {
        verlangeNeunZiffern(nummer);
        int summe = 0;
        for (int i = 0; i < 9; i++) {
            summe += LANR_GEWICHTE[i] * (nummer.charAt(i) - '0');
        }
        return summe;
    }

    /** Summe der Quersummen der gewichteten Stellen 3 bis 8 (IK): Beispiel {@link #IK_GUELTIG} ergibt 21. */
    public static int ikQuersummenSumme(String nummer) {
        verlangeNeunZiffern(nummer);
        int summe = 0;
        for (int i = 0; i < 9; i++) {
            int produkt = IK_GEWICHTE[i] * (nummer.charAt(i) - '0');
            summe += produkt / 10 + produkt % 10;
        }
        return summe;
    }

    /**
     * Unabhaengige Referenz LANR (Gewichtstabelle statt Zeilenformel des Produkts): gueltig, wenn die Nummer aus genau
     * neun ASCII-Ziffern besteht und Summe + Stelle 7 durch 10 teilbar ist. Alles andere (auch {@code null}) ist
     * ungueltig, ohne Ausnahme.
     */
    public static boolean lanrGueltigReferenz(String nummer) {
        if (!istNeunZiffern(nummer)) {
            return false;
        }
        return (lanrGewichtsSumme(nummer) + (nummer.charAt(6) - '0')) % 10 == 0;
    }

    /**
     * Unabhaengige Referenz IK (Lesart "Rest"): gueltig, wenn die Nummer aus genau neun ASCII-Ziffern besteht und die
     * Quersummensumme modulo 10 gleich Stelle 9 ist. Alles andere (auch {@code null}) ist ungueltig, ohne Ausnahme.
     */
    public static boolean ikGueltigReferenz(String nummer) {
        if (!istNeunZiffern(nummer)) {
            return false;
        }
        return ikQuersummenSumme(nummer) % 10 == nummer.charAt(8) - '0';
    }

    // ------------------------------------------------------------------ rechnerische Bildung

    /** Bildet rechnerisch eine nach {@link #lanrGueltigReferenz} gueltige LANR mit Praefix {@code 00}. */
    public static String bildeLanr(Random zufall) {
        StringBuilder sb = new StringBuilder("00");
        for (int i = 0; i < 4; i++) {
            sb.append(zufall.nextInt(10));
        }
        sb.append('0').append(zufall.nextInt(10)).append(zufall.nextInt(10));
        int summe = lanrGewichtsSumme(sb.toString());
        int pruefziffer = 0;
        while ((summe + pruefziffer) % 10 != 0) {
            pruefziffer++;
        }
        sb.setCharAt(6, (char) ('0' + pruefziffer));
        return sb.toString();
    }

    /** Bildet rechnerisch ein nach {@link #ikGueltigReferenz} gueltiges IK mit Praefix {@code 00}. */
    public static String bildeIk(Random zufall) {
        StringBuilder sb = new StringBuilder("00");
        for (int i = 0; i < 6; i++) {
            sb.append(zufall.nextInt(10));
        }
        sb.append('0');
        int rest = ikQuersummenSumme(sb.toString()) % 10;
        sb.setCharAt(8, (char) ('0' + rest));
        return sb.toString();
    }

    // ------------------------------------------------------------------ Fremdnummern (Fehlalarm-Pruefstand 1b)

    /** Zahl der zufaelligen Ziffern einer Fremdnummer; davor stehen die vier Nullen (A-6, H-1). */
    private static final int FREMD_ZUFALLSZIFFERN = 4;

    /** Die ersten acht Ziffern einer Fremdnummer: {@code 0000} und vier zufaellige Ziffern. */
    private static String fremdAchtZiffern(Random zufall) {
        StringBuilder sb = new StringBuilder("0000");
        for (int i = 0; i < FREMD_ZUFALLSZIFFERN; i++) {
            sb.append(zufall.nextInt(10));
        }
        return sb.toString();
    }

    private static char fremdBuchstabe(Random zufall) {
        return (char) ('A' + zufall.nextInt(26));
    }

    /**
     * Bildet zur Laufzeit eine Fremdnummer der Form Grossbuchstabe {@code A-Z} und neun Ziffern: {@code 0000}, vier
     * zufaellige Ziffern und eine Pruefziffer, die nach {@code KvnrBsnrHilfen.kvnrPruefziffer} passt
     * ({@code gueltig}) oder sicher nicht passt (eine andere Ziffer als die berechnete). Die Nummer steht nie als
     * Literal im Code (Regel 4 des Plans kvnr-bsnr-erkennung).
     */
    public static String fremdKvnr(Random zufall, boolean gueltig) {
        char buchstabe = fremdBuchstabe(zufall);
        String acht = fremdAchtZiffern(zufall);
        int richtig = KvnrBsnrHilfen.kvnrPruefziffer(buchstabe, acht);
        int pruefziffer = gueltig ? richtig : (richtig + 1 + zufall.nextInt(9)) % 10;
        return buchstabe + acht + pruefziffer;
    }

    /**
     * Wie {@link #fremdKvnr}, aber die letzte Ziffer ist gleichverteilt (0 bis 9) und damit in etwa jedem zehnten Fall
     * die passende Pruefziffer (reine Pruefziffer-Erwartung des Abbruchkriteriums D-5, AK-16 c).
     */
    public static String zufallsKvnr(Random zufall) {
        char buchstabe = fremdBuchstabe(zufall);
        String acht = fremdAchtZiffern(zufall);
        return buchstabe + acht + zufall.nextInt(10);
    }
}
