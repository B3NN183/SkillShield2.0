package de.skillshield.core.recognizer.kv;

import de.skillshield.core.model.Finding;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Gemeinsamer Zeichendurchlauf der KV-Nummern-Erkenner (LANR, IK und KVNR, je Art konfiguriert ueber
 * {@link KvArt}). Der Text wird einmal von links nach rechts gelesen und in Ereignisse zerlegt: Kontextwort,
 * Fuellwort, Ziffernkette, Satzende und Zeilenumbruch. Eine Ziffernkette wird nur dann ein Fund, wenn ein
 * Kontextwort sie erschliesst. Die Pruefziffer bestimmt nur Stufe und Quelle, nie, ob ein Fund entsteht.
 *
 * <p><b>Zeichenklassen.</b> Ziffer: nur ASCII {@code 0} bis {@code 9}. Buchstabe: Codepunkt mit
 * {@link Character#isLetter(int)} oder Kombinationszeichen. Unsichtbares Zeichen: Formatzeichen
 * ({@link Character#FORMAT}, z. B. U+00AD, U+200B, U+FEFF) und Variantenwaehler U+FE00 bis U+FE0F. Lauf: maximale
 * Folge aus Buchstaben und unsichtbaren Zeichen. Teilstueck: maximale Buchstabenfolge innerhalb eines Laufs; die
 * unsichtbaren Zeichen trennen die Teilstuecke. Wort (Kontextwort, Vorwort, Nachsatzwort): eine zusammenhaengende
 * Folge ganzer Teilstuecke eines Laufs, verglichen ohne die unsichtbaren Zeichen dazwischen und mit Faltung von
 * {@code A-Z} (kein {@code toLowerCase}, damit die tuerkischen i-Formen und das Kelvin-Zeichen nicht zu {@code i}
 * und {@code k} werden). Teilstueckgrenzen sind Wortgrenzen: {@code PraxisLANR} ohne unsichtbares Zeichen, {@code
 * LANRX}, {@code Technik} und {@code IKEA} haben kein Kontextwort, {@code Praxis}+U+200B+{@code LANR} und
 * {@code LA}+U+00AD+{@code NR} haben eines. Ein Kombinationszeichen direkt am Kontextwort bleibt Teil seines
 * Teilstuecks und ergibt damit ein anderes Wort. Kontextwort: ein Wort, das einem Eintrag von
 * {@link KvArt#kontextwoerter()} gleicht, oder ein Wort aus {@link KvArt#vorwoerter()}, dem nach hoechstens einem
 * {@code -}, Leerzeichen, U+00A0, U+2011 oder U+2013 die ersten Teilstuecke des naechsten Laufs als Wort {@code nr}
 * oder {@code nummer} folgen. Ein Lauf ohne Buchstaben im Sinne von {@link Character#isLetter(int)} ist neutral.
 * Zeilenumbruch: CR LF (zaehlt als einer), CR, ein oder mehrere unsichtbare Zeichen, LF (zaehlt ebenfalls als
 * einer, D-1), LF, CR, U+0085, U+2028, U+2029. Leerraum: Leerzeichen, Tab, U+00A0 und
 * alle anderen Zeichen mit {@link Character#isWhitespace(int)} oder {@link Character#isSpaceChar(int)} ausser dem
 * Zeilenumbruch. Gruppentrenner in einer Ziffernkette: genau eines von Leerzeichen, U+00A0, U+2009, U+202F,
 * {@code -}, {@code .}, {@code /}.
 *
 * <p><b>Gesperrte Schreibweise.</b> Einzelbuchstabe: ein Lauf mit genau einem Buchstaben, der zugleich
 * {@link Character#isLetter(int)} ist; unsichtbare Zeichen vor oder hinter dem Buchstaben gehoeren zum Lauf. Ein
 * Buchstabe mit Kombinationszeichen sind zwei Buchstaben und kein Einzelbuchstabe. Sperr-Trenner: genau eines von
 * Leerzeichen, U+00A0, U+2009, U+202F, {@code .}, {@code -}, U+2011, U+2013. Sperrfolge: maximale Folge von
 * Einzelbuchstaben, je durch genau einen Sperr-Trenner verbunden. Ein zweiter Trenner, jedes andere Zeichen, ein Lauf
 * mit mehr als einem Buchstaben oder nur aus Kombinationszeichen oder unsichtbaren Zeichen, eine Ziffer, ein
 * Zeilenumbruch, ein Tab und das Textende beenden die Folge; ein Trenner hinter dem letzten Buchstaben ({@code I.K.})
 * ist erlaubt und gehoert nicht zum Vergleich. Gesperrtes Kontextwort: eine Sperrfolge von mindestens drei
 * Buchstaben, deren gefaltete Buchstaben ({@code A-Z}) genau einem Eintrag von {@link KvArt#kontextwoerter()}
 * gleichen ({@code L A N R}, {@code L.A.N.R.}, {@code I K N R}); Vorwoerter zaehlen nicht. Das Zwei-Buchstaben-Wort
 * {@code I K} ({@code I.K.}, {@code I-K}) ist ab zwei Buchstaben ein gesperrtes Kontextwort, gilt aber nur fuer die
 * unmittelbar folgende Nummer (siehe Zustand und Ereignisse). Da unsichtbare Zeichen immer an einem Nachbarlauf
 * haengen oder einen eigenen, die Folge beendenden Lauf bilden, kann kein eingestreutes unsichtbares Zeichen eine
 * neue Luecke oeffnen.
 *
 * <p><b>Erweiterte Schreibweise (Weit-Folge, D-2 bis D-4).</b> Weit-Luecke zwischen zwei Einzelbuchstaben: (a) ein
 * Sperr-Trenner; (b) zwei oder drei Leerraumzeichen der Sperrschrift (Leerzeichen, U+00A0, U+2009, U+202F), beliebig
 * gemischt; (c) ein {@code .} und ein bis drei dieser Leerraumzeichen; (d) genau ein Tab. (b) bis (d) heissen
 * erweitert. Laeufe nur aus unsichtbaren Zeichen innerhalb einer Luecke sind durchsichtig (sie zaehlen nicht zu den
 * drei Leerraumzeichen); ein unsichtbares Zeichen zwischen zwei Buchstaben ohne Luecke bildet einen Lauf mit zwei
 * Buchstaben und ist keine Luecke (Grenze 14a). Weit-Folge: maximale Folge von Einzelbuchstaben, je durch eine
 * Weit-Luecke verbunden; eine Luecke hinter dem letzten Buchstaben gehoert nicht zum Vergleich. Weit-Kontextwort: eine
 * Weit-Folge mit mindestens einer erweiterten Luecke und vier bis 27 Buchstaben, deren gefaltete Buchstaben genau
 * einem Eintrag von {@link KvArt#kontextwoerter()} gleichen (nie das Zwei-Buchstaben-Wort: {@code I. K.}, {@code I  K}
 * und {@code I<Tab>K} bleiben ohne Wirkung). Eine Weit-Folge ohne erweiterte Luecke ist die strenge Sperrfolge und
 * bleibt beim Sperr-Verfolger. Nicht zulaessig (die Folge endet): Zeilenumbruch, {@code ,}, {@code ;}, {@code :},
 * {@code /}, {@code _}, vier oder mehr Leerraumzeichen, zwei Tabs, Tab mit Leerzeichen, Leerzeichen vor dem Punkt,
 * zwei Punkte, {@code -} oder U+2013 mit Leerraum und jedes andere Zeichen.
 *
 * <p><b>Ziffernketten.</b> Eine Kette besteht aus Ziffernbloecken, die durch je genau einen Gruppentrenner
 * verbunden sind, dem wieder eine Ziffer folgt. Zwei Trenner hintereinander oder ein Trenner am Ende beenden die
 * Kette. Ab dem Kettenanfang werden Praefixe aus ganzen Bloecken betrachtet: Gibt es ein Praefix mit genau neun
 * Ziffern, ist es das Segment (Pruefziffer ueber die neun Ziffern ohne Trenner). Sonst ist das laengste Praefix mit
 * 6 bis 12 Ziffern das Segment mit Laengenabweichung. Sonst gibt es kein Segment, und der erste Block zaehlt als
 * Fuellwort. Danach geht der Durchlauf hinter dem Segment bzw. hinter dem ersten Block weiter; der Rest der Kette
 * wird als neue Kette gelesen. Die Praefixsuche bricht ab, sobald mehr als 12 Ziffern gezaehlt sind (auch mitten in
 * einem Block). Unsichtbare Zeichen innerhalb einer Nummer beenden die Kette (bekannte Grenze, nicht veraendert).
 *
 * <p><b>KVNR (Buchstabenform, nur Arten mit {@link KvArt#buchstabenform()}; LANR und IK laufen unveraendert).</b>
 * Drei Ergaenzungen, jeweils nur fuer die KVNR wirksam: (1) Ein Lauf aus genau einem ASCII-Buchstaben, dem
 * unmittelbar eine Ziffer oder genau ein Gruppentrenner und eine Ziffer folgt, ist der Anfang einer Kette (vor der
 * Verfolgung von Sperr- und Weit-Folge geprueft, die er beendet: {@code K V N R Z...} hat ein gesperrtes
 * Kontextwort). Die Ziffernbloecke dahinter werden mit derselben Praefixregel zerlegt; das Segment reicht vom
 * Buchstaben bis zum Ende der gewaehlten Bloecke. Gibt es kein Segment, zaehlen Buchstabe und erster Block zusammen
 * als ein Fuellwort, und die Regel "nummerierte Liste" gilt nicht. Zwei Buchstaben ({@code AB1}) sind ein Lauf mit
 * zwei Buchstaben und kein Anfang. (2) Eine Kette aus Ziffern, vor der unmittelbar ein Buchstabe steht
 * ({@code KVNR1234...}, {@code AB1234...}), ergibt kein Segment; der erste Block zaehlt als Fuellwort (Grenze G-K4).
 * (3) Das Vorwort {@code kv} mit Nachsatzwort ({@code KV-Nr.}, {@code KV-Nummer}, {@code KV Nr.}) beginnt einen
 * Kontext mit dem Merker {@code nurBuchstabenform}: Eine Ziffernfolge ohne Buchstaben ist dort bei {@code aktiv}
 * ein Fuellwort, und beim nachgestellten Kontext werden nur offene Segmente mit einem Buchstaben am Anfang ausgegeben
 * (Grenze G-K7). Neun Ziffern mit Buchstaben und gueltiger Pruefziffer ({@link KvnrPruefziffer}) haben bei direktem
 * Kontext 0,95, sonst gelten die Stufen von LANR und IK (0,80, Komma 0,60, gesperrt hoechstens 0,80); ungueltige
 * Pruefziffer 0,50 ({@code kvnr-pruefziffer-ungueltig}); Buchstabe mit 6 bis 12 Ziffern ausser neun und jede reine
 * Ziffernfolge von 6 bis 12 Ziffern (auch neun) 0,40 ({@code kvnr-form-abweichend}). Der Fundbereich beginnt beim
 * Buchstaben. Bekannte Folge G-K6: {@code KVNR AB 1234...} (zwei Buchstaben, dann Trenner) ergibt 0,40 ueber die
 * reinen Ziffern. Unsichtbare Zeichen (Formatzeichen, Variantenwaehler) unmittelbar vor dem Buchstaben, auch
 * mehrere, gehoeren nicht zum Fund: Der Lauf aus ihnen, einem ASCII-Buchstaben und einer Ziffer oder einem Trenner und
 * einer Ziffer ist der Anfang einer Kette; der Fund beginnt beim Buchstaben, eine laufende Sperr- oder Weit-Folge endet
 * davor. G-K5 (geschlossen, soweit sauber moeglich): Steht die Sperrfolge im Zustand "Trenner" und ergibt sie mit dem
 * Buchstaben ein Kontextwort der Art ({@code K V N R 1234...}), ist der Buchstabe dessen letzter Buchstabe; die
 * reine Ziffernfolge dahinter hat 0,40. {@code K V N R Z1234...} und {@code K V N R Z 1234...} bleiben die
 * Buchstabenform. Rest der Grenze: Ein Einzelbuchstabe mit unmittelbar folgender Ziffer oder Trenner und Ziffer gilt
 * sonst als Buchstabe einer Nummer; die Sperrfolge davor endet und ist kein Kontextwort. Ein Kontextwort unmittelbar
 * vor dem Buchstaben ohne Trenner ({@code KVNRZ1234...}) und ein unsichtbares Zeichen zwischen Buchstabe und Ziffern
 * bleiben Grenzen.
 *
 * <p><b>Zustand und Ereignisse.</b> Zustand: {@code aktiv} (ein Kontextwort erschliesst Zahlen), {@code direkt}
 * (seit dem Kontextwort kam nichts ausser Sonstigem und Nachsatzwoertern), {@code woerter} (Fuellwoerter seit
 * Kontextwort bzw. letztem Segment), {@code umbrueche} (ebenso) und die Folge der offenen Segmente samt
 * Luecken-Merkern (fuer nachgestellten Kontext), dazu der Deckel der Konfidenz des Kontexts und der Merker
 * {@code nurDirekt} (der Kontext endet mit {@code direkt}, gesetzt nur durch {@code I K}).
 * Bekannte Grenze 14g: Eine Nummer, die {@code I K} direkt erschliesst und die bisher nur ueber ein nachgestelltes
 * Kontextwort erkannt wurde, hat jetzt hoechstens 0,50 (vorher 0,80 bzw. 0,60), die Position bleibt abgedeckt: bei
 * {@code I K N (IK)}, {@code I K N IK}, Aufzaehlung {@code I K N1 N2 IK} (nur N1; N2 bleibt 0,80), Komma
 * {@code I K N, IK}, Nachsatzwoertern, {@code =} oder neutralen Zeichen zwischen {@code I K} und N und
 * {@code I K N IK M} (N 0,50, M 0,92).
 * <ul>
 * <li>Kontextwort: Sind Segmente offen und die Luecke hinter dem letzten sauber oder mit genau einem {@code ,}
 * bzw. {@code ;} (siehe unten), werden alle ausgegeben (nachgestellter Kontext). Dann {@code aktiv = direkt = true},
 * {@code woerter = umbrueche = 0}, Deckel 1,0.</li>
 * <li>Gesperrtes Kontextwort (Ereignis am Ende der Sperrfolge, vor der Verarbeitung des beendenden Zeichens bzw.
 * Laufs): Nur wenn kein Kontext {@code aktiv} ist, werden bei drei und mehr Buchstaben die zu Beginn der Folge offenen
 * Segmente (Abbild aus sechs Werten, die Buchstaben der Folge leeren sie sonst als Fuellwoerter) wiederhergestellt
 * und wie beim Kontextwort ausgewertet; dann {@code aktiv = direkt = true}, {@code woerter = umbrueche = 0}, Deckel
 * {@link #CONFIDENCE_SPERRSCHRIFT}. Bei genau zwei Buchstaben ({@code I K}) gibt es keinen nachgestellten Kontext
 * (das Abbild wird verworfen), der Deckel ist {@link #CONFIDENCE_SPERRSCHRIFT_ZWEI} und der Merker {@code nurDirekt}
 * gesetzt: Der Kontext endet ({@code aktiv = false}), sobald {@code direkt} verloren geht, also bei einem
 * Fuellwort, einem ausgegebenen Segment (keine Aufzaehlung) und einem Zeilenumbruch; ein Nachsatzwort
 * ({@code I K-Nr.}), {@code :}, {@code =} und Leerraum bleiben erlaubt. Die Buchstaben selbst werden
 * unveraendert je als Fuellwort verarbeitet; das Ereignis kommt nur hinzu. Bei laufendem Kontext
 * ({@code aktiv}, Grenze 14h, geschlossen) bleiben Deckel, {@code direkt} und {@code nurDirekt} unveraendert, aber
 * {@code I K} ist dort kein Fuellwort: die beiden gezaehlten Fuellwoerter werden zurueckgegeben ({@code woerter}
 * sinkt um zwei), und unmittelbar dahinter gelten ein Nachsatzwort (kein Fuellwort) und ein Punkt (kein Satzende)
 * wie bei {@code direkt} (Merker {@code direktNachIk}; die Konfidenz wird dadurch nicht gehoben). Der Merker endet
 * mit {@code direkt}-Verlust, Satzende und neuem Kontext. Ist der Kontext schon erloschen (viertes Fuellwort vor
 * oder in {@code I K}), gilt das Ereignis wie ohne laufenden Kontext.
 * Fuer LANR gibt es kein Zwei-Buchstaben-Kontextwort; dort aendert sich nichts.
 * Der Verfolger hat drei Zustaende: keine Folge, zuletzt ein Einzelbuchstabe, danach genau ein Sperr-Trenner. Ein
 * Lauf hinter einem Trenner wird vorab (Vorgriff, hoechstens bis zum zweiten Buchstaben) auf Einzelbuchstabe
 * geprueft: Ist er einer, wird er angehaengt, sonst endet die Folge vor seiner Verarbeitung, damit ein Nachsatzwort
 * ({@code L A N R-Nummer}) im direkten Kontext liegt. Das Abbild wird bei jedem Laufanfang ohne laufende Folge neu
 * gespeichert. Ein normales Kontextwort setzt Deckel 1,0 und {@code nurDirekt = false} ({@code I K IK N}:
 * 0,92).</li>
 * <li>Weit-Kontextwort (Ereignis am Ende der Weit-Folge, vor der Verarbeitung des beendenden Zeichens bzw. Laufs; der
 * Weit-Verfolger laeuft neben dem Sperr-Verfolger und schreibt nur hier in den Zustand): Ohne laufenden Kontext
 * werden die zu Beginn der Folge offenen Segmente (eigenes Abbild aus sechs Werten) wie beim gesperrten Kontextwort
 * wiederhergestellt und ausgewertet (nachgestellter Kontext, Komma-Regel); dann {@code aktiv = direkt = true},
 * {@code woerter = umbrueche = 0}, Deckel {@link #CONFIDENCE_SPERRSCHRIFT} (0,80 in jedem Zustand, nachgestellt mit
 * Komma 0,60). Ist am Folgenende ein Kontext {@code aktiv}, wird er mit denselben Werten neu begonnen, ohne
 * Wiederherstellen und ohne Ausgabe offener Segmente (D-5, D-7 im Sinne der Vergleichsform: dort ist der Kontext am
 * Ende des Worts nie mehr aktiv; er laeuft hier nur durch ein strenges Teilereignis wie {@code L A N R.} oder
 * {@code I K.} innerhalb der Folge). Das nimmt nie etwas weg. Alle Zwischenzustaende des
 * Weit-Verfolgers sind wirkungslos: Auf einem Text ohne Weit-Kontextwort ist die Fundliste die des Sperr-Verfolgers
 * allein. Die Buchstaben der Folge werden unveraendert je als Fuellwort verarbeitet.</li>
 * <li>Segment bei {@code aktiv}: wird ausgegeben; danach {@code direkt = false}, {@code woerter = umbrueche = 0}
 * (Aufzaehlung, auch zeilenweise). Segment ohne {@code aktiv}: wird offen (siehe unten).</li>
 * <li>Fuellwort (Wort, Ziffernblock ohne Segment, Nachsatzwort ausserhalb von {@code direkt}): {@code woerter++},
 * {@code direkt = false}; bei mehr als drei Fuellwoertern {@code aktiv = false}. Von den Teilstuecken eines Laufs
 * zaehlen die vor einem Kontextwort desselben Laufs nur als Fuellwort im Sinne von "leert die offenen Segmente";
 * die hinter dem letzten Kontextwort (ohne dessen Nachsatzwort) zaehlen zusammen als genau ein Fuellwort. Ein Lauf
 * ganz ohne Kontextwort zaehlt als genau ein Fuellwort.</li>
 * <li>Nachsatzwort ({@code nr}, {@code nummer}, auch aus mehreren Teilstuecken): Unmittelbar hinter einem
 * Kontextwort ({@code direkt}) oder hinter {@code I K} bei laufendem Kontext ({@code direktNachIk}), im selben
 * oder im folgenden Lauf, ist es kein Fuellwort.</li>
 * <li>{@code .}: bei {@code aktiv} und nicht {@code direkt} (auch nicht {@code direktNachIk}) Satzende
 * ({@code aktiv = false}). Kein Satzende ist der Punkt direkt hinter dem Kontextwort ({@code LANR-Nr.}) und der
 * Abkuerzungspunkt unmittelbar hinter einem Lauf mit Buchstaben aus hoechstens vier Buchstaben ({@code Dr.},
 * {@code e.V.}, {@code Prof.}); das Wort zaehlt weiter als Fuellwort. Ein ein- bis zweistelliger Ziffernblock, der
 * das erste Zeichen seiner Zeile nach optionalem Leerraum ist und dem unmittelbar ein {@code .} folgt, das nicht
 * Gruppentrenner einer Kette ist, ist samt Punkt neutral (nummerierte Liste). {@code !} und {@code ?}: {@code aktiv =
 * false}.</li>
 * <li>Zeilenumbruch: bei {@code aktiv} {@code umbrueche++}, {@code direkt = false}; bei mehr als einem Umbruch
 * {@code aktiv = false} (Leerzeile). Zaehlt ein einzelnes CR, bleibt es offen ({@code crOffen}), solange nur Laeufe
 * aus unsichtbaren Zeichen folgen; ein LF dahinter gehoert zum selben Umbruch und wird nicht noch einmal gezaehlt.
 * Jedes andere Zeichen, auch Leerraum, ein Buchstabe oder ein Kombinationszeichen, beendet das Offene: {@code CR UZ
 * CR}, {@code CR UZ LF UZ LF}, {@code CR Leerzeichen LF} und {@code CR UZ A LF} sind zwei Umbrueche.</li>
 * </ul>
 *
 * <p><b>Offene Segmente (nachgestellter Kontext).</b> Ein Segment ohne {@code aktiv} wird an die Folge der offenen
 * Segmente angehaengt, wenn zwischen ihm und dem vorigen offenen Segment nur Leerraum, {@code ,}, {@code ;},
 * {@code /} oder das Wort {@code und} (ASCII, fallunabhaengig, hoechstens einmal je Luecke) stehen; sonst ersetzt es
 * die Folge. Jedes andere Ereignis (Wort, Ziffernblock ohne Segment, Satzzeichen, Zeilenumbruch, sonstiges Zeichen)
 * leert die ganze Folge. Zwischen dem letzten Segment und dem Kontextwort sind nur Leerraum, neutrale Laeufe und
 * hoechstens ein {@code (} oder {@code [} erlaubt (dazu die Luecken-Zeichen {@code ,} und {@code ;}, siehe
 * unten). Die Luecke hinter dem letzten offenen Segment wird beim
 * Kontextwort so ausgewertet: Merker sind die Zahl der {@code ,} und {@code ;} (saettigt bei 2), ein Flag fuer
 * {@code /}, das Wort {@code und} und ein Flag "Klammer vor dem Komma". Ohne {@code ,}, {@code ;}, {@code /} und
 * {@code und} werden alle offenen Segmente ausgegeben (Deckel 1,0). Mit genau einem {@code ,} oder {@code ;}, ohne
 * {@code /}, {@code und} und ohne Klammer vor dem Komma werden sie ebenfalls ausgegeben, aber mit dem Deckel
 * {@link #CONFIDENCE_KOMMA_NACHGESTELLT} (0,60; {@code N, LANR}, {@code N; (IK)}, {@code N1, N2, LANR}). In allen
 * anderen Faellen ({@code N,, KW}, {@code N, und KW}, {@code N, / KW}, {@code N(, KW)}) wird nichts ausgegeben.
 * Das Komma steht nie im Fundbereich.
 *
 * <p><b>Konfidenz, Stufe, Quelle.</b> Neun Ziffern mit gueltiger Pruefziffer: Konfidenz der Art, Quelle der Art
 * (bei {@code direkt}; sonst {@link #CONFIDENCE_KONTEXT_ENTFERNT}, auch fuer nachgestellten Kontext; mit einem
 * {@code ,} oder {@code ;} vor dem nachgestellten Kontextwort hoechstens {@link #CONFIDENCE_KOMMA_NACHGESTELLT},
 * Quelle unveraendert; mit gesperrtem Kontextwort ab drei Buchstaben hoechstens
 * {@link #CONFIDENCE_SPERRSCHRIFT}, in jedem Zustand (ebenso mit einem Weit-Kontextwort, ab vier Buchstaben mit
 * erweiterten Trennern); mit gesperrtem Kontextwort aus zwei Buchstaben ({@code I K})
 * hoechstens {@link #CONFIDENCE_SPERRSCHRIFT_ZWEI}, auch bei gueltiger Pruefziffer; die Quelle folgt weiter nur
 * der Pruefziffer). Ungueltige
 * Pruefziffer: {@link #CONFIDENCE_PRUEFZIFFER_UNGUELTIG}. Sechs bis zwoelf Ziffern ausser neun:
 * {@link #CONFIDENCE_LAENGE_ABWEICHEND}. Der Fundbereich ist genau das Segment (Ziffern samt inneren Trennern), nie
 * das Kontextwort und nie ein unsichtbares Zeichen; unsichtbare Zeichen veraendern keinen Index. Die Funde sind nach
 * Position sortiert und ueberlappen nicht, jedes Segment wird hoechstens einmal ausgegeben.
 *
 * <p><b>Bekannte Grenzen (Feature kv-kontext-luecken, Plan Kap. 11).</b> Geschlossen: Grenze 12 (Komma und Semikolon
 * vor dem nachgestellten Kontextwort) und Grenze 14 (gesperrte Schreibweise) des Vorgaengers, jeweils in den Grenzen
 * der Regeln D-1 bis D-5, dazu 14h ({@code IK: Praxis I K Nr N}). Verbleibend: 12R ({@code und} und {@code /} vor dem
 * nachgestellten Kontextwort), 14a (Teilgruppen wie {@code LA NR}), 14b (gesperrtes Vor- und Nachsatzwort), 14c
 * (Initialen mit Punkt und Leerzeichen, Tab, Zeilenumbruch, zwei Trenner; seit kv-trenner-und-umbruch bis auf den
 * Rest 14cR geschlossen, siehe unten), 14d (unmittelbar wiederholte Sperrschrift
 * ist eine Folge), 14e (Einzelbuchstabe direkt vor oder hinter der Folge; mit erweiterten Trennern siehe unten),
 * 14f ({@code I K} ohne Fuellwoerter,
 * Aufzaehlung, Umbruch, nachgestellten Kontext), 14g (0,50 statt 0,80 fuer eine von {@code I K} und einem
 * nachgestellten Kontextwort erschlossene Nummer; die Position bleibt abgedeckt), die Restformen nach 14h
 * (mehrere Vorwoerter vor {@code I K}, Fuellwort zwischen {@code I K} und Nachsatzwort, zwei Zeilenumbrueche,
 * wiederholtes {@code I K} verlaengert den Kontext; auf Scanner-Ebene kann ein Pfad-Fund mit 0,80 mit einem
 * 14g-IK-Fund mit 0,50 zu einem Fund mit 0,50 zusammengelegt werden, die Positionen bleiben abgedeckt). Je Grenze
 * gibt es einen Test {@code bekannteGrenze...}.
 *
 * <p><b>Bekannte Grenzen (Feature kv-trenner-und-umbruch).</b> Geschlossen: Grenze 15 (CR, ein oder mehrere
 * unsichtbare Zeichen, LF zaehlen als ein Zeilenumbruch, D-1). Rest 15R, je als Test
 * {@code bekannteGrenze15Rest...}: CR, Leerraum und LF, CR, ein Buchstabe oder Kombinationszeichen und LF, CR, ein
 * unsichtbares Zeichen und CR, und jede Kombination mit U+0085, U+2028 oder U+2029 zaehlen weiter als zwei
 * Zeilenumbrueche. Dazu gehoeren auch Zeichen, die man nicht sieht, aber nicht unter die Regel "unsichtbares Zeichen"
 * fallen (U+E0100 bis U+E01EF, U+034F, U+3164, U+115F): {@code CR}, eines davon und {@code LF} sind zwei Umbrueche
 * (Schwere niedrig, nur bei praepariertem Text). Geschlossen: Grenze 14c (Initialen mit Punkt und Leerzeichen, mehrere
 * Leerzeichen und Tab zwischen den Buchstaben eines gesperrten Kontextworts ab vier Buchstaben, D-2 bis D-4), bis auf
 * den Rest 14cR, je als Test {@code bekannteGrenze14cRest...}: das Zwei-Buchstaben-Wort mit erweiterten Trennern
 * ({@code I. K.}, {@code I  K}, {@code I<Tab>K}) ohne Fund, ebenso vier und mehr Leerraumzeichen, zwei Tabs, Tab mit
 * Leerraum, Punkt mit Tab, Leerraum vor dem Punkt, zwei Punkte, {@code -} oder U+2013 mit Leerraum, Zeilenumbruch,
 * {@code ,}, {@code ;}, {@code :}, {@code /}, {@code _} und andere Unicode-Leerraeume (U+2002, U+3000) als Luecke.
 * Grenzen 14d und 14e mit erweiterten Trennern, je als Test {@code bekannteGrenze14dUnd14eErweitert...} und
 * {@code bekannteGrenze14eErweitert...}: Eine Folge, die ein davor- oder dahinterstehender Einzelbuchstabe ueber eine
 * Luecke nach D-2 verlaengert ({@code L. A. N. R. L. A. N. R. N}, {@code Herr A. L. A. N. R. N},
 * {@code L. A. N. R. X N}), ist kein Kontextwort. Das gilt auch fuer die nachgestellte Form: Folgt auf
 * {@code N L. A. N. R.} ein Einzelbuchstabe oder eine Initiale ({@code N L. A. N. R. B. Beispiel}), bleibt die Nummer
 * ohne Fund; steht ein Komma, eine Klammer, ein Strich oder ein Umbruch dazwischen, ist es ein Fund 0,80. Das ist kein
 * Rueckschritt gegenueber dem Stand vor diesem Feature und bewusst nicht geschlossen: Eine Regel "Teilfolge genuegt"
 * machte Initialenketten zu Kontextwoertern. Bekannte Folge 14cF: Initialen oder Einzelbuchstaben, die
 * zusammengelesen genau ein Kontextwort ab vier Buchstaben ergeben ({@code Prof. L. A. N. R. Beispiel, Rechnung N}),
 * erschliessen eine folgende Zahl (kein Fehler, sondern Folge von D-3). Bei laufendem Kontext wird der Kontext durch
 * das Weit-Kontextwort neu begonnen (siehe oben, Ereignis Weit-Kontextwort). Unveraendert bleiben 12R, 14a, 14b, 14f,
 * 14g und die Restformen nach 14h.
 *
 * <p><b>Linearitaet.</b> Ein Durchlauf, keine Rekursion, kein regulaerer Ausdruck, kein Zuruecksetzen der
 * Leseposition der Hauptschleife. Jeder Lauf wird in einem Zug gelesen. Dabei fuehrt der Durchlauf nur ein Fenster
 * der letzten 28 Buchstaben des Laufs (laengere Teilstuecke koennen keinem Eintrag gleichen, das laengste Kontextwort
 * hat hoechstens 27 Zeichen) und eine Bitmaske der Teilstueckanfaenge darin. Am Ende jedes Teilstuecks werden die
 * Fenstersuffixe ab einem Teilstueckanfang verglichen: hoechstens 28 Vergleiche mit Texten von hoechstens 27 Zeichen,
 * also konstanter Aufwand je Teilstueck; jedes Zeichen des Laufs wird einmal gelesen, unsichtbare Zeichen werden ohne
 * Speicherung uebersprungen. Das Nachsatzwort hinter einem Kontextwort desselben Laufs wird ueber dieselbe Zaehlung
 * der Buchstaben seit dem Kontextwort (hoechstens sechs) erkannt, ohne Vorausschau. Hinter einem Vorwort am Ende
 * eines Laufs wird der Anfang des folgenden Laufs fuer das Nachsatzwort einmal vorausgelesen (hoechstens sechs
 * Buchstaben, abgebrochen beim ersten Widerspruch); die Hauptschleife liest ihn danach noch einmal, jedes Zeichen wird
 * damit hoechstens zweimal beruehrt, und da der Vorgriff nur am Ende eines Laufs mit Vorwort beginnt, ueberlappen
 * sich die Vorgriffe nicht. Die Praefixsuche einer Kette liest den ersten Block ganz und danach hoechstens 13 Ziffern
 * und 13 Trenner; da der Durchlauf mindestens hinter dem ersten Block weitergeht, wird jeder Block hoechstens
 * 13-mal beruehrt. Nur bei der KVNR liest ein Vorgriff an jedem Laufanfang den Lauf unsichtbarer Zeichen bis zum
 * ersten anderen Zeichen und einen festen Rest (Buchstabe, Trenner, Ziffer); der Lauf wird danach uebersprungen oder
 * einmal von der Hauptschleife gelesen, jedes Zeichen also hoechstens zweimal. Der Zeilenanfang-Merker
 * ({@code zeilenanfang}) wird vorwaerts mitgefuehrt, nicht rueckwaerts gesucht. Der Merker {@code crOffen}
 * (CR UZ+ LF als ein Umbruch) ist ein einzelnes Flag, wird je Zeichen in konstanter Zeit gesetzt oder
 * geloescht und braucht weder Vorgriff noch Rueckkehr; {@code CR} gefolgt von einer Million unsichtbarer Zeichen
 * ist ein einziger Lauf. Der Weit-Verfolger (erweiterte Trenner) macht je Zeichen
 * ausserhalb eines Laufs und je Lauf ebenfalls konstante Arbeit und setzt die Leseposition nie zurueck: Er fuehrt einen
 * Puffer fester Groesse (27 Buchstaben) plus Zaehler, hoechstens drei Leerraumzeichen je Luecke (danach endet die
 * Folge, weitere Leerzeichen kosten je Zeichen konstante Zeit), das Abbild der offenen Segmente (sechs Werte) und
 * vergleicht einmal je Folgenende hoechstens 27 Zeichen mit acht Eintraegen. Der Vorgriff {@code vorausArt} (Lauf
 * hinter einer Luecke bis zum zweiten Buchstaben oder Laufende) ist fuer beide Verfolger gemeinsam und wird je
 * Laufanfang hoechstens einmal ausgefuehrt, nur wenn der Weit-Verfolger in einer Luecke steht; da ein Lauf mit mehreren
 * Buchstaben jede Folge beendet, trifft er nie denselben Lauf wie der Nachsatz-Vorgriff. Der Sperr-Verfolger macht
 * je Zeichen ausserhalb eines Laufs und je Lauf konstante Arbeit und setzt die Leseposition nie zurueck: Er fuehrt
 * einen Puffer fester Groesse (27 Buchstaben) plus Zaehler (laengere Folgen koennen keinem Kontextwort gleichen)
 * und das Abbild der offenen Segmente (sechs Werte, Wiederherstellen O(1)); der Vergleich mit den
 * Kontextwoertern geschieht einmal je Folgenende mit hoechstens 27 Zeichen und acht Eintraegen.
 * Jedes offene Segment wird hoechstens einmal ausgegeben, weil das Ereignis die Folge leert. Der Vorgriff auf
 * Einzelbuchstabe liest nur den einen Lauf hinter einem Sperr-Trenner (bis zum zweiten Buchstaben oder Laufende) und
 * beginnt nie im Lauf des Nachsatz-Vorgriffs (dieser betrifft den Lauf hinter einem Vorwort-Lauf mit mehreren
 * Buchstaben, also ohne laufende Sperrfolge); jedes Zeichen wird damit hoechstens zweimal gelesen. Die Zustandsmerker
 * aendern sich je Ereignis in konstanter Zeit; die Folge der offenen Segmente waechst um drei Zahlen je Segment und
 * wird hoechstens einmal ausgegeben oder verworfen. Gesamtaufwand O(n), Zusatzspeicher O(1)
 * plus Fundliste plus Folge der offenen Segmente. Belegt ist das fuer alle Verfolger, den Merker {@code crOffen}
 * und den
 * gemeinsamen Vorgriff durch die Grenzlast-Tests ({@code GrenzlastTest}, AK-26): je Eingabe mit einer Million Zeichen
 * (zum Beispiel {@code L. } wiederholt, {@code L} und eine Million Leerzeichen, {@code L. A. N. R. N } wiederholt,
 * {@code CR} und eine Million unsichtbarer Zeichen) liefern Scan, Pseudonymisierung und Rueckuebersetzung je in unter
 * zwei Sekunden die exakt erwartete Fundzahl.
 *
 * <p>Der Durchlauf wirft nur {@link NullPointerException} mit den festen Meldungen {@code art} und {@code text}; er
 * schreibt nichts aus, und kein Fund und keine Meldung enthaelt Ziffern des Textes.
 */
final class KvNumberScanner {

    /** Konfidenz bei gueltiger Pruefziffer und weiter entferntem Kontextwort. */
    static final double CONFIDENCE_KONTEXT_ENTFERNT = 0.80;

    /** Konfidenz bei neun Ziffern mit ungueltiger Pruefziffer. */
    static final double CONFIDENCE_PRUEFZIFFER_UNGUELTIG = 0.50;

    /** Konfidenz bei einer Ziffernzahl von 6 bis 12 ausser neun. */
    static final double CONFIDENCE_LAENGE_ABWEICHEND = 0.40;

    /** Deckel bei Komma oder Semikolon vor dem nachgestellten Kontextwort (D-5). */
    static final double CONFIDENCE_KOMMA_NACHGESTELLT = 0.60;

    /** Deckel bei gesperrt geschriebenem Kontextwort ab drei Buchstaben (D-4). */
    static final double CONFIDENCE_SPERRSCHRIFT = 0.80;

    /** Deckel bei gesperrt geschriebenem Kontextwort aus zwei Buchstaben (D-3). */
    static final double CONFIDENCE_SPERRSCHRIFT_ZWEI = 0.50;

    private static final int NEUN = 9;
    private static final int MIN_LAENGE = 6;
    private static final int MAX_LAENGE = 12;
    private static final int MAX_FUELLWOERTER = 3;
    private static final int FENSTER = 28;
    private static final int MAX_ABKUERZUNG = 4;
    /**
     * Laengstes denkbares Kontextwort (27 Zeichen, Reserve fuer {@code nebenbetriebsstaettennummer}) und damit
     * Puffergroesse von Sperr- und Weit-Folge.
     */
    static final int MAX_KONTEXTWORT = FENSTER - 1;
    /** Feste Meldung, wenn ein Kontext- oder Vorwort die Fenstergroesse sprengt; nennt nie das Wort. */
    private static final String FEHLER_WORT_ZU_LANG = "Kontext- oder Vorwort laenger als MAX_KONTEXTWORT";

    /**
     * Sichert, dass jedes Kontext- und Vorwort in Fenster, Sperr- und Weit-Puffer passt. Ein zu langes Wort wuerde
     * sonst nie gefunden (Laenge {@code FENSTER}) oder den Laengenmerker verfehlen; die Pruefung schlaegt sofort und
     * eindeutig fehl, fuer gueltige Woerter (Laenge 1 bis {@code MAX_KONTEXTWORT}) hat sie keine Wirkung.
     */
    static void pruefeWortLaenge(String wort) {
        if (wort.length() > MAX_KONTEXTWORT) {
            throw new IllegalStateException(FEHLER_WORT_ZU_LANG);
        }
    }

    /**
     * Kuerzeste gesperrte Folge, die ein Ereignis ausloest ({@code I K}, D-3); genau diese Laenge gilt nur fuer die
     * unmittelbar folgende Nummer.
     */
    private static final int MIN_SPERR_BUCHSTABEN = 2;
    /** Kein Deckel der Konfidenz. */
    private static final double KEIN_DECKEL = 1.0;
    private static final int SPERR_KEINE = 0;
    private static final int SPERR_BUCHSTABE = 1;
    private static final int SPERR_TRENNER = 2;
    /** D-3: erweiterte Trenner gelten erst ab vier Buchstaben. */
    private static final int MIN_WEIT_BUCHSTABEN = 4;
    /** D-2 (b), (c): hoechstens drei Leerraumzeichen der Sperrschrift je Luecke. */
    private static final int MAX_WEIT_LEERRAUM = 3;
    // Zustaende des Weit-Verfolgers
    private static final int WEIT_KEINE = 0;
    private static final int WEIT_BUCHSTABE = 1;
    private static final int WEIT_PUNKT = 2;
    private static final int WEIT_PUNKT_LEER = 3;
    private static final int WEIT_LEER = 4;
    private static final int WEIT_STRICH = 5;
    private static final int WEIT_TAB = 6;
    // Ergebnis des gemeinsamen Vorgriffs
    private static final int VORAUS_ANDERS = 0;
    private static final int VORAUS_EINZEL = 1;
    private static final int VORAUS_UNSICHTBAR = 2;
    private static final String NR = "nr";
    private static final String NUMMER = "nummer";
    private static final String UND = "und";
    private static final char PLATZHALTER = (char) 0xFFFF;
    private static final char NBSP = (char) 0xA0;
    private static final char SCHMALES_LEERZEICHEN = (char) 0x2009;
    private static final char SCHMALES_NBSP = (char) 0x202F;
    private static final char GESCHUETZTER_BINDESTRICH = (char) 0x2011;
    private static final char HALBGEVIERTSTRICH = (char) 0x2013;
    private static final int NEL = 0x85;
    private static final int ZEILENTRENNER = 0x2028;
    private static final int ABSATZTRENNER = 0x2029;
    private static final int VARIANTENWAEHLER_ERSTER = 0xFE00;
    private static final int VARIANTENWAEHLER_LETZTER = 0xFE0F;

    private KvNumberScanner() {
    }

    /**
     * Liefert die Funde der Art im Text, nach Position sortiert.
     *
     * @param art  Nummernart, nicht {@code null}
     * @param text zu durchsuchender Text, nicht {@code null}
     * @return die Funde, nie {@code null}
     * @throws NullPointerException mit der Meldung {@code art} oder {@code text} bei {@code null}
     */
    static List<Finding> detect(KvArt art, String text) {
        Objects.requireNonNull(art, "art");
        Objects.requireNonNull(text, "text");
        Lauf lauf = new Lauf(art, text);
        lauf.durchlaufen();
        return lauf.funde;
    }

    private static boolean istZiffer(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean istAsciiBuchstabe(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean istBuchstabe(int cp) {
        if (Character.isLetter(cp)) {
            return true;
        }
        int typ = Character.getType(cp);
        return typ == Character.NON_SPACING_MARK || typ == Character.ENCLOSING_MARK
                || typ == Character.COMBINING_SPACING_MARK;
    }

    /** Formatzeichen und Variantenwaehler: trennen Teilstuecke und werden beim Wortvergleich uebergangen. */
    private static boolean istUnsichtbar(int cp) {
        return Character.getType(cp) == Character.FORMAT
                || (cp >= VARIANTENWAEHLER_ERSTER && cp <= VARIANTENWAEHLER_LETZTER);
    }

    private static boolean istZeilenumbruch(int cp) {
        return cp == '\n' || cp == '\r' || cp == NEL || cp == ZEILENTRENNER || cp == ABSATZTRENNER;
    }

    private static boolean istLeerraum(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }

    private static boolean istTrenner(char c) {
        return c == ' ' || c == NBSP || c == SCHMALES_LEERZEICHEN || c == SCHMALES_NBSP || c == '-' || c == '.'
                || c == '/';
    }

    /** Zulaessiger Trenner zwischen zwei Einzelbuchstaben einer gesperrten Schreibweise (D-1). */
    private static boolean istSperrTrenner(int cp) {
        return cp == ' ' || cp == NBSP || cp == SCHMALES_LEERZEICHEN || cp == SCHMALES_NBSP || cp == '.' || cp == '-'
                || cp == GESCHUETZTER_BINDESTRICH || cp == HALBGEVIERTSTRICH;
    }

    /** Leerraumzeichen der Sperrschrift (WS4): Leerzeichen, NBSP, U+2009, U+202F. */
    private static boolean istSperrLeerraum(int cp) {
        return cp == ' ' || cp == NBSP || cp == SCHMALES_LEERZEICHEN || cp == SCHMALES_NBSP;
    }

    private static boolean istNachsatzTrenner(char c) {
        return c == '-' || c == ' ' || c == NBSP || c == GESCHUETZTER_BINDESTRICH || c == HALBGEVIERTSTRICH;
    }

    /** Gefaltetes Vergleichszeichen: {@code A-Z} klein, sonst unveraendert, ausserhalb der BMP ein Platzhalter. */
    private static char vergleichszeichen(int cp) {
        if (cp >= 'A' && cp <= 'Z') {
            return (char) (cp + ('a' - 'A'));
        }
        return cp > 0xFFFF ? PLATZHALTER : (char) cp;
    }

    /** Gleicht {@code b[0, len)} genau {@code ref}? */
    private static boolean gleich(String ref, char[] b, int from, int len) {
        if (ref.length() != len) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            if (ref.charAt(i) != b[from + i]) {
                return false;
            }
        }
        return true;
    }

    /** Ist {@code b[from, from+len)} ein Anfangsstueck von {@code ref}? */
    private static boolean praefix(String ref, char[] b, int from, int len) {
        if (len > ref.length()) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            if (ref.charAt(i) != b[from + i]) {
                return false;
            }
        }
        return true;
    }

    /** Zustand und Ereignisbehandlung eines Durchlaufs. */
    private static final class Lauf {

        private final KvArt art;
        private final String text;
        private final int n;
        private final List<Finding> funde = new ArrayList<>();
        private final boolean[] kontextAnfang = new boolean[128];
        private final boolean[] kontextLaenge = new boolean[FENSTER + 1];
        private final boolean[] vorwortLaenge = new boolean[FENSTER + 1];
        private boolean aktiv;
        private boolean direkt;
        private int woerter;
        private int umbrueche;
        private boolean zeilenanfang = true;
        /** Deckel der Konfidenz bei {@code aktiv}: {@code KEIN_DECKEL}, sonst {@code CONFIDENCE_SPERRSCHRIFT*}. */
        private double kontextDeckel = KEIN_DECKEL;
        /** D-3: Der Kontext endet, sobald {@code direkt} verloren geht (gesetzt nur durch {@code I K}). */
        private boolean nurDirekt;
        /**
         * D-2: Der Kontext stammt vom Vorwort {@code kv} ({@code KV-Nr.}) und erschliesst nur Nummern in
         * Buchstabenform (KVNR); eine reine Ziffernfolge ist dann ein Fuellwort und wird nie nachgestellt ausgegeben.
         */
        private boolean nurBuchstabenform;
        /**
         * 14h: {@code I K} bei laufendem Kontext hat {@code direkt} nicht gesetzt (die Konfidenz bleibt die des
         * Kontexts), aber unmittelbar dahinter gilt der Kontext wie {@code direkt} fuer das Nachsatzwort (kein
         * Fuellwort) und den Punkt (kein Satzende), wie beim frischen Kontext von {@code I K}.
         */
        private boolean direktNachIk;
        /**
         * D-1: Ein einzelnes CR wurde als Zeilenumbruch gezaehlt; der Merker bleibt ueber Laeufe aus nur unsichtbaren
         * Zeichen stehen, ein LF danach wird nicht noch einmal gezaehlt. Jedes andere Zeichen loescht ihn.
         */
        private boolean crOffen;

        /** Offene Segmente als Tripel (Start, Ende, Ziffernzahl). */
        private int[] offenDaten = new int[12];
        private int offenAnzahl;
        private int offenKlammern;
        /** Luecke hinter dem letzten offenen Segment: Zahl der {@code ,} und {@code ;} (saettigt bei 2). */
        private int offenKomma;
        private boolean offenSchraeg;
        /** Ein {@code ,} oder {@code ;} kam nach einer {@code (} bzw. {@code [} der Luecke. */
        private boolean offenKlammerVorKomma;
        private boolean offenUnd;

        // Sperr-Verfolger (gesperrt geschriebenes Kontextwort)
        private int sperrZustand = SPERR_KEINE;
        /** Gefaltete Buchstaben der laufenden Sperrfolge, nur die ersten {@code MAX_KONTEXTWORT}. */
        private final char[] sperrZeichen = new char[MAX_KONTEXTWORT];
        /** Zahl der Einzelbuchstaben der laufenden Folge (zaehlt ueber den Puffer hinaus weiter). */
        private int sperrLaenge;
        /** Abbild der offenen Segmente samt Luecken-Merkern zu Beginn der Folge (die Zeichen der Folge leeren sie). */
        private int sperrOffenAnzahl;
        private int sperrOffenKlammern;
        private int sperrOffenKomma;
        private boolean sperrOffenSchraeg;
        private boolean sperrOffenKlammerVorKomma;
        private boolean sperrOffenUnd;

        // Weit-Verfolger (erweiterte Trenner, D-2 bis D-7): schreibt nur in weitEnde() in Bestandszustand
        private int weitZustand = WEIT_KEINE;
        /** Leerraumzeichen der laufenden Luecke, 0 bis 3. */
        private int weitLeer;
        /** Mindestens eine Luecke nach D-2 (b), (c) oder (d). */
        private boolean weitErweitert;
        /** Gefaltete Buchstaben der laufenden Weit-Folge, nur die ersten {@code MAX_KONTEXTWORT}. */
        private final char[] weitZeichen = new char[MAX_KONTEXTWORT];
        /** Zahl der Einzelbuchstaben der Weit-Folge (zaehlt ueber den Puffer hinaus weiter). */
        private int weitLaenge;
        /** Abbild der offenen Segmente samt Luecken-Merkern zu Beginn der Weit-Folge. */
        private int weitOffenAnzahl;
        private int weitOffenKlammern;
        private int weitOffenKomma;
        private boolean weitOffenSchraeg;
        private boolean weitOffenKlammerVorKomma;
        private boolean weitOffenUnd;

        /** Ende des letzten Laufs und Zahl seiner Buchstaben (0, wenn ohne Buchstaben), fuer den Abkuerzungspunkt. */
        private int laufEnde = -1;
        private int laufBuchstaben;

        // Zustand des gerade gelesenen Laufs
        private final char[] fenster = new char[FENSTER];
        private int fensterLaenge;
        private int teilstueckAnfaenge;
        private int buchstaben;
        private boolean kontextImLauf;
        private int seitKontext;
        private boolean restBuchstabe;
        private boolean restWiderspruch;

        Lauf(KvArt art, String text) {
            this.art = art;
            this.text = text;
            this.n = text.length();
            for (String w : art.kontextwoerter()) {
                pruefeWortLaenge(w);
                kontextAnfang[w.charAt(0)] = true;
                kontextLaenge[w.length()] = true;
            }
            for (String w : art.vorwoerter()) {
                pruefeWortLaenge(w);
                kontextAnfang[w.charAt(0)] = true;
                vorwortLaenge[w.length()] = true;
            }
        }

        void durchlaufen() {
            int i = 0;
            while (i < n) {
                char c = text.charAt(i);
                int cp = text.codePointAt(i);
                if (istUnsichtbar(cp) || istBuchstabe(cp)) {
                    int anfang = art.buchstabenform() ? buchstabenAnfang(i) : -1;
                    if (anfang >= 0) {
                        // Unsichtbare Zeichen vor dem Buchstaben (i bis anfang) gehoeren nicht zum Fund. Weder
                        // laufEnde noch laufBuchstaben werden gesetzt: Eine Kette aus Buchstabe und Ziffern ist
                        // kein Wort, und der Punkt dahinter kann nie auf dem alten laufEnde liegen (es liegt vor
                        // dem Buchstaben); fuer LANR und IK ist der Zweig nie erreichbar.
                        crOffen = false;
                        char buchstabe = text.charAt(anfang);
                        sperrSchritt(buchstabe); // beendet eine laufende Sperrfolge (K V N R vor dem Buchstaben)
                        weitSchritt(buchstabe); // beendet eine laufende Weit-Folge
                        i = ziffernkette(anfang); // die Kette beginnt beim Buchstaben
                        continue;
                    }
                    i = laufMitVerfolger(i);
                    if (buchstaben > 0) {
                        crOffen = false; // ein Buchstabe oder Kombinationszeichen: kein CR UZ+ LF mehr (D-1)
                    }
                    continue;
                }
                if (crOffen && c == '\n') {
                    crOffen = false; // CR UZ+ LF: Fortsetzung desselben Umbruchs, nicht noch einmal zaehlen (D-1)
                    i++;
                    continue;
                }
                crOffen = false;
                sperrSchritt(cp);
                weitSchritt(cp);
                if (istZiffer(c)) {
                    i = ziffernkette(i);
                } else if (c == '.') {
                    punkt(i);
                    i++;
                } else if (c == '!' || c == '?') {
                    satzende();
                    i++;
                } else {
                    int laenge = Character.charCount(cp);
                    if (istZeilenumbruch(cp)) {
                        i += laenge;
                        if (cp == '\r' && i < n && text.charAt(i) == '\n') {
                            i++;
                        } else if (cp == '\r') {
                            crOffen = true; // ein einzelnes CR zaehlt; unsichtbare Zeichen und ein LF gehoeren dazu
                        }
                        zeilenumbruch();
                    } else {
                        sonstiges(cp);
                        i += laenge;
                    }
                }
            }
            sperrEnde();
            weitEnde();
        }

        // ------------------------------------------------------------------ Ereignisse

        private void offenLeeren() {
            offenAnzahl = 0;
            lueckeZuruecksetzen();
        }

        /** Setzt alle Merker der Luecke hinter dem letzten offenen Segment zurueck (neue Merker hier eintragen). */
        private void lueckeZuruecksetzen() {
            offenKlammern = 0;
            offenKomma = 0;
            offenSchraeg = false;
            offenKlammerVorKomma = false;
            offenUnd = false;
        }

        /** Ein Kontextwort; {@code nurBuchstabenformKontext}: Vorwort {@code kv} (D-2), nur Buchstabenform. */
        private void kontextwort(boolean nurBuchstabenformKontext) {
            offenNachgestelltAusgeben(nurBuchstabenformKontext);
            kontextBeginnen(KEIN_DECKEL, false, nurBuchstabenformKontext);
        }

        private void kontextBeginnen(double deckel, boolean nurDirektKontext) {
            kontextBeginnen(deckel, nurDirektKontext, false);
        }

        /** Beginnt einen Kontext: leert die offenen Segmente, setzt alle Kontextmerker (zentral, hier erweitern). */
        private void kontextBeginnen(double deckel, boolean nurDirektKontext, boolean nurBuchstabenformKontext) {
            offenLeeren();
            nurBuchstabenform = nurBuchstabenformKontext;
            aktiv = true;
            direkt = true;
            woerter = 0;
            umbrueche = 0;
            kontextDeckel = deckel;
            nurDirekt = nurDirektKontext;
            direktNachIk = false;
        }

        /** Verliert der Kontext {@code direkt}, endet ein nur-direkter Kontext (D-3). */
        private void direktVerloren() {
            direkt = false;
            direktNachIk = false;
            if (nurDirekt) {
                aktiv = false;
            }
        }

        /** Auswertung der Luecke beim Kontextwort: gibt die offenen Segmente aus oder nicht (Bestand und D-5). */
        private void offenNachgestelltAusgeben() {
            offenNachgestelltAusgeben(false);
        }

        /** Wie oben; bei {@code nurBuchstabenformKontext} nur Segmente mit einem Buchstaben am Anfang (D-2). */
        private void offenNachgestelltAusgeben(boolean nurBuchstabenformKontext) {
            if (offenAnzahl > 0 && !offenSchraeg && !offenUnd) {
                if (offenKomma == 0) {
                    offenAusgeben(KEIN_DECKEL, nurBuchstabenformKontext);
                } else if (offenKomma == 1 && !offenKlammerVorKomma) {
                    offenAusgeben(CONFIDENCE_KOMMA_NACHGESTELLT, nurBuchstabenformKontext);
                }
            }
        }

        private void offenAusgeben(double deckel, boolean nurBuchstabenformKontext) {
            for (int k = 0; k < offenAnzahl; k++) {
                int start = offenDaten[3 * k];
                if (!nurBuchstabenformKontext || istAsciiBuchstabe(text.charAt(start))) {
                    ausgeben(start, offenDaten[3 * k + 1], offenDaten[3 * k + 2], false, deckel);
                }
            }
        }

        private void fuellwort() {
            offenLeeren();
            direktVerloren();
            if (aktiv) {
                woerter++;
                if (woerter > MAX_FUELLWOERTER) {
                    aktiv = false;
                }
            }
        }

        private void punkt(int index) {
            zeilenanfang = false;
            offenLeeren();
            boolean abkuerzung = index == laufEnde && laufBuchstaben >= 1 && laufBuchstaben <= MAX_ABKUERZUNG;
            if (aktiv && !direkt && !direktNachIk && !abkuerzung) {
                aktiv = false;
            }
        }

        private void satzende() {
            zeilenanfang = false;
            offenLeeren();
            aktiv = false;
            direkt = false;
            direktNachIk = false;
        }

        private void zeilenumbruch() {
            zeilenanfang = true;
            offenLeeren();
            if (aktiv) {
                umbrueche++;
                direktVerloren();
                if (umbrueche > 1) {
                    aktiv = false;
                }
            }
        }

        /** Leerraum und Sonstiges: neutral; fuer offene Segmente gilt die Lueckenregel. */
        private void sonstiges(int cp) {
            if (!istLeerraum(cp)) {
                zeilenanfang = false;
            }
            if (offenAnzahl == 0) {
                return;
            }
            if (cp == '(' || cp == '[') {
                offenKlammern++;
                if (offenKlammern > 1) {
                    offenLeeren();
                }
            } else if (cp == ',' || cp == ';') {
                offenKomma = Math.min(offenKomma + 1, 2);
                if (offenKlammern > 0) {
                    offenKlammerVorKomma = true;
                }
            } else if (cp == '/') {
                offenSchraeg = true;
            } else if (!istLeerraum(cp)) {
                offenLeeren();
            }
        }

        /** Ein Segment [start, end) mit {@code anzahl} Ziffern wurde gelesen. */
        private void segment(int start, int end, int anzahl) {
            if (aktiv && nurBuchstabenform && !istAsciiBuchstabe(text.charAt(start))) {
                fuellwort(); // D-2: KV-Nr. vor einer reinen Ziffernfolge ist kein KVNR-Kontext
                return;
            }
            if (aktiv) {
                ausgeben(start, end, anzahl, direkt, kontextDeckel);
                direktVerloren();
                woerter = 0;
                umbrueche = 0;
            } else {
                if (offenAnzahl > 0 && offenKlammern > 0) {
                    offenLeeren();
                }
                if (3 * (offenAnzahl + 1) > offenDaten.length) {
                    offenDaten = Arrays.copyOf(offenDaten, offenDaten.length * 2);
                }
                offenDaten[3 * offenAnzahl] = start;
                offenDaten[3 * offenAnzahl + 1] = end;
                offenDaten[3 * offenAnzahl + 2] = anzahl;
                offenAnzahl++;
                lueckeZuruecksetzen();
            }
        }

        private void ausgeben(int start, int end, int anzahl, boolean mitDirektemKontext, double deckel) {
            double konfidenz;
            String quelle;
            char buchstabe = art.buchstabenform() && istAsciiBuchstabe(text.charAt(start)) ? text.charAt(start) : 0;
            if (anzahl == NEUN && (!art.buchstabenform() || buchstabe != 0)) {
                if (art.isValid(buchstabe, ziffernwerte(start, end))) {
                    konfidenz = mitDirektemKontext ? art.confidenceDirekt() : CONFIDENCE_KONTEXT_ENTFERNT;
                    quelle = art.source();
                } else {
                    konfidenz = CONFIDENCE_PRUEFZIFFER_UNGUELTIG;
                    quelle = art.sourcePruefzifferUngueltig();
                }
            } else {
                konfidenz = CONFIDENCE_LAENGE_ABWEICHEND;
                quelle = art.sourceLaengeAbweichend();
            }
            konfidenz = Math.min(konfidenz, deckel);
            funde.add(new Finding(art.type(), text.substring(start, end), start, end, konfidenz, quelle));
        }

        // ------------------------------------------------------------------ Sperr-Verfolger

        /**
         * Verarbeitet den Lauf ab {@code start} und fuehrt dabei die Sperrfolge mit. Ist der Lauf hinter einem Trenner
         * kein Einzelbuchstabe, endet die Folge vor seiner Verarbeitung (Vorgriff), damit das Ereignis vor einem
         * Nachsatzwort wirkt.
         */
        private int laufMitVerfolger(int start) {
            boolean luecke = weitInLuecke();
            int voraus = luecke ? vorausArt(start) : VORAUS_ANDERS; // hoechstens einmal je Laufanfang
            boolean sperrWeiter = sperrZustand == SPERR_TRENNER && voraus == VORAUS_EINZEL;
            boolean weitWeiter = luecke && voraus != VORAUS_ANDERS;
            int lueckeZustand = weitZustand;
            int lueckeLeer = weitLeer;
            if (!sperrWeiter) {
                sperrEnde(); // Bestand zuerst
            }
            if (!weitWeiter) {
                weitEnde();
            }
            if (!sperrWeiter) {
                abbildSpeichern();
            }
            if (!weitWeiter) {
                weitAbbildSpeichern();
            }
            int ende = lauf(start);
            if (sperrWeiter) {
                sperrAnhaengen();
                sperrZustand = SPERR_BUCHSTABE;
            } else if (laufBuchstaben == 1) {
                sperrLaenge = 0;
                sperrAnhaengen();
                sperrZustand = SPERR_BUCHSTABE;
            }
            if (weitWeiter) {
                if (voraus == VORAUS_EINZEL) {
                    weitErweitert |= lueckeZustand == WEIT_PUNKT_LEER || lueckeZustand == WEIT_TAB
                            || (lueckeZustand == WEIT_LEER && lueckeLeer >= 2);
                    weitAnhaengen();
                }
            } else if (laufBuchstaben == 1) {
                weitLaenge = 0;
                weitErweitert = false;
                weitAnhaengen();
            }
            return ende;
        }

        private boolean weitInLuecke() {
            return weitZustand != WEIT_KEINE && weitZustand != WEIT_BUCHSTABE;
        }

        /** Haengt den Buchstaben des soeben gelesenen Einzelbuchstaben-Laufs an die Weit-Folge an. */
        private void weitAnhaengen() {
            if (weitLaenge < MAX_KONTEXTWORT) {
                weitZeichen[weitLaenge] = fenster[0];
            }
            weitLaenge++;
            weitZustand = WEIT_BUCHSTABE;
            weitLeer = 0;
        }

        /** Zeichen ausserhalb eines Laufs, nach {@link #sperrSchritt(int)} und vor der Bestandsverarbeitung. */
        private void weitSchritt(int cp) {
            switch (weitZustand) {
                case WEIT_BUCHSTABE -> {
                    if (istSperrLeerraum(cp)) {
                        weitZustand = WEIT_LEER;
                        weitLeer = 1;
                    } else if (cp == '.') {
                        weitZustand = WEIT_PUNKT;
                    } else if (cp == '-' || cp == GESCHUETZTER_BINDESTRICH || cp == HALBGEVIERTSTRICH) {
                        weitZustand = WEIT_STRICH;
                    } else if (cp == '\t') {
                        weitZustand = WEIT_TAB;
                    } else {
                        weitEnde();
                    }
                }
                case WEIT_PUNKT -> {
                    if (istSperrLeerraum(cp)) {
                        weitZustand = WEIT_PUNKT_LEER;
                        weitLeer = 1;
                    } else {
                        weitEnde();
                    }
                }
                case WEIT_PUNKT_LEER, WEIT_LEER -> {
                    if (istSperrLeerraum(cp) && weitLeer < MAX_WEIT_LEERRAUM) {
                        weitLeer++;
                    } else {
                        weitEnde();
                    }
                }
                case WEIT_STRICH, WEIT_TAB -> weitEnde();
                default -> {
                    // keine Folge: nichts zu tun
                }
            }
        }

        /**
         * Die Weit-Folge ist zu Ende. Nur hier schreibt der Weit-Verfolger in den Bestandszustand: Hat die Folge
         * mindestens eine erweiterte Luecke, vier bis 27 Buchstaben und gleicht sie einem Kontextwort der Art, beginnt
         * ein Kontext wie bei einem gesperrten Kontextwort ab drei Buchstaben; ein laufender Kontext wird dabei
         * neu begonnen, ohne das Abbild der offenen Segmente wiederherzustellen (Plan Kap. 3.2.5).
         */
        private void weitEnde() {
            int laenge = weitLaenge;
            boolean verfolgt = weitZustand != WEIT_KEINE;
            boolean erweitert = weitErweitert;
            weitZustand = WEIT_KEINE;
            weitLaenge = 0;
            weitLeer = 0;
            weitErweitert = false;
            if (!verfolgt || !erweitert || laenge < MIN_WEIT_BUCHSTABEN || laenge > MAX_KONTEXTWORT
                    || !kontextLaenge[laenge] || !istKontextwort(weitZeichen, laenge)) {
                return;
            }
            if (aktiv) {
                // Laeuft ein Kontext, geht er nur auf ein strenges Teilereignis innerhalb der Folge zurueck
                // (L A N R. N R, I K. N R): Es gibt keine offenen Segmente, das Abbild bleibt unberuehrt.
                kontextBeginnen(CONFIDENCE_SPERRSCHRIFT, false);
                return;
            }
            weitAbbildWiederherstellen();
            offenNachgestelltAusgeben();
            kontextBeginnen(CONFIDENCE_SPERRSCHRIFT, false);
        }

        /** Merkt die offenen Segmente samt Luecken-Merkern (sechs Werte) zu Beginn einer moeglichen Weit-Folge. */
        private void weitAbbildSpeichern() {
            weitOffenAnzahl = offenAnzahl;
            weitOffenKlammern = offenKlammern;
            weitOffenKomma = offenKomma;
            weitOffenSchraeg = offenSchraeg;
            weitOffenKlammerVorKomma = offenKlammerVorKomma;
            weitOffenUnd = offenUnd;
        }

        private void weitAbbildWiederherstellen() {
            offenAnzahl = weitOffenAnzahl;
            offenKlammern = weitOffenKlammern;
            offenKomma = weitOffenKomma;
            offenSchraeg = weitOffenSchraeg;
            offenKlammerVorKomma = weitOffenKlammerVorKomma;
            offenUnd = weitOffenUnd;
        }

        /** Haengt den Buchstaben des soeben gelesenen Einzelbuchstaben-Laufs an die Folge an. */
        private void sperrAnhaengen() {
            if (sperrLaenge < MAX_KONTEXTWORT) {
                sperrZeichen[sperrLaenge] = fenster[0];
            }
            sperrLaenge++;
        }

        /** Zeichen ausserhalb eines Laufs, vor seiner Bestandsverarbeitung. */
        private void sperrSchritt(int cp) {
            if (sperrZustand == SPERR_BUCHSTABE && istSperrTrenner(cp)) {
                sperrZustand = SPERR_TRENNER;
            } else {
                sperrEnde();
            }
        }

        /**
         * Gemeinsamer Vorgriff beider Verfolger auf den Lauf ab {@code p}: {@code VORAUS_EINZEL} (genau ein Buchstabe
         * im Sinne von {@link Character#isLetter(int)}, eventuell mit unsichtbaren Zeichen), {@code VORAUS_UNSICHTBAR}
         * (nur unsichtbare Zeichen) oder {@code VORAUS_ANDERS}. Liest hoechstens bis zum zweiten Buchstaben oder zum
         * Laufende, aendert nichts.
         */
        private int vorausArt(int p) {
            int anzahl = 0;
            boolean buchstabe = false;
            int j = p;
            while (j < n) {
                int cp = text.codePointAt(j);
                if (!istUnsichtbar(cp)) {
                    if (!istBuchstabe(cp)) {
                        break;
                    }
                    anzahl++;
                    if (anzahl > 1) {
                        return VORAUS_ANDERS;
                    }
                    buchstabe = Character.isLetter(cp);
                }
                j += Character.charCount(cp);
            }
            if (anzahl == 0) {
                return VORAUS_UNSICHTBAR;
            }
            return buchstabe ? VORAUS_EINZEL : VORAUS_ANDERS;
        }

        /** Die Sperrfolge ist zu Ende: Ist sie ein Kontextwort ab zwei Buchstaben, beginnt hier ein Kontext. */
        private void sperrEnde() {
            int laenge = sperrLaenge;
            boolean verfolgt = sperrZustand != SPERR_KEINE;
            sperrZustand = SPERR_KEINE;
            sperrLaenge = 0;
            if (!verfolgt || laenge < MIN_SPERR_BUCHSTABEN || laenge > MAX_KONTEXTWORT
                    || !kontextLaenge[laenge] || !istKontextwort(sperrZeichen, laenge)) {
                return;
            }
            if (aktiv) {
                if (laenge == MIN_SPERR_BUCHSTABEN) {
                    ikBeiLaufendemKontext();
                }
                return;
            }
            if (laenge == MIN_SPERR_BUCHSTABEN) {
                kontextBeginnen(CONFIDENCE_SPERRSCHRIFT_ZWEI, true); // kein nachgestellter Kontext, Abbild verworfen
                return;
            }
            abbildWiederherstellen();
            offenNachgestelltAusgeben();
            kontextBeginnen(CONFIDENCE_SPERRSCHRIFT, false);
        }

        /**
         * 14h: {@code I K} bei laufendem Kontext. Die zwei Einzelbuchstaben wurden als Fuellwoerter gezaehlt; sie
         * werden zurueckgegeben (ein Wort der Art, kein Fuellwort), und ein Nachsatzwort unmittelbar dahinter ist
         * kein Fuellwort. Deckel, {@code direkt} und {@code nurDirekt} bleiben unveraendert.
         *
         * <p>Reichweite: Wiederholtes {@code I K} mit einem neutralen Zeichen dazwischen ({@code IK: I K, I K, N})
         * verlaengert den laufenden Kontext, weil jedes {@code I K} die Fuellwoerter zurueckgibt. Das kostet keinen
         * Schutz; es kann einen Fehlalarm mit 0,80 erzeugen (Plan Kap. 11, "Restformen nach T-7a", Punkt 5).
         */
        private void ikBeiLaufendemKontext() {
            woerter = Math.max(0, woerter - MIN_SPERR_BUCHSTABEN);
            direktNachIk = true;
        }

        /** Merkt die offenen Segmente samt Luecken-Merkern (sechs Werte) zu Beginn einer moeglichen Sperrfolge. */
        private void abbildSpeichern() {
            sperrOffenAnzahl = offenAnzahl;
            sperrOffenKlammern = offenKlammern;
            sperrOffenKomma = offenKomma;
            sperrOffenSchraeg = offenSchraeg;
            sperrOffenKlammerVorKomma = offenKlammerVorKomma;
            sperrOffenUnd = offenUnd;
        }

        /** Stellt das gemerkte Abbild der offenen Segmente wieder her (Gegenstueck zu {@link #abbildSpeichern()}). */
        private void abbildWiederherstellen() {
            offenAnzahl = sperrOffenAnzahl;
            offenKlammern = sperrOffenKlammern;
            offenKomma = sperrOffenKomma;
            offenSchraeg = sperrOffenSchraeg;
            offenKlammerVorKomma = sperrOffenKlammerVorKomma;
            offenUnd = sperrOffenUnd;
        }

        /** Gleicht {@code zeichen[0, laenge)} einem Kontextwort der Art? */
        private boolean istKontextwort(char[] zeichen, int laenge) {
            for (String w : art.kontextwoerter()) {
                if (gleich(w, zeichen, 0, laenge)) {
                    return true;
                }
            }
            return false;
        }

        // ------------------------------------------------------------------ Laeufe, Teilstuecke, Woerter

        /** Verarbeitet den Lauf ab {@code start}; liefert die Position dahinter. */
        private int lauf(int start) {
            fensterLaenge = 0;
            teilstueckAnfaenge = 0;
            buchstaben = 0;
            kontextImLauf = direkt || direktNachIk; // ein Nachsatzwort darf folgen
            seitKontext = 0;
            restBuchstabe = false;
            restWiderspruch = false;
            boolean hatBuchstabe = false;
            boolean imTeilstueck = false;
            int p = start;
            while (p < n) {
                int cp = text.codePointAt(p);
                boolean unsichtbar = istUnsichtbar(cp);
                if (!unsichtbar && !istBuchstabe(cp)) {
                    break;
                }
                if (unsichtbar) {
                    if (imTeilstueck) {
                        teilstueckEnde();
                        imTeilstueck = false;
                    }
                } else {
                    if (Character.isLetter(cp)) {
                        hatBuchstabe = true;
                        if (kontextImLauf) {
                            restBuchstabe = true;
                        }
                    }
                    buchstaben++;
                    if (kontextImLauf) {
                        seitKontext++;
                    }
                    anhaengen(cp, !imTeilstueck);
                    imTeilstueck = true;
                }
                p += Character.charCount(cp);
            }
            if (imTeilstueck) {
                teilstueckEnde();
            }
            laufEnde = p;
            laufBuchstaben = hatBuchstabe ? buchstaben : 0;
            if (!hatBuchstabe) {
                return p; // nur unsichtbare Zeichen oder alleinstehende Kombinationszeichen: neutral
            }
            zeilenanfang = false;
            int vorwort = suffixLaenge(art.vorwoerter().isEmpty() ? null : art.vorwoerter(), vorwortLaenge);
            if (vorwort > 0 && nachsatzFolgt(p)) {
                if (buchstaben > vorwort) {
                    offenLeeren();
                }
                kontextwort(nurBuchstabenVorwort(vorwort));
            } else if (kontextImLauf) {
                if (seitKontext > 0 && restBuchstabe) {
                    fuellwort();
                }
            } else if (istUnd() && offenAnzahl > 0 && offenKlammern == 0 && !offenUnd) {
                offenUnd = true;
            } else {
                fuellwort();
            }
            return p;
        }

        /** Ist das Vorwort am Ende des Fensters (Laenge {@code laenge}) eines aus {@code nurBuchstabenVorwoerter}? */
        private boolean nurBuchstabenVorwort(int laenge) {
            Set<String> nur = art.nurBuchstabenVorwoerter();
            return !nur.isEmpty() && nur.contains(new String(fenster, fensterLaenge - laenge, laenge));
        }

        private void anhaengen(int cp, boolean neuesTeilstueck) {
            if (fensterLaenge == FENSTER) {
                System.arraycopy(fenster, 1, fenster, 0, FENSTER - 1);
                fensterLaenge--;
                teilstueckAnfaenge >>>= 1;
            }
            fenster[fensterLaenge] = vergleichszeichen(cp);
            if (neuesTeilstueck) {
                teilstueckAnfaenge |= 1 << fensterLaenge;
            }
            fensterLaenge++;
        }

        /** Das Teilstueck endet; erkennt ein Kontextwort oder verfolgt das Nachsatzwort. */
        private void teilstueckEnde() {
            int treffer = suffixLaenge(art.kontextwoerter(), kontextLaenge);
            if (treffer > 0) {
                if (buchstaben > treffer) {
                    offenLeeren(); // Teilstuecke davor wirken wie ein Fuellwort
                }
                kontextwort(false);
                kontextImLauf = true;
                seitKontext = 0;
                restBuchstabe = false;
                restWiderspruch = false;
                return;
            }
            if (!kontextImLauf || restWiderspruch) {
                return;
            }
            int s = seitKontext;
            int von = fensterLaenge - s;
            if (s <= NUMMER.length() && (gleich(NR, fenster, von, s) || gleich(NUMMER, fenster, von, s))) {
                seitKontext = 0; // Nachsatzwort, kein Fuellwort
                restBuchstabe = false;
            } else if (!(praefix(NR, fenster, von, s) || praefix(NUMMER, fenster, von, s))) {
                restWiderspruch = true;
            }
        }

        /**
         * Laenge des laengsten Fenstersuffixes, das an einem Teilstueckanfang beginnt und einem Eintrag von
         * {@code woerter} gleicht; 0, wenn keiner passt.
         */
        private int suffixLaenge(Set<String> woerter, boolean[] laengen) {
            if (woerter == null) {
                return 0;
            }
            int m = teilstueckAnfaenge;
            while (m != 0) {
                int k = Integer.numberOfTrailingZeros(m);
                m &= m - 1;
                int len = fensterLaenge - k;
                char c0 = fenster[k];
                if (len < laengen.length && laengen[len] && c0 < 128 && kontextAnfang[c0]
                        && woerter.contains(new String(fenster, k, len))) {
                    return len;
                }
            }
            return 0;
        }

        /** Besteht der Lauf genau aus dem Wort {@code und}? */
        private boolean istUnd() {
            return buchstaben == UND.length() && fensterLaenge == UND.length()
                    && gleich(UND, fenster, 0, UND.length());
        }

        /**
         * Bilden die ersten Teilstuecke des Laufs hinter {@code ende} (nach hoechstens einem Nachsatztrenner) das Wort
         * {@code nr} oder {@code nummer}? Liest hoechstens sechs Buchstaben und bricht beim ersten Widerspruch ab.
         */
        private boolean nachsatzFolgt(int ende) {
            int j = ende;
            if (j < n && istNachsatzTrenner(text.charAt(j))) {
                j++;
            }
            char[] puffer = new char[NUMMER.length()];
            int len = 0;
            boolean imTeilstueck = false;
            while (j < n) {
                int cp = text.codePointAt(j);
                if (istUnsichtbar(cp)) {
                    if (imTeilstueck) {
                        if (gleich(NR, puffer, 0, len) || gleich(NUMMER, puffer, 0, len)) {
                            return true;
                        }
                        imTeilstueck = false;
                    }
                } else if (istBuchstabe(cp)) {
                    if (len == puffer.length) {
                        return false;
                    }
                    puffer[len++] = vergleichszeichen(cp);
                    if (!(praefix(NR, puffer, 0, len) || praefix(NUMMER, puffer, 0, len))) {
                        return false;
                    }
                    imTeilstueck = true;
                } else {
                    break;
                }
                j += Character.charCount(cp);
            }
            return imTeilstueck && (gleich(NR, puffer, 0, len) || gleich(NUMMER, puffer, 0, len));
        }

        // ------------------------------------------------------------------ Ziffernketten

        /** Verarbeitet die Kette ab {@code start}; liefert die Position, ab der weitergelesen wird. */
        private int ziffernkette(int start) {
            boolean anfang = zeilenanfang;
            zeilenanfang = false;
            if (art.buchstabenform() && angeklebtAnBuchstabe(start)) {
                int ende = ziffernEnde(start);
                fuellwort(); // G-K4: Ziffern direkt an einem Buchstaben sind keine Nummer dieser Art
                return ende;
            }
            // Buchstabenform: Der Buchstabe und ein Trenner gehoeren zum Segment, die Bloecke beginnen dahinter.
            int ziffernStart = start;
            if (art.buchstabenform() && istAsciiBuchstabe(text.charAt(start))) {
                ziffernStart = istZiffer(text.charAt(start + 1)) ? start + 1 : start + 2;
            }
            int blockEnde = ziffernEnde(ziffernStart);
            int ersterBlock = blockEnde - ziffernStart;
            int anzahl = ersterBlock;
            int neunEnde = -1;
            int besteEnde = -1;
            int besteAnzahl = 0;
            if (anzahl == NEUN) {
                neunEnde = blockEnde;
            } else if (anzahl >= MIN_LAENGE && anzahl <= MAX_LAENGE) {
                besteEnde = blockEnde;
                besteAnzahl = anzahl;
            }
            int p = blockEnde;
            while (neunEnde < 0 && anzahl <= MAX_LAENGE && p + 1 < n && istTrenner(text.charAt(p))
                    && istZiffer(text.charAt(p + 1))) {
                int q = p + 1;
                int c = 0;
                while (q + c < n && istZiffer(text.charAt(q + c)) && anzahl + c <= MAX_LAENGE) {
                    c++;
                }
                if (anzahl + c > MAX_LAENGE) {
                    break;
                }
                anzahl += c;
                p = q + c;
                if (anzahl == NEUN) {
                    neunEnde = p;
                } else if (anzahl >= MIN_LAENGE) {
                    besteEnde = p;
                    besteAnzahl = anzahl;
                }
            }
            if (neunEnde >= 0) {
                segment(start, neunEnde, NEUN);
                return neunEnde;
            }
            if (besteEnde >= 0) {
                segment(start, besteEnde, besteAnzahl);
                return besteEnde;
            }
            if (anfang && ziffernStart == start && ersterBlock <= 2 && blockEnde < n
                    && text.charAt(blockEnde) == '.'
                    && (blockEnde + 1 >= n || !istZiffer(text.charAt(blockEnde + 1)))) {
                return blockEnde + 1; // nummerierte Liste: Ziffern samt Punkt sind neutral
            }
            fuellwort();
            return blockEnde;
        }

        /** Steht unmittelbar vor der Ziffer an {@code start} ein Buchstabe (auch ein Kombinationszeichen)? */
        private boolean angeklebtAnBuchstabe(int start) {
            return istZiffer(text.charAt(start)) && start > 0 && istBuchstabe(text.codePointBefore(start));
        }

        /**
         * Liefert die Position des Buchstabens, wenn der Lauf ab {@code i} (Laufanfang) aus null oder mehr unsichtbaren
         * Zeichen und dann genau einem ASCII-Buchstaben mit folgender Ziffer oder Trenner und Ziffer besteht (Anfang
         * einer Nummer in Buchstabenform), sonst -1. Der Vorgriff liest nur den Lauf unsichtbarer Zeichen und einen
         * festen Rest und beginnt nur an einem Laufanfang: Der Lauf wird danach entweder uebersprungen oder einmal von
         * {@link #laufMitVerfolger(int)} gelesen, jedes Zeichen also hoechstens zweimal.
         *
         * <p>G-K5: Steht die Sperrfolge im Zustand "Trenner" und ergibt sie mit diesem Buchstaben ein Kontextwort
         * der Art, und folgt auf den Buchstaben ein Trenner ({@code K V N R 1234...}), ist er der letzte Buchstabe
         * des gesperrten Kontextworts und kein Anfang. {@code K V N R Z 1234...} bleibt die Buchstabenform, denn
         * {@code KVNRZ} ist kein Kontextwort. Grenze: Ein Buchstabe, der zugleich Wortende und Buchstabe einer Nummer
         * sein koennte ({@code K V N R} mit einer Nummer, die mit {@code R} beginnt und einen Trenner hat), gilt als
         * Wortende.
         */
        private int buchstabenAnfang(int i) {
            int j = i;
            while (j < n) {
                int cp = text.codePointAt(j);
                if (!istUnsichtbar(cp)) {
                    break;
                }
                j += Character.charCount(cp);
            }
            if (j >= n || !istBuchstabenAnfang(j)) {
                return -1;
            }
            return sperrBuchstabeDesKontextworts(j) ? -1 : j;
        }

        /** Ist der ASCII-Buchstabe bei {@code j}, hinter einem Trenner der Sperrfolge, deren letzter Buchstabe? */
        private boolean sperrBuchstabeDesKontextworts(int j) {
            int laenge = sperrLaenge + 1;
            if (sperrZustand != SPERR_TRENNER || laenge < MIN_SPERR_BUCHSTABEN || laenge > MAX_KONTEXTWORT
                    || !kontextLaenge[laenge] || !istTrenner(text.charAt(j + 1))) {
                return false;
            }
            sperrZeichen[sperrLaenge] = vergleichszeichen(text.charAt(j)); // wird beim Anhaengen ueberschrieben
            return istKontextwort(sperrZeichen, laenge);
        }

        /**
         * Beginnt bei {@code i} ein einzelner ASCII-Buchstabe, dem unmittelbar eine Ziffer oder genau ein
         * Gruppentrenner und eine Ziffer folgen (Anfang einer Nummer in Buchstabenform, O(1) Vorgriff)?
         */
        private boolean istBuchstabenAnfang(int i) {
            if (!istAsciiBuchstabe(text.charAt(i)) || i + 1 >= n) {
                return false;
            }
            char c1 = text.charAt(i + 1);
            return istZiffer(c1) || (istTrenner(c1) && i + 2 < n && istZiffer(text.charAt(i + 2)));
        }

        private int ziffernEnde(int start) {
            int p = start;
            while (p < n && istZiffer(text.charAt(p))) {
                p++;
            }
            return p;
        }

        /** Die neun Ziffernwerte des Segments [start, end), Trenner uebersprungen. */
        private int[] ziffernwerte(int start, int end) {
            int[] d = new int[NEUN];
            int k = 0;
            for (int p = start; p < end; p++) {
                char c = text.charAt(p);
                if (istZiffer(c)) {
                    d[k++] = c - '0';
                }
            }
            return d;
        }
    }
}
