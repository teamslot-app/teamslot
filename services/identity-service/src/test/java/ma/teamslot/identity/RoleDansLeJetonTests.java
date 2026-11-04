package ma.teamslot.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import com.nimbusds.jwt.SignedJWT;
import ma.teamslot.identity.application.TokenService;
import ma.teamslot.identity.domain.PlatformRole;
import org.junit.jupiter.api.Test;

/** SCRUM-74 : le jeton d'accès porte le rôle, signé par identity. */
class RoleDansLeJetonTests {

    private final TokenService tokens = newTokens();

    private static TokenService newTokens() {
        try {
            return new TokenService("", "teamslot-identity", 900);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void lejetonPorteLeRoleEtLeSub() throws Exception {
        UUID userId = UUID.randomUUID();
        for (PlatformRole role : PlatformRole.values()) {
            SignedJWT jwt = SignedJWT.parse(tokens.createAccessToken(userId, role));
            assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo(role.name());
            assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(userId.toString());
        }
    }
}
