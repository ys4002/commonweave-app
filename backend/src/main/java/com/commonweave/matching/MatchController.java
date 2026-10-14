package com.commonweave.matching;

import com.commonweave.auth.CurrentUser;
import com.commonweave.matching.MatchDtos.MatchView;
import com.commonweave.matching.MatchDtos.SuggestedMatch;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MatchController {
    private final MatchingService service;

    public MatchController(MatchingService service) {
        this.service = service;
    }

    @GetMapping("/listings/{id}/matches")
    public List<MatchView> forListing(@PathVariable Long id) {
        return service.forListing(CurrentUser.id(), id);
    }

    @GetMapping("/matches/suggested")
    public List<SuggestedMatch> suggested() {
        return service.suggested(CurrentUser.id(), MatchingService.SUGGESTION_LIMIT);
    }
}
