package app.trilo.admin.controller;

import app.trilo.admin.config.CurrentAdmin;
import app.trilo.admin.domain.AdminUser;
import app.trilo.admin.dto.ActionRequest;
import app.trilo.admin.dto.MemberDto;
import app.trilo.admin.dto.UpdateMemberRequest;
import app.trilo.admin.service.MemberManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/members")
@RequiredArgsConstructor
public class MemberManagementController {

  private final MemberManagementService memberService;

  @GetMapping
  public ResponseEntity<Page<MemberDto>> getAllMembers(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean deleted,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(memberService.getAllMembers(search, deleted, pageable));
  }

  @GetMapping("/{memberId}")
  public ResponseEntity<MemberDto> getMember(@PathVariable UUID memberId) {
    return ResponseEntity.ok(memberService.getMember(memberId));
  }

  @PatchMapping("/{memberId}")
  public ResponseEntity<MemberDto> updateMember(
      @CurrentAdmin AdminUser admin,
      @PathVariable UUID memberId,
      @Valid @RequestBody UpdateMemberRequest request,
      HttpServletRequest httpRequest) {
    return ResponseEntity.ok(memberService.updateMember(admin, memberId, request, httpRequest));
  }

  @PostMapping("/{memberId}/suspend")
  public ResponseEntity<Void> suspendMember(
      @CurrentAdmin AdminUser admin,
      @PathVariable UUID memberId,
      @Valid @RequestBody ActionRequest request,
      HttpServletRequest httpRequest) {
    memberService.suspendMember(admin, memberId, request, httpRequest);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{memberId}/restore")
  public ResponseEntity<Void> restoreMember(
      @CurrentAdmin AdminUser admin,
      @PathVariable UUID memberId,
      @Valid @RequestBody ActionRequest request,
      HttpServletRequest httpRequest) {
    memberService.restoreMember(admin, memberId, request, httpRequest);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{memberId}")
  public ResponseEntity<Void> deleteMemberPermanently(
      @CurrentAdmin AdminUser admin,
      @PathVariable UUID memberId,
      @Valid @RequestBody ActionRequest request,
      HttpServletRequest httpRequest) {
    memberService.deleteMemberPermanently(admin, memberId, request, httpRequest);
    return ResponseEntity.noContent().build();
  }
}
