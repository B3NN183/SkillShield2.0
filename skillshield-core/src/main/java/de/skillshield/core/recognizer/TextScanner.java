package de.skillshield.core.recognizer;

import de.skillshield.core.conflict.ConflictResolver;
import de.skillshield.core.model.Finding;
import de.skillshield.core.recognizer.general.EmailRecognizer;
import de.skillshield.core.recognizer.general.Ipv4Recognizer;
import de.skillshield.core.recognizer.general.UrlRecognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Scan: ruft alle Erkenner auf und macht die Kandidaten mit dem {@link ConflictResolver} überlappungsfrei.
 *
 * <p>Das Ergebnis ist unveränderlich, überlappungsfrei und aufsteigend nach {@code start} sortiert; es ist
 * unabhängig von der Reihenfolge der Erkenner. Der Scan verändert den Text nicht (er ist ein {@code String}) und
 * ersetzt nichts. Funde jeder Konfidenzstufe bleiben erhalten.
 */
public final class TextScanner {

    private final List<Recognizer> recognizers;

    /**
     * @param recognizers die Erkenner; die Liste wird kopiert, {@code null} und {@code null}-Elemente werden
     *                    abgelehnt
     */
    public TextScanner(List<? extends Recognizer> recognizers) {
        Objects.requireNonNull(recognizers, "recognizers");
        for (Recognizer r : recognizers) {
            Objects.requireNonNull(r, "recognizers enthält null");
        }
        this.recognizers = List.copyOf(recognizers);
    }

    /** Scanner mit den Standard-Erkennern (wächst mit den folgenden Tasks; bisher: E-Mail, IPv4, URL). */
    public static TextScanner standard() {
        return new TextScanner(List.of(new EmailRecognizer(), new Ipv4Recognizer(), new UrlRecognizer()));
    }

    /**
     * Durchsucht den Text.
     *
     * @param text der Text, nicht {@code null}
     * @return unveränderliche, überlappungsfreie Fundliste, aufsteigend nach {@code start}
     * @throws NullPointerException  bei {@code null} als Text (Meldung {@code text}) oder wenn ein Erkenner
     *                               {@code null} liefert
     * @throws IllegalStateException wenn ein Erkenner einen Fund liefert, der nicht zum Text passt (die Meldung
     *                               enthält keinen Fundwert), oder wenn ein Erkenner eine
     *                               {@link RuntimeException} wirft (die Meldung nennt nur Index und Klassenname;
     *                               die Ausnahme hat weder Ursache noch unterdrückte Ausnahmen).
     *                               Ein {@link Error} wird bewusst nicht gekapselt (TE-20): er zeigt einen
     *                               Zustand der JVM an, nicht einen Fehler des Erkenners; die Klartextfreiheit
     *                               der Erkenner sichern deren eigene Tests
     */
    public List<Finding> scan(String text) {
        Objects.requireNonNull(text, "text");
        List<Finding> candidates = new ArrayList<>();
        for (int i = 0; i < recognizers.size(); i++) {
            List<Finding> found = Objects.requireNonNull(detect(recognizers.get(i), i, text),
                    "Erkenner lieferte null");
            for (Finding f : found) {
                Objects.requireNonNull(f, "Erkenner lieferte null-Element");
                if (!matchesText(f, text)) {
                    throw new IllegalStateException("Erkenner lieferte einen Fund, der nicht zum Text passt");
                }
                candidates.add(f);
            }
        }
        return ConflictResolver.resolve(text, candidates);
    }

    /** Ruft den Erkenner auf und kapselt dessen Ausnahmen klartextfrei (keine Meldung, keine Ursache). */
    private static List<Finding> detect(Recognizer recognizer, int index, String text) {
        try {
            return recognizer.detect(text);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Erkenner an Index " + index + " fehlgeschlagen: "
                    + e.getClass().getName());
        }
    }

    private static boolean matchesText(Finding f, String text) {
        // regionMatches liefert false, wenn der Bereich über das Textende hinausreicht
        return text.regionMatches(f.start(), f.originalValue(), 0, f.originalValue().length());
    }
}
