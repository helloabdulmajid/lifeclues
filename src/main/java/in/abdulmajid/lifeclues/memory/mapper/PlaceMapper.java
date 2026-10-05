package in.abdulmajid.lifeclues.memory.mapper;

import in.abdulmajid.lifeclues.memory.dto.PlaceResponse;
import in.abdulmajid.lifeclues.memory.entity.Place;
import org.springframework.stereotype.Component;

@Component
public class PlaceMapper {

    public PlaceResponse toResponse(Place place) {
        return new PlaceResponse(place.getId(), place.getName());
    }
}