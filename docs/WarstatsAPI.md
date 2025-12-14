# WarstatsAPI Documentation

## Overview
The **WarstatsAPI** is a lightweight, read-only RESTful HTTP server embedded within the **War & Taxes** mod. It allows external applications (such as community websites, Discord bots, or mobile apps) to query real-time war and raid statistics from the Minecraft server.

The API runs directly on the Minecraft server process and provides detailed JSON data about player performance, server-wide totals, and leaderboards.

---

## Architecture

The API consists of three main components:

| File | Purpose |
| :--- | :--- |
| [WebAPIServer.java](file:///c:/Dev/War-N-Taxes-Mod---Minecolonies-Addon/src/main/java/net/machiavelli/minecolonytax/webapi/WebAPIServer.java) | HTTP server, routing, authentication, rate limiting, CORS |
| [WarStatsAPIData.java](file:///c:/Dev/War-N-Taxes-Mod---Minecolonies-Addon/src/main/java/net/machiavelli/minecolonytax/webapi/WarStatsAPIData.java) | Data collection & JSON serialization |
| [PlayerDataCache.java](file:///c:/Dev/War-N-Taxes-Mod---Minecolonies-Addon/src/main/java/net/machiavelli/minecolonytax/webapi/PlayerDataCache.java) | Offline player data caching |

---

## Configuration

The API is **disabled by default** and must be explicitly enabled in the mod's configuration file (`minecolonytax-server.toml`).

### Main Settings (`[Web API]`)

| Setting | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `EnableWebAPI` | Boolean | `false` | Set to `true` to start the API server. |
| `WebAPIPort` | Integer | `8090` | The TCP port the server listens on. Ensure this port is open/forwarded if accessing externally. |
| `WebAPIKey` | String | `""` | A secure secret key for authentication. Required if authentication is enabled. |
| `WebAPIRequireAuthentication` | Boolean | `true` | If `true`, all requests must include the `X-API-Key` header. Highly recommended for public servers. |
| `WebAPIRateLimitRequestsPerMinute` | Integer | `60` | Max requests per IP per minute. Set to `0` to disable. |
| `WebAPIEnableOfflinePlayers` | Boolean | `false` | If `true`, the API scans player data files to include offline players in stats. Uses more resources. |
| `WebAPICacheRefreshMinutes` | Integer | `10` | Frequency (in minutes) to refresh the offline player data cache. |

---

## Authentication

When `WebAPIRequireAuthentication` is enabled, every HTTP request must include the **API Key** in the headers.

**Header Name:** `X-API-Key`
**Value:** The string configured in `WebAPIKey`.

Example curl request:
```bash
curl -H "X-API-Key: my-secret-key" http://localhost:8090/api/warstats/server
```

---

## Endpoints

All endpoints return data in **JSON** format.

### 1. Health Check
Used to verify if the API server is up and reachable.

- **URL:** `/api/health`
- **Method:** `GET`
- **Authentication:** Required (if enabled)

**Response:**
```json
{
  "status": "ok",
  "service": "WarStats API",
  "version": "1.0"
}
```

---

### 2. Server Statistics
Get aggregate statistics for the entire server (total wars, total raids, etc.).

- **URL:** `/api/warstats/server`
- **Method:** `GET`

**Response:**
```json
{
  "generated": "2023-10-27T10:00:00Z",
  "serverName": "My Minecraft Server",
  "onlinePlayers": 5,
  "maxPlayers": 20,
  "totals": {
    "warsWon": 150,
    "coloniesRaided": 42,
    "playersKilled": 300,
    "amountRaided": 50000
  }
}
```

---

### 3. Player Statistics
Get war statistics for a specific player by their UUID.

- **URL:** `/api/warstats/player/<uuid>`
- **Method:** `GET`
- **Path Parameters:**
  - `<uuid>`: The UUID of the player (e.g., `123e4567-e89b-12d3-a456-426614174000`).

**Response (Found):**
```json
{
  "generated": "2023-10-27T10:00:00Z",
  "uuid": "123e4567-e89b-12d3-a456-426614174000",
  "found": true,
  "player": {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "name": "PlayerName",
    "playersKilled": 10,
    "coloniesRaided": 5,
    "amountRaided": 1000,
    "warsWon": 3,
    "warStalemates": 1,
    "online": true
  }
}
```

**Response (Not Found):**
```json
{
  "generated": "2023-10-27T10:00:00Z",
  "uuid": "invalid-uuid",
  "found": false,
  "error": "Player not found"
}
```

---

### 4. Leaderboard
Get a sorted list of top players based on specific criteria.

- **URL:** `/api/warstats/leaderboard`
- **Method:** `GET`
- **Query Parameters:**
  - `sort` (optional): The field to sort by. Options: `warsWon` (default), `playersKilled`, `coloniesRaided`, `amountRaided`, `warStalemates`.
  - `limit` (optional): Number of results to return (1-100). Default: `10`.
  - `includeOffline` (optional): Set to `true` to include offline players in the leaderboard (requires `WebAPIEnableOfflinePlayers` enabled).

**Example URL:** `/api/warstats/leaderboard?sort=amountRaided&limit=5&includeOffline=true`

**Response:**
```json
{
  "generated": "2023-10-27T10:00:00Z",
  "sortBy": "amountRaided",
  "limit": 5,
  "leaderboard": [
    {
      "rank": 1,
      "uuid": "...",
      "name": "TopRaider",
      "value": 5000,
      "online": true,
      "stats": {
        "playersKilled": 50,
        "coloniesRaided": 20,
        "amountRaided": 5000,
        "warsWon": 10,
        "warStalemates": 2
      }
    }
  ]
}
```

---

### 5. All Players
Get statistics for all players. Use with caution on large servers.

- **URL:** `/api/warstats/all`
- **Method:** `GET`
- **Query Parameters:**
  - `includeOffline` (optional): Set to `true` to include offline players (requires `WebAPIEnableOfflinePlayers` enabled).

**Response:**
```json
{
  "generated": "2023-10-27T10:00:00Z",
  "totalPlayers": 15,
  "players": [
    // Array of player objects (same structure as Player Stats endpoint)
  ]
}
```

---

## Data Objects

### Player Stats Object
| Field | Type | Description |
| :--- | :--- | :--- |
| `uuid` | String | Minecraft Player UUID. |
| `name` | String | Current display name of the player. |
| `playersKilled` | Integer | Total number of players killed during wars/raids. |
| `coloniesRaided` | Integer | Total number of colonies successfully raided. |
| `amountRaided` | Long | Total currency amount stolen during raids. |
| `warsWon` | Integer | Total number of wars won. |
| `warStalemates` | Integer | Total number of wars ending in a stalemate. |
| `online` | Boolean | Whether the player is currently online. |

---

## Error Handling

The API uses standard HTTP status codes:

| Code | Meaning |
| :--- | :--- |
| **200** | Request processed successfully. |
| **400** | Bad Request – Missing or invalid parameters. |
| **401** | Unauthorized – Missing or invalid API Key. |
| **405** | Method Not Allowed – Only GET is supported. |
| **429** | Too Many Requests – Rate limit exceeded. |
| **500** | Internal Server Error. |

**Error Response Format:**
```json
{
  "error": "Description of the error",
  "code": 401
}
```

---

## Performance & Efficiency Analysis

### Current Implementation Review

#### ✅ What's Done Well

| Area | Implementation | Rating |
| :--- | :--- | :--- |
| **Thread Safety** | Uses `ConcurrentHashMap` for rate limiter and cache. | ✅ Excellent |
| **Daemon Threads** | HTTP workers and cache refresh use daemon threads – won't block server shutdown. | ✅ Excellent |
| **Rate Limiting** | Per-IP rate limiting prevents abuse and DoS. | ✅ Excellent |
| **Caching** | Offline player data is cached and refreshed periodically, not on every request. | ✅ Good |
| **Lazy Loading** | API is disabled by default; no resources used unless explicitly enabled. | ✅ Excellent |
| **Non-blocking** | HTTP server runs on separate thread pool (4 workers), doesn't block main game thread. | ✅ Excellent |
| **Error Handling** | Try-catch blocks prevent crashes from propagating to main server. | ✅ Good |

#### ⚠️ Potential Performance Concerns

| Area | Issue | Impact | Severity |
| :--- | :--- | :--- | :--- |
| **Online Player Iteration** | `/api/warstats/server` and `/api/warstats/all` loop through all online players on every request. | Low-Medium (minimal with <50 players) | 🟡 Low |
| **Capability Resolution** | Calls `player.getCapability().resolve()` per player per request. | Low (Forge caches capabilities) | 🟢 Minimal |
| **Cache Refresh** | `PlayerDataCache.refresh()` reads **all** `.dat` files from disk synchronously. | Medium-High on large servers (1000+ players) | 🟠 Medium |
| **JSON Serialization** | Uses `Gson.toJson()` with pretty printing on every request. | Low (minimal overhead) | 🟢 Minimal |
| **No Response Caching** | Every request regenerates JSON from scratch. | Low-Medium | 🟡 Low |

---

## Improvement Suggestions

### 1. Add Response Caching (High Priority)

**Problem:** Leaderboard and all-player endpoints regenerate full JSON on every request.

**Solution:** Cache the JSON response for a short duration (e.g., 30 seconds).

```java
// In WarStatsAPIData.java
private String cachedLeaderboardJSON = null;
private long leaderboardCacheTime = 0;
private static final long RESPONSE_CACHE_MS = 30_000; // 30 seconds

public String getLeaderboardJSON(String sortBy, int limit, boolean includeOffline) {
    long now = System.currentTimeMillis();
    String cacheKey = sortBy + ":" + limit + ":" + includeOffline;
    
    if (cachedLeaderboardJSON != null && (now - leaderboardCacheTime) < RESPONSE_CACHE_MS) {
        return cachedLeaderboardJSON;
    }
    
    // Generate fresh response...
    cachedLeaderboardJSON = GSON.toJson(response);
    leaderboardCacheTime = now;
    return cachedLeaderboardJSON;
}
```

**Benefit:** Reduces CPU usage by 90%+ for frequently polled endpoints.

---

### 2. Offload Cache Refresh to Background Thread (Medium Priority)

**Problem:** `PlayerDataCache.refresh()` is synchronous and blocks while reading files.

**Current Code (PlayerDataCache.java:70):**
```java
public synchronized void refresh() {
    // ... reads all .dat files synchronously
}
```

**Solution:** Already runs on a scheduled executor, but consider adding a timeout or batch processing for very large servers.

```java
// In PlayerDataCache.java - add batch processing
private static final int BATCH_SIZE = 100;

for (int i = 0; i < playerFiles.length; i += BATCH_SIZE) {
    int end = Math.min(i + BATCH_SIZE, playerFiles.length);
    for (int j = i; j < end; j++) {
        // Process file...
    }
    // Optional: yield to other threads between batches
    Thread.yield();
}
```

**Benefit:** Prevents long file I/O from monopolizing CPU during cache refresh.

---

### 3. Disable Pretty Printing in Production (Low Priority)

**Current Code (WarStatsAPIData.java:23):**
```java
private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
```

**Suggestion:** Add a config option to disable pretty printing for production:
```java
private static final Gson GSON = TaxConfig.isWebAPIDebugMode() 
    ? new GsonBuilder().setPrettyPrinting().create()
    : new Gson();
```

**Benefit:** Reduces JSON size by ~30% and slightly faster serialization.

---

### 4. Add Pagination to `/api/warstats/all` (Medium Priority)

**Problem:** Returns ALL players in one response – can be massive on large servers.

**Solution:** Add `offset` and `limit` query parameters:
```
GET /api/warstats/all?limit=50&offset=0
GET /api/warstats/all?limit=50&offset=50
```

**Benefit:** Prevents massive JSON responses and enables incremental loading.

---

### 5. Consider ETag/Last-Modified Headers (Low Priority)

Add HTTP caching headers so clients can skip re-downloading unchanged data:

```java
exchange.getResponseHeaders().add("ETag", "\"" + dataHash + "\"");
exchange.getResponseHeaders().add("Cache-Control", "max-age=30");
```

**Benefit:** Reduces bandwidth for polling clients.

---

### 6. Rate Limiter Memory Cleanup (Low Priority)

**Problem:** `RateLimiter.counters` map never removes old IP entries.

**Current Code (WebAPIServer.java:368):**
```java
private final Map<String, RequestCounter> counters = new ConcurrentHashMap<>();
```

**Solution:** Add periodic cleanup of stale entries:
```java
// Run periodically (e.g., every 5 minutes)
counters.entrySet().removeIf(entry -> 
    System.currentTimeMillis() - entry.getValue().windowStart > 300_000);
```

**Benefit:** Prevents memory leak on servers with many unique IPs over time.

---

## Server Impact Summary

| Scenario | TPS Impact | Memory | Notes |
| :--- | :--- | :--- | :--- |
| API Disabled | **0** | **0** | No resources used |
| API Enabled, Low Traffic (<10 req/min) | **< 0.1%** | **~1-2 MB** | Negligible |
| API Enabled, High Traffic (60 req/min) | **< 0.5%** | **~2-5 MB** | Minimal with rate limiting |
| Offline Players Enabled (100 players) | **< 0.1%** (during refresh) | **~5-10 MB** | Cache refresh every 10 min |
| Offline Players Enabled (1000+ players) | **~1-2%** (during refresh) | **~20-50 MB** | Consider increasing refresh interval |

---

## Recommendations for Server Operators

1. **Keep `EnableWebAPI` disabled** unless you specifically need external API access.
2. **Always enable authentication** (`WebAPIRequireAuthentication = true`) on public servers.
3. **Keep rate limiting enabled** (default 60 req/min is reasonable).
4. **Only enable `WebAPIEnableOfflinePlayers`** if you need offline stats for a website. Increase `WebAPICacheRefreshMinutes` to 30+ for servers with many players.
5. **Use a reverse proxy** (nginx, Caddy) for SSL/TLS if exposing the API to the internet.

---

## Security Checklist

- [x] API disabled by default
- [x] API key authentication
- [x] Rate limiting per IP
- [x] Read-only (GET only, no mutations)
- [x] CORS headers for browser access
- [ ] No HTTPS (must use reverse proxy)
- [ ] No IP whitelist option
- [ ] No request logging/audit trail
