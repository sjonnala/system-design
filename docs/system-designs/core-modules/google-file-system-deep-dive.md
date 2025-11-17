# Google File System (GFS) Deep Dive

## Contents

- [Google File System (GFS) Deep Dive](#google-file-system-gfs-deep-dive)
  - [Core Mental Model](#core-mental-model)
  - [Architecture Components](#1-architecture-components)
  - [Write Path & Record Append](#2-write-path--record-append)
  - [Consistency Model & Guarantees](#3-consistency-model--guarantees)
  - [Fault Tolerance & Recovery](#4-fault-tolerance--recovery)
  - [Master Operations & Metadata Management](#5-master-operations--metadata-management)
  - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
    - [Requirements Clarification (RADIO: Requirements)](#1-requirements-clarification-radio-requirements)
    - [Capacity Estimation (RADIO: Scale)](#2-capacity-estimation-radio-scale)
    - [API Design (RADIO: API)](#3-api-design-radio-api)
    - [Data Model (RADIO: Data-model)](#4-data-model-radio-data-model)
    - [High-Level Design (RADIO: Initial Design)](#5-high-level-design-radio-initial-design)
    - [Deep Dives (RADIO: Optimize)](#6-deep-dives-radio-optimize)
  - [MIND MAP: GFS CONCEPTS](#mind-map-gfs-concepts)

## Core Mental Model

```text
GFS Design Philosophy:
Component failures are the norm, not the exception
Optimize for: Large files + Append operations + Sequential reads

Traditional FS: Optimized for random access, small files, POSIX semantics
GFS: Optimized for streaming reads, append-heavy workloads, relaxed consistency
```

**Three Fundamental Trade-offs:**
```
┌─────────────────────────────────────────────────────────┐
│ 1. Consistency vs Performance                           │
│    ├─ Relaxed consistency model                        │
│    └─ High throughput for append operations            │
│                                                         │
│ 2. Single Master vs Fault Tolerance                    │
│    ├─ Simplified design, central metadata              │
│    └─ Master is single point of failure (mitigated)   │
│                                                         │
│ 3. Large Chunk Size (64MB) vs Space Efficiency        │
│    ├─ Reduces metadata overhead                        │
│    ├─ Fewer client-master interactions                 │
│    └─ Internal fragmentation for small files           │
└─────────────────────────────────────────────────────────┘
```

**Why 64MB Chunks?**

🎓 **PROFESSOR**: This is a **fundamental trade-off in distributed storage**:

```text
Small chunk size (4KB - traditional FS):
✓ Good space efficiency
✓ Better for random access
✗ More metadata to manage
✗ More network round trips

Large chunk size (64MB - GFS):
✓ Reduced metadata (1 PB file = ~16M chunks)
✓ Fewer client-master interactions
✓ Persistent TCP connections to chunkservers
✗ Hotspots for small files
✗ Internal fragmentation
```

🏗️ **ARCHITECT**: Real-world implications:

```python
# GFS chunk calculation
class GFSChunkCalculator:
    CHUNK_SIZE = 64 * 1024 * 1024  # 64 MB

    def calculate_metadata(self, file_size_bytes: int) -> dict:
        """
        Show why large chunk size matters at scale
        """
        num_chunks = math.ceil(file_size_bytes / self.CHUNK_SIZE)

        # Each chunk needs metadata (64 bytes per chunk)
        metadata_size = num_chunks * 64

        return {
            'num_chunks': num_chunks,
            'metadata_size_mb': metadata_size / (1024 * 1024),
            'metadata_overhead_pct': (metadata_size / file_size_bytes) * 100
        }

# Example: 1 PB storage
calc = GFSChunkCalculator()
result = calc.calculate_metadata(1 * 1024**5)  # 1 PB

# Output:
# num_chunks: 16,777,216
# metadata_size_mb: 1,024 MB (1 GB metadata for 1 PB data!)
# metadata_overhead: 0.0001%

# If we used 4KB chunks instead:
# num_chunks: 268,435,456,000 (268 billion!)
# metadata_size_mb: 16,384,000 MB (16 TB metadata!)
# metadata_overhead: 1.6%
```

**Key insight**: At Google's scale (petabytes), even 0.1% metadata overhead is significant. 64MB chunks keep master's in-memory metadata manageable.

---

## 1. Architecture Components

🎓 **PROFESSOR**: GFS follows a **master-worker architecture** with three key components:

```text
┌─────────────────────────────────────────────────────────┐
│                    GFS CLIENT                           │
│  (Application code + GFS client library)                │
└──────────────┬────────────────┬─────────────────────────┘
               │                │
        (metadata)         (data flow)
               │                │
               ↓                ↓
     ┌─────────────────┐  ┌──────────────────┐
     │  MASTER (1)     │  │  CHUNKSERVERS    │
     │                 │  │   (100-1000s)    │
     │  - Namespace    │  │                  │
     │  - Metadata     │  │  - Store chunks  │
     │  - Chunk lease  │  │  - Serve data    │
     │  - GC           │  │  - Checksums     │
     └─────────────────┘  └──────────────────┘
```

### Master Responsibilities

```java
/**
 * Master stores ALL metadata in memory
 * Persists to disk via operation log + checkpoints
 */
public class GFSMaster {

    // In-memory data structures
    private Map<String, FileMetadata> namespace;           // filename -> metadata
    private Map<ChunkHandle, ChunkMetadata> chunkMetadata; // chunk -> locations
    private Map<ChunkHandle, LeaseInfo> chunkLeases;       // chunk -> primary lease

    /**
     * File namespace operations
     */
    public ChunkHandle createFile(String filename) {
        // 1. Acquire namespace lock
        // 2. Log operation to disk
        // 3. Update in-memory namespace
        // 4. Return chunk handles

        FileMetadata metadata = new FileMetadata(filename);
        namespace.put(filename, metadata);

        // Create initial chunk
        ChunkHandle handle = generateChunkHandle();
        List<String> chunkservers = selectChunkServers(3); // 3x replication

        chunkMetadata.put(handle, new ChunkMetadata(chunkservers));
        return handle;
    }

    /**
     * Grant lease to primary replica
     * Primary orders all mutations for a chunk
     */
    public LeaseInfo grantLease(ChunkHandle chunk) {
        // Lease timeout: 60 seconds (renewable)
        String primary = selectPrimary(chunk);
        LeaseInfo lease = new LeaseInfo(primary,
                                       Instant.now().plusSeconds(60));
        chunkLeases.put(chunk, lease);
        return lease;
    }

    /**
     * Heartbeat from chunkserver (every ~few seconds)
     */
    public void handleHeartbeat(ChunkServerInfo server) {
        // 1. Update chunkserver status
        // 2. Collect chunk inventory
        // 3. Issue commands (replicate, delete, etc.)

        server.setLastHeartbeat(Instant.now());

        // Garbage collection: delete orphaned chunks
        List<ChunkHandle> orphans = findOrphanedChunks(server);
        server.sendDeleteCommand(orphans);
    }
}
```

### Chunkserver Implementation

```python
class ChunkServer:
    """
    Stores chunks as Linux files
    Each chunk is a plain Linux file in local disk
    """

    def __init__(self, chunk_dir: str):
        self.chunk_dir = chunk_dir
        self.chunks: Dict[ChunkHandle, str] = {}  # handle -> filepath
        self.checksums: Dict[ChunkHandle, List[int]] = {}

    def store_chunk(self, handle: ChunkHandle, data: bytes, offset: int):
        """
        Write data to chunk
        Chunks are stored as Linux files
        """
        filepath = f"{self.chunk_dir}/{handle}"

        with open(filepath, 'r+b') as f:
            f.seek(offset)
            f.write(data)

        # Update checksum for affected blocks (64KB blocks)
        self.update_checksum(handle, offset, len(data))

    def read_chunk(self, handle: ChunkHandle, offset: int, length: int) -> bytes:
        """
        Read data from chunk
        Verify checksum before returning
        """
        filepath = f"{self.chunk_dir}/{handle}"

        # Verify checksum
        if not self.verify_checksum(handle, offset, length):
            raise ChecksumMismatchError(f"Corruption detected in chunk {handle}")

        with open(filepath, 'rb') as f:
            f.seek(offset)
            return f.read(length)

    def update_checksum(self, handle: ChunkHandle, offset: int, length: int):
        """
        Checksum at 64KB block granularity
        Allows partial chunk verification
        """
        BLOCK_SIZE = 64 * 1024
        start_block = offset // BLOCK_SIZE
        end_block = (offset + length) // BLOCK_SIZE

        filepath = f"{self.chunk_dir}/{handle}"

        with open(filepath, 'rb') as f:
            for block_num in range(start_block, end_block + 1):
                f.seek(block_num * BLOCK_SIZE)
                block_data = f.read(BLOCK_SIZE)
                checksum = crc32(block_data)

                if handle not in self.checksums:
                    self.checksums[handle] = []

                self.checksums[handle][block_num] = checksum
```

**Interview talking point**: "GFS chunkservers don't have a complex storage engine. Each chunk is just a Linux file. Simplicity reduces bugs and leverages OS page cache."

---

## 2. Write Path & Record Append

🎓 **PROFESSOR**: The write path is GFS's most complex operation. It involves **decoupling control flow from data flow**:

```text
Control Flow: Client → Master → Client
Data Flow:    Client → Closest Replica → Chain → All Replicas
```

### Standard Write Operation

```text
Step 1: Client asks master for chunk locations + lease holder
Step 2: Master returns: [Primary, Secondary1, Secondary2] + lease info
Step 3: Client pushes data to ALL replicas (pipelined, no order)
Step 4: After all replicas ACK data receipt, client sends WRITE to primary
Step 5: Primary assigns serial order, applies mutation, tells secondaries
Step 6: Secondaries apply in same order, ACK to primary
Step 7: Primary replies to client (success or failure)
```

🏗️ **ARCHITECT**: Code implementation of write path:

```java
public class GFSClient {

    public void write(String filename, long offset, byte[] data)
            throws IOException {

        // Step 1: Get chunk location from master
        ChunkInfo chunkInfo = master.getChunkInfo(filename, offset);
        ChunkHandle handle = chunkInfo.getHandle();
        List<String> replicas = chunkInfo.getReplicas();
        String primary = chunkInfo.getPrimary();

        // Step 2: Push data to ALL replicas (in any order)
        // Use pipelining: forward to next replica while receiving
        String dataId = UUID.randomUUID().toString();
        pushDataToReplicas(replicas, dataId, data);

        // Step 3: Send write command to primary
        // Primary will apply serial order
        WriteRequest request = new WriteRequest(
            handle,
            dataId,
            offset,
            data.length
        );

        WriteResponse response = sendToPrimary(primary, request);

        if (response.isSuccess()) {
            return;
        } else {
            // Retry logic: some replicas may have failed
            // GFS allows inconsistent replicas temporarily
            handleWriteFailure(response);
        }
    }

    /**
     * Pipeline data to replicas
     * Replica A forwards to B while receiving from client
     * Minimizes latency in multi-datacenter setup
     */
    private void pushDataToReplicas(List<String> replicas,
                                    String dataId,
                                    byte[] data) {
        // Find closest replica (by network topology)
        String closest = findClosestReplica(replicas);

        // Send to closest, which will forward to others
        // This is called "chain replication topology"
        PushDataRequest request = new PushDataRequest(
            dataId,
            data,
            replicas.stream()
                   .filter(r -> !r.equals(closest))
                   .collect(Collectors.toList())
        );

        sendData(closest, request);
    }
}
```

### Record Append (Atomic Append)

🎓 **PROFESSOR**: Record append is GFS's **killer feature** for distributed applications:

**Problem**: Multiple producers writing to same file (e.g., MapReduce output)
**Traditional approach**: Coordinate via locks (slow, complex)
**GFS approach**: Atomic append at offset chosen by GFS

```java
public class RecordAppendOperation {

    /**
     * Record Append: GFS picks offset, guarantees atomicity
     * Perfect for producer-consumer patterns
     */
    public long recordAppend(String filename, byte[] record) {

        // Step 1: Get chunk info from master
        ChunkInfo chunkInfo = master.getChunkInfoForAppend(filename);

        // Step 2: Primary picks offset
        // If record doesn't fit in current chunk, pad + allocate new chunk
        AppendRequest request = new AppendRequest(
            chunkInfo.getHandle(),
            record
        );

        AppendResponse response = sendToPrimary(
            chunkInfo.getPrimary(),
            request
        );

        if (response.isSuccess()) {
            return response.getOffset();  // Offset where record was written
        } else if (response.getError() == ErrorCode.CHUNK_FULL) {
            // Retry with new chunk
            return recordAppend(filename, record);
        } else {
            // Partial failure: some replicas succeeded, some failed
            // GFS handles this via:
            // 1. Padding failed replicas to same offset
            // 2. Retrying append (may create duplicates)
            throw new AppendException("Record append failed");
        }
    }
}
```

**Consistency guarantee**:
- **At-least-once delivery**: Record may be duplicated on retry
- **Atomic visibility**: Record is fully written or not visible
- **Serial order**: Primary defines order

```python
# Real-world usage: MapReduce writing intermediate results
class MapReduceWriter:

    def emit_intermediate(self, key: str, value: str):
        """
        Multiple mappers append to same GFS file
        No coordination needed!
        """
        record = f"{key}\t{value}\n".encode()

        # GFS handles concurrency
        # No locks, no coordination
        offset = gfs_client.record_append(
            "/mapreduce/job123/intermediate",
            record
        )

        # Application handles potential duplicates in reduce phase
        # This is cheaper than distributed locking!
```

**Interview gold**: "Record append is why MapReduce is simple to implement. Multiple workers append to same file without locks. The reduce phase deduplicates. This is a classic trade-off: accept duplicate data to avoid coordination overhead."

---

## 3. Consistency Model & Guarantees

🎓 **PROFESSOR**: GFS has a **relaxed consistency model** that differs from POSIX filesystem semantics:

```text
Consistency Spectrum:

Strong Consistency        Eventual Consistency         GFS Model
(Linearizable)           (No guarantees)              (Defined Inconsistency)
        ↓                        ↓                            ↓
   POSIX FS                  DNS, Cassandra         Defined + Undefined regions
   (slow)                    (fast, but...)          (fast + predictable)
```

### Consistency States

```
┌──────────────────────────────────────────────────────────────┐
│                    After Mutation                            │
├──────────────────┬───────────────────┬───────────────────────┤
│                  │   Write           │   Record Append       │
├──────────────────┼───────────────────┼───────────────────────┤
│ Serial Success   │ DEFINED           │ DEFINED INTERSPERSED  │
│                  │ (consistent)      │ (at-least-once)       │
├──────────────────┼───────────────────┼───────────────────────┤
│ Concurrent       │ CONSISTENT        │ CONSISTENT            │
│ Success          │ but UNDEFINED     │ but UNDEFINED         │
├──────────────────┼───────────────────┼───────────────────────┤
│ Failure          │ INCONSISTENT      │ INCONSISTENT          │
│                  │ (replicas differ) │ (replicas differ)     │
└──────────────────┴───────────────────┴───────────────────────┘
```

**Definitions**:
- **Consistent**: All replicas have same data
- **Defined**: Consistent + application can see exactly what was written
- **Inconsistent**: Replicas differ (transient state, will be garbage collected)

🏗️ **ARCHITECT**: How applications handle this:

```python
class GFSApplicationPattern:
    """
    Best practices for working with GFS consistency model
    """

    def write_with_verification(self, filename: str, data: bytes):
        """
        Pattern 1: Write with checksum for verification
        """
        # Embed checksum in data
        checksum = hashlib.sha256(data).hexdigest()
        record = {
            'data': data,
            'checksum': checksum,
            'timestamp': time.time()
        }

        serialized = pickle.dumps(record)
        offset = self.gfs.write(filename, serialized)

        # Verification read
        read_data = self.gfs.read(filename, offset, len(serialized))
        read_record = pickle.loads(read_data)

        if read_record['checksum'] != checksum:
            # Undefined region, retry
            return self.write_with_verification(filename, data)

        return offset

    def append_with_deduplication(self, filename: str, record: bytes):
        """
        Pattern 2: Handle duplicate records from retried appends
        Use unique ID for deduplication
        """
        record_id = uuid.uuid4().bytes
        payload = record_id + record

        # Append (may create duplicates on retry)
        self.gfs.record_append(filename, payload)

        # Reader will deduplicate by record_id

    def read_with_undefined_handling(self, filename: str):
        """
        Pattern 3: Skip undefined regions when reading
        """
        offset = 0
        records = []

        while offset < file_size:
            try:
                # Try to read record
                record_with_checksum = self.gfs.read(filename, offset, RECORD_SIZE)

                # Verify checksum
                if self.verify_checksum(record_with_checksum):
                    records.append(record_with_checksum)
                    offset += RECORD_SIZE
                else:
                    # Undefined region, skip ahead
                    offset += RECORD_SIZE

            except ChecksumError:
                # Skip corrupted region
                offset += RECORD_SIZE

        return records
```

**Interview talking point**: "GFS's consistency model is weaker than POSIX but stronger than eventual consistency. Applications embed checksums and use record append for at-least-once delivery. This is a deliberate trade-off: weaker semantics enable higher performance."

---

## 4. Fault Tolerance & Recovery

🎓 **PROFESSOR**: GFS is designed for **component failure as the norm**:

```text
Failure Model at Google Scale:
- 1000 machines = ~1 failure per day (MTBF ≈ 3 years per machine)
- Disk failure rate: ~1% per year
- Network partitions, power outages, human errors

GFS Assumes: Everything fails, all the time
```

### Chunkserver Failure & Recovery

```java
public class ChunkReplicationManager {

    private static final int TARGET_REPLICATION = 3;
    private static final int MAX_REPLICATION = 5;

    /**
     * Master monitors chunkserver health via heartbeats
     * Re-replicates chunks when replica count drops
     */
    @Scheduled(fixedDelay = 1000)  // Check every second
    public void monitorReplication() {

        for (ChunkHandle chunk : allChunks) {
            List<String> replicas = getAliveReplicas(chunk);

            if (replicas.size() < TARGET_REPLICATION) {
                // Under-replicated: create new replica ASAP
                priorityQueue.add(new ReplicationTask(
                    chunk,
                    Priority.HIGH,
                    TARGET_REPLICATION - replicas.size()
                ));
            } else if (replicas.size() > MAX_REPLICATION) {
                // Over-replicated: delete excess replicas
                deleteExcessReplicas(chunk, replicas);
            }
        }
    }

    /**
     * Clone chunk from existing replica to new chunkserver
     */
    public void replicateChunk(ChunkHandle chunk) {
        // 1. Select source replica (load balancing)
        String source = selectSourceReplica(chunk);

        // 2. Select destination chunkserver
        //    Consider: disk space, load, network topology
        String destination = selectDestination();

        // 3. Tell destination to clone from source
        //    This is chunkserver-to-chunkserver transfer
        //    Master is not in data path!
        cloneCommand = new CloneCommand(chunk, source);
        sendToChunkserver(destination, cloneCommand);
    }
}
```

### Master Failure & Recovery

🏗️ **ARCHITECT**: Master is single point of failure, mitigated via:

```python
class MasterRecovery:
    """
    Master persists state via:
    1. Operation log (append-only)
    2. Checkpoints (periodic snapshots)
    """

    def __init__(self):
        self.operation_log = OperationLog("/gfs/master/log")
        self.checkpoint_manager = CheckpointManager("/gfs/master/checkpoint")

    def perform_operation(self, operation: Operation):
        """
        All metadata mutations go through operation log
        """
        # 1. Append to log (sync to disk + replicas)
        self.operation_log.append(operation)

        # 2. Apply to in-memory state
        self.apply_to_memory(operation)

        # 3. Checkpoint periodically
        if self.operation_log.size() > CHECKPOINT_THRESHOLD:
            self.create_checkpoint()

    def create_checkpoint(self):
        """
        B-tree snapshot of namespace + chunk metadata
        Allows fast recovery
        """
        checkpoint = {
            'namespace': self.namespace.serialize(),
            'chunk_metadata': self.chunk_metadata.serialize(),
            'version': self.current_version
        }

        # Write to disk (not blocking operations)
        # Use shadow master to create checkpoint
        self.checkpoint_manager.save(checkpoint)

        # Truncate old operation log entries
        self.operation_log.truncate_before(checkpoint['version'])

    def recover_from_failure(self):
        """
        Master recovery process
        """
        # 1. Load latest checkpoint
        checkpoint = self.checkpoint_manager.load_latest()
        self.namespace = checkpoint['namespace']
        self.chunk_metadata = checkpoint['chunk_metadata']
        last_version = checkpoint['version']

        # 2. Replay operation log from checkpoint
        for operation in self.operation_log.read_from(last_version):
            self.apply_to_memory(operation)

        # 3. Poll all chunkservers for chunk inventory
        #    Rebuild chunk location info (not persisted)
        for chunkserver in self.all_chunkservers:
            chunks = chunkserver.get_chunk_inventory()
            self.update_chunk_locations(chunkserver, chunks)

        # Recovery complete (typically < 1 minute)
        logger.info("Master recovered successfully")
```

**Shadow Masters**:
```text
┌─────────────────────────────────────────────────────────┐
│  PRIMARY MASTER                                         │
│  - Handles all mutations                                │
│  - Streams operation log to shadows                     │
└────────────┬────────────────────────────────────────────┘
             │ (replicate log)
             ↓
┌─────────────────────────────────────────────────────────┐
│  SHADOW MASTERS (read-only)                             │
│  - Lag slightly behind primary                          │
│  - Can serve read-only requests                         │
│  - Provide monitoring and diagnostics                   │
└─────────────────────────────────────────────────────────┘
```

**Interview gold**: "Master recovery is fast because chunk locations are NOT persisted. After loading checkpoint + log replay, master polls all chunkservers. This rebuilds location info in ~1 minute."

---

## 5. Master Operations & Metadata Management

🎓 **PROFESSOR**: Master must manage **petabytes of data with 60-100 bytes per chunk**:

```text
Metadata Scale Example:
1 PB storage ÷ 64 MB per chunk = 16 million chunks
16M chunks × 64 bytes metadata = 1 GB RAM

With 3x replication:
3 PB raw storage → 1 PB usable
Still only 1 GB metadata (chunk locations not counted)
```

### Namespace Management

```java
/**
 * GFS namespace is NOT a traditional directory tree
 * It's a lookup table: full_pathname → metadata
 * Uses prefix compression for memory efficiency
 */
public class NamespaceManager {

    // Full pathname as key (prefix compressed)
    private PrefixTree<FileMetadata> namespace;

    // Read-write locks at directory level
    private Map<String, ReadWriteLock> directoryLocks;

    /**
     * Create file: acquire locks on all parent directories
     * Example: /home/user/foo
     * - Read lock on /home
     * - Read lock on /home/user
     * - Write lock on /home/user/foo
     */
    public void createFile(String pathname) {
        String[] components = pathname.split("/");
        List<ReadWriteLock> acquiredLocks = new ArrayList<>();

        try {
            // Acquire read locks on all parent directories
            for (int i = 0; i < components.length - 1; i++) {
                String dir = String.join("/",
                    Arrays.copyOfRange(components, 0, i + 1));
                ReadWriteLock lock = getDirectoryLock(dir);
                lock.readLock().lock();
                acquiredLocks.add(lock);
            }

            // Acquire write lock on file itself
            ReadWriteLock fileLock = getDirectoryLock(pathname);
            fileLock.writeLock().lock();
            acquiredLocks.add(fileLock);

            // Create file metadata
            FileMetadata metadata = new FileMetadata(pathname);
            namespace.put(pathname, metadata);

            // Log operation
            operationLog.append(new CreateFileOp(pathname));

        } finally {
            // Release locks in reverse order
            Collections.reverse(acquiredLocks);
            acquiredLocks.forEach(lock -> {
                if (lock.isWriteLocked()) lock.writeLock().unlock();
                else lock.readLock().unlock();
            });
        }
    }

    /**
     * Snapshot: copy-on-write at chunk level
     * Uses reference counting
     */
    public void createSnapshot(String source, String destination) {
        // 1. Revoke leases on chunks (prevent concurrent writes)
        revokeLeases(source);

        // 2. Duplicate namespace entry
        //    Chunks are shared (copy-on-write)
        FileMetadata sourceMeta = namespace.get(source);
        FileMetadata destMeta = sourceMeta.clone();
        namespace.put(destination, destMeta);

        // 3. Increment reference count on chunks
        for (ChunkHandle chunk : sourceMeta.getChunks()) {
            incrementRefCount(chunk);
        }

        // 4. On next write to chunk, make copy first
        //    (lazy copy-on-write)

        // Log operation
        operationLog.append(new SnapshotOp(source, destination));
    }
}
```

### Garbage Collection

🏗️ **ARCHITECT**: GFS uses **lazy garbage collection** instead of immediate deletion:

```python
class GarbageCollector:
    """
    GFS garbage collection: lazy, background process
    Simplifies error handling and recovery
    """

    def delete_file(self, filename: str):
        """
        Step 1: Hide file (rename to hidden)
        Actual deletion happens later via GC
        """
        hidden_name = f"/.trash/{uuid.uuid4()}/{filename}"
        self.namespace.rename(filename, hidden_name)
        self.namespace.set_deletion_timestamp(hidden_name, time.time())

        # File is now invisible to clients
        # But chunks still exist (for undelete)

    @scheduled(interval=timedelta(hours=1))
    def garbage_collect_namespace(self):
        """
        Step 2: Remove old hidden files from namespace
        """
        cutoff_time = time.time() - (3 * 24 * 3600)  # 3 days

        for filename in self.namespace.list_hidden_files():
            deletion_time = self.namespace.get_deletion_timestamp(filename)

            if deletion_time < cutoff_time:
                # Remove from namespace
                # Chunks become orphans (no file references them)
                self.namespace.delete(filename)

    @scheduled(interval=timedelta(hours=6))
    def garbage_collect_chunks(self):
        """
        Step 3: Chunkservers report chunks to master
        Master identifies orphans (not in any file)
        """
        all_referenced_chunks = set()

        # Scan namespace for all chunk references
        for file_metadata in self.namespace.all_files():
            all_referenced_chunks.update(file_metadata.chunks)

        # Tell each chunkserver which chunks to delete
        for chunkserver in self.all_chunkservers:
            reported_chunks = chunkserver.get_chunk_list()
            orphans = reported_chunks - all_referenced_chunks

            if orphans:
                chunkserver.delete_chunks(orphans)
                logger.info(f"GC: {len(orphans)} orphaned chunks on {chunkserver}")
```

**Why lazy GC?**
1. **Simplicity**: No distributed transaction for delete
2. **Undelete**: Can recover files within grace period
3. **Batching**: Amortize overhead across many deletes
4. **Fault tolerance**: Crash during delete doesn't leave inconsistent state

**Interview talking point**: "GFS garbage collection is lazy by design. Delete is a rename, actual cleanup happens hours later. This simplifies error handling: if master crashes during delete, no harm done. Recovery will continue GC later."

---

## SYSTEM DESIGN INTERVIEW FRAMEWORK

When asked to *"Design a distributed file system like GFS"*, use this structure:

### 1. Requirements Clarification (RADIO: Requirements)
```
Functional:
- Store and retrieve large files (multi-GB to TB)
- Support append operations (MapReduce use case)
- Snapshot capability
- Concurrent readers/writers

Non-Functional:
- Scale: Petabytes of storage, hundreds of clients
- Availability: 99.9% (tolerates frequent component failures)
- Throughput over latency (batch processing workload)
- Consistency: Relaxed (defined inconsistency acceptable)

Assumptions:
- Large files (100MB - 1TB average)
- Mostly sequential reads and appends
- Commodity hardware (failures expected)
```

### 2. Capacity Estimation (RADIO: Scale)
```
Storage:
- Total capacity: 1 PB usable
- With 3x replication: 3 PB raw storage
- Chunk size: 64 MB
- Number of chunks: 1 PB / 64 MB = ~16 million chunks

Metadata:
- Per chunk metadata: 64 bytes
- Total metadata: 16M × 64 bytes = 1 GB (fits in RAM!)
- Operation log: ~50 GB (with periodic checkpoints)

Throughput:
- Read throughput: 100 GB/sec (aggregate)
- Write throughput: 50 GB/sec (aggregate)
- Per chunkserver: ~100 MB/sec (assuming 1000 chunkservers)

Network:
- 100 GB/sec = 800 Gbps aggregate bandwidth
- Per chunkserver: 800 Mbps (1 Gbps NIC)
```

### 3. API Design (RADIO: API)
```
Client API:
- create(filename) → handle
- delete(filename) → success
- open(filename, mode) → file_handle
- close(file_handle) → success
- read(file_handle, offset, length) → data
- write(file_handle, offset, data) → success
- append(file_handle, data) → offset
- snapshot(source_path, dest_path) → success

Master Internal API:
- getChunkHandle(filename, chunk_index) → handle
- getChunkLocations(chunk_handle) → [servers]
- getLease(chunk_handle) → primary_server
- reportChunkLocations(chunkserver_id, chunk_list) → ack

Chunkserver API:
- readChunk(chunk_handle, offset, length) → data
- writeChunk(chunk_handle, offset, data) → success
- createChunk(chunk_handle) → success
- deleteChunk(chunk_handle) → success
```

### 4. Data Model (RADIO: Data-Model)
```java
/**
 * Domain model for GFS
 */

// Master's in-memory metadata
@Entity
public class FileMetadata {
    private String filename;
    private List<ChunkHandle> chunks;      // Ordered list of chunks
    private long fileSize;
    private Timestamp createdAt;
    private Timestamp modifiedAt;
    private int referenceCount;            // For copy-on-write snapshots
}

@Entity
public class ChunkMetadata {
    private ChunkHandle handle;            // 64-bit unique ID
    private long version;                  // Incremented on each mutation
    private List<String> locations;        // Chunkserver IDs (not persisted)
    private String primaryLease;           // Primary replica (if lease active)
    private Timestamp leaseExpiry;
}

// Chunkserver's local state
public class ChunkStorage {
    private ChunkHandle handle;
    private String localFilePath;         // /gfs/chunks/chunk_12345
    private long version;                  // Must match master's version
    private List<Integer> checksums;       // 64KB block checksums
}

// Operation log entry (persisted)
@Entity
public class OperationLogEntry {
    private long sequenceNumber;
    private OperationType type;            // CREATE, DELETE, RENAME, etc.
    private Map<String, Object> params;
    private Timestamp timestamp;
}
```

### 5. High-Level Design (RADIO: Initial Design)
```
┌─────────────────────────────────────────────────────────────────┐
│                         GFS CLIENT                              │
│  ┌──────────────────┐                                          │
│  │  Application     │                                          │
│  └────────┬─────────┘                                          │
│           │                                                     │
│  ┌────────▼─────────┐                                          │
│  │  GFS Library     │  ← Caching, prefetching, buffering      │
│  └────────┬─────────┘                                          │
└───────────┼─────────────────────────────────────────────────────┘
            │
            ├─────────── Metadata Ops ─────────┐
            │                                  │
            │                                  ↓
            │                     ┌───────────────────────────┐
            │                     │      MASTER (Single)      │
            │                     │                           │
            │                     │ ┌─────────────────────┐   │
            │                     │ │  Namespace (RAM)    │   │
            │                     │ │  /home/user/file    │   │
            │                     │ └─────────────────────┘   │
            │                     │                           │
            │                     │ ┌─────────────────────┐   │
            │                     │ │ Chunk Metadata      │   │
            │                     │ │ handle → locations  │   │
            │                     │ └─────────────────────┘   │
            │                     │                           │
            │                     │ ┌─────────────────────┐   │
            │                     │ │  Operation Log      │   │
            │                     │ │  (Persistent)       │   │
            │                     │ └─────────────────────┘   │
            │                     └────────┬──────────────────┘
            │                              │
            ├──────── Data Flow ──────────┼────────────────┐
            │                              │                │
            │                              ↓                │
            │                     (Heartbeat + Commands)   │
            │                              │                │
            ↓                              ↓                ↓
┌──────────────────┐        ┌──────────────────┐  ┌──────────────────┐
│  CHUNKSERVER 1   │        │  CHUNKSERVER 2   │  │  CHUNKSERVER N   │
│                  │◄──────►│                  │◄►│                  │
│ ┌──────────────┐ │        │ ┌──────────────┐ │  │ ┌──────────────┐ │
│ │Chunk A (ver2)│ │        │ │Chunk A (ver2)│ │  │ │Chunk B (ver1)│ │
│ └──────────────┘ │        │ └──────────────┘ │  │ └──────────────┘ │
│ ┌──────────────┐ │        │ ┌──────────────┐ │  │ ┌──────────────┐ │
│ │Chunk B (ver1)│ │        │ │Chunk C (ver3)│ │  │ │Chunk A (ver2)│ │
│ └──────────────┘ │        │ └──────────────┘ │  │ └──────────────┘ │
│                  │        │                  │  │                  │
│  Linux FS        │        │  Linux FS        │  │  Linux FS        │
│  (local disk)    │        │  (local disk)    │  │  (local disk)    │
└──────────────────┘        └──────────────────┘  └──────────────────┘

        ↑                           ↑                      ↑
        └───────── Replication Pipeline ───────────────────┘
                   (3x replication)
```

### 6. Deep Dives (RADIO: Optimize)

**A. Write Path Optimization**
```java
/**
 * Pipeline data to replicas to minimize latency
 * Replica forwards to next while receiving
 */
public class PipelinedReplication {

    public void replicateData(byte[] data, List<String> replicas) {
        // Network topology aware ordering
        List<String> orderedReplicas = orderByNetworkDistance(replicas);

        // Send to first replica with forward chain
        String first = orderedReplicas.get(0);
        List<String> remaining = orderedReplicas.subList(1, orderedReplicas.size());

        PushDataRequest request = new PushDataRequest(
            data,
            remaining  // Forward chain
        );

        // First replica will:
        // 1. Write to local disk
        // 2. Simultaneously forward to next in chain
        // This pipelines disk I/O with network transfer

        sendAsync(first, request);
    }

    // Latency = max(disk_write_time, network_transfer_time)
    // Without pipelining: Latency = disk_write_time + network_transfer_time × num_replicas
}
```

**B. Master Scalability**
```python
class MasterScalability:
    """
    How master scales to petabytes
    """

    def __init__(self):
        # Keep metadata small
        self.BYTES_PER_FILE = 64
        self.BYTES_PER_CHUNK = 64

        # 1 million files, 3 chunks average, 3 replicas
        # = 64 MB for file metadata
        # = 192 MB for chunk metadata
        # = 256 MB total (fits in L3 cache!)

    def reduce_master_communication(self):
        """
        Client caches chunk locations
        """
        # Client asks master once
        chunk_info = master.get_chunk_locations(filename, chunk_index)

        # Cache for duration of lease (60 seconds)
        self.chunk_location_cache[filename] = {
            'locations': chunk_info,
            'expiry': time.time() + 60
        }

        # Future operations use cache
        # Master is not in critical path for data!

    def batch_operations(self):
        """
        Master batches heartbeat responses
        """
        commands_for_chunkserver = []

        # Accumulate commands during heartbeat interval
        commands_for_chunkserver.append(ReplicateCommand(...))
        commands_for_chunkserver.append(DeleteCommand(...))

        # Send all at once in heartbeat response
        return HeartbeatResponse(commands_for_chunkserver)
```

**C. Fault Tolerance: Failure Scenarios**
```
Scenario 1: Chunkserver crashes during write
- Some replicas have new data, some don't
- Client gets error, retries
- Primary lease expires, master grants new lease
- New primary sees inconsistent replicas
- GFS pads inconsistent replicas to align offsets
- Retry succeeds, creates "defined" region

Scenario 2: Network partition
- Master can't reach chunkserver (heartbeat timeout)
- Master assumes chunkserver dead
- Triggers re-replication of under-replicated chunks
- Chunkserver comes back online
- Master sees over-replication, garbage collects extras

Scenario 3: Master crashes
- Shadow master detects primary failure
- Human operator promotes shadow to primary
- Shadow replays operation log from checkpoint
- Polls all chunkservers for chunk inventory
- Resumes operation (< 1 minute downtime)
```

**D. Consistency Trade-offs**
```text
Why relaxed consistency?

Alternative 1: Strong consistency (Linearizable)
├─ Requires distributed consensus (Paxos/Raft) for every write
├─ High latency (multiple round trips)
└─ Complex implementation

Alternative 2: Eventual consistency
├─ Too weak (no guarantees)
└─ Application can't reason about state

GFS Choice: Defined inconsistency
├─ Primary defines serial order (simple)
├─ Replicas may temporarily diverge (high performance)
├─ Application can detect/handle undefined regions (checksums)
└─ Best of both worlds for append-heavy workloads
```

---

## MIND MAP: GFS CONCEPTS

```
                    Google File System
                          |
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                  ↓
   ARCHITECTURE        OPERATIONS        FAULT TOLERANCE
        |                 |                  |
   ┌────┴────┐      ┌────┴────┐        ┌────┴────┐
   ↓         ↓      ↓         ↓        ↓         ↓
Master  Chunkserver Write  Record   Replication GC
   |         |      Path   Append       |         |
   ↓         ↓       |       |          ↓         ↓
Metadata  64MB    Pipeline  Atomic   3-way    Lazy
 (RAM)    Chunks    Data   At-least  Replica  Delete
   |         |       |      once        |        |
   ↓         ↓       ↓       ↓          ↓        ↓
Operation  Linux  Primary  Offset   Version  Hidden
  Log      Files  Lease   Chosen    Number   Files
   |                |                 |
Checkpoint      Concurrent         Heartbeat
(B-tree)        Appends            Monitoring
```

## EMOTIONAL ANCHORS (For Subconscious Power)

Create strong memories with these analogies:

**1. GFS = Library System with Central Catalog**
- **Master = Central catalog** (knows which books exist, where they are)
- **Chunkservers = Library branches** (store actual books)
- **Chunks = Book volumes** (split encyclopedia into volumes)
- **Lease = Checkout card** (only one person can write in the book)

When you want to read:
1. Ask catalog where book is (master)
2. Go directly to branch and read (chunkserver)
3. Catalog not involved in reading! (master not in data path)

**2. Record Append = Append-only Guest Book**
- Multiple people write in same guest book
- No one erases or modifies existing entries
- Order determined by book keeper (primary)
- May write same message twice if pen skips (at-least-once)
- But never half-written messages (atomic)

**3. Relaxed Consistency = Wikipedia Edit History**
- Concurrent editors may create messy state temporarily
- Eventually consistent once editors coordinate
- You can see edit history (checksums detect undefined regions)
- Good enough for most use cases (not bank transactions!)

**4. 64MB Chunks = Shipping Containers**
- Don't ship individual items (small chunks)
- Pack into large containers (64MB)
- Easier to track (less metadata)
- Faster to load/unload (fewer operations)
- Some wasted space (internal fragmentation)
- But overall more efficient at scale

**5. Lazy GC = Recycle Bin / Trash Can**
- Delete doesn't immediately destroy
- Goes to trash first (hidden namespace)
- Garbage truck comes later (GC process)
- Can undelete before pickup (grace period)
- Simpler than coordinating immediate deletion

---

## FINAL INTERVIEW TIPS

**What to emphasize**:
1. **Design for failure**: "GFS assumes components fail constantly. At Google's scale, this is not paranoia—it's daily reality."

2. **Trade-offs are intentional**: "GFS relaxes consistency for higher throughput. This works for MapReduce because reduce phase deduplicates anyway."

3. **Single master is not a bottleneck**: "Master is only for metadata. Data flows directly from client to chunkserver. Master can handle 1000+ ops/sec easily."

4. **Large chunk size**: "64MB chunks mean 1 PB needs only 1 GB metadata. Master fits entirely in RAM for fast operations."

5. **Record append is the killer feature**: "Enables concurrent appends without distributed locking. This is why MapReduce is so simple to implement on GFS."

**Common mistakes to avoid**:
- Don't say "master is a bottleneck" (understand shadow masters + metadata caching)
- Don't over-complicate consistency (GFS intentionally relaxed, not broken)
- Don't suggest POSIX semantics (wrong workload!)
- Don't propose multi-master (complexity not worth it for GFS use case)

**Closing statement**:
"GFS pioneered the architecture now used by HDFS, Hadoop, and Colossus (GFS successor). Its key insight: for batch processing workloads, relaxed consistency + large chunks + simple architecture beats strong consistency + complex distributed transactions."
