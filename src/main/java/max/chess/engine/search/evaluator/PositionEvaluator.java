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
    public static final long LIGHT_SQUARES = 0xAA55AA55AA55AA55L;
    public static final long CENTER_16     = 0x00003C3C3C3C0000L;

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

        // V11 extras (phase-blended inside each helper)
        int rookShape       = rookFileAnd7thScore(sideBB,          game.board(), isWhiteTurn,  gameProgress256);
        int knOutposts      = knightOutpostScore(sideBB,           game.board(), isWhiteTurn,  gameProgress256);
        int passerExtras    = passedPawnExtrasScore(sideBB,        game.board(), isWhiteTurn,  gameProgress256);

        int oppRookShape    = rookFileAnd7thScore(oppositeSideBB,  game.board(), !isWhiteTurn, gameProgress256);
        int oppKnOutposts   = knightOutpostScore(oppositeSideBB,   game.board(), !isWhiteTurn, gameProgress256);
        int oppPasserExtras = passedPawnExtrasScore(oppositeSideBB,game.board(), !isWhiteTurn, gameProgress256);

        // Batch B additions
        int bishopQuality   = bishopQualityScore(sideBB,           game.board(), isWhiteTurn,  gameProgress256);
        int diagPoke        = longDiagonalPokeScore(               game.board(), isWhiteTurn,  gameProgress256);

        int oppBishopQuality= bishopQualityScore(oppositeSideBB,   game.board(), !isWhiteTurn, gameProgress256);
        int oppDiagPoke     = longDiagonalPokeScore(               game.board(),!isWhiteTurn,  gameProgress256);

        // Batch C additions
        int spaceScoreUs    = spaceScore(                          game.board(), isWhiteTurn,  gameProgress256);
        int queen7thUs      = queen7thScore(sideBB,                game.board(), isWhiteTurn,  gameProgress256);
        int doubledRooksUs  = doubledRooksScore(sideBB,            game.board(), isWhiteTurn,  gameProgress256);
        int outsidePassUs   = outsidePasserBonus(sideBB,           game.board(), isWhiteTurn,  gameProgress256);
        int candPassUs      = candidatePassersScore(sideBB,        game.board(), isWhiteTurn,  gameProgress256);
        int egKingActUs     = endgameKingActivityBonus(            game.board(), isWhiteTurn,  gameProgress256);

        int spaceScoreOp    = spaceScore(                          game.board(), !isWhiteTurn, gameProgress256);
        int queen7thOp      = queen7thScore(oppositeSideBB,        game.board(), !isWhiteTurn, gameProgress256);
        int doubledRooksOp  = doubledRooksScore(oppositeSideBB,    game.board(), !isWhiteTurn, gameProgress256);
        int outsidePassOp   = outsidePasserBonus(oppositeSideBB,   game.board(), !isWhiteTurn, gameProgress256);
        int candPassOp      = candidatePassersScore(oppositeSideBB,game.board(), !isWhiteTurn, gameProgress256);
        int egKingActOp     = endgameKingActivityBonus(            game.board(), !isWhiteTurn, gameProgress256);

        long whiteAtt = allAttacksForSide(game.board(), true);
        long blackAtt = allAttacksForSide(game.board(), false);
        int threatDiff = simpleThreatScoreDiff(game.board(), isWhiteTurn, whiteAtt, blackAtt, gameProgress256);

        return (playerScore + rookShape + knOutposts + passerExtras
            + bishopQuality + threatDiff + diagPoke
            + spaceScoreUs + queen7thUs + doubledRooksUs + outsidePassUs + candPassUs + egKingActUs)
            - (opponentScore + oppRookShape + oppKnOutposts + oppPasserExtras
            + oppBishopQuality + oppDiagPoke
            + spaceScoreOp + queen7thOp + doubledRooksOp + outsidePassOp + candPassOp + egKingActOp)
            + pawnsScore;
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

    // Rooks: pay only for *useful* 7th and a tiny king-file alignment; no generic file bonus; cap term
    private static int rookFileAnd7thScore(long sideBB, Board b, boolean isWhite, int phase256) {
        long rooks = b.rookBB & sideBB;
        if (rooks == 0) return 0;

        long oppP = b.pawnBB & (isWhite ? b.blackBB : b.whiteBB);
        long oppKBB = b.kingBB & (isWhite ? b.blackBB : b.whiteBB);
        int oppKingSq   = (oppKBB != 0) ? Long.numberOfTrailingZeros(oppKBB) : 60;
        int oppKingFile = oppKingSq & 7;

        int mg = 0, eg = 0;
        while (rooks != 0) {
            int s = Long.numberOfTrailingZeros(rooks); rooks &= rooks - 1;
            int file = s & 7;
            int rank = s >>> 3;

            // 7th rank: require target on 7th or real activity along the rank
            boolean on7 = isWhite ? (rank == 6) : (rank == 1);
            if (on7) {
                long rankMask = OrthogonalMoveUtils.RANKS[isWhite ? 6 : 1];
                boolean target = ((oppP & rankMask) != 0) ||
                    ((b.kingBB & (isWhite ? b.blackBB : b.whiteBB) & rankMask) != 0);
                long attOnRank = Rook.getAttackBB(s, b.gameBB) & rankMask;
                boolean activeOn7 = Long.bitCount(attOnRank) >= 2;

                if (target || activeOn7) {
                    mg += 2 + (target ? 2 : 0);
                    eg += 10 + (target ? 6 : 0);
                }
            }

            // Tiny nudge if close to king file (prevents overeager hunts)
            if (Math.abs(file - oppKingFile) <= 1) {
                mg += 1;
                eg += 2;
            }
        }

        // Cap per-side to avoid stacking (conservative)
        mg = Math.min(mg, 16);
        eg = Math.min(eg, 24);

        int im = 256 - phase256, ie = phase256;
        return ((mg * im + eg * ie) + 128) >> 8;
    }

    // Knight outposts: no enemy pawn on adjacent files that can ever attack the square (future presence), bigger if pawn-supported
    private static int knightOutpostScore(long sideBB, Board b, boolean isWhite, int phase256) {
        long kn = b.knightBB & sideBB;
        if (kn == 0) return 0;

        long myP  = b.pawnBB & sideBB;
        long oppP = b.pawnBB & (isWhite ? b.blackBB : b.whiteBB);

        int mg = 0, eg = 0;
        while (kn != 0) {
            int s = Long.numberOfTrailingZeros(kn); kn &= kn - 1;
            int f = s & 7, r = s >>> 3;

            // Future enemy pawns on adjacent files "ahead" can eventually attack this square -> not an outpost
            long adjFiles = 0L;
            if (f > 0) adjFiles |= OrthogonalMoveUtils.FILES[f - 1];
            if (f < 7) adjFiles |= OrthogonalMoveUtils.FILES[f + 1];

            long ahead;
            if (isWhite) {
                // Guard r==7 to avoid << 64 (which becomes << 0 on longs)
                long ranksAhead = (r == 7) ? 0L : (~0L << ((r + 1) * 8));  // ranks r+1..7
                ahead = oppP & adjFiles & ranksAhead;
            } else {
                // Black pawns move toward lower ranks; ranks 0..r-1 are "ahead"
                long ranksAhead = (r == 0) ? 0L : ((1L << (r * 8)) - 1);   // ranks 0..r-1
                ahead = oppP & adjFiles & ranksAhead;
            }
            if (ahead != 0) continue; // enemy pawn could eventually attack -> not a true outpost

            // pawn-supported required for payout
            boolean supported;
            if (isWhite) {
                long sup = 0L;
                if (f > 0 && s - 9 >= 0) sup |= 1L << (s - 9);
                if (f < 7 && s - 7 >= 0) sup |= 1L << (s - 7);
                supported = (myP & sup) != 0;
            } else {
                long sup = 0L;
                if (f > 0 && s + 7 <= 63) sup |= 1L << (s + 7);
                if (f < 7 && s + 9 <= 63) sup |= 1L << (s + 9);
                supported = (myP & sup) != 0;
            }
            if (!supported) continue; // no free lunch: unsupported "outposts" don't score

            int adv  = isWhite ? r : (7 - r);
            int tier = (adv >= 5) ? 1 : (adv >= 4 ? 1 : 0);
            int edgePenalty = (f == 0 || f == 7) ? 1 : 0;

            mg += 3 + tier * 2 - edgePenalty; // slightly calmer than before
            eg += 2 + tier * 2 - edgePenalty;
        }

        int im = 256 - phase256, ie = phase256;
        return ((mg * im + eg * ie) + 128) >> 8;
    }


    // Passed-pawn extras: rook behind, king race, connected passers
// Passed-pawn extras: tiny, EG-weighted; avoid overlap with phalanx/support
    private static int passedPawnExtrasScore(long sideBB, Board b, boolean isWhite, int phase256) {
        long myP = b.pawnBB & sideBB;
        if (myP == 0) return 0;

        long oppBB = isWhite ? b.blackBB : b.whiteBB;
        long myR   = b.rookBB & sideBB;

        long myKBB = b.kingBB & sideBB;
        long opKBB = b.kingBB & oppBB;
        int myK = (myKBB != 0) ? Long.numberOfTrailingZeros(myKBB) : -1;
        int opK = (opKBB != 0) ? Long.numberOfTrailingZeros(opKBB) : -1;

        int mg = 0, eg = 0;
        long passers = 0L;

        // collect passers
        for (long x = myP; x != 0; x &= x - 1) {
            int s = Long.numberOfTrailingZeros(x);
            boolean passed = isWhite
                ? ((b.pawnBB & oppBB & FRONT_W[s]) == 0)
                : ((b.pawnBB & oppBB & FRONT_B[s]) == 0);
            if (passed) passers |= 1L << s;
        }
        if (passers == 0) return 0;

        long x = passers;
        while (x != 0) {
            int s = Long.numberOfTrailingZeros(x); x &= x - 1;
            int f = s & 7, r = s >>> 3;

            // rook behind passer with clear path (EG-only)
            if (myR != 0) {
                for (long rr = myR; rr != 0; rr &= rr - 1) {
                    int rsq = Long.numberOfTrailingZeros(rr);
                    if ((rsq & 7) != f) continue;
                    boolean behind = isWhite ? (rsq < s) : (rsq > s);
                    if (!behind) continue;
                    long between = max.chess.engine.movegen.utils.ObstructedLinesUtils.OBSTRUCTED_BB[rsq][s];
                    if ((between & b.gameBB) == 0) { /* mg += 0; */ eg += 5; break; }
                }
            }

            // king race (EG only, 1 cp per square)
            if (myK >= 0 && opK >= 0) {
                int diff = tchebychevDistance(Position.of(opK), Position.of(s)) - tchebychevDistance(Position.of(myK), Position.of(s));
                if (diff > 0) eg += Math.min(diff, 4); // EG-only, cap small
            }

            // connected passers only (both are passed) to avoid overlap with general phalanx bonus
            long adjFiles = 0L;
            if (f > 0) adjFiles |= OrthogonalMoveUtils.FILES[f - 1];
            if (f < 7) adjFiles |= OrthogonalMoveUtils.FILES[f + 1];

            boolean connSame = (passers & adjFiles & OrthogonalMoveUtils.RANKS[r]) != 0;
            boolean connStep = isWhite
                ? (r < 7 && (passers & adjFiles & OrthogonalMoveUtils.RANKS[r + 1]) != 0)
                : (r > 0 && (passers & adjFiles & OrthogonalMoveUtils.RANKS[r - 1]) != 0);
            if (connSame || connStep) { /* mg += 0; */ eg += 4; }
        }

        eg = Math.min(eg, 14);
        int im = 256 - phase256, ie = phase256;
        return ((mg * im + eg * ie) + 128) >> 8;
    }
    // --------------------- Batch B helpers ---------------------

    // Bishop quality: (a) bad bishop penalty by own pawns on bishop color; (b) "good" bishop on long diagonals.
// Conservative, phase-blended, capped.
    private static int bishopQualityScore(long sideBB, Board b, boolean isWhite, int phase256) {
        long myP = b.pawnBB & sideBB;
        long myB = b.bishopBB & sideBB;
        if (myB == 0) return 0;

        // Count our pawns on light vs dark squares
        final long LIGHT = LIGHT_SQUARES;
        final long DARK  = ~LIGHT;

        int mg = 0, eg = 0;

        long bb = myB;
        while (bb != 0) {
            int s = Long.numberOfTrailingZeros(bb); bb &= bb - 1;

            // compute bishop reach with and without our own pawns on the board
            long occAll     = b.gameBB;
            long occNoOwnP  = occAll & ~(myP);  // strip our pawns only

            long attAll     = Bishop.getAttackBB(s, occAll);
            long attNoOwn   = Bishop.getAttackBB(s, occNoOwnP);

            // own-pawn blockage = extra squares we'd get if our pawns vanished
            int ownBlockage = Math.max(0, Long.bitCount(attNoOwn) - Long.bitCount(attAll));

            // penalty for own-pawn blockage (tiny)
            mg -= 2 * ownBlockage;
            eg -= 1 * ownBlockage;

            // good-bishop heuristic: long, clean diagonals
            int reach = Long.bitCount(attAll);
            if (reach >= 8) { mg += 2; eg += 3; }
            else if (reach >= 6) { mg += 1; eg += 2; }

            // small extra if bishop eyes central squares
            if ((attAll & CENTER_16) != 0) { mg += 1; eg += 1; }

        }

        // cap so two bishops don’t run away with it
        mg = Math.max(-24, Math.min(24, mg));
        eg = Math.max(-16, Math.min(32, eg));

        int im = 256 - phase256, ie = phase256;
        return ((mg * im + eg * ie) + 128) >> 8;
    }

    // Simple threats: bonus if we attack an undefended enemy piece; penalty if ours are attacked and undefended.
// Tiny, capped, king excluded.
    private static int simpleThreatScore(Game game, boolean forWhite, int phase256) {
        Board b = game.board();
        long sideBB = forWhite ? b.whiteBB : b.blackBB;
        long oppBB  = forWhite ? b.blackBB : b.whiteBB;

        long occ = b.gameBB;

        // All-attacks maps (fast unions; no SEE)
        long ourAtt = attacksForSide(b, forWhite, occ);
        long oppAtt = attacksForSide(b, !forWhite, occ);

        int mg = 0, eg = 0;

        // Our pieces hanging?
        long ours = sideBB & ~b.kingBB;
        while (ours != 0) {
            int s = Long.numberOfTrailingZeros(ours); ours &= ours - 1;
            long sq = 1L << s;
            boolean attacked = (oppAtt & sq) != 0;
            boolean defended = (ourAtt & sq) != 0;
            if (attacked && !defended) {
                int w = pieceBucketPenalty(b, s); // tiny per type
                mg -= w; eg -= (w >> 1);
            }
        }

        // Their pieces hanging?
        long theirs = oppBB & ~b.kingBB;
        while (theirs != 0) {
            int s = Long.numberOfTrailingZeros(theirs); theirs &= theirs - 1;
            long sq = 1L << s;
            boolean attacked = (ourAtt & sq) != 0;
            boolean defended = (oppAtt & sq) != 0;
            if (attacked && !defended) {
                int w = pieceBucketPenalty(b, s);
                mg += w; eg += (w >> 1);
            }
        }

        // cap to avoid noise
        mg = Math.max(-24, Math.min(24, mg));
        eg = Math.max(-16, Math.min(16, eg));

        int im = 256 - phase256, ie = phase256;
        return ((mg * im + eg * ie) + 128) >> 8;
    }

    // Long-diagonal poke at king: bishop/queen aligned with enemy king with <=1 blocker on a diagonal.
// Very small; aims to reward latent mating geometry.
    private static int longDiagonalPokeScore(Board b, boolean forWhite, int phase256) {
        long sideBB = forWhite ? b.whiteBB : b.blackBB;
        long oppBB  = forWhite ? b.blackBB : b.whiteBB;
        long occ    = b.gameBB;

        long oppKBB = b.kingBB & oppBB;
        if (oppKBB == 0) return 0;
        int ksq = Long.numberOfTrailingZeros(oppKBB);

        long bishops = (b.bishopBB | b.queenBB) & sideBB;
        if (bishops == 0) return 0;

        int mg = 0, eg = 0;
        long bb = bishops;
        while (bb != 0) {
            int s = Long.numberOfTrailingZeros(bb); bb &= bb - 1;

            // Check if s and ksq are on same diagonal: difference abs(x1-x2) == abs(y1-y2)
            int sx = s & 7, sy = s >>> 3, kx = ksq & 7, ky = ksq >>> 3;
            if (Math.abs(sx - kx) != Math.abs(sy - ky)) continue;

            long between = max.chess.engine.movegen.utils.ObstructedLinesUtils.OBSTRUCTED_BB[s][ksq];
            int blockers = Long.bitCount(between & occ);
            if (blockers <= 1) {
                mg += 1;  // minuscule
                eg += 2;
            }
        }

        mg = Math.min(mg, 4);
        eg = Math.min(eg, 6);

        int im = 256 - phase256, ie = phase256;
        return ((mg * im + eg * ie) + 128) >> 8;
    }

// Utilities -------------------------------------------------

    // Union of attacks for one side given current occupancy
    private static long attacksForSide(Board b, boolean forWhite, long occ) {
        long side = forWhite ? b.whiteBB : b.blackBB;
        long att = 0L;

        // Pawns
        long pawns = b.pawnBB & side;
        if (forWhite) {
            long left  = (pawns << 7) & ~OrthogonalMoveUtils.FILES[7];
            long right = (pawns << 9) & ~OrthogonalMoveUtils.FILES[0];
            att |= left | right;
        } else {
            long left  = (pawns >>> 9) & ~OrthogonalMoveUtils.FILES[7];
            long right = (pawns >>> 7) & ~OrthogonalMoveUtils.FILES[0];
            att |= left | right;
        }

        // Knights
        for (long x = (b.knightBB & side); x != 0; x &= x - 1) {
            int s = Long.numberOfTrailingZeros(x);
            att |= Knight.getAttackBB(s);
        }

        // Bishops and Queens (diagonals)
        long diag = (b.bishopBB | b.queenBB) & side;
        for (long x = diag; x != 0; x &= x - 1) {
            int s = Long.numberOfTrailingZeros(x);
            att |= Bishop.getAttackBB(s, occ);
        }

        // Rooks and Queens (orthogonals)
        long ortho = (b.rookBB | b.queenBB) & side;
        for (long x = ortho; x != 0; x &= x - 1) {
            int s = Long.numberOfTrailingZeros(x);
            att |= Rook.getAttackBB(s, occ);
        }

        // King
        for (long x = (b.kingBB & side); x != 0; x &= x - 1) {
            int s = Long.numberOfTrailingZeros(x);
            att |= King.getAttackBB(s);
        }

        return att;
    }

    // Tiny threat bucket per piece type (don’t overfit to material values)
    private static int pieceBucketPenalty(Board b, int sq) {
        long bit = 1L << sq;
        if ((b.pawnBB & bit) != 0)   return 2;
        if ((b.knightBB & bit) != 0) return 3;
        if ((b.bishopBB & bit) != 0) return 3;
        if ((b.rookBB & bit) != 0)   return 4;
        if ((b.queenBB & bit) != 0)  return 5;
        return 0; // king excluded
    }
    // Compute union of attacks for one side (allocation-free)
    private static long allAttacksForSide(Board b, boolean forWhite) {
        long side = forWhite ? b.whiteBB : b.blackBB;
        long occ  = b.gameBB;
        long att  = 0L;

        // Pawns
        long pawns = b.pawnBB & side;
        if (forWhite) {
            att |= ((pawns << 7) & ~OrthogonalMoveUtils.FILES[7])
                | ((pawns << 9) & ~OrthogonalMoveUtils.FILES[0]);
        } else {
            att |= ((pawns >>> 9) & ~OrthogonalMoveUtils.FILES[7])
                | ((pawns >>> 7) & ~OrthogonalMoveUtils.FILES[0]);
        }

        // Knights
        for (long x = (b.knightBB & side); x != 0; x &= x - 1) {
            att |= Knight.getAttackBB(Long.numberOfTrailingZeros(x));
        }
        // Bishops & Queens (diagonals)
        for (long x = ((b.bishopBB | b.queenBB) & side); x != 0; x &= x - 1) {
            att |= Bishop.getAttackBB(Long.numberOfTrailingZeros(x), occ);
        }
        // Rooks & Queens (orthogonals)
        for (long x = ((b.rookBB | b.queenBB) & side); x != 0; x &= x - 1) {
            att |= Rook.getAttackBB(Long.numberOfTrailingZeros(x), occ);
        }
        // King
        for (long x = (b.kingBB & side); x != 0; x &= x - 1) {
            att |= King.getAttackBB(Long.numberOfTrailingZeros(x));
        }
        return att;
    }

    // Threat difference: (our undefended hits - their undefended hits), tiny/capped, skip deep EG
    private static int simpleThreatScoreDiff(Board b, boolean usWhite, long whiteAtt, long blackAtt, int phase256) {
        if (phase256 >= 208) return 0; // deep EG: threats mostly irrelevant, skip entirely

        long ourSide = usWhite ? b.whiteBB : b.blackBB;
        long oppSide = usWhite ? b.blackBB : b.whiteBB;

        long ourAtt = usWhite ? whiteAtt : blackAtt;
        long oppAtt = usWhite ? blackAtt : whiteAtt;

        int mg = 0, eg = 0;

        // Our pieces hanging (excluding king)
        for (long x = (ourSide & ~b.kingBB); x != 0; x &= x - 1) {
            int s = Long.numberOfTrailingZeros(x);
            long sq = 1L << s;
            if ((oppAtt & sq) != 0 && (ourAtt & sq) == 0) {
                int w = pieceBucketPenalty(b, s);
                mg -= w; eg -= (w >> 1);
            }
        }

        // Their pieces hanging (excluding king)
        for (long x = (oppSide & ~b.kingBB); x != 0; x &= x - 1) {
            int s = Long.numberOfTrailingZeros(x);
            long sq = 1L << s;
            if ((ourAtt & sq) != 0 && (oppAtt & sq) == 0) {
                int w = pieceBucketPenalty(b, s);
                mg += w; eg += (w >> 1);
            }
        }

        // caps
        mg = Math.max(-20, Math.min(20, mg));
        eg = Math.max(-12, Math.min(12, eg));

        int im = 256 - phase256, ie = phase256;
        return ((mg * im + eg * ie) + 128) >> 8;
    }
// --------------------- Batch C helpers ---------------------

    // Space / territory (MG-only): our controlled, safe, empty squares in opponent's half.
// Uses union-of-attacks minus enemy pawn attacks; tight cap.
    private static int spaceScore(Board b, boolean forWhite, int phase256) {
        if (phase256 >= 192) return 0; // MG-oriented

        long occ = b.gameBB;
        long ourAtt = allAttacksForSide(b, forWhite);
        long oppPawns = b.pawnBB & (forWhite ? b.blackBB : b.whiteBB);

        // squares attacked by opponent pawns (unsafe to occupy)
        long oppPawnAtt;
        if (forWhite) {
            oppPawnAtt = ((oppPawns >>> 9) & ~OrthogonalMoveUtils.FILES[7])
                | ((oppPawns >>> 7) & ~OrthogonalMoveUtils.FILES[0]);
        } else {
            oppPawnAtt = ((oppPawns << 7) & ~OrthogonalMoveUtils.FILES[7])
                | ((oppPawns << 9) & ~OrthogonalMoveUtils.FILES[0]);
        }

        // Opponent half
        long oppHalf = 0L;
        if (forWhite) {
            // ranks 4..7
            oppHalf = OrthogonalMoveUtils.RANKS[4] | OrthogonalMoveUtils.RANKS[5]
                | OrthogonalMoveUtils.RANKS[6] | OrthogonalMoveUtils.RANKS[7];
        } else {
            // ranks 0..3
            oppHalf = OrthogonalMoveUtils.RANKS[0] | OrthogonalMoveUtils.RANKS[1]
                | OrthogonalMoveUtils.RANKS[2] | OrthogonalMoveUtils.RANKS[3];
        }

        long safeEmptyControlled = ourAtt & ~oppPawnAtt & oppHalf & ~occ;
        int count = Long.bitCount(safeEmptyControlled);

        // 1 cp per 2 squares, cap 24
        int mg = Math.min(24, count / 2);
        int eg = 0;

        int im = 256 - phase256, ie = phase256;
        return ((mg * im + eg * ie) + 128) >> 8;
    }

    // Queen on 7th rank (useful only): target on 7th or active along the rank. Modest.
    private static int queen7thScore(long sideBB, Board b, boolean isWhite, int phase256) {
        long q = b.queenBB & sideBB;
        if (q == 0) return 0;

        int mg = 0, eg = 0;
        long opp = isWhite ? b.blackBB : b.whiteBB;
        long oppP = b.pawnBB & opp;
        int targetRank = isWhite ? 6 : 1;
        long rankMask  = OrthogonalMoveUtils.RANKS[targetRank];

        for (long x = q; x != 0; x &= x - 1) {
            int s = Long.numberOfTrailingZeros(x);
            int rank = s >>> 3;
            if (rank != targetRank) continue;

            boolean target = ((oppP & rankMask) != 0) || ((b.kingBB & opp & rankMask) != 0);
            long attOnRank = (Bishop.getAttackBB(s, b.gameBB) | Rook.getAttackBB(s, b.gameBB)) & rankMask;
            boolean active = Long.bitCount(attOnRank) >= 2;

            if (target || active) {
                mg += 1 + (target ? 1 : 0);
                eg += 8 + (target ? 4 : 0);
            }
        }
        mg = Math.min(mg, 8);
        eg = Math.min(eg, 16);

        int im = 256 - phase256, ie = phase256;
        return ((mg * im + eg * ie) + 128) >> 8;
    }

    // Doubled rooks on semi-open/open file: tiny structural bonus, phase-blended, capped.
    private static int doubledRooksScore(long sideBB, Board b, boolean isWhite, int phase256) {
        long rooks = b.rookBB & sideBB;
        if (Long.bitCount(rooks) < 2) return 0;

        long myP  = b.pawnBB & sideBB;
        long oppP = b.pawnBB & (isWhite ? b.blackBB : b.whiteBB);

        int mg = 0, eg = 0;
        for (int file = 0; file < 8; file++) {
            long fMask = OrthogonalMoveUtils.FILES[file];
            int rCount = Long.bitCount(rooks & fMask);
            if (rCount < 2) continue;

            boolean open = ((myP | oppP) & fMask) == 0;
            boolean semi = !open && (myP & fMask) == 0;
            if (open || semi) { mg += 2; eg += 4; }
        }

        mg = Math.min(mg, 6);
        eg = Math.min(eg, 10);

        int im = 256 - phase256, ie = phase256;
        return ((mg * im + eg * ie) + 128) >> 8;
    }

    // Outside passer (EG-only): passer sits outside all our other pawns (leftmost or rightmost file).
    private static int outsidePasserBonus(long sideBB, Board b, boolean isWhite, int phase256) {
        long myP = b.pawnBB & sideBB;
        if (myP == 0) return 0;

        long opp = isWhite ? b.blackBB : b.whiteBB;
        long passers = 0L;

        // Detect passers
        for (long x = myP; x != 0; x &= x - 1) {
            int s = Long.numberOfTrailingZeros(x);
            boolean passed = isWhite
                ? ((b.pawnBB & opp & FRONT_W[s]) == 0)
                : ((b.pawnBB & opp & FRONT_B[s]) == 0);
            if (passed) passers |= 1L << s;
        }
        if (passers == 0) return 0;

        int eg = 0;
        long others = myP;
        for (long p = passers; p != 0; p &= p - 1) {
            int s = Long.numberOfTrailingZeros(p);
            int f = s & 7;

            long rest = others & ~(1L << s);
            if (rest == 0) continue; // lone pawn: ignore

            // Find min/max file among our other pawns
            int minF = 7, maxF = 0;
            for (long r = rest; r != 0; r &= r - 1) {
                int rs = Long.numberOfTrailingZeros(r);
                int rf = rs & 7;
                if (rf < minF) minF = rf;
                if (rf > maxF) maxF = rf;
            }
            boolean outside = f < minF || f > maxF;
            if (outside) eg += 6;
        }

        eg = Math.min(eg, 12);
        int im = 256 - phase256, ie = phase256;
        return ((0 * im + eg * ie) + 128) >> 8;
    }

    // Candidate passers (MG-only): near-passers with no opposing pawn in front on same file and at most one on adjacent files ahead.
    private static int candidatePassersScore(long sideBB, Board b, boolean isWhite, int phase256) {
        if (phase256 >= 192) return 0;
        long myP  = b.pawnBB & sideBB;
        long oppP = b.pawnBB & (isWhite ? b.blackBB : b.whiteBB);
        if (myP == 0) return 0;

        int mg = 0;
        for (long x = myP; x != 0; x &= x - 1) {
            int s = Long.numberOfTrailingZeros(x);
            int f = s & 7, r = s >>> 3;

            // Not already passed
            boolean passed = isWhite
                ? ((oppP & FRONT_W[s]) == 0)
                : ((oppP & FRONT_B[s]) == 0);
            if (passed) continue;

            // Same-file enemy pawns ahead?
            long aheadSameFile = isWhite
                ? ((r == 7) ? 0L : (~0L << ((r + 1) * 8)))
                : ((r == 0) ? 0L : ((1L << (r * 8)) - 1));
            boolean sameFileBlock = (oppP & OrthogonalMoveUtils.FILES[f] & aheadSameFile) != 0;
            if (sameFileBlock) continue;

            // At most one enemy pawn on adjacent files ahead
            long adjFiles = 0L;
            if (f > 0) adjFiles |= OrthogonalMoveUtils.FILES[f - 1];
            if (f < 7) adjFiles |= OrthogonalMoveUtils.FILES[f + 1];
            long ahead = isWhite
                ? ((r == 7) ? 0L : (~0L << ((r + 1) * 8)))
                : ((r == 0) ? 0L : ((1L << (r * 8)) - 1));
            int adjCount = Long.bitCount(oppP & adjFiles & ahead);
            if (adjCount <= 1) mg += 3;  // tiny
        }

        mg = Math.min(mg, 12);
        int im = 256 - phase256, ie = phase256;
        return ((mg * im + 0 * ie) + 128) >> 8;
    }

    // Endgame king activity (EG-only): reward being closer to center than opponent, without double-counting your king EG term.
    private static int endgameKingActivityBonus(Board b, boolean usWhite, int phase256) {
        if (phase256 < 192) return 0;
        if (b.queenBB != 0) return 0; // only when queens are off

        long ourK = b.kingBB & (usWhite ? b.whiteBB : b.blackBB);
        long opK  = b.kingBB & (usWhite ? b.blackBB : b.whiteBB);
        if (ourK == 0 || opK == 0) return 0;

        int us   = Long.numberOfTrailingZeros(ourK);
        int them = Long.numberOfTrailingZeros(opK);

        // Distance to center via Chebyshev (4 central squares)
        int usD = chebToCenter(us);
        int thD = chebToCenter(them);
        int diff = thD - usD; // >0 if we’re closer

        int eg = Math.max(0, Math.min(6, diff)); // 1 cp per step, up to +6
        return ((0 * (256 - phase256) + eg * phase256) + 128) >> 8;
    }

    private static int chebToCenter(int sq) {
        int x = sq & 7, y = sq >>> 3;
        // closest of d4,e4,d5,e5 (27,28,35,36)
        int d4 = Math.max(Math.abs(x - 3), Math.abs(y - 3));
        int e4 = Math.max(Math.abs(x - 4), Math.abs(y - 3));
        int d5 = Math.max(Math.abs(x - 3), Math.abs(y - 4));
        int e5 = Math.max(Math.abs(x - 4), Math.abs(y - 4));
        int m1 = Math.min(d4, e4), m2 = Math.min(d5, e5);
        return Math.min(m1, m2);
    }

}
