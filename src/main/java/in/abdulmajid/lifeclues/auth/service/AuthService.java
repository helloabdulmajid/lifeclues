package in.abdulmajid.lifeclues.auth.service;

import in.abdulmajid.lifeclues.account.dto.RegisterRequest;
import in.abdulmajid.lifeclues.account.dto.UserResponse;
import in.abdulmajid.lifeclues.account.entity.User;
import in.abdulmajid.lifeclues.account.mapper.UserMapper;
import in.abdulmajid.lifeclues.account.repository.UserRepository;
import in.abdulmajid.lifeclues.account.service.AccountService;
import in.abdulmajid.lifeclues.auth.dto.AuthResponse;
import in.abdulmajid.lifeclues.auth.dto.ForgotPasswordRequest;
import in.abdulmajid.lifeclues.auth.dto.LoginRequest;
import in.abdulmajid.lifeclues.auth.dto.RefreshTokenRequest;
import in.abdulmajid.lifeclues.auth.dto.ResendVerificationRequest;
import in.abdulmajid.lifeclues.auth.dto.ResetPasswordRequest;
import in.abdulmajid.lifeclues.auth.dto.VerifyEmailRequest;
import in.abdulmajid.lifeclues.auth.entity.AuthToken;
import in.abdulmajid.lifeclues.auth.entity.RefreshToken;
import in.abdulmajid.lifeclues.auth.mail.MailService;
import in.abdulmajid.lifeclues.auth.repository.AuthTokenRepository;
import in.abdulmajid.lifeclues.auth.repository.RefreshTokenRepository;
import in.abdulmajid.lifeclues.common.dto.MessageResponse;
import in.abdulmajid.lifeclues.common.exception.BadRequestException;
import in.abdulmajid.lifeclues.common.exception.UnauthorizedException;
import in.abdulmajid.lifeclues.security.JwtService;
import in.abdulmajid.lifeclues.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public static final String TYPE_VERIFY = "VERIFY";
    public static final String TYPE_RESET = "RESET";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AccountService accountService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthTokenRepository authTokenRepository;
    private final UserMapper userMapper;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshExpirySeconds;
    private final long verifyExpiryMinutes;
    private final long resetExpiryMinutes;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       AccountService accountService,
                       UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       AuthTokenRepository authTokenRepository,
                       UserMapper userMapper,
                       MailService mailService,
                       PasswordEncoder passwordEncoder,
                       @Value("${lifeclues.refresh.expiry-days:7}") long refreshExpiryDays,
                       @Value("${lifeclues.verify-token.expiry-minutes:30}") long verifyExpiryMinutes,
                       @Value("${lifeclues.reset-token.expiry-minutes:30}") long resetExpiryMinutes) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.accountService = accountService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authTokenRepository = authTokenRepository;
        this.userMapper = userMapper;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
        this.refreshExpirySeconds = refreshExpiryDays * 24 * 3600;
        this.verifyExpiryMinutes = verifyExpiryMinutes;
        this.resetExpiryMinutes = resetExpiryMinutes;
    }

    @Transactional
    public UserResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        UserResponse response = accountService.register(request);
        sendVerificationLink(response.id(), response.email(), httpRequest);
        return response;
    }

    /** Creates a verification token (if the account exists and is not verified) and emails a link. */
    @Transactional
    public MessageResponse resendVerification(ResendVerificationRequest request, HttpServletRequest httpRequest) {
        userRepository.findByEmail(request.email().trim().toLowerCase())
                .filter(user -> !user.isEmailVerified())
                .ifPresent(user -> {
                    revokeUnusedTokens(user.getId(), TYPE_VERIFY);
                    sendVerificationLink(user.getId(), user.getEmail(), httpRequest);
                });
        return MessageResponse.of(
                "If that email belongs to an unverified account, a new verification link has been sent.");
    }

    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        AuthToken token = findValidToken(request.token(), TYPE_VERIFY, "This verification link is invalid or has expired.");
        token.setUsedAt(Instant.now());
        User user = token.getUser();
        user.setEmailVerified(true);
        authTokenRepository.save(token);
        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        userRepository.findByEmail(request.email().trim().toLowerCase())
                .ifPresent(user -> {
                    revokeUnusedTokens(user.getId(), TYPE_RESET);
                    sendResetLink(user.getId(), user.getEmail(), httpRequest);
                });
        return MessageResponse.of("If an account exists for that email, a password reset link has been sent.");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        AuthToken token = findValidToken(request.token(), TYPE_RESET, "This reset link is invalid or has expired.");
        token.setUsedAt(Instant.now());

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.deleteByUserId(user.getId());

        authTokenRepository.save(token);
        userRepository.save(user);
        return MessageResponse.of("Password reset successfully. You can now sign in.");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.login(), request.password()));
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            User user = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new UnauthorizedException("Invalid login or password"));
            if (!user.isEmailVerified()) {
                throw new UnauthorizedException("Please verify your email address before signing in.");
            }
            return issueTokens(user);
        } catch (BadCredentialsException ex) {
            throw new UnauthorizedException("Invalid login or password");
        }
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(request.refreshToken()))
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        token.setRevoked(true);
        refreshTokenRepository.save(token);

        return issueTokens(token.getUser());
    }

    @Transactional
    public MessageResponse logout(RefreshTokenRequest request) {
        refreshTokenRepository.findByTokenHash(hash(request.refreshToken()))
                .ifPresent(refreshTokenRepository::delete);
        return MessageResponse.of("Logged out successfully");
    }

    private void sendVerificationLink(UUID userId, String email, HttpServletRequest httpRequest) {
        createAndSendToken(userId, email, TYPE_VERIFY, verifyExpiryMinutes, httpRequest,
                (user, raw) -> mailService.sendVerificationLink(httpRequest, user.getEmail(), raw));
    }

    private void sendResetLink(UUID userId, String email, HttpServletRequest httpRequest) {
        createAndSendToken(userId, email, TYPE_RESET, resetExpiryMinutes, httpRequest,
                (user, raw) -> mailService.sendResetLink(httpRequest, user.getEmail(), raw));
    }

    @FunctionalInterface
    private interface TokenSender {
        void send(User user, String rawToken);
    }

    private void createAndSendToken(UUID userId, String email, String type, long expiryMinutes,
                                    HttpServletRequest httpRequest, TokenSender sender) {
        try {
            String rawToken = generateRawToken();
            AuthToken token = new AuthToken();
            token.setUser(userRepository.getReferenceById(userId));
            token.setType(type);
            token.setTokenHash(hash(rawToken));
            token.setExpiresAt(Instant.now().plusSeconds(expiryMinutes * 60));
            token.setCreatedAt(Instant.now());
            authTokenRepository.save(token);
            sender.send(userRepository.getReferenceById(userId), rawToken);
        } catch (Exception ex) {
            log.warn("Failed to create/send {} email for {}: {}", type, email, ex.getMessage());
        }
    }

    private AuthToken findValidToken(String rawToken, String type, String message) {
        AuthToken token = authTokenRepository.findByTokenHashAndType(hash(rawToken), type)
                .orElseThrow(() -> new BadRequestException(message));
        if (token.isUsed() || token.isExpired()) {
            throw new BadRequestException(message);
        }
        return token;
    }

    private void revokeUnusedTokens(UUID userId, String type) {
        authTokenRepository.findByUser_IdAndTypeAndUsedAtIsNull(userId, type)
                .forEach(token -> token.setUsedAt(Instant.now()));
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);

        String rawRefreshToken = generateRawToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawRefreshToken));
        refreshToken.setExpiresAt(Instant.now().plusSeconds(refreshExpirySeconds));
        refreshToken.setCreatedAt(Instant.now());
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.of(accessToken, rawRefreshToken, jwtService.getAccessExpirySeconds(),
                userMapper.toResponse(user));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}