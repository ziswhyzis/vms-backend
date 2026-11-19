package com.starkgroup.vms.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Spring-Page-shaped response contract required by the VMS API specification:
 * content, number, size, totalElements, totalPages, last.
 */
public record PageResponse<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
