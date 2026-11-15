# Community and generated-content reports

LetsStudy's adult experience has an optional signed-in leaderboard. Learners can report a public profile and block its entry locally. They can also report generated questions and tutor answers. Children and teens never see the community or online AI routes.

Reports are written to `contentReports/{uuid}` in Firestore with a category, source ID, reason, excerpt (maximum 500 characters) and server timestamp. Client rules allow only creation; they forbid reading, changing or deleting reports. Firebase App Check must be enforced for Firestore in the release project to reduce unauthenticated report abuse. App Check does not replace operational abuse monitoring or rate limits.

## Operator procedure before release

1. Assign an operator to monitor `contentReports` in Firebase Console at least daily while the community is enabled. Also monitor `admin@vallssolutions.com` for safety and deletion requests. A queue with no reviewer is not sufficient moderation.
2. Review the reason and excerpt. Do not assume the report is accurate. For a community report, inspect `publicLeaderboard/{subjectId}` and `leaderboardOwners/{subjectId}` through the Firebase Console or an Admin SDK. The owner document contains the account UID. Do not share that UID with reporters.
3. For a confirmed harmful public profile, create `moderationBans/{uid}` with an internal reason and timestamp using privileged Firebase Console or Admin SDK access, then delete the offending `publicLeaderboard/{entryId}`. Firestore rules prevent that UID from publishing a replacement entry. Notify the account owner when appropriate. Review repeated reports and escalate urgent safety concerns according to applicable law.
4. For a generated-content report, review the excerpt and source context available to the operator, correct the prompt/validation or provider configuration, and decide whether to block a type of generation. Because the full local study is not uploaded with a report, contact the reporter if more context is needed.
5. Record the action outside the public app data, restrict operator access, and remove reports after their review and any required retention. Handle access and deletion requests through `admin@vallssolutions.com`.

The app has no automated moderation dashboard, notification or server-side rate limit yet. If the operator cannot keep the stated review cadence, disable the public board before production release. A production acceptance run must verify live App Check, report writes, moderator review and the ban/delete path.
