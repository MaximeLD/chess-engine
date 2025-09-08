package max.chess.engine.tb.syzygy;

import max.chess.engine.game.Game;
import max.chess.engine.game.board.Board;
import max.chess.engine.movegen.Move;
import max.chess.engine.movegen.pieces.Knight;
import max.chess.engine.tb.EndgameTablebases;
import max.chess.engine.tb.TBRootResult;
import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;
import max.chess.engine.utils.PieceUtils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SyzygyJNI implements EndgameTablebases {
    private volatile long nativeCtx = 0L;
    private volatile boolean initialized = false;
    private volatile String currentPath = null; // filesystem dir with .rtbw/.rtbz
    private final AtomicBoolean failed = new AtomicBoolean(false);

    // --- JNI natives ---
//    private static native long  jniInit(String[] tbDirs, int cacheMb, int maxThreads);
//    private static native void  jniClose(long ctx);
//    private static native int   jniProbeWDL(long ctx,
//                                            int stm, int castling, int epSquare, int rule50,
//                                            long wP, long wN, long wB, long wR, long wQ, long wK,
//                                            long bP, long bN, long bB, long bR, long bQ, long bK);
//    // returns packed int: (from<<8)|(to<<2)|promo (promo: 0=none,1=N,2=B,3=R,4=Q); wdl in out[0], dtz in out[1]
//    private static native int   jniProbeRoot(long ctx,
//                                             int stm, int castling, int epSquare, int rule50,
//                                             long wP, long wN, long wB, long wR, long wQ, long wK,
//                                             long bP, long bN, long bB, long bR, long bQ, long bK,
//                                             int[] outWdlDtz);

    @Override
    public boolean isAvailable() { return initialized && nativeCtx != 0 && !failed.get(); }

    @Override
    public Optional<TBRootResult> probeRoot(Game g, int maxPieces) {
        if (!ensureInit()) return Optional.empty();
        if (pieces(g.board()) > maxPieces) return Optional.empty();

        var b = g.board();
        final boolean whiteToMove = ColorUtils.isWhite(g.currentPlayer);
        final int castling = encodeCastling(b);
        final int ep = encodeEp(b, g);             // 0..63 if legal EP capture exists, else 0
        final int rule50 = g.halfMoveClock;        // Fathom expects the half-move clock as-is

        final long white  = b.whiteBB;
        final long black  = b.blackBB;
        final long kings  = b.kingBB;
        final long queens = b.queenBB;
        final long rooks  = b.rookBB;
        final long bishops= b.bishopBB;
        final long knights= b.knightBB;
        final long pawns  = b.pawnBB;

        int[] meta = new int[2]; // [0]=wdl, [1]=dtz
        int packed = SyzygyNative.tbProbeRoot(white, black, kings, queens, rooks, bishops, knights, pawns,
            rule50, 0, ep, whiteToMove, meta);

        if (packed == -1) return Optional.empty();

        // Per tbprobe.h:
        final int to   = (packed >>> 4)  & 0x3F;
        final int from = (packed >>> 10) & 0x3F;
        final int promoCode = (packed >>> 16) & 0x7;
        final byte promo = switch (promoCode) {
            case 1 -> max.chess.engine.utils.PieceUtils.QUEEN;
            case 2 -> max.chess.engine.utils.PieceUtils.ROOK;
            case 3 -> max.chess.engine.utils.PieceUtils.BISHOP;
            case 4 -> max.chess.engine.utils.PieceUtils.KNIGHT;
            default -> max.chess.engine.utils.PieceUtils.NONE;
        };

        long fromBB = BitUtils.getPositionIndexBitMask(from);
        final byte pieceType;
        if((fromBB & pawns) != 0) {
            pieceType = PieceUtils.PAWN;
        } else if ((fromBB & bishops) != 0) {
            pieceType = PieceUtils.BISHOP;
        } else if ((fromBB & rooks) != 0) {
            pieceType = PieceUtils.ROOK;
        } else if ((fromBB & queens) != 0) {
            pieceType = PieceUtils.QUEEN;
        } else if ((fromBB & knights) != 0) {
            pieceType = PieceUtils.KNIGHT;
        } else if ((fromBB & kings) != 0) {
            pieceType = PieceUtils.KING;
        } else {
            throw new RuntimeException("invalid piece type");
        }

        return Optional.of(new TBRootResult(Move.asBytes(from, to, pieceType, promo), meta[0], meta[1]));
    }

    @Override
    public OptionalInt probeWDL(Game g, int maxPieces) {
        if (!ensureInit()) return OptionalInt.empty();
        if (pieces(g.board()) > maxPieces) return OptionalInt.empty();

        var b = g.board();
        final boolean whiteToMove = ColorUtils.isWhite(g.currentPlayer);
        final int castling = encodeCastling(b);
        final int ep = encodeEp(b, g);
        final int rule50 = g.halfMoveClock;

        int res = SyzygyNative.tbProbeWdl(
            b.whiteBB, b.blackBB, b.kingBB, b.queenBB, b.rookBB, b.bishopBB, b.knightBB, b.pawnBB,
            rule50, castling, ep, whiteToMove);
        if (res < 0) return OptionalInt.empty();
        return OptionalInt.of(res);
    }

    @Override
    public void close() {

    }

    // init: accept filesystem path or "classpath:...". Calls SyzygyNative.tbInit(fsPath).
    private synchronized boolean ensureInit() {
        if (initialized || failed.get()) return initialized;
        configure(currentPath != null ? currentPath : System.getProperty("syzygy.path", "syzygy/3-4-5/Syzygy345"));
        return initialized;
    }

    public synchronized void configure(String pathOrClasspath) {
        try {
            String fsPath = pathOrClasspath;
            if (fsPath == null || fsPath.isBlank()) fsPath = extractClasspathDefault();
            else if (fsPath.startsWith("classpath:")) fsPath = extractClasspath(fsPath.substring("classpath:".length()));
            if (fsPath == null) { failed.set(true); return; }

            int ok = SyzygyNative.tbInit(fsPath);
            initialized = ok > 0;
            currentPath = fsPath;
            failed.set(!initialized);
        } catch (Throwable t) {
            failed.set(true);
        }
    }

    // --- helpers ---

    private static int pieces(Board b) {
        return Long.bitCount(b.gameBB);
    }

    private static int encodeCastling(Board b) {
        int c = 0;
        if (b.game().whiteCanCastleKingSide)  c |= 1; // Wk
        if (b.game().whiteCanCastleQueenSide) c |= 2; // Wq
        if (b.game().blackCanCastleKingSide)  c |= 4; // Bk
        if (b.game().blackCanCastleQueenSide) c |= 8; // Bq
        return c;
    }

    // EP: only when at least one EP capture is legal; otherwise 0 (none).
    private static int encodeEp(Board b, Game g) {
        // Fathom: 0 = no EP; otherwise epSquare as 0..63.
        final int ep = b.enPassantIndex; // -1 if none in your model
        if (ep < 0 || ep > 63) return 0;

        final boolean white = max.chess.engine.utils.ColorUtils.isWhite(g.currentPlayer);
        final long myPawns  = b.pawnBB & (white ? b.whiteBB : b.blackBB);
        final int f = ep & 7;

        // A legal EP must be capturable by side-to-move. We do a cheap presence check
        // (TB will handle checks/obstructions).
        if (white) {
            long canCap =
                ((f > 0 ? (1L << (ep - 9)) : 0L) |
                    (f < 7 ? (1L << (ep - 7)) : 0L));
            if ((myPawns & canCap) == 0) return 0;
        } else {
            long canCap =
                ((f > 0 ? (1L << (ep + 7)) : 0L) |
                    (f < 7 ? (1L << (ep + 9)) : 0L));
            if ((myPawns & canCap) == 0) return 0;
        }
        return ep; // 0..63
    }

    private static String extractClasspathDefault() throws IOException {
        return extractClasspath("syzygy/3-4-5/Syzygy345");
    }

    private static String extractClasspath(String root) throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        var url = cl.getResource(root);
        if (url == null) return null;

        Path target = Files.createTempDirectory("syzygy-tb-");
        target.toFile().deleteOnExit();

        // If we're running from exploded classes (file://), copy or just use dir
        if ("file".equalsIgnoreCase(url.getProtocol())) {
            Path p = Paths.get(url.getPath());
            if (Files.isDirectory(p)) return p.toAbsolutePath().toString();
        }

        // In JAR: enumerate and extract only .rtbw/.rtbz
        try (var in = cl.getResourceAsStream(root)) {
            // Some classloaders don't list; fallback: brute copy known filenames is impractical.
            // Instead, try to copy via FileSystems for jar URL:
        }
        // Portable listing for jar:
        try (FileSystem fs = FileSystems.newFileSystem(url.toURI(), Map.of())) {
            Path jarRoot = fs.getPath("/" + root);
            Files.walk(jarRoot).forEach(src -> {
                try {
                    if (Files.isRegularFile(src)) {
                        String name = src.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (name.endsWith(".rtbw") || name.endsWith(".rtbz")) {
                            Path dst = target.resolve(src.getFileName().toString());
                            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } catch (IOException ignored) {}
            });
        } catch (Exception e) {
            // As a last resort, we fail; user can set SyzygyPath to filesystem
            throw new IOException("Failed to extract TBs from classpath: " + e.getMessage(), e);
        }
        return target.toAbsolutePath().toString();
    }

    public static final int WDL_LOSS = 0;
    public static final int WDL_BLESSED_LOSS = 1;  /* LOSS but 50-move draw */
    public static final int WDL_DRAW = 2;
    public static final int WDL_CURSED_WIN = 3; /* WIN but 50-move draw */
    public static final int WDL_WIN = 4;

    public static boolean isWin(int wdl) {
        return wdl == WDL_WIN;
    }
    public static boolean isLoss(int wdl) {
        return wdl == WDL_LOSS;
    }
    public static boolean isDraw(int wdl) {
        return !isWin(wdl) && !isLoss(wdl);
    }
}
