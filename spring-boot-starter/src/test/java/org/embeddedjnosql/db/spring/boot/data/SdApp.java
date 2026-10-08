package org.embeddedjnosql.db.spring.boot.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * A minimal application exercising the Spring Data repository surface: interfaces extending
 * {@link JpaRepository} (one derived queries + paging, one bare CRUD) declared against
 * document-stored entities, wired through {@code @EnableEmbedJpaRepositories}.
 */
@SpringBootApplication
@EnableEmbedJpaRepositories
public class SdApp {

    @Entity
    @Table(name = "sd_items")
    public static class SdItem {
        @Id
        String id;
        String title;
        @Column(name = "unit_price")
        double price;
        Instant createdAt;
        boolean active;

        public SdItem() {
        }

        SdItem(String id, String title, double price, Instant createdAt, boolean active) {
            this.id = id;
            this.title = title;
            this.price = price;
            this.createdAt = createdAt;
            this.active = active;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public double getPrice() { return price; }
        public Instant getCreatedAt() { return createdAt; }
        public boolean isActive() { return active; }
    }

    public interface SdItemRepository extends JpaRepository<SdItem, String> {
        List<SdItem> findByTitleIgnoreCase(String title);

        List<SdItem> findByPriceLessThan(double price);

        List<SdItem> findByActiveTrue();

        List<SdItem> findByTitleStartingWith(String prefix);

        long countByActiveFalse();

        List<SdItem> findByOrderByPriceDesc();

        boolean existsByTitle(String title);

        List<SdItem> findFirst2ByPriceGreaterThanOrderByPriceAsc(double price);
    }

    public interface BareRepository extends JpaRepository<SdItem, String> {
    }
}
