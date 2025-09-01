package max.chess.engine.game;

import max.chess.engine.movegen.utils.CheckUtils;
import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;
import max.chess.engine.utils.PieceUtils;
import max.chess.engine.game.board.Board;
import max.chess.engine.game.board.MovePlayed;
import max.chess.engine.movegen.Move;
import max.chess.engine.movegen.MoveGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Game {

    public static void warmUp() {
        MoveGenerator.warmUp();
    }

    private final Board board;

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
    }

    public Game(Game other) {
        this.board = new Board(this, other.board);
        this.halfMoveClock = other.halfMoveClock;
        this.fullMoveClock = other.fullMoveClock;
        this.whiteCanCastleKingSide = other.whiteCanCastleKingSide;
        this.whiteCanCastleQueenSide = other.whiteCanCastleQueenSide;
        this.blackCanCastleKingSide = other.blackCanCastleKingSide;
        this.blackCanCastleQueenSide = other.blackCanCastleQueenSide;
        this.currentPlayer = other.currentPlayer;
        this.zobristKey = other.zobristKey;
    }

    public int[] getLegalMoves() {
        return GameCache.getOrComputeLegalMoves(this);
    }
    public int getLegalMoves(int[] buffer) {
        return MoveGenerator.generateMoves(this, buffer);
    }
    public int getLegalMovesCount() {
        return MoveGenerator.countMoves(this);
    }

    public GameChanges playSimpleMove(Move move) {
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

        return new GameChanges(movePlayed, previousHalfMoveClock, previousWhiteCanCastleKingSide, previousWhiteCanCastleQueenSide,
                previousBlackCanCastleKingSide, previousBlackCanCastleQueenSide);
    }

    public long playMove(int move) {
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

        return GameChanges.asBytes(movePlayed, previousHalfMoveClock, previousWhiteCanCastleKingSide, previousWhiteCanCastleQueenSide,
                previousBlackCanCastleKingSide, previousBlackCanCastleQueenSide);
    }

    public void undoMove(long gameChanges) {
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
    }

    public PlayerState getPlayerState() {
        if(isInsufficientMaterial()) {
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
            zobristKey = ZobristHashKeys.switchWhiteKingSideCastle(zobristKey);
            this.whiteCanCastleKingSide = whiteCanCastleKingSide;
        }
    }

    public void setWhiteCanCastleQueenSide(boolean whiteCanCastleQueenSide) {
        if(this.whiteCanCastleQueenSide != whiteCanCastleQueenSide) {
            zobristKey = ZobristHashKeys.switchWhiteQueenSideCastle(zobristKey);
            this.whiteCanCastleQueenSide = whiteCanCastleQueenSide;
        }
    }

    public void setBlackCanCastleKingSide(boolean blackCanCastleKingSide) {
        if(this.blackCanCastleKingSide != blackCanCastleKingSide) {
            zobristKey = ZobristHashKeys.switchBlackKingSideCastle(zobristKey);
            this.blackCanCastleKingSide = blackCanCastleKingSide;
        }
    }

    public Game setBlackCanCastleQueenSide(boolean blackCanCastleQueenSide) {
        if(this.blackCanCastleQueenSide != blackCanCastleQueenSide) {
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
}
