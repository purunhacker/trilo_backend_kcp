package app.trilo.mail;

import app.trilo.mail.config.MailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"app.trilo.mail", "app.trilo.common"})
@EnableConfigurationProperties(MailProperties.class)
public class MailServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(MailServiceApplication.class, args);
  }
}
