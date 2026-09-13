package in.abdulmajid.lifeclues.memory.dto;

import java.util.List;

public record MemoryPageResponse(
        List<MemoryResponse> items,
        long total,
        boolean hasMore) {
}