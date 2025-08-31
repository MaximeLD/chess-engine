package max.chess.engine.utils;

public final class ColorUtils {
    public static final int BLACK = -1;
    public static final int WHITE = 1;

    public static int switchColor(int color) {
        return ~color + 1;
    }

    public static boolean isBlack(int color) {
        return color == BLACK;
    }

    public static boolean isWhite(int color) {
        return color == WHITE;
    }
}
