package com.commonweave.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static final int DEFAULT_SIZE = 12;
    public static final int MAX_SIZE = 50;

    /** Maps the whole page content in one call so mappers can batch their lookups. */
    public static <S, T> PageResponse<T> of(Page<S> page, Function<List<S>, List<T>> mapper) {
        return new PageResponse<>(mapper.apply(page.getContent()), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public static Pageable pageable(Integer page, Integer size, Sort sort) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, size));
        return PageRequest.of(p, s, sort);
    }
}
