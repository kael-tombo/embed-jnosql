package org.junify.db.index;

import org.junify.db.nosql.document.Document;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A per-field inverted text index over {@link Document} ids.
 *
 * <p>Documents and queries pass through the same pipeline: lower-case, split on
 * non-alphanumeric characters, then drop stop words and single-character tokens — only tokens
 * that survive that pipeline are indexed. {@link #search(String)} intersects the per-token
 * posting sets; {@link #searchPhrases(List)} additionally requires the phrase's tokens to sit on
 * consecutive positions of the surviving token stream, tracked in a parallel positional map.
 * Both structures are cleaned up symmetrically by {@link #remove(Document)}.</p>
 *
 * <p>This is a library-side utility a consumer maintains explicitly via
 * {@link #add(Document)} / {@link #remove(Document)}; it is not wired into the storage engine or
 * the console REST API.</p>
 */
public class TextIndex {

    private final String collection;
    private final String field;
    private final Map<String, Set<String>> invertedIndex;
    /** token -> doc id -> ascending positions of the token in the doc's indexable token stream */
    private final Map<String, Map<String, List<Integer>>> termPositions;
    private final Set<String> stopWords;

    private static final Set<String> DEFAULT_STOP_WORDS = Set.of(
        "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "as", "is", "was", "are", "were", "been",
        "be", "have", "has", "had", "do", "does", "did", "will", "would", "could",
        "should", "may", "might", "must", "can", "this", "that", "these", "those"
    );

    public TextIndex(String collection, String field) {
        this(collection, field, DEFAULT_STOP_WORDS);
    }

    public TextIndex(String collection, String field, Set<String> stopWords) {
        this.collection = collection;
        this.field = field;
        this.invertedIndex = new ConcurrentHashMap<>();
        this.termPositions = new ConcurrentHashMap<>();
        this.stopWords = new HashSet<>(stopWords);
    }

    public void add(Document doc) {
        if (!doc.has(field)) return;
        
        var value = doc.getRaw(field);
        if (value == null) return;
        
        var text = value.toString().toLowerCase();
        var indexable = indexableTokens(tokenize(text));

        Map<String, List<Integer>> docPositions = new HashMap<>();
        for (int position = 0; position < indexable.size(); position++) {
            var token = indexable.get(position);
            invertedIndex.computeIfAbsent(token, k -> ConcurrentHashMap.newKeySet()).add(doc.id());
            docPositions.computeIfAbsent(token, k -> new ArrayList<>()).add(position);
        }

        for (var entry : docPositions.entrySet()) {
            termPositions.computeIfAbsent(entry.getKey(), k -> new ConcurrentHashMap<>())
                    .put(doc.id(), entry.getValue());
        }
    }

    public void remove(Document doc) {
        if (!doc.has(field)) return;
        
        var value = doc.getRaw(field);
        if (value == null) return;
        
        var text = value.toString().toLowerCase();
        var tokens = tokenize(text);
        
        for (var token : tokens) {
            var ids = invertedIndex.get(token);
            if (ids != null) {
                ids.remove(doc.id());
                if (ids.isEmpty()) {
                    invertedIndex.remove(token);
                }
            }

            var posting = termPositions.get(token);
            if (posting != null) {
                posting.remove(doc.id());
                if (posting.isEmpty()) {
                    termPositions.remove(token);
                }
            }
        }
    }

    public Set<String> search(String query) {
        var queryTokens = tokenize(query.toLowerCase());
        Set<String> result = null;
        
        for (var token : queryTokens) {
            if (stopWords.contains(token) || token.length() < 2) continue;
            
            var tokenDocs = invertedIndex.get(token);
            if (tokenDocs != null) {
                if (result == null) {
                    result = new HashSet<>(tokenDocs);
                } else {
                    result.retainAll(tokenDocs);
                }
            }
        }
        
        return result != null ? result : Collections.emptySet();
    }

    /**
     * Phrase search: the ids of documents matching ANY of the phrases.
     *
     * <p>A document matches a phrase when the phrase's tokens occur on consecutive positions of
     * the document's indexable token stream — the exact stream built by {@link #add(Document)}:
     * lower-cased, split on non-alphanumeric characters, stop words and single-character tokens
     * dropped. Both sides use the same pipeline, so {@code "quick brown"} matches
     * {@code "The Quick, brown fox!"} and also {@code "quick; the; brown"} (a dropped token is
     * transparent on both sides), but not {@code "quick lazy brown"}. A single-token phrase is
     * containment; a phrase with no indexable tokens (stop words only, or empty) matches nothing;
     * null phrases are skipped. Never returns null.</p>
     */
    public Set<String> searchPhrases(List<String> phrases) {
        var result = new HashSet<String>();
        if (phrases == null) {
            return result;
        }

        for (var phrase : phrases) {
            if (phrase == null) continue;
            var phraseTokens = indexableTokens(tokenize(phrase.toLowerCase()));
            if (phraseTokens.isEmpty()) continue;

            var firstDocs = invertedIndex.get(phraseTokens.get(0));
            if (firstDocs == null) continue;

            for (var docId : firstDocs) {
                if (matchesPhrase(docId, phraseTokens)) {
                    result.add(docId);
                }
            }
        }

        return result;
    }

    /** True when phraseTokens sit on consecutive positions of the document's token stream. */
    private boolean matchesPhrase(String docId, List<String> phraseTokens) {
        var startsByDoc = termPositions.get(phraseTokens.get(0));
        if (startsByDoc == null) {
            return false;
        }
        var starts = startsByDoc.get(docId);
        if (starts == null) {
            return false;
        }

        for (int start : starts) {
            boolean allConsecutive = true;
            for (int i = 1; i < phraseTokens.size() && allConsecutive; i++) {
                var posting = termPositions.get(phraseTokens.get(i));
                var positions = posting == null ? null : posting.get(docId);
                allConsecutive = positions != null && positions.contains(start + i);
            }
            if (allConsecutive) {
                return true;
            }
        }
        return false;
    }

    /** The tokens of the indexable stream, in order: stop words and single characters removed. */
    private List<String> indexableTokens(List<String> tokens) {
        var result = new ArrayList<String>(tokens.size());
        for (var token : tokens) {
            if (!stopWords.contains(token) && token.length() > 1) {
                result.add(token);
            }
        }
        return result;
    }

    private List<String> tokenize(String text) {
        var tokens = new ArrayList<String>();
        var current = new StringBuilder();
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                current.append(c);
            } else if (current.length() > 0) {
                tokens.add(current.toString());
                current.setLength(0);
            }
        }
        
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        
        return tokens;
    }

    public long size() {
        return invertedIndex.values().stream().mapToLong(Set::size).sum();
    }

    public boolean isEmpty() {
        return invertedIndex.isEmpty();
    }

    public String field() {
        return field;
    }

    public String collection() {
        return collection;
    }

    public Map<String, Set<String>> toMap() {
        return Map.copyOf(invertedIndex);
    }

    public Set<String> getIndexedTerms() {
        return Set.copyOf(invertedIndex.keySet());
    }
}
