package app.trilo.mail.api;

import app.trilo.common.dto.MailSendRequest;
import app.trilo.common.dto.MailSendResult;
import app.trilo.mail.service.MailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal send endpoint. Reachable only by authorised internal containers: blocked at the gateway
 * from the outside and guarded by the shared-secret {@code X-Internal-Token} header (see {@code
 * InternalAuthFilter}).
 */
@RestController
@RequestMapping("/mail/internal")
@RequiredArgsConstructor
public class MailInternalController {

  private final MailService mailService;

  @PostMapping("/send")
  public ResponseEntity<MailSendResult> send(@Valid @RequestBody MailSendRequest request) {
    return ResponseEntity.ok(mailService.send(request));
  }
}
