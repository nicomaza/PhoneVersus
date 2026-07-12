package celulares.cordobacelulares;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"tiendaporte.catalog-cache.initial-delay-ms=600000",
		"tiendaporte.catalog-cache.refresh-ms=600000"
})
class CordobacelularesApplicationTests {

	@Test
	void contextLoads() {
	}

}
