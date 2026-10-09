# Preparing a signed Android build

The normal GitHub checks create a debug APK and an unsigned release APK. The optional **Signed Android build** workflow signs a review APK with your private key and Firebase configuration. It does not publish the app, upload it to Google Play, change Firebase settings or make AI quota unlimited.

## First choose the signing key

If Google Play App Signing has already been enabled for this app, check **Play Console → Setup → App integrity** first. Use the upload key associated with that Play app; do not generate an unrelated key for Play uploads. The certificate used by Google Play to sign installed updates is separate from the upload certificate. If this is only for sideloaded testing and no Play app-signing setup exists, create one permanent release key and keep it for every later update.

For a phone review APK, Android Studio can create the key:

1. Open the Android project in Android Studio.
2. Select **Build → Generate Signed Bundle / APK… → APK → Next**.
3. Select **Create new…** beside Key store path. Save it outside the repository, for example in a private folder such as `C:\Users\Valls\Documents\LetsStudyKeys\letsstudy-release.jks`.
4. Enter a key-store password, key alias, key password and a long validity period. Keep the passwords in a password manager. Certificate name and organization fields identify the publisher and are not secret.
5. Finish creating the key and keep two protected backups. Losing the key can prevent later builds from updating the installed app. Never add the JKS file to Git, Google Drive shared folders or a public artifact.

If Play Console already shows an upload certificate, use that existing private upload key for CI. Play App Signing’s public app-signing certificate can be downloaded from Play Console; retain its SHA-256 fingerprint too.

## Add Firebase release fingerprints

Find the SHA-256 certificate fingerprint with Android Studio’s Terminal or PowerShell:

```powershell
keytool -list -v -keystore 'C:\Users\Valls\Documents\LetsStudyKeys\letsstudy-release.jks' -alias 'letsstudy-upload'
```

The command prompts for the password. Copy the SHA-256 value from its output; do not put passwords in the command. In Firebase Console, open **Project settings → Your apps → Let’sStudy (Android) → SHA certificate fingerprints** and add the release certificate. For a Play-distributed app, also add the Play App Signing SHA-256 fingerprint shown in Play Console.

In Firebase **App Check → Apps**, configure Play Integrity for the release app. Keep the debug provider and debug token limited to local debug builds. A Play Integrity production check should be verified from a Play internal-testing installation; a directly sideloaded APK is not a substitute for the Play-installed release path.

## Configure GitHub Actions secrets

Open the public repository’s **Settings → Secrets and variables → Actions → New repository secret**. Add these exact names:

| Secret name | Value |
| --- | --- |
| `GOOGLE_SERVICES_JSON` | The complete `google-services.json` for the Firebase Android app whose package is `com.oriol.letsstudy`. |
| `ANDROID_RELEASE_KEYSTORE_BASE64` | Base64 text representing the JKS keystore file. |
| `ANDROID_RELEASE_STORE_PASSWORD` | Key-store password. |
| `ANDROID_RELEASE_KEY_ALIAS` | The alias you created or the existing Play upload-key alias. |
| `ANDROID_RELEASE_KEY_PASSWORD` | Key password. |

To put the keystore’s Base64 text on the Windows clipboard without printing it into a terminal log, run this in PowerShell and paste the clipboard directly into the matching GitHub secret field:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\Users\Valls\Documents\LetsStudyKeys\letsstudy-release.jks')) | Set-Clipboard
```

Do not paste the Firebase file, keystore, passwords, full Base64 value or App Check debug token in a GitHub issue, chat or source file. GitHub stores these as Actions secrets. The workflow reconstructs the Firebase file and keystore only on its temporary runner and removes them at the end.

## Run and install the review APK

1. Open the repository’s **Actions** tab.
2. Select **Signed Android build**.
3. Select **Run workflow** and confirm the branch is `main`.
4. When the run succeeds, open its summary and download the `letsstudy-signed-apk` artifact.
5. Extract the APK. Connect the phone with USB debugging and install with Android Studio, or run `adb install -r app-release.apk` from the folder containing the APK.

The artifact expires after 14 days. Because the repository is public, assume that anyone able to view its Actions runs can access that artifact during retention. Do not run the workflow until you are ready for that build to be shared. Confirm the installed app displays the expected version and Firebase project before testing.

The current workflow produces a signed APK for review. Google Play releases should use an Android App Bundle (`.aab`) and Play Console’s app-signing workflow; this APK artifact is not itself a Play Store submission. A later AAB workflow can reuse the protected signing setup if you decide to publish through Play.

For the audit status, emulator test scope, known release checks and hosted-AI research, see [launch-readiness.md](launch-readiness.md).

The Firebase Spark plan and provider free allowances remain finite. Signing the APK does not expand them.
