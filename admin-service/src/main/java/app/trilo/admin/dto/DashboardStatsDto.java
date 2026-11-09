package app.trilo.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DashboardStatsDto {
  private long totalMembers;
  private double membersChange; // percentage change from last week
  private long totalTrips;
  private double tripsChange;
  private long totalMedia;
  private double mediaChange;
  private long activeToday;
}
