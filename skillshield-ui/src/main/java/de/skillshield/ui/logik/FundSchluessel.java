package de.skillshield.ui.logik;

/** Schlüssel (Typname, Originalwert) für die Konsistenzregel des Kerns; {@code toString()} ohne Wert. */
record FundSchluessel(String typ, String wert) {

    @Override
    public String toString() {
        return "FundSchluessel[typ=" + typ + "]";
    }
}
