# Launch readiness

Audit date: 9 October 2026. This is a native Android application review on an isolated Android emulator, not a production certification.

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
| AI generation | Not verified in this audit | No Gemini or other hosted request was sent and no quota was consumed. No local model generation was re-run. Prior owner-reported success does not establish reliability for a release build. |
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

## Launch blockers and next checks

1. **Create and protect the permanent Android release signing key.** Use the Windows walkthrough in [release.md](release.md). Keep two secure backups outside Git and do not send the file or passwords in chat.
2. **Connect release identity to Firebase App Check.** Register the certificate SHA-256 fingerprint for the Android app and use Play Integrity for release. Debug tokens and debug-provider configuration are for development only.
3. **Add GitHub Actions secrets.** Add the Firebase Android configuration and four signing values listed in [release.md](release.md). The signed workflow creates an APK artifact that expires after 14 days; this public repository artifact may be downloadable by other repository visitors during that period.
4. **Run and inspect the signed artifact.** Install it on the Samsung, confirm the package and signature, then test account create/sign-in/password reset, profile sync, a board join/photo-consent/leave cycle, notification permission and a scheduled reminder. Confirm the same signing key can install an update over the installed build.
5. **Verify production Firebase without widening access.** The owner reports that the Firestore rules are published. Before inviting learners, confirm the published version matches `firestore.rules`, required indexes exist, Email/Password is enabled, App Check enforcement is configured for the intended Firebase products and account deletion removes the learner's cloud data. Do this from Firebase Console; local rules tests are not proof of live configuration.
6. **Make a controlled online AI acceptance run.** The app owner should choose the provider and a maximum daily request budget first. Test one synthetic job description and one topic, then inspect the generated set for four options, correct answer, clear explanation, duplicate rejection, error handling and latency. Recheck quota before broader use.
7. **Complete release publishing checks.** Verify the privacy policy URL, Google Play Data safety answers, screenshots, content rating, target audience, app icon, app signing choice and staged rollout in Play Console. The current signed workflow produces an APK for review; Google Play production publishing follows its own Play Console flow.

## Hosted AI provider review

Learners do not need a provider account when Let’sStudy calls a provider through an owner-operated backend. The provider key must stay in a server-side secret; an Android APK is inspectable and cannot protect a provider key. A backend also needs authentication/App Check verification, per-user and project-wide request caps, output validation, abuse handling and a clear notice that the job listing or study material is sent to the selected provider.

| Option | Current fit | Main limitation |
| --- | --- | --- |
| Firebase AI Logic with Gemini (current) | Already integrated; end users do not need a Gemini account. | Every learner shares the owner’s Firebase/Gemini project quota. Limits and model capacity can block requests; it is not a guaranteed free unlimited service. |
| Cloudflare Workers AI (recommended proof of concept) | A Worker can keep credentials server-side and call a hosted open model. The Free Workers plan currently includes 10,000 Neurons per day at no charge; the quota is shared across the account and resets at 00:00 UTC. `@cf/meta/llama-3.1-8b-instruct` is listed as supporting JSON Mode, which could suit the app’s structured multiple-choice output. | The free allowance is finite. When it is exhausted, requests fail unless the account moves to a paid plan; a paid plan charges beyond the free allocation. Structured output still needs strict validation and error handling. Model quality, speed and output completeness have not been benchmarked against Let’sStudy prompts. |
| Groq API | Another hosted inference service with a documented free tier and organization-level rate limits; use it behind the same kind of backend. | Exact free limits are specific to the account/model and can return HTTP 429. The rate-limit page does not promise an unlimited or permanent free service. No Let’sStudy integration or speed benchmark has been performed. |
| ChatGPT plan / OpenAI API | Could be integrated through a backend if the owner intentionally chooses it. | A ChatGPT subscription does not include API usage; API billing is separate. This does not meet the goal of a free hosted provider by itself. |

**Recommendation:** retain Gemini while it works and build a small, opt-in Cloudflare Workers AI proof of concept using one schema-capable, free-plan-eligible model. Limit it to one generation request per round, cap input and output tokens, validate exactly 15 distinct questions and four choices, and return a clear retry/limit message. Do not silently call two providers on the same tap because it may consume two quotas. Compare response completeness, latency and learner-facing accuracy with a fixed synthetic test set before switching the app. This recommendation is a candidate to test, not a claim that the service is production-ready or permanently free.

Sources checked 9 October 2026: [Cloudflare Workers AI pricing](https://developers.cloudflare.com/workers-ai/platform/pricing/), [Cloudflare JSON Mode and its supported models](https://developers.cloudflare.com/workers-ai/features/json-mode/), [Groq rate limits](https://console.groq.com/docs/rate-limits), and [OpenAI API and ChatGPT billing separation](https://help.openai.com/en/articles/9039756-managing-billing-settings-on-chatgpt-web-and-platform). Provider quotas, eligible models and prices may change; check each provider before release.
