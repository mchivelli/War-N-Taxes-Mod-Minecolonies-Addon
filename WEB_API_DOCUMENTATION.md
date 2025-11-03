# Web API Documentation - WarStats REST API

## Overview

The WarStats Web API provides HTTP REST endpoints to access player war statistics from your Minecraft server. This allows you to create external leaderboards, dashboards, and integrations with websites.

**IMPORTANT:** The API runs **SERVER-SIDE ONLY** and requires proper configuration to function securely.

---

## Features

✅ **RESTful JSON API** - Standard HTTP endpoints returning JSON data  
✅ **Secure by Default** - API key authentication and rate limiting built-in  
✅ **Real-Time Data** - Query live server statistics  
✅ **CORS Support** - Works with web browsers and JavaScript  
✅ **Leaderboards** - Sortable rankings by multiple criteria  
✅ **Server-Side Only** - No client-side code, runs entirely on the server  
✅ **Performance Optimized** - Rate limiting and async processing  

---

## Configuration

### Config File Location
`config/warntax/minecolonytax.toml`

### Web API Settings

```toml
[Web API]
    # Enable the Web API server
    EnableWebAPI = false
    
    # Port number (1024-65535)
    WebAPIPort = 8090
    
    # API authentication key (set a strong random key!)
    WebAPIKey = "your-secret-api-key-here-change-this"
    
    # Requests allowed per IP per minute (0 = unlimited)
    WebAPIRateLimitRequestsPerMinute = 60
    
    # Require API key for all requests
    WebAPIRequireAuthentication = true
```

### Security Best Practices

1. **Always Enable Authentication** - Set `WebAPIRequireAuthentication = true`
2. **Use a Strong API Key** - Minimum 32 random characters
3. **Enable Rate Limiting** - Prevent abuse (60 requests/min recommended)
4. **Firewall Your Port** - Only expose to trusted networks if possible
5. **Keep API Key Private** - Never commit to version control

### Port Forwarding

To access the API from outside your network:
1. Forward the configured port (default 8090) on your router
2. Use your public IP or domain name in API requests
3. Consider using a reverse proxy (nginx, Apache) for HTTPS

---

## API Endpoints

### Base URL
```
http://your-server-ip:8090/api
```

### Authentication

Include the API key in the `X-API-Key` header for all requests:

```bash
curl -H "X-API-Key: your-secret-api-key" http://localhost:8090/api/health
```

---

### 1. Health Check

**Endpoint:** `GET /api/health`

**Description:** Check if the API server is running

**Authentication:** Required if enabled

**Response:**
```json
{
  "status": "ok",
  "service": "WarStats API",
  "version": "1.0"
}
```

**Example:**
```bash
curl http://localhost:8090/api/health
```

---

### 2. All Players Stats

**Endpoint:** `GET /api/warstats/all`

**Description:** Get war statistics for all online players

**Authentication:** Required if enabled

**Response:**
```json
{
  "generated": "2025-01-18T14:30:00Z",
  "server": "Minecraft Server",
  "totalPlayers": 5,
  "players": [
    {
      "uuid": "123e4567-e89b-12d3-a456-426614174000",
      "name": "PlayerOne",
      "playersKilled": 45,
      "coloniesRaided": 12,
      "amountRaided": 50000,
      "warsWon": 8,
      "warStalemates": 2,
      "online": true
    }
  ]
}
```

**Example:**
```bash
curl -H "X-API-Key: your-key" http://localhost:8090/api/warstats/all
```

---

### 3. Leaderboard

**Endpoint:** `GET /api/warstats/leaderboard`

**Description:** Get ranked leaderboard for a specific statistic

**Authentication:** Required if enabled

**Query Parameters:**
- `sort` (optional) - Stat to sort by:
  - `warsWon` (default)
  - `playersKilled`
  - `coloniesRaided`
  - `amountRaided`
  - `warStalemates`
- `limit` (optional) - Number of results (1-100, default: 10)

**Response:**
```json
{
  "generated": "2025-01-18T14:30:00Z",
  "sortBy": "warsWon",
  "limit": 10,
  "leaderboard": [
    {
      "rank": 1,
      "uuid": "123e4567-e89b-12d3-a456-426614174000",
      "name": "PlayerOne",
      "value": 45,
      "online": true,
      "stats": {
        "playersKilled": 120,
        "coloniesRaided": 35,
        "amountRaided": 150000,
        "warsWon": 45,
        "warStalemates": 3
      }
    }
  ]
}
```

**Examples:**
```bash
# Top 10 by wars won
curl -H "X-API-Key: your-key" \
  "http://localhost:8090/api/warstats/leaderboard?sort=warsWon&limit=10"

# Top 20 by colonies raided
curl -H "X-API-Key: your-key" \
  "http://localhost:8090/api/warstats/leaderboard?sort=coloniesRaided&limit=20"

# Top 5 by amount raided
curl -H "X-API-Key: your-key" \
  "http://localhost:8090/api/warstats/leaderboard?sort=amountRaided&limit=5"
```

---

### 4. Player Stats

**Endpoint:** `GET /api/warstats/player/{uuid}`

**Description:** Get statistics for a specific player by UUID

**Authentication:** Required if enabled

**URL Parameters:**
- `uuid` - Player's UUID (format: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`)

**Response (Player Found):**
```json
{
  "generated": "2025-01-18T14:30:00Z",
  "uuid": "123e4567-e89b-12d3-a456-426614174000",
  "found": true,
  "player": {
    "uuid": "123e4567-e89b-12d3-a456-426614174000",
    "name": "PlayerOne",
    "playersKilled": 45,
    "coloniesRaided": 12,
    "amountRaided": 50000,
    "warsWon": 8,
    "warStalemates": 2,
    "online": true
  }
}
```

**Response (Player Not Found):**
```json
{
  "generated": "2025-01-18T14:30:00Z",
  "uuid": "123e4567-e89b-12d3-a456-426614174000",
  "found": false,
  "error": "Player not online",
  "online": false
}
```

**Example:**
```bash
curl -H "X-API-Key: your-key" \
  "http://localhost:8090/api/warstats/player/123e4567-e89b-12d3-a456-426614174000"
```

---

### 5. Server Stats

**Endpoint:** `GET /api/warstats/server`

**Description:** Get server-wide statistics and totals

**Authentication:** Required if enabled

**Response:**
```json
{
  "generated": "2025-01-18T14:30:00Z",
  "serverName": "Minecraft Server",
  "onlinePlayers": 15,
  "maxPlayers": 50,
  "totals": {
    "warsWon": 234,
    "coloniesRaided": 89,
    "playersKilled": 456,
    "amountRaided": 1250000
  }
}
```

**Example:**
```bash
curl -H "X-API-Key: your-key" http://localhost:8090/api/warstats/server
```

---

## Error Responses

### 401 Unauthorized
**Cause:** Missing or invalid API key

```json
{
  "error": "Unauthorized. Please provide a valid API key in the 'X-API-Key' header.",
  "code": 401
}
```

### 429 Too Many Requests
**Cause:** Rate limit exceeded

```json
{
  "error": "Rate limit exceeded. Please try again later.",
  "code": 429
}
```

### 400 Bad Request
**Cause:** Invalid parameters

```json
{
  "error": "Invalid UUID format",
  "code": 400
}
```

### 405 Method Not Allowed
**Cause:** Non-GET request

```json
{
  "error": "Method not allowed. Only GET requests are supported.",
  "code": 405
}
```

### 500 Internal Server Error
**Cause:** Server error

```json
{
  "error": "Internal server error: ...",
  "code": 500
}
```

---

## Web Integration Examples

### JavaScript/Fetch API

```javascript
const API_KEY = 'your-secret-api-key';
const API_URL = 'http://your-server-ip:8090/api';

async function getLeaderboard(sortBy = 'warsWon', limit = 10) {
  const response = await fetch(
    `${API_URL}/warstats/leaderboard?sort=${sortBy}&limit=${limit}`,
    {
      headers: {
        'X-API-Key': API_KEY
      }
    }
  );
  
  if (!response.ok) {
    throw new Error(`API error: ${response.status}`);
  }
  
  return await response.json();
}

// Use it
getLeaderboard('warsWon', 10)
  .then(data => {
    console.log('Top 10 by wars won:', data.leaderboard);
  })
  .catch(error => {
    console.error('Error:', error);
  });
```

### jQuery

```javascript
$.ajax({
  url: 'http://your-server-ip:8090/api/warstats/leaderboard',
  method: 'GET',
  headers: {
    'X-API-Key': 'your-secret-api-key'
  },
  data: {
    sort: 'warsWon',
    limit: 10
  },
  success: function(data) {
    console.log('Leaderboard:', data);
  },
  error: function(xhr, status, error) {
    console.error('Error:', error);
  }
});
```

### PHP

```php
<?php
$api_key = 'your-secret-api-key';
$url = 'http://your-server-ip:8090/api/warstats/leaderboard?sort=warsWon&limit=10';

$ch = curl_init($url);
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
curl_setopt($ch, CURLOPT_HTTPHEADER, [
    'X-API-Key: ' . $api_key
]);

$response = curl_exec($ch);
$http_code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
curl_close($ch);

if ($http_code === 200) {
    $data = json_decode($response, true);
    print_r($data['leaderboard']);
} else {
    echo "Error: HTTP $http_code\n";
}
?>
```

### Python

```python
import requests

API_KEY = 'your-secret-api-key'
API_URL = 'http://your-server-ip:8090/api'

headers = {
    'X-API-Key': API_KEY
}

# Get leaderboard
response = requests.get(
    f'{API_URL}/warstats/leaderboard',
    params={'sort': 'warsWon', 'limit': 10},
    headers=headers
)

if response.status_code == 200:
    data = response.json()
    for player in data['leaderboard']:
        print(f"#{player['rank']}: {player['name']} - {player['value']} wars won")
else:
    print(f"Error: {response.status_code}")
```

---

## Troubleshooting

### API Not Starting

**Check logs for:**
```
Web API is disabled in configuration
```
**Solution:** Set `EnableWebAPI = true` in config

**Error:**
```
Failed to start Web API server on port 8090
```
**Solutions:**
- Check if port is already in use
- Try a different port (e.g., 8091, 9000)
- Ensure you have permission to bind to the port

### Authentication Issues

**Error:** `401 Unauthorized`

**Solutions:**
- Check `X-API-Key` header is present
- Verify API key matches configuration exactly
- Disable authentication for testing: `WebAPIRequireAuthentication = false`

### Rate Limiting

**Error:** `429 Too Many Requests`

**Solutions:**
- Wait 60 seconds and try again
- Increase `WebAPIRateLimitRequestsPerMinute` in config
- Set to `0` to disable (not recommended for public servers)

### CORS Issues (Browser)

**Error:** `CORS policy blocked`

**Solution:** The API includes CORS headers by default. If still having issues:
- Check browser console for specific error
- Ensure you're using the correct protocol (http/https)
- Consider using a reverse proxy

---

## Performance Considerations

### Online Players Only
- By default, the API only returns stats for **online players**
- This ensures fast response times
- For full player data, consider periodic caching

### Rate Limiting
- Default: 60 requests/minute per IP
- Prevents server overload
- Adjust based on your needs

### Caching Recommendations
- Cache leaderboard data on your website (5-minute refresh recommended)
- Use `generated` timestamp to show data freshness
- Implement client-side caching for better UX

---

## Security Checklist

- [ ] `EnableWebAPI` set to `true`
- [ ] `WebAPIRequireAuthentication` set to `true`
- [ ] Strong API key configured (32+ characters)
- [ ] Rate limiting enabled (60 requests/min minimum)
- [ ] Port properly firewalled if needed
- [ ] API key stored securely (not in public code)
- [ ] HTTPS enabled via reverse proxy (for public access)

---

## Support

For issues or questions:
1. Check server logs in `logs/latest.log`
2. Verify configuration in `config/warntax/minecolonytax.toml`
3. Test with `/api/health` endpoint first
4. Review this documentation

---

**Version:** 1.0  
**Last Updated:** January 2025  
**Compatibility:** MineColony Tax Addon v1.0+
