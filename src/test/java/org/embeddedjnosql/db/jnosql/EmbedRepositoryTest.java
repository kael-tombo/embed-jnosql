package org.embeddedjnosql.db.jnosql;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import jakarta.nosql.Id;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.adapter.jnosql.EmbedRepository;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Eclipse JNoSQL EmbedRepository Tests")
class EmbedRepositoryTest {

    private EmbedJNoSQL db;
    private EmbedRepository<Book, String> repo;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.create(EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY)
                .buildConfig());
        repo = EmbedRepository.of(Book.class, db);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null) db.close();
    }

    @Entity("books")
    static class Book {
        @Id
        private String isbn;

        @Column("book_title")
        private String title;

        @Column
        private String author;

        @Column
        private double price;

        public Book() {}

        public Book(String isbn, String title, String author, double price) {
            this.isbn = isbn;
            this.title = title;
            this.author = author;
            this.price = price;
        }

        public String getIsbn() { return isbn; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAuthor() { return author; }
        public double getPrice() { return price; }
    }

    @Test
    @DisplayName("Repository: save, findById, existsById, count, deleteById")
    void testBasicRepositoryCrud() {
        Book b1 = new Book("978-0134685991", "Effective Java", "Joshua Bloch", 45.0);
        Book b2 = new Book("978-0132350884", "Clean Code", "Robert C. Martin", 40.0);

        repo.save(b1);
        repo.save(b2);

        assertEquals(2, repo.count());
        assertTrue(repo.existsById("978-0134685991"));

        Optional<Book> found = repo.findById("978-0134685991");
        assertTrue(found.isPresent());
        assertEquals("Effective Java", found.get().getTitle());

        // Update
        b1.setTitle("Effective Java (3rd Edition)");
        repo.save(b1);

        Book updated = repo.findById("978-0134685991").orElseThrow();
        assertEquals("Effective Java (3rd Edition)", updated.getTitle());
        assertEquals(2, repo.count());

        // Delete
        repo.deleteById("978-0132350884");
        assertEquals(1, repo.count());
        assertFalse(repo.existsById("978-0132350884"));
    }

    @Test
    @DisplayName("Repository: findBy field and native entity range query")
    void testRepositoryQueries() {
        repo.save(new Book("B-1", "Design Patterns", "GoF", 55.0));
        repo.save(new Book("B-2", "Refactoring", "Martin Fowler", 50.0));
        repo.save(new Book("B-3", "Domain-Driven Design", "Eric Evans", 60.0));

        List<Book> byAuthor = repo.findBy("author", "Martin Fowler");
        assertEquals(1, byAuthor.size());
        assertEquals("Refactoring", byAuthor.get(0).getTitle());

        List<Book> expensive = db.from(Book.class)
                .where("price >= ?", 55.0)
                .orderBy("price DESC")
                .list();
        assertEquals(2, expensive.size());
        assertEquals("Domain-Driven Design", expensive.get(0).getTitle());
        assertEquals("Design Patterns", expensive.get(1).getTitle());
    }
}
