package in.abdulmajid.lifeclues.memory.repository;

import in.abdulmajid.lifeclues.memory.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    /** Find a tag by name for a specific user, ignoring case. */
    Optional<Tag> findByUserIdAndNameIgnoreCase(UUID userId, String name);

    /** Return all tags belonging to a user, sorted by name. */
    List<Tag> findAllByUserIdOrderByName(UUID userId);
}
