# Auftrag für Claude am PC: Fernhilfe im Play Store (interner Test) einrichten

Diesen ganzen Text in Claude (Desktop-App mit „Computer use“ oder Chrome-Erweiterung „Claude in Chrome“) einfügen.

---

Bitte richte im Browser meine Android-App im Google Play Store für den **internen Test** ein. Ich bin in Chrome mit dem Google-Konto meines Play-Entwicklerkontos angemeldet. Die App **„Fernhilfe“** ist in der Play Console schon angelegt (App, kostenlos). Frag mich vor jedem Absenden, Speichern oder Veröffentlichen kurz nach. Gib keine Zahlungsdaten ein und ändere nichts an anderen Apps.

**Hintergrund:** Fernhilfe ist eine private Fernwartungs-App für meine Familie (höchstens 4 Personen). Ein Familienmitglied sieht den Bildschirm des anderen und kann für ihn tippen. Das funktioniert nur in einer Sitzung, die der Nutzer freigegeben hat. Paketname `de.controlme.app`, ohne Werbung und ohne Tracking. Der Quellcode liegt auf GitHub unter `DePhoSa/ControlMe`.

## Schritt 1: App-Bundle herunterladen
1. https://github.com/DePhoSa/ControlMe/actions öffnen (ich bin bei GitHub angemeldet).
2. Den neuesten **grünen** Lauf „APK bauen“ auf dem Branch `main` öffnen.
3. Unten bei „Artifacts“ **„Fernhilfe-AAB“** herunterladen. Das ist eine ZIP-Datei. Sie entpacken, darin liegt `Fernhilfe.aab`.

## Schritt 2: Interner Test, erster Release
1. Play Console → App „Fernhilfe“ → **Testen → Interner Test** → **Neuen Release erstellen**.
2. Bei der **App-Signatur** die von Google verwaltete Play-App-Signatur annehmen (Standard).
3. `Fernhilfe.aab` hochladen. Release-Name: Standard lassen. Versionshinweise: „Erste Testversion“.
4. Speichern. Noch **nicht** veröffentlichen, erst die Schritte 3 bis 5 erledigen.

## Schritt 3: Tester
1. Interner Test → Reiter **Tester** → **E-Mail-Liste erstellen**, Name „Familie“.
2. Die E-Mail-Adressen eintragen, die ich dir nenne (frag mich danach).
3. Speichern und den **Einladungslink** kopieren, den brauche ich später.

## Schritt 4: App-Inhalte und Erklärungen (Menü „Richtlinien → App-Inhalte“ bzw. Dashboard „App einrichten“)
Fülle alles aus, was für einen Release im internen Test verlangt wird:

- **Datenschutzerklärung (URL):** https://github.com/DePhoSa/ControlMe/blob/main/docs/datenschutz.md
- **App-Zugriff:** Alle Funktionen sind ohne besondere Zugangsdaten verfügbar. Zur Funktion werden zwei Geräte gebraucht, die über einen 6-stelligen Code gekoppelt werden.
- **Werbung:** Enthält keine Werbung.
- **Einstufung des Inhalts:** Fragebogen ausfüllen. Kategorie „Dienstprogramm/Tools“, keine problematischen Inhalte.
- **Zielgruppe:** 18 Jahre und älter.
- **Datensicherheit:**
  - Erhoben werden: **Geräte- oder andere IDs** (anonyme Firebase-Kennung) und **App-Aktivität / sonstige** (Gerätename, Modell, letzter Online-Zeitpunkt). Zweck: App-Funktionalität. Nicht geteilt, nicht optional.
  - Bildschirminhalt und Eingaben werden direkt zwischen den Geräten übertragen und **nicht erhoben** (nicht auf Servern gespeichert).
  - Daten werden bei der Übertragung verschlüsselt. Nutzer können die Löschung beantragen.
- **Erklärung zur Bedienungshilfe-API (AccessibilityService):**
  > Fernhilfe ist eine Fernwartungs-App für Familienmitglieder. Der Nutzer koppelt sein Gerät einmalig mit einem Helfer (z. B. Sohn/Tochter). Nur während einer Fernhilfe-Sitzung, die der Nutzer freigegeben hat und die durch eine dauerhafte Benachrichtigung mit „Beenden“-Knopf angezeigt wird, führt die Bedienungshilfe die Tipp-, Wisch- und Texteingaben des Helfers aus (dispatchGesture, performGlobalAction, ACTION_SET_TEXT). Es werden keine Daten über die Bedienungshilfe erhoben, gespeichert oder weitergegeben. Die App ist kein Hilfsmittel für Menschen mit Behinderung (isAccessibilityTool=false).
  - Falls ein Video verlangt wird: frag mich, ich nehme es auf.
- **Vordergrunddienste (Foreground Service):**
  - *Besondere Nutzung (specialUse):* „Wartet im Hintergrund auf Fernhilfe-Anfragen gekoppelter Familienmitglieder, damit Hilfe ohne Zutun des Nutzers starten kann. Sichtbar durch eine dauerhafte Benachrichtigung.“
  - *Medienprojektion (mediaProjection):* „Überträgt während einer vom Nutzer bestätigten Fernhilfe-Sitzung den Bildschirm per WebRTC an den gekoppelten Helfer.“
  - Falls ein Video verlangt wird: frag mich.
- **Vollbild-Intent (USE_FULL_SCREEN_INTENT), falls gefragt:** Die App ist keine Wecker- oder Telefon-App. Angeben, dass die Berechtigung nicht für Kernfunktionen nötig ist.
- **Regierungs-/Finanz-/Gesundheits-Apps, Nachrichten-Apps:** jeweils „Nein“.

## Schritt 5: Release veröffentlichen
Zurück zu **Interner Test → Release** → prüfen → **Einführung im internen Test starten**. Vorher bei mir nachfragen.

## Schritt 6 (optional, nur wenn ich zustimme): automatische Updates
Damit GitHub künftige Versionen selbst hochlädt:
1. In der Google Cloud Console mit dem Konto des Play-Entwicklers ein Projekt wählen oder anlegen, die API **„Google Play Android Developer API“** aktivieren, unter IAM → **Dienstkonten** ein Konto `play-upload` anlegen und einen **JSON-Schlüssel** erstellen und herunterladen.
2. Play Console → **Nutzer und Berechtigungen** → Nutzer einladen → E-Mail des Dienstkontos → nur für die App „Fernhilfe“ → Berechtigung **„Releases in Testtracks veröffentlichen“**.
3. GitHub → `DePhoSa/ControlMe` → Settings → Secrets and variables → Actions → neues Secret **`PLAY_SERVICE_ACCOUNT_JSON`** mit dem kompletten Inhalt der JSON-Datei anlegen. Danach die JSON-Datei vom PC löschen.

Sag mir am Ende, was erledigt ist, was noch fehlt, und gib mir den Tester-Einladungslink.
