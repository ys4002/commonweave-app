package com.commonweave.impact;

import com.commonweave.auth.CurrentUser;
import com.commonweave.impact.ImpactDtos.ImpactResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ImpactController {
    private final ImpactService service;

    public ImpactController(ImpactService service) {
        this.service = service;
    }

    @GetMapping("/circles/{id}/impact")
    public ImpactResponse circle(@PathVariable Long id, @RequestParam(required = false) Integer days) {
        return service.forCircle(CurrentUser.id(), id, days);
    }

    @GetMapping("/impact/me")
    public ImpactResponse me(@RequestParam(required = false) Integer days) {
        return service.forUser(CurrentUser.id(), days);
    }
}
