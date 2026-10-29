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
3. Im Reiter **Widget** die Darstellung wählen:
   - **Mehrere Werte**: 1×1 → 1, 2×1/1×2 → 2, 3×1 → 3, ab 2×2 → 4 Werte; ab 3×4 mit kleinen Zusatzinfos
   - **Wischen**: Kartenstapel mit 1 oder 2 Werten pro Karte; jeder Wisch springt genau eine Karte weiter
4. Kacheln auswählen und sortieren. Tippen aufs Widget öffnet Garmin Connect (umstellbar).

## Gut zu wissen

- Die App nutzt dieselbe (inoffizielle) Schnittstelle wie die Garmin-Connect-App. Ändert Garmin
  etwas, können einzelne Werte vorübergehend fehlen. Gespeichert wird nur ein verschlüsseltes
  Zugriffstoken, nie das Passwort.
- Welche Werte es gibt, hängt von der Uhr ab. Die nächtliche HFV kommt bei Uhren ohne HFV-Status
  (z. B. Fenix 6) aus den Schlafdaten. Fehlt ein Wert, hilft *Konto → Rohdaten kopieren* bei der Suche.
- **Trainingsvorschlag:** Garmin stellt den Vorschlag der Uhr nicht über Connect bereit. Steht für
  heute ein Lauf im Garmin-Kalender (z. B. Garmin Coach), wird dieser gezeigt; sonst berechnet die
  App einen Vorschlag aus Erholung, Body Battery, Schlaf, HFV, Belastung und Belastungsfokus.
- Die Wisch-Animation und eine Vibration kann ein Widget nicht selbst steuern – das übernimmt der
  Launcher; das Einrasten kommt vom Android-Kartenstapel.

## Aufbau

| Ordner | Inhalt |
|---|---|
| `auth/` | Anmeldung (Ticket → Token), Token-Refresh, verschlüsselte Speicherung |
| `data/` | Abruf und Aufbereitung der Werte, Trainingsvorschlag, Einstellungen |
| `sync/` | Hintergrund-Aktualisierung (WorkManager) |
| `widget/` | Widget (RemoteViews): Raster und Kartenstapel |
| `ui/` | App-Oberfläche (Heute, Widget, Konto, Anmeldung) |
