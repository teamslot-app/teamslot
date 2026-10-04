package ma.teamslot.venue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VersionEndpointTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void versionEndpointReturnsVersion() throws Exception {
        mockMvc.perform(get("/api/v1/template/version"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").exists());
    }
}
