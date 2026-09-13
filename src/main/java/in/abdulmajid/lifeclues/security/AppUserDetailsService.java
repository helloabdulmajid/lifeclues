package in.abdulmajid.lifeclues.security;

import in.abdulmajid.lifeclues.account.entity.User;
import in.abdulmajid.lifeclues.account.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        String trimmed = login.trim();
        String email = trimmed.contains("@") ? trimmed.toLowerCase(Locale.ROOT) : trimmed;

        User user = userRepository.findByEmailOrUsername(email, trimmed)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return UserPrincipal.from(user);
    }

    public UserDetails loadUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return UserPrincipal.from(user);
    }
}