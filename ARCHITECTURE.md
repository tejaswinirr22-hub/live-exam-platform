# System Architecture Document

## Live Exam Platform

### 1. Architecture Overview

The Live Exam Platform uses a layered architecture consisting of:

* React frontend
* Spring Boot REST API
* Redis caching and temporary state
* PostgreSQL persistent database

The architecture is designed to support horizontal scaling for a large number of concurrent candidates.

```text
                    Candidates
                        |
                        v
                +----------------+
                | Load Balancer  |
                +----------------+
                        |
             +----------+----------+
             |          |          |
             v          v          v
        +---------+ +---------+ +---------+
        | Spring  | | Spring  | | Spring  |
        | Boot #1 | | Boot #2 | | Boot #N |
        +---------+ +---------+ +---------+
             |          |          |
             +----------+----------+
                        |
              +---------+---------+
              |                   |
              v                   v
        +-----------+       +-------------+
        |   Redis   |       | PostgreSQL  |
        |   Cache   |       |  Database   |
        +-----------+       +-------------+
```

### 2. Frontend Architecture

The frontend is developed using React and Vite.

The frontend is responsible for:

* Displaying examination questions.
* Displaying answer options.
* Maintaining the examination timer.
* Capturing candidate answers.
* Detecting online/offline status.
* Temporarily storing pending answers in browser local storage.
* Synchronizing answers with the backend.
* Allowing manual submission.

The frontend communicates with the backend through REST APIs.

### 3. Backend Architecture

The backend is implemented using Spring Boot and Java 21.

The backend provides REST APIs for:

* Starting examination sessions.
* Recovering existing sessions.
* Generating question papers.
* Saving answers.
* Retrieving saved answers.
* Submitting examinations.

The backend is responsible for authoritative validation.

Important validations include:

* Candidate existence.
* Exam existence.
* Device/session ownership.
* Session submission state.
* Answer validity.
* Examination expiry.

### 4. Redis Architecture

Redis is used for temporary, high-speed data access.

The prototype uses Redis for:

#### Candidate Lock

```text
exam-lock:<candidateId>:<examId>
```

This prevents multiple active devices from using the same examination session.

#### Question Paper Cache

```text
exam-paper:<candidateId>:<examId>
```

This stores the generated question IDs so the same candidate receives the same paper during recovery.

#### Temporary Answers

```text
exam-answer:<sessionId>:<questionId>
```

Answers can be stored temporarily in Redis before being persisted to PostgreSQL.

### 5. PostgreSQL Architecture

PostgreSQL provides durable storage.

Main entities include:

```text
Candidate
    |
    +---- Exam Session
              |
              +---- Candidate Answer

Exam
    |
    +---- Question Paper
              |
              +---- Questions

Question
    |
    +---- Question Paper
```

Important database tables include:

* `candidates`
* `exams`
* `questions`
* `question_papers`
* `exam_sessions`
* `candidate_answers`

PostgreSQL is treated as the persistent source of truth.

### 6. Question Paper Generation

The system contains a pool of approximately 10,000 questions.

When a candidate starts the examination:

1. The backend checks whether a paper already exists.
2. Redis is checked first.
3. PostgreSQL is used as a fallback.
4. If no paper exists, available question IDs are retrieved.
5. The IDs are randomized.
6. A fixed number of questions is selected.
7. The generated paper is saved.
8. The paper is cached in Redis.

For the prototype, each candidate receives 20 questions.

The question paper is persisted so that recovery does not generate a different paper.

### 7. Exam Session Flow

```text
Candidate
    |
    v
Start Exam Request
    |
    v
Check Candidate + Exam
    |
    v
Check Existing Device Lock
    |
    +---- Existing same device ---> Recover Session
    |
    +---- Different device -------> Reject
    |
    v
Create Exam Session
    |
    v
Generate/Load Question Paper
    |
    v
Return Session + Questions + Expiry
```

### 8. Answer Saving Flow

```text
Candidate selects answer
          |
          v
Frontend stores answer locally
          |
          v
Send answer to backend
          |
          v
Backend validates session
          |
          v
Check exam expiry
          |
          v
Store temporary answer in Redis
          |
          v
Persist answer to PostgreSQL
```

The frontend attempts synchronization every 30 seconds.

If the internet connection is temporarily unavailable, pending answers remain in local storage and are synchronized when connectivity is restored.

### 9. Exam Expiry and Auto Submission

The server provides the session expiry time to the frontend.

The frontend uses this value to display the remaining time.

The backend also validates the current server time whenever an answer is submitted.

Therefore, changing the candidate's local system clock cannot extend the examination.

A scheduled backend process checks unsubmitted sessions and automatically marks expired sessions as submitted.

### 10. Multiple Device Handling

A Redis-based candidate/exam lock is used to prevent concurrent access.

Example:

```text
Candidate 29
     |
     +---- Device A ---> Allowed
     |
     +---- Device B ---> Rejected
```

If Device A reconnects, it can recover the existing session.

### 11. Handling Network Failures

The platform uses multiple layers of protection.

#### Browser Layer

Selected answers are temporarily stored in local storage.

#### Backend Layer

Answers can be stored temporarily in Redis.

#### Persistent Layer

Answers are eventually persisted to PostgreSQL.

This reduces the risk of losing answers during temporary connectivity problems.

### 12. Scalability Strategy

The prototype runs locally, but the production architecture is designed for horizontal scaling.

For 500,000 candidates, the expected architecture would use:

* CDN for static frontend assets.
* Load balancer.
* Multiple Spring Boot application instances.
* Redis Cluster.
* PostgreSQL with read replicas where appropriate.
* Connection pooling.
* Message queues or Redis Streams for high-volume answer persistence.
* Monitoring and centralized logging.

Application servers should remain stateless wherever possible so that any instance can handle a request.

### 13. Deployment Strategy

A production deployment could use:

```text
Internet
   |
   v
CDN
   |
   v
Load Balancer
   |
   +-------------------------------+
   |               |               |
   v               v               v
Backend 1       Backend 2       Backend N
   |               |               |
   +---------------+---------------+
                   |
          +--------+--------+
          |                 |
          v                 v
      Redis Cluster     PostgreSQL
```

Docker can be used to package application components.

A cloud platform could provide:

* Auto scaling.
* Load balancing.
* Managed PostgreSQL.
* Managed Redis.
* Monitoring.
* Logging.
* Backup and disaster recovery.

### 14. Security Considerations

The production implementation should include:

* Secure authentication.
* Authorization.
* HTTPS.
* Secure session management.
* Input validation.
* Rate limiting.
* Protection against duplicate requests.
* Database access controls.
* Redis access controls.
* Secrets management.
* Audit logging.

Correct answers should never be sent to the browser during an active examination.

### 15. Load Testing

The prototype includes a Python load-testing script.

It was tested with:

```text
100 concurrent candidates
```

Observed prototype result:

```text
Successful requests: 98
Failed requests: 2
Total execution time: 3.40 seconds
Average response time: 0.403 seconds
```

The two failed requests were associated with existing session/device state.

This local test should not be interpreted as a 500,000-user production test.

A real 500,000-user validation would require distributed cloud-based load testing.

### 16. Known Prototype Limitations

The current prototype has some limitations:

* Local development environment.
* Basic development security configuration.
* Scheduler-based persistence is suitable for demonstration but requires a more scalable approach for production.
* Large-scale distributed testing has not been performed.
* Production authentication is not implemented.
* Redis and PostgreSQL are single local instances.
* Production monitoring and observability are not implemented.

### 17. Future Improvements

With additional development time, the following improvements would be implemented:

1. Production authentication and authorization.
2. Distributed Redis deployment.
3. PostgreSQL replication.
4. Queue-based answer persistence.
5. Distributed load testing.
6. Advanced monitoring.
7. Automated CI/CD deployment.
8. Disaster recovery.
9. Better idempotency and retry handling.
10. Production-grade security controls.
11. Advanced anti-cheating mechanisms.
12. Cloud auto-scaling.

### 18. Technology Summary

| Component        | Technology   |
| ---------------- | ------------ |
| Frontend         | React + Vite |
| Backend          | Spring Boot  |
| Language         | Java 21      |
| Database         | PostgreSQL   |
| Cache            | Redis        |
| Build            | Maven        |
| Containerization | Docker       |
| Load Testing     | Python       |
| API Style        | REST         |

### 19. Architecture Decision Summary

The architecture separates responsibilities between the frontend, backend, cache, and database.

React provides the candidate interface.

Spring Boot provides authoritative business logic and validation.

Redis handles high-speed temporary data and distributed locks.

PostgreSQL provides durable persistence.

This separation allows the backend to scale horizontally while reducing unnecessary database load.
