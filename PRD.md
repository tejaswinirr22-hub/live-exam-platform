# Product Requirements Document (PRD)

## Live Exam Platform

### 1. Product Overview

The Live Exam Platform is a web-based examination system designed to support large-scale online examinations. The system is designed with scalability, reliability, exam-time enforcement, auto-saving, session recovery, and secure candidate access in mind.

The prototype implements a React frontend, Spring Boot backend, PostgreSQL database, and Redis caching layer.

### 2. Problem Statement

The platform must support a live examination where up to 500,000 candidates may participate simultaneously.

The examination:

* Starts exactly at 10:00 AM.
* Runs for 3 hours.
* Ends automatically at 1:00 PM.
* Provides each candidate with a unique randomized question paper selected from a pool of 10,000 questions.
* Automatically saves answers every 30 seconds.
* Must handle temporary internet connectivity loss.
* Must prevent simultaneous access from multiple devices.
* Must enforce the server-side examination deadline.

### 3. Goals

The main goals are:

* Support large-scale concurrent candidates through a horizontally scalable architecture.
* Provide each candidate with a consistent randomized question paper.
* Prevent candidates from accessing the examination after the deadline.
* Save answers reliably during the examination.
* Allow recovery after temporary network failures.
* Prevent the same candidate from using multiple devices simultaneously.
* Provide a simple and responsive examination interface.

### 4. Scope

#### Included

* Candidate exam session creation.
* Candidate/device locking.
* Session recovery on the same device.
* Randomized question-paper generation.
* Redis caching for frequently accessed data.
* PostgreSQL persistence.
* Answer saving and persistence.
* 30-second answer synchronization.
* Local browser storage for temporary offline answers.
* Internet connection status detection.
* Automatic exam submission at the deadline.
* Manual exam submission.
* Server-side validation of exam expiry.
* Basic concurrent load testing.

#### Excluded

The following are outside the prototype scope:

* Production deployment to a cloud provider.
* Real authentication/identity verification.
* Online proctoring.
* Webcam monitoring.
* Payment functionality.
* Advanced AI-based cheating detection.
* Production-grade distributed load testing for 500,000 real users.

### 5. Functional Requirements

#### FR1 — Start Exam

The candidate should be able to start an examination using their candidate ID, exam ID, and device ID.

#### FR2 — Device Restriction

Only one active device should be allowed for a candidate's examination session.

If another device attempts to access the same active examination, the request should be rejected.

The original device should be able to recover the existing session.

#### FR3 — Random Question Paper

The system should select a fixed set of questions from the available question pool.

Once generated, the question paper should remain consistent for the candidate during the examination.

#### FR4 — Display Questions

The frontend should display the candidate's assigned questions and available answer options.

Correct answers must not be exposed to the frontend.

#### FR5 — Save Answers

Candidate answers should be saved automatically.

The frontend attempts synchronization every 30 seconds, while the backend can temporarily store answers in Redis before persistence to PostgreSQL.

#### FR6 — Offline Handling

If the candidate temporarily loses internet connectivity:

* The selected answer should remain in browser local storage.
* The candidate should continue interacting with the loaded questions.
* Pending answers should be synchronized when the connection is restored.

#### FR7 — Exam Timer

The examination timer should be based on the server-provided session expiry time rather than relying only on the candidate's local clock.

#### FR8 — Automatic Submission

When the examination reaches its deadline, the session must be marked as submitted.

Answers received after the deadline must be rejected by the backend.

#### FR9 — Manual Submission

The candidate should be able to submit the examination before the deadline.

### 6. Non-Functional Requirements

#### Scalability

The production architecture should support horizontal scaling of backend instances behind a load balancer.

#### Reliability

Candidate answers should not depend only on browser memory. Answers should be persisted through Redis and PostgreSQL.

#### Performance

Frequently accessed data such as question papers and temporary answers should use Redis to reduce database load.

#### Consistency

A candidate should receive the same generated question paper when recovering an existing session.

#### Security

The backend must perform server-side validation for session state, device access, answer validity, and exam expiry.

#### Availability

The production design should avoid a single backend instance becoming a single point of failure.

### 7. Technology Stack

| Layer                | Technology                             |
| -------------------- | -------------------------------------- |
| Frontend             | React + Vite                           |
| Backend              | Spring Boot                            |
| Programming Language | Java 21                                |
| Database             | PostgreSQL                             |
| Cache                | Redis                                  |
| Build Tool           | Maven                                  |
| Containerization     | Docker                                 |
| Load Testing         | Python + Requests + ThreadPoolExecutor |

### 8. Data Requirements

The main entities are:

* Candidate
* Exam
* Question
* Question Paper
* Exam Session
* Candidate Answer

Redis is used for:

* Candidate exam locks.
* Generated question-paper caching.
* Temporary answer caching.

PostgreSQL is used for durable storage.

### 9. Success Criteria

The prototype is considered successful when:

* Candidates can start an exam session.
* A randomized 20-question paper is generated.
* The same paper can be recovered.
* A second device is rejected.
* The original device can recover its session.
* Answers can be saved.
* Answers are persisted to PostgreSQL.
* Temporary internet loss can be handled through local storage and synchronization.
* Expired sessions are automatically submitted.
* Answers after the deadline are rejected.
* The application can handle a basic concurrent load test.

### 10. Prototype Testing

A local concurrent test was performed with 100 simulated candidates.

Observed result:

* Total candidates: 100
* Successful requests: 98
* Failed requests: 2
* Total execution time: approximately 3.40 seconds
* Average response time: approximately 0.403 seconds

The two failed requests were related to existing session/device state.

This test is a prototype validation and **not** a claim that 500,000 users were tested locally.

### 11. Future Improvements

For production deployment, the platform could be improved with:

* Cloud-based horizontal scaling.
* Load balancers.
* Multiple Spring Boot instances.
* Redis Cluster.
* PostgreSQL replication and connection pooling.
* Distributed load testing.
* Message queues or Redis Streams for answer persistence.
* Strong authentication and authorization.
* Monitoring and observability.
* Rate limiting.
* Production-grade security.
* Disaster recovery and backups.
