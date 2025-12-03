package com.zoomdash;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ZoomRecordingsResponse {
    @JsonProperty("recording_files")
    private List<RecordingFile> recordingFiles;
    
    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("uuid")
    private String uuid;
    
    @JsonProperty("account_id")
    private String accountId;
    
    @JsonProperty("host_id")
    private String hostId;
    
    @JsonProperty("topic")
    private String topic;
    
    @JsonProperty("type")
    private Integer type;
    
    @JsonProperty("start_time")
    private String startTime;
    
    @JsonProperty("timezone")
    private String timezone;
    
    @JsonProperty("host_email")
    private String hostEmail;
    
    @JsonProperty("duration")
    private Integer duration;
    
    @JsonProperty("total_size")
    private Long totalSize;
    
    @JsonProperty("recording_count")
    private Integer recordingCount;
    
    @JsonProperty("share_url")
    private String shareUrl;
    
    // getters and setters
    public List<RecordingFile> getRecordingFiles() { 
        return recordingFiles; 
    }
    
    public void setRecordingFiles(List<RecordingFile> recordingFiles) { 
        this.recordingFiles = recordingFiles; 
    }
    
    public Long getId() { 
        return id; 
    }
    
    public void setId(Long id) { 
        this.id = id; 
    }
    
    public String getUuid() { 
        return uuid; 
    }
    
    public void setUuid(String uuid) { 
        this.uuid = uuid; 
    }
    
    public String getAccountId() { 
        return accountId; 
    }
    
    public void setAccountId(String accountId) { 
        this.accountId = accountId; 
    }
    
    public String getHostId() { 
        return hostId; 
    }
    
    public void setHostId(String hostId) { 
        this.hostId = hostId; 
    }
    
    public String getTopic() { 
        return topic; 
    }
    
    public void setTopic(String topic) { 
        this.topic = topic; 
    }
    
    public Integer getType() { 
        return type; 
    }
    
    public void setType(Integer type) { 
        this.type = type; 
    }
    
    public String getStartTime() { 
        return startTime; 
    }
    
    public void setStartTime(String startTime) { 
        this.startTime = startTime; 
    }
    
    public String getTimezone() { 
        return timezone; 
    }
    
    public void setTimezone(String timezone) { 
        this.timezone = timezone; 
    }
    
    public String getHostEmail() { 
        return hostEmail; 
    }
    
    public void setHostEmail(String hostEmail) { 
        this.hostEmail = hostEmail; 
    }
    
    public Integer getDuration() { 
        return duration; 
    }
    
    public void setDuration(Integer duration) { 
        this.duration = duration; 
    }
    
    public Long getTotalSize() { 
        return totalSize; 
    }
    
    public void setTotalSize(Long totalSize) { 
        this.totalSize = totalSize; 
    }
    
    public Integer getRecordingCount() { 
        return recordingCount; 
    }
    
    public void setRecordingCount(Integer recordingCount) { 
        this.recordingCount = recordingCount; 
    }
    
    public String getShareUrl() { 
        return shareUrl; 
    }
    
    public void setShareUrl(String shareUrl) { 
        this.shareUrl = shareUrl; 
    }
}