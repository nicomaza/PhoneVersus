package celulares.cordobacelulares.repository;

import celulares.cordobacelulares.entities.HomeBanner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class HomeBannerRepositoryTest {

    @Autowired
    private HomeBannerRepository repository;

    @Test
    void returnsOnlyActiveBannersOrderedByOrderThenId() {
        HomeBanner later = repository.save(banner("later", true, 2));
        HomeBanner first = repository.save(banner("first", true, 0));
        repository.save(banner("inactive", false, 1));
        HomeBanner secondSameOrder = repository.save(banner("same-order-a", true, 1));
        HomeBanner thirdSameOrder = repository.save(banner("same-order-b", true, 1));

        List<HomeBanner> result = repository.findAllByActiveTrueOrderBySortOrderAscIdAsc();

        assertThat(result).extracting(HomeBanner::getId)
                .containsExactly(first.getId(), secondSameOrder.getId(), thirdSameOrder.getId(), later.getId());
    }

    private HomeBanner banner(String name, boolean active, int sortOrder) {
        HomeBanner banner = new HomeBanner();
        banner.setTitle(name);
        banner.setAltText(name);
        banner.setActive(active);
        banner.setSortOrder(sortOrder);
        banner.setDesktopStoredFilename(name + ".png");
        banner.setDesktopPublicPath("/home-banner-media/" + name + ".png");
        banner.setDesktopOriginalFilename(name + ".png");
        banner.setDesktopMimeType("image/png");
        return banner;
    }
}
