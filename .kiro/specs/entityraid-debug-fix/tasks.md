# Implementation Plan

- [x] 1. Add comprehensive debug logging system





  - Create debug configuration options in TaxConfig for EntityRaid logging
  - Implement structured logging methods with different verbosity levels
  - Add logging to all major EntityRaid detection and filtering steps
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_

- [x] 2. Enhance entity detection and filtering pipeline




- [x] 2.1 Improve recruit entity identification


  - Enhance `isRecruitEntity()` method with multiple detection strategies
  - Add logging for each detection method attempted
  - Test with all recruit entity types from the whitelist
  - _Requirements: 1.1, 2.5_

- [x] 2.2 Fix grace period calculation in `isRecentlyRecruited()`


  - Replace unreliable `entity.tickCount` with more accurate timing methods
  - Add recruitment time tracking system for better grace period detection
  - Implement fallback mechanisms for timing calculation
  - _Requirements: 1.4, 2.3_




- [x] 2.3 Enhance alliance detection system





  - Implement reflection method caching for Recruits mod integration
  - Add multiple fallback mechanisms for alliance detection


  - Improve error handling and graceful degradation
  - Add comprehensive logging for alliance check results


  - _Requirements: 1.3, 2.4, 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 2.4 Add comprehensive filtering pipeline logging
  - Modify `shouldRecruitTriggerRaid()` to log each filter step
  - Add detailed reasoning for why entities are included or excluded
  - Implement filter step tracking with pass/fail reasons
  - _Requirements: 2.2, 2.3_

- [ ] 3. Fix EntityRaid triggering logic
- [x] 3.1 Add prerequisite validation logging
  - Log colony owner online status checks
  - Log entity count vs threshold comparisons
  - Log existing raid and cooldown status checks
  - Verify entity type whitelist matching
  - _Requirements: 1.1, 1.6, 1.7, 2.6_

- [x] 3.2 Enhance main detection loop in `checkForEntityRaid()`
  - Add comprehensive logging for entity detection process
  - Log the number of entities found vs entities that pass filtering
  - Add debugging output for whitelist type conversion
  - Implement step-by-step detection process logging
  - _Requirements: 1.1, 2.1_

- [ ] 3.3 Test and validate entity filtering with debug output
  - Create test scenarios with different recruit configurations
  - Verify filtering logic works correctly for owned, allied, and enemy recruits
  - Test boundary detection and grace period functionality
  - _Requirements: 1.2, 1.3, 1.4, 1.5_

- [ ] 4. Implement per-player glow effect system
- [ ] 4.1 Create custom network packet for glow effects
  - Design `EntityGlowPacket` class for client-server communication
  - Implement packet serialization and deserialization
  - Create packet handler registration and routing
  - _Requirements: 3.1, 3.2_

- [ ] 4.2 Implement client-side glow effect renderer
  - Create `EntityRaidRenderer` for client-side visual effects
  - Implement entity glow effect overlay system
  - Add glow effect lifecycle management on client
  - _Requirements: 3.1, 3.2_

- [ ] 4.3 Create server-side glow effect manager
  - Implement `GlowEffectManager` for tracking glow states
  - Replace universal `MobEffects.GLOWING` with targeted packet system
  - Add proper cleanup when raids end or players disconnect
  - _Requirements: 3.3, 3.4, 3.5_

- [ ] 4.4 Update `applyGlowEffectToEntities()` method
  - Replace current universal glow approach with packet-based system
  - Send glow packets only to colony owners
  - Implement proper glow effect cleanup
  - _Requirements: 3.1, 3.2, 3.3_

- [ ] 5. Enhance raid state management and monitoring
- [ ] 5.1 Improve `ActiveEntityRaid` data structure
  - Add per-entity tracking with `EntityRaidData` inner class
  - Implement current raid state tracking
  - Add entity count monitoring for bossbar updates
  - _Requirements: 4.1, 4.2, 4.3_

- [ ] 5.2 Update bossbar system for accurate real-time information
  - Modify bossbar updates to reflect actual entity states
  - Implement proper raid state transitions (DETECTED, RAIDING, LEAVING)
  - Add accurate time remaining and tax loss calculations
  - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [ ] 5.3 Enhance entity boundary monitoring
  - Improve `monitorEntityBoundaries()` with better state tracking
  - Add logging for entity position changes and boundary crossings
  - Implement more accurate alive entity counting
  - _Requirements: 4.4, 4.5_

- [ ] 6. Add performance optimizations and stability improvements
- [ ] 6.1 Implement reflection method caching
  - Create `ReflectionCache` class for Recruits mod method caching
  - Add initialization and error handling for reflection operations
  - Implement safe fallback mechanisms when reflection fails
  - _Requirements: 5.1, 5.2, 5.3, 6.3_

- [ ] 6.2 Improve resource cleanup and memory management
  - Enhance `ActiveEntityRaid.cleanup()` method
  - Add proper cleanup for glow effects and bossbar resources
  - Implement concurrent collection safety for multi-colony operations
  - _Requirements: 6.1, 6.2, 6.4, 6.5_

- [ ] 6.3 Add configuration options for debug and performance tuning
  - Add debug logging configuration to `TaxConfig`
  - Implement configurable debug levels (Basic, Detailed, Verbose)
  - Add performance monitoring configuration options
  - _Requirements: 2.1, 6.2_

- [ ] 7. Create comprehensive testing and validation tools
- [ ] 7.1 Implement admin debug commands
  - Create `/entityraid debug` command for colony-specific debugging
  - Add `/entityraid simulate` command for testing raid scenarios
  - Implement `/entityraid glow` command for testing glow effects
  - _Requirements: 2.1, 2.2, 2.3_

- [ ] 7.2 Add automated testing scenarios
  - Create test cases for different recruit ownership scenarios
  - Implement alliance detection testing with mock entities
  - Add boundary detection and grace period testing
  - _Requirements: 1.2, 1.3, 1.4, 1.5_

- [ ] 7.3 Validate complete system integration
  - Test end-to-end raid triggering with debug logging enabled
  - Verify per-player glow effects work correctly
  - Test system performance with multiple active colonies
  - Validate error handling and graceful degradation
  - _Requirements: 1.1, 3.1, 3.2, 5.4, 5.5_