package com.company.idm.interfaces.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CurrentUserResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Test
    void alwaysPublishesTheCompleteDepartmentContract() throws Exception {
        CurrentUserResponse response = new CurrentUserResponse(
            1L, "employee-a", "Employee A", null, null, null, null, null,
            "FD_DAAB006E3D5E", null, null, Set.of("NORMAL_USER")
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.has("deptCode")).isTrue();
        assertThat(json.has("deptName")).isTrue();
        assertThat(json.has("departmentPath")).isTrue();
        assertThat(json.get("deptCode").asText()).isEqualTo("FD_DAAB006E3D5E");
        assertThat(json.get("deptName").isNull()).isTrue();
        assertThat(json.get("departmentPath").isNull()).isTrue();
    }
}
