package app.trilo.weather;

import app.trilo.weather.config.WeatherProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"app.trilo.weather", "app.trilo.common"})
@EnableFeignClients(basePackages = "app.trilo.common.client")
@EnableConfigurationProperties(WeatherProperties.class)
public class WeatherServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(WeatherServiceApplication.class, args);
  }
}
