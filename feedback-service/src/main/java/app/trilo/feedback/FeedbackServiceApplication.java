package app.trilo.feedback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"app.trilo.feedback", "app.trilo.common"})
public class FeedbackServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(FeedbackServiceApplication.class, args);
  }
}
