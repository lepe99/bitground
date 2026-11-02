package bit.bitgroundspring.common.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 페이지 응답 공통 형식. Spring의 PageImpl을 그대로 직렬화하면 구조가 보장되지 않으므로 이 형식으로 감싼다.
 *
 * @param page 0부터 시작하는 페이지 번호
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return of(page.map(mapper));
    }
}
