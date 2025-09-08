package max.chess.engine.tb;

public record TBRootResult(int bestMove, int wdl, int dtz) {
    // WDL:  2 = Win, 1 = CurseWin, 0 = Draw, -1 = BlessLoss, -2 = Loss (conventional mapping)
}
