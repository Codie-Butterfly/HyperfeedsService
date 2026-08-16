package zw.co.hyperfeeds.identity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerRegistrationServiceTests {
    @Test
    void normalizesZimbabweLocalNumbers() {
        assertThat(CustomerRegistrationService.normalizePhone("0771 234 567"))
                .isEqualTo("+263771234567");
    }

    @Test
    void preservesValidInternationalNumbers() {
        assertThat(CustomerRegistrationService.normalizePhone("+27 (82) 123-4567"))
                .isEqualTo("+27821234567");
    }

    @Test
    void rejectsInvalidNumbers() {
        assertThatThrownBy(() -> CustomerRegistrationService.normalizePhone("123"))
                .isInstanceOf(IdentityException.class);
    }
}
