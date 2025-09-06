# Patch ledger

This repository uses chat-delivered patches tracked here. Each patch is identified by `CE-vX.Y-PNNN` and is applied with explicit `before/after` blocks.

## Index

| ID            | Title                                          | Date (Europe/Paris) | Status    | Affected                     |
|---------------|-------------------------------------------------|---------------------|-----------|------------------------------|
| CE-v7.3-P001  | Introduce PATCHES.md and .editorconfig         | 2025-09-06          | APPLIED   | PATCHES.md, .editorconfig    |
| CE-v7.3-P002  | Root PV/bestMove sanity checks (debug-only)    | 2025-09-06          | PROPOSED  | SearchFacade.java            |

---

## CE-v7.3-P001
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
