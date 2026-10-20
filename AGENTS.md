# AethelAC — instructions for AI assistant sessions

This repository is developed by an AI assistant in long autonomous stretches.
Use the repository README and changelog as the maintained project documentation.

Key reminders:

- Work in a continuous loop (research → compare → plan → build → push → monitor
  CI → audit → next). Do not stop between stages to ask for permission.
- Update the relevant user-facing documentation with each shipped change.
- No local builds — CI (GitHub Actions) compiles, checkstyles, tests and
  releases on every push to `main`. Monitor with:
  `gh run list -R aethelreborn/AethelAC -L 1 --json status,conclusion,databaseId`
- 3 commits per stage (code / config+punishments / docs), then one push.
- Never commit secrets. `AETHEL_DEV_NOTES.md` is legacy scratch (gitignored);
  do not commit it.
