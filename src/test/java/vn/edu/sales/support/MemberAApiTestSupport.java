package vn.edu.sales.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.edu.sales.application.port.out.PasswordHasher;
import vn.edu.sales.application.port.out.UserRepository;
import vn.edu.sales.domain.model.Role;
import vn.edu.sales.domain.model.User;
import vn.edu.sales.domain.model.UserStatus;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("member-a-test")
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
public abstract class MemberAApiTestSupport {
    protected static final String EMAIL = "customer-a@example.test";
    protected static final String ORIGINAL = "OldPassword@123";
    protected static final String REPLACEMENT = "NewPassword@456";
    @Autowired
    protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordHasher passwords;
    @Autowired private DataSource dataSource;
    @Value("${app.jwt.secret}") protected String testSecret;

    @BeforeEach
    protected void resetOnlyOurH2Database() throws Exception {
        // Never run cleanup against Docker, local MySQL, or another test database.
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .startsWith("jdbc:h2:mem:member-a-handoff");
        }
        for (String table : List.of("inventory_movements", "cart_items", "order_items", "orders", "products", "users")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    protected String body(Object value) throws Exception { return json.writeValueAsString(value); }

    protected JsonNode payload(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected Long createUser(String email, Role role) {
        return users.save(new User(null, email, passwords.hash(ORIGINAL), "Member A test", role,
                UserStatus.ACTIVE, null)).id();
    }

    protected String login(String email, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn();
        JsonNode response = payload(result);
        assertThat(response.path("accessToken").asText()).isNotBlank();
        assertThat(response.path("tokenType").asText()).isEqualTo("Bearer");
        return response.get("accessToken").asText();
    }

    protected String registerCustomer() throws Exception {
        var result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("fullName", "Member A customer", "email", EMAIL, "password", ORIGINAL))))
                .andExpect(status().isCreated()).andReturn();
        assertThat(payload(result).path("role").asText()).isEqualTo("CUSTOMER");
        // Get the token through real login, not a mocked authentication context.
        return login(EMAIL, ORIGINAL);
    }

    protected String hashOf(String email) {
        return users.findByEmail(email).orElseThrow().passwordHash();
    }

    protected void assertError(MvcResult result, int status, String code, String path) throws Exception {
        JsonNode error = payload(result);
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(error.path("status").asInt()).isEqualTo(status);
        assertThat(error.path("code").asText()).isEqualTo(code);
        assertThat(error.path("path").asText()).isEqualTo(path);
        assertThat(error.path("timestamp").asText()).isNotBlank();
        assertThat(error.path("message").asText()).isNotBlank();
        assertThat(error.has("fields")).isTrue();
        assertThat(error.has("fieldErrors")).isFalse();
        assertThat(error.has("trace")).isFalse();
    }
}
