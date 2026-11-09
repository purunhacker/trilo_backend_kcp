package app.trilo.gallery.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GalleryCursor {
  private Instant takenAt;
  private String mediaId;
}
