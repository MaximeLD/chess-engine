Clean engine:

# V7.4
Search hygiene & anti-draw behavior

- Root anti-repetition ordering: demote immediate bounce-back quiets at the root so non-repeating candidates get searched first.

- Banded contempt: draw bias scales with eval (0/½/1× at ≤60 / 60..120 / >120 cp), so +0.8 to +1.2 positions actively avoid cheap repetitions. True threefold/stalemate remain exact 0.

- Early-repetition handling: integrates with ordering so repetition lines don’t dominate PV by inertia.

LMR (Late Move Reductions) safety

- Never reduce recaptures; softer reductions for captures/checks.

- Shallow-depth clamps: max R=1 at depth ≤6, R=2 at depth ≤8; tiny PV cap.

- Keeps tactical refutations alive while retaining speed on quiet tails.

Pruning in sharp positions

- ProbCut gated by quick king-danger heuristic; skipped in dangerous king positions.

- Futility tightened/disabled in king-danger nodes to avoid pruning necessary defenses/attacks.

- Null-move verification: after a null-move cutoff in dangerous nodes, verify with a reduced search.

Bug-fix & robustness

- Trusted TT-move hoist is segment-safe (never swaps beyond moveCount, respects capture/quiet boundary).

- Debug hooks: optional post-make sanity check that our own king isn’t left in check (behind cfg.debug).

Config knobs (defaults on)
```
rootAntiRepOrdering = true

dangerGatesProbCut = true

tightenFutilityOnDanger = true

nmpVerifyOnDanger = true
```

(Banded contempt uses existing useContempt, dynamicContempt, contemptCp.)

Expected effect

- Fewer “+ but draw by repetition” outcomes; better conversion in slightly better positions.

- More stable evals in sharp middlegames (fewer phantom fail-highs).

- Minimal speed loss; reductions still aggressive on safe, quiet branches.

# V7.3
- V7.2 +
    - Smart contempt (avoid 3fold repetition when winning)

# V7.2
- V7.1 +
    - Dynamic LMR (reduction R is now dependent on the current context)

# V7.1
- V7 +
    - Bugfix sometimes hoisting TT move before quiet moves
    - Bugfix in beta cutoff heuristic not properly considering the side to move

# V7
- V6 +
    - Move pruning:
        - ProbCut

# V6
- V5 +
    - Move ordering:
        - Internal Iterative Deepening (IID)

# V5
- V4 +
    - Move pruning:
        - Futility Pruning (FP), Extended Futility (EFP), and Reverse Futility (RFP)

# V4
- V3 +
  - Move ordering:
    - Countermove & Continuation History (CMH / CH)

# V3
- V2 +
  - Move pruning:
    - Late Move Reduction

# V2
- V1 +
  - Move pruning:
    - Null Move
# V1
- Negamax
- Iterative Deepening
- Quiescence
- Transposition Table
- Move reordering
  - TT Move reordering
  - MMV-LVA
  - Killer moves
- Move pruning:
  - SEE

