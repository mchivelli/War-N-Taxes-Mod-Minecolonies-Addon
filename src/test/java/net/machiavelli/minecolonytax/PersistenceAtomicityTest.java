package net.machiavelli.minecolonytax;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the rule that mod state is only ever written atomically (tmp + rename).
 *
 * <p>Background (5.0.9 audit): most managers wrote their JSON straight into the final path
 * with a plain FileWriter. A crash or kill mid-write left a truncated file, and the loaders
 * treated that as "no data, starting fresh" - silently erasing taxes, war chests, factions,
 * occupations or spy state. All save paths now go through SafeFileIO.writeStringAtomic or a
 * local tmp+ATOMIC_MOVE block. This test fails the build if a direct writer creeps back in.
 */
class PersistenceAtomicityTest {

    /** Files whose direct writes are deliberate (append-only diagnostics, not state). */
    private static final List<String> ALLOWED = List.of("CrashLogger.java", "SafeFileIO.java");

    @Test
    @DisplayName("no state file is written without the tmp+rename pattern")
    void noDirectStateWrites() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String name = p.getFileName().toString();
                if (ALLOWED.contains(name)) return;
                String code;
                try {
                    code = Files.readString(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                int idx = 0;
                while ((idx = code.indexOf("new FileWriter(", idx)) >= 0) {
                    int end = code.indexOf(')', idx);
                    String arg = code.substring(idx + "new FileWriter(".length(), end < 0 ? code.length() : end);
                    if (!arg.toLowerCase().contains("tmp")) {
                        offenders.add(name + " -> new FileWriter(" + arg.trim() + ")");
                    }
                    idx = end < 0 ? code.length() : end;
                }
                idx = 0;
                while ((idx = code.indexOf("Files.newBufferedWriter(", idx)) >= 0) {
                    int end = code.indexOf(')', idx);
                    String arg = code.substring(idx + "Files.newBufferedWriter(".length(), end < 0 ? code.length() : end);
                    if (!arg.toLowerCase().contains("tmp")) {
                        offenders.add(name + " -> Files.newBufferedWriter(" + arg.trim() + ")");
                    }
                    idx = end < 0 ? code.length() : end;
                }
            });
        }
        assertTrue(offenders.isEmpty(),
                "State files must be written atomically (SafeFileIO.writeStringAtomic or a local "
                        + "tmp+ATOMIC_MOVE block) - a torn write erases player data on the next load. "
                        + "Direct writers found:\n  " + String.join("\n  ", offenders));
    }
}
