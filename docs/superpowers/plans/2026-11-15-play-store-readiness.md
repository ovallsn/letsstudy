# LetsStudy Play Store Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the app, its policy pages, moderation flows, store assets, and release bundle ready for Google Play review for learners aged 9 and up.

**Architecture:** Keep the current adult workspace. Route younger learners into a separate local-only Compose experience before constructing the adult ViewModel. Use restricted Firestore writes for adult reports and a private blocked-entry list for the community board. Keep public policy pages as static files deployable to a dedicated subdomain.

**Tech Stack:** Kotlin, Jetpack Compose, Room/SharedPreferences, Firebase Firestore rules and emulator tests, Gradle, GitHub Actions, static HTML/CSS.

**Spec:** `docs/superpowers/specs/2026-11-15-play-store-readiness-design.md`

## Global Constraints

- Preserve all existing adult study behavior and current visual tokens.
- Children and unknown-age users must not reach adult network, account, import, speech, or social flows.
- Do not store a birthdate or identifying information for child routing.
- Do not publish policy pages or upload a signed build until URLs, production configuration, and release artifacts are reviewed.
- Keep signing credentials, Firebase production config, and private report data out of Git.

## Review Focus

- A child relaunches the app: the child route persists and no adult ViewModel is constructed.
- An unknown-age user: no adult data operation starts before the age choice.
- A guest adult reports AI content: the report is bounded and write-only; failure is visible.
- A signed-in adult blocks a board entry: it disappears without deleting another learner's data.
- A release bundle: tests and lint pass; the AAB is signed only when all release secrets are supplied.

### Task 1: Age routing and child-safe study

- [x] Add failing tests for age routing, local progress, and age-appropriate question content.
- [x] Implement neutral age selection and a separate local-only youth Compose flow.
- [x] Keep Firebase-backed adult initialization out of youth routing and remove Firebase's eager init provider. Live device network capture remains a release gate.
- [x] Run focused and full Android unit tests.

### Task 2: In-app reports and community controls

- [x] Add failing Firestore rules tests for bounded write-only reports and moderator bans. The blocked-entry list stays local by design.
- [x] Add failing Kotlin tests for report validation and board filtering.
- [x] Implement report actions on generated questions, lessons and tutor responses and community entries, plus block/unblock and user feedback.
- [x] Add community rules and an operator review procedure; run Firestore and Android tests.

### Task 3: Privacy and account deletion

- [x] Create app-specific static privacy and deletion-request pages using the publisher and contact details supplied by the owner.
- [x] Link privacy and deletion information from Android Settings and the youth route.
- [x] Document manual verification and deletion handling; test links, responsive layout and accessibility locally.

### Task 4: AAB and Play assets

- [x] Add signed AAB output to the existing release workflow without exposing the upload key or artifacts unnecessarily.
- [x] Prepare a 512 px store icon, 1024×500 feature graphic, and conforming screenshots from the app.
- [x] Document Play Console declarations and conditional testing requirements.
- [x] Build and inspect debug, lint, unit tests, Firestore rules, and unsigned AAB locally.

### Task 5: Final review

- [x] Inspect changed files and configuration for privacy, security, and age-path regressions.
- [x] Verify generated assets and policy pages visually at phone and desktop sizes.
- [x] Record remaining production-only gates without claiming live Firebase or Play Console validation.
