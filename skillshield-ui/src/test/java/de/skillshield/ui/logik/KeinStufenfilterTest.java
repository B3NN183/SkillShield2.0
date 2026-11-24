package de.skillshield.ui.logik;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.skillshield.core.model.ConfidenceLevel;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * AK-16, D-2: Die oeffentliche Schnittstelle der Sitzung bietet keine Funktion, die nach Stufe oder Konfidenz
 * an- oder abwaehlt, filtert, ausblendet oder alle abwaehlt. Der Test prueft die oeffentlichen Methoden per
 * Reflexion; das Verhalten (Tabelle zeigt immer alle Funde, alle angewaehlt) deckt {@code SitzungScanTest} ab.
 */
class KeinStufenfilterTest {

    private static final List<String> VERBOTENE_NAMENSTEILE = List.of(
            "abwaehl", "abwahl", "deselect", "filter", "ausblend", "verberg", "stufe", "konfidenz", "confidence",
            "schwelle", "sortier", "nurfraglich", "nursicher");

    private static List<Method> oeffentlicheMethoden() {
        List<Method> liste = new ArrayList<>();
        for (Method m : Sitzung.class.getMethods()) {
            if (m.getDeclaringClass() == Sitzung.class && Modifier.isPublic(m.getModifiers())) {
                liste.add(m);
            }
        }
        return liste;
    }

    @Test
    void keineOeffentlicheMethodeTraegtEinenFilterOderAbwahlNamen() {
        for (Method m : oeffentlicheMethoden()) {
            String name = m.getName().toLowerCase(Locale.ROOT);
            for (String teil : VERBOTENE_NAMENSTEILE) {
                assertFalse(name.contains(teil), m.getName());
            }
        }
    }

    @Test
    void keineMethodeNimmtStufeKonfidenzOderEinenFilterAlsParameter() {
        Set<Class<?>> verboten = Set.of(ConfidenceLevel.class, double.class, float.class, Double.class, Float.class,
                java.util.function.Predicate.class, java.util.function.Function.class,
                java.util.Comparator.class);
        for (Method m : oeffentlicheMethoden()) {
            for (Class<?> typ : m.getParameterTypes()) {
                assertFalse(verboten.contains(typ), m.getName() + " " + typ.getSimpleName());
            }
        }
    }

    @Test
    void keineMethodeWaehltMehrereFundeAufEinmalAb() {
        for (Method m : oeffentlicheMethoden()) {
            if (m.getName().equals("scanAbgeschlossen")) {
                continue; // nimmt das Scan-Ergebnis des Kerns entgegen, keine Auswahl
            }
            for (Class<?> typ : m.getParameterTypes()) {
                assertFalse(typ.isArray() || Collection.class.isAssignableFrom(typ), m.getName());
            }
        }
    }

    @Test
    void abwaehlenGehtNurEinzelnUeberAnwaehlenMitZeilenindex() {
        List<Method> anwaehlen = oeffentlicheMethoden().stream()
                .filter(m -> m.getName().equals("anwaehlen")).toList();

        assertEquals(1, anwaehlen.size());
        assertEquals(List.of(int.class, boolean.class), List.of(anwaehlen.get(0).getParameterTypes()));
        assertTrue(oeffentlicheMethoden().stream().anyMatch(m -> m.getName().equals("alleAnwaehlen")
                && m.getParameterCount() == 0));
    }

    @Test
    void selbsttest_dieNamenslisteSchlaegtBeiBekanntenFilterNamenAn() {
        assertTrue(VERBOTENE_NAMENSTEILE.stream().anyMatch("alleabwaehlen"::contains));
        assertTrue(VERBOTENE_NAMENSTEILE.stream().anyMatch("nurfraglichanwaehlen"::contains));
        assertTrue(VERBOTENE_NAMENSTEILE.stream().anyMatch("fundeausblenden"::contains));
        assertTrue(VERBOTENE_NAMENSTEILE.stream().anyMatch("nachstufefiltern"::contains));
    }
}
