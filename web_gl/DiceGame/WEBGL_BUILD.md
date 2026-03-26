# Building and Running the Dice Game as WebGL

**This Unity project (`web_gl/DiceGame`) is the single source for both WebGL and the Android export.** The Android project at `unity/Gundu Ata Project 6` references this folder; when you export from Unity (File → Build Settings → Android → Export Project) into that directory, both platforms stay in sync.

## Install WebGL support (one-time)

Unity does not include WebGL by default. Install it once:

1. Open **Unity Hub** → **Installs**.
2. Find your Editor version (e.g. 6000.3.8f1) → click the **⋮** or **Manage** → **Add modules**.
3. Check **Web Build Support** (WebGL) → **Install**.

## Code changes for WebGL

- **AndroidToast.cs**: On WebGL, toasts fall back to `Debug.Log` (no Android Java calls).
- **GameManager.cs**: `PlayerPrefs.DeleteAll()` on quit is disabled for WebGL so session/tokens persist in the browser.
- **EditorBuildSettings**: MainGame and Dice scenes are included in the build.

## Build and run

### Option A: Command line (close Unity Editor first)

1. Close Unity Editor if the DiceGame project is open.
2. From the repo root:
   ```bash
   cd web_gl/DiceGame && ./BuildAndServeWebGL.sh
   ```
3. When the build finishes, the script starts a local server. Open **http://localhost:8080** in your browser.

### Option B: From Unity Editor

1. **File → Build Settings**
2. Select **WebGL**, click **Switch Platform** if needed.
3. Click **Build and Run** (or **Build**, then run the serve script manually).
4. If you only built (no run), start the server from `DiceGame`:
   ```bash
   ./ServeWebGLBuild.sh
   ```
5. Open **http://localhost:8080** in your browser.

## Calling from JavaScript (e.g. login)

To pass tokens from your web page into the game:

```javascript
unityInstance.SendMessage('GameManager', 'SetAccessAndRefreshToken', JSON.stringify({
  accessToken: '...',
  refreshToken: '...'
}));
```

Replace `GameManager` with the actual GameObject name that has the `GameManager` component if different in your hierarchy.

---

## How to update the WebGL build (for the website)

The site serves the game from `website/public/game`. To refresh it with a new build:

1. **Build in Unity**
   - Open this project (`web_gl/DiceGame`) in Unity Editor.
   - **File → Build Settings** → select **WebGL** → **Build** (or **Build and Run**). Choose output folder `Builds/WebGL` if prompted.
   - Wait for the build to finish.

2. **Copy the build to the website**
   From the repo root:
   ```bash
   ./website/copy-webgl-build.sh
   ```
   Or from the `website` folder:
   ```bash
   ./copy-webgl-build.sh
   ```
   This copies `Build/` and `TemplateData/` from `web_gl/DiceGame/Builds/WebGL` to `website/public/game/`. The game will then be served at `/game/index.html` when you run the website.

**Optional – command-line build:** If Unity is installed and the project is closed, you can try:
```bash
cd web_gl/DiceGame && ./BuildAndServeWebGL.sh
```
If the automated build fails (e.g. Unity “Failed to remove directory”), build from the Editor as in step 1, then run the copy script.
