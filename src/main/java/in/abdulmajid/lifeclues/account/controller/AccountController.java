package in.abdulmajid.lifeclues.account.controller;

import in.abdulmajid.lifeclues.account.dto.UpdateProfileRequest;
import in.abdulmajid.lifeclues.account.dto.UserResponse;
import in.abdulmajid.lifeclues.account.service.AccountService;
import in.abdulmajid.lifeclues.common.dto.MessageResponse;
import in.abdulmajid.lifeclues.account.dto.ChangePasswordRequest;
import in.abdulmajid.lifeclues.security.CurrentUser;
import in.abdulmajid.lifeclues.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public UserResponse me(@CurrentUser UserPrincipal currentUser) {
        return accountService.me(currentUser.getId());
    }

    @PutMapping("/me")
    public UserResponse updateProfile(@CurrentUser UserPrincipal currentUser,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return accountService.updateProfile(currentUser.getId(), request);
    }

    @PostMapping("/me/change-password")
    public MessageResponse changePassword(@CurrentUser UserPrincipal currentUser,
                                          @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(currentUser.getId(), request);
        return MessageResponse.of("Password changed successfully");
    }

    @DeleteMapping("/me")
    public ResponseEntity<MessageResponse> deleteAccount(@CurrentUser UserPrincipal currentUser) {
        accountService.deleteAccount(currentUser.getId());
        return ResponseEntity.ok(MessageResponse.of("Account deleted successfully"));
    }
}