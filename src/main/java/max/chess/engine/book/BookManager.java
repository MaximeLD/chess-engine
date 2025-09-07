package max.chess.engine.book;

import max.chess.engine.game.Game;
import max.chess.engine.utils.notations.MoveIOUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.Optional;

public final class BookManager {
    private volatile OpeningBook book;
    private volatile BookPolicy policy = BookPolicy.defaults();
    private volatile boolean enabled = true;
    private volatile int pliesPlayed = 0;

    public void setEnabled(boolean v) { enabled = v; }
    public void setPolicy(BookPolicy p) { policy = p; }
    public void setPliesPlayed(int plies) { pliesPlayed = Math.max(0, plies); }

    /** Accepts 'classpath:...' or a filesystem path (file or directory). */
    public void loadAuto(String resourceOrPath) {
        close();
        if (resourceOrPath == null || resourceOrPath.isBlank()) return;

        try {
            if (resourceOrPath.startsWith("classpath:")) {
                String res = resourceOrPath.substring("classpath:".length());
                var cl = Thread.currentThread().getContextClassLoader();
                var url = cl.getResource(res);
                if (url == null) return;

                // If it's a directory on the filesystem (dev), list .bin; otherwise treat as file resource
                if ("file".equalsIgnoreCase(url.getProtocol())) {
                    var p = java.nio.file.Paths.get(url.toURI());
                    if (java.nio.file.Files.isDirectory(p)) {
                        var bin = resolveBin(p);
                        if (bin != null) {
                            book = new max.chess.engine.book.polyglot.PolyglotBook(bin);
                            return;
                        }
                    }
                }

                try (var in = cl.getResourceAsStream(res)) {
                    if (in != null) book = new max.chess.engine.book.polyglot.PolyglotBook(in);
                }
                return;
            }

            // Filesystem path
            var path = java.nio.file.Paths.get(resourceOrPath);
            java.nio.file.Path bin = java.nio.file.Files.isDirectory(path) ? resolveBin(path) : path;
            if (bin != null) book = new max.chess.engine.book.polyglot.PolyglotBook(bin);
        } catch (Exception ignore) {
            // noop: no book loaded
        }
    }

    public void loadFile(java.nio.file.Path file) throws java.io.IOException {
        close();
        book = new max.chess.engine.book.polyglot.PolyglotBook(file);
    }

    public void loadClasspath(String resPath) throws java.io.IOException {
        close();
        var in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resPath);
        if (in == null) throw new java.io.IOException("Resource not found: " + resPath);
        book = new max.chess.engine.book.polyglot.PolyglotBook(in);
    }

    public void close() {
        OpeningBook b = book;
        book = null;
        if (b != null) try { b.close(); } catch (Exception ignored) {}
    }

    /** Returns UCI string for a book move or empty. Enforces maxPlies. */
    public Optional<String> pickUci(Game game) {
        if (!enabled) return Optional.empty();
        if (book == null || !book.isLoaded()) return Optional.empty();
        if (pliesPlayed > policy.maxPlies()) return Optional.empty();

        long seed = System.nanoTime() ^ Thread.currentThread().getId();
        return book.pickMove(game, policy, seed).map(MoveIOUtils::toUCINotation);
    }

    private static java.nio.file.Path resolveBin(java.nio.file.Path hint) throws java.io.IOException {
        if (hint == null) return null;
        if (java.nio.file.Files.isRegularFile(hint)) return hint;
        if (java.nio.file.Files.isDirectory(hint)) {
            try (var s = java.nio.file.Files.list(hint)) {
                return s.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".bin"))
                    .sorted()
                    .findFirst().orElse(null);
            }
        }
        return null;
    }
}
