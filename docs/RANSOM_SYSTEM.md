# Ransom System Documentation

## Overview

The **Ransom System** allows attackers during raids to demand payment from defenders who die. This creates a strategic alternative to combat - victims can pay to end the raid peacefully or fight back.

## Features

### Ransom Offers

When a **colony owner or officer** dies during a raid (killed by the raider), a ransom offer is triggered:

1. **Attacker** receives confirmation: "Ransom demand sent!"
2. **Victim** receives a clickable chat message with **[ACCEPT]** and **[DENY]** buttons

### Accept Ransom
- Payment is deducted from colony tax balance
- Raid ends immediately
- 24-hour raid immunity granted
- Cooldown starts (30 min before same attacker can demand again)

### Deny Ransom
- Raid continues
- Cooldown still starts
- Victim can continue fighting

### Timeout
- Offers expire after 60 seconds (configurable)
- Expired offers = raid continues

---

## Commands

| Command | Description |
|---------|-------------|
| `/wnt ransom accept` | Accept pending ransom offer |
| `/wnt ransom deny` | Deny pending ransom offer |
| `/wnt ransom status` | Check pending offer or immunity status |

---

## Config Options

All values are in `warntax-common.toml`:

```toml
[Tax Expansion - War Mechanics."Ransom System"]
EnableRansomSystem = true
RansomDefaultPercent = 0.15     # 15% of victim's tax balance
RansomMinAmount = 100           # Minimum ransom
RansomMaxAmount = 10000         # Maximum ransom
RansomTimeoutSeconds = 60       # Time to respond
RansomCooldownMinutes = 30      # Before same attacker can demand again
RansomImmunityAfterPaymentHours = 24  # Protection after paying
```

---

## Raid Immunity

After paying ransom, the colony owner gains **raid immunity**:

- Duration: 24 hours (configurable)
- Any raid attempt will show: `"This colony is protected from raids! (X hours remaining)"`
- Immunity is stored persistently (survives server restarts)

---

## Why Only Online Defenders?

Ransom offers are **only sent when the victim is online**. This ensures:

1. **Fair gameplay**: Players can't be "ransomed" while AFK
2. **No auto-pay**: Victims must actively accept/deny
3. **Reduced griefing**: Offline colonies can't be forced to pay

For offline raids, the normal raid mechanics apply (reduced steal % if configured).

---

## Manual Testing Guide

### Test 1: Basic Ransom Offer
1. Player A starts raid on Player B's colony
2. Player A kills Player B (colony owner)
3. **Expected**: Player B receives clickable ransom message
4. Player B clicks **[ACCEPT]**
5. **Expected**: Raid ends, tax deducted, immunity granted

### Test 2: Deny Ransom
1. Same setup as Test 1
2. Player B clicks **[DENY]**
3. **Expected**: Raid continues, message sent to both players

### Test 3: Timeout
1. Same setup as Test 1
2. Wait 60 seconds without responding
3. **Expected**: "Ransom offer expired" message, raid continues

### Test 4: Immunity Check
1. After paying ransom, try to raid same colony again
2. **Expected**: "Colony is protected from raids!" error

### Test 5: Server Restart
1. Pay ransom, check immunity with `/wnt ransom status`
2. Restart server
3. Run `/wnt ransom status` again
4. **Expected**: Immunity persists

---

## Files

| File | Description |
|------|-------------|
| `economy/RansomManager.java` | Core ransom logic, offers, immunity, persistence |
| `commands/RansomCommand.java` | `/wnt ransom` commands |
| `event/PvPKillEconomyHandler.java` | Death trigger integration |
| `raid/RaidManager.java` | Immunity check for raids |
