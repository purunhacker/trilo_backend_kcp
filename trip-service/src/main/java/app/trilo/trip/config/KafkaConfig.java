package app.trilo.trip.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

  public static final String TRIP_DELETED_TOPIC = "trilo.trip.deleted";

  @Bean
  public NewTopic tripDeletedTopic() {
    return TopicBuilder.name(TRIP_DELETED_TOPIC).partitions(3).replicas(1).build();
  }
}
