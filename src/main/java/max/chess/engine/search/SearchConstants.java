package max.chess.engine.search;

public class SearchConstants {
    public static final int INF     =  30000;

    // Nominal ply bound for principal variation & heuristic arrays
    public static final int MAX_PLY = 128;

    // Extra headroom for per-ply move/score buffers used by qsearch
    // Keep this comfortably above MAX_PLY to survive deep capture trees.
    public static final int STACK_PLY = 256;

    // Allow roomy move lists; some synthetic positions can exceed 128.
    public static final int MAX_MOVES = 256;
}
