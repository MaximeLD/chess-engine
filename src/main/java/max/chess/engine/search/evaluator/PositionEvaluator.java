package max.chess.engine.search.evaluator;

import max.chess.engine.common.Position;
import max.chess.engine.game.Game;
import max.chess.engine.game.board.Board;
import max.chess.engine.movegen.MoveGenerator;
import max.chess.engine.movegen.pieces.Bishop;
import max.chess.engine.movegen.pieces.King;
import max.chess.engine.movegen.pieces.Knight;
import max.chess.engine.movegen.pieces.Rook;
import max.chess.engine.movegen.utils.OrthogonalMoveUtils;
import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;
import max.chess.engine.utils.PieceUtils;

import static max.chess.engine.search.evaluator.PieceValues.*;

public class PositionEvaluator {
    // Put these in PositionEvaluator (or a small EvalTables class).
    private static final long[] KING_RING       = new long[64];
    private static final long[] RING_DIAG_RAYS  = new long[64]; // optional prune
    private static final long[] RING_ORTHO_RAYS = new long[64]; // optional prune
    private static final long[] RING_KNIGHT_FROM= new long[64]; // optional prune

    private static final long[] STORM_R4_W = new long[64];
    private static final long[] STORM_R5_W = new long[64];
    private static final long[] STORM_R4_B = new long[64];
    private static final long[] STORM_R5_B = new long[64];
    private static final long[] ADJ_FILES  = new long[64]; // 3-file mask per square

    static final long[] FRONT_W = new long[64];
    static final long[] FRONT_B = new long[64];
    // Rank-scaled bonuses (by advancement from own side)
    // index = board rank for white (0..7), or (7-rank) for black
    static final int[] PP_MG = { 0, 0,  8, 16, 26, 40,  60, 0 };
    static final int[] PP_EG = { 0, 0, 12, 24, 40, 65, 100, 0 };

    static {
        for (int s = 0; s < 64; s++) {
            // king pawn storm cache
            int file = s & 7;      // 0..7
            int rank = s >>> 3;    // 0..7

            long fMask = 0L;
            fMask |= OrthogonalMoveUtils.FILES[file];
            if (file > 0) fMask |= OrthogonalMoveUtils.FILES[file - 1];
            if (file < 7) fMask |= OrthogonalMoveUtils.FILES[file + 1];
            ADJ_FILES[s] = fMask;

            // ---- White king: your current semantics ----
            // R4 triggers from enemy pawns on board rank 4; applicable if king rank <= 3
            // R5 triggers from enemy pawns on board rank 3; applicable if king rank <= 2
            STORM_R4_W[s] = (rank <= 3) ? (fMask & OrthogonalMoveUtils.RANKS[4]) : 0L;
            STORM_R5_W[s] = (rank <= 2) ? (fMask & OrthogonalMoveUtils.RANKS[3]) : 0L;

            // ---- Black king (symmetrical): your semantics ----
            // R4 triggers from enemy pawns on board rank 3; applicable if king rank >= 4
            // R5 triggers from enemy pawns on board rank 4; applicable if king rank >= 5
            STORM_R4_B[s] = (rank >= 4) ? (fMask & OrthogonalMoveUtils.RANKS[3]) : 0L;
            STORM_R5_B[s] = (rank >= 5) ? (fMask & OrthogonalMoveUtils.RANKS[4]) : 0L;

            // King ring pressure cache
            long ring = King.getAttackBB(s);
            KING_RING[s] = ring;

            long diag = 0L, ortho = 0L, kfrom = 0L;
            long r = ring;
            while (r != 0) {
                int rs = Long.numberOfTrailingZeros(r);
                r &= r - 1;

                // “Unblocked” rays from ring squares (occupancy = 0) build a superset mask.
                diag  |= Bishop.getAttackBB(rs, 0L);
                ortho |= Rook.getAttackBB(rs,   0L);

                // Squares from which a knight could hit any ring square
                kfrom |= Knight.getAttackBB(rs);
            }
            RING_DIAG_RAYS[s]   = diag;
            RING_ORTHO_RAYS[s]  = ortho;
            RING_KNIGHT_FROM[s] = kfrom;
        }

        // passed pawns

        for (int s = 0; s < 64; s++) {
            int f = s & 7, r = s >>> 3;
            long spanW = 0, spanB = 0;
            for (int ff = Math.max(0, f-1); ff <= Math.min(7, f+1); ff++) {
                for (int rr = r+1; rr < 8; rr++) spanW |= 1L << (rr*8 + ff);
                for (int rr = r-1; rr >=0; rr--) spanB |= 1L << (rr*8 + ff);
            }
            FRONT_W[s] = spanW;
            FRONT_B[s] = spanB;
        }
    }

    public static void warmUp() {
        // To trigger the static block
    }

    // The higher the score, the better the position
    public static int evaluatePosition(Game game) {
        int currentPlayer = game.currentPlayer;
        int oppositePlayer = ColorUtils.switchColor(currentPlayer);
        boolean isWhiteTurn = ColorUtils.isWhite(currentPlayer);

        int gameProgress256 = GamePhase.toPhase256(GamePhase.currentGameProgress(game));

        long sideBB;
        long oppositeSideBB;
        if(isWhiteTurn) {
            sideBB = game.board().whiteBB;
            oppositeSideBB = game.board().blackBB;
        } else {
            sideBB = game.board().blackBB;
            oppositeSideBB = game.board().whiteBB;
        }

//        int pieceValueScore = getPieceValueScore(sideBB, game.board());
        int pstScore = materialPlusPst(sideBB, game.board(), isWhiteTurn, gameProgress256);
        int mobilityScore = getPieceMobilityScore(sideBB, game.board(), gameProgress256);
        int castlingScore = getCastlingScore(game, currentPlayer, gameProgress256);
        int kingScore = getKingScore(game, currentPlayer, gameProgress256);
        int bishopPairScore = bishopPairScore(sideBB, game.board(), gameProgress256);
        int passedPawnScore = passedPawnScore(sideBB, game.board(), isWhiteTurn, gameProgress256);
        int tempoScore = tempoScore(currentPlayer, currentPlayer, gameProgress256);
        int playerScore = passedPawnScore + tempoScore + bishopPairScore + kingScore + castlingScore + mobilityScore + pstScore;

//        int opponentPieceValueScore = getPieceValueScore(oppositeSideBB, game.board());
        int opponentPSTScore = materialPlusPst(oppositeSideBB, game.board(), !isWhiteTurn, gameProgress256);
        int opponentMobilityScore = getPieceMobilityScore(oppositeSideBB, game.board(), gameProgress256);
        int opponentCastlingScore = getCastlingScore(game, oppositePlayer, gameProgress256);
        int opponentKingScore = getKingScore(game, oppositePlayer, gameProgress256);
        int opponentBishopPairScore = bishopPairScore(oppositeSideBB, game.board(), gameProgress256);
        int opponentTempoScore = tempoScore(currentPlayer, oppositePlayer, gameProgress256);
        int opponentPassedPawnScore = passedPawnScore(oppositeSideBB, game.board(), !isWhiteTurn, gameProgress256);
        int opponentScore = opponentPassedPawnScore + opponentTempoScore + opponentBishopPairScore + opponentKingScore + opponentCastlingScore + opponentMobilityScore + opponentPSTScore;

        int pawnsScore = PawnEval.evalPawnStructureWithHash(game.board(), gameProgress256);

        return playerScore - opponentScore + pawnsScore;
    }

    private static int materialPlusPst(long sideBB, Board b, boolean isWhite, int phase256) {
        int mg = 0, eg = 0;
        // Pawns
        long x = b.pawnBB & sideBB;
        while (x != 0) {
            int s = Long.numberOfTrailingZeros(x); x &= x - 1;
            int idx = isWhite ? s : mirrorV(s);
            mg += PAWN_VALUE + P_MG[idx];
            eg += PAWN_VALUE + P_EG[idx];
        }
        // Knights
        x = b.knightBB & sideBB;
        while (x != 0) {
            int s = Long.numberOfTrailingZeros(x); x &= x - 1;
            int idx = isWhite ? s : mirrorV(s);
            mg += KNIGHT_VALUE + N_MG[idx];
            eg += KNIGHT_VALUE + N_EG[idx];
        }
        // Bishops
        x = b.bishopBB & sideBB;
        while (x != 0) {
            int s = Long.numberOfTrailingZeros(x); x &= x - 1;
            int idx = isWhite ? s : mirrorV(s);
            mg += BISHOP_VALUE + B_MG[idx];
            eg += BISHOP_VALUE + B_EG[idx];
        }
        // Rooks
        x = b.rookBB & sideBB;
        while (x != 0) {
            int s = Long.numberOfTrailingZeros(x); x &= x - 1;
            int idx = isWhite ? s : mirrorV(s);
            mg += ROOK_VALUE + R_MG[idx];
            eg += ROOK_VALUE + R_EG[idx];
        }
        // Queens
        x = b.queenBB & sideBB;
        while (x != 0) {
            int s = Long.numberOfTrailingZeros(x); x &= x - 1;
            int idx = isWhite ? s : mirrorV(s);
            mg += QUEEN_VALUE + Q_MG[idx];
            eg += QUEEN_VALUE + Q_EG[idx];
        }
        // King (no base value added to score—mate handled by search)
        x = b.kingBB & sideBB;
        if (x != 0) {
            int s = Long.numberOfTrailingZeros(x);
            int idx = isWhite ? s : mirrorV(s);
            mg += K_MG[idx];
            eg += K_EG[idx];
        }

        int im = 256 - phase256, ie = phase256;
        return ((mg * im + eg * ie) + 128) >> 8;
    }

    private static int getPieceValueScore(long sideBB, Board board) {
        int rookValue = PieceValues.ROOK_VALUE * BitUtils.bitCount(sideBB & board.rookBB);
        int bishopValue = PieceValues.BISHOP_VALUE * BitUtils.bitCount(sideBB & board.bishopBB);
        int pawnValue = PieceValues.PAWN_VALUE * BitUtils.bitCount(sideBB & board.pawnBB);
        int queenValue = PieceValues.QUEEN_VALUE * BitUtils.bitCount(sideBB & board.queenBB);
        int knightValue = PieceValues.KNIGHT_VALUE * BitUtils.bitCount(sideBB & board.knightBB);

        return rookValue+bishopValue+pawnValue+queenValue+knightValue;
    }

    private static int getPieceMobilityScore(long sideBB, Board board, int phase256) {
        // Local copies to help JIT and avoid repeated field loads
        final long occ     = board.gameBB;
        final long targets = ~sideBB;

        // Blend weights once (phase in [0..256])
        final int im = 256 - phase256, ie = phase256;

        // Per-type blended per-square weights
        final int W_KN = ((PieceValues.Mobility.KN_MG * im + PieceValues.Mobility.KN_EG * ie) + 128) >> 8;
        final int W_BI = ((PieceValues.Mobility.BI_MG * im + PieceValues.Mobility.BI_EG * ie) + 128) >> 8;
        final int W_RO = ((PieceValues.Mobility.RO_MG * im + PieceValues.Mobility.RO_EG * ie) + 128) >> 8;
        final int W_QU = ((PieceValues.Mobility.QU_MG * im + PieceValues.Mobility.QU_EG * ie) + 128) >> 8;
        final int W_KI = ((PieceValues.Mobility.KI_MG * im + PieceValues.Mobility.KI_EG * ie) + 128) >> 8;

        // Blended floors (you had EG floor = 2/3 * MG floor)
        final int F_KN = ((PieceValues.Mobility.KN_FLOOR * im + ((2 * PieceValues.Mobility.KN_FLOOR) / 3) * ie) + 128) >> 8;
        final int F_BI = ((PieceValues.Mobility.BI_FLOOR * im + ((2 * PieceValues.Mobility.BI_FLOOR) / 3) * ie) + 128) >> 8;
        final int F_RO = ((PieceValues.Mobility.RO_FLOOR * im + ((2 * PieceValues.Mobility.RO_FLOOR) / 3) * ie) + 128) >> 8;
        if ((W_KN | W_BI | W_RO | W_QU | W_KI) == 0) return 0;

        int score = 0;

        // Knights (occupancy-independent attacks)
        long kn = sideBB & board.knightBB;
        if(kn != 0) {
            while (kn != 0) {
                final int s = Long.numberOfTrailingZeros(kn);
                kn &= kn - 1;
                final int m = Long.bitCount(Knight.getAttackBB(s) & targets);
                score += m * W_KN;
                if (m <= PieceValues.Mobility.KN_FLOOR_AT) score += F_KN;
            }
        }

        // Bishops (magics with occ)
        long bi = sideBB & board.bishopBB;
        if(bi != 0) {
            while (bi != 0) {
                final int s = Long.numberOfTrailingZeros(bi);
                bi &= bi - 1;
                final int m = Long.bitCount(Bishop.getAttackBB(s, occ) & targets);
                score += m * W_BI;
                if (m <= PieceValues.Mobility.BI_FLOOR_AT) score += F_BI;
            }
        }

        // Rooks
        long ro = sideBB & board.rookBB;
        if(ro != 0) {
            while (ro != 0) {
                final int s = Long.numberOfTrailingZeros(ro);
                ro &= ro - 1;
                final int m = Long.bitCount(Rook.getAttackBB(s, occ) & targets);
                score += m * W_RO;
                if (m <= PieceValues.Mobility.RO_FLOOR_AT) score += F_RO;
            }
        }

        // Queens (one bishop magic + one rook magic; same as before but fewer ops in the loop)
        long qu = sideBB & board.queenBB;
        if(qu != 0) {
            while (qu != 0) {
                final int s = Long.numberOfTrailingZeros(qu);
                qu &= qu - 1;
                final long att = (Bishop.getAttackBB(s, occ) | Rook.getAttackBB(s, occ)) & targets;
                score += Long.bitCount(att) * W_QU;
                // no floor for queen in your scheme
            }
        }

        // King mobility (tiny weight; still cheap)
        long ki = sideBB & board.kingBB;
        if (ki != 0) {
            final int s = Long.numberOfTrailingZeros(ki);
            score += Long.bitCount(King.getAttackBB(s) & targets) * W_KI;
        }

        return score;
    }

    private static int getPieceMobilityScoreV1(long sideBB, Board board, int gameProgress) {
        int middleGameScore = 0, endGameScore = 0;

        long rookBB = sideBB & board.rookBB;
        long bishopBB = sideBB & board.bishopBB;
        long queenBB = sideBB & board.queenBB;
        long knightBB = sideBB & board.knightBB;

        while (knightBB != 0) {
            int index = BitUtils.bitScanForward(knightBB);
            knightBB &= knightBB - 1;
            int numberOfKnightMoves = BitUtils.bitCount(Knight.getAttackBB(index) & ~sideBB);
            middleGameScore += numberOfKnightMoves * PieceValues.Mobility.KN_MG;
            endGameScore += numberOfKnightMoves * PieceValues.Mobility.KN_EG;
            if (numberOfKnightMoves <= PieceValues.Mobility.KN_FLOOR_AT) {
                middleGameScore += PieceValues.Mobility.KN_FLOOR;
                endGameScore += (2 * PieceValues.Mobility.KN_FLOOR) / 3;
            }
        }

        while (bishopBB != 0) {
            int index = BitUtils.bitScanForward(bishopBB);
            bishopBB &= bishopBB - 1;
            int numberOfBishopMoves = BitUtils.bitCount(Bishop.getAttackBB(index, board.gameBB) & ~sideBB);
            middleGameScore += numberOfBishopMoves * PieceValues.Mobility.BI_MG;
            endGameScore += numberOfBishopMoves * PieceValues.Mobility.BI_EG;
            if (numberOfBishopMoves <= PieceValues.Mobility.BI_FLOOR_AT) {
                middleGameScore += PieceValues.Mobility.BI_FLOOR;
                endGameScore += (2 * PieceValues.Mobility.BI_FLOOR) / 3;
            }
        }

        while (rookBB != 0) {
            int index = BitUtils.bitScanForward(rookBB);
            rookBB &= rookBB - 1;
            int numberOfRookMoves = BitUtils.bitCount(Rook.getAttackBB(index, board.gameBB) & ~sideBB);
            middleGameScore += numberOfRookMoves * PieceValues.Mobility.RO_MG;
            endGameScore += numberOfRookMoves * PieceValues.Mobility.RO_EG;
            if (numberOfRookMoves <= PieceValues.Mobility.RO_FLOOR_AT) {
                middleGameScore += PieceValues.Mobility.RO_FLOOR;
                endGameScore += (2 * PieceValues.Mobility.RO_FLOOR) / 3;
            }
        }

        while (queenBB != 0) {
            int index = BitUtils.bitScanForward(queenBB);
            queenBB &= queenBB - 1;
            int numberOfQueenMoves = BitUtils.bitCount((Rook.getAttackBB(index, board.gameBB) | Bishop.getAttackBB(index, board.gameBB)) & ~sideBB);
            middleGameScore += numberOfQueenMoves * PieceValues.Mobility.QU_MG;
            endGameScore += numberOfQueenMoves * PieceValues.Mobility.QU_EG;
        }

        long sideKingBB = board.kingBB & sideBB;
        if (sideKingBB != 0) {
            int index = BitUtils.bitScanForward(sideKingBB);
            int numberOfKingMoves = BitUtils.bitCount(King.getAttackBB(index) & ~sideBB);
            middleGameScore += numberOfKingMoves * PieceValues.Mobility.KI_MG;
            endGameScore += numberOfKingMoves * PieceValues.Mobility.KI_EG;
        }

        return GamePhase.blend256(middleGameScore, endGameScore, gameProgress);
    }

    private static int getCastlingScore(Game game, int color, int gameProgress) {
        // phase: 0 = midgame, 1 = endgame
        int rightsMG = 0, rightsEG = 0;

        boolean canCastleKingSide = ColorUtils.isWhite(color) ? game.whiteCanCastleKingSide : game.blackCanCastleKingSide;
        boolean canCastleQueenSide = ColorUtils.isWhite(color) ? game.whiteCanCastleQueenSide : game.blackCanCastleQueenSide;

        if (canCastleKingSide) rightsMG += 6;
        if (canCastleQueenSide)  rightsMG += 4;
        if (canCastleKingSide && canCastleQueenSide) rightsMG += 3; // flexibility

        int castledMG = 0, castledEG = 0;
        boolean castledKingSide = ColorUtils.isWhite(color) ? game.board().whiteCastledKingSide : game.board().blackCastledKingSide;
        boolean castledQueenSide = ColorUtils.isWhite(color) ? game.board().whiteCastledQueenSide : game.board().blackCastledQueenSide;

        if (castledKingSide) castledMG += 12;
        if (castledQueenSide)  castledMG += 10;

        return GamePhase.blend256(castledMG + rightsMG, castledEG + rightsEG, gameProgress);
    }

    public static int getKingScore(Game game, int color, int gameProgress) {
        Position kingPosition = Position.of(BitUtils.bitScanForward(game.board().kingBB & (ColorUtils.isWhite(color) ? game.board().whiteBB : game.board().blackBB)));
        return GamePhase.blend256(getKingSafetyScore(kingPosition, game, color), getKingEndGameScore(kingPosition), gameProgress);
    }

    public static int getKingEndGameScore(Position kingPosition) {
        return KingEndgame.CENTER_STEP * (4 - tchebychevDistanceToCenter(kingPosition));
    }

    private static int getKingSafetyScore(Position kingPosition, Game game, int color) {
        boolean isWhite = ColorUtils.isWhite(color);
        Board board = game.board();
        int score = 0;

        if(isWhite && (board.queenBB & board.blackBB) != 0 && !board.whiteCastledKingSide && !board.whiteCastledQueenSide) {
            score += KingSafety.UNCASTLED_QON;
        } else if(!isWhite && (board.queenBB & board.whiteBB) != 0 && !board.blackCastledKingSide && !board.blackCastledQueenSide) {
            score += KingSafety.UNCASTLED_QON;
        }

        score += kingPawnScore(board, kingPosition, color);

        long opponentQueenBB = game.board().queenBB & (ColorUtils.isWhite(color) ? board.blackBB : board.whiteBB);
        boolean opponentQueenLess = opponentQueenBB == 0;

        if(!opponentQueenLess) {
            score += scoreKingRingPressure(kingPosition, board, isWhite);
        }

        return score;
    }

    private static final Position E4 = Position.of(28);
    private static final Position E5 = Position.of(36);
    private static final Position D4 = Position.of(27);
    private static final Position D5 = Position.of(35);

    private static int tchebychevDistanceToCenter(Position square) {
        return Math.min(
                tchebychevDistance(square, D4),
                Math.min(tchebychevDistance(square, D5),
                Math.min(tchebychevDistance(square, E4),
                        tchebychevDistance(square, E5)))
        );
    }

    private static int tchebychevDistance(Position a, Position b) {
        return Math.max(Math.abs(a.x - b.x),  Math.abs(a.y - b.y));
    }

    private static boolean isFileOpened(Board board, int file) {
        return (board.pawnBB & OrthogonalMoveUtils.FILES[file]) == 0;
    }

    private static boolean isFileOpenedForSide(Board board, int file, int color) {
        long pawnSideBB = board.pawnBB & (ColorUtils.isWhite(color) ? board.whiteBB : board.blackBB);
        long pawnOppSideBB = board.pawnBB & (ColorUtils.isWhite(color) ? board.blackBB : board.whiteBB);
        long fileMask = OrthogonalMoveUtils.FILES[file];
        return (pawnSideBB & fileMask) == 0 && (pawnOppSideBB & fileMask) != 0;
    }

    private static final long KING_WHITE_SHELTER_BEST_CANDIDATES = OrthogonalMoveUtils.RANKS[1];
    private static final long KING_BLACK_SHELTER_BEST_CANDIDATES = OrthogonalMoveUtils.RANKS[6];
    private static final long KING_WHITE_SHELTER_CANDIDATES = OrthogonalMoveUtils.RANKS[1] | OrthogonalMoveUtils.RANKS[2];
    private static final long KING_BLACK_SHELTER_CANDIDATES = OrthogonalMoveUtils.RANKS[6] | OrthogonalMoveUtils.RANKS[5];

    private static int kingPawnScore(Board board, Position kingPosition, int color) {
        int score = 0;

        int fileLeft = kingPosition.x - 1;
        int fileRight = kingPosition.x + 1;
        int file = kingPosition.x;

        boolean isWhite = ColorUtils.isWhite(color);

        long oppPawnBB = board.pawnBB & (isWhite ? board.blackBB : board.whiteBB);

        boolean shelterApplicable = (isWhite && kingPosition.y <= 2) || (!isWhite && kingPosition.y >= 5);
        long friendlyPawns = board.pawnBB & (isWhite ? board.whiteBB : board.blackBB);

        long shelterRanks  = isWhite
                ? (OrthogonalMoveUtils.RANKS[1] | OrthogonalMoveUtils.RANKS[2])  // ranks 2–3
                : (OrthogonalMoveUtils.RANKS[6] | OrthogonalMoveUtils.RANKS[5]); // ranks 7–6

        long bestRank      = isWhite
                ? OrthogonalMoveUtils.RANKS[1]  // rank 2
                : OrthogonalMoveUtils.RANKS[6]; // rank 7

        long pawnShelterCandidates = friendlyPawns & shelterRanks;
        long pawnShelterBestCandidatesMask = bestRank;

        if(fileLeft >= 0) {
            if(isFileOpened(board, fileLeft)) {
                score += KingSafety.OPEN_ADJ;
            } else if(isFileOpenedForSide(board, fileLeft, color)) {
                score += KingSafety.HALFOPEN_ADJ;
            }

            // pawn storm
            long fileMask = OrthogonalMoveUtils.FILES[fileLeft];

            if(shelterApplicable) {
                // shelter
                boolean bestOnThisFile =
                        (fileMask & pawnShelterCandidates & pawnShelterBestCandidatesMask) != 0;
                boolean anyOnThisFile =
                        (fileMask & pawnShelterCandidates) != 0;

                if (anyOnThisFile) {
                    score += bestOnThisFile ? KingSafety.SHELTER_ADJ_R2 : KingSafety.SHELTER_ADJ_R3;
                }
            }
        }

        if(fileRight <= 7) {
            if(isFileOpened(board, fileRight)) {
                score += KingSafety.OPEN_ADJ;
            } else if(isFileOpenedForSide(board, fileRight, color)) {
                score += KingSafety.HALFOPEN_ADJ;
            }

            if(shelterApplicable) {
                // shelter
                long fileMask = OrthogonalMoveUtils.FILES[fileRight];
                boolean bestOnThisFile =
                        (fileMask & pawnShelterCandidates & pawnShelterBestCandidatesMask) != 0;
                boolean anyOnThisFile =
                        (fileMask & pawnShelterCandidates) != 0;

                if (anyOnThisFile) {
                    score += bestOnThisFile ? KingSafety.SHELTER_ADJ_R2 : KingSafety.SHELTER_ADJ_R3;
                }
            }
        }

        if(isFileOpened(board, file)) {
            score += KingSafety.OPEN_KFILE;
        } else if(isFileOpenedForSide(board, file, color)) {
            score += KingSafety.HALFOPEN_KFILE;
        }

        if(shelterApplicable) {
            // shelter
            long fileMask = OrthogonalMoveUtils.FILES[file];

            boolean bestOnThisFile =
                    (fileMask & pawnShelterCandidates & pawnShelterBestCandidatesMask) != 0;
            boolean anyOnThisFile =
                    (fileMask & pawnShelterCandidates) != 0;

            if (anyOnThisFile) {
                score += bestOnThisFile ? KingSafety.SHELTER_K_R2 : KingSafety.SHELTER_K_R3;
            } else {
                score += KingSafety.HOLE_K;
            }
        }

        // pawn storm
        score += pawnStormPenaltyFast(kingPosition.getFlatIndex(), isWhite, oppPawnBB);

        return score;
    }

    // oppPawnBB = (board.pawnBB & (isWhite ? board.blackBB : board.whiteBB))
    private static int pawnStormPenaltyFast(int kingSq, boolean isWhite, long oppPawnBB) {
        final long r4 = isWhite ? STORM_R4_W[kingSq] : STORM_R4_B[kingSq];
        final long r5 = isWhite ? STORM_R5_W[kingSq] : STORM_R5_B[kingSq];

        // Two popcounts, no branches
        final int c4 = Long.bitCount(oppPawnBB & r4);
        final int c5 = Long.bitCount(oppPawnBB & r5);

        return c4 * KingSafety.STORM_R4 + c5 * KingSafety.STORM_R5;
    }

    private static int scoreKingRingPressure(Position kingPos, Board b, boolean isWhite) {
        final int  ksq   = kingPos.getFlatIndex();
        final long ring  = KING_RING[ksq];
        if (ring == 0) return 0;

        final long occ   = b.gameBB;
        final long opp   = isWhite ? b.blackBB : b.whiteBB;

        long oppN = b.knightBB & opp;
        long oppB = b.bishopBB & opp;
        long oppR = b.rookBB   & opp;
        long oppQ = b.queenBB  & opp;

        // Union of squares that could (in principle) see the ring (precomputed once)
                long maybe =
                        (oppN & RING_KNIGHT_FROM[ksq]) |
                                ((oppB | oppQ) & RING_DIAG_RAYS[ksq]) |
                                ((oppR | oppQ) & RING_ORTHO_RAYS[ksq]);
        if (maybe == 0) return 0;   // bail out early

        // Optional cheap culls (skip whole category if impossible to ever hit the ring)
        if ((oppN & RING_KNIGHT_FROM[ksq]) == 0) oppN = 0;
        if (((oppB | oppQ) & RING_DIAG_RAYS[ksq]) == 0) { oppB = 0; /* diag part of Q handled below */ }
        if (((oppR | oppQ) & RING_ORTHO_RAYS[ksq]) == 0) { oppR = 0; /* ortho part of Q handled below */ }

        int nHits = 0, bHits = 0, rHits = 0, qHits = 0;

        // Knights: occupancy-independent — just count ring squares they attack
        while (oppN != 0) {
            int s = Long.numberOfTrailingZeros(oppN);
            oppN &= oppN - 1;
            nHits += Long.bitCount(Knight.getAttackBB(s) & ring);
        }

        // Bishops: one magic per bishop
        while (oppB != 0) {
            int s = Long.numberOfTrailingZeros(oppB);
            oppB &= oppB - 1;
            bHits += Long.bitCount(Bishop.getAttackBB(s, occ) & ring);
        }

        // Rooks: one magic per rook
        while (oppR != 0) {
            int s = Long.numberOfTrailingZeros(oppR);
            oppR &= oppR - 1;
            rHits += Long.bitCount(Rook.getAttackBB(s, occ) & ring);
        }

        // Queens: two magics per queen (or one bishop+one rook magic in your implementation)
        while (oppQ != 0) {
            int s = Long.numberOfTrailingZeros(oppQ);
            oppQ &= oppQ - 1;

            long qAtt = (Bishop.getAttackBB(s, occ) | Rook.getAttackBB(s, occ)) & ring;
            qHits += Long.bitCount(qAtt);
        }

        int score = 0;
        score += nHits * KingSafety.RING_NB;
        score += bHits * KingSafety.RING_NB;   // bishops share NB weight
        score += rHits * KingSafety.RING_R;
        score += qHits * KingSafety.RING_Q;

        // Cap (negative floor)
        return Math.max(score, KingSafety.RING_CAP);
    }

    private static int scoreKingRingPressureV2(Position kingPos, Board b, boolean isWhite) {
        final int kingSq  = kingPos.getFlatIndex();
        final long ringBB = King.getAttackBB(kingSq);           // 8 squares max
        if (ringBB == 0) return 0;

        final long occ    = b.gameBB;
        final long oppW   = isWhite ? b.blackBB : b.whiteBB;

        final long oppN   = b.knightBB & oppW;
        final long oppB   = b.bishopBB & oppW;
        final long oppR   = b.rookBB   & oppW;
        final long oppQ   = b.queenBB  & oppW;

        int nCount = 0, bCount = 0, rCount = 0, qCount = 0;

        long ring = ringBB;
        while (ring != 0) {
            final int s = Long.numberOfTrailingZeros(ring);
            ring &= ring - 1;

            // Knights: occupancy-independent. How many enemy knights attack this ring square?
            long nAttackers = Knight.getAttackBB(s) & oppN;
            nCount += Long.bitCount(nAttackers);

            // Diagonal sliders (bishops/queens): magic lookup from the RING SQUARE
            long diag = Bishop.getAttackBB(s, occ);
            long diagAttackers = diag & (oppB | oppQ);
            // separate B and Q to weight differently
            bCount += Long.bitCount(diagAttackers & oppB);
            qCount += Long.bitCount(diagAttackers & oppQ);

            // Orthogonal sliders (rooks/queens)
            long ortho = Rook.getAttackBB(s, occ);
            long orthoAttackers = ortho & (oppR | oppQ);
            rCount += Long.bitCount(orthoAttackers & oppR);
            qCount += Long.bitCount(orthoAttackers & oppQ);
        }

        int score = 0;
        score += nCount * KingSafety.RING_NB;
        score += bCount * KingSafety.RING_NB;   // bishops share NB weight
        score += rCount * KingSafety.RING_R;
        score += qCount * KingSafety.RING_Q;

        // Cap (RING_CAP is negative): clamp to at most that penalty
        return Math.max(KingSafety.RING_CAP, score);
    }

    private static int scoreKingRingPressureV1(Position kingPosition, Board board, boolean isWhite) {
        int kingIndex = kingPosition.getFlatIndex();
        int score = 0;

        long oppBishopBB = board.bishopBB & (isWhite ? board.blackBB : board.whiteBB);
        long oppKnightBB = board.knightBB & (isWhite ? board.blackBB : board.whiteBB);
        long oppRookBB = board.rookBB & (isWhite ? board.blackBB : board.whiteBB);
        long oppQueenBB = board.queenBB & (isWhite ? board.blackBB : board.whiteBB);

        long kingMaskBB = King.getAttackBB(kingIndex);

        int enemyColor = isWhite ? ColorUtils.BLACK : ColorUtils.WHITE;

        // Attack mask per piece type
        // knight - bishop
        long knightBishopAttackBB = MoveGenerator.doGetAttackBB(0, oppBishopBB, 0, oppKnightBB, 0, board.gameBB, enemyColor);
        score += BitUtils.bitCount(knightBishopAttackBB & kingMaskBB) * KingSafety.RING_NB;
        // rook
        long rookAttackBB = MoveGenerator.doGetAttackBB(0, 0, oppRookBB, 0, 0, board.gameBB, enemyColor);
        score += BitUtils.bitCount(rookAttackBB & kingMaskBB) * KingSafety.RING_R;
        // rook
        long queenAttackBB = MoveGenerator.doGetAttackBB(0, oppQueenBB, oppQueenBB, 0, 0, board.gameBB, enemyColor);
        score += BitUtils.bitCount(queenAttackBB & kingMaskBB) * KingSafety.RING_Q;

        return Math.max(KingSafety.RING_CAP, score);
    }

    public static int scorePawnStorm(Position kingPosition, int file, boolean isWhite, long oppPawnBB) {
        int score = 0;
        long fileMask = OrthogonalMoveUtils.FILES[file];

        if (isWhite) {
            // Black pawns advanced on white ranks 4/5
            boolean enemyPawnR5 = (oppPawnBB & fileMask & OrthogonalMoveUtils.RANKS[3]) != 0;
            boolean enemyPawnR4 = (oppPawnBB & fileMask & OrthogonalMoveUtils.RANKS[4]) != 0;
            if      (kingPosition.y <= 2 && enemyPawnR5) score += KingSafety.STORM_R5;
            else if (kingPosition.y <= 3 && enemyPawnR4) score += KingSafety.STORM_R4;
        } else {
            // White pawns advanced on white ranks 4/5  (same rank masks!)
            boolean enemyPawnR5 = (oppPawnBB & fileMask & OrthogonalMoveUtils.RANKS[4]) != 0;
            boolean enemyPawnR4 = (oppPawnBB & fileMask & OrthogonalMoveUtils.RANKS[3]) != 0;
            if      (kingPosition.y >= 5 && enemyPawnR5) score += KingSafety.STORM_R5;
            else if (kingPosition.y >= 4 && enemyPawnR4) score += KingSafety.STORM_R4;
        }
        return score;
    }

    private static int bishopPairScore(long sideBB, Board b, int phase256) {
        int count = Long.bitCount((b.bishopBB & sideBB));
        if (count < 2) return 0;
        final int MG = 35, EG = 20; // cp
        int im = 256 - phase256, ie = phase256;
        return ((MG*im + EG*ie) + 128) >> 8;
    }

    private static int tempoScore(int stmColor, int color, int phase256) {
        if (stmColor != color) return 0;
        final int MG = 10, EG = 0;
        int im = 256 - phase256, ie = phase256;
        return ((MG*im + EG*ie) + 128) >> 8;
    }

    private static int passedPawnScore(long sideBB, Board b, boolean isWhite, int phase256) {
        long my = b.pawnBB & sideBB;
        long opp = b.pawnBB & (isWhite ? b.blackBB : b.whiteBB);
        if (my == 0) return 0;

        int mg=0, eg=0;
        while (my != 0) {
            int s = Long.numberOfTrailingZeros(my); my &= my - 1;
            boolean passed = isWhite
                    ? (opp & FRONT_W[s]) == 0
                    : (opp & FRONT_B[s]) == 0;
            if (!passed) continue;

            int rank = s >>> 3;
            int adv  = isWhite ? rank : (7 - rank); // 0..7
            mg += PP_MG[adv];
            eg += PP_EG[adv];
        }
        int im = 256 - phase256, ie = phase256;
        return ((mg * im + eg * ie) + 128) >> 8;
    }
}
