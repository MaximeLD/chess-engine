package max.chess.engine.utils.notations;

import max.chess.engine.common.PieceType;
import max.chess.engine.common.Position;
import max.chess.engine.utils.PieceUtils;
import max.chess.engine.game.board.utils.BoardUtils;
import max.chess.engine.game.board.MovePlayed;
import max.chess.engine.movegen.Move;

public class MoveIOUtils {
    public static String writeAlgebraicNotation(MovePlayed movePlayed) {
        if(movePlayed.castleKingSide()) {
            return "O-O";
        }
        if(movePlayed.castleQueenSide()) {
            return "O-O-O";
        }
        String initialPosition = getSquareFromPosition(Position.of(movePlayed.move().startPosition()));
        String targetPosition = getSquareFromPosition(Position.of(movePlayed.move().endPosition()));
        String algebraicNotationLetter = getAlgebraicNotationLetter(PieceUtils.toPieceType(movePlayed.pieceType()));
        String enPassant = movePlayed.enPassant() ? " (e.p.)" : "";
        String take = BoardUtils.isEmptySquare(movePlayed.pieceEaten()) ? "x" : "";
        String promotion = movePlayed.move().promotion() != PieceUtils.NONE ? "="+getAlgebraicNotationLetter(PieceUtils.toPieceType(movePlayed.move().promotion())) : "";
        String checkInfo = "";
        // TODO
        return algebraicNotationLetter+initialPosition+take+targetPosition+promotion+enPassant+checkInfo;
    }

    public static String writeAlgebraicNotation(Move move) {
        String initialPosition = getSquareFromPosition(Position.of(move.startPosition()));
        String targetPosition = getSquareFromPosition(Position.of(move.endPosition()));
        String promotedPiece = move.promotion() == PieceUtils.NONE ? "" : getAlgebraicNotationLetter(PieceUtils.toPieceType(move.promotion()));
        return initialPosition+targetPosition+promotedPiece;
    }

    public static String writeAlgebraicNotation(int move) {
        return writeAlgebraicNotation(Move.fromBytes(move));
    }

    private static String getAlgebraicNotationLetter(PieceType pieceType) {
        return switch (pieceType) {
            case KING -> "K";
            case PAWN -> "";
            case KNIGHT -> "N";
            case QUEEN -> "Q";
            case ROOK -> "R";
            case BISHOP -> "B";
            case NONE -> null;
        };
    }

    public static PieceType getPieceTypeFromLetter(char letter) {
        return switch (letter) {
            case 'k', 'K' -> PieceType.KING;
            case 'n', 'N' -> PieceType.KNIGHT;
            case 'q', 'Q' -> PieceType.QUEEN;
            case 'r', 'R' -> PieceType.ROOK;
            case 'b', 'B' -> PieceType.BISHOP;
            default -> throw new RuntimeException("Unknown piece letter "+letter);
        };
    }

    public static String getSquareFromPosition(Position position) {
        String letter = getLetterFromPosition(position);
        String number = getNumberFromPosition(position);

        return letter+number;
    }

    public static String getNumberFromPosition(Position position) {
        return String.valueOf(position.getY() + 1);
    }
    public static String getLetterFromPosition(Position position) {
        return switch(position.getX()) {
            case 0 -> "a";
            case 1 -> "b";
            case 2 -> "c";
            case 3 -> "d";
            case 4 -> "e";
            case 5 -> "f";
            case 6 -> "g";
            case 7 -> "h";
            default -> throw new RuntimeException("position x should be in [1-8]");
        };
    }
    public static Position getPositionFromSquare(String square) {
        char[] squareChars = square.toCharArray();
        if(squareChars.length != 2) {
            throw new RuntimeException("square should be format 'a1'");
        }

        int y = Integer.parseInt(String.valueOf(squareChars[1]));
        Integer x = switch (squareChars[0]) {
            case 'a' -> 0;
            case 'b' -> 1;
            case 'c' -> 2;
            case 'd' -> 3;
            case 'e' -> 4;
            case 'f' -> 5;
            case 'g' -> 6;
            case 'h' -> 7;
            default -> throw new RuntimeException("square letter should be in [a-h]");
        };

        if(y > 8 || y < 1) {
            throw new RuntimeException("square digit should be in [1-8]");
        }

        y--; // 0-based

        return Position.of(x, y);
    }

    public static void printBitboard(long bitboard) {
        System.out.println(bitboardToString(bitboard));
    }

    public static String bitboardToString(long bitboard) {
        StringBuilder stringBuilder = new StringBuilder();
        StringBuilder line1 = new StringBuilder();
        StringBuilder line2 = new StringBuilder();
        StringBuilder line3 = new StringBuilder();
        StringBuilder line4 = new StringBuilder();
        StringBuilder line5 = new StringBuilder();
        StringBuilder line6 = new StringBuilder();
        StringBuilder line7 = new StringBuilder();
        StringBuilder line8 = new StringBuilder();

        StringBuilder currentStringBuilder;
        int counter = 0;
        long i = 1L;
        while(bitboard != 0) {
            if(counter < 8) {
                currentStringBuilder = line1;
            } else if(counter < 16) {
                currentStringBuilder = line2;
            } else if(counter < 24) {
                currentStringBuilder = line3;
            } else if(counter < 32) {
                currentStringBuilder = line4;
            } else if(counter < 40) {
                currentStringBuilder = line5;
            } else if(counter < 48) {
                currentStringBuilder = line6;
            } else if(counter < 56) {
                currentStringBuilder = line7;
            } else {
                currentStringBuilder = line8;
            }
            if((bitboard & i) != 0) {
                currentStringBuilder.append(" O ");
            } else {
                currentStringBuilder.append(" . ");
            }

            bitboard &= ~i;
            counter++;
            i <<= 1;
        }

        while(counter < 64) {
            if(counter < 8) {
                currentStringBuilder = line1;
            } else if(counter < 16) {
                currentStringBuilder = line2;
            } else if(counter < 24) {
                currentStringBuilder = line3;
            } else if(counter < 32) {
                currentStringBuilder = line4;
            } else if(counter < 40) {
                currentStringBuilder = line5;
            } else if(counter < 48) {
                currentStringBuilder = line6;
            } else if(counter < 56) {
                currentStringBuilder = line7;
            } else {
                currentStringBuilder = line8;
            }
            currentStringBuilder.append(" . ");
            counter++;
        }

        stringBuilder
                .append(line8).append('\n')
                .append(line7).append('\n')
                .append(line6).append('\n')
                .append(line5).append('\n')
                .append(line4).append('\n')
                .append(line3).append('\n')
                .append(line2).append('\n')
                .append(line1).append('\n');

        return stringBuilder.toString();
    }
}
