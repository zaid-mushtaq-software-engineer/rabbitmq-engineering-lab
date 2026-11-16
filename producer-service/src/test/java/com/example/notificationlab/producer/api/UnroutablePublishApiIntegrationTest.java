package com.example.notificationlab.producer.api;

import com.example.notificationlab.contracts.RabbitNames;
import com.example.notificationlab.producer.config.IntegrationRabbitTopologyConfig;
import com.example.notificationlab.producer.persistence.ProducerNotificationRepository;
import com.example.notificationlab.producer.persistence.ProducerNotificationStatus;
import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(IntegrationRabbitTopologyConfig.class)
class UnroutablePublishApiIntegrationTest {

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
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private ProducerNotificationRepository notificationRepository;

    @Test
    void postWithoutBindingsReturns503AndPublishFailed() throws Exception {
        rabbitAdmin.getRabbitTemplate().execute(channel -> {
            channel.queueDelete(RabbitNames.EMAIL_QUEUE);
            return null;
        });

        String json = mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"EMAIL","recipient":"alice@example.com","message":"unroutable"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("MESSAGE_PUBLISH_FAILED"))
                .andExpect(jsonPath("$.notificationId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String notificationId = com.jayway.jsonpath.JsonPath.read(json, "$.notificationId");
        assertThat(notificationRepository.findById(notificationId))
                .isPresent()
                .get()
                .extracting(e -> e.getStatus())
                .isEqualTo(ProducerNotificationStatus.PUBLISH_FAILED);
    }
}