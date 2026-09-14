# Design Thinking Document

## Live Exam Platform

### 1. Understanding the Problem

The problem requires an online examination platform capable of handling up to 500,000 candidates simultaneously.

The key challenges identified were:

* Very high concurrent traffic.
* Exact examination start and end times.
* Unique randomized question papers.
* Frequent answer saving.
* Temporary internet failures.
* Multiple-device access.
* Network latency.
* Reliable answer persistence.
* Automatic submission at the examination deadline.

The main design principle was to make the server authoritative for important examination rules while using client-side mechanisms to improve the candidate experience.

### 2. Design Goals

The design focused on:

1. Reliability — candidate answers should be protected from temporary failures.
2. Scalability — the backend should be horizontally scalable.
3. Consistency — candidates should receive a stable question paper.
4. Security — candidates should not be able to bypass the examination deadline.
5. Usability — the examination interface should remain simple.
6. Recoverability — candidates should be able to recover an interrupted session.

### 3. Key Design Decisions

#### React for Frontend

React was selected because it provides a component-based approach for building an interactive examination interface.

It handles:

* Questions and options.
* Timer display.
* Answer selection.
* Online/offline status.
* Local pending answers.
* Submission.

#### Spring Boot for Backend

Spring Boot was selected for the backend because it provides a structured way to build REST APIs and business logic using Java.

It is responsible for all important examination validations.

#### PostgreSQL for Persistence

PostgreSQL was selected as the persistent database because examination data requires reliable and structured storage.

It stores:

* Candidates.
* Exams.
* Questions.
* Question papers.
* Exam sessions.
* Candidate answers.

#### Redis for High-Speed Temporary Data

Redis was selected because it provides fast in-memory access.

It is used for:

* Candidate/device locks.
* Question-paper caching.
* Temporary answer storage.

This helps reduce repeated database operations.

### 4. Handling the 500,000 Candidate Requirement

A single backend server would not be appropriate for 500,000 concurrent candidates.

The production design therefore uses horizontal scaling.

Multiple Spring Boot instances can run behind a load balancer.

```text id="8z9e9m"
500,000 Candidates
        |
        v
Load Balancer
        |
   +----+----+----+
   |    |    |    |
   v    v    v    v
 App  App  App  App
   \    |    |   /
        |
     Redis
        |
   PostgreSQL
```

The local prototype does not attempt to simulate 500,000 real users because a local computer would not provide a representative production environment.

Instead, the prototype was validated using 100 concurrent simulated candidates.

A real 500,000-user test would require distributed load-testing infrastructure.

### 5. Question Randomization Decision

The requirement specifies that each candidate should receive a unique randomized question paper.

The system generates a fixed question set from the larger question pool.

For the prototype:

* Question pool: approximately 10,000 questions.
* Questions per candidate: 20.

The generated question IDs are persisted and cached.

This was an important decision because simply generating a new random paper on every request could result in a candidate receiving different questions after reconnecting.

Persisting the generated paper provides consistency.

### 6. Exam Timer Decision

The server provides the examination expiry time.

The frontend calculates the remaining time using the server-provided expiry information.

The backend independently checks the current server time when processing answers.

This prevents a candidate from extending the examination by changing the local computer clock.

### 7. Auto-Save Decision

The requirement specifies automatic saving every 30 seconds.

The frontend therefore attempts to synchronize answers periodically.

However, relying only on the 30-second request is not sufficient for network failures.

Therefore, the design uses:

```text id="zq0i9v"
Candidate Answer
      |
      v
Browser Local Storage
      |
      v
Backend
      |
      v
Redis
      |
      v
PostgreSQL
```

This provides multiple protection layers.

### 8. Offline Handling

A candidate may temporarily lose internet connectivity.

The frontend detects the offline state and keeps selected answers locally.

When the network connection returns, pending answers are synchronized with the backend.

This approach improves usability without making the browser the final source of truth.

The backend remains authoritative.

### 9. Multiple Device Decision

The requirement states that two-device access must be handled.

A Redis lock is used for the candidate and examination combination.

For example:

```text id="8q2b2t"
Candidate 29
     |
     +---- Device A → Allowed
     |
     +---- Device B → Rejected
```

If the same device reconnects, the existing session can be recovered.

### 10. Handling Network Latency

Network latency can cause delayed requests or duplicate requests.

The system therefore validates the examination state on the backend rather than trusting the frontend.

For production, idempotency keys and request identifiers could be added to make answer synchronization more robust.

### 11. Important Trade-Offs

#### Redis vs Direct Database Writes

Writing every answer directly to PostgreSQL would increase database pressure during a large examination.

Redis provides a faster temporary layer.

Trade-off:

Redis adds infrastructure complexity and requires a reliable persistence strategy.

#### Local Storage vs Only Server Storage

Local storage improves recovery during temporary network failure.

Trade-off:

Browser storage cannot be treated as the authoritative source because it is controlled by the client.

Therefore, server-side storage remains authoritative.

#### Scheduler vs Event-Driven Processing

The prototype uses scheduled processing for demonstration simplicity.

Trade-off:

A scheduler that scans large numbers of sessions does not scale optimally to 500,000 candidates.

A production system should use more efficient database queries, event-driven processing, queues, or Redis Streams.

### 12. Prototype vs Production

The prototype focuses on demonstrating the important functional requirements.

Production would require additional infrastructure and engineering.

| Area              | Prototype               | Production                  |
| ----------------- | ----------------------- | --------------------------- |
| Backend           | Local Spring Boot       | Multiple instances          |
| Redis             | Single instance         | Redis Cluster               |
| PostgreSQL        | Local database          | Managed/replicated database |
| Load testing      | 100 candidates          | Distributed 500K test       |
| Authentication    | Basic development setup | Production authentication   |
| Monitoring        | Basic                   | Full observability          |
| Answer processing | Scheduled persistence   | Queue/stream based          |
| Deployment        | Local                   | Cloud infrastructure        |

### 13. Testing Approach

The following scenarios were tested:

* Exam session creation.
* Question-paper generation.
* Question-paper recovery.
* Same-device recovery.
* Second-device rejection.
* Answer saving.
* Redis answer caching.
* PostgreSQL answer persistence.
* Automatic submission after expiry.
* Rejection of answers after expiry.
* Manual exam submission.
* Concurrent requests using 100 simulated candidates.

### 14. Known Limitations

The current implementation is a functional prototype and has limitations.

These include:

* It has not been deployed to production cloud infrastructure.
* A real 500,000-user distributed test has not been performed.
* Authentication is not production-grade.
* Redis is currently a local instance.
* PostgreSQL is currently a local instance.
* Scheduler-based processing would need to be replaced or optimized for large-scale production.
* Advanced monitoring and alerting are not included.
* Advanced anti-cheating mechanisms are not implemented.

### 15. What I Would Do Differently With More Time

With additional development time, I would:

1. Deploy the system to a cloud environment.
2. Use multiple backend instances behind a load balancer.
3. Configure Redis Cluster.
4. Configure PostgreSQL replication and connection pooling.
5. Replace large Redis key scans with SCAN, Streams, or another scalable persistence mechanism.
6. Add a message queue for high-volume answer processing.
7. Add production authentication and authorization.
8. Add comprehensive monitoring and distributed tracing.
9. Perform distributed load testing approaching the 500,000-candidate requirement.
10. Add stronger idempotency and retry mechanisms.
11. Add automated CI/CD.
12. Implement disaster recovery and backup strategies.

### 16. Design Philosophy

The overall design follows three principles:

**Server authority:**
Important exam rules such as expiry, session validity, and answer acceptance are controlled by the backend.

**Fast access:**
Redis is used for data that requires high-speed access.

**Durable persistence:**
PostgreSQL is used for long-term storage of important examination data.

The resulting architecture balances performance, reliability, scalability, and implementation complexity while keeping the prototype practical to develop and test.
