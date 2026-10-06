package ma.teamslot.servicetemplate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecuriteTests {

    private static final String VERSION = "/api/v1/template/version";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sans_jeton_401() throws Exception {
        mockMvc.perform(get(VERSION)).andExpect(status().isUnauthorized());
    }

    @Test
    void jeton_illisible_401() throws Exception {
        mockMvc.perform(get(VERSION).header("Authorization", "Bearer pas-un-jwt"))
                .andExpect(status().isUnauthorized());
    }

    /** Jeton non signe ("alg": "none") : refuse, meme avec le bon emetteur (ADR-013). */
    @Test
    void jeton_non_signe_401() throws Exception {
        String nonSigne = base64("{\"alg\":\"none\"}") + "." + base64("{\"sub\":\"1\",\"iss\":\"teamslot-identity\"}") + ".";
        mockMvc.perform(get(VERSION).header("Authorization", "Bearer " + nonSigne))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jeton_valide_accepte() throws Exception {
        mockMvc.perform(get(VERSION).with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isOk());
    }

    @Test
    void sante_publique_sans_jeton() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    /** Encode une partie de JWT (Base64URL sans remplissage). */
    private static String base64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
