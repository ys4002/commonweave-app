package com.commonweave.handoff;

import com.commonweave.auth.CurrentUser;
import com.commonweave.common.PageResponse;
import com.commonweave.handoff.HandoffDtos.HandoffDetail;
import com.commonweave.handoff.HandoffDtos.HandoffSummary;
import com.commonweave.handoff.HandoffDtos.ProposeRequest;
import com.commonweave.handoff.HandoffDtos.TransitionRequest;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/handoffs")
public class HandoffController {
    private final HandoffService service;

    public HandoffController(HandoffService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HandoffDetail propose(@Valid @RequestBody ProposeRequest req) {
        return service.propose(CurrentUser.id(), req);
    }

    @GetMapping
    public PageResponse<HandoffSummary> list(@RequestParam(required = false) HandoffStatus status,
                                             @RequestParam(defaultValue = "false") boolean active,
                                             @RequestParam(required = false) HandoffRole role,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        return service.list(CurrentUser.id(), status, active, role, page, size);
    }

    @GetMapping("/{id}")
    public HandoffDetail get(@PathVariable Long id) {
        return service.get(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/transitions")
    public HandoffDetail transition(@PathVariable Long id, @Valid @RequestBody TransitionRequest req) {
        return service.transition(CurrentUser.id(), id, req);
    }
}
