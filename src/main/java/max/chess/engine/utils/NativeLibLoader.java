package max.chess.engine.utils;

import java.io.*;
import java.nio.file.*;

public final class NativeLibLoader {
    private NativeLibLoader() {}

    public static void loadFromClasspath(String base, String libName) throws IOException {
        String os = os();
        String arch = arch();

        String fileName = System.mapLibraryName(libName);
        String resPath = base + "/" + os + "/" + arch + "/" + fileName;

        try (InputStream in = NativeLibLoader.class.getResourceAsStream(resPath)) {
            if (in == null) throw new FileNotFoundException("Native lib not found: " + resPath);
            Path temp = Files.createTempFile(libName + "-" + os + "-" + arch, fileName.endsWith(".dll")? ".dll" : ".so");
            temp.toFile().deleteOnExit();
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            System.load(temp.toAbsolutePath().toString());
        }
    }

    private static String os() {
        String s = System.getProperty("os.name").toLowerCase();
        if (s.contains("win")) return "windows";
        if (s.contains("mac")) return "macos";
        return "linux";
    }
    private static String arch() {
        String a = System.getProperty("os.arch").toLowerCase();
        if (a.contains("aarch64") || a.contains("arm64")) return "aarch64";
        if (a.contains("64")) return "x86_64";
        return "x86_64";
    }
}
