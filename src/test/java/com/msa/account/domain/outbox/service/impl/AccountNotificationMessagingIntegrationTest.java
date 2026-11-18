package com.msa.account.domain.outbox.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import com.msa.account.domain.notification.service.BusinessNoticeService;
import com.msa.account.domain.outbox.repository.OutboxEventRepository;
import com.msa.account.global.config.database.TestcontainersConfiguration;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;

import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 실제 Kafka를 중단/복구하고 자동 발행 작업이 미발행 Outbox를 재전송하는지 확인합니다. */
@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "account.outbox.publisher-enabled=true",
            "account.outbox.poll-ms=50"
        })
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountNotificationMessagingIntegrationTest {
    static final String TOPIC = "stockdrop.notifications.account.v1";
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        KAFKA.start();
        try (var admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1)))
                    .all()
                    .get(20, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry p) {
        p.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired BusinessNoticeService notices;
    @Autowired OutboxEventRepository outbox;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper json;
    @MockitoBean Clock clock;

    @Test
    void kafkaRecoveryPublishesOriginalPendingEventAutomatically() throws Exception {
        var now = new AtomicReference<>(Instant.parse("2030-01-01T00:00:00Z"));
        when(clock.instant()).thenAnswer(i -> now.get());
        UUID account = UUID.randomUUID(), aggregate = UUID.randomUUID();
        UUID event =
                UUID.nameUUIDFromBytes(
                        ("ACCOUNT:SELLER_APPROVED:" + aggregate + ":1:" + account)
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var docker = DockerClientFactory.instance().client();
        boolean paused = false;
        try {
            docker.pauseContainerCmd(KAFKA.getContainerId()).exec();
            paused = true;
            new TransactionTemplate(manager)
                    .executeWithoutResult(
                            s ->
                                    notices.enqueue(
                                            account,
                                            "SELLER_APPROVED",
                                            aggregate,
                                            1,
                                            "SELLER_APPLICATION",
                                            aggregate,
                                            null,
                                            null));
            await().atMost(Duration.ofSeconds(25))
                    .untilAsserted(
                            () -> {
                                var row = outbox.findById(event).orElseThrow();
                                assertThat(row.attempts).isGreaterThanOrEqualTo(1);
                                assertThat(row.publishedAt).isNull();
                            });
            docker.unpauseContainerCmd(KAFKA.getContainerId()).exec();
            paused = false;
            now.set(now.get().plusSeconds(120));
            await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(
                            () -> {
                                var row = outbox.findById(event).orElseThrow();
                                assertThat(row.publishedAt).isNotNull();
                                assertThat(row.attempts).isGreaterThanOrEqualTo(2);
                            });
            try (var consumer =
                    new KafkaConsumer<String, byte[]>(
                            Map.of(
                                    "bootstrap.servers",
                                    KAFKA.getBootstrapServers(),
                                    "group.id",
                                    "notice-outbox-test-" + UUID.randomUUID(),
                                    "auto.offset.reset",
                                    "earliest",
                                    "key.deserializer",
                                    StringDeserializer.class.getName(),
                                    "value.deserializer",
                                    ByteArrayDeserializer.class.getName()))) {
                consumer.subscribe(List.of(TOPIC));
                boolean found = false;
                Instant until = Instant.now().plusSeconds(15);
                while (!found && Instant.now().isBefore(until))
                    for (var record : consumer.poll(Duration.ofMillis(300)))
                        if (event.toString()
                                .equals(json.readTree(record.value()).path("eventId").asText())) {
                            assertThat(record.key()).isEqualTo(aggregate.toString());
                            found = true;
                        }
                assertThat(found).isTrue();
            }
        } finally {
            if (paused) docker.unpauseContainerCmd(KAFKA.getContainerId()).exec();
        }
    }
}
