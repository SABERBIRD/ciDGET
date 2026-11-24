# Installing ciDGET

ciDGET is not on Google Play. You install the APK file from GitHub. Android 12 or newer is required.

> **Install ciDGET only from <https://github.com/SABERBIRD/ciDGET/releases>.** The signing key is public, so an APK from anywhere else could be a fake that replaces the app and reads your token.

1. On the phone, open <https://github.com/SABERBIRD/ciDGET/releases/latest>.
2. Download `cidget-<version>.apk`.
3. Open the downloaded file. If Android asks, allow **Install unknown apps** for your browser (or file manager), then tap **Install**.
4. Open ciDGET once and paste your token ([how to create it](TOKEN.md)).
5. Long-press the home screen → **Widgets** → **ciDGET** → drag it to the screen. Tap the `~/… $ ci` header to choose a repository.

## Updating

Download the new APK from Releases and install it over the old one. Settings are kept. All builds use the same signing key, so updates always install over the old version.

## Test builds

Every push builds a debug APK: *Actions* tab → latest **build** run → *Artifacts* → `cidget-debug-…` (you need to be signed in to GitHub). Debug builds install next to the release build as "ciDGET debug".
