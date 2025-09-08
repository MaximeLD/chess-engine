# Patch ledger

This repository uses chat-delivered patches tracked here. Each patch is identified by `CE-vX.Y-PNNN` and is applied with explicit `before/after` blocks.

## Index

| ID            | Title                                                                                        | Date (Europe/Paris) | Status   | Affected                                                                            |
|---------------|----------------------------------------------------------------------------------------------|---------------------|----------|-------------------------------------------------------------------------------------|
| CE-v13.0-P001 | Syzygy endgame tables: DTZ at root, WDL inside search                                        | 2025-09-07          | PROPOSED | tb/*, search/SearchFacade.java, tb/TBManager.java, search/{Negamax,Quiescence}.java |
| CE-v12.0-P001 | Opening book (Polyglot) + UCI options + search hook                                          | 2025-09-07          | PROPOSED | book/*, uci/UciEngineImpl.java, utils/notations/MoveIOUtils.java                    |
| CE-v11.0-P003 | Eval Batch C: space, queen-7th, doubled rooks, outside & candidate passers, EG king activity | 2025-09-07          | PROPOSED | PositionEvaluator.java                                                              |
| CE-v11.0-P002 | Eval: bishop quality, simple threats, long-diagonal poke                                     | 2025-09-07          | PROPOSED | PositionEvaluator.java                                                              |
| CE-v11.0-P001 | Evaluator: rooks/open+7th; outposts; passer FX                                               | 2025-09-06          | PROPOSED | PositionEvaluator.java                                                              |
| CE-v10.0-P002 | SE fix: per-ply exclude + stricter gates                                                     | 2025-09-06          | PROPOSED | SearchContext.java, Negamax.java, SearchConfig.java                                 |
| CE-v10.0-P001 | Singular Extension v1 (PV-only)                                                              | 2025-09-06          | PROPOSED | SearchConfig.java, SearchContext.java, Negamax.java                                 |
| CE-v9.0-P001  | Razoring at shallow depth (d≤2)                                                              | 2025-09-06          | PROPOSED | Negamax.java                                                                        |
| CE-v8.0-P004  | Soften LMP; disable HP by default; safety gates                                              | 2025-09-06          | PROPOSED | SearchConfig.java (defaults), Negamax.java                                          |
| CE-v8.0-P003  | LMP + History Pruning (non-PV, shallow)                                                      | 2025-09-06          | PROPOSED | SearchConfig.java, Negamax.java                                                     |
| CE-v7.3-P002  | Root PV/bestMove sanity checks (debug-only)                                                  | 2025-09-06          | PROPOSED | SearchFacade.java                                                                   |
| CE-v7.3-P001  | Introduce PATCHES.md and .editorconfig                                                       | 2025-09-06          | APPLIED  | PATCHES.md, .editorconfig                                                           |

---

## CE-v13.0-P001
- **Title:** Syzygy endgame tables: DTZ at root, WDL inside search
- **Rationale:** Use DTZ to pick a root move that respects the 50-move rule; use WDL for fast exact/static results at interior nodes and quiescence.
- **Risk:** Low. Guarded by piece-count/enable flags; falls back to normal search when out of scope.
- **Testing:** Unit tests for probeWDL/probeRoot already pass; quick matches in 3–5 man endings show faster convergence and correct 50-move handling.

**CE-v12.0-P001**
- Add OpeningBook API and BookManager.
- Implement memory-mapped PolyglotBook (.bin): binary search by key, legal-move validation, weighted selection.
- UCI options: OwnBook, BookFile, BookMaxPlies, BookMinWeight, BookRandomness, BookPreferMainline.
- Probe book before search; return book move when available.
- Reuse existing Polyglot Zobrist table (ZobristHashKeys).

## CE-v11.0-P003
- Space (MG-only, safe & capped).
- Queen on 7th (useful-only).
- Doubled rooks on semi/open files (tiny).
- Outside passer (EG-only).
- Candidate passers (MG-only).
- Endgame king activity (EG-only, queens off, relative to opponent).
- All weights conservative to avoid search noise.

## CE-v11.0-P002
- **Title:** Evaluator: bishop quality, simple threats, long-diagonal king poke
- **Rationale:** Cheap, general heuristics that improve move ordering and conversion without bloating the tree.
- **Risk:** Low. Conservative weights, tight caps, allocation-free.
- **Testing:** A/B vs v10 and v11.0-P005 with identical openings/TC. Expect small Elo uptick; if noisy, trim the caps by 25%.

## CE-v11.0-P001
- **Title:** Evaluator: rooks/open+7th; knight outposts; passed-pawn extras
- **Rationale:** Three classic, low-risk heuristics that convert mobility and pawn advantages into real pressure and cleaner endgames.
- **Risk:** Low. Weights conservative and phase-blended.
- **Testing:** Bench vs v10 with identical TC/openings; expect small speed-neutral Elo gain. Check a few KRP vs KR endgames for improved conversion.

## CE-v10.0-P001
- **Title:** Singular Extension v1 (PV-only)
- **Rationale:** When the TT move is convincingly better than all alternatives, extend it by +1 ply. Improves deep tactics and stability with conservative gates.
- **Risk:** Low–moderate; gated by depth, PV, TT bound, and verification search excluding the TT move.
- **How to test:** Same gauntlet as v9.0. Expect neutral-to-positive ELO with small node increase. Verify no time blowups.

## CE-v9.0-P001
- **Title:** Razoring at shallow depth (d≤2)
- **Rationale:** Fast fail-low at depth 1–2 when static eval is well below α. Confirm with qsearch to avoid tactical misses.
- **Risk:** Low. Guarded by mate margin, no-check, and king-danger.
- **How to test:** Same opening set + TC. Expect speedup; no tactical regressions on standard suites.
- **Dependencies:** None.
- **Notes:** Margins (150/300) are conservative and can be tuned later.

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
