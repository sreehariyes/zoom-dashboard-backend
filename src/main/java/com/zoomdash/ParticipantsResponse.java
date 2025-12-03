package com.zoomdash;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ParticipantsResponse {
    @JsonProperty("participants")
    private List<Participant> participants;
    
    @JsonProperty("page_count")
    private int pageCount;
    
    @JsonProperty("page_size")
    private int pageSize;
    
    @JsonProperty("total_records")
    private int totalRecords;
    
    @JsonProperty("next_page_token")
    private String nextPageToken;

    // Getters and Setters
    public List<Participant> getParticipants() { return participants; }
    public void setParticipants(List<Participant> participants) { this.participants = participants; }
    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
    public String getNextPageToken() { return nextPageToken; }
    public void setNextPageToken(String nextPageToken) { this.nextPageToken = nextPageToken; }
}