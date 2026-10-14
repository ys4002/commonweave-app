package com.commonweave.listing;

import com.commonweave.auth.CurrentUser;
import com.commonweave.common.PageResponse;
import com.commonweave.listing.ListingDtos.ListingDetail;
import com.commonweave.listing.ListingDtos.ListingRequest;
import com.commonweave.listing.ListingDtos.ListingSummary;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/listings")
public class ListingController {
    private final ListingService service;

    public ListingController(ListingService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ListingSummary> search(
            @RequestParam(required = false) Long circleId,
            @RequestParam(required = false) ListingType type,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) ListingKind kind,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate availableOn,
            @RequestParam(required = false) ListingStatus status,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.search(CurrentUser.id(), new ListingService.SearchParams(circleId, type, category, kind, q,
                availableOn, status, mine, sort, page, size));
    }

    @GetMapping("/{id}")
    public ListingDetail get(@PathVariable Long id) {
        return service.get(CurrentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ListingDetail create(@Valid @RequestBody ListingRequest req) {
        return service.create(CurrentUser.id(), req);
    }

    @PutMapping("/{id}")
    public ListingDetail update(@PathVariable Long id, @Valid @RequestBody ListingRequest req) {
        return service.update(CurrentUser.id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@PathVariable Long id) {
        service.close(CurrentUser.id(), id);
    }
}
