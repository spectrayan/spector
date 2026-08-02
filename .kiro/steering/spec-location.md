---
inclusion: always
---

# Spec location — create specs in the spectrayan repo

Specs for Spector are NOT kept in this repo. The `spectrayan` repo is the canonical home for all requirements/specs across the portfolio.

- When asked to create or edit a spec, create it under `spectrayan/.kiro/specs/<spec-name>/` (requirements.md, design.md, tasks.md).
- Note the target repo (`spectrayan/spector`) inside the spec's `requirements.md`.
- Implementation and code changes still happen here in `spector`; only the spec documents live in `spectrayan`.
- R&D reports and ADRs go to `spectrayan/RnD/`.
