package bit.bitgroundspring.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * S3 호환 오브젝트 스토리지 설정.
 * 운영(AWS)은 endpoint·키를 비워 두고 EC2 IAM 역할로 접근하고,
 * 로컬은 S3Mock endpoint와 임의의 키를 지정한다.
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        String bucket,
        String region,
        String endpoint,
        String publicUrl,
        String accessKey,
        String secretKey
) {
    public boolean hasEndpoint() {
        return StringUtils.hasText(endpoint);
    }

    public boolean hasStaticCredentials() {
        return StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey);
    }

    // 업로드된 파일을 외부에서 읽을 때 쓰는 기본 주소
    public String resolvedPublicUrl() {
        if (StringUtils.hasText(publicUrl)) {
            return publicUrl.replaceAll("/+$", "");
        }
        if (hasEndpoint()) {
            return endpoint.replaceAll("/+$", "") + "/" + bucket;
        }
        return "https://" + bucket + ".s3." + region + ".amazonaws.com";
    }
}
