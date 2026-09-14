# Product Requirements Document (PRD)

## Live Exam Platform

**Project:** Full Stack Developer Intern Assignment
**Problem Statement:** 1 — Live Exam Platform
**Author:** Tejaswini R R
**Repository:** https://github.com/tejaswinirr22-hub/live-exam-platform

---

# 1. Product Overview

The Live Exam Platform is a scalable online examination system designed for large-scale national examinations.

The system must support approximately **500,000 candidates appearing simultaneously**, with the examination beginning at exactly **10:00 AM** and ending at exactly **1:00 PM**.

Each candidate receives a unique randomized question paper generated from a pool of **10,000 questions**.

The platform must provide reliable answer saving, session management, network-disconnection recovery, multi-device protection, and strict server-side examination cutoff.

---

# 2. Problem Statement

A national-level examination creates several major technical challenges:

1. 500,000 candidates may start the examination simultaneously.
2. Each candidate requires a unique randomized question paper.
3. Candidates automatically save answers every 30 seconds.
4. All candidates may attempt to save answers at approximately the same time.
5. Candidates may temporarily lose internet connectivity.
6. Candidates may attempt to open the examination on multiple devices.
7. Network latency differs between candidates.
8. The examination must end fairly for everyone at exactly 1:00 PM.
9. The system must remain available during the entire examination.

The solution must therefore prioritize **scalability, reliability, consistency, performance, and fairness**.

---

# 3. Goals

## Primary Goals

The system should:

* Support a scalable architecture for 500,000 concurrent candidates.
* Generate a unique question paper for every candidate.
* Provide fast answer auto-save.
* Prevent excessive database load during simultaneous saves.
* Preserve answers during temporary internet failures.
* Prevent simultaneous sessions on multiple devices.
* Enforce the examination cutoff using server-side time.
* Automatically submit expired examination sessions.
* Maintain PostgreSQL as the durable source of truth.
* Use Redis for high-speed temporary examination state.

---

# 4. Non-Goals

The following features are outside the primary scope of this implementation:

* Full online proctoring using cameras.
* Facial recognition.
* Live video monitoring.
* Payment processing.
* Candidate registration and admission-card generation.
* Complex examination-result processing.
* Advanced AI-based cheating detection.

These can be added later if required.

---

# 5. Target Users

## Candidate

The candidate should be able to:

* Start the examination.
* View their assigned questions.
* Select answers.
* Automatically save answers.
* Continue working during temporary network issues.
* Recover their examination session.
* Submit their examination.
* Receive automatic submission at the examination cutoff.

## Examination Administrator

An administrator should eventually be able to:

* Create examinations.
* Add questions.
* Manage candidates.
* Configure examination schedules.
* Monitor examination health.
* View submission status.

---

# 6. Functional Requirements

## FR-01: Candidate Session Creation

The system shall allow an eligible candidate to start an examination session.

The session shall contain:

* Candidate ID
* Examination ID
* Device ID
* Start time
* Expiry time
* Submission status

---

## FR-02: Unique Device Session

The system shall prevent the same candidate from simultaneously accessing the same examination from multiple devices.

A distributed lock using Redis shall be used to coordinate active sessions.

Example:

```text
Candidate 29
      |
      +---- Device A → Allowed
      |
      +---- Device B → Rejected
```

---

## FR-03: Randomized Question Paper

Each candidate shall receive a randomized set of questions.

The initial implementation uses:

* Question pool: 10,000 questions
* Questions per paper: 20

The generated paper shall be associated with the candidate and examination.

Once generated, the same candidate should receive the same paper when recovering the session.

---

## FR-04: Question Paper Caching

Generated question papers shall be cached in Redis.

Example Redis key:

```text
exam-paper:{candidateId}:{examId}
```

Example:

```text
exam-paper:29:1
```

The system should check Redis before querying PostgreSQL.

---

## FR-05: Answer Auto-Save

Candidate answers shall be automatically saved during the examination.

The frontend shall perform an auto-save operation every 30 seconds.

Answers shall first be written to Redis.

This prevents every auto-save operation from directly generating a PostgreSQL write.

---

## FR-06: Durable Answer Persistence

Answers stored in Redis shall be periodically persisted to PostgreSQL by a background service.

Architecture:

```text
Candidate
    |
    v
Spring Boot API
    |
    v
Redis
    |
    v
Background Persistence
    |
    v
PostgreSQL
```

PostgreSQL remains the durable source of truth.

---

## FR-07: Answer Recovery

When a candidate requests an answer:

1. The system checks Redis.
2. If the answer is not available in Redis, PostgreSQL is checked.
3. If the answer exists in PostgreSQL, it is returned.

This allows answers to remain available even after Redis persistence has occurred.

---

## FR-08: Internet Disconnection

If the candidate loses internet connectivity:

* Existing server-side answers must remain safe.
* The frontend shall maintain pending answer data locally.
* Pending answers shall be synchronized when connectivity returns.

Example:

```text
Internet Lost
     |
     v
Save pending answer locally
     |
     v
Candidate continues examination
     |
     v
Internet restored
     |
     v
Synchronize pending answers
```

---

## FR-09: Examination Timer

The frontend shall display the remaining examination time.

However, the browser clock shall not be trusted for examination enforcement.

The backend shall determine whether the examination is still active.

---

## FR-10: Hard Examination Cutoff

The examination shall end at exactly 1:00 PM according to server time.

After the cutoff:

* New answers shall be rejected.
* The examination session shall be marked as submitted.
* Expired sessions shall be automatically submitted.

Example:

```text
Candidate submits answer
          |
          v
Server checks current time
          |
          +---- Before cutoff → Accept
          |
          +---- After cutoff → Reject
```

---

## FR-11: Automatic Submission

A scheduled backend process shall identify expired sessions and mark them as submitted.

This ensures that candidates cannot continue after the examination deadline.

---

## FR-12: Manual Submission

Candidates shall be able to submit the examination before the deadline.

After submission, additional answers shall not be accepted.

---

# 7. Non-Functional Requirements

## NFR-01: Scalability

The production architecture should support approximately:

**500,000 concurrent candidates.**

The application should scale horizontally by running multiple backend instances behind a load balancer.

---

## NFR-02: Performance

Frequently accessed examination data should be served from Redis where appropriate.

The target is to keep candidate-facing API response times low even during traffic spikes.

---

## NFR-03: Availability

The system should remain operational throughout the three-hour examination.

The production system should avoid single points of failure.

---

## NFR-04: Reliability

Candidate answers must not be lost because of a temporary network failure or an individual application-server failure.

---

## NFR-05: Consistency

Important examination state such as:

* Submission status
* Candidate session
* Final answers
* Question paper assignment

must eventually be persisted in PostgreSQL.

---

## NFR-06: Security

The production system should implement:

* HTTPS
* Authentication
* Authorization
* Candidate-specific access control
* Input validation
* Rate limiting
* Secure session management
* Audit logging

---

## NFR-07: Fairness

The examination cutoff must be determined by the server rather than by the candidate's browser.

Candidates should receive the same official examination end time regardless of their local computer clock.

---

# 8. High-Level Architecture

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
                  +--------+ +--------+ +--------+
                       \       |       /
                        \      |      /
                         v     v     v
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

# 9. Data Storage Strategy

## Redis

Redis is responsible for high-frequency and temporary state.

Examples:

```text
Candidate answers
Question-paper cache
Device locks
Session-related temporary state
```

## PostgreSQL

PostgreSQL stores durable data.

Examples:

```text
Candidates
Examinations
Questions
Question papers
Exam sessions
Candidate answers
Submission state
```

---

# 10. Scaling Strategy

A single backend server is not sufficient for 500,000 concurrent candidates.

The system should use horizontal scaling.

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
                    Redis
                       |
                       v
                  PostgreSQL
```

If traffic increases, additional API instances can be added.

---

# 11. Auto-Save Scaling Strategy

A major challenge is that 500,000 candidates may attempt auto-save at approximately the same time.

Directly writing all requests to PostgreSQL could create a large database bottleneck.

Instead:

```text
500,000 candidates
        |
        v
Load Balancer
        |
        v
Multiple API Instances
        |
        v
Redis
        |
        v
Background Workers
        |
        v
PostgreSQL
```

The production implementation can further improve this using:

* Redis Streams
* Message queues
* Batch database writes
* Multiple persistence workers
* Connection pooling

---

# 12. Network Failure Strategy

Candidate answers can exist in two locations:

### Server

Redis contains the latest server-side answer.

### Browser

The frontend stores unsynchronized answers in localStorage while offline.

When connectivity returns, pending answers are sent to the backend.

This provides an additional recovery mechanism.

---

# 13. Multi-Device Strategy

A Redis lock can be created using:

```text
exam-lock:{candidateId}:{examId}
```

Example:

```text
exam-lock:29:1
```

The lock remains valid until the examination ends.

This allows multiple backend servers to share the same session state.

---

# 14. Cutoff Strategy

The server determines whether the examination is still active.

For every answer request:

```text
if currentServerTime < examExpiry
       accept answer
else
       reject answer
```

This prevents candidates from manipulating their browser clocks.

A scheduled process also automatically submits expired sessions.

---

# 15. AI Opportunities

AI should only be introduced where it provides measurable value.

Potential applications include:

## Intelligent System Anomaly Detection

AI could identify unusual patterns in:

* API latency
* Request volume
* Redis usage
* Database load
* Error rates

## Candidate Technical Issue Detection

AI could help classify technical issues such as:

* Network instability
* Browser problems
* Session problems
* Device problems

## Examination Analytics

AI could identify unusual answer timing or interaction patterns for further review.

AI should not be added merely to make the project appear AI-enabled.

---

# 16. Technology Stack

| Layer               | Technology                  |
| ------------------- | --------------------------- |
| Backend             | Java 21                     |
| Framework           | Spring Boot                 |
| API                 | Spring Web                  |
| ORM                 | Spring Data JPA / Hibernate |
| Database            | PostgreSQL                  |
| Cache               | Redis                       |
| Frontend            | React                       |
| Build Tool          | Maven                       |
| Frontend Build Tool | Vite                        |
| Containerization    | Docker                      |
| API Testing         | Postman                     |
| Load Testing        | Python Requests             |
| Version Control     | Git / GitHub                |

---

# 17. Testing Requirements

The system should be tested for:

### Functional Testing

* Session creation
* Session recovery
* Question paper generation
* Answer saving
* Answer retrieval
* Manual submission
* Automatic submission

### Failure Testing

* Internet disconnection
* Redis availability
* Database availability
* Duplicate device access
* Expired examination

### Load Testing

The prototype includes a Python concurrency test.

The local test simulated:

```text
100 concurrent candidates
```

Observed result:

```text
Successful requests: 98
Failed requests: 2
Total execution time: 3.40 seconds
Average response time: 0.403 seconds
```

The 2 failed requests returned HTTP 409 conflicts associated with existing session/device state.

This test demonstrates the concurrency approach at development scale; it is not a claim that the local machine can serve 500,000 candidates.

---

# 18. Success Criteria

The project is considered successful when:

* A candidate can start an examination.
* A randomized question paper is assigned.
* The paper can be cached in Redis.
* Answers can be saved rapidly.
* Answers are persisted to PostgreSQL.
* Saved answers can be recovered.
* Offline answers can be synchronized.
* Multiple devices for the same candidate are prevented.
* Expired answers are rejected.
* Expired sessions are automatically submitted.
* The system demonstrates concurrent request handling.
* The architecture provides a credible path to 500,000 concurrent candidates.

---

# 19. Risks and Mitigation

| Risk                         | Mitigation                                         |
| ---------------------------- | -------------------------------------------------- |
| Database overload            | Redis + batching + multiple workers                |
| Redis failure                | Durable PostgreSQL persistence + recovery strategy |
| Server failure               | Multiple backend instances                         |
| Network failure              | Browser pending-answer storage                     |
| Multiple devices             | Distributed Redis lock                             |
| Browser clock manipulation   | Server-side cutoff                                 |
| Traffic spike                | Load balancer + horizontal scaling                 |
| Slow database queries        | Indexing + connection pooling                      |
| Simultaneous auto-save spike | Redis buffering + asynchronous persistence         |

---

# 20. Future Improvements

For production deployment, the following improvements are recommended:

* Redis Cluster
* PostgreSQL read replicas
* Database partitioning
* Redis Streams or message queues
* Kubernetes-based deployment
* Horizontal autoscaling
* CDN
* Distributed monitoring
* Centralized logging
* Distributed tracing
* Circuit breakers
* Multi-region disaster recovery
* Strong authentication and authorization
* Comprehensive audit logging
* Production-grade security

---

# 21. Final Product Vision

The final system should provide candidates with a reliable examination experience while protecting the platform from traffic spikes.

The core architecture follows three principles:

```text
FAST STATE
    ↓
Redis

DURABLE DATA
    ↓
PostgreSQL

HORIZONTAL SCALE
    ↓
Multiple Spring Boot Instances
```

The system therefore separates high-frequency temporary operations from durable database operations and uses distributed infrastructure to support large-scale examination traffic.
