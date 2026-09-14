# Live Exam Platform

A scalable backend prototype for a national-level online examination platform designed to support large numbers of concurrent candidates, randomized question papers, real-time answer auto-save, internet disconnection recovery, multi-device protection, and a strict exam-time cutoff.

This project was developed as part of the **Full Stack Developer Intern Assignment**.

---

## 1. Problem Statement

The platform is designed for a national-level online examination scenario similar to NEET or GATE.

The system must support:

* Up to **500,000 candidates** appearing simultaneously.
* Exam starting at exactly **10:00 AM**.
* Exam duration of **3 hours**.
* A unique randomized question paper for every candidate.
* A question pool containing **10,000 questions**.
* Automatic answer saving every **30 seconds**.
* Recovery of answers after temporary internet disconnection.
* Prevention of simultaneous examination from multiple devices.
* Strict server-side examination cutoff at **1:00 PM**.
* Automatic submission when the examination ends.

---

## 2. Key Features

### Candidate Session Management

* Creates an examination session for a candidate.
* Assigns a unique device ID to the session.
* Prevents the same candidate from using multiple devices simultaneously.
* Allows an existing candidate to recover their active session.

### Randomized Question Paper

* Questions are selected randomly from the question pool.
* Each candidate receives a separate question paper.
* Generated papers are stored in PostgreSQL.
* Frequently accessed papers are cached in Redis.

### High-Speed Answer Auto-Save

Candidate answers are first written to Redis.

This prevents every answer request from directly hitting PostgreSQL.

The architecture is:

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

This approach reduces database load during simultaneous auto-save operations.

### Internet Disconnection Recovery

The system uses two levels of protection:

1. **Redis** stores the latest server-side answer.
2. **Browser localStorage** stores pending answers when the candidate is offline.

When the connection is restored, pending answers are synchronized with the backend.

Therefore, a temporary internet interruption does not immediately result in loss of answers.

### Strict Exam Cutoff

The backend uses server-side time rather than relying on the candidate's browser clock.

After the examination expiry time:

* New answers are rejected.
* The session is marked as submitted.
* A scheduled background process automatically submits expired sessions.

This prevents candidates from extending the examination by changing their computer clock.

---

## 3. Technology Stack

### Backend

* Java 21
* Spring Boot
* Spring Web
* Spring Data JPA
* Hibernate
* Maven

### Database

* PostgreSQL

### Cache / Fast Data Store

* Redis

### Frontend

* React
* Vite
* JavaScript
* HTML
* CSS

### Testing

* Postman
* Python
* Python Requests
* Concurrent ThreadPoolExecutor

### Development Tools

* IntelliJ IDEA
* Visual Studio Code
* Docker Desktop
* Git
* GitHub

---

## 4. Architecture

The proposed production architecture is:

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

### Why Redis?

Redis is used for high-frequency and temporary examination data such as:

* Candidate answer state
* Question-paper cache
* Session/device locks
* Short-lived examination state

Redis provides much faster access than repeatedly writing every operation directly to PostgreSQL.

### Why PostgreSQL?

PostgreSQL is used as the durable system of record for:

* Candidates
* Exams
* Questions
* Question papers
* Examination sessions
* Candidate answers
* Submission state

---

## 5. Project Structure

```text
exam-platform-api/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/examplatformapi/
│   │   │       │
│   │   │       ├── answer/
│   │   │       ├── controller/
│   │   │       ├── entity/
│   │   │       ├── repository/
│   │   │       ├── service/
│   │   │       └── session/
│   │   │
│   │   └── resources/
│   │       └── application.properties
│   │
│   └── test/
│
├── load-test/
│   └── load-test.py
│
├── pom.xml
├── README.md
└── ...
```

---

## 6. Important API Endpoints

### Start Examination Session

```http
POST /api/sessions/start
```

Parameters:

```text
candidateId
examId
deviceId
```

Example:

```text
POST http://localhost:8080/api/sessions/start?candidateId=29&examId=1&deviceId=test-device
```

---

### Recover Examination Session

```http
GET /api/sessions/recover
```

Parameters:

```text
candidateId
examId
deviceId
```

---

### Generate / Retrieve Question Paper

```http
GET /api/papers/generate
```

Parameters:

```text
candidateId
examId
```

Example:

```text
GET http://localhost:8080/api/papers/generate?candidateId=29&examId=1
```

The endpoint returns the candidate's randomized questions.

---

### Save Answer

```http
POST /api/answers/save
```

Parameters:

```text
sessionId
questionId
selectedAnswer
```

Example:

```text
POST http://localhost:8080/api/answers/save?sessionId=117&questionId=8535&selectedAnswer=A
```

The answer is first saved to Redis.

---

### Retrieve Answer

```http
GET /api/answers/get
```

Parameters:

```text
sessionId
questionId
```

The system checks Redis first and then PostgreSQL if the answer is no longer present in Redis.

---

### Submit Examination

```http
GET /api/submissions/submit
```

Parameters:

```text
sessionId
```

The frontend uses this endpoint to submit the examination.

---

## 7. Redis Design

Redis keys follow a predictable naming convention.

### Question Paper

```text
exam-paper:{candidateId}:{examId}
```

Example:

```text
exam-paper:29:1
```

### Candidate Answer

```text
exam-answer:{sessionId}:{questionId}
```

Example:

```text
exam-answer:117:8535
```

### Device Lock

```text
exam-lock:{candidateId}:{examId}
```

Example:

```text
exam-lock:29:1
```

This lock prevents the same candidate from starting the same examination on another device.

---

## 8. Answer Persistence Strategy

The system avoids sending every answer directly to PostgreSQL.

Instead:

```text
Candidate
   |
   | Answer
   v
Redis
   |
   | Background persistence
   v
PostgreSQL
```

A scheduled Spring Boot process periodically transfers Redis answers into PostgreSQL.

This provides:

* Faster answer saving
* Reduced database pressure
* Better handling of traffic spikes
* Temporary server-side persistence during network problems

---

## 9. Automatic Submission

The system contains a scheduled service that checks examination sessions.

Conceptually:

```text
Every second
     |
     v
Check active sessions
     |
     v
Is expiresAt <= current server time?
     |
   Yes
     |
     v
Mark session as submitted
```

The candidate's browser cannot extend the examination by changing its local clock because the backend determines whether an answer is accepted.

---

## 10. Handling 500,000 Candidates

A single Spring Boot server should not be expected to handle 500,000 simultaneous candidates.

The production design uses horizontal scaling.

```text
500,000 Candidates
        |
        v
Load Balancer
        |
        +-------------------+
        |                   |
        v                   v
 API Instance 1       API Instance 2
        |                   |
        +---------+---------+
                  |
                  v
               Redis
                  |
                  v
             PostgreSQL
```

Additional instances can be added when traffic increases.

Redis provides shared state across application instances, allowing candidates to move between backend instances without losing their session state.

---

## 11. Handling 500,000 Simultaneous Auto-Saves

The examination platform must avoid a situation where all 500,000 candidates directly write to PostgreSQL every 30 seconds.

Instead:

```text
500,000 Auto-Save Requests
            |
            v
      Load Balancer
            |
            v
      Spring Boot APIs
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

The production implementation can further use techniques such as:

* Redis Streams
* Message queues
* Batch database writes
* Multiple persistence workers
* Database connection pooling
* PostgreSQL indexing
* Horizontal API scaling

---

## 12. Network Disconnection Handling

If a candidate loses internet connectivity:

```text
Candidate goes offline
        |
        v
Answer stored locally
        |
        v
Candidate continues exam
        |
        v
Internet restored
        |
        v
Pending answers synchronized
        |
        v
Server / Redis
```

Previously saved server-side answers remain available.

Therefore, a 10-minute network interruption does not automatically mean that all previous answers are lost.

---

## 13. Multi-Device Protection

Each examination session is associated with a device ID.

Example:

```text
Candidate 29
     |
     +---- Device A -> Active session
     |
     +---- Device B -> Rejected
```

A Redis lock is used to coordinate this across multiple backend instances.

This prevents a candidate from simultaneously answering from two devices.

---

## 14. Fair 1:00 PM Cutoff

The cutoff is enforced using the backend server's time.

The browser clock is not trusted.

For every answer request:

```text
Current server time < exam expiry
```

Only then is the answer accepted.

After the cutoff:

```text
Answer Request
      |
      v
Server checks time
      |
      v
Exam expired
      |
      v
Answer rejected
```

This provides a consistent cutoff even when candidates have different network latency.

---

## 15. Load Test

A Python-based concurrency test was created to simulate multiple candidates starting their examination sessions simultaneously.

The test uses:

* Python
* Requests
* ThreadPoolExecutor
* 100 concurrent candidates

Command:

```powershell
py load-test/load-test.py
```

### Local Test Result

The local test produced:

```text
Total candidates: 100
Successful requests: 98
Failed requests: 2
Total execution time: 3.40 seconds
Average response time: 0.403 seconds
```

Success rate:

```text
98%
```

The two HTTP `409 Conflict` responses were related to existing candidate/session conflicts rather than a connection failure.

### Important Note

This local test does **not** claim that the laptop can process 500,000 candidates.

It demonstrates the working concurrency pattern at a smaller scale.

The 500,000-candidate requirement is addressed through the proposed horizontally scalable production architecture.

---

## 16. AI Opportunities

AI should only be introduced where it provides measurable value.

Possible useful applications include:

### Intelligent Anomaly Detection

AI can detect unusual examination behavior such as:

* Abnormal answer timing
* Sudden repeated answer changes
* Unusual navigation patterns
* Suspicious session behavior

### System Monitoring

Machine-learning-based anomaly detection could identify unusual:

* Request spikes
* API latency
* Redis failures
* Database load patterns

### Candidate Support

AI could analyze technical issues and identify whether a candidate's problem is likely related to:

* Network connectivity
* Browser problems
* Session problems
* Device problems

AI should not be added simply for the sake of making the system "AI-powered."

---

## 17. Running the Backend

### Prerequisites

Install:

* Java 21
* Maven
* PostgreSQL
* Redis
* Docker Desktop
* IntelliJ IDEA

### PostgreSQL

Create a database:

```sql
CREATE DATABASE exam_platform;
```

Update the credentials in:

```text
src/main/resources/application.properties
```

Example:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/exam_platform
spring.datasource.username=postgres
spring.datasource.password=test123
```

### Redis

Start Redis using Docker:

```powershell
docker start exam-platform-redis
```

Verify:

```powershell
docker exec -it exam-platform-redis redis-cli ping
```

Expected:

```text
PONG
```

### Run Spring Boot

Run:

```text
ExamPlatformApiApplication
```

The backend starts on:

```text
http://localhost:8080
```

---

## 18. Running the Frontend

The React frontend uses Vite.

Navigate to the frontend project and run:

```powershell
npm install
```

Then:

```powershell
npm run dev
```

The frontend is available at:

```text
http://localhost:5173
```

---

## 19. Testing Redis

A Redis test endpoint is available:

```http
GET /redis-test
```

Example:

```text
http://localhost:8080/redis-test
```

Expected response:

```text
Redis is working!
```

---

## 20. Testing the System

Recommended testing flow:

### Step 1

Start PostgreSQL.

### Step 2

Start Redis:

```powershell
docker start exam-platform-redis
```

### Step 3

Start the Spring Boot application.

### Step 4

Start the React frontend.

### Step 5

Open:

```text
http://localhost:5173
```

### Step 6

Start/recover the examination session.

### Step 7

Select an answer.

### Step 8

Verify the answer is saved.

### Step 9

Test answer recovery.

### Step 10

Run the concurrency test:

```powershell
py load-test/load-test.py
```

---

## 21. Production Improvements

The current project is a functional prototype demonstrating the major architectural concepts.

For a true national-scale production deployment, the following improvements would be recommended:

* Redis Cluster
* PostgreSQL read replicas
* Database partitioning
* Connection pooling
* Message queues / Redis Streams
* Kubernetes or equivalent container orchestration
* Horizontal autoscaling
* CDN for static assets
* Distributed monitoring
* Centralized logging
* Rate limiting
* Authentication and authorization
* Encryption
* Disaster recovery
* Multi-region deployment
* Health checks
* Circuit breakers
* Distributed tracing
* Production-grade session management

---

## 22. Design Trade-offs

### Redis vs PostgreSQL for Answer Saves

**Redis**

Advantages:

* Very fast
* Handles high-frequency writes
* Reduces database pressure

Trade-off:

* Requires persistence/recovery strategy.

**PostgreSQL**

Advantages:

* Durable
* Reliable
* Suitable as the system of record

Trade-off:

* Directly receiving every high-frequency auto-save creates significant database load.

Therefore, this project uses Redis for fast writes and PostgreSQL for durable persistence.

---

## 23. Security Considerations

A production deployment should include:

* HTTPS
* Secure authentication
* JWT/session-based authorization
* Candidate-specific authorization
* Input validation
* Rate limiting
* SQL injection protection
* Secure Redis configuration
* Database encryption
* Audit logging
* Device/session validation

Candidate answers should only be accessible by the corresponding authenticated candidate/session.

---

## 24. Project Status

### Problem 1 — Live Exam Platform

Implemented:

* [x] Spring Boot backend
* [x] PostgreSQL integration
* [x] Redis integration
* [x] Candidate sessions
* [x] Device/session locking
* [x] Randomized question papers
* [x] Redis question-paper caching
* [x] Answer auto-save
* [x] Background answer persistence
* [x] Answer recovery
* [x] Internet disconnection handling
* [x] Server-side exam cutoff
* [x] Automatic submission
* [x] React frontend
* [x] 100-candidate concurrency test

---

## 25. Author

**Tejaswini R R**

B.Tech — Computer Science and Engineering (Artificial Intelligence & Machine Learning)

---

## 26. Conclusion

This project demonstrates a backend architecture for a large-scale online examination platform.

The main design principle is to avoid treating PostgreSQL as the destination for every real-time operation.

Instead:

```text
Redis
→ handles fast, high-frequency state

PostgreSQL
→ stores durable examination data

Spring Boot
→ provides the application and business logic

Load Balancer + Multiple Instances
→ provides horizontal scalability
```

The local implementation demonstrates the core functionality, while the architecture is designed to scale toward the **500,000 concurrent candidate** requirement through distributed infrastructure and horizontal scaling.
