# Dealing with Write Conflicts Deep Dive - What Do You Do?

## Contents

- [Dealing with Write Conflicts Deep Dive - What Do You Do?](#dealing-with-write-conflicts-deep-dive---what-do-you-do)
    - [Core Mental Model](#core-mental-model)
    - [Types of Write Conflicts](#types-of-write-conflicts)
    - [Conflict Avoidance Strategies](#conflict-avoidance-strategies)
    - [Conflict Resolution: Last-Write-Wins (LWW)](#conflict-resolution-last-write-wins-lww)
    - [Conflict Resolution: Multi-Value (Keep All)](#conflict-resolution-multi-value-keep-all)
    - [Conflict Resolution: Custom Merge Functions](#conflict-resolution-custom-merge-functions)
    - [Conflict Resolution: Three-Way Merge](#conflict-resolution-three-way-merge)
    - [Conflict Resolution: Operational Transformation](#conflict-resolution-operational-transformation)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
    - [MIND MAP: CONFLICT RESOLUTION](#mind-map-conflict-resolution)
    - [EMOTIONAL ANCHORS](#emotional-anchors)

## Core Mental Model

```text
Write Conflicts = Concurrent Modifications to Same Data

Primary Source of Truth Model:
┌────────────────────────────────────────┐
│ Single-Leader: Conflicts IMPOSSIBLE    │
│ (serialized through leader)            │
└────────────────────────────────────────┘

Distributed Truth Model:
┌────────────────────────────────────────┐
│ Multi-Leader/Leaderless:               │
│ Conflicts INEVITABLE                   │
│                                         │
│ Strategy: Detect → Resolve → Converge  │
└────────────────────────────────────────┘
```

**The Conflict Resolution Spectrum:**
```
┌──────────────────────────────────────────────────────────┐
│                                                           │
│  SIMPLE ←────────────────────────────→ SOPHISTICATED     │
│  (data loss)                          (data preservation) │
│                                                           │
│  Last-Write      Multi-Value   Custom     Operational    │
│  Wins            (Keep All)    Merge      Transform      │
│  (LWW)                         Functions  (OT)           │
│                                                           │
│  ↓               ↓              ↓          ↓             │
│  Automatic      Manual         Semi-Auto  Automatic      │
│  Fast           Slow           Medium     Complex        │
│  Loses data     Keeps all      Smart      Fancy          │
│                                                           │
└──────────────────────────────────────────────────────────┘
```

---

## Types of Write Conflicts

🎓 **PROFESSOR**: Not all conflicts are equal. Taxonomy of conflicts:

### 1. Direct Overwrite Conflict

```
Timeline:
────────
t0: Initial state: user.name = "Alice"

t1: Node1: user.name ← "Alice Smith"
    Node2: user.name ← "Alice Johnson"  (concurrent)

t2: After replication, what is user.name?

This is the SIMPLEST conflict type
```

### 2. Lost Update Conflict

```python
class LostUpdateConflict:
    """
    Read-Modify-Write cycle where update is lost
    """

    def demonstrate(self):
        # Initial: counter = 100

        # Node1:
        value1 = read("counter")    # Reads 100
        new_value1 = value1 + 1     # Calculates 101
        write("counter", 101)       # Writes 101

        # Node2 (concurrent):
        value2 = read("counter")    # Reads 100
        new_value2 = value2 + 1     # Calculates 101
        write("counter", 101)       # Writes 101

        # Final state: 101 (should be 102!)
        # One increment was LOST
```

### 3. Write Skew

```sql
-- Example: Meeting room booking

-- Initial state: Room A has 0 bookings

-- Transaction 1:
BEGIN;
SELECT COUNT(*) FROM bookings WHERE room_id = 'A';  -- Returns 0
-- Logic: If count < 2, allow booking
INSERT INTO bookings (room_id, time, user) VALUES ('A', '2pm', 'Alice');
COMMIT;

-- Transaction 2 (concurrent):
BEGIN;
SELECT COUNT(*) FROM bookings WHERE room_id = 'A';  -- Returns 0
INSERT INTO bookings (room_id, time, user) VALUES ('A', '2pm', 'Bob');
COMMIT;

-- Final state: 2 bookings for same room at same time!
-- Both transactions saw valid state, but together they violated constraint
```

### 4. Delete-Update Conflict

```python
class DeleteUpdateConflict:
    """
    One node deletes, another updates
    """

    def demonstrate(self):
        # Node1: DELETE user WHERE id = 123
        # Node2: UPDATE user SET email='new@example.com' WHERE id = 123

        # After sync:
        # Option 1: Delete wins → User gone (update lost)
        # Option 2: Update wins → User resurrects (zombie record!)
        # Option 3: Keep both → Application decides
```

🏗️ **ARCHITECT**: Real-world conflict frequencies:

```java
/**
 * Conflict statistics from production (multi-datacenter setup)
 */
public class ConflictStatistics {

    /**
     * Observed conflict rates (per 1M writes):
     *
     * Hot user records (celebrities):     500-1000 conflicts
     * Normal user records:                1-10 conflicts
     * Shared resources (inventory):       100-500 conflicts
     * Append-only logs:                   0 conflicts
     *
     * Lesson: Design schema to minimize conflicts!
     */

    public void reduceConflicts() {
        // Anti-pattern: Single "likes_count" column
        // Every like increments same field → conflicts!

        // Better: Append-only likes table
        // Each like is separate row → no conflicts
        // Count on read (or materialized view)
    }
}
```

---

## Conflict Avoidance Strategies

🎓 **PROFESSOR**: The best conflict resolution is conflict AVOIDANCE:

### Strategy 1: Partition Data to Avoid Concurrent Writes

```
Assign each user to a "home" datacenter
All writes for that user go to their home datacenter

┌──────────────────────────────────────┐
│ US Users  → US Datacenter            │
│ EU Users  → EU Datacenter            │
│ APAC Users → APAC Datacenter         │
└──────────────────────────────────────┘

Conflicts only occur if:
1. User travels (changes datacenter)
2. Multiple users modify shared resource
```

🏗️ **ARCHITECT**: Implementation:

```python
class DatacenterAffinity:
    """
    Route writes based on data ownership
    """

    def __init__(self):
        self.user_to_datacenter = {
            # user_id → preferred datacenter
            "alice": "us-east-1",
            "bob": "eu-west-1",
            "charlie": "ap-southeast-1"
        }

    def write_user_data(self, user_id, data):
        # Determine user's home datacenter
        home_dc = self.user_to_datacenter.get(user_id)

        if not home_dc:
            # New user: assign to nearest datacenter
            home_dc = self.determine_nearest_dc()
            self.user_to_datacenter[user_id] = home_dc

        # Route write to home datacenter
        datacenter = self.get_datacenter(home_dc)
        datacenter.write(f"user:{user_id}", data)

        # No conflicts because all writes for this user go to same place!

    def handle_user_travel(self, user_id, new_location):
        """
        User travels to different region
        """
        # Option 1: Stick to home datacenter (higher latency)
        # Pro: No conflicts
        # Con: Poor UX (slow writes)

        # Option 2: Switch home datacenter
        # Pro: Low latency
        # Con: Risk of conflicts during migration

        # Option 3: Temporary writes to new location, async sync to home
        # Pro: Low latency, eventual consistency
        # Con: Conflict resolution needed
```

### Strategy 2: Immutable Data (Append-Only)

```java
/**
 * Avoid updates, only append
 */
@Entity
public class EventLog {

    // Anti-pattern: UPDATE counter
    // ✗ Conflicts when multiple increments

    // Better pattern: INSERT new events
    // ✓ No conflicts, each event is independent

    @Id
    private UUID eventId;  // Unique per event

    private String userId;
    private String eventType;  // "like", "view", "purchase"
    private Instant timestamp;

    // To get count: SELECT COUNT(*) WHERE eventType = 'like'
    // No conflicts because each INSERT is independent!
}
```

### Strategy 3: CRDTs (Conflict-Free Replicated Data Types)

```
Use specially-designed data structures that merge automatically
(Covered in detail in CRDT deep-dive)

Examples:
- G-Counter: Grow-only counter (only increments)
- PN-Counter: Increment/decrement counter
- OR-Set: Add/remove items from set
- LWW-Register: Last-write-wins register

Property: ANY order of operations → SAME final state
```

**Interview Gold**: "In distributed systems, schema design is conflict prevention. Append-only beats update-in-place. Event sourcing beats state mutation."

---

## Conflict Resolution: Last-Write-Wins (LWW)

🎓 **PROFESSOR**: The simplest (and most dangerous) resolution strategy:

```
Rule: Pick the write with the latest timestamp, discard others

Pros:
- Simple to implement
- Automatic (no human intervention)
- Deterministic (all nodes converge to same value)

Cons:
- DATA LOSS (one write always discarded)
- Depends on clock synchronization
- "Last" is ambiguous in distributed system
```

### Implementation

```python
class LastWriteWins:
    """
    LWW implementation with hybrid logical clocks
    """

    def __init__(self, node_id):
        self.node_id = node_id
        self.logical_clock = 0

    def write(self, key, value):
        # Generate timestamp (hybrid: physical + logical)
        timestamp = {
            "physical": time.time(),  # Wall-clock time
            "logical": self.logical_clock,  # Logical counter
            "node_id": self.node_id  # Tie-breaker
        }

        self.logical_clock += 1

        return {
            "key": key,
            "value": value,
            "timestamp": timestamp
        }

    def resolve_conflict(self, write1, write2):
        """
        Pick the write with greater timestamp
        """
        # Compare physical timestamps
        if write1["timestamp"]["physical"] > write2["timestamp"]["physical"]:
            return write1
        elif write2["timestamp"]["physical"] > write1["timestamp"]["physical"]:
            return write2

        # Physical timestamps equal, compare logical
        if write1["timestamp"]["logical"] > write2["timestamp"]["logical"]:
            return write1
        elif write2["timestamp"]["logical"] > write1["timestamp"]["logical"]:
            return write2

        # Both equal, use node_id as tie-breaker
        if write1["timestamp"]["node_id"] > write2["timestamp"]["node_id"]:
            return write1
        else:
            return write2

        # One write is DISCARDED (data loss!)
```

🏗️ **ARCHITECT**: When LWW is acceptable:

```java
@Service
public class LWWUseCases {

    /**
     * Acceptable: Cache invalidation
     */
    public void cacheInvalidation() {
        // Multiple nodes invalidate same cache key
        // Last invalidation wins
        // No data loss (cache will be refilled on read)
    }

    /**
     * Acceptable: User preferences
     */
    public void userPreferences() {
        // User changes theme from multiple devices
        // Most recent theme wins
        // User probably wants latest choice anyway
    }

    /**
     * DANGEROUS: Shopping cart
     */
    public void shoppingCart() {
        // Device 1: Add "Book A"
        // Device 2: Add "Book B" (later timestamp)

        // LWW: Device 2 wins → Cart has only "Book B"
        // ✗ "Book A" disappeared! (data loss)

        // Better: Multi-value or merge function
    }

    /**
     * DANGEROUS: Counters
     */
    public void counters() {
        // Node 1: counter = 100 → 101
        // Node 2: counter = 100 → 101 (concurrent)

        // LWW: One wins → counter = 101 (should be 102!)
        // ✗ One increment lost

        // Better: CRDT counter
    }
}
```

### Hybrid Logical Clocks (HLC)

```python
class HybridLogicalClock:
    """
    Best of both worlds: Physical time + logical counter
    Used by CockroachDB, MongoDB
    """

    def __init__(self):
        self.logical_time = 0
        self.physical_time = 0

    def send_or_local_event(self):
        """
        Generate timestamp for local write or outgoing message
        """
        physical_now = int(time.time() * 1000)  # milliseconds

        if physical_now > self.physical_time:
            # Physical clock advanced
            self.physical_time = physical_now
            self.logical_time = 0
        else:
            # Physical clock hasn't advanced (or went backwards!)
            # Increment logical counter
            self.logical_time += 1

        return (self.physical_time, self.logical_time)

    def receive_event(self, remote_timestamp):
        """
        Update clock when receiving message from remote node
        """
        remote_physical, remote_logical = remote_timestamp
        physical_now = int(time.time() * 1000)

        # Take maximum of local and remote physical time
        self.physical_time = max(
            max(self.physical_time, remote_physical),
            physical_now
        )

        # Update logical time
        if remote_physical > self.physical_time:
            self.logical_time = remote_logical + 1
        elif remote_physical == self.physical_time:
            self.logical_time = max(self.logical_time, remote_logical) + 1
        else:
            self.logical_time += 1

        return (self.physical_time, self.logical_time)
```

---

## Conflict Resolution: Multi-Value (Keep All)

🎓 **PROFESSOR**: Instead of picking a winner, keep ALL conflicting versions:

```
Idea: Don't resolve, delegate to application or user

Pros:
- No data loss
- Application has full context to make smart decision
- User can manually resolve if needed

Cons:
- Complexity pushed to application
- Storage overhead (multiple versions)
- Need eventual convergence (can't keep conflicts forever)
```

### Implementation: Cassandra/Riak Style

```java
/**
 * Cassandra's multi-value conflicts
 */
public class MultiValueConflicts {

    public List<Value> read(String key) {
        // May return multiple conflicting values

        List<Value> values = db.read(key);

        if (values.size() == 1) {
            // No conflict
            return values;
        }

        // Conflict detected! Return all versions
        return values;  // [{value: "A", version: {...}}, {value: "B", version: {...}}]
    }

    public void applicationMerge(String key) {
        List<Value> conflicts = this.read(key);

        if (conflicts.size() <= 1) {
            return;  // No conflict
        }

        // Application decides how to merge
        Value merged = this.customMergeLogic(conflicts);

        // Write back merged value
        // This write includes version vectors from ALL conflicts
        // So future reads see merged value, not conflicts
        this.write(key, merged, mergeVersionVectors(conflicts));
    }

    private Value customMergeLogic(List<Value> conflicts) {
        // Example: Shopping cart - union of items
        Set<String> allItems = new HashSet<>();

        for (Value v : conflicts) {
            List<String> items = (List<String>) v.getValue();
            allItems.addAll(items);
        }

        return new Value(new ArrayList<>(allItems));
    }
}
```

🏗️ **ARCHITECT**: Amazon Dynamo's shopping cart resolution:

```python
class DynamoShoppingCart:
    """
    Real-world example from Amazon's Dynamo paper (2007)
    """

    def add_item(self, cart_id, item):
        # Read current cart (may have conflicts)
        carts = self.dynamo.read(cart_id)

        # Merge all conflicting versions
        merged_items = set()
        version_vector = {}

        for cart in carts:
            merged_items.update(cart["items"])
            version_vector = self.merge_version_vectors(
                version_vector,
                cart["version"]
            )

        # Add new item
        merged_items.add(item)

        # Increment version vector for this node
        version_vector[self.node_id] = version_vector.get(self.node_id, 0) + 1

        # Write back
        self.dynamo.write(cart_id, {
            "items": list(merged_items),
            "version": version_vector
        })

        """
        Result: Items NEVER disappear from cart
        Worst case: User manually removes duplicate items
        Better than: Items mysteriously disappearing (LWW)
        """
```

**Interview Gold**: "Multi-value is like 'undo' in a text editor. You keep history, application (or user) decides final state. It's the garbage collection of distributed systems - manual control with more power."

---

## Conflict Resolution: Custom Merge Functions

🎓 **PROFESSOR**: Domain-specific merge logic based on business rules:

```
Idea: Use knowledge about data semantics to merge intelligently

Examples:
- User profile: Field-level merge (take non-null values)
- Counter: Sum all increments
- Set: Union or intersection
- Calendar: Detect double-bookings, alert both parties
```

### Implementation Patterns

```java
@Service
public class CustomMergeStrategies {

    /**
     * Field-level merge for user profiles
     */
    public User mergeUserProfiles(User version1, User version2) {
        return User.builder()
            // Take non-null value, prefer more recent if both non-null
            .name(selectNonNull(version1.name, version2.name))
            .email(selectNonNull(version1.email, version2.email))
            .phone(selectNonNull(version1.phone, version2.phone))

            // For conflicting non-null values, use business logic
            .address(selectMoreComplete(version1.address, version2.address))

            // For numeric fields, take maximum (assume increment-only)
            .profileViews(Math.max(version1.profileViews, version2.profileViews))

            .build();
    }

    /**
     * Set merge: Union for additive, intersection for restrictive
     */
    public Set<String> mergePermissions(
        Set<String> version1,
        Set<String> version2
    ) {
        // Business rule: Union (grant any permission from either version)
        // Rationale: Adding permissions is intentional, less risky than removing

        Set<String> merged = new HashSet<>(version1);
        merged.addAll(version2);
        return merged;
    }

    /**
     * Numeric merge: Preserve deltas
     */
    public int mergeCounter(
        CounterWrite write1,
        CounterWrite write2,
        int baseValue
    ) {
        // Both writers read baseValue, then modified
        int delta1 = write1.newValue - baseValue;
        int delta2 = write2.newValue - baseValue;

        // Apply both deltas
        return baseValue + delta1 + delta2;

        // Example:
        // Base: 100
        // Write1: 100 + 5 = 105 (delta: +5)
        // Write2: 100 + 3 = 103 (delta: +3)
        // Merged: 100 + 5 + 3 = 108  ✓ (not LWW: 105 or 103)
    }

    /**
     * Conflict escalation: Alert humans
     */
    public Booking mergeBookings(Booking b1, Booking b2) {
        if (b1.overlaps(b2)) {
            // Can't automatically resolve
            alertConflict(b1, b2);

            // Return conflict marker
            return Booking.conflict(b1, b2);
        }

        // No overlap, both bookings valid
        return Booking.merge(b1, b2);
    }
}
```

🏗️ **ARCHITECT**: LinkedIn's approach:

```python
class LinkedInProfileMerge:
    """
    LinkedIn's profile update conflict resolution
    """

    def merge_profiles(self, profile1, profile2, base_profile):
        """
        Three-way merge: base, version1, version2
        """
        merged = {}

        for field in profile1.keys():
            v1 = profile1.get(field)
            v2 = profile2.get(field)
            base = base_profile.get(field)

            if v1 == v2:
                # No conflict
                merged[field] = v1
            elif v1 == base:
                # Only version2 changed
                merged[field] = v2
            elif v2 == base:
                # Only version1 changed
                merged[field] = v1
            else:
                # Both changed (conflict!)
                merged[field] = self.resolve_field_conflict(
                    field, v1, v2, base
                )

        return merged

    def resolve_field_conflict(self, field, v1, v2, base):
        """
        Field-specific resolution rules
        """
        if field == "headline":
            # Take longer headline (assume more descriptive)
            return v1 if len(v1) > len(v2) else v2

        if field == "skills":
            # Union of skills
            return list(set(v1) | set(v2))

        if field == "profilePicture":
            # Take most recent upload
            return self.latest_by_timestamp(v1, v2)

        # Default: Multi-value (keep both)
        return [v1, v2]
```

---

## Conflict Resolution: Three-Way Merge

🎓 **PROFESSOR**: Git-style merge using common ancestor:

```
Idea: Use original value to determine who actually changed what

Timeline:
────────
t0: Base value: X = "Hello"

t1: User A: X ← "Hello World"    (added " World")
    User B: X ← "Hi"              (changed "Hello" → "Hi")

Without base:
- LWW: Pick one, lose the other
- Multi-value: ["Hello World", "Hi"] - ambiguous

With base:
- User A added " World"
- User B changed greeting
- Merged: "Hi World"  (apply both changes!)
```

### Implementation

```python
class ThreeWayMerge:
    """
    Git-style three-way merge
    """

    def merge(self, base, version1, version2):
        """
        Merge two versions using their common ancestor
        """
        if version1 == version2:
            # No conflict
            return version1

        if version1 == base:
            # Only version2 changed
            return version2

        if version2 == base:
            # Only version1 changed
            return version1

        # Both changed - need smart merge
        return self.smart_merge(base, version1, version2)

    def smart_merge(self, base, v1, v2):
        """
        Attempt to merge changes
        """
        # For strings: Diff and merge
        if isinstance(base, str):
            return self.three_way_text_merge(base, v1, v2)

        # For lists: Merge edits
        if isinstance(base, list):
            return self.three_way_list_merge(base, v1, v2)

        # For dicts: Field-by-field merge
        if isinstance(base, dict):
            return self.three_way_dict_merge(base, v1, v2)

        # Can't merge: Escalate
        raise ConflictError(base, v1, v2)

    def three_way_text_merge(self, base, v1, v2):
        """
        Merge text changes using diff
        """
        # Compute diffs
        diff1 = self.diff(base, v1)  # What v1 changed
        diff2 = self.diff(base, v2)  # What v2 changed

        # Apply both diffs to base
        if self.diffs_compatible(diff1, diff2):
            # Non-overlapping changes - safe to merge
            merged = self.apply_diffs(base, diff1, diff2)
            return merged
        else:
            # Overlapping changes - conflict!
            raise ConflictError(
                f"Conflicting edits:\n"
                f"Version1: {diff1}\n"
                f"Version2: {diff2}"
            )
```

🏗️ **ARCHITECT**: CouchDB's revision tree:

```javascript
// CouchDB keeps entire revision history (like Git)

// Document evolution:
const doc = {
    _id: "user123",
    name: "Alice",
    _rev: "1-abc"  // Revision 1
};

// Two concurrent updates:
// Client A:
const docA = {
    _id: "user123",
    name: "Alice Smith",
    _rev: "2-def",      // Revision 2 (from rev 1)
    _revisions: {
        start: 2,
        ids: ["def", "abc"]  // Revision tree
    }
};

// Client B (concurrent):
const docB = {
    _id: "user123",
    name: "Alice",
    email: "alice@example.com",
    _rev: "2-ghi",      // Also revision 2 (from rev 1)
    _revisions: {
        start: 2,
        ids: ["ghi", "abc"]  // Different branch!
    }
};

// After sync, CouchDB has BOTH:
// Revision tree:
//       1-abc
//       /   \
//   2-def  2-ghi   ← Conflict!

// Application resolves by creating revision 3:
const merged = {
    _id: "user123",
    name: "Alice Smith",  // From 2-def
    email: "alice@example.com",  // From 2-ghi
    _rev: "3-jkl",
    _revisions: {
        start: 3,
        ids: ["jkl", "def", "abc"]  // Merged lineage
    }
};
```

---

## Conflict Resolution: Operational Transformation

🎓 **PROFESSOR**: Transform concurrent operations to be compatible:

```
Used in: Google Docs, Figma, real-time collaborative editors

Idea: Transform operations so they can apply in any order

Example:
────────
Initial: "Hello"

User A: Insert "World" at position 6  → "Hello World"
User B: Insert "!" at position 5      → "Hello!"

Problem: Positions conflict after first operation applied

OT transforms operations:
- If A applied first: Transform B's operation
  "Insert '!' at position 5" → "Insert '!' at position 11"
  Result: "Hello World!"

- If B applied first: Transform A's operation
  "Insert 'World' at position 6" → "Insert 'World' at position 6"
  Result: "Hello! World"  (different, but consistent!)
```

### Implementation (Simplified)

```python
class OperationalTransformation:
    """
    Simplified OT for text editing
    """

    class Operation:
        def __init__(self, type, position, content):
            self.type = type  # 'insert' or 'delete'
            self.position = position
            self.content = content

    def transform(self, op1, op2):
        """
        Transform op1 against op2
        (assuming op2 was applied first)
        """
        if op1.type == 'insert' and op2.type == 'insert':
            # Both insertions
            if op2.position < op1.position:
                # op2 inserted before op1
                # Shift op1's position by length of op2's insert
                op1.position += len(op2.content)

            elif op2.position == op1.position:
                # Same position - op2 applied first
                # op1 should go after
                op1.position += len(op2.content)

            return op1

        elif op1.type == 'insert' and op2.type == 'delete':
            # op1 inserts, op2 deletes
            if op2.position < op1.position:
                # op2 deleted before op1's insert
                op1.position -= len(op2.content)

            return op1

        # ... handle other cases (delete/delete, delete/insert)

    def apply_operations(self, document, ops):
        """
        Apply list of operations in order
        """
        for op in ops:
            if op.type == 'insert':
                document = (
                    document[:op.position] +
                    op.content +
                    document[op.position:]
                )
            elif op.type == 'delete':
                document = (
                    document[:op.position] +
                    document[op.position + len(op.content):]
                )

        return document
```

**Interview Gold**: "OT is hard. It's why Google Docs is impressive. Most systems use CRDTs instead (simpler but more storage overhead)."

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### Choosing Conflict Resolution Strategy

```
Decision Matrix:
───────────────

Data Type: Counters
- Best: CRDT (PN-Counter)
- Fallback: Custom merge (sum deltas)
- Never: LWW (loses increments)

Data Type: Sets
- Best: CRDT (OR-Set)
- Fallback: Union merge
- OK: LWW (if idempotent operations)

Data Type: User Profiles
- Best: Field-level three-way merge
- Fallback: Multi-value + manual resolution
- OK: LWW (for preference fields)

Data Type: Calendar Bookings
- Best: Conflict detection + alert
- Fallback: Multi-value + manual resolution
- Never: LWW (double-booking!)

Data Type: Shopping Cart
- Best: Union merge (keep all items)
- Fallback: Multi-value
- Never: LWW (items disappear!)

Data Type: Analytics Events
- Best: Append-only (no conflicts)
- Fallback: LWW (dedupe by event ID)
- OK: Ignore conflicts (eventual consistency)
```

### Implementation Checklist

```
1. Conflict Detection
   ☐ Version vectors or vector clocks
   ☐ Detect concurrent writes
   ☐ Detect causality violations

2. Conflict Storage
   ☐ Multi-value storage (keep all versions)
   ☐ Metadata: timestamps, version vectors
   ☐ Garbage collection for resolved conflicts

3. Conflict Resolution
   ☐ Automatic resolution strategy
   ☐ Manual resolution UI (if applicable)
   ☐ Conflict escalation path

4. Monitoring
   ☐ Conflict rate metrics
   ☐ Resolution latency
   ☐ Unresolved conflict count
   ☐ Alert on conflict spikes
```

---

## 🧠 MIND MAP: CONFLICT RESOLUTION

```
        WRITE CONFLICTS
               |
    ┌──────────┼──────────┐
    ↓          ↓           ↓
 AVOID     DETECT      RESOLVE
    |          |           |
 ┌──┼──┐   Version    ┌────┼────┐
 ↓  ↓  ↓   Vectors    ↓    ↓    ↓
Data Append CRDTs    LWW Multi Custom
Affinity Only             Value Merge
                            |     |
                         Keep  Three
                         All   Way
                              Merge
```

---

## 💡 EMOTIONAL ANCHORS

### 1. **LWW = Coin Flip 🪙**
- Two people shout different answers
- Whoever shouts last wins
- But "last" is subjective (depends on who heard when)
- Loser's answer is lost forever

### 2. **Multi-Value = Democratic Debate 🗳️**
- Two people give different answers
- Write both down
- Committee (application/user) decides later
- No information lost

### 3. **Three-Way Merge = Git Merge 🔀**
- You have original document
- Alice edited copy A
- Bob edited copy B
- Compare both to original to see WHO changed WHAT
- Merge non-conflicting changes

### 4. **Custom Merge = Expert Arbitration 👨‍⚖️**
- Two conflicting claims
- Domain expert uses business rules
- Makes informed decision
- May combine both (shopping cart union)

### 5. **Operational Transform = Real-time Google Docs ✍️**
- Two people type simultaneously
- Each keystroke is transformed based on others' keystrokes
- Everyone sees same final document
- Complex but powerful

---

## 🔑 Key Takeaways

1. **Conflict avoidance beats conflict resolution**
   - Partition data by owner
   - Use append-only patterns
   - Use CRDTs for specific data types

2. **LWW is data loss by design**
   - Only use when losing data is acceptable
   - User preferences, cache invalidation
   - Never for critical business data

3. **Multi-value preserves all information**
   - Safest option when unsure
   - Push complexity to application layer
   - User can manually resolve if needed

4. **Three-way merge requires history**
   - Need common ancestor
   - More powerful than two-way merge
   - Used by Git, CouchDB

5. **Custom merge encodes business logic**
   - Most flexible approach
   - Requires domain knowledge
   - Test thoroughly (edge cases!)

6. **Monitor conflict rates**
   - High conflict rate = schema redesign needed
   - Spike in conflicts = investigate
   - Unresolved conflicts = technical debt

7. **Operational Transform is hard**
   - Use battle-tested libraries
   - Consider CRDTs instead
   - Only needed for real-time collaboration
