# Signed Android build

The regular Android checks deliberately use placeholder Firebase configuration and create an unsigned APK. Use the manually triggered **Signed Android build** workflow only after configuring a real Firebase app and a release signing key.

## Before configuring the workflow

1. Create a release keystore and keep a protected backup outside the repository. The keystore is the app's permanent signing identity; losing it can prevent users from installing future updates over this build.
2. Register the release certificate's SHA-256 fingerprint in Firebase and configure Play Integrity for App Check. Keep the debug App Check provider for debug builds only.
3. Confirm the Firebase project has the intended Authentication provider, Firestore database, published rules and indexes, and Firebase AI Logic setup. The release workflow does not alter Firebase configuration or deploy rules.
4. In the GitHub repository, open **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret | Value |
| --- | --- |
| `GOOGLE_SERVICES_JSON` | Full contents of the Firebase Android app's `google-services.json` |
| `ANDROID_RELEASE_KEYSTORE_BASE64` | Base64-encoded release keystore bytes |
| `ANDROID_RELEASE_STORE_PASSWORD` | Keystore password |
| `ANDROID_RELEASE_KEY_ALIAS` | Alias of the app signing key |
| `ANDROID_RELEASE_KEY_PASSWORD` | Password of the app signing key |

Never paste these values into an issue, commit, workflow log, or chat. The build workflow writes the Firebase file and keystore only on its temporary runner, removes them when it finishes, and uploads the signed APK as a 14-day Actions artifact. Anyone with access to this public repository may be able to download that artifact during its retention period, so run it only when you intend to share that build.

## Run the build

Open **Actions → Signed Android build → Run workflow**. The job stops with a clear error if a required secret is missing. After it succeeds, open that workflow run and download `letsstudy-signed-apk` from **Artifacts**. This workflow creates no tagged release and does not deploy anything to Firebase or Google Play.

The project stays on Firebase Spark; Gemini quota and Firebase free usage allowances remain finite and shared by the Firebase project.
