# AethelAC — instructions for AI assistant sessions

This repository is developed by an AI assistant in long autonomous stretches.
**Before doing anything, read `HANDOFF/README.md` and `HANDOFF/SESSION_LOG.md`.**
They contain the standing mandate, the workflow loop, all conventions, verified
APIs, current state and next moves.

Key reminders:

- Work in a continuous loop (research → compare → plan → build → push → monitor
  CI → audit → next). Do not stop between stages to ask for permission.
- After every stage, append a detailed entry to `HANDOFF/SESSION_LOG.md` and
  keep `HANDOFF/README.md` (sections 6-7) up to date. Commit these with the
  stage work.
- No local builds — CI (GitHub Actions) compiles, checkstyles, tests and
  releases on every push to `main`. Monitor with:
  `gh run list -R aethelreborn/AethelAC -L 1 --json status,conclusion,databaseId`
- 3 commits per stage (code / config+punishments / docs), then one push.
- Never commit secrets. `AETHEL_DEV_NOTES.md` is legacy scratch (gitignored);
  the canonical log is `HANDOFF/SESSION_LOG.md`.
