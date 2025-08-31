package max.chess.engine.common;

// Don't use this enum on the hotpath, only for convenience in warm-up or other parts of the game
@Deprecated
public enum Color {
    BLACK, WHITE;

    public Color getOppositeColor() {
        if(this == WHITE) {
            return BLACK;
        } else {
            return WHITE;
        }
    }
}
