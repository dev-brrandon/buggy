# LEGIT G1 / First-Live — Canonical Live Candidate Versions

Date: 2026-10-06

## Versions to deploy live

| Component | Canonical live candidate | Status |
|---|---|---|
| Legit Assistant | `0.1.0-dev.19` | Stage-1 accepted; dev.19 is the release bump for the dev.18 + First-Live H1-H5 source |
| NetworkCore | `0.18.0-dev.23.5-hotfix.70` (NC70) | Release bump for G1.2 Authority + G1.3 Discord changes accumulated after NC69 |
| System | `1.1.0-dev.23.5-hotfix.68` (SYS68) | unchanged compatibility baseline |
| LegitPlatform | `0.1.2-dev.8` | unchanged compatibility baseline |
| Panel Gateway | `0.1.0-dev.22` | unchanged G1.1/G1.1.5 baseline |
| Panel Web | `0.1.0-dev.15` | unchanged G1.1/G1.1.5 baseline |
| Buggy Discord Bot | `0.1.0-dev.1` | G1.3 source implemented/offline-verified; live acceptance pending |
| PostgreSQL | `18` (`postgres:18-alpine` in first-live stack) | Stage-1 accepted |
| Java | `25` | Stage-1 accepted |
| Gradle | `9.6.1` | full quality gate accepted |

## Version discipline

Assistant and NetworkCore receive new release identifiers because their source changed after the previously named dev.18 / NC69 baselines. System, LegitPlatform and Panel remain unchanged because no source modifications were required for the First-Live Stage-1 work.

The Assistant's Foundation compatibility baseline remains `0.1.0-dev.18`; `0.1.0-dev.19` is the product/live-candidate release that carries G1.2/G1.3 plus the validated First-Live corrections.
