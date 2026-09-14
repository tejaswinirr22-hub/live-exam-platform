# Prompt Log

## Live Exam Platform

### 1. Purpose

AI assistance was used during development to support problem understanding, architecture planning, debugging, documentation, and learning.

The final implementation was reviewed and tested locally.

### 2. Problem Understanding Prompts

Examples of prompts used:

* "Explain the Novintix Live Exam Platform problem statement in simple terms."
* "What are the main technical challenges in supporting 500,000 concurrent candidates?"
* "How should an online examination platform handle internet loss and network latency?"
* "How can a system prevent the same candidate from accessing an exam from two devices?"

### 3. Architecture Prompts

Examples:

* "Design a scalable architecture for a live exam platform using React, Spring Boot, PostgreSQL and Redis."
* "How can Redis be used for candidate session locking?"
* "How should randomized question papers be generated and persisted?"
* "How can the backend enforce the exact exam end time?"
* "How should a system scale horizontally for a very large number of concurrent users?"

### 4. Backend Development Prompts

Examples:

* "Create a Spring Boot REST API for starting an exam session."
* "How should exam session recovery work when the same device reconnects?"
* "Implement a Redis-based device lock for an exam session."
* "Create an endpoint to save candidate answers."
* "How can answer data be temporarily stored in Redis and persisted to PostgreSQL?"
* "How should expired exam sessions be automatically submitted?"

### 5. Frontend Development Prompts

Examples:

* "Create a React examination interface with questions and multiple-choice options."
* "How can React detect online and offline network status?"
* "How can answers be stored temporarily in localStorage when the internet is unavailable?"
* "How should the frontend synchronize pending answers when the connection is restored?"
* "How can the exam timer use the server-provided expiry time?"
* "Add a manual Submit Exam button to the React application."

### 6. Debugging Prompts

Examples:

* "Help debug a Spring Boot Redis connection problem."
* "Why is my Redis test endpoint not working?"
* "Help troubleshoot Maven dependency configuration."
* "Explain how to verify Redis data using redis-cli."
* "Help verify that answers are being persisted from Redis to PostgreSQL."
* "How can I test that expired exam sessions reject new answers?"

### 7. Testing Prompts

Examples:

* "How should I test the exam platform's second-device restriction?"
* "How can I test automatic exam submission after expiry?"
* "How can I verify that the same randomized question paper is returned after recovery?"
* "Create a Python concurrent load test for the exam platform."
* "How should I honestly report a 100-user local load test when the requirement is 500,000 concurrent users?"

### 8. Documentation Prompts

Examples:

* "Create a PRD for the Live Exam Platform assignment."
* "Create an architecture document covering frontend, backend, database, Redis and deployment."
* "Create a design thinking document covering trade-offs and limitations."
* "Create a prompt log for AI-assisted development."

### 9. Important AI-Assisted Decisions

AI assistance helped identify several design approaches:

* Redis for high-speed temporary data.
* Redis locks for candidate/device restriction.
* PostgreSQL for durable persistence.
* Server-side expiry validation.
* Browser local storage for temporary offline answers.
* Persistent question-paper generation.
* Horizontal scaling for production deployment.
* Distributed load testing as the appropriate approach for validating very high concurrency.

These decisions were reviewed against the assignment requirements and tested where possible.

### 10. Human Verification and Testing

AI-generated suggestions were not treated as automatically correct.

The implementation was manually tested by:

* Running the Spring Boot backend.
* Running the React frontend.
* Starting exam sessions.
* Testing device restrictions.
* Testing session recovery.
* Generating question papers.
* Saving answers.
* Verifying Redis values.
* Verifying PostgreSQL records.
* Testing exam expiry.
* Testing automatic submission.
* Testing manual submission.
* Running a 100-candidate concurrent load test.

### 11. Load-Test Transparency

The assignment requirement mentions 500,000 concurrent candidates.

A real 500,000-user load test was not performed on the local development machine because such a test would not represent a realistic production environment.

Instead, the prototype was tested with 100 concurrent simulated candidates.

The production architecture was designed with horizontal scaling, load balancing, Redis, and PostgreSQL to provide a path toward the required scale.

### 12. AI Usage Summary

AI was used as a development assistant for:

* Understanding requirements.
* Learning unfamiliar technologies.
* Generating initial implementation ideas.
* Debugging.
* Test planning.
* Documentation.

The developer remained responsible for integrating, testing, verifying, and making final implementation decisions.
