package app.trilo.auth.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

  public static final String USER_DELETED_TOPIC = "trilo.user.deleted";

  @Bean
  public NewTopic userDeletedTopic() {
    return TopicBuilder.name(USER_DELETED_TOPIC).partitions(3).replicas(1).build();
  }
}
