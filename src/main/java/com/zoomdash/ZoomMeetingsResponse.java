package com.zoomdash;

import java.util.List;

public class ZoomMeetingsResponse {
    private List<ZoomMeeting> meetings;
    private int pageCount;
    private int pageNumber;
    private int pageSize;
    private int totalRecords;

    public List<ZoomMeeting> getMeetings() { return meetings; }
    public void setMeetings(List<ZoomMeeting> meetings) { this.meetings = meetings; }
    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }
    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
}