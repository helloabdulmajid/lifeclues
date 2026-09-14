package in.abdulmajid.lifeclues.memory.mapper;

import in.abdulmajid.lifeclues.memory.dto.MemoryResponse;
import in.abdulmajid.lifeclues.memory.dto.TagResponse;
import in.abdulmajid.lifeclues.memory.entity.Memory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class MemoryMapper {

    private final TagMapper tagMapper;

    public MemoryMapper(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    public MemoryResponse toResponse(Memory memory) {
        List<TagResponse> tags = memory.getTags() == null
                ? List.of()
                : memory.getTags().stream()
                        .sorted(Comparator.comparing(t -> t.getName().toLowerCase()))
                        .map(tagMapper::toResponse)
                        .toList();

        return new MemoryResponse(
                memory.getId(),
                memory.getTitle(),
                memory.getContent(),
                memory.getEventDate(),
                memory.getEventTime(),
                memory.getStatus(),
                tags,
                memory.getCreatedAt(),
                memory.getUpdatedAt(),
                memory.getDeletedAt());
    }
}