package com.miniurl.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Standard paginated response wrapper.
 * @param <T> The type of the list content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> implements Serializable {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;

    public static <T> PagedResponse<T> of(List<T> content, int pageNumber, int pageSize, long totalElements) {
        return PagedResponse.<T>builder()
                .content(content)
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages((int) Math.ceil((double) totalElements / pageSize))
                .last(pageNumber >= (int) Math.ceil((double) totalElements / pageSize) - 1)
                .build();
    }
}