package max.chess.engine.game.board;

import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;

// A "dirty board" which cannot be used continuously to play moves, but can be handy to verify king safety after a move
// as it only play the critical and minimal part of the move, without any extra, so it's CPU-friendly
// even if you have to build one from a "real" board first, as per my profiler
public class DirtyBoard {
    public long whiteBishopBB = 0;
    public long blackBishopBB = 0;
    public long whiteQueenBB = 0;
    public long blackQueenBB = 0;
    public long whiteKingBB = 0;
    public long blackKingBB = 0;
    public long whiteKnightBB = 0;
    public long blackKnightBB = 0;
    public long whiteRookBB = 0;
    public long blackRookBB = 0;
    public long whitePawnBB = 0;
    public long blackPawnBB = 0;

    public int enPassantFile = -1;
    public int enPassantIndex = -1;

    public DirtyBoard(final Board board) {
        this.whiteBishopBB = board.bishopBB & board.whiteBB;
        this.whiteKnightBB = board.knightBB  & board.whiteBB;
        this.whitePawnBB = board.pawnBB & board.whiteBB;
        this.whiteRookBB = board.rookBB & board.whiteBB;
        this.whiteQueenBB = board.queenBB & board.whiteBB;
        this.whiteKingBB = board.kingBB & board.whiteBB;

        this.blackBishopBB = board.bishopBB & board.blackBB;
        this.blackKnightBB = board.knightBB & board.blackBB;
        this.blackPawnBB = board.pawnBB & board.blackBB;
        this.blackRookBB = board.rookBB & board.blackBB;
        this.blackQueenBB = board.queenBB & board.blackBB;
        this.blackKingBB = board.kingBB & board.blackBB;

        this.enPassantFile = board.enPassantFile;
        this.enPassantIndex = board.enPassantIndex;
    }

    // Usually used with dirty boards to simulate a move and get an attack BB for king checks verification
    public void playDirtyMove(final int startPosition, final int endPosition, final int color) {
        // It's dirty move, we only have to include piece actual move and pieces eaten
        // We don't even care about the zobrist key updates or anything

        long pawnBB = ColorUtils.isWhite(color) ? whitePawnBB : blackPawnBB;
        boolean isAPawnMove = (BitUtils.getPositionIndexBitMask(startPosition) & pawnBB) != 0;
        boolean isEnPassant = enPassantFile != -1 && isAPawnMove && endPosition == enPassantIndex;
        
        if(isEnPassant) {
            if(ColorUtils.isWhite(color)) {
                blackPawnBB &= ~(1L << (endPosition-8));
            } else {
                whitePawnBB &= ~(1L << (endPosition+8));
            }
        } else {
            long notEndPositionBB = ~BitUtils.getPositionIndexBitMask(endPosition);
            if(ColorUtils.isWhite(color)) {
                blackPawnBB &= notEndPositionBB;
                blackBishopBB &= notEndPositionBB;
                blackKnightBB &= notEndPositionBB;
                blackQueenBB &= notEndPositionBB;
                blackRookBB &= notEndPositionBB;
                // No need to update the king BB, it cannot be captured
            } else {
                whitePawnBB &= notEndPositionBB;
                whiteBishopBB &= notEndPositionBB;
                whiteKnightBB &= notEndPositionBB;
                whiteQueenBB &= notEndPositionBB;
                whiteRookBB &= notEndPositionBB;
                // No need to update the king BB, it cannot be captured
            }
        }
        long notStartPositionBB = ~BitUtils.getPositionIndexBitMask(startPosition);
        long endPositionBB = BitUtils.getPositionIndexBitMask(endPosition);
        
        // Now we update the actual move
        if(ColorUtils.isBlack(color)) {
            long previousBlackPawnBB = blackPawnBB;
            blackPawnBB &= notStartPositionBB;
            if(previousBlackPawnBB != blackPawnBB) {
                blackPawnBB |= endPositionBB;
                return;
            }
            long previousBlackBishopBB = blackBishopBB;
            blackBishopBB &= notStartPositionBB;
            if(previousBlackBishopBB != blackBishopBB) {
                blackBishopBB |= endPositionBB;
                return;
            }
            long previousBlackKnightBB = blackKnightBB;
            blackKnightBB &= notStartPositionBB;
            if(previousBlackKnightBB != blackKnightBB) {
                blackKnightBB |= endPositionBB;
                return;
            }
            long previousBlackQueenBB = blackQueenBB;
            blackQueenBB &= notStartPositionBB;
            if(previousBlackQueenBB != blackQueenBB) {
                blackQueenBB |= endPositionBB;
                return;
            }
            long previousBlackRookBB = blackRookBB;
            blackRookBB &= notStartPositionBB;
            if(previousBlackRookBB != blackRookBB) {
                blackRookBB |= endPositionBB;
                return;
            }
            long previousBlackKingBB = blackKingBB;
            blackKingBB &= notStartPositionBB;
            if(previousBlackKingBB != blackKingBB) {
                blackKingBB |= endPositionBB;
                return;
            }
        } else {
            long previousWhitePawnBB = whitePawnBB;
            whitePawnBB &= notStartPositionBB;
            if(previousWhitePawnBB != whitePawnBB) {
                whitePawnBB |= endPositionBB;
                return;
            }
            long previousWhiteBishopBB = whiteBishopBB;
            whiteBishopBB &= notStartPositionBB;
            if(previousWhiteBishopBB != whiteBishopBB) {
                whiteBishopBB |= endPositionBB;
                return;
            }
            long previousWhiteKnightBB = whiteKnightBB;
            whiteKnightBB &= notStartPositionBB;
            if(previousWhiteKnightBB != whiteKnightBB) {
                whiteKnightBB |= endPositionBB;
                return;
            }
            long previousWhiteQueenBB = whiteQueenBB;
            whiteQueenBB &= notStartPositionBB;
            if(previousWhiteQueenBB != whiteQueenBB) {
                whiteQueenBB |= endPositionBB;
                return;
            }
            long previousWhiteRookBB = whiteRookBB;
            whiteRookBB &= notStartPositionBB;
            if(previousWhiteRookBB != whiteRookBB) {
                whiteRookBB |= endPositionBB;
                return;
            }
            long previousWhiteKingBB = whiteKingBB;
            whiteKingBB &= notStartPositionBB;
            if(previousWhiteKingBB != whiteKingBB) {
                whiteKingBB |= endPositionBB;
                return;
            }
        }
    }

    public long computeSquaresOccupiedByBlackBB() {
        return blackBishopBB | blackPawnBB |  blackKnightBB | blackRookBB | blackQueenBB | blackKingBB;
    }

    public long computeSquaresOccupiedByWhiteBB() {
        return whiteBishopBB | whitePawnBB |  whiteKnightBB | whiteRookBB | whiteQueenBB | whiteKingBB;
    }
}
