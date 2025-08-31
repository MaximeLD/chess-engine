package max.chess.engine.game;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import max.chess.engine.movegen.MoveGenerator;

// A poor attempt at cache for transposition table with Zobrist Keys
// Will be reworked when going with actual transposition tables during search implementation
@Deprecated(forRemoval = true)
public class GameCache {
    private record Entry(IntArrayList moves, long key, long signature) {}
    public static long SIGNIFICANT_ZOBRIST_KEY_BITS = 22;
    public static long CACHE_HIT = 0;
    public static long CACHE_MISS = 0;
    public static long CACHE_COLLISION = 0;
    public static long CACHE_HIT_ATTACK_BB = 0;
    public static long CACHE_MISS_ATTACK_BB = 0;
    private final static boolean TRANSPOSITION_TABLES_ENABLED = false;

    private static final int CACHE_ENTRY_SIZE = 128;
    private static final int CACHE_ALLOCATED_SIZE = 2 << 20;
    //    public static int CACHE_SIZE = 2 << (SIGNIFICANT_ZOBRIST_KEY_BITS+1);
    public static int CACHE_SIZE = CACHE_ALLOCATED_SIZE/CACHE_ENTRY_SIZE;

//    private static final Entry[] CACHED_GAMES = new Entry[CACHE_SIZE];
//    private static final Map<Game, Entry> CACHE_MAP = new HashMap<>(CACHE_SIZE);
    private static final Long2ObjectLinkedOpenHashMap<IntArrayList> FAST_CACHED_MOVES =
        new Long2ObjectLinkedOpenHashMap<>(CACHE_SIZE, 0.999f);

    private static final Long2LongLinkedOpenHashMap FAST_CACHED_ATTACK_BB =
            new Long2LongLinkedOpenHashMap(1024, 0.999f);

//    private static final Map<Long, Move[]> CACHED_MOVES = new LinkedHashMap<>(CACHE_SIZE, 1.0f);

    static {
        System.out.println("Cache initialized for "+CACHE_SIZE+" entries - "+CACHE_ALLOCATED_SIZE+" bytes allocated.");
        System.out.println("Cache Attack BB initialized for "+CACHE_ALLOCATED_SIZE/16+" entries - "+CACHE_ALLOCATED_SIZE+" bytes allocated.");
        FAST_CACHED_ATTACK_BB.defaultReturnValue(0);
    }

    private static long getEntryOffset(int index) {
        return (long) index * CACHE_ENTRY_SIZE;
    }

    private static long getSignature(Game game) {
        return game.board().gameBB;
    }

    private static Entry toEntry(Game game, IntArrayList moves) {
        return new Entry(moves, game.zobristKey(), getSignature(game));
    }

    public static long getOrComputeAttackBB(Game game, int color) {
        return MoveGenerator.doGetAttackBB(game.board(), color, 0);
//        long key = game.zobristKey();
//        if(color != game.currentPlayer) {
//            key = ZobristHashKeys.switchPlayer(key);
//        }
//        long attackBB = FAST_CACHED_ATTACK_BB.getAndMoveToFirst(key);
//        if(attackBB == 0L) {
//            FAST_CACHED_ATTACK_BB.putAndMoveToFirst(key, attackBB);
//            if(FAST_CACHED_ATTACK_BB.size() >= 1024-1) {
//                FAST_CACHED_ATTACK_BB.removeLastLong();
//            }
//            CACHE_MISS_ATTACK_BB++;
//        } else {
//            CACHE_HIT_ATTACK_BB++;
//        }
//
//        return attackBB;
    }

    public static int[] getOrComputeLegalMoves(Game game) {
        int[] cachedMoves = null;
        long zobristKey = game.zobristKey();
        boolean canUseTransposition = TRANSPOSITION_TABLES_ENABLED;
//        int cacheIndex = (int) (Long.remainderUnsigned(zobristKey, CACHE_SIZE));
//        int cacheIndex = (int) (game.zobristKey() >>> (64 - SIGNIFICANT_ZOBRIST_KEY_BITS));
        if(canUseTransposition) {
//            cachedMoves = FAST_CACHED_MOVES.getAndMoveToLast(zobristKey);
//            cachedMoves = CACHED_MOVES.get(game.zobristKey() >> (64 - SIGNIFICANT_ZOBRIST_KEY_BITS));
//            Entry entryCached =  CACHED_GAMES[cacheIndex];
//            if(entryCached != null && entryCached.key == zobristKey) {
//                if(entryCached.signature == getSignature(game)) {
//                    cachedMoves = entryCached.moves;
//                } else {
//                    CACHE_COLLISION++;
//                }
            }

//            long cachedKey = CACHED_MOVES_KEYS[cacheIndex];
//            boolean cachedKeyFound = cachedKey != 0L;
//            boolean collision = cachedKey != zobristKey;
//            if(cachedKeyFound && collision) {
                // We have a collision !
//                CACHE_COLLISION++;
//            } else if(cachedKeyFound) {
//                cachedMoves = CACHED_MOVES[cacheIndex];
//            }
//        }

        if (cachedMoves == null) {
            CACHE_MISS++;
            return MoveGenerator.generateMoves(game);
//            if(canUseTransposition) {
//                FAST_CACHED_MOVES.putAndMoveToLast(zobristKey, cachedMoves);
//                if(FAST_CACHED_MOVES.size() >= CACHE_SIZE-1) {
//                    FAST_CACHED_MOVES.removeFirst();
//                }
////                CACHED_GAMES[cacheIndex] = toEntry(game, cachedMoves);
////                Entry entryToCache = new Entry(cachedMoves, getSignature(game));
////                CACHE_MAP.put(game, entryToCache);
////                CACHED_MOVES.put(game.zobristKey() >> (64 - SIGNIFICANT_ZOBRIST_KEY_BITS), cachedMoves);
////                if(CACHE_MAP.size() == CACHE_SIZE - 1) {
////                    CACHE_MAP.clear();
//////                    CACHED_MOVES.remove(CACHED_MOVES.entrySet().iterator().next().getKey());
////                }
//            }
        } else {
            CACHE_HIT++;
        }

        return cachedMoves;
    }

    public static void clearZobristCache() {
        CACHE_MISS = 0;
        CACHE_HIT = 0;
        CACHE_COLLISION = 0;
//        Arrays.fill(CACHED_GAMES, null);
    }

    public static void printZobristCacheReport() {
        System.out.println("********************");
        System.out.println("ZOBRIST CACHE REPORT");
        System.out.println("\tHIT: "+ GameCache.CACHE_HIT);
        System.out.println("\tMISS: "+ GameCache.CACHE_MISS);
        System.out.println("\tCOLLISION: "+ GameCache.CACHE_COLLISION);
        System.out.println("CACHE ATTACK BB");
        System.out.println("\tHIT: "+ GameCache.CACHE_HIT_ATTACK_BB);
        System.out.println("\tMISS: "+ GameCache.CACHE_MISS_ATTACK_BB);
        System.out.println("********************");
    }
}
