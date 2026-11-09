package app.trilo.ai;

import app.trilo.ai.config.AiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"app.trilo.ai", "app.trilo.common"})
@EnableFeignClients(basePackages = "app.trilo.common.client")
@EnableConfigurationProperties(AiProperties.class)
public class AiServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AiServiceApplication.class, args);
  }
}
