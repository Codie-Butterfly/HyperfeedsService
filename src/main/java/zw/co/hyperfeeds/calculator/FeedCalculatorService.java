package zw.co.hyperfeeds.calculator;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class FeedCalculatorService {
    private static final BigDecimal DEFAULT_BAG_SIZE_KG = new BigDecimal("50.00");
    private final JdbcClient jdbc;

    public FeedCalculatorService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProfileView> profiles() {
        return jdbc.sql("""
                select code,animal_name,description,default_days,source_label,source_url,disclaimer
                from feed_calculator_profiles
                where active
                order by display_order,animal_name
                """).query(ProfileView.class).list();
    }

    public CalculationView calculate(String profileCode, int animalCount, Integer requestedDays,
                                     BigDecimal requestedBagSizeKg) {
        ProfileView profile = jdbc.sql("""
                select code,animal_name,description,default_days,source_label,source_url,disclaimer
                from feed_calculator_profiles where code=:code and active
                """).param("code", profileCode).query(ProfileView.class).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Calculator profile not found"));

        int days = requestedDays == null ? profile.defaultDays() : requestedDays;
        BigDecimal bagSizeKg = requestedBagSizeKg == null ? DEFAULT_BAG_SIZE_KG : requestedBagSizeKg;
        List<PhaseDefinition> definitions = jdbc.sql("""
                select f.phase_name,f.rate_mode,f.rate_kg_per_animal,f.phase_days,f.uses_requested_days,
                       f.notes,p.sku product_sku,p.name product_name,p.image_url
                from feed_calculator_phases f
                left join products p on p.sku=f.product_sku
                where f.profile_code=:code
                order by f.display_order,f.phase_name
                """).param("code", profileCode).query(PhaseDefinition.class).list();

        List<PhaseView> phases = definitions.stream()
                .map(phase -> calculatePhase(phase, animalCount, days, bagSizeKg))
                .toList();
        BigDecimal totalKg = phases.stream().map(PhaseView::totalKg).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal exactBags = totalKg.divide(bagSizeKg, 2, RoundingMode.HALF_UP);
        int bagsToBuy = totalKg.divide(bagSizeKg, 0, RoundingMode.CEILING).intValueExact();

        return new CalculationView(profile.code(), profile.animalName(), animalCount, days,
                bagSizeKg.setScale(2, RoundingMode.HALF_UP), totalKg, exactBags, bagsToBuy,
                profile.sourceLabel(), profile.sourceUrl(), profile.disclaimer(), phases);
    }

    static PhaseView calculatePhase(PhaseDefinition phase, int animalCount, int requestedDays,
                                    BigDecimal bagSizeKg) {
        int appliedDays = phase.usesRequestedDays() ? requestedDays : phase.phaseDays();
        BigDecimal quantityPerAnimal = phase.rateKgPerAnimal();
        if ("DAILY".equals(phase.rateMode())) {
            quantityPerAnimal = quantityPerAnimal.multiply(BigDecimal.valueOf(appliedDays));
        }
        BigDecimal totalKg = quantityPerAnimal.multiply(BigDecimal.valueOf(animalCount))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal exactBags = totalKg.divide(bagSizeKg, 2, RoundingMode.HALF_UP);
        int bagsToBuy = totalKg.divide(bagSizeKg, 0, RoundingMode.CEILING).intValueExact();
        return new PhaseView(phase.phaseName(), phase.productSku(), phase.productName(), phase.imageUrl(),
                phase.rateMode(), appliedDays, quantityPerAnimal.setScale(3, RoundingMode.HALF_UP),
                totalKg, exactBags, bagsToBuy, phase.notes());
    }

    public record ProfileView(String code, String animalName, String description, int defaultDays,
                              String sourceLabel, String sourceUrl, String disclaimer) {}
    record PhaseDefinition(String phaseName, String rateMode, BigDecimal rateKgPerAnimal, int phaseDays,
                           boolean usesRequestedDays, String notes, String productSku, String productName,
                           String imageUrl) {}
    public record PhaseView(String phaseName, String productSku, String productName, String imageUrl,
                            String rateMode, int appliedDays, BigDecimal kgPerAnimal, BigDecimal totalKg,
                            BigDecimal exactBags, int bagsToBuy, String notes) {}
    public record CalculationView(String profileCode, String animalName, int animalCount, int days,
                                  BigDecimal bagSizeKg, BigDecimal totalKg, BigDecimal exactBags,
                                  int bagsToBuy, String sourceLabel, String sourceUrl, String disclaimer,
                                  List<PhaseView> phases) {}
}
