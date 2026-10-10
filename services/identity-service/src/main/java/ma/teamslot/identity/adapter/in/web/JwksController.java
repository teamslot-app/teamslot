package ma.teamslot.identity.adapter.in.web;

import java.util.Map;

import ma.teamslot.identity.application.TokenService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Clé publique de vérification des jetons (ADR-013), lue par la passerelle et les services. */
@RestController
public class JwksController {

    private final TokenService tokens;

    public JwksController(TokenService tokens) {
        this.tokens = tokens;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return tokens.publicJwks();
    }
}
