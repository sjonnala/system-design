# CI/CD Pipelines

Continuous Integration and Continuous Deployment (CI/CD) automates the software delivery process from code commit to production deployment.

## Core Concepts

### What is CI/CD?

**Continuous Integration (CI)**:
- Automatically build and test code changes
- Merge code frequently (multiple times per day)
- Early detection of integration issues
- Maintain a working main branch

**Continuous Deployment (CD)**:
- Automatically deploy to production
- Every change that passes tests goes live
- Reduces time to market
- Enables rapid feedback

**Continuous Delivery**:
- Automatically deploy to staging
- Manual approval for production
- Production-ready code at all times

### CI/CD Pipeline Stages

```
┌──────────────┐
│  Code Push   │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│    Build     │ ← Compile, package
└──────┬───────┘
       │
       ▼
┌──────────────┐
│     Test     │ ← Unit, integration, e2e
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Security   │ ← SAST, DAST, dependency scan
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Artifact   │ ← Build container, upload
└──────┬───────┘
       │
       ▼
┌──────────────┐
│    Deploy    │ ← Dev → Staging → Prod
└──────────────┘
```

## Major CI/CD Tools

### 1. GitHub Actions

**Overview**:
- Native GitHub integration
- YAML-based workflows
- Huge marketplace of actions
- Free for public repos

**Example Workflow**:
```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  NODE_VERSION: '18'
  REGISTRY: ghcr.io

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v3

      - name: Setup Node.js
        uses: actions/setup-node@v3
        with:
          node-version: ${{ env.NODE_VERSION }}
          cache: 'npm'

      - name: Install dependencies
        run: npm ci

      - name: Run linter
        run: npm run lint

      - name: Run tests
        run: npm test -- --coverage

      - name: Upload coverage
        uses: codecov/codecov-action@v3

  build-docker:
    needs: build
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v3

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v2

      - name: Login to GitHub Container Registry
        uses: docker/login-action@v2
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push
        uses: docker/build-push-action@v4
        with:
          context: .
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ github.repository }}:${{ github.sha }}
            ${{ env.REGISTRY }}/${{ github.repository }}:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

  deploy-staging:
    needs: build-docker
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/develop'

    steps:
      - name: Deploy to staging
        run: |
          # Update Kubernetes deployment
          kubectl set image deployment/app \
            app=${{ env.REGISTRY }}/${{ github.repository }}:${{ github.sha }} \
            -n staging

  deploy-production:
    needs: build-docker
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    environment: production

    steps:
      - name: Deploy to production
        run: |
          kubectl set image deployment/app \
            app=${{ env.REGISTRY }}/${{ github.repository }}:${{ github.sha }} \
            -n production
```

**Reusable Workflows**:
```yaml
# .github/workflows/reusable-build.yml
name: Reusable Build

on:
  workflow_call:
    inputs:
      node-version:
        required: true
        type: string
    outputs:
      artifact-id:
        value: ${{ jobs.build.outputs.artifact-id }}

jobs:
  build:
    runs-on: ubuntu-latest
    outputs:
      artifact-id: ${{ steps.upload.outputs.artifact-id }}

    steps:
      - uses: actions/checkout@v3
      - name: Setup Node
        uses: actions/setup-node@v3
        with:
          node-version: ${{ inputs.node-version }}
      - run: npm ci && npm run build
      - id: upload
        uses: actions/upload-artifact@v3
        with:
          name: build-artifact
          path: dist/

# Use in another workflow
jobs:
  build:
    uses: ./.github/workflows/reusable-build.yml
    with:
      node-version: '18'
```

### 2. GitLab CI/CD

**Overview**:
- Integrated with GitLab
- Powerful pipeline features
- Auto DevOps
- Built-in container registry

**Example `.gitlab-ci.yml`**:
```yaml
stages:
  - build
  - test
  - security
  - deploy

variables:
  DOCKER_DRIVER: overlay2
  IMAGE_TAG: $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA

# Build stage
build:
  stage: build
  image: node:18
  script:
    - npm ci
    - npm run build
  artifacts:
    paths:
      - dist/
    expire_in: 1 hour
  cache:
    paths:
      - node_modules/

# Test stage
unit-test:
  stage: test
  image: node:18
  script:
    - npm ci
    - npm run test:unit -- --coverage
  coverage: '/Statements\s*:\s*(\d+\.?\d*)%/'
  artifacts:
    reports:
      coverage_report:
        coverage_format: cobertura
        path: coverage/cobertura-coverage.xml

integration-test:
  stage: test
  image: node:18
  services:
    - postgres:14
    - redis:7
  variables:
    DATABASE_URL: postgres://user:pass@postgres:5432/test
    REDIS_URL: redis://redis:6379
  script:
    - npm ci
    - npm run test:integration

# Security stage
sast:
  stage: security
  image: returntocorp/semgrep
  script:
    - semgrep --config=auto --json -o sast-report.json
  artifacts:
    reports:
      sast: sast-report.json

dependency-scan:
  stage: security
  image: aquasec/trivy
  script:
    - trivy fs --security-checks vuln --format json -o dependency-report.json .
  artifacts:
    reports:
      dependency_scanning: dependency-report.json

# Build Docker image
docker-build:
  stage: build
  image: docker:24
  services:
    - docker:24-dind
  script:
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
    - docker build -t $IMAGE_TAG .
    - docker push $IMAGE_TAG
  only:
    - main
    - develop

# Deploy stages
deploy-staging:
  stage: deploy
  image: bitnami/kubectl
  script:
    - kubectl config use-context staging
    - kubectl set image deployment/app app=$IMAGE_TAG -n staging
    - kubectl rollout status deployment/app -n staging
  environment:
    name: staging
    url: https://staging.example.com
  only:
    - develop

deploy-production:
  stage: deploy
  image: bitnami/kubectl
  script:
    - kubectl config use-context production
    - kubectl set image deployment/app app=$IMAGE_TAG -n production
    - kubectl rollout status deployment/app -n production
  environment:
    name: production
    url: https://example.com
  when: manual
  only:
    - main
```

### 3. Jenkins

**Overview**:
- Self-hosted
- Highly customizable
- Huge plugin ecosystem
- Groovy-based pipelines

**Jenkinsfile Example**:
```groovy
pipeline {
    agent any

    environment {
        DOCKER_REGISTRY = 'registry.example.com'
        IMAGE_NAME = 'myapp'
        KUBECONFIG = credentials('kubeconfig')
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/company/app.git'
            }
        }

        stage('Build') {
            agent {
                docker {
                    image 'node:18'
                    args '-v $HOME/.npm:/root/.npm'
                }
            }
            steps {
                sh 'npm ci'
                sh 'npm run build'
                stash includes: 'dist/**', name: 'build-artifacts'
            }
        }

        stage('Test') {
            parallel {
                stage('Unit Tests') {
                    steps {
                        sh 'npm run test:unit'
                    }
                }
                stage('Lint') {
                    steps {
                        sh 'npm run lint'
                    }
                }
                stage('Security Scan') {
                    steps {
                        sh 'npm audit --production'
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    docker.build("${DOCKER_REGISTRY}/${IMAGE_NAME}:${BUILD_NUMBER}")
                }
            }
        }

        stage('Push Image') {
            steps {
                script {
                    docker.withRegistry("https://${DOCKER_REGISTRY}", 'docker-credentials') {
                        def image = docker.image("${DOCKER_REGISTRY}/${IMAGE_NAME}:${BUILD_NUMBER}")
                        image.push()
                        image.push('latest')
                    }
                }
            }
        }

        stage('Deploy to Staging') {
            when {
                branch 'develop'
            }
            steps {
                sh '''
                    kubectl set image deployment/app \
                        app=${DOCKER_REGISTRY}/${IMAGE_NAME}:${BUILD_NUMBER} \
                        -n staging
                    kubectl rollout status deployment/app -n staging
                '''
            }
        }

        stage('Deploy to Production') {
            when {
                branch 'main'
            }
            steps {
                input message: 'Deploy to production?', ok: 'Deploy'
                sh '''
                    kubectl set image deployment/app \
                        app=${DOCKER_REGISTRY}/${IMAGE_NAME}:${BUILD_NUMBER} \
                        -n production
                    kubectl rollout status deployment/app -n production
                '''
            }
        }
    }

    post {
        success {
            slackSend channel: '#deployments',
                      color: 'good',
                      message: "Build ${BUILD_NUMBER} succeeded"
        }
        failure {
            slackSend channel: '#deployments',
                      color: 'danger',
                      message: "Build ${BUILD_NUMBER} failed"
        }
        always {
            cleanWs()
        }
    }
}
```

### 4. CircleCI

**Overview**:
- Cloud-based or self-hosted
- Fast builds with caching
- Docker-first approach
- Orbs (reusable config packages)

**Example `.circleci/config.yml`**:
```yaml
version: 2.1

orbs:
  node: circleci/node@5.0
  docker: circleci/docker@2.1
  kubernetes: circleci/kubernetes@1.3

executors:
  node-executor:
    docker:
      - image: cimg/node:18.0

jobs:
  build-and-test:
    executor: node-executor
    steps:
      - checkout
      - restore_cache:
          keys:
            - v1-deps-{{ checksum "package-lock.json" }}
            - v1-deps-
      - run:
          name: Install dependencies
          command: npm ci
      - save_cache:
          key: v1-deps-{{ checksum "package-lock.json" }}
          paths:
            - node_modules
      - run:
          name: Run tests
          command: npm test
      - run:
          name: Run linter
          command: npm run lint
      - persist_to_workspace:
          root: .
          paths:
            - dist

  build-docker:
    executor: docker/docker
    steps:
      - checkout
      - setup_remote_docker
      - docker/check
      - docker/build:
          image: myapp
          tag: ${CIRCLE_SHA1}
      - docker/push:
          image: myapp
          tag: ${CIRCLE_SHA1}

  deploy-staging:
    executor: kubernetes/default
    steps:
      - kubernetes/install-kubectl
      - run:
          name: Deploy to staging
          command: |
            kubectl set image deployment/app \
              app=myapp:${CIRCLE_SHA1} \
              -n staging

workflows:
  build-test-deploy:
    jobs:
      - build-and-test
      - build-docker:
          requires:
            - build-and-test
      - deploy-staging:
          requires:
            - build-docker
          filters:
            branches:
              only: develop
```

## Deployment Strategies

### 1. Blue/Green Deployment

**Concept**: Run two identical environments, switch traffic between them

```
┌─────────────────────────────────────┐
│        Load Balancer                │
└─────────┬───────────────────────────┘
          │
          ├───────────┬────────────┐
          │           │            │
     ┌────▼───┐  ┌───▼────┐  ┌───▼────┐
     │ Blue   │  │ Blue   │  │ Blue   │
     │ v1.0   │  │ v1.0   │  │ v1.0   │ ← Currently serving traffic
     └────────┘  └────────┘  └────────┘

     ┌────────┐  ┌────────┐  ┌────────┐
     │ Green  │  │ Green  │  │ Green  │
     │ v2.0   │  │ v2.0   │  │ v2.0   │ ← New version ready
     └────────┘  └────────┘  └────────┘

After validation, switch LB to Green
```

**Implementation**:
```yaml
# Kubernetes example
apiVersion: v1
kind: Service
metadata:
  name: app-service
spec:
  selector:
    app: myapp
    version: blue  # Switch to 'green' when ready
  ports:
    - port: 80

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app-blue
spec:
  replicas: 3
  selector:
    matchLabels:
      app: myapp
      version: blue
  template:
    metadata:
      labels:
        app: myapp
        version: blue
    spec:
      containers:
      - name: app
        image: myapp:v1.0

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app-green
spec:
  replicas: 3
  selector:
    matchLabels:
      app: myapp
      version: green
  template:
    metadata:
      labels:
        app: myapp
        version: green
    spec:
      containers:
      - name: app
        image: myapp:v2.0
```

### 2. Canary Deployment

**Concept**: Gradually roll out to subset of users

```
Traffic Distribution:

Step 1: 95% → v1.0, 5% → v2.0
Step 2: 80% → v1.0, 20% → v2.0
Step 3: 50% → v1.0, 50% → v2.0
Step 4: 0% → v1.0, 100% → v2.0
```

**Argo Rollouts Example**:
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
      - setWeight: 10
      - pause: {duration: 5m}
      - setWeight: 30
      - pause: {duration: 5m}
      - setWeight: 50
      - pause: {duration: 5m}
      - setWeight: 80
      - pause: {duration: 5m}
  template:
    spec:
      containers:
      - name: app
        image: myapp:v2.0
```

**Flagger (with Istio)**:
```yaml
apiVersion: flagger.app/v1beta1
kind: Canary
metadata:
  name: app-canary
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: app
  service:
    port: 80
  analysis:
    interval: 1m
    threshold: 5
    maxWeight: 50
    stepWeight: 10
    metrics:
    - name: request-success-rate
      thresholdRange:
        min: 99
    - name: request-duration
      thresholdRange:
        max: 500
```

### 3. Rolling Deployment

**Concept**: Gradually replace old instances with new ones

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app
spec:
  replicas: 10
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 2        # Max new pods to create
      maxUnavailable: 1  # Max old pods to terminate
  template:
    spec:
      containers:
      - name: app
        image: myapp:v2.0
```

## CI/CD for Different Architectures

### Monorepo CI/CD

**Challenge**: Only build/test changed services

```yaml
# GitHub Actions with path filtering
name: Monorepo CI

on:
  push:
    paths:
      - 'services/**'

jobs:
  detect-changes:
    runs-on: ubuntu-latest
    outputs:
      services: ${{ steps.filter.outputs.changes }}
    steps:
      - uses: actions/checkout@v3
      - uses: dorny/paths-filter@v2
        id: filter
        with:
          filters: |
            service-a:
              - 'services/service-a/**'
            service-b:
              - 'services/service-b/**'

  build-service:
    needs: detect-changes
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: ${{ fromJSON(needs.detect-changes.outputs.services) }}
    steps:
      - uses: actions/checkout@v3
      - name: Build ${{ matrix.service }}
        run: |
          cd services/${{ matrix.service }}
          docker build -t ${{ matrix.service }}:${{ github.sha }} .
```

### Microservices CI/CD

**Per-Service Pipelines**:
```
Repository Structure:
├── service-a/
│   ├── .gitlab-ci.yml
│   └── Dockerfile
├── service-b/
│   ├── .gitlab-ci.yml
│   └── Dockerfile
└── service-c/
    ├── .gitlab-ci.yml
    └── Dockerfile

Each service has independent pipeline
```

**Orchestrated Deployment**:
```yaml
# Parent pipeline
stages:
  - trigger-builds
  - integration-test
  - deploy

trigger-service-a:
  stage: trigger-builds
  trigger:
    project: company/service-a
    branch: main

trigger-service-b:
  stage: trigger-builds
  trigger:
    project: company/service-b
    branch: main

integration-test:
  stage: integration-test
  script:
    - ./run-integration-tests.sh

deploy-all:
  stage: deploy
  script:
    - helm upgrade --install app ./helm-chart
```

## Best Practices

### 1. Pipeline as Code

**Version Control**:
- Store pipeline config in git
- Review changes via pull requests
- Track pipeline evolution

### 2. Fast Feedback

**Optimize Build Times**:
```yaml
# Cache dependencies
- restore_cache:
    keys:
      - v1-deps-{{ checksum "package-lock.json" }}

# Parallel execution
jobs:
  parallel-tests:
    strategy:
      matrix:
        test-suite: [unit, integration, e2e]
    steps:
      - run: npm run test:${{ matrix.test-suite }}

# Docker layer caching
- name: Build with cache
  uses: docker/build-push-action@v4
  with:
    cache-from: type=gha
    cache-to: type=gha,mode=max
```

### 3. Security in CI/CD

**Security Scanning**:
```yaml
security-scan:
  stage: security
  parallel:
    matrix:
      - SCANNER: [sast, dependency, container, secrets]
  script:
    - |
      case $SCANNER in
        sast)
          semgrep --config=auto
          ;;
        dependency)
          npm audit
          ;;
        container)
          trivy image myapp:latest
          ;;
        secrets)
          gitleaks detect
          ;;
      esac
```

**Secrets Management**:
```yaml
# Use secrets, never hardcode
- name: Deploy
  env:
    API_KEY: ${{ secrets.API_KEY }}
    DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
  run: ./deploy.sh
```

### 4. Observability

**Pipeline Metrics**:
- Build duration
- Success/failure rate
- Deploy frequency
- Lead time for changes
- Mean time to recovery (MTTR)

**Notifications**:
```yaml
# Slack notifications
- name: Notify Slack
  if: always()
  uses: slackapi/slack-github-action@v1
  with:
    payload: |
      {
        "text": "Build ${{ job.status }}: ${{ github.workflow }}",
        "blocks": [
          {
            "type": "section",
            "text": {
              "type": "mrkdwn",
              "text": "Build *${{ job.status }}*\nWorkflow: ${{ github.workflow }}\nCommit: ${{ github.sha }}"
            }
          }
        ]
      }
```

## System Design Interview Questions

### Q: Design a CI/CD pipeline for 500 microservices

**Requirements**:
- Fast feedback (< 10 min for most builds)
- Cost-effective
- Scalable
- Reliable

**Design**:
```
┌─────────────────────────────────────────────────────────┐
│                    Code Repository                      │
│              (Monorepo with 500 services)               │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│              Change Detection Layer                     │
│  - Detect which services changed                        │
│  - Determine dependencies                               │
│  - Generate build matrix                                │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│              Parallel Build Workers                     │
│  - Kubernetes-based build runners                       │
│  - Auto-scaling based on queue depth                    │
│  - Per-service resource allocation                      │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│              Artifact Registry                          │
│  - Container images (Harbor/ECR)                        │
│  - Build caching (BuildKit/Kaniko)                      │
│  - Vulnerability scanning                               │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│              Deployment Orchestrator                    │
│  - ArgoCD for GitOps                                    │
│  - Progressive delivery (Flagger)                       │
│  - Rollback automation                                  │
└─────────────────────────────────────────────────────────┘
```

**Key Optimizations**:
- **Incremental builds**: Only build changed services
- **Dependency graph**: Build dependent services in order
- **Shared base images**: Reduce build times
- **Distributed caching**: Share build artifacts
- **Resource quotas**: Prevent resource exhaustion

### Q: How do you handle database migrations in CI/CD?

**Strategies**:

1. **Forward-compatible migrations**:
```sql
-- Step 1: Add new column (nullable)
ALTER TABLE users ADD COLUMN email VARCHAR(255);

-- Deploy app code that writes to both fields

-- Step 2: Backfill data
UPDATE users SET email = old_email WHERE email IS NULL;

-- Step 3: Make column NOT NULL
ALTER TABLE users ALTER COLUMN email SET NOT NULL;

-- Step 4: Remove old column
ALTER TABLE users DROP COLUMN old_email;
```

2. **Blue/Green with schema versioning**:
- Blue environment: v1 schema
- Green environment: v2 schema (compatible with v1)
- Migrate schema, then switch traffic

3. **Migration tools integration**:
```yaml
deploy:
  script:
    # Run migrations before deployment
    - flyway migrate -url=$DB_URL -user=$DB_USER -password=$DB_PASS
    # Deploy application
    - kubectl apply -f k8s/
```

### Q: How do you ensure zero-downtime deployments?

**Techniques**:

1. **Health checks**:
```yaml
livenessProbe:
  httpGet:
    path: /health
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /ready
    port: 8080
  initialDelaySeconds: 5
  periodSeconds: 5
```

2. **Graceful shutdown**:
```javascript
process.on('SIGTERM', async () => {
  console.log('SIGTERM received, starting graceful shutdown');

  // Stop accepting new requests
  server.close(() => {
    console.log('HTTP server closed');
  });

  // Finish processing existing requests (with timeout)
  await Promise.race([
    finishExistingRequests(),
    timeout(30000)
  ]);

  process.exit(0);
});
```

3. **Pre-stop hooks**:
```yaml
lifecycle:
  preStop:
    exec:
      command: ["/bin/sh", "-c", "sleep 10"]
```

## Resources

**Tools**:
- [GitHub Actions](https://docs.github.com/en/actions)
- [GitLab CI/CD](https://docs.gitlab.com/ee/ci/)
- [Jenkins](https://www.jenkins.io/doc/)
- [CircleCI](https://circleci.com/docs/)
- [ArgoCD](https://argo-cd.readthedocs.io/)
- [Tekton](https://tekton.dev/)

**Learning**:
- [CI/CD Best Practices](https://www.thoughtworks.com/insights/articles/ci-cd-best-practices)
- [DORA Metrics](https://cloud.google.com/blog/products/devops-sre/using-the-four-keys-to-measure-your-devops-performance)
