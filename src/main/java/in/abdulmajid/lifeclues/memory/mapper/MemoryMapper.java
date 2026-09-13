package in.abdulmajid.lifeclues.memory.mapper;

import in.abdulmajid.lifeclues.memory.dto.MemoryResponse;
import in.abdulmajid.lifeclues.memory.entity.Memory;
import org.springframework.stereotype.Component;

@Component
public class MemoryMapper {

    public MemoryResponse toResponse(Memory memory) {
        return new MemoryResponse(
                memory.getId(),
                memory.getTitle(),
                memory.getContent(),
                memory.getEventDate(),
                memory.getEventTime(),
                memory.getStatus(),
                memory.getCreatedAt(),
                memory.getUpdatedAt(),
                memory.getDeletedAt());
    }
}