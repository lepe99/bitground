package bit.bitgroundspring.security.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 소셜 로그인이 준 프로필 사진이 그 서비스의 '기본 프로필 사진'(사진을 올리지 않은 사용자에게 주는 그림)인지 판단한다.
 * 기본 사진이면 가입할 때 저장하지 않고, 화면에서는 우리 서비스의 기본 프로필 사진(DefaultAvatar)이 보이게 한다.
 * - 카카오: 사용자 정보 API의 kakao_account.profile.is_default_image
 * - 구글: People API 사진의 default (OIDC picture 주소만으로는 이니셜 그림과 실제 사진을 구분할 수 없다)
 * - 네이버: 기본 사진 주소가 하나로 고정되어 있어 주소로 비교
 * 확인 API 호출이 실패하면 기본 사진이 아닌 것으로 본다 (실제 사진을 잘못 지우지 않도록)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultProfileImageDetector {

    static final String NAVER_DEFAULT_IMAGE = "https://ssl.pstatic.net/static/pwe/address/img_profile.png";
    private static final String KAKAO_USER_URL = "https://kapi.kakao.com/v2/user/me";
    private static final String GOOGLE_PHOTOS_URL = "https://people.googleapis.com/v1/people/me?personFields=photos";

    private final RestTemplate restTemplate;

    /** 기본 사진이면 null, 아니면 받은 주소 그대로 */
    public String keepIfCustom(String provider, String imageUrl, String accessToken) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        boolean isDefault = switch (provider) {
            case "naver" -> isNaverDefault(imageUrl);
            case "kakao" -> isKakaoDefault(accessToken);
            case "google" -> isGoogleDefault(accessToken);
            default -> false;
        };
        return isDefault ? null : imageUrl;
    }

    static boolean isNaverDefault(String imageUrl) {
        return imageUrl.replaceFirst("^http://", "https://").equals(NAVER_DEFAULT_IMAGE);
    }

    @SuppressWarnings("unchecked")
    private boolean isKakaoDefault(String accessToken) {
        Map<String, Object> body = get(KAKAO_USER_URL, accessToken, "카카오");
        Map<String, Object> account = body == null ? null : (Map<String, Object>) body.get("kakao_account");
        Map<String, Object> profile = account == null ? null : (Map<String, Object>) account.get("profile");
        return profile != null && Boolean.TRUE.equals(profile.get("is_default_image"));
    }

    @SuppressWarnings("unchecked")
    private boolean isGoogleDefault(String accessToken) {
        Map<String, Object> body = get(GOOGLE_PHOTOS_URL, accessToken, "구글");
        List<Map<String, Object>> photos = body == null ? null : (List<Map<String, Object>>) body.get("photos");
        if (photos == null || photos.isEmpty()) {
            return false;
        }
        // 대표 사진(metadata.primary)이 기본 그림인지 본다
        Map<String, Object> primary = photos.stream()
                .filter(p -> p.get("metadata") instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("primary")))
                .findFirst()
                .orElse(photos.getFirst());
        return Boolean.TRUE.equals(primary.get("default"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String url, String accessToken, String providerName) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
        } catch (Exception e) {
            log.warn("{} 기본 프로필 사진 확인 실패, 받은 사진을 그대로 씀: {}", providerName, e.getMessage());
            return null;
        }
    }
}
