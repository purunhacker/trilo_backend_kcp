package app.trilo.gallery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"app.trilo.gallery", "app.trilo.common"})
public class GalleryServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(GalleryServiceApplication.class, args);
  }
}
