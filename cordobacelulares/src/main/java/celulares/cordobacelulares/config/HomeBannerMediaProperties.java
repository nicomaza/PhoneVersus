package celulares.cordobacelulares.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "home.banner-media")
public class HomeBannerMediaProperties {

    private Path directory = Path.of("./data/home-banner-media");
    private long maxSizeBytes = 5L * 1024L * 1024L;

    public Path getDirectory() { return directory; }
    public void setDirectory(Path directory) { this.directory = directory; }
    public long getMaxSizeBytes() { return maxSizeBytes; }
    public void setMaxSizeBytes(long maxSizeBytes) { this.maxSizeBytes = maxSizeBytes; }
}
