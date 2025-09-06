package max.chess.engine.game;

import max.chess.engine.movegen.utils.CheckUtils;
import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;
import max.chess.engine.utils.PieceUtils;
import max.chess.engine.game.board.Board;
import max.chess.engine.game.board.MovePlayed;
import max.chess.engine.movegen.Move;
import max.chess.engine.movegen.MoveGenerator;
import max.chess.engine.utils.notations.FENUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Game {

    public static void warmUp() {
        MoveGenerator.warmUp();
    }

    private final Board board;

    public final RepetitionCounter repetitionCounter;
    public int currentPlayer = ColorUtils.WHITE;
    public boolean whiteCanCastleKingSide = true;
    public boolean whiteCanCastleQueenSide = true;
    public boolean blackCanCastleKingSide = true;
    public boolean blackCanCastleQueenSide = true;

    public int halfMoveClock = 0;
    public int fullMoveClock = 1;

    private long zobristKey = 0;

    public Game() {
        board = new Board(this);
        recomputeZobristKey();
        repetitionCounter = new RepetitionCounter(8); // 256 entries
    }

    public int[] getLegalMoves() {
        return getLegalMoves(false);
    }

    // ignoreDraw is useful for perft
    public int[] getLegalMoves(boolean ignoreDraw) {
        if(!ignoreDraw && isADraw()) {
            return new int[0];
        }
        return GameCache.getOrComputeLegalMoves(this);
    }

    public boolean isADraw() {
        // TODO implement insufficient material draw
        return isInsufficientMaterial()
                || repetitionCounter.get(zobristKey) >= 3 // 3fold repetition
                || halfMoveClock >= 100;  // 50-moves rule
    }

    public boolean isHardDraw() {
        return isInsufficientMaterial()
                || halfMoveClock >= 100;  // 50-moves rule
    }

    public int getLegalMoves(int[] buffer) {
        return getLegalMoves(buffer, false);
    }

    // ignoreDraw is useful for perft
    public int getLegalMoves(int[] buffer, boolean ignoreDraw) {
        if(!ignoreDraw && isADraw()) {
            return 0;
        }
        return MoveGenerator.generateMoves(this, buffer);
    }

    public int getLegalMovesCount() {
        return getLegalMovesCount(false);
    }

    // ignoreDraw is useful for perft
    public int getLegalMovesCount(boolean ignoreDraw) {
        if(!ignoreDraw && isADraw()) {
            return 0;
        }
        return MoveGenerator.countMoves(this);
    }

    public int getOpponentLegalMovesCount() {
        return getOpponentLegalMovesCount(false);
    }

    // ignoreDraw is useful for perft
    public int getOpponentLegalMovesCount(boolean ignoreDraw) {
        if(!ignoreDraw && isADraw()) {
            return 0;
        }
        return MoveGenerator.countMovesOpponent(this);
    }

    public GameChanges playSimpleMove(Move move) {
        if(fullMoveClock == 1) {
            repetitionCounter.inc(zobristKey);
        }

        int previousHalfMoveClock = halfMoveClock;
        boolean previousWhiteCanCastleKingSide = whiteCanCastleKingSide;
        boolean previousWhiteCanCastleQueenSide = whiteCanCastleQueenSide;
        boolean previousBlackCanCastleKingSide = blackCanCastleKingSide;
        boolean previousBlackCanCastleQueenSide = blackCanCastleQueenSide;
        final MovePlayed movePlayed = board.playSimpleMove(move);
        byte pieceType = movePlayed.pieceType();

        if(ColorUtils.isBlack(currentPlayer)) {
            fullMoveClock++;
        }

        int startPosition = movePlayed.move().startPosition();
        int endPosition = movePlayed.move().endPosition();
        if(pieceType == PieceUtils.KING) {
            if(ColorUtils.isWhite(currentPlayer)) {
                setWhiteCanCastleKingSide(false);
                setWhiteCanCastleQueenSide(false);
            } else {
                setBlackCanCastleKingSide(false);
                setBlackCanCastleQueenSide(false);
            }
        }

        // Removing castling rights when needed
        if(whiteCanCastleKingSide) {
            if(endPosition == 7 || startPosition == 7) {
                setWhiteCanCastleKingSide(false);
            }
        }
        if(whiteCanCastleQueenSide) {
            if(endPosition == 0 || startPosition == 0) {
                setWhiteCanCastleQueenSide(false);
            }
        }
        if(blackCanCastleKingSide) {
            if(endPosition == 63 || startPosition == 63) {
                setBlackCanCastleKingSide(false);
            }
        }
        if(blackCanCastleQueenSide) {
            if(endPosition == 56 || startPosition == 56) {
                setBlackCanCastleQueenSide(false);
            }
        }

        if(pieceType == PieceUtils.PAWN || movePlayed.pieceEaten() != PieceUtils.NONE) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }

        nextTurn();
        repetitionCounter.inc(zobristKey);
        return new GameChanges(movePlayed, previousHalfMoveClock, previousWhiteCanCastleKingSide, previousWhiteCanCastleQueenSide,
                previousBlackCanCastleKingSide, previousBlackCanCastleQueenSide);
    }

    public long playMove(int move) {
        // save repetition epoch
        int prevEpoch = repetitionCounter.snapshotEpoch();

        int previousHalfMoveClock = halfMoveClock;
        boolean previousWhiteCanCastleKingSide = whiteCanCastleKingSide;
        boolean previousWhiteCanCastleQueenSide = whiteCanCastleQueenSide;
        boolean previousBlackCanCastleKingSide = blackCanCastleKingSide;
        boolean previousBlackCanCastleQueenSide = blackCanCastleQueenSide;
        byte pieceType = Move.getPieceType(move);
        final int movePlayed = board.playMove(move);

        if(ColorUtils.isBlack(currentPlayer)) {
            fullMoveClock++;
        }

        int startPosition = Move.getStartPosition(move);
        int endPosition = Move.getEndPosition(move);
        if(pieceType == PieceUtils.KING) {
            if(ColorUtils.isWhite(currentPlayer)) {
                setWhiteCanCastleKingSide(false);
                setWhiteCanCastleQueenSide(false);
            } else {
                setBlackCanCastleKingSide(false);
                setBlackCanCastleQueenSide(false);
            }
        }

        // Removing castling rights when needed
        if(whiteCanCastleKingSide) {
            if(endPosition == 7 || startPosition == 7) {
                setWhiteCanCastleKingSide(false);
            }
        }
        if(whiteCanCastleQueenSide) {
            if(endPosition == 0 || startPosition == 0) {
                setWhiteCanCastleQueenSide(false);
            }
        }
        if(blackCanCastleKingSide) {
            if(endPosition == 63 || startPosition == 63) {
                setBlackCanCastleKingSide(false);
            }
        }
        if(blackCanCastleQueenSide) {
            if(endPosition == 56 || startPosition == 56) {
                setBlackCanCastleQueenSide(false);
            }
        }

        if(pieceType == PieceUtils.PAWN || MovePlayed.getPieceEaten(movePlayed) != PieceUtils.NONE) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }

        nextTurn();

        // position after move is now current
        repetitionCounter.inc(zobristKey);

        if(BitUtils.bitCount(board.kingBB) != 2) {
            return 0;
        }
        return GameChanges.asBytes(movePlayed, previousHalfMoveClock, previousWhiteCanCastleKingSide, previousWhiteCanCastleQueenSide,
                previousBlackCanCastleKingSide, previousBlackCanCastleQueenSide, prevEpoch);
    }

    public void undoMove(long gameChanges) {
        if(BitUtils.bitCount(board.kingBB) != 2) {
            return;
        }
        repetitionCounter.dec(zobristKey);
        int movePlayed = GameChanges.getMovePlayed(gameChanges);
        board.undoMove(movePlayed);

        setWhiteCanCastleKingSide(GameChanges.getPreviousWhiteCanCastleKingSide(gameChanges));
        setWhiteCanCastleQueenSide(GameChanges.getPreviousWhiteCanCastleQueenSide(gameChanges));

        setBlackCanCastleKingSide(GameChanges.getPreviousBlackCanCastleKingSide(gameChanges));
        setBlackCanCastleQueenSide(GameChanges.getPreviousBlackCanCastleQueenSide(gameChanges));


        this.halfMoveClock = GameChanges.getPreviousHalfMoveClock(gameChanges);
        previousTurn();

        if(ColorUtils.isBlack(currentPlayer)) {
            fullMoveClock--;
        }

        // finally, restore repetition epoch from the token saved before the move
        int prevEpoch = GameChanges.getPreviousEpoch(gameChanges);
        repetitionCounter.restoreEpoch(prevEpoch);
    }

    public void undoNullMove(long gameChanges) {

        if(BitUtils.bitCount(board.kingBB) != 2) {
            return;
        }
        repetitionCounter.dec(zobristKey);
        int movePlayed = GameChanges.getMovePlayed(gameChanges);
        board.undoNullMove(movePlayed);

        this.halfMoveClock = GameChanges.getPreviousHalfMoveClock(gameChanges);

        previousTurn();
        if(ColorUtils.isBlack(currentPlayer)) {
            fullMoveClock--;
        }

        // finally, restore repetition epoch from the token saved before the move
        int prevEpoch = GameChanges.getPreviousEpoch(gameChanges);
        repetitionCounter.restoreEpoch(prevEpoch);
    }

    public PlayerState getPlayerState() {
        if(isADraw()) {
            return PlayerState.DRAW;
        }

        boolean legalMovePossible = MoveGenerator.countMoves(this) > 0;
        if(legalMovePossible) {
            return PlayerState.IN_PROGRESS;
        }

        // We have no legal move possible, so we are either in pat or checkmate depending on the king check state
        boolean isKingInCheck = inCheck();

        if(isKingInCheck) {
            return PlayerState.CHECKMATE;
        }

        return PlayerState.PAT;
    }

    public boolean inCheck() {
        boolean isWhiteTurn = ColorUtils.isWhite(currentPlayer);
        long usKingBB = board.kingBB & (isWhiteTurn ? board.whiteBB : board.blackBB);
        int kingPosition = BitUtils.bitScanForward(usKingBB);
        return MoveGenerator.getCheckersBB(kingPosition, board, ColorUtils.switchColor(currentPlayer), true) != 0;
    }

    private boolean isInsufficientMaterial() {
        // TODO
        return false;
    }

    public void setWhiteCanCastleKingSide(boolean whiteCanCastleKingSide) {
        if(this.whiteCanCastleKingSide != whiteCanCastleKingSide) {
            repetitionCounter.resetEpoch();
            zobristKey = ZobristHashKeys.switchWhiteKingSideCastle(zobristKey);
            this.whiteCanCastleKingSide = whiteCanCastleKingSide;
        }
    }

    public void setWhiteCanCastleQueenSide(boolean whiteCanCastleQueenSide) {
        if(this.whiteCanCastleQueenSide != whiteCanCastleQueenSide) {
            repetitionCounter.resetEpoch();
            zobristKey = ZobristHashKeys.switchWhiteQueenSideCastle(zobristKey);
            this.whiteCanCastleQueenSide = whiteCanCastleQueenSide;
        }
    }

    public void setBlackCanCastleKingSide(boolean blackCanCastleKingSide) {
        if(this.blackCanCastleKingSide != blackCanCastleKingSide) {
            repetitionCounter.resetEpoch();
            zobristKey = ZobristHashKeys.switchBlackKingSideCastle(zobristKey);
            this.blackCanCastleKingSide = blackCanCastleKingSide;
        }
    }

    public Game setBlackCanCastleQueenSide(boolean blackCanCastleQueenSide) {
        if(this.blackCanCastleQueenSide != blackCanCastleQueenSide) {
            repetitionCounter.resetEpoch();
            zobristKey = ZobristHashKeys.switchBlackQueenSideCastle(zobristKey);
            this.blackCanCastleQueenSide = blackCanCastleQueenSide;
        }
        return this;
    }

    public List<GameChanges> playMoves(String moves) {
        return playMoves(Arrays.stream(moves.split(" ")).toList());
    }

    public List<GameChanges> playMoves(List<String> moveList) {
        List<GameChanges> gameChangesList = new ArrayList<>(moveList.size());
        for(String move : moveList) {
            gameChangesList.add(playSimpleMove(Move.fromAlgebraicNotation(move)));
        }

        return gameChangesList;
    }

    public Board board() {
        return board;
    }

    public void nextTurn() {
        switchPlayer();
    }

    private void switchPlayer() {
        this.currentPlayer = ColorUtils.switchColor(currentPlayer);
        this.zobristKey = ZobristHashKeys.switchPlayer(zobristKey);
    }

    public void previousTurn() {
        switchPlayer();

    }

    public long zobristKey() {
        return zobristKey;
    }

    public void setZobristKey(long zobristKey) {
        this.zobristKey = zobristKey;
    }

    // Use incremental update on the hotpath
    @Deprecated
    public void recomputeZobristKey() {
        zobristKey = ZobristHashKeys.getHashKey(this);
    }

    public void setCurrentPlayer(int currentPlayer) {
        if(currentPlayer != this.currentPlayer) {
            zobristKey = ZobristHashKeys.switchPlayer(zobristKey);
        }
        this.currentPlayer = currentPlayer;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Game game = (Game) o;
        return zobristKey == game.zobristKey;
    }

    @Override
    public int hashCode() {
        return (int) zobristKey;
    }

    public long playNullMove() {
        // save repetition epoch
        int prevEpoch = repetitionCounter.snapshotEpoch();

        int previousHalfMoveClock = halfMoveClock;
        boolean previousWhiteCanCastleKingSide = whiteCanCastleKingSide;
        boolean previousWhiteCanCastleQueenSide = whiteCanCastleQueenSide;
        boolean previousBlackCanCastleKingSide = blackCanCastleKingSide;
        boolean previousBlackCanCastleQueenSide = blackCanCastleQueenSide;
        final int movePlayed = board.playNullMove();

        if(ColorUtils.isBlack(currentPlayer)) {
            fullMoveClock++;
        }

        halfMoveClock++;

        nextTurn();

        repetitionCounter.inc(zobristKey);

        if(BitUtils.bitCount(board.kingBB) != 2) {
            return 0;
        }
        return GameChanges.asBytes(movePlayed, previousHalfMoveClock, previousWhiteCanCastleKingSide, previousWhiteCanCastleQueenSide,
                previousBlackCanCastleKingSide, previousBlackCanCastleQueenSide, prevEpoch);
    }

    @Override
    public String toString() {
        return boardAscii(this);
    }

    /** Returns an ASCII diagram of the board (ranks 8..1). */
    public static String boardAscii(Game game) {
        Board b = game.board();
        StringBuilder sb = new StringBuilder(8 * (8 + 4));
        for (int rank = 7; rank >= 0; rank--) {
            sb.append(rank + 1).append("  ");
            for (int file = 0; file < 8; file++) {
                int sq = rank * 8 + file;
                sb.append(pieceCharAt(b, sq)).append(' ');
            }
            sb.append('\n');
        }
        sb.append("\n   a b c d e f g h");
        return sb.toString();
    }
    /** Returns a pretty board followed by the computed FEN on the next line. */
    public static String boardWithFen(Game game) {
        StringBuilder sb = new StringBuilder();
        sb.append(boardAscii(game)).append('\n');
        sb.append("FEN: ").append(FENUtils.getFENFromBoard(game));
        // Optional: include zobrist and side info
        sb.append("\nZobrist: 0x").append(Long.toHexString(game.zobristKey()));
        return sb.toString();
    }

    /** Returns FEN char for the piece on sq, or '.' if empty. Uppercase = white, lowercase = black. */
    private static char pieceCharAt(Board b, int sq) {
        int pt = b.getPieceTypeAt(sq);
        if (pt == PieceUtils.NONE) return '.';
        long bit = 1L << sq;
        boolean white = (b.whiteBB & bit) != 0L;
        char c = switch (pt) {
            case PieceUtils.PAWN   -> 'p';
            case PieceUtils.KNIGHT -> 'n';
            case PieceUtils.BISHOP -> 'b';
            case PieceUtils.ROOK   -> 'r';
            case PieceUtils.QUEEN  -> 'q';
            case PieceUtils.KING   -> 'k';
            default -> '?';
        };
        return white ? Character.toUpperCase(c) : c;
    }
}
