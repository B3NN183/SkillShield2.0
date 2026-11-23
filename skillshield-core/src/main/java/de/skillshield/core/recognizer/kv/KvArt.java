package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.FindingType;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Konfiguration der KV-Nummernarten fuer den {@link KvNumberScanner}: Fundtyp, Konfidenz bei direktem Kontext,
 * Grundquelle, Kontextwoerter und Pruefziffernverfahren. Alle Kontextwoerter sind klein geschrieben und ASCII, nur die
 * der BSNR gibt es zusaetzlich mit dem Umlaut (U+00E4); der Scanner faltet {@code A-Z} und U+00C4 und vergleicht
 * ganze Woerter, die aus ganzen Teilstuecken eines Laufs bestehen (unsichtbare Zeichen wie Formatzeichen und
 * Variantenwaehler trennen Teilstuecke und werden beim Vergleich uebergangen).
 *
 * <p>Die Eintraege {@code LANR} und {@code IK} werden vom {@code LanrRecognizer} bzw. {@code IkRecognizer} ueber den
 * {@link KvNumberScanner} genutzt, {@code KVNR} vom {@code KvnrRecognizer}. Die Klasse wird nur additiv erweitert
 * (Plan kvnr-bsnr-erkennung, Regel 11): LANR und IK behalten Ordinal 0 und 1 und liefern in {@code type},
 * {@code confidenceDirekt}, {@code source}, {@code kontextwoerter}, {@code vorwoerter},
 * {@code sourcePruefzifferUngueltig}, {@code sourceLaengeAbweichend} und {@code isValid(int[])} dieselben Werte wie
 * zuvor; fuer sie ist {@code buchstabenform()} {@code false} und {@code nurBuchstabenVorwoerter()} leer.
 *
 * <p><b>KVNR.</b> Ein Buchstabe und neun Ziffern, die neunte Ziffer ist die Pruefziffer ({@link KvnrPruefziffer}).
 * Kontextwoerter: {@code kvnr}, {@code versichertennummer}, {@code versichertennr}, {@code krankenversichertennummer},
 * {@code krankenversichertennr}, {@code krankenversicherungsnummer}, {@code krankenversicherungsnr},
 * {@code egknummer}, {@code egknr}; Vorwoerter (mit Nachsatzwort {@code nr}/{@code nummer}): {@code versicherten},
 * {@code krankenversicherten}, {@code egk}, {@code kv}. Das Vorwort {@code kv} ({@code KV-Nr.}) erschliesst nur die
 * Buchstabenform. Keine Kontextwoerter sind {@code vknr}, {@code versicherungsnummer},
 * {@code rentenversicherungsnummer}, {@code kv} allein und {@code nr} allein. Die Quellen heissen
 * {@code kvnr}, {@code kvnr-pruefziffer-ungueltig} und {@code kvnr-form-abweichend}; die vierte Quelle
 * {@code kvnr-rest} gehoert nicht zu dieser Klasse, sie entsteht beim Zuschneiden im {@code TextScanner}.
 *
 * <p><b>BSNR.</b> Neun Ziffern, die Stellen 1 und 2 sind der Regionalcode ({@link BsnrRegionalcodes}), keine
 * Pruefziffer. Kontextwoerter (die Woerter mit {@code ae} gibt es auch mit dem Umlaut, einzige Stelle
 * {@code mitUmlaut}): {@code bsnr}, {@code bsnrnr}, {@code nbsnr}, {@code betriebsstaettennummer},
 * {@code betriebsstaettennr}, {@code nebenbetriebsstaettennummer}, {@code nebenbetriebsstaettennr}; Vorwoerter (mit
 * Nachsatzwort {@code nr}/{@code nummer}): {@code betriebsstaette}, {@code betriebsstaetten},
 * {@code nebenbetriebsstaette}, {@code nebenbetriebsstaetten}. Konfidenzen: 0,90 bei direktem Kontextwort und
 * bekanntem Regionalcode, 0,80 bei weiterem Kontext, nachgestelltem oder gesperrt geschriebenem Kontextwort, 0,60
 * nachgestellt hinter Komma oder Semikolon; unbekannter Regionalcode 0,50 (Quelle
 * {@code bsnr-regionalcode-unbekannt});
 * Laenge 6 bis 12 Ziffern ausser neun 0,40 (Quelle {@code bsnr-laenge-abweichend}); sonst Quelle {@code bsnr}. Die
 * Pruefung ({@code isValid}) nutzt die Vierziffern-Variante von {@link BsnrRegionalcodes}, damit die Sonderkennungen
 * 35 und 75 an den Stellen 3 und 4 gelten. Das nachgestellte Kompositum gilt fuer KVNR und BSNR
 * ({@link #nachgestelltesKompositum()}), nicht fuer LANR und IK.
 *
 * <p><b>Konfidenzen der KVNR</b> (Buchstabe und neun Ziffern mit gueltiger Pruefziffer): 0,95 bei direktem Kontextwort
 * ({@link #confidenceDirekt()}), 0,80 bei weiterem Kontext, nachgestelltem Kontextwort oder gesperrt geschriebenem
 * Kontextwort, 0,60 nachgestellt hinter Komma oder Semikolon; ungueltige Pruefziffer 0,50 (Quelle
 * {@link #sourcePruefzifferUngueltig()}); abweichende Form (Buchstabe und 6 bis 12 Ziffern ausser neun oder reine
 * Ziffernfolge) 0,40 (Quelle {@link #sourceLaengeAbweichend()}). Die bekannten Grenzen der KVNR (G-K1 bis G-K17, G-D2)
 * stehen im Javadoc von {@code KvNumberScanner}.
 */
enum KvArt {

    /** Lebenslange Arztnummer; {@code arzt} ist nur mit Nachsatzwort {@code nr}/{@code nummer} ein Kontextwort. */
    LANR(FindingType.LANR, 0.95, "lanr", Set.of("lanr", "lanrnr", "arztnummer", "arztnr"), Set.of("arzt")),

    /** Institutionskennzeichen. */
    IK(FindingType.IK, 0.92, "ik", Set.of("ik", "iknr", "iknummer", "institutionskennzeichen"), Set.of()),

    /** Krankenversichertennummer (Buchstabe und neun Ziffern, Pruefziffer ueber alle zehn Zeichen). */
    KVNR(FindingType.KVNR, 0.95, "kvnr",
            Set.of("kvnr", "versichertennummer", "versichertennr", "krankenversichertennummer",
                    "krankenversichertennr", "krankenversicherungsnummer", "krankenversicherungsnr",
                    "egknummer", "egknr"),
            Set.of("versicherten", "krankenversicherten", "egk", "kv"),
            Set.of("kv"), true, "-pruefziffer-ungueltig", "-form-abweichend"),

    /**
     * Betriebsstaettennummer (neun Ziffern, keine Pruefziffer). "Ungueltig" heisst hier "Regionalcode unbekannt"
     * ({@link BsnrRegionalcodes}); die Woerter stehen in ASCII mit {@code ae} und zusaetzlich mit dem Umlaut.
     */
    BSNR(FindingType.BSNR, 0.90, "bsnr",
            mitUmlaut("bsnr", "bsnrnr", "nbsnr", "betriebsstaettennummer", "betriebsstaettennr",
                    "nebenbetriebsstaettennummer", "nebenbetriebsstaettennr"),
            mitUmlaut("betriebsstaette", "betriebsstaetten", "nebenbetriebsstaette", "nebenbetriebsstaetten"),
            Set.of(), false, "-regionalcode-unbekannt", "-laenge-abweichend");

    private final FindingType type;
    private final double confidenceDirekt;
    private final String source;
    private final Set<String> kontextwoerter;
    private final Set<String> vorwoerter;
    private final Set<String> nurBuchstabenVorwoerter;
    private final boolean buchstabenform;
    private final String suffixPruefzifferUngueltig;
    private final String suffixLaengeAbweichend;

    KvArt(FindingType type, double confidenceDirekt, String source, Set<String> kontextwoerter,
            Set<String> vorwoerter) {
        this(type, confidenceDirekt, source, kontextwoerter, vorwoerter, Set.of(), false,
                "-pruefziffer-ungueltig", "-laenge-abweichend");
    }

    KvArt(FindingType type, double confidenceDirekt, String source, Set<String> kontextwoerter,
            Set<String> vorwoerter, Set<String> nurBuchstabenVorwoerter, boolean buchstabenform,
            String suffixPruefzifferUngueltig, String suffixLaengeAbweichend) {
        this.type = type;
        this.confidenceDirekt = confidenceDirekt;
        this.source = source;
        this.kontextwoerter = kontextwoerter;
        this.vorwoerter = vorwoerter;
        this.nurBuchstabenVorwoerter = nurBuchstabenVorwoerter;
        this.buchstabenform = buchstabenform;
        this.suffixPruefzifferUngueltig = suffixPruefzifferUngueltig;
        this.suffixLaengeAbweichend = suffixLaengeAbweichend;
    }

    /** Fundtyp der Funde dieser Art. */
    FindingType type() {
        return type;
    }

    /** Konfidenz bei direktem Kontextwort und gueltiger Pruefziffer. */
    double confidenceDirekt() {
        return confidenceDirekt;
    }

    /** Grundquelle der Funde, ohne Ziffern. */
    String source() {
        return source;
    }

    /**
     * Woerter, die allein ein Kontextwort sind (klein geschrieben; LANR, IK und KVNR in ASCII, bei der BSNR zu jedem
     * Wort mit {@code ae} zusaetzlich die Form mit dem Umlaut).
     */
    Set<String> kontextwoerter() {
        return kontextwoerter;
    }

    /**
     * Woerter, die nur mit Nachsatzwort {@code nr} oder {@code nummer} ein Kontextwort sind (klein geschrieben; LANR,
     * IK und KVNR in ASCII, bei der BSNR zu jedem Wort mit {@code ae} zusaetzlich die Form mit dem Umlaut).
     */
    Set<String> vorwoerter() {
        return vorwoerter;
    }

    /**
     * Teilmenge von {@link #vorwoerter()}, deren Kontext nur Nummern in Buchstabenform erschliesst ({@code kv} bei
     * der KVNR); leer fuer LANR und IK.
     */
    Set<String> nurBuchstabenVorwoerter() {
        return nurBuchstabenVorwoerter;
    }

    /**
     * Gilt das nachgestellte Kompositum ({@code N (Patienten-KVNR)}, {@code N, Pat.-BSNR}) als nachgestelltes
     * Kontextwort? Nur fuer KVNR und BSNR; fuer LANR und IK {@code false} (Verhalten wie die eingefrorene Kopie).
     */
    boolean nachgestelltesKompositum() {
        return switch (this) {
            case LANR, IK -> false;
            case KVNR, BSNR -> true;
        };
    }

    /**
     * Nimmt zu jedem Wort die Form auf, in der jedes {@code ae} durch den Umlaut (U+00E4) ersetzt ist; Woerter ohne
     * {@code ae} kommen einmal vor. Einzige Stelle der Umlautformen; der Quelltext bleibt ASCII.
     */
    private static Set<String> mitUmlaut(String... woerter) {
        Set<String> alle = new LinkedHashSet<>();
        for (String wort : woerter) {
            alle.add(wort);
            alle.add(wort.replace("ae", String.valueOf((char) 0xE4)));
        }
        return Set.copyOf(alle);
    }

    /** Hat die Nummer dieser Art einen Buchstaben vor den neun Ziffern (nur KVNR)? */
    boolean buchstabenform() {
        return buchstabenform;
    }

    /**
     * Quelle bei neun Ziffern mit ungueltiger Pruefziffer; bei der BSNR (keine Pruefziffer) die Quelle bei neun
     * Ziffern mit unbekanntem Regionalcode.
     */
    String sourcePruefzifferUngueltig() {
        return source + suffixPruefzifferUngueltig;
    }

    /** Quelle bei einer Laenge von 6 bis 12 Ziffern ausser neun (KVNR: abweichende Form). */
    String sourceLaengeAbweichend() {
        return source + suffixLaengeAbweichend;
    }

    /**
     * Prueft die Pruefziffer einer reinen Ziffernfolge ({@code isValid(buchstabe = 0, d)}); fuer die KVNR gibt es
     * keine reine Ziffernfolge, der Aufruf wirft dort eine {@link IllegalArgumentException} (Scanner und Erkenner
     * rufen ihn fuer die KVNR nie so auf). Fuer die BSNR (keine Pruefziffer) prueft der Aufruf, ob der Regionalcode
     * bekannt ist ({@link BsnrRegionalcodes}).
     *
     * <p><b>Falle fuer generische Aufrufer:</b> Wer ueber alle Arten schleift und {@code isValid(int[])} ruft, bekommt
     * bei {@link #KVNR} eine Ausnahme statt einer Antwort. Fuer jede Art, die auch die KVNR sein kann, gehoert der
     * Aufruf {@link #isValid(char, int[])} mit dem Buchstaben der Nummer.
     *
     * @param d genau neun Werte 0 bis 9
     * @return {@code true}, wenn die Pruefziffer nach dem Verfahren dieser Art passt
     */
    boolean isValid(int[] d) {
        return isValid((char) 0, d);
    }

    /**
     * Prueft die Pruefziffer. LANR und IK ignorieren den Buchstaben; die KVNR rechnet ueber
     * {@link KvnrPruefziffer} (einzige Stelle des Verfahrens). Die BSNR hat keine Pruefziffer und ignoriert den
     * Buchstaben ebenfalls: Sie prueft, ob der Regionalcode (Stellen 1 bis 4, wegen der Sonderkennungen 35 und 75)
     * bekannt ist.
     *
     * @param buchstabe der Buchstabe der Nummer ({@code A-Z}, {@code a-z}), {@code 0} bei reiner Ziffernfolge
     * @param d         genau neun Werte 0 bis 9
     * @return {@code true}, wenn die Pruefziffer nach dem Verfahren dieser Art passt
     */
    boolean isValid(char buchstabe, int[] d) {
        return switch (this) {
            case LANR -> KvCheckDigits.isValidLanr(d);
            case IK -> KvCheckDigits.isValidIk(d);
            case KVNR -> KvnrPruefziffer.istGueltig(buchstabe, d);
            case BSNR -> BsnrRegionalcodes.istBekannt(d[0], d[1], d[2], d[3]);
        };
    }
}
