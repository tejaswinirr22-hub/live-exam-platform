# Architecture Document

## Live Exam Platform

**Project:** Full Stack Developer Intern Assignment
**Problem Statement:** 1 — Live Exam Platform
**Author:** Tejaswini R R
**GitHub Repository:** https://github.com/tejaswinirr22-hub/live-exam-platform

---

# 1. Architecture Objective

The objective is to design a reliable and scalable online examination platform capable of supporting approximately **500,000 candidates simultaneously**.

The platform must handle:

* Simultaneous examination start
* Randomized question papers
* High-frequency answer auto-save
* Temporary network failures
* Multiple-device access
* Strict examination cutoff
* Automatic submission
* High availability

The architecture separates **fast temporary state** from **durable data** and uses horizontal scaling to handle large traffic volumes.

---

# 2. High-Level Architecture

```text
                         500,000 Candidates
                                |
                                v
                       +------------------+
                       |  Load Balancer   |
                       +------------------+
                         /      |       \
                        /       |        \
                       v        v         v
                  +--------+ +--------+ +--------+
                  | API-1  | | API-2  | | API-N  |
                  |Spring  | |Spring  | |Spring  |
                  | Boot   | | Boot   | | Boot   |
                  +--------+ +--------+ +--------+
                       \       |       /
                        \      |      /
                         +-----+-----+
                               |
                               v
                       +----------------+
                       |     Redis      |
                       | Cache / State  |
                       +----------------+
                               |
                               v
                       +----------------+
                       |   PostgreSQL   |
                       | Durable Store  |
                       +----------------+
```

---

# 3. Component Responsibilities

## 3.1 React Frontend

The React frontend provides the candidate-facing examination interface.

Responsibilities:

* Display questions
* Display examination timer
* Accept candidate answers
* Auto-save answers every 30 seconds
* Detect online/offline status
* Store pending answers locally
* Synchronize pending answers after reconnection
* Start or recover examination sessions
* Submit examination

The frontend must never be trusted for security-critical decisions.

---

# 3.2 Load Balancer

The load balancer distributes incoming traffic across multiple Spring Boot application instances.

Example:

```text
Candidate Requests
       |
       v
Load Balancer
   |    |    |
   v    v    v
 API1 API2 APIN
```

Benefits:

* Horizontal scalability
* Better availability
* Distribution of traffic
* Failover when an application instance becomes unavailable

---

# 3.3 Spring Boot Application

Spring Boot provides the backend REST APIs and business logic.

Responsibilities include:

* Candidate session management
* Examination validation
* Question paper generation
* Answer saving
* Answer retrieval
* Submission
* Cutoff enforcement
* Device/session validation

Multiple identical application instances can run simultaneously.

---

# 3.4 Redis

Redis is the high-speed shared state layer.

It is used for:

* Question-paper caching
* Candidate answer state
* Device locks
* Temporary examination state

Redis is particularly useful because these operations occur frequently during an examination.

---

# 3.5 PostgreSQL

PostgreSQL is the durable source of truth.

It stores:

* Candidates
* Examinations
* Questions
* Question papers
* Examination sessions
* Candidate answers
* Submission status

Redis improves performance, while PostgreSQL provides durability.

---

# 4. Data Flow — Starting an Examination

The candidate requests to start an examination.

```text
Candidate
    |
    | POST /api/sessions/start
    v
Load Balancer
    |
    v
Spring Boot
    |
    +---- Check candidate
    |
    +---- Check examination schedule
    |
    +---- Check Redis device lock
    |
    +---- Create/recover session
    |
    +---- Generate/retrieve question paper
    |
    v
Redis
    |
    v
Candidate
```

The session contains:

* Candidate ID
* Examination ID
* Device ID
* Start time
* Expiry time
* Submission status

---

# 5. Question Paper Generation

The examination contains a pool of approximately **10,000 questions**.

The prototype selects a randomized set of questions for each candidate.

For the prototype:

```text
Question Pool = 10,000
Questions per Paper = 20
```

Flow:

```text
Candidate
    |
    v
Check Redis
    |
    +---- Paper exists → Return paper
    |
    v
Check PostgreSQL
    |
    +---- Paper exists → Cache + return
    |
    v
Retrieve question IDs
    |
    v
Randomize
    |
    v
Select questions
    |
    v
Store in PostgreSQL
    |
    v
Cache in Redis
```

Redis key:

```text
exam-paper:{candidateId}:{examId}
```

Example:

```text
exam-paper:29:1
```

---

# 6. Production Optimization for Question Papers

Generating a randomized paper by retrieving and shuffling all 10,000 question IDs for every candidate is acceptable for a small prototype but is not ideal for 500,000 candidates.

At national scale, papers should be prepared before the examination.

A production strategy could be:

```text
Before Examination
        |
        v
Paper Generation Workers
        |
        v
Generate Candidate Papers
        |
        v
PostgreSQL
        |
        v
Redis
        |
        v
Exam Starts
        |
        v
Candidates receive pre-generated papers
```

This moves expensive work away from the 10:00 AM traffic spike.

---

# 7. Answer Auto-Save Architecture

Every candidate automatically saves answers approximately every 30 seconds.

With 500,000 candidates, direct database writes could create a large database spike.

Therefore:

```text
500,000 Candidates
        |
        v
Load Balancer
        |
        v
Spring Boot Instances
        |
        v
Redis
        |
        v
Background Persistence Workers
        |
        v
PostgreSQL
```

The initial prototype writes the latest answer to Redis and uses a scheduled persistence service to transfer answers to PostgreSQL.

---

# 8. Redis Answer Keys

Answer keys use the following format:

```text
exam-answer:{sessionId}:{questionId}
```

Example:

```text
exam-answer:117:8535
```

The value contains the selected option.

Example:

```text
A
```

Redis provides rapid access to the latest answer.

---

# 9. Background Answer Persistence

A background service periodically searches for pending Redis answers and persists them to PostgreSQL.

Conceptually:

```text
Redis Answer
     |
     v
Persistence Worker
     |
     v
Find Session
     |
     v
Find Question
     |
     v
Create / Update CandidateAnswer
     |
     v
PostgreSQL
     |
     v
Remove processed Redis entry
```

The current prototype runs this operation periodically.

---

# 10. Production Improvement for Answer Persistence

The prototype uses Redis key scanning for simplicity.

For production, scanning Redis keys using a broad `KEYS` operation should be avoided because it can block Redis during large workloads.

A production implementation should use:

* Redis Streams
* Message queues
* Redis consumer groups
* Dedicated persistence workers

Example:

```text
Candidate
    |
    v
Redis Stream
    |
    +---- Worker 1
    |
    +---- Worker 2
    |
    +---- Worker N
    |
    v
PostgreSQL
```

This allows persistence workers to process answer events independently and horizontally.

---

# 11. Internet Disconnection Handling

A candidate may lose internet connectivity during the examination.

The system uses two layers of protection.

### Server-Side State

Previously synchronized answers remain in Redis/PostgreSQL.

### Client-Side State

The React frontend stores unsynchronized answers in localStorage.

Flow:

```text
Candidate Offline
       |
       v
Answer stored locally
       |
       v
Candidate continues
       |
       v
Internet restored
       |
       v
Pending answers synchronized
       |
       v
Redis
       |
       v
PostgreSQL
```

Therefore, temporary connectivity problems do not automatically erase previously saved answers.

---

# 12. Multi-Device Protection

A candidate must not be able to run the same examination simultaneously on multiple devices.

A distributed Redis lock is used.

Key:

```text
exam-lock:{candidateId}:{examId}
```

Example:

```text
exam-lock:29:1
```

Architecture:

```text
                Candidate 29
                     |
              +------+------+
              |             |
              v             v
          Device A       Device B
              |             |
              v             v
             API           API
              \             /
               \           /
                v         v
                  Redis
                    |
                    v
                Lock Check
```

If Device A owns the lock, Device B is rejected.

This works across multiple Spring Boot instances because all instances share Redis.

---

# 13. Atomic Locking

In production, the device lock should be created atomically.

Conceptually:

```text
SET lock-key device-id NX EX <expiry>
```

`NX` ensures that the lock is only created when it does not already exist.

This prevents race conditions when two devices attempt to start at almost exactly the same time.

---

# 14. Examination Cutoff

The examination starts at:

```text
10:00 AM
```

and ends at:

```text
1:00 PM
```

The backend server determines the official time.

The browser's local clock is not trusted.

For every answer request:

```text
Current Server Time
        |
        v
Is current time before expiry?
       / \
     Yes  No
      |    |
      v    v
   Accept Reject
          |
          v
       Submit
```

---

# 15. Network Latency and Fairness

Different candidates may have different network latency.

The system therefore defines the cutoff using the server's authoritative time.

If an answer reaches the server after the official cutoff, it is rejected.

The browser cannot extend the examination by changing its clock.

The server is responsible for the final decision.

---

# 16. Automatic Submission

A scheduled service periodically checks examination sessions.

Conceptually:

```text
Scheduler
    |
    v
Find sessions where:
expiresAt <= current time
    |
    v
Mark submitted = true
```

The system therefore automatically closes expired sessions.

---

# 17. Submission Race Condition

There may be a race between:

* A candidate submitting an answer.
* The examination reaching the cutoff.

The backend must perform the cutoff check at the time the request is processed.

The database submission state should be treated as authoritative.

For production, final submission should use transactional operations and appropriate concurrency controls.

---

# 18. Handling the 10:00 AM Traffic Spike

The largest traffic spike occurs when the examination begins.

A production deployment should prepare data before 10:00 AM.

Recommended approach:

```text
Before 10:00 AM
       |
       +---- Pre-generate question papers
       |
       +---- Warm Redis cache
       |
       +---- Scale API instances
       |
       +---- Verify database capacity
       |
       +---- Verify monitoring
       |
       v
10:00 AM
       |
       v
Candidates enter examination
```

This reduces expensive work during the peak.

---

# 19. Handling 500,000 Concurrent Candidates

A single server should not handle all candidates.

The production system should use multiple application instances.

Example:

```text
                  Load Balancer
                        |
        +---------------+---------------+
        |               |               |
        v               v               v
      API-1           API-2           API-N
        |               |               |
        +---------------+---------------+
                        |
                        v
                   Redis Cluster
                        |
                        v
              PostgreSQL Cluster
```

The exact number of instances should be determined through realistic load testing and capacity planning.

---

# 20. Database Strategy

PostgreSQL should remain the durable source of truth.

Production improvements include:

* Proper indexes
* Connection pooling
* Read replicas
* Partitioning
* Batch writes
* Query optimization
* Database monitoring

Frequently accessed read operations can be served from Redis where appropriate.

---

# 21. Redis Strategy

For 500,000 candidates, a single Redis instance may not provide sufficient capacity or availability.

A production environment should consider:

```text
Redis Cluster
    |
    +---- Shard 1
    |
    +---- Shard 2
    |
    +---- Shard N
```

Redis replication should also be used for high availability.

---

# 22. Caching Strategy

Candidate-specific examination data can be cached.

Examples:

```text
Question paper
Latest answer
Session state
Device lock
```

Cache entries should have appropriate TTLs.

Example:

```text
exam-paper:29:1 → 24 hour TTL
```

The exact TTL should be selected based on examination lifecycle requirements.

---

# 23. Failure Scenarios

## Application Server Failure

If one Spring Boot instance fails:

```text
Candidate
    |
    v
Load Balancer
    |
    X API-1 failed
    |
    v
API-2
```

Shared Redis state allows another instance to continue processing the candidate.

---

## Redis Failure

Production Redis should use replication and high availability.

Durable examination information must also exist in PostgreSQL.

A production design should define explicit Redis recovery behavior before deployment.

---

## Database Failure

PostgreSQL should use:

* Replication
* Backups
* Monitoring
* Disaster recovery

Critical final examination state must be durable.

---

# 24. Security Architecture

The production platform should implement:

```text
HTTPS
  |
  v
Authentication
  |
  v
Authorization
  |
  v
Candidate Session
  |
  v
Exam APIs
```

Security requirements include:

* HTTPS/TLS
* Secure authentication
* Authorization
* Candidate-specific access control
* Input validation
* Rate limiting
* Secure Redis access
* Secure database credentials
* Audit logging
* Session validation

A candidate must never be allowed to access another candidate's examination data by changing an ID in an API request.

---

# 25. Important API Authorization Rule

For production, every answer request should verify:

```text
Authenticated Candidate
        |
        v
Owns Session?
        |
        v
Owns Question Paper?
        |
        v
Answer belongs to assigned paper?
```

The prototype should be extended with these authorization checks before production deployment.

---

# 26. Monitoring

A national examination requires continuous monitoring.

Important metrics include:

### Application

* Requests per second
* API latency
* Error rate
* HTTP status codes
* Active sessions

### Redis

* Memory usage
* Operations per second
* Cache hit rate
* Connection count

### PostgreSQL

* CPU usage
* Connections
* Query latency
* Lock contention
* Disk usage

### Infrastructure

* CPU
* Memory
* Network
* Instance health

---

# 27. Observability

A production implementation should include:

* Centralized logs
* Metrics
* Distributed tracing
* Alerts
* Health checks

Example:

```text
Spring Boot
     |
     +---- Logs
     |
     +---- Metrics
     |
     +---- Traces
     |
     v
Monitoring Platform
```

---

# 28. AI Opportunities

AI should only be used where it produces measurable value.

## 28.1 Infrastructure Anomaly Detection

AI can identify abnormal patterns in:

* API latency
* Request rates
* Error rates
* Redis memory
* Database load

This can help operations teams detect problems before they become major outages.

---

## 28.2 Candidate Technical Issue Classification

AI can help classify technical problems reported by candidates.

For example:

```text
Candidate Issue
       |
       v
AI Classification
       |
       +---- Network
       +---- Browser
       +---- Device
       +---- Session
       +---- Platform
```

This can speed up support response.

---

## 28.3 Examination Analytics

AI could identify unusual patterns for human review, such as:

* Abnormal answer timing
* Unusual navigation patterns
* Unexpected session behavior

AI output should be treated as a signal rather than automatically making high-impact decisions about candidates.

---

# 29. Why AI Should Not Be Added Everywhere

AI introduces:

* Additional infrastructure
* Additional cost
* Monitoring requirements
* Model maintenance
* Potential false positives
* Additional complexity

Therefore, AI should only be introduced when the expected benefit is greater than the additional operational complexity.

---

# 30. Technology Stack

| Component        | Technology                  |
| ---------------- | --------------------------- |
| Frontend         | React + Vite                |
| Backend          | Java 21 + Spring Boot       |
| API              | REST                        |
| ORM              | Spring Data JPA / Hibernate |
| Database         | PostgreSQL                  |
| Cache            | Redis                       |
| Containerization | Docker                      |
| Load Testing     | Python + Requests           |
| API Testing      | Postman                     |
| Version Control  | Git + GitHub                |

---

# 31. Prototype vs Production

| Area               | Prototype                   | Production                         |
| ------------------ | --------------------------- | ---------------------------------- |
| API servers        | Single Spring Boot instance | Multiple instances                 |
| Redis              | Single Redis container      | Redis Cluster                      |
| Database           | PostgreSQL                  | PostgreSQL cluster/replicas        |
| Paper generation   | On demand                   | Pre-generated                      |
| Answer persistence | Scheduled persistence       | Redis Streams/message queue        |
| Redis scanning     | Simple key scan             | SCAN/Streams                       |
| Load testing       | 100 candidates              | Large-scale distributed testing    |
| Monitoring         | Basic logs                  | Full observability                 |
| Security           | Development setup           | Production authentication/security |
| Deployment         | Local machine               | Cloud/Kubernetes/infrastructure    |

---

# 32. Local Load Test

The prototype includes:

```text
load-test/load-test.py
```

The test simulates:

```text
100 concurrent candidates
```

Observed result:

```text
Total candidates: 100
Successful requests: 98
Failed requests: 2
Total execution time: 3.40 seconds
Average response time: 0.403 seconds
```

The two failed requests returned HTTP 409 conflicts associated with existing candidate/session state.

This test validates the concurrency approach at development scale.

It does not represent a 500,000-user production benchmark.

---

# 33. Scalability Calculation

The examination requirement is:

```text
500,000 candidates
```

Each candidate may auto-save approximately every:

```text
30 seconds
```

The theoretical average save rate is approximately:

```text
500,000 / 30
≈ 16,667 answer-save operations per second
```

This demonstrates why direct PostgreSQL writes from every candidate should not be the only mechanism.

The architecture therefore uses Redis as the high-speed ingestion layer and asynchronous persistence to PostgreSQL.

The real production capacity would need to be validated using distributed load testing.

---

# 34. Key Architectural Decisions

## Decision 1 — Redis for High-Frequency State

Chosen because answer saves and session state require fast access.

## Decision 2 — PostgreSQL for Durable Data

Chosen because examination data must remain durable and queryable.

## Decision 3 — Horizontal API Scaling

Chosen because 500,000 candidates cannot reliably depend on a single backend instance.

## Decision 4 — Server-Side Cutoff

Chosen to provide a common authoritative examination deadline.

## Decision 5 — Local Offline Storage

Chosen to protect pending answers during temporary network failures.

## Decision 6 — Distributed Device Lock

Chosen to prevent multiple simultaneous examination sessions across devices.

---

# 35. Main Trade-offs

### Performance vs Durability

Redis provides speed, while PostgreSQL provides durability.

Therefore, the system uses both.

### Simplicity vs Scalability

The prototype is intentionally simpler than a production national examination platform.

Production would introduce distributed workers, Redis clustering, database replicas, and stronger observability.

### AI Capability vs Complexity

AI can improve monitoring and analytics, but unnecessary AI would increase infrastructure complexity without improving the candidate experience.

---

# 36. Final Architecture

The recommended production architecture is:

```text
                         Candidates
                       ~500,000 users
                              |
                              v
                    +------------------+
                    |  Global / Local  |
                    |  Load Balancer   |
                    +------------------+
                              |
              +---------------+---------------+
              |               |               |
              v               v               v
          Spring Boot     Spring Boot     Spring Boot
           Instance 1      Instance 2       Instance N
              |               |               |
              +---------------+---------------+
                              |
                              v
                     +----------------+
                     | Redis Cluster  |
                     |                |
                     | Session State  |
                     | Answer State   |
                     | Paper Cache    |
                     | Device Locks   |
                     +----------------+
                              |
                              v
                     +----------------+
                     | Message/Stream |
                     | Processing     |
                     +----------------+
                              |
                              v
                     +----------------+
                     |  PostgreSQL    |
                     |  Primary/      |
                     |  Replicas      |
                     +----------------+
                              |
                              v
                     Monitoring / Logs
```

---

# 37. Conclusion

The Live Exam Platform architecture is designed around the principle of separating **high-frequency real-time operations** from **durable database operations**.

The key architecture is:

```text
Load Balancer
      ↓
Multiple Spring Boot Instances
      ↓
Redis
      ↓
Background Persistence
      ↓
PostgreSQL
```

This design allows the platform to scale horizontally while maintaining fast candidate interactions and durable examination data.

The local prototype demonstrates the core functionality, while the production architecture provides a path toward supporting approximately **500,000 concurrent candidates**.
