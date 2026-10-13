package ma.teamslot.events.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import ma.teamslot.events.EventReader;
import ma.teamslot.events.IdempotentProcessor;
import ma.teamslot.events.Outbox;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * Règles de l'ADR-015 pour l'auto-configuration, sans base ni Kafka (dépendances simulées) :
 * - un service obtient tous les beans en ajoutant la dépendance ;
 * - un bean du service remplace celui du module (@ConditionalOnMissingBean) ;
 * - une propriété désactive le relais planifié.
 */
class EventsAutoConfigurationTest {

    private final ApplicationContextRunner contexte = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventsAutoConfiguration.class))
            .withPropertyValues("spring.application.name=service-de-test")
            // Réponses vides : si le relais planifié tourne pendant le test, il ne trouve rien à publier.
            .withBean(JdbcClient.class, () -> mock(JdbcClient.class, RETURNS_DEEP_STUBS))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class));

    @Test
    void fournit_tous_les_beans_avec_la_seule_dependance() {
        contexte.run(ctx -> {
            assertThat(ctx).hasSingleBean(Outbox.class);
            assertThat(ctx.getBean(Outbox.class)).isInstanceOf(JdbcOutbox.class);
            assertThat(ctx).hasSingleBean(OutboxRelay.class);
            assertThat(ctx).hasSingleBean(EventReader.class);
            assertThat(ctx).hasSingleBean(IdempotentProcessor.class);
            assertThat(ctx).hasSingleBean(DefaultErrorHandler.class);
        });
    }

    @Test
    void un_bean_du_service_remplace_celui_du_module() {
        Outbox outboxDuService = (aggregateId, type, data) -> { };
        CommonErrorHandler erreursDuService = mock(CommonErrorHandler.class);

        contexte.withBean(Outbox.class, () -> outboxDuService)
                .withBean(CommonErrorHandler.class, () -> erreursDuService)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(Outbox.class);
                    assertThat(ctx.getBean(Outbox.class)).isSameAs(outboxDuService);
                    assertThat(ctx).hasSingleBean(CommonErrorHandler.class);
                    assertThat(ctx).doesNotHaveBean(DefaultErrorHandler.class);
                });
    }

    @Test
    void le_relais_est_planifie_par_defaut() {
        contexte.run(ctx -> assertThat(ctx)
                .hasBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
    }

    @Test
    void une_propriete_desactive_le_relais_planifie() {
        contexte.withPropertyValues("teamslot.outbox.relay.enabled=false")
                .run(ctx -> assertThat(ctx)
                        .doesNotHaveBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
    }

    @Test
    void refuse_de_demarrer_sans_nom_de_service() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(EventsAutoConfiguration.class))
                .withBean(JdbcClient.class, () -> mock(JdbcClient.class))
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("spring.application.name"));
    }
}
