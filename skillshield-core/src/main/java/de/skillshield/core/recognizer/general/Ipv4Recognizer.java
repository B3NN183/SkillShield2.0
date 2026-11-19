package de.skillshield.core.recognizer.general;

import de.skillshield.core.model.Finding;
import de.skillshield.core.model.FindingType;
import de.skillshield.core.recognizer.Recognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Erkennt IPv4-Adressen in Punktschreibweise rein per Zeichenprüfung (ohne Netzwerkbibliotheken, keine
 * Namensauflösung).
 *
 * <p>Suchverfahren (ein einziger Durchlauf von links nach rechts, kein Regex): Der Erkenner zerlegt den Text in
 * Zahlenketten. Eine Kette ist eine Folge von Ziffernblöcken ({@code [0-9]}), die durch je genau einen Punkt
 * getrennt sind, wobei hinter dem Punkt wieder eine Ziffer folgt. Ein Punkt ohne folgende Ziffer beendet die Kette
 * ({@code 192.0.2.10.} am Satzende: der Schlusspunkt gehört nicht zum Fund). Alles andere, auch Punkt-, Binde- oder
 * Unterstrichfolgen und Buchstaben direkt davor oder dahinter, begrenzt die Kette nur und verhindert den Fund nicht
 * ({@code IP:......10.0.0.1}, {@code -10.0.0.1}, {@code _10.0.0.1} ergeben die Adresse).
 *
 * <p>Ein Block ist ein gültiges Oktett, wenn er ein bis drei Ziffern hat und sein Wert höchstens 255 ist; führende
 * Nullen sind erlaubt ({@code 192.0.2.010}, {@code 192.000.002.010}), vier Ziffern ({@code 0010}) nicht.
 *
 * <p>Eine Kette ist genau dann ein Fund, wenn sie irgendwo mindestens vier aufeinanderfolgende gültige Blöcke
 * enthält. Der Fund ist stets die ganze Kette (lieber zu groß als gar nicht erkannt): {@code 192.0.2.10.52344}
 * (Adresse mit Port), {@code 19.11.2026.192.0.2.10.52344} (Adresse in der Mitte), {@code 3.192.0.2.10} und auch
 * Versionsnummern wie {@code 1.2.3.4.5} werden vollständig erfasst. Kein Fund sind Ketten mit weniger als vier
 * Blöcken ({@code 192.0.2}), mit genau vier Blöcken und einem ungültigen ({@code 300.1.1.1}, {@code 1.2.3.4567})
 * sowie längere Ketten ohne vier aufeinanderfolgende gültige Blöcke ({@code 300.300.1.300.300}). Da jeder Fund
 * genau eine ganze Kette ist, entstehen keine Teil- oder überlappenden Funde.
 *
 * <p>Laufzeit: linear, jedes Zeichen wird genau einmal besucht; keine Rekursion. Speicher je Kette konstant: Es
 * werden nur die aktuelle Länge der Folge gültiger Blöcke und ein Merkmal „vier erreicht“ mitgezählt. Konfidenz 0.85, Quelle
 * {@code ipv4}. Der Erkenner ist zustandslos.
 */
public final class Ipv4Recognizer implements Recognizer {

    /** Quellname der Funde. */
    public static final String SOURCE = "ipv4";
    /** Konfidenz der Funde (Stufe WAHRSCHEINLICH). */
    public static final double CONFIDENCE = 0.85;

    private static final int OCTETS = 4;
    private static final int MAX_OCTET_DIGITS = 3;
    private static final int MAX_OCTET_VALUE = 255;

    @Override
    public List<Finding> detect(String text) {
        Objects.requireNonNull(text, "text");
        List<Finding> result = new ArrayList<>();
        int n = text.length();
        int i = 0;
        while (i < n) {
            if (!isDigit(text.charAt(i))) {
                i++;
                continue;
            }
            int blocks = 0;
            int run = 0; // aufeinanderfolgende gültige Blöcke direkt vor dem aktuellen Kettenende
            boolean fourInARow = false; // wurde irgendwo in der Kette run >= 4 erreicht?
            int p = i;
            while (true) {
                int blockStart = p;
                p = skipDigits(text, p);
                blocks++;
                if (isValidOctet(text, blockStart, p)) {
                    run++;
                    if (run >= OCTETS) {
                        fourInARow = true;
                    }
                } else {
                    run = 0;
                }
                if (!continuesChain(text, p)) {
                    break;
                }
                p++; // der Punkt gehört zur Kette, da eine Ziffer folgt
            }
            if (blocks >= OCTETS && fourInARow) {
                result.add(new Finding(FindingType.IP, text.substring(i, p), i, p, CONFIDENCE, SOURCE));
            }
            i = p;
        }
        return result;
    }

    /** Position hinter der Ziffernfolge, die bei {@code from} beginnt. */
    private static int skipDigits(String text, int from) {
        int p = from;
        while (p < text.length() && isDigit(text.charAt(p))) {
            p++;
        }
        return p;
    }

    /** Wahr, wenn an {@code p} ein Punkt steht, auf den eine Ziffer folgt (die Kette also weitergeht). */
    private static boolean continuesChain(String text, int p) {
        return p + 1 < text.length() && text.charAt(p) == '.' && isDigit(text.charAt(p + 1));
    }

    /** Wahr, wenn {@code text[from, to)} (nur Ziffern, nicht leer) ein bis drei Ziffern mit Wert höchstens 255 ist. */
    private static boolean isValidOctet(String text, int from, int to) {
        int len = to - from;
        if (len < 1 || len > MAX_OCTET_DIGITS) {
            return false;
        }
        int value = 0;
        for (int k = from; k < to; k++) {
            value = value * 10 + (text.charAt(k) - '0');
        }
        return value <= MAX_OCTET_VALUE;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
