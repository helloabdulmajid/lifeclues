package in.abdulmajid.lifeclues.memory.service;

import in.abdulmajid.lifeclues.account.entity.User;
import in.abdulmajid.lifeclues.account.repository.UserRepository;
import in.abdulmajid.lifeclues.memory.dto.PersonResponse;
import in.abdulmajid.lifeclues.memory.entity.Person;
import in.abdulmajid.lifeclues.memory.mapper.PersonMapper;
import in.abdulmajid.lifeclues.memory.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PersonService {

    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_PER_MEMORY = 50;

    private final PersonRepository personRepository;
    private final UserRepository userRepository;
    private final PersonMapper personMapper;

    public PersonService(PersonRepository personRepository, UserRepository userRepository,
                         PersonMapper personMapper) {
        this.personRepository = personRepository;
        this.userRepository = userRepository;
        this.personMapper = personMapper;
    }

    @Transactional(readOnly = true)
    public List<PersonResponse> listPeople(UUID userId) {
        return personRepository.findAllByUserIdOrderByName(userId)
                .stream()
                .map(personMapper::toResponse)
                .toList();
    }

    /**
     * Resolve a list of person names into Person entities for a given user.
     * Reuses existing people (case-insensitive match) or creates new ones.
     */
    @Transactional
    public Set<Person> resolvePeople(UUID userId, List<String> names) {
        if (names == null || names.isEmpty()) {
            // Mutable on purpose: Hibernate merge may clear() the collection, and
            // immutable Set.of() would blow up with UnsupportedOperationException.
            return new LinkedHashSet<>();
        }

        User user = userRepository.getReferenceById(userId);

        Set<String> uniqueNames = names.stream()
                .map(name -> name != null ? name.trim() : "")
                .filter(name -> !name.isEmpty() && name.length() <= MAX_NAME_LENGTH)
                .distinct()
                .limit(MAX_PER_MEMORY)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Person> people = new LinkedHashSet<>();
        for (String name : uniqueNames) {
            Person person = personRepository.findByUserIdAndNameIgnoreCase(userId, name)
                    .orElseGet(() -> {
                        Person newPerson = new Person();
                        newPerson.setUser(user);
                        newPerson.setName(name);
                        return personRepository.save(newPerson);
                    });
            people.add(person);
        }
        return people;
    }
}