package app.trilo.trip.service;

import app.trilo.trip.repository.TripRepository;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TripIdGenerator {

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");
  private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
  private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

  private final TripRepository tripRepository;

  public String generate(String title, LocalDate tripDate, UUID ownerId) {
    String slugTitle = slugify(title);
    String dateStr = tripDate.format(DATE_FORMATTER);

    List<String> existingIds = tripRepository.findIdsByOwnerIdAndTripDate(ownerId, tripDate);

    int sequence = 1;
    String baseId = slugTitle + "-" + dateStr;
    String candidateId = baseId + "-" + String.format("%02d", sequence);

    while (existingIds.contains(candidateId)) {
      sequence++;
      candidateId = baseId + "-" + String.format("%02d", sequence);
    }

    return candidateId;
  }

  private String slugify(String input) {
    if (input == null || input.isBlank()) {
      return "trip";
    }

    String noWhitespace = WHITESPACE.matcher(input.trim()).replaceAll("-");
    String normalized = Normalizer.normalize(noWhitespace, Normalizer.Form.NFD);
    String slug = NON_LATIN.matcher(normalized).replaceAll("");
    slug = slug.toLowerCase(Locale.ENGLISH);
    slug = slug.replaceAll("-+", "-");
    slug = slug.replaceAll("^-|-$", "");

    if (slug.isEmpty()) {
      return "trip";
    }

    if (slug.length() > 50) {
      slug = slug.substring(0, 50);
      if (slug.endsWith("-")) {
        slug = slug.substring(0, slug.length() - 1);
      }
    }

    return slug;
  }
}
