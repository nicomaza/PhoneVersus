package celulares.cordobacelulares.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "home_banner",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_home_banner_desktop_filename", columnNames = "desktop_stored_filename"),
                @UniqueConstraint(name = "uk_home_banner_mobile_filename", columnNames = "mobile_stored_filename")
        },
        indexes = @Index(name = "idx_home_banner_active_order", columnList = "active, sort_order, id")
)
@Getter
@Setter
@NoArgsConstructor
public class HomeBanner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "alt_text", nullable = false, length = 500)
    private String altText;

    @Column(name = "link_url", length = 2048)
    private String linkUrl;

    @Column(name = "desktop_stored_filename", nullable = false, unique = true, length = 100)
    private String desktopStoredFilename;

    @Column(name = "desktop_public_path", nullable = false, length = 255)
    private String desktopPublicPath;

    @Column(name = "desktop_original_filename", nullable = false, length = 512)
    private String desktopOriginalFilename;

    @Column(name = "desktop_mime_type", nullable = false, length = 50)
    private String desktopMimeType;

    @Column(name = "mobile_stored_filename", unique = true, length = 100)
    private String mobileStoredFilename;

    @Column(name = "mobile_public_path", length = 255)
    private String mobilePublicPath;

    @Column(name = "mobile_original_filename", length = 512)
    private String mobileOriginalFilename;

    @Column(name = "mobile_mime_type", length = 50)
    private String mobileMimeType;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
