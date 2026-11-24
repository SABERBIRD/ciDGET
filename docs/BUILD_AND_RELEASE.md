# Build and release

## 1. Gradle

- Gradle Kotlin DSL, version catalog `gradle/libs.versions.toml`, Gradle wrapper committed.
- The latest stable AGP, Kotlin and Gradle at implementation time; JDK 17 toolchain.
- `compileSdk = 36`, `targetSdk = 36`, `minSdk = 31` (D4).
- Modules: `:core`, `:github` (`kotlin("jvm")`), `:app` (`com.android.application`). Compose is enabled only in `:app`.
- Build types:
  - `debug`: `applicationIdSuffix = ".debug"`, `versionNameSuffix = "-debug"`. It installs next to the release build and its label is "ciDGET debug".
  - `release`: `isMinifyEnabled = true`, `isShrinkResources = true`.
  - **Both** are signed with the same committed key (below).
- `versionCode` = `GITHUB_RUN_NUMBER` env var if present, else 1. `versionName` = tag without the `v` (`v1.2.0` → `1.2.0`), else `0.0.0-dev`.
- `./gradlew check` runs unit tests and Android lint, and must pass with no lint errors.

### Signing: public committed key (D14)

The owner does not want to manage secrets. The signing key is therefore **committed to the repository** and is public on purpose:

- File: `app/signing/cidget-public.jks`, alias `cidget`, store and key password `cidget-public`. The passwords are written in plain text in `app/build.gradle.kts`.
- T1 generates it once and commits it:
  ```sh
  keytool -genkeypair -keystore app/signing/cidget-public.jks -alias cidget \
    -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=ciDGET public key" \
    -storepass cidget-public -keypass cidget-public
  ```
- **Never regenerate or replace it.** A different key makes every installed copy refuse updates, so users would have to uninstall and lose the token and settings. Replacing it requires the owner's explicit decision and a note in DECISIONS.md.
- Why this is acceptable: the key protects nothing secret. Its only purpose is that each new APK installs over the previous one. The remaining risk is that someone builds a fake APK with the same key and the user installs it from somewhere else. It would then inherit the app data, including the token, which is read-only. The user guide therefore says to install only from this repository's Releases page.
- If the app is ever published to a store, switch to a private key kept in GitHub Secrets. Users then reinstall once.

```kotlin
signingConfigs {
    create("cidget") {
        storeFile = rootProject.file("app/signing/cidget-public.jks")
        storePassword = "cidget-public"
        keyAlias = "cidget"
        keyPassword = "cidget-public"
    }
}
buildTypes {
    getByName("debug") { signingConfig = signingConfigs.getByName("cidget") }
    getByName("release") { signingConfig = signingConfigs.getByName("cidget") }
}
```

## 2. CI workflows

### `.github/workflows/build.yml`: every push and pull request

1. Checkout, `actions/setup-java` (temurin 17), `gradle/actions/setup-gradle`.
2. `./gradlew check assembleDebug`.
3. Upload `app/build/outputs/apk/debug/*.apk` as the artifact `cidget-debug-<sha7>` (retention 14 days).
4. Upload test reports on failure.

### `.github/workflows/release.yml`: tags `v*`

1. The same setup.
2. `./gradlew check assembleRelease`.
3. Verify the signature: `apksigner verify --print-certs`.
4. Rename to `cidget-<version>.apk`, write its SHA-256 to `cidget-<version>.apk.sha256`, then create a GitHub Release with both files (`gh release create "$GITHUB_REF_NAME" … --generate-notes`, `permissions: contents: write`).

No repository secrets are needed.

Pin third-party actions to a commit SHA, with the version in a comment.

## 3. Making a release

```sh
git tag v0.1.0 && git push origin v0.1.0
```

The APK appears under *Releases* after a few minutes. See [user/INSTALL.md](user/INSTALL.md).
