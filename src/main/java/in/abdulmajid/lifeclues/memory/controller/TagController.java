package in.abdulmajid.lifeclues.memory.controller;

import in.abdulmajid.lifeclues.memory.dto.TagResponse;
import in.abdulmajid.lifeclues.memory.service.TagService;
import in.abdulmajid.lifeclues.security.CurrentUser;
import in.abdulmajid.lifeclues.security.UserPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public List<TagResponse> listTags(@CurrentUser UserPrincipal currentUser) {
        return tagService.listTags(currentUser.getId());
    }
}
