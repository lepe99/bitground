package bit.bitgroundspring.security.oauth2;

import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.UserRepository;
import bit.bitgroundspring.util.InitialCashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {
    private final UserRepository userRepository;
    private final InitialCashUtil initialCashUtil;
    private final RestTemplate restTemplate;
    
    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        
        // OIDC 사용자 정보 가져오기
        String provider = userRequest.getClientRegistration().getRegistrationId();
        
        if (provider.equals("naver")) {
            return processNaverUser(userRequest, provider);
        } else {
            return processDefaultUser(userRequest, provider);
        }
    }
    
    // 일반 사용자 처리
    private OidcUser processDefaultUser(OidcUserRequest userRequest, String provider) {
        
        OidcUser oidcUser = super.loadUser(userRequest);
        String providerId = oidcUser.getSubject();
        
        // 사용자 정보에서 필요한 데이터 추출
        Map<String, Object> attributes = oidcUser.getAttributes();
        
        // 사용자 정보 저장
        User user = saveUserInfo(provider, attributes, providerId);
        
        
        // OidcUser 객체 반환 (인증 정보 포함)
        return new DefaultOidcUser(
                Collections.singleton(new SimpleGrantedAuthority(user.getRole().name())),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "sub"
        );
    }
    
    // 네이버 사용자 처리
    private OidcUser processNaverUser(OidcUserRequest userRequest, String provider) {
        // id token 가져오기
        OidcIdToken idToken = userRequest.getIdToken();
        String providerId = idToken.getSubject();
        
        // OAuth2 접근 토큰 가져오기
        OAuth2AccessToken accessToken = userRequest.getAccessToken();
        
        // 네이버 API에서 사용자 정보 직접 가져오기
        Map<String, Object> userAttributes = getUserInfo(accessToken);
        Map<String, Object> attributes = (Map<String, Object>) userAttributes.get("response");
        
        // 사용자 정보 저장
        User user = saveUserInfo(provider, attributes, providerId);
        
        // OidcUser 객체 반환 (인증 정보 포함)
        OidcUserInfo userInfo = new OidcUserInfo(Collections.singletonMap("sub", providerId));
        
        return new DefaultOidcUser(
                Collections.singleton(new SimpleGrantedAuthority(user.getRole().name())),
                idToken,
                userInfo,
                "sub"
        );
    }
    
    // 네이버 API에서 사용자 정보 가져오기
    private Map<String, Object> getUserInfo(OAuth2AccessToken accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.getTokenValue());
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                "https://openapi.naver.com/v1/nid/me",
                HttpMethod.GET,
                entity,
                Map.class
        );
        
        return response.getBody();
    }
    
    // 사용자 정보 처리 메서드
    private User saveUserInfo(String provider, Map<String, Object> attributes, String providerId) {
        String email = extractEmail(provider, attributes);
        String name = extractName(provider, attributes);
        String profileImage = extractProfileImage(provider, attributes);
        
        // 사용자 존재 여부 확인
        Optional<User> existingUser = userRepository.findByProviderAndProviderId(provider, providerId);
        
        User user;
        if (existingUser.isPresent()) {
            // 기존 사용자 정보 업데이트
            user = existingUser.get();
        } else {
            // 새로운 사용자 생성
            user = User.builder()
                    .provider(provider)
                    .providerId(providerId)
                    .email(email)
                    .name(name)
                    .cash(initialCashUtil.getInitialCash()) // 초기 캐시 설정
                    .profileImage(profileImage)
                    .build();
            
            // 사용자 정보 저장
            userRepository.save(user);
        }
        return user;
    }
    
    // 제공자별 닉네임 추출 메서드
    private String extractName(String provider, Map<String, Object> attributes) {
        if (provider.equals("google")) return (String) attributes.get("name");
        else if (provider.equals("kakao") || provider.equals("naver")) return (String) attributes.get("nickname");
        else return null;
    }
    
    // 제공자별 이메일 추출 메서드
    private String extractEmail(String provider, Map<String, Object> attributes) {
        if (provider.equals("google") || provider.equals("naver")) return (String) attributes.get("email");
        else return null;
    }
    
    // 제공자별 프로필 이미지 추출 메서드
    private String extractProfileImage(String provider, Map<String, Object> attributes) {
        if (provider.equals("google") || provider.equals("kakao")) return (String) attributes.get("picture");
        else if (provider.equals("naver")) return (String) attributes.get("profile_image");
        else return null;
    }
}