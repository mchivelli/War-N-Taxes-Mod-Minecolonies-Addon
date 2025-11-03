# WebAPI Debugging Guide

## Issue: 500 Internal Server Error

### Root Cause
The `getServerStatsJSON()` method had broken logic with redundant loops and arrays that were never used, causing the server to crash when processing requests.

### Fixed Issues
1. **Removed redundant loops** - Three loops that did nothing useful
2. **Added proper error handling** - Catches exceptions when reading player data
3. **Enhanced logging** - Better error messages with full stack traces

---

## Testing the WebAPI

### Step 1: Check Configuration

Edit `config/warntax-common.toml`:

```toml
# Enable the Web API
EnableWebAPI = true

# Port (default: 8090)
WebAPIPort = 8090

# API Key (change this!)
WebAPIKey = "your-secret-key-here"

# Authentication (recommended: true for production)
WebAPIAuthenticationRequired = true

# Rate limiting (requests per minute, 0 = unlimited)
WebAPIRateLimitRequestsPerMinute = 60

# Offline player support
WebAPIEnableOfflinePlayers = false
```

### Step 2: Start the Server

Watch the server logs for:
```
========================================
Web API Server Started Successfully!
Port: 8090
Authentication: Enabled
Rate Limit: 60 requests/minute

Available Endpoints:
  GET /api/health
  GET /api/warstats/all
  GET /api/warstats/leaderboard?sort=warsWon&limit=10
  GET /api/warstats/player/<uuid>
  GET /api/warstats/server
========================================
```

### Step 3: Test Endpoints

#### Health Check (No Auth Required)
```bash
curl http://localhost:8090/api/health
```

Expected response:
```json
{
  "status": "ok",
  "service": "WarStats API",
  "version": "1.0"
}
```

#### With Authentication
All other endpoints require the API key in the header:

```bash
# Test leaderboard
curl -H "X-API-Key: your-secret-key-here" \
  "http://localhost:8090/api/warstats/leaderboard?sort=warsWon&limit=10"

# Test server stats
curl -H "X-API-Key: your-secret-key-here" \
  "http://localhost:8090/api/warstats/server"

# Test all players
curl -H "X-API-Key: your-secret-key-here" \
  "http://localhost:8090/api/warstats/all"

# Test specific player
curl -H "X-API-Key: your-secret-key-here" \
  "http://localhost:8090/api/warstats/player/550e8400-e29b-41d4-a716-446655440000"
```

---

## Common Issues

### Issue: Connection Refused
**Symptoms:** `GET http://warbornrealms.com/api/warstats/leaderboard 500`

**Solutions:**
1. Check if WebAPI is enabled in config
2. Check if server started successfully (look for "Web API Server Started" in logs)
3. Check if port is open (firewall, router port forwarding)
4. Check if another service is using the port

### Issue: 401 Unauthorized
**Symptoms:** `{"error":"Unauthorized","code":401}`

**Solutions:**
1. Check if you're sending the `X-API-Key` header
2. Verify the API key matches the config file
3. If testing from browser, ensure CORS is working

### Issue: 429 Rate Limit
**Symptoms:** `{"error":"Rate limit exceeded","code":429}`

**Solutions:**
1. Wait 60 seconds
2. Increase rate limit in config
3. Set `WebAPIRateLimitRequestsPerMinute = 0` to disable

### Issue: 500 Internal Server Error (Fixed)
**Symptoms:** API crashes with 500 error

**Fix Applied:**
- Removed broken stat collection loops in `getServerStatsJSON()`
- Added try-catch blocks around player data access
- Added detailed error logging

**How to Debug:**
Check server logs for lines like:
```
[ERROR] Error processing API request for path /api/warstats/server: <exception details>
```

---

## Production Setup

### For External Access (warbornrealms.com)

You need to set up a **reverse proxy** on your web server to forward API requests to your Minecraft server.

#### Nginx Configuration
```nginx
# In your nginx config for warbornrealms.com
location /api/ {
    proxy_pass http://your-minecraft-server-ip:8090/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-API-Key $http_x_api_key;
    
    # CORS headers
    add_header Access-Control-Allow-Origin * always;
    add_header Access-Control-Allow-Methods "GET, OPTIONS" always;
    add_header Access-Control-Allow-Headers "X-API-Key, Content-Type" always;
    
    # Handle preflight
    if ($request_method = OPTIONS) {
        return 204;
    }
}
```

#### Apache Configuration
```apache
# In your apache config for warbornrealms.com
<Location /api>
    ProxyPass http://your-minecraft-server-ip:8090/api
    ProxyPassReverse http://your-minecraft-server-ip:8090/api
    
    # CORS headers
    Header always set Access-Control-Allow-Origin "*"
    Header always set Access-Control-Allow-Methods "GET, OPTIONS"
    Header always set Access-Control-Allow-Headers "X-API-Key, Content-Type"
</Location>
```

### Security Checklist

- [ ] Strong API key set (not default)
- [ ] Authentication enabled (`WebAPIAuthenticationRequired = true`)
- [ ] Rate limiting enabled
- [ ] Firewall rules configured (only allow from web server if using reverse proxy)
- [ ] HTTPS enabled on your website (reverse proxy handles SSL)
- [ ] API key stored securely (environment variable, not in frontend code)

---

## Monitoring

### Check API Health
```bash
# Every 5 minutes
*/5 * * * * curl -s http://localhost:8090/api/health || echo "API DOWN!"
```

### Monitor Logs
```bash
# Watch for errors
tail -f logs/latest.log | grep "Web API\|ERROR"
```

### Key Log Messages

**Success:**
```
[INFO] Web API Server Started Successfully!
[DEBUG] ✅ WHITELIST ALLOWED: Player ...
```

**Warnings:**
```
[WARN] Rate limit exceeded for IP: 1.2.3.4
[WARN] Unauthorized API access attempt from IP: 1.2.3.4
[WARN] Error reading stats for player PlayerName: <reason>
```

**Errors:**
```
[ERROR] Failed to start Web API server on port 8090
[ERROR] Error processing API request for path /api/warstats/leaderboard: <details>
```

---

## Performance Tips

### For Large Servers (100+ players)

1. **Enable Offline Player Support with Caution**
   ```toml
   WebAPIEnableOfflinePlayers = true
   WebAPICacheRefreshMinutes = 30  # Refresh every 30 minutes
   ```
   
   This scans all player .dat files - can be slow with thousands of players.

2. **Increase Rate Limits for High Traffic**
   ```toml
   WebAPIRateLimitRequestsPerMinute = 120
   ```

3. **Use Query Parameters Wisely**
   - `?includeOffline=false` - Faster (online only)
   - `?limit=10` - Smaller leaderboards load faster
   - `?sort=warsWon` - Different sort fields

### Response Times
- `/api/health` - <1ms (instant)
- `/api/warstats/server` - <10ms (online players only)
- `/api/warstats/leaderboard?limit=10` - <50ms (online only)
- `/api/warstats/all?includeOffline=true` - 100ms-5s (depends on player count)

---

## JavaScript Integration Example

### Fetch Leaderboard
```javascript
const API_URL = 'https://warbornrealms.com/api';
const API_KEY = 'your-secret-key-here'; // Should be in backend/env variable

async function fetchLeaderboard() {
    try {
        const response = await fetch(
            `${API_URL}/warstats/leaderboard?sort=warsWon&limit=50`,
            {
                headers: {
                    'X-API-Key': API_KEY
                }
            }
        );
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        
        const data = await response.json();
        return data.leaderboard;
        
    } catch (error) {
        console.error('Error fetching leaderboard:', error);
        throw error;
    }
}

// Usage
fetchLeaderboard()
    .then(leaderboard => {
        leaderboard.forEach(entry => {
            console.log(`${entry.rank}. ${entry.name} - ${entry.value} wars won`);
        });
    })
    .catch(error => {
        console.error('Failed to load leaderboard:', error);
    });
```

---

## Troubleshooting Checklist

When API returns 500 error:

1. ✅ **Check server logs** - Look for ERROR messages with stack traces
2. ✅ **Verify config** - All required settings present
3. ✅ **Test health endpoint** - `curl http://localhost:8090/api/health`
4. ✅ **Check player data** - Ensure at least one player is online with stats
5. ✅ **Restart server** - Sometimes helps after config changes
6. ✅ **Check Minecraft version** - WebAPI requires Java 17+
7. ✅ **Verify dependencies** - MineColonies and SDMShop if using currency

---

## Support

If you're still experiencing issues after following this guide:

1. **Collect Information:**
   - Server logs (last 100 lines)
   - Config file (`warntax-common.toml`)
   - Exact error message
   - Steps to reproduce

2. **Enable Debug Logging:**
   Add to `config/warntax-common.toml`:
   ```toml
   EnableDebugLogging = true
   ```

3. **Test with Minimal Config:**
   ```toml
   EnableWebAPI = true
   WebAPIPort = 8090
   WebAPIKey = "test"
   WebAPIAuthenticationRequired = false  # For testing only!
   WebAPIRateLimitRequestsPerMinute = 0
   WebAPIEnableOfflinePlayers = false
   ```

4. **Check Server Startup:**
   Look for "Web API Server Started Successfully!" in logs.
