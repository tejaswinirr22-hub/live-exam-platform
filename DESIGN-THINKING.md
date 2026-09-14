# Design Thinking Document

## Live Exam Platform

**Project:** Full Stack Developer Intern Assignment
**Problem Statement:** 1 — Live Exam Platform
**Author:** Tejaswini R R
**GitHub Repository:** https://github.com/tejaswinirr22-hub/live-exam-platform

---

# 1. Introduction

The Live Exam Platform is designed for a national-level online examination where approximately **500,000 candidates** may appear simultaneously.

The main challenge is not simply creating an examination website.

The real challenge is ensuring that the system remains:

* Fast
* Reliable
* Fair
* Scalable
* Fault tolerant
* Secure

during extreme traffic conditions.

The design process therefore focused on identifying the highest-risk parts of the system and solving them independently.

---

# 2. Understanding the Problem

The problem can be divided into several major challenges.

```text
500,000 Candidates
       |
       +---- 10:00 AM simultaneous start
       |
       +---- Unique randomized papers
       |
       +---- Auto-save every 30 seconds
       |
       +---- Network failures
       |
       +---- Multiple devices
       |
       +---- Different network latency
       |
       +---- Exact 1:00 PM cutoff
```

Each of these requirements creates a different engineering problem.

---

# 3. User Perspective

The primary user is the candidate.

From the candidate's perspective, the platform should feel simple:

```text
Login
  ↓
Start Exam
  ↓
View Questions
  ↓
Answer Questions
  ↓
Answers Automatically Saved
  ↓
Submit
```

The candidate should not need to understand:

* Redis
* PostgreSQL
* Load balancers
* Background workers
* Distributed locks

The complexity should remain behind the interface.

---

# 4. Candidate Pain Points

The main problems a candidate may experience are:

### Pain Point 1 — Slow Exam Start

If 500,000 candidates request question papers simultaneously, generating every paper at that exact moment could overload the backend.

### Pain Point 2 — Lost Answers

If an answer is saved only in the browser or only through a slow database operation, an interruption could cause data loss.

### Pain Point 3 — Internet Failure

Candidates may temporarily lose internet connectivity.

### Pain Point 4 — Multiple Devices

A candidate may accidentally or intentionally open the examination on another device.

### Pain Point 5 — Unfair Cutoff

Candidates may have different network latency.

The platform must still enforce one official examination end time.

---

# 5. Problem Decomposition

Instead of trying to solve everything as one problem, the system was divided into independent components.

```text
                    Live Exam Platform
                           |
        +------------------+------------------+
        |                  |                  |
        v                  v                  v
 Question Papers       Answer Saving      Sessions
        |                  |                  |
        v                  v                  v
     Redis             Redis + DB        Redis Lock
        |
        v
   PostgreSQL
```

This makes the architecture easier to reason about and test.

---

# 6. Designing for the 10:00 AM Spike

The examination start creates a large traffic spike.

If every candidate triggers expensive paper generation at exactly 10:00 AM, the system could experience:

* CPU spikes
* Database connection exhaustion
* Increased latency
* Request failures

Therefore, the production design should move expensive work before the examination begins.

```text
Before 10:00 AM
       |
       v
Generate Papers
       |
       v
Cache Papers
       |
       v
10:00 AM
       |
       v
Candidates Receive Papers
```

This is an example of moving computation from a peak period to a preparation period.

---

# 7. Designing the Randomized Paper

The requirement says each candidate receives a unique randomized paper.

The prototype uses:

```text
Question Pool = 10,000
Questions per Paper = 20
```

The first design considered generating the paper whenever the candidate starts the exam.

However, at 500,000 candidates this could be expensive.

Therefore, the production design recommends pre-generating papers.

---

# 8. Designing Answer Auto-Save

The answer-saving requirement creates another major challenge.

If 500,000 candidates save answers every 30 seconds:

```text
500,000 / 30
≈ 16,667 operations per second
```

Sending every operation directly to PostgreSQL could create significant database pressure.

Therefore, the design uses:

```text
Candidate
    |
    v
Spring Boot
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

Redis acts as the fast ingestion layer.

PostgreSQL remains the durable storage layer.

---

# 9. Why Not Save Directly to PostgreSQL?

Direct database saving is simple but creates a scaling problem.

### Direct Database Approach

```text
500,000 Candidates
        |
        v
PostgreSQL
```

Potential problems:

* Too many connections
* High write volume
* Increased disk I/O
* Lock contention
* Increased latency

### Redis + Database Approach

```text
500,000 Candidates
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

This separates the real-time workload from durable persistence.

---

# 10. Designing for Internet Failure

The question is:

> If a candidate loses internet for 10 minutes, do they lose their answers?

The design uses multiple layers.

### Previously Saved Answers

Already synchronized answers exist on the server.

### New Offline Answers

The frontend stores pending answers locally.

```text
Internet Lost
      |
      v
Answer → localStorage
      |
      v
Candidate continues
      |
      v
Internet restored
      |
      v
Synchronize pending answers
```

This provides resilience against temporary connectivity problems.

---

# 11. Designing Multi-Device Protection

The requirement asks:

> What if a candidate opens the examination on two devices?

The solution is a distributed lock.

```text
Candidate 29
      |
      +---- Device A
      |
      +---- Device B
```

The first device creates:

```text
exam-lock:29:1
```

The second device checks the same Redis key.

If the lock already exists, the second device is rejected.

This works even when requests reach different backend instances.

---

# 12. Designing the Examination Cutoff

The cutoff is one of the most important fairness requirements.

The browser cannot be trusted because the candidate can change the local system clock.

Therefore:

```text
Browser Clock
     X
     |
     | Not authoritative
     |
Server Clock
     |
     v
Official Exam Time
```

Every answer request is checked against server time.

---

# 13. Handling Network Latency

Different candidates may have:

* Fast internet
* Slow internet
* High latency
* Temporary packet loss

The system cannot guarantee identical network conditions.

However, it can guarantee one authoritative cutoff.

The server decides whether a request arrived before the deadline.

```text
Request arrives
      |
      v
Server checks time
      |
      +---- Before cutoff → Process
      |
      +---- After cutoff → Reject
```

This provides a consistent rule for all candidates.

---

# 14. Automatic Submission

The system also needs to handle candidates who do not manually submit.

A scheduled service checks expired sessions.

```text
Scheduler
    |
    v
Find expired sessions
    |
    v
Mark submitted
```

This guarantees that an examination cannot remain open indefinitely.

---

# 15. Reliability Thinking

The system should assume that failures will happen.

Possible failures include:

```text
Application server failure
Redis failure
Database failure
Network failure
Browser crash
Candidate device failure
Traffic spike
```

The design therefore avoids depending on a single component for everything.

---

# 16. Handling Application Server Failure

Suppose one backend instance fails.

```text
                Load Balancer
                     |
             +-------+-------+
             |               |
             v               v
          API-1            API-2
           X
        Failed
```

The load balancer can route future requests to API-2.

Shared Redis state means the candidate's temporary state does not belong exclusively to API-1.

---

# 17. Handling Redis Failure

Redis is used for high-speed state, but the production system should not depend on a single Redis instance.

A production deployment should use:

* Redis replication
* Redis Cluster
* Monitoring
* Automatic failover

Durable examination data should also be persisted to PostgreSQL.

---

# 18. Handling Database Failure

PostgreSQL contains important durable data.

Production deployment should therefore use:

* Database backups
* Replication
* Monitoring
* Disaster recovery
* Recovery procedures

The database should not be treated as a single-server dependency.

---

# 19. Security Thinking

The candidate's examination data is sensitive.

The system must prevent a candidate from accessing another candidate's data.

For example, simply changing:

```text
candidateId=29
```

to:

```text
candidateId=30
```

must not provide access to candidate 30's examination.

Production APIs should therefore verify authenticated ownership.

---

# 20. Authorization Flow

For an answer request:

```text
Authenticated Candidate
          |
          v
Does session belong to candidate?
          |
         Yes
          |
          v
Does question belong to assigned paper?
          |
         Yes
          |
          v
Save Answer
```

This prevents unauthorized access.

---

# 21. Performance Thinking

Performance should be considered at every layer.

### Frontend

* Avoid unnecessary API calls.
* Cache current state locally.
* Batch pending synchronization when possible.

### Backend

* Keep APIs stateless where possible.
* Use connection pooling.
* Avoid unnecessary database queries.

### Redis

* Use appropriate key design.
* Set appropriate TTLs.
* Avoid blocking operations.

### PostgreSQL

* Use indexes.
* Use batch operations.
* Optimize queries.

---

# 22. Important Prototype vs Production Decision

The project is intentionally implemented as a prototype.

Some implementation choices are suitable for demonstrating the architecture but would need improvement before national-scale deployment.

For example:

```text
Prototype
Redis KEYS operation
       ↓
Production
Redis SCAN / Redis Streams
```

Similarly:

```text
Prototype
Generate papers on demand
       ↓
Production
Pre-generate papers
```

And:

```text
Prototype
Single Redis instance
       ↓
Production
Redis Cluster
```

---

# 23. Testing Thinking

Testing was divided into functional and concurrency testing.

### Functional Testing

Test individual requirements:

```text
Start session
Generate paper
Save answer
Retrieve answer
Recover session
Submit exam
Reject expired answer
Prevent second device
```

### Concurrency Testing

A Python load-testing script was created.

It simulates:

```text
100 concurrent candidates
```

---

# 24. Load Test Result

The local concurrency test produced:

```text
Total candidates: 100
Successful requests: 98
Failed requests: 2
Total execution time: 3.40 seconds
Average response time: 0.403 seconds
```

The two failed requests returned HTTP 409 conflicts associated with existing session/device state.

The result demonstrates that the prototype can process concurrent requests successfully in the local development environment.

It does not claim that a local machine can support 500,000 candidates.

---

# 25. Scaling Beyond the Local Test

A local laptop cannot realistically reproduce a national examination workload.

Therefore, the design uses architectural scaling rather than assuming that one machine can handle everything.

```text
100 candidates
     |
Local concurrency test
     |
     v
Validate implementation
```

For production:

```text
500,000 candidates
        |
        v
Distributed load testing
        |
        v
Multiple API instances
        |
        v
Redis Cluster
        |
        v
Database infrastructure
```

---

# 26. AI Design Thinking

AI should be evaluated using one question:

> Does AI provide a meaningful improvement?

If the answer is no, it should not be added.

Potential useful areas include:

### Infrastructure Anomaly Detection

Detect unusual traffic or infrastructure behavior.

### Candidate Technical Support

Classify candidate-reported technical problems.

### Examination Analytics

Identify unusual patterns for human review.

---

# 27. Why AI Should Not Be Used for Every Feature

Adding AI everywhere can create:

* Higher infrastructure costs
* Additional latency
* More failure points
* Model maintenance requirements
* False positives
* Privacy concerns

Therefore, AI should be introduced selectively.

The goal is to improve outcomes, not simply increase the number of technologies in the architecture.

---

# 28. Key Design Principles

The design follows these principles:

### Principle 1 — Separate Fast and Durable Data

```text
Redis → Fast state
PostgreSQL → Durable state
```

### Principle 2 — Scale Horizontally

```text
Multiple API instances
```

rather than depending on one large server.

### Principle 3 — Move Expensive Work Away From Peak Time

Pre-generate question papers before 10:00 AM.

### Principle 4 — Trust Server Time

Use server-side time for the examination cutoff.

### Principle 5 — Design for Failure

Assume that networks, servers, databases, and clients can fail.

### Principle 6 — Keep the Candidate Experience Simple

Hide infrastructure complexity behind the application.

---

# 29. Final Design

The final conceptual architecture is:

```text
                         Candidates
                       ~500,000 users
                              |
                              v
                    +------------------+
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
                     | Paper Cache    |
                     | Answer State   |
                     | Session Lock   |
                     +----------------+
                              |
                              v
                     +----------------+
                     | Message /      |
                     | Stream Layer   |
                     +----------------+
                              |
                              v
                     +----------------+
                     |  PostgreSQL    |
                     | Durable Store  |
                     +----------------+
                              |
                              v
                       Monitoring &
                        Observability
```

---

# 30. Conclusion

The design process focused on the actual risks created by a national-scale examination rather than simply implementing CRUD APIs.

The most important decisions were:

1. Use Redis for high-frequency state.
2. Use PostgreSQL for durable data.
3. Scale Spring Boot horizontally.
4. Pre-generate question papers before the examination.
5. Use local browser storage for pending offline answers.
6. Use distributed locks for multi-device protection.
7. Use server time for the hard cutoff.
8. Persist answers asynchronously.
9. Test concurrency at development scale.
10. Introduce AI only where it provides measurable value.

The resulting architecture provides a practical path from a working prototype to a production-grade national examination platform.
