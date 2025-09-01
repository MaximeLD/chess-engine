package max.chess.engine.search;

import max.chess.engine.utils.notations.MoveIOUtils;

public record SearchResult(int move, int score, long nodes, long timeMs, long nps, int[] principalVariation) {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SearchResult\n")
            .append("best move: ").append(MoveIOUtils.writeAlgebraicNotation(move)).append("\n")
            .append("score: ").append(score).append("\n")
            .append("search time (ms): ").append(timeMs).append("\n")
            .append("nodes/sec: ").append(nps).append("\n")
            .append("PV: ");
        for(int move :  principalVariation) {
            sb.append(MoveIOUtils.writeAlgebraicNotation(move)).append(" ");
        }
        return sb.toString();
    }

    public String toUCIInfo() {
        StringBuilder sb = new StringBuilder("info")
            .append(" depth ").append(principalVariation.length)
                .append(" time ").append(timeMs)
                .append(" score cp ").append(score)
                .append(" nps ").append(nps)
                .append(" nodes ").append(nodes)
                .append(" pv ");

        for(int move :  principalVariation) {
            sb.append(MoveIOUtils.writeAlgebraicNotation(move)).append(" ");
        }

        return sb.toString();
    }
}
