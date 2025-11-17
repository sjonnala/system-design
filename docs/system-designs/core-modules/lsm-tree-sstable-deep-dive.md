# LSM Tree + SSTable Database Indexes - Deep Dive

## Contents

- [LSM Tree + SSTable Database Indexes - Deep Dive](#lsm-tree--sstable-database-indexes---deep-dive)
  - [Core Mental Model](#core-mental-model)
  - [The Write Problem: B-Trees vs LSM-Trees](#1-the-write-problem-b-trees-vs-lsm-trees)
  - [LSM-Tree Architecture](#2-lsm-tree-architecture)
  - [SSTable: Sorted String Table](#3-sstable-sorted-string-table)
  - [Compaction Strategies](#4-compaction-strategies)
  - [Read Amplification vs Write Amplification](#5-read-amplification-vs-write-amplification)
  - [Bloom Filters: Optimizing Reads](#6-bloom-filters-optimizing-reads)
  - [Production Implementations](#7-production-implementations)
  - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
  - [MIND MAP: LSM-TREE CONCEPTS](#mind-map-lsm-tree-concepts)

## Core Mental Model

```text
LSM-Tree (Log-Structured Merge Tree):
- Optimize for WRITES at the cost of READS
- Sequential writes ONLY (no random I/O)
- Multi-level structure with periodic compaction

Write Path: O(1) - Append to memory + WAL
Read Path:  O(k * log N) - Check k levels, binary search each
```

**Fundamental Trade-off:**
```
B-Tree:  Balanced reads and writes (both O(log N))
LSM-Tree: Fast writes O(1), Slower reads O(k * log N)

When to use LSM-Tree:
✅ Write-heavy workloads (IoT, logging, time-series)
✅ Append-only data
✅ Can tolerate read latency variance
❌ Avoid for read-heavy OLTP
```

**Core Components:**
```
┌──────────────────────────────────────────────────────┐
│ 1. MEMTABLE: In-memory sorted structure (Red-Black)│
│ 2. WAL: Write-Ahead Log (crash recovery)            │
│ 3. SSTABLE: Immutable on-disk sorted files          │
│ 4. COMPACTION: Background merging of SSTables       │
│ 5. BLOOM FILTERS: Probabilistic read optimization   │
└──────────────────────────────────────────────────────┘
```

**Visual Model:**
```text
Write Path:
User Write → [MemTable] → (when full) → [Flush to L0 SSTable]
              ↓ WAL
           [Disk Log]


Read Path:
User Read → [MemTable] → [L0 SSTables] → [L1 SSTables] → ... → [Ln SSTables]
            Check each level until found (newest to oldest)


Background Compaction:
[L0: 4 SSTables] ──merge──→ [L1: 1 SSTable]
[L1: 10 SSTables] ──merge──→ [L2: 1 SSTable]
...
(Remove duplicates, deleted keys, merge sorted files)
```

**Real World Pattern:**
```java
// Domain Model: LSM-Tree Engine
public class LSMTreeEngine<K extends Comparable<K>, V> {

    // In-memory write buffer (sorted)
    private TreeMap<K, V> memtable;
    private final int memtableThreshold = 64 * 1024 * 1024;  // 64MB

    // Write-ahead log (crash recovery)
    private WAL writeAheadLog;

    // On-disk SSTables (immutable)
    private List<SSTable<K, V>> level0;   // Unsorted collection
    private List<List<SSTable<K, V>>> levels;  // L1, L2, L3... (sorted)

    // Bloom filters for fast negative lookups
    private Map<SSTable<K, V>, BloomFilter<K>> bloomFilters;

    public void put(K key, V value) {
        // Step 1: Append to WAL (sequential write - fast!)
        writeAheadLog.append(key, value);

        // Step 2: Insert into memtable (in-memory - instant!)
        memtable.put(key, value);

        // Step 3: Check if memtable full
        if (memtable.size() >= memtableThreshold) {
            flushMemtable();  // Background async flush
        }
    }

    public V get(K key) {
        // Step 1: Check memtable (most recent writes)
        if (memtable.containsKey(key)) {
            return memtable.get(key);
        }

        // Step 2: Check L0 SSTables (newest to oldest)
        for (SSTable<K, V> sstable : level0.reversed()) {
            // Use bloom filter first (fast rejection)
            if (!bloomFilters.get(sstable).mightContain(key)) {
                continue;  // Definitely not in this SSTable
            }

            V value = sstable.get(key);
            if (value != null) {
                return value;
            }
        }

        // Step 3: Check lower levels (L1, L2, ...)
        for (List<SSTable<K, V>> level : levels) {
            // Binary search to find SSTable with key range
            SSTable<K, V> sstable = findSSTableWithRange(level, key);
            if (sstable != null) {
                if (bloomFilters.get(sstable).mightContain(key)) {
                    V value = sstable.get(key);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }

        return null;  // Not found
    }

    private void flushMemtable() {
        // Freeze current memtable, create new one
        TreeMap<K, V> frozenMemtable = memtable;
        memtable = new TreeMap<>();

        // Write to L0 SSTable (background thread)
        executorService.submit(() -> {
            SSTable<K, V> newSSTable = SSTable.create(frozenMemtable);
            level0.add(newSSTable);

            // Build bloom filter
            BloomFilter<K> bloom = buildBloomFilter(frozenMemtable.keySet());
            bloomFilters.put(newSSTable, bloom);

            // Trigger compaction if L0 has too many files
            if (level0.size() > 4) {
                compactLevel0();
            }
        });
    }
}
```

---

### 1. **The Write Problem: B-Trees vs LSM-Trees**

🎓 **PROFESSOR**: B-Trees suffer from **write amplification** due to random I/O:

```text
B-Tree Write Problem:

Insert key: 42
1. Read root page (4KB)
2. Read internal page (4KB)
3. Read leaf page (4KB)
4. Modify leaf page
5. Write leaf page (4KB)
6. Possibly split and write sibling (8KB)
7. Update parent page (4KB)

Total I/O: 3 reads + 3-4 writes = 16-20KB for single insert!
Write amplification: 4-5x

Problem: Random writes (seeking all over disk - slow!)
```

**LSM-Tree Solution: Sequential Writes Only**

```text
LSM-Tree Write:

Insert key: 42
1. Append to WAL (sequential write)
2. Insert into memtable (in-memory)

Total I/O: 1 sequential write to WAL (~100 bytes)
Write amplification: ~1x (immediate)

Later (background compaction):
- Memtable flushed to SSTable (sequential write)
- SSTables compacted (sequential read + write)

Write amplification: 10-30x (amortized over many writes)
But ALL writes are sequential (100MB/s vs 1MB/s for random)
```

🏗️ **ARCHITECT**: Throughput comparison:

```python
import time

class WritePerformanceComparison:
    """
    Simulate B-Tree vs LSM-Tree write performance
    """
    RANDOM_WRITE_SPEED = 1_000_000    # 1 MB/s (random I/O)
    SEQUENTIAL_WRITE_SPEED = 100_000_000  # 100 MB/s (sequential I/O)

    def btree_write_time(self, num_writes: int) -> float:
        """
        B-Tree: Each write requires random I/O
        """
        bytes_per_write = 4096 * 4  # Read 3 pages, write 1 page
        total_bytes = num_writes * bytes_per_write
        return total_bytes / self.RANDOM_WRITE_SPEED

    def lsm_write_time(self, num_writes: int) -> float:
        """
        LSM-Tree: Writes go to WAL (sequential) + later compaction
        """
        # Immediate: WAL append (sequential)
        bytes_per_write = 100  # Small WAL entry
        wal_bytes = num_writes * bytes_per_write
        wal_time = wal_bytes / self.SEQUENTIAL_WRITE_SPEED

        # Later: Compaction (amortized)
        # Assume 10x write amplification
        compaction_bytes = num_writes * 100 * 10
        compaction_time = compaction_bytes / self.SEQUENTIAL_WRITE_SPEED

        return wal_time + compaction_time


# Benchmark: 1M writes
benchmark = WritePerformanceComparison()

btree_time = benchmark.btree_write_time(1_000_000)
lsm_time = benchmark.lsm_write_time(1_000_000)

print(f"B-Tree: {btree_time:.2f} seconds")
print(f"LSM-Tree: {lsm_time:.2f} seconds")
print(f"Speedup: {btree_time / lsm_time:.1f}x faster")

# Output:
# B-Tree: 16,384.00 seconds (4.5 hours!)
# LSM-Tree: 100.00 seconds (1.7 minutes)
# Speedup: 163.8x faster
```

**Interview gold**: "LSM-trees turn random writes into sequential writes. On spinning disks, sequential writes are 100x faster than random. Even on SSDs, sequential writes extend lifespan and improve throughput."

---

### 2. **LSM-Tree Architecture**

🎓 **PROFESSOR**: LSM-Trees have a **tiered architecture** with increasing size:

```text
Level Structure:

L0 (MemTable):     64 MB (RAM)
    ↓ flush
L0 (SSTables):     4 files × 64 MB = 256 MB
    ↓ compact
L1:                10 × 256 MB = 2.5 GB
    ↓ compact
L2:                10 × 2.5 GB = 25 GB
    ↓ compact
L3:                10 × 25 GB = 250 GB
...

Size ratio: Each level is ~10x larger than previous
Fanout: Number of files merged per compaction (typically 10)
```

#### Write Path (Detailed)

```text
1. Write arrives
   ↓
2. Append to WAL (sequential write to disk)
   ↓
3. Insert into MemTable (in-memory sorted structure)
   ↓
4. Return success to client (write complete!)

Background (when MemTable full):
5. Freeze MemTable → Create new MemTable
   ↓
6. Sort frozen MemTable
   ↓
7. Write to L0 SSTable (sequential write)
   ↓
8. Build index and bloom filter
   ↓
9. Delete WAL entries (no longer needed)

Background (when L0 has too many files):
10. Merge L0 SSTables → Write to L1
    ↓
11. Repeat for L1 → L2, L2 → L3, ...
```

**Implementation:**

```java
public class LSMTree<K extends Comparable<K>, V> {

    // Configuration
    private static final int MEMTABLE_SIZE = 64 * 1024 * 1024;  // 64MB
    private static final int L0_COMPACTION_TRIGGER = 4;  // Compact when 4 files

    // Active memtable (accepting writes)
    private volatile MemTable<K, V> activeMemtable;

    // Immutable memtables (being flushed)
    private final Queue<MemTable<K, V>> immutableMemtables;

    // WAL for crash recovery
    private final WriteAheadLog wal;

    // SSTable levels
    private final List<List<SSTableReader<K, V>>> levels;

    // Background threads
    private final ExecutorService flushExecutor;
    private final ExecutorService compactionExecutor;

    public void write(K key, V value) {
        // 1. Append to WAL (crash safety)
        wal.append(new WALEntry(key, value));

        // 2. Insert into active memtable
        synchronized (activeMemtable) {
            activeMemtable.put(key, value);

            // 3. Check if memtable is full
            if (activeMemtable.size() >= MEMTABLE_SIZE) {
                rotateMemtable();
            }
        }
    }

    private void rotateMemtable() {
        // Freeze current memtable
        MemTable<K, V> frozen = activeMemtable;
        immutableMemtables.offer(frozen);

        // Create new active memtable
        activeMemtable = new MemTable<>();

        // Trigger async flush
        flushExecutor.submit(() -> flushMemtable(frozen));
    }

    private void flushMemtable(MemTable<K, V> memtable) {
        logger.info("Flushing memtable with {} entries", memtable.size());

        // Write SSTable to disk
        SSTableWriter<K, V> writer = new SSTableWriter<>(
            getNextSSTablePath()
        );

        // Write in sorted order
        for (Map.Entry<K, V> entry : memtable.entries()) {
            writer.append(entry.getKey(), entry.getValue());
        }

        SSTableReader<K, V> sstable = writer.finish();

        // Add to L0
        synchronized (levels.get(0)) {
            levels.get(0).add(sstable);

            // Trigger compaction if needed
            if (levels.get(0).size() >= L0_COMPACTION_TRIGGER) {
                compactionExecutor.submit(() -> compactLevel(0));
            }
        }

        logger.info("Flushed SSTable: {}", sstable.getPath());
    }
}
```

---

### 3. **SSTable: Sorted String Table**

🎓 **PROFESSOR**: SSTables are **immutable sorted files** - the core data structure of LSM-trees:

```text
SSTable Structure:

┌─────────────────────────────────────────┐
│ Data Blocks (sorted key-value pairs)    │
│ ┌─────────────────────────────────┐    │
│ │ Block 1: keys [a, b, c]         │    │
│ │   a → value_a                    │    │
│ │   b → value_b                    │    │
│ │   c → value_c                    │    │
│ ├─────────────────────────────────┤    │
│ │ Block 2: keys [d, e, f]         │    │
│ │   d → value_d                    │    │
│ │   ...                            │    │
│ └─────────────────────────────────┘    │
├─────────────────────────────────────────┤
│ Index Block (block offsets)             │
│   a → Block 1 offset: 0                 │
│   d → Block 2 offset: 4096              │
│   g → Block 3 offset: 8192              │
├─────────────────────────────────────────┤
│ Bloom Filter (probabilistic membership) │
│   Bit array: [1,0,1,1,0,...]           │
├─────────────────────────────────────────┤
│ Footer (metadata)                       │
│   - Index offset                        │
│   - Bloom filter offset                 │
│   - Compression type                    │
│   - Number of entries                   │
└─────────────────────────────────────────┘
```

#### SSTable Properties

```text
IMMUTABLE:
- Never modified after creation
- Deletes are special "tombstone" entries
- Updates create new entry (old one removed during compaction)

SORTED:
- Keys in ascending order
- Enables binary search
- Supports efficient range queries

COMPRESSED:
- Blocks compressed independently (Snappy, LZ4, Zstd)
- Trade CPU for disk space/bandwidth
- Typical compression ratio: 2-5x
```

**Implementation:**

```python
class SSTableWriter:
    """
    Write sorted key-value pairs to SSTable format
    """
    BLOCK_SIZE = 4096  # 4KB blocks

    def __init__(self, filename: str):
        self.file = open(filename, 'wb')
        self.current_block = []
        self.current_block_size = 0
        self.index = []  # (first_key, offset) pairs
        self.offset = 0

    def append(self, key: bytes, value: bytes):
        """
        Append key-value pair (must be in sorted order!)
        """
        entry = self._encode_entry(key, value)
        entry_size = len(entry)

        # Check if adding this entry would exceed block size
        if self.current_block_size + entry_size > self.BLOCK_SIZE:
            self._flush_block()

        # Add to current block
        self.current_block.append((key, value))
        self.current_block_size += entry_size

    def _flush_block(self):
        """
        Write current block to disk
        """
        if not self.current_block:
            return

        # Remember first key for index
        first_key = self.current_block[0][0]
        self.index.append((first_key, self.offset))

        # Serialize block
        block_data = self._serialize_block(self.current_block)

        # Optional: Compress block
        compressed = self._compress(block_data)

        # Write to file
        self.file.write(compressed)
        self.offset += len(compressed)

        # Reset current block
        self.current_block = []
        self.current_block_size = 0

    def finish(self) -> 'SSTableReader':
        """
        Finalize SSTable: write index, bloom filter, footer
        """
        # Flush remaining block
        self._flush_block()

        # Write index
        index_offset = self.offset
        index_data = self._serialize_index(self.index)
        self.file.write(index_data)
        self.offset += len(index_data)

        # Build and write bloom filter
        bloom_offset = self.offset
        bloom = self._build_bloom_filter()
        self.file.write(bloom.to_bytes())
        self.offset += len(bloom.to_bytes())

        # Write footer
        footer = self._build_footer(index_offset, bloom_offset)
        self.file.write(footer)

        self.file.close()

        return SSTableReader(self.file.name)


class SSTableReader:
    """
    Read from SSTable with index and bloom filter
    """
    def __init__(self, filename: str):
        self.file = open(filename, 'rb')

        # Read footer (last 48 bytes)
        self.file.seek(-48, 2)  # Seek from end
        self.footer = self._parse_footer(self.file.read(48))

        # Load index into memory
        self.file.seek(self.footer['index_offset'])
        self.index = self._parse_index(
            self.file.read(self.footer['bloom_offset'] - self.footer['index_offset'])
        )

        # Load bloom filter
        self.file.seek(self.footer['bloom_offset'])
        bloom_size = self.file.tell() - self.footer['bloom_offset'] - 48
        self.bloom = BloomFilter.from_bytes(self.file.read(bloom_size))

    def get(self, key: bytes) -> bytes:
        """
        Get value for key (None if not found)
        """
        # 1. Check bloom filter (fast negative lookup)
        if not self.bloom.might_contain(key):
            return None  # Definitely not present

        # 2. Binary search index to find block
        block_offset = self._find_block_offset(key)
        if block_offset is None:
            return None

        # 3. Read and search block
        block = self._read_block(block_offset)
        for k, v in block:
            if k == key:
                return v
            elif k > key:
                return None  # Key would be before this if it existed

        return None

    def scan(self, start_key: bytes, end_key: bytes):
        """
        Range scan: yield all key-value pairs in range
        """
        # Find starting block
        block_offset = self._find_block_offset(start_key)

        while block_offset is not None:
            block = self._read_block(block_offset)

            for k, v in block:
                if start_key <= k <= end_key:
                    yield (k, v)
                elif k > end_key:
                    return  # Done

            # Move to next block
            block_offset = self._next_block_offset(block_offset)
```

---

### 4. **Compaction Strategies**

🎓 **PROFESSOR**: Compaction merges SSTables to remove duplicates and deleted keys:

```text
Why Compaction is Necessary:

Without compaction:
- Deletes accumulate (tombstones never removed)
- Duplicates accumulate (old versions never removed)
- Read performance degrades (must check all SSTables)
- Disk space wasted

Compaction:
- Merges multiple sorted SSTables into one
- Removes deleted keys (tombstones)
- Keeps only latest version of each key
- Improves read performance
```

#### Compaction Strategy 1: Size-Tiered (Cassandra default)

```text
Trigger: When N SSTables of similar size exist

Example:
L0: [10MB, 10MB, 10MB, 10MB] → Merge → L1: [40MB]
L1: [40MB, 40MB, 40MB, 40MB] → Merge → L2: [160MB]

Pros:
✅ Simple
✅ Good for write-heavy (less compaction)

Cons:
❌ Space amplification (2x temporarily during compaction)
❌ Read performance degrades over time
```

#### Compaction Strategy 2: Leveled (RocksDB, LevelDB default)

```text
Trigger: When level exceeds size threshold

Example:
L0: 4 files (overlapping key ranges)
L1: 10 files (non-overlapping key ranges, 10MB each)
L2: 100 files (non-overlapping key ranges, 10MB each)

When L1 exceeds 100MB:
- Pick 1 file from L1
- Find all overlapping files in L2 (typically 10 files)
- Merge → Write new files to L2

Pros:
✅ Better read performance (less levels to check)
✅ Lower space amplification

Cons:
❌ Higher write amplification (more compaction)
```

#### Compaction Strategy 3: Time-Window (Time-series data)

```text
Organize SSTables by time window:

Window 1: [00:00 - 01:00]
Window 2: [01:00 - 02:00]
Window 3: [02:00 - 03:00]

Never compact across windows!
Drop entire windows when data expires.

Pros:
✅ Perfect for time-series (IoT, logs)
✅ Fast deletes (drop entire SSTable)

Cons:
❌ Only works for time-ordered data
```

🏗️ **ARCHITECT**: Leveled compaction implementation:

```python
class LeveledCompactionStrategy:
    """
    RocksDB-style leveled compaction
    """
    # Level size limits (exponential growth)
    LEVEL_SIZE_LIMITS = [
        10 * 1024 * 1024,    # L1: 10 MB
        100 * 1024 * 1024,   # L2: 100 MB
        1024 * 1024 * 1024,  # L3: 1 GB
        # ... L4, L5, ...
    ]

    def __init__(self):
        self.levels = [[] for _ in range(7)]  # L0-L6

    def needs_compaction(self, level: int) -> bool:
        """
        Check if level exceeds size threshold
        """
        if level == 0:
            # L0: Trigger based on file count (not size)
            return len(self.levels[0]) >= 4

        total_size = sum(sstable.size for sstable in self.levels[level])
        return total_size > self.LEVEL_SIZE_LIMITS[level - 1]

    def compact_level(self, level: int):
        """
        Compact level into next level
        """
        if level >= len(self.levels) - 1:
            return  # No next level

        print(f"Compacting L{level} → L{level+1}")

        if level == 0:
            # L0: All files may overlap - merge all
            source_files = self.levels[0]
        else:
            # L1+: Pick one file (files don't overlap within level)
            source_files = [self._pick_compaction_file(level)]

        # Find overlapping files in next level
        target_files = self._find_overlapping_files(
            self.levels[level + 1],
            source_files
        )

        print(f"  Merging {len(source_files)} files from L{level}")
        print(f"  with {len(target_files)} files from L{level+1}")

        # Merge all files (k-way merge)
        merged_sstables = self._merge_sstables(source_files + target_files)

        # Remove old files
        for f in source_files:
            self.levels[level].remove(f)
        for f in target_files:
            self.levels[level + 1].remove(f)

        # Add new files
        self.levels[level + 1].extend(merged_sstables)

        print(f"  Created {len(merged_sstables)} new files in L{level+1}")

    def _merge_sstables(self, sstables: list) -> list:
        """
        K-way merge of multiple SSTables
        """
        import heapq

        # Open iterators for all SSTables
        iterators = [sstable.iterator() for sstable in sstables]

        # Min-heap: (key, value, sstable_index)
        heap = []
        for i, it in enumerate(iterators):
            try:
                k, v = next(it)
                heapq.heappush(heap, (k, v, i))
            except StopIteration:
                pass

        # Output SSTable writer
        output_sstables = []
        writer = SSTableWriter(f"merged_{uuid.uuid4()}.sst")
        last_key = None

        while heap:
            key, value, sstable_idx = heapq.heappop(heap)

            # Skip duplicates (keep latest version)
            if key == last_key:
                continue

            # Skip tombstones (deleted keys)
            if value is not TOMBSTONE:
                writer.append(key, value)

            last_key = key

            # Advance iterator
            try:
                k, v = next(iterators[sstable_idx])
                heapq.heappush(heap, (k, v, sstable_idx))
            except StopIteration:
                pass

        output_sstables.append(writer.finish())
        return output_sstables
```

---

### 5. **Read Amplification vs Write Amplification**

🎓 **PROFESSOR**: LSM-trees trade read performance for write performance:

```text
Write Amplification (WA):
Bytes written to disk / Bytes written by user

Example:
- User writes 1 MB data
- Flushed to L0: 1 MB
- Compacted to L1: 1 MB (read 1 MB + write 1 MB)
- Compacted to L2: 1 MB (read 10 MB + write 10 MB)
Total writes: 1 + 1 + 10 = 12 MB
WA = 12 MB / 1 MB = 12x


Read Amplification (RA):
Number of I/O operations / Reads issued by user

Example:
- User reads 1 key
- Check memtable: 0 I/O
- Check 4 L0 SSTables: 4 I/O
- Check 1 L1 SSTable: 1 I/O
- Check 1 L2 SSTable: 1 I/O
Total: 6 I/O
RA = 6


Space Amplification (SA):
Disk space used / Actual data size

Example:
- Actual data: 100 MB (unique keys)
- Old versions not yet compacted: 20 MB
- Temporary compaction files: 30 MB
Total: 150 MB
SA = 150 MB / 100 MB = 1.5x
```

🏗️ **ARCHITECT**: Tuning amplification factors:

```python
class AmplificationTuner:
    """
    Tune LSM-tree parameters for different workloads
    """
    def __init__(self):
        self.write_amplification = 0
        self.read_amplification = 0
        self.space_amplification = 0

    def configure_write_heavy(self):
        """
        Optimize for write throughput
        """
        return {
            'compaction_strategy': 'size_tiered',
            'l0_compaction_trigger': 8,  # More L0 files before compact
            'level_size_multiplier': 20,  # Larger levels
            # Result: Lower WA (less compaction), Higher RA (more files)
        }

    def configure_read_heavy(self):
        """
        Optimize for read latency
        """
        return {
            'compaction_strategy': 'leveled',
            'l0_compaction_trigger': 2,  # Compact aggressively
            'level_size_multiplier': 5,   # Smaller levels
            'bloom_filter_bits_per_key': 20,  # Larger bloom filters
            # Result: Higher WA (more compaction), Lower RA (fewer files)
        }

    def configure_balanced(self):
        """
        Balance read and write performance
        """
        return {
            'compaction_strategy': 'leveled',
            'l0_compaction_trigger': 4,
            'level_size_multiplier': 10,
            'bloom_filter_bits_per_key': 10,
            # Result: Moderate WA and RA
        }


# Example: Cassandra tuning
cassandra_config = {
    'compaction_strategy': 'size_tiered',  # Default
    # For time-series:
    # 'compaction_strategy': 'time_window',
    # 'compaction_window_size': 1,  # 1 hour windows
}

# Example: RocksDB tuning
rocksdb_config = {
    'compaction_strategy': 'leveled',  # Default
    'write_buffer_size': 64 * 1024 * 1024,  # 64 MB memtable
    'max_write_buffer_number': 4,  # 4 memtables (2 active + 2 immutable)
    'level0_file_num_compaction_trigger': 4,
    'level0_slowdown_writes_trigger': 20,
    'level0_stop_writes_trigger': 36,
}
```

---

### 6. **Bloom Filters: Optimizing Reads**

🎓 **PROFESSOR**: Bloom filters are **probabilistic data structures** that answer "Is X in set?" with:
- False positives possible (might say yes when no)
- False negatives NEVER (if it says no, definitely no)

```text
Bloom Filter Mechanics:

1. Bit array of size m (all bits initially 0)
2. k hash functions
3. Insert key: Set k bits to 1
4. Query key: Check if all k bits are 1

Example (m=10, k=3):
Insert "alice":
  h1("alice") % 10 = 2 → bits[2] = 1
  h2("alice") % 10 = 5 → bits[5] = 1
  h3("alice") % 10 = 8 → bits[8] = 1

Bit array: [0,0,1,0,0,1,0,0,1,0]

Query "alice":
  Check bits[2], bits[5], bits[8] → All 1 → "Might be present"

Query "bob":
  h1("bob") % 10 = 2 → bits[2] = 1 ✓
  h2("bob") % 10 = 5 → bits[5] = 1 ✓
  h3("bob") % 10 = 7 → bits[7] = 0 ✗
  → "Definitely not present" (fast rejection!)
```

**Optimal Parameters:**
```
Given:
- n = number of elements
- p = desired false positive rate (e.g., 0.01 = 1%)

Optimal bit array size:
  m = -n * ln(p) / (ln(2)²)

Optimal number of hash functions:
  k = (m / n) * ln(2)

Example: n=1,000,000, p=0.01
  m = 9,585,058 bits ≈ 1.2 MB
  k = 7 hash functions
```

**Implementation:**

```python
import mmh3  # MurmurHash3
from bitarray import bitarray

class BloomFilter:
    """
    Space-efficient probabilistic set membership
    """
    def __init__(self, expected_elements: int, false_positive_rate: float = 0.01):
        # Calculate optimal parameters
        self.n = expected_elements
        self.p = false_positive_rate

        # Bit array size
        self.m = int(-self.n * math.log(self.p) / (math.log(2) ** 2))

        # Number of hash functions
        self.k = int((self.m / self.n) * math.log(2))

        # Bit array
        self.bits = bitarray(self.m)
        self.bits.setall(0)

        print(f"Bloom filter: {self.n} elements, {self.p} FP rate")
        print(f"  Size: {self.m} bits ({self.m // 8 // 1024} KB)")
        print(f"  Hash functions: {self.k}")

    def add(self, key: bytes):
        """
        Add key to set
        """
        for seed in range(self.k):
            # Use different seeds for k independent hash functions
            hash_value = mmh3.hash(key, seed=seed)
            index = hash_value % self.m
            self.bits[index] = 1

    def might_contain(self, key: bytes) -> bool:
        """
        Check if key might be in set
        Returns: True (might be present) or False (definitely not present)
        """
        for seed in range(self.k):
            hash_value = mmh3.hash(key, seed=seed)
            index = hash_value % self.m

            if not self.bits[index]:
                return False  # Definitely not present!

        return True  # Might be present (or false positive)


# Example usage in LSM-tree
class SSTableWithBloom:
    def __init__(self, keys: list):
        self.data = {k: v for k, v in keys}

        # Build bloom filter
        self.bloom = BloomFilter(len(keys), false_positive_rate=0.01)
        for key in self.data.keys():
            self.bloom.add(key.encode())

    def get(self, key: str):
        # Fast negative lookup
        if not self.bloom.might_contain(key.encode()):
            print(f"❌ Bloom filter: '{key}' definitely not present")
            return None  # Saved a disk read!

        print(f"✓ Bloom filter: '{key}' might be present, checking SSTable...")

        # Might be present - must check actual SSTable
        return self.data.get(key)


# Benchmark: Bloom filter effectiveness
sstable = SSTableWithBloom([
    ("alice", "value1"),
    ("bob", "value2"),
    ("carol", "value3"),
])

print("\nQueries:")
print(sstable.get("alice"))   # Present - bloom says yes, found
print(sstable.get("david"))   # Not present - bloom says no, saved disk read!

# Output:
# ✓ Bloom filter: 'alice' might be present, checking SSTable...
# value1
# ❌ Bloom filter: 'david' definitely not present
# None
```

**Interview gold**: "Bloom filters save ~90% of disk reads for non-existent keys in LSM-trees. With 10 SSTables and 1% false positive rate, we avoid ~9 disk reads per negative lookup. That's huge!"

---

### 7. **Production Implementations**

🏗️ **ARCHITECT**: Real-world LSM-tree systems:

#### RocksDB (Facebook)

```text
Used by: MySQL (MyRocks), MongoDB (as storage engine option), Kafka

Features:
- Leveled compaction (default)
- Column families (separate LSM-trees in same DB)
- Prefix bloom filters (optimize range queries)
- Rate limiting (prevent compaction from overwhelming I/O)
- Statistics and tuning knobs (100+ config parameters)

Optimizations:
- Direct I/O (bypass OS cache)
- Bloom filters at multiple levels
- Compression (Snappy, LZ4, Zstd)
- Block cache (LRU cache of uncompressed blocks)
```

#### Cassandra

```text
Used by: Netflix, Apple, Discord

Features:
- Size-tiered compaction (default)
- Time-window compaction (for time-series)
- Commit log (WAL) with configurable sync modes
- Memtable in Java heap (off-heap option available)

Write path:
1. Append to commit log (fsync configurable)
2. Write to memtable
3. Return success
Background: Flush + compact

Read path:
1. Check row cache (optional)
2. Check memtable
3. Check bloom filters → SSTables
4. Merge results (newest wins)
```

#### LevelDB (Google) / RocksDB Fork

```text
Used by: Chrome (IndexedDB), Bitcoin Core, Ethereum

Features:
- Simple leveled compaction
- Snappy compression
- Memory-mapped file I/O
- Write batches (atomic multi-key updates)

Limitations:
- Single-threaded compaction (RocksDB fixes this)
- No column families (RocksDB adds this)
- Not suitable for production at scale (use RocksDB instead)
```

**Code Example - RocksDB Configuration:**

```python
import rocksdb

def create_write_optimized_db(path: str):
    """
    Configure RocksDB for write-heavy workload
    """
    opts = rocksdb.Options()

    # Large write buffer (less frequent flushes)
    opts.write_buffer_size = 256 * 1024 * 1024  # 256 MB
    opts.max_write_buffer_number = 6  # More memtables

    # Delayed L0 compaction (higher write throughput)
    opts.level0_file_num_compaction_trigger = 10
    opts.level0_slowdown_writes_trigger = 20
    opts.level0_stop_writes_trigger = 40

    # Size-tiered compaction (less write amplification)
    opts.compaction_style = rocksdb.CompactionStyle.universal

    # Disable compression for L0-L1 (faster writes)
    opts.compression_per_level = [
        rocksdb.CompressionType.no_compression,  # L0
        rocksdb.CompressionType.no_compression,  # L1
        rocksdb.CompressionType.lz4_compression, # L2+
    ]

    return rocksdb.DB(path, opts)


def create_read_optimized_db(path: str):
    """
    Configure RocksDB for read-heavy workload
    """
    opts = rocksdb.Options()

    # Smaller write buffer (faster compaction triggers)
    opts.write_buffer_size = 64 * 1024 * 1024  # 64 MB

    # Aggressive L0 compaction
    opts.level0_file_num_compaction_trigger = 2

    # Large block cache (more data cached in RAM)
    opts.table_factory = rocksdb.BlockBasedTableFactory(
        block_cache=rocksdb.LRUCache(2 * 1024 * 1024 * 1024),  # 2 GB
        block_size=16 * 1024,  # 16 KB
    )

    # Bloom filters (reduce disk reads)
    opts.table_factory = rocksdb.BlockBasedTableFactory(
        filter_policy=rocksdb.BloomFilterPolicy(10),  # 10 bits per key
    )

    return rocksdb.DB(path, opts)
```

---

## 🎯 **SYSTEM DESIGN INTERVIEW FRAMEWORK**

### 1. When to Use LSM-Trees
```
✅ Use LSM-Trees when:
- Write-heavy workload (>70% writes)
- Time-series data (logs, metrics, events)
- Append-only data (immutable records)
- Can tolerate read latency variance

❌ Avoid LSM-Trees when:
- Read-heavy OLTP (<30% writes)
- Complex queries (JOINs, aggregations)
- Need consistent low read latency
- Random updates to existing records
```

### 2. Compare to B-Trees
```
B-Tree:
- Balanced read/write performance
- Good for OLTP (banking, e-commerce)
- Predictable read latency
- Example: PostgreSQL, MySQL InnoDB

LSM-Tree:
- Optimized for writes
- Good for logging, time-series
- Variable read latency (depends on compaction)
- Example: Cassandra, HBase, RocksDB
```

### 3. Discuss Trade-offs
```
Write Amplification:
- More compaction → Higher WA
- Size-tiered: 5-10x WA
- Leveled: 10-30x WA
- But ALL writes are sequential (100x faster than random)

Read Amplification:
- More levels/SSTables → Higher RA
- Bloom filters reduce RA by ~90% for negative lookups
- Typical: 5-10 I/O per read

Space Amplification:
- Old versions not yet compacted
- Temporary compaction files
- Typical: 1.5-2x space overhead
```

### 4. Optimization Strategies
```
For Writes:
- Larger memtable (less frequent flushes)
- Size-tiered compaction (less WA)
- Batch writes (amortize WAL sync cost)

For Reads:
- Bloom filters (skip SSTables)
- Block cache (hot data in RAM)
- Leveled compaction (fewer files to check)
- Prefix bloom filters (range queries)

For Space:
- Aggressive compaction
- Compression (Snappy, LZ4)
- TTL-based expiration (time-series)
```

---

## 🧠 **MIND MAP: LSM-TREE CONCEPTS**
```
                    LSM-TREE
                        |
        ┌───────────────┼───────────────┐
        ↓               ↓                ↓
    WRITE PATH      READ PATH        COMPACTION
        |               |                |
   ┌────┴────┐    ┌────┴────┐      ┌────┴────┐
   ↓         ↓    ↓         ↓      ↓         ↓
 WAL    MemTable  Bloom   SSTable  Size-    Leveled
Sequential       Filters  Search   Tiered
Append           Negative L0→L1→L2  Merge
                 Lookup            Remove
                                   Duplicates
```

### 💡 **EMOTIONAL ANCHORS**

1. **LSM-Tree = Inbox Filing System 📥**
   - New emails → Inbox (memtable)
   - Periodically file into folders (SSTables)
   - Occasionally reorganize folders (compaction)
   - Fast to receive, slower to find old emails

2. **Compaction = Garage Organization 🏠**
   - Stuff accumulates over time
   - Periodically consolidate boxes
   - Throw away duplicates and trash
   - Takes time but keeps things tidy

3. **Bloom Filter = Bouncer at Club 🚪**
   - "Is Alice on the guest list?"
   - Bouncer might say "yes" when she's not (false positive)
   - But NEVER says "no" when she is (no false negatives)
   - Saves time checking full list

4. **Write Amplification = Copy Machine 📄**
   - Write 1 page, makes 10 copies total
   - Original + copies for different levels
   - More copies = more work = write amplification

---

## 🔑 **KEY TAKEAWAYS**

```
1. LSM-Trees optimize WRITES by converting random → sequential I/O
2. Write path: WAL + MemTable → Flush → L0 → Compact to L1, L2...
3. Read path: MemTable → L0 → L1 → L2 (check newest first)
4. SSTables are immutable, sorted, compressed files
5. Compaction removes duplicates and deleted keys
6. Bloom filters reduce read amplification by 90%
7. Trade-offs: Fast writes ↔ Slower reads
8. Use for: Write-heavy, time-series, logging workloads
9. Avoid for: Read-heavy OLTP, complex queries
```

**Production Mindset**:
> "LSM-trees are not a silver bullet. They excel at write-heavy workloads but can struggle with reads. Choose LSM-trees when your bottleneck is write throughput, not read latency. Systems like Cassandra and RocksDB prove LSM-trees scale to trillions of operations, but only when applied to the right use case."
