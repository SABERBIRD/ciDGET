# Build and release

## 1. Gradle

- Gradle Kotlin DSL, version catalog `gradle/libs.versions.toml`, Gradle wrapper committed.
- The latest stable AGP, Kotlin and Gradle at implementation time; JDK 17 toolchain.
- `compileSdk = 36`, `targetSdk = 36`, `minSdk = 31` (D4).
- Modules: `:core`, `:github` (`kotlin("jvm")`), `:app` (`com.android.application`). Compose is enabled only in `:app`.
- Build types:
  - `debug`: `applicationIdSuffix = ".debug"`, `versionNameSuffix = "-debug"`. It installs next to the release build and its label is "ciDGET debug".
  - `release`: `isMinifyEnabled = true`, `isShrinkResources = true`, signed from environment variables (below). If they are absent, the release build is unsigned and the build does not fail. That case is only for local builds.
- `versionCode` = `GITHUB_RUN_NUMBER` env var if present, else 1. `versionName` = tag without the `v` (`v1.2.0` → `1.2.0`), else `0.0.0-dev`.
- `./gradlew check` runs unit tests and Android lint, and must pass with no lint errors.

Signing config, read from the environment:

```kotlin
signingConfigs {
    create("release") {
        val ks = System.getenv("CIDGET_KEYSTORE_FILE")
        if (ks != null) {
            storeFile = file(ks)
            storePassword = System.getenv("CIDGET_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("CIDGET_KEY_ALIAS")
            keyPassword = System.getenv("CIDGET_KEY_PASSWORD")
        }
    }
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
2. Decode the keystore: `echo "$KEYSTORE_BASE64" | base64 -d > $RUNNER_TEMP/release.jks`.
3. `./gradlew check assembleRelease` with the `CIDGET_*` env vars from secrets.
4. Verify the signature: `apksigner verify --print-certs`.
5. Rename to `cidget-<version>.apk`, write its SHA-256 to `cidget-<version>.apk.sha256`, then create a GitHub Release with both files (`gh release create "$GITHUB_REF_NAME" … --generate-notes`, `permissions: contents: write`).

Pin third-party actions to a commit SHA, with the version in a comment.

### Repository secrets

| Secret | Content |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `cidget` |
| `KEY_PASSWORD` | key password (can be the same as the keystore password) |

## 3. Creating the signing key (once, by the owner)

On any computer with Java (JDK) installed:

```sh
keytool -genkeypair -v -keystore release.jks -alias cidget \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=ciDGET"
base64 -w0 release.jks > release.jks.b64     # macOS: base64 -i release.jks -o release.jks.b64
```

Then on GitHub: *ciDGET → Settings → Secrets and variables → Actions → New repository secret*. Add the four secrets above, pasting the content of `release.jks.b64` as `KEYSTORE_BASE64`.

**Keep `release.jks` and its passwords safe and offline** (for example in a password manager). If the key is lost, updates can't be installed over the existing app; you would have to uninstall it, which loses its settings. Never commit the keystore.

## 4. Making a release

```sh
git tag v0.1.0 && git push origin v0.1.0
```

The APK appears under *Releases* after a few minutes. See [user/INSTALL.md](user/INSTALL.md).
