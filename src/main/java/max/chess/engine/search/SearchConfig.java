package max.chess.engine.search;

public final class SearchConfig {

    public final boolean debug;

    // TT
    public final boolean useTT;
    public final boolean useTTBounds;
    public final boolean storeExactOnlyAtShallow;
    public final boolean storeTighterBounds;
    public final int ttSizeMb;

    // Aspiration window
    public final int aspirationCp;

    // Quiescence margins
    public final int deltaMargin;
    public final int seeMargin;

    // Null move pruning
    public final boolean useNullMove;
    public final int nullBaseReduction;
    public final int nullMinDepth;
    public final int nullVerifyDepth;
    public final boolean disableNullInPV;
    public final boolean disableNullInZugzwangish;
    public final boolean nmpVerifyOnDanger; // default true
    public final int nmpVerifyMinDepth; // default 6

    // Late Move Reduction
    public final boolean useLMR;
    public final int lmrMinDepth;          // depth threshold to consider LMR (e.g., 3)
    public final int lmrMinMove;           // reduce from this move index (e.g., 4 => after 3 moves)
    public final int lmrBase;              // base reduction (0 or 1). 1 is common.
    public final int lmrMax;               // clamp reductions (e.g., 3 or 4)
    public final boolean lmrReduceCaptures; // default false: reduce only quiets
    public final boolean lmrReduceChecks;   // default false: don’t reduce checking moves
    public final boolean lmrNoReduceTTTrusted; // don’t reduce trusted TT move
    public final boolean lmrNoReduceKiller;    // don’t reduce killer moves
    public final int lmrHistorySkip;       // if history >= threshold, skip reduction (e.g., 4000)
    public final int  lmrPVMaxReduction;         // (default 1) allow at most 1 ply reduction in PV
    public final int  lmrHistoryPunish;         // (default 0) <= this -> slightly stronger reduction


    // Futility pruning
    public final boolean useFutility;
    public final boolean useExtendedFutility;
    public final boolean useReverseFutility;
    public final int futilityMargin1;   // depth == 1
    public final int futilityMargin2;   // depth == 2
    public final int futilityMargin3;   // depth == 3
    public final int reverseFutilityMargin; // for RFP at depth <= 3
    public final boolean tightenFutilityOnDanger; // default true

    // Move Count Pruning (LMP) and History Pruning (HP)
    public final boolean useLMP;
    public final int  lmpMaxDepth;        // prune only when depth <= this (e.g., 3)
    public final int  lmpBaseQuiets;      // baseline late-quiet index (e.g., 4)
    public final int  lmpScale;           // threshold = lmpBaseQuiets + depth*lmpScale (e.g., 2)
    public final boolean lmpBlockOnDanger; // disable LMP when king danger flagged

    public final boolean useHistoryPruning;
    public final int  histPruneMaxDepth;     // prune only when depth <= this (e.g., 3)
    public final int  histPruneAfter;        // start considering from this quiet index (e.g., 6)
    public final int  histPruneThreshold;    // history <= threshold -> eligible (e.g., 1000)
    public final boolean histPruneBlockOnDanger;

    // Internal Iterative Deepening
    public final boolean useIID;
    public final int iidMinDepth;        // e.g., 5
    public final int iidReduction;       // e.g., 2 plies
    public final boolean iidPvOnly;      // only apply on PV nodes
    public final boolean iidWhenInCheck; // allow IID while in check (default false)

    // ProbCut
    public final boolean useProbCut;
    public final int probCutMinDepth;     // e.g., 5
    public final int probCutReduction;    // e.g., 2
    public final int probCutMargin;       // e.g., 120 cp
    public final int probCutMaxMoves;     // e.g., top 8 captures
    public final boolean probCutRequireSEEPositive; // true keeps only non-losing captures
    public final int probCutVictimMin;    // e.g., PieceValues.ROOK_VALUE
    public final boolean dangerGatesProbCut; // default true

    // Prefer progress, avoid drifting into 3-fold when winning
    public final boolean useEarlyRepetitionDraw;   // detect twofold on stack and score as draw (default: true)
    public final int     repScanMaxPlies;    // scan back along stack in steps of 2 (default: 200)
    public final int     erdMinDepth;    // min depth to consider early repetition (default: 6)

    // If a move immediately bounces last move (A→B, B→A), add extra LMR reduction
    public final boolean bumpLMROnImmediateRepetition; // (default true)
    public final int     immRepLMRBump               ;   // + reduction tier (default 1)

    // Draw handling / contempt
    public final boolean useContempt;        // enable non-zero draw scores - default true
    public final boolean dynamicContempt;    // base draw score on local eval - default true
    public final int contemptCp;               // 10–50 is common at blitz (default 20)
    public final int contemptEvalMargin;       // eval threshold to trigger dynamic sign (default 50)

    public final boolean rootAntiRepOrdering; // pushes back repetition moves (default: true)
    // Repetition-aware ordering
    public final boolean penalizeImmediateRepetition = true;
    public final int repetitionPenaltyOrdering = 600_000;

    public final int  counterMoveOrderingBonus; // 850_000;
    public final int historyCustomScale; // -1 (disabled) by default ; leaving the standard historyScale of 16384

    // Safety nets
    public final long defaultNoTimeNs;
    public final long maxHardCapNs;

    private SearchConfig(Builder b) {
        debug = b.debug;

        useTT = b.useTT; useTTBounds = b.useTTBounds;
        storeExactOnlyAtShallow = b.storeExactOnlyAtShallow;
        storeTighterBounds = b.storeTighterBounds;
        ttSizeMb = b.ttSizeMb;
        aspirationCp = b.aspirationCp;
        deltaMargin = b.deltaMargin;
        seeMargin = b.seeMargin;

        useNullMove = b.useNullMove;
        nullBaseReduction = b.nullBaseReduction;
        nullMinDepth = b.nullMinDepth;
        nullVerifyDepth = b.nullVerifyDepth;
        disableNullInPV = b.disableNullInPV;
        disableNullInZugzwangish = b.disableNullInZugzwangish;
        nmpVerifyOnDanger = b.nmpVerifyOnDanger;
        nmpVerifyMinDepth = b.nmpVerifyMinDepth;

        useLMR = b.useLMR;
        lmrMinDepth = b.lmrMinDepth;
        lmrMinMove = b.lmrMinMove;
        lmrBase = b.lmrBase;
        lmrMax = b.lmrMax;
        lmrReduceCaptures = b.lmrReduceCaptures;
        lmrReduceChecks = b.lmrReduceChecks;
        lmrNoReduceTTTrusted = b.lmrNoReduceTTTrusted;
        lmrNoReduceKiller = b.lmrNoReduceKiller;
        lmrHistorySkip = b.lmrHistorySkip;
        lmrPVMaxReduction = b.lmrPVMaxReduction;
        lmrHistoryPunish = b.lmrHistoryPunish;

        useFutility = b.useFutility;
        useExtendedFutility = b.useExtendedFutility;
        useReverseFutility = b.useReverseFutility;
        futilityMargin1 = b.futilityMargin1;
        futilityMargin2 = b.futilityMargin2;
        futilityMargin3 = b.futilityMargin3;
        reverseFutilityMargin = b.reverseFutilityMargin;
        tightenFutilityOnDanger = b.tightenFutilityOnDanger;

        // LMP/HP
        useLMP = b.useLMP;
        lmpMaxDepth = b.lmpMaxDepth;
        lmpBaseQuiets = b.lmpBaseQuiets;
        lmpScale = b.lmpScale;
        lmpBlockOnDanger = b.lmpBlockOnDanger;

        useHistoryPruning = b.useHistoryPruning;
        histPruneMaxDepth = b.histPruneMaxDepth;
        histPruneAfter = b.histPruneAfter;
        histPruneThreshold = b.histPruneThreshold;
        histPruneBlockOnDanger = b.histPruneBlockOnDanger;

        useIID = b.useIID;
        iidMinDepth = b.iidMinDepth;
        iidReduction = b.iidReduction;
        iidPvOnly = b.iidPvOnly;
        iidWhenInCheck = b.iidWhenInCheck;

        useProbCut = b.useProbCut;
        probCutMinDepth = b.probCutMinDepth;
        probCutReduction = b.probCutReduction;
        probCutMargin = b.probCutMargin;
        probCutMaxMoves = b.probCutMaxMoves;
        probCutRequireSEEPositive = b.probCutRequireSEEPositive;
        probCutVictimMin = b.probCutVictimMin;
        dangerGatesProbCut = b.dangerGatesProbCut;

        useEarlyRepetitionDraw = b.useEarlyRepetitionDraw;
        repScanMaxPlies = b.repScanMaxPlies;
        erdMinDepth = b.erdMinDepth;

        bumpLMROnImmediateRepetition = b.bumpLMROnImmediateRepetition;
        immRepLMRBump = b.immRepLMRBump;

        useContempt = b.useContempt;
        dynamicContempt = b.dynamicContempt;
        contemptCp = b.contemptCp;
        contemptEvalMargin = b.contemptEvalMargin;
        rootAntiRepOrdering = b.rootAntiRepOrdering;

        counterMoveOrderingBonus = b.counterMoveOrderingBonus;
        historyCustomScale = b.historyCustomScale;

        defaultNoTimeNs = b.defaultNoTimeNs;
        maxHardCapNs = b.maxHardCapNs;
    }
    public static class Builder {
        private boolean debug = false;

        private boolean useTT = true, useTTBounds = true, storeExactOnlyAtShallow = true, storeTighterBounds = false;
        private int ttSizeMb = 64, aspirationCp = 18, deltaMargin = 40, seeMargin = 20;

        // Null move defaults
        private boolean useNullMove = true;
        private int nullBaseReduction = 2;
        private int nullMinDepth = 3;
        private int nullVerifyDepth = 0;
        private boolean disableNullInPV = true;
        private boolean disableNullInZugzwangish = true;
        private boolean nmpVerifyOnDanger = true;
        private int nmpVerifyMinDepth = 6;

        // LMR defaults
        private boolean useLMR = true;
        private int lmrMinDepth = 3;
        private int lmrMinMove = 4;
        private int lmrBase = 1;
        private int lmrMax = 3;
        private boolean lmrReduceCaptures = false;
        private boolean lmrReduceChecks = false;
        private boolean lmrNoReduceTTTrusted = true;
        private boolean lmrNoReduceKiller = true;
        private int lmrHistorySkip = 4000;
        private int  lmrPVMaxReduction = 1;
        private int  lmrHistoryPunish = 0;

        // Futility defaults (conservative)
        private boolean useFutility = true;
        private boolean useExtendedFutility = true;
        private boolean useReverseFutility = true;
        private int futilityMargin1 = 100;  // d=1
        private int futilityMargin2 = 200;  // d=2
        private int futilityMargin3 = 300;  // d=3
        private int reverseFutilityMargin = 100;
        private boolean tightenFutilityOnDanger = true;

        // LMP/HP defaults (conservative)
        private boolean useLMP = true;
        private int  lmpMaxDepth = 2;   // only d <= 2
        private int  lmpBaseQuiets = 8; // prune very late quiets
        private int  lmpScale = 1;      // gentler scaling
        private boolean lmpBlockOnDanger = true;

        private boolean useHistoryPruning = true;
        private int  histPruneMaxDepth = 3;
        private int  histPruneAfter = 6;
        private int  histPruneThreshold = 1000; // history score <= threshold -> prune
        private boolean histPruneBlockOnDanger = true;

        // IID defaults (conservative)
        private boolean useIID = true;
        private int iidMinDepth = 5;
        private int iidReduction = 2;
        private boolean iidPvOnly = true;
        private boolean iidWhenInCheck = false;

        // ProbCut defaults (conservative)
        private boolean useProbCut = true;
        private int probCutMinDepth = 5;
        private int probCutReduction = 2;
        private int probCutMargin = 120;
        private int probCutMaxMoves = 8;
        private boolean probCutRequireSEEPositive = true;
        private int probCutVictimMin = max.chess.engine.search.evaluator.PieceValues.ROOK_VALUE;
        private boolean dangerGatesProbCut = true;

        // Prefer progress, avoid drifting into 3-fold when winning
        public boolean useEarlyRepetitionDraw = true;   // detect twofold on stack and score as draw
        public int     repScanMaxPlies        = 200;    // scan back along stack in steps of 2
        public int     erdMinDepth        = 6;    // min depth to consider early repetition

        // If a move immediately bounces last move (A→B, B→A), add extra LMR reduction
        public boolean bumpLMROnImmediateRepetition = true;
        public int     immRepLMRBump                = 1;   // +1 reduction tier

        // Draw handling / contempt
        public boolean useContempt = true;
        public boolean dynamicContempt = true;
        public int contemptCp = 20;
        public int contemptEvalMargin = 50;
        public boolean rootAntiRepOrdering = true;

        // Repetition-aware ordering
        public boolean penalizeImmediateRepetition = true;
        public int repetitionPenaltyOrdering = 600_000;

        public int  counterMoveOrderingBonus = 850_000;
        public int historyCustomScale = -1;

        private long defaultNoTimeNs = java.time.Duration.ofSeconds(2).toNanos();
        private long maxHardCapNs = java.time.Duration.ofSeconds(120).toNanos();

        public Builder debug(boolean v){debug=v;return this;}

        public Builder useTT(boolean v){useTT=v;return this;}
        public Builder useTTBounds(boolean v){useTTBounds=v;return this;}
        public Builder storeExactOnlyAtShallow(boolean v){storeExactOnlyAtShallow=v;return this;}
        public Builder storeTighterBounds(boolean v){storeTighterBounds=v;return this;}
        public Builder ttSizeMb(int v){ttSizeMb=v;return this;}
        public Builder aspirationCp(int v){aspirationCp=v;return this;}
        public Builder deltaMargin(int v){deltaMargin=v;return this;}
        public Builder seeMargin(int v){seeMargin=v;return this;}

        public Builder useNullMove(boolean v){useNullMove=v;return this;}
        public Builder nullBaseReduction(int v){nullBaseReduction=v;return this;}
        public Builder nullMinDepth(int v){nullMinDepth=v;return this;}
        public Builder nullVerifyDepth(int v){nullVerifyDepth=v;return this;}
        public Builder disableNullInPV(boolean v){disableNullInPV=v;return this;}
        public Builder disableNullInZugzwangish(boolean v){disableNullInZugzwangish=v;return this;}
        public Builder nmpVerifyOnDanger(boolean v){nmpVerifyOnDanger=v;return this;}

        public Builder useLMR(boolean v){useLMR=v;return this;}
        public Builder lmrMinDepth(int v){lmrMinDepth=v;return this;}
        public Builder lmrMinMove(int v){lmrMinMove=v;return this;}
        public Builder lmrBase(int v){lmrBase=v;return this;}
        public Builder lmrMax(int v){lmrMax=v;return this;}
        public Builder lmrReduceCaptures(boolean v){lmrReduceCaptures=v;return this;}
        public Builder lmrReduceChecks(boolean v){lmrReduceChecks=v;return this;}
        public Builder lmrNoReduceTTTrusted(boolean v){lmrNoReduceTTTrusted=v;return this;}
        public Builder lmrNoReduceKiller(boolean v){lmrNoReduceKiller=v;return this;}
        public Builder lmrHistorySkip(int v){lmrHistorySkip=v;return this;}

        public Builder useFutility(boolean v){useFutility=v;return this;}
        public Builder useExtendedFutility(boolean v){useExtendedFutility=v;return this;}
        public Builder useReverseFutility(boolean v){useReverseFutility=v;return this;}
        public Builder tightenFutilityOnDanger(boolean v){tightenFutilityOnDanger=v;return this;}
        public Builder futilityMargin1(int v){futilityMargin1=v;return this;}
        public Builder futilityMargin2(int v){futilityMargin2=v;return this;}
        public Builder futilityMargin3(int v){futilityMargin3=v;return this;}
        public Builder reverseFutilityMargin(int v){reverseFutilityMargin=v;return this;}

        // LMP/HP setters
        public Builder useLMP(boolean v){useLMP=v;return this;}
        public Builder lmpMaxDepth(int v){lmpMaxDepth=v;return this;}
        public Builder lmpBaseQuiets(int v){lmpBaseQuiets=v;return this;}
        public Builder lmpScale(int v){lmpScale=v;return this;}
        public Builder lmpBlockOnDanger(boolean v){lmpBlockOnDanger=v;return this;}

        public Builder useHistoryPruning(boolean v){useHistoryPruning=v;return this;}
        public Builder histPruneMaxDepth(int v){histPruneMaxDepth=v;return this;}
        public Builder histPruneAfter(int v){histPruneAfter=v;return this;}
        public Builder histPruneThreshold(int v){histPruneThreshold=v;return this;}
        public Builder histPruneBlockOnDanger(boolean v){histPruneBlockOnDanger=v;return this;}

        public Builder useIID(boolean v){useIID=v;return this;}
        public Builder iidMinDepth(int v){iidMinDepth=v;return this;}
        public Builder iidReduction(int v){iidReduction=v;return this;}
        public Builder iidPvOnly(boolean v){iidPvOnly=v;return this;}
        public Builder iidWhenInCheck(boolean v){iidWhenInCheck=v;return this;}

        public Builder useProbCut(boolean v){useProbCut=v;return this;}
        public Builder probCutMinDepth(int v){probCutMinDepth=v;return this;}
        public Builder probCutReduction(int v){probCutReduction=v;return this;}
        public Builder probCutMargin(int v){probCutMargin=v;return this;}
        public Builder probCutMaxMoves(int v){probCutMaxMoves=v;return this;}
        public Builder probCutRequireSEEPositive(boolean v){probCutRequireSEEPositive=v;return this;}
        public Builder probCutVictimMin(int v){probCutVictimMin=v;return this;}
        public Builder dangerGatesProbCut(boolean v){dangerGatesProbCut=v;return this;}

        public Builder counterMoveOrderingBonus(int v){counterMoveOrderingBonus=v;return this;}
        public Builder historyCustomScale(int v){historyCustomScale=v;return this;}

        public Builder useContempt(boolean v){useContempt=v;return this;}
        public Builder dynamicContempt(boolean v){dynamicContempt=v;return this;}
        public Builder contemptCp(int v){contemptCp=v;return this;}
        public Builder contemptEvalMargin(int v){contemptEvalMargin=v;return this;}
        public Builder rootAntiRepOrdering(boolean v){rootAntiRepOrdering=v;return this;}

        public Builder defaultNoTimeNs(long v){defaultNoTimeNs=v;return this;}
        public Builder maxHardCapNs(long v){maxHardCapNs=v;return this;}
        public SearchConfig build(){return new SearchConfig(this);}
    }
}
