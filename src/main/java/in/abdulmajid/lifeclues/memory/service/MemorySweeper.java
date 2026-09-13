package in.abdulmajid.lifeclues.memory.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Removes memories whose Trash retention window has lapsed. */
@Component
public class MemorySweeper {

    private final MemoryService memoryService;
    private final long retentionDays;

    public MemorySweeper(MemoryService memoryService,
                         @Value("${lifeclues.trash.retention-days:30}") long retentionDays) {
        this.memoryService = memoryService;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${lifeclues.trash.sweep-cron:0 0 3 * * *}")
    public void purgeExpiredTrash() {
        memoryService.purgeExpiredTrash(retentionDays);
    }
}