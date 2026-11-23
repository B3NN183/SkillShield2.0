package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.FindingType;
import java.util.Set;

/**
 * Konfiguration der KV-Nummernarten fuer den {@link KvNumberScanner}: Fundtyp, Konfidenz bei direktem Kontext,
 * Grundquelle, Kontextwoerter und Pruefziffernverfahren. Alle Kontextwoerter sind ASCII und klein geschrieben; der
 * Scanner faltet nur {@code A-Z} und vergleicht ganze Woerter, die aus ganzen Teilstuecken eines Laufs bestehen
 * (unsichtbare Zeichen wie Formatzeichen und Variantenwaehler trennen Teilstuecke und werden beim Vergleich
 * uebergangen).
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
 * {@code kvnr}, {@code kvnr-pruefziffer-ungueltig} und {@code kvnr-form-abweichend}.
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
            Set.of("kv"), true, "-pruefziffer-ungueltig", "-form-abweichend");

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

    /** Woerter (ASCII, klein), die allein ein Kontextwort sind. */
    Set<String> kontextwoerter() {
        return kontextwoerter;
    }

    /** Woerter (ASCII, klein), die nur mit Nachsatzwort {@code nr} oder {@code nummer} ein Kontextwort sind. */
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

    /** Hat die Nummer dieser Art einen Buchstaben vor den neun Ziffern (nur KVNR)? */
    boolean buchstabenform() {
        return buchstabenform;
    }

    /** Quelle bei neun Ziffern mit ungueltiger Pruefziffer. */
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
     * rufen ihn fuer die KVNR nie so auf).
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
     * {@link KvnrPruefziffer} (einzige Stelle des Verfahrens).
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
        };
    }
}
