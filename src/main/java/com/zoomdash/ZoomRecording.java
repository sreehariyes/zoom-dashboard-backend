package com.zoomdash;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ZoomRecording {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("meeting_id")
    private String meetingId;
    
    @JsonProperty("start_time")
    private String startTime;
    
    @JsonProperty("end_time")
    private String endTime;
    
    @JsonProperty("recording_files")
    private List<RecordingFile> recordingFiles;
    
    @JsonProperty("audio_transcript")
    private String audioTranscript;
    
    // getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getMeetingId() { return meetingId; }
    public void setMeetingId(String meetingId) { this.meetingId = meetingId; }
    
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    
    public List<RecordingFile> getRecordingFiles() { return recordingFiles; }
    public void setRecordingFiles(List<RecordingFile> recordingFiles) { this.recordingFiles = recordingFiles; }
    
    public String getAudioTranscript() { return audioTranscript; }
    public void setAudioTranscript(String audioTranscript) { this.audioTranscript = audioTranscript; }
}