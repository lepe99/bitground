package bit.bitgroundspring.service;

import bit.bitgroundspring.dto.BoardDto;
import bit.bitgroundspring.entity.Category;
import bit.bitgroundspring.entity.Post;
import bit.bitgroundspring.entity.User;
import bit.bitgroundspring.repository.BoardRepository;
import bit.bitgroundspring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;

    /**
     * 게시글 저장
     */
    public Post savePost(BoardDto dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Post post = Post.builder()
                .user(user)
                .tier(dto.getTier())
                .category(Category.valueOf(dto.getCategory()))
                .title(dto.getTitle())
                .content(dto.getContent())
                .views(dto.getViews())
                .likes(0)
                .dislikes(0)
                .isDeleted(false)
                .build();

        return boardRepository.save(post);
    }

    /**
     * 게시글 목록 조회 (최적화된 DTO 매핑)
     * - hasImage: <img 포함 여부만 판단
     * - highestTier, pastSeasonTiers는 상세 보기에서만 추가
     */
    public Page<BoardDto> getBoardDtos(String category, Pageable pageable) {
        Page<Object[]> rowsPage = boardRepository.findAllBoardDtosRaw(category, pageable);

        return rowsPage.map(row -> {
            Integer userId = (Integer) row[1];
            String content = (String) row[5];

            return new BoardDto(
                    (Integer) row[0],                          // id
                    userId,                                    // userId
                    (String) row[2],                           // name
                    (String) row[3],                           // profileImage
                    (String) row[4],                           // title
                    content,                                   // content
                    ((Number) row[6]).intValue(),              // tier
                    ((Number) row[7]).intValue(),              // likes
                    ((Number) row[8]).intValue(),              // dislikes
                    Boolean.TRUE.equals(row[9]),               // isDeleted
                    ((Timestamp) row[10]).toLocalDateTime(),   // createdAt
                    ((Timestamp) row[11]).toLocalDateTime(),   // updatedAt
                    (String) row[12],                          // category
                    ((Number) row[13]).intValue(),             // views
                    ((Number) row[14]).longValue(),            // commentCount
                    content != null && content.contains("<img") // hasImage
            );
        });
    }
}