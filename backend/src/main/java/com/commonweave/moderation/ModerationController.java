package com.commonweave.moderation;

import com.commonweave.auth.CurrentUser;
import com.commonweave.moderation.ModerationDtos.CreateReportRequest;
import com.commonweave.moderation.ModerationDtos.LogEntry;
import com.commonweave.moderation.ModerationDtos.RemoveListingRequest;
import com.commonweave.moderation.ModerationDtos.ReportView;
import com.commonweave.moderation.ModerationDtos.ResolveRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ModerationController {
    private final ModerationService service;

    public ModerationController(ModerationService service) {
        this.service = service;
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public void report(@Valid @RequestBody CreateReportRequest req) {
        service.createReport(CurrentUser.id(), req);
    }

    @GetMapping("/circles/{id}/reports")
    public List<ReportView> reports(@PathVariable Long id, @RequestParam(required = false) ReportStatus status) {
        return service.list(CurrentUser.id(), id, status);
    }

    @PostMapping("/circles/{id}/reports/{reportId}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable Long id, @PathVariable Long reportId, @Valid @RequestBody ResolveRequest req) {
        service.resolve(CurrentUser.id(), id, reportId, req);
    }

    @PostMapping("/circles/{id}/listings/{listingId}/remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id, @PathVariable Long listingId,
                       @Valid @RequestBody RemoveListingRequest req) {
        service.removeListingAsModerator(CurrentUser.id(), id, listingId, req.note());
    }

    @GetMapping("/circles/{id}/moderation-log")
    public List<LogEntry> log(@PathVariable Long id) {
        return service.log(CurrentUser.id(), id);
    }
}
