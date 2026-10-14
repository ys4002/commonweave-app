package com.commonweave.common;

import com.commonweave.listing.Category;
import com.commonweave.listing.ListingKind;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {
    public record CodeLabel(String code, String label) {
    }

    @GetMapping
    public Map<String, Object> meta() {
        List<CodeLabel> categories = Arrays.stream(Category.values())
                .map(c -> new CodeLabel(c.name(), c.label())).toList();
        List<CodeLabel> kinds = Arrays.stream(ListingKind.values())
                .map(k -> new CodeLabel(k.name(), k.label())).toList();
        return Map.of("currency", "INR", "locale", "en-IN", "timezone", "Asia/Kolkata",
                "categories", categories, "listingKinds", kinds);
    }
}
