package com.commonweave.admin;

import com.commonweave.admin.AdminDtos.AdminHandoff;
import com.commonweave.admin.AdminDtos.AdminReport;
import com.commonweave.admin.AdminDtos.AdminUser;
import com.commonweave.admin.AdminDtos.AdminUserDetail;
import com.commonweave.admin.AdminDtos.AuditEntry;
import com.commonweave.admin.AdminDtos.Overview;
import com.commonweave.admin.AdminDtos.RemoveListingRequest;
import com.commonweave.admin.AdminDtos.ResetPasswordRequest;
import com.commonweave.admin.AdminDtos.UpdateUserRequest;
import com.commonweave.auth.CurrentUser;
import com.commonweave.circle.CircleDtos.CircleSummary;
import com.commonweave.common.PageResponse;
import com.commonweave.handoff.HandoffStatus;
import com.commonweave.listing.ListingDtos.ListingSummary;
import com.commonweave.listing.ListingService;
import com.commonweave.listing.ListingStatus;
import com.commonweave.moderation.ReportStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Authorised by the filter chain (/api/v1/admin/** requires ROLE_ADMIN). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService admin;
    private final ListingService listings;

    public AdminController(AdminService admin, ListingService listings) {
        this.admin = admin;
        this.listings = listings;
    }

    @GetMapping("/overview")
    public Overview overview() {
        return admin.overview();
    }

    @GetMapping("/users")
    public PageResponse<AdminUser> users(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) Boolean enabled,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        return admin.listUsers(q, enabled, page, size);
    }

    @GetMapping("/users/{id}")
    public AdminUserDetail user(@PathVariable Long id) {
        return admin.userDetail(id);
    }

    @PatchMapping("/users/{id}")
    public AdminUser update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req) {
        return admin.updateUser(CurrentUser.id(), id, req);
    }

    @PostMapping("/users/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req) {
        admin.resetPassword(CurrentUser.id(), id, req.newPassword());
    }

    @GetMapping("/circles")
    public PageResponse<CircleSummary> circles(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return admin.listCircles(CurrentUser.id(), q, page, size);
    }

    @GetMapping("/listings")
    public PageResponse<ListingSummary> listings(@RequestParam(required = false) ListingStatus status,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return listings.adminSearch(status, q, page, size);
    }

    @GetMapping("/handoffs")
    public PageResponse<AdminHandoff> handoffs(@RequestParam(required = false) HandoffStatus status,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return admin.listHandoffs(status, page, size);
    }

    @GetMapping("/reports")
    public PageResponse<AdminReport> reports(@RequestParam(required = false) ReportStatus status,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        return admin.listReports(status, page, size);
    }

    @PostMapping("/listings/{id}/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id, @Valid @RequestBody RemoveListingRequest req) {
        admin.removeListing(CurrentUser.id(), id, req.note());
    }

    @GetMapping("/audit-log")
    public PageResponse<AuditEntry> audit(@RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return admin.auditLog(page, size);
    }
}
