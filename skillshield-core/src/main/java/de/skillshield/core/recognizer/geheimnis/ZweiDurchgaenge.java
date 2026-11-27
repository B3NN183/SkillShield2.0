package de.skillshield.core.recognizer.geheimnis;

import de.skillshield.core.model.Finding;
import java.util.ArrayList;
import java.util.List;

/**
 * Die zwei Durchgaenge der Kontextregel fuer eine Art (Plan Nachtrag "T-5 verkleinert", Punkt 2), gemeinsam fuer den
 * {@code PasswortRecognizer} und den {@code TokenRecognizer}: Der erste Durchgang ({@link KontextRegel}: K1 und
 * Zeilenumbruch) liefert seine Kandidaten unveraendert; der zweite ({@link SchwacheVerbinder}: K2 bis K4) meldet nur
 * Kandidaten, die keinen des ersten beruehren. Die Ergebnisse werden nach dem Beginn zusammengefuehrt; die Liste ist
 * aufsteigend und ueberlappungsfrei.
 */
final class ZweiDurchgaenge {

    private ZweiDurchgaenge() {
    }

    /** Die Kandidaten beider Durchgaenge fuer die Art, unveraenderlich, aufsteigend, ueberlappungsfrei. */
    static List<Finding> finde(String text, Art art) {
        List<Finding> erster = new ArrayList<>();
        KontextRegel.finde(text, art, erster);
        List<Finding> zweiter = new ArrayList<>();
        SchwacheVerbinder.finde(text, art, erster, zweiter);
        if (zweiter.isEmpty()) {
            return List.copyOf(erster);
        }
        List<Finding> kandidaten = new ArrayList<>(erster.size() + zweiter.size());
        int a = 0;
        int b = 0;
        while (a < erster.size() || b < zweiter.size()) {
            boolean ersterZuerst = b >= zweiter.size()
                    || (a < erster.size() && erster.get(a).start() < zweiter.get(b).start());
            kandidaten.add(ersterZuerst ? erster.get(a++) : zweiter.get(b++));
        }
        return List.copyOf(kandidaten);
    }
}
