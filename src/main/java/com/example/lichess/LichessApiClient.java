package com.example.lichess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class LichessApiClient {
    private static final Logger logger = LoggerFactory.getLogger(LichessApiClient.class);
    private static final String LICHESS_API_BASE = "https://lichess.org/api";
    private static final int REQUEST_TIMEOUT = 30000; // 30 seconds
    
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiToken;

    public LichessApiClient() {
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
        this.apiToken = System.getenv("LICHESS_API_TOKEN");
    }

    public LichessApiClient(String apiToken) {
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
        this.apiToken = apiToken;
    }

    public String getUserProfile(String username) throws Exception {
        String url = LICHESS_API_BASE + "/user/" + username;
        return makeApiCall(url);
    }

    public String getGame(String gameId) throws Exception {
        String url = LICHESS_API_BASE + "/game/" + gameId;
        return makeApiCall(url);
    }

    public String getGamePgn(String gameId) throws Exception {
        // Use export endpoint which works for ongoing games
        String url = "https://lichess.org/game/export/" + gameId;
        return makePgnApiCall(url);
    }

    public String getGameWithClocks(String gameId) throws Exception {
        String url = LICHESS_API_BASE + "/game/" + gameId + "?clocks=true";
        return makeApiCall(url);
    }

    public String getUserGames(String username, int max) throws Exception {
        String url = LICHESS_API_BASE + "/games/user/" + username + "?max=" + max;
        return makeApiCall(url);
    }

    public String getUserGames(String username) throws Exception {
        return getUserGames(username, 10);
    }

    public String getLeaderboard(String perfType) throws Exception {
        String url = LICHESS_API_BASE + "/player/top/200/" + perfType;
        return makeApiCall(url);
    }

    public String getTournaments() throws Exception {
        String url = LICHESS_API_BASE + "/tournament";
        return makeApiCall(url);
    }

    public String getTournament(String tournamentId) throws Exception {
        String url = LICHESS_API_BASE + "/tournament/" + tournamentId;
        return makeApiCall(url);
    }

    public String streamGameMoves(String gameId) throws Exception {
        String url = LICHESS_API_BASE + "/stream/game/" + gameId;
        return makeApiCall(url);
    }

    public String getUserRatingHistory(String username) throws Exception {
        String url = LICHESS_API_BASE + "/user/" + username + "/rating-history";
        return makeApiCall(url);
    }

    public String getUserStatus(String username) throws Exception {
        String url = LICHESS_API_BASE + "/users/status?ids=" + username;
        return makeApiCall(url);
    }

    public String createGame(String player1, String player2, String timeControl, String variant) throws Exception {
        String url = LICHESS_API_BASE + "/challenge/open";
        
        ObjectMapper mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode requestBody = mapper.createObjectNode();
        
        if (timeControl != null && !timeControl.isEmpty()) {
            String[] timeParts = timeControl.split("\\+");
            if (timeParts.length >= 1) {
                try {
                    int clockLimit = Integer.parseInt(timeParts[0]) * 60; // Convert minutes to seconds
                    requestBody.put("clock.limit", clockLimit);
                    
                    if (timeParts.length > 1) {
                        int clockIncrement = Integer.parseInt(timeParts[1]);
                        requestBody.put("clock.increment", clockIncrement);
                    }
                } catch (NumberFormatException e) {
                    // If parsing fails, create a correspondence game
                    requestBody.put("days", 3);
                }
            }
        } else {
            // Default to 10+0 blitz
            requestBody.put("clock.limit", 600);
            requestBody.put("clock.increment", 0);
        }
        
        if (variant != null && !variant.isEmpty()) {
            requestBody.put("variant", variant);
        } else {
            requestBody.put("variant", "standard");
        }
        
        requestBody.put("rated", false);
        requestBody.put("name", "MCP Game: " + player1 + " vs " + player2);
        
        return makePostApiCall(url, requestBody.toString());
    }

    public String upgradeToBotAccount() throws Exception {
        String url = LICHESS_API_BASE + "/bot/account/upgrade";
        return makePostApiCall(url, "");
    }

    public String acceptChallenge(String challengeId) throws Exception {
        String url = LICHESS_API_BASE + "/challenge/" + challengeId + "/accept";
        return makePostApiCall(url, "");
    }

    public String makeMove(String gameId, String move) throws Exception {
        String url = LICHESS_API_BASE + "/bot/game/" + gameId + "/move/" + move;
        return makePostApiCall(url, "");
    }

    public String getBotGameState(String gameId) throws Exception {
        // Use account/playing endpoint to get real-time bot game state
        String url = LICHESS_API_BASE + "/account/playing";
        return makeApiCall(url);
    }
    
    public String pollForMyTurn(String gameId, int maxPolls, int intervalSeconds) throws Exception {
        String url = LICHESS_API_BASE + "/account/playing";
        
        for (int i = 0; i < maxPolls; i++) {
            String response = makeApiCall(url);
            
            // Parse response to check if it's my turn or if game is over
            if (response.contains("\"gameId\" : \"" + gameId + "\"")) {
                // Check if game is finished
                if (response.contains("\"name\" : \"mate\"") || response.contains("\"name\" : \"resign\"") || 
                    response.contains("\"name\" : \"timeout\"") || response.contains("\"name\" : \"draw\"") ||
                    response.contains("\"name\" : \"stalemate\"") || response.contains("\"name\" : \"aborted\"")) {
                    return ">>> GAME OVER! <<<\n\nPolling stopped after " + (i + 1) + " checks.\n\n" + response;
                }
                // Check if it's my turn
                if (response.contains("\"isMyTurn\" : true")) {
                    return ">>> IT'S YOUR TURN! <<<\n\nPolling stopped after " + (i + 1) + " checks.\n\n" + response;
                }
            } else if (response.contains("\"nowPlaying\" : [ ]") || !response.contains("\"gameId\" : \"" + gameId + "\"")) {
                // Game no longer in nowPlaying list, so it's finished
                return ">>> GAME OVER! <<<\n\nGame " + gameId + " is no longer active. Polling stopped after " + (i + 1) + " checks.\n\n" + response;
            }
            
            if (i < maxPolls - 1) { // Don't sleep on the last iteration
                try {
                    Thread.sleep(intervalSeconds * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new Exception("Polling interrupted");
                }
            }
        }
        
        return "Polling completed after " + maxPolls + " checks. Still not your turn.\n\nLast state:\n" + makeApiCall(url);
    }
    
    private String makeJsonGameStateCall(String url) throws Exception {
        HttpGet request = new HttpGet(url);
        request.addHeader("Accept", "application/vnd.lichess.v3+json");
        request.addHeader("User-Agent", "LichessMcpServer/1.0");
        
        if (apiToken != null && !apiToken.isEmpty()) {
            request.addHeader("Authorization", "Bearer " + apiToken);
        }

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            
            if (statusCode == 200) {
                return formatJsonResponse(responseBody);
            } else if (statusCode == 404) {
                throw new Exception("Game not found: " + url);
            } else if (statusCode == 429) {
                throw new Exception("Rate limit exceeded. Please try again later.");
            } else {
                throw new Exception("HTTP " + statusCode + ": " + responseBody);
            }
        } catch (IOException e) {
            logger.error("Error making JSON game state call to: " + url, e);
            throw new Exception("Network error: " + e.getMessage(), e);
        }
    }
    
    public String watchGameStream(String gameId, int maxUpdates) throws Exception {
        String url = LICHESS_API_BASE + "/bot/game/stream/" + gameId;
        return makeWatchStreamApiCall(url, maxUpdates);
    }
    
    private String makeWatchStreamApiCall(String url, int maxUpdates) throws Exception {
        HttpGet request = new HttpGet(url);
        request.addHeader("Accept", "application/json");
        request.addHeader("User-Agent", "LichessMcpServer/1.0");
        
        if (apiToken != null && !apiToken.isEmpty()) {
            request.addHeader("Authorization", "Bearer " + apiToken);
        }

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            
            if (statusCode == 200) {
                StringBuilder updates = new StringBuilder();
                int updateCount = 0;
                
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(response.getEntity().getContent()))) {
                    String line;
                    while ((line = reader.readLine()) != null && updateCount < maxUpdates) {
                        if (!line.trim().isEmpty()) {
                            updates.append("Update ").append(++updateCount).append(": ").append(line).append("\n");
                            
                            // Check if this update indicates it's our turn
                            if (line.contains("\"color\":\"black\"") && line.contains("\"turn\":")) {
                                updates.append(">>> IT'S YOUR TURN TO MOVE! <<<\n");
                                break;
                            }
                        }
                    }
                }
                
                return updates.toString();
            } else if (statusCode == 404) {
                throw new Exception("Game not found: " + url);
            } else if (statusCode == 401) {
                throw new Exception("Authentication required. Please provide a valid API token.");
            } else if (statusCode == 429) {
                throw new Exception("Rate limit exceeded. Please try again later.");
            } else {
                String responseBody = EntityUtils.toString(response.getEntity());
                throw new Exception("HTTP " + statusCode + ": " + responseBody);
            }
        } catch (IOException e) {
            logger.error("Error making watch stream API call to: " + url, e);
            throw new Exception("Network error: " + e.getMessage(), e);
        }
    }
    
    private String makeStreamApiCall(String url) throws Exception {
        HttpGet request = new HttpGet(url);
        request.addHeader("Accept", "application/json");
        request.addHeader("User-Agent", "LichessMcpServer/1.0");
        
        if (apiToken != null && !apiToken.isEmpty()) {
            request.addHeader("Authorization", "Bearer " + apiToken);
        }

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            
            if (statusCode == 200) {
                // Read the first line of the stream (the initial game state)
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(response.getEntity().getContent()))) {
                    String firstLine = reader.readLine();
                    if (firstLine != null && !firstLine.trim().isEmpty()) {
                        return formatJsonResponse(firstLine);
                    } else {
                        return "{}"; // Empty response
                    }
                }
            } else if (statusCode == 404) {
                throw new Exception("Game not found: " + url);
            } else if (statusCode == 401) {
                throw new Exception("Authentication required. Please provide a valid API token.");
            } else if (statusCode == 429) {
                throw new Exception("Rate limit exceeded. Please try again later.");
            } else {
                String responseBody = EntityUtils.toString(response.getEntity());
                throw new Exception("HTTP " + statusCode + ": " + responseBody);
            }
        } catch (IOException e) {
            logger.error("Error making stream API call to: " + url, e);
            throw new Exception("Network error: " + e.getMessage(), e);
        }
    }

    public String resignGame(String gameId) throws Exception {
        String url = LICHESS_API_BASE + "/bot/game/" + gameId + "/resign";
        return makePostApiCall(url, "");
    }

    public String sendChatMessage(String gameId, String text, String room) throws Exception {
        String url = LICHESS_API_BASE + "/bot/game/" + gameId + "/chat";
        
        ObjectMapper mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("text", text);
        requestBody.put("room", room != null ? room : "player");
        
        return makePostApiCall(url, requestBody.toString());
    }

    private String makeApiCall(String url) throws Exception {
        HttpGet request = new HttpGet(url);
        request.addHeader("Accept", "application/json");
        request.addHeader("User-Agent", "LichessMcpServer/1.0");
        
        if (apiToken != null && !apiToken.isEmpty()) {
            request.addHeader("Authorization", "Bearer " + apiToken);
        }

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            
            if (statusCode == 200) {
                return formatJsonResponse(responseBody);
            } else if (statusCode == 404) {
                throw new Exception("Resource not found: " + url);
            } else if (statusCode == 429) {
                throw new Exception("Rate limit exceeded. Please try again later.");
            } else {
                throw new Exception("HTTP " + statusCode + ": " + responseBody);
            }
        } catch (IOException e) {
            logger.error("Error making API call to: " + url, e);
            throw new Exception("Network error: " + e.getMessage(), e);
        }
    }

    private String makePgnApiCall(String url) throws Exception {
        HttpGet request = new HttpGet(url);
        request.addHeader("Accept", "application/x-chess-pgn");
        request.addHeader("User-Agent", "LichessMcpServer/1.0");
        
        if (apiToken != null && !apiToken.isEmpty()) {
            request.addHeader("Authorization", "Bearer " + apiToken);
        }

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            
            if (statusCode == 200) {
                return responseBody; // Return raw PGN string
            } else if (statusCode == 404) {
                throw new Exception("Game not found: " + url);
            } else if (statusCode == 429) {
                throw new Exception("Rate limit exceeded. Please try again later.");
            } else {
                throw new Exception("HTTP " + statusCode + ": " + responseBody);
            }
        } catch (IOException e) {
            logger.error("Error making PGN API call to: " + url, e);
            throw new Exception("Network error: " + e.getMessage(), e);
        }
    }

    private String makePostApiCall(String url, String jsonBody) throws Exception {
        HttpPost request = new HttpPost(url);
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("User-Agent", "LichessMcpServer/1.0");
        
        if (apiToken != null && !apiToken.isEmpty()) {
            request.addHeader("Authorization", "Bearer " + apiToken);
        }
        
        request.setEntity(new StringEntity(jsonBody));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            
            if (statusCode == 200 || statusCode == 201) {
                return formatJsonResponse(responseBody);
            } else if (statusCode == 401) {
                throw new Exception("Authentication required. Please provide a valid API token.");
            } else if (statusCode == 404) {
                throw new Exception("Resource not found: " + url);
            } else if (statusCode == 429) {
                throw new Exception("Rate limit exceeded. Please try again later.");
            } else {
                throw new Exception("HTTP " + statusCode + ": " + responseBody);
            }
        } catch (IOException e) {
            logger.error("Error making POST API call to: " + url, e);
            throw new Exception("Network error: " + e.getMessage(), e);
        }
    }

    private String formatJsonResponse(String jsonString) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        } catch (Exception e) {
            logger.warn("Could not format JSON response, returning as-is", e);
            return jsonString;
        }
    }

    public void setRateLimit(long delayMs) {
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }
}