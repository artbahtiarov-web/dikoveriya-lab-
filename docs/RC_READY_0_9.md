# 0.9.0-rc1 — signed hardware validation trigger

Date: 2026-11-13 UTC.

This file intentionally triggers the isolated branch-only permanent-key Android release-candidate workflow.

- The code includes a direct NIIMBOT BLE implementation, 28 separate embedded OFL Cyrillic font families, B/I/U typography and physical 40×12mm element resizing.
- Latest successful PR build before this trigger: https://github.com/artbahtiarov-web/dikoveriya-lab-/actions/runs/43412642258 .
- UI emulator run for latest candidate must be checked independently before declaring completed validation.
- **Do not merge this branch to main** until the owner has printed a physical 40×12mm label, verified QR, orientation, print statuses, and ten unique codes.
- Do not install PR debug APK over permanently signed 0.8.0. Only signed artifact from `build-niimbot-rc.yml` after certificate SHA-256 check is eligible for hardware test.
- After successful physical test and final regressions, create stable 0.9.0 release with a new signed main branch run.
- Related PR: https://github.com/artbahtiarov-web/dikoveriya-lab-/pull/20
- Target criteria: https://github.com/artbahtiarov-web/dikoveriya-lab-/issues/19
