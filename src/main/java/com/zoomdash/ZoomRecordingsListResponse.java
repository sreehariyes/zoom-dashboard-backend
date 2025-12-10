package com.zoomdash;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class ZoomRecordingsListResponse {
    
    @JsonProperty("from")
    private String from;
    
    @JsonProperty("to")
    private String to;
    
    @JsonProperty("page_count")
    private Integer pageCount;
    
    @JsonProperty("page_size")
    private Integer pageSize;
    
    @JsonProperty("total_records")
    private Integer totalRecords;
    
    @JsonProperty("next_page_token")
    private String nextPageToken;
    
    @JsonProperty("meetings")
    private List<Map<String, Object>> meetings; // This will contain your recordings
    
    // getters and setters
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
    
    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }
    
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    
    public Integer getTotalRecords() { return totalRecords; }
    public void setTotalRecords(Integer totalRecords) { this.totalRecords = totalRecords; }
    
    public String getNextPageToken() { return nextPageToken; }
    public void setNextPageToken(String nextPageToken) { this.nextPageToken = nextPageToken; }
    
    public List<Map<String, Object>> getMeetings() { return meetings; }
    public void setMeetings(List<Map<String, Object>> meetings) { this.meetings = meetings; }
}