# Tax Policies

## Overview

Every colony in War 'N Taxes operates under a tax policy. The policy controls two things at once: how much revenue your colony generates from taxes, and how that taxation affects your citizens' happiness. These two values always trade off against each other — policies that raise more money hurt morale, and policies that keep citizens happy bring in less gold.

The default policy is Normal, which applies no modifiers. Colonies that never change their policy behave exactly as they would without this system enabled.

Policies persist across server restarts and are stored per colony. A colony manager or owner can change the policy at any time using the command below.

---

## Changing Your Colony's Policy

You must be a colony owner or colony manager to change the tax policy.

```
/wnt taxpolicy set <policy>
```

Replace `<policy>` with one of: `NORMAL`, `LOW`, `HIGH`, or `WAR_ECONOMY`.

**Other commands:**

| Command | What it does |
|---|---|
| `/wnt taxpolicy` | Shows your colony's current policy and its active modifiers |
| `/wnt taxpolicy list` | Lists all available policies with their modifiers |
| `/wnt taxpolicy set <policy>` | Changes your colony's tax policy |
| `/wnt taxpolicy help` | Shows command help |

---

## Policy Types

### Normal

The baseline. No revenue modifier, no happiness modifier. This is the default for all colonies.

Use this when you want stable, predictable taxes without any side effects.

### Low Tax

Reduces tax revenue but improves citizen happiness over time.

- Default revenue modifier: **-25%**
- Default happiness modifier: **+0.20** (additive to happiness growth)

Use this when your colony is recovering from war or a raid, when happiness has dropped low, or when you want to build goodwill before a difficult period. The happiness benefit can reduce the chance of negative random events that require low happiness as a trigger condition.

### High Tax

Increases tax revenue but reduces citizen happiness.

- Default revenue modifier: **+25%**
- Default happiness modifier: **-0.15** (additive to happiness growth)

Use this when you need to fill your treasury quickly and can absorb the happiness cost. Be aware that staying on High Tax for extended periods can trigger negative random events such as Corrupt Official and Guard Desertion, both of which require High or War Economy policy as a condition.

### War Economy

Maximum revenue at a significant happiness cost. Intended for active wartime use.

- Default revenue modifier: **+50%**
- Default happiness modifier: **-0.25** (additive to happiness growth)

Use this during active wars when you need sustained treasury income to cover war expenses. This policy also enables the War Profiteering random event, which can add a further temporary revenue bonus. The happiness penalty is severe — monitor your colony happiness if you plan to hold this policy for more than a few tax cycles. Events like Labor Strike and Guard Desertion both list War Economy policy as a trigger condition.

---

## How the Modifiers Work

The revenue modifier is multiplicative. A +25% modifier means each tax cycle pays out 1.25 times what it otherwise would. A -25% modifier means 0.75 times.

The happiness modifier is additive to your colony's average happiness growth. The colony happiness scale runs from 0 to 10. A modifier of +0.20 nudges that average upward; -0.25 nudges it downward. This interacts with the existing happiness system — it does not override it or set a fixed value.

---

## Random Event Interactions

Several random events check your current tax policy as a trigger condition:

- **War Profiteering** — only triggers when War Economy policy is active; adds a temporary +35% tax bonus
- **Corrupt Official** — only triggers on High or War Economy policy; applies -25% tax and a small happiness penalty
- **Guard Desertion** — requires happiness below 3.0 and High or War Economy policy; reduces tax and happiness
- **Labor Strike** — requires happiness below 3.5, 30+ citizens, and High or War Economy policy; temporarily disables a portion of your workforce

Staying on a high-revenue policy for a long time without managing happiness increases the risk of these negative events firing.

---

## Configuration

Server administrators can adjust all policy modifiers in the config file (`config/warntax/minecolonytax.toml`) under the `Tax Policies` section. The system can also be disabled entirely with the `EnableTaxPolicies` key, in which case all colonies behave as if they are on Normal policy.

| Config Key | Default | Description |
|---|---|---|
| `EnableTaxPolicies` | `true` | Enables or disables the tax policy system |
| `LowPolicyRevenueModifier` | `-0.25` | Revenue modifier for Low Tax (negative = less) |
| `LowPolicyHappinessModifier` | `0.20` | Happiness modifier for Low Tax (positive = better) |
| `HighPolicyRevenueModifier` | `0.25` | Revenue modifier for High Tax |
| `HighPolicyHappinessModifier` | `-0.15` | Happiness modifier for High Tax |
| `WarPolicyRevenueModifier` | `0.50` | Revenue modifier for War Economy |
| `WarPolicyHappinessModifier` | `-0.25` | Happiness modifier for War Economy |

All revenue modifier values are fractions added to 1.0 at runtime (so `-0.25` becomes a 0.75 multiplier). Happiness modifiers are added directly to the colony's happiness growth calculation.
