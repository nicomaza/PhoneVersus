package celulares.cordobacelulares.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "seo_product_spec")
@Getter
@Setter
@NoArgsConstructor
public class SeoProductSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private SeoProductSpecSection section;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    @Column(name = "value", nullable = false, length = 2000)
    private String value;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "source_key", length = 512)
    private String sourceKey;

    @Column(name = "source_label", length = 255)
    private String sourceLabel;

    @Column(name = "source_value", length = 2000)
    private String sourceValue;

    @Column(name = "manually_edited", nullable = false)
    private boolean manuallyEdited;
}
