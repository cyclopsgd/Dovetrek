# Understanding the Bitmask Dynamic Programming Route Solver

This document explains how the DoveTrek route solver works, written for someone new to programming or unfamiliar with bitmask techniques.

## The Problem We're Solving

Imagine you're planning a hiking route with these constraints:
- There are ~17 checkpoints to visit
- Each checkpoint is only **open during certain hours**
- You must **start at 10:00** and **finish by 17:00**
- Walking between checkpoints takes time (based on distance and your speed)
- You need to spend some "dwell time" at each checkpoint (e.g., 7 minutes)

**Goal:** Visit as many checkpoints as possible while finishing on time.

This is a variant of the famous "Travelling Salesman Problem" - but with time windows!

---

## Part 1: What is a Bitmask?

### Binary Numbers Refresher

Computers store numbers in **binary** (base 2), using only 0s and 1s:

```
Decimal    Binary
   0    =  0000
   1    =  0001
   2    =  0010
   3    =  0011
   4    =  0100
   5    =  0101
   6    =  0110
   7    =  0111
   8    =  1000
```

Each position represents a power of 2:
```
Binary: 0 1 0 1
        │ │ │ └── 1 × 2⁰ = 1
        │ │ └──── 0 × 2¹ = 0
        │ └────── 1 × 2² = 4
        └──────── 0 × 2³ = 0
                          ───
                  Total =  5
```

### Using Binary to Represent Sets

Here's the clever bit: we can use each binary digit to represent **whether something is included in a set**.

Imagine we have 4 checkpoints: `CP1, CP2, CP3, CP4`

```
Binary: 0 0 0 0  = {} (empty set - visited nothing)
        │ │ │ │
        │ │ │ └── CP1: not visited
        │ │ └──── CP2: not visited
        │ └────── CP3: not visited
        └──────── CP4: not visited

Binary: 0 1 0 1  = {CP1, CP3} (visited CP1 and CP3)
        │ │ │ │
        │ │ │ └── CP1: visited (1)
        │ │ └──── CP2: not visited (0)
        │ └────── CP3: visited (1)
        └──────── CP4: not visited (0)

Binary: 1 1 1 1  = {CP1, CP2, CP3, CP4} (visited all)
```

This binary number is called a **bitmask** - it "masks" which items are in our set.

### Why Use Bitmasks?

1. **Compact**: A single number stores the entire set
2. **Fast**: Computers are incredibly fast at binary operations
3. **Easy to manipulate**: Adding/removing items is simple

---

## Part 2: Bitmask Operations

### Checking if a Checkpoint is Visited

To check if checkpoint `i` is in our visited set:

```javascript
// Is checkpoint i visited?
if (mask & (1 << i)) {
    // Yes, it's visited
}
```

**How this works:**
- `1 << i` creates a number with only bit `i` set (called "left shift")
- `&` is the AND operation - only returns 1 where BOTH inputs have 1

```
Example: Is CP2 (index 2) visited in mask = 0101 (decimal 5)?

  mask:     0101
  1 << 2:   0100   (shift 1 left by 2 positions)
            ────
  AND:      0100   ← Not zero, so YES, CP2 is visited!

Example: Is CP1 (index 1) visited in mask = 0101?

  mask:     0101
  1 << 1:   0010
            ────
  AND:      0000   ← Zero, so NO, CP1 is not visited
```

### Adding a Checkpoint to the Set

```javascript
// Add checkpoint i to the visited set
newMask = mask | (1 << i);
```

**How this works:**
- `|` is the OR operation - returns 1 where EITHER input has 1

```
Example: Add CP1 (index 1) to mask = 0101

  mask:     0101
  1 << 1:   0010
            ────
  OR:       0111   ← New mask with CP1 added!
```

### Counting Visited Checkpoints

```javascript
function countBits(n) {
    let count = 0;
    while (n > 0) {
        count += n & 1;  // Add 1 if last bit is set
        n >>= 1;         // Shift right (remove last bit)
    }
    return count;
}
```

```
Example: Count bits in 0101

  0101 & 1 = 1, count = 1, shift to 010
  010 & 1 = 0, count = 1, shift to 01
  01 & 1 = 1, count = 2, shift to 0
  Done! Count = 2
```

---

## Part 3: Dynamic Programming

### What is Dynamic Programming?

Dynamic Programming (DP) solves complex problems by:
1. Breaking them into smaller **subproblems**
2. Solving each subproblem **once**
3. Storing results to **avoid recalculation**

### Our Subproblem

For the route solver, our subproblem is:

> "What's the **earliest time** I can be ready to leave checkpoint `last`,
> having already visited the checkpoints in `mask`?"

We store this as: `dp[mask][last] = earliest_departure_time`

### The State Space

With `n` checkpoints:
- `mask` can be any value from 0 to 2ⁿ - 1 (all possible subsets)
- `last` can be any checkpoint from 0 to n-1

Total states: `2ⁿ × n`

For 17 checkpoints: `2¹⁷ × 17 = 131,072 × 17 ≈ 2.2 million states`

This sounds like a lot, but computers handle it in milliseconds!

---

## Part 4: The Algorithm Step by Step

### Initialization

Start from the starting point. For each checkpoint `i` we could visit first:

```
1. Calculate travel time from Start to checkpoint i
2. arrival_time = start_time + travel_time
3. Check if checkpoint i is open at arrival_time
   - If not open yet: wait until it opens
   - If closed: can't visit this checkpoint first
4. departure_time = arrival_time + wait_time + dwell_time
5. Store: dp[{i}][i] = departure_time
```

```
Example:
- Start time: 10:00 (600 minutes)
- Travel to CP2: 15 minutes
- CP2 opens at 10:00
- Dwell time: 7 minutes

arrival = 600 + 15 = 615 (10:15)
CP2 is open at 10:15 ✓
departure = 615 + 0 + 7 = 622 (10:22)

dp[0010][CP2] = 622
   ││││
   │││└─ CP1: not visited
   ││└── CP2: visited
   │└─── CP3: not visited
   └──── CP4: not visited
```

### Main Loop: Extending Routes

For each possible state (mask, last):

```
For each unvisited checkpoint 'next':
    1. Calculate travel time from 'last' to 'next'
    2. arrival_time = dp[mask][last] + travel_time
    3. Check if 'next' is open at arrival_time
    4. If reachable:
       - new_mask = mask | (1 << next)  // Add 'next' to visited set
       - departure = arrival + wait + dwell
       - If departure < dp[new_mask][next]:
           dp[new_mask][next] = departure  // Found a better route!
```

### Visual Example

Let's trace through a tiny example with 3 checkpoints:

```
Checkpoints: CP1, CP2, CP3
Start time: 10:00
Finish closes: 17:00

Travel times (minutes):
  Start → CP1: 20    CP1 → CP2: 15    CP2 → CP3: 25
  Start → CP2: 30    CP1 → CP3: 35
  Start → CP3: 45    CP2 → Finish: 20
  CP1 → Finish: 40   CP3 → Finish: 15

All checkpoints open 10:00-17:00 (no restrictions for simplicity)
Dwell time: 7 minutes
```

**Step 1: Initialize from Start**

```
Visit CP1 first:
  arrive: 10:00 + 20 = 10:20
  depart: 10:20 + 7 = 10:27
  dp[001][CP1] = 627 (10:27)

Visit CP2 first:
  arrive: 10:00 + 30 = 10:30
  depart: 10:30 + 7 = 10:37
  dp[010][CP2] = 637 (10:37)

Visit CP3 first:
  arrive: 10:00 + 45 = 10:45
  depart: 10:45 + 7 = 10:52
  dp[100][CP3] = 652 (10:52)
```

**Step 2: Extend from each state**

```
From dp[001][CP1] = 627 (at CP1, visited {CP1}):

  Go to CP2:
    arrive: 627 + 15 = 642 (10:42)
    depart: 642 + 7 = 649 (10:49)
    dp[011][CP2] = 649

  Go to CP3:
    arrive: 627 + 35 = 662 (11:02)
    depart: 662 + 7 = 669 (11:09)
    dp[101][CP3] = 669

From dp[010][CP2] = 637 (at CP2, visited {CP2}):

  Go to CP1:
    arrive: 637 + 15 = 652 (10:52)
    depart: 652 + 7 = 659 (10:59)
    dp[011][CP1] = 659

  Go to CP3:
    arrive: 637 + 25 = 662 (11:02)
    depart: 662 + 7 = 669 (11:09)
    dp[110][CP3] = 669

... and so on
```

**Step 3: Find Best Final State**

After filling the DP table, check which states can reach the finish:

```
For each state (mask, last):
    finish_time = dp[mask][last] + travel_to_finish
    if finish_time <= 17:00:
        Consider this as a solution

Choose the solution with:
  1. Most checkpoints visited (highest bit count in mask)
  2. If tied, earliest finish time
```

### Reconstructing the Route

We also store **parent pointers** to remember how we reached each state:

```
parent[new_mask][next] = (mask, last)
```

To get the route, trace backwards:
```
Route: [Finish]
current_state = best_final_state

while current_state is not start:
    Route.prepend(current_state.last)
    current_state = parent[current_state]

Route.prepend(Start)
```

---

## Part 5: Handling Time Windows

Real checkpoints have opening hours. This adds complexity:

```javascript
function getWaitTime(arrivalTime, checkpoint) {
    for (let slot of checkpoint.openSlots) {
        if (arrivalTime >= slot.open && arrivalTime <= slot.close) {
            return 0;  // Already open, no wait
        }
        if (arrivalTime < slot.open) {
            return slot.open - arrivalTime;  // Wait for it to open
        }
    }
    return INFINITY;  // Checkpoint has closed, can't visit
}
```

Example:
```
CP5 opens: 11:00-12:00, 14:00-15:00

Arrive at 10:30 → Wait 30 min until 11:00
Arrive at 11:15 → No wait (it's open)
Arrive at 12:30 → Wait until 14:00 (90 min)
Arrive at 15:30 → IMPOSSIBLE (closed for the day)
```

---

## Part 6: Why This Is Fast

### Compared to Brute Force

**Brute force** tries every possible route order:
- 17 checkpoints = 17! = 355,687,428,096,000 routes
- That's 355 trillion routes to check!

**Bitmask DP** reuses work:
- Only 2.2 million states
- Each state computed once
- ~160,000× faster than brute force

### Compared to Databricks/Spark

The bitmask DP runs in **milliseconds** on your phone.

Databricks is slow because:
1. **Cluster startup**: 30-60 seconds to spin up workers
2. **Job scheduling**: Overhead to distribute work
3. **Serialization**: Converting data for network transfer
4. **Small data penalty**: Spark is built for terabytes, not kilobytes

For this problem, a single CPU core is faster than a whole cluster!

---

## Part 7: Code Example (Simplified JavaScript)

```javascript
function solve(checkpoints, distances, speed, dwellTime, startTime, finishClose) {
    const n = checkpoints.length;
    const INF = 1e9;

    // DP table: dp[mask][last] = earliest departure time
    // Using flat array: index = mask * n + last
    const dp = new Array((1 << n) * n).fill(INF);
    const parent = new Array((1 << n) * n).fill(-1);

    // Initialize: try each checkpoint as first visit
    for (let i = 0; i < n; i++) {
        const travelTime = distances['Start'][checkpoints[i]] / speed * 60;
        const arriveTime = startTime + travelTime;
        const waitTime = getWaitTime(arriveTime, checkpoints[i]);

        if (waitTime < INF) {
            const departTime = arriveTime + waitTime + dwellTime;
            const mask = 1 << i;
            dp[mask * n + i] = departTime;
            parent[mask * n + i] = -2;  // Came from start
        }
    }

    // Main DP: extend routes
    for (let mask = 1; mask < (1 << n); mask++) {
        for (let last = 0; last < n; last++) {
            if (!(mask & (1 << last))) continue;  // 'last' not in mask

            const currentTime = dp[mask * n + last];
            if (currentTime >= INF) continue;

            // Try extending to each unvisited checkpoint
            for (let next = 0; next < n; next++) {
                if (mask & (1 << next)) continue;  // Already visited

                const travelTime = distances[checkpoints[last]][checkpoints[next]] / speed * 60;
                const arriveTime = currentTime + travelTime;
                const waitTime = getWaitTime(arriveTime, checkpoints[next]);

                if (waitTime >= INF) continue;  // Can't reach in time

                const departTime = arriveTime + waitTime + dwellTime;
                const newMask = mask | (1 << next);
                const newIdx = newMask * n + next;

                if (departTime < dp[newIdx]) {
                    dp[newIdx] = departTime;
                    parent[newIdx] = mask * n + last;
                }
            }
        }
    }

    // Find best solution that can reach finish
    let bestMask = 0, bestLast = -1, bestFinish = INF;

    for (let mask = 0; mask < (1 << n); mask++) {
        for (let last = 0; last < n; last++) {
            if (!(mask & (1 << last))) continue;

            const currentTime = dp[mask * n + last];
            if (currentTime >= INF) continue;

            const travelTime = distances[checkpoints[last]]['Finish'] / speed * 60;
            const finishTime = currentTime + travelTime;

            if (finishTime <= finishClose) {
                const count = countBits(mask);
                const bestCount = countBits(bestMask);

                if (count > bestCount || (count === bestCount && finishTime < bestFinish)) {
                    bestMask = mask;
                    bestLast = last;
                    bestFinish = finishTime;
                }
            }
        }
    }

    // Reconstruct route from parent pointers
    const route = reconstructRoute(parent, checkpoints, bestMask, bestLast, n);

    return {
        route: ['Start', ...route, 'Finish'],
        checkpointsVisited: countBits(bestMask),
        finishTime: bestFinish
    };
}
```

---

## Summary

| Concept | What it means |
|---------|---------------|
| **Bitmask** | A number where each bit represents "is this item included?" |
| **State** | `(visited_checkpoints, last_checkpoint)` |
| **DP Table** | Stores "best time to reach this state" |
| **Transition** | "If I'm at state X, what states can I reach?" |
| **Time Complexity** | O(2ⁿ × n²) - fast for n ≤ 20 |

The key insight is that we don't care about the **order** we visited checkpoints - only **which** checkpoints we've visited and **where** we are now. This reduces trillions of possibilities to just millions!

---

## Further Reading

- [Bitmask DP Tutorial (Competitive Programming)](https://cp-algorithms.com/algebra/all-submasks.html)
- [Travelling Salesman Problem](https://en.wikipedia.org/wiki/Travelling_salesman_problem)
- [Dynamic Programming](https://en.wikipedia.org/wiki/Dynamic_programming)
