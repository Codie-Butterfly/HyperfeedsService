package zw.co.hyperfeeds.catalogue;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogueImportServiceTests {
    @Test void parsesQuotedCommasAndEscapedQuotes() {
        var rows=CatalogueImportService.parseCsv("sku,name,category,pack_size\nF1,\"Feed, Starter\",Feed,\"50kg \"\"bag\"\"\"\n");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1)).containsExactly("F1","Feed, Starter","Feed","50kg \"bag\"");
    }
    @Test void rejectsUnclosedQuotes() {
        assertThatThrownBy(() -> CatalogueImportService.parseCsv("sku,name\n1,\"broken"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
