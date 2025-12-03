package com.zoomdash;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class SimpleController {
    
    private final ZoomService zoomService;
    private final WebClient.Builder webClientBuilder;
    
    public SimpleController(ZoomService zoomService, WebClient.Builder webClientBuilder) {
        this.zoomService = zoomService;
        this.webClientBuilder = webClientBuilder;
    }
    
    // ========== COMPLETE DEBUGGING ENDPOINTS ==========
    
    // 1. COMPREHENSIVE DIAGNOSTIC ENDPOINT
    @GetMapping("/debug-transcript/{meetingId}")
    public Mono<Map<String, Object>> debugTranscriptComplete(@PathVariable String meetingId) {
        System.out.println("\n🔍 ========== COMPLETE TRANSCRIPT DEBUG START ==========");
        System.out.println("🎯 Meeting ID: " + meetingId);
        System.out.println("⏰ Timestamp: " + new Date());
        System.out.println("=======================================================\n");
        
        List<Map<String, Object>> debugSteps = new ArrayList<>();
        
        return zoomService.getAccessToken()
                .flatMap(authResponse -> {
                    // STEP 1: Check access token
                    Map<String, Object> step1 = new HashMap<>();
                    step1.put("step", 1);
                    step1.put("description", "Get Access Token");
                    step1.put("success", authResponse != null);
                    step1.put("token_type", authResponse.getTokenType());
                    step1.put("token_length", authResponse.getAccessToken() != null ? authResponse.getAccessToken().length() : 0);
                    step1.put("token_preview", authResponse.getAccessToken() != null ? 
                              authResponse.getAccessToken().substring(0, Math.min(20, authResponse.getAccessToken().length())) + "..." : "null");
                    step1.put("expires_in", authResponse.getExpiresIn());
                    debugSteps.add(step1);
                    
                    System.out.println("✅ STEP 1: Access Token Obtained");
                    System.out.println("   Token Type: " + authResponse.getTokenType());
                    System.out.println("   Token Preview: " + step1.get("token_preview"));
                    System.out.println("   Expires In: " + authResponse.getExpiresIn() + " seconds");
                    
                    // STEP 2: Get recordings list
                    return webClientBuilder.build()
                            .get()
                            .uri("https://api.zoom.us/v2/meetings/" + meetingId + "/recordings")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.getAccessToken())
                            .retrieve()
                            .bodyToMono(String.class) // Get raw JSON response
                            .flatMap(rawResponse -> {
                                Map<String, Object> step2 = new HashMap<>();
                                step2.put("step", 2);
                                step2.put("description", "Get Recordings List");
                                step2.put("success", rawResponse != null && !rawResponse.isEmpty());
                                step2.put("response_length", rawResponse.length());
                                step2.put("has_recordings", rawResponse.contains("recording_files"));
                                
                                // Try to parse to find transcript
                                boolean hasTranscript = rawResponse.contains("\"file_type\":\"TRANSCRIPT\"") || 
                                                       rawResponse.contains("\"file_type\":\"TRANSCRIPT\"") ||
                                                       rawResponse.contains("TRANSCRIPT");
                                step2.put("has_transcript", hasTranscript);
                                
                                // Find download URL in raw response
                                String downloadUrl = extractDownloadUrl(rawResponse);
                                step2.put("download_url_found", downloadUrl != null);
                                step2.put("download_url", downloadUrl);
                                
                                debugSteps.add(step2);
                                
                                System.out.println("\n✅ STEP 2: Recordings Response");
                                System.out.println("   Response Length: " + rawResponse.length() + " chars");
                                System.out.println("   Has Recordings: " + step2.get("has_recordings"));
                                System.out.println("   Has Transcript: " + step2.get("has_transcript"));
                                System.out.println("   Download URL Found: " + step2.get("download_url_found"));
                                if (downloadUrl != null) {
                                    System.out.println("   Download URL: " + downloadUrl);
                                }
                                
                                // STEP 3: If we have a download URL, test it
                                if (downloadUrl != null) {
                                    return testDownloadUrl(authResponse.getAccessToken(), downloadUrl, meetingId)
                                            .map(testResult -> {
                                                step2.put("download_test", testResult);
                                                System.out.println("\n✅ STEP 3: Download URL Test");
                                                System.out.println("   HTTP Status: " + testResult.get("http_status"));
                                                System.out.println("   Is Redirect: " + testResult.get("is_redirect"));
                                                if (testResult.get("redirect_location") != null) {
                                                    System.out.println("   Redirect URL: " + testResult.get("redirect_location"));
                                                }
                                                System.out.println("   Error: " + testResult.get("error"));
                                                
                                                // STEP 4: Build final diagnostic result
                                                Map<String, Object> finalResult = buildDiagnosticResult(meetingId, debugSteps, rawResponse);
                                                return finalResult;
                                            });
                                } else {
                                    // No download URL found
                                    Map<String, Object> step3 = new HashMap<>();
                                    step3.put("step", 3);
                                    step3.put("description", "No Download URL Found");
                                    step3.put("success", false);
                                    step3.put("error", "No transcript download URL found in recordings response");
                                    debugSteps.add(step3);
                                    
                                    System.out.println("\n❌ STEP 3: No Download URL Found");
                                    System.out.println("   Error: No transcript download URL found in recordings response");
                                    
                                    Map<String, Object> finalResult = buildDiagnosticResult(meetingId, debugSteps, rawResponse);
                                    return Mono.just(finalResult);
                                }
                            })
                            .onErrorResume(e -> {
                                Map<String, Object> step2Error = new HashMap<>();
                                step2Error.put("step", 2);
                                step2Error.put("description", "Get Recordings List - ERROR");
                                step2Error.put("success", false);
                                step2Error.put("error", e.getMessage());
                                step2Error.put("error_type", e.getClass().getName());
                                debugSteps.add(step2Error);
                                
                                System.out.println("\n❌ STEP 2 ERROR: " + e.getMessage());
                                e.printStackTrace();
                                
                                Map<String, Object> errorResult = new HashMap<>();
                                errorResult.put("meeting_id", meetingId);
                                errorResult.put("success", false);
                                errorResult.put("error", "Failed to get recordings: " + e.getMessage());
                                errorResult.put("debug_steps", debugSteps);
                                errorResult.put("timestamp", new Date().toString());
                                return Mono.just(errorResult);
                            });
                })
                .onErrorResume(e -> {
                    System.out.println("\n❌ STEP 1 ERROR: Failed to get access token");
                    e.printStackTrace();
                    
                    Map<String, Object> step1Error = new HashMap<>();
                    step1Error.put("step", 1);
                    step1Error.put("description", "Get Access Token - ERROR");
                    step1Error.put("success", false);
                    step1Error.put("error", e.getMessage());
                    step1Error.put("error_type", e.getClass().getName());
                    debugSteps.add(step1Error);
                    
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("meeting_id", meetingId);
                    errorResult.put("success", false);
                    errorResult.put("error", "Failed to get access token: " + e.getMessage());
                    errorResult.put("debug_steps", debugSteps);
                    errorResult.put("timestamp", new Date().toString());
                    return Mono.just(errorResult);
                });
    }
    
    
 // NEW: SIMPLE AND RELIABLE TRANSCRIPT ENDPOINT
    @GetMapping("/transcript-direct/{meetingId}")
    public Mono<Map<String, Object>> getTranscriptDirect(@PathVariable String meetingId) {
        System.out.println("🎯 DIRECT TRANSCRIPT for: " + meetingId);
        
        return zoomService.getAccessToken()
                .flatMap(authResponse -> {
                    System.out.println("✅ Got access token");
                    
                    // Get recordings to find transcript
                    return webClientBuilder.build()
                            .get()
                            .uri("https://api.zoom.us/v2/meetings/" + meetingId + "/recordings")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.getAccessToken())
                            .retrieve()
                            .bodyToMono(String.class)
                            .flatMap(rawResponse -> {
                                System.out.println("📥 Got recordings response: " + rawResponse.length() + " chars");
                                
                                // Find transcript URL in response
                                String downloadUrl = extractDownloadUrl(rawResponse);
                                if (downloadUrl == null) {
                                    System.out.println("❌ No transcript download URL found");
                                    Map<String, Object> error = new HashMap<>();
                                    error.put("success", false);
                                    error.put("error", "No transcript download URL found");
                                    error.put("meeting_id", meetingId);
                                    error.put("raw_response_preview", rawResponse.substring(0, Math.min(500, rawResponse.length())));
                                    return Mono.just(error);
                                }
                                
                                System.out.println("🔗 Found transcript URL: " + downloadUrl);
                                
                                // Try to download the transcript content
                                return webClientBuilder.build()
                                        .get()
                                        .uri(downloadUrl)
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.getAccessToken())
                                        .header(HttpHeaders.ACCEPT, "text/vtt, text/plain, */*")
                                        .retrieve()
                                        .bodyToMono(String.class)
                                        .map(content -> {
                                            System.out.println("✅ Downloaded transcript content: " + content.length() + " chars");
                                            
                                            Map<String, Object> result = new HashMap<>();
                                            result.put("success", true);
                                            result.put("meeting_id", meetingId);
                                            result.put("content", content);
                                            result.put("content_length", content.length());
                                            result.put("download_url", downloadUrl);
                                            result.put("has_content", !content.trim().isEmpty());
                                            result.put("transcript_available", true);
                                            
                                            return result;
                                        })
                                        .onErrorResume(e -> {
                                            System.out.println("❌ Error downloading transcript: " + e.getMessage());
                                            
                                            // Return at least the URL
                                            Map<String, Object> fallback = new HashMap<>();
                                            fallback.put("success", true);
                                            fallback.put("meeting_id", meetingId);
                                            fallback.put("download_url", downloadUrl);
                                            fallback.put("transcript_available", true);
                                            fallback.put("error", "Could not download content but URL is available");
                                            fallback.put("message", "Use download_url to get transcript");
                                            return Mono.just(fallback);
                                        });
                            });
                })
                .onErrorResume(e -> {
                    System.out.println("❌ Error in direct transcript endpoint: " + e.getMessage());
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("error", e.getMessage());
                    error.put("meeting_id", meetingId);
                    return Mono.just(error);
                })
                .timeout(Duration.ofSeconds(30));
    }
    
    
    
    // 2. DIRECT DOWNLOAD TEST ENDPOINT
    @GetMapping("/debug-download/{meetingId}")
    public Mono<Map<String, Object>> debugDirectDownload(@PathVariable String meetingId) {
        System.out.println("\n⬇️ ========== DIRECT DOWNLOAD DEBUG ==========");
        System.out.println("🎯 Meeting ID: " + meetingId);
        
        return zoomService.getAccessToken()
                .flatMap(authResponse -> {
                    System.out.println("✅ Got access token");
                    
                    // Step 1: Get the recordings
                    return webClientBuilder.build()
                            .get()
                            .uri("https://api.zoom.us/v2/meetings/" + meetingId + "/recordings")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.getAccessToken())
                            .retrieve()
                            .bodyToMono(String.class)
                            .flatMap(rawResponse -> {
                                System.out.println("📥 Got recordings response: " + rawResponse.length() + " chars");
                                
                                // Extract download URL
                                String downloadUrl = extractDownloadUrl(rawResponse);
                                if (downloadUrl == null) {
                                    System.out.println("❌ No download URL found in response");
                                    Map<String, Object> error = new HashMap<>();
                                    error.put("success", false);
                                    error.put("error", "No download URL found");
                                    error.put("raw_response_preview", rawResponse.substring(0, Math.min(500, rawResponse.length())));
                                    return Mono.just(error);
                                }
                                
                                System.out.println("🔗 Download URL: " + downloadUrl);
                                
                                // Step 2: Try HEAD request to check URL
                                return webClientBuilder.build()
                                        .head()
                                        .uri(downloadUrl)
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.getAccessToken())
                                        .header(HttpHeaders.USER_AGENT, "Zoom-Debug-Tool/1.0")
                                        .exchangeToMono(response -> {
                                            System.out.println("📊 HEAD Response Status: " + response.statusCode());
                                            
                                            Map<String, Object> result = new HashMap<>();
                                            result.put("success", true);
                                            result.put("meeting_id", meetingId);
                                            result.put("download_url", downloadUrl);
                                            result.put("head_status", response.statusCode().value());
                                            
                                            // Check headers
                                            Map<String, String> headers = new HashMap<>();
                                            response.headers().asHttpHeaders().forEach((key, values) -> {
                                                if (values != null && !values.isEmpty()) {
                                                    headers.put(key, values.get(0));
                                                }
                                            });
                                            result.put("headers", headers);
                                            
                                            // Check for redirect
                                            if (response.statusCode().is3xxRedirection()) {
                                                String redirectUrl = response.headers().header(HttpHeaders.LOCATION).stream()
                                                        .findFirst()
                                                        .orElse(null);
                                                if (redirectUrl != null) {
                                                    System.out.println("🔄 Redirect URL: " + redirectUrl);
                                                    result.put("redirect_url", redirectUrl);
                                                    result.put("is_redirect", true);
                                                    
                                                    // Try to follow redirect
                                                    return attemptFollowRedirect(redirectUrl, meetingId)
                                                            .map(redirectResult -> {
                                                                result.put("redirect_result", redirectResult);
                                                                return result;
                                                            });
                                                }
                                            }
                                            
                                            // If not redirect, try actual GET
                                            if (response.statusCode().is2xxSuccessful()) {
                                                return attemptDirectDownload(downloadUrl, authResponse.getAccessToken(), meetingId)
                                                        .map(downloadResult -> {
                                                            result.put("download_result", downloadResult);
                                                            return result;
                                                        });
                                            }
                                            
                                            return Mono.just(result);
                                        })
                                        .onErrorResume(e -> {
                                            System.out.println("❌ HEAD request error: " + e.getMessage());
                                            Map<String, Object> error = new HashMap<>();
                                            error.put("success", false);
                                            error.put("error", "HEAD request failed: " + e.getMessage());
                                            error.put("meeting_id", meetingId);
                                            return Mono.just(error);
                                        });
                            });
                })
                .onErrorResume(e -> {
                    System.out.println("❌ Overall error: " + e.getMessage());
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("error", e.getMessage());
                    error.put("meeting_id", meetingId);
                    return Mono.just(error);
                })
                .timeout(Duration.ofSeconds(30));
    }
    
    // 3. MANUAL DOWNLOAD TEST WITH DIFFERENT STRATEGIES
    @GetMapping("/debug-manual-download/{meetingId}")
    public Mono<Map<String, Object>> debugManualDownload(@PathVariable String meetingId) {
        System.out.println("\n🛠️ ========== MANUAL DOWNLOAD DEBUG ==========");
        
        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put("meeting_id", meetingId);
        finalResult.put("timestamp", new Date().toString());
        finalResult.put("strategies_tried", new ArrayList<Map<String, Object>>());
        
        return zoomService.getAccessToken()
                .flatMap(authResponse -> {
                    System.out.println("✅ Step 1: Got access token");
                    
                    // Get recordings to find download URL
                    return webClientBuilder.build()
                            .get()
                            .uri("https://api.zoom.us/v2/meetings/" + meetingId + "/recordings")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.getAccessToken())
                            .retrieve()
                            .bodyToMono(String.class)
                            .flatMap(rawResponse -> {
                                System.out.println("✅ Step 2: Got recordings, length: " + rawResponse.length());
                                
                                String downloadUrl = extractDownloadUrl(rawResponse);
                                if (downloadUrl == null) {
                                    System.out.println("❌ No download URL found");
                                    finalResult.put("success", false);
                                    finalResult.put("error", "No download URL found");
                                    return Mono.just(finalResult);
                                }
                                
                                System.out.println("🔗 Download URL: " + downloadUrl);
                                finalResult.put("download_url", downloadUrl);
                                
                                // Try Strategy 1: Direct download with auth
                                System.out.println("\n🔄 Strategy 1: Direct download with auth");
                                return tryDownloadStrategy(downloadUrl, "Bearer " + authResponse.getAccessToken(), 
                                        "Strategy 1 - With Auth", meetingId)
                                        .flatMap(strategy1Result -> {
                                            ((List<Map<String, Object>>) finalResult.get("strategies_tried")).add(strategy1Result);
                                            
                                            if (strategy1Result.get("success").equals(true) && strategy1Result.get("content") != null) {
                                                System.out.println("✅ Strategy 1 SUCCESS!");
                                                finalResult.put("success", true);
                                                finalResult.put("content", strategy1Result.get("content"));
                                                finalResult.put("content_length", strategy1Result.get("content_length"));
                                                finalResult.put("winning_strategy", "Strategy 1 - With Auth");
                                                return Mono.just(finalResult);
                                            }
                                            
                                            // Strategy 2: Try with different accept headers
                                            System.out.println("\n🔄 Strategy 2: Different accept headers");
                                            return tryDownloadStrategy(downloadUrl, "Bearer " + authResponse.getAccessToken(), 
                                                    "Strategy 2 - Different Headers", meetingId, Map.of(
                                                            HttpHeaders.ACCEPT, "application/json, text/plain, */*",
                                                            HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                                                    ))
                                                    .flatMap(strategy2Result -> {
                                                        ((List<Map<String, Object>>) finalResult.get("strategies_tried")).add(strategy2Result);
                                                        
                                                        if (strategy2Result.get("success").equals(true) && strategy2Result.get("content") != null) {
                                                            System.out.println("✅ Strategy 2 SUCCESS!");
                                                            finalResult.put("success", true);
                                                            finalResult.put("content", strategy2Result.get("content"));
                                                            finalResult.put("content_length", strategy2Result.get("content_length"));
                                                            finalResult.put("winning_strategy", "Strategy 2 - Different Headers");
                                                            return Mono.just(finalResult);
                                                        }
                                                        
                                                        // Strategy 3: Try without auth (for AWS signed URLs)
                                                        System.out.println("\n🔄 Strategy 3: Without auth");
                                                        return tryDownloadStrategy(downloadUrl, null, 
                                                                "Strategy 3 - No Auth", meetingId, Map.of(
                                                                        HttpHeaders.USER_AGENT, "Mozilla/5.0",
                                                                        HttpHeaders.ACCEPT, "*/*"
                                                                ))
                                                                .flatMap(strategy3Result -> {
                                                                    ((List<Map<String, Object>>) finalResult.get("strategies_tried")).add(strategy3Result);
                                                                    
                                                                    if (strategy3Result.get("success").equals(true) && strategy3Result.get("content") != null) {
                                                                        System.out.println("✅ Strategy 3 SUCCESS!");
                                                                        finalResult.put("success", true);
                                                                        finalResult.put("content", strategy3Result.get("content"));
                                                                        finalResult.put("content_length", strategy3Result.get("content_length"));
                                                                        finalResult.put("winning_strategy", "Strategy 3 - No Auth");
                                                                        return Mono.just(finalResult);
                                                                    }
                                                                    
                                                                    // All strategies failed
                                                                    System.out.println("❌ All strategies failed");
                                                                    finalResult.put("success", false);
                                                                    finalResult.put("error", "All download strategies failed");
                                                                    return Mono.just(finalResult);
                                                                });
                                                    });
                                        });
                            });
                })
                .onErrorResume(e -> {
                    System.out.println("❌ Manual download error: " + e.getMessage());
                    finalResult.put("success", false);
                    finalResult.put("error", e.getMessage());
                    return Mono.just(finalResult);
                });
    }
    
    // 4. CHECK RAW RESPONSE ENDPOINT
    @GetMapping("/debug-raw/{meetingId}")
    public Mono<Map<String, Object>> debugRawResponse(@PathVariable String meetingId) {
        System.out.println("\n📄 ========== RAW RESPONSE DEBUG ==========");
        
        return zoomService.getAccessToken()
                .flatMap(authResponse -> {
                    return webClientBuilder.build()
                            .get()
                            .uri("https://api.zoom.us/v2/meetings/" + meetingId + "/recordings")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.getAccessToken())
                            .retrieve()
                            .bodyToMono(String.class)
                            .map(rawResponse -> {
                                System.out.println("✅ Got raw response, length: " + rawResponse.length());
                                
                                Map<String, Object> result = new HashMap<>();
                                result.put("success", true);
                                result.put("meeting_id", meetingId);
                                result.put("response_length", rawResponse.length());
                                
                                // Analyze response
                                boolean hasRecordingFiles = rawResponse.contains("recording_files");
                                boolean hasTranscript = rawResponse.contains("TRANSCRIPT") || 
                                                       rawResponse.contains("\"file_type\":\"TRANSCRIPT\"");
                                
                                result.put("has_recording_files", hasRecordingFiles);
                                result.put("has_transcript", hasTranscript);
                                
                                // Extract important parts
                                String downloadUrl = extractDownloadUrl(rawResponse);
                                result.put("download_url_found", downloadUrl != null);
                                result.put("download_url", downloadUrl);
                                
                                // Get response preview
                                int previewLength = Math.min(1000, rawResponse.length());
                                result.put("response_preview", rawResponse.substring(0, previewLength));
                                
                                // Find all file types
                                List<String> fileTypes = extractFileTypes(rawResponse);
                                result.put("file_types_found", fileTypes);
                                
                                System.out.println("📊 Analysis:");
                                System.out.println("   Has Recording Files: " + hasRecordingFiles);
                                System.out.println("   Has Transcript: " + hasTranscript);
                                System.out.println("   Download URL Found: " + (downloadUrl != null));
                                System.out.println("   File Types Found: " + fileTypes);
                                
                                return result;
                            });
                })
                .onErrorResume(e -> {
                    System.out.println("❌ Raw response error: " + e.getMessage());
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("error", e.getMessage());
                    error.put("meeting_id", meetingId);
                    return Mono.just(error);
                });
    }
    
    // ========== NEW STREAMING ENDPOINTS ==========
    
    // 5. STREAM TRANSCRIPT ENDPOINT - Handles large files by streaming
    @GetMapping(value = "/transcript-stream/{meetingId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamTranscript(@PathVariable String meetingId) {
        System.out.println("🌊 STREAM TRANSCRIPT for: " + meetingId);
        
        return zoomService.getMeetingTranscript(meetingId)
                .flatMapMany(transcriptInfo -> {
                    if (!(Boolean) transcriptInfo.get("success")) {
                        return Flux.just(ServerSentEvent.<String>builder()
                                .data("No transcript available")
                                .event("error")
                                .build());
                    }
                    
                    String downloadUrl = (String) transcriptInfo.get("download_url");
                    System.out.println("🔗 Streaming from URL: " + downloadUrl);
                    
                    return zoomService.getAccessToken()
                            .flatMapMany(authResponse -> {
                                WebClient webClient = WebClient.builder()
                                        .codecs(configurer -> configurer
                                                .defaultCodecs()
                                                .maxInMemorySize(1024 * 1024)) // 1MB chunks
                                        .build();
                                
                                // First send a start event
                                Flux<ServerSentEvent<String>> startEvent = Flux.just(
                                    ServerSentEvent.<String>builder()
                                        .data("Starting transcript stream...")
                                        .event("start")
                                        .build()
                                );
                                
                                // Stream the transcript content
                                Flux<ServerSentEvent<String>> transcriptStream = webClient.get()
                                        .uri(downloadUrl)
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.getAccessToken())
                                        .header(HttpHeaders.ACCEPT, "text/vtt, text/plain, */*")
                                        .retrieve()
                                        .bodyToFlux(String.class)
                                        .map(chunk -> ServerSentEvent.<String>builder()
                                                .data(chunk)
                                                .event("chunk")
                                                .build())
                                        .doOnNext(chunk -> System.out.println("📦 Sent chunk: " + chunk.data().length() + " chars"))
                                        .doOnComplete(() -> System.out.println("✅ Stream complete"))
                                        .onErrorResume(e -> {
                                            System.out.println("❌ Stream error: " + e.getMessage());
                                            return Flux.just(ServerSentEvent.<String>builder()
                                                    .data("Stream error: " + e.getMessage())
                                                    .event("error")
                                                    .build());
                                        });
                                
                                // Combine start event with transcript stream
                                return startEvent.concatWith(transcriptStream);
                            });
                })
                .onErrorResume(e -> {
                    System.out.println("❌ Initial stream setup error: " + e.getMessage());
                    return Flux.just(ServerSentEvent.<String>builder()
                            .data("Setup error: " + e.getMessage())
                            .event("error")
                            .build());
                })
                .timeout(Duration.ofSeconds(120)); // 2 minute timeout for streaming
    }
    
    // 6. SIMPLE STREAMING - Returns plain text stream (easier for frontend)
    @GetMapping(value = "/transcript-stream-simple/{meetingId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<ResponseEntity<Flux<String>>> streamTranscriptSimple(@PathVariable String meetingId) {
        System.out.println("🌊 SIMPLE STREAM TRANSCRIPT for: " + meetingId);
        
        return zoomService.getMeetingTranscript(meetingId)
                .flatMap(transcriptInfo -> {
                    if (!(Boolean) transcriptInfo.get("success")) {
                        return Mono.just(ResponseEntity.badRequest()
                                .body(Flux.just("ERROR: No transcript available")));
                    }
                    
                    String downloadUrl = (String) transcriptInfo.get("download_url");
                    System.out.println("🔗 Simple streaming from URL: " + downloadUrl);
                    
                    return zoomService.getAccessToken()
                            .flatMap(authResponse -> {
                                WebClient webClient = WebClient.builder()
                                        .codecs(configurer -> configurer
                                                .defaultCodecs()
                                                .maxInMemorySize(5 * 1024 * 1024)) // 5MB max buffer
                                        .build();
                                
                                Flux<String> stream = webClient.get()
                                        .uri(downloadUrl)
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.getAccessToken())
                                        .header(HttpHeaders.ACCEPT, "text/vtt, text/plain, */*")
                                        .retrieve()
                                        .bodyToFlux(String.class)
                                        .doOnSubscribe(s -> System.out.println("▶️ Starting transcript stream"))
                                        .doOnNext(chunk -> {
                                            if (chunk.length() > 0) {
                                                System.out.println("📦 Received chunk: " + chunk.length() + " chars");
                                            }
                                        })
                                        .doOnComplete(() -> System.out.println("✅ Transcript stream complete"))
                                        .doOnError(e -> System.out.println("❌ Stream error: " + e.getMessage()))
                                        .timeout(Duration.ofSeconds(90))
                                        .onErrorResume(e -> Flux.just("ERROR: " + e.getMessage()));
                                
                                return Mono.just(ResponseEntity.ok()
                                        .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8")
                                        .header("X-Transcript-Url", downloadUrl)
                                        .header("X-Streaming", "true")
                                        .body(stream));
                            });
                })
                .onErrorResume(e -> {
                    System.out.println("❌ Stream setup error: " + e.getMessage());
                    return Mono.just(ResponseEntity.status(500)
                            .body(Flux.just("ERROR: " + e.getMessage())));
                });
    }
    
    // ========== HELPER METHODS ==========
    
    private String extractDownloadUrl(String rawResponse) {
        try {
            System.out.println("🔍 Searching for transcript download URL...");
            
            // Parse JSON properly
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> responseMap = mapper.readValue(rawResponse, new TypeReference<Map<String, Object>>() {});
            
            // Get recording files
            List<Map<String, Object>> recordingFiles = (List<Map<String, Object>>) responseMap.get("recording_files");
            
            if (recordingFiles == null || recordingFiles.isEmpty()) {
                System.out.println("❌ No recording files found");
                return null;
            }
            
            // Look for transcript file
            for (Map<String, Object> file : recordingFiles) {
                String fileType = (String) file.get("file_type");
                System.out.println("📄 Found file type: " + fileType);
                
                if ("TRANSCRIPT".equals(fileType) || 
                    "transcript".equalsIgnoreCase(fileType) ||
                    (fileType != null && fileType.toUpperCase().contains("TRANSCRIPT"))) {
                    
                    String downloadUrl = (String) file.get("download_url");
                    System.out.println("✅ Found transcript! URL: " + downloadUrl);
                    return downloadUrl;
                }
            }
            
            System.out.println("❌ No transcript file found in recording files");
            
            // Debug: List all file types
            System.out.println("📋 All file types found:");
            recordingFiles.forEach(file -> {
                System.out.println("  - " + file.get("file_type") + " (" + file.get("file_extension") + ")");
            });
            
        } catch (Exception e) {
            System.out.println("⚠️ Error parsing JSON: " + e.getMessage());
            
            // Fallback: Try string search
            if (rawResponse.contains("\"file_type\":\"TRANSCRIPT\"")) {
                int start = rawResponse.indexOf("\"download_url\":\"", rawResponse.indexOf("\"file_type\":\"TRANSCRIPT\""));
                if (start > -1) {
                    start += 16;
                    int end = rawResponse.indexOf("\"", start);
                    if (end > start) {
                        String url = rawResponse.substring(start, end);
                        System.out.println("✅ Found transcript URL via fallback: " + url);
                        return url;
                    }
                }
            }
        }
        
        return null;
    }
    
    private List<String> extractFileTypes(String rawResponse) {
        List<String> fileTypes = new ArrayList<>();
        try {
            int index = 0;
            while ((index = rawResponse.indexOf("\"file_type\":\"", index)) != -1) {
                int start = index + 13;
                int end = rawResponse.indexOf("\"", start);
                if (end > start) {
                    String fileType = rawResponse.substring(start, end);
                    fileTypes.add(fileType);
                }
                index = end + 1;
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error extracting file types: " + e.getMessage());
        }
        return fileTypes;
    }
    
    private Mono<Map<String, Object>> testDownloadUrl(String accessToken, String downloadUrl, String meetingId) {
        return webClientBuilder.build()
                .head()
                .uri(downloadUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.USER_AGENT, "Zoom-Debug-Client/1.0")
                .exchangeToMono(response -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("http_status", response.statusCode().value());
                    result.put("is_redirect", response.statusCode().is3xxRedirection());
                    
                    if (response.statusCode().is3xxRedirection()) {
                        String redirectUrl = response.headers().header(HttpHeaders.LOCATION).stream()
                                .findFirst()
                                .orElse(null);
                        result.put("redirect_location", redirectUrl);
                    }
                    
                    // Get headers
                    Map<String, String> headers = new HashMap<>();
                    response.headers().asHttpHeaders().forEach((key, values) -> {
                        if (values != null && !values.isEmpty()) {
                            headers.put(key, values.get(0));
                        }
                    });
                    result.put("headers", headers);
                    
                    return Mono.just(result);
                })
                .onErrorResume(e -> {
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", e.getMessage());
                    error.put("error_type", e.getClass().getName());
                    return Mono.just(error);
                })
                .timeout(Duration.ofSeconds(10));
    }
    
    private Map<String, Object> buildDiagnosticResult(String meetingId, List<Map<String, Object>> debugSteps, String rawResponse) {
        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put("meeting_id", meetingId);
        finalResult.put("success", true);
        finalResult.put("debug_steps", debugSteps);
        finalResult.put("timestamp", new Date().toString());
        
        // Analyze overall success
        boolean overallSuccess = debugSteps.stream()
                .filter(step -> step.get("success") != null)
                .allMatch(step -> (Boolean) step.get("success"));
        finalResult.put("overall_success", overallSuccess);
        
        // Add response preview
        if (rawResponse != null) {
            finalResult.put("response_length", rawResponse.length());
            finalResult.put("response_preview", rawResponse.substring(0, Math.min(500, rawResponse.length())) + "...");
            
            // Extract summary info
            boolean hasTranscript = rawResponse.contains("\"file_type\":\"TRANSCRIPT\"") || 
                                   rawResponse.contains("TRANSCRIPT");
            finalResult.put("has_transcript_file", hasTranscript);
            
            String downloadUrl = extractDownloadUrl(rawResponse);
            finalResult.put("download_url_found", downloadUrl != null);
        }
        
        System.out.println("\n✅ ========== DEBUG COMPLETE ==========");
        System.out.println("🎯 Meeting ID: " + meetingId);
        System.out.println("✅ Overall Success: " + overallSuccess);
        System.out.println("📊 Debug Steps: " + debugSteps.size());
        System.out.println("======================================\n");
        
        return finalResult;
    }
    
    private Mono<Map<String, Object>> attemptFollowRedirect(String redirectUrl, String meetingId) {
        System.out.println("   🔄 Attempting to follow redirect...");
        
        return webClientBuilder.build()
                .get()
                .uri(redirectUrl)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
                .header(HttpHeaders.ACCEPT, "*/*")
                .retrieve()
                .bodyToMono(String.class)
                .map(content -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("content_length", content.length());
                    result.put("has_content", !content.trim().isEmpty());
                    result.put("is_aws_url", redirectUrl.contains("amazonaws.com") || redirectUrl.contains("cloudfront.net"));
                    return result;
                })
                .onErrorResume(e -> {
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("error", e.getMessage());
                    error.put("is_aws_url", redirectUrl.contains("amazonaws.com") || redirectUrl.contains("cloudfront.net"));
                    return Mono.just(error);
                })
                .timeout(Duration.ofSeconds(15));
    }
    
    private Mono<Map<String, Object>> attemptDirectDownload(String downloadUrl, String accessToken, String meetingId) {
        System.out.println("   ⬇️ Attempting direct download...");
        
        return webClientBuilder.build()
                .get()
                .uri(downloadUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, "text/vtt, text/plain, */*")
                .retrieve()
                .bodyToMono(String.class)
                .map(content -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    result.put("content_length", content.length());
                    result.put("has_content", !content.trim().isEmpty());
                    result.put("content_preview", content.substring(0, Math.min(200, content.length())) + "...");
                    return result;
                })
                .onErrorResume(e -> {
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("error", e.getMessage());
                    return Mono.just(error);
                })
                .timeout(Duration.ofSeconds(15));
    }
    
    private Mono<Map<String, Object>> tryDownloadStrategy(String url, String authHeader, String strategyName, String meetingId) {
        return tryDownloadStrategy(url, authHeader, strategyName, meetingId, new HashMap<>());
    }
    
    private Mono<Map<String, Object>> tryDownloadStrategy(String url, String authHeader, String strategyName, 
                                                         String meetingId, Map<String, String> additionalHeaders) {
        System.out.println("   🧪 Trying " + strategyName + "...");
        
        WebClient.RequestHeadersSpec<?> request = webClientBuilder.build()
                .get()
                .uri(url);
        
        if (authHeader != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, authHeader);
        }
        
        for (Map.Entry<String, String> header : additionalHeaders.entrySet()) {
            request = request.header(header.getKey(), header.getValue());
        }
        
        return request.retrieve()
                .bodyToMono(String.class)
                .map(content -> {
                    System.out.println("     ✅ " + strategyName + " SUCCESS! Content length: " + content.length());
                    Map<String, Object> result = new HashMap<>();
                    result.put("strategy", strategyName);
                    result.put("success", true);
                    result.put("content", content);
                    result.put("content_length", content.length());
                    result.put("has_content", !content.trim().isEmpty());
                    return result;
                })
                .onErrorResume(e -> {
                    System.out.println("     ❌ " + strategyName + " FAILED: " + e.getMessage());
                    Map<String, Object> result = new HashMap<>();
                    result.put("strategy", strategyName);
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    result.put("error_type", e.getClass().getName());
                    return Mono.just(result);
                })
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> {
                    System.out.println("     ⏱️ " + strategyName + " TIMEOUT");
                    Map<String, Object> result = new HashMap<>();
                    result.put("strategy", strategyName);
                    result.put("success", false);
                    result.put("error", "Timeout after 10 seconds");
                    return Mono.just(result);
                });
    }
    
    // ========== KEEP YOUR EXISTING ENDPOINTS BELOW ==========
    
    @GetMapping("/transcript-debug/{meetingId}")
    public Mono<Map<String, Object>> getMeetingTranscriptDebug(@PathVariable String meetingId) {
        System.out.println("🐛 DEBUG ENDPOINT: Fetching transcript for meeting: " + meetingId);
        return zoomService.getMeetingTranscript(meetingId)
                .doOnNext(result -> {
                    System.out.println("🐛 DEBUG RESPONSE: " + result);
                });
    }

    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Zoom Dashboard");
        response.put("message", "Service is running with real Zoom API integration");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @GetMapping("/meetings")
    public Mono<Map<String, Object>> getMeetings() {
        return zoomService.getAllMeetings();
    }
    
    @GetMapping("/analytics/{meetingId}")
    public Mono<Map<String, Object>> getMeetingAnalytics(
            @PathVariable String meetingId,
            @RequestParam(required = false, defaultValue = "5") Integer interval) {
        
        System.out.println("🎯 Fetching analytics for meeting: " + meetingId + " with interval: " + interval + " minutes");
        return zoomService.getMeetingAnalytics(meetingId, interval);
    }

    @GetMapping("/meeting/{meetingId}")
    public Mono<Map<String, Object>> getMeetingDetails(@PathVariable String meetingId) {
        return zoomService.getMeetingDetails(meetingId);
    }

    @GetMapping("/transcript/{meetingId}")
    public Mono<Map<String, Object>> getMeetingTranscript(@PathVariable String meetingId) {
        System.out.println("🎤 Fetching transcript for meeting: " + meetingId);
        return zoomService.getMeetingTranscript(meetingId);
    }

    // COMPLETELY FIXED: Simple and reliable transcript download
    @GetMapping("/transcript-download/{meetingId}")
    public Mono<Map<String, Object>> downloadTranscriptContent(@PathVariable String meetingId) {
        System.out.println("📥 FIXED DOWNLOAD: Starting for meeting: " + meetingId);
        
        return zoomService.getMeetingTranscript(meetingId)
                .flatMap(transcriptInfo -> {
                    System.out.println("📄 STEP 1 - Got transcript info: " + transcriptInfo);
                    
                    if (!(Boolean) transcriptInfo.get("success")) {
                        System.out.println("❌ STEP 1 - No transcript available");
                        return Mono.just(createErrorResponse("No transcript available for this meeting"));
                    }
                    
                    String downloadUrl = (String) transcriptInfo.get("download_url");
                    System.out.println("🔗 STEP 1 - Download URL: " + downloadUrl);
                    
                    // Use the NEW download method from ZoomService
                    return zoomService.downloadTranscriptWithContent(meetingId, downloadUrl)
                            .map(downloadResult -> {
                                System.out.println("✅ STEP 2 - Download completed: " + downloadResult.get("success"));
                                return downloadResult;
                            });
                })
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    System.out.println("❌ Overall error: " + e.getMessage());
                    return Mono.just(createErrorResponse("Download timeout or failed: " + e.getMessage()));
                });
    }

    // NEW ENDPOINT: Frontend-friendly transcript with AWS redirect handling
    @GetMapping("/transcript-frontend/{meetingId}")
    public Mono<Map<String, Object>> getTranscriptForFrontend(@PathVariable String meetingId) {
        System.out.println("💻 FRONTEND TRANSCRIPT for: " + meetingId);
        
        return zoomService.getMeetingTranscript(meetingId)
                .flatMap(transcriptInfo -> {
                    System.out.println("📄 Got transcript info for frontend: " + transcriptInfo);
                    
                    if (!(Boolean) transcriptInfo.get("success")) {
                        System.out.println("❌ No transcript available");
                        Map<String, Object> errorResult = new HashMap<>();
                        errorResult.put("success", false);
                        errorResult.put("error", "No transcript available for this meeting");
                        errorResult.put("meeting_id", meetingId);
                        errorResult.put("transcript_available", false);
                        return Mono.just(errorResult);
                    }
                    
                    String downloadUrl = (String) transcriptInfo.get("download_url");
                    System.out.println("🔗 Download URL: " + downloadUrl);
                    
                    // Get the AWS redirect URL for frontend
                    return zoomService.getAccessToken()
                            .flatMap(authResponse -> {
                                return WebClient.builder()
                                        .build()
                                        .get()
                                        .uri(downloadUrl)
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.getAccessToken())
                                        .header(HttpHeaders.ACCEPT, "text/vtt, text/plain, */*")
                                        .exchangeToMono(response -> {
                                            Map<String, Object> result = new HashMap<>();
                                            result.put("success", true);
                                            result.put("meeting_id", meetingId);
                                            result.put("transcript_available", true);
                                            result.put("original_download_url", downloadUrl);
                                            
                                            if (response.statusCode().is3xxRedirection()) {
                                                String redirectUrl = response.headers().header(HttpHeaders.LOCATION).get(0);
                                                System.out.println("🔄 Found AWS redirect URL: " + redirectUrl);
                                                
                                                result.put("has_redirect", true);
                                                result.put("redirect_url", redirectUrl);
                                                result.put("solution", "frontend_direct_download");
                                                result.put("instructions", "Use redirect_url directly in browser window to download");
                                            } else if (response.statusCode().is2xxSuccessful()) {
                                                result.put("has_redirect", false);
                                                result.put("solution", "frontend_auth_download");
                                                result.put("instructions", "Use original_download_url with Authorization header");
                                            } else {
                                                result.put("success", false);
                                                result.put("error", "HTTP " + response.statusCode());
                                            }
                                            
                                            return Mono.just(result);
                                        });
                            });
                })
                .onErrorResume(e -> {
                    System.out.println("❌ Error getting frontend transcript: " + e.getMessage());
                    return Mono.just(createErrorResponse("Failed to get transcript: " + e.getMessage()));
                })
                .timeout(Duration.ofSeconds(15));
    }
    
    
    

    // Enhanced transcript endpoint
    @GetMapping("/transcript-enhanced/{meetingId}")
    public Mono<Map<String, Object>> getEnhancedTranscript(@PathVariable String meetingId) {
        System.out.println("🚀 ENHANCED TRANSCRIPT for: " + meetingId);
        
        return zoomService.getMeetingTranscript(meetingId)
                .flatMap(transcriptInfo -> {
                    System.out.println("📄 Enhanced - Transcript info: " + transcriptInfo);
                    
                    if (!(Boolean) transcriptInfo.get("success")) {
                        System.out.println("❌ Enhanced - No transcript available");
                        Map<String, Object> noTranscriptResult = new HashMap<>();
                        noTranscriptResult.put("success", false);
                        noTranscriptResult.put("error", "No transcript available for this meeting");
                        noTranscriptResult.put("meeting_id", meetingId);
                        noTranscriptResult.put("step", "basic_info_failed");
                        return Mono.just(noTranscriptResult);
                    }
                    
                    String downloadUrl = (String) transcriptInfo.get("download_url");
                    System.out.println("🔗 Enhanced - Download URL: " + downloadUrl);
                    
                    return zoomService.getAccessToken()
                            .flatMap(authResponse -> {
                                System.out.println("🔑 Enhanced - Got access token");
                                
                                return WebClient.builder()
                                        .build()
                                        .get()
                                        .uri(downloadUrl)
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + authResponse.getAccessToken())
                                        .header(HttpHeaders.ACCEPT, "text/vtt, text/plain, */*")
                                        .exchangeToMono(response -> {
                                            System.out.println("📥 Enhanced - HTTP Status: " + response.statusCode());
                                            
                                            Map<String, Object> result = new HashMap<>();
                                            result.put("meeting_id", meetingId);
                                            result.put("download_url", downloadUrl);
                                            result.put("http_status", response.statusCode().value());
                                            
                                            if (response.statusCode().is2xxSuccessful()) {
                                                return response.bodyToMono(String.class)
                                                        .map(content -> {
                                                            result.put("success", true);
                                                            result.put("content", content);
                                                            result.put("content_length", content.length());
                                                            result.put("has_content", !content.trim().isEmpty());
                                                            result.put("method_used", "direct_success");
                                                            return result;
                                                        });
                                            } else if (response.statusCode().is3xxRedirection()) {
                                                String redirectUrl = response.headers().header(HttpHeaders.LOCATION).get(0);
                                                result.put("redirect_url", redirectUrl);
                                                result.put("has_redirect", true);
                                                result.put("success", false);
                                                result.put("method_used", "redirect_required");
                                                return Mono.just(result);
                                            } else {
                                                result.put("success", false);
                                                result.put("error", "HTTP " + response.statusCode());
                                                result.put("method_used", "http_error");
                                                return Mono.just(result);
                                            }
                                        });
                            });
                })
                .onErrorResume(e -> {
                    System.out.println("❌ Enhanced error: " + e.getMessage());
                    return Mono.just(createErrorResponse("Enhanced endpoint failed: " + e.getMessage()));
                });
    }

    // SIMPLE WORKING VERSION - Always returns basic info
    @GetMapping("/transcript-simple/{meetingId}")
    public Mono<Map<String, Object>> getTranscriptSimple(@PathVariable String meetingId) {
        System.out.println("🔄 SIMPLE TRANSCRIPT for: " + meetingId);
        
        return zoomService.getMeetingTranscript(meetingId)
                .map(transcriptInfo -> {
                    System.out.println("📄 Simple transcript info: " + transcriptInfo);
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", transcriptInfo.get("success"));
                    result.put("meeting_id", meetingId);
                    result.put("transcript_available", transcriptInfo.get("transcript_available"));
                    result.put("download_url", transcriptInfo.get("download_url"));
                    result.put("file_type", transcriptInfo.get("file_type"));
                    result.put("file_extension", transcriptInfo.get("file_extension"));
                    result.put("message", "Use the download_url with proper authentication to get transcript content");
                    result.put("debug_note", "This endpoint only returns metadata, not content");
                    
                    return result;
                })
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> {
                    System.out.println("❌ Simple transcript error: " + e.getMessage());
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("success", false);
                    errorResult.put("error", "Simple transcript failed: " + e.getMessage());
                    errorResult.put("meeting_id", meetingId);
                    return Mono.just(errorResult);
                });
    }

    
    
    
    @PostMapping("/analyze")
    public Mono<Map<String, Object>> analyzeMeeting(@RequestBody Map<String, String> request) {
        String meetingId = request.get("meeting_id");
        Integer interval = Integer.parseInt(request.getOrDefault("interval", "5"));
        
        if (meetingId == null || meetingId.trim().isEmpty()) {
            return Mono.just(createErrorResponse("Meeting ID is required"));
        }
        
        System.out.println("🎯 Analyzing meeting: " + meetingId + " with interval: " + interval + " minutes");
        return zoomService.getMeetingAnalytics(meetingId, interval);
    }

    @GetMapping("/test-token")
    public Mono<Map<String, Object>> testToken() {
        return zoomService.testConnection();
    }

    @GetMapping("/test")
    public Map<String, Object> test() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "API is working perfectly!");
        response.put("timestamp", System.currentTimeMillis());
        response.put("status", "success");
        response.put("service", "Zoom Analytics Dashboard");
        return response;
    }

    @GetMapping("/connection-test")
    public Mono<Map<String, Object>> connectionTest() {
        return zoomService.testConnection();
    }

    @GetMapping("/webinars")
    public Mono<Map<String, Object>> getWebinars() {
        return zoomService.getAllWebinars();
    }

    @GetMapping("/webinar-analytics/{webinarId}")
    public Mono<Map<String, Object>> getWebinarAnalytics(
            @PathVariable String webinarId,
            @RequestParam(required = false, defaultValue = "5") Integer interval) {
        
        System.out.println("🎯 Fetching analytics for webinar: " + webinarId + " with interval: " + interval + " minutes");
        return zoomService.getWebinarAnalytics(webinarId, interval);
    }

    @PostMapping("/analyze-webinar")
    public Mono<Map<String, Object>> analyzeWebinar(@RequestBody Map<String, String> request) {
        String webinarId = request.get("webinar_id");
        Integer interval = Integer.parseInt(request.getOrDefault("interval", "5"));
        
        if (webinarId == null || webinarId.trim().isEmpty()) {
            return Mono.just(createErrorResponse("Webinar ID is required"));
        }
        
        System.out.println("🎯 Analyzing webinar: " + webinarId + " with interval: " + interval + " minutes");
        return zoomService.getWebinarAnalytics(webinarId, interval);
    }

    private Map<String, Object> createErrorResponse(String error) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", error);
        errorResponse.put("transcript_available", false);
        return errorResponse;
    }
}