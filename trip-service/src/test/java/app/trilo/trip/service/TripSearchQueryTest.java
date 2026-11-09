package app.trilo.trip.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TripSearchQueryTest {

  @Test
  void toPrefixTsQuery_appendsPrefixOperatorPerTerm() {
    assertThat(TripSearchService.toPrefixTsQuery("속초")).isEqualTo("속초:*");
    assertThat(TripSearchService.toPrefixTsQuery("속초 여행")).isEqualTo("속초:* & 여행:*");
    assertThat(TripSearchService.toPrefixTsQuery("Sok")).isEqualTo("Sok:*");
    assertThat(TripSearchService.toPrefixTsQuery("")).isEmpty();
  }

  @Test
  void sanitizeQuery_keepsOnlyLettersAndDigits() {
    assertThat(TripSearchService.sanitizeQuery("속초 & 여행")).isEqualTo("속초 여행");
    assertThat(TripSearchService.sanitizeQuery("a|b!c(d)e:f*g")).isEqualTo("a b c d e f g");
    assertThat(TripSearchService.sanitizeQuery("'quoted' <-> \\back")).isEqualTo("quoted back");
    assertThat(TripSearchService.sanitizeQuery("속초,여행")).isEqualTo("속초 여행");
    assertThat(TripSearchService.sanitizeQuery("ソクチョ 束草 2026")).isEqualTo("ソクチョ 束草 2026");
    assertThat(TripSearchService.sanitizeQuery(null)).isEmpty();
    assertThat(TripSearchService.sanitizeQuery("   ")).isEmpty();
  }

  @Test
  void sanitizeThenPrefix_producesValidTsQueryInput() {
    String result = TripSearchService.toPrefixTsQuery(TripSearchService.sanitizeQuery("강원 (여행)"));
    assertThat(result).isEqualTo("강원:* & 여행:*");
  }
}
