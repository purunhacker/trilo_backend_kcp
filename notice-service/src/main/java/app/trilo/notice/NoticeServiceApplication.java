package app.trilo.notice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"app.trilo.notice", "app.trilo.common"})
@EnableFeignClients(basePackages = "app.trilo.common.client")
public class NoticeServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(NoticeServiceApplication.class, args);
  }
}
