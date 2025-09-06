package max.chess.engine.utils.notations;

import max.chess.engine.common.PieceType;
import max.chess.engine.common.Position;
import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;
import max.chess.engine.utils.PieceUtils;
import max.chess.engine.game.board.utils.BoardUtils;
import max.chess.engine.game.Game;

// FEN Visualizer: https://www.redhotpawn.com/chess/chess-fen-viewer.php
public class FENUtils {
    private record PieceTypeAndColor(PieceType pieceType, int color) {}

    // https://en.wikipedia.org/wiki/Forsyth%E2%80%93Edwards_Notation
    public static Game getBoardFrom(String FEN) {
        Game game = new Game();
        String[] fenFields = FEN.split(" ");

        if(fenFields.length != 6) {
             throw new RuntimeException("Invalid FEN record");
        }

        String piecePlacement = fenFields[0];
        String currentTurn = fenFields[1];
        String castlingRights = fenFields[2];
        String enPassantSquare = fenFields[3];
        String halfMoveClock = fenFields[4];
        String fullMoveNumber = fenFields[5];

        injectPiecePlacement(game, piecePlacement);
        injectCurrentTurn(game, currentTurn);
        injectCastlingRights(game, castlingRights);
        injectEnPassantSquare(game, enPassantSquare);
        injectHalfMoveClock(game, halfMoveClock);
        injectFullMoveNumber(game, fullMoveNumber);

        game.recomputeZobristKey();
        return game;
    }

    public static String getFENFromBoard(Game game) {
        StringBuilder fen = new StringBuilder();
        injectPiecePlacement(game, fen);
        injectCurrentTurn(game, fen);
        injectCastlingRights(game, fen);
        injectEnPassantSquare(game, fen);
        injectHalfMoveClock(game, fen);
        injectFullMoveNumber(game, fen);
        return fen.toString();
    }

    private static void injectHalfMoveClock(Game game, StringBuilder fen) {
        fen.append(' ').append(game.halfMoveClock);
    }

    private static void injectEnPassantSquare(Game game, StringBuilder fen) {
        fen.append(' ');
        int enPassantIndex = game.board().enPassantIndex;
        if(enPassantIndex != -1) {
            fen.append(MoveIOUtils.getSquareFromPosition(Position.of(enPassantIndex)));
        } else {
            fen.append('-');
        }
    }

    private static void injectCastlingRights(Game game, StringBuilder fen) {
        fen.append(' ');
        StringBuilder castlingRights = new StringBuilder();
        if(game.whiteCanCastleKingSide) {
            castlingRights.append("K");
        }
        if(game.whiteCanCastleQueenSide) {
            castlingRights.append("Q");
        }
        if(game.blackCanCastleKingSide) {
            castlingRights.append("k");
        }
        if(game.blackCanCastleQueenSide) {
            castlingRights.append("q");
        }

        if(castlingRights.isEmpty()) {
            fen.append('-');
        } else {
            fen.append(castlingRights);
        }
    }

    private static void injectCurrentTurn(Game game, StringBuilder fen) {
        fen.append(' ').append(ColorUtils.isBlack(game.currentPlayer) ? 'b' : 'w');
    }

    private static void injectPiecePlacement(Game game, StringBuilder fen) {
        byte[] squares = game.board().generateSquares();
        long squaresOccupiedBB = game.board().gameBB;
        for(int i = 7 ; i >= 0 ; i--) {
            int emptySpaceCounter = 0;
            if(i != 7) {
                fen.append('/');
            }
            for(int j = 0 ; j < 8 ; j++) {
                Position position = Position.of(j, i);
                byte square = squares[position.getFlatIndex()];
                if(BoardUtils.isEmptySquare(square)) {
                    emptySpaceCounter++;
                    continue;
                }
                PieceType pieceType = BoardUtils.getSquarePieceType(square);
                int pieceColor = BoardUtils.getSquareColor(square);

                if(emptySpaceCounter != 0) {
                    fen.append(emptySpaceCounter);
                    emptySpaceCounter = 0;
                }
                fen.append(getFENLetterFromPiece(pieceType, pieceColor));
            }

            if(emptySpaceCounter != 0) {
                fen.append(emptySpaceCounter);
            }
        }
    }

    private static void injectFullMoveNumber(Game game, StringBuilder fen) {
        fen.append(' ').append(game.fullMoveClock);
    }

    private static void injectFullMoveNumber(Game game, String fullMoveNumber) {
        game.fullMoveClock = Integer.parseInt(fullMoveNumber);
    }

    private static void injectHalfMoveClock(Game game, String halfMoveClock) {
        game.halfMoveClock = Integer.parseInt(halfMoveClock);
    }

    private static void injectEnPassantSquare(Game game, String enPassantSquare) {
        if("-".equals(enPassantSquare)) {
            game.board().setEnPassantIndex(-1);
        } else {
            Position positionFromSquare = MoveIOUtils.getPositionFromSquare(enPassantSquare);
            game.board().setEnPassantIndex(positionFromSquare.getFlatIndex());
        }
    }

    private static void injectCastlingRights(Game game, String castlingRights) {
        boolean whiteCanCastleKingSide = false;
        boolean whiteCanCastleQueenSide = false;
        boolean blackCanCastleKingSide = false;
        boolean blackCanCastleQueenSide = false;

        for(char character : castlingRights.toCharArray()) {
            switch (character) {
                case 'K' -> whiteCanCastleKingSide = true;
                case 'k' -> blackCanCastleKingSide = true;
                case 'Q' -> whiteCanCastleQueenSide = true;
                case 'q' -> blackCanCastleQueenSide = true;
            }
        }
        game.setBlackCanCastleKingSide(blackCanCastleKingSide);
        game.setBlackCanCastleQueenSide(blackCanCastleQueenSide);
        game.setWhiteCanCastleKingSide(whiteCanCastleKingSide);
        game.setWhiteCanCastleQueenSide(whiteCanCastleQueenSide);
    }

    private static void injectCurrentTurn(Game game, String currentTurn) {
        int currentPlayer = "b".equals(currentTurn) ? ColorUtils.BLACK : ColorUtils.WHITE;
        game.setCurrentPlayer(currentPlayer);
    }

    private static void injectPiecePlacement(Game game, String piecePlacement) {
        String[] piecePlacementRows = piecePlacement.split("/");
        int currentRow = 8;
        int currentCol = 1;
        for(String piecePlacementRow : piecePlacementRows) {
            for(char character : piecePlacementRow.toCharArray()) {
                int nextColShift = 1;
                PieceTypeAndColor pieceTypeAndColor = null;
                switch (character) {
                    case '1','2','3','4','5','6','7','8' ->
                            nextColShift = Integer.parseInt(String.valueOf(character));
                    default ->
                            pieceTypeAndColor = getPieceTypeAndColorFromFENLetter(character);
                }
                if(pieceTypeAndColor != null) {
                    long positionBB = BitUtils.getPositionIndexBitMask(Position.of(currentCol-1, currentRow-1).getFlatIndex());
                    game.board().addToBBs(positionBB, PieceUtils.fromPieceType(pieceTypeAndColor.pieceType()), pieceTypeAndColor.color());
                }
                currentCol += nextColShift;
                if(currentCol > 8) {
                    currentCol = 1;
                }
            }

            currentRow--;
        }
    }

    private static char getFENLetterFromPiece(PieceType pieceType, int color) {
        char letter = switch (pieceType) {
            case KING -> 'k';
            case PAWN -> 'p';
            case KNIGHT -> 'n';
            case QUEEN -> 'q';
            case ROOK -> 'r';
            case BISHOP -> 'b';
            case NONE -> ' ';
        };

        return ColorUtils.isWhite(color) ? Character.toUpperCase(letter) : letter;
    }
    private static PieceTypeAndColor getPieceTypeAndColorFromFENLetter(char letter) {
        int color;
        PieceType pieceType = null;
        switch(letter) {
            case 'k' -> {
                pieceType = PieceType.KING;
                color = ColorUtils.BLACK;
            }
            case 'K' -> {
                pieceType = PieceType.KING;
                color = ColorUtils.WHITE;
            }
            case 'n' -> {
                pieceType = PieceType.KNIGHT;
                color = ColorUtils.BLACK;
            }
            case 'N' -> {
                pieceType = PieceType.KNIGHT;
                color = ColorUtils.WHITE;
            }
            case 'p' -> {
                pieceType = PieceType.PAWN;
                color = ColorUtils.BLACK;
            }
            case 'P' -> {
                pieceType = PieceType.PAWN;
                color = ColorUtils.WHITE;
            }
            case 'b' -> {
                pieceType = PieceType.BISHOP;
                color = ColorUtils.BLACK;
            }
            case 'B' -> {
                pieceType = PieceType.BISHOP;
                color = ColorUtils.WHITE;
            }
            case 'q' -> {
                pieceType = PieceType.QUEEN;
                color = ColorUtils.BLACK;
            }
            case 'Q' -> {
                pieceType = PieceType.QUEEN;
                color = ColorUtils.WHITE;
            }
            case 'r' -> {
                pieceType = PieceType.ROOK;
                color = ColorUtils.BLACK;
            }
            case 'R' -> {
                pieceType = PieceType.ROOK;
                color = ColorUtils.WHITE;
            }
            default -> throw new RuntimeException("Unexpected fen letter "+letter);
        }

        return new PieceTypeAndColor(pieceType, color);
    }
}
