package max.chess.engine.movegen;

import max.chess.engine.movegen.pieces.Bishop;
import max.chess.engine.movegen.pieces.King;
import max.chess.engine.movegen.pieces.Knight;
import max.chess.engine.movegen.pieces.Pawn;
import max.chess.engine.movegen.pieces.Rook;
import max.chess.engine.movegen.utils.ObstructedLinesUtils;
import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;
import max.chess.engine.utils.PieceUtils;
import max.chess.engine.game.board.DirtyBoard;
import max.chess.engine.game.board.Board;
import max.chess.engine.game.Game;
import max.chess.engine.movegen.utils.CheckUtils;
import max.chess.engine.movegen.utils.DiagonalMoveUtils;
import max.chess.engine.movegen.utils.OrthogonalMoveUtils;

import java.util.Arrays;

public class MoveGenerator {
    public static int EVASION_MOVES_GENERATORS = 0;
    public static int LEGAL_MOVES_GENERATORS = 0;

    public static void printGeneratorReport() {
        System.out.println("********************");
        System.out.println("MOVE GENERATOR REPORT");
        System.out.println("\tEVASION MOVE GENERATORS: "+ EVASION_MOVES_GENERATORS);
        System.out.println("\tLEGAL MOVE GENERATORS: "+ LEGAL_MOVES_GENERATORS);
        System.out.println("********************");
    }

    public static void clearGeneratorReport() {
        EVASION_MOVES_GENERATORS = 0;
        LEGAL_MOVES_GENERATORS = 0;
    }

    // According to literature, maximum number of legal moves in a given position is 218 ( TODO add source )
    private static final int[] moves = new int[218];

    // Keeping it static to prevent re-allocation at each movegen
    static int currentNumberOfMoves;

    private static boolean WARMED_UP = false;
    static {
        warmUp();
    }

    public static void warmUp() {
        if(WARMED_UP) {
            return;
        }
        Pawn.warmUp();
        Knight.warmUp();
        King.warmUp();
        Rook.warmUp();
        Bishop.warmUp();
        ObstructedLinesUtils.warmUp();
        WARMED_UP = true;
    }

    public static int[] generateMoves(Game game) {
        generateMoves(game, moves);
        // Shrinking the array to the appropriate size
        return Arrays.copyOf(moves, currentNumberOfMoves);
    }

    // Useful for perft leaf-node count and position evaluation
    public static int countMoves(Game game) {
        return countMoves(game, PieceUtils.ALL);
    }

    // Useful for perft leaf-node count and position evaluation
    public static int countMoves(Game game, byte requestedPieceType) {
        return countMoves(game, game.currentPlayer, requestedPieceType);
    }

    public static int countMoves(Game game, int color, byte requestedPieceType) {
        return generateMoves(game, null, color, requestedPieceType);
    }

    // Useful for position evaluation
    public static int countMovesOpponent(Game game) {
        return countMovesOpponent(game, PieceUtils.ALL);
    }

    public static int countMovesOpponent(Game game, byte requestedPieceType) {
        return countMoves(game, ColorUtils.switchColor(game.currentPlayer), requestedPieceType);
    }

    public static int generateMoves(Game game, int[] buffer) {
        return generateMoves(game, buffer, game.currentPlayer);
    }

    public static int generateMoves(Game game, int[] buffer, int side) {
        return generateMoves(game, buffer, side, PieceUtils.ALL);
    }

    public static int generateMoves(Game game, int[] buffer, int side, byte requestedPieceType) {
        currentNumberOfMoves = 0;
        final long friendlyKingBB;

        if(ColorUtils.isWhite(side)) {
            friendlyKingBB = game.board().kingBB & game.board().whiteBB;
        } else {
            friendlyKingBB = game.board().kingBB & game.board().blackBB;
        }

        final int kingPosition = BitUtils.bitScanForward(friendlyKingBB);
        final boolean isKingInCheck = getCheckersBB(kingPosition, game.board(), ColorUtils.switchColor(side), true) != 0;
        if(isKingInCheck) {
            EVASION_MOVES_GENERATORS++;
            // When in check, no need to check every move possible ; we can precisely generate only evasion moves
            // and save CPU time
            return EvasionMoveGenerator.generateEvasionMoves(game, buffer, kingPosition, side, requestedPieceType);
        } else {
            // We are not in check, we should compute the full set of legal moves
            LEGAL_MOVES_GENERATORS++;
            return generateLegalMoves(game, buffer, side, requestedPieceType);
        }
    }

    private static int generateLegalMoves(Game game, int[] buffer, int side, byte requestedPieceType) {
        long friendlyBishopBB;
        long friendlyRookBB;
        long friendlyQueenBB;
        long friendlyKnightBB;
        long friendlyKingBB;
        long friendlyPawnBB;

        final long friendlyOccupiedSquareBB;
        final long enemyOccupiedSquareBB;
        final long pinnedPiecesBB = MoveGenerator.getPinnedBB(game.board(), side);
        boolean isWhiteTurn = ColorUtils.isWhite(side);

        friendlyOccupiedSquareBB = isWhiteTurn ? game.board().whiteBB :  game.board().blackBB;
        enemyOccupiedSquareBB = isWhiteTurn ? game.board().blackBB :  game.board().whiteBB;
        friendlyBishopBB = (game.board().bishopBB | game.board().queenBB) & friendlyOccupiedSquareBB;
        friendlyRookBB = (game.board().rookBB | game.board().queenBB) & friendlyOccupiedSquareBB;
        friendlyKnightBB = game.board().knightBB & friendlyOccupiedSquareBB;
        friendlyKingBB = game.board().kingBB & friendlyOccupiedSquareBB;
        friendlyPawnBB = game.board().pawnBB & friendlyOccupiedSquareBB;
        friendlyQueenBB = game.board().queenBB & friendlyOccupiedSquareBB;

        final int kingPosition = BitUtils.bitScanForward(friendlyKingBB);

        final long occupiedSquareBB = game.board().gameBB;

        // King moves
        final boolean canCastleKingSide = isWhiteTurn ? game.whiteCanCastleKingSide : game.blackCanCastleKingSide;
        final boolean canCastleQueenSide = isWhiteTurn ? game.whiteCanCastleQueenSide : game.blackCanCastleQueenSide;

        if(requestedPieceType == PieceUtils.ALL || requestedPieceType == PieceUtils.KING) {
            final long kingMovesBB = King.getNonCastleLegalMovesBB(side, kingPosition, friendlyOccupiedSquareBB, game.board());
            addMovesFromBitboard(PieceUtils.KING, kingPosition, kingMovesBB, false, false, game, buffer);

            if (King.isCastleKingSideLegal(side, occupiedSquareBB, false, canCastleKingSide, game.board())) {
                addKingCastleMove(side, buffer);
            }

            if (King.isCastleQueenSideLegal(side, occupiedSquareBB, false, canCastleQueenSide, game.board())) {
                addQueenCastleMove(side, buffer);
            }
        }

        if(requestedPieceType == PieceUtils.ALL || requestedPieceType == PieceUtils.ROOK || requestedPieceType == PieceUtils.QUEEN) {
            // Rook moves
            while (friendlyRookBB != 0) {
                final int rookPosition = BitUtils.bitScanForward(friendlyRookBB);
                friendlyRookBB &= friendlyRookBB - 1;
                long pieceBB = BitUtils.getPositionIndexBitMask(rookPosition);
                final boolean isPinned = (pinnedPiecesBB & pieceBB) != 0;
                byte pieceType = (pieceBB & friendlyQueenBB) == 0 ? PieceUtils.ROOK : PieceUtils.QUEEN;
                if(requestedPieceType != PieceUtils.ALL && requestedPieceType != pieceType) {
                    continue;
                }
                final long rookMovesBB = Rook.getLegalMovesBB(rookPosition, friendlyOccupiedSquareBB, occupiedSquareBB);
                addMovesFromBitboard(pieceType, rookPosition, rookMovesBB, false, isPinned, game, buffer);
            }
        }


        if(requestedPieceType == PieceUtils.ALL || requestedPieceType == PieceUtils.BISHOP || requestedPieceType == PieceUtils.QUEEN) {
            // Bishop moves
            while (friendlyBishopBB != 0) {
                final int bishopPosition = BitUtils.bitScanForward(friendlyBishopBB);
                friendlyBishopBB &= friendlyBishopBB - 1;
                long pieceBB = BitUtils.getPositionIndexBitMask(bishopPosition);
                final boolean isPinned = (pinnedPiecesBB & pieceBB) != 0;
                byte pieceType = (pieceBB & friendlyQueenBB) == 0 ? PieceUtils.BISHOP : PieceUtils.QUEEN;
                if(requestedPieceType != PieceUtils.ALL && requestedPieceType != pieceType) {
                    continue;
                }

                final long bishopMovesBB = Bishop.getPseudoLegalMovesBB(bishopPosition, friendlyOccupiedSquareBB, occupiedSquareBB);
                addMovesFromBitboard(pieceType, bishopPosition, bishopMovesBB, false, isPinned, game, buffer);
            }
        }


        if(requestedPieceType == PieceUtils.ALL || requestedPieceType == PieceUtils.KNIGHT) {
            // Knight moves
            while (friendlyKnightBB != 0) {
                final int knightPosition = BitUtils.bitScanForward(friendlyKnightBB);
                friendlyKnightBB &= friendlyKnightBB - 1;
                final long knightMovesBB = Knight.getLegalMovesBB(knightPosition, friendlyOccupiedSquareBB);
                final boolean isPinned = (pinnedPiecesBB & BitUtils.getPositionIndexBitMask(knightPosition)) != 0;
                addMovesFromBitboard(PieceUtils.KNIGHT, knightPosition, knightMovesBB, false, isPinned, game, buffer);
            }
        }

        if(requestedPieceType == PieceUtils.ALL || requestedPieceType == PieceUtils.PAWN) {
            // Pawn moves
            boolean enPassantPossible = game.board().enPassantFile != -1;
            long enPassantBitMask = 0L;
            if (enPassantPossible) {
                enPassantBitMask = BitUtils.getPositionIndexBitMask(game.board().enPassantIndex);
            }

            while (friendlyPawnBB != 0) {
                final int pawnPosition = BitUtils.bitScanForward(friendlyPawnBB);
                friendlyPawnBB &= friendlyPawnBB - 1;
                final long pawnMovesBB = Pawn.getPseudoLegalMovesBB(pawnPosition, side, occupiedSquareBB, enemyOccupiedSquareBB);
                final boolean promotedMove = (isWhiteTurn && pawnPosition >= 48) || (ColorUtils.isBlack(side) && pawnPosition <= 15);
                final boolean isPinned = (pinnedPiecesBB & BitUtils.getPositionIndexBitMask(pawnPosition)) != 0;
                addMovesFromBitboard(PieceUtils.PAWN, pawnPosition, pawnMovesBB, promotedMove, isPinned, game, buffer);

                // en passant
                // TODO we could early exit if the pawn position is not one of the 2 candidates for en passant
                if (enPassantPossible) {
                    final long enPassantBB = Pawn.getAttackBB(pawnPosition, side) & enPassantBitMask;
                    if (enPassantBB != 0) {
                        addEnPassantMove(pawnPosition, game, buffer, true);
                    }
                }
            }
        }

        return currentNumberOfMoves;
    }

    static void addEnPassantMove(final int startPosition, final Game game, int[] buffer, boolean verifyChecks) {
        if(!verifyChecks || !CheckUtils.wouldKingBeInCheck(startPosition, game.board().enPassantIndex, game.currentPlayer, game)) {
            if (buffer == null) {
                MoveGenerator.currentNumberOfMoves++;
            } else {
                buffer[MoveGenerator.currentNumberOfMoves++] = Move.asBytesEnPassant(startPosition, game.board().enPassantIndex);
            }
        }
    }

    private static void addKingCastleMove(int color, int[] buffer) {
        if(buffer == null) {
            currentNumberOfMoves ++;
        } else {
            if(ColorUtils.isWhite(color)) {
                buffer[currentNumberOfMoves++] = Move.CASTLE_KING_SIDE_WHITE_MOVE;
            } else {
                buffer[currentNumberOfMoves++] = Move.CASTLE_KING_SIDE_BLACK_MOVE;
            }
        }
    }

    private static void addQueenCastleMove(int color, int[] buffer) {
        if(buffer == null) {
            currentNumberOfMoves ++;
        } else {
            if(ColorUtils.isWhite(color)) {
                buffer[currentNumberOfMoves++] = Move.CASTLE_QUEEN_SIDE_WHITE_MOVE;
            } else {
                buffer[currentNumberOfMoves++] = Move.CASTLE_QUEEN_SIDE_BLACK_MOVE;
            }
        }
    }

    static void addMovesFromBitboard(final byte pieceType, final int startPosition, final long moveBitboard,
                                     final boolean promotedMove, final boolean verifyChecks, final Game game,
                                     int[] buffer) {
        long moveBB = moveBitboard;
        if(buffer == null && !verifyChecks) {
            if(promotedMove) {
                currentNumberOfMoves += BitUtils.bitCount(moveBB)*4;
            } else {
                currentNumberOfMoves += BitUtils.bitCount(moveBB);
            }
            return;
        }
        while(moveBB != 0) {
            int endPosition = BitUtils.bitScanForward(moveBB);
            moveBB &= moveBB - 1;
            if(!verifyChecks || !CheckUtils.wouldKingBeInCheck(startPosition, endPosition, game.currentPlayer, game)) {
                if(promotedMove) {
                    if(buffer == null) {
                        currentNumberOfMoves += 4;
                    } else {
                        buffer[currentNumberOfMoves++] = Move.asBytes(startPosition, endPosition, pieceType, PieceUtils.KNIGHT);
                        buffer[currentNumberOfMoves++] = Move.asBytes(startPosition, endPosition, pieceType, PieceUtils.QUEEN);
                        buffer[currentNumberOfMoves++] = Move.asBytes(startPosition, endPosition, pieceType, PieceUtils.BISHOP);
                        buffer[currentNumberOfMoves++] = Move.asBytes(startPosition, endPosition, pieceType, PieceUtils.ROOK);
                    }
                } else {
                    if (buffer != null) {
                        buffer[currentNumberOfMoves] = Move.asBytes(startPosition, endPosition, pieceType);
                    }
                    currentNumberOfMoves++;
                }
            }
        }
    }

    public static long getPinnedBB(Board board, int colorPinned) {
        long pinnedBB = 0;

        final long friendlyOccupiedSquareBB;
        final long enemyOccupiedSquareBB;
        final long opponentBishopBB;
        final long opponentRookBB;
        final long kingBB;
        final long opponentQueenBB;
        if(ColorUtils.isWhite(colorPinned)) {
            opponentBishopBB = board.bishopBB & board.blackBB;
            opponentRookBB = board.rookBB & board.blackBB;
            opponentQueenBB = board.queenBB & board.blackBB;
            kingBB = board.kingBB & board.whiteBB;
            friendlyOccupiedSquareBB = board.whiteBB;
            enemyOccupiedSquareBB = board.blackBB;
        } else {
            opponentBishopBB = board.bishopBB & board.whiteBB;
            opponentRookBB = board.rookBB & board.whiteBB;
            opponentQueenBB = board.queenBB & board.whiteBB;
            kingBB = board.kingBB & board.blackBB;
            friendlyOccupiedSquareBB = board.blackBB;
            enemyOccupiedSquareBB = board.whiteBB;
        }

        // King position
        final int kingPosition = BitUtils.bitScanForward(kingBB);

        final long opponentRookLikeBB = opponentRookBB | opponentQueenBB;
        final long occupiedBB = friendlyOccupiedSquareBB | enemyOccupiedSquareBB;
        if((opponentRookLikeBB & (OrthogonalMoveUtils.orthogonals[kingPosition])) != 0) {
            // Simulating rook attack from the king itself
            long rookAttackFromKingBB = Rook.getAttackBB(kingPosition, occupiedBB);
            long potentialPinnedByRook = rookAttackFromKingBB & friendlyOccupiedSquareBB;
            if (potentialPinnedByRook != 0) {
                final long xRayRookAttackFromKingBB = rookAttackFromKingBB ^ Rook.getAttackBB(kingPosition, (friendlyOccupiedSquareBB ^ potentialPinnedByRook) | enemyOccupiedSquareBB);
                long rookPinners = xRayRookAttackFromKingBB & opponentRookLikeBB;
                while (rookPinners != 0) {
                    final int pinnerIndex = BitUtils.bitScanForward(rookPinners);
                    rookPinners &= rookPinners - 1;
                    pinnedBB |= potentialPinnedByRook & ObstructedLinesUtils.OBSTRUCTED_BB[pinnerIndex][kingPosition];
                }
            }
        }

        final long opponentBishopLikeBB = opponentBishopBB | opponentQueenBB;
        if((opponentBishopLikeBB & (DiagonalMoveUtils.diagonals[kingPosition])) != 0) {
            if (opponentBishopLikeBB != 0) {
                // Simulating bishop attack from the king itself
                final long bishopAttackFromKingBB = Bishop.getAttackBB(kingPosition, occupiedBB);
                final long potentialPinnedByBishop = bishopAttackFromKingBB & friendlyOccupiedSquareBB;
                if (potentialPinnedByBishop != 0) {
                    final long xRayBishopAttackFromKingBB = bishopAttackFromKingBB ^ Bishop.getAttackBB(kingPosition, (friendlyOccupiedSquareBB ^ potentialPinnedByBishop) | enemyOccupiedSquareBB);
                    long bishopPinners = xRayBishopAttackFromKingBB & opponentBishopLikeBB;
                    while (bishopPinners != 0) {
                        final int pinnerIndex = BitUtils.bitScanForward(bishopPinners);
                        bishopPinners &= bishopPinners - 1;
                        pinnedBB |= potentialPinnedByBishop & ObstructedLinesUtils.OBSTRUCTED_BB[pinnerIndex][kingPosition];
                    }
                }
            }
        }

        return pinnedBB;
    }

    public static long doGetAttackBB(Board board, int color, long occupiedXorBB) {
        final long bishopBB;
        final long rookBB;
        final long knightBB;
        final long kingBB;
        final long pawnBB;
        if(ColorUtils.isWhite(color)) {
            bishopBB = (board.bishopBB | board.queenBB) & board.whiteBB;
            rookBB = (board.rookBB | board.queenBB) & board.whiteBB;
            knightBB = board.knightBB & board.whiteBB;
            kingBB = board.kingBB & board.whiteBB;
            pawnBB = board.pawnBB & board.whiteBB;
        } else {
            bishopBB = (board.bishopBB | board.queenBB) & board.blackBB;
            rookBB = (board.rookBB | board.queenBB) & board.blackBB;
            knightBB = board.knightBB & board.blackBB;
            kingBB = board.kingBB & board.blackBB;
            pawnBB = board.pawnBB & board.blackBB;
        }

        return doGetAttackBB(kingBB, bishopBB, rookBB, knightBB, pawnBB, board.gameBB ^ occupiedXorBB, color);
    }

    public static long getCheckersBB(int positionIndex, DirtyBoard board, int color, boolean earlyExit) {
        final long bishopBB;
        final long rookBB;
        final long knightBB;
        final long kingBB;
        final long pawnBB;
        if(ColorUtils.isWhite(color)) {
            bishopBB = board.whiteBishopBB | board.whiteQueenBB;
            rookBB = board.whiteRookBB | board.whiteQueenBB;
            knightBB = board.whiteKnightBB;
            kingBB = board.whiteKingBB;
            pawnBB = board.whitePawnBB;
        } else {
            bishopBB = board.blackBishopBB | board.blackQueenBB;
            rookBB = board.blackRookBB | board.blackQueenBB;
            knightBB = board.blackKnightBB;
            kingBB = board.blackKingBB;
            pawnBB = board.blackPawnBB;
        }

        return getCheckersBB(positionIndex, kingBB, bishopBB, rookBB, knightBB, pawnBB,
                board.computeSquaresOccupiedByBlackBB() | board.computeSquaresOccupiedByWhiteBB(), color, earlyExit);
    }

    public static long getCheckersBB(int positionIndex, Board board, int color, boolean earlyExit) {
        final long bishopBB;
        final long rookBB;
        final long knightBB;
        final long kingBB;
        final long pawnBB;
        final boolean white = ColorUtils.isWhite(color);
        final long sideBoardBB = white ? board.whiteBB : board.blackBB;
        bishopBB = (board.bishopBB | board.queenBB) & sideBoardBB;
        rookBB = (board.rookBB | board.queenBB) & sideBoardBB;
        knightBB = board.knightBB & sideBoardBB;
        kingBB = board.kingBB & sideBoardBB;
        pawnBB = board.pawnBB & sideBoardBB;

        return getCheckersBB(positionIndex, kingBB, bishopBB, rookBB, knightBB, pawnBB,
                board.gameBB, color, earlyExit);
    }

    public static long getCheckersBB(int positionIndex, long kingBB, long bishopBB, long rookBB,
                                     long knightBB, long pawnBB, long occupiedBB, int color, boolean earlyExit) {
        long attackingPieces = Knight.getAttackBB(positionIndex) & knightBB;
        attackingPieces |= King.getAttackBB(positionIndex) & kingBB;
        attackingPieces |= Pawn.getAttackBB(positionIndex, ColorUtils.switchColor(color)) & pawnBB;
        if(earlyExit && attackingPieces != 0) {
            return attackingPieces;
        }
        attackingPieces |= Bishop.getAttackBB(positionIndex, occupiedBB) & (bishopBB);
        if(earlyExit && attackingPieces != 0) {
            return attackingPieces;
        }
        attackingPieces |= Rook.getAttackBB(positionIndex, occupiedBB) & (rookBB);

        return attackingPieces;
    }

    public static long doGetAttackBB(long kingBB, long bishopBB, long rookBB, long knightBB, long pawnBB,
                                     long occupiedBB, int color) {
        long attackBB = 0;

        // Rook moves
        while(rookBB != 0) {
            int rookPosition = BitUtils.bitScanForward(rookBB);
            attackBB |= Rook.getAttackBB(rookPosition, occupiedBB);
            rookBB &= rookBB - 1;
        }

        // Bishop moves
        while(bishopBB != 0) {
            int bishopPosition = BitUtils.bitScanForward(bishopBB);
            attackBB |= Bishop.getAttackBB(bishopPosition, occupiedBB);
            bishopBB &= bishopBB - 1;
        }

        // Knight moves
        while(knightBB != 0) {
            int knightPosition = BitUtils.bitScanForward(knightBB);
            attackBB |= Knight.getAttackBB(knightPosition);
            knightBB &= knightBB - 1;
        }

        attackBB |= Pawn.getAttackBB(pawnBB, color);

        // King moves
        while(kingBB != 0) {
            int kingPosition = BitUtils.bitScanForward(kingBB);
            attackBB |= King.getAttackBB(kingPosition);
            kingBB &= kingBB - 1;
        }

        return attackBB;
    }
}
