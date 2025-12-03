package com.zoomdash;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ZoomService {
    
    private final WebClient webClient;

    @Value("${zoom.account-id}")
    private String accountId;

    @Value("${zoom.client-id}")
    private String clientId;

    @Value("${zoom.client-secret}")
    private String clientSecret;

    // DateTime formatter for parsing Zoom timestamps
    private static final DateTimeFormatter ZOOM_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public ZoomService(WebClient webClient) {
        this.webClient = webClient;
    }

    // Get Access Token from Zoom API
    public Mono<ZoomAuthResponse> getAccessToken() {
        String credentials = clientId + ":" + clientSecret;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

        return webClient.post()
                .uri("https://zoom.us/oauth/token?grant_type=account_credentials&account_id=" + accountId)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials)
                .retrieve()
                .bodyToMono(ZoomAuthResponse.class);
    }

    // Get User's Meetings
    public Mono<ZoomMeetingsResponse> getMeetings(String accessToken) {
        return webClient.get()
                .uri("https://api.zoom.us/v2/users/me/meetings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(ZoomMeetingsResponse.class);
    }

    // Get Meeting Participants
    public Mono<ParticipantsResponse> getMeetingParticipants(String accessToken, String meetingId) {
        return webClient.get()
                .uri("https://api.zoom.us/v2/report/meetings/" + meetingId + "/participants")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(ParticipantsResponse.class);
    }

    // Calculate REAL Engagement Metrics with ACTUAL Join/Leave Times AND Individual User Tracking
    public Map<String, Object> calculateEngagementMetrics(ParticipantsResponse participantsResponse, int webinarDuration, int intervalMinutes) {
        Map<String, Object> engagementData = new HashMap<>();
        
        if (participantsResponse == null || participantsResponse.getParticipants() == null) {
            engagementData.put("error", "No participant data available");
            return engagementData;
        }

        List<Participant> participants = participantsResponse.getParticipants();
        int totalParticipants = participants.size();

        // DEBUG: Show sample participant data
        System.out.println("=== DEBUG: Processing " + totalParticipants + " participants ===");
        System.out.println("📊 Using interval: " + intervalMinutes + " minutes");
        if (!participants.isEmpty()) {
            for (int i = 0; i < Math.min(3, participants.size()); i++) {
                Participant participant = participants.get(i);
                System.out.println("Sample Participant " + (i+1) + ": " + participant.getName() + " | Join: " + participant.getJoinTime() + " | Leave: " + participant.getLeaveTime());
            }
        }

        // Calculate basic duration metrics
        int totalDurationSeconds = 0;
        int maxDurationSeconds = 0;
        int minDurationSeconds = Integer.MAX_VALUE;

        for (Participant participant : participants) {
            int duration = participant.getDuration(); // This is in SECONDS
            totalDurationSeconds += duration;
            
            if (duration > maxDurationSeconds) {
                maxDurationSeconds = duration;
            }
            if (duration < minDurationSeconds) {
                minDurationSeconds = duration;
            }
        }

        // Convert seconds to minutes
        double averageDurationMinutes = totalParticipants > 0 ? (double) totalDurationSeconds / totalParticipants / 60.0 : 0;
        double maxDurationMinutes = maxDurationSeconds / 60.0;
        double minDurationMinutes = minDurationSeconds == Integer.MAX_VALUE ? 0 : minDurationSeconds / 60.0;
        double totalMeetingMinutes = totalDurationSeconds / 60.0;

        // USE DYNAMIC TIME BINS based on interval parameter
        List<String> timeLabels = generateDynamicTimeBins(webinarDuration, intervalMinutes);
        
        // Calculate REAL-TIME engagement from ACTUAL join/leave times WITH INDIVIDUAL USER TRACKING
        Map<String, Object> realTimeAnalysis = calculateRealTimeEngagementWithUserTracking(participants, webinarDuration, intervalMinutes);
        
        List<Integer> activeParticipants = (List<Integer>) realTimeAnalysis.get("active_participants");
        List<Integer> engagementRates = (List<Integer>) realTimeAnalysis.get("engagement_rate");
        List<Integer> usersJoined = (List<Integer>) realTimeAnalysis.get("users_joined");
        List<Integer> usersLeft = (List<Integer>) realTimeAnalysis.get("users_left");
        List<Integer> peakActiveUsers = (List<Integer>) realTimeAnalysis.get("peak_active_users");
        List<Map<String, Object>> userTimelines = (List<Map<String, Object>>) realTimeAnalysis.get("user_timelines");
        List<Map<String, Object>> participantDetails = (List<Map<String, Object>>) realTimeAnalysis.get("participant_details");
        
        Map<String, Object> engagementGraph = new HashMap<>();
        engagementGraph.put("labels", timeLabels);
        engagementGraph.put("active_participants", activeParticipants);
        engagementGraph.put("engagement_rate", engagementRates);
        engagementGraph.put("users_joined", usersJoined);
        engagementGraph.put("users_left", usersLeft);
        engagementGraph.put("peak_active_users", peakActiveUsers);
        engagementGraph.put("user_timelines", userTimelines); // Individual user presence per segment
        
        engagementData.put("total_participants", totalParticipants);
        engagementData.put("average_participation_minutes", Math.round(averageDurationMinutes * 100.0) / 100.0);
        engagementData.put("max_participation_minutes", Math.round(maxDurationMinutes * 100.0) / 100.0);
        engagementData.put("min_participation_minutes", Math.round(minDurationMinutes * 100.0) / 100.0);
        engagementData.put("total_meeting_minutes", Math.round(totalMeetingMinutes * 100.0) / 100.0);
        engagementData.put("peak_concurrent_users", realTimeAnalysis.get("peak_concurrent"));
        engagementData.put("final_active_users", realTimeAnalysis.get("final_active_users"));
        engagementData.put("total_joined", realTimeAnalysis.get("total_joined"));
        engagementData.put("total_left", realTimeAnalysis.get("total_left"));
        engagementData.put("engagement_over_time", engagementGraph);
        engagementData.put("participant_details", participantDetails); // Detailed user information

        return engagementData;
    }

    // Generate DYNAMIC time bins based on interval parameter - FIXED to return proper time format
    private List<String> generateDynamicTimeBins(int webinarDurationMinutes, int intervalMinutes) {
        List<String> timeLabels = new ArrayList<>();
        int binSize = intervalMinutes; // Use dynamic interval
        
        for (int i = 0; i < webinarDurationMinutes; i += binSize) {
            int start = i;
            int end = Math.min(i + binSize, webinarDurationMinutes);
            
            // FIXED: Always generate time format like "00:05", "00:10", etc.
            int startHour = start / 60;
            int startMinute = start % 60;
            int endHour = end / 60;
            int endMinute = end % 60;
            
            // Format as "HH:MM" for start time
            String timeDisplay = String.format("%02d:%02d", startHour, startMinute);
            timeLabels.add(timeDisplay);
        }
        
        // Ensure the last bin goes exactly to the webinar duration
        if (!timeLabels.isEmpty()) {
            String lastLabel = timeLabels.get(timeLabels.size() - 1);
            // Parse last label to get time
            String[] parts = lastLabel.split(":");
            int lastHour = Integer.parseInt(parts[0]);
            int lastMinute = Integer.parseInt(parts[1]);
            int lastTotalMinutes = lastHour * 60 + lastMinute;
            
            if (lastTotalMinutes + binSize <= webinarDurationMinutes) {
                // Add a final bin if needed to reach exact duration
                int finalStart = lastTotalMinutes + binSize;
                int finalHour = finalStart / 60;
                int finalMinute = finalStart % 60;
                String finalLabel = String.format("%02d:%02d", finalHour, finalMinute);
                timeLabels.add(finalLabel);
            }
        }
        
        System.out.println("🕒 Generated DYNAMIC " + intervalMinutes + "-min time bins for " + webinarDurationMinutes + "min webinar: " + timeLabels);
        return timeLabels;
    }

    // Calculate REAL-TIME engagement with INDIVIDUAL USER TRACKING
    private Map<String, Object> calculateRealTimeEngagementWithUserTracking(List<Participant> participants, int webinarDuration, int intervalMinutes) {
        // Generate dynamic time segments based on interval
        List<String> timeBins = generateDynamicTimeBins(webinarDuration, intervalMinutes);
        int segmentCount = timeBins.size();
        int segmentDuration = intervalMinutes; // Use dynamic interval
        int totalMinutes = webinarDuration;
        
        // Arrays to store results for each segment
        int[] activeBySegment = new int[segmentCount];      // Average active users per segment
        int[] joinedBySegment = new int[segmentCount];      // Users joined in each segment
        int[] leftBySegment = new int[segmentCount];        // Users left in each segment  
        int[] peakBySegment = new int[segmentCount];        // Peak active users per segment
        
        // Minute-by-minute tracking for accurate real-time calculation
        int[] activeUsersPerMinute = new int[totalMinutes];
        Arrays.fill(activeUsersPerMinute, 0);
        
        // Individual user tracking
        List<Map<String, Object>> userTimelines = new ArrayList<>();
        List<Map<String, Object>> participantDetails = new ArrayList<>();
        
        // Find webinar start time (earliest join time)
        LocalDateTime webinarStart = findWebinarStartTime(participants);
        System.out.println("📅 Webinar Start Time: " + webinarStart);
        System.out.println("⏰ Webinar Duration: " + webinarDuration + " minutes");
        System.out.println("📊 Segment Count: " + segmentCount + " segments");
        System.out.println("⏱️ Segment Duration: " + segmentDuration + " minutes per segment");
        
        // Process each participant's actual join and leave times
        for (Participant participant : participants) {
            try {
                String joinTimeStr = participant.getJoinTime();
                String leaveTimeStr = participant.getLeaveTime();
                
                // Parse join and leave times
                LocalDateTime joinTime = LocalDateTime.parse(joinTimeStr, ZOOM_TIME_FORMATTER);
                LocalDateTime leaveTime = LocalDateTime.parse(leaveTimeStr, ZOOM_TIME_FORMATTER);
                
                // Calculate minutes from webinar start
                long joinMinuteFromStart = Duration.between(webinarStart, joinTime).toMinutes();
                long leaveMinuteFromStart = Duration.between(webinarStart, leaveTime).toMinutes();
                
                // Ensure minutes are within webinar bounds (0 to webinarDuration-1 minutes)
                joinMinuteFromStart = Math.max(0, Math.min(joinMinuteFromStart, totalMinutes - 1));
                leaveMinuteFromStart = Math.max(0, Math.min(leaveMinuteFromStart, totalMinutes - 1));
                
                // Determine which segment the join and leave events happened in
                int joinSegment = (int) (joinMinuteFromStart / segmentDuration);
                int leaveSegment = (int) (leaveMinuteFromStart / segmentDuration);
                
                // Count join event
                if (joinSegment < segmentCount) {
                    joinedBySegment[joinSegment]++;
                }
                
                // Count leave event  
                if (leaveSegment < segmentCount) {
                    leftBySegment[leaveSegment]++;
                }
                
                // Mark participant as active for each minute they were present
                for (int minute = (int) joinMinuteFromStart; minute <= (int) leaveMinuteFromStart; minute++) {
                    if (minute < totalMinutes) {
                        activeUsersPerMinute[minute]++;
                    }
                }
                
                // Create individual user timeline
                Map<String, Object> userTimeline = new HashMap<>();
                userTimeline.put("user_id", participant.getUserId() != null ? participant.getUserId() : "");
                userTimeline.put("name", participant.getName() != null ? participant.getName() : "");
                userTimeline.put("email", participant.getUserEmail() != null ? participant.getUserEmail() : "");
                userTimeline.put("join_time", joinTimeStr);
                userTimeline.put("leave_time", leaveTimeStr);
                userTimeline.put("duration_minutes", Math.round(participant.getDuration() / 60.0 * 100.0) / 100.0);
                userTimeline.put("join_minute", (int) joinMinuteFromStart);
                userTimeline.put("leave_minute", (int) leaveMinuteFromStart);
                
                // Create presence array for each segment (1 = present, 0 = absent)
                List<Integer> presenceBySegment = new ArrayList<>();
                for (int segment = 0; segment < segmentCount; segment++) {
                    int segmentStartMinute = segment * segmentDuration;
                    int segmentEndMinute = Math.min((segment + 1) * segmentDuration - 1, totalMinutes - 1);
                    
                    boolean presentInSegment = ((int) joinMinuteFromStart <= segmentEndMinute) && 
                                             ((int) leaveMinuteFromStart >= segmentStartMinute);
                    presenceBySegment.add(presentInSegment ? 1 : 0);
                }
                userTimeline.put("presence_by_segment", presenceBySegment);
                userTimelines.add(userTimeline);
                
                // Create participant details
                Map<String, Object> participantDetail = new HashMap<>();
                participantDetail.put("user_id", participant.getUserId() != null ? participant.getUserId() : "");
                participantDetail.put("name", participant.getName() != null ? participant.getName() : "");
                participantDetail.put("email", participant.getUserEmail() != null ? participant.getUserEmail() : "");
                participantDetail.put("join_time", joinTimeStr);
                participantDetail.put("leave_time", leaveTimeStr);
                participantDetail.put("duration_seconds", participant.getDuration());
                participantDetail.put("duration_minutes", Math.round(participant.getDuration() / 60.0 * 100.0) / 100.0);
                participantDetail.put("join_segment", joinSegment);
                participantDetail.put("leave_segment", leaveSegment);
                participantDetail.put("attentiveness_score", participant.getAttentivenessScore() != null ? participant.getAttentivenessScore() : "");
                participantDetails.add(participantDetail);
                
            } catch (Exception e) {
                System.err.println("❌ Error processing participant " + participant.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // Calculate segment statistics from minute-by-minute data
        for (int segment = 0; segment < segmentCount; segment++) {
            int segmentStartMinute = segment * segmentDuration;
            int segmentEndMinute = Math.min((segment + 1) * segmentDuration - 1, totalMinutes - 1);
            
            // Calculate average active users in this segment
            int segmentSum = 0;
            int segmentPeak = 0;
            int minutesInSegment = 0;
            
            for (int minute = segmentStartMinute; minute <= segmentEndMinute; minute++) {
                segmentSum += activeUsersPerMinute[minute];
                if (activeUsersPerMinute[minute] > segmentPeak) {
                    segmentPeak = activeUsersPerMinute[minute];
                }
                minutesInSegment++;
            }
            
            activeBySegment[segment] = minutesInSegment > 0 ? segmentSum / minutesInSegment : 0; // Average active users
            peakBySegment[segment] = segmentPeak; // Peak active users in segment
        }
        
        // Calculate overall statistics
        int peakConcurrent = Arrays.stream(activeUsersPerMinute).max().orElse(0);
        int finalActiveUsers = totalMinutes > 0 ? activeUsersPerMinute[totalMinutes - 1] : 0; // Users still active at end
        int totalJoined = Arrays.stream(joinedBySegment).sum();
        int totalLeft = Arrays.stream(leftBySegment).sum();
        
        // Convert arrays to lists for response
        List<Integer> activeParticipantsList = new ArrayList<>();
        List<Integer> engagementRatesList = new ArrayList<>();
        List<Integer> usersJoinedList = new ArrayList<>();
        List<Integer> usersLeftList = new ArrayList<>();
        List<Integer> peakActiveUsersList = new ArrayList<>();
        
        for (int i = 0; i < segmentCount; i++) {
            activeParticipantsList.add(activeBySegment[i]);
            int engagementRate = participants.size() > 0 ? (activeBySegment[i] * 100) / participants.size() : 0;
            engagementRatesList.add(engagementRate);
            usersJoinedList.add(joinedBySegment[i]);
            usersLeftList.add(leftBySegment[i]);
            peakActiveUsersList.add(peakBySegment[i]);
        }
        
        // Debug output
        System.out.println("🎯 REAL-TIME ENGAGEMENT ANALYSIS WITH USER TRACKING:");
        System.out.println("📊 Peak Concurrent Users: " + peakConcurrent);
        System.out.println("📊 Final Active Users: " + finalActiveUsers);
        System.out.println("📊 Total Joined: " + totalJoined + " | Total Left: " + totalLeft);
        System.out.println("👥 Total Participants: " + participants.size());
        System.out.println("⏰ Segment Active Users: " + activeParticipantsList);
        System.out.println("➕ Segment Joins: " + usersJoinedList);
        System.out.println("➖ Segment Leaves: " + usersLeftList);
        
        Map<String, Object> result = new HashMap<>();
        result.put("active_participants", activeParticipantsList);
        result.put("engagement_rate", engagementRatesList);
        result.put("users_joined", usersJoinedList);
        result.put("users_left", usersLeftList);
        result.put("peak_active_users", peakActiveUsersList);
        result.put("peak_concurrent", peakConcurrent);
        result.put("final_active_users", finalActiveUsers);
        result.put("total_joined", totalJoined);
        result.put("total_left", totalLeft);
        result.put("user_timelines", userTimelines);
        result.put("participant_details", participantDetails);
        
        return result;
    }

    // Helper method to find webinar start time (earliest join time)
    private LocalDateTime findWebinarStartTime(List<Participant> participants) {
        LocalDateTime earliest = null;
        
        for (Participant participant : participants) {
            try {
                String joinTimeStr = participant.getJoinTime();
                LocalDateTime joinTime = LocalDateTime.parse(joinTimeStr, ZOOM_TIME_FORMATTER);
                
                if (earliest == null || joinTime.isBefore(earliest)) {
                    earliest = joinTime;
                }
            } catch (Exception e) {
                // Skip participants with invalid join times
                System.err.println("⚠️  Invalid join time for participant: " + participant.getName());
            }
        }
        
        // If no valid join times found, use the first participant's join time as fallback
        if (earliest == null && !participants.isEmpty()) {
            try {
                String firstJoinTime = participants.get(0).getJoinTime();
                earliest = LocalDateTime.parse(firstJoinTime, ZOOM_TIME_FORMATTER);
                System.out.println("⚠️  Using first participant's join time as webinar start: " + earliest);
            } catch (Exception e) {
                // Ultimate fallback
                earliest = LocalDateTime.now().minusMinutes(60);
                System.out.println("⚠️  Using default webinar start time: " + earliest);
            }
        }
        
        return earliest;
    }

    // Get Complete Meeting Analytics with Participant Details - UPDATED to accept interval
    public Mono<Map<String, Object>> getMeetingAnalytics(String meetingId, Integer intervalMinutes) {
        int interval = intervalMinutes != null ? intervalMinutes : 5; // Default to 5 minutes
        
        return getAccessToken()
                .flatMap(authResponse -> {
                    // Get transcript data FIRST and include it in analytics
                    return getMeetingTranscript(meetingId)
                            .flatMap(transcriptData -> {
                                System.out.println("🎤 Transcript data retrieved for analytics: " + transcriptData.get("success"));
                                
                                // Then get participant data and analytics
                                return getMeetingParticipants(authResponse.getAccessToken(), meetingId)
                                        .map(participantsResponse -> {
                                            Map<String, Object> analytics = new HashMap<>();
                                            // Default meeting duration if not available
                                            int meetingDuration = 60; // default 1 hour
                                            Map<String, Object> engagementData = calculateEngagementMetrics(participantsResponse, meetingDuration, interval);
                                            
                                            analytics.put("meeting_id", meetingId);
                                            analytics.put("success", true);
                                            analytics.put("interval_minutes", interval);
                                            analytics.put("total_participants", engagementData.get("total_participants"));
                                            analytics.put("engagement_metrics", engagementData);
                                            analytics.put("engagement_graph", engagementData.get("engagement_over_time"));
                                            analytics.put("participant_details", engagementData.get("participant_details"));
                                            analytics.put("user_timelines", engagementData.get("user_timelines"));
                                            
                                            // ADD TRANSCRIPT DATA TO ANALYTICS
                                            analytics.put("transcript", transcriptData);
                                            analytics.put("transcript_available", transcriptData.get("success"));
                                            analytics.put("transcript_download_url", transcriptData.get("download_url"));
                                            
                                            analytics.put("message", "Real participant data analyzed with individual user tracking");
                                            analytics.put("data_source", "zoom_api");
                                            
                                            // Add real-time specific metrics
                                            analytics.put("peak_concurrent_users", engagementData.get("peak_concurrent_users"));
                                            analytics.put("final_active_users", engagementData.get("final_active_users"));
                                            analytics.put("total_joined", engagementData.get("total_joined"));
                                            analytics.put("total_left", engagementData.get("total_left"));
                                            
                                            System.out.println("✅ Analytics response includes transcript: " + analytics.containsKey("transcript"));
                                            return analytics;
                                        })
                                        .onErrorResume(e -> {
                                            System.err.println("❌ Error getting real meeting data: " + e.getMessage());
                                            // Even if analytics fail, return transcript data
                                            Map<String, Object> fallbackAnalytics = new HashMap<>();
                                            fallbackAnalytics.put("meeting_id", meetingId);
                                            fallbackAnalytics.put("success", false);
                                            fallbackAnalytics.put("error", "Analytics failed but transcript available");
                                            fallbackAnalytics.put("transcript", transcriptData);
                                            fallbackAnalytics.put("transcript_available", transcriptData.get("success"));
                                            return Mono.just(fallbackAnalytics);
                                        });
                            })
                            .onErrorResume(e -> {
                                System.err.println("❌ Error getting transcript for analytics: " + e.getMessage());
                                // If transcript fails, try to get analytics without transcript
                                return getMeetingParticipants(authResponse.getAccessToken(), meetingId)
                                        .map(participantsResponse -> {
                                            Map<String, Object> analytics = new HashMap<>();
                                            int meetingDuration = 60;
                                            Map<String, Object> engagementData = calculateEngagementMetrics(participantsResponse, meetingDuration, interval);
                                            
                                            analytics.put("meeting_id", meetingId);
                                            analytics.put("success", true);
                                            analytics.put("interval_minutes", interval);
                                            analytics.put("total_participants", engagementData.get("total_participants"));
                                            analytics.put("engagement_metrics", engagementData);
                                            analytics.put("engagement_graph", engagementData.get("engagement_over_time"));
                                            analytics.put("participant_details", engagementData.get("participant_details"));
                                            analytics.put("user_timelines", engagementData.get("user_timelines"));
                                            analytics.put("message", "Real participant data analyzed (transcript unavailable)");
                                            analytics.put("data_source", "zoom_api");
                                            analytics.put("transcript_available", false);
                                            analytics.put("transcript_error", "Failed to load transcript: " + e.getMessage());
                                            
                                            return analytics;
                                        })
                                        .onErrorResume(e2 -> {
                                            System.err.println("❌ Both transcript and analytics failed: " + e2.getMessage());
                                            return generateSimulatedAnalytics(meetingId, interval);
                                        });
                            });
                })
                .onErrorResume(e -> {
                    System.err.println("❌ Error in meeting analytics: " + e.getMessage());
                    return generateSimulatedAnalytics(meetingId, interval);
                });
    }

    // Generate simulated analytics when real data is not available - UPDATED to accept interval
    private Mono<Map<String, Object>> generateSimulatedAnalytics(String meetingId, int intervalMinutes) {
        return getAccessToken()
                .flatMap(authResponse -> getMeetings(authResponse.getAccessToken()))
                .map(meetingsResponse -> {
                    Map<String, Object> analytics = new HashMap<>();
                    
                    // Find the meeting to get basic info
                    Optional<ZoomMeeting> meetingOpt = meetingsResponse.getMeetings().stream()
                            .filter(meeting -> meetingId.equals(meeting.getId()))
                            .findFirst();
                    
                    if (meetingOpt.isPresent()) {
                        ZoomMeeting meeting = meetingOpt.get();
                        Map<String, Object> simulatedData = createSimulatedEngagementData(meeting, intervalMinutes);
                        
                        analytics.put("meeting_id", meetingId);
                        analytics.put("success", true);
                        analytics.put("interval_minutes", intervalMinutes);
                        analytics.put("meeting_topic", meeting.getTopic());
                        analytics.put("meeting_duration", meeting.getDuration());
                        analytics.put("meeting_start_time", meeting.getStartTime());
                        analytics.put("total_participants", simulatedData.get("total_participants"));
                        analytics.put("engagement_metrics", simulatedData);
                        analytics.put("engagement_graph", simulatedData.get("engagement_over_time"));
                        analytics.put("message", "Simulated analytics (real participant data not available)");
                        analytics.put("data_source", "simulated");
                        analytics.put("note", "Real participant data is only available for recent meetings via Zoom API");
                        analytics.put("transcript_available", false);
                    } else {
                        analytics.put("success", false);
                        analytics.put("error", "Meeting not found");
                        analytics.put("meeting_id", meetingId);
                        analytics.put("transcript_available", false);
                    }
                    
                    return analytics;
                })
                .onErrorResume(e -> {
                    // Ultimate fallback - basic simulated data
                    Map<String, Object> basicAnalytics = createBasicSimulatedData(meetingId, intervalMinutes);
                    return Mono.just(basicAnalytics);
                });
    }

    // Create realistic simulated engagement data based on meeting - UPDATED to accept interval
    private Map<String, Object> createSimulatedEngagementData(ZoomMeeting meeting, int intervalMinutes) {
        Map<String, Object> engagementData = new HashMap<>();
        
        // Simulate realistic participant counts based on meeting type
        int baseParticipants = 12;
        if (meeting.getTopic().toLowerCase().contains("review") || 
            meeting.getTopic().toLowerCase().contains("team")) {
            baseParticipants = 8; // Smaller for reviews
        } else if (meeting.getTopic().toLowerCase().contains("one on one")) {
            baseParticipants = 2; // For 1:1 meetings
        }
        
        Random random = new Random(meeting.getId().hashCode()); // Consistent based on meeting ID
        
        int totalParticipants = baseParticipants + random.nextInt(8);
        int meetingDuration = meeting.getDuration();
        
        // Generate DYNAMIC time bins based on interval
        List<String> timeLabels = generateDynamicTimeBins(meetingDuration, intervalMinutes);
        int segments = timeLabels.size();
        
        List<Integer> activeParticipants = new ArrayList<>();
        List<Integer> engagementRates = new ArrayList<>();
        List<Integer> usersJoined = new ArrayList<>();
        List<Integer> usersLeft = new ArrayList<>();
        List<Integer> peakActiveUsers = new ArrayList<>();
        List<Map<String, Object>> userTimelines = new ArrayList<>();
        List<Map<String, Object>> participantDetails = new ArrayList<>();
        
        for (int i = 0; i < segments; i++) {
            // Simulate natural drop-off
            double retention = 1.0 - (i * 0.08); // 8% drop per segment
            int active = (int) (totalParticipants * retention);
            activeParticipants.add(Math.max(active, 1));
            
            int engagementRate = 100 - (i * 8); // 8% engagement drop per segment
            engagementRates.add(Math.max(engagementRate, 20));
            
            // Simulate join/leave patterns
            usersJoined.add(i == 0 ? totalParticipants : random.nextInt(3));
            usersLeft.add(i == 0 ? 0 : random.nextInt(5));
            peakActiveUsers.add(active + random.nextInt(3));
        }
        
        // Create simulated participant details
        for (int i = 0; i < totalParticipants; i++) {
            Map<String, Object> participantDetail = new HashMap<>();
            participantDetail.put("user_id", "simulated_user_" + i);
            participantDetail.put("name", "User " + (i + 1));
            participantDetail.put("email", "user" + (i + 1) + "@example.com");
            participantDetail.put("join_time", LocalDateTime.now().minusMinutes(meetingDuration).format(ZOOM_TIME_FORMATTER));
            participantDetail.put("leave_time", LocalDateTime.now().format(ZOOM_TIME_FORMATTER));
            participantDetail.put("duration_seconds", meetingDuration * 60);
            participantDetail.put("duration_minutes", meetingDuration);
            participantDetail.put("attentiveness_score", "");
            participantDetails.add(participantDetail);
            
            Map<String, Object> userTimeline = new HashMap<>();
            userTimeline.put("user_id", "simulated_user_" + i);
            userTimeline.put("name", "User " + (i + 1));
            userTimeline.put("email", "user" + (i + 1) + "@example.com");
            userTimeline.put("join_time", LocalDateTime.now().minusMinutes(meetingDuration).format(ZOOM_TIME_FORMATTER));
            userTimeline.put("leave_time", LocalDateTime.now().format(ZOOM_TIME_FORMATTER));
            userTimeline.put("duration_minutes", meetingDuration);
            userTimeline.put("join_minute", 0);
            userTimeline.put("leave_minute", meetingDuration - 1);
            
            // Create presence array
            List<Integer> presenceBySegment = new ArrayList<>();
            for (int seg = 0; seg < segments; seg++) {
                presenceBySegment.add(1); // All present in simulation
            }
            userTimeline.put("presence_by_segment", presenceBySegment);
            userTimelines.add(userTimeline);
        }
        
        // Calculate metrics
        int totalDuration = meetingDuration * totalParticipants;
        double averageDuration = meetingDuration * 0.7; // 70% average attendance
        
        Map<String, Object> engagementGraph = new HashMap<>();
        engagementGraph.put("labels", timeLabels);
        engagementGraph.put("active_participants", activeParticipants);
        engagementGraph.put("engagement_rate", engagementRates);
        engagementGraph.put("users_joined", usersJoined);
        engagementGraph.put("users_left", usersLeft);
        engagementGraph.put("peak_active_users", peakActiveUsers);
        engagementGraph.put("user_timelines", userTimelines);
        
        engagementData.put("total_participants", totalParticipants);
        engagementData.put("average_participation_minutes", Math.round(averageDuration * 100.0) / 100.0);
        engagementData.put("max_participation_minutes", meetingDuration);
        engagementData.put("min_participation_minutes", 5);
        engagementData.put("total_meeting_minutes", totalDuration);
        engagementData.put("engagement_score", 75 + random.nextInt(20));
        engagementData.put("attention_retention", Math.round((activeParticipants.get(activeParticipants.size()-1) / (double)totalParticipants) * 100));
        engagementData.put("engagement_over_time", engagementGraph);
        engagementData.put("participant_details", participantDetails);
        engagementData.put("user_timelines", userTimelines);
        engagementData.put("peak_concurrent_users", totalParticipants);
        engagementData.put("final_active_users", activeParticipants.get(activeParticipants.size()-1));
        engagementData.put("total_joined", totalParticipants);
        engagementData.put("total_left", totalParticipants - activeParticipants.get(activeParticipants.size()-1));
        
        return engagementData;
    }

    // Create basic simulated data as ultimate fallback - UPDATED to accept interval
    private Map<String, Object> createBasicSimulatedData(String meetingId, int intervalMinutes) {
        Map<String, Object> analytics = new HashMap<>();
        Map<String, Object> engagementData = new HashMap<>();
        
        // Use DYNAMIC time bins for 60-minute meeting
        List<String> timeLabels = generateDynamicTimeBins(60, intervalMinutes);
        
        // Generate data for each segment
        List<Integer> activeParticipants = new ArrayList<>();
        List<Integer> engagementRates = new ArrayList<>();
        List<Integer> usersJoined = new ArrayList<>();
        List<Integer> usersLeft = new ArrayList<>();
        List<Integer> peakActiveUsers = new ArrayList<>();
        List<Map<String, Object>> userTimelines = new ArrayList<>();
        List<Map<String, Object>> participantDetails = new ArrayList<>();
        
        Random random = new Random(meetingId.hashCode());
        int baseParticipants = 15;
        
        for (int i = 0; i < timeLabels.size(); i++) {
            // Simulate natural drop-off
            double retention = 1.0 - (i * 0.07);
            int active = (int) (baseParticipants * retention);
            activeParticipants.add(Math.max(active, 1));
            
            int engagementRate = 100 - (i * 7);
            engagementRates.add(Math.max(engagementRate, 20));
            
            // Simulate join/leave patterns
            usersJoined.add(i == 0 ? baseParticipants : random.nextInt(2));
            usersLeft.add(i == 0 ? 0 : random.nextInt(3));
            peakActiveUsers.add(active + random.nextInt(2));
        }
        
        // Create basic participant details
        for (int i = 0; i < baseParticipants; i++) {
            Map<String, Object> participantDetail = new HashMap<>();
            participantDetail.put("user_id", "basic_user_" + i);
            participantDetail.put("name", "Basic User " + (i + 1));
            participantDetail.put("email", "basic" + (i + 1) + "@example.com");
            participantDetail.put("join_time", LocalDateTime.now().minusMinutes(60).format(ZOOM_TIME_FORMATTER));
            participantDetail.put("leave_time", LocalDateTime.now().minusMinutes(10).format(ZOOM_TIME_FORMATTER));
            participantDetail.put("duration_seconds", 3000);
            participantDetail.put("duration_minutes", 50);
            participantDetail.put("attentiveness_score", "");
            participantDetails.add(participantDetail);
        }
        
        Map<String, Object> engagementGraph = new HashMap<>();
        engagementGraph.put("labels", timeLabels);
        engagementGraph.put("active_participants", activeParticipants);
        engagementGraph.put("engagement_rate", engagementRates);
        engagementGraph.put("users_joined", usersJoined);
        engagementGraph.put("users_left", usersLeft);
        engagementGraph.put("peak_active_users", peakActiveUsers);
        engagementGraph.put("user_timelines", userTimelines);
        
        engagementData.put("total_participants", 15);
        engagementData.put("average_participation_minutes", 42.5);
        engagementData.put("max_participation_minutes", 60);
        engagementData.put("min_participation_minutes", 5);
        engagementData.put("total_meeting_minutes", 637);
        engagementData.put("engagement_score", 82);
        engagementData.put("attention_retention", 67);
        engagementData.put("engagement_over_time", engagementGraph);
        engagementData.put("participant_details", participantDetails);
        engagementData.put("user_timelines", userTimelines);
        engagementData.put("peak_concurrent_users", 15);
        engagementData.put("final_active_users", 10);
        engagementData.put("total_joined", 15);
        engagementData.put("total_left", 5);
        
        analytics.put("meeting_id", meetingId);
        analytics.put("success", true);
        analytics.put("interval_minutes", intervalMinutes);
        analytics.put("total_participants", 15);
        analytics.put("engagement_metrics", engagementData);
        analytics.put("engagement_graph", engagementGraph);
        analytics.put("participant_details", participantDetails);
        analytics.put("user_timelines", userTimelines);
        analytics.put("message", "Basic simulated analytics (fallback data)");
        analytics.put("data_source", "basic_fallback");
        analytics.put("peak_concurrent_users", 15);
        analytics.put("final_active_users", 10);
        analytics.put("total_joined", 15);
        analytics.put("total_left", 5);
        analytics.put("transcript_available", false);
        
        return analytics;
    }

    // Get All Meetings with Basic Info
    public Mono<Map<String, Object>> getAllMeetings() {
        return getAccessToken()
                .flatMap(authResponse -> getMeetings(authResponse.getAccessToken()))
                .map(meetingsResponse -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("total_meetings", meetingsResponse.getTotalRecords());
                    
                    if (meetingsResponse.getMeetings() != null && !meetingsResponse.getMeetings().isEmpty()) {
                        result.put("meetings", meetingsResponse.getMeetings());
                        
                        // Count upcoming vs completed
                        long upcoming = meetingsResponse.getMeetings().stream()
                                .filter(meeting -> meeting.getStartTime().isAfter(java.time.LocalDateTime.now()))
                                .count();
                        long completed = meetingsResponse.getMeetings().size() - upcoming;
                        
                        result.put("upcoming", upcoming);
                        result.put("completed", completed);
                        
                        // Add meeting summaries for quick overview
                        List<Map<String, Object>> meetingSummaries = new ArrayList<>();
                        for (ZoomMeeting meeting : meetingsResponse.getMeetings()) {
                            Map<String, Object> summary = new HashMap<>();
                            summary.put("id", meeting.getId());
                            summary.put("topic", meeting.getTopic());
                            summary.put("start_time", meeting.getStartTime());
                            summary.put("duration", meeting.getDuration());
                            summary.put("join_url", meeting.getJoinUrl());
                            meetingSummaries.add(summary);
                        }
                        result.put("meeting_summaries", meetingSummaries);
                    } else {
                        result.put("meetings", Collections.emptyList());
                        result.put("upcoming", 0);
                        result.put("completed", 0);
                        result.put("meeting_summaries", Collections.emptyList());
                        result.put("message", "No meetings found");
                    }
                    
                    return result;
                })
                .onErrorResume(e -> {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "Failed to fetch meetings: " + e.getMessage());
                    return Mono.just(errorResponse);
                });
    }

    // Get meeting details by ID
    public Mono<Map<String, Object>> getMeetingDetails(String meetingId) {
        return getAccessToken()
                .flatMap(authResponse -> getMeetings(authResponse.getAccessToken()))
                .map(meetingsResponse -> {
                    Map<String, Object> result = new HashMap<>();
                    
                    if (meetingsResponse.getMeetings() != null) {
                        Optional<ZoomMeeting> meetingOpt = meetingsResponse.getMeetings().stream()
                                .filter(meeting -> meetingId.equals(meeting.getId()))
                                .findFirst();
                        
                        if (meetingOpt.isPresent()) {
                            ZoomMeeting meeting = meetingOpt.get();
                            result.put("success", true);
                            result.put("meeting", meeting);
                            result.put("meeting_id", meeting.getId());
                            result.put("topic", meeting.getTopic());
                            result.put("start_time", meeting.getStartTime());
                            result.put("duration", meeting.getDuration());
                            result.put("join_url", meeting.getJoinUrl());
                        } else {
                            result.put("success", false);
                            result.put("error", "Meeting not found with ID: " + meetingId);
                        }
                    } else {
                        result.put("success", false);
        result.put("error", "No meetings available");
                    }
                    
                    return result;
                })
                .onErrorResume(e -> {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "Failed to fetch meeting details: " + e.getMessage());
                    return Mono.just(errorResponse);
                });
    }

    // Test Zoom API connection
    public Mono<Map<String, Object>> testConnection() {
        return getAccessToken()
                .map(authResponse -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("message", "Zoom API connection successful");
                    result.put("token_type", authResponse.getTokenType());
                    result.put("expires_in", authResponse.getExpiresIn());
                    result.put("scope", authResponse.getScope());
                    return result;
                })
                .onErrorResume(e -> {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "Zoom API connection failed: " + e.getMessage());
                    return Mono.just(errorResponse);
                });
    }
    
    // ========== TRANSCRIPT METHODS ==========
    
    // Get Meeting Transcript - SIMPLE VERSION (Returns download URL for frontend)
    public Mono<Map<String, Object>> getMeetingTranscript(String meetingId) {
        System.out.println("🎯 SIMPLE getMeetingTranscript for: " + meetingId);

        return getAccessToken()
                .flatMap(authResponse -> {
                    System.out.println("✅ Got access token");
                    
                    return webClient.get()
                            .uri("https://api.zoom.us/v2/meetings/" + meetingId + "/recordings")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.getAccessToken())
                            .retrieve()
                            .bodyToMono(ZoomRecordingsResponse.class)
                            .flatMap(recordingsResponse -> {
                                System.out.println("📥 Found " + recordingsResponse.getRecordingFiles().size() + " recording files");
                                
                                // Find transcript file
                                Optional<RecordingFile> transcriptOpt = recordingsResponse.getRecordingFiles().stream()
                                        .filter(file -> "TRANSCRIPT".equals(file.getFileType()))
                                        .findFirst();

                                if (transcriptOpt.isEmpty()) {
                                    System.out.println("❌ No transcript file available");
                                    Map<String, Object> errorResult = new HashMap<>();
                                    errorResult.put("success", false);
                                    errorResult.put("error", "No transcript file available for this meeting");
                                    errorResult.put("transcript_available", false);
                                    return Mono.just(errorResult);
                                }

                                RecordingFile transcript = transcriptOpt.get();
                                System.out.println("🎤 Found transcript file: " + transcript.getDownloadUrl());
                                
                                // Return the URL and let frontend handle the download
                                Map<String, Object> result = new HashMap<>();
                                result.put("success", true);
                                result.put("meeting_id", meetingId);
                                result.put("transcript_available", true);
                                result.put("download_url", transcript.getDownloadUrl());
                                result.put("file_id", transcript.getId());
                                result.put("file_type", transcript.getFileType());
                                result.put("file_extension", transcript.getFileExtension());
                                result.put("message", "Transcript available - use download_url to get content");
                                result.put("solution", "Frontend should fetch the download_url directly");
                                result.put("data_source", "zoom_api");
                                
                                return Mono.just(result);
                            });
                })
                .onErrorResume(e -> {
                    System.out.println("❌ Error in transcript method: " + e.getMessage());
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("success", false);
                    errorResult.put("error", "Failed to get transcript: " + e.getMessage());
                    errorResult.put("transcript_available", false);
                    return Mono.just(errorResult);
                });
    }

    // NEW FIXED METHOD: Download transcript with actual content - COMPLETELY FIXED
 // FIXED VERSION: Download transcript with STREAMING to handle large files
    public Mono<Map<String, Object>> downloadTranscriptWithContent(String meetingId, String downloadUrl) {
        System.out.println("📥 DOWNLOAD WITH CONTENT (STREAMING) for: " + meetingId);
        System.out.println("🔗 Download URL: " + downloadUrl);
        
        return getAccessToken()
                .flatMap(authResponse -> {
                    System.out.println("🔑 Got access token for download");
                    
                    // Create a WebClient with larger buffer for streaming
                    WebClient webClient = WebClient.builder()
                            .codecs(configurer -> configurer
                                    .defaultCodecs()
                                    .maxInMemorySize(10 * 1024 * 1024)) // 10MB max buffer
                            .build();
                    
                    return webClient.get()
                            .uri(downloadUrl)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.getAccessToken())
                            .header(HttpHeaders.ACCEPT, "text/vtt, text/plain, */*")
                            .exchangeToMono(response -> {
                                System.out.println("📥 Initial request HTTP Status: " + response.statusCode());
                                
                                if (response.statusCode().is2xxSuccessful()) {
                                    // Stream the content
                                    return response.bodyToMono(String.class)
                                            .map(content -> {
                                                System.out.println("✅ SUCCESS: Downloaded transcript, content length: " + content.length());
                                                
                                                Map<String, Object> result = new HashMap<>();
                                                result.put("success", true);
                                                result.put("meeting_id", meetingId);
                                                result.put("content", content);
                                                result.put("content_length", content.length());
                                                result.put("has_content", !content.trim().isEmpty());
                                                result.put("transcript_available", true);
                                                result.put("download_method", "streamed_download");
                                                result.put("http_status", response.statusCode().value());
                                                
                                                // Add preview for debugging
                                                if (content.length() > 0) {
                                                    String preview = content.substring(0, Math.min(1000, content.length()));
                                                    result.put("content_preview", preview);
                                                }
                                                
                                                return result;
                                            })
                                            .timeout(Duration.ofSeconds(120)); // 120 second timeout
                                } else if (response.statusCode().is3xxRedirection()) {
                                    // Handle redirect
                                    String redirectUrl = response.headers().header(HttpHeaders.LOCATION).get(0);
                                    System.out.println("🔄 Found redirect URL: " + redirectUrl);
                                    
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("success", true);
                                    result.put("meeting_id", meetingId);
                                    result.put("has_redirect", true);
                                    result.put("redirect_url", redirectUrl);
                                    result.put("transcript_available", true);
                                    result.put("download_method", "aws_redirect");
                                    result.put("message", "Large file - use redirect_url for download");
                                    return Mono.just(result);
                                } else {
                                    // Other HTTP status
                                    System.out.println("❌ Download failed with HTTP status: " + response.statusCode());
                                    
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("success", false);
                                    result.put("error", "HTTP " + response.statusCode());
                                    result.put("meeting_id", meetingId);
                                    result.put("transcript_available", true);
                                    result.put("download_method", "http_error");
                                    result.put("http_status", response.statusCode().value());
                                    return Mono.just(result);
                                }
                            });
                })
                .onErrorResume(e -> {
                    System.out.println("❌ Download error: " + e.getMessage());
                    
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("success", false);
                    errorResult.put("error", "Download failed: " + e.getMessage());
                    errorResult.put("meeting_id", meetingId);
                    errorResult.put("transcript_available", true);
                    errorResult.put("download_method", "token_error");
                    return Mono.just(errorResult);
                })
                .timeout(Duration.ofSeconds(180)) // 3 minute timeout overall
                .onErrorResume(e -> {
                    System.out.println("❌ Download timeout: " + e.getMessage());
                    
                    Map<String, Object> timeoutResult = new HashMap<>();
                    timeoutResult.put("success", false);
                    timeoutResult.put("error", "Download timeout (180s): " + e.getMessage());
                    timeoutResult.put("meeting_id", meetingId);
                    timeoutResult.put("transcript_available", true);
                    timeoutResult.put("download_method", "timeout");
                    timeoutResult.put("suggestion", "Try streaming endpoint: /api/transcript-stream-simple/" + meetingId);
                    return Mono.just(timeoutResult);
                });
    }

    // New method to try multiple AWS download strategies
    private Mono<Map<String, Object>> attemptMultipleAWSDownloadStrategies(
        String meetingId, String accessToken, String awsUrl
    ) {
        System.out.println("🔄 Trying multiple AWS download strategies...");
        
        Map<String, Object> baseResult = new HashMap<>();
        baseResult.put("success", false);
        baseResult.put("meeting_id", meetingId);
        baseResult.put("transcript_available", true);
        baseResult.put("aws_url", awsUrl);
        
        // Strategy 1: Try with original access token (sometimes works)
        Mono<Map<String, Object>> strategy1 = tryDownloadWithStrategy(awsUrl, "strategy1_auth", Map.of(
            HttpHeaders.AUTHORIZATION, "Bearer " + accessToken,
            HttpHeaders.ACCEPT, "text/vtt, text/plain, */*"
        ));
        
        // Strategy 2: Try with no auth but proper user agent (CloudFront sometimes allows)
        Mono<Map<String, Object>> strategy2 = tryDownloadWithStrategy(awsUrl, "strategy2_no_auth", Map.of(
            HttpHeaders.ACCEPT, "text/vtt, text/plain, */*",
            HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        ));
        
        // Strategy 3: Try with minimal headers
        Mono<Map<String, Object>> strategy3 = tryDownloadWithStrategy(awsUrl, "strategy3_minimal", Map.of(
            HttpHeaders.ACCEPT, "*/*"
        ));
        
        // Try strategies in order until one works
        return strategy1
                .flatMap(result -> {
                    if (result.containsKey("content") && result.get("content") != null) {
                        System.out.println("✅ Strategy 1 (with auth) succeeded");
                        return Mono.just(result);
                    }
                    System.out.println("⚠️ Strategy 1 failed, trying strategy 2");
                    return strategy2;
                })
                .flatMap(result -> {
                    if (result.containsKey("content") && result.get("content") != null) {
                        System.out.println("✅ Strategy 2 (no auth) succeeded");
                        return Mono.just(result);
                    }
                    System.out.println("⚠️ Strategy 2 failed, trying strategy 3");
                    return strategy3;
                })
                .flatMap(result -> {
                    if (result.containsKey("content") && result.get("content") != null) {
                        System.out.println("✅ Strategy 3 (minimal) succeeded");
                        return Mono.just(result);
                    }
                    System.out.println("❌ All AWS strategies failed");
                    return Mono.just(baseResult);
                })
                .onErrorReturn(baseResult);
    }

    // Helper method to try a download strategy
    private Mono<Map<String, Object>> tryDownloadWithStrategy(String url, String strategyName, Map<String, String> headers) {
        return WebClient.builder()
                .build()
                .get()
                .uri(url)
                .headers(httpHeaders -> headers.forEach(httpHeaders::add))
                .retrieve()
                .bodyToMono(String.class)
                .map(content -> {
                    System.out.println("✅ " + strategyName + " succeeded, content length: " + content.length());
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("content", content);
                    result.put("content_length", content.length());
                    result.put("download_method", strategyName);
                    return result;
                })
                .onErrorResume(e -> {
                    System.out.println("⚠️ " + strategyName + " failed: " + e.getMessage());
                    return Mono.empty();
                })
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> {
                    System.out.println("⏱️ " + strategyName + " timeout");
                    return Mono.empty();
                });
    }

    // DELETE THIS DUPLICATE METHOD - Remove the old downloadTranscriptContent method
    /*
    public Mono<Map<String, Object>> downloadTranscriptContent(String meetingId) {
        // DELETE THIS ENTIRE METHOD - it's causing conflicts
    }
    */

    // Helper method to create error response
    private Map<String, Object> createErrorResponse(String error) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", error);
        errorResponse.put("transcript_available", false);
        return errorResponse;
    }

    // Rest of your existing methods for webinars, etc...
    
    // Get User's Webinars
    public Mono<ZoomWebinarsResponse> getWebinars(String accessToken) {
        return webClient.get()
                .uri("https://api.zoom.us/v2/users/me/webinars")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(ZoomWebinarsResponse.class);
    }

    // Get Webinar Participants with PAGINATION
    public Mono<ParticipantsResponse> getWebinarParticipants(String accessToken, String webinarId) {
        System.out.println("🚀 Starting pagination for webinar: " + webinarId);
        return getAllWebinarParticipants(accessToken, webinarId, null)
                .collectList()
                .map(participantsLists -> {
                    ParticipantsResponse combinedResponse = new ParticipantsResponse();
                    List<Participant> allParticipants = new ArrayList<>();
                    
                    for (ParticipantsResponse response : participantsLists) {
                        if (response.getParticipants() != null) {
                            allParticipants.addAll(response.getParticipants());
                        }
                    }
                    
                    combinedResponse.setParticipants(allParticipants);
                    combinedResponse.setTotalRecords(allParticipants.size());
                    System.out.println("✅ Pagination complete! Total participants: " + allParticipants.size());
                    return combinedResponse;
                });
    }

    // Recursive method to get all pages of webinar participants
    private Flux<ParticipantsResponse> getAllWebinarParticipants(String accessToken, String webinarId, String nextPageToken) {
        String uri = "https://api.zoom.us/v2/past_webinars/" + webinarId + "/participants";
        if (nextPageToken != null && !nextPageToken.isEmpty()) {
            uri += "?next_page_token=" + nextPageToken;
        }
        
        System.out.println("🎯 Fetching URL: " + uri);
        
        return webClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(ParticipantsResponse.class)
                .doOnNext(response -> debugPagination(response, nextPageToken))
                .flatMapMany(response -> {
                    if (response.getNextPageToken() != null && !response.getNextPageToken().isEmpty()) {
                        System.out.println("🔄 Found next page token: " + response.getNextPageToken());
                        // If there's a next page, recursively fetch it
                        return Flux.concat(
                                Flux.just(response),
                                getAllWebinarParticipants(accessToken, webinarId, response.getNextPageToken())
                        );
                    } else {
                        System.out.println("✅ No more pages - pagination complete");
                        // No more pages
                        return Flux.just(response);
                    }
                });
    }

    // Get All Webinars with Basic Info
    public Mono<Map<String, Object>> getAllWebinars() {
        return getAccessToken()
                .flatMap(authResponse -> getWebinars(authResponse.getAccessToken()))
                .map(webinarsResponse -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("total_webinars", webinarsResponse.getTotalRecords());
                    
                    if (webinarsResponse.getWebinars() != null && !webinarsResponse.getWebinars().isEmpty()) {
                        result.put("webinars", webinarsResponse.getWebinars());
                        
                        // Count upcoming vs completed
                        long upcoming = webinarsResponse.getWebinars().stream()
                                .filter(webinar -> webinar.getStartTime().isAfter(java.time.LocalDateTime.now()))
                                .count();
                        long completed = webinarsResponse.getWebinars().size() - upcoming;
                        
                        result.put("upcoming", upcoming);
                        result.put("completed", completed);
                        
                        // Add webinar summaries for quick overview
                        List<Map<String, Object>> webinarSummaries = new ArrayList<>();
                        for (ZoomWebinar webinar : webinarsResponse.getWebinars()) {
                            Map<String, Object> summary = new HashMap<>();
                            summary.put("id", webinar.getId());
                            summary.put("topic", webinar.getTopic());
                            summary.put("start_time", webinar.getStartTime());
                            summary.put("duration", webinar.getDuration());
                            summary.put("join_url", webinar.getJoinUrl());
                            summary.put("type", webinar.getType());
                            webinarSummaries.add(summary);
                        }
                        result.put("webinar_summaries", webinarSummaries);
                    } else {
                        result.put("webinars", Collections.emptyList());
                        result.put("upcoming", 0);
                        result.put("completed", 0);
                        result.put("webinar_summaries", Collections.emptyList());
                        result.put("message", "No webinars found");
                    }
                    
                    return result;
                })
                .onErrorResume(e -> {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "Failed to fetch webinars: " + e.getMessage());
                    return Mono.just(errorResponse);
                });
    }

    // Get Webinar Analytics with REAL-TIME tracking - UPDATED to accept interval
    public Mono<Map<String, Object>> getWebinarAnalytics(String webinarId, Integer intervalMinutes) {
        int interval = intervalMinutes != null ? intervalMinutes : 5; // Default to 5 minutes
        
        return getAccessToken()
                .flatMap(authResponse -> {
                    // Get webinar participants first
                    return getWebinarParticipants(authResponse.getAccessToken(), webinarId)
                            .flatMap(participantsResponse -> {
                                // Get webinar duration
                                return getWebinarDuration(webinarId, authResponse.getAccessToken())
                                        .map(webinarDuration -> {
                                            Map<String, Object> analytics = new HashMap<>();
                                            Map<String, Object> engagementData = calculateEngagementMetrics(participantsResponse, webinarDuration, interval);
                                            
                                            analytics.put("webinar_id", webinarId);
                                            analytics.put("success", true);
                                            analytics.put("interval_minutes", interval);
                                            analytics.put("webinar_duration", webinarDuration);
                                            analytics.put("total_participants", engagementData.get("total_participants"));
                                            analytics.put("engagement_metrics", engagementData);
                                            analytics.put("engagement_graph", engagementData.get("engagement_over_time"));
                                            analytics.put("participant_details", engagementData.get("participant_details"));
                                            analytics.put("user_timelines", engagementData.get("user_timelines"));
                                            analytics.put("message", "Real participant data analyzed with real-time join/leave tracking");
                                            analytics.put("data_source", "zoom_api");
                                            
                                            // Add real-time specific metrics
                                            analytics.put("peak_concurrent_users", engagementData.get("peak_concurrent_users"));
                                            analytics.put("final_active_users", engagementData.get("final_active_users"));
                                            analytics.put("total_joined", engagementData.get("total_joined"));
                                            analytics.put("total_left", engagementData.get("total_left"));
                                            
                                            return analytics;
                                        });
                            })
                            .onErrorResume(e -> {
                                System.err.println("❌ Error getting real webinar data: " + e.getMessage());
                                return generateSimulatedWebinarAnalytics(webinarId, interval);
                            });
                })
                .onErrorResume(e -> {
                    System.err.println("❌ Error in webinar analytics: " + e.getMessage());
                    return generateSimulatedWebinarAnalytics(webinarId, interval);
                });
    }

    // Helper method to get webinar duration
    private Mono<Integer> getWebinarDuration(String webinarId, String accessToken) {
        return webClient.get()
                .uri("https://api.zoom.us/v2/webinars/" + webinarId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(ZoomWebinar.class)
                .map(ZoomWebinar::getDuration)
                .onErrorReturn(180); // Default to 3 hours if unable to fetch
    }

    // Generate simulated analytics for webinars - UPDATED to accept interval
    private Mono<Map<String, Object>> generateSimulatedWebinarAnalytics(String webinarId, int intervalMinutes) {
        return getAccessToken()
                .flatMap(authResponse -> getWebinars(authResponse.getAccessToken()))
                .map(webinarsResponse -> {
                    Map<String, Object> analytics = new HashMap<>();
                    
                    // Find the webinar to get basic info
                    Optional<ZoomWebinar> webinarOpt = webinarsResponse.getWebinars().stream()
                            .filter(webinar -> webinarId.equals(webinar.getId().toString()))
                            .findFirst();
                    
                    if (webinarOpt.isPresent()) {
                        ZoomWebinar webinar = webinarOpt.get();
                        Map<String, Object> simulatedData = createSimulatedWebinarEngagementData(webinar, intervalMinutes);
                        
                        analytics.put("webinar_id", webinarId);
                        analytics.put("success", true);
                        analytics.put("interval_minutes", intervalMinutes);
                        analytics.put("webinar_topic", webinar.getTopic());
                        analytics.put("webinar_duration", webinar.getDuration());
                        analytics.put("webinar_start_time", webinar.getStartTime());
                        analytics.put("total_participants", simulatedData.get("total_participants"));
                        analytics.put("engagement_metrics", simulatedData);
                        analytics.put("engagement_graph", simulatedData.get("engagement_over_time"));
                        analytics.put("participant_details", simulatedData.get("participant_details"));
                        analytics.put("user_timelines", simulatedData.get("user_timelines"));
                        analytics.put("message", "Simulated analytics (real participant data not available)");
                        analytics.put("data_source", "simulated");
                        analytics.put("note", "Real participant data is only available for recent webinars via Zoom API");
                    } else {
                        analytics.put("success", false);
                        analytics.put("error", "Webinar not found");
                        analytics.put("webinar_id", webinarId);
                    }
                    
                    return analytics;
                })
                .onErrorResume(e -> {
                    Map<String, Object> basicAnalytics = createBasicSimulatedWebinarData(webinarId, intervalMinutes);
                    return Mono.just(basicAnalytics);
                });
    }

    // Create realistic simulated engagement data for webinars - UPDATED to accept interval
    private Map<String, Object> createSimulatedWebinarEngagementData(ZoomWebinar webinar, int intervalMinutes) {
        Map<String, Object> engagementData = new HashMap<>();
        
        // Webinars typically have more participants than meetings
        int baseParticipants = 50 + new Random(webinar.getId().hashCode()).nextInt(100);
        int webinarDuration = webinar.getDuration();
        
        // Generate DYNAMIC time bins based on interval
        List<String> timeLabels = generateDynamicTimeBins(webinarDuration, intervalMinutes);
        int segments = timeLabels.size();
        
        List<Integer> activeParticipants = new ArrayList<>();
        List<Integer> engagementRates = new ArrayList<>();
        List<Integer> usersJoined = new ArrayList<>();
        List<Integer> usersLeft = new ArrayList<>();
        List<Integer> peakActiveUsers = new ArrayList<>();
        List<Map<String, Object>> userTimelines = new ArrayList<>();
        List<Map<String, Object>> participantDetails = new ArrayList<>();
        
        for (int i = 0; i < segments; i++) {
            // Simulate natural drop-off (webinars have higher retention)
            double retention = 1.0 - (i * 0.05); // 5% drop per segment for webinars
            int active = (int) (baseParticipants * retention);
            activeParticipants.add(Math.max(active, 1));
            
            int engagementRate = 100 - (i * 5); // 5% engagement drop per segment
            engagementRates.add(Math.max(engagementRate, 30));
            
            // Simulate join/leave patterns for webinars
            usersJoined.add(i == 0 ? baseParticipants : new Random().nextInt(10));
            usersLeft.add(i == 0 ? 0 : new Random().nextInt(15));
            peakActiveUsers.add(active + new Random().nextInt(5));
        }
        
        // Create simulated participant details for webinars
        for (int i = 0; i < baseParticipants; i++) {
            Map<String, Object> participantDetail = new HashMap<>();
            participantDetail.put("user_id", "webinar_user_" + i);
            participantDetail.put("name", "Webinar User " + (i + 1));
            participantDetail.put("email", "webinar" + (i + 1) + "@example.com");
            participantDetail.put("join_time", LocalDateTime.now().minusMinutes(webinarDuration).format(ZOOM_TIME_FORMATTER));
            participantDetail.put("leave_time", LocalDateTime.now().format(ZOOM_TIME_FORMATTER));
            participantDetail.put("duration_seconds", webinarDuration * 60);
            participantDetail.put("duration_minutes", webinarDuration);
            participantDetail.put("attentiveness_score", "");
            participantDetails.add(participantDetail);
            
            Map<String, Object> userTimeline = new HashMap<>();
            userTimeline.put("user_id", "webinar_user_" + i);
            userTimeline.put("name", "Webinar User " + (i + 1));
            userTimeline.put("email", "webinar" + (i + 1) + "@example.com");
            userTimeline.put("join_time", LocalDateTime.now().minusMinutes(webinarDuration).format(ZOOM_TIME_FORMATTER));
            userTimeline.put("leave_time", LocalDateTime.now().format(ZOOM_TIME_FORMATTER));
            userTimeline.put("duration_minutes", webinarDuration);
            userTimeline.put("join_minute", 0);
            userTimeline.put("leave_minute", webinarDuration - 1);
            
            // Create presence array
            List<Integer> presenceBySegment = new ArrayList<>();
            for (int seg = 0; seg < segments; seg++) {
                presenceBySegment.add(1); // All present in simulation
            }
            userTimeline.put("presence_by_segment", presenceBySegment);
            userTimelines.add(userTimeline);
        }
        
        // Calculate metrics
        int totalDuration = webinarDuration * baseParticipants;
        double averageDuration = webinarDuration * 0.8; // 80% average attendance for webinars
        
        Map<String, Object> engagementGraph = new HashMap<>();
        engagementGraph.put("labels", timeLabels);
        engagementGraph.put("active_participants", activeParticipants);
        engagementGraph.put("engagement_rate", engagementRates);
        engagementGraph.put("users_joined", usersJoined);
        engagementGraph.put("users_left", usersLeft);
        engagementGraph.put("peak_active_users", peakActiveUsers);
        engagementGraph.put("user_timelines", userTimelines);
        
        engagementData.put("total_participants", baseParticipants);
        engagementData.put("average_participation_minutes", Math.round(averageDuration * 100.0) / 100.0);
        engagementData.put("max_participation_minutes", webinarDuration);
        engagementData.put("min_participation_minutes", 10);
        engagementData.put("total_meeting_minutes", totalDuration);
        engagementData.put("engagement_score", 80 + new Random().nextInt(15));
        engagementData.put("attention_retention", Math.round((activeParticipants.get(activeParticipants.size()-1) / (double)baseParticipants) * 100));
        engagementData.put("engagement_over_time", engagementGraph);
        engagementData.put("participant_details", participantDetails);
        engagementData.put("user_timelines", userTimelines);
        engagementData.put("peak_concurrent_users", baseParticipants);
        engagementData.put("final_active_users", activeParticipants.get(activeParticipants.size()-1));
        engagementData.put("total_joined", baseParticipants);
        engagementData.put("total_left", baseParticipants - activeParticipants.get(activeParticipants.size()-1));
        
        return engagementData;
    }

    private Map<String, Object> createBasicSimulatedWebinarData(String webinarId, int intervalMinutes) {
        Map<String, Object> analytics = new HashMap<>();
        Map<String, Object> engagementData = new HashMap<>();
        
        // Use DYNAMIC time bins for 180-minute webinar
        List<String> timeLabels = generateDynamicTimeBins(180, intervalMinutes);
        
        // Generate data for each segment
        List<Integer> activeParticipants = new ArrayList<>();
        List<Integer> engagementRates = new ArrayList<>();
        List<Integer> usersJoined = new ArrayList<>();
        List<Integer> usersLeft = new ArrayList<>();
        List<Integer> peakActiveUsers = new ArrayList<>();
        List<Map<String, Object>> userTimelines = new ArrayList<>();
        List<Map<String, Object>> participantDetails = new ArrayList<>();
        
        Random random = new Random(webinarId.hashCode());
        int baseParticipants = 85;
        
        for (int i = 0; i < timeLabels.size(); i++) {
            // Simulate natural drop-off
            double retention = 1.0 - (i * 0.03);
            int active = (int) (baseParticipants * retention);
            activeParticipants.add(Math.max(active, 1));
            
            int engagementRate = 100 - (i * 3);
            engagementRates.add(Math.max(engagementRate, 30));
            
            // Simulate join/leave patterns
            usersJoined.add(i == 0 ? baseParticipants : random.nextInt(5));
            usersLeft.add(i == 0 ? 0 : random.nextInt(8));
            peakActiveUsers.add(active + random.nextInt(3));
        }
        
        // Create basic participant details for webinars
        for (int i = 0; i < baseParticipants; i++) {
            Map<String, Object> participantDetail = new HashMap<>();
            participantDetail.put("user_id", "basic_webinar_user_" + i);
            participantDetail.put("name", "Basic Webinar User " + (i + 1));
            participantDetail.put("email", "basicwebinar" + (i + 1) + "@example.com");
            participantDetail.put("join_time", LocalDateTime.now().minusMinutes(180).format(ZOOM_TIME_FORMATTER));
            participantDetail.put("leave_time", LocalDateTime.now().minusMinutes(20).format(ZOOM_TIME_FORMATTER));
            participantDetail.put("duration_seconds", 9600);
            participantDetail.put("duration_minutes", 160);
            participantDetail.put("attentiveness_score", "");
            participantDetails.add(participantDetail);
        }
        
        Map<String, Object> engagementGraph = new HashMap<>();
        engagementGraph.put("labels", timeLabels);
        engagementGraph.put("active_participants", activeParticipants);
        engagementGraph.put("engagement_rate", engagementRates);
        engagementGraph.put("users_joined", usersJoined);
        engagementGraph.put("users_left", usersLeft);
        engagementGraph.put("peak_active_users", peakActiveUsers);
        engagementGraph.put("user_timelines", userTimelines);
        
        engagementData.put("total_participants", 85);
        engagementData.put("average_participation_minutes", 135.5);
        engagementData.put("max_participation_minutes", 180);
        engagementData.put("min_participation_minutes", 15);
        engagementData.put("total_meeting_minutes", 11517);
        engagementData.put("engagement_score", 85);
        engagementData.put("attention_retention", 70);
        engagementData.put("engagement_over_time", engagementGraph);
        engagementData.put("participant_details", participantDetails);
        engagementData.put("user_timelines", userTimelines);
        engagementData.put("peak_concurrent_users", 85);
        engagementData.put("final_active_users", 60);
        engagementData.put("total_joined", 85);
        engagementData.put("total_left", 25);
        
        analytics.put("webinar_id", webinarId);
        analytics.put("success", true);
        analytics.put("interval_minutes", intervalMinutes);
        analytics.put("total_participants", 85);
        analytics.put("engagement_metrics", engagementData);
        analytics.put("engagement_graph", engagementGraph);
        analytics.put("participant_details", participantDetails);
        analytics.put("user_timelines", userTimelines);
        analytics.put("message", "Basic simulated webinar analytics (fallback data)");
        analytics.put("data_source", "basic_fallback");
        analytics.put("peak_concurrent_users", 85);
        analytics.put("final_active_users", 60);
        analytics.put("total_joined", 85);
        analytics.put("total_left", 25);
        
        return analytics;
    }
    
    // DEBUG METHOD - Add this to check pagination
    private void debugPagination(ParticipantsResponse response, String pageToken) {
        System.out.println("=== PAGINATION DEBUG ===");
        System.out.println("Page Token: " + pageToken);
        System.out.println("Participants in this page: " + (response.getParticipants() != null ? response.getParticipants().size() : 0));
        System.out.println("Total Records: " + response.getTotalRecords());
        System.out.println("Next Page Token: " + response.getNextPageToken());
        System.out.println("Page Size: " + response.getPageSize());
        System.out.println("Page Count: " + response.getPageCount());
        if (response.getParticipants() != null && !response.getParticipants().isEmpty()) {
            System.out.println("First participant: " + response.getParticipants().get(0).getName());
            System.out.println("Last participant: " + response.getParticipants().get(response.getParticipants().size()-1).getName());
        }
        System.out.println("========================");
    }
}
