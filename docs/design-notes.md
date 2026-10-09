# Let’sStudy design notes

## MagicPath Study Workspace implementation

The selected “Let’s Study — Study Workspace” revision is the visual source of truth. Its portrait home screen uses a left-aligned menu and page title, compact leaderboard/reminder/profile actions, a date and personal greeting, a streak chip, one prominent study composer, two compact shortcuts, and five quiet bottom-navigation destinations. The Compose implementation adapts this hierarchy to Android safe areas and scrolling while retaining the project's DM Sans and Manrope typefaces, violet accents, soft lavender surfaces, and white cards.

The same shared header, card treatment, and bottom navigation carry through the existing study, job preparation, library, progress, and settings flows. Home's composer creates the same topic path as the dedicated topic form; its job shortcut opens the existing job-offer flow. Material import remains available from New study. Saved studies, answers, explanations, tutor conversations, and progress remain backed by local Room records.

The canvas shows profile initials but no sign-in or account flow; the implemented app extends that reference with optional Firebase Authentication and private, consent-based Firestore sync. Guest study remains local to the phone. Unsupported OCR, code execution, and live legal verification are not represented as working features.

## Product idea

Let’sStudy turns a goal into a manageable learning path. For interview preparation, the job description sets the context; for other goals, the learner's topic or study material does. Every path combines repeatable practice, clear explanations and saved progress, without requiring learners to bring an AI account.

## Competitor patterns reviewed

| Product | Useful pattern | Let’sStudy decision |
| --- | --- | --- |
| [Quizlet Learn and Test](https://quizlet.com/features/study-modes) | Multiple-choice practice and separate study modes | Keep practice, saved questions, and answered-question review clearly labeled. |
| [Brilliant](https://brilliant.org/help/features/) | Hands-on questions with immediate feedback and guided explanations | Show a concise explanation after each choice and keep it available on return. |
| [Duolingo](https://blog.duolingo.com/new-duolingo-home-screen-design/) | A visible path and repeatable practice | Show progress through the set and let learners move to the next question without returning to a list. |
| [Khan Academy](https://blog.khanacademy.org/how-should-people-practice-on-khan-academy/) | Explanatory rationales after an answer | Explain why the correct option works and why a tempting alternative fails. |

These are behavior patterns, not visual assets to copy. The selected MagicPath canvas—not the earlier competitor notes—sets the current visual direction: violet, soft lavender, light neutral backgrounds, compact sans-serif navigation, and focused study cards.

## Full app experience

1. **Home:** a compact AI study composer starts a topic path; “Paste a job offer” and “Study a topic” open the existing focused flows. New study retains document import. A learner can resume a saved study further down the page.
2. **Library:** a personal shelf of saved sets with dates and languages, plus a clear way to start fresh and a confirmation before deletion.
3. **Study set:** progress and the next question are shown first. A compact 15-question action sits above a continuous route with all, studied answers, and saved filters.
4. **Question:** one prompt at a time, four large answer targets, immediate feedback in a focused explanation sheet, and persistent Previous/Next navigation. Older written-answer sets follow the same visual hierarchy.
5. **Offline setup and feedback:** download, waiting, failure, explanation, and deletion states share the same card shapes, colors, typography, and plain-language actions.

## Constraints

- Fast online sends one Gemini request per 15-question round. Review, navigation, and retry use saved local data.
- Learner sign-in is optional. The avatar opens profile/settings, and sessions and choices remain in Room on the phone unless the learner enables account sync.
- On-device generation remains available when the shared online quota runs out, with a slower response.
- Screens should remain readable with larger Android font settings; long questions and explanations scroll, while navigation remains visible.

## Language level, review and community features

- A standalone 20-question placement flow uses original English, Spanish, French and Thai items. It displays an approximate A1–C1/Pre-A1 written-skills starting point and can prefill a language study path. It is not an official score or a substitute for speaking, listening or writing assessment.
- Answered questions return in a local review queue. Correct recall gradually increases the interval, while a missed answer returns sooner. Reviews remain available offline.
- The community leaderboard is behind account sign-in and a separate nickname consent. Public fields exclude email, username, UID and study content. The displayed points are self-reported, capped weekly, and intended for friendly motivation rather than a verified contest.
- Scanned PDF pages can be recognized locally on Android for up to 30 Latin-script pages. The OCR dependency is bundled. Thai-script scanned pages remain unsupported; searchable PDF text continues through the existing reader.
