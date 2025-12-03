package com.zoomdash;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class Participant {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("user_email")
    private String userEmail;
    
    @JsonProperty("join_time")
    private String joinTime;
    
    @JsonProperty("leave_time")
    private String leaveTime;
    
    @JsonProperty("duration")
    private int duration;
    
    @JsonProperty("registrant_id")
    private String registrantId;
    
    @JsonProperty("failover")
    private boolean failover;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("groupId")
    private String groupId;
    
    @JsonProperty("internal_user")
    private boolean internalUser;
    
    @JsonProperty("attentiveness_score")
    private String attentivenessScore; // ADD THIS FIELD
    
    @JsonProperty("customer_key")
    private String customerKey; // ADD THIS FIELD
    
    @JsonProperty("participant_user_id")
    private String participantUserId; // ADD THIS FIELD

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    
    public String getJoinTime() { return joinTime; }
    public void setJoinTime(String joinTime) { this.joinTime = joinTime; }
    
    public String getLeaveTime() { return leaveTime; }
    public void setLeaveTime(String leaveTime) { this.leaveTime = leaveTime; }
    
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    
    public String getRegistrantId() { return registrantId; }
    public void setRegistrantId(String registrantId) { this.registrantId = registrantId; }
    
    public boolean isFailover() { return failover; }
    public void setFailover(boolean failover) { this.failover = failover; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    
    public boolean isInternalUser() { return internalUser; }
    public void setInternalUser(boolean internalUser) { this.internalUser = internalUser; }
    
    public String getAttentivenessScore() { return attentivenessScore; } // ADD THIS GETTER
    public void setAttentivenessScore(String attentivenessScore) { this.attentivenessScore = attentivenessScore; } // ADD THIS SETTER
    
    public String getCustomerKey() { return customerKey; } // ADD THIS GETTER
    public void setCustomerKey(String customerKey) { this.customerKey = customerKey; } // ADD THIS SETTER
    
    public String getParticipantUserId() { return participantUserId; } // ADD THIS GETTER
    public void setParticipantUserId(String participantUserId) { this.participantUserId = participantUserId; } // ADD THIS SETTER
}