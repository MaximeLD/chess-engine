package max.chess.engine.search.evaluator;

import max.chess.engine.game.board.Board;
import max.chess.engine.movegen.utils.OrthogonalMoveUtils;

public final class PawnEval {
    public static final PawnHash PAWN_HASH = new PawnHash(1 << 16); // 65k buckets * 2-way ≈ 131k entries

    public static void newSearch() { PAWN_HASH.newSearch(); } // call at iterative-deepening root
    public static void clearPawnHash() { PAWN_HASH.clear(); }

    private static final long[] PASSED_W = new long[64];
    private static final long[] PASSED_B = new long[64];

    static {
        // Build passed masks
        for (int s=0; s<64; s++) {
            int f = s & 7, r = s >>> 3;
            long files = OrthogonalMoveUtils.FILES[f];
            if (f>0) files |= OrthogonalMoveUtils.FILES[f-1];
            if (f<7) files |= OrthogonalMoveUtils.FILES[f+1];

            long aheadW = files & (~0L << ((r+1)*8));      // squares strictly ahead for white
//            long aheadB = files & (~northFill(~0L) >>> ((7-r+1)*8)); // or simply files & ((1L<< (r*8)) - 1)

            // Easier: for black, “in front” is towards south:
            long aheadB2 = files & ((1L << (r*8)) - 1);

            PASSED_W[s] = aheadW;
            PASSED_B[s] = aheadB2;
        }
    }

    public static int evalPawnStructureWithHash(Board b, int phase256) {
        final long wp = b.pawnBB & b.whiteBB;
        final long bp = b.pawnBB & b.blackBB;
        if ((wp | bp) == 0) return 0;

        final PawnHash.Hit hit = new PawnHash.Hit();
        if (PAWN_HASH.probe(wp, bp, hit)) {
            // Add blocked-passed using CURRENT occupancy
            int blkMG = 0, blkEG = 0;
            final long occ = b.gameBB;
            int wBlocked = Long.bitCount((hit.wPassed << 8) & occ);
            int bBlocked = Long.bitCount((hit.bPassed >>> 8) & occ);
            // Your weights:
            blkMG += (wBlocked - bBlocked) * (-6);
            blkEG += (wBlocked - bBlocked) * (-14);

            int im = 256 - phase256, ie = phase256;
            int mg = hit.mgDiff + blkMG;
            int eg = hit.egDiff + blkEG;
            return ((mg * im + eg * ie) + 128) >> 8;
        }

        // Miss: compute pawn-only, store, then add blocked penalty
        PawnOnly eval = computePawnOnly(b, wp, bp); // see below
        PAWN_HASH.store(wp, bp, eval.mgDiff, eval.egDiff, eval.wPassed, eval.bPassed);

        final long occ = b.gameBB;
        int wBlocked = Long.bitCount((eval.wPassed << 8) & occ);
        int bBlocked = Long.bitCount((eval.bPassed >>> 8) & occ);
        int blkMG = (wBlocked - bBlocked) * (-6);
        int blkEG = (wBlocked - bBlocked) * (-14);

        int im = 256 - phase256, ie = phase256;
        int mg = eval.mgDiff + blkMG;
        int eg = eval.egDiff + blkEG;
        return ((mg * im + eg * ie) + 128) >> 8;
    }

    private static final class PawnOnly {
        int mgDiff, egDiff;
        long wPassed, bPassed;
    }

    // EXACTLY your earlier pawn-only logic, but **no occ usage** and **no blocked-passed** inside.
    private static PawnOnly computePawnOnly(Board b, long wp, long bp) {
        PawnOnly out = new PawnOnly();

        // Supported / phalanx (pawn-only)
        long wAtt = ((wp & ~OrthogonalMoveUtils.FILES[0]) << 7) | ((wp & ~OrthogonalMoveUtils.FILES[7]) << 9);
        long bAtt = ((bp & ~OrthogonalMoveUtils.FILES[7]) >>> 7) | ((bp & ~OrthogonalMoveUtils.FILES[0]) >>> 9);
        long wSup = wp & wAtt, bSup = bp & bAtt;
        long wPhl = wp & ( ((wp & ~OrthogonalMoveUtils.FILES[0]) >>> 1) | ((wp & ~OrthogonalMoveUtils.FILES[7]) << 1) );
        long bPhl = bp & ( ((bp & ~OrthogonalMoveUtils.FILES[0]) >>> 1) | ((bp & ~OrthogonalMoveUtils.FILES[7]) << 1) );

        // Isolated / doubled / islands (per-file loop)
        int isoW=0, isoB=0, dblW=0, dblB=0, islW=0, islB=0, wm=0, bm=0;
        for (int f=0; f<8; f++) {
            long F = OrthogonalMoveUtils.FILES[f];
            long wF = wp & F, bF = bp & F;
            if (wF!=0) wm |= (1<<f);
            if (bF!=0) bm |= (1<<f);

            int cw = Long.bitCount(wF), cb = Long.bitCount(bF);
            if (cw>1) dblW += (cw-1);
            if (cb>1) dblB += (cb-1);

            boolean wL  = (f>0) && ((wp & OrthogonalMoveUtils.FILES[f-1]) != 0);
            boolean wR  = (f<7) && ((wp & OrthogonalMoveUtils.FILES[f+1]) != 0);
            if (cw>0 && !wL && !wR) isoW += cw;

            boolean bL  = (f>0) && ((bp & OrthogonalMoveUtils.FILES[f-1]) != 0);
            boolean bR  = (f<7) && ((bp & OrthogonalMoveUtils.FILES[f+1]) != 0);
            if (cb>0 && !bL && !bR) isoB += cb;
        }
        islW = Integer.bitCount((wm & ~(wm<<1)) & 0xFF); islW = Math.max(0, islW-1);
        islB = Integer.bitCount((bm & ~(bm<<1)) & 0xFF); islB = Math.max(0, islB-1);

        // Backward (pawn-only: ignore occupancy)
        long wCand = wp & ~wSup;
        long bCand = bp & ~bSup;
        // advance squares attacked by enemy pawns => “unsafe push”
        long wUnsafePush = (wCand << 8) & bAtt;
        long bUnsafePush = (bCand >>> 8) & wAtt;
        // map back to pawn squares
        long wBwd = (wUnsafePush >>> 8) & wCand;
        long bBwd = (bUnsafePush << 8)   & bCand;

        // Passed pawns (using your PASSED masks)
        long wPassed = 0L, bPassed = 0L, wProt = 0L, bProt = 0L;
        long x = wp;
        while (x!=0) {
            int s = Long.numberOfTrailingZeros(x); x&=x-1;
            if ( (bp & PASSED_W[s]) == 0 ) {
                long bit = 1L<<s;
                wPassed |= bit;
                if ((wSup & bit)!=0) wProt |= bit;
            }
        }
        x = bp;
        while (x!=0) {
            int s = Long.numberOfTrailingZeros(x); x&=x-1;
            if ( (wp & PASSED_B[s]) == 0 ) {
                long bit = 1L<<s;
                bPassed |= bit;
                if ((bSup & bit)!=0) bProt |= bit;
            }
        }

        // Count components & apply your weights (MG/EG), EXCEPT blocked-passed
        final int SUP_MG=+6,  SUP_EG=+4;
        final int PHL_MG=+5,  PHL_EG=+3;
        final int PROTP_MG=+10, PROTP_EG=+16;

        final int ISO_MG=12, ISO_EG=14;
        final int DBL_MG=10, DBL_EG=12;
        final int BWD_MG=8,  BWD_EG=12;
        final int ISL_MG=6,  ISL_EG=4;
        final int[] PP_MG = {0,8,15,25,45,80};
        final int[] PP_EG = {0,12,24,40,70,120};

        int supW = Long.bitCount(wSup), supB = Long.bitCount(bSup);
        int phW  = Long.bitCount(wPhl), phB  = Long.bitCount(bPhl);
        int bwdW = Long.bitCount(wBwd), bwdB = Long.bitCount(bBwd);
        int isoWC = isoW, isoBC = isoB, dblWC = dblW, dblBC = dblB;

        // passed by rank
        int wPPmg=0, wPPeg=0, bPPmg=0, bPPeg=0;
        long p = wPassed;
        while (p!=0) {
            int s = Long.numberOfTrailingZeros(p); p&=p-1;
            int rank = s>>>3; int idx = Math.max(0, Math.min(5, rank-1));
            wPPmg += PP_MG[idx]; wPPeg += PP_EG[idx];
        }
        p = bPassed;
        while (p!=0) {
            int s = Long.numberOfTrailingZeros(p); p&=p-1;
            int rankFrom8 = 7-(s>>>3); int idx = Math.max(0, Math.min(5, rankFrom8-1));
            bPPmg += PP_MG[idx]; bPPeg += PP_EG[idx];
        }

        int proW = Long.bitCount(wProt), proB = Long.bitCount(bProt);

        int mgW = supW*SUP_MG + phW*PHL_MG - isoWC*ISO_MG - dblWC*DBL_MG - bwdW*BWD_MG + wPPmg + proW*PROTP_MG - islW*ISL_MG;
        int egW = supW*SUP_EG + phW*PHL_EG - isoWC*ISO_EG - dblWC*DBL_EG - bwdW*BWD_EG + wPPeg + proW*PROTP_EG - islW*ISL_EG;
        int mgB = supB*SUP_MG + phB*PHL_MG - isoBC*ISO_MG - dblBC*DBL_MG - bwdB*BWD_MG + bPPmg + proB*PROTP_MG - islB*ISL_MG;
        int egB = supB*SUP_EG + phB*PHL_EG - isoBC*ISO_EG - dblBC*DBL_EG - bwdB*BWD_EG + bPPeg + proB*PROTP_EG - islB*ISL_EG;

        out.mgDiff = mgW - mgB;
        out.egDiff = egW - egB;
        out.wPassed = wPassed;
        out.bPassed = bPassed;
        return out;
    }

}
