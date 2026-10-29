# Garmin Widget

Android-App mit Material-You-Widget (1×1 bis 4×4) für deine Garmin-Connect-Werte:
Trainingszustand, Trainingsvorschlag (Laufen), VO2max, Schlaf/Schlafscore, Erholungsdauer,
Body Battery, Belastung, HFV und Kalorien. Läuft komplett auf dem Handy – kein Server nötig.

## APK bauen

Android-Studio-Projekt (Kotlin, Jetpack Compose, Glance). Zwei Wege:

**A) Ohne PC über GitHub (empfohlen)**
1. Privates GitHub-Repository anlegen und diesen Ordner hochladen (Inhalt, nicht die ZIP).
2. GitHub baut die App automatisch (Reiter *Actions*, ca. 5–8 Minuten).
3. Unter *Releases → latest* liegt `GarminWidget.apk` – auf dem Handy öffnen und installieren
   (einmalig „Installation aus unbekannten Quellen“ für den Browser erlauben).

Jeder neue Push baut eine neue Version, die sich als Update über die alte installieren lässt
(feste Signatur in `app/garminwidget.jks`).

**B) Mit Android Studio**
Ordner öffnen → warten bis Gradle fertig ist → Handy per USB anschließen → ▶ Run.

## Erster Start

1. App öffnen → **Bei Garmin anmelden** (die echte Garmin-Anmeldeseite, inkl. Zwei-Faktor-Code).
2. Startbildschirm lange drücken → Widgets → *Garmin Widget* → Größe frei ziehen (1×1 bis 4×4).
3. Im Reiter **Widget**: Kacheln auswählen/sortieren und Modus wählen
   - **Mehrere Werte**: 1×1 → 1, 2×1/1×2 → 2, 3×1 → 3, ab 2×2 bzw. 4×1 → 4 Werte; große Widgets mit Ringanzeigen und Details
   - **Wischen**: 1 oder 2 Werte pro Seite, die übrigen per Wischen nach oben/unten

## Gut zu wissen

- Die App nutzt dieselbe (inoffizielle) Schnittstelle wie die Garmin-Connect-App. Ändert Garmin
  etwas, können einzelne Werte vorübergehend fehlen. Gespeichert wird nur ein verschlüsseltes
  Zugriffstoken, nie das Passwort.
- Welche Werte es gibt, hängt von der Uhr ab. Ältere Modelle (z. B. Fenix 6) liefern keinen
  HFV-Status und keine Trainingsbereitschaft – dann zeigt die Kachel „Für dein Gerät nicht
  verfügbar“. Die Erholungsdauer stammt aus der Trainingsbereitschaft.
- **Trainingsvorschlag:** Garmin stellt den Vorschlag der Uhr nicht über Connect bereit. Steht für
  heute ein Lauf im Garmin-Kalender (z. B. Garmin Coach), wird dieser gezeigt; sonst berechnet die
  App einen Vorschlag aus Erholung, Body Battery, Schlaf, HFV, Belastung und Belastungsfokus.
- Android-Widgets können nicht seitenweise einrasten: Im Wischmodus scrollt das Widget, jede
  Seite ist aber genau so hoch wie das Widget.

## Aufbau

| Ordner | Inhalt |
|---|---|
| `auth/` | Anmeldung (Ticket → Token), Token-Refresh, verschlüsselte Speicherung |
| `data/` | Abruf und Aufbereitung der Werte, Trainingsvorschlag, Einstellungen |
| `sync/` | Hintergrund-Aktualisierung (WorkManager) |
| `widget/` | Glance-Widget mit größenabhängigen Layouts |
| `ui/` | App-Oberfläche (Heute, Widget, Konto, Anmeldung) |
