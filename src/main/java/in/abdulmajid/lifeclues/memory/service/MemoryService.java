package in.abdulmajid.lifeclues.memory.service;

import in.abdulmajid.lifeclues.account.repository.UserRepository;
import in.abdulmajid.lifeclues.common.exception.BadRequestException;
import in.abdulmajid.lifeclues.common.exception.ResourceNotFoundException;
import in.abdulmajid.lifeclues.memory.dto.MemoryPageResponse;
import in.abdulmajid.lifeclues.memory.dto.MemoryFlagsRequest;
import in.abdulmajid.lifeclues.memory.dto.MemoryRequest;
import in.abdulmajid.lifeclues.memory.dto.MemoryResponse;
import in.abdulmajid.lifeclues.memory.dto.MemoryStatus;
import in.abdulmajid.lifeclues.memory.dto.Mood;
import in.abdulmajid.lifeclues.memory.dto.StatusChangeRequest;
import in.abdulmajid.lifeclues.memory.entity.Memory;
import in.abdulmajid.lifeclues.memory.entity.Tag;
import in.abdulmajid.lifeclues.memory.mapper.MemoryMapper;
import in.abdulmajid.lifeclues.memory.repository.MemoryRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);
    private static final int MAX_TITLE_LENGTH = 60;
    private static final int MAX_PINNED = 5;
    private static final Set<String> SORT_FIELDS = Set.of("eventDate", "eventTime", "createdAt", "updatedAt");
    private static final Set<String> SORT_ORDERS = Set.of("asc", "desc");

    private final MemoryRepository memoryRepository;
    private final UserRepository userRepository;
    private final MemoryMapper memoryMapper;
    private final TagService tagService;
    private final CategoryService categoryService;
    private final PersonService personService;
    private final PlaceService placeService;

    public MemoryService(MemoryRepository memoryRepository, UserRepository userRepository,
                         MemoryMapper memoryMapper, TagService tagService,
                         CategoryService categoryService, PersonService personService,
                         PlaceService placeService) {
        this.memoryRepository = memoryRepository;
        this.userRepository = userRepository;
        this.memoryMapper = memoryMapper;
        this.tagService = tagService;
        this.categoryService = categoryService;
        this.personService = personService;
        this.placeService = placeService;
    }

    @Transactional
    public MemoryResponse create(UUID userId, MemoryRequest request) {
        MemoryStatus status = request.status() == null ? MemoryStatus.DRAFT : request.status();
        ensureNotTrashed(status);

        Memory memory = new Memory();
        memory.setUser(userRepository.getReferenceById(userId));
        memory.setTitle(trimmedTitle(request.title(), request.content()));
        memory.setContent(request.content().trim());
        memory.setEventDate(request.eventDate());
        memory.setEventTime(request.eventTime());
        memory.setStatus(status);

        memory.setMood(request.mood());

        Set<Tag> tags = tagService.resolveTags(userId, request.tags());
        memory.setTags(tags);
        memory.setCategories(categoryService.resolveCategories(userId, request.categories()));
        memory.setPeople(personService.resolvePeople(userId, request.people()));
        memory.setPlaces(placeService.resolvePlaces(userId, request.places()));

        return memoryMapper.toResponse(memoryRepository.save(memory));
    }

    @Transactional(readOnly = true)
    public MemoryPageResponse list(UUID userId, int limit, int offset, String sort, String order,
                                   LocalDate from, LocalDate to, boolean favorite,
                                   Mood mood, List<String> tags, List<String> categories,
                                   List<String> people, List<String> places) {
        // Guard the range up front so every branch (including the clue-filter
        // one, which goes through a Specification) rejects inverted ranges.
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'.");
        }
        Pageable pageable = pageRequest(limit, offset, sort, order);
        Page<Memory> page;
        if (mood != null || hasAny(tags) || hasAny(categories) || hasAny(people) || hasAny(places)) {
            // Clue filters need joins, so they use a Specification composed with the
            // same date-range / favorite / status predicates instead of derived queries.
            page = memoryRepository.findAll(
                    clueSpec(userId, from, to, favorite, mood, tags, categories, people, places),
                    pageable);
        } else if (favorite) {
            if (from != null && to != null) {
                if (from.isAfter(to)) {
                    throw new BadRequestException("'from' must not be after 'to'.");
                }
                page = memoryRepository.findByUserIdAndStatusNotAndFavoriteTrueAndEventDateBetween(
                        userId, MemoryStatus.TRASHED, from, to, pageable);
            } else if (from != null) {
                page = memoryRepository.findByUserIdAndStatusNotAndFavoriteTrueAndEventDateGreaterThanEqual(
                        userId, MemoryStatus.TRASHED, from, pageable);
            } else if (to != null) {
                page = memoryRepository.findByUserIdAndStatusNotAndFavoriteTrueAndEventDateLessThanEqual(
                        userId, MemoryStatus.TRASHED, to, pageable);
            } else {
                page = memoryRepository.findByUserIdAndStatusNotAndFavoriteTrue(
                        userId, MemoryStatus.TRASHED, pageable);
            }
        } else if (from != null && to != null) {
            if (from.isAfter(to)) {
                throw new BadRequestException("'from' must not be after 'to'.");
            }
            page = memoryRepository.findByUserIdAndStatusNotAndEventDateBetween(
                    userId, MemoryStatus.TRASHED, from, to, pageable);
        } else if (from != null) {
            page = memoryRepository.findByUserIdAndStatusNotAndEventDateGreaterThanEqual(
                    userId, MemoryStatus.TRASHED, from, pageable);
        } else if (to != null) {
            page = memoryRepository.findByUserIdAndStatusNotAndEventDateLessThanEqual(
                    userId, MemoryStatus.TRASHED, to, pageable);
        } else {
            page = memoryRepository.findByUserIdAndStatusNot(userId, MemoryStatus.TRASHED, pageable);
        }
        return toPage(page);
    }

    @Transactional(readOnly = true)
    public MemoryPageResponse listTrashed(UUID userId, int limit, int offset) {
        Page<Memory> page = memoryRepository.findByUserIdAndStatus(
                userId, MemoryStatus.TRASHED, pageRequest(limit, offset));
        return toPage(page);
    }

    @Transactional(readOnly = true)
    public MemoryPageResponse search(UUID userId, String query, int limit, int offset) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return new MemoryPageResponse(List.of(), 0, false);
        }
        Page<Memory> page = memoryRepository.findAll(
                searchSpec(userId, trimmed), pageRequest(limit, offset, "eventDate", "desc"));
        return toPage(page);
    }

    @Transactional(readOnly = true)
    public MemoryResponse get(UUID userId, UUID id) {
        return memoryMapper.toResponse(findOwned(userId, id));
    }

    @Transactional
    public MemoryResponse update(UUID userId, UUID id, MemoryRequest request) {
        Memory memory = findOwned(userId, id);
        if (memory.getStatus() == MemoryStatus.TRASHED) {
            throw new BadRequestException("Restore this memory before editing it.");
        }

        if (request.status() != null) {
            ensureNotTrashed(request.status());
            memory.setStatus(request.status());
        }
        memory.setTitle(trimmedTitle(request.title(), request.content()));
        memory.setContent(request.content().trim());
        memory.setEventDate(request.eventDate());
        memory.setEventTime(request.eventTime());

        // Unconditional so a null/missing mood clears the stored one; the editor
        // always sends mood (value or null), matching how it sets clue lists.
        memory.setMood(request.mood());

        if (request.tags() != null) {
            Set<Tag> tags = tagService.resolveTags(userId, request.tags());
            memory.setTags(tags);
        }
        if (request.categories() != null) {
            memory.setCategories(categoryService.resolveCategories(userId, request.categories()));
        }
        if (request.people() != null) {
            memory.setPeople(personService.resolvePeople(userId, request.people()));
        }
        if (request.places() != null) {
            memory.setPlaces(placeService.resolvePlaces(userId, request.places()));
        }

        return memoryMapper.toResponse(memoryRepository.save(memory));
    }

    @Transactional
    public MemoryResponse updateFlags(UUID userId, UUID id, MemoryFlagsRequest request) {
        Memory memory = findOwned(userId, id);
        memory.setFavorite(request.favorite());

        if (request.pinned() && !Boolean.TRUE.equals(memory.getPinned())) {
            long currentlyPinned = memoryRepository.countByUserIdAndPinnedTrue(userId);
            if (currentlyPinned >= MAX_PINNED) {
                throw new BadRequestException(
                        "You can pin up to " + MAX_PINNED + " memories. Unpin one before pinning another.");
            }
            memory.setPinned(true);
            memory.setPinnedAt(Instant.now());
        } else if (!request.pinned()) {
            memory.setPinned(false);
            memory.setPinnedAt(null);
        }

        return memoryMapper.toResponse(memoryRepository.save(memory));
    }

    @Transactional
    public MemoryResponse changeStatus(UUID userId, UUID id, StatusChangeRequest request) {
        Memory memory = findOwned(userId, id);
        MemoryStatus target = request.status();

        if (target == MemoryStatus.DRAFT || target == MemoryStatus.COMPLETED) {
            memory.setStatus(target);
        } else {
            throw new BadRequestException("Use delete to move a memory to Trash.");
        }

        return memoryMapper.toResponse(memoryRepository.save(memory));
    }

    @Transactional
    public MemoryResponse trash(UUID userId, UUID id) {
        Memory memory = findOwned(userId, id);
        if (memory.getStatus() == MemoryStatus.TRASHED) {
            return memoryMapper.toResponse(memory);
        }
        memory.setTrashedFrom(memory.getStatus());
        memory.setStatus(MemoryStatus.TRASHED);
        memory.setDeletedAt(Instant.now());
        memory.setPinned(false);
        memory.setPinnedAt(null);
        return memoryMapper.toResponse(memoryRepository.save(memory));
    }

    @Transactional
    public MemoryResponse restore(UUID userId, UUID id) {
        Memory memory = findOwned(userId, id);
        if (memory.getStatus() != MemoryStatus.TRASHED) {
            throw new BadRequestException("That memory is not in Trash.");
        }
        memory.setStatus(memory.getTrashedFrom() == null ? MemoryStatus.COMPLETED : memory.getTrashedFrom());
        memory.setTrashedFrom(null);
        memory.setDeletedAt(null);
        return memoryMapper.toResponse(memoryRepository.save(memory));
    }

    @Transactional
    public void permanentDelete(UUID userId, UUID id) {
        Memory memory = findOwned(userId, id);
        memoryRepository.delete(memory);
    }

    @Transactional
    public long emptyTrash(UUID userId) {
        return memoryRepository.deleteByUserIdAndStatus(userId, MemoryStatus.TRASHED);
    }

    /** Daily sweep: permanently remove memories whose trash retention has lapsed. */
    @Transactional
    public long purgeExpiredTrash(long retentionDays) {
        long removed = memoryRepository.deleteByDeletedAtBefore(Instant.now().minusSeconds(retentionDays * 86400));
        if (removed > 0) {
            log.info("Purged {} memories past their trash retention.", removed);
        }
        return removed;
    }

    private void ensureNotTrashed(MemoryStatus status) {
        if (status == MemoryStatus.TRASHED) {
            throw new BadRequestException("A memory cannot be created directly in Trash.");
        }
    }

    private Memory findOwned(UUID userId, UUID id) {
        return memoryRepository.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Memory not found"));
    }

    private Pageable pageRequest(int limit, int offset) {
        return pageRequest(limit, offset, "eventDate", "desc");
    }

    private Pageable pageRequest(int limit, int offset, String sort, String order) {
        int safeLimit = Math.max(1, Math.min(Math.max(limit, 0), 100));
        int safeOffset = Math.max(offset, 0);

        String field = sort == null || sort.isBlank() ? "eventDate" : sort;
        if (!SORT_FIELDS.contains(field)) {
            throw new BadRequestException("Unknown sort field: " + field + ".");
        }
        String direction = order == null || order.isBlank() ? "desc" : order;
        if (!SORT_ORDERS.contains(direction)) {
            throw new BadRequestException("Unknown sort order: " + direction + ".");
        }

        List<Sort.Order> orders = new ArrayList<>();
        orders.add(new Sort.Order(direction.equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, field));
        if (!field.equals("createdAt")) {
            orders.add(Sort.Order.desc("createdAt"));
        }
        return PageRequest.of(safeOffset / safeLimit, safeLimit, Sort.by(orders));
    }

    private MemoryPageResponse toPage(Page<Memory> page) {
        List<MemoryResponse> items = page.getContent().stream().map(memoryMapper::toResponse).toList();
        return new MemoryPageResponse(items, page.getTotalElements(), page.hasNext());
    }

    /** Phase 5 clue filters: joins each clue list and ANDs the per-type `in` predicates. */
    private Specification<Memory> clueSpec(UUID userId, LocalDate from, LocalDate to, boolean favorite,
                                           Mood mood, List<String> tags, List<String> categories,
                                           List<String> people, List<String> places) {
        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            predicates.add(cb.notEqual(root.get("status"), MemoryStatus.TRASHED));
            if (favorite) {
                predicates.add(cb.isTrue(root.get("favorite")));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("eventDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("eventDate"), to));
            }
            if (mood != null) {
                predicates.add(cb.equal(root.get("mood"), mood));
            }
            addClueMemberPredicates(predicates, root, cb, "tags", tags);
            addClueMemberPredicates(predicates, root, cb, "categories", categories);
            addClueMemberPredicates(predicates, root, cb, "people", people);
            addClueMemberPredicates(predicates, root, cb, "places", places);
            if (predicates.size() == 1) {
                return predicates.get(0);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addClueMemberPredicates(List<Predicate> predicates, Root<Memory> root,
                                         CriteriaBuilder cb, String field, List<String> names) {
        if (names == null || names.isEmpty()) {
            return;
        }
        List<String> lower = names.stream().map(String::toLowerCase).toList();
        Join<Memory, Object> join = root.join(field);
        predicates.add(cb.lower(join.<String>get("name")).in(lower));
    }

    /**
     * Phase 6 search: whitespace-separated tokens; a leading '+' makes a token
     * required (AND), everything else is optional (OR). Each token matches
     * anywhere in the title, content, or any clue name (tags, categories,
     * people, places), case-insensitively.
     */
    private Specification<Memory> searchSpec(UUID userId, String query) {
        return (root, tx, cb) -> {
            tx.distinct(true);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            predicates.add(cb.notEqual(root.get("status"), MemoryStatus.TRASHED));

            List<Predicate> required = new ArrayList<>();
            List<Predicate> optional = new ArrayList<>();
            for (String raw : query.split("\\s+")) {
                String token = raw.trim();
                if (token.isEmpty()) {
                    continue;
                }
                boolean mandatory = token.startsWith("+");
                String word = mandatory ? token.substring(1) : token;
                if (word.isEmpty()) {
                    continue;
                }
                Predicate group = addSearchTerms(root, cb, "%" + escapeLike(word.toLowerCase()) + "%");
                if (mandatory) {
                    required.add(group);
                } else {
                    optional.add(group);
                }
            }

            if (!required.isEmpty()) {
                predicates.add(cb.and(required.toArray(Predicate[]::new)));
            }
            if (optional.size() == 1) {
                predicates.add(optional.get(0));
            } else if (optional.size() > 1) {
                predicates.add(cb.or(optional.toArray(Predicate[]::new)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** One OR group for a single token: title, content, and every clue name. */
    private Predicate addSearchTerms(Root<Memory> root, CriteriaBuilder cb, String pattern) {
        List<Predicate> terms = new ArrayList<>();
        terms.add(cb.like(cb.lower(root.get("title")), pattern, '\\'));
        terms.add(cb.like(cb.lower(root.get("content")), pattern, '\\'));
        addSearchMemberPredicates(terms, root, cb, "tags", pattern);
        addSearchMemberPredicates(terms, root, cb, "categories", pattern);
        addSearchMemberPredicates(terms, root, cb, "people", pattern);
        addSearchMemberPredicates(terms, root, cb, "places", pattern);
        return cb.or(terms.toArray(Predicate[]::new));
    }

    /** LEFT join so memories without that clue still match via other fields. */
    private void addSearchMemberPredicates(List<Predicate> terms, Root<Memory> root,
                                           CriteriaBuilder cb, String field, String pattern) {
        Join<Memory, Object> join = root.join(field, JoinType.LEFT);
        terms.add(cb.like(cb.lower(join.<String>get("name")), pattern, '\\'));
    }

    /** Escape LIKE wildcards so user input is matched literally. */
    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private boolean hasAny(List<String> values) {
        return values != null && !values.isEmpty();
    }

    /** Collapse whitespace, cap at ~60 chars, append an ellipsis when cut. */
    private String trimmedTitle(String title, String content) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        String compact = content.trim().replaceAll("\\s+", " ");
        if (compact.length() <= MAX_TITLE_LENGTH) {
            return compact;
        }
        int cut = compact.lastIndexOf(' ', MAX_TITLE_LENGTH);
        if (cut < 20) {
            cut = MAX_TITLE_LENGTH;
        }
        return compact.substring(0, cut) + "…";
    }
}