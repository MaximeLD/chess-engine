package max.chess.engine.game.board;

import max.chess.engine.common.PieceType;
import max.chess.engine.game.board.utils.BoardUtils;
import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;
import max.chess.engine.utils.PieceUtils;
import max.chess.engine.game.Game;
import max.chess.engine.game.ZobristHashKeys;
import max.chess.engine.movegen.Move;
import max.chess.engine.movegen.utils.BitBoardUtils;

import java.util.Arrays;

public class Board {
    private final Game game;
    public long bishopBB = 0;
    public long queenBB = 0;
    public long kingBB = 0;
    public long knightBB = 0;
    public long rookBB = 0;
    public long pawnBB = 0;

    public long whiteBB = 0;
    public long blackBB = 0;

    public long gameBB = 0;

    public int enPassantFile = -1;
    public int enPassantIndex = -1;

    private final byte[] pieceAt;

    public Board(Game game) {
        this.game = game;
        this.pieceAt = new byte[64];
        Arrays.fill(pieceAt, (byte) 0);
    }

    public Board(Game game, Board other) {
        this.game = game;
        this.bishopBB = other.bishopBB;
        this.queenBB = other.queenBB;
        this.kingBB = other.kingBB;
        this.knightBB = other.knightBB;
        this.rookBB = other.rookBB;
        this.pawnBB = other.pawnBB;
        this.whiteBB = other.whiteBB;
        this.blackBB = other.blackBB;
        this.gameBB = other.gameBB;
        this.enPassantFile = other.enPassantFile;
        this.enPassantIndex = other.enPassantIndex;
        this.pieceAt = other.pieceAt.clone();
    }

    public DirtyBoard dirtyCopy() {
        return new DirtyBoard(this);
    }

    private byte getPieceCode(long positionBB) {
        return PieceUtils.toPieceCode(pieceAt[BitUtils.bitScanForward(positionBB)]);
    }

    private int getPieceColor(long positionBB) {
        if((whiteBB & positionBB) != 0) {
            return ColorUtils.WHITE;
        } else if((blackBB & positionBB) != 0) {
            return ColorUtils.BLACK;
        }  else {
            return 0;
        }
    }

    // Simple move is a move with minimal information such as d7d8Q
    // move with just start and end position + promotion
    public MovePlayed playSimpleMove(Move move) {
        int startPosition = move.startPosition();
        int endPosition = move.endPosition();
        byte promotion = move.promotion();
        int originalEnPassantIndex = enPassantIndex;
        long startPositionBB = BitUtils.getPositionIndexBitMask(startPosition);
        long endPositionBB = BitUtils.getPositionIndexBitMask(endPosition);
        long pieceEatenBB = endPositionBB;
        byte pieceEaten;
        byte pieceType = getPieceCode(startPositionBB);
        int pieceColor = game.currentPlayer;

        // manage castle
        boolean castleKingSide = false;
        boolean castleQueenSide = false;
        if(pieceType == PieceUtils.KING) {
            if(endPosition - startPosition == 2) {
                // We have to assume we castle as it must be a legal move
                castleKingSide = true;
            } else if(startPosition - endPosition == 2) {
                castleQueenSide = true;
            }
        }

        // determining piece eaten
        // en passant move
        boolean enPassant = false;
        if(pieceType == PieceUtils.PAWN && endPosition == enPassantIndex && Math.abs(endPosition - startPosition) != 8) {
            // We have to assume it's en-passant as it's a legal move
            int enPassantEatenIndex = (ColorUtils.isWhite(pieceColor) ? endPosition - 8 : endPosition + 8);
            pieceEaten = PieceUtils.PAWN;
            pieceEatenBB = BitUtils.getPositionIndexBitMask(enPassantEatenIndex);
            enPassant = true;
        } else {
            pieceEaten = getPieceCode(endPositionBB);
        }

        // set enPassantSquare
        if(pieceType == PieceUtils.PAWN && (Math.abs(startPosition - endPosition) == 16)) {
            if(ColorUtils.isWhite(pieceColor)) {
                setEnPassantIndex(startPosition + 8);
            } else {
                setEnPassantIndex(startPosition - 8);
            }
        } else {
            setEnPassantIndex(-1);
        }

        // Castle moves
        if(castleKingSide) {
            castleKingSide(pieceColor);
        } else if(castleQueenSide) {
            castleQueenSide(pieceColor);
        } else {
            // Actual piece move
            boolean isPieceEaten = pieceEaten != PieceUtils.NONE;
            if(isPieceEaten) {
                removeFromBBs(pieceEatenBB, pieceEaten, ColorUtils.switchColor(pieceColor));
            }

            if(promotion != PieceUtils.NONE) {
                removeFromBBs(startPositionBB, pieceType, pieceColor);
                addToBBs(endPositionBB, promotion, pieceColor);
            } else {
                updateBBs(startPositionBB, endPositionBB, pieceType, pieceColor);
            }
        }

        return new MovePlayed(pieceType, move, enPassant, castleKingSide, castleQueenSide, pieceEaten, originalEnPassantIndex);
    }

    public int playMove(int move) {
        if(Move.isCastleKingSide(move)) {
            return playCastleKingSide(move);
        } else if(Move.isCastleQueenSide(move)) {
            return playCastleQueenSide(move);
        }
        boolean isEnPassant = Move.isEnPassant(move);
        byte pieceType = Move.getPieceType(move);
        int startPosition = Move.getStartPosition(move);
        int endPosition = Move.getEndPosition(move);
        byte promotion = Move.getPromotion(move);
        int originalEnPassantIndex = enPassantIndex;
        long startPositionBB = BitUtils.getPositionIndexBitMask(startPosition);
        long endPositionBB = BitUtils.getPositionIndexBitMask(endPosition);
        long pieceEatenBB = endPositionBB;
        byte pieceEaten;
        int pieceColor = game.currentPlayer;

        // determining piece eaten
        // en passant move
        if(isEnPassant) {
            int enPassantEatenIndex = (ColorUtils.isWhite(pieceColor) ? endPosition - 8 : endPosition + 8);
            pieceEaten = PieceUtils.PAWN;
            pieceEatenBB = BitUtils.getPositionIndexBitMask(enPassantEatenIndex);
        } else {
            pieceEaten = getPieceCode(endPositionBB);
        }

        boolean isCapture = pieceEaten != PieceUtils.NONE;


        // set enPassantSquare
        if(pieceType == PieceUtils.PAWN && Math.abs(endPosition - startPosition) == 16) {
            if(ColorUtils.isWhite(pieceColor)) {
                setEnPassantIndex(startPosition + 8);
            } else {
                setEnPassantIndex(startPosition - 8);
            }
        } else {
            setEnPassantIndex(-1);
        }

        // Actual piece move
        if(isCapture) {
            removeFromBBs(pieceEatenBB, pieceEaten, ColorUtils.switchColor(pieceColor));
        }

        if(promotion != PieceUtils.NONE) {
            removeFromBBs(startPositionBB, pieceType, pieceColor);
            addToBBs(endPositionBB, promotion, pieceColor);
        } else {
            updateBBs(startPositionBB, endPositionBB, pieceType, pieceColor);
        }

        return MovePlayed.asBytes(move, pieceEaten, originalEnPassantIndex);
    }

    private int playCastleQueenSide(int move) {
        int originalEnPassantIndex = enPassantIndex;
        castleQueenSide(game.currentPlayer);
        setEnPassantIndex(-1);
        return MovePlayed.asBytes(move, PieceUtils.NONE, originalEnPassantIndex);
    }

    private int playCastleKingSide(int move) {
        int originalEnPassantIndex = enPassantIndex;
        castleKingSide(game.currentPlayer);
        setEnPassantIndex(-1);
        return MovePlayed.asBytes(move, PieceUtils.NONE, originalEnPassantIndex);
    }

    private void castleKingSide(int color) {
        long originalKingBB;
        long originalRookBB;
        if(ColorUtils.isWhite(color)) {
            originalKingBB = kingBB & whiteBB;
            originalRookBB = BitUtils.getPositionIndexBitMask(7);
        } else {
            originalKingBB = kingBB & blackBB;
            originalRookBB = BitUtils.getPositionIndexBitMask(63);
        }

        long newKingBB = (originalKingBB << 2);
        long newRookBB = (originalRookBB >>> 2);

        updateBBs(originalKingBB, newKingBB, PieceUtils.KING, color);
        updateBBs(originalRookBB, newRookBB, PieceUtils.ROOK, color);
    }

    private void undoCastleKingSide(int color) {
        long originalKingBB;
        long originalRookBB;
        if(ColorUtils.isWhite(color)) {
            originalKingBB = kingBB & whiteBB;
            originalRookBB = BitUtils.getPositionIndexBitMask(5);
        } else {
            originalKingBB = kingBB & blackBB;
            originalRookBB = BitUtils.getPositionIndexBitMask(61);
        }

        long newKingBB = (originalKingBB >>> 2);
        long newRookBB = (originalRookBB << 2);

        updateBBs(originalKingBB, newKingBB, PieceUtils.KING, color);
        updateBBs(originalRookBB, newRookBB, PieceUtils.ROOK, color);
    }

    private void castleQueenSide(int color) {
        long originalKingBB;
        long originalRookBB;
        if(ColorUtils.isWhite(color)) {
            originalKingBB = kingBB & whiteBB;
            originalRookBB = BitUtils.getPositionIndexBitMask(0);
        } else {
            originalKingBB = kingBB & blackBB;
            originalRookBB = BitUtils.getPositionIndexBitMask(56);
        }

        long newKingBB = (originalKingBB >>> 2);
        long newRookBB = (originalRookBB << 3);

        updateBBs(originalKingBB, newKingBB, PieceUtils.KING, color);
        updateBBs(originalRookBB, newRookBB, PieceUtils.ROOK, color);
    }
    private void undoCastleQueenSide(int color) {
        long originalKingBB;
        long originalRookBB;
        if(ColorUtils.isWhite(color)) {
            originalKingBB = kingBB & whiteBB;
            originalRookBB = BitUtils.getPositionIndexBitMask(3);
        } else {
            originalKingBB = kingBB & blackBB;
            originalRookBB = BitUtils.getPositionIndexBitMask(59);
        }

        long newKingBB = (originalKingBB << 2);
        long newRookBB = (originalRookBB >>> 3);

        updateBBs(originalKingBB, newKingBB, PieceUtils.KING, color);
        updateBBs(originalRookBB, newRookBB, PieceUtils.ROOK, color);
    }

    private void removeFromBBs(long bb, byte pieceType, int color) {
        switch (pieceType) {
            case PieceUtils.PAWN -> pawnBB &= ~bb;
            case PieceUtils.KNIGHT -> knightBB &= ~bb;
            case PieceUtils.BISHOP -> bishopBB &= ~bb;
            case PieceUtils.ROOK -> rookBB &= ~bb;
            case PieceUtils.QUEEN -> queenBB &= ~bb;
            case PieceUtils.KING -> kingBB &= ~bb;
            case PieceUtils.NONE -> {
                return;
            }
        }
        if(ColorUtils.isWhite(color)) {
            whiteBB &= ~bb;
        } else {
            blackBB &= ~bb;
        }
        gameBB &= ~bb;

        pieceAt[BitUtils.bitScanForward(bb)] = PieceUtils.NONE;
    }

    public void addToBBs(long bb, byte pieceType, int color) {
        switch (pieceType) {
            case PieceUtils.PAWN -> pawnBB |= bb;
            case PieceUtils.KNIGHT -> knightBB |= bb;
            case PieceUtils.BISHOP -> bishopBB |= bb;
            case PieceUtils.ROOK -> rookBB |= bb;
            case PieceUtils.QUEEN -> queenBB |= bb;
            case PieceUtils.KING -> kingBB |= bb;
            case PieceUtils.NONE -> {
                return;
            }
        }
        if(ColorUtils.isWhite(color)) {
            whiteBB |= bb;
        } else {
            blackBB |= bb;
        }
        gameBB |= bb;

        pieceAt[BitUtils.bitScanForward(bb)] = pieceType;
    }

    private void updateBBs(long oldBB, long newBB, byte pieceType, int color) {
        removeFromBBs(oldBB, pieceType, color);
        addToBBs(newBB, pieceType, color);
    }

    public void undoMove(int movePlayed) {
        final int move = MovePlayed.getMove(movePlayed);
        boolean isCastleKingSide = Move.isCastleKingSide(move);
        boolean isCastleQueenSide = Move.isCastleQueenSide(move);
        int pieceColor = ColorUtils.switchColor(game.currentPlayer);

        if(isCastleKingSide) {
            undoCastleKingSide(pieceColor);
        } else if(isCastleQueenSide) {
            undoCastleQueenSide(pieceColor);
        } else {
            int startPosition = Move.getStartPosition(move);
            int endPosition = Move.getEndPosition(move);
            byte promotion = Move.getPromotion(move);
            byte pieceEaten = MovePlayed.getPieceEaten(movePlayed);
            boolean capture = pieceEaten != PieceUtils.NONE;
            boolean enPassant = Move.isEnPassant(move);
            long startPositionBB = BitUtils.getPositionIndexBitMask(startPosition);
            long endPositionBB = BitUtils.getPositionIndexBitMask(endPosition);
            byte pieceType = Move.getPieceType(move);
            if(promotion != PieceUtils.NONE) {
                removeFromBBs(endPositionBB, promotion, pieceColor);
                addToBBs(startPositionBB, PieceUtils.PAWN, pieceColor);
            } else {
                updateBBs(endPositionBB, startPositionBB, pieceType, pieceColor);
            }

            if(capture) {
                if(enPassant) {
                    int enPassantEatenIndex = ColorUtils.isWhite(pieceColor)
                            ? endPosition - 8
                            : endPosition + 8;
                    addToBBs(BitUtils.getPositionIndexBitMask(enPassantEatenIndex),
                            PieceUtils.PAWN, ColorUtils.switchColor(pieceColor));
                } else {
                    addToBBs(endPositionBB, pieceEaten, ColorUtils.switchColor(pieceColor));
                }
            }
        }

        // Resetting en passant square
        setEnPassantIndex(MovePlayed.getPreviousEnPassantIndex(movePlayed));
    }

    public Board setEnPassantFile(int enPassantFile) {
        if(this.enPassantFile != enPassantFile) {
            game.setZobristKey(ZobristHashKeys.changeEnPassant(game.zobristKey(), this.enPassantFile, enPassantFile));
            this.enPassantFile = enPassantFile;
        }
        return this;
    }

    public Board setEnPassantIndex(int enPassantIndex) {
        this.enPassantIndex = enPassantIndex;
        setEnPassantFile(determineEnPassantFile());
        return this;
    }

    private int determineEnPassantFile() {
        if(enPassantIndex == -1) {
            return -1;
        }

        final long enPassantBB = BitUtils.getPositionIndexBitMask(enPassantIndex + (ColorUtils.isWhite(game.currentPlayer) ? 8 : -8));
        final long candidateBB = BitBoardUtils.shift(enPassantBB, BitBoardUtils.Direction.EAST) | BitBoardUtils.shift(enPassantBB, BitBoardUtils.Direction.WEST);

        final long actualPawnBB = pawnBB & (ColorUtils.isWhite(game.currentPlayer) ? blackBB : whiteBB);
        final long pawnWhichCanEnPassant = candidateBB & actualPawnBB;
        if(pawnWhichCanEnPassant != 0L) {
            return enPassantIndex%8;
        }
        return -1;
    }

    public Game game() {
        return game;
    }

    // Utility method just there for other utils relying on square list
    // Not to be used on the hotpath
    @Deprecated
    public byte[] generateSquares() {
        byte[] squares = new byte[64];
        for(int i = 0; i < 64; i++) {
            long positionBB = BitUtils.getPositionIndexBitMask(i);
            int color = getPieceColor(positionBB);
            if(color == 0) {
                squares[i] = BoardUtils.encodeEmptySquare();
                continue;
            }

            PieceType pieceType = PieceUtils.toPieceType(getPieceCode(positionBB));
            squares[i] = BoardUtils.encodePiece(color, pieceType);
        }

        return squares;
    }

}
