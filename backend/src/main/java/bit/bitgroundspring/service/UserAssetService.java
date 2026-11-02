package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.projection.UserAssetProjection;
import bit.bitgroundspring.dto.response.UserAssetResponse;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.OrderRepository;
import bit.bitgroundspring.repository.UserAssetRepository;
import bit.bitgroundspring.repository.UserRepository;
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
        Integer cash = getAvailableCash(userId);
        
        // 사용자 자산 조회 (projection 사용)
        List<UserAssetProjection> userAssets = userAssetRepository
                .findUserAssetsWithAvailableAmount(userId);
        
        return new UserAssetResponse(cash, userAssets);
    }

    //투자내역 보유자산목록
    public List<UserAssetProjection> getOnlyUserAssets(Integer userId) {
        return userAssetRepository.findByUserId(userId);
    }

    public Integer getAvailableCash(Integer userId) {
        // 사용자 현금 조회
        int cash = userRepository.findById(userId)
                .map(User::getCash)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Integer reservedCash = orderRepository.calculateTotalReservePriceForBuyOrdersByUserId(userId);
        // 현금에서 예약된 금액 차감
        if (reservedCash == null) {
            reservedCash = 0; // 예약된 금액이 없으면 0으로 설정
        }
        cash -= reservedCash;

        // 만약 현금이 음수라면 예외 처리
        if (cash < 0) {
            throw new RuntimeException("Insufficient cash after reserving for buy orders");
        }
        return cash;
    }



}