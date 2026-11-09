package app.trilo.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"app.trilo.admin", "app.trilo.common"})
@EnableFeignClients(basePackages = {"app.trilo.admin", "app.trilo.common.client"})
public class AdminApplication {
  public static void main(String[] args) {
    SpringApplication.run(AdminApplication.class, args);
  }
}
