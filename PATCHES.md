# Patch ledger

This repository uses chat-delivered patches tracked here. Each patch is identified by `CE-vX.Y-PNNN` and is applied with explicit `before/after` blocks.

## Index

| ID           | Title                                          | Date (Europe/Paris) | Status    | Affected                     |
|--------------|-------------------------------------------------|---------------------|-----------|------------------------------|
| CE-v7.3-P001 | Introduce PATCHES.md and .editorconfig         | 2025-09-06          | APPLIED   | PATCHES.md, .editorconfig    |
| CE-v7.3-P002 | Root PV/bestMove sanity checks (debug-only)    | 2025-09-06          | PROPOSED  | SearchFacade.java            |
| CE-v8.0-P003 | LMP + History Pruning (non-PV, shallow)        | 2025-09-06          | PROPOSED  | SearchConfig.java, Negamax.java |
| CE-v8.0-P004 | Soften LMP; disable HP by default; safety gates| 2025-09-06          | PROPOSED  | SearchConfig.java (defaults), Negamax.java |

---

## CE-v8.0-P004
- **Title:** Soften LMP; disable HP by default; safety gates
- **Rationale:** Previous LMP/HP stack over-pruned late quiets and hurt playing strength. New gates avoid pruning pawn pushes/castling, require staticEval far below α, shrink depth/indices, and disable HP by default.
- **Risk:** Low to moderate. Should recover ELO while retaining speed-up.
- **Testing:** Same gauntlet/openings. Check that nodes/s is still higher vs. pre-P003, with draw rate and tactics stable.

## CE-v8.0-P001
- **Title:** Introduce PATCHES.md ledger and .editorconfig
- **Rationale:** Establish lightweight change tracking and enforce consistent whitespace/indentation. No runtime impact.
- **Risk:** None
- **How to test:** `mvn -q -DskipTests package` behaves exactly as before.
- **Dependencies:** None
- **Notes:** Future patches will append entries here with status updates (PROPOSED → APPLIED/REVERTED/SUPERSEDED).

## CE-v7.3-P002
- **Title:** Root PV/bestMove sanity checks (debug-only)
- **Rationale:** Catch illegal `bestMove` or PV head mismatch early during debug runs; zero perf impact when debug=false.
- **Risk:** Low; debug-only.
- **How to test:** Run perft and a tiny gauntlet with `debug=true`; expect no exceptions.
- **Dependencies:** None
- **Notes:** Exceptions include the patch ID for quick grepping.

## CE-v7.3-P003
- **Title:** LMP + History Pruning (non-PV, shallow)
- **Rationale:** Skip hopeless late quiets at shallow depth and those with very weak history, while respecting killers/TT and king-danger.
- **Risk:** Low-moderate; guarded by depth, non-PV, no-check, danger-aware gates.
- **How to test:** Bench with same openings/time control; expect speedup and stable or improved ELO. Verify no tactical blowups on standard test suites.
