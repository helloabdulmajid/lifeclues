package in.abdulmajid.lifeclues.account.mapper;

import in.abdulmajid.lifeclues.account.dto.UserResponse;
import in.abdulmajid.lifeclues.account.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getBio(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getDateOfBirth(),
                user.getPhone(),
                user.getGender(),
                user.getCity(),
                user.getCountry(),
                user.getProfession(),
                user.getRelationshipStatus(),
                user.getLanguages(),
                user.isEmailVerified(),
                user.getTimeFormat());
    }
}