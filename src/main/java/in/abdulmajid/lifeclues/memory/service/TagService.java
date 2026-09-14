package in.abdulmajid.lifeclues.memory.service;

import in.abdulmajid.lifeclues.account.entity.User;
import in.abdulmajid.lifeclues.account.repository.UserRepository;
import in.abdulmajid.lifeclues.common.exception.BadRequestException;
import in.abdulmajid.lifeclues.memory.dto.TagResponse;
import in.abdulmajid.lifeclues.memory.entity.Tag;
import in.abdulmajid.lifeclues.memory.mapper.TagMapper;
import in.abdulmajid.lifeclues.memory.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TagService {

    private static final int MAX_TAG_NAME_LENGTH = 50;
    private static final int MAX_TAGS_PER_MEMORY = 50;

    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final TagMapper tagMapper;

    public TagService(TagRepository tagRepository, UserRepository userRepository,
                      TagMapper tagMapper) {
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
        this.tagMapper = tagMapper;
    }

    @Transactional(readOnly = true)
    public List<TagResponse> listTags(UUID userId) {
        return tagRepository.findAllByUserIdOrderByName(userId)
                .stream()
                .map(tagMapper::toResponse)
                .toList();
    }

    /**
     * Resolve a list of tag names into Tag entities for a given user.
     * Reuses existing tags (case-insensitive match) or creates new ones.
     * Enforces the 50-character name limit and 50-tag-per-memory limit.
     */
    @Transactional
    public Set<Tag> resolveTags(UUID userId, List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return Set.of();
        }

        User user = userRepository.getReferenceById(userId);

        Set<String> uniqueNames = tagNames.stream()
                .map(name -> name != null ? name.trim() : "")
                .filter(name -> !name.isEmpty() && name.length() <= MAX_TAG_NAME_LENGTH)
                .distinct()
                .limit(MAX_TAGS_PER_MEMORY)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Tag> tags = new LinkedHashSet<>();
        for (String name : uniqueNames) {
            Tag tag = tagRepository.findByUserIdAndName(userId, name)
                    .orElseGet(() -> {
                        Tag newTag = new Tag();
                        newTag.setUser(user);
                        newTag.setName(name);
                        return tagRepository.save(newTag);
                    });
            tags.add(tag);
        }
        return tags;
    }
}
