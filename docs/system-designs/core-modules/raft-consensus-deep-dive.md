# Distributed Consensus: Raft Deep Dive - Leader Election and Writes

## Contents

- [Distributed Consensus: Raft Deep Dive - Leader Election and Writes](#distributed-consensus-raft-deep-dive---leader-election-and-writes)
    - [Core Mental Model](#core-mental-model)
    - [The Consensus Problem](#the-consensus-problem)
    - [Raft Overview - Designed for Understandability](#raft-overview---designed-for-understandability)
    - [Leader Election Protocol](#leader-election-protocol)
    - [Log Replication - The Write Path](#log-replication---the-write-path)
    - [Safety Guarantees](#safety-guarantees)
    - [Handling Network Partitions](#handling-network-partitions)
    - [Real-World Implementations](#real-world-implementations)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
    - [MIND MAP: RAFT CONSENSUS](#mind-map-raft-consensus)
    - [EMOTIONAL ANCHORS](#emotional-anchors)

## Core Mental Model

```text
Raft = Replicated State Machine via Consensus

The Goal:
┌────────────────────────────────────────────────────────────┐
│ Multiple servers agree on sequence of commands             │
│ Even in presence of failures and network partitions        │
│ → All servers execute same commands in same order          │
│ → All servers end up in same state (consensus!)            │
└────────────────────────────────────────────────────────────┘

The Raft Approach:
──────────────────

1. ELECT a leader (one server in charge)
2. Leader REPLICATES log entries to followers
3. Leader COMMITS entries when majority agrees
4. All servers APPLY committed entries to state machine

                    ┌─────────────┐
                    │   LEADER    │
                    │ (Term 5)    │
                    │ Accepts     │
                    │ writes      │
                    └──────┬──────┘
                           │
            ┌──────────────┼──────────────┐
            │ Replicate Log Entries       │
            ↓                              ↓
      ┌──────────┐                   ┌──────────┐
      │ FOLLOWER │                   │ FOLLOWER │
      │ (Term 5) │                   │ (Term 5) │
      │ Receives │                   │ Receives │
      │ entries  │                   │ entries  │
      └──────────┘                   └──────────┘

Key Insight: Always exactly ONE leader per term
```

**The Raft Guarantee:**
```
If a log entry is committed, it will be present in the logs
of all future leaders.

→ Committed entries are durable
→ All servers eventually see all committed entries
→ Linearizable consistency!
```

---

## The Consensus Problem

🎓 **PROFESSOR**: Why is consensus hard?

### The Fundamental Challenge

```
Problem: Get multiple computers to agree on a value
────────────────────────────────────────────────────

Seems simple:
  Server1: "I vote for X"
  Server2: "I vote for X"
  Server3: "I vote for X"
  → Consensus: X

But reality:
  ✗ Network delays (message takes 1 second)
  ✗ Network partitions (message never arrives)
  ✗ Server crashes (in middle of voting)
  ✗ Byzantine failures (malicious servers)

Example failure scenario:
  Server1: "I vote for X"  ─────┐
  Server2: "I vote for X"  ──×──┼── Network partition!
  Server3: "I vote for Y"  ─────┘   (Server3 didn't receive votes)

  → No consensus!
```

### The FLP Impossibility Result

```
FLP Theorem (1985):
──────────────────
In an asynchronous network with even ONE faulty process,
there is NO deterministic algorithm that guarantees consensus.

Translation: Perfect consensus is IMPOSSIBLE!

But: We can build PRACTICAL consensus with:
  1. Timeouts (assume crashed after N seconds)
  2. Majority quorums (tolerate minority failures)
  3. Strong leadership (one server coordinates)

Raft is one such practical algorithm!
```

### What Consensus Solves

```java
/**
 * Use cases for consensus
 */
public class ConsensusUseCases {

    /**
     * 1. Replicated State Machine
     */
    public void replicatedDB() {
        /**
         * Problem: Replicate database across 5 servers
         *
         * Without Consensus:
         *   - Primary receives write
         *   - Replicates asynchronously
         *   - Primary crashes before replication
         *   → Write lost!
         *
         * With Consensus (Raft):
         *   - Leader receives write
         *   - Replicates to majority (3/5 servers)
         *   - Commits only after majority ACKs
         *   → Write durable! (survives 2 server failures)
         */
    }

    /**
     * 2. Leader Election
     */
    public void leaderElection() {
        /**
         * Problem: Elect exactly one leader
         *
         * Without Consensus:
         *   - Multiple servers think they're leader (split-brain)
         *   - Data corruption
         *
         * With Consensus (Raft):
         *   - Election uses majority vote
         *   - Only one leader per term
         *   - Automatic re-election if leader fails
         */
    }

    /**
     * 3. Configuration Management
     */
    public void configChange() {
        /**
         * Problem: Change cluster configuration (add/remove servers)
         *
         * Without Consensus:
         *   - Different servers have different configs
         *   - No agreement on who's in cluster
         *
         * With Consensus (Raft):
         *   - Config change is a log entry
         *   - All servers agree on config
         *   - Joint consensus during transition
         */
    }
}
```

---

## Raft Overview - Designed for Understandability

🎓 **PROFESSOR**: Raft vs Paxos - A deliberate design choice:

### Why Raft Was Created

```
Paxos (1989):                   Raft (2014):
────────────                    ────────────

✓ Proven correct                ✓ Proven correct
✓ Used in production            ✓ Used in production
✗ Very hard to understand       ✓ Designed for understandability
✗ Hard to implement             ✓ Easier to implement
✗ Multi-Paxos is underspecified ✓ Complete algorithm specified

Quote from Raft paper:
"Paxos is exceptionally difficult to understand...
 We believe that Raft is superior to Paxos for building
 practical systems, because it is more understandable."
```

### Raft's Key Design Principles

```
1. Strong Leadership
   ───────────────────
   - Only leader accepts client requests
   - Only leader appends to log
   - Simplifies reasoning (vs. Paxos's leaderless approach)

2. Leader Election
   ────────────────
   - Randomized timeouts prevent split votes
   - Simple majority voting
   - At most one leader per term

3. Membership Changes
   ──────────────────
   - Joint consensus approach
   - Prevents split-brain during config changes
   - Safe reconfiguration

Result: Easier to understand, easier to implement correctly
```

### Raft Server States

```
Every Raft server is in one of three states:
────────────────────────────────────────────

┌─────────────┐
│  FOLLOWER   │  ← Default state
│             │    Passive: receives entries from leader
└──────┬──────┘    Starts election if no heartbeat
       │
       │ Election timeout
       ↓
┌─────────────┐
│  CANDIDATE  │  ← Transitional state
│             │    Requests votes from other servers
└──────┬──────┘    Either wins election or steps down
       │
       │ Receives majority votes
       ↓
┌─────────────┐
│   LEADER    │  ← One per term
│             │    Accepts client requests
└─────────────┘    Replicates log entries
                   Sends heartbeats

State Transitions:
──────────────────
Follower → Candidate:  Election timeout expires
Candidate → Leader:    Receives majority votes
Candidate → Follower:  Discovers leader or new term
Leader → Follower:     Discovers higher term
```

---

## Leader Election Protocol

🏗️ **ARCHITECT**: How Raft elects a leader:

### Election Process Step-by-Step

```python
class RaftNode:
    """
    Simplified Raft implementation
    """

    def __init__(self, node_id, peers):
        self.node_id = node_id
        self.peers = peers
        self.state = "FOLLOWER"

        # Persistent state (on disk)
        self.current_term = 0
        self.voted_for = None
        self.log = []  # [(term, command), ...]

        # Volatile state
        self.commit_index = 0
        self.last_applied = 0

        # Leader state
        self.next_index = {}   # For each peer
        self.match_index = {}  # For each peer

    def start_election(self):
        """
        STEP 1: Follower timeout → Become candidate
        """
        # Increment term
        self.current_term += 1
        self.state = "CANDIDATE"
        self.voted_for = self.node_id

        # Vote for self
        votes_received = 1

        # Request votes from all peers
        for peer in self.peers:
            response = self.request_vote(peer)

            if response.vote_granted:
                votes_received += 1

        # Did we win?
        if votes_received > len(self.peers) / 2:
            self.become_leader()
        else:
            # Lost election → back to follower
            self.state = "FOLLOWER"

    def request_vote(self, peer):
        """
        STEP 2: Send RequestVote RPC
        """
        request = RequestVoteRequest(
            term=self.current_term,
            candidate_id=self.node_id,
            last_log_index=len(self.log) - 1,
            last_log_term=self.log[-1][0] if self.log else 0
        )

        return peer.handle_request_vote(request)

    def handle_request_vote(self, request):
        """
        STEP 3: Follower decides whether to grant vote
        """
        # Reject if candidate's term is old
        if request.term < self.current_term:
            return RequestVoteResponse(
                term=self.current_term,
                vote_granted=False
            )

        # Update term if candidate has higher term
        if request.term > self.current_term:
            self.current_term = request.term
            self.state = "FOLLOWER"
            self.voted_for = None

        # Grant vote if:
        # 1. Haven't voted in this term, OR already voted for this candidate
        # 2. Candidate's log is at least as up-to-date as ours
        if (self.voted_for is None or self.voted_for == request.candidate_id):
            if self.is_log_up_to_date(request):
                self.voted_for = request.candidate_id
                return RequestVoteResponse(
                    term=self.current_term,
                    vote_granted=True
                )

        return RequestVoteResponse(
            term=self.current_term,
            vote_granted=False
        )

    def is_log_up_to_date(self, request):
        """
        STEP 4: Check if candidate's log is up-to-date
        """
        if not self.log:
            return True  # Our log is empty

        our_last_term = self.log[-1][0]
        our_last_index = len(self.log) - 1

        # Candidate's log is more up-to-date if:
        # 1. Last entry has higher term, OR
        # 2. Same term but longer log
        if request.last_log_term > our_last_term:
            return True
        elif request.last_log_term == our_last_term:
            return request.last_log_index >= our_last_index
        else:
            return False

    def become_leader(self):
        """
        STEP 5: Won election → Become leader
        """
        print(f"Node {self.node_id} elected leader for term {self.current_term}")

        self.state = "LEADER"

        # Initialize leader state
        for peer in self.peers:
            self.next_index[peer] = len(self.log)  # Next entry to send
            self.match_index[peer] = 0             # Highest replicated entry

        # Send heartbeat immediately
        self.send_heartbeats()

    def send_heartbeats(self):
        """
        STEP 6: Leader sends periodic heartbeats
        """
        for peer in self.peers:
            # Empty AppendEntries = heartbeat
            peer.append_entries(AppendEntriesRequest(
                term=self.current_term,
                leader_id=self.node_id,
                prev_log_index=len(self.log) - 1,
                prev_log_term=self.log[-1][0] if self.log else 0,
                entries=[],  # Empty for heartbeat
                leader_commit=self.commit_index
            ))

        # Schedule next heartbeat
        threading.Timer(0.1, self.send_heartbeats).start()  # Every 100ms
```

### Election Timeline

```
Timeline of successful election:
────────────────────────────────

t0: All servers are followers
    S1: Follower (term=1)
    S2: Follower (term=1)
    S3: Follower (term=1)

t1: S1's election timeout fires (random: 150-300ms)
    S1: Candidate (term=2) → RequestVote to S2, S3

t2: S2 receives RequestVote
    S2: Grants vote (term=2, voted_for=S1)

t3: S3 receives RequestVote
    S3: Grants vote (term=2, voted_for=S1)

t4: S1 receives majority votes (3/3)
    S1: Leader (term=2)
    S1: Send heartbeat to S2, S3

t5: S2, S3 receive heartbeat
    S2: Follower (term=2, leader=S1)
    S3: Follower (term=2, leader=S1)

    Election complete! S1 is leader.
```

### Preventing Split Votes

```java
/**
 * Raft's solution to split votes: Randomized timeouts
 */
public class ElectionTimeout {

    /**
     * Problem: Split votes
     */
    public void splitVoteProblem() {
        /**
         * Without randomization:
         *
         * t0: All servers timeout simultaneously
         *     S1, S2, S3 → All become candidates
         *
         * t1: Everyone votes for themselves
         *     S1: votes=1, S2: votes=1, S3: votes=1
         *
         * t2: No majority! → All timeout again
         *     → Infinite loop of split votes
         */
    }

    /**
     * Solution: Randomized timeouts
     */
    public void randomizedTimeout() {
        // Each server picks random timeout: 150-300ms
        Random random = new Random();
        int electionTimeout = 150 + random.nextInt(150);

        /**
         * Result:
         *
         * t0: S1 timeout = 160ms
         *     S2 timeout = 250ms
         *     S3 timeout = 200ms
         *
         * t1 (160ms): S1 times out first
         *     S1 → Candidate, requests votes
         *
         * t2 (180ms): S1 receives majority votes
         *     S1 → Leader (before others time out!)
         *
         * t3: S2, S3 receive heartbeat from S1
         *     → Remain followers
         *
         * Split vote avoided!
         */
    }

    /**
     * Tuning election timeout
     */
    public void tuningTimeout() {
        /**
         * Guideline: electionTimeout >> broadcastTime
         *
         * broadcastTime: Time to send RPC to all servers
         * - Typical: 0.5ms - 20ms (LAN)
         * - With retries: 100ms
         *
         * electionTimeout: 150-300ms is typical
         * - Too short: Unnecessary elections (network blip)
         * - Too long: Slow failover
         *
         * Recommendation: 10x broadcastTime minimum
         */
    }
}
```

---

## Log Replication - The Write Path

🏗️ **ARCHITECT**: How Raft replicates client requests:

### Log Structure

```
Raft Log (per server):
──────────────────────

Index:  0      1      2      3      4      5
Term:   -     [1]    [1]    [1]    [2]    [3]
Cmd:    -     [x=1]  [y=2]  [x=3]  [y=5]  [x=7]
        ↑      ↑      ↑      ↑      ↑      ↑
     Empty  Committed          Uncommitted

commit_index = 3  ← Entries 0-3 are committed (durable)
last_applied = 3  ← Entries 0-3 applied to state machine

Leader:  [1,x=1] [1,y=2] [1,x=3] [2,y=5] [3,x=7]
Follower1:[1,x=1] [1,y=2] [1,x=3] [2,y=5]
Follower2:[1,x=1] [1,y=2] [1,x=3] [2,y=5] [3,x=7]

Key Properties:
  1. Log entries are immutable (never modified)
  2. If two logs contain entry with same index and term,
     they are identical up to that index
  3. Leader's log is authoritative
```

### Replication Protocol

```python
class RaftReplication:
    """
    Log replication in Raft
    """

    def client_request(self, command):
        """
        STEP 1: Client sends request to leader
        """
        if self.state != "LEADER":
            # Redirect to leader
            return {"error": "Not leader", "leader": self.leader_id}

        # Append to local log
        entry = (self.current_term, command)
        self.log.append(entry)

        # Replicate to followers
        self.replicate_to_followers()

        # Wait for majority to replicate
        while self.match_index_count() < self.majority():
            time.sleep(0.01)

        # Commit entry
        self.commit_index = len(self.log) - 1

        # Apply to state machine
        self.apply_to_state_machine()

        # Return success to client
        return {"status": "ok", "value": self.state_machine[command.key]}

    def replicate_to_followers(self):
        """
        STEP 2: Leader sends AppendEntries to each follower
        """
        for peer in self.peers:
            # Get next entry to send to this peer
            next_idx = self.next_index[peer]

            # Get entries from next_idx onwards
            entries = self.log[next_idx:]

            # Get previous entry (for consistency check)
            prev_log_index = next_idx - 1
            prev_log_term = self.log[prev_log_index][0] if prev_log_index >= 0 else 0

            # Send AppendEntries RPC
            response = peer.append_entries(AppendEntriesRequest(
                term=self.current_term,
                leader_id=self.node_id,
                prev_log_index=prev_log_index,
                prev_log_term=prev_log_term,
                entries=entries,
                leader_commit=self.commit_index
            ))

            if response.success:
                # Update next_index and match_index
                self.next_index[peer] = next_idx + len(entries)
                self.match_index[peer] = next_idx + len(entries) - 1
            else:
                # Follower's log inconsistent → decrement next_index
                self.next_index[peer] = max(0, next_idx - 1)

    def handle_append_entries(self, request):
        """
        STEP 3: Follower receives AppendEntries
        """
        # Reply false if term < currentTerm
        if request.term < self.current_term:
            return AppendEntriesResponse(
                term=self.current_term,
                success=False
            )

        # Update term if leader has higher term
        if request.term > self.current_term:
            self.current_term = request.term
            self.state = "FOLLOWER"
            self.voted_for = None

        # Reset election timeout (received heartbeat)
        self.reset_election_timer()

        # Check log consistency
        if request.prev_log_index >= 0:
            if request.prev_log_index >= len(self.log):
                # Missing entry → reject
                return AppendEntriesResponse(
                    term=self.current_term,
                    success=False
                )

            if self.log[request.prev_log_index][0] != request.prev_log_term:
                # Term mismatch → delete conflicting entries
                self.log = self.log[:request.prev_log_index]
                return AppendEntriesResponse(
                    term=self.current_term,
                    success=False
                )

        # Append new entries
        insert_index = request.prev_log_index + 1
        for i, entry in enumerate(request.entries):
            if insert_index + i < len(self.log):
                # Overwrite conflicting entry
                self.log[insert_index + i] = entry
            else:
                # Append new entry
                self.log.append(entry)

        # Update commit index
        if request.leader_commit > self.commit_index:
            self.commit_index = min(
                request.leader_commit,
                len(self.log) - 1
            )
            self.apply_to_state_machine()

        return AppendEntriesResponse(
            term=self.current_term,
            success=True
        )

    def apply_to_state_machine(self):
        """
        STEP 4: Apply committed entries to state machine
        """
        while self.last_applied < self.commit_index:
            self.last_applied += 1
            term, command = self.log[self.last_applied]

            # Execute command
            if command.op == "SET":
                self.state_machine[command.key] = command.value
            elif command.op == "DELETE":
                del self.state_machine[command.key]

            print(f"Applied: {command}")
```

### Replication Timeline

```
Timeline of write request:
─────────────────────────

t0: Client sends "SET x=5" to Leader

t1: Leader appends to log
    Leader: [..., (term=3, SET x=5)]  ← Index 5, uncommitted

t2: Leader sends AppendEntries to Followers
    → Follower1
    → Follower2

t3: Follower1 receives AppendEntries
    Follower1: Appends entry, replies success

t4: Follower2 receives AppendEntries
    Follower2: Appends entry, replies success

t5: Leader receives majority ACKs (2/3)
    Leader: Commits entry (commit_index = 5)
    Leader: Applies to state machine (x ← 5)
    Leader: Returns success to client

t6: Leader sends next heartbeat
    AppendEntries(leader_commit=5)

t7: Followers receive heartbeat
    Follower1: commit_index ← 5, applies to state machine
    Follower2: commit_index ← 5, applies to state machine

All servers now have x=5 in state machine!
```

---

## Safety Guarantees

🎓 **PROFESSOR**: Raft's correctness properties:

### The Five Safety Properties

```
Raft guarantees (if network eventually recovers):
─────────────────────────────────────────────────

1. Election Safety
   ┌─────────────────────────────────────────────┐
   │ At most one leader per term                 │
   └─────────────────────────────────────────────┘
   → Prevents split-brain

2. Leader Append-Only
   ┌─────────────────────────────────────────────┐
   │ Leaders never overwrite or delete log entries│
   └─────────────────────────────────────────────┘
   → Leader's log only grows

3. Log Matching
   ┌─────────────────────────────────────────────┐
   │ If two logs contain entry with same index   │
   │ and term, then logs are identical up to     │
   │ that index                                   │
   └─────────────────────────────────────────────┘
   → Logs are consistent

4. Leader Completeness
   ┌─────────────────────────────────────────────┐
   │ If a log entry is committed in a term, it   │
   │ will be present in logs of all future leaders│
   └─────────────────────────────────────────────┘
   → Committed entries never lost

5. State Machine Safety
   ┌─────────────────────────────────────────────┐
   │ If a server applies log entry at index i,   │
   │ no other server will apply different entry  │
   │ at index i                                   │
   └─────────────────────────────────────────────┘
   → Linearizability!
```

### Commitment Rule

```java
/**
 * When is a log entry committed?
 */
public class CommitmentRule {

    /**
     * Raft's commitment rule (tricky part!)
     */
    public void commitmentLogic() {
        /**
         * Entry is committed when:
         *
         * 1. Replicated on majority of servers, AND
         * 2. At least one entry from current term is also
         *    replicated on majority
         *
         * Why the second condition?
         * → Prevents committing entries from old terms
         *   that might later be overwritten
         */
    }

    /**
     * Example: Why we need rule #2
     */
    public void whyRule2() {
        /**
         * Scenario without rule #2:
         *
         * Term 1:
         *   S1 (leader): [1,x=1] [1,y=2]
         *   S2:          [1,x=1] [1,y=2]
         *   S3:          [1,x=1]
         *
         *   S1 crashes before S3 gets [1,y=2]
         *
         * Term 2:
         *   S3 elected leader (has [1,x=1])
         *   S3 adds:     [1,x=1] [2,z=3]
         *   S3 crashes before replicating [2,z=3]
         *
         * Term 3:
         *   S1 elected leader (has [1,x=1] [1,y=2])
         *   S1 replicates [1,y=2] to S2
         *   [1,y=2] now on majority (S1, S2)
         *
         *   If we commit [1,y=2] now → WRONG!
         *   S3 could be elected again and overwrite it!
         *
         * Correct behavior:
         *   S1 adds new entry: [1,x=1] [1,y=2] [3,a=4]
         *   [3,a=4] replicated to majority
         *   → NOW commit both [1,y=2] AND [3,a=4]
         *   → Safe! Any future leader must have [3,a=4],
         *           which implies it has [1,y=2]
         */
    }
}
```

---

## Handling Network Partitions

🏗️ **ARCHITECT**: Raft under partition scenarios:

### Scenario 1: Minority Partition

```
Initial: 5-server cluster, S1 is leader
─────────────────────────────────────

Network partition:
  Majority: S1 (leader), S2, S3  (can still form quorum)
  Minority: S4, S5               (cannot form quorum)

Majority partition:
  ✓ S1 continues as leader
  ✓ Accepts writes (quorum of 3)
  ✓ Commits entries normally

Minority partition:
  ✗ S4, S5 cannot hear from leader
  ✗ Election timeouts fire
  ✗ S4 becomes candidate, requests votes
  ✗ Only has 2 votes (S4, S5) → not majority
  ✗ Election fails → retry → fail → retry...
  ✗ Cannot make progress (correct behavior!)

Result:
  ✓ System remains available (majority partition)
  ✓ Minority partition unavailable (correct - prevents split-brain)
```

### Scenario 2: Leader in Minority Partition

```
Initial: 5-server cluster, S1 is leader
─────────────────────────────────────

Network partition:
  Minority: S1 (old leader), S2  (cannot form quorum)
  Majority: S3, S4, S5           (can form quorum)

Minority partition (S1, S2):
  t0: S1 thinks it's still leader
  t1: S1 tries to replicate entries
  t2: Cannot get majority ACKs (only S2)
  t3: Entries stuck uncommitted
  t4: Client requests timeout

Majority partition (S3, S4, S5):
  t0: Don't hear from S1 (network partition)
  t1: Election timeout fires
  t2: S3 becomes candidate (term=2)
  t3: S3 receives votes from S4, S5 → elected leader
  t4: S3 accepts writes, commits entries

Partition heals:
  t5: S1 reconnects
  t6: S1 sees S3 with higher term
  t7: S1 steps down to follower
  t8: S1 discards uncommitted entries
  t9: S1 replicates from S3 (new leader)

Result:
  ✓ Only one active leader (S3)
  ✓ No data corruption
  ✓ Automatic failover
```

### Scenario 3: Split-Brain Prevention

```python
class SplitBrainPrevention:
    """
    How Raft prevents split-brain
    """

    def split_brain_attempt(self):
        """
        Attempted split-brain scenario
        """
        # Partition: {S1, S2} | {S3, S4, S5}

        # S1 (term=1, old leader):
        s1 = RaftNode(
            term=1,
            state="LEADER",
            votes=0  # Cannot get majority from {S1, S2}
        )

        # S3 (term=2, new leader):
        s3 = RaftNode(
            term=2,
            state="LEADER",
            votes=3  # Majority from {S3, S4, S5}
        )

        # Client sends write to S1:
        response = s1.handle_write("SET x=5")

        # S1 tries to replicate to majority:
        acks = s1.replicate_to_followers()
        # acks = 1 (only S2)
        # Cannot commit! (need 3 acks)

        # Entry stuck uncommitted → client timeout

        # Client sends write to S3:
        response = s3.handle_write("SET x=10")

        # S3 replicates to majority:
        acks = s3.replicate_to_followers()
        # acks = 3 (S3, S4, S5)
        # Committed! ✓

        """
        Result:
          ✗ S1 cannot commit (no quorum)
          ✓ S3 commits successfully
          → No split-brain!
          → Only one active leader (S3)
        """

    def term_based_rejection(self):
        """
        Additional safety: Term numbers
        """
        # When partition heals:

        # S1 (term=1) sends AppendEntries to S3
        # S3 (term=2) rejects (request.term < current_term)

        # S1 sees higher term → steps down to follower

        # → Old leader automatically demoted
```

---

## Real-World Implementations

🏗️ **ARCHITECT**: Production Raft implementations:

### etcd (Kubernetes)

```go
// etcd: Kubernetes's distributed configuration store
// Used for: Service discovery, leader election, distributed locking

package main

import (
    "context"
    "go.etcd.io/etcd/client/v3"
)

func main() {
    // Connect to etcd cluster (3-5 nodes)
    cli, _ := clientv3.New(clientv3.Config{
        Endpoints: []string{"etcd1:2379", "etcd2:2379", "etcd3:2379"},
    })

    // Write (goes through Raft consensus)
    cli.Put(context.Background(), "/config/db_url", "postgres://...")

    // Read (can read from follower - may be slightly stale)
    resp, _ := cli.Get(context.Background(), "/config/db_url")

    // Linearizable read (read from leader)
    resp, _ = cli.Get(
        context.Background(),
        "/config/db_url",
        clientv3.WithSerializable(), // Disable linearizable read
    )

    // Leader election (using Raft)
    session, _ := concurrency.NewSession(cli)
    election := concurrency.NewElection(session, "/election/")

    // Campaign to be leader
    election.Campaign(context.Background(), "node1")
    // Blocks until elected leader

    // Do leader work...

    // Resign leadership
    election.Resign(context.Background())
}

/**
 * etcd characteristics:
 * - Written in Go
 * - gRPC API (vs ZooKeeper's custom protocol)
 * - Raft consensus (vs ZooKeeper's ZAB)
 * - Watch support (like ZooKeeper)
 * - Used by Kubernetes for all cluster state
 */
```

### Consul (HashiCorp)

```javascript
// Consul: Service mesh with built-in service discovery

const consul = require('consul')();

// Service registration (stored via Raft)
await consul.agent.service.register({
    name: 'web',
    address: '192.168.1.10',
    port: 8080,
    check: {
        http: 'http://192.168.1.10:8080/health',
        interval: '10s'
    }
});

// Service discovery
const services = await consul.health.service('web');
// Returns all healthy instances of 'web' service

// Key-value store (Raft-replicated)
await consul.kv.set('config/db_url', 'postgres://...');

// Leader election
const session = await consul.session.create({
    ttl: '10s',
    behavior: 'delete'
});

const acquired = await consul.kv.set({
    key: 'service/leader',
    value: 'node1',
    acquire: session
});

if (acquired) {
    console.log('I am the leader!');
}

/**
 * Consul characteristics:
 * - Multi-datacenter support (Raft per datacenter)
 * - Built-in health checking
 * - DNS interface (in addition to HTTP API)
 * - Service mesh features (proxying, encryption)
 */
```

### CockroachDB

```sql
-- CockroachDB: Distributed SQL database using Raft

-- Each range (chunk of data) is a Raft group
-- Typical: 64MB per range

-- Create table (data automatically sharded into ranges)
CREATE TABLE users (
    id UUID PRIMARY KEY,
    name STRING,
    email STRING
);

-- Write (goes through Raft for affected range)
INSERT INTO users VALUES
    (gen_random_uuid(), 'Alice', 'alice@example.com');

-- Read (can use follower reads for historical queries)
SELECT * FROM users
AS OF SYSTEM TIME '-10s';  -- Read from 10 seconds ago (follower)

-- Show Raft status
SHOW RANGES FROM TABLE users;

/**
 * Output:
 * start_key | end_key | range_id | replicas | lease_holder
 * ---------------------------------------------------------
 * NULL      | /1000   | 1        | {1,2,3}  | 1
 * /1000     | /2000   | 2        | {2,3,4}  | 2
 * ...
 *
 * Each range is a separate Raft group!
 * → Horizontal scalability while maintaining consistency
 */
```

### TiKV (TiDB)

```rust
// TiKV: Distributed key-value store using Raft

use tikv_client::RawClient;

#[tokio::main]
async fn main() {
    // Connect to TiKV cluster
    let client = RawClient::new(vec!["tikv1:2379", "tikv2:2379"]).await.unwrap();

    // Write (Raft consensus)
    client.put("key1".to_owned(), "value1".to_owned()).await.unwrap();

    // Read
    let value = client.get("key1".to_owned()).await.unwrap();

    // Batch operations (single Raft proposal)
    client.batch_put(vec![
        ("key2".to_owned(), "value2".to_owned()),
        ("key3".to_owned(), "value3".to_owned()),
    ]).await.unwrap();
}

/**
 * TiKV characteristics:
 * - Raft groups per region (similar to CockroachDB)
 * - Automatic rebalancing
 * - Geo-replication support
 * - Used by TiDB (MySQL-compatible distributed database)
 */
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### When to Use Raft

```
Use Raft (or Paxos) when:
────────────────────────

✓ Need strong consistency (linearizability)
✓ Can tolerate higher write latency (consensus overhead)
✓ Need automatic failover
✓ Building: Distributed database, configuration store, lock service

Examples:
  - etcd (Kubernetes configuration)
  - Consul (service discovery)
  - CockroachDB (distributed SQL)
  - TiKV (distributed KV store)

Don't use Raft when:
────────────────────

✗ High write throughput required (use leaderless replication)
✗ Eventual consistency acceptable (use async replication)
✗ Single datacenter + can tolerate downtime (use single leader)

Examples:
  - Cassandra (leaderless, eventual consistency)
  - MongoDB (single leader, async replication)
```

### Designing with Raft

```
Interview Framework:
───────────────────

1. Identify Consistency Requirements
   "Our distributed lock service needs strong consistency.
    Clients must see locks in same order. → Use Raft"

2. Choose Cluster Size
   "We'll use 5 nodes to tolerate 2 failures.
    Quorum = 3, so we can survive 2 node failures."

3. Handle Write Path
   "Client writes go to leader.
    Leader replicates to followers.
    Commits when majority (3/5) ACKs.
    Returns success to client."

4. Handle Read Path
   "For linearizable reads: Read from leader.
    For faster reads: Allow follower reads (may be stale).
    Or use lease-based reads (leader confirms it's still leader)."

5. Handle Failure Scenarios
   "If leader fails:
    - Followers detect via heartbeat timeout
    - New election triggered
    - New leader elected in ~1 second
    - Clients retry to new leader"

6. Handle Network Partitions
   "If network partitions:
    - Majority partition continues operating
    - Minority partition cannot make progress
    - When partition heals, minority syncs from majority"
```

### Raft Performance Tuning

```python
class RaftPerformanceTuning:
    """
    Production tuning guidelines
    """

    def tune_election_timeout(self):
        """
        Election timeout: 150-300ms typical
        """
        # Too short: Unnecessary elections (network blip)
        # Too long: Slow failover

        # Guideline: 10x RPC time
        rpc_time = 10  # ms (LAN)
        min_timeout = rpc_time * 10  # 100ms
        max_timeout = min_timeout * 2  # 200ms

    def tune_heartbeat_interval(self):
        """
        Heartbeat interval: election_timeout / 10
        """
        election_timeout = 150  # ms
        heartbeat_interval = election_timeout / 10  # 15ms

        # Ensures multiple heartbeats during election timeout
        # → Prevents spurious elections

    def batch_log_entries(self):
        """
        Batch multiple client requests into single Raft proposal
        """
        # Instead of:
        # Client1: SET x=1 → Raft proposal 1
        # Client2: SET y=2 → Raft proposal 2
        # Client3: SET z=3 → Raft proposal 3
        # → 3 rounds of consensus (slow!)

        # Batch:
        # Buffer requests for 10ms
        # Bundle into single proposal: [SET x=1, SET y=2, SET z=3]
        # → 1 round of consensus (3x faster!)

    def pipeline_replication(self):
        """
        Pipeline AppendEntries (don't wait for ACK)
        """
        # Sequential:
        # Send entry 1 → wait for ACK → send entry 2 → wait...
        # → Slow!

        # Pipelined:
        # Send entry 1, entry 2, entry 3, ... (without waiting)
        # Process ACKs as they arrive
        # → Much faster!

    def use_followers_for_reads(self):
        """
        Offload reads to followers (if staleness acceptable)
        """
        # Leader handles: 100% writes + 100% reads = overloaded
        # Followers handle: 0% writes + 80% reads = underutilized

        # Solution: Follower reads
        # - Stale by ~election_timeout (150ms)
        # - Acceptable for many use cases
        # - 5x read throughput (5 servers)
```

---

## 🧠 MIND MAP: RAFT CONSENSUS

```
         RAFT CONSENSUS
               │
       ┌───────┴────────┐
       ↓                ↓
  LEADER ELECTION   LOG REPLICATION
       │                │
   ┌───┼───┐        ┌───┼───┐
   ↓   ↓   ↓        ↓   ↓   ↓
 Term Random Majority Append Commit Apply
  #   Timeout Vote   Entries  Rule to SM
```

---

## 💡 EMOTIONAL ANCHORS

### 1. **Raft = Democracy Election 🗳️**
- Citizens (servers) vote for president (leader)
- Majority vote wins
- President serves for term
- If president fails, new election
- Only one president per term

### 2. **Log = Captain's Log 📓**
- Ship captain records events in order
- Immutable (can't change history)
- Other ships copy the log
- If ships disagree, captain's log is authority

### 3. **Heartbeat = Pulse Check 💓**
- Doctor checks patient's pulse regularly
- No pulse → patient dead → call emergency
- Regular heartbeat → all is well
- Raft: Leader sends heartbeat, followers expect it

### 4. **Majority Quorum = Jury Verdict ⚖️**
- 12 jurors, need 7 to convict (majority)
- Can lose 5 jurors and still decide
- Can't decide with only 5 jurors (no quorum)
- Raft: Need majority to commit

### 5. **Term Number = Epoch 📅**
- History divided into eras (BC, AD)
- Each era has one ruler
- Higher era supersedes lower
- Raft: Higher term supersedes lower

### 6. **Commitment = Publishing Book 📚**
- Draft (uncommitted) vs published (committed)
- Draft can be deleted (log entry not committed)
- Published book is permanent (committed entry)
- Can't unpublish (can't uncommit)

---

## 🔑 Key Takeaways

### Leader Election

1. **Exactly one leader per term**
   - Prevented by majority vote requirement
   - Term numbers detect stale leaders
   - Randomized timeouts prevent split votes

2. **Only candidates with up-to-date logs can become leader**
   - Log comparison in RequestVote
   - Ensures committed entries never lost
   - Preserves linearizability

3. **Election timeout tuning is critical**
   - Too short → spurious elections
   - Too long → slow failover
   - Typical: 150-300ms (10x RPC time)

### Log Replication

4. **Leader's log is authoritative**
   - Followers overwrite conflicting entries
   - Append-only on leader
   - Eventually all logs identical

5. **Majority quorum ensures durability**
   - Entry committed when replicated to majority
   - Survives minority failures
   - Can't make progress without majority

6. **Commitment rule prevents data loss**
   - Must replicate entry from current term
   - Prevents committing old entries that might be overwritten
   - Subtle but critical for safety

### Safety

7. **Network partitions handled correctly**
   - Majority partition continues operating
   - Minority partition unavailable (correct!)
   - Prevents split-brain

8. **Linearizability guaranteed**
   - State machine safety property
   - All servers apply same commands in same order
   - Strongest consistency guarantee

9. **Automatic failover**
   - Followers detect leader failure via heartbeat timeout
   - New leader elected in ~1 second
   - No manual intervention

### Production

10. **Raft is production-ready**
    - Used by etcd, Consul, CockroachDB, TiKV
    - Proven correct (formal verification)
    - Easier to understand than Paxos
    - Use existing implementations (don't build your own!)

---

**Final Thought**: Raft is the "understandable consensus algorithm." If you understand Raft, you understand how distributed consensus works - and that knowledge applies to Paxos, Viewstamped Replication, and other consensus protocols. Master Raft, and you've mastered one of the most important concepts in distributed systems.
