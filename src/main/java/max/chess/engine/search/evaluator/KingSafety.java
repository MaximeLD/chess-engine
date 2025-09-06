package max.chess.engine.search.evaluator;

// Middlegame weights (apply * (1 - gameProgress))
public final class KingSafety {
  public static final int OPEN_KFILE      = -25;
  public static final int HALFOPEN_KFILE  = -12;
  public static final int OPEN_ADJ        = -10;
  public static final int HALFOPEN_ADJ    = -5;

  public static final int SHELTER_K_R2    = +12; // friendly pawn on home rank on king file
  public static final int SHELTER_K_R3    = +6;
  public static final int SHELTER_ADJ_R2  = +8;
  public static final int SHELTER_ADJ_R3  = +4;
  public static final int HOLE_K          = -6;  // no pawn up to rank3/6 on king file
  public static final int FIANCHETTO_BONUS= +6;

  public static final int STORM_R4        = -8;  // enemy pawn on rank 4 (from enemy side)
  public static final int STORM_R5        = -12; // enemy pawn on rank 5

  public static final int RING_NB         = -3;  // per attack on king ring by N/B
  public static final int RING_R          = -5;  // per attack on king ring by R
  public static final int RING_Q          = -9;  // per attack on king ring by Q
  public static final int RING_CAP        = -60; // cap

  public static final int CASTLED         = +10; // small
  public static final int UNCASTLED_QON   = -20; // only if queens on board
}
