package de.skillshield.core;

import static de.skillshield.core.SynthetischeKvNummern.IK_FORM_DOKU_IP;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.IK_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_GUELTIG_ZWEI;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN;
import static de.skillshield.core.SynthetischeKvNummern.KVNR_UNGUELTIG_VERTAUSCHT;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_6;
import static de.skillshield.core.SynthetischeKvNummern.LAENGE_8;
import static de.skillshield.core.SynthetischeKvNummern.LANR_GUELTIG;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gezielte Kollisionstexte fuer T-6 (Plan kvnr-bsnr-erkennung): KVNR-Form neben und ueber LANR-, IK-, IP-, E-Mail-,
 * URL- und Pfad-Funden, jeweils mit KVNR-Kontextwort in der Naehe. Alle Nummern sind die synthetischen Konstanten
 * aus {@link SynthetischeKvNummern}; der Laufzeit-Waechter prueft jeden Text. Die Mengen sind nach dem Fall
 * geordnet, den sie ausloesen sollen; die Zahl der tatsaechlich ausgeloesten Faelle misst
 * {@code KvnrScannerBestandTest}.
 *
 * <p>Die Menge {@link #ipGestalt()} enthaelt Texte mit einem Buchstaben vor einer IPv4-artigen Ziffernfolge hinter
 * einem KVNR-Kontextwort. Nach der Produktregel des Nachtrags zu T-6 ist der Buchstabe dort kein Nummernanfang, der
 * alte IP-Fund bleibt unveraendert, und es gibt keinen Kollisionsfall.
 */
public final class KvnrKollisionsTexte {

    private static final String BS = String.valueOf((char) 0x5C);
    private static final String EMAIL_ENDE = "@example.invalid";

    private KvnrKollisionsTexte() {
    }

    private static String kleinNummer(String kvnr) {
        return kvnr.toLowerCase(Locale.ROOT);
    }

    /** Buchstabe, ein Leerzeichen und die neun Ziffern in Dreiergruppen, je zwei Gruppen durch den Trenner getrennt. */
    private static String gruppiert(String kvnr, String trenner) {
        String z = kvnr.substring(1);
        return kvnr.charAt(0) + " " + z.substring(0, 3) + trenner + z.substring(3, 6) + trenner + z.substring(6);
    }

    /** Texte mit LANR-/IK-Kontextwort vor einer KVNR-Form (K-1) und gleichzeitigem KVNR-Kontextwort. */
    public static List<String> k1() {
        List<String> t = new ArrayList<>();
        t.add("KVNR LANR " + KVNR_GUELTIG);
        t.add("KVNR IK " + KVNR_GUELTIG);
        t.add("LANR " + KVNR_GUELTIG + " KVNR");
        t.add("IK Versichertennummer " + KVNR_GUELTIG);
        t.add("LANR " + LANR_GUELTIG + ", KVNR " + KVNR_GUELTIG);
        t.add("KVNR " + KVNR_GUELTIG + ", LANR " + LANR_GUELTIG);
        t.add("LANR " + LANR_GUELTIG + "/KVNR " + KVNR_GUELTIG_ZWEI);
        t.add("IK " + IK_GUELTIG + "/KVNR " + KVNR_GUELTIG);
        t.add("LANR/KVNR " + LANR_GUELTIG + "/" + KVNR_GUELTIG);
        t.add("KVNR/LANR " + KVNR_GUELTIG + "/" + LANR_GUELTIG);
        t.add("KVNR/IK " + KVNR_GUELTIG + "/" + IK_GUELTIG_ZWEI);
        t.add("KVNR LANR " + KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN);
        t.add("LANR " + KVNR_UNGUELTIG_VERTAUSCHT + " KVNR");
        t.add("KVNR IK " + KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN);
        t.add("kvnr lanr " + kleinNummer(KVNR_GUELTIG));
        t.add("eGK-Nummer IK " + KVNR_GUELTIG_ZWEI);
        t.add("Versichertennr. LANR " + KVNR_GUELTIG);
        t.add("Krankenversichertennummer: LANR: " + KVNR_GUELTIG);
        t.add("KVNR\nLANR " + KVNR_GUELTIG);
        t.add("KVNR LANR " + gruppiert(KVNR_GUELTIG, "-"));
        t.add("KVNR IK " + gruppiert(KVNR_GUELTIG_ZWEI, " "));
        t.add("Patient KVNR " + KVNR_GUELTIG + " (LANR " + KVNR_GUELTIG_ZWEI + ")");
        t.add("K V N R LANR " + KVNR_GUELTIG);
        t.add("K. V. N. R. IK " + KVNR_GUELTIG);
        return t;
    }

    /** Texte mit gleichem Bereich und gleicher Konfidenz bei LANR und KVNR (K-2, Laenge acht Ziffern). */
    public static List<String> k2() {
        List<String> t = new ArrayList<>();
        t.add("Versichertennummer LANR " + LAENGE_8);
        t.add("LANR Versichertennummer " + LAENGE_8);
        t.add("LANR " + LAENGE_8 + ", KVNR");
        t.add("eGK-Nummer LANR " + LAENGE_8);
        t.add("KVNR LANR " + LAENGE_8 + " und weiter");
        return t;
    }

    /**
     * Texte mit Teilueberlappung zwischen KV-Arten (K-4): Ziffernbloecke, an die ein Kontextwort ohne Trenner
     * geklebt ist, ergeben eine Folge von LANR-/IK- und KVNR-Kandidaten, die sich teilweise ueberdecken. Seit
     * Nachtrag 2 zu T-6 ist K-4 nur noch zwischen KV-Arten moeglich (Funde von E-Mail, IP, URL und Pfad bleiben
     * unveraendert).
     */
    public static List<String> k4() {
        String z = KVNR_GUELTIG.substring(1);
        String lanrEnde = LANR_GUELTIG.substring(6);
        String lanrMitte = LANR_GUELTIG.substring(3, 6);
        String zwoelf = LANR_GUELTIG.substring(2, 4);
        List<String> t = new ArrayList<>();
        t.add("LANR: Versichertennummer" + lanrEnde + " " + lanrMitte + " " + z + ".");
        t.add("IK:KVNR" + LAENGE_8 + " " + lanrEnde + " " + z);
        t.add("LANR:Versichertennummer0000 " + lanrMitte + "/" + z);
        t.add("Versichertennummer" + zwoelf + z.substring(0, 4) + " " + lanrEnde + " " + z + " LANR:-");
        t.add("LANR KVNR" + z.substring(0, 4) + " " + LAENGE_6.substring(0, 3) + " " + zwoelf + " " + zwoelf + ".");
        return t;
    }

    /**
     * Texte mit gesperrtem Kontextwort ({@code K V N R}) und ungueltiger KVNR: Das Kontextwort ist kein
     * zusammenhaengendes Wort, und es steht keine enge KVNR-Form im Fenster; die Naehe ergibt sich nach der
     * eingefrorenen Pruefregel nur ueber den Buchstabenstrom.
     */
    public static List<String> verstreut() {
        List<String> t = new ArrayList<>();
        t.add("K V N R LANR " + KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN);
        t.add("K. V. N. R. IK " + KVNR_UNGUELTIG_VERTAUSCHT);
        t.add("Patient K V N R, LANR " + KVNR_UNGUELTIG_PRUEFZIFFER_VERSCHOBEN);
        return t;
    }

    /**
     * Texte, in denen die KVNR-Form neben oder in einem alten Fund liegt: KVNR neben LANR, IK und IP, in E-Mail,
     * URL und Pfad. Nur in den meisten ist der alte Fund unveraendert; die Messung entscheidet.
     */
    public static List<String> nebeneinander() {
        List<String> t = new ArrayList<>();
        t.add("IK " + IK_GUELTIG + ", KVNR");
        t.add("Versichertennummer " + IK_GUELTIG + ", IK");
        t.add("IK " + IK_FORM_DOKU_IP + ", KVNR");
        t.add("KVNR " + KVNR_GUELTIG + " " + IK_FORM_DOKU_IP);
        t.add("KVNR " + IK_FORM_DOKU_IP + " " + KVNR_GUELTIG);
        t.add("KVNR " + KVNR_GUELTIG + EMAIL_ENDE);
        t.add("KVNR " + KVNR_GUELTIG + " " + KVNR_GUELTIG_ZWEI + EMAIL_ENDE);
        t.add("KVNR https://example.invalid/?kvnr=" + KVNR_GUELTIG);
        t.add("KVNR C:" + BS + "Praxis" + BS + KVNR_GUELTIG + BS + "x.txt");
        t.add("KVNR " + KVNR_GUELTIG + ".pdf");
        t.add("KVNR " + LANR_GUELTIG + " LANR " + LANR_GUELTIG);
        t.add("LANR " + LANR_GUELTIG + ", KVNR");
        t.add("KVNR " + KVNR_GUELTIG + ", IK " + IK_GUELTIG + ", LANR " + LANR_GUELTIG);
        t.add("Mail a@example.invalid, KVNR " + KVNR_GUELTIG + ", Server " + IK_FORM_DOKU_IP);
        return t;
    }

    /**
     * Texte mit KVNR-Kontextwort, einem Buchstaben (direkt, mit Trenner, mit unsichtbarem Zeichen davor) und einer
     * Ziffernfolge in IPv4-Gestalt, auch als Anfang laengerer Ketten (Nachtrag zu T-6). Erwartet: Verstoesse 0, keine
     * Kollision; der alte IP-Fund bleibt in Bereich, Typ und Konfidenz erhalten.
     */
    public static List<String> ipGestalt() {
        String ip = IK_FORM_DOKU_IP;
        List<String> t = new ArrayList<>();
        t.add("KVNR Z" + ip);
        t.add("KVNR z" + ip);
        t.add("KVNR Z " + ip);
        t.add("KVNR Z-" + ip);
        t.add("KVNR Z/" + ip);
        t.add("KVNR Z." + ip);
        t.add("KVNR: Z " + ip + " Ende");
        t.add("Versichertennummer Z" + ip);
        t.add("eGK-Nummer Z " + ip);
        t.add("KV-Nr. Z" + ip);
        t.add("KV-Nr. Z " + ip);
        t.add("Patient KVNR Z" + ip + ", Befund");
        t.add("KVNR" + (char) 0x200B + " Z" + ip);
        t.add("KVNR " + (char) 0x200B + "Z" + ip);
        t.add("K V N R Z" + ip);
        t.add("K V N R Z " + ip);
        t.add("KVNR Z" + ip + " " + LAENGE_6);
        t.add("KVNR Z " + ip + " 12");
        t.add("KVNR Z" + ip + ".5");
        t.add("KVNR Z" + ip + ".5.6");
        t.add("KVNR z" + ip + ".5");
        t.add("Versichertennummer Z" + ip + ".5");
        t.add("K V N R Z" + ip + ".5");
        t.add("KVNR Z " + ip + ".5.6.7");
        t.add("Server " + ip + ", KVNR Z" + ip);
        t.add("KVNR Z" + ip + " und KVNR Y " + ip);
        return t;
    }

    /**
     * Objekte, bei denen ein alter Fund von E-Mail, IP, URL oder Pfad mit Buchstabe und Ziffern davor, darin und
     * dahinter zusammentrifft (Nachtrag 2 zu T-6): IP mit {@code /Praefix}, IP, Leerzeichen und Ziffern, IP-Ketten mit
     * langem erstem Block und mit vierstelliger vierter Gruppe, E-Mail, URL, Pfad und Dateiname.
     */
    private static List<String> bestandObjekte() {
        String ip = IK_FORM_DOKU_IP;
        String ipAnfang = ip.substring(0, ip.lastIndexOf('.') + 1);
        String ziffern = KVNR_GUELTIG.substring(1);
        List<String> o = new ArrayList<>();
        for (String praefix : List.of("/24", "/8", "/32")) {
            o.add(ip + praefix);
            o.add("Z" + ip + praefix);
            o.add("Z " + ip + praefix);
            o.add(ip + praefix + "Praxis");
        }
        o.add(ip + " " + LAENGE_6);
        o.add(ip + " 12");
        o.add(ip + " " + ziffern);
        o.add(ip + " " + KVNR_GUELTIG);
        o.add("Z" + ip + " " + LAENGE_6);
        o.add("Z" + ip + " " + ziffern);
        o.add("Z" + ip + ":8080");
        o.add("1234." + ip);
        o.add("12345." + ip);
        o.add("Z1234." + ip);
        o.add("Z " + LAENGE_6 + "." + ip);
        o.add("Z" + ipAnfang + "1234." + ip);
        o.add("Z" + ipAnfang + "1234.1.2.3.4");
        o.add(ipAnfang + "1234." + ip);
        o.add("a." + KVNR_GUELTIG + EMAIL_ENDE);
        o.add(KVNR_GUELTIG + EMAIL_ENDE);
        o.add("Z " + ziffern + EMAIL_ENDE);
        o.add("a" + ziffern + EMAIL_ENDE);
        o.add("a" + EMAIL_ENDE + " " + KVNR_GUELTIG);
        o.add("a" + EMAIL_ENDE + KVNR_GUELTIG);
        o.add("a" + EMAIL_ENDE + " Z " + ziffern);
        o.add("a" + KVNR_GUELTIG_ZWEI + "@x" + KVNR_GUELTIG + ".invalid");
        o.add("https://example.invalid/" + KVNR_GUELTIG);
        o.add("https://example.invalid/?id=" + ziffern);
        o.add("https://example.invalid/x Z " + ziffern);
        o.add(KVNR_GUELTIG + "https://example.invalid/x");
        o.add("https://example.invalid/Z" + ziffern + "/" + KVNR_GUELTIG_ZWEI);
        o.add("C:" + BS + "Praxis" + BS + KVNR_GUELTIG + ".txt");
        o.add("C:" + BS + "Praxis" + BS + "Z " + ziffern + ".txt");
        o.add("/home/praxis/" + KVNR_GUELTIG + ".txt");
        o.add("/home/praxis/Z" + ziffern + "/x.txt");
        o.add(KVNR_GUELTIG + ".pdf");
        o.add("Z " + ziffern + ".pdf");
        o.add("befund-" + KVNR_GUELTIG + ".pdf");
        o.add("befund_" + ziffern + ".pdf");
        o.add(KVNR_GUELTIG + ".pdf " + KVNR_GUELTIG_ZWEI);
        return o;
    }

    /**
     * Texte fuer das Zuschneiden (Nachtrag 3 zu T-6): KVNR-Kontextwort, ein Pfad oder eine URL ohne
     * Anfuehrungszeichen, deren alter Fund am Buchstaben der Nummer endet, dann Leerzeichen oder geschuetztes
     * Leerzeichen und die neun Ziffern (geschlossen und gruppiert); dazu die Form "Kontextwort Nummer, Datei
     * Pfad mit derselben Nummer" und Texte an der Drei-Ziffern-Schwelle (Reststuecke mit zwei und mit drei
     * Ziffern). Erwartet: jeder alte Fund bleibt unveraendert, die Ziffern ausserhalb sind durch einen Restfund
     * (Quelle {@code kvnr-rest}) abgedeckt, soweit das Reststueck mindestens drei Ziffern hat.
     */
    public static List<String> zuschnitt() {
        String buchstabe = KVNR_GUELTIG.substring(0, 1);
        String z = KVNR_GUELTIG.substring(1);
        List<String> formen = List.of(z, z.substring(0, 3) + " " + z.substring(3, 6) + " " + z.substring(6));
        List<String> objekte = List.of("C:" + BS + "Praxis" + BS, "C:/Praxis/", BS + BS + "srv" + BS + "share" + BS,
                "/home/praxis/", "file:///C:/Praxis/", "https://example.invalid/?kvnr=");
        List<String> t = new ArrayList<>();
        for (String kontext : List.of("KVNR: ", "Versichertennummer ", "KV-Nr. ")) {
            for (String objekt : objekte) {
                for (String trenner : List.of(" ", String.valueOf((char) 0xA0))) {
                    for (String form : formen) {
                        t.add(kontext + objekt + buchstabe + trenner + form);
                    }
                }
            }
            t.add(kontext + KVNR_GUELTIG + ", Datei C:" + BS + "Praxis" + BS + buchstabe + " " + z);
        }
        String ip = "192.0.2.1";
        t.add("KVNR 123 " + ip);
        t.add("KVNR 12 " + ip);
        t.add("KVNR 12 " + ip + " 123");
        t.add("KVNR 12 " + ip + " 45");
        t.add("KVNR " + IK_FORM_DOKU_IP + "/24");
        return t;
    }

    /**
     * Die Objekte aus {@link #bestandObjekte()} hinter dem KVNR-Kontextwort, hinter {@code KV-Nr.}, hinter einem
     * gesperrt geschriebenen Kontextwort und mit nachgestelltem Kompositum. Erwartet: jeder alte Fund von E-Mail,
     * IP, URL und Pfad steht unveraendert in der neuen Liste (Nachtrag 2 zu T-6); dazu die Texte aus
     * {@link #zuschnitt()} (Nachtrag 3 zu T-6).
     */
    public static List<String> bestandVorrang() {
        List<String> t = new ArrayList<>();
        for (String objekt : bestandObjekte()) {
            t.add("KVNR " + objekt);
            t.add("Versichertennummer: " + objekt + " Ende");
            t.add("KV-Nr. " + objekt);
            t.add("K V N R " + objekt);
            t.add(objekt + " (Patienten-KVNR)");
            t.add(objekt + ", Pat.-KVNR");
        }
        t.addAll(zuschnitt());
        return t;
    }

    /** Alle gezielten Kollisionstexte. */
    public static List<String> alle() {
        List<String> t = new ArrayList<>(k1());
        t.addAll(k2());
        t.addAll(k4());
        t.addAll(verstreut());
        t.addAll(nebeneinander());
        t.addAll(ipGestalt());
        t.addAll(bestandVorrang());
        return t;
    }
}
