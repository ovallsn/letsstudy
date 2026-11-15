# Preparing a signed Android build

The normal GitHub checks create a debug APK and an unsigned release build. The optional **Signed Android build** workflow signs both a review APK and an Android App Bundle (AAB) for Play with your private upload key and Firebase configuration. It does not publish the app, upload it to Google Play, change Firebase settings or make AI quota unlimited.

## First choose the signing key

If Google Play App Signing has already been enabled for this app, check **Play Console → Setup → App integrity** first. Use the upload key associated with that Play app; do not generate an unrelated key for Play uploads. The certificate used by Google Play to sign installed updates is separate from the upload certificate. If this is only for sideloaded testing and no Play app-signing setup exists, create one permanent release key and keep it for every later update.

For a phone review APK, Android Studio can create the key:

1. Open the Android project in Android Studio.
2. Select **Build → Generate Signed Bundle / APK… → APK → Next**.
3. Select **Create new…** beside Key store path. Save it outside the repository, for example in a private folder under your Windows user directory such as `$env:USERPROFILE\LetsStudyKeys\letsstudy-release.jks`.
4. Enter a key-store password, key alias, key password and a long validity period. Keep the passwords in a password manager. Certificate name and organization fields identify the publisher and are not secret.
5. Finish creating the key and keep two protected backups. Losing the key can prevent later builds from updating the installed app. Never add the JKS file to Git, Google Drive shared folders or a public artifact.

If Play Console already shows an upload certificate, use that existing private upload key for CI. Play App Signing’s public app-signing certificate can be downloaded from Play Console; retain its SHA-256 fingerprint too.

## Add Firebase release fingerprints

Find the SHA-256 certificate fingerprint with Android Studio’s Terminal or PowerShell:

```powershell
keytool -list -v -keystore "$env:USERPROFILE\LetsStudyKeys\letsstudy-release.jks" -alias 'letsstudy-upload'
```

The command prompts for the password. Copy the SHA-256 value from its output; do not put passwords in the command. In Firebase Console, open **Project settings → Your apps → LetsStudy (Android) → SHA certificate fingerprints** and add the release certificate. For a Play-distributed app, also add the Play App Signing SHA-256 fingerprint shown in Play Console.

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
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\LetsStudyKeys\letsstudy-release.jks")) | Set-Clipboard
```

Do not paste the Firebase file, keystore, passwords, full Base64 value or App Check debug token in a GitHub issue, chat or source file. GitHub stores these as Actions secrets. The workflow reconstructs the Firebase file and keystore only on its temporary runner and removes them at the end.

## Run and inspect the signed build

1. Open the repository’s **Actions** tab.
2. Select **Signed Android build**.
3. Select **Run workflow** and confirm the branch is `main`.
4. When the run succeeds, download `letsstudy-signed-apk` to test on a phone and `letsstudy-signed-aab` for Play Console.
5. Extract the APK. Connect the phone with USB debugging and install with Android Studio, or run `adb install -r app-release.apk` from the folder containing the APK.
6. Upload the AAB to Play Console internal testing after completing the release checklist below. Play App Signing must be enabled for the app.

The artifact expires after 14 days. Because the repository is public, assume that anyone able to view its Actions runs can access that artifact during retention. Do not run the workflow until you are ready for that build to be shared. Confirm the installed app displays the expected version and Firebase project before testing.

The APK artifact is only for direct review. The AAB is the Play submission file; inspect its package name, version code (12), upload certificate and permissions before uploading. Keep the upload keystore backed up privately. A local `:app:bundleRelease` without the signing variables is intentionally unsigned and cannot be uploaded.

## Play Console checklist for version 0.10.0

1. Confirm the exact legal publisher name that will appear in Play Console. GitHub Pages is already configured to deploy the `site/` workflow from `main` at `letsstudy.vallssolutions.com`, but the release PR is still unmerged and DNS is not resolving. In GitHub profile settings → Pages, verify domain ownership with the TXT challenge shown there and keep that TXT record. In Cloudflare, add a CNAME with name `letsstudy` and target `ovallsn.github.io`. After DNS propagates, merge the release PR, wait for the Pages workflow, enable HTTPS when GitHub makes it available, and verify `/privacy/` and `/delete-account/` load publicly. Enter those HTTPS URLs in the privacy policy and account-deletion fields.
2. Select age groups **9–12, 13–15, 16–17 and 18+** in Target audience and content. The neutral first-launch age screen routes ages 9–17 to offline curated lessons, with no account, AI, microphone, file import or community. Complete the Families policy and Data safety forms based on the installed release build. Do not claim that the app collects no data overall, because adult features use Firebase and optional AI providers.
3. Declare the data-sync foreground service used for the optional model download and provide the Play Console description and demonstration requested for foreground-service permissions. Confirm notification and microphone prompts are accurately described for the adult route.
4. Use the 512×512 store icon, 1024×500 feature graphic and phone screenshots under `play-listing/`. Review them against the final build, especially the new youth route; replace any screenshot that no longer reflects the UI.
5. Fill in the content rating, app access, ads (none), AI-generated content and community moderation declarations. `admin@vallssolutions.com` must be monitored for deletion and safety requests. Review Firestore `contentReports` regularly and take action on reported public profiles. Publish the updated `firestore.rules` before enabling reports in a release build.
6. Install from Play internal testing. Verify first launch for each age band, adult sign-in/sync/deletion, report/block, online AI disclosure, App Check, notifications and model download on real devices. Run a child-route network capture to confirm no Firebase or other SDK request before considering Families compliance complete.
7. If this is a newly created personal Play developer account, complete the required closed testing period before applying for production access. Confirm the current eligibility rules in your own Play Console.

For the audit status, emulator test scope, known release checks and hosted-AI research, see [launch-readiness.md](launch-readiness.md).

The Firebase Spark plan and provider free allowances remain finite. Signing the APK does not expand them.
