package max.chess.engine.movegen;

import max.chess.engine.movegen.pieces.Bishop;
import max.chess.engine.movegen.pieces.King;
import max.chess.engine.movegen.pieces.Knight;
import max.chess.engine.movegen.pieces.Rook;
import max.chess.engine.movegen.utils.ObstructedLinesUtils;
import max.chess.engine.movegen.utils.BitBoardUtils;
import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;
import max.chess.engine.utils.PieceUtils;
import max.chess.engine.game.Game;

// ChatGPT has been used to generate most of the below code, hence why the code style differ
// TODO one day maybe rewrite it with better code style and to reuse some common methods of the repo
class EvasionMoveGenerator {
    private static final class PinInfo {
        long pinned;           // all pinned our pieces
        long[] lineMask = new long[64]; // for pinned sq, allowed line (between king and pinner + pinner square)
    }
    
    static int generateEvasionMoves(Game game, int[] buffer, int kingPosition, int side, byte requestedPieceType) {
        int oppositeColor = ColorUtils.switchColor(side);
        long checkersBB = MoveGenerator.getCheckersBB(kingPosition, game.board(), oppositeColor, false);
        long attackNoKingBB = MoveGenerator.doGetAttackBB(game.board(), oppositeColor, BitUtils.getPositionIndexBitMask(kingPosition));
        boolean doubleCheck = BitUtils.bitCount(checkersBB) > 1;

        boolean isWhiteTurn = ColorUtils.isWhite(side);
        final long usPieces = isWhiteTurn ? game.board().whiteBB : game.board().blackBB;
        final long themPieces = isWhiteTurn ? game.board().blackBB : game.board().whiteBB;

        if(requestedPieceType == PieceUtils.ALL || requestedPieceType == PieceUtils.KING) {
            // King is always candidate
            long kingMovesBB = King.getEvasionMovesBB(kingPosition, attackNoKingBB, usPieces);
            MoveGenerator.addMovesFromBitboard(PieceUtils.KING, kingPosition, kingMovesBB, false, false, game, buffer);
        }

        if(doubleCheck) {
            // Only king moves are possible
            return MoveGenerator.currentNumberOfMoves;
        }

        // 3) single check: capture checker or block the ray
        final int checkerSq = BitUtils.bitScanForward(checkersBB);
        final long singleCheckerBB = BitUtils.getPositionIndexBitMask(checkerSq);

        // Interpose mask (empty if knight/pawn/king checker)
        final long blockMask = ObstructedLinesUtils.OBSTRUCTED_BB[kingPosition][checkerSq];
        final long evasionTargets = blockMask | singleCheckerBB; // squares we can move to with non-king pieces

        final long themPawns = game.board().pawnBB & themPieces;
        final long themBishops = game.board().bishopBB & themPieces;
        final long themRooks = game.board().rookBB & themPieces;
        final long themKnights = game.board().knightBB & themPieces;
        final long themKing = game.board().kingBB & themPieces;
        final long themQueens = game.board().queenBB & themPieces;
        final long usPawns = game.board().pawnBB & usPieces;
        final long usKnights = game.board().knightBB & usPieces;
        final long usBishops = game.board().bishopBB & usPieces;
        final long usQueens = game.board().queenBB & usPieces;
        final long usRooks = game.board().rookBB & usPieces;

        if(requestedPieceType == PieceUtils.ALL || requestedPieceType == PieceUtils.PAWN) {
            // Handle en-passant evasion when checker is a pawn
            if (game.board().enPassantIndex >= 0 && ((themPawns & singleCheckerBB) != 0)) {
                genEpEvasionIfLegal(side, kingPosition, usPawns, themPawns, themKnights, themBishops | themQueens,
                        themRooks | themQueens, themKing, game.board().enPassantIndex, game.board().gameBB, buffer, game);
            }
        }

        // 4) generate piece moves onto evasionTargets, respecting pins
        final PinInfo pin = computePins(kingPosition, usPieces, themPieces, themBishops, themRooks, themQueens);

        if(requestedPieceType == PieceUtils.ALL || requestedPieceType == PieceUtils.KNIGHT) {
            // Knights (cannot move if pinned)
            long knights = usKnights & ~pin.pinned;
            while (knights != 0) {
                int from = BitUtils.bitScanForward(knights);
                knights &= knights - 1;
                long moves = Knight.getLegalMovesBB(from, usPawns) & evasionTargets;
                MoveGenerator.addMovesFromBitboard(PieceUtils.KNIGHT, from, moves, false, false, game, buffer);
            }
        }

        if(requestedPieceType == PieceUtils.ALL || requestedPieceType == PieceUtils.BISHOP) {
            // Bishops and Queens on diagonals
            long bishops = usBishops;
            while (bishops != 0) {
                int from = BitUtils.bitScanForward(bishops);
                bishops &= bishops - 1;
                long legalMask = pinMaskFor(from, pin); // either all-ones or a single ray line
                long moves = Bishop.getAttackBB(from, game.board().gameBB) & evasionTargets & legalMask & ~usPieces;
                MoveGenerator.addMovesFromBitboard(PieceUtils.BISHOP, from, moves, false, false, game, buffer);
            }
        }

        if(requestedPieceType == PieceUtils.ALL || requestedPieceType == PieceUtils.QUEEN) {
            long queenBishops = usQueens;
            while (queenBishops != 0) {
                int from = BitUtils.bitScanForward(queenBishops);
                queenBishops &= queenBishops - 1;
                long legalMask = pinMaskFor(from, pin); // either all-ones or a single ray line
                long moves = Bishop.getAttackBB(from, game.board().gameBB) & evasionTargets & legalMask & ~usPieces;
                MoveGenerator.addMovesFromBitboard(PieceUtils.QUEEN, from, moves, false, false, game, buffer);
            }
        }

        // Rooks and Queens on ranks/files
        if(requestedPieceType == PieceUtils.ALL || requestedPieceType == PieceUtils.ROOK) {
            long rooks = (usRooks);
            while (rooks != 0) {
                int from = BitUtils.bitScanForward(rooks);
                rooks &= rooks - 1;
                long legalMask = pinMaskFor(from, pin);
                long moves = Rook.getAttackBB(from, game.board().gameBB) & evasionTargets & legalMask & ~usPieces;
                MoveGenerator.addMovesFromBitboard(PieceUtils.ROOK, from, moves, false, false, game, buffer);
            }
        }

        if(requestedPieceType == PieceUtils.ALL || requestedPieceType == PieceUtils.QUEEN) {
            long queenRooks = (usQueens);
            while (queenRooks != 0) {
                int from = BitUtils.bitScanForward(queenRooks);
                queenRooks &= queenRooks - 1;
                long legalMask = pinMaskFor(from, pin);
                long moves = Rook.getAttackBB(from, game.board().gameBB) & evasionTargets & legalMask & ~usPieces;
                MoveGenerator.addMovesFromBitboard(PieceUtils.QUEEN, from, moves, false, false, game, buffer);
            }
        }

        if(requestedPieceType == PieceUtils.ALL || requestedPieceType == PieceUtils.PAWN) {
            // Pawns: captures to checker, pushes to block squares, promos included
            genPawnEvasions(side, usPawns, usPieces, themPieces, game.board().gameBB,
                    evasionTargets, pin, buffer, game);
        }

        return MoveGenerator.currentNumberOfMoves;
    }

    private static void genPawnEvasions(int side, long pawns, long us, long them, long occ,
                                        long evasionTargets, PinInfo pin, int[] out, Game game) {
        if (pawns == 0) return;

        if (ColorUtils.isWhite(side)) { // white
            // Captures
            long leftCaps  = BitBoardUtils.shift(pawns, BitBoardUtils.Direction.NORTHWEST) & them & evasionTargets;
            long rightCaps = BitBoardUtils.shift(pawns, BitBoardUtils.Direction.NORTHEAST) & them & evasionTargets;
            emitPawnCapsUp(leftCaps,  -7, us, pin, out);
            emitPawnCapsUp(rightCaps, -9, us, pin, out);

            // Pushes (single and double) to block squares only
            long empty = ~occ;
            long pushUpSingle = BitBoardUtils.shift(pawns, BitBoardUtils.Direction.NORTH) & empty;
            long single = pushUpSingle & evasionTargets;
            long dbl    = (BitBoardUtils.shift(pushUpSingle & 0x0000000000ff0000L, BitBoardUtils.Direction.NORTH) & empty) & evasionTargets; // from rank 2 to 4
            emitPawnPushesUp(single, us, pin, out, false);
            emitPawnPushesUp(dbl,    us, pin, out, true);
        } else { // black
            long leftCaps  = BitBoardUtils.shift(pawns, BitBoardUtils.Direction.SOUTHWEST) & them & evasionTargets;
            long rightCaps = BitBoardUtils.shift(pawns, BitBoardUtils.Direction.SOUTHEAST) & them & evasionTargets;
            emitPawnCapsDown(leftCaps,  +9, us, pin, out);
            emitPawnCapsDown(rightCaps, +7, us, pin, out);

            long empty = ~occ;
            long pushDownSingle = (BitBoardUtils.shift(pawns, BitBoardUtils.Direction.SOUTH)) & empty;
            long single = pushDownSingle & evasionTargets;
            long dbl    = ((BitBoardUtils.shift(pushDownSingle & 0x0000ff0000000000L, BitBoardUtils.Direction.SOUTH)) & empty) & evasionTargets; // from rank 7 to 5
            emitPawnPushesDown(single, us, pin, out, false);
            emitPawnPushesDown(dbl,    us, pin, out, true);
        }
    }

    private static void emitPawnCapsUp(long caps, int deltaFromTo, long us, PinInfo pin, int[] buffer) {
        while (caps != 0) {
            int to = BitUtils.bitScanForward(caps); caps &= caps - 1;
            int from = to + deltaFromTo;
            if (((us >>> to) & 1L) != 0) continue;
            long legal = pinMaskFor(from, pin);
            if (((legal >>> to) & 1L) == 0) continue;
            boolean promotedMove = (to >= 56);
            addPawnCapturesFromBitboard(from, BitUtils.getPositionIndexBitMask(to), promotedMove, buffer);
        }
    }

    private static void emitPawnCapsDown(long caps, int deltaFromTo, long us, PinInfo pin, int[] buffer) {
        while (caps != 0) {
            int to = BitUtils.bitScanForward(caps); caps &= caps - 1;
            int from = to + deltaFromTo;
            if (((us >>> to) & 1L) != 0) continue;
            long legal = pinMaskFor(from, pin);
            if (((legal >>> to) & 1L) == 0) continue;
            boolean promotedMove = (to <= 7);
            addPawnCapturesFromBitboard(from, BitUtils.getPositionIndexBitMask(to), promotedMove, buffer);
        }
    }

    private static void emitPawnPushesUp(long pushes, long us, PinInfo pin, int[] buffer, boolean doublePush) {
        while (pushes != 0) {
            int to = BitUtils.bitScanForward(pushes); pushes &= pushes - 1;
            int from = to - (doublePush?16:8);
            if (((us >>> to) & 1L) != 0) continue;
            long legal = pinMaskFor(from, pin);
            if (((legal >>> to) & 1L) == 0) continue;
            boolean promotedMove = (to >= 56);
            addPawnNonCapturesFromBitboard(from, BitUtils.getPositionIndexBitMask(to), promotedMove, buffer);
        }
    }

    private static void emitPawnPushesDown(long pushes, long us, PinInfo pin, int[] buffer, boolean doublePush) {
        while (pushes != 0) {
            int to = BitUtils.bitScanForward(pushes); pushes &= pushes - 1;
            int from = to + (doublePush?16:8);
            if (((us >>> to) & 1L) != 0) continue;
            long legal = pinMaskFor(from, pin);
            if (((legal >>> to) & 1L) == 0) continue;
            boolean promotedMove = (to <= 7);
            addPawnNonCapturesFromBitboard(from, BitUtils.getPositionIndexBitMask(to), promotedMove, buffer);
        }
    }

    private static long pinMaskFor(int from, PinInfo pin) {
        if (((pin.pinned >>> from) & 1L) == 0) return ~0L;
        return pin.lineMask[from];
    }

    private static PinInfo computePins(int kingSq, long us, long them,
                                                     long themBishops, long themRooks, long themQueens) {
        PinInfo p = new PinInfo();
        final long occ = us | them;

        // 8 directions around the king: rook-like and bishop-like
        final int[] dirDelta = {+8, -8, +1, -1, +9, +7, -7, -9};
        final boolean[] isDiag = {false,false,false,false, true, true, true, true};

        for (int d = 0; d < 8; d++) {
            int cur = kingSq;
            int blockedSq = -1;

            while (true) {
                int next = step(cur, dirDelta[d]);
                if (next < 0) break;

                long bit = 1L << next;
                if ((occ & bit) == 0) { cur = next; continue; }

                if ((us & bit) != 0) {
                    // first our piece on the ray
                    blockedSq = next;
                    break;
                } else {
                    // first hit is enemy piece -> no pin on this ray
                    break;
                }
            }

            if (blockedSq < 0) continue;

            // continue further to find a potential pinner
            int cur2 = blockedSq;
            while (true) {
                int next = step(cur2, dirDelta[d]);
                if (next < 0) break;
                long bit = 1L << next;
                if ((occ & bit) == 0) { cur2 = next; continue; }

                boolean diag = isDiag[d];
                boolean pinner = diag ? (((themBishops | themQueens) & bit) != 0)
                        : (((themRooks   | themQueens) & bit) != 0);
                if (pinner) {
                    p.pinned |= (1L << blockedSq);
                    // allowed line: squares between king and pinner, plus pinner square
                    p.lineMask[blockedSq] = ObstructedLinesUtils.OBSTRUCTED_BB[kingSq][next] | bit;
                }
                break;
            }
        }
        return p;
    }

    private static int step(int sq, int delta) {
        int r = sq >>> 3, f = sq & 7;
        int nr = r + (delta / 8), nf = f + (delta % 8);
        if (delta == +9 || delta == -7) { nf = f + 1; nr = r + (delta == +9 ? 1 : -1); }
        else if (delta == +7 || delta == -9) { nf = f - 1; nr = r + (delta == +7 ? 1 : -1); }
        else if (delta == +1 || delta == -1) { nr = r; nf = f + (delta == +1 ? 1 : -1); }
        else if (delta == +8 || delta == -8) { nf = f; nr = r + (delta == +8 ? 1 : -1); }
        if (nr < 0 || nr > 7 || nf < 0 || nf > 7) return -1;
        return (nr << 3) | nf;
    }

    /** Fully legal EP evasion without make/unmake. */
    private static void genEpEvasionIfLegal(
            int side, int kingSq,
            long usPawns,
            long themPawns, long themKnights, long themBishops, long themRooks, long themKingBB,
            int epSq, long occAll,
            int[] buffer, Game game
    ) {
        final long epBB = BitUtils.getPositionIndexBitMask(epSq);

        // Which of our pawns could capture to epSq?
        long fromSet;
        int capSq;
        if (ColorUtils.isWhite(side)) { // white moves up
            fromSet = (BitBoardUtils.shift(epBB, BitBoardUtils.Direction.SOUTHEAST) | BitBoardUtils.shift(epBB, BitBoardUtils.Direction.SOUTHWEST))  & usPawns;
            capSq = epSq - 8; // black pawn removed
        } else {          // black moves down
            fromSet = (BitBoardUtils.shift(epBB, BitBoardUtils.Direction.NORTHWEST) | BitBoardUtils.shift(epBB, BitBoardUtils.Direction.NORTHEAST))  & usPawns;
            capSq = epSq + 8; // white pawn removed
        }

        // EP destination must be a valid evasion target (already checked by caller),
        // but we still loop in case two pawns can take EP (rare).
        while (fromSet != 0) {
            int from = BitUtils.bitScanForward(fromSet);
            fromSet &= fromSet - 1;

            // Build hypothetical occupancy after EP: move our pawn, remove captured pawn, add on epSq
            long captureBB = BitUtils.getPositionIndexBitMask(capSq);
            long occNew = (occAll ^ BitUtils.getPositionIndexBitMask(from) ^ captureBB) | epBB;

            // Enemy pawns after capture
            long themPawnsNew = themPawns & ~captureBB;

            // Is our king attacked in this hypothetical position?
            boolean isKingAttacked = MoveGenerator.getCheckersBB(kingSq, themKingBB, themBishops, themRooks, themKnights, themPawnsNew, occNew, ColorUtils.switchColor(side), true) != 0;

            if (!isKingAttacked) {
                MoveGenerator.addEnPassantMove(from, game, buffer, false);
            }
        }
    }

    static void addPawnCapturesFromBitboard(final int startPosition, final long moveBitboard,
                                            final boolean promotedMove, int[] buffer) {
        addPawnMovesFromBitboard(startPosition, moveBitboard, promotedMove, buffer);
    }

    private static void addPawnNonCapturesFromBitboard(final int startPosition, final long moveBitboard,
                                                       final boolean promotedMove, int[] buffer) {
        addPawnMovesFromBitboard(startPosition, moveBitboard, promotedMove, buffer);
    }

    private static void addPawnMovesFromBitboard(int startPosition, long moveBitboard, boolean promotedMove, int[] buffer) {
        long moveBB = moveBitboard;
        if(buffer == null) {
            if(promotedMove) {
                MoveGenerator.currentNumberOfMoves += BitUtils.bitCount(moveBB)*4;
            } else {
                MoveGenerator.currentNumberOfMoves += BitUtils.bitCount(moveBB);
            }
            return;
        }
        while(moveBB != 0) {
            int endPosition = BitUtils.bitScanForward(moveBB);
            moveBB &= moveBB - 1;
            if (promotedMove) {
                buffer[MoveGenerator.currentNumberOfMoves++] = Move.asBytes(startPosition, endPosition, PieceUtils.PAWN, PieceUtils.KNIGHT);
                buffer[MoveGenerator.currentNumberOfMoves++] = Move.asBytes(startPosition, endPosition, PieceUtils.PAWN, PieceUtils.QUEEN);
                buffer[MoveGenerator.currentNumberOfMoves++] = Move.asBytes(startPosition, endPosition, PieceUtils.PAWN, PieceUtils.BISHOP);
                buffer[MoveGenerator.currentNumberOfMoves++] = Move.asBytes(startPosition, endPosition, PieceUtils.PAWN, PieceUtils.ROOK);
            } else {
                buffer[MoveGenerator.currentNumberOfMoves] = Move.asBytes(startPosition, endPosition, PieceUtils.PAWN);
                MoveGenerator.currentNumberOfMoves++;
            }
        }
    }
}
