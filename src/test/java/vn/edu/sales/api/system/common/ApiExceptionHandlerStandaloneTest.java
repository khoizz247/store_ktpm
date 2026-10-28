package vn.edu.sales.api.system.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import testfixtures.api.ApiErrorProbeController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ApiExceptionHandlerStandaloneTest {
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ApiErrorProbeController())
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private JsonNode error(MvcResult result, int expectedStatus, String expectedCode, String path) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode value = json.readTree(raw);
        assertThat(value.path("status").asInt()).isEqualTo(expectedStatus);
        assertThat(value.path("code").asText()).isEqualTo(expectedCode);
        assertThat(value.path("path").asText()).isEqualTo(path);
        assertThat(value.path("timestamp").asText()).isNotBlank();
        assertThat(value.path("message").asText()).isNotBlank();
        assertThat(value.has("fields")).isTrue();
        assertThat(value.has("fieldErrors")).isFalse();
        assertThat(value.has("trace")).isFalse();
        assertThat(raw).doesNotContain("DO_NOT_LEAK", "jdbc:mysql:", "SELECT secret_column", "private-host");
        return value;
    }

    @ParameterizedTest
    @CsvSource({
            "not-found,404,RESOURCE_NOT_FOUND",
            "business,409,BUSINESS_CONFLICT",
            "credentials,401,INVALID_CREDENTIALS",
            "bad-request,400,INVALID_REQUEST",
            "integrity,409,DATA_CONFLICT",
            "database,500,DATABASE_ERROR",
            "transaction-create,500,DATABASE_ERROR",
            "transaction-system,500,DATABASE_ERROR",
            "pessimistic,409,CONCURRENT_CHANGE",
            "optimistic,409,CONCURRENT_CHANGE"
    })
    void adviceMapsExceptionsWithoutLeakingInternalDatabaseDetails(String kind, int status, String code) throws Exception {
        String path = "/__test-errors/" + kind;
        error(mvc.perform(get(path)).andReturn(), status, code, path);
    }

    @Test
    void validationIncludesFieldName() throws Exception {
        String path = "/__test-errors/body";
        var result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")).andReturn();
        assertThat(error(result, 400, "VALIDATION_ERROR", path).path("fields").has("name")).isTrue();
    }

    @Test
    void malformedJsonMissingParameterAndWrongTypeAre400() throws Exception {
        String path = "/__test-errors/body";
        error(mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{")).andReturn(),
                400, "INVALID_REQUEST", path);
        path = "/__test-errors/number";
        error(mvc.perform(get(path)).andReturn(), 400, "INVALID_REQUEST", path);
        error(mvc.perform(get(path).param("value", "not-a-number")).andReturn(), 400, "INVALID_REQUEST", path);
    }
}
