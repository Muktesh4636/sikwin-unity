# WebGL game – stable copy (do not overwrite)

This folder is a **separate, preserved copy** of the working WebGL build.  
The live game served at `/game/` uses `public/game/`. This folder is kept so you can restore the known-good build anytime.

- **Restore this build to live:**  
  From repo root: `./website/copy-webgl-build.sh --use-stable`
- **Do not** run the normal `copy-webgl-build.sh` (without `--use-stable`) if you want to keep the current live game unchanged; that script overwrites `public/game/` from the Unity build.
