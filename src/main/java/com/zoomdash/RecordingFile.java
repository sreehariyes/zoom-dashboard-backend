package com.zoomdash;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RecordingFile {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("meeting_id")
    private String meetingId;
    
    @JsonProperty("recording_start")
    private String recordingStart;
    
    @JsonProperty("recording_end")
    private String recordingEnd;
    
    @JsonProperty("file_type")
    private String fileType;
    
    @JsonProperty("file_extension")
    private String fileExtension;
    
    @JsonProperty("file_size")
    private Long fileSize;
    
    @JsonProperty("download_url")
    private String downloadUrl;
    
    @JsonProperty("play_url")
    private String playUrl;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("recording_type")
    private String recordingType;
    
    // getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getMeetingId() { return meetingId; }
    public void setMeetingId(String meetingId) { this.meetingId = meetingId; }
    
    public String getRecordingStart() { return recordingStart; }
    public void setRecordingStart(String recordingStart) { this.recordingStart = recordingStart; }
    
    public String getRecordingEnd() { return recordingEnd; }
    public void setRecordingEnd(String recordingEnd) { this.recordingEnd = recordingEnd; }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public String getFileExtension() { return fileExtension; }
    public void setFileExtension(String fileExtension) { this.fileExtension = fileExtension; }
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    
    public String getPlayUrl() { return playUrl; }
    public void setPlayUrl(String playUrl) { this.playUrl = playUrl; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getRecordingType() { return recordingType; }
    public void setRecordingType(String recordingType) { this.recordingType = recordingType; }
}