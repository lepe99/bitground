package bit.bitgroundspring.service;

import bit.bitgroundspring.aop.Loggable;
import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.dto.response.TierDto;
import bit.bitgroundspring.entity.Season;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.security.token.AuthTokenService;
import bit.bitgroundspring.storage.StorageService;
import bit.bitgroundspring.repository.UserRankingRepository;
import bit.bitgroundspring.repository.SeasonRepository;
import bit.bitgroundspring.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Pattern;


@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AuthTokenService authTokenService;
    private final UserRankingRepository userRankingRepository;
    private final SeasonRepository seasonRepository;

    // 탈퇴하지 않은 사용자 조회 (없으면 USER_NOT_FOUND)
    @Transactional(readOnly = true)
    public User getActiveUser(Integer userId) {
        User user = userRepository.findById(userId)
                .filter(u -> !u.getIsDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.isSuspended()) {
            throw new BusinessException(ErrorCode.USER_SUSPENDED);
        }
        return user;
    }

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** 이번 시즌 티어. 아직 이번 시즌 랭킹에 없으면 지난 시즌 확정 티어(users.tier) */
    @Transactional(readOnly = true)
    public int getCurrentTier(User user) {
        return userRankingRepository.findCurrentSeasonTiers(user.getId()).stream()
                .findFirst()
                .orElse(user.getTier() == null ? 0 : user.getTier());
    }

    /**
     * 프로필 수정 (처음 가입 후 확인 화면 /welcome 과 마이페이지가 함께 쓴다). 저장하면 첫 화면을 마친 것으로 본다
     * @param email     비우면 이메일 없음 (카카오처럼 이메일을 주지 않는 로그인)
     * @param imageUrl  새로 올린 사진 주소. null이면 사진은 그대로
     * @param removeImage true면 사진을 지워 기본 프로필 사진으로 (새 사진이 함께 오면 새 사진이 우선)
     */
    @Transactional
    @Loggable("회원 정보 수정")
    public User updateUser(Integer userId, String name, String email, String imageUrl, boolean removeImage) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.length() < 2 || trimmedName.length() > 8) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "닉네임은 2자 이상 8자 이하로 입력해주세요.");
        }
        String trimmedEmail = email == null ? "" : email.trim();
        if (!trimmedEmail.isEmpty() && !EMAIL.matcher(trimmedEmail).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "올바른 이메일 형식이 아닙니다.");
        }

        user.setName(trimmedName);
        user.setEmail(trimmedEmail.isEmpty() ? null : trimmedEmail);
        user.setIsOnboarded(true);

        String previousImage = user.getProfileImage();
        if (imageUrl != null && !imageUrl.equals(previousImage)) {
            user.setProfileImage(imageUrl);
        } else if (removeImage) {
            user.setProfileImage(null); // 화면에서는 기본 프로필 사진으로 보인다
        }
        // 바뀐 경우 이전 사진 파일 삭제 (직접 업로드한 이미지만, 소셜 프로필 URL은 무시됨)
        if (previousImage != null && !previousImage.equals(user.getProfileImage())) {
            storageService.deleteByUrl(previousImage);
        }

        return userRepository.save(user);
    }

    //이미지업로드
    public String uploadProfileImage(MultipartFile file) {
        return storageService.uploadImage("profile", file);
    }

    // 탈퇴한 회원의 이름 대신 보여줄 표시 (게시글·지난 시즌 순위는 남기되 누구인지 알 수 없게)
    public static final String WITHDRAWN_NAME = "탈퇴한 회원";

    // 탈퇴: 행은 남겨 거래·순위·게시글 기록을 유지하고, 개인을 알아볼 수 있는 정보는 지운다 (개인정보처리방침 '보유 기간과 파기')
    @Loggable("회원 탈퇴")
    public void softDeleteUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String uploadedImage = user.getProfileImage();
        user.setIsDeleted(true);
        user.setTier(0);
        user.setName(WITHDRAWN_NAME);
        user.setEmail(null);
        user.setProviderId(null);
        user.setProfileImage(null);
        userRepository.save(user);
        // 직접 올린 프로필 사진 파일도 지운다 (소셜 프로필 URL은 무시됨). 파일 삭제가 실패해도 탈퇴는 진행한다
        try {
            storageService.deleteByUrl(uploadedImage);
        } catch (RuntimeException e) {
            log.warn("[user] 탈퇴 회원 프로필 사진 삭제 실패 - userId: {}", userId, e);
        }
        // 모든 기기의 리프레시 토큰과 이미 발급된 액세스 토큰을 즉시 무효화
        authTokenService.revokeAll(userId);
    }

    @Transactional(readOnly = true)
    public TierDto getTierDtoForUserAndSeason(User user, Integer seasonId) {
        Season season = seasonRepository.findById(seasonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEASON_NOT_FOUND));

        return userRankingRepository.findByUserAndSeason(user, season)
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

    @Transactional(readOnly = true)
    public Long getCashByUserId(Integer userId) {
        return userRepository.findById(userId)
                .map(User::getCash)
                .orElse(0L);
    }

}