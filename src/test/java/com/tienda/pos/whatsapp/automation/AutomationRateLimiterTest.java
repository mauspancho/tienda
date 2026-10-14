package com.tienda.pos.whatsapp.automation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutomationRateLimiterTest {
    @Test
    void rejectsRequestsAfterPerTokenLimit() {
        AutomationRateLimiter limiter = new AutomationRateLimiter();

        for (int request = 0; request < 120; request++) {
            assertThatCode(() -> limiter.check(42L)).doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> limiter.check(42L))
                .isInstanceOf(AutomationRateLimitException.class);
        assertThatCode(() -> limiter.check(43L)).doesNotThrowAnyException();
    }
}
