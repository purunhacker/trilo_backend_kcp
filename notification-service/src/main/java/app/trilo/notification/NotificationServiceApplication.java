package app.trilo.notification;

import app.trilo.notification.config.NotificationProperties;
import app.trilo.notification.config.PushProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"app.trilo.notification", "app.trilo.common"})
@EnableFeignClients(basePackages = "app.trilo.common.client")
@EnableConfigurationProperties({NotificationProperties.class, PushProperties.class})
public class NotificationServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(NotificationServiceApplication.class, args);
  }
}
