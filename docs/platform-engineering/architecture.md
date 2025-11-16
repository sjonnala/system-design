# Platform Architecture

Platform architecture focuses on designing internal developer platforms (IDPs) that enable teams to build, deploy, and operate applications efficiently at scale.

## Core Concepts

### What is an Internal Developer Platform (IDP)?

**Definition**: A self-service layer that abstracts infrastructure complexity and provides consistent workflows for application teams.

**Key Characteristics**:
- **Self-Service**: Developers can provision resources without manual ops intervention
- **Standardized**: Consistent patterns across all teams
- **Automated**: Reduce manual toil
- **Guardrails**: Security and compliance by default
- **Observable**: Built-in monitoring and logging

### Platform Layers

```
┌─────────────────────────────────────────────────────────┐
│            Developer Experience (Portal/CLI)            │
│  - Self-service UI                                      │
│  - CLI tools                                            │
│  - APIs                                                 │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│              Application Services                       │
│  - Databases (RDS, DynamoDB)                           │
│  - Caches (Redis, Memcached)                           │
│  - Message Queues (SQS, Kafka)                         │
│  - Storage (S3, EBS)                                   │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│              Platform Services                          │
│  - CI/CD (Jenkins, ArgoCD)                             │
│  - Observability (Prometheus, Grafana)                 │
│  - Secret Management (Vault)                           │
│  - Service Mesh (Istio)                                │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│          Container Orchestration                        │
│  - Kubernetes                                          │
│  - Service discovery                                   │
│  - Load balancing                                      │
│  - Auto-scaling                                        │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│              Infrastructure                             │
│  - Compute (EC2, ECS, Lambda)                          │
│  - Networking (VPC, Load Balancers)                    │
│  - Storage (EBS, S3)                                   │
└─────────────────────────────────────────────────────────┘
```

## Platform Architecture Patterns

### 1. Multi-Tenant Kubernetes Platform

**Architecture**:
```
┌─────────────────────────────────────────────────────────┐
│                  Control Plane                          │
│  - Kubernetes API Server                               │
│  - etcd                                                │
│  - Controllers                                         │
└─────────────────────┬───────────────────────────────────┘
                      │
        ┌─────────────┼─────────────┬─────────────┐
        │             │             │             │
┌───────▼────┐ ┌──────▼─────┐ ┌───▼──────┐ ┌───▼──────┐
│  Team A    │ │  Team B    │ │  Team C  │ │  Shared  │
│ Namespace  │ │ Namespace  │ │Namespace │ │ Services │
│            │ │            │ │          │ │          │
│ - Pods     │ │ - Pods     │ │ - Pods   │ │ -Ingress │
│ - Services │ │ - Services │ │ -Services│ │ -Monitoring│
│ - Ingress  │ │ - Ingress  │ │ -Ingress │ │ -Logging │
└────────────┘ └────────────┘ └──────────┘ └──────────┘
```

**Isolation Mechanisms**:

**Namespace-level Isolation**:
```yaml
# Namespace with quotas
apiVersion: v1
kind: Namespace
metadata:
  name: team-a
  labels:
    team: team-a

---
# Resource Quota
apiVersion: v1
kind: ResourceQuota
metadata:
  name: team-a-quota
  namespace: team-a
spec:
  hard:
    requests.cpu: "100"
    requests.memory: 200Gi
    requests.storage: 1Ti
    persistentvolumeclaims: "50"
    pods: "100"

---
# Limit Range (default limits)
apiVersion: v1
kind: LimitRange
metadata:
  name: team-a-limits
  namespace: team-a
spec:
  limits:
  - max:
      cpu: "4"
      memory: 8Gi
    min:
      cpu: "100m"
      memory: 128Mi
    default:
      cpu: "500m"
      memory: 512Mi
    defaultRequest:
      cpu: "200m"
      memory: 256Mi
    type: Container
```

**Network Policies**:
```yaml
# Deny all traffic by default
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
  namespace: team-a
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress

---
# Allow ingress from same namespace
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-same-namespace
  namespace: team-a
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  ingress:
  - from:
    - podSelector: {}

---
# Allow specific cross-namespace communication
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-from-ingress
  namespace: team-a
spec:
  podSelector:
    matchLabels:
      app: web
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
```

**RBAC**:
```yaml
# Role for team members
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: team-a-developer
  namespace: team-a
rules:
- apiGroups: ["", "apps", "batch"]
  resources: ["pods", "deployments", "services", "jobs", "cronjobs"]
  verbs: ["get", "list", "watch", "create", "update", "patch", "delete"]
- apiGroups: [""]
  resources: ["pods/log", "pods/exec"]
  verbs: ["get", "list", "watch"]

---
# RoleBinding
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: team-a-developers
  namespace: team-a
subjects:
- kind: Group
  name: team-a
  apiGroup: rbac.authorization.k8s.io
roleRef:
  kind: Role
  name: team-a-developer
  apiGroup: rbac.authorization.k8s.io
```

### 2. Multi-Cluster Platform

**Architecture**:
```
┌─────────────────────────────────────────────────────────┐
│              Management/Hub Cluster                     │
│  - ArgoCD (GitOps)                                     │
│  - Policy Engine (OPA/Kyverno)                         │
│  - Central Observability                               │
│  - Service Catalog                                     │
└────────┬──────────────────┬─────────────────┬──────────┘
         │                  │                 │
    ┌────▼────┐        ┌────▼────┐      ┌────▼────┐
    │  Dev    │        │ Staging │      │  Prod   │
    │ Cluster │        │ Cluster │      │ Cluster │
    │         │        │         │      │         │
    │ - Apps  │        │ - Apps  │      │ - Apps  │
    │ - Data  │        │ - Data  │      │ - Data  │
    └─────────┘        └─────────┘      └─────────┘
```

**When to Use Multi-Cluster**:

**Use Cases**:
- Environment separation (dev/staging/prod)
- Regional deployment (US/EU/Asia)
- Compliance requirements (PCI, HIPAA)
- Blast radius reduction
- Different tenants with strict isolation

**Trade-offs**:
| Single Cluster | Multi-Cluster |
|----------------|---------------|
| ✓ Simpler operations | ✗ Complex operations |
| ✓ Lower cost | ✗ Higher cost |
| ✓ Better resource utilization | ✗ Resource overhead per cluster |
| ✗ Single point of failure | ✓ Fault isolation |
| ✗ Noisy neighbor issues | ✓ Strong isolation |

### 3. Service Catalog Pattern

**Self-Service Portal**:
```yaml
# Backstage service definition
apiVersion: backstage.io/v1alpha1
kind: Component
metadata:
  name: postgres
  description: Managed PostgreSQL database
  tags:
    - database
    - postgresql
spec:
  type: service
  lifecycle: production
  owner: platform-team
  definition:
    apiVersion: v1
    kind: ServiceTemplate
    spec:
      parameters:
        - name: name
          type: string
          description: Database name
        - name: size
          type: string
          enum: [small, medium, large]
          default: small
        - name: version
          type: string
          enum: ["14", "15", "16"]
          default: "15"

      resources:
        - apiVersion: database.example.com/v1
          kind: PostgreSQL
          metadata:
            name: "{{ .parameters.name }}"
          spec:
            version: "{{ .parameters.version }}"
            size: "{{ .parameters.size }}"
            backupRetention: 7
```

**Crossplane Example** (Infrastructure as Code via K8s):
```yaml
# Composition for RDS
apiVersion: apiextensions.crossplane.io/v1
kind: Composition
metadata:
  name: postgres-rds
spec:
  compositeTypeRef:
    apiVersion: database.example.com/v1
    kind: PostgreSQL

  resources:
  - name: rds-instance
    base:
      apiVersion: rds.aws.crossplane.io/v1alpha1
      kind: DBInstance
      spec:
        forProvider:
          engine: postgres
          engineVersion: "15"
          dbInstanceClass: db.t3.medium
          masterUsername: admin
          allocatedStorage: 100
          storageEncrypted: true
          publiclyAccessible: false

  - name: security-group
    base:
      apiVersion: ec2.aws.crossplane.io/v1beta1
      kind: SecurityGroup
      spec:
        forProvider:
          groupName: "{{ .metadata.name }}-sg"
          description: "Security group for {{ .metadata.name }}"

  - name: connection-secret
    base:
      apiVersion: v1
      kind: Secret
      metadata:
        namespace: "{{ .metadata.namespace }}"
      stringData:
        endpoint: "{{ .resources.rds-instance.status.atProvider.endpoint }}"
```

## Platform Capabilities

### 1. Developer Portal

**Backstage Example**:
```yaml
# Software Catalog
apiVersion: backstage.io/v1alpha1
kind: System
metadata:
  name: e-commerce
  description: E-commerce platform
spec:
  owner: platform-team

---
apiVersion: backstage.io/v1alpha1
kind: Component
metadata:
  name: api-service
  description: REST API service
  annotations:
    github.com/project-slug: company/api-service
    jenkins.io/job-full-name: api-service/main
    pagerduty.com/integration-key: abc123
spec:
  type: service
  lifecycle: production
  owner: team-a
  system: e-commerce
  dependsOn:
    - component:database
    - component:cache
  providesApis:
    - api-v1
```

**Developer Portal Features**:
- Service catalog
- Documentation
- API explorer
- Deployment status
- Cost visibility
- Runbooks
- On-call information

### 2. Golden Paths

**Template for New Service**:
```yaml
# Cookiecutter/Backstage template
apiVersion: scaffolder.backstage.io/v1beta3
kind: Template
metadata:
  name: nodejs-service
  title: Node.js Microservice
  description: Create a new Node.js microservice
spec:
  owner: platform-team
  type: service

  parameters:
    - title: Service Information
      required:
        - name
        - owner
      properties:
        name:
          title: Name
          type: string
          description: Unique service name
        owner:
          title: Owner
          type: string
          ui:field: OwnerPicker
        description:
          title: Description
          type: string

  steps:
    - id: fetch-template
      name: Fetch Template
      action: fetch:template
      input:
        url: ./template
        values:
          name: ${{ parameters.name }}
          owner: ${{ parameters.owner }}

    - id: publish
      name: Publish to GitHub
      action: publish:github
      input:
        repoUrl: github.com?repo=${{ parameters.name }}

    - id: register
      name: Register in Catalog
      action: catalog:register
      input:
        repoContentsUrl: ${{ steps.publish.output.repoContentsUrl }}

    - id: create-argocd-app
      name: Create ArgoCD Application
      action: argocd:create-app
      input:
        name: ${{ parameters.name }}
        repoUrl: ${{ steps.publish.output.remoteUrl }}
```

### 3. Policy Enforcement

**OPA Gatekeeper**:
```yaml
# Require resource limits
apiVersion: templates.gatekeeper.sh/v1
kind: ConstraintTemplate
metadata:
  name: k8srequireresourcelimits
spec:
  crd:
    spec:
      names:
        kind: K8sRequireResourceLimits

  targets:
    - target: admission.k8s.gatekeeper.sh
      rego: |
        package k8srequireresourcelimits

        violation[{"msg": msg}] {
          container := input.review.object.spec.containers[_]
          not container.resources.limits.cpu
          msg := sprintf("Container %v missing CPU limit", [container.name])
        }

        violation[{"msg": msg}] {
          container := input.review.object.spec.containers[_]
          not container.resources.limits.memory
          msg := sprintf("Container %v missing memory limit", [container.name])
        }

---
# Apply constraint
apiVersion: constraints.gatekeeper.sh/v1beta1
kind: K8sRequireResourceLimits
metadata:
  name: must-have-limits
spec:
  match:
    kinds:
      - apiGroups: ["apps"]
        kinds: ["Deployment", "StatefulSet"]
```

**Kyverno** (Kubernetes-native policies):
```yaml
# Require labels
apiVersion: kyverno.io/v1
kind: ClusterPolicy
metadata:
  name: require-labels
spec:
  validationFailureAction: enforce
  rules:
  - name: check-labels
    match:
      any:
      - resources:
          kinds:
          - Deployment
          - Service
    validate:
      message: "Labels 'app', 'team', and 'env' are required"
      pattern:
        metadata:
          labels:
            app: "?*"
            team: "?*"
            env: "?*"

---
# Auto-add labels
apiVersion: kyverno.io/v1
kind: ClusterPolicy
metadata:
  name: add-default-labels
spec:
  rules:
  - name: add-team-label
    match:
      any:
      - resources:
          kinds:
          - Deployment
    mutate:
      patchStrategicMerge:
        metadata:
          labels:
            managed-by: platform
```

### 4. Cost Management

**Kubecost Integration**:
```yaml
# Install Kubecost
helm install kubecost kubecost/cost-analyzer \
  --namespace kubecost \
  --set prometheus.server.global.external_labels.cluster_id=prod

# View costs by team/namespace
kubectl port-forward -n kubecost svc/kubecost-cost-analyzer 9090:9090
```

**Cost Allocation**:
```yaml
# Label-based cost allocation
apiVersion: v1
kind: Namespace
metadata:
  name: team-a
  labels:
    team: team-a
    cost-center: engineering
    product: platform

---
# Resource quotas for cost control
apiVersion: v1
kind: ResourceQuota
metadata:
  name: team-a-quota
  namespace: team-a
spec:
  hard:
    requests.cpu: "50"
    requests.memory: 100Gi
  scopeSelector:
    matchExpressions:
    - operator: In
      scopeName: PriorityClass
      values: ["low", "medium"]
```

## Platform Design Patterns

### 1. Platform as a Product

**Principles**:
- Treat developers as customers
- Focus on developer experience
- Measure platform adoption
- Iterate based on feedback
- Provide SLAs for platform services

**Metrics**:
```yaml
# Platform health dashboard
metrics:
  - name: time_to_first_deploy
    target: < 1 hour
    current: 45 minutes

  - name: deployment_frequency
    target: > 10/day per team
    current: 15/day

  - name: platform_availability
    target: 99.9%
    current: 99.95%

  - name: mean_time_to_recovery
    target: < 30 minutes
    current: 20 minutes

  - name: developer_satisfaction
    target: > 8/10
    current: 8.5/10
```

### 2. Progressive Disclosure

**Complexity Layers**:
```
Layer 1 (Simple): One-click deploy
  - Use default everything
  - Minimal configuration
  - Example: `platform deploy my-app`

Layer 2 (Intermediate): Common customization
  - Choose instance size
  - Set scaling parameters
  - Configure domains

Layer 3 (Advanced): Full control
  - Custom networking
  - Advanced security
  - Fine-grained resource control

Layer 4 (Expert): Escape hatches
  - Direct Kubernetes access
  - Custom resources
  - Low-level configuration
```

### 3. Standardization vs Flexibility

**Golden Path (80% use cases)**:
```yaml
# Simple deployment config
apiVersion: platform.example.com/v1
kind: Application
metadata:
  name: my-app
spec:
  image: my-app:v1.0.0
  replicas: 3
  resources:
    size: medium  # Predefined: small/medium/large

  ingress:
    domain: my-app.example.com

  database:
    type: postgres
    size: small
```

**Escape Hatch (20% use cases)**:
```yaml
# Advanced configuration
apiVersion: platform.example.com/v1
kind: Application
metadata:
  name: my-app
spec:
  # ... standard config ...

  advanced:
    customResources:
      - apiVersion: v1
        kind: ConfigMap
        # ... custom resource ...

    podSpec:
      affinity:
        nodeAffinity:
          # ... custom affinity ...
```

## System Design Interview Questions

### Q: Design a platform for 100 teams, 1000+ services

**Requirements**:
- Self-service deployment
- Multi-cloud (AWS + GCP)
- Strong isolation between teams
- Cost visibility
- Compliance (SOC2, HIPAA)

**High-Level Architecture**:
```
┌─────────────────────────────────────────────────────────┐
│              Developer Portal (Backstage)               │
│  - Service catalog                                     │
│  - Self-service provisioning                           │
│  - Documentation                                       │
└─────────────────────┬───────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────┐
│              Control Plane                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │   ArgoCD     │  │  Crossplane  │  │    OPA       │ │
│  │   (GitOps)   │  │  (Infra)     │  │  (Policy)    │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
└─────────────────────┬───────────────────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        │                           │
┌───────▼────────┐          ┌───────▼────────┐
│   AWS Clusters │          │  GCP Clusters  │
│                │          │                │
│  ┌──────────┐ │          │  ┌──────────┐  │
│  │  Prod    │ │          │  │  Prod    │  │
│  └──────────┘ │          │  └──────────┘  │
│  ┌──────────┐ │          │  ┌──────────┐  │
│  │ Staging  │ │          │  │ Staging  │  │
│  └──────────┘ │          │  └──────────┘  │
│  ┌──────────┐ │          │  ┌──────────┐  │
│  │   Dev    │ │          │  │   Dev    │  │
│  └──────────┘ │          │  └──────────┘  │
└────────────────┘          └────────────────┘
```

**Key Design Decisions**:

1. **Team Isolation**:
   - Namespace per team per environment
   - Network policies
   - Resource quotas
   - RBAC

2. **Multi-Cloud Strategy**:
   - Crossplane for unified API
   - Kubernetes as abstraction layer
   - Cloud-specific features via CRDs

3. **Compliance**:
   - Separate clusters for HIPAA workloads
   - Audit logging (Falco, audit2rbac)
   - Policy enforcement (OPA)
   - Encryption at rest and in transit

4. **Cost Management**:
   - Kubecost for visibility
   - Resource quotas
   - Auto-shutdown for dev environments
   - Chargeback reports per team

5. **Observability**:
   - Centralized logging (Loki)
   - Metrics (Prometheus + Thanos)
   - Tracing (Jaeger)
   - Unified dashboards (Grafana)

### Q: How do you migrate teams to a new platform?

**Migration Strategy**:

**Phase 1: Build Core Platform**
```
Week 1-4:
- Set up Kubernetes clusters
- Install core services (GitOps, observability)
- Define golden paths
- Create documentation
```

**Phase 2: Pilot with Early Adopters**
```
Week 5-8:
- Select 2-3 friendly teams
- Migrate their services
- Gather feedback
- Iterate on platform
```

**Phase 3: Gradual Rollout**
```
Week 9-20:
- Migrate teams in waves
- Provide migration support
- Update documentation
- Address pain points
```

**Phase 4: Deprecate Old Platform**
```
Week 21-24:
- Set deprecation date
- Migrate remaining teams
- Sunset old platform
```

**Migration Playbook**:
```markdown
# Service Migration Checklist

## Pre-Migration
- [ ] Service documented
- [ ] Dependencies identified
- [ ] Resource requirements estimated
- [ ] Migration scheduled with team

## Migration
- [ ] Repository created
- [ ] CI/CD pipeline configured
- [ ] Kubernetes manifests created
- [ ] Secrets migrated (Vault)
- [ ] Deployed to dev
- [ ] Integration tests pass
- [ ] Deployed to staging
- [ ] Load testing completed
- [ ] Runbook created

## Post-Migration
- [ ] Deployed to production
- [ ] Monitoring configured
- [ ] Alerts set up
- [ ] Old service decommissioned
- [ ] Team trained
```

## Best Practices

### 1. Start Small, Scale Gradually

Begin with core capabilities:
- Container orchestration (Kubernetes)
- CI/CD (ArgoCD)
- Observability (Prometheus + Grafana)
- Documentation

Add advanced features later:
- Service mesh
- Multi-cluster
- Advanced networking
- FinOps tools

### 2. Measure Everything

**Platform Metrics**:
```yaml
# DORA metrics
deployment_frequency: "per day"
lead_time_for_changes: "hours"
time_to_restore_service: "minutes"
change_failure_rate: "percentage"

# Platform-specific
time_to_first_deploy: "hours"
platform_adoption_rate: "percentage"
self_service_usage: "percentage"
platform_availability: "percentage"
```

### 3. Build for Day 2 Operations

**Operational Excellence**:
- Automated backups
- Disaster recovery plans
- Upgrade automation
- Security patching
- Cost optimization
- Capacity planning

### 4. Documentation is Critical

**Platform Docs Structure**:
```
docs/
├── getting-started/
│   ├── quickstart.md
│   └── first-deployment.md
├── guides/
│   ├── deploying-services.md
│   ├── databases.md
│   ├── monitoring.md
│   └── troubleshooting.md
├── reference/
│   ├── api.md
│   ├── cli.md
│   └── resource-limits.md
└── runbooks/
    ├── incident-response.md
    └── common-issues.md
```

### 5. Provide Escape Hatches

Allow teams to:
- Use custom Kubernetes resources
- Direct kubectl access (with audit)
- Custom integrations
- Opt-out of certain features

## Resources

**Tools**:
- [Backstage](https://backstage.io/) - Developer portal
- [Crossplane](https://crossplane.io/) - Universal control plane
- [Kratix](https://kratix.io/) - Platform framework
- [KubeVela](https://kubevela.io/) - Application delivery platform

**Learning**:
- [Team Topologies (book)](https://teamtopologies.com/)
- [Platform Engineering Guide](https://platformengineering.org/)
- [CNCF Platforms White Paper](https://github.com/cncf/tag-app-delivery/blob/main/platforms-whitepaper/v1/assets/platforms-def-v1.0.pdf)

**Communities**:
- [Platform Engineering Slack](https://platformengineering.org/slack)
- CNCF Platforms Working Group
