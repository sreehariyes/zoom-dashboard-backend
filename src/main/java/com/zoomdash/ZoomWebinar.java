package com.zoomdash;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class ZoomWebinar {
    private String uuid;
    private Long id;
    private String topic;
    private int duration;
    
    @JsonProperty("start_time")
    private LocalDateTime startTime;
    
    @JsonProperty("join_url")
    private String joinUrl;
    
    @JsonProperty("host_id")
    private String hostId;
    
    private String timezone;
    
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    
    private int type;
    
    @JsonProperty("is_simulive")
    private boolean isSimulive;

    public ZoomWebinar() {}

    // Getters and Setters
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public String getJoinUrl() { return joinUrl; }
    public void setJoinUrl(String joinUrl) { this.joinUrl = joinUrl; }
    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    public boolean isSimulive() { return isSimulive; }
    public void setSimulive(boolean isSimulive) { this.isSimulive = isSimulive; }
}