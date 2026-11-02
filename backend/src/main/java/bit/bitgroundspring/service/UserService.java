package bit.bitgroundspring.service;

import bit.bitgroundspring.aop.Loggable;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.dto.TierDto;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.security.token.AuthTokenService;
import bit.bitgroundspring.storage.StorageService;
import bit.bitgroundspring.repository.RankRepository;
import bit.bitgroundspring.repository.SeasonRepository;
import bit.bitgroundspring.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AuthTokenService authTokenService;
    private final RankRepository rankRepository;
    private final SeasonRepository seasonRepository;

    // 탈퇴하지 않은 사용자 조회 (없으면 USER_NOT_FOUND)
    @Transactional(readOnly = true)
    public User getActiveUser(Integer userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.getIsDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    // 유저 업데이트
    @Transactional
    @Loggable("회원 정보 수정")
    public User updateUser(Integer userId, String name, String email, String imageUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.setName(name);
        user.setEmail(email);

        if (imageUrl != null && !imageUrl.equals(user.getProfileImage())) {
            // 이전 이미지 삭제 (직접 업로드한 이미지만, 소셜 프로필 URL은 그대로 둠)
            storageService.deleteByUrl(user.getProfileImage());

            // 새 이미지 저장
            user.setProfileImage(imageUrl);
        }

        return userRepository.save(user);
    }

    //이미지업로드
    public String uploadProfileImage(MultipartFile file) {
        return storageService.uploadImage("profile", file);
    }

    //isDeleted -> 1
    @Loggable("회원 탈퇴")
    public void softDeleteUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.setIsDeleted(true);
        user.setTier(0);
        user.setEmail(null);
        user.setProviderId(null);
        userRepository.save(user);
        // 모든 기기의 리프레시 토큰과 이미 발급된 액세스 토큰을 즉시 무효화
        authTokenService.revokeAll(userId);
    }

    @Transactional(readOnly = true)
    public TierDto getTierDtoForUserAndSeason(User user, Integer seasonId) {
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_FOUND));

        return rankRepository.findByUserAndSeason(user, season)
                .map(userRanking -> {
                    int tier = userRanking.getTier();
                    String tierName = getTierName(tier);
                    return new TierDto(tier, tierName);
                })
                .orElse(null);
    }

    private String getTierName(int tier) {
        return switch (tier) {
            case 1 -> "Bronze";
            case 2 -> "Silver";
            case 3 -> "Gold";
            case 4 -> "Platinum";
            case 5 -> "Diamond";
            case 6 -> "Master";
            case 7 -> "Grandmaster";
            default -> "Unranked";
        };
    }

    public Integer getCashByUserId(Integer userId) {
        return userRepository.findById(userId)
                .map(user -> user.getCash())
                .orElse(0);
    }

}