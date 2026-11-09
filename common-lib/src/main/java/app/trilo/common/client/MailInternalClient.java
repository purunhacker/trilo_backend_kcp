package app.trilo.common.client;

import app.trilo.common.dto.MailSendRequest;
import app.trilo.common.dto.MailSendResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Internal client other services use to send transactional emails. The shared secret header is
 * added automatically by {@link MailFeignConfig}.
 */
@FeignClient(
    name = "mail-service",
    url = "${services.mail-service-url:http://localhost:8087}",
    configuration = MailFeignConfig.class)
public interface MailInternalClient {

  @PostMapping("/mail/internal/send")
  MailSendResult send(@RequestBody MailSendRequest request);
}
