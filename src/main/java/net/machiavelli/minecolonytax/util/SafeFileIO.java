package net.machiavelli.minecolonytax.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Atomic file writes for the mod's JSON persistence.
 *
 * <p>Every state file (taxes, war chests, factions, occupations, spy missions, history, ...)
 * used to be written straight into its final path with a plain FileWriter. A crash or kill
 * mid-write left a truncated file, which the loaders then treated as "no data, starting
 * fresh" - silently erasing player progress. This helper writes to a sibling .tmp file and
 * renames it over the target, so the on-disk file is always either the old or the new state,
 * never a torn one.
 */
public final class SafeFileIO {

    private SafeFileIO() {}

    /** Writes {@code content} to {@code file} atomically (tmp + rename). Creates parent dirs. */
    public static void writeStringAtomic(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Path tmp = file.toPath().resolveSibling(file.getName() + ".tmp");
        Files.write(tmp, content.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException atomicEx) {
            // Some filesystems (notably across drive letters on Windows) cannot ATOMIC_MOVE.
            Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Overload for callers that hold the path as a String. */
    public static void writeStringAtomic(String path, String content) throws IOException {
        writeStringAtomic(new File(path), content);
    }
}
