package in.abdulmajid.lifeclues.memory.service;

import in.abdulmajid.lifeclues.account.entity.User;
import in.abdulmajid.lifeclues.account.repository.UserRepository;
import in.abdulmajid.lifeclues.memory.dto.PlaceResponse;
import in.abdulmajid.lifeclues.memory.entity.Place;
import in.abdulmajid.lifeclues.memory.mapper.PlaceMapper;
import in.abdulmajid.lifeclues.memory.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlaceService {

    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_PER_MEMORY = 50;

    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final PlaceMapper placeMapper;

    public PlaceService(PlaceRepository placeRepository, UserRepository userRepository,
                        PlaceMapper placeMapper) {
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
        this.placeMapper = placeMapper;
    }

    @Transactional(readOnly = true)
    public List<PlaceResponse> listPlaces(UUID userId) {
        return placeRepository.findAllByUserIdOrderByName(userId)
                .stream()
                .map(placeMapper::toResponse)
                .toList();
    }

    /**
     * Resolve a list of place names into Place entities for a given user.
     * Reuses existing places (case-insensitive match) or creates new ones.
     */
    @Transactional
    public Set<Place> resolvePlaces(UUID userId, List<String> names) {
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

        Set<Place> places = new LinkedHashSet<>();
        for (String name : uniqueNames) {
            Place place = placeRepository.findByUserIdAndNameIgnoreCase(userId, name)
                    .orElseGet(() -> {
                        Place newPlace = new Place();
                        newPlace.setUser(user);
                        newPlace.setName(name);
                        return placeRepository.save(newPlace);
                    });
            places.add(place);
        }
        return places;
    }
}