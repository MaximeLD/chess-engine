package max.chess.engine.common;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

// Tried to use PEXT CPU instructions for movegen, but it appears to add too much overhead to go through a native call
@Deprecated
public final class Pext {
    private static final MethodHandle MH_PEXT64;
    private static final MethodHandle MH_PEXT32;
    private static final MethodHandle MH_HAS_BMI2;
    private static final boolean BMI2;

    static {
        boolean bmi2 = false;
        MethodHandle mh64 = null, mh32 = null, mhHas = null;

        // Skip everything on non-x86. Don’t try to be a hero on ARM.
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean isX86 = arch.contains("x86") || arch.contains("amd64");
        if (isX86) {
            try {
                // Load lib by name (requires it on java.library.path) or use an absolute path via System.load(...)
                System.loadLibrary("extract");

                Linker linker = Linker.nativeLinker();
                // Lookup across all loaded libraries
                SymbolLookup lookup = SymbolLookup.loaderLookup();

                mh64 = linker.downcallHandle(
                        lookup.find("pext64").orElseThrow(() -> new UnsatisfiedLinkError("symbol pext64 not found")),
                        FunctionDescriptor.of(JAVA_LONG, JAVA_LONG, JAVA_LONG));

                mh32 = linker.downcallHandle(
                        lookup.find("pext32").orElseThrow(() -> new UnsatisfiedLinkError("symbol pext32 not found")),
                        FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT));

                mhHas = linker.downcallHandle(
                        lookup.find("has_bmi2").orElseThrow(() -> new UnsatisfiedLinkError("symbol has_bmi2 not found")),
                        FunctionDescriptor.of(JAVA_INT));

                bmi2 = ((int) mhHas.invokeExact()) != 0;
            } catch (Throwable t) {
                // Surface the real cause so you don’t get that useless generic message again
                throw new RuntimeException("PEXT binding failed: " + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
            }
        }

        MH_PEXT64 = mh64;
        MH_PEXT32 = mh32;
        MH_HAS_BMI2 = mhHas;
        BMI2 = bmi2;
    }

    public static boolean isBmi2() { return BMI2; }

    public static long pext64(long src, long mask) {
        if (BMI2 && MH_PEXT64 != null) {
            try { return (long) MH_PEXT64.invokeExact(src, mask); } catch (Throwable ignore) {}
        }
        return pext64Java(src, mask);
    }

    public static int pext32(int src, int mask) {
        if (BMI2 && MH_PEXT32 != null) {
            try { return (int) MH_PEXT32.invokeExact(src, mask); } catch (Throwable ignore) {}
        }
        return pext32Java(src, mask);
    }

    static long pext64Java(long src, long mask) {
        long out = 0, bit = 1;
        for (long m = mask; m != 0; m &= (m - 1)) {
            long lsb = m & -m;
            if ((src & lsb) != 0) out |= bit;
            bit <<= 1;
        }
        return out;
    }

    static int pext32Java(int src, int mask) {
        int out = 0, bit = 1;
        for (int m = mask; m != 0; m &= (m - 1)) {
            int lsb = m & -m;
            if ((src & lsb) != 0) out |= bit;
            bit <<= 1;
        }
        return out;
    }

    private Pext() {}
}
