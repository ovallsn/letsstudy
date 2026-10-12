# Launch readiness

Audit date: 10 October 2026. This is a native Android application review on an isolated Android emulator, not a production certification.

## What was checked

| Area | Result | Scope and limits |
| --- | --- | --- |
| Android build | Passed | `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` and `:app:assembleRelease` completed successfully. The release output is unsigned until the maintainer supplies the signing key. |
| Firestore rules | Passed locally | The repository rules suite passed all 4 tests against the local Firebase emulator. It checks owner-only access, rejects access granted by an admin claim alone, and exercises username reservation. It does not contact or verify the live Firebase project. |
| Navigation | Checked | The five bottom destinations, the short and expanded drawer, and Android Back closing the drawer were exercised on emulator-5556. The separate emulator-5554 was left untouched. |
| Home and study setup | Checked | Home actions opened job preparation, topic setup and the language-level entry point. Empty or invalid job input leaves question generation disabled. A topic setup form rendered. |
| Material import | Partial | The Android file picker opened and a synthetic plain-text file was extracted and previewed. PDF extraction, PPTX extraction and scanned-PDF OCR were not re-run in this audit. |
| Progress and language result | Checked | A synthetic saved C1 language-check result was opened from Progress. The result and band breakdown render and scroll on a 393 dp wide emulator. |
| Profile pictures | Checked | The initials avatar and illustrated choices render; the Android photo picker accepted a normal PNG in the earlier smoke pass, and removing a selected photo worked. No real user photo was used. |
| Notifications | Partial | The app-level Android notification settings action opened the operating-system notification settings. The emulator has notifications disabled. Reminder scheduling, delivery, exact alarm behavior and permission-on behavior were not exercised. |
| Community board | Partial | The signed-out privacy explanation rendered. Joining, refreshing live entries, photo sharing and leaving the board need a signed-in staging account and live Firestore, so they were not invoked. |
| ChatGPT provider code | Unit tests, lint and build passed | Tests exercise provider routing, consent-gate decisions, usage-limit recovery, local fallback, extracted-source boundaries and failure classification. No real OpenAI sign-in or Responses API request was made, so app/account eligibility and Android browser return remain unverified. |
| AI generation | Not verified live | No Gemini, ChatGPT or local generation request was sent and no provider quota was consumed. Prior owner-reported success does not establish reliability for a release build. |
| Account and cloud sync | Not verified | No real account was created or used, and no live account, profile, study, photo or leaderboard data was written or deleted. |
| Device coverage | Partial | The isolated emulator was used for navigation and screen review. A physical Samsung was not attached to ADB for this pass. |

The five bottom-navigation controls, representative drawer rows, core setup forms and the buttons listed above were checked directly. Static inspection confirms explicit handlers for other visible controls, but this does not prove every interaction in every state. Sign-in, password reset, destructive confirmations, account deletion, model download/cancel/error states, mic permission, reminder scheduling, question generation and each file-format path still need focused acceptance checks.

The app had no fatal exception during the exercised emulator flows. At 360 dp the core navigation and setup screens remained usable. Large font scaling, TalkBack, landscape, API-level variation and a Samsung-sized physical display still need release QA.

## Work completed during this audit

- Replaced the sprout default profile mark with the requested dark initials avatar; existing saved sprout selections migrate to initials.
- Reused the initials avatar in the header, profile choices and community board, including when a display name is missing.
- Reduced the navigation drawer height and width for phone screens, and made Android Back close an expanded drawer before leaving the app.
- Added unit coverage for initials generation and compatibility with the previous default avatar ID.
- Updated the README preview with current screenshots captured in an isolated emulator. Replaced stale profile and screen images and removed the old tutor image that exposed internal quota commentary.
- Added an optional ChatGPT provider using OpenAI’s documented open-source sign-in and Responses API flow, with encrypted local credentials, a distinct first-use disclosure and provider selection across all six study actions.
- Added explicit ChatGPT usage-limit recovery: the learner can open usage settings, opt into the local model, download it if needed and resume the same in-memory action. Gemini is not used as an automatic fallback.
- Updated provider settings, privacy wording, README and launch notes. Fresh installs remain Gemini-first; provider preferences, ChatGPT disclosure acceptance and local fallback preference stay on the device.
- Ran focused ChatGPT, router, repository and workspace unit tests, `:app:lintDebug` and `:app:assembleDebug`; all completed successfully.

## Launch blockers and next checks

1. **Create and protect the permanent Android release signing key.** Use the Windows walkthrough in [release.md](release.md). Keep two secure backups outside Git and do not send the file or passwords in chat.
2. **Connect release identity to Firebase App Check.** Register the certificate SHA-256 fingerprint for the Android app and use Play Integrity for release. Debug tokens and debug-provider configuration are for development only.
3. **Add GitHub Actions secrets.** Add the Firebase Android configuration and four signing values listed in [release.md](release.md). The signed workflow creates an APK artifact that expires after 14 days; this public repository artifact may be downloadable by other repository visitors during that period.
4. **Run and inspect the signed artifact.** Install it on the Samsung, confirm the package and signature, then test account create/sign-in/password reset, profile sync, a board join/photo-consent/leave cycle, notification permission and a scheduled reminder. Confirm the same signing key can install an update over the installed build.
5. **Verify production Firebase without widening access.** The owner reports that the Firestore rules are published. Before inviting learners, confirm the published version matches `firestore.rules`, required indexes exist, Email/Password is enabled, App Check enforcement is configured for the intended Firebase products and account deletion removes the learner's cloud data. Do this from Firebase Console; local rules tests are not proof of live configuration.
6. **Complete a controlled ChatGPT sign-in and AI acceptance run.** The learner must authorize ChatGPT in the system browser on the target Android build. Confirm OpenAI grants the plan-use scope and that the app receives the callback. Then issue one short synthetic request and verify provider routing, output validation, privacy boundaries and latency. Do not assume a ChatGPT plan is unlimited or guaranteed to work for every account or deployment. Simulate a usage-limit response through the test client rather than consuming the learner’s real allowance.
7. **Complete release publishing checks.** Verify the privacy policy URL, Google Play Data safety answers, screenshots, content rating, target audience, app icon, app signing choice and staged rollout in Play Console. The current signed workflow produces an APK for review; Google Play production publishing follows its own Play Console flow.

## AI providers and limits

| Option | Current implementation | Main limitation |
| --- | --- | --- |
| Firebase AI Logic with Gemini | Existing fast online provider; no personal Gemini login is required. | All learners share the owner’s Firebase/Gemini project quota. Limits and model capacity can block requests. |
| ChatGPT plan via Sign in with ChatGPT | Implemented as an optional Android provider. Each learner authorizes their own eligible account; the app requests eligible Responses API work without a shared key or backend proxy. | Account, workspace, policy, model and app-specific limits apply. The initial real Android sign-in and service request still need acceptance testing. A plan is not unlimited, and a reported app limit does not necessarily mean all plan usage is exhausted. |
| On-device model | Optional downloaded provider. It can be selected directly or used after an explicit ChatGPT usage-limit response when enabled. | Requires a large one-time download and can be slower; device inference has not been re-run in this audit. |

The selected provider is used for job questions, topic paths, practice rounds, written-answer feedback, requested lessons and tutor messages. Let’sStudy does not silently switch between online providers. The only automatic fallback is the learner’s explicit ChatGPT-to-on-device setting after a confirmed ChatGPT usage-limit response. Network, authentication, temporary service and invalid-output errors do not trigger that fallback. There is no Cloudflare, Groq or OpenAI API-key backend integration in this build.

Sources checked 10 October 2026: [OpenAI’s open-source Sign in with ChatGPT guide](https://developers.openai.com/siwc/token-sharing-open-source), [quickstart and account eligibility](https://developers.openai.com/siwc/quickstart), and [usage-limit recovery guidance](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery). Provider eligibility and usage controls can change; confirm them during release testing.
