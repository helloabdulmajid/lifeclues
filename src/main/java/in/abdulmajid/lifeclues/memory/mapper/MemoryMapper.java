package in.abdulmajid.lifeclues.memory.mapper;

import in.abdulmajid.lifeclues.memory.dto.CategoryResponse;
import in.abdulmajid.lifeclues.memory.dto.MemoryResponse;
import in.abdulmajid.lifeclues.memory.dto.PersonResponse;
import in.abdulmajid.lifeclues.memory.dto.PlaceResponse;
import in.abdulmajid.lifeclues.memory.dto.TagResponse;
import in.abdulmajid.lifeclues.memory.entity.Category;
import in.abdulmajid.lifeclues.memory.entity.Memory;
import in.abdulmajid.lifeclues.memory.entity.Person;
import in.abdulmajid.lifeclues.memory.entity.Place;
import in.abdulmajid.lifeclues.memory.entity.Tag;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class MemoryMapper {

    private final TagMapper tagMapper;
    private final CategoryMapper categoryMapper;
    private final PersonMapper personMapper;
    private final PlaceMapper placeMapper;

    public MemoryMapper(TagMapper tagMapper, CategoryMapper categoryMapper,
                        PersonMapper personMapper, PlaceMapper placeMapper) {
        this.tagMapper = tagMapper;
        this.categoryMapper = categoryMapper;
        this.personMapper = personMapper;
        this.placeMapper = placeMapper;
    }

    private List<TagResponse> tags(Memory memory) {
        if (memory.getTags() == null) {
            return List.of();
        }
        return memory.getTags().stream()
                .sorted(Comparator.comparing(t -> t.getName().toLowerCase()))
                .map(tagMapper::toResponse)
                .toList();
    }

    private List<CategoryResponse> categories(Memory memory) {
        if (memory.getCategories() == null) {
            return List.of();
        }
        return memory.getCategories().stream()
                .sorted(Comparator.comparing(c -> c.getName().toLowerCase()))
                .map(categoryMapper::toResponse)
                .toList();
    }

    private List<PersonResponse> people(Memory memory) {
        if (memory.getPeople() == null) {
            return List.of();
        }
        return memory.getPeople().stream()
                .sorted(Comparator.comparing(p -> p.getName().toLowerCase()))
                .map(personMapper::toResponse)
                .toList();
    }

    private List<PlaceResponse> places(Memory memory) {
        if (memory.getPlaces() == null) {
            return List.of();
        }
        return memory.getPlaces().stream()
                .sorted(Comparator.comparing(p -> p.getName().toLowerCase()))
                .map(placeMapper::toResponse)
                .toList();
    }

    public MemoryResponse toResponse(Memory memory) {
        return new MemoryResponse(
                memory.getId(),
                memory.getTitle(),
                memory.getContent(),
                memory.getEventDate(),
                memory.getEventTime(),
                memory.getStatus(),
                memory.getMood(),
                tags(memory),
                categories(memory),
                people(memory),
                places(memory),
                memory.getCreatedAt(),
                memory.getUpdatedAt(),
                memory.getDeletedAt(),
                Boolean.TRUE.equals(memory.getFavorite()),
                Boolean.TRUE.equals(memory.getPinned()));
    }
}