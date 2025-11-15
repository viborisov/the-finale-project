package searchengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "indexing.http")
public class Http {

    private String userAgent;
    private String referrer;
    private int timeout;
}
