package bit.bitgroundspring.market.dto;

/**
 * 뉴스 검색 결과 (필드 이름은 네이버 API 응답과 같게 유지)
 */
public record NewsResponse(String title, String originallink, String link, String description, String pubDate) {
}
