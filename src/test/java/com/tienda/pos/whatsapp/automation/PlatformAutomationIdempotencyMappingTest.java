package com.tienda.pos.whatsapp.automation;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAutomationIdempotencyMappingTest {
    @Test
    void responseJsonMatchesFlywayLongtextColumn() throws Exception {
        Column column = PlatformAutomationIdempotency.class
                .getDeclaredField("responseJson")
                .getAnnotation(Column.class);

        assertThat(column).isNotNull();
        assertThat(column.columnDefinition()).isEqualTo("LONGTEXT");
        assertThat(column.nullable()).isFalse();
    }
}
