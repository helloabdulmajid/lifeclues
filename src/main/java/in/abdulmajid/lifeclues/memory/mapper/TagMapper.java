package in.abdulmajid.lifeclues.memory.mapper;

import in.abdulmajid.lifeclues.memory.dto.TagResponse;
import in.abdulmajid.lifeclues.memory.entity.Tag;
import org.springframework.stereotype.Component;

@Component
public class TagMapper {

    public TagResponse toResponse(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}
