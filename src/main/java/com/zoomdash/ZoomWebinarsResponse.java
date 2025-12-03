package com.zoomdash;

import java.util.List;

public class ZoomWebinarsResponse {
    private List<ZoomWebinar> webinars;
    private int pageSize;
    private int totalRecords;
    private String nextPageToken;

    public ZoomWebinarsResponse() {}

    public List<ZoomWebinar> getWebinars() { return webinars; }
    public void setWebinars(List<ZoomWebinar> webinars) { this.webinars = webinars; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
    public String getNextPageToken() { return nextPageToken; }
    public void setNextPageToken(String nextPageToken) { this.nextPageToken = nextPageToken; }
}