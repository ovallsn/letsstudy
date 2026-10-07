# Architecture notes

## Study flow

1. The learner provides a public HTTPS job URL or pastes a description, then chooses a question language. `JobOfferReader` extracts structured job data or readable page text on the phone. It does not bypass sign-in, challenge pages, or access controls.
2. Fast online mode is the default. `FirebaseFastQuestionGenerator` sends a structured request for a 15-question round to Gemini 3.1 Flash-Lite through Firebase AI Logic. App Check uses the debug provider in development and Play Integrity in release builds.
3. On-device mode uses the verified Gemma 4 E2B download through LiteRT-LM. It builds three validated five-question batches. The learner chooses this slower mode explicitly.
4. `StudyOutputParser` validates the question count, distinct prompts, four distinct options, correct-answer index, and explanation before a full round is saved. Invalid output never creates a partial round.
5. Selecting a multiple-choice answer is scored locally and reveals the explanation immediately. No evaluation request is made. The learner can continue to the next question without returning to the question list. Legacy free-response questions remain readable and retain their saved feedback.
6. A learner can request a concept lesson for an individual question. The online prompt combines the session context with the question, choices, answer, explanation, and role basis. The response must contain four required teaching sections, two to five glossary terms, and a concise takeaway. The parser rejects incomplete, malformed, or oversized content before it is saved.
7. `StudyRepository` stores a validated lesson on its question row. Opening a cached lesson reads the Room value and requires no network or Gemini request. Database version 3 adds the lesson column with an explicit additive migration from version 2; the version 1-to-2 migration remains in place.
8. Continuing a session can use either question-generation mode, allowing the learner to switch from online to on-device rounds when the shared Gemini quota is unavailable.

## Data and cost boundaries

- A job URL is fetched by the phone. Online question generation sends extracted job context and, for continuation rounds, earlier question prompts to Firebase AI Logic.
- A first concept lesson request sends the relevant question, answer choices, correct answer, existing explanation, role context, and source basis. The learner's written answers, selected choices, and review flags are not included in that request.
- The first lesson for a question consumes one additional Gemini request from the same finite, shared project quota. The result is stored locally; reopening it does not consume quota.
- The Firebase Spark plan does not require a billing account, but free quotas are finite, shared by the project, and subject to change. App Check reduces abuse; it does not make generation unlimited.
- On-device generation sends no listing text to a generation service. The model file is downloaded from Hugging Face into app-private storage and excluded from backups and Git.
- Firebase configuration, debug App Check tokens, local settings, build output, and signing material are excluded from source control.

## Failure states

- Unreadable URL: prompt the learner to paste the listing text.
- Online quota, service, network, timeout, or App Check failure: show a retry path and retain saved questions, answers, and navigation state.
- Missing local model: show the one-time download flow.
- Invalid or incomplete generated JSON: reject it without caching a partial round or lesson.
- Multiple-choice answers and cached lessons remain available offline after they have been saved.
