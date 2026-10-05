package in.abdulmajid.lifeclues.memory.controller;

import in.abdulmajid.lifeclues.memory.dto.PlaceResponse;
import in.abdulmajid.lifeclues.memory.service.PlaceService;
import in.abdulmajid.lifeclues.security.CurrentUser;
import in.abdulmajid.lifeclues.security.UserPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final PlaceService placeService;

    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @GetMapping
    public List<PlaceResponse> listPlaces(@CurrentUser UserPrincipal currentUser) {
        return placeService.listPlaces(currentUser.getId());
    }
}