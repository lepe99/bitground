package bit.bitgroundspring.order;

import bit.bitgroundspring.common.error.BusinessException;
import bit.bitgroundspring.common.error.ErrorCode;
import bit.bitgroundspring.order.dto.UserAssetProjection;
import bit.bitgroundspring.order.dto.UserAssetResponse;
import bit.bitgroundspring.user.User;
import bit.bitgroundspring.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAssetService {
    private final UserRepository userRepository;
    private final UserAssetRepository userAssetRepository;
    private final OrderRepository orderRepository;
    
    
    /**
     * userId 로 보유 자산들 조회
     * @param userId 유저 ID
     * @return 보유 자산 정보 리스트
     */
    @Transactional(readOnly = true)
    public UserAssetResponse getUserAssets(Integer userId) {
        Long cash = getAvailableCash(userId);
        
        // 사용자 자산 조회 (projection 사용)
        List<UserAssetProjection> userAssets = userAssetRepository
                .findUserAssetsWithAvailableAmount(userId);
        
        return new UserAssetResponse(cash, userAssets);
    }

    //투자내역 보유자산목록
    public List<UserAssetProjection> getOnlyUserAssets(Integer userId) {
        return userAssetRepository.findByUserId(userId);
    }

    public Long getAvailableCash(Integer userId) {
        // 사용자 현금에서 예약 매수에 묶인 금액을 뺀 주문 가능 금액
        long cash = userRepository.findById(userId)
                .map(User::getCash)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                - orderRepository.reservedBuyCash(userId);

        // 예약 금액이 현금보다 크면(데이터 불일치) 화면이 깨지지 않도록 0으로 보정
        return Math.max(cash, 0);
    }



}