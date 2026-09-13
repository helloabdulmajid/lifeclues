package in.abdulmajid.lifeclues.memory.service;

import in.abdulmajid.lifeclues.account.repository.UserRepository;
import in.abdulmajid.lifeclues.common.exception.BadRequestException;
import in.abdulmajid.lifeclues.common.exception.ResourceNotFoundException;
import in.abdulmajid.lifeclues.memory.dto.MemoryPageResponse;
import in.abdulmajid.lifeclues.memory.dto.MemoryRequest;
import in.abdulmajid.lifeclues.memory.dto.MemoryResponse;
import in.abdulmajid.lifeclues.memory.dto.MemoryStatus;
import in.abdulmajid.lifeclues.memory.dto.StatusChangeRequest;
import in.abdulmajid.lifeclues.memory.entity.Memory;
import in.abdulmajid.lifeclues.memory.mapper.MemoryMapper;
import in.abdulmajid.lifeclues.memory.repository.MemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);
    private static final int MAX_TITLE_LENGTH = 60;

    private final MemoryRepository memoryRepository;
    private final UserRepository userRepository;
    private final MemoryMapper memoryMapper;

    public MemoryService(MemoryRepository memoryRepository, UserRepository userRepository,
                         MemoryMapper memoryMapper) {
        this.memoryRepository = memoryRepository;
        this.userRepository = userRepository;
        this.memoryMapper = memoryMapper;
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

        return memoryMapper.toResponse(memoryRepository.save(memory));
    }

    @Transactional(readOnly = true)
    public MemoryPageResponse list(UUID userId, int limit, int offset) {
        Page<Memory> page = memoryRepository.findByUserIdAndStatusNot(
                userId, MemoryStatus.TRASHED, pageRequest(limit, offset));
        return toPage(page);
    }

    @Transactional(readOnly = true)
    public MemoryPageResponse listTrashed(UUID userId, int limit, int offset) {
        Page<Memory> page = memoryRepository.findByUserIdAndStatus(
                userId, MemoryStatus.TRASHED, pageRequest(limit, offset));
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
        int safeLimit = Math.max(1, Math.min(Math.max(limit, 0), 100));
        int safeOffset = Math.max(offset, 0);
        return PageRequest.of(safeOffset / safeLimit, safeLimit,
                Sort.by(Sort.Order.desc("eventDate"), Sort.Order.desc("createdAt")));
    }

    private MemoryPageResponse toPage(Page<Memory> page) {
        List<MemoryResponse> items = page.getContent().stream().map(memoryMapper::toResponse).toList();
        return new MemoryPageResponse(items, page.getTotalElements(), page.hasNext());
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