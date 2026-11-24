package de.skillshield.ui.swing;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Element;
import javax.swing.text.PlainDocument;

/**
 * Mehrzeiliges Textfeld der Oberfläche. Ziehen aus dem Feld ist abgeschaltet; alle Kopier-, Ausschneide- und
 * Einfügewege installiert {@link Textaktionen}. Die Auswahl bleibt auch ohne Fokus sichtbar (der Anwender markiert
 * und klickt dann eine Schaltfläche).
 *
 * <p><b>Zeilenumbruch:</b> Swing bricht eine einzige sehr lange Zeile in quadratischer Zeit um (bei einer Million
 * Zeichen rund elf Sekunden je Breitenänderung, beim ersten Aufbau minutenlang, jeweils auf dem Ereignis-Thread).
 * Darum prüft das Dokument des Feldes nach jeder Einfügung die Länge der betroffenen Zeilen (nicht den ganzen Text)
 * und schaltet den Umbruch ab, sobald eine Zeile über {@link #UMBRUCH_GRENZE} liegt (waagerechter Rollbalken). Nach
 * einer Löschung wird der Umbruch wieder eingeschaltet, wenn der ganze Text höchstens {@link #UMBRUCH_GRENZE} Zeichen
 * hat (dann kann keine Zeile darüber liegen; das kostet keinen Durchlauf). Ein Text mit mehreren Zeilen, der nur
 * insgesamt länger ist, bleibt ohne Umbruch, bis er vollständig ersetzt oder das Feld zurückgesetzt wird.
 *
 * <p><b>Zurücksetzen:</b> {@link #zuruecksetzen()} ersetzt das Dokument durch ein neues. Ein bloßes Leeren ließe den
 * alten Text im Zeichenpuffer des alten Dokuments stehen. Die über {@link #aenderungenMelden} angemeldeten Hörer
 * werden am neuen Dokument wieder angemeldet.
 *
 * <p>Testhaken: {@link #fokusAnfragen()} zählt jede Anfrage, den Fokus in dieses Feld zu holen (AK-18: Hervorheben
 * einer Fundstelle zieht den Fokus nicht an sich).
 */
class TextBereich extends JTextArea {

    /**
     * Längste Zeile (in Zeichen), die noch umbrochen wird; darüber wird der Zeilenumbruch des Feldes abgeschaltet.
     */
    static final int UMBRUCH_GRENZE = 50_000;

    private final List<DocumentListener> hoerer = new ArrayList<>();
    private int fokusAnfragen;
    /** Solange ein ganzer Text ersetzt wird, darf die Löschung den Umbruch nicht zwischendurch einschalten. */
    private boolean ersetzt;

    TextBereich(String name, int zeilen) {
        super(zeilen, 40);
        setDocument(neuesDokument());
        setName(name);
        setLineWrap(true);
        setWrapStyleWord(true);
        setDragEnabled(false);
        BleibendeAuswahl caret = new BleibendeAuswahl();
        int rate = UIManager.getInt("TextArea.caretBlinkRate");
        caret.setBlinkRate(rate > 0 ? rate : 500);
        setCaret(caret);
    }

    /** {@code true}, wenn keine Zeile über der Grenze liegt (Zeilenende wie im Dokument: nur der Zeilenvorschub). */
    static boolean umbruchMoeglich(String text) {
        int zeile = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                zeile = 0;
            } else if (++zeile > UMBRUCH_GRENZE) {
                return false;
            }
        }
        return true;
    }

    /** Meldet jede Änderung des Dokuments (auch eines nach {@link #zuruecksetzen()} neuen) an die Aktion. */
    void aenderungenMelden(Runnable aktion) {
        DocumentListener neuerHoerer = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                aktion.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                aktion.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                aktion.run();
            }
        };
        hoerer.add(neuerHoerer);
        getDocument().addDocumentListener(neuerHoerer);
    }

    /**
     * Verwirft den Inhalt samt Zeichenpuffer: Hervorhebungen weg, ein neues Dokument mit denselben Hörern, Umbruch
     * wieder ein. Löst keine Dokumentereignisse aus; wer die Änderung braucht, meldet sie selbst.
     */
    void zuruecksetzen() {
        getHighlighter().removeAllHighlights();
        PlainDocument neu = neuesDokument();
        setDocument(neu);
        for (DocumentListener h : hoerer) {
            neu.addDocumentListener(h);
        }
        setLineWrap(true);
    }

    /** Ersetzt den ganzen Text und stellt vorher den Zeilenumbruch passend zur längsten Zeile ein. */
    void textSetzen(String text) {
        setLineWrap(umbruchMoeglich(text));
        ersetzt = true;
        try {
            setText(text);
        } finally {
            ersetzt = false;
        }
    }

    /** Ersetzt die Auswahl durch den Text; schaltet den Zeilenumbruch ab, wenn der Text eine zu lange Zeile hat. */
    void einfuegen(String text) {
        if (getLineWrap() && !umbruchMoeglich(text)) {
            setLineWrap(false);
        }
        ersetzt = true;
        try {
            replaceSelection(text);
        } finally {
            ersetzt = false;
        }
    }

    private PlainDocument neuesDokument() {
        return new UmbruchDokument();
    }

    /** Prüft nach der Einfügung die betroffenen Zeilen: Länge ohne Zeilenende, je Zeile in konstanter Zeit. */
    private void nachEinfuegung(int stelle, int laenge) {
        if (!getLineWrap()) {
            return;
        }
        Element wurzel = getDocument().getDefaultRootElement();
        int bis = wurzel.getElementIndex(stelle + laenge);
        for (int i = wurzel.getElementIndex(stelle); i <= bis; i++) {
            Element zeile = wurzel.getElement(i);
            if (zeile.getEndOffset() - zeile.getStartOffset() - 1 > UMBRUCH_GRENZE) {
                setLineWrap(false);
                return;
            }
        }
    }

    /** Nach einer Löschung: ein Text bis zur Grenze hat keine zu lange Zeile, der Umbruch darf wieder ein. */
    private void nachLoeschung(int gesamtLaenge) {
        if (!ersetzt && !getLineWrap() && gesamtLaenge <= UMBRUCH_GRENZE) {
            setLineWrap(true);
        }
    }

    /** Dokument, das dem Feld jede Änderung meldet, nachdem alle anderen Hörer benachrichtigt sind. */
    private final class UmbruchDokument extends PlainDocument {
        @Override
        protected void fireInsertUpdate(DocumentEvent e) {
            super.fireInsertUpdate(e);
            nachEinfuegung(e.getOffset(), e.getLength());
        }

        @Override
        protected void fireRemoveUpdate(DocumentEvent e) {
            super.fireRemoveUpdate(e);
            nachLoeschung(getLength());
        }
    }

    /** Zahl der Anfragen, den Fokus in dieses Feld zu holen. */
    int fokusAnfragen() {
        return fokusAnfragen;
    }

    @Override
    public void requestFocus() {
        fokusAnfragen++;
        super.requestFocus();
    }

    @Override
    public boolean requestFocus(boolean temporary) {
        fokusAnfragen++;
        return super.requestFocus(temporary);
    }

    @Override
    public boolean requestFocusInWindow() {
        fokusAnfragen++;
        return super.requestFocusInWindow();
    }

    @Override
    protected boolean requestFocusInWindow(boolean temporary) {
        fokusAnfragen++;
        return super.requestFocusInWindow(temporary);
    }

    @Override
    public void grabFocus() {
        fokusAnfragen++;
        super.grabFocus();
    }

    /** Caret, der die Auswahl auch ohne Fokus anzeigt. */
    private static final class BleibendeAuswahl extends DefaultCaret {
        @Override
        public void setSelectionVisible(boolean sichtbar) {
            super.setSelectionVisible(true);
        }
    }
}
