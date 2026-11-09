package app.trilo.admin.controller;

import app.trilo.admin.dto.DashboardStatsDto;
import app.trilo.admin.service.DashboardStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardStatsService dashboardStatsService;

  @GetMapping("/dashboard")
  public ResponseEntity<DashboardStatsDto> getDashboardStats() {
    return ResponseEntity.ok(dashboardStatsService.getDashboardStats());
  }
}
