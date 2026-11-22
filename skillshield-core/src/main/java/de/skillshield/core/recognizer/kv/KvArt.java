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
 * <p>Beide Eintraege werden vom {@code LanrRecognizer} bzw. {@code IkRecognizer} ueber den
 * {@link KvNumberScanner} genutzt.
 */
enum KvArt {

    /** Lebenslange Arztnummer; {@code arzt} ist nur mit Nachsatzwort {@code nr}/{@code nummer} ein Kontextwort. */
    LANR(FindingType.LANR, 0.95, "lanr", Set.of("lanr", "lanrnr", "arztnummer", "arztnr"), Set.of("arzt")),

    /** Institutionskennzeichen. */
    IK(FindingType.IK, 0.92, "ik", Set.of("ik", "iknr", "iknummer", "institutionskennzeichen"), Set.of());

    private final FindingType type;
    private final double confidenceDirekt;
    private final String source;
    private final Set<String> kontextwoerter;
    private final Set<String> vorwoerter;

    KvArt(FindingType type, double confidenceDirekt, String source, Set<String> kontextwoerter,
            Set<String> vorwoerter) {
        this.type = type;
        this.confidenceDirekt = confidenceDirekt;
        this.source = source;
        this.kontextwoerter = kontextwoerter;
        this.vorwoerter = vorwoerter;
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

    /** Quelle bei neun Ziffern mit ungueltiger Pruefziffer. */
    String sourcePruefzifferUngueltig() {
        return source + "-pruefziffer-ungueltig";
    }

    /** Quelle bei einer Laenge von 6 bis 12 Ziffern ausser neun. */
    String sourceLaengeAbweichend() {
        return source + "-laenge-abweichend";
    }

    /**
     * Prueft die Pruefziffer.
     *
     * @param d genau neun Werte 0 bis 9
     * @return {@code true}, wenn die Pruefziffer nach dem Verfahren dieser Art passt
     */
    boolean isValid(int[] d) {
        return switch (this) {
            case LANR -> KvCheckDigits.isValidLanr(d);
            case IK -> KvCheckDigits.isValidIk(d);
        };
    }
}
