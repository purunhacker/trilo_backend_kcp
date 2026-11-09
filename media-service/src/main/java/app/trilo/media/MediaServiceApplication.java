package app.trilo.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"app.trilo.media", "app.trilo.common"})
@EnableFeignClients(basePackages = "app.trilo.common.client")
@EnableAsync
public class MediaServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(MediaServiceApplication.class, args);
  }
}
