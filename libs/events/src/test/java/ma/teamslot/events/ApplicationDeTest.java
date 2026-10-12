package ma.teamslot.events;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Application minimale pour les tests de libs/events : une bibliothèque n'a pas de classe main.
 * Pas de scan de paquets : les beans viennent seulement de l'auto-configuration, comme dans un service.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class ApplicationDeTest {
}
