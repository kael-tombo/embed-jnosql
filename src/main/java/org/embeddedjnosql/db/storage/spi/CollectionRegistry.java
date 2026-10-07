package org.embeddedjnosql.db.storage.spi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Disk-backed record of collections that were explicitly created, for engines whose
 * collection identity is otherwise derived from the records they hold.
 *
 * <p><b>R-62 (2026-09-23):</b> {@code FILE} persists one snapshot file per collection, so an
 * empty collection is naturally durable there. {@code LSM_TREE} and {@code B_TREE} address
 * data by {@code collection:key} — their {@code collections()} is derived from memtable and
 * sstable/index keys — so a collection with no records has no representation at all, and
 * {@code CREATE TABLE t (id INT)} disappeared on restart. This registry gives those engines
 * the missing identity: one appended line per created collection, written atomically.</p>
 *
 * <p>Collections in EmbedJNoSQL are never dropped wholesale ({@code DROP TABLE} removes rows,
 * and there is no delete-collection API — R-63), so the registry is monotone and needs no
 * removal path. It is deliberately not a data file: it carries names only, never content.</p>
 */
final class CollectionRegistry {

    private static final String FILE_NAME = ".collections";

    private final Path file;
    private final Set<String> names = ConcurrentHashMap.newKeySet();

    CollectionRegistry(Path dataDir) {
        this.file = dataDir.resolve(FILE_NAME);
        this.names.addAll(read());
    }

    /** Records {@code name} as existing and persists the registry. Returns false for a blank name. */
    boolean mark(String name) {
        if (name == null || name.isBlank()) return false;
        boolean added = names.add(name);
        write();
        return added;
    }

    /** Names recorded so far, including any read back from a previous run. */
    Set<String> names() {
        return Set.copyOf(names);
    }

    private List<String> read() {
        try {
            if (!Files.exists(file)) return List.of();
            return Files.readAllLines(file).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
        } catch (IOException e) {
            // A registry that cannot be read must not block startup: the engine's own data
            // still loads, and a missing name only costs an empty collection its identity.
            System.err.println("CollectionRegistry: could not read " + file + ": " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Writes the registry through a temp file plus atomic move, so a crash mid-write cannot
     * leave a torn name list behind — the same guarantee the FILE engine's snapshots use.
     */
    private synchronized void write() {
        var tmp = file.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            var sorted = new LinkedHashSet<>(names);
            Files.write(tmp, sorted.stream().sorted().toList());
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("CollectionRegistry: could not persist " + file + ": " + e.getMessage());
        }
    }
}
