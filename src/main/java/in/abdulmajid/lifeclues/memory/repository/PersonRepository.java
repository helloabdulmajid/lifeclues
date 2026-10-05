package in.abdulmajid.lifeclues.memory.repository;

import in.abdulmajid.lifeclues.memory.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    /** Find a person by name for a specific user, ignoring case. */
    Optional<Person> findByUserIdAndNameIgnoreCase(UUID userId, String name);

    /** Return all people belonging to a user, sorted by name. */
    List<Person> findAllByUserIdOrderByName(UUID userId);
}