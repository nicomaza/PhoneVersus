package celulares.cordobacelulares.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "blocked_catalog_product",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_blocked_catalog_product_origin_external_id",
                columnNames = {"origen", "external_product_id"}
        ),
        indexes = @Index(
                name = "idx_blocked_catalog_product_origin_model_key",
                columnList = "origen, normalized_model_key"
        )
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BlockedCatalogProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "origen", nullable = false, length = 40)
    private String origen;

    @Column(name = "external_product_id", nullable = false)
    private Long externalProductId;

    @Column(name = "marca", nullable = false, length = 120)
    private String marca;

    @Column(name = "modelo", nullable = false, length = 512)
    private String modelo;

    @Column(name = "precio_usd", precision = 19, scale = 4)
    private BigDecimal precioUsd;

    @Column(name = "normalized_model_key", nullable = false, length = 768)
    private String normalizedModelKey;

    @Column(name = "blocked_at", nullable = false)
    private Instant blockedAt;

    @PrePersist
    public void prePersist() {
        if (blockedAt == null) {
            blockedAt = Instant.now();
        }
    }
}
