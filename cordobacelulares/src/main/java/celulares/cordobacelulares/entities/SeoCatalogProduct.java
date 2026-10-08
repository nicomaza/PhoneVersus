package celulares.cordobacelulares.entities;

import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "seo_catalog_product",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seo_catalog_product_slug",
                columnNames = "slug"
        ),
        indexes = @Index(
                name = "idx_seo_catalog_product_active_slug",
                columnList = "active, slug"
        )
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SeoCatalogProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "slug", nullable = false, length = 512)
    private String slug;

    @Column(name = "marca", nullable = false, length = 120)
    private String marca;

    @Column(name = "modelo", nullable = false, length = 512)
    private String modelo;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", length = 40)
    private CatalogProductOrigin origen;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (firstSeenAt == null) {
            firstSeenAt = now;
        }
        if (lastSeenAt == null) {
            lastSeenAt = firstSeenAt;
        }
    }
}
