package in.abdulmajid.lifeclues.memory.repository;

import in.abdulmajid.lifeclues.memory.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlaceRepository extends JpaRepository<Place, UUID> {

    /** Find a place by name for a specific user, ignoring case. */
    Optional<Place> findByUserIdAndNameIgnoreCase(UUID userId, String name);

    /** Return all places belonging to a user, sorted by name. */
    List<Place> findAllByUserIdOrderByName(UUID userId);
}