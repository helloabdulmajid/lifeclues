package in.abdulmajid.lifeclues.memory.service;

import in.abdulmajid.lifeclues.account.entity.User;
import in.abdulmajid.lifeclues.account.repository.UserRepository;
import in.abdulmajid.lifeclues.memory.dto.CategoryResponse;
import in.abdulmajid.lifeclues.memory.entity.Category;
import in.abdulmajid.lifeclues.memory.mapper.CategoryMapper;
import in.abdulmajid.lifeclues.memory.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private static final int MAX_NAME_LENGTH = 50;
    private static final int MAX_PER_MEMORY = 50;

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryRepository categoryRepository, UserRepository userRepository,
                           CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(UUID userId) {
        return categoryRepository.findAllByUserIdOrderByName(userId)
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    /**
     * Resolve a list of category names into Category entities for a given user.
     * Reuses existing categories (case-insensitive match) or creates new ones.
     */
    @Transactional
    public Set<Category> resolveCategories(UUID userId, List<String> names) {
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

        Set<Category> categories = new LinkedHashSet<>();
        for (String name : uniqueNames) {
            Category category = categoryRepository.findByUserIdAndNameIgnoreCase(userId, name)
                    .orElseGet(() -> {
                        Category newCategory = new Category();
                        newCategory.setUser(user);
                        newCategory.setName(name);
                        return categoryRepository.save(newCategory);
                    });
            categories.add(category);
        }
        return categories;
    }
}