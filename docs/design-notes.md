# Let’sStudy design notes

## Product idea

Interview study should feel calm, personal, and easy to continue. A job offer becomes a route of short questions. Each answer teaches a concept; learners can revisit it later without spending another AI request.

## Competitor patterns reviewed

| Product | Useful pattern | Let’sStudy decision |
| --- | --- | --- |
| [Quizlet Learn and Test](https://quizlet.com/features/study-modes) | Multiple-choice practice and separate study modes | Keep practice, saved questions, and answered-question review clearly labeled. |
| [Brilliant](https://brilliant.org/help/features/) | Hands-on questions with immediate feedback and guided explanations | Show a concise explanation after each choice and keep it available on return. |
| [Duolingo](https://blog.duolingo.com/new-duolingo-home-screen-design/) | A visible path and repeatable practice | Show progress through the set and let learners move to the next question without returning to a list. |
| [Khan Academy](https://blog.khanacademy.org/how-should-people-practice-on-khan-academy/) | Explanatory rationales after an answer | Explain why the correct option works and why a tempting alternative fails. |

These are patterns, not visual assets to copy. Let’sStudy uses its own book-and-sprout mark, forest-green and warm-paper palette, serif headings, and coral accents.

## Full app experience

1. **Home:** start or resume a study set. A guided form separates source, language, and generation mode. Creation shows the real reading and writing stages.
2. **Library:** a personal shelf of saved sets with dates and languages, plus a clear way to start fresh and a confirmation before deletion.
3. **Study set:** progress and the next question are shown first. A compact 15-question action sits above a continuous route with all, studied answers, and saved filters.
4. **Question:** one prompt at a time, four large answer targets, immediate feedback in a focused explanation sheet, and persistent Previous/Next navigation. Older written-answer sets follow the same visual hierarchy.
5. **Offline setup and feedback:** download, waiting, failure, explanation, and deletion states share the same card shapes, colors, typography, and plain-language actions.

## Constraints

- Fast online sends one Gemini request per 15-question round. Review, navigation, and retry use saved local data.
- No account is required for learners. Sessions and choices remain in Room on the phone.
- On-device generation remains available when the shared online quota runs out, with a slower response.
- Screens should remain readable with larger Android font settings; long questions and explanations scroll, while navigation remains visible.
