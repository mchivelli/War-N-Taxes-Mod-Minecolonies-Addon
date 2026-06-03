Wrote the audit report to [audit/CODEX_INDEPENDENT.md](C:/Dev/War-N-Taxes-Mod---Minecolonies-Addon/audit/CODEX_INDEPENDENT.md). Static analysis only; I did not run Gradle.

Top 10 findings:

1. `CRIT`: Negative `ClaimTaxPacket` amounts mint tax balance, enabling unbounded currency dupe.
2. `CRIT`: Vassal tribute claim awards both overlord colony tax and direct player payout.
3. `HIGH`: `UpdatePlayerTaxPermissionPacket` lets any client alter tax permissions.
4. `HIGH`: GUI tax claim packet ignores `TaxPermissionManager` restrictions.
5. `HIGH`: Officers can buy investments through packet despite owner-only command policy.
6. `HIGH`: Treasury tax-balance transfers persist treasury but not tax ledger, enabling crash dupes/losses.
7. `HIGH`: Invalid `TreasuryActionPacket` enum ordinal can crash packet decoding.
8. `HIGH`: Multiple C2S handlers null-deref MineColonies ranks for non-members.
9. `HIGH`: Active war restoration can skip expired wars and delete the only saved state.
10. `HIGH`: PvP uses a non-daemon scheduled executor that is never shut down.