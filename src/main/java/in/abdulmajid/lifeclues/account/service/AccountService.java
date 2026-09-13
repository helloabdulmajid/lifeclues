package in.abdulmajid.lifeclues.account.service;

import in.abdulmajid.lifeclues.account.dto.ChangePasswordRequest;
import in.abdulmajid.lifeclues.account.dto.RegisterRequest;
import in.abdulmajid.lifeclues.account.dto.UpdateProfileRequest;
import in.abdulmajid.lifeclues.account.dto.UserResponse;
import in.abdulmajid.lifeclues.account.entity.User;
import in.abdulmajid.lifeclues.account.mapper.UserMapper;
import in.abdulmajid.lifeclues.account.repository.UserRepository;
import in.abdulmajid.lifeclues.common.exception.BadRequestException;
import in.abdulmajid.lifeclues.common.exception.ConflictException;
import in.abdulmajid.lifeclues.common.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username().trim();

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username is already taken");
        }

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName((request.displayName() == null || request.displayName().isBlank())
                ? username
                : request.displayName().trim());
        user.setBio("");

        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return userMapper.toResponse(findById(userId));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findById(userId);

        if (request.displayName() != null) {
            String displayName = request.displayName().trim();
            user.setDisplayName(displayName.isEmpty() ? user.getUsername() : displayName);
        }
        if (request.bio() != null) {
            user.setBio(request.bio().trim());
        }

        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }
        if (request.phone() != null) {
            user.setPhone(normalize(request.phone()));
        }
        if (request.gender() != null) {
            user.setGender(normalize(request.gender()));
        }
        if (request.city() != null) {
            user.setCity(normalize(request.city()));
        }
        if (request.country() != null) {
            user.setCountry(normalize(request.country()));
        }
        if (request.profession() != null) {
            user.setProfession(normalize(request.profession()));
        }
        if (request.relationshipStatus() != null) {
            user.setRelationshipStatus(normalize(request.relationshipStatus()));
        }
        if (request.languages() != null) {
            user.setLanguages(normalize(request.languages()));
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    /** Trim an optional free-text value; a blank value means "clear it" (null). */
    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = findById(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private User findById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}