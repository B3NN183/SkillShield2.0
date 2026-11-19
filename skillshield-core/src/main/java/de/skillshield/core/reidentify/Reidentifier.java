package de.skillshield.core.reidentify;

import de.skillshield.core.pseudonymize.PlaceholderMapping;
import de.skillshield.core.pseudonymize.PlaceholderSyntax;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Rückübersetzung: ersetzt jeden bekannten Platzhalter im Text durch seinen Originalwert.
 *
 * <p>Die Eingabe ist eine KI-Antwort und wird als nicht vertrauenswürdig behandelt. Die Ersetzung ist einpassig:
 * {@link PlaceholderSyntax#tokenize} läuft einmal über den Eingabetext, die Ausgabe entsteht per
 * {@link StringBuilder}; eingesetzte Originalwerte werden nie erneut durchsucht und nie als Ersetzungsmuster
 * gedeutet. Unbekannte und beschädigte Platzhalter bleiben unverändert stehen und werden gemeldet; es wird nichts
 * geraten. Fehlende Platzhalter sind kein Fehler. Meldungen und Ausnahmen enthalten keinen Klartext.
 */
public final class Reidentifier {

    /**
     * @param text    zurückkommender Text, nicht {@code null}
     * @param mapping Zuordnung, nicht {@code null}
     * @return rückübersetzter Text und Liste der nicht aufgelösten Platzhalter
     * @throws NullPointerException mit dem Parameternamen als Meldung, wenn ein Argument {@code null} ist
     */
    public ReidentificationResult restore(String text, PlaceholderMapping mapping) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(mapping, "mapping");
        List<PlaceholderSyntax.Token> tokens = PlaceholderSyntax.tokenize(text, 0, text.length(), mapping.typeNames());
        if (tokens.isEmpty()) {
            return new ReidentificationResult(text, List.of());
        }
        StringBuilder out = new StringBuilder(text.length());
        List<UnresolvedPlaceholder> unresolved = new ArrayList<>();
        int pos = 0;
        for (PlaceholderSyntax.Token token : tokens) {
            out.append(text, pos, token.start());
            pos = token.end();
            Optional<String> original = token.kind() == PlaceholderSyntax.Token.Kind.PLATZHALTER
                    ? mapping.resolve(token.canonical())
                    : Optional.empty();
            if (original.isPresent()) {
                out.append(original.get());
            } else {
                out.append(text, token.start(), token.end());
                unresolved.add(new UnresolvedPlaceholder(text.substring(token.start(), token.end()), token.start(),
                        token.kind() == PlaceholderSyntax.Token.Kind.PLATZHALTER
                                ? UnresolvedPlaceholder.Reason.UNBEKANNT
                                : UnresolvedPlaceholder.Reason.BESCHAEDIGT));
            }
        }
        out.append(text, pos, text.length());
        return new ReidentificationResult(out.toString(), unresolved);
    }
}
