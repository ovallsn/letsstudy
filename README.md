# Let’sStudy

<p align="center"><img src="docs/letsstudy-icon.png" alt="Let'sStudy open-book and sprout icon" width="160"></p>

A native Android app that turns a job listing into interview practice. Paste a public job URL or its description, choose a question language, and receive 15 multiple-choice questions. Each question has four options and an immediate explanation. Move straight to the next question from the answer sheet or the navigation bar. Continue with another 15 whenever you want. Saved sessions and answers stay on the phone. Use **Study answers** to revisit your choice, the correct answer and a short explanation of the underlying concept. You can try a question again without generating a new batch. The side menu lists every session and requires confirmation before deleting a session and its questions.

## App preview

Screens captured from the Android emulator. The study path and question use a debug-only sample about Bitcoin mining support, so the preview does not consume Gemini quota.

| Home | Study path | Multiple choice | Explanation |
| --- | --- | --- | --- |
| <img src="docs/screenshots/home.png" alt="Let’sStudy home" width="190"> | <img src="docs/screenshots/study-path.png" alt="Study path and progress" width="190"> | <img src="docs/screenshots/question.png" alt="Four-choice practice question" width="190"> | <img src="docs/screenshots/explanation.png" alt="Answer explanation and next question" width="190"> |

## Study modes

**Fast online** is the default. The app reads the listing on the phone and asks Firebase AI Logic for all 15 questions in one Gemini Flash-Lite request. It requires internet and sends the extracted job text to Google. The app owner configures one Firebase project; learners need no AI account or API key. The Spark plan can be used without billing, but its **quota is limited and shared by all users of the project**. We cannot promise unlimited free generations or a fixed response time. If the quota is reached, choose the on-device mode or try again later.

To inspect the current online limit, open [Google AI Studio Rate limits](https://aistudio.google.com/rate-limit), select the same Google project as Firebase, and find **Gemini 3.1 Flash Lite**. RPM is requests per minute, TPM is input tokens per minute, and RPD is requests per day. The usage shown over a selected date range is a historical maximum, not a guaranteed live balance. **Dashboard → Usage** shows consumption. Reviewing saved answers does not call Gemini.

**On-device** uses Gemma 4 E2B through LiteRT-LM. It needs a one-time download of about 2.59 GB and enough free storage. Generation runs locally and may take several minutes. URLs still require internet to load; pasted job descriptions can be studied offline once the model is downloaded. Existing free-response sessions stay readable after updating; new questions use multiple choice.

The app interface and initial question language are English. Spanish, Thai and a custom language are available.

## Privacy and accuracy

- Fast online sends extracted listing text and prior question titles to Firebase AI Logic. Answers, progress and saved sessions remain in Room on the device.
- On-device generation keeps the listing and prompts on the phone. The model download contacts Hugging Face.
- Public job sites may block automated reading or require sign-in. Paste the job text when a URL cannot be read.
- Generated answers may be wrong. Verify commands, security guidance and company details before relying on them.
- The current product focuses on job offers; general topics and presentation/PDF import are planned but not implemented.

## Build and Firebase setup

Open `android/` in Android Studio or build from that folder:

```powershell
cd android
./gradlew.bat :app:assembleDebug
```

Create a Firebase Android app with package ID `com.oriol.letsstudy`, enable **Firebase AI Logic** using the **Gemini Developer API** on the Spark plan, and place its `google-services.json` at `android/app/google-services.json`. That file is excluded from Git. Firebase App Check protects the project's free quota. For a debug APK, register the debug token printed by the SDK in **Firebase Console → App Check → Apps → Manage debug tokens**. Never commit or share that token. For a distributed release build, configure **Play Integrity** in App Check and the appropriate signing certificate; see [Firebase's Play Integrity guide](https://firebase.google.com/docs/app-check/android/play-integrity-provider). Debug tokens are for development only. Each project owner should monitor model limits in [Google AI Studio](https://aistudio.google.com/rate-limit).

For local validation:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

The APK is `android/app/build/outputs/apk/debug/app-debug.apk`. A successful build verifies the local code, but online generation, App Check registration and response time need a real device and the configured Firebase project.

## Architecture

- `android/` — Kotlin, Jetpack Compose, Room, WorkManager, Firebase AI Logic, LiteRT-LM and a bounded HTTPS job-page reader.
- `backend/` — an earlier prototype that the Android app does not call.
- `docs/` — architecture, feasibility notes and portfolio assets.

The visual direction and competitor references are in [design notes](docs/design-notes.md).

Gemma 4 E2B is authored by Google DeepMind and licensed under Apache 2.0. See [third-party notices](THIRD_PARTY_NOTICES.md). Let’sStudy itself has no project license selected yet.
