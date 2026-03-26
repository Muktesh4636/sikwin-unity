# Gundu Ata – Android export

This folder is the **Android export** of the Unity game (gradle + unityLibrary). It is not the Unity project itself.

**Unity project source (same as WebGL):** `../../web_gl/DiceGame`

- Build **WebGL** from `web_gl/DiceGame` (File → Build Settings → WebGL).
- Build **Android**: open `web_gl/DiceGame` in Unity, then File → Build Settings → Android → Export Project, and export into this folder to refresh the export.

`gradle.properties` sets `unity.projectPath` to `../../web_gl/DiceGame` so the export stays linked to that project.
