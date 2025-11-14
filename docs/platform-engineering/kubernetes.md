# Kubernetes (K8s)

## What is Kubernetes?

**Container orchestration platform** for automating deployment, scaling, and management of containerized applications.

## Core Architecture

```
┌─────────────────────────────────────────┐
│           Control Plane                 │
│  ┌──────────┐  ┌──────────┐            │
│  │API Server│  │Scheduler │            │
│  └──────────┘  └──────────┘            │
│  ┌──────────┐  ┌──────────┐            │
│  │Controller│  │  etcd    │            │
│  │ Manager  │  │(key-value)            │
│  └──────────┘  └──────────┘            │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│          Worker Nodes                   │
│  ┌────────────────────────────────┐    │
│  │  Pod  │ Pod │ Pod │ Pod │ Pod  │    │
│  └────────────────────────────────┘    │
│  ┌──────────┐  ┌──────────┐           │
│  │ kubelet  │  │kube-proxy│           │
│  └──────────┘  └──────────┘           │
└─────────────────────────────────────────┘
```

### Control Plane Components

**API Server**:
- Frontend for K8s control plane
- All communication goes through API server
- Validates and processes requests
- Updates etcd

**etcd**:
- Distributed key-value store
- Stores cluster state
- Source of truth for K8s
- Uses Raft consensus

**Scheduler**:
- Assigns pods to nodes
- Considers: resource requirements, constraints, affinity
- Node selection algorithm

**Controller Manager**:
- Runs controller loops
- Node controller (monitors nodes)
- Replication controller (maintains pod count)
- Endpoints controller (populates endpoints)
- Service account controller

### Worker Node Components

**kubelet**:
- Agent on each node
- Ensures containers running in pods
- Reports node status to API server
- Mounts volumes, downloads secrets

**kube-proxy**:
- Network proxy on each node
- Maintains network rules
- Enables Service abstraction
- Load balances traffic

**Container Runtime**:
- Runs containers (Docker, containerd, CRI-O)
- Pulls images
- Starts/stops containers

---

## Core Concepts

### Pods

**Smallest deployable unit** in K8s

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: my-app
spec:
  containers:
  - name: app
    image: nginx:latest
    ports:
    - containerPort: 80
    resources:
      requests:
        memory: "64Mi"
        cpu: "250m"
      limits:
        memory: "128Mi"
        cpu: "500m"
```

**Key Points**:
- One or more containers
- Shared network namespace (localhost)
- Shared volumes
- Scheduled together on same node
- Ephemeral (can be deleted/recreated)

### Deployments

**Declarative updates for Pods**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx:1.21
        ports:
        - containerPort: 80
```

**Features**:
- Declarative updates (rolling updates)
- Rollback capability
- Scaling (manual or auto)
- Self-healing (recreates failed pods)

**Rolling Update**:
```
Old ReplicaSet: 3 pods → 2 → 1 → 0
New ReplicaSet: 0 pods → 1 → 2 → 3

Zero downtime deployment
```

### Services

**Stable endpoint for pods**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: nginx-service
spec:
  type: LoadBalancer
  selector:
    app: nginx
  ports:
  - port: 80
    targetPort: 80
```

**Service Types**:

**ClusterIP** (default):
```
Internal IP, accessible only within cluster
Use: Internal microservices communication
```

**NodePort**:
```
Exposes service on each node's IP at static port
Use: Development, simple external access
```

**LoadBalancer**:
```
Cloud load balancer (AWS ELB, GCP LB)
Use: Production external access
```

**ExternalName**:
```
Maps to external DNS name
Use: Access external services
```

### ConfigMaps & Secrets

**ConfigMap** (non-sensitive configuration):
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  database_url: "postgres://db:5432"
  log_level: "info"
```

**Secret** (sensitive data):
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: app-secret
type: Opaque
data:
  password: cGFzc3dvcmQxMjM=  # base64 encoded
```

**Usage in Pod**:
```yaml
spec:
  containers:
  - name: app
    envFrom:
    - configMapRef:
        name: app-config
    - secretRef:
        name: app-secret
```

### Volumes

**Persistent storage for pods**

**Types**:

**emptyDir** (temporary):
```yaml
volumes:
- name: cache
  emptyDir: {}
```

**PersistentVolumeClaim** (persistent):
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
  storageClassName: fast-ssd
```

### Namespaces

**Virtual clusters within K8s cluster**

```bash
kubectl create namespace development
kubectl create namespace production
```

**Use Cases**:
- Environment separation (dev, staging, prod)
- Team isolation
- Resource quotas per namespace
- RBAC per namespace

---

## Scaling

### Horizontal Pod Autoscaler (HPA)

**Auto-scale based on metrics**:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: nginx-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: nginx-deployment
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

**How it works**:
```
CPU > 70% → Add pods
CPU < 70% → Remove pods (cooldown period)
```

### Vertical Pod Autoscaler (VPA)

**Auto-adjust resource requests/limits**

Useful when pod needs more CPU/memory, not more replicas

### Cluster Autoscaler

**Add/remove nodes based on demand**

```
Pods pending (no resources) → Add nodes
Nodes underutilized → Remove nodes
```

---

## Networking

### Pod-to-Pod Communication

**Kubernetes Network Model**:
- Every pod gets unique IP
- Pods can communicate without NAT
- Flat network space

**CNI Plugins**: Calico, Flannel, Weave, Cilium

### Service Discovery

**DNS-based**:
```
Service: nginx-service in namespace default

DNS: nginx-service.default.svc.cluster.local
Short: nginx-service
```

**Environment Variables**:
```
NGINX_SERVICE_HOST=10.0.0.5
NGINX_SERVICE_PORT=80
```

### Ingress

**HTTP/HTTPS routing to services**:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: my-ingress
spec:
  rules:
  - host: myapp.example.com
    http:
      paths:
      - path: /api
        pathType: Prefix
        backend:
          service:
            name: api-service
            port:
              number: 80
      - path: /web
        pathType: Prefix
        backend:
          service:
            name: web-service
            port:
              number: 80
```

**Ingress Controllers**: Nginx, Traefik, HAProxy, AWS ALB

---

## Security

### RBAC (Role-Based Access Control)

**Role** (namespace-scoped):
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  namespace: development
  name: pod-reader
rules:
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list"]
```

**RoleBinding**:
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: read-pods
  namespace: development
subjects:
- kind: User
  name: jane
  apiGroup: rbac.authorization.k8s.io
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io
```

### Network Policies

**Control pod-to-pod traffic**:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: api-network-policy
spec:
  podSelector:
    matchLabels:
      app: api
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: frontend
    ports:
    - protocol: TCP
      port: 8080
```

### Pod Security

**Security Context**:
```yaml
spec:
  securityContext:
    runAsNonRoot: true
    runAsUser: 1000
  containers:
  - name: app
    securityContext:
      allowPrivilegeEscalation: false
      readOnlyRootFilesystem: true
```

---

## Interview Q&A

**Q: "Design a multi-tenant Kubernetes platform for 100+ teams"**

**Answer**:
```
Architecture:

1. Isolation Strategy:
   - Namespace per team
   - Network policies (isolate traffic)
   - Resource quotas (prevent noisy neighbors)
   - Pod security policies

2. Resource Management:
   ResourceQuota per namespace:
   apiVersion: v1
   kind: ResourceQuota
   metadata:
     name: team-quota
   spec:
     hard:
       requests.cpu: "100"
       requests.memory: 200Gi
       pods: "50"

3. Access Control:
   - RBAC per namespace
   - Teams can only access their namespace
   - Platform team has cluster-admin

4. Network Isolation:
   - Default deny-all network policy
   - Teams whitelist allowed traffic
   - Service mesh (Istio) for mTLS

5. Observability:
   - Prometheus per namespace
   - Central aggregation (Thanos)
   - Team-specific dashboards
   - Distributed tracing (Jaeger)

6. CI/CD:
   - GitOps (ArgoCD) per team
   - Automated deployment pipelines
   - Canary deployments

7. Security:
   - Image scanning (Trivy)
   - Pod security admission
   - Secrets management (External Secrets Operator + Vault)

Challenges:
- Cluster upgrade coordination
- Cost attribution per team
- Preventing cluster resource exhaustion
- Supporting different deployment patterns
```

**Q: "How do you handle a pod that keeps crashing?"**

**Answer**:
```
Debugging Process:

1. Check Pod Status:
   kubectl describe pod <pod-name>
   kubectl logs <pod-name>
   kubectl logs <pod-name> --previous (crashed container)

2. Common Issues:
   - OOMKilled → Memory limit too low
   - CrashLoopBackOff → Application error
   - ImagePullBackOff → Wrong image or auth
   - Pending → No resources available

3. Investigation:
   - Check resource requests/limits
   - Review application logs
   - Check liveness/readiness probes
   - Verify dependencies (DB, external services)

4. Solutions:
   - Increase memory/CPU limits
   - Fix application code
   - Adjust probe timing
   - Add init containers for dependencies

5. Prevention:
   - Proper resource sizing
   - Health checks configured correctly
   - Graceful shutdown handling
   - Testing in staging environment
```

---

## Key Takeaways

1. **Declarative**: Desired state, not imperative commands
2. **Self-Healing**: Automatically recovers from failures
3. **Scalable**: Horizontal scaling built-in
4. **Portable**: Runs on any cloud or on-prem
5. **Complex**: Requires expertise to operate at scale

## Further Reading

- Kubernetes Documentation
- "Kubernetes Up & Running" book
- "Kubernetes Patterns" book
- CNCF Cloud Native Landscape
- Platform Engineering Blog Posts
