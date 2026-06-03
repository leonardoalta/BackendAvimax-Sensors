package com.avimax.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SensorReadingPageResponse {
    public List<SensorReadingResponse> data;
    public PageMeta meta;

    public SensorReadingPageResponse(List<SensorReadingResponse> data, int page, int size, long total) {
        this.data = data;
        this.meta = new PageMeta(page, size, total);
    }

    // Inner class for pagination metadata
    public static class PageMeta {
        public int page;
        public int size;
        public long total;

        public PageMeta(int page, int size, long total) {
            this.page = page;
            this.size = size;
            this.total = total;
        }
    }
}
