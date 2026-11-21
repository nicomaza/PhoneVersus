package celulares.cordobacelulares.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "seo_product_content",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seo_product_content_catalog_product",
                columnNames = "seo_catalog_product_id"
        )
)
@Getter
@Setter
@NoArgsConstructor
public class SeoProductContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seo_catalog_product_id", nullable = false)
    private SeoCatalogProduct seoCatalogProduct;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "source_name", length = 255)
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SeoProductContentStatus status = SeoProductContentStatus.DRAFT;

    @Column(name = "published_snapshot", columnDefinition = "TEXT")
    private String publishedSnapshot;

    @Column(name = "draft_dirty", nullable = false)
    private boolean draftDirty;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<SeoProductHighlight> highlights = new ArrayList<>();

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<SeoProductSpecSection> specSections = new ArrayList<>();

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("primaryImage DESC, sortOrder ASC, id ASC")
    private List<SeoProductImage> images = new ArrayList<>();

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = SeoProductContentStatus.DRAFT;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
