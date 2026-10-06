# Architecture Notes

## Study flow

1. The user provides a public HTTPS job URL or pastes the description, then chooses a question language. `JobOfferReader` extracts `JobPosting` JSON-LD or readable page text on the phone. It does not bypass sign-in or challenge pages.
2. Fast online mode is the default. `FirebaseFastQuestionGenerator` sends one structured request for a 15-question round to Gemini Flash-Lite through Firebase AI Logic. App Check uses the debug provider in development and Play Integrity in release builds.
3. On-device mode uses the verified Gemma 4 E2B download through LiteRT-LM. It builds three validated five-question batches. The user chooses this slower mode explicitly.
4. `StudyOutputParser` validates the count, distinct prompts, four distinct options, the correct index and an explanation before a full round is saved. Invalid output never creates a partial round.
5. Room stores sessions, question choices and review flags locally. Choosing an answer writes one local row and opens the explanation immediately; no evaluation API call is made. Legacy free-response questions remain readable.
6. Continuing a session can use either study mode, so a user can move from online to on-device generation if the free quota is unavailable.

## Data and cost boundaries

- A job URL is fetched directly by the phone. Online generation sends extracted listing text and prior question prompts to Firebase AI Logic. Saved answers and progress are kept in Room.
- The Firebase Spark plan needs no billing account, but free quotas are finite and shared across the owner's project. App Check reduces abuse; it does not make generation unlimited.
- On-device generation sends no listing text to a generation service. The model file is downloaded from Hugging Face into app-private storage and excluded from backups and Git.
- The Firebase configuration file is excluded from Git. Each project owner supplies their own `google-services.json`. Development App Check tokens must never be committed.

## Failure states

- Unreadable URL: ask the user to paste the listing text.
- Online quota, service or App Check failure: explain the issue and keep existing sessions intact; the on-device mode is available if downloaded.
- Missing local model: show the one-time download flow.
- Invalid or incomplete generated JSON: reject the round without storing partial questions.
- Multiple-choice answers are scored locally and remain available offline after generation.
