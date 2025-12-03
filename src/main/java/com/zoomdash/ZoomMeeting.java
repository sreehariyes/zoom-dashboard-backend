package com.zoomdash;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class ZoomMeeting {
    private String uuid;
    private String id;
    private String topic;
    private int duration;
    
    @JsonProperty("start_time")
    private LocalDateTime startTime;
    
    @JsonProperty("join_url")
    private String joinUrl;

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public String getJoinUrl() { return joinUrl; }
    public void setJoinUrl(String joinUrl) { this.joinUrl = joinUrl; }
}