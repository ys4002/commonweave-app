package com.commonweave.circle;

import com.commonweave.auth.CurrentUser;
import com.commonweave.circle.CircleDtos.ActivityItem;
import com.commonweave.circle.CircleDtos.CircleDetail;
import com.commonweave.circle.CircleDtos.CircleSummary;
import com.commonweave.circle.CircleDtos.CreateCircleRequest;
import com.commonweave.circle.CircleDtos.MemberResponse;
import com.commonweave.circle.CircleDtos.UpdateCircleRequest;
import com.commonweave.circle.CircleDtos.UpdateMemberRequest;
import com.commonweave.common.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
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

@RestController
@RequestMapping("/api/v1/circles")
public class CircleController {
    private final CircleService service;

    public CircleController(CircleService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<CircleSummary> list(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) String city,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        return service.list(CurrentUser.id(), q, city, page, size);
    }

    @GetMapping("/{id}")
    public CircleDetail get(@PathVariable Long id) {
        return service.get(CurrentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CircleDetail create(@Valid @RequestBody CreateCircleRequest req) {
        return service.create(CurrentUser.id(), req);
    }

    @PostMapping("/{id}/join")
    public CircleDetail join(@PathVariable Long id) {
        return service.join(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable Long id) {
        service.leave(CurrentUser.id(), id);
    }

    @PatchMapping("/{id}")
    public CircleDetail update(@PathVariable Long id, @Valid @RequestBody UpdateCircleRequest req) {
        return service.update(CurrentUser.id(), id, req);
    }

    @GetMapping("/{id}/members")
    public List<MemberResponse> members(@PathVariable Long id) {
        return service.members(CurrentUser.id(), id);
    }

    @PatchMapping("/{id}/members/{userId}")
    public MemberResponse updateMember(@PathVariable Long id, @PathVariable Long userId,
                                       @RequestBody UpdateMemberRequest req) {
        return service.updateMember(CurrentUser.id(), id, userId, req);
    }

    @GetMapping("/{id}/activity")
    public List<ActivityItem> activity(@PathVariable Long id, @RequestParam(defaultValue = "10") int size) {
        return service.activity(CurrentUser.id(), id, size);
    }
}
