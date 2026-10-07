package org.embeddedjnosql.db;

import org.embeddedjnosql.db.index.TextIndex;
import org.embeddedjnosql.db.nosql.document.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the phrase-search contract of {@link TextIndex}: a phrase matches a document when its
 * tokens sit on consecutive positions of the indexable token stream (lower-cased, split on
 * non-alphanumeric characters, stop words and single characters dropped — identically on the
 * document and the phrase side). Guards against regression to the old first-token-only stub.
 */
@DisplayName("TextIndex phrase search — real positional matching, not a first-token stub")
class TextIndexPhraseTest {

    private static Document doc(String id, String name) {
        var d = Document.of("name", name);
        d.id(id);
        return d;
    }

    private static TextIndex indexWith(Document... docs) {
        var idx = new TextIndex("products", "name");
        for (var d : docs) {
            idx.add(d);
        }
        return idx;
    }

    @Test
    @DisplayName("a phrase requires adjacency, not just the first token")
    void phraseRequiresAdjacency() {
        var idx = indexWith(
                doc("d1", "the quick brown fox"),
                doc("d2", "quick lazy brown fox"));

        assertEquals(Set.of("d1"), idx.searchPhrases(List.of("quick brown")));
        assertEquals(Set.of("d1"), idx.searchPhrases(List.of("quick brown fox")));
        assertTrue(idx.searchPhrases(List.of("brown quick")).isEmpty(),
                "reversed order must not match an ordered phrase");
    }

    @Test
    @DisplayName("case and punctuation are transparent on both sides")
    void caseAndPunctuationTransparent() {
        var idx = indexWith(doc("d1", "The Quick, BROWN Fox!"));

        assertEquals(Set.of("d1"), idx.searchPhrases(List.of("quick brown")));
        assertEquals(Set.of("d1"), idx.searchPhrases(List.of("Quick,   BROWN!")));
    }

    @Test
    @DisplayName("a dropped token is transparent on both sides (documented contract)")
    void stopWordBetweenTokensIsTransparent() {
        var idx = indexWith(
                doc("d1", "quick the brown"),
                doc("d2", "quick lazy brown"));

        assertEquals(Set.of("d1"), idx.searchPhrases(List.of("quick brown")),
                "the stop word between the tokens is dropped on the document side, so the survivors are adjacent");
        assertEquals(Set.of("d1"), idx.searchPhrases(List.of("quick the brown")),
                "the stop word is dropped on the phrase side too, so this equals the bare phrase");
    }

    @Test
    @DisplayName("regression: a phrase starting with a stop word no longer matches nothing")
    void phraseStartingWithStopWord() {
        var idx = indexWith(doc("d1", "the quick brown fox"));

        // old stub: the first token "the" is never indexed, so the phrase matched nothing at all
        assertEquals(Set.of("d1"), idx.searchPhrases(List.of("the quick brown")));
        assertEquals(Set.of("d1"), idx.searchPhrases(List.of("the brown")));
    }

    @Test
    @DisplayName("single-token phrase is containment and agrees with search()")
    void singleTokenPhraseIsContainment() {
        var idx = indexWith(doc("d1", "alpha beta"), doc("d2", "gamma alpha"), doc("d3", "delta"));

        assertEquals(Set.of("d1", "d2"), idx.searchPhrases(List.of("alpha")));
        assertEquals(idx.search("alpha"), idx.searchPhrases(List.of("alpha")),
                "a one-token phrase and a one-token query must resolve to the same documents");
    }

    @Test
    @DisplayName("stop-word-only and empty phrases match nothing; null phrases are skipped")
    void degeneratePhrases() {
        var idx = indexWith(doc("d1", "alpha beta"));

        assertTrue(idx.searchPhrases(List.of()).isEmpty());
        assertTrue(idx.searchPhrases(List.of("the", "of and")).isEmpty());
        assertTrue(idx.searchPhrases(List.of("a", "x")).isEmpty(),
                "single-character tokens are not indexable, so such a phrase matches nothing");
        assertTrue(idx.searchPhrases(java.util.Arrays.asList(null, "alpha")).contains("d1"),
                "a null phrase must be skipped without breaking the rest of the list");
        assertTrue(idx.searchPhrases(null).isEmpty(), "null list must return an empty set, never throw");
    }

    @Test
    @DisplayName("repeated tokens inside a phrase match consecutive occurrences")
    void repeatedTokenPhrase() {
        var idx = indexWith(doc("d1", "brown brown fox"), doc("d2", "brown fox"));

        assertEquals(Set.of("d1"), idx.searchPhrases(List.of("brown brown")));
        assertEquals(Set.of("d1", "d2"), idx.searchPhrases(List.of("brown fox")));
        assertTrue(idx.searchPhrases(List.of("fox brown")).isEmpty());
    }

    @Test
    @DisplayName("phrases are OR-ed across the list")
    void phrasesAreOrEd() {
        var idx = indexWith(
                doc("d1", "red apple pie"),
                doc("d2", "green apple sky"),
                doc("d3", "red car"));

        assertEquals(Set.of("d1", "d2"), idx.searchPhrases(List.of("red apple", "green apple")));
        assertEquals(Set.of("d1", "d3"), idx.searchPhrases(List.of("red apple", "red car")));
    }

    @Test
    @DisplayName("remove() clears positions so removed docs stop matching")
    void removeClearsPositions() {
        var idx = indexWith(doc("d1", "quick brown fox"), doc("d2", "quick brown bear"));
        assertEquals(Set.of("d1", "d2"), idx.searchPhrases(List.of("quick brown")));

        idx.remove(doc("d1", "quick brown fox"));
        assertEquals(Set.of("d2"), idx.searchPhrases(List.of("quick brown")));

        idx.remove(doc("d2", "quick brown bear"));
        assertTrue(idx.searchPhrases(List.of("quick brown")).isEmpty(),
                "no stale positions may survive removal");
    }

    @Test
    @DisplayName("re-adding a document replaces its positions instead of duplicating them")
    void reAddReplacesPositions() {
        var idx = new TextIndex("products", "name");
        var d1 = doc("d1", "alpha beta");
        idx.add(d1);
        idx.add(d1);
        assertEquals(Set.of("d1"), idx.searchPhrases(List.of("alpha beta")));

        idx.remove(d1);
        idx.add(doc("d1", "beta gamma"));
        assertTrue(idx.searchPhrases(List.of("alpha beta")).isEmpty());
        assertEquals(Set.of("d1"), idx.searchPhrases(List.of("beta gamma")));
    }

    @Test
    @DisplayName("only the indexed field participates")
    void otherFieldsIgnored() {
        var idx = new TextIndex("products", "name");
        var d = Document.of("name", "quick brown").add("description", "brown quick");
        d.id("d1");
        idx.add(d);

        assertEquals(Set.of("d1"), idx.searchPhrases(List.of("quick brown")));
        assertTrue(idx.searchPhrases(List.of("brown quick")).isEmpty(),
                "the description field must not leak into a name-field phrase match");
    }
}
