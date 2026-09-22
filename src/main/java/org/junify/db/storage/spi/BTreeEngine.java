package org.junify.db.storage.spi;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BTreeEngine implements StorageEngine {

    private final Path dataDir;
    private final Path indexDir;
    private final int maxNodeSize;
    private final ConcurrentHashMap<String, String> ramIndex;
    private final ReentrantReadWriteLock indexLock;
    private final Set<String> dirtyKeys;
    private final boolean asyncEnabled;
    private volatile boolean closed;

    public BTreeEngine(Path dataDir) {
        this(dataDir, 1000);
    }

    public BTreeEngine(Path dataDir, int maxNodeSize) {
        this.dataDir = dataDir;
        this.indexDir = dataDir.resolve(".btree");
        this.maxNodeSize = maxNodeSize;
        this.ramIndex = new ConcurrentHashMap<>();
        this.indexLock = new ReentrantReadWriteLock();
        this.dirtyKeys = ConcurrentHashMap.newKeySet();
        this.asyncEnabled = true;
        this.closed = false;

        try {
            Files.createDirectories(indexDir);
            loadIndex();
        } catch (IOException e) {
            throw new org.junify.db.core.exception.StorageException("Failed to initialize B-Tree engine", e);
        }
    }

    @Override
    public String name() {
        return "B_TREE";
    }

    @Override
    public void put(String collection, String key, String value) {
        checkOpen();
        String compositeKey = compositeKey(collection, key);
        
        indexLock.writeLock().lock();
        try {
            ramIndex.put(compositeKey, value);
            dirtyKeys.add(compositeKey);
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public void putAll(String collection, Map<String, String> entries) {
        checkOpen();
        indexLock.writeLock().lock();
        try {
            for (var entry : entries.entrySet()) {
                String compositeKey = compositeKey(collection, entry.getKey());
                ramIndex.put(compositeKey, entry.getValue());
                dirtyKeys.add(compositeKey);
            }
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public String get(String collection, String key) {
        checkOpen();
        String compositeKey = compositeKey(collection, key);
        
        indexLock.readLock().lock();
        try {
            return ramIndex.get(compositeKey);
        } finally {
            indexLock.readLock().unlock();
        }
    }

    @Override
    public List<String> getAll(String collection, List<String> keys) {
        return keys.stream().map(k -> get(collection, k)).collect(Collectors.toList());
    }

    @Override
    public void delete(String collection, String key) {
        checkOpen();
        String compositeKey = compositeKey(collection, key);
        
        indexLock.writeLock().lock();
        try {
            ramIndex.remove(compositeKey);
            dirtyKeys.add(compositeKey);
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public void deleteAll(String collection, List<String> keys) {
        for (String key : keys) {
            delete(collection, key);
        }
    }

    @Override
    public boolean exists(String collection, String key) {
        return get(collection, key) != null;
    }

    @Override
    public List<String> scan(String collection) {
        checkOpen();
        String prefix = collection + ":";
        List<String> results = new ArrayList<>();
        
        indexLock.readLock().lock();
        try {
            for (var entry : ramIndex.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    results.add(entry.getValue());
                }
            }
        } finally {
            indexLock.readLock().unlock();
        }
        
        return results;
    }

    @Override
    public List<String> scan(String collection, Predicate<String> filter) {
        return scan(collection).stream().filter(filter).collect(Collectors.toList());
    }

    @Override
    public Set<String> keys(String collection) {
        checkOpen();
        String prefix = collection + ":";
        Set<String> keys = new HashSet<>();
        
        indexLock.readLock().lock();
        try {
            for (var entry : ramIndex.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    keys.add(extractKey(entry.getKey()));
                }
            }
        } finally {
            indexLock.readLock().unlock();
        }
        
        return keys;
    }

    @Override
    public void flush() {
        indexLock.writeLock().lock();
        try {
            if (!dirtyKeys.isEmpty()) {
                try {
                    persistIndex();
                } catch (IOException e) {
                    System.err.println("Failed to persist B-Tree index: " + e.getMessage());
                }
                dirtyKeys.clear();
            }
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        if (closed) return;
        flush();
        closed = true;
    }

    public List<String> rangeScan(String collection, String startKey, String endKey) {
        checkOpen();
        String start = collection + ":" + startKey;
        String end = collection + ":" + endKey;
        List<String> results = new ArrayList<>();
        
        indexLock.readLock().lock();
        try {
            var sortedKeys = ramIndex.keySet().stream()
                    .filter(k -> k.compareTo(start) >= 0 && k.compareTo(end) <= 0)
                    .sorted()
                    .toList();
            for (var k : sortedKeys) {
                results.add(ramIndex.get(k));
            }
        } finally {
            indexLock.readLock().unlock();
        }
        
        return results;
    }

    public List<String> prefixScan(String collection, String prefix) {
        checkOpen();
        String compositePrefix = collection + ":" + prefix;
        List<String> results = new ArrayList<>();
        
        indexLock.readLock().lock();
        try {
            for (var entry : ramIndex.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(compositePrefix) || key.substring(key.indexOf(':') + 1).startsWith(prefix)) {
                    results.add(entry.getValue());
                }
            }
        } finally {
            indexLock.readLock().unlock();
        }
        
        return results;
    }

    private void persistIndex() throws IOException {
        Path indexFile = indexDir.resolve("btree_index.dat");
        
        try (var channel = FileChannel.open(indexFile, 
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            
            var sortedEntries = ramIndex.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList();
            
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
            
            for (var entry : sortedEntries) {
                buffer.clear();
                byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                byte[] valueBytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
                
                buffer.putInt(keyBytes.length);
                buffer.put(keyBytes);
                buffer.putInt(valueBytes.length);
                buffer.put(valueBytes);
                buffer.flip();
                channel.write(buffer);
            }
        }
    }

    private void loadIndex() throws IOException {
        Path indexFile = indexDir.resolve("btree_index.dat");
        if (!Files.exists(indexFile)) return;
        
        try (var channel = FileChannel.open(indexFile, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
            
            while (channel.position() < channel.size()) {
                buffer.clear();
                buffer.limit(Math.min(buffer.capacity(), (int)(channel.size() - channel.position())));
                
                int bytesRead = channel.read(buffer);
                if (bytesRead <= 0) break;
                
                buffer.flip();
                
                while (buffer.remaining() >= 4) {
                    int keyLen = buffer.getInt();
                    if (buffer.remaining() < keyLen) break;
                    
                    byte[] keyBytes = new byte[keyLen];
                    buffer.get(keyBytes);
                    
                    if (buffer.remaining() < 4) break;
                    int valueLen = buffer.getInt();
                    if (buffer.remaining() < valueLen) break;
                    
                    byte[] valueBytes = new byte[valueLen];
                    buffer.get(valueBytes);
                    
                    String key = new String(keyBytes, StandardCharsets.UTF_8);
                    String value = new String(valueBytes, StandardCharsets.UTF_8);
                    ramIndex.put(key, value);
                }
            }
        }
    }

    private String compositeKey(String collection, String key) {
        return collection + ":" + key;
    }

    @Override
    public Set<String> collections() {
        checkOpen();
        Set<String> collections = new HashSet<>();
        indexLock.readLock().lock();
        try {
            for (String composite : ramIndex.keySet()) {
                int idx = composite.indexOf(':');
                collections.add(idx > 0 ? composite.substring(0, idx) : composite);
            }
        } finally {
            indexLock.readLock().unlock();
        }
        return collections;
    }

    private String extractKey(String compositeKey) {
        int idx = compositeKey.indexOf(':');
        return idx > 0 ? compositeKey.substring(idx + 1) : compositeKey;
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("B-Tree engine is closed");
        }
    }

    @Override
    public int size() {
        return ramIndex.size();
    }

    @Override
    public Map<String, Object> stats() {
        return Map.of(
            "engine", name(),
            "totalEntries", ramIndex.size(),
            "indexDir", indexDir.toString(),
            "maxNodeSize", maxNodeSize,
            "dirtyKeys", dirtyKeys.size(),
            "type", "btree-persistent"
        );
    }
}
