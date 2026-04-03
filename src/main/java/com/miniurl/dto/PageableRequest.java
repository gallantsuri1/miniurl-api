package com.miniurl.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Pagination request parameters")
public class PageableRequest {

    @Schema(description = "Page number (0-indexed)", example = "0", defaultValue = "0")
    private int page = 0;

    @Schema(description = "Number of items per page", example = "10", defaultValue = "10")
    private int size = 10;

    @Schema(description = "Sort by field (id, originalUrl, shortCode, accessCount, createdAt)", example = "createdAt", defaultValue = "createdAt")
    private String sortBy = "createdAt";

    @Schema(description = "Sort direction (asc or desc)", example = "desc", defaultValue = "desc")
    private String sortDirection = "desc";

    public PageableRequest() {}

    public PageableRequest(int page, int size, String sortBy, String sortDirection) {
        this.page = page;
        this.size = size;
        this.sortBy = sortBy;
        this.sortDirection = sortDirection;
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }

    public String getSortDirection() { return sortDirection; }
    public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }

    public boolean isAscending() {
        return "asc".equalsIgnoreCase(sortDirection);
    }
}
