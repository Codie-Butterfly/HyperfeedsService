package zw.co.hyperfeeds.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/auth/customers")
class CustomerAuthController {
    private final CustomerRegistrationService registration;
    CustomerAuthController(CustomerRegistrationService registration) { this.registration = registration; }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.ACCEPTED)
    CustomerRegistrationService.SignupResult signup(@Valid @RequestBody SignupRequest request) {
        return registration.signup(request.phoneNumber(), request.firstName(), request.lastName());
    }

    @PostMapping("/verify-phone")
    EmployeeAuthenticationService.TokenResult verify(@Valid @RequestBody VerifyRequest request) {
        return registration.verify(request.challengeId(), request.code());
    }
    @PostMapping("/refresh") EmployeeAuthenticationService.TokenResult refresh(@Valid @RequestBody RefreshRequest request) {
        return registration.refresh(request.refreshToken());
    }

    record SignupRequest(@NotBlank String phoneNumber,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName) {}
    record VerifyRequest(@NotNull UUID challengeId,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "must be a six-digit code") String code) {}
    record RefreshRequest(@NotBlank String refreshToken) {}
}
