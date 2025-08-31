package max.chess.engine.common;

// Don't use it on the hot path !
@Deprecated
public class Position {

    // coding with 2 digits mean we can have at most index 77 (10*x + y ; x and y int in [0,7])
    // but we also need relative negative positions up to -3, so we eventually have 100*(x+3) + y+3
    private final static Position[] POSITION_CACHE =  new Position[100*(8+3) + 8 + 3];
    static {
        for(int i = -3; i < 8; i++) {
            for(int j = -3; j < 8; j++) {
                POSITION_CACHE[getCacheIndex(i,j)] = new Position(i,j);
            }
        }
    }

    public static Position of(int x, int y) {
        int cacheIndex = getCacheIndex(x, y);
        if(cacheIndex >= POSITION_CACHE.length) {
            // It means we don't support this move and is out of the board
            return null;
        }

        return POSITION_CACHE[cacheIndex];
    }

    public static Position of(int flatIndex) {
        int x = flatIndex%8;
        int y = flatIndex/8;

        return Position.of(x, y);
    }

    private static int getCacheIndex(int x, int y) {
        return 100*(x+3)+(y+3);
    }
    public final int x;
    public final int y;
    public final int flatIndex;

    private Position(int x, int y) {
        this.x = x;
        this.y = y;
        this.flatIndex = x + 8*y;
    }

    public Position add(Position other) {
        return Position.of(other.getX() + x, other.getY() + y);
    }

    @Deprecated
    public Position copy() {
        return this;
    }

    public int getX() {
        return x;
    }
    public int getY() {
        return y;
    }

    public short getFlatIndex() {
        return (short) flatIndex;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public String toString() {
        return "Position{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }
}
