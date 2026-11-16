# LetsStudy Play Store readiness design

## Intent

Prepare the Android app and its public support material for a Google Play release under Valls Solutions. The app should serve learners from age 9 upward internationally. The repository remains the source for Android code, Firebase rules, release automation, store assets, and public policy pages. Publication and live service configuration are separate release gates.

## Product approach

- A neutral age selection precedes access to the app. Ages 9–12 and 13–17 get a local study experience built from original, reviewed material. It must not initialize account sync, online AI, ChatGPT sign-in, file or URL import, dictation, or the community board. Ages 18+ keep the existing workspace.
- The child experience must be useful on its own: short lessons, practice, explanations, local progress, and a clear way to erase progress. No login or personal profile is needed.
- Adult learners may report generated content from the question, lesson, and tutor surfaces without leaving the app. Signed-in adults may report a community entry and hide it from their own board. Reports go to a restricted Firestore collection and have visible success and failure states. Community rules and a real review process accompany the feature.
- Publishable privacy and account-deletion pages are prepared for `letsstudy.vallssolutions.com`, naming Valls Solutions and `admin@vallssolutions.com`. The external deletion path must be actionable without installing the app; support must verify ownership before deleting data. No secrets or unauthenticated deletion endpoints are exposed.
- The release path produces a signed Android App Bundle using the existing upload key secrets. Play Console settings, Firebase production configuration, DNS, and signing key material are never committed.

## Constraints and decisions

- Keep Kotlin, Compose, Room, Firebase, current design tokens, and the existing adult features.
- Child and unknown-age paths fail closed: they do not show the adult workspace until an adult selection is recorded. An age selection is a routing control, not legal proof of age or parental consent.
- Do not present the age selection as a reward gate; options receive equal visual prominence. Do not claim the resulting build is legally certified for children without live SDK/data-flow and jurisdictional review.
- No new Android or JavaScript dependency is needed for the app changes. Static policy pages may use plain HTML and CSS.
- Store screenshots must show real app screens, meet Play dimensions, and exclude personal data.

## Release gates outside source control

1. Verify Cloudflare DNS and serve the policy and deletion pages over HTTPS at the chosen subdomain.
2. Confirm that the live Firebase project has the reviewed Firestore rules, App Check enforcement, indexes, and a monitored abuse-report review process.
3. Verify data collection by all SDKs, including the speech service, for each age path and obtain qualified legal review for children in the release regions.
4. Set the Play Console audience, Data safety, content rating, app access, foreground-service declaration, store listing, Play App Signing, and tester track accurately.
5. Test a Play-installed signed bundle on devices and complete any account-specific closed-testing requirement.
