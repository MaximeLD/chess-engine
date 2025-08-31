package max.chess.engine.uci;

/** Engine should return this at the end of search. */
public final class UciResult {
    public final String bestmove;
    public final String ponder; // nullable
    public UciResult(String bestmove, String ponder) {
        this.bestmove = bestmove;
        this.ponder = ponder;
    }
    public static UciResult best(String bm) { return new UciResult(bm, null); }
    public static UciResult bestPonder(String bm, String pd) { return new UciResult(bm, pd); }
}
