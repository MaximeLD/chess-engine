package max.chess.engine.tb;

import max.chess.engine.tb.syzygy.SyzygyJNI;

public class TBUtils {
    // Tunable: “weight” of TB result in centipawns.
    private static final int TB_WIN_CP = 600;         // strong preference
    private static final int TB_CURSED_WIN_CP = 200;  // weaker (cursed)
    private static final int TB_LOSS_CP = -600;
    private static final int TB_BLESSED_LOSS_CP = -200;

    /** Map WDL to bounded CP, not mate. */
    public static int scoreFromWDL(int wdl) {
        return switch (wdl) {
            case SyzygyJNI.WDL_WIN          ->  TB_WIN_CP;
            case SyzygyJNI.WDL_CURSED_WIN   ->  TB_CURSED_WIN_CP;
            case SyzygyJNI.WDL_LOSS         ->  TB_LOSS_CP;
            case SyzygyJNI.WDL_BLESSED_LOSS ->  TB_BLESSED_LOSS_CP;
            default -> 0; // draw
        };
    }
}
