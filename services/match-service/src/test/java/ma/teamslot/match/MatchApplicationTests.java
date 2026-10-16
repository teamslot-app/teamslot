package ma.teamslot.match;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(MongoDeTest.class)
class MatchApplicationTests {

    @Test
    void contextLoads() {
    }
}
