package zw.co.hyperfeeds.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/employees")
class EmployeeAuthController {
    private final EmployeeAuthenticationService authentication;
    EmployeeAuthController(EmployeeAuthenticationService authentication) { this.authentication = authentication; }

    @PostMapping("/login")
    EmployeeAuthenticationService.TokenResult login(@Valid @RequestBody LoginRequest request) {
        return authentication.login(request.phoneNumber(), request.password());
    }

    @PostMapping("/refresh")
    EmployeeAuthenticationService.TokenResult refresh(@Valid @RequestBody RefreshRequest request) {
        return authentication.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest request) { authentication.logout(request.refreshToken()); }

    record LoginRequest(@NotBlank String phoneNumber, @NotBlank String password) {}
    record RefreshRequest(@NotBlank String refreshToken) {}
}
