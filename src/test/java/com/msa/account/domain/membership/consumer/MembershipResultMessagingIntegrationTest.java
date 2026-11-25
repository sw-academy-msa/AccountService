package com.msa.account.domain.membership.consumer;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.msa.account.domain.membership.repository.MembershipRepository;
import com.msa.account.global.config.database.TestcontainersConfiguration;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.kafka.KafkaContainer;

import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 실제 Kafka에서 정상 결과는 projection으로, 계약 오류는 재시도 없이 DLT로 보내는지 확인합니다. */
@SpringBootTest(
        properties = {
            "spring.profiles.active=test",
            "account.membership.consumer-enabled=true",
            "account.membership.expiring-poll-ms=3600000"
        })
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MembershipResultMessagingIntegrationTest {
    static final String TOPIC = "stockdrop.membership.events.private.v1";
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        KAFKA.start();
        try (var admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(
                            List.of(
                                    new NewTopic(TOPIC, 2, (short) 1),
                                    new NewTopic(TOPIC + ".DLT", 2, (short) 1)))
                    .all()
                    .get(20, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired KafkaTemplate<String, byte[]> kafka;
    @Autowired MembershipRepository memberships;
    @Autowired ObjectMapper json;

    private Map<String, Object> event(UUID account) {
        var e = new LinkedHashMap<String, Object>();
        e.put("eventId", UUID.randomUUID());
        e.put("schemaVersion", 1);
        e.put("type", "MEMBERSHIP_STARTED");
        e.put("source", "PAYMENT");
        e.put("accountId", account);
        e.put("subscriptionId", UUID.randomUUID());
        e.put("generation", 1);
        e.put("aggregateVersion", 1);
        e.put("cycleNumber", 1);
        e.put("state", "ACTIVE");
        e.put("autoRenew", true);
        e.put("periodStart", Instant.parse("2030-01-01T00:00:00Z"));
        e.put("periodEnd", Instant.parse("2030-02-01T00:00:00Z"));
        e.put("entitlementUntil", Instant.parse("2030-02-01T00:00:00Z"));
        e.put("graceUntil", null);
        e.put("amount", 4900);
        e.put("currency", "KRW");
        e.put("planVersion", "monthly-v1");
        e.put("occurredAt", Instant.parse("2030-01-01T00:00:00Z"));
        return e;
    }

    @Test
    void validResultProjectsAndInvalidResultGoesToDlt() throws Exception {
        UUID account = UUID.randomUUID();
        kafka.send(TOPIC, account.toString(), json.writeValueAsBytes(event(account)))
                .get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(memberships.findById(account)).isPresent());
        // 파티션 키와 accountId가 다르면 재시도해도 성공하지 않으므로 즉시 DLT로 격리합니다.
        UUID other = UUID.randomUUID();
        var invalid = event(other);
        kafka.send(TOPIC, UUID.randomUUID().toString(), json.writeValueAsBytes(invalid))
                .get(10, TimeUnit.SECONDS);
        try (var consumer =
                new KafkaConsumer<String, byte[]>(
                        Map.of(
                                "bootstrap.servers",
                                KAFKA.getBootstrapServers(),
                                "group.id",
                                "membership-dlt-test-" + UUID.randomUUID(),
                                "auto.offset.reset",
                                "earliest"),
                        new StringDeserializer(),
                        new ByteArrayDeserializer())) {
            consumer.subscribe(List.of(TOPIC + ".DLT"));
            await().atMost(Duration.ofSeconds(25))
                    .until(
                            () -> {
                                for (var record : consumer.poll(Duration.ofMillis(300)))
                                    if (invalid.get("eventId")
                                            .toString()
                                            .equals(
                                                    json.readTree(record.value())
                                                            .path("eventId")
                                                            .asText())) return true;
                                return false;
                            });
        }
        assertThat(memberships.findById(other)).isEmpty();
    }
}
