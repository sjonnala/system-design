# Apache Airflow Deep Dive: Workflow Management & DAGs

## Contents

- [Apache Airflow Deep Dive: Workflow Management & DAGs](#apache-airflow-deep-dive-workflow-management--dags)
    - [Core Mental Model](#core-mental-model)
    - [What is Airflow?](#what-is-airflow)
    - [DAGs: Directed Acyclic Graphs](#dags-directed-acyclic-graphs)
    - [Operators & Tasks](#operators--tasks)
    - [Scheduling & Execution](#scheduling--execution)
    - [Dependencies & Control Flow](#dependencies--control-flow)
    - [Monitoring & Debugging](#monitoring--debugging)
    - [Best Practices](#best-practices)
    - [Production Patterns](#production-patterns)
    - [Airflow vs Alternatives](#airflow-vs-alternatives)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
    - [MIND MAP: APACHE AIRFLOW](#mind-map-apache-airflow)
    - [EMOTIONAL ANCHORS](#emotional-anchors)

## Core Mental Model

```text
Airflow = Cron on steroids
          Workflow orchestration platform

The Problem:
┌────────────────────────────────────────────────────────────┐
│ Complex data pipelines have:                               │
│   - Multiple steps (ETL, ML, reporting)                    │
│   - Dependencies (step 2 needs step 1 output)             │
│   - Scheduling (run daily at 2am)                          │
│   - Monitoring (did it succeed?)                           │
│   - Retries (handle transient failures)                    │
│                                                             │
│ Managing with cron scripts = NIGHTMARE                     │
└────────────────────────────────────────────────────────────┘

Cron approach (BAD):
  0 2 * * * /scripts/extract.sh
  0 3 * * * /scripts/transform.sh  # Hope extract finished!
  0 4 * * * /scripts/load.sh       # Hope transform finished!

  Problems:
    ✗ No dependency management
    ✗ No failure handling
    ✗ No monitoring
    ✗ No retry logic
    ✗ Hard to debug

Airflow approach (GOOD):
  DAG (Directed Acyclic Graph):
    extract → transform → load

  Benefits:
    ✓ Explicit dependencies
    ✓ Automatic retry on failure
    ✓ Web UI for monitoring
    ✓ Backfilling (rerun historical data)
    ✓ Alerting (email/Slack on failure)

                  CRON                        AIRFLOW
            ┌──────────────┐           ┌──────────────┐
            │ Script 1     │           │   extract    │
            │ → Wait       │           │      ↓       │
            │ Script 2     │           │  transform   │
            │ → Wait       │           │      ↓       │
            │ Script 3     │           │    load      │
            │              │           │              │
            │ (Hope it     │           │ (Automatic   │
            │  works!)     │           │  execution)  │
            └──────────────┘           └──────────────┘
```

**Airflow's Core Concepts:**
```
1. DAG (Directed Acyclic Graph)
   - Workflow definition
   - Tasks + Dependencies
   - Schedule (e.g., daily at 2am)

2. Operator
   - Single task in workflow
   - BashOperator, PythonOperator, SparkOperator, etc.

3. Task
   - Instance of operator
   - Represents one unit of work

4. Task Instance
   - Specific run of task
   - Has state (running, success, failed)

5. Schedule
   - When to run DAG
   - Cron expression or preset

Example:
  DAG: "daily_etl"
  Tasks: [extract_data, transform_data, load_data]
  Schedule: "0 2 * * *" (daily at 2am)
  Dependencies: extract → transform → load
```

---

## What is Airflow?

🎓 **PROFESSOR**: Airflow is a platform to programmatically author, schedule, and monitor workflows.

### History & Purpose

```
Created by: Airbnb (2014)
Open-sourced: 2015
Apache: 2016

Problem Airflow solves:
────────────────────────
  At Airbnb:
    - 1000s of ETL jobs
    - Complex dependencies
    - Multiple teams
    - Cron chaos (fragile, hard to debug)

  Solution:
    - Define workflows as code (Python)
    - Visualize dependencies (DAG graph)
    - Monitor execution (Web UI)
    - Handle failures (retries, alerts)

Adoption:
─────────
  Companies using Airflow:
    - Airbnb (creator)
    - Uber
    - Netflix
    - Spotify
    - Twitter
    - Lyft
    - 1000s more

  Use cases:
    - ETL pipelines
    - ML training pipelines
    - Data warehousing
    - Report generation
    - Infrastructure automation
```

### Architecture

```
Airflow Components:
──────────────────

┌────────────────────────────────────────────────────────┐
│                     WEB SERVER                         │
│  - UI for viewing DAGs, tasks, logs                    │
│  - Trigger manual runs                                 │
│  - http://localhost:8080                               │
└───────────────────────┬────────────────────────────────┘
                        │
┌───────────────────────┴────────────────────────────────┐
│                     SCHEDULER                          │
│  - Reads DAG files                                     │
│  - Schedules tasks                                     │
│  - Monitors task states                                │
│  - Heart of Airflow                                    │
└───────────────────────┬────────────────────────────────┘
                        │
┌───────────────────────┴────────────────────────────────┐
│                    METADATA DB                         │
│  - Stores DAG/task definitions                         │
│  - Task states, logs, variables                        │
│  - PostgreSQL, MySQL (NOT SQLite in production!)      │
└───────────────────────┬────────────────────────────────┘
                        │
┌───────────────────────┴────────────────────────────────┐
│                     EXECUTOR                           │
│  - Executes tasks                                      │
│  - Types:                                              │
│    * SequentialExecutor (dev only, one task at a time) │
│    * LocalExecutor (multiple tasks, same machine)      │
│    * CeleryExecutor (distributed, multiple machines)   │
│    * KubernetesExecutor (one pod per task)             │
└───────────────────────┬────────────────────────────────┘
                        │
         ┌──────────────┼──────────────┐
         ↓              ↓              ↓
    ┌────────┐     ┌────────┐     ┌────────┐
    │ WORKER │     │ WORKER │     │ WORKER │
    │ Task 1 │     │ Task 2 │     │ Task 3 │
    └────────┘     └────────┘     └────────┘

Workflow:
─────────
  1. User writes DAG file (Python) → /dags folder
  2. Scheduler scans /dags folder (every N seconds)
  3. Scheduler parses DAG, creates task instances
  4. Scheduler checks schedule (is it time to run?)
  5. Scheduler sends tasks to executor
  6. Executor runs tasks on workers
  7. Workers update task state in metadata DB
  8. Web UI displays task states (reads from DB)
```

---

## DAGs: Directed Acyclic Graphs

🏗️ **ARCHITECT**: DAGs are the heart of Airflow.

### What is a DAG?

```
DAG = Directed Acyclic Graph
─────────────────────────────

Directed: Edges have direction (task A → task B)
Acyclic:  No cycles (can't go A → B → C → A)
Graph:    Nodes (tasks) + Edges (dependencies)

Why DAG?
────────
  Represents workflow with dependencies
  Guarantees: Can always find execution order (topological sort)

Example:
         ┌──────────┐
         │ extract  │
         └─────┬────┘
               │
               ↓
         ┌─────────┐
         │transform│
         └────┬────┘
              │
              ↓
         ┌────────┐
         │  load  │
         └────────┘

  Execution order: extract → transform → load
  Guaranteed to finish (no cycles!)

Invalid (has cycle):
         ┌──────────┐
         │ task A   │
         └─────┬────┘
               │
               ↓
         ┌─────────┐
    ┌──→│ task B  │
    │   └────┬────┘
    │        │
    │        ↓
    │   ┌────────┐
    └───│ task C │
        └────────┘

  Can't determine execution order!
  A → B → C → A → ... (infinite loop)
```

### Creating a DAG

```python
"""
Basic DAG definition
"""

from airflow import DAG
from airflow.operators.bash import BashOperator
from airflow.operators.python import PythonOperator
from datetime import datetime, timedelta

# Default arguments (apply to all tasks)
default_args = {
    'owner': 'data_team',
    'depends_on_past': False,  # Don't depend on previous run
    'email': ['alerts@company.com'],
    'email_on_failure': True,
    'email_on_retry': False,
    'retries': 3,
    'retry_delay': timedelta(minutes=5),
}

# Define DAG
dag = DAG(
    'daily_etl_pipeline',
    default_args=default_args,
    description='Daily ETL pipeline',
    schedule_interval='0 2 * * *',  # Daily at 2am
    start_date=datetime(2024, 1, 1),
    catchup=False,  # Don't backfill past runs
    tags=['etl', 'daily'],
)

# Define tasks
extract_task = BashOperator(
    task_id='extract_data',
    bash_command='python /scripts/extract.py',
    dag=dag,
)

transform_task = PythonOperator(
    task_id='transform_data',
    python_callable=transform_function,
    dag=dag,
)

load_task = BashOperator(
    task_id='load_data',
    bash_command='python /scripts/load.py',
    dag=dag,
)

# Define dependencies
extract_task >> transform_task >> load_task
# Equivalent to:
# extract_task.set_downstream(transform_task)
# transform_task.set_downstream(load_task)

"""
This creates:
  extract_data → transform_data → load_data

When executed:
  1. extract_data runs first
  2. If extract succeeds, transform_data runs
  3. If transform succeeds, load_data runs
  4. If any fails, retry 3 times (5 min apart)
  5. If still fails, send email alert
"""
```

### DAG Parameters Explained

```python
"""
Understanding DAG parameters
"""

dag = DAG(
    # DAG ID (unique identifier)
    dag_id='my_pipeline',

    # Default args for all tasks
    default_args=default_args,

    # Human-readable description
    description='Process customer data',

    # When to run
    schedule_interval='0 2 * * *',
    # Options:
    #   '0 2 * * *'        - Cron expression (daily at 2am)
    #   '@daily'           - Preset (midnight)
    #   '@hourly'          - Every hour
    #   '@weekly'          - Sunday midnight
    #   timedelta(hours=1) - Every hour
    #   None               - Manual trigger only

    # When DAG starts being scheduled
    start_date=datetime(2024, 1, 1),
    # Note: First run is at start_date + schedule_interval

    # When DAG stops being scheduled (optional)
    end_date=datetime(2024, 12, 31),

    # Backfill past runs?
    catchup=False,
    # True:  Run all missed runs from start_date to now
    # False: Only run from now onwards

    # Maximum active runs
    max_active_runs=1,
    # Prevent multiple DAG runs simultaneously

    # Tags for filtering in UI
    tags=['etl', 'critical', 'daily'],

    # Default task execution timeout
    dagrun_timeout=timedelta(hours=4),
    # Kill DAG if not finished in 4 hours

    # SLA (Service Level Agreement)
    sla_miss_callback=notify_sla_miss,
    # Call function if task takes too long
)

"""
Common schedule_interval examples:
  '0 * * * *'    - Every hour (top of hour)
  '*/15 * * * *' - Every 15 minutes
  '0 0 * * *'    - Daily at midnight
  '0 2 * * *'    - Daily at 2am
  '0 2 * * 0'    - Weekly on Sunday at 2am
  '0 2 1 * *'    - Monthly on 1st at 2am
"""
```

---

## Operators & Tasks

🏗️ **ARCHITECT**: Operators define what work to do.

### Built-in Operators

```python
from airflow.operators.bash import BashOperator
from airflow.operators.python import PythonOperator
from airflow.operators.empty import EmptyOperator
from airflow.operators.email import EmailOperator
from airflow.providers.postgres.operators.postgres import PostgresOperator
from airflow.providers.http.operators.http import SimpleHttpOperator

"""
1. BashOperator: Run bash commands
"""
bash_task = BashOperator(
    task_id='run_script',
    bash_command='python /scripts/process.py --date {{ ds }}',
    # {{ ds }} is template variable (execution date)
)

"""
2. PythonOperator: Run Python functions
"""
def my_function(**context):
    execution_date = context['execution_date']
    print(f"Running for {execution_date}")
    return "Success"

python_task = PythonOperator(
    task_id='process_data',
    python_callable=my_function,
    provide_context=True,  # Pass context to function
)

"""
3. EmptyOperator: Placeholder (for grouping)
"""
start = EmptyOperator(task_id='start')
end = EmptyOperator(task_id='end')

"""
4. EmailOperator: Send emails
"""
email_task = EmailOperator(
    task_id='send_report',
    to='team@company.com',
    subject='Daily Report {{ ds }}',
    html_content='<p>Report attached</p>',
)

"""
5. PostgresOperator: Run SQL
"""
sql_task = PostgresOperator(
    task_id='create_table',
    postgres_conn_id='my_postgres',
    sql='''
        CREATE TABLE IF NOT EXISTS users (
            id INT PRIMARY KEY,
            name VARCHAR(100)
        );
    ''',
)

"""
6. SimpleHttpOperator: HTTP requests
"""
http_task = SimpleHttpOperator(
    task_id='call_api',
    http_conn_id='my_api',
    endpoint='/process',
    method='POST',
    data='{"date": "{{ ds }}"}',
)

"""
7. Sensors: Wait for condition
"""
from airflow.sensors.filesystem import FileSensor

wait_for_file = FileSensor(
    task_id='wait_for_input',
    filepath='/data/input/{{ ds }}.csv',
    poke_interval=60,  # Check every 60 seconds
    timeout=3600,      # Give up after 1 hour
)

"""
8. Provider Operators (many available)
"""
from airflow.providers.apache.spark.operators.spark_submit import SparkSubmitOperator
from airflow.providers.amazon.aws.operators.s3 import S3CreateBucketOperator
from airflow.providers.google.cloud.operators.bigquery import BigQueryInsertJobOperator

spark_task = SparkSubmitOperator(
    task_id='run_spark_job',
    application='/scripts/spark_job.py',
    conn_id='spark_default',
    conf={'spark.executor.memory': '4g'},
)
```

### Task Dependencies

```python
"""
Different ways to define dependencies
"""

# Method 1: Bitshift operators (most common)
task1 >> task2 >> task3  # Sequential: 1 → 2 → 3

# Method 2: List for fan-out/fan-in
task1 >> [task2, task3, task4] >> task5
# Parallel: 1 → (2, 3, 4) → 5

# Method 3: Explicit methods
task1.set_downstream(task2)
task2.set_upstream(task1)

# Method 4: Chain utility
from airflow.models.baseoperator import chain

chain(task1, task2, task3, task4)  # 1 → 2 → 3 → 4

# Complex example: Diamond pattern
start = EmptyOperator(task_id='start')
task_a = BashOperator(task_id='task_a', bash_command='echo A')
task_b = BashOperator(task_id='task_b', bash_command='echo B')
task_c = BashOperator(task_id='task_c', bash_command='echo C')
task_d = BashOperator(task_id='task_d', bash_command='echo D')
end = EmptyOperator(task_id='end')

start >> [task_a, task_b]  # Fan out
task_a >> task_c
task_b >> task_c  # Both A and B must complete before C
task_c >> task_d
task_d >> end

"""
Visual:
       start
       ┌─┴─┐
       ↓   ↓
       A   B
       └─┬─┘
         ↓
         C
         ↓
         D
         ↓
       end
"""
```

### Passing Data Between Tasks (XCom)

```python
"""
XCom = Cross-Communication
  Share data between tasks
"""

from airflow.operators.python import PythonOperator

def extract(**context):
    # Extract data
    data = {'count': 100, 'date': '2024-01-01'}

    # Push to XCom
    context['task_instance'].xcom_push(key='extracted_data', value=data)
    # Or simply return (automatically pushed with key 'return_value')
    return data

def transform(**context):
    # Pull from XCom
    ti = context['task_instance']
    data = ti.xcom_pull(task_ids='extract_task', key='return_value')

    # Transform
    data['count'] = data['count'] * 2

    return data

def load(**context):
    ti = context['task_instance']
    data = ti.xcom_pull(task_ids='transform_task')

    print(f"Loading {data['count']} records for {data['date']}")

extract_task = PythonOperator(
    task_id='extract_task',
    python_callable=extract,
)

transform_task = PythonOperator(
    task_id='transform_task',
    python_callable=transform,
)

load_task = PythonOperator(
    task_id='load_task',
    python_callable=load,
)

extract_task >> transform_task >> load_task

"""
WARNING: XCom stored in metadata DB
  - Max size: ~48KB (depends on DB)
  - NOT for large data (use S3/HDFS instead)

For large data:
  extract: Write to S3, XCom push S3 path
  transform: XCom pull S3 path, read from S3, transform, write to S3
  load: XCom pull S3 path, read from S3
"""
```

---

## Scheduling & Execution

🎓 **PROFESSOR**: Understanding Airflow's execution model is critical.

### Execution Date vs Run Date

```
CONFUSING CONCEPT: execution_date
──────────────────────────────────

execution_date ≠ when task actually runs!

execution_date = logical date of data being processed

Example:
  DAG: schedule_interval='@daily'
  start_date: 2024-01-01

  Run 1:
    - execution_date: 2024-01-01 (data date)
    - Actually runs: 2024-01-02 00:00 (next day!)
    - Processes data: 2024-01-01 (full day)

  Run 2:
    - execution_date: 2024-01-02
    - Actually runs: 2024-01-03 00:00
    - Processes data: 2024-01-02

Why?
  Ensures complete data for period
  Daily job at midnight processes PREVIOUS day's data

Visual timeline:
  ┌──────────┬──────────┬──────────┬──────────┐
  │ Jan 1    │ Jan 2    │ Jan 3    │ Jan 4    │
  │          │          │          │          │
  │ [Data]   │ [Data]   │ [Data]   │ [Data]   │
  │          │          │          │          │
  │          │ Run 1    │ Run 2    │ Run 3    │
  │          │ (exec    │ (exec    │ (exec    │
  │          │  date:   │  date:   │  date:   │
  │          │  Jan 1)  │  Jan 2)  │  Jan 3)  │
  └──────────┴──────────┴──────────┴──────────┘

Template variables:
  {{ ds }}               - execution_date (YYYY-MM-DD)
  {{ execution_date }}   - full datetime
  {{ next_ds }}          - next execution_date
  {{ prev_ds }}          - previous execution_date
```

### Backfilling

```python
"""
Backfilling: Run DAG for historical dates
"""

# DAG with catchup=True
dag = DAG(
    'backfill_example',
    start_date=datetime(2024, 1, 1),
    schedule_interval='@daily',
    catchup=True,  # Run all missed runs!
)

# If deployed on 2024-01-10:
# Airflow runs:
#   2024-01-01 → 2024-01-02
#   2024-01-02 → 2024-01-03
#   ...
#   2024-01-09 → 2024-01-10
# Total: 9 runs!

# To avoid (recommended for new DAGs):
dag = DAG(
    'no_backfill',
    start_date=datetime(2024, 1, 1),
    schedule_interval='@daily',
    catchup=False,  # Only run from now onwards
)

"""
Manual backfill (CLI):
  airflow dags backfill \\
    --start-date 2024-01-01 \\
    --end-date 2024-01-10 \\
    my_dag

This manually runs DAG for specified date range
Useful for reprocessing data after bug fix
"""
```

### Task States

```
Task Instance States:
─────────────────────

┌──────────────────────────────────────────────────────┐
│ LIFECYCLE                                            │
├──────────────────────────────────────────────────────┤
│                                                      │
│  None → Scheduled → Queued → Running → Success      │
│            │           │        │         │         │
│            │           │        │         └→ Failed  │
│            │           │        │                    │
│            │           │        └→ Retry (then loop)│
│            │           │                             │
│            │           └→ Upstream Failed            │
│            │                                         │
│            └→ Skipped                                │
│                                                      │
└──────────────────────────────────────────────────────┘

State Descriptions:
  None:            Not yet scheduled
  Scheduled:       Scheduled to run (waiting for execution)
  Queued:          Waiting for executor slot
  Running:         Currently executing
  Success:         Completed successfully
  Failed:          Failed after retries
  Retry:           Failed, will retry
  Skipped:         Skipped (e.g., branch condition)
  Upstream Failed: Parent task failed
  Up for Retry:    Waiting to retry

Colors in UI:
  Green:  Success
  Red:    Failed
  Yellow: Running
  Orange: Retry/Queued
  Pink:   Upstream Failed
  Gray:   Skipped
```

---

## Dependencies & Control Flow

🏗️ **ARCHITECT**: Advanced dependency patterns.

### Branching

```python
"""
Branch based on condition
"""

from airflow.operators.python import BranchPythonOperator

def choose_branch(**context):
    execution_date = context['execution_date']

    # Branch logic
    if execution_date.weekday() == 0:  # Monday
        return 'full_load_task'  # Task ID to run
    else:
        return 'incremental_load_task'

branch_task = BranchPythonOperator(
    task_id='branch',
    python_callable=choose_branch,
)

full_load = BashOperator(
    task_id='full_load_task',
    bash_command='python /scripts/full_load.py',
)

incremental_load = BashOperator(
    task_id='incremental_load_task',
    bash_command='python /scripts/incremental_load.py',
)

branch_task >> [full_load, incremental_load]

"""
Execution:
  Monday:     branch → full_load (incremental_load skipped)
  Other days: branch → incremental_load (full_load skipped)
"""
```

### Trigger Rules

```python
"""
Trigger rules: When should task run?
"""

from airflow.utils.trigger_rule import TriggerRule

# Default: all_success (all parents succeeded)
task1 = BashOperator(
    task_id='task1',
    bash_command='echo 1',
    trigger_rule=TriggerRule.ALL_SUCCESS,  # Default
)

# Run if at least one parent succeeded
task2 = BashOperator(
    task_id='task2',
    bash_command='echo 2',
    trigger_rule=TriggerRule.ONE_SUCCESS,
)

# Run if all parents failed
task3 = BashOperator(
    task_id='task3',
    bash_command='echo 3',
    trigger_rule=TriggerRule.ALL_FAILED,
)

# Run regardless of parent status (cleanup tasks)
cleanup = BashOperator(
    task_id='cleanup',
    bash_command='rm -rf /tmp/*',
    trigger_rule=TriggerRule.ALL_DONE,  # Always run
)

"""
Trigger Rules:
  ALL_SUCCESS:      All parents succeeded (default)
  ALL_FAILED:       All parents failed
  ALL_DONE:         All parents finished (success or failed)
  ONE_SUCCESS:      At least one parent succeeded
  ONE_FAILED:       At least one parent failed
  NONE_FAILED:      No parent failed (success or skipped)
  NONE_SKIPPED:     No parent skipped
"""

# Example: Always cleanup
start >> [taskA, taskB, taskC] >> cleanup
# cleanup runs even if taskA, taskB, or taskC fail
```

### Task Groups

```python
"""
Task Groups: Organize tasks in UI
"""

from airflow.utils.task_group import TaskGroup

with DAG('grouped_tasks', ...):
    start = EmptyOperator(task_id='start')

    with TaskGroup('extract_group') as extract:
        extract_users = BashOperator(task_id='users', ...)
        extract_orders = BashOperator(task_id='orders', ...)
        extract_products = BashOperator(task_id='products', ...)

    with TaskGroup('transform_group') as transform:
        transform_users = BashOperator(task_id='users', ...)
        transform_orders = BashOperator(task_id='orders', ...)

    load = BashOperator(task_id='load', ...)
    end = EmptyOperator(task_id='end')

    start >> extract >> transform >> load >> end

"""
In UI, tasks grouped:
  start
    ↓
  extract_group
    ├─ extract_group.users
    ├─ extract_group.orders
    └─ extract_group.products
    ↓
  transform_group
    ├─ transform_group.users
    └─ transform_group.orders
    ↓
  load
    ↓
  end

Benefits:
  - Cleaner UI (collapse groups)
  - Logical organization
  - Reusable groups
"""
```

---

## Monitoring & Debugging

🏗️ **ARCHITECT**: Production operations for Airflow.

### Web UI Features

```
Airflow Web UI (http://localhost:8080):
───────────────────────────────────────

1. DAGs List
   - All DAGs with status
   - Toggle DAG on/off
   - Trigger manual run
   - View schedule

2. DAG Graph View
   - Visual of task dependencies
   - Task states (colors)
   - Click task for details

3. Tree View
   - Historical runs (rows)
   - Tasks (columns)
   - Quickly spot failures

4. Gantt Chart
   - Task execution timeline
   - Identify bottlenecks
   - See parallel execution

5. Task Logs
   - stdout/stderr for each task
   - Critical for debugging

6. Code View
   - View DAG source code
   - Ensure correct version deployed

7. Admin
   - Connections (DB, APIs, etc.)
   - Variables (config)
   - Pools (resource limits)
```

### Logging

```python
"""
Logging in tasks
"""

import logging

def my_task(**context):
    # Get logger
    logger = logging.getLogger("airflow.task")

    logger.info("Task started")
    logger.debug("Debug information")
    logger.warning("Warning message")
    logger.error("Error occurred")

    try:
        # Do work
        result = process_data()
        logger.info(f"Processed {result} records")
    except Exception as e:
        logger.error(f"Failed: {e}")
        raise

"""
Logs visible in:
  - Web UI (Task → Logs)
  - Files: /logs/dag_id/task_id/execution_date/attempt.log

Production: Ship logs to centralized system
  - ELK stack (Elasticsearch, Logstash, Kibana)
  - Splunk
  - CloudWatch (AWS)
  - Stackdriver (GCP)
"""
```

### Alerting

```python
"""
Alerts on failure
"""

from airflow.operators.email import EmailOperator

# Method 1: Email on task failure (in default_args)
default_args = {
    'email': ['oncall@company.com'],
    'email_on_failure': True,
    'email_on_retry': False,
}

# Method 2: Custom callback
def failure_callback(context):
    task_instance = context['task_instance']
    exception = context.get('exception')

    message = f"""
    Task {task_instance.task_id} failed!
    DAG: {task_instance.dag_id}
    Execution Date: {task_instance.execution_date}
    Log: {task_instance.log_url}
    Exception: {exception}
    """

    # Send to Slack
    send_slack_alert(message)

    # Send to PagerDuty
    trigger_pagerduty(task_instance)

task = BashOperator(
    task_id='critical_task',
    bash_command='...',
    on_failure_callback=failure_callback,
)

# Method 3: SLA (Service Level Agreement)
task = BashOperator(
    task_id='time_sensitive_task',
    bash_command='...',
    sla=timedelta(hours=2),  # Must complete in 2 hours
)

def sla_miss_callback(dag, task_list, blocking_task_list, slas, blocking_tis):
    print(f"SLA missed for tasks: {task_list}")
    # Alert on-call

dag = DAG(
    'sla_dag',
    sla_miss_callback=sla_miss_callback,
    ...
)
```

### Debugging Failed Tasks

```python
"""
Common debugging steps
"""

# 1. Check logs
# Web UI → DAG → Task → Logs
# Look for error messages, stack traces

# 2. Check XCom (data passed between tasks)
# Web UI → DAG → Task → XCom

# 3. Reproduce locally
# CLI: airflow tasks test dag_id task_id execution_date
# Example:
# airflow tasks test daily_etl extract_data 2024-01-01

# 4. Check environment
# Variables, connections, resources

# 5. Enable debug logging
import logging
logging.basicConfig(level=logging.DEBUG)

# 6. Add print statements
def my_task(**context):
    print(f"Context: {context}")
    print(f"Execution date: {context['execution_date']}")
    # ... rest of task

# 7. Check task retries
# How many times did it retry?
# Did it succeed after retry?

# 8. Check upstream tasks
# Did parent tasks succeed?
# Did they produce expected output?
```

---

## Best Practices

🎓 **PROFESSOR**: Production-grade Airflow usage.

### Do's

```python
"""
1. Idempotent tasks
   Running task multiple times = same result
"""
# BAD: Append to table (duplicate data on retry)
def bad_load():
    run_query("INSERT INTO table SELECT * FROM staging")

# GOOD: Replace data (safe to retry)
def good_load():
    run_query("DELETE FROM table WHERE date = '{{ ds }}'")
    run_query("INSERT INTO table SELECT * FROM staging WHERE date = '{{ ds }}'")

"""
2. Atomic operations
   All-or-nothing
"""
# BAD: Multiple steps, can fail halfway
def bad_process():
    step1()  # Succeeds
    step2()  # Fails ← Partial state!

# GOOD: Transactional
def good_process():
    with transaction:
        step1()
        step2()
    # Both succeed or both rollback

"""
3. Use connections (not hardcoded)
"""
# BAD: Hardcoded credentials
def bad_connect():
    conn = psycopg2.connect(
        host='db.example.com',
        user='admin',
        password='secret123'  # BAD!
    )

# GOOD: Use Airflow connections
from airflow.hooks.postgres_hook import PostgresHook

def good_connect():
    hook = PostgresHook(postgres_conn_id='my_postgres')
    conn = hook.get_conn()

"""
4. Use Variables for config
"""
# BAD: Hardcoded config
BATCH_SIZE = 1000

# GOOD: Variables (can change without redeploying)
from airflow.models import Variable

BATCH_SIZE = Variable.get("batch_size", default_var=1000)

"""
5. Small, focused tasks
   Many small tasks > Few large tasks
"""
# BAD: One giant task
def bad_etl():
    extract_users()
    extract_orders()
    extract_products()
    transform_users()
    transform_orders()
    load_all()

# GOOD: Separate tasks
extract_users_task >> transform_users_task >> load_users_task
extract_orders_task >> transform_orders_task >> load_orders_task
# Better parallelism, easier debugging

"""
6. Use sensors for waiting
"""
# BAD: Sleep in task (wastes resources)
def bad_wait():
    while not file_exists('/data/input.csv'):
        time.sleep(60)
    process_file()

# GOOD: Use sensor
from airflow.sensors.filesystem import FileSensor

wait_for_file = FileSensor(
    task_id='wait_for_input',
    filepath='/data/input.csv',
    poke_interval=60,
    mode='reschedule',  # Free up worker slot while waiting
)

"""
7. Set timeouts
   Prevent stuck tasks
"""
task = BashOperator(
    task_id='long_running',
    bash_command='...',
    execution_timeout=timedelta(hours=2),  # Kill after 2 hours
)
```

### Don'ts

```python
"""
1. DON'T do heavy computation in DAG file
"""
# BAD: Expensive operation at DAG parse time
df = pd.read_csv('/large_file.csv')  # Runs every scheduler cycle!
tasks = [create_task(row) for row in df.itertuples()]

# GOOD: Heavy work in task
def load_and_process():
    df = pd.read_csv('/large_file.csv')  # Runs only when task executes
    process(df)

"""
2. DON'T use datetime.now()
"""
# BAD: Non-deterministic
dag = DAG(
    'bad_dag',
    start_date=datetime.now(),  # Different on each parse!
)

# GOOD: Fixed date
dag = DAG(
    'good_dag',
    start_date=datetime(2024, 1, 1),  # Deterministic
)

"""
3. DON'T use top-level code
"""
# BAD: Runs at every DAG parse
result = expensive_api_call()  # Called every 30 seconds!

# GOOD: Inside task
def my_task():
    result = expensive_api_call()  # Called only when task runs

"""
4. DON'T pass large data via XCom
"""
# BAD: Store 1GB DataFrame in XCom
def bad_extract():
    df = load_large_data()  # 1GB
    return df  # Stored in metadata DB (BAD!)

# GOOD: Store data externally
def good_extract():
    df = load_large_data()
    s3_path = 's3://bucket/data.parquet'
    df.to_parquet(s3_path)
    return s3_path  # Only path in XCom (small!)

"""
5. DON'T create dynamic DAGs at runtime
"""
# BAD: Create tasks in loop at parse time
for i in range(1000):
    task = BashOperator(task_id=f'task_{i}', ...)  # 1000 tasks!

# GOOD: Use parameters
def process_batch(batch_id):
    for i in range(batch_size):
        process_item(batch_id, i)

task = PythonOperator(
    task_id='process',
    python_callable=process_batch,
    op_args=[{{ params.batch_id }}],
)
```

---

## Production Patterns

🏗️ **ARCHITECT**: Real-world Airflow patterns.

### Pattern 1: Data Lake ETL

```python
"""
Daily ETL: S3 → Spark → Redshift
"""

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.providers.amazon.aws.transfers.s3_to_redshift import S3ToRedshiftOperator
from airflow.providers.apache.spark.operators.spark_submit import SparkSubmitOperator

dag = DAG(
    'daily_data_lake_etl',
    schedule_interval='0 2 * * *',
    start_date=datetime(2024, 1, 1),
    catchup=False,
)

# 1. Check for new S3 files
check_files = PythonOperator(
    task_id='check_s3_files',
    python_callable=check_s3_partition,
    op_args=['s3://data-lake/raw/{{ ds }}'],
)

# 2. Run Spark job to transform
transform = SparkSubmitOperator(
    task_id='spark_transform',
    application='/scripts/transform.py',
    application_args=['--date', '{{ ds }}'],
    conf={
        'spark.executor.memory': '8g',
        'spark.executor.cores': '4',
    },
)

# 3. Load to Redshift
load = S3ToRedshiftOperator(
    task_id='load_redshift',
    schema='public',
    table='daily_metrics',
    s3_bucket='data-lake',
    s3_key='processed/{{ ds }}/metrics.parquet',
    copy_options=['FORMAT AS PARQUET'],
)

# 4. Run quality checks
quality_check = PythonOperator(
    task_id='data_quality_check',
    python_callable=run_quality_checks,
    op_args=['public.daily_metrics', '{{ ds }}'],
)

# 5. Send notification
notify = EmailOperator(
    task_id='send_notification',
    to='data-team@company.com',
    subject='Daily ETL Complete - {{ ds }}',
    html_content='ETL completed successfully for {{ ds }}',
)

check_files >> transform >> load >> quality_check >> notify
```

### Pattern 2: ML Training Pipeline

```python
"""
Weekly ML model training
"""

dag = DAG(
    'weekly_ml_training',
    schedule_interval='0 2 * * 0',  # Sunday at 2am
    start_date=datetime(2024, 1, 1),
)

# 1. Extract features
extract_features = SparkSubmitOperator(
    task_id='extract_features',
    application='/ml/feature_engineering.py',
)

# 2. Train model
train_model = PythonOperator(
    task_id='train_model',
    python_callable=train_and_evaluate,
)

# 3. Validate model
validate = PythonOperator(
    task_id='validate_model',
    python_callable=validate_model_performance,
)

# 4. Branch: Deploy if good
def check_model_quality(**context):
    metrics = context['task_instance'].xcom_pull(task_ids='validate_model')
    if metrics['accuracy'] > 0.95:
        return 'deploy_model'
    else:
        return 'alert_failure'

branch = BranchPythonOperator(
    task_id='branch',
    python_callable=check_model_quality,
)

# 5a. Deploy (if good)
deploy = BashOperator(
    task_id='deploy_model',
    bash_command='kubectl apply -f /ml/deployment.yaml',
)

# 5b. Alert (if bad)
alert = EmailOperator(
    task_id='alert_failure',
    to='ml-team@company.com',
    subject='Model Training Failed',
    html_content='Model quality below threshold',
)

extract_features >> train_model >> validate >> branch
branch >> [deploy, alert]
```

### Pattern 3: Sensor-Based Pipeline

```python
"""
Process data as it arrives
"""

from airflow.sensors.s3_key_sensor import S3KeySensor

dag = DAG(
    'process_on_arrival',
    schedule_interval='@hourly',  # Check every hour
    start_date=datetime(2024, 1, 1),
)

# 1. Wait for file
wait_for_file = S3KeySensor(
    task_id='wait_for_file',
    bucket_name='uploads',
    bucket_key='incoming/data_{{ ds }}.csv',
    poke_interval=300,  # Check every 5 minutes
    timeout=3600,       # Give up after 1 hour
    mode='reschedule',  # Free worker while waiting
)

# 2. Process file
process = PythonOperator(
    task_id='process_file',
    python_callable=process_uploaded_file,
)

# 3. Archive
archive = BashOperator(
    task_id='archive_file',
    bash_command='aws s3 mv s3://uploads/incoming/data_{{ ds }}.csv s3://uploads/archive/',
)

wait_for_file >> process >> archive
```

---

## Airflow vs Alternatives

🎓 **PROFESSOR**: How Airflow compares to other tools.

### Comparison Matrix

```
┌─────────────────────────────────────────────────────────────┐
│              WORKFLOW ORCHESTRATION TOOLS                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│ AIRFLOW                                                      │
│ ────────                                                     │
│ + Python-based (familiar for data engineers)                │
│ + Rich ecosystem (100+ providers)                           │
│ + Active community                                          │
│ + Visual UI                                                  │
│ - Steep learning curve                                      │
│ - Resource-heavy (scheduler, DB, workers)                   │
│ Use: Complex ETL, batch processing                          │
│                                                              │
│ PREFECT                                                      │
│ ────────                                                     │
│ + Modern Python API (more Pythonic than Airflow)            │
│ + Cloud-native                                              │
│ + Better error handling                                     │
│ - Smaller ecosystem than Airflow                            │
│ - Commercial features behind paywall                        │
│ Use: Python-heavy workflows, startups                       │
│                                                              │
│ DAGSTER                                                      │
│ ────────                                                     │
│ + Asset-oriented (focus on data, not tasks)                 │
│ + Strong typing                                             │
│ + Excellent testing support                                 │
│ - Newer (less mature)                                       │
│ - Smaller community                                         │
│ Use: Data lakehouse, analytics engineering                  │
│                                                              │
│ LUIGI                                                        │
│ ──────                                                       │
│ + Lightweight                                               │
│ + Easy to learn                                             │
│ - No scheduler (just dependency resolution)                 │
│ - Limited UI                                                │
│ - Less active development                                   │
│ Use: Simple pipelines, legacy systems                       │
│                                                              │
│ ARGO WORKFLOWS                                              │
│ ───────────────                                             │
│ + Kubernetes-native                                         │
│ + Scales well                                               │
│ + GitOps-friendly (YAML configs)                            │
│ - Kubernetes required                                       │
│ - Less Python-friendly                                      │
│ Use: K8s environments, microservices                        │
│                                                              │
│ AWS STEP FUNCTIONS                                          │
│ ───────────────────                                         │
│ + Serverless (no infrastructure)                            │
│ + AWS integration                                           │
│ - AWS lock-in                                               │
│ - Limited to AWS services                                   │
│ Use: AWS-only environments                                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### Using Airflow in System Design

```
Scenario: "Design a system to process clickstream data daily"
────────────────────────────────────────────────────────────

Requirements:
  - Ingest clickstream logs (100GB/day)
  - Process and aggregate metrics
  - Load to data warehouse
  - Run nightly (2am)
  - Send report email

Architecture:
  ┌────────────────────────────────────────────┐
  │  Clickstream → Kafka → S3 (raw logs)       │
  │                           ↓                │
  │         Airflow DAG (nightly 2am)          │
  │  ┌──────────────────────────────────────┐ │
  │  │ 1. Check S3 partition exists         │ │
  │  │ 2. Run Spark job (aggregate metrics) │ │
  │  │ 3. Load to Redshift                  │ │
  │  │ 4. Data quality checks               │ │
  │  │ 5. Generate report                   │ │
  │  │ 6. Email stakeholders                │ │
  │  └──────────────────────────────────────┘ │
  │                           ↓                │
  │         Redshift ← BI Tools                │
  └────────────────────────────────────────────┘

Why Airflow?
  ✓ Complex dependencies (6 steps)
  ✓ Need scheduling (daily 2am)
  ✓ Need monitoring (web UI)
  ✓ Need retry logic (transient failures)
  ✓ Need alerting (email on failure)

Deep Dive:
  Q: How to handle late-arriving data?
  A: - Use sensors to wait for data
     - Set timeout (1 hour)
     - If timeout, send alert + skip processing
     - Reprocess next day with backfill

  Q: What if Spark job fails?
  A: - Retry 3 times (5 min apart)
     - If still fails, send alert
     - Manual trigger for reprocessing

  Q: How to scale for 1TB/day?
  A: - Use CeleryExecutor or KubernetesExecutor
     - Run Spark on EMR/Dataproc
     - Partition data by date for faster processing
```

---

## 🧠 MIND MAP: APACHE AIRFLOW

```
       AIRFLOW
          |
    ┌─────┼─────┐
    ↓     ↓     ↓
  DAG  TASKS  SCHEDULE
   |     |       |
  Graph Operators Cron
   |     |       |
 Acyclic Bash  @daily
      Python
```

---

## 💡 EMOTIONAL ANCHORS

### 1. DAG = Recipe Book 📖
- Recipe (DAG): Chocolate cake
- Steps (tasks): Mix ingredients → Bake → Frost
- Must follow order (dependencies)
- Can't frost before baking!

### 2. Scheduler = Kitchen Timer ⏰
- Set timer (schedule)
- Ding! Time to bake (trigger DAG)
- Runs automatically
- Never forget!

### 3. XCom = Passing Notes 📝
- Task A writes note (XCom push)
- Task B reads note (XCom pull)
- Small notes only (no novels!)

### 4. Retry = Try, Try Again 🎯
- Dart misses bullseye (task fails)
- Try again (retry #1)
- Miss again (retry #2)
- Finally hit! (success)

### 5. Sensor = Doorbell 🔔
- Wait for delivery (file arrival)
- Ring! Package arrived (sensor succeeds)
- Now process package (next task)

### 6. Trigger Rule = Meeting Attendance 👥
- all_success: All attendees required
- one_success: At least one person
- all_done: Meeting happens regardless

---

## 🔑 Key Takeaways

1. **Airflow = Workflow orchestration for data pipelines**
   - Define workflows as code (Python)
   - Visual monitoring (Web UI)
   - Automatic scheduling & retries

2. **DAG = Directed Acyclic Graph**
   - Tasks + Dependencies
   - No cycles allowed
   - Topological sort for execution order

3. **Operators define what to do**
   - BashOperator: Shell commands
   - PythonOperator: Python functions
   - 100+ providers for integrations

4. **execution_date ≠ actual run time**
   - execution_date = data being processed
   - Runs at execution_date + schedule_interval
   - Confusing but ensures complete data

5. **Make tasks idempotent**
   - Safe to run multiple times
   - Same result each time
   - Critical for retries

6. **Use connections & variables**
   - No hardcoded credentials
   - Configurable without redeployment
   - Environment-specific settings

7. **Don't pass large data via XCom**
   - XCom in metadata DB (size limit)
   - Use S3/HDFS for large data
   - Pass paths, not data

8. **Monitor with Web UI**
   - Graph view: Dependencies
   - Tree view: Historical runs
   - Logs: Debug failures

9. **Airflow is industry standard**
   - Largest community
   - Rich ecosystem
   - Battle-tested at scale

10. **Consider alternatives for specific needs**
    - Prefect: Modern Python API
    - Dagster: Asset-oriented
    - Step Functions: Serverless AWS

---

**Final Thought**: Airflow transformed data pipeline management from fragile cron scripts to maintainable, monitored workflows. Understanding DAGs, dependencies, and scheduling is key to mastering Airflow. The learning curve is steep, but the payoff is enormous - reliable, scalable data pipelines that the whole team can understand and maintain.
