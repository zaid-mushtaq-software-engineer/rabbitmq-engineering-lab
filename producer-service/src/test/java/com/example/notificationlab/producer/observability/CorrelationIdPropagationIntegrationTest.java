package com.example.notificationlab.producer.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.notificationlab.contracts.RabbitNames;
import com.example.notificationlab.producer.config.IntegrationRabbitTopologyConfig;
import com.example.notificationlab.contracts.ObservabilityKeys;
import com.example.notificationlab.producer.error.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(IntegrationRabbitTopologyConfig.class)
class CorrelationIdPropagationIntegrationTest {

    private static final String TRACE_ID = "trace-demo-1";

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("notification_lab")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.rabbitmq.publisher-confirm-type", () -> "correlated");
        registry.add("spring.rabbitmq.publisher-returns", () -> "true");
        registry.add("lab.publisher.confirm-timeout", () -> "15s");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void attachLogCapture() {
        rabbitAdmin.purgeQueue(RabbitNames.EMAIL_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitNames.SMS_QUEUE, false);
        listAppender = new ListAppender<>();
        listAppender.start();
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.addAppender(listAppender);
    }

    @AfterEach
    void detachLogCapture() {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.detachAppender(listAppender);
    }

    @Test
    void submitEchoesCorrelationIdInLogs() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .header(CorrelationIdFilter.HEADER, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"EMAIL","recipient":"alice@example.com","message":"hi"}
                                """))
                .andExpect(status().isAccepted());

        assertThat(listAppender.list.stream().anyMatch(event ->
                TRACE_ID.equals(event.getMDCPropertyMap().get(ObservabilityKeys.MDC_CORRELATION_ID))
                        && event.getFormattedMessage().contains("notification.accepted")
        )).isTrue();
        assertThat(listAppender.list.stream().anyMatch(event ->
                TRACE_ID.equals(event.getMDCPropertyMap().get(ObservabilityKeys.MDC_CORRELATION_ID))
                        && event.getFormattedMessage().contains("publish.confirmed")
        )).isTrue();
    }
}