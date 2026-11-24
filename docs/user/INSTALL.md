# Installing ciDGET

ciDGET is not on Google Play. You install the APK file from GitHub. Android 12 or newer is required.

> ## ⚠️ THE APP IS SIGNED WITH A PUBLIC KEY!
> **THE SIGNING KEY IS PUBLISHED IN THE REPOSITORY, SO ANYONE CAN SIGN AN APK WITH IT. A VALID SIGNATURE DOES NOT PROVE THAT AN APK IS GENUINE. INSTALL ONLY FROM <https://github.com/SABERBIRD/ciDGET/releases>.** AN APK FROM ANYWHERE ELSE COULD BE A FAKE THAT REPLACES THE APP AND READS YOUR TOKENS.

1. On the phone, open <https://github.com/SABERBIRD/ciDGET/releases/latest>.
2. Download `cidget-<version>.apk`.
3. Open the downloaded file. If Android asks, allow **Install unknown apps** for your browser (or file manager), then tap **Install**.
4. Long-press the home screen → **Widgets** → **ciDGET** → drag it to the screen. There is no app icon (D22): the settings open right away when you add the widget.
5. Copy your token and tap the paste button ([how to create it](TOKEN.md); you can add several), then choose a repository. Later: long-press the widget → **Reconfigure**, or tap `[⚙]` in its header.

## Updating

Download the new APK from Releases and install it over the old one. Settings are kept. All builds use the same signing key, so updates always install over the old version.

## Test builds

Every push builds a debug APK: *Actions* tab → latest **build** run → *Artifacts* → `cidget-debug-…` (you need to be signed in to GitHub). Debug builds install next to the release build as "ciDGET debug".
