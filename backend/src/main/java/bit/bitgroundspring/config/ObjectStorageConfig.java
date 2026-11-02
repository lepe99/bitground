package bit.bitgroundspring.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ncp")
@Getter
@Setter
public class ObjectStorageConfig {
    private String accessKey;
    private String secretKey;
    private String regionName;
    private String endPoint;
}
