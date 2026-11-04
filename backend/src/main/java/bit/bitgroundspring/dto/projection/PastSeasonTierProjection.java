package bit.bitgroundspring.dto.projection;

public interface PastSeasonTierProjection {
    String getName();
    Integer getTier();
    Integer getRanks();       // 시즌 최종 순위
    Long getTotalValue();     // 시즌 최종 총 자산 (원)
    Long getParticipants();   // 그 시즌 참가자 수 (상위 % 계산용)
}
