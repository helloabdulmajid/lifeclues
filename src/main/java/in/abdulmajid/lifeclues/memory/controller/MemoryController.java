package in.abdulmajid.lifeclues.memory.controller;

import in.abdulmajid.lifeclues.common.dto.MessageResponse;
import in.abdulmajid.lifeclues.memory.dto.MemoryPageResponse;
import in.abdulmajid.lifeclues.memory.dto.MemoryRequest;
import in.abdulmajid.lifeclues.memory.dto.MemoryResponse;
import in.abdulmajid.lifeclues.memory.dto.StatusChangeRequest;
import in.abdulmajid.lifeclues.memory.service.MemoryService;
import in.abdulmajid.lifeclues.security.CurrentUser;
import in.abdulmajid.lifeclues.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public MemoryPageResponse list(@CurrentUser UserPrincipal currentUser,
                                   @RequestParam(defaultValue = "50") int limit,
                                   @RequestParam(defaultValue = "0") int offset) {
        return memoryService.list(currentUser.getId(), limit, offset);
    }

    @GetMapping("/trash")
    public MemoryPageResponse listTrashed(@CurrentUser UserPrincipal currentUser,
                                          @RequestParam(defaultValue = "50") int limit,
                                          @RequestParam(defaultValue = "0") int offset) {
        return memoryService.listTrashed(currentUser.getId(), limit, offset);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemoryResponse create(@CurrentUser UserPrincipal currentUser,
                                 @Valid @RequestBody MemoryRequest request) {
        return memoryService.create(currentUser.getId(), request);
    }

    @GetMapping("/{id}")
    public MemoryResponse get(@CurrentUser UserPrincipal currentUser, @PathVariable UUID id) {
        return memoryService.get(currentUser.getId(), id);
    }

    @PutMapping("/{id}")
    public MemoryResponse update(@CurrentUser UserPrincipal currentUser,
                                 @PathVariable UUID id,
                                 @Valid @RequestBody MemoryRequest request) {
        return memoryService.update(currentUser.getId(), id, request);
    }

    @PatchMapping("/{id}/status")
    public MemoryResponse changeStatus(@CurrentUser UserPrincipal currentUser,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody StatusChangeRequest request) {
        return memoryService.changeStatus(currentUser.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    public MemoryResponse trash(@CurrentUser UserPrincipal currentUser, @PathVariable UUID id) {
        return memoryService.trash(currentUser.getId(), id);
    }

    @PostMapping("/{id}/restore")
    public MemoryResponse restore(@CurrentUser UserPrincipal currentUser, @PathVariable UUID id) {
        return memoryService.restore(currentUser.getId(), id);
    }

    @DeleteMapping("/{id}/permanent")
    public MessageResponse permanentDelete(@CurrentUser UserPrincipal currentUser, @PathVariable UUID id) {
        memoryService.permanentDelete(currentUser.getId(), id);
        return MessageResponse.of("Memory deleted permanently");
    }

    @DeleteMapping("/trash")
    public MessageResponse emptyTrash(@CurrentUser UserPrincipal currentUser) {
        long removed = memoryService.emptyTrash(currentUser.getId());
        return MessageResponse.of(removed > 0 ? "Trash emptied" : "Trash is already empty");
    }
}