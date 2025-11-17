# How Do Hash Indexes Work? - Deep Dive

## Contents

- [How Do Hash Indexes Work? - Deep Dive](#how-do-hash-indexes-work---deep-dive)
  - [Core Mental Model](#core-mental-model)
  - [Hash Function Fundamentals](#1-hash-function-fundamentals)
  - [Collision Resolution Strategies](#2-collision-resolution-strategies)
  - [Hash Index Internal Structure](#3-hash-index-internal-structure)
  - [Hash vs B-Tree: When to Use Each](#4-hash-vs-b-tree-when-to-use-each)
  - [Limitations of Hash Indexes](#5-limitations-of-hash-indexes)
  - [Production Hash Index Implementations](#6-production-hash-index-implementations)
  - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
  - [MIND MAP: HASH INDEX CONCEPTS](#mind-map-hash-index-concepts)

## Core Mental Model

```text
Hash Index Performance:
- Point Query (WHERE key = value):  O(1) average case
- Range Query (WHERE key > value):  NOT SUPPORTED
- Sorted Retrieval (ORDER BY):      NOT SUPPORTED

Core Trade-off:
Perfect for equality lookups ←→ Useless for ranges/sorting
```

**Fundamental Hash Index Equation:**
```
bucket_index = hash(key) % num_buckets

Where:
- hash(key) produces integer from any input
- % num_buckets maps to bucket array index
- Collisions are inevitable (pigeonhole principle)
```

**Three Core Components:**
```
┌──────────────────────────────────────────────────┐
│ 1. HASH FUNCTION: Key → Integer                 │
│ 2. BUCKET ARRAY: Fixed-size array of buckets    │
│ 3. COLLISION HANDLER: Multiple keys per bucket  │
└──────────────────────────────────────────────────┘
```

**Visual Model:**
```text
Key: "user@example.com" (input)
         ↓
    hash("user@example.com") = 948573829 (hash function)
         ↓
    948573829 % 1000 = 829 (bucket selection)
         ↓
    Bucket[829] → [Row Pointer] (value retrieval)
```

**Real World Pattern:**
```java
// Domain Model: Hash Index Implementation
public class HashIndex<K, V> {

    private static final double LOAD_FACTOR = 0.75;  // Resize at 75% full
    private Bucket<K, V>[] buckets;
    private int size;

    static class Bucket<K, V> {
        List<Entry<K, V>> entries;  // Collision chain

        static class Entry<K, V> {
            K key;
            V value;  // Row pointer in database context
            int hashCode;  // Cache hash to avoid recomputation
        }
    }

    public V get(K key) {
        int bucketIndex = getBucketIndex(key);
        Bucket<K, V> bucket = buckets[bucketIndex];

        // Linear search within bucket (collision resolution)
        for (Entry<K, V> entry : bucket.entries) {
            if (entry.key.equals(key)) {  // Must check equality!
                return entry.value;  // O(1) average, O(n) worst case
            }
        }

        return null;  // Not found
    }

    private int getBucketIndex(K key) {
        int hash = key.hashCode();
        // Defend against negative hash codes
        return Math.abs(hash) % buckets.length;
    }

    public void put(K key, V value) {
        // Check load factor and resize if needed
        if ((double) size / buckets.length > LOAD_FACTOR) {
            resize();  // Rehash all entries - expensive!
        }

        int bucketIndex = getBucketIndex(key);
        Bucket<K, V> bucket = buckets[bucketIndex];

        // Update if exists, otherwise append
        for (Entry<K, V> entry : bucket.entries) {
            if (entry.key.equals(key)) {
                entry.value = value;  // Update
                return;
            }
        }

        bucket.entries.add(new Entry<>(key, value, key.hashCode()));
        size++;
    }
}
```

---

### 1. **Hash Function Fundamentals**

🎓 **PROFESSOR**: A good hash function must satisfy **three critical properties**:

```text
1. DETERMINISM: Same input always produces same output
   hash("john@example.com") = 12345  (always!)

2. UNIFORMITY: Distribute keys evenly across buckets
   Bad:  90% of keys → bucket 5 (clustering)
   Good: Keys distributed evenly across all buckets

3. EFFICIENCY: Fast to compute
   Goal: O(1) computation time
   Bad: Cryptographic hash (SHA-256) - too slow
   Good: MurmurHash, CityHash, xxHash
```

#### Common Hash Functions

```python
# 1. Simple Modulo Hash (educational only - poor distribution)
def simple_hash(key: str, num_buckets: int) -> int:
    """
    Sum ASCII values - terrible for production!
    """
    return sum(ord(c) for c in key) % num_buckets

# Problem: "abc" and "cab" produce same hash (collision)


# 2. DJB2 Hash (better, classic algorithm)
def djb2_hash(key: str) -> int:
    """
    Daniel J. Bernstein's famous hash function
    Used in early versions of many databases
    """
    hash_value = 5381
    for char in key:
        # hash * 33 + char
        hash_value = ((hash_value << 5) + hash_value) + ord(char)
    return hash_value & 0xFFFFFFFF  # Keep 32-bit


# 3. MurmurHash3 (production quality)
import mmh3

def murmur_hash(key: str, num_buckets: int) -> int:
    """
    Fast, excellent distribution, non-cryptographic
    Used by: Redis, Cassandra, Hadoop
    """
    hash_value = mmh3.hash(key, seed=42)
    return abs(hash_value) % num_buckets


# 4. Cryptographic Hash (DO NOT USE for indexes - too slow)
import hashlib

def sha256_hash(key: str) -> int:
    """
    Excellent distribution, but ~100x slower than MurmurHash
    Use only when security matters (password hashing)
    """
    digest = hashlib.sha256(key.encode()).digest()
    return int.from_bytes(digest[:8], 'big')
```

🏗️ **ARCHITECT**: Hash function performance comparison:

```java
// Benchmark: 10M hash computations
public class HashFunctionBenchmark {

    @Benchmark
    public void testMurmurHash3() {
        for (String key : keys) {
            int hash = Hashing.murmur3_32().hashString(key, UTF_8).asInt();
        }
    }
    // Result: 250ms for 10M keys (40K ops/ms)

    @Benchmark
    public void testSHA256() {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String key : keys) {
            byte[] hash = digest.digest(key.getBytes(UTF_8));
        }
    }
    // Result: 18,000ms for 10M keys (555 ops/ms)
    // 72x SLOWER than MurmurHash!

    @Benchmark
    public void testJavaHashCode() {
        for (String key : keys) {
            int hash = key.hashCode();  // JVM built-in
        }
    }
    // Result: 180ms for 10M keys (55K ops/ms)
}
```

**Interview gold**: "For database indexes, use non-cryptographic hashes like MurmurHash. Cryptographic hashes are 50-100x slower and provide no benefit since we're not defending against attacks."

---

### 2. **Collision Resolution Strategies**

🎓 **PROFESSOR**: When two keys hash to the same bucket (inevitable by **pigeonhole principle**), we need collision resolution:

```text
Collision Example:
hash("alice@example.com") % 1000 = 537
hash("bob@example.com")   % 1000 = 537  ← Collision!

Two keys → same bucket → need resolution strategy
```

#### Strategy 1: Chaining (Separate Chaining)

```text
Most common in databases. Each bucket contains a linked list:

Buckets:
┌────┬──────────────────────────────────┐
│  0 │ → null                           │
│  1 │ → [key1, ptr1] → [key2, ptr2]   │  ← Collision chain
│  2 │ → [key3, ptr3]                   │
│ .. │                                  │
│537 │ → [alice, ptr_A] → [bob, ptr_B] │  ← Both hashed to 537
└────┴──────────────────────────────────┘

Lookup Algorithm:
1. Hash key → bucket index
2. Traverse chain (linear search)
3. Compare keys with equals()

Time Complexity:
- Best:    O(1) - no collision
- Average: O(1 + α) where α = load factor
- Worst:   O(n) - all keys in one bucket (poor hash function!)
```

**Implementation:**

```java
public class ChainingHashIndex<K, V> {

    static class Node<K, V> {
        K key;
        V value;
        Node<K, V> next;  // Linked list

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private Node<K, V>[] buckets;
    private int size;

    @SuppressWarnings("unchecked")
    public ChainingHashIndex(int capacity) {
        buckets = (Node<K, V>[]) new Node[capacity];
    }

    public V get(K key) {
        int index = hash(key);
        Node<K, V> node = buckets[index];

        // Traverse collision chain
        while (node != null) {
            if (node.key.equals(key)) {  // Found!
                return node.value;
            }
            node = node.next;
        }

        return null;  // Not found
    }

    public void put(K key, V value) {
        int index = hash(key);
        Node<K, V> node = buckets[index];

        // Check if key already exists (update case)
        while (node != null) {
            if (node.key.equals(key)) {
                node.value = value;  // Update
                return;
            }
            node = node.next;
        }

        // Insert at head (O(1) insertion)
        Node<K, V> newNode = new Node<>(key, value);
        newNode.next = buckets[index];
        buckets[index] = newNode;
        size++;
    }

    private int hash(K key) {
        return Math.abs(key.hashCode()) % buckets.length;
    }
}
```

#### Strategy 2: Open Addressing (Linear Probing)

```text
No chains. All entries stored directly in bucket array.
On collision, probe to next available slot:

Buckets (size 10):
┌────┬─────────┬─────────┬─────────┬─────────┐
│ 0  │         │         │         │         │
│ 1  │ alice   │ bob     │ carol   │         │  ← All hashed to 1, probed to 2, 3
│    │         │ (probed)│ (probed)│         │
│ 5  │ dave    │         │         │         │
└────┴─────────┴─────────┴─────────┴─────────┘

Lookup Algorithm:
1. Hash key → index
2. If bucket[index] == key, return
3. If bucket[index] != key, probe next: (index + 1) % size
4. Repeat until found or empty bucket

Problem: CLUSTERING - consecutive filled buckets degrade to O(n)
```

**Implementation:**

```python
class OpenAddressingHashIndex:
    """
    Linear probing with tombstones for deletion
    """
    DELETED = object()  # Sentinel for deleted entries

    def __init__(self, capacity=1000):
        self.capacity = capacity
        self.keys = [None] * capacity
        self.values = [None] * capacity
        self.size = 0

    def get(self, key):
        index = self._hash(key)

        # Probe until found or empty
        for i in range(self.capacity):
            probe_index = (index + i) % self.capacity

            if self.keys[probe_index] is None:
                return None  # Not found (empty slot)

            if self.keys[probe_index] is self.DELETED:
                continue  # Skip tombstone

            if self.keys[probe_index] == key:
                return self.values[probe_index]  # Found!

        return None  # Table full, not found

    def put(self, key, value):
        if self.size / self.capacity > 0.75:  # Load factor check
            self._resize()

        index = self._hash(key)

        for i in range(self.capacity):
            probe_index = (index + i) % self.capacity

            # Empty or deleted slot - insert here
            if self.keys[probe_index] is None or \
               self.keys[probe_index] is self.DELETED:
                self.keys[probe_index] = key
                self.values[probe_index] = value
                self.size += 1
                return

            # Update existing key
            if self.keys[probe_index] == key:
                self.values[probe_index] = value
                return

        raise Exception("Hash table full!")  # Should never reach if resize works

    def delete(self, key):
        index = self._hash(key)

        for i in range(self.capacity):
            probe_index = (index + i) % self.capacity

            if self.keys[probe_index] is None:
                return False  # Not found

            if self.keys[probe_index] == key:
                self.keys[probe_index] = self.DELETED  # Tombstone
                self.values[probe_index] = None
                self.size -= 1
                return True

        return False

    def _hash(self, key):
        return hash(key) % self.capacity
```

🏗️ **ARCHITECT**: Comparison of collision strategies in production:

| Strategy | Pros | Cons | Used By |
|----------|------|------|---------|
| **Chaining** | Simple, handles high load factor well | Extra memory for pointers, cache-unfriendly | PostgreSQL hash indexes, Redis dictionaries |
| **Linear Probing** | Cache-friendly, no extra pointers | Clustering, requires low load factor (<70%) | Java HashMap (until overflow) |
| **Quadratic Probing** | Reduces clustering | More complex | Python dict (until 3.6) |
| **Double Hashing** | Best distribution | Two hash functions needed | Rarely used in databases |

**Interview talking point**: "Chaining is preferred in databases because insert/delete are simpler and it degrades gracefully under high load. Open addressing requires careful load factor management."

---

### 3. **Hash Index Internal Structure**

🎓 **PROFESSOR**: Let's examine the **on-disk layout** of a hash index:

```text
Database Hash Index File Structure:

┌──────────────────────────────────────────────────┐
│ HEADER BLOCK                                     │
│ - Magic number (file type identifier)           │
│ - Version                                        │
│ - Number of buckets                              │
│ - Load factor                                    │
│ - Hash function identifier                       │
└──────────────────────────────────────────────────┘
         ↓
┌──────────────────────────────────────────────────┐
│ BUCKET DIRECTORY (array of bucket page pointers) │
│ Bucket[0] → Page 10                              │
│ Bucket[1] → Page 15                              │
│ Bucket[2] → Page 23                              │
│ ...                                              │
│ Bucket[N] → Page 847                             │
└──────────────────────────────────────────────────┘
         ↓
┌──────────────────────────────────────────────────┐
│ BUCKET PAGE (contains actual key-value pairs)    │
│ ┌────────────────┬──────────────┐                │
│ │ Key Hash       │ Row Pointer  │                │
│ ├────────────────┼──────────────┤                │
│ │ 12847389       │ Page 5, #7   │                │
│ │ 12847401       │ Page 5, #19  │  ← Collisions  │
│ │ 12847405       │ Page 8, #3   │                │
│ └────────────────┴──────────────┘                │
│ Overflow Page Pointer → Page 150 (if needed)     │
└──────────────────────────────────────────────────┘
```

🏗️ **ARCHITECT**: PostgreSQL hash index implementation details:

```c
// Simplified PostgreSQL hash index structure
typedef struct HashMetaPageData {
    uint32      magic;           // 0x6440640 (hash index identifier)
    uint32      version;         // Version number
    uint32      ntuples;         // Total entries in index
    uint16      ffactor;         // Fill factor (entries per bucket)
    uint16      bsize;           // Bucket size in bytes
    uint16      bmsize;          // Bitmap size
    uint16      bmshift;         // Bitmap shift amount
    uint32      maxbucket;       // Current max bucket number
    uint32      highmask;        // Mask to modulo into entire table
    uint32      lowmask;         // Mask to modulo into lower half of table
} HashMetaPageData;

// Bucket page structure
typedef struct HashPageOpaqueData {
    BlockNumber hasho_prevblkno; // Previous bucket page (overflow chain)
    BlockNumber hasho_nextblkno; // Next bucket page (overflow chain)
    Bucket      hasho_bucket;    // Bucket number this page belongs to
    uint16      hasho_flag;      // Page type flags
    uint16      hasho_page_id;   // For debugging
} HashPageOpaqueData;
```

**Code Example - Simulating Bucket Overflow:**

```python
class DiskBasedHashIndex:
    """
    Hash index with overflow pages (like PostgreSQL)
    """
    PAGE_SIZE = 8192  # 8KB pages (standard DB page size)
    ENTRIES_PER_PAGE = 256  # Approximate

    class BucketPage:
        def __init__(self, bucket_num):
            self.bucket_num = bucket_num
            self.entries = []  # (key, row_pointer) tuples
            self.overflow_page = None  # Pointer to next page

        def is_full(self):
            return len(self.entries) >= DiskBasedHashIndex.ENTRIES_PER_PAGE

    def __init__(self, num_buckets=1024):
        self.num_buckets = num_buckets
        self.buckets = [self.BucketPage(i) for i in range(num_buckets)]
        self.total_pages = num_buckets  # Track storage usage

    def insert(self, key, row_pointer):
        bucket_index = hash(key) % self.num_buckets
        page = self.buckets[bucket_index]

        # Find page with space (follow overflow chain)
        while page.is_full():
            if page.overflow_page is None:
                # Create new overflow page
                page.overflow_page = self.BucketPage(bucket_index)
                self.total_pages += 1
                print(f"⚠️ Overflow page created for bucket {bucket_index}")

            page = page.overflow_page

        page.entries.append((key, row_pointer))

    def search(self, key):
        bucket_index = hash(key) % self.num_buckets
        page = self.buckets[bucket_index]

        # Search through overflow chain
        page_reads = 0
        while page is not None:
            page_reads += 1
            for entry_key, row_pointer in page.entries:
                if entry_key == key:
                    print(f"Found after {page_reads} page reads")
                    return row_pointer
            page = page.overflow_page

        print(f"Not found after {page_reads} page reads")
        return None

    def analyze_distribution(self):
        """
        Analyze hash distribution quality
        """
        chain_lengths = []
        for bucket in self.buckets:
            length = 0
            page = bucket
            while page is not None:
                length += 1
                page = page.overflow_page
            chain_lengths.append(length)

        return {
            'avg_chain_length': sum(chain_lengths) / len(chain_lengths),
            'max_chain_length': max(chain_lengths),
            'buckets_with_overflow': sum(1 for l in chain_lengths if l > 1),
            'total_pages': self.total_pages,
            'space_efficiency': self.num_buckets / self.total_pages
        }
```

---

### 4. **Hash vs B-Tree: When to Use Each**

🎓 **PROFESSOR**: The choice between hash and B-Tree indexes depends on **query patterns**:

```text
┌─────────────────────┬──────────────┬──────────────┐
│ Operation           │ Hash Index   │ B-Tree Index │
├─────────────────────┼──────────────┼──────────────┤
│ Equality (=)        │ O(1) ✅      │ O(log n)     │
│ Range (<, >, BETWEEN│ NOT SUPPORTED│ O(log n) ✅  │
│ Prefix (LIKE 'abc%')│ NOT SUPPORTED│ O(log n) ✅  │
│ ORDER BY            │ NOT SUPPORTED│ O(log n) ✅  │
│ MIN/MAX             │ NOT SUPPORTED│ O(log n) ✅  │
│ Space overhead      │ Low ✅       │ Higher       │
│ Write performance   │ Fast ✅      │ Slower       │
└─────────────────────┴──────────────┴──────────────┘
```

🏗️ **ARCHITECT**: Real decision tree for production:

```python
class IndexTypeSelector:
    """
    Automated index type selection based on query analysis
    """
    def analyze_query_pattern(self, query: str) -> str:
        """
        Return recommended index type
        """
        # Parse WHERE clause
        has_equality = '=' in query
        has_range = any(op in query for op in ['<', '>', 'BETWEEN'])
        has_like = 'LIKE' in query
        has_order_by = 'ORDER BY' in query

        # Decision tree
        if has_range or has_like or has_order_by:
            return "B-Tree (range/sorting required)"

        if has_equality and not (has_range or has_like or has_order_by):
            return "Hash (pure equality lookup)"

        return "B-Tree (default - most versatile)"

# Example usage:
selector = IndexTypeSelector()

# Query 1: Point lookup
query1 = "SELECT * FROM users WHERE user_id = 12345"
print(selector.analyze_query_pattern(query1))
# → "Hash (pure equality lookup)"

# Query 2: Range query
query2 = "SELECT * FROM orders WHERE created_at > '2024-01-01'"
print(selector.analyze_query_pattern(query2))
# → "B-Tree (range/sorting required)"

# Query 3: Sorted results
query3 = "SELECT * FROM products WHERE category = 'electronics' ORDER BY price"
print(selector.analyze_query_pattern(query3))
# → "B-Tree (range/sorting required)"
```

**Interview gold**: "Hash indexes are like a phone book organized by phone number - perfect if you know the exact number, useless if you want 'all numbers starting with 555'. B-Tree is like a dictionary - good for ranges ('all words from A to C')."

---

### 5. **Limitations of Hash Indexes**

🎓 **PROFESSOR**: Hash indexes have **significant limitations** that explain why they're rare in production databases:

#### Limitation 1: No Range Queries

```sql
-- ❌ Hash index CANNOT optimize this:
SELECT * FROM users WHERE age > 25;
SELECT * FROM orders WHERE created_at BETWEEN '2024-01-01' AND '2024-12-31';

-- Why? Hash destroys ordering:
hash(25) = 8473829
hash(26) = 1847293  ← No correlation!
hash(27) = 9384721
```

#### Limitation 2: No Prefix Matching

```sql
-- ❌ Hash index CANNOT optimize this:
SELECT * FROM users WHERE email LIKE 'john%';

-- Why? Must hash the ENTIRE key:
hash('john')           = 12345
hash('john@gmail.com') = 98765  ← Completely different!
```

#### Limitation 3: No Sorting

```sql
-- ❌ Hash index CANNOT optimize this:
SELECT * FROM products ORDER BY name;

-- Why? Hash output is random, not sorted
-- Database must fetch all rows and sort in memory
```

#### Limitation 4: Resize Overhead

```python
class ResizableHashIndex:
    """
    Demonstrates the cost of resizing
    """
    def resize(self):
        """
        When load factor exceeds threshold, must resize
        """
        old_buckets = self.buckets
        old_capacity = len(old_buckets)

        # Double the capacity
        new_capacity = old_capacity * 2
        self.buckets = [[] for _ in range(new_capacity)]

        # REHASH ALL ENTRIES - expensive!
        for bucket in old_buckets:
            for key, value in bucket:
                # Hash function output changes (% new_capacity)
                new_index = hash(key) % new_capacity  # Different from old!
                self.buckets[new_index].append((key, value))

        # Cost: O(n) where n = total entries
        # Blocks all operations during resize
        print(f"⚠️ Resized from {old_capacity} to {new_capacity} buckets")
        print(f"   Rehashed {self.size} entries")
```

#### Limitation 5: Poor Cache Locality

```text
B-Tree: Sequential leaf nodes (cache-friendly)
┌─────┬─────┬─────┬─────┐
│ 100 │ 200 │ 300 │ 400 │  ← Contiguous in memory
└─────┴─────┴─────┴─────┘

Hash: Random bucket access (cache-unfriendly)
┌─────┐     ┌─────┐     ┌─────┐
│ 537 │ ... │ 129 │ ... │ 874 │  ← Scattered in memory
└─────┘     └─────┘     └─────┘

Impact: More cache misses → slower in practice than theoretical O(1)
```

🏗️ **ARCHITECT**: Why PostgreSQL deprecated hash indexes (until v10):

```text
PostgreSQL History:
- Pre-v10: Hash indexes NOT crash-safe (not WAL-logged)
- Pre-v10: Hash indexes slower than B-Tree for most workloads
- v10+: Fixed WAL logging, but still rarely used

Why B-Tree dominates:
1. Versatile (equality + range + sorting)
2. Better cache locality
3. Predictable performance (no resize spikes)
4. Proven at scale

Hash index niche:
- Exact equality lookups only
- Key size > 100 bytes (hash reduces to fixed size)
- Non-relational data stores (Redis, Memcached)
```

---

### 6. **Production Hash Index Implementations**

🏗️ **ARCHITECT**: Real-world systems using hash indexes:

#### Redis: Hash-Based Dictionary

```python
# Redis uses hash table for all data structures
# Incremental rehashing to avoid blocking

class RedisDict:
    """
    Simplified Redis dict with incremental rehashing
    """
    def __init__(self):
        self.ht = [HashTable(size=4), None]  # Two hash tables
        self.rehashing = False
        self.rehash_idx = 0  # Current bucket being rehashed

    def get(self, key):
        # Search in ht[0]
        value = self.ht[0].get(key)
        if value:
            return value

        # If rehashing, also search in ht[1]
        if self.rehashing:
            return self.ht[1].get(key)

        return None

    def set(self, key, value):
        # If rehashing, move one bucket during each operation
        if self.rehashing:
            self._rehash_step()

        # Always insert into new table during rehashing
        if self.rehashing:
            self.ht[1].set(key, value)
        else:
            self.ht[0].set(key, value)

        # Check if need to start rehashing
        if self.ht[0].load_factor() > 1.0 and not self.rehashing:
            self._start_rehashing()

    def _start_rehashing(self):
        """
        Start incremental rehashing
        """
        self.ht[1] = HashTable(size=self.ht[0].size * 2)
        self.rehashing = True
        self.rehash_idx = 0
        print("Started incremental rehashing")

    def _rehash_step(self):
        """
        Rehash one bucket (called on every operation)
        """
        if self.rehash_idx >= self.ht[0].size:
            # Rehashing complete
            self.ht[0] = self.ht[1]
            self.ht[1] = None
            self.rehashing = False
            print("Completed incremental rehashing")
            return

        # Move all entries from ht[0][rehash_idx] to ht[1]
        bucket = self.ht[0].buckets[self.rehash_idx]
        for key, value in bucket:
            self.ht[1].set(key, value)

        self.ht[0].buckets[self.rehash_idx] = []  # Clear old bucket
        self.rehash_idx += 1
```

**Key insight**: Redis spreads rehashing across many operations to avoid blocking. Each command rehashes one bucket.

#### MySQL Memory Hash Indexes

```sql
-- MySQL MEMORY engine supports hash indexes
CREATE TABLE cache_table (
    key_col VARCHAR(100) PRIMARY KEY,
    value_col TEXT,
    expires_at TIMESTAMP
) ENGINE=MEMORY;  -- Uses hash index for PRIMARY KEY

-- Check index type:
SHOW INDEX FROM cache_table;
/*
+-------+--------+----------+--------------+
| Table | Key_name | Index_type            |
+-------+--------+----------+--------------+
| cache | PRIMARY  | HASH                  |
+-------+--------+----------+--------------+
*/

-- Works great:
SELECT * FROM cache_table WHERE key_col = 'user:12345';  -- O(1)

-- Does NOT use index (full scan):
SELECT * FROM cache_table WHERE key_col LIKE 'user:%';  -- O(n)
```

#### Cassandra: Partition Key Hashing

```java
// Cassandra uses consistent hashing for data distribution
public class CassandraPartitioner {

    /**
     * Murmur3 hash of partition key determines which node stores data
     */
    public long getToken(ByteBuffer partitionKey) {
        long hash = MurmurHash.hash3_x64_128(partitionKey);
        return hash;  // Token range: -2^63 to 2^63-1
    }

    /**
     * Find which node owns this token
     */
    public Node getNodeForToken(long token) {
        // Nodes arranged in ring by token range
        for (Node node : tokenRing) {
            if (token <= node.maxToken) {
                return node;
            }
        }
        return tokenRing.get(0);  // Wrap around
    }
}

// Example:
// Partition key: user_id = 12345
// hash(12345) = 8473829384729384
// Token → Node 3 (owns range 8000000000000000 - 9000000000000000)
```

---

## 🎯 **SYSTEM DESIGN INTERVIEW FRAMEWORK**

When asked about hash indexes in interviews:

### 1. Clarify Query Patterns
```
Questions to ask:
- Are queries primarily equality lookups (=) or ranges (<, >, BETWEEN)?
- Do results need to be sorted (ORDER BY)?
- Are there prefix searches (LIKE 'abc%')?
- What's the read vs write ratio?

If only equality lookups: Hash index is viable
If any range/sorting: B-Tree is required
```

### 2. Discuss Trade-offs
```
Hash Index Advantages:
✅ O(1) equality lookups (best case)
✅ Simpler implementation
✅ Lower memory overhead per entry
✅ Faster writes (no tree balancing)

Hash Index Disadvantages:
❌ No range queries
❌ No sorted retrieval
❌ Resize overhead (rehashing)
❌ Poor cache locality
❌ Less versatile than B-Tree
```

### 3. Implementation Details
```
Key decisions:
1. Hash function: MurmurHash3, CityHash, xxHash
2. Collision strategy: Chaining (most common) vs Open Addressing
3. Load factor: 0.75 (resize at 75% full)
4. Bucket count: Power of 2 for fast modulo (bitwise AND)
5. Incremental rehashing: Avoid blocking (Redis approach)
```

### 4. Production Examples
```
When hash indexes make sense:
- Cache systems (Redis, Memcached): Pure key-value lookups
- Session stores: Lookup by session ID
- Distributed systems: Consistent hashing for partitioning

When B-Tree is better:
- RDBMS primary indexes: Need range queries
- Time-series data: Need ORDER BY timestamp
- Text search: Need prefix matching
```

---

## 🧠 **MIND MAP: HASH INDEX CONCEPTS**
```
                    HASH INDEXES
                          |
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                  ↓
   HASH FUNCTION      COLLISION          LIMITATIONS
        |              RESOLUTION              |
   ┌────┴────┐      ┌────┴────┐          ┌────┴────┐
   ↓         ↓      ↓         ↓          ↓         ↓
MurmurHash CityHash Chaining  Open      No Range  No Sort
 Uniform   Fast             Addressing  Queries   Support
 O(1)      Non-crypto  LinkedList  Linear    LIKE %   ORDER BY
                                  Probing
```

### 💡 **EMOTIONAL ANCHORS**

1. **Hash Index = Locker System 🔐**
   - You have key #537 → Go directly to locker #537
   - Don't need to search all lockers
   - But can't find "lockers 500-600" (no range)

2. **Collision = Birthday Paradox 🎂**
   - Only 23 people needed for 50% chance of shared birthday
   - Only ~sqrt(buckets) keys before collision is likely
   - Must handle collisions gracefully

3. **Rehashing = Moving Houses 🏠**
   - When house (bucket) gets too crowded, must move
   - Must tell everyone your new address (rehash)
   - Expensive and disruptive (O(n) cost)

4. **Hash vs B-Tree = Phone Book vs Dictionary 📖**
   - Phone book by number: Hash (exact lookup)
   - Dictionary by word: B-Tree (ranges, sorting)
   - Different tools for different jobs

---

## 🔑 **KEY TAKEAWAYS**

```
1. Hash indexes provide O(1) lookups for EQUALITY ONLY
2. Cannot support range queries, sorting, or prefix matching
3. Use chaining for collision resolution (simpler, more robust)
4. Load factor ~0.75 balances space efficiency and performance
5. Rehashing is expensive - consider incremental rehashing
6. B-Tree is more versatile and often faster in practice
7. Hash indexes shine in key-value stores (Redis), not RDBMS
8. Use non-cryptographic hash functions (MurmurHash, xxHash)
```

**Production Mindset**:
> "Hash indexes are theoretically O(1), but B-Trees are often faster in practice due to better cache locality and CPU branch prediction. Always benchmark with real data before choosing hash over B-Tree."
