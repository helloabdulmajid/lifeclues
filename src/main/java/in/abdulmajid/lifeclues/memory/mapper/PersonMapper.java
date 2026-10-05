package in.abdulmajid.lifeclues.memory.mapper;

import in.abdulmajid.lifeclues.memory.dto.PersonResponse;
import in.abdulmajid.lifeclues.memory.entity.Person;
import org.springframework.stereotype.Component;

@Component
public class PersonMapper {

    public PersonResponse toResponse(Person person) {
        return new PersonResponse(person.getId(), person.getName());
    }
}