package max.chess.engine.search;

import max.chess.engine.game.Game;
import max.chess.engine.game.board.Board;
import max.chess.engine.movegen.Move;
import max.chess.engine.movegen.pieces.Bishop;
import max.chess.engine.movegen.pieces.King;
import max.chess.engine.movegen.pieces.Knight;
import max.chess.engine.movegen.pieces.Rook;
import max.chess.engine.movegen.utils.OrthogonalMoveUtils;
import max.chess.engine.search.evaluator.PieceValues;
import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;
import max.chess.engine.utils.PieceUtils;

/**
 * All move-ordering and fast tactical helpers live here.
 * Everything is static, pure, and allocation-free.
 */
final class MoveOrdering {

    private MoveOrdering() {}

    /* =========================
     * Public entry points
     * ========================= */

    /**
     * Partition captures/promotions to the front of {@code moves} and score them by MVV-LVA.
     * Returns the number of tactical moves now at the front.
     */
    static int partitionAndScoreCaptures(Game g, int[] moves, int n, int[] tmpScores) {
        final boolean stmWhite = ColorUtils.isWhite(g.currentPlayer);
        final long enemyOcc = stmWhite ? g.board().blackBB : g.board().whiteBB;

        int k = 0;
        for (int i = 0; i < n; i++) {
            final int m = moves[i];
            final int to = Move.getEndPosition(m);
            final boolean isCap = Move.isEnPassant(m) || (((enemyOcc >>> to) & 1L) != 0);
            if (isCap || Move.getPromotion(m) != PieceUtils.NONE) {
                // swap to front
                final int t = moves[k]; moves[k] = m; moves[i] = t;
                tmpScores[k] = scoreCaptureMVVLVA(g, moves[k]);
                k++;
            }
        }

        // insertion sort captures by score desc on range [0..k)
        for (int i = 1; i < k; i++) {
            int m = moves[i], s = tmpScores[i], j = i - 1;
            while (j >= 0 && tmpScores[j] < s) {
                moves[j + 1] = moves[j];
                tmpScores[j + 1] = tmpScores[j];
                j--;
            }
            moves[j + 1] = m;
            tmpScores[j + 1] = s;
        }
        return k;
    }

    /**
     * Score quiets by history/killers and insertion-sort the quiet range [quietStart..n).
     */
    static void scoreAndSortQuiets(Game g, int[] moves, int quietStart, int n, int[] scores,
                                   int[][] killer, int[][][] history, int ply,
                                   SearchContext ctx, int prevMove) {
        for (int i = quietStart; i < n; i++) {
            final int m = moves[i];
            scores[i] = scoreQuietWithCMH(g, m, ply, killer, history, ctx, prevMove);
        }
        // insertion sort on [quietStart..n)
        for (int i = quietStart + 1; i < n; i++) {
            int m = moves[i], s = scores[i], j = i - 1;
            while (j >= quietStart && scores[j] < s) {
                moves[j + 1] = moves[j];
                scores[j + 1] = scores[j];
                j--;
            }
            moves[j + 1] = m;
            scores[j + 1] = s;
        }
    }

    /** If ttMove is in the list, swap it to index 0. */
    static void moveTTToFront(int ttMove, int[] moves, int n) {
        if (ttMove == 0) return;
        for (int i = 0; i < n; i++) {
            if (moves[i] == ttMove) {
                if (i != 0) {
                    int t = moves[0]; moves[0] = moves[i]; moves[i] = t;
                }
                return;
            }
        }
    }

    /** True if not a capture and no promotion. */
    static boolean isQuiet(Game g, int move) {
        return !isTactical(g, move);
    }

    /** True if capture or promotion. */
    static boolean isTactical(Game g, int move) {
        final long opponentBB = ColorUtils.isWhite(g.currentPlayer) ? g.board().blackBB : g.board().whiteBB;
        return Move.getPromotion(move) != PieceUtils.NONE
                || Move.isEnPassant(move)
                || (BitUtils.getPositionIndexBitMask(Move.getEndPosition(move)) & opponentBB) != 0;
    }

    /** Upper bound for material swing of a capture+promotion (used for delta pruning). */
    static int captureUpperBound(Game g, int move) {
        final int to = Move.getEndPosition(move);
        final boolean stmIsWhite = ColorUtils.isWhite(g.currentPlayer);

        int capturedType;
        if (Move.isEnPassant(move)) {
            final int epVictimSq = stmIsWhite ? to - 8 : to + 8;
            capturedType = g.board().getPieceTypeAt(epVictimSq);
        } else {
            capturedType = g.board().getPieceTypeAt(to);
        }
        int ub = PieceValues.pieceTypeToValue(capturedType);

        final byte prom = Move.getPromotion(move);
        if (prom != PieceUtils.NONE) {
            ub += PieceValues.pieceTypeToValue(prom) - PieceValues.PAWN_VALUE;
        }
        return ub;
    }

    /** SEE swap evaluation on the target square. Positive means winning capture sequence. */
    static int seeSwap(Game g, int move) {
        final Board b = g.board();
        final boolean whiteSTM = ColorUtils.isWhite(g.currentPlayer);
        final int from = Move.getStartPosition(move);
        final int to   = Move.getEndPosition(move);
        final byte promo = Move.getPromotion(move);

        final int[] VAL = {
                0,
                PieceValues.PAWN_VALUE,
                PieceValues.KNIGHT_VALUE,
                PieceValues.BISHOP_VALUE,
                PieceValues.ROOK_VALUE,
                PieceValues.QUEEN_VALUE,
                PieceValues.KING_VALUE
        };

        long WP = b.pawnBB   & b.whiteBB, BP = b.pawnBB   & b.blackBB;
        long WN = b.knightBB & b.whiteBB, BN = b.knightBB & b.blackBB;
        long WB = b.bishopBB & b.whiteBB, BB = b.bishopBB & b.blackBB;
        long WR = b.rookBB   & b.whiteBB, BR = b.rookBB   & b.blackBB;
        long WQ = b.queenBB  & b.whiteBB, BQ = b.queenBB  & b.blackBB;
        long WK = b.kingBB   & b.whiteBB, BK = b.kingBB   & b.blackBB;

        long occ = b.gameBB;

        // Remove victim from sets immediately
        int capturedType;
        if (Move.isEnPassant(move)) {
            int epVictimSq = whiteSTM ? (to - 8) : (to + 8);
            capturedType = PieceUtils.PAWN;
            if (whiteSTM) BP &= ~(1L << epVictimSq); else WP &= ~(1L << epVictimSq);
            occ &= ~(1L << epVictimSq);
        } else {
            capturedType = b.getPieceTypeAt(to);
            if (capturedType != PieceUtils.NONE) {
                long bitTo = 1L << to;
                if (whiteSTM) {
                    switch (capturedType) {
                        case PieceUtils.PAWN  -> BP &= ~bitTo;
                        case PieceUtils.KNIGHT-> BN &= ~bitTo;
                        case PieceUtils.BISHOP-> BB &= ~bitTo;
                        case PieceUtils.ROOK  -> BR &= ~bitTo;
                        case PieceUtils.QUEEN -> BQ &= ~bitTo;
                        case PieceUtils.KING  -> BK &= ~bitTo;
                    }
                } else {
                    switch (capturedType) {
                        case PieceUtils.PAWN  -> WP &= ~bitTo;
                        case PieceUtils.KNIGHT-> WN &= ~bitTo;
                        case PieceUtils.BISHOP-> WB &= ~bitTo;
                        case PieceUtils.ROOK  -> WR &= ~bitTo;
                        case PieceUtils.QUEEN -> WQ &= ~bitTo;
                        case PieceUtils.KING  -> WK &= ~bitTo;
                    }
                }
            }
        }

        // Remove attacker from 'from'
        {
            long bitFrom = 1L << from;
            int attType = b.getPieceTypeAt(from);
            if (whiteSTM) {
                switch (attType) {
                    case PieceUtils.PAWN  -> WP &= ~bitFrom;
                    case PieceUtils.KNIGHT-> WN &= ~bitFrom;
                    case PieceUtils.BISHOP-> WB &= ~bitFrom;
                    case PieceUtils.ROOK  -> WR &= ~bitFrom;
                    case PieceUtils.QUEEN -> WQ &= ~bitFrom;
                    case PieceUtils.KING  -> WK &= ~bitFrom;
                }
            } else {
                switch (attType) {
                    case PieceUtils.PAWN  -> BP &= ~bitFrom;
                    case PieceUtils.KNIGHT-> BN &= ~bitFrom;
                    case PieceUtils.BISHOP-> BB &= ~bitFrom;
                    case PieceUtils.ROOK  -> BR &= ~bitFrom;
                    case PieceUtils.QUEEN -> BQ &= ~bitFrom;
                    case PieceUtils.KING  -> BK &= ~bitFrom;
                }
            }
            occ &= ~bitFrom;
        }

        int promoDelta = (promo != PieceUtils.NONE) ? (VAL[promo] - VAL[PieceUtils.PAWN]) : 0;
        final int[] gain = new int[32];
        int d = 0;
        gain[0] = VAL[capturedType] + promoDelta;

        int curType  = (promo != PieceUtils.NONE) ? promo : b.getPieceTypeAt(from);
        boolean curWhite = whiteSTM;

        // Place mover on 'to'
        {
            long bitTo = 1L << to;
            if (curWhite) {
                switch (curType) {
                    case PieceUtils.PAWN  -> WP |= bitTo;
                    case PieceUtils.KNIGHT-> WN |= bitTo;
                    case PieceUtils.BISHOP-> WB |= bitTo;
                    case PieceUtils.ROOK  -> WR |= bitTo;
                    case PieceUtils.QUEEN -> WQ |= bitTo;
                    case PieceUtils.KING  -> WK |= bitTo;
                }
            } else {
                switch (curType) {
                    case PieceUtils.PAWN  -> BP |= bitTo;
                    case PieceUtils.KNIGHT-> BN |= bitTo;
                    case PieceUtils.BISHOP-> BB |= bitTo;
                    case PieceUtils.ROOK  -> BR |= bitTo;
                    case PieceUtils.QUEEN -> BQ |= bitTo;
                    case PieceUtils.KING  -> BK |= bitTo;
                }
            }
            occ |= bitTo;
        }

        boolean sideWhite = !whiteSTM;

        while (true) {
            long attP = pawnAttackerMaskTo(to, sideWhite) & (sideWhite ? WP : BP);
            long attN = Knight.getAttackBB(to) & (sideWhite ? WN : BN);
            long attB = Bishop.getAttackBB(to, occ) & (sideWhite ? WB : BB);
            long attR = Rook.getAttackBB(to, occ)   & (sideWhite ? WR : BR);
            long attQ = (attB | attR) & (sideWhite ? WQ : BQ);
            long attK = King.getAttackBB(to) & (sideWhite ? WK : BK);

            long attackers = attP | attN | attB | attR | attQ | attK;
            if (attackers == 0) break;

            int takeType; long fromBB;
            if      (attP != 0) { fromBB = attP & -attP; takeType = PieceUtils.PAWN; }
            else if (attN != 0) { fromBB = attN & -attN; takeType = PieceUtils.KNIGHT; }
            else if (attB != 0) { fromBB = attB & -attB; takeType = PieceUtils.BISHOP; }
            else if (attR != 0) { fromBB = attR & -attR; takeType = PieceUtils.ROOK; }
            else if (attQ != 0) { fromBB = attQ & -attQ; takeType = PieceUtils.QUEEN; }
            else                { fromBB = attK & -attK; takeType = PieceUtils.KING; }

            int fromSq = Long.numberOfTrailingZeros(fromBB);
            gain[++d] = VAL[takeType] - gain[d - 1];

            long bitFrom = 1L << fromSq, bitTo = 1L << to;

            // remove current occupant from 'to'
            if (curWhite) {
                switch (curType) {
                    case PieceUtils.PAWN  -> WP &= ~bitTo;
                    case PieceUtils.KNIGHT-> WN &= ~bitTo;
                    case PieceUtils.BISHOP-> WB &= ~bitTo;
                    case PieceUtils.ROOK  -> WR &= ~bitTo;
                    case PieceUtils.QUEEN -> WQ &= ~bitTo;
                    case PieceUtils.KING  -> WK &= ~bitTo;
                }
            } else {
                switch (curType) {
                    case PieceUtils.PAWN  -> BP &= ~bitTo;
                    case PieceUtils.KNIGHT-> BN &= ~bitTo;
                    case PieceUtils.BISHOP-> BB &= ~bitTo;
                    case PieceUtils.ROOK  -> BR &= ~bitTo;
                    case PieceUtils.QUEEN -> BQ &= ~bitTo;
                    case PieceUtils.KING  -> BK &= ~bitTo;
                }
            }

            // move new capturer onto 'to'
            if (sideWhite) {
                switch (takeType) {
                    case PieceUtils.PAWN  -> { WP &= ~bitFrom; WP |= bitTo; }
                    case PieceUtils.KNIGHT-> { WN &= ~bitFrom; WN |= bitTo; }
                    case PieceUtils.BISHOP-> { WB &= ~bitFrom; WB |= bitTo; }
                    case PieceUtils.ROOK  -> { WR &= ~bitFrom; WR |= bitTo; }
                    case PieceUtils.QUEEN -> { WQ &= ~bitFrom; WQ |= bitTo; }
                    case PieceUtils.KING  -> { WK &= ~bitFrom; WK |= bitTo; }
                }
            } else {
                switch (takeType) {
                    case PieceUtils.PAWN  -> { BP &= ~bitFrom; BP |= bitTo; }
                    case PieceUtils.KNIGHT-> { BN &= ~bitFrom; BN |= bitTo; }
                    case PieceUtils.BISHOP-> { BB &= ~bitFrom; BB |= bitTo; }
                    case PieceUtils.ROOK  -> { BR &= ~bitFrom; BR |= bitTo; }
                    case PieceUtils.QUEEN -> { BQ &= ~bitFrom; BQ |= bitTo; }
                    case PieceUtils.KING  -> { BK &= ~bitFrom; BK |= bitTo; }
                }
            }

            occ &= ~bitFrom;

            curType = takeType;
            curWhite = sideWhite;
            sideWhite = !sideWhite;
        }

        while (--d >= 0) gain[d] = -Math.max(-gain[d], gain[d + 1]);
        return gain[0];
    }

    /** Fast check detection after applying a move hypothetically. */
    static boolean givesCheckFast(Game g, int move) {
        final Board b = g.board();
        final boolean white = ColorUtils.isWhite(g.currentPlayer);
        final int from = Move.getStartPosition(move);
        final int to   = Move.getEndPosition(move);
        final int usPiece = b.getPieceTypeAt(from);
        final byte promo = Move.getPromotion(move);

        long oppKBB = b.kingBB & (white ? b.blackBB : b.whiteBB);
        if (oppKBB == 0) return false;
        final int oppKingSq = Long.numberOfTrailingZeros(oppKBB);

        long occ = b.gameBB;
        occ &= ~(1L << from);
        occ |=  (1L << to);
        if (Move.isEnPassant(move)) {
            int epVict = white ? (to - 8) : (to + 8);
            occ &= ~(1L << epVict);
        }

        int pt = (promo != PieceUtils.NONE) ? promo : usPiece;

        long att;
        switch (pt) {
            case PieceUtils.PAWN -> {
                long bb = 1L << to;
                att = white
                        ? ((bb << 7) & ~OrthogonalMoveUtils.FILES[7]) | ((bb << 9) & ~OrthogonalMoveUtils.FILES[0])
                        : ((bb >>> 7) & ~OrthogonalMoveUtils.FILES[0]) | ((bb >>> 9) & ~OrthogonalMoveUtils.FILES[7]);
            }
            case PieceUtils.KNIGHT -> att = Knight.getAttackBB(to);
            case PieceUtils.BISHOP -> att = Bishop.getAttackBB(to, occ);
            case PieceUtils.ROOK   -> att = Rook.getAttackBB(to, occ);
            case PieceUtils.QUEEN  -> att = Bishop.getAttackBB(to, occ) | Rook.getAttackBB(to, occ);
            case PieceUtils.KING   -> att = King.getAttackBB(to);
            default -> att = 0L;
        }
        return ((att >>> oppKingSq) & 1L) != 0;
    }

    /** Just the promotion type helper to keep call sites readable. */
    static int promotionType(int move) { return Move.getPromotion(move); }

    /**
     * If bestMove is illegal in the current node, try TT hint, else first legal.
     * Needs context for TT access and preallocated buffers.
     */
    static int sanitizeBestMove(Game g, int bestMove, SearchContext ctx) {
        final int[] buf = ctx.moveBuf[0];
        final int n = g.getLegalMoves(buf);
        if (n == 0) return 0;

        for (int i = 0; i < n; i++) if (buf[i] == bestMove) return bestMove;

        if (ctx.tt != null) {
            int ttMv = ctx.tt.peekMove(g.zobristKey());
            if (ttMv != 0) {
                for (int i = 0; i < n; i++) if (buf[i] == ttMv) return ttMv;
            }
        }
        return buf[0];
    }

    /* =========================
     * Private helpers
     * ========================= */

    /** MVV-LVA scoring with small promotion bias. */
    private static int scoreCaptureMVVLVA(Game g, int move) {
        final boolean stmWhite = ColorUtils.isWhite(g.currentPlayer);
        final int to = Move.getEndPosition(move);
        final int from = Move.getStartPosition(move);

        int victimPiece = g.board().getPieceTypeAt(to);
        int attacker    = g.board().getPieceTypeAt(from);

        if (Move.isEnPassant(move)) {
            int epVictimSq = stmWhite ? to - 8 : to + 8;
            victimPiece = g.board().getPieceTypeAt(epVictimSq);
        }

        int victim = PieceValues.pieceTypeToValue(victimPiece);
        int lva    = PieceValues.lvaRankOfPiece(attacker);

        int s = (victim << 8) - (lva << 4);

        if (Move.getPromotion(move) != PieceUtils.NONE) {
            int promo = Move.getPromotion(move);
            int pv = switch (promo) {
                case PieceUtils.QUEEN -> 800;
                case PieceUtils.ROOK -> 500;
                case PieceUtils.BISHOP -> 330;
                case PieceUtils.KNIGHT -> 320;
                default -> 200;
            };
            s += pv;
        }
        return s;
    }

    private static boolean isCapture(Game g, int m) {
        int to = Move.getEndPosition(m);
        if (Move.isEnPassant(m)) return true;
        long opp = ColorUtils.isWhite(g.currentPlayer) ? g.board().blackBB : g.board().whiteBB;
        return ((opp >>> to) & 1L) != 0;
    }

    // Mask of source squares of pawns that could attack 'sq' for the given color.
    private static long pawnAttackerMaskTo(int sq, boolean white) {
        long bb = 1L << sq;
        if (white) {
            long s1 = (bb >>> 7) & ~OrthogonalMoveUtils.FILES[0];
            long s2 = (bb >>> 9) & ~OrthogonalMoveUtils.FILES[7];
            return s1 | s2;
        } else {
            long s1 = (bb << 7) & ~OrthogonalMoveUtils.FILES[7];
            long s2 = (bb << 9) & ~OrthogonalMoveUtils.FILES[0];
            return s1 | s2;
        }
    }

    private static int scoreQuietWithCMH(Game g, int move, int ply, int[][] killer, int[][][] history,
                                         SearchContext ctx, int prevMove) {
        // Killer moves first
        final int kp = (ply < killer.length) ? ply : (killer.length - 1);
        if (move == killer[kp][0]) return 1_000_000;
        if (move == killer[kp][1]) return   900_000;

        // Base quiet history
        final int side = ColorUtils.isWhite(g.currentPlayer) ? 0 : 1;
        final int from = Move.getStartPosition(move) & 63;
        final int to   = Move.getEndPosition(move) & 63;

        int score = history[side][from][to];

        if (prevMove != 0) {
            final int pf = Move.getStartPosition(prevMove) & 63;
            final int pt = Move.getEndPosition(prevMove) & 63;

            // Countermove mapping: prev(from,to) -> best reply (a move id)
            final int cm = ctx.countermove[pf][pt];
            if (cm == move) {
                final int cmBonus = (ctx.cfg != null && ctx.cfg.counterMoveOrderingBonus > 0)
                        ? ctx.cfg.counterMoveOrderingBonus
                        : 850_000;
                score += cmBonus;
            }

            // Continuation history: side-aware, prevTo -> to
            score += ctx.contHistory[side][pt][to];

            // Immediate-repetition penalty: if this quiet just bounces back to the prev square,
            // it tends to force a 2-ply repetition cycle. Push it down the order.
            if (ctx.cfg != null && ctx.cfg.penalizeImmediateRepetition) {
                boolean undoes =
                        (from == pt) &&
                                (to   == pf) &&
                                Move.getPromotion(move) == PieceUtils.NONE &&
                                !isTactical(g, move);
                if (undoes) {
                    int pen = (ctx.cfg.repetitionPenaltyOrdering > 0)
                            ? ctx.cfg.repetitionPenaltyOrdering
                            : 600_000;
                    score -= pen;
                }
            }
        }

        return score;
    }

    // Put this anywhere in MoveOrdering (public static area)
    static void hoistTrustedToSegment(Game g, int candidate, int[] moves, int moveCount, int capCount) {
        if (candidate == 0 || moveCount <= 1) return;

        int idx = -1;
        for (int i = 0; i < moveCount; i++) {
            if (moves[i] == candidate) { idx = i; break; }
        }
        if (idx < 0) return;

        boolean tactical = isTactical(g, candidate);
        int segmentStart = tactical ? 0 : capCount;       // captures [0..capCount)
        if (segmentStart >= moveCount) segmentStart = 0;   // edge case: no quiets, drop at 0

        if (idx != segmentStart) {
            int tmp = moves[segmentStart];
            moves[segmentStart] = moves[idx];
            moves[idx] = tmp;
        }
    }

    /** Root-only: demote quiet immediate bounce-back (prev A→B, now B→A) to the end of quiets. */
    static void demoteImmediateBounceAtRoot(Game g, int[] moves, int capCount, int moveCount, int prevMove) {
        if (prevMove == 0 || moveCount <= capCount + 1) return;
        final int pf = Move.getStartPosition(prevMove) & 63;
        final int pt = Move.getEndPosition(prevMove) & 63;

        // find the first quiet bounce-back (B->A)
        int idx = -1;
        for (int i = capCount; i < moveCount; i++) {
            int m = moves[i];
            if (!isTactical(g, m)
                    && (Move.getStartPosition(m) & 63) == pt
                    && (Move.getEndPosition(m) & 63) == pf
                    && Move.getPromotion(m) == max.chess.engine.utils.PieceUtils.NONE) {
                idx = i; break;
            }
        }
        if (idx < 0) return;

        // stable-demote by swapping forward to the last quiet index
        int lastQuiet = moveCount - 1;
        if (lastQuiet < capCount) return;
        for (int j = idx; j < lastQuiet; j++) {
            int t = moves[j]; moves[j] = moves[j + 1]; moves[j + 1] = t;
        }
    }
}