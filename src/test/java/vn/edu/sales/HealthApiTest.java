package vn.edu.sales;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
// This test uses real application beans, so Mockito reset/agent listeners are unnecessary.
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class HealthApiTest {
    @Autowired
    MockMvc mvc;

    @Test
    void baseStartsAndExposesItsHealthContract() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.service").value("single-store-service"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.stage").value("base"));
    }
}
