# Object Storage

Object storage is designed for storing and serving unstructured data like files, images, videos, and backups at massive scale.

## Contents
- [What is Object Storage?](#what-is-object-storage)
- [Object Storage vs Other Storage Types](#object-storage-vs-other-storage-types)
- [Architecture and Concepts](#architecture-and-concepts)
- [Major Providers](#major-providers)
- [Use Cases](#use-cases)
- [Design Patterns](#design-patterns)
- [Best Practices](#best-practices)

## What is Object Storage?

Object storage manages data as **objects** rather than files (file storage) or blocks (block storage).

### Object Components

```
Object = Data + Metadata + Unique ID

┌─────────────────────────────────┐
│ Unique ID (Key)                 │
│  └─ /users/123/profile.jpg      │
├─────────────────────────────────┤
│ Data (Binary)                   │
│  └─ [image bytes...]            │
├─────────────────────────────────┤
│ Metadata                        │
│  ├─ Content-Type: image/jpeg    │
│  ├─ Size: 2.5 MB                │
│  ├─ Last-Modified: 2024-01-15   │
│  ├─ ETag: "abc123..."           │
│  └─ Custom: user_id=123         │
└─────────────────────────────────┘
```

### Key Characteristics

✅ **Highly Scalable** - Store billions of objects
✅ **Cost-Effective** - Cheaper than block/file storage
✅ **Durable** - 99.999999999% (11 nines) durability
✅ **HTTP-Accessible** - RESTful API
✅ **Metadata-Rich** - Custom key-value metadata
❌ **No Hierarchical File System** - Flat namespace
❌ **No Partial Updates** - Must rewrite entire object
❌ **Higher Latency** - Not for low-latency apps

## Object Storage vs Other Storage Types

| Feature | Object Storage | File Storage | Block Storage |
|---------|---------------|--------------|---------------|
| **Data Model** | Objects (key-value) | Files/Directories | Raw blocks |
| **Access Method** | HTTP/S3 API | NFS/SMB | iSCSI/FC |
| **Scalability** | Petabytes+ | Limited | Limited |
| **Cost** | Low | Medium | High |
| **Performance** | Medium | Medium | High (IOPS) |
| **Use Case** | Backups, media | Shared files | Databases, VMs |
| **Modification** | Replace entire object | Modify parts | Modify blocks |
| **Examples** | S3, GCS, Azure Blob | NFS, EFS, Azure Files | EBS, SAN |

### When to Use Each

**Object Storage:**
- Static assets (images, videos, CSS, JS)
- Backups and archives
- Data lakes
- Content distribution

**File Storage:**
- Shared documents
- Home directories
- Content management systems
- Development environments

**Block Storage:**
- Database storage
- Virtual machine disks
- High-performance applications
- Transactional workloads

## Architecture and Concepts

### Flat Namespace

**No true directories** - keys look like paths but are flat:

```
bucket/users/123/avatar.jpg     → Key (not a directory structure)
bucket/users/456/avatar.jpg     → Separate object
bucket/users/123/documents/resume.pdf → Another key
```

**List by prefix:**
```bash
# List all objects for user 123
aws s3 ls s3://bucket/users/123/
```

### Buckets (Containers)

**Top-level namespace** for organizing objects.

```
AWS S3:        Bucket
Azure:         Container
Google Cloud:  Bucket
```

**Properties:**
- Globally unique name (across all users)
- Region-specific
- Access control
- Versioning, lifecycle policies

```bash
# Create bucket
aws s3 mb s3://my-unique-bucket-name

# List buckets
aws s3 ls
```

### Object Versioning

**Keep multiple versions** of the same object.

```
my-file.txt (version 1) → ID: abc123
my-file.txt (version 2) → ID: def456
my-file.txt (version 3) → ID: ghi789 (latest)
```

**Benefits:**
- Protect against accidental deletes
- Rollback to previous versions
- Audit trail

**Cost:** Storage for all versions

```bash
# Enable versioning
aws s3api put-bucket-versioning \
  --bucket my-bucket \
  --versioning-configuration Status=Enabled

# List versions
aws s3api list-object-versions --bucket my-bucket --prefix my-file.txt
```

### Storage Classes (Tiers)

**Different classes for different access patterns:**

**AWS S3 Storage Classes:**

| Class | Use Case | Durability | Availability | Cost |
|-------|----------|------------|--------------|------|
| **S3 Standard** | Frequently accessed | 11 9's | 99.99% | $$$ |
| **S3 Intelligent-Tiering** | Unknown/changing access | 11 9's | 99.9% | $$ (auto) |
| **S3 Standard-IA** | Infrequent access | 11 9's | 99.9% | $$ |
| **S3 One Zone-IA** | Infrequent, non-critical | 11 9's | 99.5% | $ |
| **S3 Glacier Instant** | Archive, instant retrieval | 11 9's | 99.9% | $ |
| **S3 Glacier Flexible** | Archive, mins-hours retrieval | 11 9's | 99.99% | ¢ |
| **S3 Glacier Deep** | Long-term archive, 12hr retrieval | 11 9's | 99.99% | ¢¢ |

**Lifecycle Policies:**

```json
{
  "Rules": [{
    "Id": "Archive old logs",
    "Status": "Enabled",
    "Transitions": [
      {
        "Days": 30,
        "StorageClass": "STANDARD_IA"
      },
      {
        "Days": 90,
        "StorageClass": "GLACIER"
      }
    ],
    "Expiration": {
      "Days": 365
    }
  }]
}
```

### Multipart Upload

**Upload large files in parts** for reliability and speed.

```
┌─────────────────────────────┐
│ Large File (5 GB)           │
└─────────────────────────────┘
         │ Split
         ▼
┌──────┬──────┬──────┬──────┐
│Part 1│Part 2│Part 3│Part 4│  ← Upload in parallel
└──────┴──────┴──────┴──────┘
         │ Combine
         ▼
┌─────────────────────────────┐
│ Complete Object in S3       │
└─────────────────────────────┘
```

**Benefits:**
- Parallel uploads (faster)
- Resume failed uploads
- Upload while still creating file

**Recommended for files > 100 MB**

```python
import boto3
s3 = boto3.client('s3')

# Initiate multipart upload
response = s3.create_multipart_upload(
    Bucket='my-bucket',
    Key='large-file.zip'
)
upload_id = response['UploadId']

# Upload parts
parts = []
for i, chunk in enumerate(file_chunks, start=1):
    part = s3.upload_part(
        Bucket='my-bucket',
        Key='large-file.zip',
        PartNumber=i,
        UploadId=upload_id,
        Body=chunk
    )
    parts.append({
        'PartNumber': i,
        'ETag': part['ETag']
    })

# Complete upload
s3.complete_multipart_upload(
    Bucket='my-bucket',
    Key='large-file.zip',
    UploadId=upload_id,
    MultipartUpload={'Parts': parts}
)
```

## Major Providers

### AWS S3 (Simple Storage Service)

**Most popular, industry standard.**

```bash
# Upload
aws s3 cp file.txt s3://bucket/key

# Download
aws s3 cp s3://bucket/key file.txt

# List
aws s3 ls s3://bucket/prefix/

# Sync directory
aws s3 sync ./local-dir s3://bucket/remote-dir/
```

**Key Features:**
- Versioning
- Object Lock (WORM)
- Replication (cross-region, same-region)
- Event notifications (Lambda triggers)
- S3 Select (query objects with SQL)
- Transfer Acceleration (edge locations)

### Azure Blob Storage

**Microsoft's object storage.**

```bash
# Upload
az storage blob upload \
  --account-name myaccount \
  --container-name mycontainer \
  --name myblob \
  --file ./file.txt

# Download
az storage blob download \
  --account-name myaccount \
  --container-name mycontainer \
  --name myblob \
  --file ./downloaded.txt
```

**Blob Types:**
- **Block Blobs:** General purpose (text, binary)
- **Append Blobs:** Log files (append-only)
- **Page Blobs:** Virtual machine disks

### Google Cloud Storage (GCS)

**Google's object storage.**

```bash
# Upload
gsutil cp file.txt gs://bucket/key

# Download
gsutil cp gs://bucket/key file.txt

# List
gsutil ls gs://bucket/
```

**Storage Classes:**
- Standard
- Nearline (30-day minimum)
- Coldline (90-day minimum)
- Archive (365-day minimum)

### MinIO

**Open-source S3-compatible object storage.**

```bash
# Self-hosted
docker run -p 9000:9000 -p 9001:9001 \
  -e "MINIO_ROOT_USER=admin" \
  -e "MINIO_ROOT_PASSWORD=password" \
  minio/minio server /data --console-address ":9001"
```

**Use Cases:**
- Private cloud
- On-premises storage
- Local development
- Air-gapped environments

## Use Cases

### 1. Static Website Hosting

**Serve HTML, CSS, JS, images directly from object storage.**

```bash
# Enable static website hosting
aws s3 website s3://my-website-bucket \
  --index-document index.html \
  --error-document error.html

# Upload website files
aws s3 sync ./website/ s3://my-website-bucket/

# Access: http://my-website-bucket.s3-website-us-east-1.amazonaws.com
```

**Benefits:**
- No servers to manage
- Automatic scaling
- Low cost
- High availability

### 2. Media Storage and Streaming

**Store images, videos, audio files.**

```
User uploads photo
     ↓
Store in S3
     ↓
Generate thumbnails (Lambda)
     ↓
Store thumbnails in S3
     ↓
Serve via CloudFront CDN
```

**Example: Instagram-like Service**
```
Original:     s3://media/photos/abc123/original.jpg
Thumbnail:    s3://media/photos/abc123/thumb_150x150.jpg
Medium:       s3://media/photos/abc123/medium_800x800.jpg
CDN URL:      https://cdn.example.com/photos/abc123/thumb_150x150.jpg
```

### 3. Backup and Disaster Recovery

**Durable, cheap storage for backups.**

```bash
# Database backup
mysqldump -u root database > backup.sql
gzip backup.sql

# Upload to S3 Glacier
aws s3 cp backup.sql.gz s3://backups/db/2024-01-15.sql.gz \
  --storage-class GLACIER

# Lifecycle: Delete after 7 years
```

### 4. Data Lakes

**Centralized repository for structured and unstructured data.**

```
S3 Data Lake Structure:
├── raw/                  (Raw ingested data)
│   ├── logs/
│   ├── events/
│   └── third-party/
├── processed/            (Cleaned, transformed)
│   ├── daily/
│   └── aggregated/
└── analytics/            (Query-optimized formats)
    ├── parquet/
    └── orc/
```

**Query with:**
- AWS Athena (SQL over S3)
- Presto
- Apache Spark

### 5. Application Asset Storage

**Store user-generated content.**

```python
# User profile picture upload
def upload_avatar(user_id, image_file):
    s3 = boto3.client('s3')
    key = f'avatars/{user_id}/avatar.jpg'

    s3.upload_fileobj(
        image_file,
        'user-assets-bucket',
        key,
        ExtraArgs={
            'ContentType': 'image/jpeg',
            'CacheControl': 'max-age=31536000',
            'Metadata': {
                'user_id': str(user_id),
                'uploaded_at': datetime.now().isoformat()
            }
        }
    )

    # Return CDN URL
    return f'https://cdn.example.com/avatars/{user_id}/avatar.jpg'
```

### 6. Big Data and Analytics

**Store datasets for processing.**

```python
# Store JSON logs
s3.put_object(
    Bucket='analytics-data',
    Key='logs/2024/01/15/events.json',
    Body=json.dumps(events)
)

# Query with Athena
SELECT event_type, COUNT(*) as count
FROM logs
WHERE year='2024' AND month='01' AND day='15'
GROUP BY event_type;
```

## Design Patterns

### 1. Direct Upload from Client

**Avoid proxying through server:**

```
┌──────────┐                    ┌──────────┐
│  Client  │────── Upload ─────→│   S3     │
└──────────┘                    └──────────┘
      ↓
  ┌──────────┐
  │  Server  │ (provides signed URL)
  └──────────┘
```

**Presigned URL (time-limited):**

```python
# Server generates presigned URL
s3 = boto3.client('s3')
url = s3.generate_presigned_url(
    'put_object',
    Params={
        'Bucket': 'my-bucket',
        'Key': f'uploads/{user_id}/{filename}',
        'ContentType': 'image/jpeg'
    },
    ExpiresIn=3600  # 1 hour
)

# Return URL to client
# Client uploads directly to S3 using URL
```

**Benefits:**
- Reduce server load
- Faster uploads
- Lower bandwidth costs

### 2. CDN Integration

**Distribute content globally:**

```
┌──────────┐      ┌──────────────┐      ┌──────────┐
│  Client  │─────→│ CloudFront   │─────→│   S3     │
└──────────┘      │  (CDN Edge)  │      │ (Origin) │
                  └──────────────┘      └──────────┘
                   Cache at edge
                   (low latency)
```

**Benefits:**
- Low latency (edge locations)
- Reduced S3 costs (fewer requests)
- DDoS protection
- SSL/TLS termination

### 3. Event-Driven Processing

**Trigger functions on object events:**

```
Upload image to S3
      ↓
   S3 Event
      ↓
  Lambda Function
      ↓
Generate thumbnail
      ↓
Store thumbnail in S3
      ↓
Update database
```

**AWS Lambda S3 Trigger:**

```python
def lambda_handler(event, context):
    # Triggered on S3 upload
    bucket = event['Records'][0]['s3']['bucket']['name']
    key = event['Records'][0]['s3']['object']['key']

    # Download image
    s3.download_file(bucket, key, '/tmp/image.jpg')

    # Process (resize, optimize, etc.)
    create_thumbnail('/tmp/image.jpg', '/tmp/thumb.jpg')

    # Upload thumbnail
    s3.upload_file('/tmp/thumb.jpg', bucket, f'thumbnails/{key}')
```

### 4. Versioning and Immutability

**Never overwrite, always create new version:**

```bash
# Enable versioning
aws s3api put-bucket-versioning \
  --bucket my-bucket \
  --versioning-configuration Status=Enabled

# Upload file (creates version 1)
aws s3 cp file.txt s3://my-bucket/file.txt

# Upload again (creates version 2)
aws s3 cp file.txt s3://my-bucket/file.txt

# Both versions preserved
```

**Compliance (Object Lock):**

```bash
# WORM (Write Once Read Many)
aws s3api put-object-lock-configuration \
  --bucket my-bucket \
  --object-lock-configuration '{
    "ObjectLockEnabled": "Enabled",
    "Rule": {
      "DefaultRetention": {
        "Mode": "COMPLIANCE",
        "Years": 7
      }
    }
  }'
```

### 5. Partitioning for Performance

**Distribute objects across key prefixes:**

```bash
# ❌ Bad: All objects with same prefix (hot partition)
s3://bucket/images/img1.jpg
s3://bucket/images/img2.jpg
...
s3://bucket/images/img1000000.jpg

# ✅ Good: Distributed by hash
s3://bucket/images/a3/img1.jpg
s3://bucket/images/7f/img2.jpg
...
s3://bucket/images/2b/img1000000.jpg

# Or by date
s3://bucket/logs/2024/01/15/events.log
s3://bucket/logs/2024/01/16/events.log
```

**Benefits:**
- Avoid rate limits
- Better parallelism
- Improved performance

## Best Practices

### 1. Security

**Principle of Least Privilege:**

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"AWS": "arn:aws:iam::123456789012:user/app"},
    "Action": ["s3:GetObject", "s3:PutObject"],
    "Resource": "arn:aws:s3:::my-bucket/uploads/*"
  }]
}
```

**Encryption:**

```bash
# Server-side encryption (SSE-S3)
aws s3 cp file.txt s3://bucket/key \
  --server-side-encryption AES256

# SSE-KMS (customer managed keys)
aws s3 cp file.txt s3://bucket/key \
  --server-side-encryption aws:kms \
  --ssekms-key-id arn:aws:kms:us-east-1:123456789012:key/abc-123

# Client-side encryption (encrypt before upload)
```

**Block Public Access:**

```bash
# Prevent accidental public exposure
aws s3api put-public-access-block \
  --bucket my-bucket \
  --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
```

### 2. Cost Optimization

**Use lifecycle policies:**

```json
{
  "Rules": [{
    "Id": "Optimize storage costs",
    "Status": "Enabled",
    "Transitions": [
      {"Days": 30, "StorageClass": "STANDARD_IA"},
      {"Days": 90, "StorageClass": "GLACIER"}
    ],
    "NoncurrentVersionTransitions": [
      {"NoncurrentDays": 7, "StorageClass": "GLACIER"}
    ],
    "AbortIncompleteMultipartUpload": {"DaysAfterInitiation": 7}
  }]
}
```

**Intelligent-Tiering:**
- Automatically moves objects between access tiers
- No retrieval fees
- Small monthly monitoring fee

**Compress data:**
```bash
# Compress before upload
gzip large-file.json
aws s3 cp large-file.json.gz s3://bucket/key \
  --content-encoding gzip \
  --content-type application/json
```

### 3. Performance

**Parallel uploads:**
```bash
# AWS CLI automatically uses multipart for large files
aws s3 cp large-file.zip s3://bucket/key \
  --storage-class STANDARD_IA
```

**Transfer Acceleration:**
```bash
# Enable
aws s3api put-bucket-accelerate-configuration \
  --bucket my-bucket \
  --accelerate-configuration Status=Enabled

# Upload via edge locations (faster for distant clients)
aws s3 cp file.txt s3://my-bucket/key --endpoint-url https://my-bucket.s3-accelerate.amazonaws.com
```

**Use CDN:**
- CloudFront (AWS)
- Azure CDN
- Cloud CDN (GCP)
- Cloudflare

### 4. Reliability

**Enable versioning:**
```bash
aws s3api put-bucket-versioning \
  --bucket my-bucket \
  --versioning-configuration Status=Enabled
```

**Cross-region replication:**
```json
{
  "Role": "arn:aws:iam::123456789012:role/replication-role",
  "Rules": [{
    "Status": "Enabled",
    "Priority": 1,
    "Destination": {
      "Bucket": "arn:aws:s3:::backup-bucket-us-west-2",
      "ReplicationTime": {
        "Status": "Enabled",
        "Time": {"Minutes": 15}
      }
    }
  }]
}
```

**Monitor and alert:**
```bash
# CloudWatch metrics
- BucketSizeBytes
- NumberOfObjects
- AllRequests
- 4xxErrors, 5xxErrors

# Set alarms for anomalies
```

## Common Mistakes

❌ **Making buckets public** - Default to private
❌ **Not using lifecycle policies** - Wasted cost
❌ **Ignoring encryption** - Security risk
❌ **Large sequential key names** - Performance bottleneck
❌ **Not enabling versioning** - Risk of data loss
❌ **Storing secrets in objects** - Use secret management
❌ **Inefficient key structure** - Hard to query/list

## Further Reading

- [AWS S3 Documentation](https://docs.aws.amazon.com/s3/)
- [Azure Blob Storage](https://docs.microsoft.com/en-us/azure/storage/blobs/)
- [Google Cloud Storage](https://cloud.google.com/storage/docs)
- [S3 Best Practices](https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html)
- [Object Storage Performance](https://aws.amazon.com/s3/features/performance/)
