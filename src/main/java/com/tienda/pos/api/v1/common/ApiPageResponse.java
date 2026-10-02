package com.tienda.pos.api.v1.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record ApiPageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <S, T> ApiPageResponse<T> from(Page<S> source, Function<S, T> mapper) {
        return new ApiPageResponse<>(source.getContent().stream().map(mapper).toList(), source.getNumber(),
                source.getSize(), source.getTotalElements(), source.getTotalPages());
    }
}
