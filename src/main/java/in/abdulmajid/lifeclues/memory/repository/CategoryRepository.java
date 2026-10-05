package in.abdulmajid.lifeclues.memory.repository;

import in.abdulmajid.lifeclues.memory.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /** Find a category by name for a specific user, ignoring case. */
    Optional<Category> findByUserIdAndNameIgnoreCase(UUID userId, String name);

    /** Return all categories belonging to a user, sorted by name. */
    List<Category> findAllByUserIdOrderByName(UUID userId);
}