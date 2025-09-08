package max.chess.engine.tb.syzygy;

import max.chess.engine.utils.NativeLibLoader;

/** Minimal JNI shim that matches the exported symbols in syzygyjni.dll/.so/.dylib */
final class SyzygyNative {
    static {
        // Try to load our JNI from classpath-native; fallback to System.loadLibrary
        try {
            NativeLibLoader.loadFromClasspath(
                "/native/syzygyjni", // base folder with OS/arch subdirs
                "syzygyjni");        // lib name (libsyzygyjni.so, syzygyjni.dll, libsyzygyjni.dylib)
        } catch (Throwable t) {
            try { System.loadLibrary("syzygyjni"); }
            catch (Throwable t2) {
                throw t2;
                // leave uninitialized; isAvailable() will be false
            }
        }
    }

    private SyzygyNative() {}

    // Matches Java_max_chess_engine_tb_syzygy_SyzygyNative_tbInit
    static native int tbInit(String path);

    static native int tbProbeWdl(
        long white, long black,
        long kings, long queens, long rooks, long bishops, long knights, long pawns,
        int rule50, int castlingMask, int epSquare, boolean whiteToMove);

    static native int tbProbeRoot(
        long white, long black,
        long kings, long queens, long rooks, long bishops, long knights, long pawns,
        int rule50, int castlingMask, int epSquare, boolean whiteToMove,
        int[] out /* [wdl, dtz] */);
}
