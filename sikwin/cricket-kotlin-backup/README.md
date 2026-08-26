# Cricket native Kotlin UI (temporarily removed from APK)

These screens were moved out of `app/src/main/java` so the APK uses the Sports WebView
(`https://gunduata.tech/sports/`) instead of local Compose cricket UI.

## Files

- `ui/screens/IplScreen.kt`
- `ui/screens/CricketBettingHistoryScreen.kt`
- `ui/screens/CricketTeamFlag.kt`
- `ui/screens/CricketMarketFilter.kt`

## Restore

Copy them back to:

`app/src/main/java/com/sikwin/app/ui/screens/`

and re-wire `AppNavigation` `ipl` / `cricket_betting_record` composables to the Compose screens.
