package ma.teamslot.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorAndCorrelationTests {

    private static final String UUID_REGEX =
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownRouteReturnsProblemDetailWithCorrelationId() throws Exception {
        mockMvc.perform(get("/api/v1/match/inexistant"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.correlationId").exists())
            .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void wrongMethodReturns405WithCorrelationId() throws Exception {
        mockMvc.perform(post("/api/v1/match/version"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void validIncomingCorrelationIdIsKept() throws Exception {
        String id = "7f3c1e0a-5b0e-4a77-9c42-1f6c2d9b8e10";
        mockMvc.perform(get("/api/v1/match/version").header("X-Correlation-Id", id))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Correlation-Id", id));
    }

    @Test
    void invalidIncomingCorrelationIdIsReplaced() throws Exception {
        MvcResult result = mockMvc
            .perform(get("/api/v1/match/version").header("X-Correlation-Id", "pas-un-uuid"))
            .andExpect(status().isOk())
            .andReturn();
        String id = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(id).matches(UUID_REGEX).isNotEqualTo("pas-un-uuid");
    }
}
