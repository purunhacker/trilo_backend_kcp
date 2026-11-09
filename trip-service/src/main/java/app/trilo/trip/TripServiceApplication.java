package app.trilo.trip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"app.trilo.trip", "app.trilo.common"})
@EnableFeignClients(basePackages = {"app.trilo.trip", "app.trilo.common.client"})
public class TripServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(TripServiceApplication.class, args);
  }
}
