# MineGolf hochladen

Alles, was du beim Anlegen der Projektseite eintippen oder hochladen musst. Texte auf der Seite sind
Englisch, weil der Mod Englisch ist.

## Vorher

1. **Im Spiel testen** – Checkliste in der [README](../../README.md#development) einmal durchspielen.
   Fehler zuerst fixen, dann hochladen.
2. **Screenshots machen** – 3–5 Stück mit `F2` (landen in `run/screenshots/`): ein Abschlag mit Zielhilfe
   und Kraftbalken, ein fertiges Loch mit Grün, Fahne und Bunker, die Scorecard, der Golfbag.
3. **Jar bauen** – siehe unten.

## Jar bauen

```bash
export JAVA_HOME="$(cygpath -w ~/.jdks/jdk-25.0.4.1+1)"
./gradlew build
```

Hochladen: `build/libs/minegolf-0.1.0.jar` (nicht die `-sources.jar`).

## Modrinth (modrinth.com → Create a project)

| Feld | Wert |
|---|---|
| Name | `MineGolf` |
| URL / Slug | `minegolf` (am 23.10.2026 noch frei) |
| Summary | `Build golf courses from tee boxes, cups and turf, then play them alone or with friends: four clubs, real ball physics, golf bag and scorecards.` |
| Icon | `docs/publish/icon-512.png` |
| Description | Inhalt von `docs/publish/description.md` einfügen |
| Categories | `Minigame`, `Equipment`, `Game Mechanics` |
| Client-side | Required |
| Server-side | Required |
| License | `MIT License` |
| Source code / Issues | leer lassen, solange das Repo privat ist |
| Gallery | die Screenshots |

Danach **Versions → Create a version**:

| Feld | Wert |
|---|---|
| Datei | `build/libs/minegolf-0.1.0.jar` |
| Version name | `MineGolf 0.1.0` |
| Version number | `0.1.0` |
| Release channel | `Beta` (erste Version, noch wenig getestet) |
| Loaders | `Fabric` |
| Game versions | `26.3` |
| Dependencies | `Fabric API` → Required |
| Changelog | Abschnitt 0.1.0 aus `CHANGELOG.md` |

Dann **Submit for review**. Modrinth prüft neue Projekte von Hand, das dauert meist ein bis wenige Tage.

## CurseForge (optional, legacy.curseforge.com → Create a project)

| Feld | Wert |
|---|---|
| Game / Class | Minecraft / Mods |
| Name | `MineGolf` |
| Summary | wie bei Modrinth |
| Description | `docs/publish/description.md` (Editor auf Markdown stellen) |
| Avatar | `docs/publish/icon-512.png` |
| Categories | `Adventure and RPG`, `Miscellaneous` |
| License | `MIT License` |

Datei hochladen: Release type `Beta`, Game version `26.3`, Modloader `Fabric`, Java `Java 25`,
Related project `Fabric API` → Required dependency. CurseForge prüft ebenfalls von Hand.

## Neue Version später

1. `mod_version` in `gradle.properties` erhöhen (z. B. `0.1.1` für Fixes, `0.2.0` für neue Inhalte).
2. Abschnitt in `CHANGELOG.md` ergänzen.
3. `./gradlew build`, neue Jar als neue Version hochladen.
