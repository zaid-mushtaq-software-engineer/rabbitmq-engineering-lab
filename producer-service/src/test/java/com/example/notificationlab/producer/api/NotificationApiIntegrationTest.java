package com.example.notificationlab.producer.api;

import com.example.notificationlab.contracts.RabbitNames;
import com.example.notificationlab.producer.config.IntegrationRabbitTopologyConfig;
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

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(IntegrationRabbitTopologyConfig.class)
class NotificationApiIntegrationTest {

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

    @Test
    void postEmailRoutesToEmailQueue() throws Exception {
        purgeQueues();
        String body = mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"EMAIL","recipient":"alice@example.com","message":"route-test"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("notificationId");
        assertThat(queueDepth(RabbitNames.EMAIL_QUEUE)).isEqualTo(1);
        assertThat(queueDepth(RabbitNames.SMS_QUEUE)).isZero();
    }

    @Test
    void postSmsRoutesToSmsQueue() throws Exception {
        purgeQueues();
        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"SMS","recipient":"+15550100099","message":"route-test"}
                                """))
                .andExpect(status().isAccepted());

        assertThat(queueDepth(RabbitNames.SMS_QUEUE)).isEqualTo(1);
        assertThat(queueDepth(RabbitNames.EMAIL_QUEUE)).isZero();
    }

    @Test
    void getByIdAfterPost() throws Exception {
        String notificationId = mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"EMAIL","recipient":"alice@example.com","message":"get-test"}
                                """))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        notificationId = com.jayway.jsonpath.JsonPath.read(notificationId, "$.notificationId");

        mockMvc.perform(get("/api/v1/notifications/" + notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.recipient").value("alice@example.com"));
    }

    private int queueDepth(String queue) {
        Properties props = rabbitAdmin.getQueueProperties(queue);
        return props == null ? 0 : Integer.parseInt(props.get("QUEUE_MESSAGE_COUNT").toString());
    }

    private void purgeQueues() {
        rabbitAdmin.purgeQueue(RabbitNames.EMAIL_QUEUE, false);
        rabbitAdmin.purgeQueue(RabbitNames.SMS_QUEUE, false);
    }
}
