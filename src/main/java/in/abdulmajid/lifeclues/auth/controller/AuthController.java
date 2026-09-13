package in.abdulmajid.lifeclues.auth.controller;

import in.abdulmajid.lifeclues.account.dto.RegisterRequest;
import in.abdulmajid.lifeclues.account.dto.UserResponse;
import in.abdulmajid.lifeclues.auth.dto.AuthResponse;
import in.abdulmajid.lifeclues.auth.dto.ForgotPasswordRequest;
import in.abdulmajid.lifeclues.auth.dto.LoginRequest;
import in.abdulmajid.lifeclues.auth.dto.RefreshTokenRequest;
import in.abdulmajid.lifeclues.auth.dto.ResendVerificationRequest;
import in.abdulmajid.lifeclues.auth.dto.ResetPasswordRequest;
import in.abdulmajid.lifeclues.auth.dto.VerifyEmailRequest;
import in.abdulmajid.lifeclues.auth.service.AuthService;
import in.abdulmajid.lifeclues.common.dto.MessageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return authService.register(request, httpRequest);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public MessageResponse logout(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.logout(request);
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                          HttpServletRequest httpRequest) {
        return authService.forgotPassword(request, httpRequest);
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request);
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return authService.verifyEmail(request);
    }

    @PostMapping("/resend-verification")
    public MessageResponse resendVerification(@Valid @RequestBody ResendVerificationRequest request,
                                              HttpServletRequest httpRequest) {
        return authService.resendVerification(request, httpRequest);
    }
}