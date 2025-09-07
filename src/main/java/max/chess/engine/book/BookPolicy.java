package max.chess.engine.book;

public record BookPolicy(
        int  maxPlies,        // stop using book after this many half-moves
        int  minWeight,       // ignore book entries with weight below this
        int  randomnessPct,   // 0..100. 0 = deterministic best; 100 = weight sampling
        boolean preferMainline // tie-break by highest weight when deterministic
) {
    public static BookPolicy defaults() { return new BookPolicy(20, 2, 15, true); }
}
