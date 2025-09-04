# Requirements Document

## Introduction

The EntityRaid feature is a sophisticated system that triggers raids when enemy entities (particularly from the Recruits mod) approach colonies. While the core framework is implemented, there are critical issues preventing proper functionality: raids are not triggering when they should, and the glow effect is visible to all players instead of just the colony owner. This spec addresses debugging and fixing these core issues to ensure the system works as intended.

## Requirements

### Requirement 1: EntityRaid Triggering System

**User Story:** As a colony owner, I want enemy recruits attacking my colony to trigger an EntityRaid so that I'm alerted to threats and appropriate defensive measures are activated.

#### Acceptance Criteria

1. WHEN 3 or more enemy recruits are within 50 blocks of a colony center AND the colony owner is online THEN the system SHALL trigger an EntityRaid
2. WHEN recruits are owned by the colony owner THEN the system SHALL NOT trigger an EntityRaid (friendly units)
3. WHEN recruits are allied to the colony through the Recruits mod diplomacy system THEN the system SHALL NOT trigger an EntityRaid
4. WHEN recruits were spawned/recruited within the last 10 seconds THEN the system SHALL NOT trigger an EntityRaid (grace period)
5. WHEN recruits are spawned inside colony boundaries THEN the system SHALL NOT trigger an EntityRaid
6. WHEN the colony owner is offline THEN the system SHALL NOT trigger an EntityRaid
7. WHEN the colony already has an active EntityRaid or regular raid THEN the system SHALL NOT trigger additional EntityRaids

### Requirement 2: Debug Logging and Diagnostics

**User Story:** As a developer, I want comprehensive logging of the EntityRaid detection process so that I can identify why raids are not triggering when expected.

#### Acceptance Criteria

1. WHEN the system checks for EntityRaids THEN it SHALL log the number of entities detected near each colony
2. WHEN filtering entities for raid eligibility THEN the system SHALL log each filter step and its result
3. WHEN an entity is excluded from triggering a raid THEN the system SHALL log the specific reason (owned, allied, grace period, etc.)
4. WHEN alliance detection is performed THEN the system SHALL log the result of diplomacy checks
5. WHEN entity type detection occurs THEN the system SHALL log whether entities are correctly identified as recruits
6. WHEN prerequisite checks fail THEN the system SHALL log which specific requirement was not met

### Requirement 3: Owner-Only Glow Effect Visibility

**User Story:** As a colony owner, I want to see glowing effects on threatening entities during an EntityRaid while other players see normal entities, so that I can identify threats without revealing tactical information to others.

#### Acceptance Criteria

1. WHEN an EntityRaid is active THEN only the colony owner SHALL see glowing effects on threatening entities
2. WHEN an EntityRaid is active THEN non-owner players SHALL see normal entity rendering without glow effects
3. WHEN an EntityRaid ends THEN all glow effects SHALL be properly removed from all players
4. WHEN the colony owner goes offline during an EntityRaid THEN glow effects SHALL be properly cleaned up
5. WHEN the colony owner comes online during an active EntityRaid THEN glow effects SHALL be applied to threatening entities

### Requirement 4: EntityRaid State Management

**User Story:** As a colony owner, I want accurate real-time information about active EntityRaids through the bossbar system so that I can track the threat status and respond appropriately.

#### Acceptance Criteria

1. WHEN an EntityRaid is triggered THEN a bossbar SHALL appear showing raid state, entity count, time remaining, and potential tax loss
2. WHEN entities move between colony boundaries THEN the bossbar SHALL update to reflect current raid state (DETECTED, RAIDING, LEAVING)
3. WHEN entities are eliminated THEN the bossbar SHALL update the alive entity count in real-time
4. WHEN all threatening entities are eliminated or leave permanently THEN the EntityRaid SHALL end and the bossbar SHALL be removed
5. WHEN the boundary timer expires after entities leave THEN the EntityRaid SHALL end automatically

### Requirement 5: Recruits Mod Integration Reliability

**User Story:** As a system administrator, I want the EntityRaid system to reliably integrate with the Recruits mod's ownership and diplomacy systems so that friendly units are properly excluded from triggering raids.

#### Acceptance Criteria

1. WHEN checking recruit ownership THEN the system SHALL use reflection to access Recruits mod methods safely
2. WHEN Recruits mod methods are unavailable THEN the system SHALL handle exceptions gracefully and log appropriate warnings
3. WHEN alliance detection fails due to missing teams THEN the system SHALL default to treating entities as potential threats
4. WHEN recruit entity detection occurs THEN the system SHALL correctly identify all recruit entity types from the whitelist
5. WHEN the Recruits mod is not present THEN the system SHALL continue to function with basic entity detection

### Requirement 6: Performance and Stability

**User Story:** As a server administrator, I want the EntityRaid system to perform efficiently without causing server lag or memory leaks so that gameplay remains smooth.

#### Acceptance Criteria

1. WHEN monitoring active EntityRaids THEN the system SHALL clean up completed raids and their resources properly
2. WHEN checking for entities THEN the system SHALL respect the configured check interval to avoid excessive processing
3. WHEN using reflection for Recruits mod integration THEN the system SHALL cache method references to avoid repeated lookups
4. WHEN handling entity UUIDs THEN the system SHALL properly manage collections to prevent memory leaks
5. WHEN multiple colonies are checked simultaneously THEN the system SHALL handle concurrent operations safely