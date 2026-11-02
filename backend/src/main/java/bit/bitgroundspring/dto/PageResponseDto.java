package bit.bitgroundspring.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * ✅ 페이징 응답 포맷 통일용 DTO
 * - Spring Data JPA의 PageImpl은 JSON 직렬화 시 경고 발생
 *   ("Serializing PageImpl instances as-is is not supported")
 * - 향후 구조 변경이나 Jackson 설정 충돌 방지를 위해 커스텀 포맷으로 감싸서 응답
 *
 * ✅ 사용 목적:
 * - 안정적인 JSON 응답 구조 보장
 * - API 문서화/협업 시 명확한 명세 제공
 * - 프론트엔드에서 content, page, size, totalPages 등 쉽게 접근 가능
 */
public class PageResponseDto<T> {
    private List<T> content;
    private int page;
    private int size;
    private int totalPages;
    private long totalElements;
    private boolean last;

    public PageResponseDto(Page<T> pageData) {
        this.content = pageData.getContent();
        this.page = pageData.getNumber();
        this.size = pageData.getSize();
        this.totalPages = pageData.getTotalPages();
        this.totalElements = pageData.getTotalElements();
        this.last = pageData.isLast();
    }

    // Getter (lombok 쓰면 @Getter 붙여도 됨)
    public List<T> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public boolean isLast() {
        return last;
    }
}