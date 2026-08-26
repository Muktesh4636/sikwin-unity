# Temporary Unity stub (APK without Gundu Ata Unity)

The real Unity module still lives at `sikwin/unityLibrary/` (~90MB natives + assets).
This stub is used so the APK stays small while Casino / Roulette / Trading / etc. use WebView.

## Restore real Unity in the APK

1. In `sikwin/settings.gradle`:
   - Uncomment `include ':unityLibrary'`
   - Comment out (or remove) `include ':unityLibraryStub'`
2. In `sikwin/app/build.gradle`:
   - Change `implementation project(':unityLibraryStub')` back to `implementation project(':unityLibrary')`
   - Set `buildConfigField "boolean", "HAS_UNITY", "true"`
3. Rebuild: `./gradlew :app:assembleGunduataDebug`
