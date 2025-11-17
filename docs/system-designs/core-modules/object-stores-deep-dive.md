# Object Stores Deep Dive

## Contents

- [Object Stores Deep Dive](#object-stores-deep-dive)
  - [Core Mental Model](#core-mental-model)
  - [Object Store Architecture](#1-object-store-architecture)
  - [Use Cases & When to Choose Object Storage](#2-use-cases--when-to-choose-object-storage)
  - [Storage Tiers & Lifecycle Management](#3-storage-tiers--lifecycle-management)
  - [Consistency Models & Versioning](#4-consistency-models--versioning)
  - [Performance Optimization Patterns](#5-performance-optimization-patterns)
  - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
    - [Requirements Clarification (RADIO: Requirements)](#1-requirements-clarification-radio-requirements)
    - [Capacity Estimation (RADIO: Scale)](#2-capacity-estimation-radio-scale)
    - [API Design (RADIO: API)](#3-api-design-radio-api)
    - [Data Model (RADIO: Data-model)](#4-data-model-radio-data-model)
    - [High-Level Design (RADIO: Initial Design)](#5-high-level-design-radio-initial-design)
    - [Deep Dives (RADIO: Optimize)](#6-deep-dives-radio-optimize)
  - [MIND MAP: OBJECT STORE CONCEPTS](#mind-map-object-store-concepts)

## Core Mental Model

```text
Storage Evolution:
Block Storage → File Storage → Object Storage

Block:   Raw volumes, app manages everything
File:    Hierarchical directories, POSIX semantics
Object:  Flat namespace, HTTP API, metadata-rich, infinitely scalable
```

**What is an Object?**
```
┌─────────────────────────────────────────────────┐
│              OBJECT ANATOMY                     │
├─────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────┐    │
│ │ Data (Blob)                             │    │
│ │ - Any size (0 bytes to 5 TB)            │    │
│ │ - Immutable (write-once, read-many)     │    │
│ │ - Binary or text                        │    │
│ └─────────────────────────────────────────┘    │
│                                                 │
│ ┌─────────────────────────────────────────┐    │
│ │ Metadata (Key-Value Pairs)              │    │
│ │ - System metadata (size, timestamp)     │    │
│ │ - User metadata (tags, custom fields)   │    │
│ │ - Content-Type, encoding, etc.          │    │
│ └─────────────────────────────────────────┘    │
│                                                 │
│ ┌─────────────────────────────────────────┐    │
│ │ Unique Identifier (Key)                 │    │
│ │ - Globally unique                       │    │
│ │ - Often hierarchical (s3://bucket/key)  │    │
│ │ - No actual directory structure         │    │
│ └─────────────────────────────────────────┘    │
└─────────────────────────────────────────────────┘
```

🎓 **PROFESSOR**: The key insight is **objects are immutable and self-describing**:

```text
File System:
├─ Mutable: Can modify byte ranges
├─ Metadata separate: inode, separate from data
└─ Hierarchical: /home/user/documents/file.txt

Object Store:
├─ Immutable: Replace entire object or version it
├─ Metadata bundled: Travels with object
└─ Flat + simulated hierarchy: bucket/user/documents/file.txt
```

**Why Immutability Matters:**

```python
# Traditional file system
with open('/data/metrics.log', 'a') as f:
    f.write('new_metric\n')  # Append in place

# Object store (immutable)
# Must read, modify, write entire object
old_data = s3.get_object('metrics.log')
new_data = old_data + 'new_metric\n'
s3.put_object('metrics.log', new_data)  # Replace entire object

# This seems inefficient, but enables:
# 1. Versioning (keep old versions automatically)
# 2. Caching (immutable = cacheable forever)
# 3. Replication (simpler consistency model)
# 4. Geo-distribution (eventual consistency works)
```

🏗️ **ARCHITECT**: Trade-offs in practice:

```java
/**
 * Object storage trade-offs
 */
public class ObjectStorageTradeoffs {

    /**
     * GOOD FOR:
     * - Static content (images, videos, logs, backups)
     * - Write-once, read-many workloads
     * - Massive scale (petabytes to exabytes)
     * - Geo-distributed access
     * - Cost-sensitive storage
     */
    public void goodUseCase() {
        // Example: Store user-uploaded photos
        byte[] photo = capturePhoto();
        String key = generateKey();  // "users/12345/photos/uuid.jpg"

        objectStore.put(key, photo, metadata()
            .contentType("image/jpeg")
            .cacheControl("max-age=31536000")  // Immutable, cache forever
            .userMetadata("uploaded_by", "user_12345")
            .build()
        );

        // Read: Direct HTTP GET, CDN cacheable
        String url = objectStore.getPresignedUrl(key);
        // https://cdn.example.com/users/12345/photos/uuid.jpg
    }

    /**
     * BAD FOR:
     * - Frequent modifications (databases, active logs)
     * - Small files with high IOPS (metadata overhead)
     * - POSIX semantics required (file locking, etc.)
     * - Low latency requirements (milliseconds)
     */
    public void badUseCase() {
        // Anti-pattern: Using object storage as a database
        for (int i = 0; i < 1000; i++) {
            // Each update replaces entire object
            // High latency (100ms+ per operation)
            // Expensive for small updates
            UserProfile profile = objectStore.get("users/" + userId);
            profile.incrementLoginCount();
            objectStore.put("users/" + userId, profile);  // ❌ WRONG
        }

        // Better: Use database for mutable data
        database.executeUpdate(
            "UPDATE users SET login_count = login_count + 1 WHERE id = ?",
            userId
        );
    }
}
```

**Key insight**: Object storage is **optimized for durability and scale, not performance**. It's append-only at the object level.

---

## 1. Object Store Architecture

🎓 **PROFESSOR**: Object stores are **distributed hash tables (DHT) with storage**:

```text
Traditional architecture:

         Client
           ↓
     Load Balancer
           ↓
      API Servers
           ↓
     Metadata DB ←→ Storage Nodes
                        ↓
                   Data Placement
                   (Consistent Hash)
```

### Architecture Components

```
┌──────────────────────────────────────────────────────┐
│                   CLIENT REQUEST                     │
│              PUT /bucket/key HTTP/1.1                │
└────────────────────┬─────────────────────────────────┘
                     ↓
          ┌─────────────────────┐
          │   API GATEWAY       │
          │  - Authentication   │ ← S3 API, Azure Blob API
          │  - Authorization    │ ← IAM, ACLs
          │  - Rate limiting    │
          └──────────┬──────────┘
                     ↓
          ┌─────────────────────┐
          │  METADATA SERVICE   │
          │  ┌───────────────┐  │
          │  │  Object Index │  │ ← key → {location, metadata}
          │  │  (Distributed)│  │ ← Often DynamoDB, Cassandra
          │  └───────────────┘  │
          │  ┌───────────────┐  │
          │  │  Bucket Info  │  │ ← bucket → {policy, lifecycle}
          │  └───────────────┘  │
          └──────────┬──────────┘
                     ↓
          ┌─────────────────────┐
          │  PLACEMENT SERVICE  │
          │  - Consistent hash  │ ← Decide which nodes store data
          │  - Replication      │ ← 3-6 copies across zones
          │  - Erasure coding   │ ← Or 1.5x overhead with EC
          └──────────┬──────────┘
                     ↓
     ┌───────────────┼───────────────┐
     ↓               ↓                ↓
┌─────────┐    ┌─────────┐     ┌─────────┐
│ Storage │    │ Storage │ ... │ Storage │
│  Node 1 │    │  Node 2 │     │  Node N │
│         │    │         │     │         │
│ ┌─────┐ │    │ ┌─────┐ │     │ ┌─────┐ │
│ │ Disk│ │    │ │ Disk│ │     │ │ Disk│ │
│ │Array│ │    │ │Array│ │     │ │Array│ │
│ └─────┘ │    │ └─────┘ │     │ └─────┘ │
└─────────┘    └─────────┘     └─────────┘
```

### Write Path (PUT Object)

```java
public class ObjectStorePutOperation {

    /**
     * Simplified S3-style PUT operation
     */
    public PutObjectResponse putObject(String bucket,
                                       String key,
                                       byte[] data,
                                       Map<String, String> metadata) {

        // Step 1: Authenticate & authorize
        User user = authenticateRequest();
        if (!authz.canWrite(user, bucket)) {
            throw new AccessDeniedException();
        }

        // Step 2: Validate request
        if (data.length > MAX_OBJECT_SIZE) {  // 5 TB for S3
            throw new EntityTooLargeException();
        }

        // Step 3: Generate object ID
        String objectId = UUID.randomUUID().toString();

        // Step 4: Calculate checksum (integrity)
        String etag = calculateMD5(data);
        String sha256 = calculateSHA256(data);

        // Step 5: Determine storage locations (consistent hashing)
        List<StorageNode> nodes = placementService.selectNodes(
            objectId,
            replicationFactor = 3,
            constraints = {
                'cross_zone': true,      // Different availability zones
                'cross_rack': true       // Different racks
            }
        );

        // Step 6: Write to storage nodes (parallel)
        List<CompletableFuture<WriteResult>> futures = nodes.stream()
            .map(node -> CompletableFuture.supplyAsync(() ->
                node.writeObject(objectId, data, etag)
            ))
            .collect(Collectors.toList());

        // Step 7: Wait for quorum (2 out of 3 for S3)
        int quorum = (nodes.size() / 2) + 1;
        List<WriteResult> results = waitForQuorum(futures, quorum, timeout = 30_000);

        if (results.size() < quorum) {
            // Rollback successful writes
            rollbackWrites(results);
            throw new InternalServerException("Failed to achieve write quorum");
        }

        // Step 8: Update metadata index
        ObjectMetadata objMetadata = new ObjectMetadata(
            bucket,
            key,
            objectId,
            data.length,
            etag,
            sha256,
            metadata,
            Instant.now(),
            nodes.stream().map(n -> n.getId()).collect(Collectors.toList())
        );

        metadataService.putMetadata(bucket + "/" + key, objMetadata);

        // Step 9: Asynchronously replicate to remaining nodes
        asyncReplicateToRemaining(objectId, data, nodes, results);

        // Step 10: Return response
        return new PutObjectResponse(
            etag,
            versionId = generateVersionId(),  // If versioning enabled
            serverSideEncryption = "AES256"
        );
    }
}
```

### Read Path (GET Object)

```python
class ObjectStoreGetOperation:
    """
    Optimized read path with caching and CDN
    """

    def get_object(self, bucket: str, key: str) -> bytes:

        # Step 1: Check cache (Redis/Memcached)
        cache_key = f"{bucket}/{key}"
        cached = self.cache.get(cache_key)

        if cached:
            # Cache hit, return immediately
            return cached['data']

        # Step 2: Lookup metadata
        metadata = self.metadata_service.get(cache_key)

        if not metadata:
            raise NoSuchKeyException(f"{bucket}/{key} not found")

        # Step 3: Select best storage node
        #   Prefer: same zone, healthy, low latency
        nodes = metadata.storage_locations
        best_node = self.select_best_node(
            nodes,
            strategy='closest',
            health_threshold=0.9
        )

        # Step 4: Read from storage node
        try:
            data = best_node.read_object(
                metadata.object_id,
                expected_etag=metadata.etag
            )

        except (ChecksumMismatchException, NodeUnavailableException):
            # Fallback: try secondary nodes
            for fallback_node in nodes[1:]:
                try:
                    data = fallback_node.read_object(
                        metadata.object_id,
                        expected_etag=metadata.etag
                    )
                    break
                except Exception:
                    continue
            else:
                raise InternalServerException("All replicas failed")

        # Step 5: Verify checksum
        if not self.verify_checksum(data, metadata.etag):
            raise ChecksumMismatchException("Data corruption detected")

        # Step 6: Update cache (if object is small enough)
        if len(data) < MAX_CACHE_SIZE:  # e.g., 1 MB
            self.cache.set(
                cache_key,
                {'data': data, 'metadata': metadata},
                ttl=3600  # 1 hour
            )

        # Step 7: Update access metrics (async)
        self.metrics.record_access(bucket, key)

        return data

    def select_best_node(self, nodes: List[StorageNode],
                         strategy: str,
                         health_threshold: float) -> StorageNode:
        """
        Select optimal storage node for read
        """
        # Filter healthy nodes
        healthy_nodes = [n for n in nodes if n.health_score > health_threshold]

        if not healthy_nodes:
            # All nodes unhealthy, pick least bad
            healthy_nodes = nodes

        if strategy == 'closest':
            # Select by network latency
            return min(healthy_nodes, key=lambda n: n.latency)

        elif strategy == 'round_robin':
            # Load balancing
            return healthy_nodes[hash(time.time()) % len(healthy_nodes)]

        elif strategy == 'least_loaded':
            # Select least busy node
            return min(healthy_nodes, key=lambda n: n.current_load)

        else:
            return healthy_nodes[0]
```

**Interview talking point**: "Object stores use quorum writes (2 out of 3) for low latency. The third replica catches up asynchronously. This is optimistic replication—prioritizes availability over consistency."

---

## 2. Use Cases & When to Choose Object Storage

🎓 **PROFESSOR**: Object storage shines in **specific scenarios**. Understanding when to use it is critical:

### Decision Matrix

```text
┌──────────────────────────────────────────────────────────────┐
│ USE CASE                  │ BEST CHOICE                      │
├───────────────────────────┼──────────────────────────────────┤
│ User uploads (photos/     │ Object Store ✓                   │
│ videos)                   │ - Immutable, large files         │
│                           │ - Metadata (user ID, tags)       │
│                           │ - CDN integration                │
├───────────────────────────┼──────────────────────────────────┤
│ Data lake / Analytics     │ Object Store ✓                   │
│                           │ - Massive scale (PB+)            │
│                           │ - Cheap storage                  │
│                           │ - Parquet/ORC files              │
├───────────────────────────┼──────────────────────────────────┤
│ Backups & archives        │ Object Store ✓                   │
│                           │ - Durability (11 nines)          │
│                           │ - Lifecycle policies             │
│                           │ - Versioning                     │
├───────────────────────────┼──────────────────────────────────┤
│ Static website hosting    │ Object Store ✓                   │
│                           │ - Serve HTML/CSS/JS/images       │
│                           │ - Cheap, scalable                │
│                           │ - Integrates with CDN            │
├───────────────────────────┼──────────────────────────────────┤
│ Database storage          │ Block/File Storage ✓             │
│                           │ - Need low latency (< 10ms)      │
│                           │ - Random writes                  │
│                           │ - Transactions                   │
├───────────────────────────┼──────────────────────────────────┤
│ Application code/binaries │ File System ✓                    │
│                           │ - Need POSIX semantics           │
│                           │ - Frequent small updates         │
│                           │ - Local file access              │
├───────────────────────────┼──────────────────────────────────┤
│ Log aggregation           │ Hybrid                           │
│                           │ - Stream to object store         │
│                           │ - Buffer locally first           │
│                           │ - Batch uploads                  │
└──────────────────────────────────────────────────────────────┘
```

### Use Case 1: Media Storage (Netflix, YouTube)

🏗️ **ARCHITECT**: Real-world implementation:

```java
/**
 * Media upload service using object storage
 */
@Service
public class MediaUploadService {

    @Autowired
    private S3Client s3Client;

    @Autowired
    private CloudFrontClient cdnClient;

    /**
     * Upload user video
     * Pattern: Multi-part upload for large files
     */
    public UploadResult uploadVideo(MultipartFile file, String userId) {

        String key = generateKey(userId, file.getOriginalFilename());
        // Format: videos/user_123/2024/11/unique_id.mp4

        // For files > 100 MB, use multi-part upload
        if (file.getSize() > 100 * 1024 * 1024) {
            return multiPartUpload(key, file, userId);
        }

        // Small files: single PUT
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        // User-defined metadata (queryable)
        metadata.addUserMetadata("uploaded_by", userId);
        metadata.addUserMetadata("original_filename", file.getOriginalFilename());
        metadata.addUserMetadata("upload_timestamp", Instant.now().toString());

        // Server-side encryption
        metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);

        PutObjectRequest request = PutObjectRequest.builder()
            .bucket("my-video-bucket")
            .key(key)
            .metadata(metadata)
            .tagging("environment=production&type=user_upload")  // For billing
            .storageClass(StorageClass.INTELLIGENT_TIERING)      // Auto cost optimization
            .build();

        s3Client.putObject(request, RequestBody.fromInputStream(
            file.getInputStream(),
            file.getSize()
        ));

        // Generate CDN URL (cached at edge)
        String cdnUrl = cdnClient.getDistributionUrl(key);

        return new UploadResult(key, cdnUrl);
    }

    /**
     * Multi-part upload for large files
     * Benefits:
     * - Resume on failure
     * - Parallel uploads (faster)
     * - Upload while processing
     */
    private UploadResult multiPartUpload(String key,
                                         MultipartFile file,
                                         String userId) {

        // Initiate multi-part upload
        CreateMultipartUploadResponse initResponse = s3Client.createMultipartUpload(
            CreateMultipartUploadRequest.builder()
                .bucket("my-video-bucket")
                .key(key)
                .build()
        );

        String uploadId = initResponse.uploadId();
        int partSize = 5 * 1024 * 1024;  // 5 MB parts
        List<CompletedPart> completedParts = new ArrayList<>();

        try (InputStream is = file.getInputStream()) {
            byte[] buffer = new byte[partSize];
            int partNumber = 1;
            int bytesRead;

            // Upload parts in parallel
            List<CompletableFuture<CompletedPart>> futures = new ArrayList<>();

            while ((bytesRead = is.read(buffer)) > 0) {
                byte[] partData = Arrays.copyOf(buffer, bytesRead);
                int currentPartNumber = partNumber++;

                // Upload each part asynchronously
                CompletableFuture<CompletedPart> future = CompletableFuture.supplyAsync(() -> {
                    UploadPartResponse response = s3Client.uploadPart(
                        UploadPartRequest.builder()
                            .bucket("my-video-bucket")
                            .key(key)
                            .uploadId(uploadId)
                            .partNumber(currentPartNumber)
                            .build(),
                        RequestBody.fromBytes(partData)
                    );

                    return CompletedPart.builder()
                        .partNumber(currentPartNumber)
                        .eTag(response.eTag())
                        .build();
                });

                futures.add(future);
            }

            // Wait for all parts to complete
            completedParts = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

            // Complete multi-part upload
            s3Client.completeMultipartUpload(
                CompleteMultipartUploadRequest.builder()
                    .bucket("my-video-bucket")
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder()
                        .parts(completedParts)
                        .build())
                    .build()
            );

            String cdnUrl = cdnClient.getDistributionUrl(key);
            return new UploadResult(key, cdnUrl);

        } catch (Exception e) {
            // Abort multi-part upload on failure
            s3Client.abortMultipartUpload(
                AbortMultipartUploadRequest.builder()
                    .bucket("my-video-bucket")
                    .key(key)
                    .uploadId(uploadId)
                    .build()
            );
            throw new UploadException("Multi-part upload failed", e);
        }
    }
}
```

### Use Case 2: Data Lake (Analytics)

```python
class DataLakeStorage:
    """
    Object storage for analytics workloads
    Pattern: Partitioned parquet files in S3
    """

    def __init__(self):
        self.s3_client = boto3.client('s3')
        self.bucket = 'analytics-data-lake'

    def write_analytics_data(self, df: pd.DataFrame, dataset: str):
        """
        Write partitioned data for efficient querying
        """
        # Partition by date (common pattern)
        # s3://bucket/dataset/year=2024/month=11/day=17/data.parquet

        today = datetime.now()
        partition_path = (
            f"{dataset}/"
            f"year={today.year}/"
            f"month={today.month:02d}/"
            f"day={today.day:02d}/"
        )

        # Convert to Parquet (columnar format)
        # 10x compression vs CSV, fast columnar scans
        parquet_buffer = io.BytesIO()
        df.to_parquet(
            parquet_buffer,
            engine='pyarrow',
            compression='snappy',  # Fast compression
            index=False
        )

        # Generate unique filename
        filename = f"{uuid.uuid4()}.parquet"
        key = partition_path + filename

        # Upload to S3
        self.s3_client.put_object(
            Bucket=self.bucket,
            Key=key,
            Body=parquet_buffer.getvalue(),
            ContentType='application/octet-stream',
            Metadata={
                'records': str(len(df)),
                'schema_version': '1.0',
                'source': 'kafka_stream'
            },
            # Use S3 Standard-IA for infrequent access
            StorageClass='STANDARD_IA'
        )

        # Update metadata catalog (AWS Glue, Hive Metastore)
        self.update_catalog(dataset, partition_path, key)

    def query_with_pushdown(self, dataset: str, filters: dict):
        """
        Query data lake using S3 Select (pushdown filtering)
        Avoid downloading entire files
        """
        # S3 Select: Run SQL on S3 directly
        # Reduces data transfer by 80-90%

        partition_prefix = self.build_partition_prefix(dataset, filters)

        # List all parquet files in partition
        response = self.s3_client.list_objects_v2(
            Bucket=self.bucket,
            Prefix=partition_prefix
        )

        results = []

        for obj in response.get('Contents', []):
            # Use S3 Select API
            select_response = self.s3_client.select_object_content(
                Bucket=self.bucket,
                Key=obj['Key'],
                ExpressionType='SQL',
                Expression="""
                    SELECT * FROM s3object
                    WHERE user_id = '12345'
                    AND event_type = 'purchase'
                """,
                InputSerialization={
                    'Parquet': {}
                },
                OutputSerialization={
                    'JSON': {}
                }
            )

            # Stream results
            for event in select_response['Payload']:
                if 'Records' in event:
                    results.append(event['Records']['Payload'].decode())

        return results
```

**Interview gold**: "Data lakes use object storage because it decouples storage from compute. You can spin up 1000 Spark workers to process petabytes, then shut them down. Storage cost is $0.023/GB/month (S3 Standard). Can't do that with HDFS!"

---

## 3. Storage Tiers & Lifecycle Management

🎓 **PROFESSOR**: Object stores offer **multiple storage tiers** based on access patterns:

```text
Storage Tier Spectrum:

Hot (Frequent Access)         Warm              Cold (Archival)
        ↓                       ↓                      ↓
    S3 Standard          S3 Standard-IA        S3 Glacier
    $0.023/GB            $0.0125/GB           $0.004/GB
    ms latency           ms latency           hours/minutes
    No retrieval cost    $0.01/GB retrieval   $0.02-0.10/GB retrieval
```

### Storage Classes Comparison

```
┌─────────────────────────────────────────────────────────────────────┐
│ Storage Class     │ Use Case              │ Cost    │ Retrieval    │
├───────────────────┼───────────────────────┼─────────┼──────────────┤
│ Standard          │ Frequently accessed   │ $$$     │ Instant      │
│                   │ (> 1x/month)          │         │ Free         │
├───────────────────┼───────────────────────┼─────────┼──────────────┤
│ Intelligent-      │ Unknown/changing      │ $$+     │ Instant      │
│ Tiering           │ access pattern        │ auto    │ Free         │
│                   │ (auto optimization)   │         │              │
├───────────────────┼───────────────────────┼─────────┼──────────────┤
│ Standard-IA       │ Infrequent access     │ $$      │ Instant      │
│ (Infrequent)      │ (< 1x/month)          │         │ $            │
├───────────────────┼───────────────────────┼─────────┼──────────────┤
│ One Zone-IA       │ Recreatable data      │ $       │ Instant      │
│                   │ (lower durability)    │         │ $            │
├───────────────────┼───────────────────────┼─────────┼──────────────┤
│ Glacier Instant   │ Archive, instant      │ $       │ Instant      │
│ Retrieval         │ retrieval needed      │         │ $$           │
├───────────────────┼───────────────────────┼─────────┼──────────────┤
│ Glacier Flexible  │ Archive, 1-5 min      │ ¢       │ 1-5 min      │
│                   │ retrieval OK          │         │ $$           │
├───────────────────┼───────────────────────┼─────────┼──────────────┤
│ Glacier Deep      │ Long-term archive     │ ¢¢      │ 12 hours     │
│ Archive           │ (7-10 year retention) │         │ $$           │
└─────────────────────────────────────────────────────────────────────┘
```

### Lifecycle Policies

🏗️ **ARCHITECT**: Automate cost optimization with lifecycle rules:

```java
/**
 * S3 Lifecycle configuration
 * Automatically transition objects between tiers
 */
public class LifecycleConfiguration {

    public void configureLifecycle(String bucketName) {

        // Rule 1: Transition old logs to cheaper storage
        LifecycleRule logsRule = LifecycleRule.builder()
            .id("logs-lifecycle")
            .status(LifecycleRuleStatus.ENABLED)
            .filter(LifecycleRuleFilter.builder()
                .prefix("logs/")  // Only logs/ objects
                .build())
            .transitions(
                // After 30 days: move to Infrequent Access
                Transition.builder()
                    .days(30)
                    .storageClass(StorageClass.STANDARD_IA)
                    .build(),

                // After 90 days: move to Glacier
                Transition.builder()
                    .days(90)
                    .storageClass(StorageClass.GLACIER)
                    .build(),

                // After 365 days: move to Deep Archive
                Transition.builder()
                    .days(365)
                    .storageClass(StorageClass.DEEP_ARCHIVE)
                    .build()
            )
            // Delete after 7 years (compliance requirement)
            .expiration(LifecycleExpiration.builder()
                .days(2555)  // 7 years
                .build())
            .build();

        // Rule 2: Clean up incomplete multi-part uploads
        LifecycleRule cleanupRule = LifecycleRule.builder()
            .id("cleanup-incomplete-uploads")
            .status(LifecycleRuleStatus.ENABLED)
            .abortIncompleteMultipartUpload(
                AbortIncompleteMultipartUpload.builder()
                    .daysAfterInitiation(7)  // Delete after 7 days
                    .build()
            )
            .build();

        // Rule 3: Delete old versions (if versioning enabled)
        LifecycleRule versioningRule = LifecycleRule.builder()
            .id("expire-old-versions")
            .status(LifecycleRuleStatus.ENABLED)
            .noncurrentVersionTransitions(
                NoncurrentVersionTransition.builder()
                    .noncurrentDays(30)
                    .storageClass(StorageClass.GLACIER)
                    .build()
            )
            .noncurrentVersionExpiration(
                NoncurrentVersionExpiration.builder()
                    .noncurrentDays(90)  // Delete after 90 days
                    .build()
            )
            .build();

        // Apply lifecycle configuration
        BucketLifecycleConfiguration config = BucketLifecycleConfiguration.builder()
            .rules(logsRule, cleanupRule, versioningRule)
            .build();

        s3Client.putBucketLifecycleConfiguration(
            PutBucketLifecycleConfigurationRequest.builder()
                .bucket(bucketName)
                .lifecycleConfiguration(config)
                .build()
        );
    }
}
```

**Cost Optimization Example:**

```python
class CostOptimizationAnalysis:
    """
    Calculate savings from lifecycle policies
    """

    def calculate_savings(self):
        # Scenario: 100 TB of logs per month
        monthly_data = 100 * 1024  # GB

        # Without lifecycle management:
        # All data in S3 Standard
        cost_without_lifecycle = monthly_data * 0.023 * 12  # 1 year
        # = 100 TB * $0.023/GB/month * 12 months = $28,416/year

        # With lifecycle management:
        # 30 days Standard, 60 days Standard-IA, 275 days Glacier
        cost_with_lifecycle = (
            monthly_data * 0.023 * 1 +      # Month 1: Standard
            monthly_data * 0.0125 * 2 +     # Months 2-3: Standard-IA
            monthly_data * 0.004 * 9        # Months 4-12: Glacier
        )
        # = $2,300 + $2,500 + $3,600 = $8,400/year

        savings = cost_without_lifecycle - cost_with_lifecycle
        savings_pct = (savings / cost_without_lifecycle) * 100

        print(f"Annual savings: ${savings:,.0f} ({savings_pct:.1f}%)")
        # Output: Annual savings: $20,016 (70.4%)
```

**Interview talking point**: "Lifecycle policies are critical for cost optimization. We saved 70% by automatically moving old logs to Glacier. The key is understanding access patterns—most logs are never accessed after 30 days."

---

## 4. Consistency Models & Versioning

🎓 **PROFESSOR**: Object stores evolved from **eventual consistency** to **strong consistency**:

```text
Historical Evolution:

S3 (2006-2020):
├─ Eventual consistency for PUT/DELETE
├─ Read-after-write for new objects only
└─ Could read stale data for updates

S3 (2020+):
├─ Strong consistency for all operations
├─ Read-after-write guaranteed
└─ List operations immediately consistent
```

### Strong Consistency Implementation

```java
/**
 * How object stores achieve strong consistency
 */
public class ConsistencyModel {

    /**
     * Write path with strong consistency
     */
    public void putObjectWithStrongConsistency(String key, byte[] data) {

        // Step 1: Write to distributed log (commit log pattern)
        //   Similar to database WAL (write-ahead log)
        long sequenceNumber = distributedLog.append(new LogEntry(
            Operation.PUT,
            key,
            data,
            timestamp = Instant.now()
        ));

        // Step 2: Wait for quorum acknowledgment
        //   Ensures majority of nodes have the log entry
        boolean quorumAchieved = distributedLog.waitForQuorum(
            sequenceNumber,
            quorum = (replicationFactor / 2) + 1,
            timeout = Duration.ofSeconds(5)
        );

        if (!quorumAchieved) {
            throw new WriteTimeoutException("Quorum not achieved");
        }

        // Step 3: Apply to storage asynchronously
        //   Log entry is durable, can apply lazily
        storageEngine.applyAsync(sequenceNumber);

        // Step 4: Update metadata with version
        metadata.put(key, new ObjectMetadata(
            sequenceNumber,  // Version = log sequence number
            data.length,
            calculateETag(data),
            storageLocations
        ));
    }

    /**
     * Read path with strong consistency
     */
    public byte[] getObjectWithStrongConsistency(String key) {

        // Step 1: Read metadata to get version
        ObjectMetadata metadata = metadataService.get(key);

        if (metadata == null) {
            throw new NoSuchKeyException(key);
        }

        // Step 2: Read from storage with version check
        //   If storage node has older version, read from log
        byte[] data = storageEngine.read(
            key,
            minVersion = metadata.version
        );

        // Step 3: If storage doesn't have latest version yet,
        //   read from distributed log (slower but consistent)
        if (data == null) {
            LogEntry entry = distributedLog.read(metadata.version);
            data = entry.getData();
        }

        return data;
    }
}
```

### Versioning

🏗️ **ARCHITECT**: Versioning enables **immutable updates** and **time travel**:

```python
class ObjectVersioning:
    """
    S3 versioning: Keep all versions of objects
    """

    def __init__(self):
        self.s3_client = boto3.client('s3')

    def enable_versioning(self, bucket: str):
        """
        Enable versioning on bucket
        """
        self.s3_client.put_bucket_versioning(
            Bucket=bucket,
            VersioningConfiguration={
                'Status': 'Enabled',
                'MFADelete': 'Disabled'  # Can enable for compliance
            }
        )

    def version_lifecycle(self, bucket: str, key: str):
        """
        Demonstrate versioning behavior
        """
        # Upload version 1
        response1 = self.s3_client.put_object(
            Bucket=bucket,
            Key=key,
            Body=b'Version 1 content'
        )
        version_id_1 = response1['VersionId']

        # Upload version 2 (same key)
        response2 = self.s3_client.put_object(
            Bucket=bucket,
            Key=key,
            Body=b'Version 2 content - updated'
        )
        version_id_2 = response2['VersionId']

        # Get latest version (default)
        latest = self.s3_client.get_object(Bucket=bucket, Key=key)
        assert latest['Body'].read() == b'Version 2 content - updated'

        # Get specific version
        v1 = self.s3_client.get_object(
            Bucket=bucket,
            Key=key,
            VersionId=version_id_1
        )
        assert v1['Body'].read() == b'Version 1 content'

        # "Delete" object (creates delete marker)
        self.s3_client.delete_object(Bucket=bucket, Key=key)

        # Object appears deleted
        try:
            self.s3_client.get_object(Bucket=bucket, Key=key)
            assert False, "Should have raised NoSuchKey"
        except ClientError as e:
            assert e.response['Error']['Code'] == 'NoSuchKey'

        # But versions still exist!
        versions = self.s3_client.list_object_versions(
            Bucket=bucket,
            Prefix=key
        )

        # Can still retrieve old versions
        restored = self.s3_client.get_object(
            Bucket=bucket,
            Key=key,
            VersionId=version_id_2
        )
        assert restored['Body'].read() == b'Version 2 content - updated'

    def implement_time_travel(self, bucket: str, key: str, timestamp: datetime):
        """
        Retrieve object as it existed at specific time
        """
        # List all versions
        versions = self.s3_client.list_object_versions(
            Bucket=bucket,
            Prefix=key
        )

        # Find version closest to timestamp
        target_version = None

        for version in versions.get('Versions', []):
            if version['LastModified'] <= timestamp:
                target_version = version['VersionId']
                break

        if target_version:
            return self.s3_client.get_object(
                Bucket=bucket,
                Key=key,
                VersionId=target_version
            )
        else:
            raise ValueError(f"No version found before {timestamp}")
```

**Use Cases for Versioning:**
1. **Accidental deletion protection**: Restore deleted files
2. **Audit trail**: See all changes over time
3. **Rollback**: Revert to previous version
4. **Compliance**: Retain all versions for legal requirements

**Interview gold**: "Versioning is how object stores handle mutability. Each PUT creates a new version. Deletes are logical (delete marker). This aligns with immutability principle while supporting update use cases."

---

## 5. Performance Optimization Patterns

🎓 **PROFESSOR**: Object stores are **not low-latency**. Optimization requires understanding limitations:

```text
Latency Breakdown (typical S3 request):

DNS lookup:        5-10ms
TLS handshake:     20-50ms
HTTP request:      50-100ms (regional)
Data transfer:     Variable (depends on size)
─────────────────────────────
Total first byte:  75-160ms (best case)

Compare to:
Local SSD:         0.1ms
Redis cache:       1ms
Database:          5-10ms
```

### Pattern 1: Request Parallelization

```java
/**
 * Parallelize S3 requests for higher throughput
 */
public class ParallelS3Operations {

    private final S3Client s3Client;
    private final ExecutorService executor = Executors.newFixedThreadPool(50);

    /**
     * Download large file using byte-range requests
     * Parallelizes download for 10x speedup
     */
    public byte[] downloadLargeFileParallel(String bucket, String key) {

        // Get object size
        HeadObjectResponse metadata = s3Client.headObject(
            HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()
        );

        long fileSize = metadata.contentLength();
        int chunkSize = 10 * 1024 * 1024;  // 10 MB chunks
        int numChunks = (int) Math.ceil((double) fileSize / chunkSize);

        // Allocate result buffer
        byte[] result = new byte[(int) fileSize];

        // Download chunks in parallel
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < numChunks; i++) {
            long start = (long) i * chunkSize;
            long end = Math.min(start + chunkSize - 1, fileSize - 1);
            int chunkIndex = i;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                // Byte-range request
                ResponseBytes<GetObjectResponse> chunk = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .range(String.format("bytes=%d-%d", start, end))
                        .build()
                );

                // Copy to result buffer
                byte[] chunkData = chunk.asByteArray();
                System.arraycopy(chunkData, 0, result, (int) start, chunkData.length);

            }, executor);

            futures.add(future);
        }

        // Wait for all chunks
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return result;

        // Performance:
        // Serial download (1 Gbps):      10 GB file = 80 seconds
        // Parallel download (50 chunks): 10 GB file = ~8 seconds (10x faster!)
    }

    /**
     * Batch process many small files in parallel
     */
    public List<ProcessedData> processSmallFilesParallel(List<String> keys) {

        // Problem: Processing 1000 small files serially = 1000 * 100ms = 100 seconds
        // Solution: Process in parallel = 100ms (limited by slowest request)

        List<CompletableFuture<ProcessedData>> futures = keys.stream()
            .map(key -> CompletableFuture.supplyAsync(() -> {
                // Download
                byte[] data = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                        .bucket("my-bucket")
                        .key(key)
                        .build()
                ).asByteArray();

                // Process
                return processData(data);

            }, executor))
            .collect(Collectors.toList());

        // Wait for all to complete
        return futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
    }
}
```

### Pattern 2: Caching & CDN Integration

```python
class S3CachingStrategy:
    """
    Multi-layer caching for object storage
    """

    def __init__(self):
        self.s3_client = boto3.client('s3')
        self.redis_client = redis.Redis()
        self.cloudfront_client = boto3.client('cloudfront')

    def get_with_caching(self, bucket: str, key: str) -> bytes:
        """
        Cache hierarchy:
        1. Application cache (Redis) - 1ms
        2. CDN (CloudFront) - 20ms
        3. S3 (origin) - 100ms
        """

        # Layer 1: Check Redis (hot data)
        cache_key = f"s3:{bucket}:{key}"
        cached = self.redis_client.get(cache_key)

        if cached:
            return cached

        # Layer 2: Check CloudFront (warm data)
        cdn_url = f"https://d123.cloudfront.net/{key}"

        try:
            response = requests.get(cdn_url, timeout=5)
            if response.status_code == 200:
                data = response.content

                # Populate Redis cache
                self.redis_client.setex(
                    cache_key,
                    3600,  # 1 hour TTL
                    data
                )

                return data
        except requests.RequestException:
            pass  # Fall through to S3

        # Layer 3: Fetch from S3 (cold data)
        s3_response = self.s3_client.get_object(
            Bucket=bucket,
            Key=key
        )

        data = s3_response['Body'].read()

        # Populate caches
        self.redis_client.setex(cache_key, 3600, data)

        return data

    def configure_cdn_caching(self, distribution_id: str):
        """
        Configure CloudFront caching behavior
        """
        config = self.cloudfront_client.get_distribution_config(
            Id=distribution_id
        )

        # Update cache behavior
        cache_behavior = {
            'TargetOriginId': 's3-origin',
            'ViewerProtocolPolicy': 'redirect-to-https',
            'AllowedMethods': {
                'Quantity': 2,
                'Items': ['GET', 'HEAD'],
                'CachedMethods': {
                    'Quantity': 2,
                    'Items': ['GET', 'HEAD']
                }
            },
            'Compress': True,  # Enable Gzip compression
            'MinTTL': 0,
            'DefaultTTL': 86400,    # 24 hours
            'MaxTTL': 31536000,     # 1 year
            'ForwardedValues': {
                'QueryString': False,
                'Cookies': {'Forward': 'none'},
                'Headers': {
                    'Quantity': 1,
                    'Items': ['Origin']  # For CORS
                }
            }
        }

        # Update distribution
        # (simplified - actual update is more complex)
        self.cloudfront_client.update_distribution(
            Id=distribution_id,
            DistributionConfig=config['DistributionConfig']
        )
```

### Pattern 3: Presigned URLs (Offload Traffic)

```java
/**
 * Generate presigned URLs for direct client access
 * Avoids routing through application servers
 */
public class PresignedUrlPattern {

    private final S3Presigner presigner;

    /**
     * Generate upload presigned URL
     * Client uploads directly to S3 (bypasses server)
     */
    public String generateUploadUrl(String bucket, String key, Duration expiration) {

        PutObjectRequest objectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType("image/jpeg")
            .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(expiration)  // Usually 15 minutes
            .putObjectRequest(objectRequest)
            .build();

        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);

        return presignedRequest.url().toString();
        // https://bucket.s3.amazonaws.com/key?X-Amz-Algorithm=...&X-Amz-Credential=...
    }

    /**
     * Generate download presigned URL
     * Client downloads directly from S3
     */
    public String generateDownloadUrl(String bucket, String key, Duration expiration) {

        GetObjectRequest getRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(expiration)
            .getObjectRequest(getRequest)
            .build();

        PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);

        return presignedRequest.url().toString();
    }

    /**
     * Full workflow: Client-side upload
     */
    public void demonstrateClientSideUpload() {

        // Step 1: Client requests upload URL from server
        String uploadUrl = generateUploadUrl(
            "my-bucket",
            "uploads/user_123/photo.jpg",
            Duration.ofMinutes(15)
        );

        // Step 2: Server returns URL to client (no file transfer to server!)
        // Client uploads directly to S3 using presigned URL

        // Step 3: Client notifies server when upload complete
        // Server can now reference the object

        // Benefits:
        // ✓ Reduced server load (no file proxying)
        // ✓ Faster uploads (direct to S3, parallel)
        // ✓ Lower server bandwidth costs
        // ✓ Better scalability
    }
}
```

**Interview talking point**: "Presigned URLs are critical for scale. Instead of uploading to our servers then to S3, clients upload directly. This saved us 90% of server bandwidth and eliminated upload-related server scaling issues."

---

## SYSTEM DESIGN INTERVIEW FRAMEWORK

When asked to *"Design an object storage system"*, use this structure:

### 1. Requirements Clarification (RADIO: Requirements)
```
Functional:
- Store and retrieve objects (blobs)
- Support large objects (GB to TB)
- Metadata storage (key-value tags)
- Versioning support
- Access control (public/private)

Non-Functional:
- Durability: 99.999999999% (11 nines)
- Availability: 99.9% - 99.99%
- Scalability: Exabytes of storage
- Latency: 100ms P99 for GET
- Throughput: 10GB/sec per client

Assumptions:
- Immutable objects (updates are rare)
- Distributed across multiple datacenters
- HTTP/HTTPS API
```

### 2. Capacity Estimation (RADIO: Scale)
```
Storage:
- Total capacity: 1 EB (exabyte) = 1000 PB
- Average object size: 10 MB
- Number of objects: 1 EB / 10 MB = 100 trillion objects
- Metadata: 100T objects × 1 KB = 100 PB metadata

Throughput:
- Read requests: 10M requests/sec
- Write requests: 1M requests/sec
- Average read size: 10 MB
- Read bandwidth: 10M × 10 MB = 100 TB/sec
- Write bandwidth: 1M × 10 MB = 10 TB/sec

Storage nodes:
- Per node capacity: 100 TB
- Number of nodes: 1 EB / 100 TB = 10,000 nodes
- With 3x replication: 30,000 nodes
```

### 3. API Design (RADIO: API)
```
Object Operations:
- PUT /:bucket/:key          → Upload object
- GET /:bucket/:key          → Download object
- DELETE /:bucket/:key       → Delete object
- HEAD /:bucket/:key         → Get metadata only
- COPY /:src → /:dest        → Copy object

Bucket Operations:
- PUT /:bucket               → Create bucket
- DELETE /:bucket            → Delete bucket (must be empty)
- GET /:bucket               → List objects (paginated)

Multipart Upload:
- POST /:bucket/:key?uploads                → Initiate
- PUT /:bucket/:key?partNumber=N&uploadId=X → Upload part
- POST /:bucket/:key?uploadId=X             → Complete
- DELETE /:bucket/:key?uploadId=X           → Abort

Versioning:
- GET /:bucket/:key?versionId=X → Get specific version
- GET /:bucket/:key?versions    → List all versions
- DELETE /:bucket/:key?versionId=X → Delete specific version

Access Control:
- PUT /:bucket?acl           → Set access control list
- GET /:bucket/:key?presign  → Generate presigned URL
```

### 4. Data Model (RADIO: Data-Model)
```java
/**
 * Domain model for object storage
 */

@Entity
public class Bucket {
    @Id
    private String name;               // Globally unique
    private String ownerId;
    private Region region;
    private boolean versioningEnabled;
    private LifecyclePolicy lifecycle;
    private AccessControlList acl;
    private Instant createdAt;
}

@Entity
public class S3Object {
    @Id
    private ObjectId id;               // Internal unique ID

    private String bucket;
    private String key;                // User-visible key
    private String versionId;          // For versioning

    private long size;
    private String contentType;
    private String etag;               // MD5 hash
    private String storageClass;

    // User-defined metadata
    private Map<String, String> metadata;

    // System metadata
    private Instant lastModified;
    private String ownerId;

    // Storage locations (not exposed to users)
    @Transient
    private List<String> storageNodeIds;
}

@Entity
public class ObjectMetadata {
    private String bucket;
    private String key;
    private List<ObjectVersion> versions;  // All versions
    private boolean deleteMarker;          // Logical delete
}

@Entity
public class ObjectVersion {
    private String versionId;
    private long size;
    private String etag;
    private Instant timestamp;
    private boolean isLatest;
    private List<String> storageLocations;
}

// Storage node record
@Entity
public class ChunkLocation {
    private ObjectId objectId;
    private String nodeId;
    private String localPath;           // /data/chunks/abc123
    private long checksum;              // For integrity verification
    private ReplicationStatus status;   // PENDING, COMPLETE, FAILED
}
```

### 5. High-Level Design (RADIO: Initial Design)
```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT                                  │
│  (Browser, SDK, CLI)                                           │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ↓ HTTPS
          ┌─────────────────────┐
          │   CDN / Edge Cache  │ ← Optional (for public objects)
          └──────────┬──────────┘
                     ↓
          ┌─────────────────────┐
          │  API Gateway / LB   │
          │  - TLS termination  │
          │  - Rate limiting    │
          │  - Authentication   │
          └──────────┬──────────┘
                     ↓
          ┌─────────────────────────────────────┐
          │    API SERVERS (Stateless)          │
          │  - Parse S3 API requests            │
          │  - Generate presigned URLs          │
          │  - Enforce IAM policies             │
          └──────────┬──────────────────────────┘
                     │
       ┌─────────────┼─────────────┐
       ↓             ↓              ↓
┌─────────────┐ ┌─────────────┐ ┌──────────────┐
│  Metadata   │ │  Placement  │ │   Storage    │
│   Service   │ │   Service   │ │   Cluster    │
│             │ │             │ │              │
│ ┌─────────┐ │ │ Consistent  │ │ ┌──────────┐ │
│ │DynamoDB │ │ │   Hashing   │ │ │  Node 1  │ │
│ │ or      │ │ │             │ │ │  ┌────┐  │ │
│ │Cassandra│ │ │ Replication │ │ │  │Disk│  │ │
│ └─────────┘ │ │   Policy    │ │ │  └────┘  │ │
│             │ │             │ │ └──────────┘ │
│  Indexes:   │ │ Erasure     │ │              │
│  - bucket/  │ │  Coding     │ │ ┌──────────┐ │
│    key      │ │             │ │ │  Node 2  │ │
│  - version  │ │             │ │ │  (replica)│ │
│  - tags     │ │             │ │ └──────────┘ │
└─────────────┘ └─────────────┘ │              │
                                │ ┌──────────┐ │
                                │ │  Node N  │ │
                                │ └──────────┘ │
                                └──────────────┘

              ┌─────────────────────────────────┐
              │   BACKGROUND SERVICES           │
              │  - Garbage collection           │
              │  - Lifecycle transitions        │
              │  - Replication monitoring       │
              │  - Integrity checking           │
              └─────────────────────────────────┘
```

### 6. Deep Dives (RADIO: Optimize)

**A. Durability: How to Achieve 11 Nines**

```python
class DurabilityCalculation:
    """
    S3 durability: 99.999999999% (11 nines)
    Probability of losing an object: 0.00000000001% per year
    """

    def calculate_durability(self):

        # Strategy 1: Replication (3 copies)
        # If each disk has 1% annual failure rate
        disk_failure_rate = 0.01

        # Probability all 3 disks fail
        all_fail = disk_failure_rate ** 3
        # = 0.000001 (99.9999% durability)

        # Not good enough! Need better strategy

        # Strategy 2: Erasure Coding (Reed-Solomon)
        # Example: 12 data chunks + 4 parity chunks = 16 total
        # Can lose any 4 chunks and still reconstruct

        # Probability of losing > 4 out of 16 chunks
        from scipy.stats import binom

        n = 16  # Total chunks
        k = 4   # Can tolerate this many failures
        p = 0.01  # Per-chunk failure rate

        # Probability of losing more than k chunks
        prob_data_loss = 1 - binom.cdf(k, n, p)
        # = 0.0000000001 (99.99999999% durability)

        # Strategy 3: Cross-region replication
        # Replicate to 3 regions
        # Probability all 3 regions fail catastrophically
        region_failure = 0.0001  # 0.01% per year

        cross_region_loss = region_failure ** 3
        # = 0.000000000001 (12 nines!)

        # Combined strategy:
        # Erasure coding within region +
        # Cross-region replication
        # = 11-12 nines durability

    def implement_erasure_coding(self, data: bytes) -> List[bytes]:
        """
        Split data into chunks with parity
        """
        from pyeclib.ec_iface import ECDriver

        # 12 data chunks, 4 parity chunks
        ec_driver = ECDriver(
            k=12,  # Data chunks
            m=4,   # Parity chunks
            ec_type='liberasurecode_rs_vand'  # Reed-Solomon
        )

        # Encode: split into 16 chunks
        chunks = ec_driver.encode(data)

        # Can reconstruct from ANY 12 chunks
        # Storage overhead: 16/12 = 1.33x (vs 3x for replication)

        return chunks

    def reconstruct_from_chunks(self, chunks: List[bytes]) -> bytes:
        """
        Reconstruct data from partial chunks
        """
        ec_driver = ECDriver(k=12, m=4, ec_type='liberasurecode_rs_vand')

        # Need only 12 out of 16 chunks
        available_chunks = [c for c in chunks if c is not None]

        if len(available_chunks) >= 12:
            data = ec_driver.decode(available_chunks)
            return data
        else:
            raise InsufficientChunksError("Need at least 12 chunks")
```

**B. Metadata Scalability**

```java
/**
 * Metadata service must handle 100T+ objects
 * Cannot fit in single database
 */
public class MetadataSharding {

    /**
     * Shard metadata by bucket + key
     */
    public int getShardId(String bucket, String key) {
        // Consistent hashing on bucket + key
        String shardKey = bucket + "/" + key;
        int hash = MurmurHash3.hash32(shardKey);

        // 1024 shards
        return Math.abs(hash) % 1024;
    }

    /**
     * Write metadata to appropriate shard
     */
    public void putMetadata(String bucket, String key, ObjectMetadata metadata) {

        int shardId = getShardId(bucket, key);

        // Get database connection for this shard
        Database db = getShardDatabase(shardId);

        // Write to shard (DynamoDB table or Cassandra partition)
        db.put(bucket + "/" + key, metadata);

        // Also maintain secondary indexes
        // For listing by prefix, tags, etc.
        updateIndexes(bucket, key, metadata);
    }

    /**
     * List objects in bucket (tricky with sharding!)
     */
    public List<ObjectMetadata> listObjects(String bucket, String prefix) {

        // Problem: Objects are scattered across 1024 shards
        // Can't query one shard

        // Solution 1: Query all shards (scatter-gather)
        //   Pros: Simple
        //   Cons: Slow for large buckets

        // Solution 2: Maintain separate index for listing
        //   Pros: Fast listing
        //   Cons: Additional storage + consistency overhead

        // S3 uses Solution 2: Secondary index optimized for listing
        return listingIndexService.query(bucket, prefix);
    }
}
```

**C. Request Rate Optimization**

```text
S3 Performance Limits (historical):

Before 2018:
├─ 100 requests/sec per prefix
└─ Caused throttling errors at scale

After 2018:
├─ 3,500 PUT/POST/DELETE per prefix per second
├─ 5,500 GET/HEAD per prefix per second
└─ Virtually unlimited total requests

How? Dynamic request routing + partition splitting
```

---

## MIND MAP: OBJECT STORE CONCEPTS

```
                   Object Storage
                         |
        ┌────────────────┼────────────────┐
        ↓                ↓                 ↓
   ARCHITECTURE       USE CASES        OPTIMIZATION
        |                |                 |
   ┌────┴────┐      ┌───┴────┐       ┌────┴────┐
   ↓         ↓      ↓        ↓       ↓         ↓
Metadata  Storage  Media   Data    Caching  Presigned
Service   Nodes   Assets   Lake              URLs
   |         |       |       |        |         |
   ↓         ↓       ↓       ↓        ↓         ↓
Sharding  Erasure  CDN   Parquet   Redis    Client
          Coding  CloudFront Files  CloudFront Direct
   |         |       |       |        |       Upload
   ↓         ↓       ↓       ↓        ↓         ↓
Consistent  Reed-  Multi-  Lifecycle Multi-   Reduce
 Hashing   Solomon Region  Policies  Layer   Server
                   Replication S3→Glacier      Load
```

## EMOTIONAL ANCHORS (For Subconscious Power)

Create strong memories with these analogies:

**1. Object Store = Amazon Warehouse**
- **Immutable products** (can't modify item in warehouse)
- **Globally unique SKU** (bucket + key = unique identifier)
- **Metadata labels** (size, weight, category = object metadata)
- **Distributed warehouses** (multi-region replication)
- **Different storage zones** (Standard = shelf, Glacier = deep storage)

When you order (GET):
1. Lookup inventory system (metadata service)
2. Find nearest warehouse (placement service)
3. Retrieve from shelf (storage node)
4. Ship to you (data transfer)

**2. Versioning = Git for Files**
- Every PUT creates new commit (version)
- Delete is like git reset (delete marker)
- Can checkout old version (time travel)
- Branch = bucket, file = object

**3. Lifecycle Policies = Aging Wine**
- New wine = S3 Standard (expensive, frequently accessed)
- Aged wine = S3 IA (cheaper, infrequent access)
- Vintage wine = Glacier (cheapest, rare access)
- Automatic movement based on age (lifecycle rules)

**4. Erasure Coding = RAID for the Cloud**
- Don't store 3 full copies (expensive)
- Store data + parity bits (math magic)
- Can lose 4 out of 16 chunks (still reconstruct)
- 1.33x overhead vs 3x for replication (60% savings!)

**5. Presigned URLs = Valet Ticket**
- Give temporary permission (time-limited)
- Direct access (no bouncer/server in middle)
- Single-use or time-limited (expires)
- Secure (signed, can't forge)

---

## FINAL INTERVIEW TIPS

**What to emphasize**:
1. **Immutability is foundational**: "Objects are immutable. This enables aggressive caching, simple replication, and versioning. Updates are new versions, not in-place modifications."

2. **Durability vs availability trade-off**: "S3 optimizes for durability (11 nines) over availability (99.9%). Would rather be temporarily unavailable than lose data."

3. **Metadata is harder than data**: "Storing petabytes of blobs is straightforward. The hard part is metadata: indexing 100 trillion objects, supporting LIST operations, maintaining consistency."

4. **Economics drive design**: "Object storage is 10x cheaper than block storage. This changes what's possible: data lakes, unlimited backups, long-term archival. Lifecycle policies save 70%+ on costs."

5. **Not for everything**: "Object storage is NOT a database replacement. Use it for write-once, read-many workloads: media, backups, analytics, logs."

**Common mistakes to avoid**:
- Don't propose mutable objects (violates core design principle)
- Don't ignore metadata scalability (listing is hard at scale)
- Don't suggest low-latency guarantees (100ms is realistic, 10ms is not)
- Don't over-complicate durability (erasure coding + replication is proven)

**Closing statement**:
"Object storage revolutionized cloud computing by decoupling storage from compute. Its economics enabled data lakes, serverless, and ML at scale. The key insight: optimize for durability and cost at the expense of latency and mutability."
