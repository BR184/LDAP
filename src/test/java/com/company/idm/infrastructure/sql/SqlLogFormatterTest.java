package com.company.idm.infrastructure.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SqlLogFormatterTest {

    @Test
    void masksStoredPersonalAccessTokenValues() {
        SqlLogFormatter formatter = new SqlLogFormatter(new SqlLogProperties());

        assertThat(formatter.formatParameters(Map.of(
            "secretValue", "idm_pat_uid_secret",
            "name", "automation"
        )))
            .contains("secretValue=\"***\"")
            .doesNotContain("idm_pat_uid_secret");
    }
}
