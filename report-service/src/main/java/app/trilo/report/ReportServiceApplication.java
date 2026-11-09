package app.trilo.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"app.trilo.report", "app.trilo.common"})
@EnableFeignClients(basePackages = "app.trilo.common.client")
public class ReportServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ReportServiceApplication.class, args);
  }
}
