package app.trilo.common.client;

import app.trilo.common.dto.ServiceExportData;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "media-service", url = "${services.media-service-url:http://localhost:8083}")
public interface MediaInternalClient {

  /** Returns the user's media metadata plus original media files to bundle in a data export. */
  @GetMapping("/media/internal/users/{userId}/export")
  ServiceExportData getUserExport(@PathVariable("userId") UUID userId);
}
