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
@Table(name = "seo_product_highlight")
@Getter
@Setter
@NoArgsConstructor
public class SeoProductHighlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_id", nullable = false)
    private SeoProductContent content;

    @Column(name = "text", nullable = false, length = 1000)
    private String text;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "source_key", length = 512)
    private String sourceKey;

    @Column(name = "source_text", length = 1000)
    private String sourceText;

    @Column(name = "manually_edited", nullable = false)
    private boolean manuallyEdited;
}
