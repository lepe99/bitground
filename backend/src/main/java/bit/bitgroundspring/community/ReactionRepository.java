package bit.bitgroundspring.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {
        Optional<Reaction> findByUserIdAndTargetTypeAndTargetIdAndLiked(
                Integer userId, ReactionTargetType type, Long targetId, boolean liked);
}
