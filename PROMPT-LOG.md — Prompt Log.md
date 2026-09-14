# Prompt Log

## Live Exam Platform

**Project:** Full Stack Developer Intern Assignment  
**Problem Statement:** 1 — Live Exam Platform  
**Author:** Tejaswini R R

---

# 1. Purpose

This document records the major prompts, questions, design discussions, and implementation decisions used while developing the Live Exam Platform.

The purpose of maintaining this prompt log is to make the development process transparent and reproducible.

---

# 2. Problem Understanding

### Prompt / Question

How would you design a backend for a national-level online examination platform supporting 500,000 candidates simultaneously?

### Key Outcome

The problem was divided into the following major areas:

- Large simultaneous exam start
- Unique randomized question papers
- High-frequency auto-save
- Internet disconnection
- Multiple-device access
- Hard examination cutoff
- Automatic submission
- Scalability
- Reliability
- Security
- AI opportunities

---

# 3. Architecture Decision

### Prompt / Question

How can the system handle 500,000 candidates without relying on a single backend server?

### Decision

Use horizontal scaling with multiple Spring Boot instances behind a load balancer.

Conceptual architecture:

```text
Candidates
    |
    v
Load Balancer
    |
    +---- Backend Instance 1
    +---- Backend Instance 2
    +---- Backend Instance N
              |
              v
          Redis
              |
              v
         PostgreSQL
```

### Reason

A single backend server creates a single point of failure and cannot efficiently handle a large traffic spike.

---

# 4. Redis Decision

### Prompt / Question

How should high-frequency examination data be handled?

### Decision

Use Redis for:

- Answer state
- Question paper caching
- Candidate session locking

Use PostgreSQL for durable storage.

### Reason

Redis provides fast access for high-frequency temporary state, while PostgreSQL provides durable persistence.

---

# 5. Question Paper Generation

### Prompt / Question

How can 500,000 unique randomized papers be served efficiently?

### Decision

Generate a randomized set of question IDs for each candidate and cache the resulting paper in Redis.

The prototype uses:

```text
Question Pool: 10,000
Questions per Paper: 20
```

### Production Improvement

For a national-scale deployment, papers should preferably be generated before the exam starts and stored in a distributed cache.

### Reason

Generating expensive randomized papers at exactly 10:00 AM could create a large CPU and database spike.

---

# 6. Answer Auto-Save

### Prompt / Question

How can the system handle 500,000 candidates auto-saving every 30 seconds?

### Calculation

```text
500,000 / 30
≈ 16,667 operations per second
```

### Decision

Answers are first written to Redis.

A background persistence process transfers the answers to PostgreSQL.

```text
Candidate
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

### Reason

This reduces direct pressure on the PostgreSQL database during traffic spikes.

---

# 7. Internet Disconnection

### Prompt / Question

If a candidate loses internet connectivity for 10 minutes, should answers be lost?

### Decision

No.

The design uses two layers:

1. Server-side Redis state for already synchronized answers.
2. Browser localStorage for answers that are created while offline.

When connectivity returns, pending answers are synchronized with the server.

```text
Internet Lost
     |
     v
Save pending answers locally
     |
     v
Internet Restored
     |
     v
Synchronize with server
```

---

# 8. Multiple Device Protection

### Prompt / Question

What happens if a candidate opens the same exam on two devices?

### Decision

Use a Redis distributed lock.

Example:

```text
exam-lock:29:1
```

The first device obtains the lock.

A second device attempting to start the same exam is rejected.

### Reason

The lock is stored outside the application server, so multiple backend instances can share the same session state.

---

# 9. Hard Cutoff

### Prompt / Question

How can the system enforce the 1:00 PM cutoff fairly when candidates have different network latency?

### Decision

The server clock is authoritative.

The browser clock is not trusted.

Every answer request checks the server-side examination expiry time.

```text
Request
   |
   v
Server checks time
   |
   +---- Before cutoff → Accept
   |
   +---- After cutoff → Reject
```

### Reason

A candidate cannot manipulate their local system clock to extend the examination.

---

# 10. Automatic Submission

### Prompt / Question

What happens if a candidate does not manually submit before the deadline?

### Decision

A scheduled backend service checks for expired sessions and marks them as submitted.

### Reason

This guarantees that all sessions are closed after the official examination period.

---

# 11. Answer Recovery

### Prompt / Question

How can previously saved answers be recovered?

### Decision

The answer retrieval endpoint checks Redis first.

If the answer is not present in Redis, PostgreSQL is checked.

```text
GET Answer
    |
    v
Redis
    |
    +---- Found → Return Answer
    |
    +---- Not Found
             |
             v
         PostgreSQL
```

### Reason

Redis provides fast recovery while PostgreSQL provides durable fallback storage.

---

# 12. Testing Strategy

### Prompt / Question

How can the system be tested without actually simulating 500,000 users on a development laptop?

### Decision

Perform smaller-scale concurrency testing locally and document the production scaling architecture separately.

A Python load-testing script was created to simulate concurrent candidates.

---

# 13. Load Test

The prototype was tested with:

```text
100 concurrent candidates
```

Result:

```text
Total candidates: 100
Successful requests: 98
Failed requests: 2
Total execution time: 3.40 seconds
Average response time: 0.403 seconds
```

The two failed requests returned HTTP 409 conflicts caused by existing session/device state.

This test validates the prototype's concurrent request handling but does not claim that the local machine can support 500,000 candidates.

---

# 14. AI Discussion

### Prompt / Question

Where can AI meaningfully improve the examination platform beyond proctoring?

### Decision

Potential areas include:

- Infrastructure anomaly detection
- Technical support classification
- Examination analytics
- Detection of unusual system behaviour

### Design Principle

AI should only be introduced when it provides measurable value.

Adding AI simply for the sake of using AI could increase:

- Cost
- Complexity
- Latency
- Infrastructure requirements
- Maintenance

---

# 15. Production Improvements Identified

During development, several areas were identified for improvement before a real national-scale deployment.

### Redis

Prototype:

```text
KEYS exam-answer:*
```

Production:

```text
SCAN
or
Redis Streams
```

---

### Question Paper Generation

Prototype:

```text
Generate on demand
```

Production:

```text
Pre-generate before exam
```

---

### Database Queries

Prototype uses individual question lookups.

Production should use batch queries such as:

```text
findAllById(...)
```

to reduce database round trips.

---

### Expired Sessions

Prototype checks active sessions periodically.

Production should query only sessions whose expiry time has passed and use an appropriate database index.

---

### Distributed Lock

Production should use an atomic Redis operation such as:

```text
SET NX
```

with an appropriate expiration time.

---

# 16. Important Trade-Offs

### Redis vs PostgreSQL

Redis provides speed.

PostgreSQL provides durability.

Therefore, both are used for different purposes.

---

### Pre-generation vs On-demand Generation

Pre-generation consumes resources before the exam but reduces the 10:00 AM traffic spike.

Therefore, pre-generation is preferred for production.

---

### Local Storage vs Server Storage

Local storage protects against temporary connectivity problems.

Server storage provides centralized persistence.

Therefore, both are useful together.

---

### AI vs Traditional Rules

Rules are simpler for deterministic requirements such as examination cutoff.

AI is more useful for pattern detection and analysis.

Therefore, deterministic business rules should not unnecessarily be replaced with AI.

---

# 17. Development Philosophy

The development process followed these principles:

1. Understand the requirement before implementing it.
2. Break large problems into smaller components.
3. Build a working prototype first.
4. Test each major feature.
5. Identify scalability limitations.
6. Document production improvements.
7. Avoid unnecessary complexity.

---

# 18. Final Outcome

The prototype demonstrates:

- Spring Boot backend
- PostgreSQL persistence
- Redis integration
- Question paper generation
- Question paper caching
- Exam sessions
- Device locking
- Answer auto-save
- Answer recovery
- Offline pending-answer synchronization
- Automatic submission
- Hard examination cutoff
- Concurrent load testing

The architecture provides a path from the local prototype to a distributed production system capable of handling much larger workloads.