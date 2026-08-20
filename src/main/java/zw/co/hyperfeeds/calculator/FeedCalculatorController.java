package zw.co.hyperfeeds.calculator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/feed-calculator")
class FeedCalculatorController {
    private final FeedCalculatorService calculator;

    FeedCalculatorController(FeedCalculatorService calculator) {
        this.calculator = calculator;
    }

    @GetMapping("/profiles")
    List<FeedCalculatorService.ProfileView> profiles() {
        return calculator.profiles();
    }

    @PostMapping("/calculate")
    FeedCalculatorService.CalculationView calculate(@Valid @RequestBody CalculationRequest request) {
        return calculator.calculate(request.profileCode().trim().toUpperCase(), request.animalCount(),
                request.days(), request.bagSizeKg());
    }

    record CalculationRequest(
            @NotBlank String profileCode,
            @NotNull @Min(1) @Max(1_000_000) Integer animalCount,
            @Min(1) @Max(365) Integer days,
            @DecimalMin("1.0") @DecimalMax("1000.0") BigDecimal bagSizeKg) {}
}
