package net.machiavelli.minecolonytax.webapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.machiavelli.minecolonytax.MineColonyTax;
import net.machiavelli.minecolonytax.TaxConfig;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * HTTP REST API Server for WarStats data.
 * Runs SERVER-SIDE ONLY when properly configured.
 * 
 * Security features:
 * - API key authentication
 * - Rate limiting per IP
 * - Read-only access
 * - CORS support for web browsers
 */
public class WebAPIServer {

    private HttpServer server;
    private final MinecraftServer minecraftServer;
    private WarStatsAPIData apiData;
    private PlayerDataCache cache;
    private final RateLimiter rateLimiter;
    private volatile boolean running = false;
    private java.util.concurrent.ScheduledExecutorService cacheRefreshExecutor;

    public WebAPIServer(MinecraftServer minecraftServer) {
        this.minecraftServer = minecraftServer;
        this.rateLimiter = new RateLimiter(TaxConfig.getWebAPIRateLimitRequestsPerMinute());
    }

    /**
     * Start the Web API server
     */
    public void start() {
        if (!TaxConfig.isWebAPIEnabled()) {
            MineColonyTax.LOGGER.info("Web API is disabled in configuration");
            return;
        }

        int port = TaxConfig.getWebAPIPort();

        try {
            // Validate configuration
            if (TaxConfig.isWebAPIAuthenticationRequired() && TaxConfig.getWebAPIKey().isEmpty()) {
                MineColonyTax.LOGGER.error("Web API authentication is required but no API key is configured!");
                MineColonyTax.LOGGER.error("Please set 'WebAPIKey' in your configuration file.");
                return;
            }

            // Create HTTP server
            server = HttpServer.create(new InetSocketAddress(port), 0);
            
            // Initialize cache if offline players are enabled
            if (TaxConfig.isWebAPIOfflinePlayersEnabled()) {
                cache = new PlayerDataCache(minecraftServer);
                MineColonyTax.LOGGER.info("Offline player support enabled - initializing cache...");
                
                // Initial cache refresh
                cache.refresh();
                
                // Schedule periodic refreshes
                int refreshMinutes = TaxConfig.getWebAPICacheRefreshMinutes();
                cacheRefreshExecutor = java.util.concurrent.Executors.newScheduledThreadPool(1, r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("WebAPI-Cache-Refresh");
                    return t;
                });
                
                cacheRefreshExecutor.scheduleAtFixedRate(
                    () -> {
                        try {
                            cache.refresh();
                        } catch (Exception e) {
                            MineColonyTax.LOGGER.error("Error refreshing cache: {}", e.getMessage());
                        }
                    },
                    refreshMinutes,
                    refreshMinutes,
                    java.util.concurrent.TimeUnit.MINUTES
                );
                
                MineColonyTax.LOGGER.info("Cache refresh scheduled every {} minutes", refreshMinutes);
            } else {
                cache = null;
                MineColonyTax.LOGGER.info("Offline player support disabled (online players only)");
            }
            
            apiData = new WarStatsAPIData(minecraftServer, cache);

            // Register endpoints
            server.createContext("/api/warstats/all", new AllStatsHandler());
            server.createContext("/api/warstats/leaderboard", new LeaderboardHandler());
            server.createContext("/api/warstats/player/", new PlayerStatsHandler());
            server.createContext("/api/warstats/server", new ServerStatsHandler());
            server.createContext("/api/health", new HealthCheckHandler());

            // Use daemon threads to prevent server shutdown blocking
            server.setExecutor(Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("WebAPI-Worker");
                return t;
            }));

            server.start();
            running = true;

            MineColonyTax.LOGGER.info("========================================");
            MineColonyTax.LOGGER.info("Web API Server Started Successfully!");
            MineColonyTax.LOGGER.info("Port: {}", port);
            MineColonyTax.LOGGER.info("Authentication: {}", TaxConfig.isWebAPIAuthenticationRequired() ? "Enabled" : "Disabled");
            MineColonyTax.LOGGER.info("Rate Limit: {} requests/minute", TaxConfig.getWebAPIRateLimitRequestsPerMinute());
            MineColonyTax.LOGGER.info("");
            MineColonyTax.LOGGER.info("Available Endpoints:");
            MineColonyTax.LOGGER.info("  GET /api/health");
            MineColonyTax.LOGGER.info("  GET /api/warstats/all");
            MineColonyTax.LOGGER.info("  GET /api/warstats/leaderboard?sort=warsWon&limit=10");
            MineColonyTax.LOGGER.info("  GET /api/warstats/player/<uuid>");
            MineColonyTax.LOGGER.info("  GET /api/warstats/server");
            MineColonyTax.LOGGER.info("========================================");

        } catch (IOException e) {
            MineColonyTax.LOGGER.error("Failed to start Web API server on port {}", port);
            MineColonyTax.LOGGER.error("Error: {}", e.getMessage());
            MineColonyTax.LOGGER.error("Make sure the port is not already in use and you have permission to bind to it.");
        }
    }

    /**
     * Stop the Web API server
     */
    public void stop() {
        if (server != null && running) {
            // Shutdown cache refresh executor
            if (cacheRefreshExecutor != null) {
                cacheRefreshExecutor.shutdown();
                try {
                    if (!cacheRefreshExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        cacheRefreshExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    cacheRefreshExecutor.shutdownNow();
                }
                MineColonyTax.LOGGER.info("Cache refresh executor stopped");
            }
            
            // Clear cache
            if (cache != null) {
                cache.clear();
            }
            
            server.stop(1); // Stop within 1 second
            running = false;
            MineColonyTax.LOGGER.info("Web API Server stopped");
        }
    }

    /**
     * Check if server is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Base handler with security checks
     */
    private abstract class SecureHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();

            // Add CORS headers for web browser access
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "X-API-Key, Content-Type");
            exchange.getResponseHeaders().add("Content-Type", "application/json");

            // Handle OPTIONS preflight request
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            // Only allow GET requests
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendError(exchange, 405, "Method not allowed. Only GET requests are supported.");
                return;
            }

            // Check rate limiting
            if (!rateLimiter.allowRequest(clientIP)) {
                MineColonyTax.LOGGER.warn("Rate limit exceeded for IP: {}", clientIP);
                sendError(exchange, 429, "Rate limit exceeded. Please try again later.");
                return;
            }

            // Check API key authentication
            if (TaxConfig.isWebAPIAuthenticationRequired()) {
                String apiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
                String configuredKey = TaxConfig.getWebAPIKey();

                if (apiKey == null || !apiKey.equals(configuredKey)) {
                    MineColonyTax.LOGGER.warn("Unauthorized API access attempt from IP: {}", clientIP);
                    sendError(exchange, 401, "Unauthorized. Please provide a valid API key in the 'X-API-Key' header.");
                    return;
                }
            }

            // Process the request
            try {
                handleSecureRequest(exchange);
            } catch (Exception e) {
                MineColonyTax.LOGGER.error("Error processing API request for path {}: ", 
                    exchange.getRequestURI().getPath(), e);
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                sendError(exchange, 500, "Internal server error: " + errorMsg);
            }
        }

        protected abstract void handleSecureRequest(HttpExchange exchange) throws IOException;

        protected void sendResponse(HttpExchange exchange, String response) throws IOException {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        protected void sendError(HttpExchange exchange, int code, String message) throws IOException {
            String errorJSON = String.format("{\"error\":\"%s\",\"code\":%d}", message, code);
            byte[] bytes = errorJSON.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        protected Map<String, String> parseQueryParams(String query) {
            Map<String, String> params = new ConcurrentHashMap<>();
            if (query != null && !query.isEmpty()) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=");
                    if (keyValue.length == 2) {
                        params.put(keyValue[0], keyValue[1]);
                    }
                }
            }
            return params;
        }
    }

    /**
     * Handler for /api/health endpoint
     */
    private class HealthCheckHandler extends SecureHandler {
        @Override
        protected void handleSecureRequest(HttpExchange exchange) throws IOException {
            String response = "{\"status\":\"ok\",\"service\":\"WarStats API\",\"version\":\"1.0\"}";
            sendResponse(exchange, response);
        }
    }

    /**
     * Handler for /api/warstats/all endpoint
     */
    private class AllStatsHandler extends SecureHandler {
        @Override
        protected void handleSecureRequest(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            boolean includeOffline = "true".equalsIgnoreCase(params.getOrDefault("includeOffline", "false"));
            
            // Check if offline players feature is enabled
            if (includeOffline && !TaxConfig.isWebAPIOfflinePlayersEnabled()) {
                sendError(exchange, 400, "Offline player support is not enabled on this server");
                return;
            }
            
            String response = apiData.getAllPlayersStatsJSON(includeOffline);
            sendResponse(exchange, response);
        }
    }

    /**
     * Handler for /api/warstats/leaderboard endpoint
     */
    private class LeaderboardHandler extends SecureHandler {
        @Override
        protected void handleSecureRequest(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            
            String sortBy = params.getOrDefault("sort", "warsWon");
            int limit = Integer.parseInt(params.getOrDefault("limit", "10"));
            boolean includeOffline = "true".equalsIgnoreCase(params.getOrDefault("includeOffline", "false"));
            
            // Validate limit
            if (limit < 1 || limit > 100) {
                sendError(exchange, 400, "Limit must be between 1 and 100");
                return;
            }

            // Check if offline players feature is enabled
            if (includeOffline && !TaxConfig.isWebAPIOfflinePlayersEnabled()) {
                sendError(exchange, 400, "Offline player support is not enabled on this server");
                return;
            }

            String response = apiData.getLeaderboardJSON(sortBy, limit, includeOffline);
            sendResponse(exchange, response);
        }
    }

    /**
     * Handler for /api/warstats/player/<uuid> endpoint
     */
    private class PlayerStatsHandler extends SecureHandler {
        @Override
        protected void handleSecureRequest(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String uuid = path.substring(path.lastIndexOf('/') + 1);

            if (uuid.isEmpty()) {
                sendError(exchange, 400, "Player UUID is required");
                return;
            }

            // Validate UUID format
            if (!uuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                sendError(exchange, 400, "Invalid UUID format");
                return;
            }

            String response = apiData.getPlayerStatsJSON(uuid);
            sendResponse(exchange, response);
        }
    }

    /**
     * Handler for /api/warstats/server endpoint
     */
    private class ServerStatsHandler extends SecureHandler {
        @Override
        protected void handleSecureRequest(HttpExchange exchange) throws IOException {
            String response = apiData.getServerStatsJSON();
            sendResponse(exchange, response);
        }
    }

    /**
     * Simple rate limiter implementation
     */
    private static class RateLimiter {
        private final Map<String, RequestCounter> counters = new ConcurrentHashMap<>();
        private final int maxRequestsPerMinute;

        RateLimiter(int maxRequestsPerMinute) {
            this.maxRequestsPerMinute = maxRequestsPerMinute;
        }

        boolean allowRequest(String ip) {
            if (maxRequestsPerMinute == 0) {
                return true; // Rate limiting disabled
            }

            RequestCounter counter = counters.computeIfAbsent(ip, k -> new RequestCounter());
            return counter.allowRequest(maxRequestsPerMinute);
        }

        private static class RequestCounter {
            private long windowStart = System.currentTimeMillis();
            private int count = 0;

            synchronized boolean allowRequest(int maxRequests) {
                long now = System.currentTimeMillis();
                
                // Reset window if 60 seconds have passed
                if (now - windowStart >= 60000) {
                    windowStart = now;
                    count = 0;
                }

                if (count < maxRequests) {
                    count++;
                    return true;
                }

                return false;
            }
        }
    }
}
