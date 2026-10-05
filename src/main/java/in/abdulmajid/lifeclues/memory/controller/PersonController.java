package in.abdulmajid.lifeclues.memory.controller;

import in.abdulmajid.lifeclues.memory.dto.PersonResponse;
import in.abdulmajid.lifeclues.memory.service.PersonService;
import in.abdulmajid.lifeclues.security.CurrentUser;
import in.abdulmajid.lifeclues.security.UserPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/people")
public class PersonController {

    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    @GetMapping
    public List<PersonResponse> listPeople(@CurrentUser UserPrincipal currentUser) {
        return personService.listPeople(currentUser.getId());
    }
}