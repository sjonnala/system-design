# Infrastructure as Code (IaC)

Infrastructure as Code is the practice of managing and provisioning infrastructure through machine-readable definition files, rather than physical hardware configuration or interactive configuration tools.

## Core Concepts

### What is IaC?

**Definition**: Managing infrastructure (servers, networks, databases, etc.) using code that can be versioned, tested, and deployed automatically.

**Benefits**:
- **Repeatability**: Same code produces same infrastructure
- **Version Control**: Track changes over time
- **Automation**: Reduce manual errors
- **Documentation**: Code serves as documentation
- **Testing**: Infrastructure can be tested before deployment
- **Speed**: Fast provisioning and scaling

### Declarative vs Imperative

**Declarative** (What you want):
```hcl
# Terraform example
resource "aws_instance" "web" {
  ami           = "ami-0c55b159cbfafe1f0"
  instance_type = "t3.medium"
  count         = 3
}
```
- Define desired end state
- Tool figures out how to achieve it
- Examples: Terraform, CloudFormation, Pulumi (when using declarative style)

**Imperative** (How to do it):
```python
# Imperative example
for i in range(3):
    ec2.create_instance(
        ami='ami-0c55b159cbfafe1f0',
        instance_type='t3.medium'
    )
```
- Define specific steps to execute
- More control, more complexity
- Examples: Scripts, Ansible (partially), AWS SDK

## Major IaC Tools

### 1. Terraform (HashiCorp)

**Overview**:
- Most popular IaC tool
- Cloud-agnostic (multi-cloud)
- Declarative HCL language
- Rich provider ecosystem

**Architecture**:
```
┌──────────────┐
│  .tf files   │ ← Configuration
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  terraform   │ ← CLI tool
│    plan      │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│  Providers   │ ← AWS, GCP, Azure, etc.
└──────┬───────┘
       │
       ▼
┌──────────────┐
│    State     │ ← terraform.tfstate
└──────────────┘
```

**Key Features**:
- **State Management**: Tracks infrastructure state
- **Plan/Apply Workflow**: Preview changes before applying
- **Modules**: Reusable infrastructure components
- **Remote State**: Team collaboration with locking
- **Providers**: 1000+ providers for different services

**Example - EC2 with VPC**:
```hcl
# VPC
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true

  tags = {
    Name = "main-vpc"
  }
}

# Subnet
resource "aws_subnet" "public" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.1.0/24"
  availability_zone = "us-west-2a"

  tags = {
    Name = "public-subnet"
  }
}

# Internet Gateway
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
}

# EC2 Instance
resource "aws_instance" "web" {
  ami           = "ami-0c55b159cbfafe1f0"
  instance_type = "t3.medium"
  subnet_id     = aws_subnet.public.id

  tags = {
    Name = "web-server"
  }
}
```

**Terraform Modules**:
```hcl
# modules/vpc/main.tf
variable "cidr_block" {
  type = string
}

resource "aws_vpc" "this" {
  cidr_block = var.cidr_block
}

output "vpc_id" {
  value = aws_vpc.this.id
}

# Root module usage
module "vpc" {
  source     = "./modules/vpc"
  cidr_block = "10.0.0.0/16"
}

resource "aws_instance" "web" {
  vpc_id = module.vpc.vpc_id
  # ...
}
```

**State Management**:
```hcl
# Remote state with S3 + DynamoDB locking
terraform {
  backend "s3" {
    bucket         = "my-terraform-state"
    key            = "prod/terraform.tfstate"
    region         = "us-west-2"
    dynamodb_table = "terraform-locks"
    encrypt        = true
  }
}
```

### 2. AWS CloudFormation

**Overview**:
- AWS-native IaC service
- JSON or YAML templates
- Deep AWS integration
- Free to use (pay for resources)

**Example**:
```yaml
AWSTemplateFormatVersion: '2010-09-09'
Description: 'Web server stack'

Parameters:
  InstanceType:
    Type: String
    Default: t3.medium
    AllowedValues:
      - t3.small
      - t3.medium
      - t3.large

Resources:
  WebServer:
    Type: AWS::EC2::Instance
    Properties:
      InstanceType: !Ref InstanceType
      ImageId: ami-0c55b159cbfafe1f0
      Tags:
        - Key: Name
          Value: WebServer

  WebServerSecurityGroup:
    Type: AWS::EC2::SecurityGroup
    Properties:
      GroupDescription: Allow HTTP
      SecurityGroupIngress:
        - IpProtocol: tcp
          FromPort: 80
          ToPort: 80
          CidrIp: 0.0.0.0/0

Outputs:
  InstanceId:
    Value: !Ref WebServer
    Description: Instance ID
```

**Features**:
- **StackSets**: Deploy across multiple accounts/regions
- **Change Sets**: Preview changes before applying
- **Drift Detection**: Detect manual changes
- **Rollback**: Automatic rollback on failure

### 3. Pulumi

**Overview**:
- Modern IaC using real programming languages
- TypeScript, Python, Go, C#, Java
- Combines IaC with general-purpose programming

**Example (TypeScript)**:
```typescript
import * as aws from "@pulumi/aws";

// Create VPC
const vpc = new aws.ec2.Vpc("main-vpc", {
    cidrBlock: "10.0.0.0/16",
    enableDnsHostnames: true,
});

// Create subnet
const subnet = new aws.ec2.Subnet("public-subnet", {
    vpcId: vpc.id,
    cidrBlock: "10.0.1.0/24",
    availabilityZone: "us-west-2a",
});

// Create security group
const securityGroup = new aws.ec2.SecurityGroup("web-sg", {
    vpcId: vpc.id,
    ingress: [{
        protocol: "tcp",
        fromPort: 80,
        toPort: 80,
        cidrBlocks: ["0.0.0.0/0"],
    }],
});

// Create EC2 instance
const instance = new aws.ec2.Instance("web-server", {
    ami: "ami-0c55b159cbfafe1f0",
    instanceType: "t3.medium",
    subnetId: subnet.id,
    vpcSecurityGroupIds: [securityGroup.id],
});

// Export outputs
export const instanceId = instance.id;
export const publicIp = instance.publicIp;
```

**Advantages**:
- Use familiar programming languages
- Better IDE support (autocomplete, type checking)
- Advanced logic (loops, conditionals, functions)
- Unit testing with standard frameworks

### 4. Ansible

**Overview**:
- Configuration management + provisioning
- Agentless (SSH-based)
- YAML playbooks
- Imperative and declarative

**Example Playbook**:
```yaml
---
- name: Configure web servers
  hosts: webservers
  become: yes

  tasks:
    - name: Install nginx
      apt:
        name: nginx
        state: present
        update_cache: yes

    - name: Start nginx
      service:
        name: nginx
        state: started
        enabled: yes

    - name: Deploy website
      copy:
        src: /local/path/index.html
        dest: /var/www/html/index.html
        owner: www-data
        mode: '0644'
```

## Best Practices

### 1. State Management

**Remote State**:
```hcl
# Store state remotely for team collaboration
terraform {
  backend "s3" {
    bucket         = "company-terraform-state"
    key            = "prod/vpc/terraform.tfstate"
    region         = "us-west-2"
    encrypt        = true
    dynamodb_table = "terraform-state-locks"
  }
}
```

**State Locking**:
- Prevent concurrent modifications
- Use DynamoDB (AWS), GCS (Google), Azure Storage

### 2. Module Design

**Module Structure**:
```
modules/
├── vpc/
│   ├── main.tf
│   ├── variables.tf
│   ├── outputs.tf
│   └── README.md
├── eks/
│   ├── main.tf
│   ├── variables.tf
│   └── outputs.tf
└── rds/
    ├── main.tf
    ├── variables.tf
    └── outputs.tf
```

**Module Principles**:
- Single responsibility
- Well-defined inputs/outputs
- Self-contained
- Versioned (git tags)
- Documented

### 3. Environment Management

**Workspace Approach**:
```bash
# Terraform workspaces
terraform workspace new dev
terraform workspace new staging
terraform workspace new prod

# Different state files per environment
terraform workspace select prod
terraform apply
```

**Directory Approach**:
```
infrastructure/
├── modules/
├── environments/
│   ├── dev/
│   │   ├── main.tf
│   │   └── terraform.tfvars
│   ├── staging/
│   │   ├── main.tf
│   │   └── terraform.tfvars
│   └── prod/
│       ├── main.tf
│       └── terraform.tfvars
```

### 4. Secrets Management

**DON'T**:
```hcl
# BAD - hardcoded secrets
resource "aws_db_instance" "main" {
  password = "mysecretpassword123"  # ❌ Never do this
}
```

**DO**:
```hcl
# GOOD - use secrets manager
data "aws_secretsmanager_secret_version" "db_password" {
  secret_id = "prod/db/password"
}

resource "aws_db_instance" "main" {
  password = data.aws_secretsmanager_secret_version.db_password.secret_string
}

# Or use variables
variable "db_password" {
  type      = string
  sensitive = true
}

# Pass via environment variable
# TF_VAR_db_password=xxx terraform apply
```

### 5. Testing

**Levels of Testing**:

1. **Syntax Validation**:
```bash
terraform validate
terraform fmt -check
```

2. **Static Analysis**:
```bash
# TFLint
tflint

# Checkov (security)
checkov -d .

# Terraform Compliance
terraform-compliance -f compliance/ -p plan.out
```

3. **Unit Testing** (Pulumi):
```typescript
import * as pulumi from "@pulumi/pulumi";
import { describe, it } from "mocha";
import * as assert from "assert";

describe("infrastructure", () => {
    it("creates VPC with correct CIDR", (done) => {
        const vpc = new Vpc("test-vpc", { cidr: "10.0.0.0/16" });

        pulumi.all([vpc.cidr]).apply(([cidr]) => {
            assert.strictEqual(cidr, "10.0.0.0/16");
            done();
        });
    });
});
```

4. **Integration Testing**:
```bash
# Terratest (Go)
go test -v -timeout 30m
```

## System Design Considerations

### Designing IaC for Scale

**Multi-Account Strategy**:
```
Organization
├── dev-account (123456789012)
├── staging-account (234567890123)
└── prod-account (345678901234)

Each account has:
├── Networking (VPC, subnets)
├── Compute (ECS, EKS)
├── Data (RDS, DynamoDB)
└── Security (IAM, KMS)
```

**Module Registry**:
```
Central Module Repository
├── terraform-aws-vpc (v2.1.0)
├── terraform-aws-eks (v3.5.2)
├── terraform-aws-rds (v1.8.1)
└── terraform-aws-alb (v2.0.3)

Teams consume versioned modules
```

### CI/CD for Infrastructure

**Pipeline Flow**:
```
┌──────────────┐
│  Git Push    │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Validate   │ ← terraform validate, fmt, lint
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Plan       │ ← terraform plan
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Review     │ ← Manual approval
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Apply      │ ← terraform apply
└──────────────┘
```

**Example GitHub Actions**:
```yaml
name: Terraform

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

jobs:
  terraform:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Setup Terraform
        uses: hashicorp/setup-terraform@v2

      - name: Terraform Format
        run: terraform fmt -check

      - name: Terraform Init
        run: terraform init

      - name: Terraform Validate
        run: terraform validate

      - name: Terraform Plan
        run: terraform plan -out=tfplan

      - name: Terraform Apply
        if: github.ref == 'refs/heads/main'
        run: terraform apply -auto-approve tfplan
```

## Common Interview Questions

### Architecture

**Q: Design IaC for a multi-region, multi-account AWS setup**

Key considerations:
- Account structure (dev, staging, prod)
- Region strategy (primary, DR)
- Module organization
- State management per account
- Cross-account permissions
- Networking (VPC peering, Transit Gateway)

**Q: How do you handle secrets in IaC?**

Approaches:
- AWS Secrets Manager / Parameter Store
- HashiCorp Vault
- Encrypted at rest (KMS)
- Never commit to version control
- Rotate regularly
- Audit access

**Q: How do you manage infrastructure drift?**

Solutions:
- Regular drift detection (terraform plan)
- Automated reconciliation
- Alerts on manual changes
- Read-only production access
- Infrastructure locks
- Change tracking

### Technical Deep Dives

**Q: Explain Terraform state and why it's important**

Terraform state:
- Maps configuration to real resources
- Tracks metadata (dependencies, outputs)
- Enables planning (comparing desired vs actual)
- Required for updates and destroys
- Must be protected (contains sensitive data)
- Should be remote (team collaboration)

**Q: How do you handle large-scale infrastructure changes?**

Strategies:
- Use `-target` for specific resources
- Break into smaller chunks
- Blue/green infrastructure approach
- Feature flags for infrastructure
- Canary deployments
- Automated rollback plans

**Q: Compare Terraform vs CloudFormation vs Pulumi**

| Feature | Terraform | CloudFormation | Pulumi |
|---------|-----------|----------------|--------|
| Multi-cloud | Yes | No (AWS only) | Yes |
| Language | HCL | JSON/YAML | Real languages |
| State | Required | Managed by AWS | Managed by Pulumi |
| Community | Huge | AWS-focused | Growing |
| Learning curve | Medium | Medium | Low (if you know the language) |

## Real-World Scenarios

### Scenario 1: Migrating from Manual to IaC

**Challenge**: 500+ manually created AWS resources

**Approach**:
1. **Inventory**: Use AWS Config to catalog resources
2. **Import**: Use `terraform import` to bring existing resources under management
3. **Refactor**: Organize into logical modules
4. **Validate**: Ensure imported state matches actual
5. **Document**: Create runbooks for the process
6. **Migrate**: Environment by environment (dev → staging → prod)

### Scenario 2: Multi-Cloud Strategy

**Challenge**: Run workloads on AWS + GCP

**Solution**:
```hcl
# Provider configuration
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.0"
    }
    google = {
      source  = "hashicorp/google"
      version = "~> 4.0"
    }
  }
}

provider "aws" {
  region = "us-west-2"
}

provider "google" {
  project = "my-project"
  region  = "us-central1"
}

# AWS resources
module "aws_vpc" {
  source = "./modules/aws-vpc"
}

# GCP resources
module "gcp_vpc" {
  source = "./modules/gcp-vpc"
}
```

### Scenario 3: Disaster Recovery

**Challenge**: Automate DR environment provisioning

**Solution**:
- Primary region: Active resources
- DR region: Terraform code ready, minimal resources
- On disaster: `terraform apply` in DR region
- Automated DNS failover
- Regular DR drills using IaC

## Resources

**Official Docs**:
- [Terraform Documentation](https://www.terraform.io/docs)
- [AWS CloudFormation](https://docs.aws.amazon.com/cloudformation)
- [Pulumi Documentation](https://www.pulumi.com/docs)

**Learning**:
- HashiCorp Learn (Terraform tutorials)
- AWS CloudFormation Workshop
- Pulumi Examples Repository

**Tools**:
- Terragrunt (Terraform wrapper for DRY)
- Atlantis (Terraform PR automation)
- TFLint (Linting)
- Checkov (Security scanning)
