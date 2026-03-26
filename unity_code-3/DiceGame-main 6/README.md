# DiceGame v1.2 (canonical Unity source)

**Open in Unity Hub / Editor**

`DiceGame-1.2/DiceGame`  
(folder that contains `Assets/` and `ProjectSettings/`)

**Ship to the website**

| Output | Where to build in Unity | Then |
|--------|-------------------------|------|
| **WebGL** | **Automated (local):** `cd website && ./build-webgl-and-copy.sh` (needs Unity 6000.x + WebGL module; uses `Assets/Editor/BuildWebGLForWebsite.cs`). **Manual:** Build Settings → WebGL → output `DiceGame/Builds/WebGL` | Then `cd website && ./copy-webgl-build.sh` if you only built in Editor |
| **APK** | Build Settings → Android (or exported Gradle in `Gundu Ata Project/`) | Copy APK to `website/public/gundu-ata.apk` or use `website/copy-apk-for-download.sh` |

**Also in this folder**

- `Gundu Ata v1.2.apk` — Android build artifact  
- `Gundu Ata Project/` — Unity Android Gradle export (launcher + unityLibrary) for reference or native sync

Older copies under `unity/` or `web_gl/` are not the active v1.2 line unless you choose to use them again.
