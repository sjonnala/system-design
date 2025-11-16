# GitOps

GitOps is a paradigm for infrastructure and application management where Git is the single source of truth for declarative infrastructure and applications.

## Core Concepts

### What is GitOps?

**Definition**: An operational framework that applies DevOps practices (version control, collaboration, CI/CD) to infrastructure automation.

**Key Principles**:
1. **Declarative**: System state described declaratively
2. **Versioned**: State stored in Git (immutable, versioned)
3. **Pulled Automatically**: Software agents automatically pull desired state
4. **Continuously Reconciled**: Software agents ensure actual state matches desired state

### GitOps vs Traditional CI/CD

**Traditional CI/CD**:
```
┌────────┐     ┌────────┐     ┌─────────┐     ┌────────────┐
│  Git   │────►│  CI    │────►│ Build   │────►│   Deploy   │
│  Push  │     │Pipeline│     │ Artifact│     │ (kubectl)  │
└────────┘     └────────┘     └─────────┘     └────────────┘
                                                     │
                                                     ▼
                                              ┌────────────┐
                                              │ Kubernetes │
                                              └────────────┘
```

**GitOps**:
```
┌────────────┐                              ┌────────────────┐
│   Config   │                              │   Kubernetes   │
│    Git     │◄─────────Pull────────────────│                │
│ Repository │                              │  ┌──────────┐  │
└────────────┘                              │  │ GitOps   │  │
                                            │  │ Operator │  │
                                            │  │(ArgoCD)  │  │
                                            │  └──────────┘  │
                                            └────────────────┘
                                                 │
                                                 ▼
                                            Continuous
                                            Reconciliation
```

## Major GitOps Tools

### 1. ArgoCD

**Overview**:
- Kubernetes-native GitOps tool
- Declarative CD
- Multi-cluster support
- Rich UI

**Architecture**:
```
┌─────────────────────────────────────────────────────────┐
│                     ArgoCD                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │
│  │ API Server   │  │  Repo Server │  │ Application  │ │
│  │              │  │  (Git sync)  │  │  Controller  │ │
│  └──────────────┘  └──────────────┘  └──────────────┘ │
└────────────┬──────────────────────────────────┬────────┘
             │                                  │
             ▼                                  ▼
      ┌────────────┐                    ┌────────────┐
      │  Git Repo  │                    │ Kubernetes │
      │            │                    │  Cluster   │
      └────────────┘                    └────────────┘
```

**Installation**:
```bash
# Install ArgoCD
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Access ArgoCD UI
kubectl port-forward svc/argocd-server -n argocd 8080:443

# Get admin password
kubectl get secret argocd-initial-admin-secret -n argocd -o jsonpath="{.data.password}" | base64 -d
```

**Application Definition**:
```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: my-app
  namespace: argocd
spec:
  # Source repository
  source:
    repoURL: https://github.com/company/app-config
    targetRevision: main
    path: k8s/overlays/production

  # Destination cluster and namespace
  destination:
    server: https://kubernetes.default.svc
    namespace: production

  # Sync policy
  syncPolicy:
    automated:
      prune: true      # Delete resources not in Git
      selfHeal: true   # Automatically sync when drift detected
      allowEmpty: false
    syncOptions:
      - CreateNamespace=true
      - PruneLast=true

    retry:
      limit: 5
      backoff:
        duration: 5s
        factor: 2
        maxDuration: 3m

  # Health assessment
  ignoreDifferences:
  - group: apps
    kind: Deployment
    jsonPointers:
    - /spec/replicas  # Ignore HPA-managed replicas
```

**Kustomize Example**:
```yaml
# Repository structure
app-config/
├── base/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── kustomization.yaml
└── overlays/
    ├── development/
    │   ├── kustomization.yaml
    │   └── replica-count.yaml
    ├── staging/
    │   ├── kustomization.yaml
    │   └── replica-count.yaml
    └── production/
        ├── kustomization.yaml
        └── replica-count.yaml

# base/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - deployment.yaml
  - service.yaml

# overlays/production/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

bases:
  - ../../base

replicas:
  - name: app
    count: 5

images:
  - name: app
    newTag: v1.2.3

configMapGenerator:
  - name: app-config
    literals:
      - ENV=production
      - LOG_LEVEL=info
```

**Helm Example**:
```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: nginx
  namespace: argocd
spec:
  source:
    repoURL: https://charts.bitnami.com/bitnami
    chart: nginx
    targetRevision: 13.2.0
    helm:
      parameters:
        - name: service.type
          value: LoadBalancer
        - name: replicaCount
          value: "3"
      values: |
        image:
          tag: 1.23.3
        resources:
          requests:
            memory: 256Mi
            cpu: 100m

  destination:
    server: https://kubernetes.default.svc
    namespace: default
```

**Multi-Cluster Setup**:
```yaml
# Add cluster
argocd cluster add prod-cluster --name production

# ApplicationSet for multi-cluster
apiVersion: argoproj.io/v1alpha1
kind: ApplicationSet
metadata:
  name: multi-cluster-app
spec:
  generators:
  - list:
      elements:
      - cluster: dev
        url: https://dev.k8s.local
      - cluster: staging
        url: https://staging.k8s.local
      - cluster: prod
        url: https://prod.k8s.local

  template:
    metadata:
      name: 'app-{{cluster}}'
    spec:
      source:
        repoURL: https://github.com/company/app-config
        targetRevision: main
        path: 'overlays/{{cluster}}'
      destination:
        server: '{{url}}'
        namespace: app
```

**Progressive Delivery with Rollouts**:
```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: app-rollout
spec:
  replicas: 10
  strategy:
    canary:
      steps:
      - setWeight: 20
      - pause: {duration: 1m}
      - setWeight: 40
      - pause: {duration: 1m}
      - setWeight: 60
      - pause: {duration: 1m}
      - setWeight: 80
      - pause: {duration: 1m}

      analysis:
        templates:
        - templateName: success-rate
        args:
        - name: service-name
          value: app-service

---
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: success-rate
spec:
  args:
  - name: service-name
  metrics:
  - name: success-rate
    interval: 1m
    successCondition: result >= 0.95
    provider:
      prometheus:
        address: http://prometheus:9090
        query: |
          sum(rate(http_requests_total{service="{{args.service-name}}",status_code!~"5.."}[5m]))
          /
          sum(rate(http_requests_total{service="{{args.service-name}}"}[5m]))
```

### 2. Flux

**Overview**:
- CNCF project
- GitOps toolkit
- Composable
- Supports multi-tenancy

**Architecture**:
```
┌─────────────────────────────────────────────────────┐
│                    Flux Components                  │
│  ┌────────────┐  ┌────────────┐  ┌──────────────┐ │
│  │  Source    │  │Kustomize   │  │    Helm      │ │
│  │ Controller │  │Controller  │  │  Controller  │ │
│  └────────────┘  └────────────┘  └──────────────┘ │
│  ┌────────────┐  ┌────────────┐                   │
│  │Notification│  │  Image     │                   │
│  │ Controller │  │ Reflector  │                   │
│  └────────────┘  └────────────┘                   │
└─────────────────────────────────────────────────────┘
```

**Installation**:
```bash
# Install Flux CLI
curl -s https://fluxcd.io/install.sh | sudo bash

# Bootstrap Flux
flux bootstrap github \
  --owner=myorg \
  --repository=fleet-infra \
  --branch=main \
  --path=clusters/production \
  --personal
```

**GitRepository Source**:
```yaml
apiVersion: source.toolkit.fluxcd.io/v1beta2
kind: GitRepository
metadata:
  name: app-config
  namespace: flux-system
spec:
  interval: 1m
  url: https://github.com/company/app-config
  ref:
    branch: main
  secretRef:
    name: git-credentials
```

**Kustomization**:
```yaml
apiVersion: kustomize.toolkit.fluxcd.io/v1beta2
kind: Kustomization
metadata:
  name: app
  namespace: flux-system
spec:
  interval: 10m
  path: ./k8s/production
  prune: true
  sourceRef:
    kind: GitRepository
    name: app-config
  healthChecks:
    - apiVersion: apps/v1
      kind: Deployment
      name: app
      namespace: production
```

**HelmRelease**:
```yaml
apiVersion: helm.toolkit.fluxcd.io/v2beta1
kind: HelmRelease
metadata:
  name: nginx
  namespace: default
spec:
  interval: 5m
  chart:
    spec:
      chart: nginx
      version: '13.2.x'
      sourceRef:
        kind: HelmRepository
        name: bitnami
        namespace: flux-system
  values:
    replicaCount: 3
    service:
      type: LoadBalancer

---
apiVersion: source.toolkit.fluxcd.io/v1beta2
kind: HelmRepository
metadata:
  name: bitnami
  namespace: flux-system
spec:
  interval: 24h
  url: https://charts.bitnami.com/bitnami
```

**Image Automation**:
```yaml
# Image repository
apiVersion: image.toolkit.fluxcd.io/v1beta1
kind: ImageRepository
metadata:
  name: app
  namespace: flux-system
spec:
  image: ghcr.io/company/app
  interval: 1m

---
# Image policy
apiVersion: image.toolkit.fluxcd.io/v1beta1
kind: ImagePolicy
metadata:
  name: app
  namespace: flux-system
spec:
  imageRepositoryRef:
    name: app
  policy:
    semver:
      range: 1.x.x

---
# Image update automation
apiVersion: image.toolkit.fluxcd.io/v1beta1
kind: ImageUpdateAutomation
metadata:
  name: app
  namespace: flux-system
spec:
  interval: 1m
  sourceRef:
    kind: GitRepository
    name: app-config
  git:
    checkout:
      ref:
        branch: main
    commit:
      author:
        email: fluxcd@company.com
        name: Flux
      messageTemplate: 'Update image to {{range .Updated.Images}}{{println .}}{{end}}'
    push:
      branch: main
  update:
    path: ./k8s/production
    strategy: Setters
```

### 3. Jenkins X

**Overview**:
- Kubernetes-native CI/CD
- GitOps for applications
- Preview environments
- Tekton-based

**Preview Environments**:
```yaml
# Automatically created for PRs
apiVersion: jenkins.io/v1
kind: Environment
metadata:
  name: preview-pr-123
spec:
  kind: Preview
  pullRequestURL: https://github.com/company/app/pull/123
  source:
    ref: PR-123
    url: https://github.com/company/app
```

## GitOps Repository Patterns

### Pattern 1: Monorepo

**Structure**:
```
gitops-repo/
├── apps/
│   ├── app-a/
│   │   ├── base/
│   │   └── overlays/
│   ├── app-b/
│   │   ├── base/
│   │   └── overlays/
│   └── app-c/
├── infrastructure/
│   ├── cert-manager/
│   ├── ingress-nginx/
│   └── prometheus/
└── clusters/
    ├── dev/
    ├── staging/
    └── production/
```

**Pros**:
- Single source of truth
- Easy cross-app changes
- Simplified access control

**Cons**:
- Large repository
- Potential conflicts
- Slower clones

### Pattern 2: Repo per App

**Structure**:
```
app-a-config/
├── base/
└── overlays/

app-b-config/
├── base/
└── overlays/

infra-config/
├── cert-manager/
└── prometheus/
```

**Pros**:
- Clear ownership
- Smaller repositories
- Isolated changes

**Cons**:
- More repos to manage
- Cross-app changes harder
- Complex RBAC

### Pattern 3: Environment Branches

**Structure**:
```
main (production)
├── dev (development)
└── staging

Each branch has full config
```

**Pros**:
- Easy promotion (merge)
- Clear environment separation

**Cons**:
- Merge conflicts
- Hard to see differences
- Not recommended for GitOps

### Recommended: Overlay Pattern

```
k8s/
├── base/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── kustomization.yaml
└── overlays/
    ├── dev/
    │   ├── kustomization.yaml
    │   └── patches/
    ├── staging/
    │   ├── kustomization.yaml
    │   └── patches/
    └── production/
        ├── kustomization.yaml
        └── patches/
```

## GitOps Workflows

### Application Deployment Workflow

**1. Developer Flow**:
```
┌─────────────────────────────────────────────────────┐
│ 1. Developer commits code                           │
│    git commit -m "Add feature X"                    │
└────────────┬────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────┐
│ 2. CI builds and pushes image                       │
│    docker build -t app:v1.2.3                       │
│    docker push app:v1.2.3                           │
└────────────┬────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────┐
│ 3. CI updates config repo (optional automation)     │
│    git clone config-repo                            │
│    kustomize edit set image app:v1.2.3              │
│    git commit && git push                           │
└────────────┬────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────┐
│ 4. ArgoCD/Flux detects change                       │
│    Pulls new config from Git                        │
└────────────┬────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────┐
│ 5. Applies to Kubernetes                            │
│    kubectl apply -k overlays/production             │
└─────────────────────────────────────────────────────┘
```

**2. CI/CD Integration**:
```yaml
# GitHub Actions
name: Build and Update Config

on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Build image
        run: |
          docker build -t myapp:${{ github.sha }} .
          docker push myapp:${{ github.sha }}

      - name: Update config repo
        run: |
          git clone https://github.com/company/app-config
          cd app-config
          cd k8s/overlays/production
          kustomize edit set image myapp:${{ github.sha }}
          git commit -am "Update image to ${{ github.sha }}"
          git push
```

### Infrastructure Changes

**1. Propose Change**:
```bash
# Create branch
git checkout -b add-monitoring

# Add Prometheus
cat <<EOF > infrastructure/prometheus/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - https://github.com/prometheus-operator/kube-prometheus
EOF

git add .
git commit -m "Add Prometheus monitoring"
git push origin add-monitoring
```

**2. Review & Merge**:
```bash
# Create PR
gh pr create --title "Add Prometheus monitoring"

# Review, approve, merge
gh pr merge add-monitoring --squash
```

**3. Automatic Deployment**:
- ArgoCD/Flux detects merge to main
- Syncs Prometheus to cluster
- Monitors health

## Security in GitOps

### Secret Management

**Option 1: Sealed Secrets**:
```bash
# Install Sealed Secrets
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.18.0/controller.yaml

# Create sealed secret
echo -n mypassword | kubectl create secret generic mysecret \
  --dry-run=client \
  --from-file=password=/dev/stdin \
  -o yaml | \
  kubeseal -o yaml > sealed-secret.yaml

# Commit sealed secret to Git
git add sealed-secret.yaml
git commit -m "Add sealed secret"
```

**Sealed Secret**:
```yaml
apiVersion: bitnami.com/v1alpha1
kind: SealedSecret
metadata:
  name: mysecret
  namespace: default
spec:
  encryptedData:
    password: AgBvN8P...encrypted...data
```

**Option 2: External Secrets Operator**:
```yaml
# ExternalSecret references secret in AWS Secrets Manager
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: app-secret
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: SecretStore
  target:
    name: app-secret
    creationPolicy: Owner
  data:
  - secretKey: password
    remoteRef:
      key: prod/app/password

---
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: aws-secrets-manager
spec:
  provider:
    aws:
      service: SecretsManager
      region: us-west-2
      auth:
        jwt:
          serviceAccountRef:
            name: external-secrets
```

**Option 3: HashiCorp Vault**:
```yaml
# Vault Agent Injector
apiVersion: v1
kind: Pod
metadata:
  name: app
  annotations:
    vault.hashicorp.com/agent-inject: "true"
    vault.hashicorp.com/role: "app"
    vault.hashicorp.com/agent-inject-secret-config: "secret/data/app/config"
spec:
  serviceAccountName: app
  containers:
  - name: app
    image: app:latest
```

### RBAC for GitOps

**ArgoCD RBAC**:
```yaml
# argocd-rbac-cm ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: argocd-rbac-cm
  namespace: argocd
data:
  policy.csv: |
    # Developer role
    p, role:developer, applications, get, */*, allow
    p, role:developer, applications, sync, */*, allow

    # Admin role
    p, role:admin, applications, *, */*, allow
    p, role:admin, clusters, *, *, allow

    # Group mappings
    g, developers, role:developer
    g, platform-team, role:admin
```

## System Design Interview Questions

### Q: Design a GitOps platform for 50 teams, 500 services

**Requirements**:
- Multi-cluster (dev, staging, prod)
- Self-service for teams
- Guardrails and policies
- Disaster recovery
- Audit trail

**Design**:
```
┌─────────────────────────────────────────────────────────┐
│              Central Config Repository                  │
│                                                         │
│  ├── infrastructure/     (Platform team)                │
│  │   ├── cert-manager/                                  │
│  │   ├── ingress/                                       │
│  │   └── monitoring/                                    │
│  │                                                      │
│  └── teams/              (Team ownership)               │
│      ├── team-a/                                        │
│      │   ├── app-1/                                     │
│      │   └── app-2/                                     │
│      ├── team-b/                                        │
│      └── team-c/                                        │
└────────────┬────────────────────────────────────────────┘
             │
             │ ArgoCD App-of-Apps Pattern
             │
   ┌─────────┼─────────┬─────────┐
   │         │         │         │
   ▼         ▼         ▼         ▼
┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐
│ Dev │  │Stage│  │ Prod│  │ DR  │
│ K8s │  │ K8s │  │ K8s │  │ K8s │
└─────┘  └─────┘  └─────┘  └─────┘
```

**App-of-Apps Pattern**:
```yaml
# Root application
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: root-app
  namespace: argocd
spec:
  source:
    repoURL: https://github.com/company/gitops
    path: teams
    directory:
      recurse: true
  destination:
    server: https://kubernetes.default.svc
    namespace: argocd
  syncPolicy:
    automated:
      prune: true
      selfHeal: true

# Each team has ApplicationSet
# teams/team-a/applicationset.yaml
apiVersion: argoproj.io/v1alpha1
kind: ApplicationSet
metadata:
  name: team-a-apps
spec:
  generators:
  - git:
      repoURL: https://github.com/company/gitops
      revision: main
      directories:
      - path: teams/team-a/*

  template:
    metadata:
      name: '{{path.basename}}'
    spec:
      source:
        repoURL: https://github.com/company/gitops
        path: '{{path}}'
      destination:
        server: https://kubernetes.default.svc
        namespace: 'team-a-{{path.basename}}'
```

**Policy Enforcement (OPA Gatekeeper)**:
```yaml
# Constraint Template
apiVersion: templates.gatekeeper.sh/v1beta1
kind: ConstraintTemplate
metadata:
  name: k8srequiredlabels
spec:
  crd:
    spec:
      names:
        kind: K8sRequiredLabels
      validation:
        openAPIV3Schema:
          properties:
            labels:
              type: array
              items:
                type: string

  targets:
    - target: admission.k8s.gatekeeper.sh
      rego: |
        package k8srequiredlabels

        violation[{"msg": msg, "details": {"missing_labels": missing}}] {
          provided := {label | input.review.object.metadata.labels[label]}
          required := {label | label := input.parameters.labels[_]}
          missing := required - provided
          count(missing) > 0
          msg := sprintf("Required labels missing: %v", [missing])
        }

---
# Constraint
apiVersion: constraints.gatekeeper.sh/v1beta1
kind: K8sRequiredLabels
metadata:
  name: require-team-label
spec:
  match:
    kinds:
      - apiGroups: ["apps"]
        kinds: ["Deployment"]
  parameters:
    labels:
      - "team"
      - "app"
      - "environment"
```

### Q: How do you handle GitOps for secrets?

**Best Practices**:

1. **Never commit plain secrets to Git**
2. **Use encrypted secrets** (Sealed Secrets, SOPS)
3. **Use external secret stores** (Vault, AWS Secrets Manager)
4. **Rotate secrets regularly**
5. **Audit secret access**

**Comparison**:

| Solution | Pros | Cons |
|----------|------|------|
| Sealed Secrets | Simple, Git-native | Keys in cluster, rotation complex |
| External Secrets | Centralized, rotation | External dependency |
| Vault | Enterprise-ready | Complex setup |
| SOPS | Flexible, multi-cloud | Manual workflow |

## Best Practices

### 1. Separate App Source from Config

```
app-repo/              config-repo/
├── src/               ├── base/
├── Dockerfile         └── overlays/
└── .github/
```

### 2. Use Kustomize or Helm, Not Both

**Kustomize**: Better for simpler apps, GitOps-native

**Helm**: Better for complex apps with many parameters

### 3. Implement Progressive Delivery

```yaml
# Argo Rollouts for gradual rollout
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: app
spec:
  strategy:
    canary:
      steps:
      - setWeight: 20
      - pause: {duration: 5m}
      - analysis:
          templates:
          - templateName: success-rate
```

### 4. Enable Notifications

```yaml
# ArgoCD notifications
apiVersion: v1
kind: ConfigMap
metadata:
  name: argocd-notifications-cm
data:
  service.slack: |
    token: $slack-token

  trigger.on-deployed: |
    - when: app.status.operationState.phase in ['Succeeded']
      send: [app-deployed]

  template.app-deployed: |
    message: |
      Application {{.app.metadata.name}} deployed successfully.
    slack:
      attachments: |
        [{
          "color": "good",
          "title": "{{.app.metadata.name}}",
          "title_link": "{{.context.argocdUrl}}/applications/{{.app.metadata.name}}"
        }]
```

### 5. Monitor Drift

```bash
# ArgoCD CLI
argocd app diff my-app

# Automated drift detection
argocd app sync my-app --dry-run
```

## Resources

**Official Docs**:
- [ArgoCD Documentation](https://argo-cd.readthedocs.io/)
- [Flux Documentation](https://fluxcd.io/docs/)
- [GitOps Principles](https://opengitops.dev/)

**Books**:
- GitOps and Kubernetes (Manning)
- Managing Kubernetes (O'Reilly)

**Tools**:
- [Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets)
- [External Secrets Operator](https://external-secrets.io/)
- [Kustomize](https://kustomize.io/)
- [Helm](https://helm.sh/)
