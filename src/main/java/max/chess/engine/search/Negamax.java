package max.chess.engine.search;

import max.chess.engine.game.Game;
import max.chess.engine.movegen.Move;
import max.chess.engine.search.transpositiontable.TranspositionTable;
import max.chess.engine.tb.TBUtils;
import max.chess.engine.utils.ColorUtils;

final class Negamax {

    static int search(Game game, SearchContext ctx, int depth, int ply,
                      int alpha, int beta,
                      java.util.concurrent.atomic.AtomicBoolean stop,
                      long start, long budgetNs) {
        boolean isPV = (beta - alpha) > 1; // wide window => PV node
        return search(game, ctx, depth, ply, alpha, beta, stop, start, budgetNs, false, isPV);
    }

    // Internal variant carrying null-move context and PV-ness
    static int search(Game game, SearchContext ctx, int depth, int ply,
                      int alpha, int beta,
                      java.util.concurrent.atomic.AtomicBoolean stop,
                      long start, long budgetNs,
                      boolean inNullMove, boolean isPV) {
        if (TimeControl.aborted(stop, start, budgetNs)) return Integer.MIN_VALUE;

        int wdlBias = 0;
        if (ctx.tbProbeInSearch && ctx.tb != null) {
            var w = ctx.tb.probeWDL(game);
            if (w.isPresent()) wdlBias = TBUtils.scoreFromWDL(w.getAsInt());
        }

        final long z0 = game.zobristKey();
        final int alphaOrig = alpha;
        final long key = game.zobristKey();
        // Remember this node’s key for repetition checks on the line
        if (ply < ctx.keyStack.length) ctx.keyStack[ply] = key;
        boolean cutOccurred = false;

        max.chess.engine.search.transpositiontable.TranspositionTable.Hit hit =
                (ctx.tt != null) ? new max.chess.engine.search.transpositiontable.TranspositionTable.Hit() : null;

        // TT bounds (as before)
        if (ctx.tt != null && ctx.cfg.useTTBounds && depth >= 3) {
            if (ctx.tt.probe(key, depth, ply, hit)) {
                if (hit.flag == max.chess.engine.search.transpositiontable.TranspositionTable.TT_EXACT) {
                    ctx.pvLen[ply] = 0; ctx.tt.countCutoff();
                    return hit.score;
                } else if (hit.flag == max.chess.engine.search.transpositiontable.TranspositionTable.TT_LOWER) {
                    if (hit.score > alpha) alpha = hit.score;
                    if (alpha >= beta) {
                        ctx.pvLen[ply] = 0; ctx.tt.countCutoff();
                        return hit.score;
                    }
                } else {
                    if (hit.score < beta) beta = hit.score;
                    if (alpha >= beta) { ctx.pvLen[ply] = 0; ctx.tt.countCutoff();
                        return hit.score;
                    }
                }
            }
        }

        ctx.nodes++; ctx.totalNodes++;
        if (game.isHardDraw()) {          // <-- threefold NOT included here
            ctx.pvLen[ply] = 0;
            return 0;
        }


        // LMR/null-move can push depth below zero; treat all <=0 as quiescence.
        if (depth <= 0) {
            if (ply < ctx.pvLen.length) ctx.pvLen[ply] = 0;
            return Quiescence.search(game, ctx, alpha, beta, ply, stop, start, budgetNs);
        }

        // Cheap stand-pat; if already >= beta, null search likely to cut
        int standPat = StaticEvalCache.get(game, ctx);
        // wdlBias will be 0 if syzygy knowledge is disabled
        standPat += wdlBias;

        // Reverse Futility Pruning (very conservative)
        // Non-PV, not in check, shallow depth only, and far from mate bounds.
        if (ctx.cfg.useReverseFutility && !isPV && depth <= 2 && !game.inCheck()) {
            int mateGuard = max.chess.engine.search.evaluator.GameValues.CHECKMATE_VALUE - 200;
            if (Math.abs(alpha) < mateGuard && Math.abs(beta) < mateGuard) {
                int margin = ctx.cfg.reverseFutilityMargin * depth;
                if (standPat - margin >= beta) {
                    return beta;     // clean fail-high semantics
                }
            }
        }

        // Razoring (shallow fail-low shortcut): non-PV, not in check, d <= 2.
        // If static eval is far below alpha, confirm with qsearch and return early.
        if (!isPV && !game.inCheck() && depth <= 2) {
            int mateGuard = max.chess.engine.search.evaluator.GameValues.CHECKMATE_VALUE - 200;
            if (Math.abs(alpha) < mateGuard && Math.abs(beta) < mateGuard) {
                // Conservative margins; tune later if needed.
                final int razorMargin = (depth == 1 ? 150 : 300);
                // In high king danger, skip razoring to avoid cutting defensive resources.
                boolean danger = max.chess.engine.search.KingSafety.quickDanger(game);
                if (!danger && standPat + razorMargin <= alpha) {
                    int qs = Quiescence.search(game, ctx, alpha, beta, ply, stop, start, budgetNs);
                    if (qs <= alpha) {
                        if (ply < ctx.pvLen.length) ctx.pvLen[ply] = 0;
                        return qs;
                    }
                }
            }
        }

        // -----------------------------
        // Null-move pruning (conservative)
        // -----------------------------
        if (ctx.cfg.useNullMove
                && !inNullMove
                && depth >= ctx.cfg.nullMinDepth
                && !game.inCheck()
                && !(ctx.cfg.disableNullInPV && isPV)
                && !(ctx.cfg.disableNullInZugzwangish && isZugzwangish(game))) {

            ctx.nmpTried++;

            if (standPat >= beta) {
                int R = ctx.cfg.nullBaseReduction + ((depth >= 6) ? 1 : 0); // simple adaptive bump
                long undoNull = game.playNullMove(); // flips side, clears EP, updates zobrist
                int score = -search(game, ctx, depth - 1 - R, ply + 1,
                        -beta, -beta + 1, stop, start, budgetNs,
                        true, /*isPV=*/false);
                game.undoNullMove(undoNull);
                if (score >= beta) {
                    boolean mustVerify = ctx.cfg.nmpVerifyOnDanger && depth >= ctx.cfg.nmpVerifyMinDepth && KingSafety.quickDanger(game);
                    // Optional verification search at shallow depths or when danger flagged
                    if (mustVerify || (ctx.cfg.nullVerifyDepth > 0 && depth - 1 - R <= ctx.cfg.nullVerifyDepth)) {
                        ctx.nmpVerify++;
                        int verify = -search(game, ctx, depth - 1, ply + 1,
                                -beta, -beta + 1, stop, start, budgetNs,
                                false, /*isPV=*/false);
                        if (verify >= beta) { ctx.nmpCut++; return beta; }
                        else { ctx.nmpVerifyFail++; /* fall through */ }
                    } else {
                        ctx.nmpCut++;
                        return beta;
                    }
                }
            }
        }

        // Decide TT hint trust BEFORE ordering; run IID if needed
        int trustedTTMove = 0;
        boolean haveTrustedTT = false;
        int candidate = 0;
        if (ctx.tt != null) {
            ctx.tt.probe(key, 0, ply, hit);
            candidate = hit.move;
//            int ttMove = hit.move;
            if (candidate != 0) {
                boolean trust = (hit.depth >= depth) || (hit.depth >= depth - 1 && hit.flag != TranspositionTable.TT_UPPER);
                if (trust) { haveTrustedTT = true; }
            }
        }
        if (!haveTrustedTT
                && ctx.cfg.useIID
                && depth >= ctx.cfg.iidMinDepth
                && (!ctx.cfg.iidPvOnly || isPV)
                && (ctx.cfg.iidWhenInCheck || !game.inCheck())) {

            ctx.iidTried++;
            int red = Math.min(ctx.cfg.iidReduction, Math.max(1, depth - 1));
            // Narrow window probe to seed TT with a good move
            int iidScore = search(game, ctx, depth - red, ply, alpha, alpha + 1, stop, start, budgetNs, false, /*isPV*/false);
            if (iidScore == Integer.MIN_VALUE) return Integer.MIN_VALUE;

            // Re-probe for a new hint
            if (ctx.tt.probe(key, 0, ply, hit)) {
                candidate = hit.move;

                boolean trust = candidate != 0 && ((hit.depth >= depth - red) || (hit.flag != TranspositionTable.TT_UPPER));
                if (trust) {
                    haveTrustedTT = true;
                } else {
                    candidate = 0;
                    haveTrustedTT = false;
                }
            }
        }

        // Move gen and ordering (buffer ply is clamped to available stack depth)
        final int bufPly = (ply < ctx.moveBuf.length) ? ply : (ctx.moveBuf.length - 1);
        final int[] moves = ctx.moveBuf[bufPly];
        int moveCount = game.getLegalMoves(moves);

        if (moveCount > moves.length) moveCount = moves.length;

        int capCount = MoveOrdering.partitionAndScoreCaptures(game, moves, moveCount, ctx.scoreBuf[bufPly]);

        // ProbCut: try a handful of strong captures at reduced depth to prove a fail-high quickly
        final boolean highDanger = KingSafety.quickDanger(game);

        if (ctx.cfg.useProbCut
                && !isPV
                && !game.inCheck()
                && ! (ctx.cfg.dangerGatesProbCut && highDanger)     // skip ProbCut in danger
                && depth >= ctx.cfg.probCutMinDepth
                && Math.abs(beta) < (max.chess.engine.search.evaluator.GameValues.CHECKMATE_VALUE - 256)) { // mate guard

            ctx.probCutTried++;
            final int limit = Math.min(capCount, Math.max(0, ctx.cfg.probCutMaxMoves));

            for (int i = 0; i < limit; i++) {
                final int m = moves[i];

                // Require a decent victim and (optionally) non-losing SEE
                int victimVal;
                {
                    final int to = max.chess.engine.movegen.Move.getEndPosition(m);
                    final boolean ep = max.chess.engine.movegen.Move.isEnPassant(m);
                    int victimType = ep ? max.chess.engine.utils.PieceUtils.PAWN
                            : game.board().getPieceTypeAt(to);
                    victimVal = max.chess.engine.search.evaluator.PieceValues.pieceTypeToValue(victimType);
                }
                if (victimVal < ctx.cfg.probCutVictimMin) continue;
                if (ctx.cfg.probCutRequireSEEPositive && MoveOrdering.seeSwap(game, m) < 0) continue;

                // Allow checking captures for ProbCut unless king-danger is flagged.
//                if (highDanger && MoveOrdering.givesCheckFast(game, m)) continue;
                if (MoveOrdering.givesCheckFast(game, m)) continue;

                int margin = ctx.cfg.probCutMargin + (victimVal >>> 2); // scale a little with victim
                final int threshold = beta + margin;

                int pcDepth = depth - 1 - ctx.cfg.probCutReduction;
                if (pcDepth > 0) {
                    // Reduced-depth, null-window search around +threshold for us:
                    // child window is [-threshold, -threshold+1]; a child fail-low implies parent >= threshold
                    long u = game.playMove(m);
                    if (ctx.cfg.debug) {
                        DebugChecks.assertMoveDidNotLeaveOwnKingInCheck(game);
                    }
                    int s = -search(game, ctx, pcDepth, ply + 1,
                            -threshold, -threshold + 1, stop, start, budgetNs, false, /*isPV*/false);
                    game.undoMove(u);
                    if (s >= threshold) {
                        ctx.probCutCut++;
                        if (ctx.cfg.debug) {
                            long u2 = game.playMove(m);
                            boolean ok = !game.inCheck();
                            game.undoMove(u2);
                            if (!ok)
                                throw new IllegalStateException("ProbCut cut on illegal move: " + Move.fromBytes(m));
                        }
                        return beta; // treat as fail-high
                    }
                }
            }
        }

        // Previous move leading to THIS node
        final int prev = (ply < ctx.prevMove.length) ? ctx.prevMove[ply] : 0;

        MoveOrdering.scoreAndSortQuiets(game, moves, capCount, moveCount, ctx.scoreBuf[bufPly],
                ctx.killer, ctx.history, ply, ctx, prev);

        // Root-only anti-repetition ordering tweak
        if (ctx.cfg.rootAntiRepOrdering && ply == 0) {
            final int evalHere = standPat; // you computed it already
            final boolean winningSideWantsPlay =
                    ctx.cfg.useContempt && ctx.cfg.dynamicContempt
                            ? (evalHere > ctx.cfg.contemptEvalMargin)   // only demote when clearly better
                            : (ColorUtils.isWhite(game.currentPlayer) == ctx.rootIsWhite);

            if (winningSideWantsPlay) {
                MoveOrdering.demoteImmediateBounceAtRoot(game, moves, capCount, moveCount, prev);
            }
        }

        if (moveCount == 0) {
            ctx.pvLen[ply] = 0;
            return game.inCheck() ? -(max.chess.engine.search.evaluator.GameValues.CHECKMATE_VALUE - ply) : 0;
        }

        // Hoist TT candidate only if it passed the trust check
        if (haveTrustedTT && candidate != 0) {
            trustedTTMove = candidate;
            MoveOrdering.hoistTrustedToSegment(game, trustedTTMove, moves, moveCount, capCount);
        } else {
            trustedTTMove = 0;
        }

        int bestScore = -SearchConstants.INF;
        int bestMove = 0;

        boolean repLineUsed = false;

        for (int i = 0; i < moveCount; i++) {
            int mv = moves[i];

            // Singular verification: exclude only at this ply
            if (ply < ctx.svExcludeAtPly.length && ctx.svExcludeAtPly[ply] == mv) {
                continue;
            }

            // Futility pruning of quiets at shallow depth (non-PV, not in check, not checking move)
            // Only prune LATE quiet moves so you don't decapitate the good ones.
            if (!isPV && !game.inCheck()) {
                boolean isQuiet = MoveOrdering.isQuiet(game, mv);
                if (isQuiet && !MoveOrdering.givesCheckFast(game, mv)) {
                    int mateGuard = max.chess.engine.search.evaluator.GameValues.CHECKMATE_VALUE - 200;
                    if (Math.abs(alpha) < mateGuard && Math.abs(beta) < mateGuard) {

                        // In king-danger, be conservative: skip futility pruning entirely
                        if (!(ctx.cfg.tightenFutilityOnDanger && highDanger)) {
                            // Late-move gate: don't prune the first few quiets
                            boolean lateQuiet = (i >= capCount + 3);

                            if (lateQuiet && ctx.cfg.useFutility && depth == 1) {
                                if (standPat + ctx.cfg.futilityMargin1 <= alpha) {
                                    continue;
                                }
                            } else if (lateQuiet && ctx.cfg.useExtendedFutility && depth == 2) {
                                int margin = ctx.cfg.futilityMargin2;
                                if (standPat + margin <= alpha) {
                                    continue;
                                }
                            }
                        }
                    }
                }
            }

            int side    = ColorUtils.isWhite(game.currentPlayer) ? 0 : 1;
            int from    = Move.getStartPosition(mv);
            int to      = Move.getEndPosition(mv);
            int hist    = ctx.history[side][from][to];
            boolean isCapture = !MoveOrdering.isQuiet(game, mv);
            final boolean isQuiet     = !isCapture;

            // LMP/HP v2: conservative pruning of late quiets only in the safest situations.
            if (!isPV && !game.inCheck() && isQuiet) {
                // Never prune structural/king-safety moves
                boolean isCastle = Move.isCastleKingSide(mv) || Move.isCastleQueenSide(mv);
                // Pawn pushes can be very important; don't prune them
                final var b = game.board();
                boolean isPawnPush = (((b.pawnBB) & (ColorUtils.isWhite(game.currentPlayer) ? b.whiteBB : b.blackBB))
                    & (1L << from)) != 0L;
                boolean givesCheck = MoveOrdering.givesCheckFast(game, mv);

                if (!givesCheck && !isCastle && !isPawnPush) {
                    int quietIndex = Math.max(0, i - capCount);

                    // Additional safety: only when static eval is clearly below alpha (like soft futility)
                    // Use a small guard to avoid pruning when position is borderline.
                    boolean staticBad = (standPat + 50) <= alpha;

                    // LMP: depth <= 2, very late quiets only, and no king-danger
                    if (ctx.cfg.useLMP && depth <= Math.min(ctx.cfg.lmpMaxDepth, 2)
                        && staticBad && !(ctx.cfg.lmpBlockOnDanger && highDanger)) {
                        int threshold = Math.max(0, ctx.cfg.lmpBaseQuiets + depth * ctx.cfg.lmpScale);
                        // Push threshold further back so we don't starve the search
                        threshold += 2; // extra cushion
                        if (quietIndex >= threshold) {
                            continue;
                        }
                    }

                    // History pruning: off by default; if enabled, require both poor main history AND poor continuation
                    if (ctx.cfg.useHistoryPruning && depth <= Math.min(ctx.cfg.histPruneMaxDepth, 2)
                        && staticBad && !(ctx.cfg.histPruneBlockOnDanger && highDanger)) {
                        int prevTo = (prev == 0) ? 0 : (Move.getEndPosition(prev) & 63);
                        int cont = ctx.contHistory[side][prevTo][to];
                        // killers/TT immunity
                        int kp = (ply < ctx.killer.length) ? ply : (ctx.killer.length - 1);
                        boolean isKiller = (mv == ctx.killer[kp][0]) || (mv == ctx.killer[kp][1]);
                        boolean isTT = (ctx.tt != null && hit != null && hit.move == mv);

                        if (!isKiller && !isTT) {
                            // Start very late and require BOTH histories to be non-positive
                            int startAfter = Math.max(ctx.cfg.histPruneAfter, 8);
                            if (quietIndex >= startAfter && hist <= 0 && cont <= 0) {
                                continue;
                            }
                        }
                    }
                }
            }

            boolean givesCheck = MoveOrdering.givesCheckFast(game, mv);

            // Precompute optional singular extension for the PV TT move
            int seExt = 0;
            if (ctx.cfg.useSingularExtension
                && isPV
                && !game.inCheck()
                && depth >= ctx.cfg.seMinDepth
                && mv == trustedTTMove
                && hit != null
                && hit.move == mv
                && hit.flag == TranspositionTable.TT_EXACT
                && hit.depth >= depth - 1
                && hit.score > alpha && hit.score < beta       // PV window at this node
                && moveCount >= 2
                && !highDanger) {                              // reuse existing danger gate

                // Only extend quiet, non-check TT move
                final boolean ttIsQuiet = MoveOrdering.isQuiet(game, mv);
                final boolean ttGivesCheck = givesCheck;       // computed just above
                if (ttIsQuiet && !ttGivesCheck) {
                    final int mateGuard = max.chess.engine.search.evaluator.GameValues.CHECKMATE_VALUE - 256;
                    if (Math.abs(hit.score) < mateGuard) {
                        final int margin   = Math.max(64, depth * ctx.cfg.seMarginPerDepth); // FIXED: comma, not "..."
                        final int singBeta = hit.score - margin;
                        final int verDepth = depth - 1 - ctx.cfg.seVerifyReduction;

                        if (verDepth >= 1) {
                            ctx.seTried++;
                            // Exclude TT move only at this ply for the verification search
                            final int saved = (ply < ctx.svExcludeAtPly.length) ? ctx.svExcludeAtPly[ply] : 0;
                            if (ply < ctx.svExcludeAtPly.length) ctx.svExcludeAtPly[ply] = mv;
                            int verify = search(game, ctx, verDepth, ply, singBeta - 1, singBeta,
                                stop, start, budgetNs, inNullMove, /*isPV*/false);
                            if (ply < ctx.svExcludeAtPly.length) ctx.svExcludeAtPly[ply] = saved;

                            if (verify < singBeta) {
                                seExt = 1;
                                ctx.seExtended++;
                            }
                        }
                    }
                }
            }


            // Segment-aware move index (quiets start at 0 after captures)
            int R = 0;
            if (ctx.cfg.useLMR) {
                // Immediate bounce-back? (A→B last ply, now B→A) — only consider for quiets
                boolean immediateBounceBack = false;
                if (isQuiet && prev != 0) {
                    final int pf = Move.getStartPosition(prev) & 63;
                    final int pt = Move.getEndPosition(prev) & 63;
                    if (from == pt && to == pf && Move.getPromotion(mv) == max.chess.engine.utils.PieceUtils.NONE) {
                        immediateBounceBack = true;
                    }
                }

                final boolean isRecapture = isCapture && prev != 0 &&
                        (Move.getEndPosition(prev) & 63) == (Move.getEndPosition(mv) & 63);

                R = LMR.suggestReduction(
                        depth,
                        /*globalIndex*/ i,
                        /*capCount   */ capCount,
                        /*isPV       */ isPV,
                        /*inCheck    */ game.inCheck(),
                        /*inNullMove */ inNullMove,
                        /*isCapture  */ isCapture,
                        /*givesCheck */ givesCheck,
                        /*isKiller   */ (mv == ctx.killer[ply][0] || mv == ctx.killer[ply][1]),
                        /*isTTTrusted*/ (mv == trustedTTMove),
                        /*history    */ hist,
                        immediateBounceBack,
                        isRecapture,
                        ctx.cfg
                );
            }


            if (ctx.cfg.useLMR) ctx.lmrTried++;
            if (R > 0) ctx.lmrReduced++;

            long undo = game.playMove(mv);

            if (ctx.cfg.debug) {
                DebugChecks.assertMoveDidNotLeaveOwnKingInCheck(game);
            }

            // Inform child node about the previous move that led to it
            if (ply + 1 < ctx.prevMove.length) ctx.prevMove[ply + 1] = mv;

            int score;

            // --- Early repetition detection on the line (third -> exact 0, second -> draw with contempt) ---
            if (ply + 1 < ctx.keyStack.length) {
                final long childKey = game.zobristKey();
                int matches = 0;

                // Scan same-side-to-move positions only: p = ply-1, ply-3, ...
                for (int p = ply - 1, scanned = 0;
                     p >= 0 && scanned < ctx.cfg.repScanMaxPlies;
                     p -= 2, scanned++) {
                    if (ctx.keyStack[p] == childKey) {
                        matches++;
                        if (matches >= 2) break;
                    }
                }

                // --- TRUE threefold on the line ---
                if (matches >= 2) {
                    repLineUsed = true;
                    score = 0;       // exact draw on the line
                    game.undoMove(undo);

                    if (score > bestScore) {
                        bestScore = score;
                        bestMove  = mv;
                        if (ply < ctx.pv.length) {
                            ctx.pv[ply][0] = mv;
                            ctx.pvLen[ply] = 1;           // <-- 1-move PV only
                        }
                    }
                    if (bestScore > alpha) alpha = bestScore;
                    if (alpha >= beta) {
                        cutOccurred = true;
                        if (MoveOrdering.isQuiet(game, mv)) {
                            Heuristics.onBetaCutoffUpdateHeuristics(ctx, game, mv, depth, ply, prev);
                        }
                        break;
                    }
                    continue;
                }

                // --- Twofold on the line => draw with contempt (soft steer) ---
                // Only steer at the root or at PV nodes and only when there's real depth to spare.
                if (matches == 1
                        && ctx.cfg.useEarlyRepetitionDraw
                        && ply == 0) {             // root-only steering
//                        && (ply == 0 || (isPV && depth >= ctx.cfg.erdMinDepth))) {
                    repLineUsed = true;
                    int evalHere = standPat; // reuse standPat for consistency and speed
                    int mag   = Math.abs(evalHere);
                    int base  = ctx.cfg.contemptCp;
                    int scaled = (mag <= 60 ? 0 : (mag <= 120 ? (base >> 1) : base));
                    int sign;
                    if (ctx.cfg.dynamicContempt) {
                        sign = (evalHere >  60) ? -1 : (evalHere < -60 ? +1 : 0);
                    } else {
                        boolean sameSide = ColorUtils.isWhite(game.currentPlayer) == ctx.rootIsWhite;
                        sign = sameSide ? -1 : +1;
                    }
                    score = sign * scaled;

                    game.undoMove(undo);

                    if (score > bestScore) {
                        bestScore = score;
                        bestMove  = mv;
                        if (ply < ctx.pv.length) {
                            ctx.pv[ply][0] = mv;
                            ctx.pvLen[ply] = 1;   // don't copy a child PV you never searched
                        }
                    }
                    if (bestScore > alpha) alpha = bestScore;
                    if (alpha >= beta) {
                        cutOccurred = true;
                        if (MoveOrdering.isQuiet(game, mv)) {
                            Heuristics.onBetaCutoffUpdateHeuristics(ctx, game, mv, depth, ply, prev);
                        }
                        break;
                    }
                    continue;
                }
            }
            // --- end repetition handling ---

            if (i == 0) {
                // PV move: allow +1 ply singular extension when verified
                int childDepth = depth - 1 + seExt;
                score = -search(game, ctx, childDepth, ply + 1, -beta, -alpha, stop, start, budgetNs, false, /*isPV=*/isPV);
            } else {
                if (R > 0) {
                    // Reduced null-window search (no SE on non-PV path)
                    ctx.lmrResearched++;
                    score = -search(game, ctx, depth - 1 - R, ply + 1, -alpha - 1, -alpha, stop, start, budgetNs, false, /*isPV=*/false);
                    if (score > alpha) {
                        // Re-search at full depth, still null-window
                        score = -search(game, ctx, depth - 1, ply + 1, -alpha - 1, -alpha, stop, start, budgetNs, false, /*isPV=*/false);
                        if (score > alpha && score < beta) {
                            // Only then widen window
                            ctx.lmrWidened++;
                            score = -search(game, ctx, depth - 1, ply + 1, -beta, -alpha, stop, start, budgetNs, false, /*isPV=*/isPV);
                        }
                    }
                } else {
                    // No LMR: standard PVS sequence (no SE)
                    score = -search(game, ctx, depth - 1, ply + 1, -alpha - 1, -alpha, stop, start, budgetNs, false, /*isPV=*/false);
                    if (score > alpha && score < beta) {
                        score = -search(game, ctx, depth - 1, ply + 1, -beta, -alpha, stop, start, budgetNs, false, /*isPV=*/isPV);
                    }
                }
            }

            game.undoMove(undo);

            if (score == Integer.MIN_VALUE) return Integer.MIN_VALUE;

            if (score > bestScore) {
                bestScore = score;
                bestMove = mv;
                if (ply < ctx.pv.length) {
                    ctx.pv[ply][0] = mv;
                    int childLen = (ply + 1 < ctx.pvLen.length) ? ctx.pvLen[ply + 1] : 0;
                    int maxCopy = Math.min(childLen, ctx.pv[ply].length - 1);
                    ctx.pvLen[ply] = 1 + maxCopy;
                    if (maxCopy > 0 && (ply + 1) < ctx.pv.length) {
                        System.arraycopy(ctx.pv[ply + 1], 0, ctx.pv[ply], 1, maxCopy);
                    }
                }
            }
            if (bestScore > alpha) alpha = bestScore;
            if (alpha >= beta) {
                cutOccurred = true;
                boolean quiet = MoveOrdering.isQuiet(game, mv);
                if (quiet) {
                    Heuristics.onBetaCutoffUpdateHeuristics(ctx, game, mv, depth, ply, prev);
                }
                break;
            }
        }

        // TT store (unchanged)
        if (bestMove != 0 && ctx.tt != null && depth >= 1) {
            byte flag;
            int storeScore;
            if (cutOccurred) { flag = max.chess.engine.search.transpositiontable.TranspositionTable.TT_LOWER; storeScore = ctx.cfg.storeTighterBounds ? bestScore : beta; }
            else if (bestScore <= alphaOrig) { flag = max.chess.engine.search.transpositiontable.TranspositionTable.TT_UPPER; storeScore = ctx.cfg.storeTighterBounds ? bestScore : alphaOrig; }
            else { flag = max.chess.engine.search.transpositiontable.TranspositionTable.TT_EXACT; storeScore = bestScore; }

            // Repetition on the line is path-dependent: never store EXACT for it.
            if (repLineUsed && flag == TranspositionTable.TT_EXACT) {
                // degrade to a safe bound (pick the side that doesn't prune winning lines)
                if (bestScore <= alphaOrig) { flag = TranspositionTable.TT_UPPER; storeScore = alphaOrig; }
                else                        { flag = TranspositionTable.TT_LOWER; storeScore = beta; }
            }

            boolean exactOnly = ctx.cfg.storeExactOnlyAtShallow && depth <= 2;
            if (!exactOnly || flag == max.chess.engine.search.transpositiontable.TranspositionTable.TT_EXACT) {
                ctx.tt.store(key, bestMove, depth, storeScore, flag, ply);
            }
        }

        return bestScore;
    }

    // Very cheap zugzwang-ish detector: side to move has no pawns and <= 2 minor pieces.
    private static boolean isZugzwangish(Game g) {
        final var b = g.board();
        final boolean white = max.chess.engine.utils.ColorUtils.isWhite(g.currentPlayer);
        long side = white ? b.whiteBB : b.blackBB;
        long pawns = b.pawnBB & side;
        if (pawns != 0L) return false;
        long minors = (b.knightBB | b.bishopBB) & side;
        long heavies = (b.rookBB | b.queenBB) & side;
        if (heavies != 0L) return false;
        return Long.bitCount(minors) <= 2;
    }
}
