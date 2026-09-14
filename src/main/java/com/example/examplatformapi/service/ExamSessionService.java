package com.example.examplatformapi.service;

import com.example.examplatformapi.entity.Candidate;
import com.example.examplatformapi.entity.Exam;
import com.example.examplatformapi.repository.CandidateRepository;
import com.example.examplatformapi.repository.ExamRepository;
import com.example.examplatformapi.repository.ExamSessionRepository;
import com.example.examplatformapi.session.ExamSession;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExamSessionService {

    private final ExamSessionRepository examSessionRepository;
    private final CandidateRepository candidateRepository;
    private final ExamRepository examRepository;
    private final QuestionPaperService questionPaperService;
    private final StringRedisTemplate redisTemplate;

    public ExamSessionService(
            ExamSessionRepository examSessionRepository,
            CandidateRepository candidateRepository,
            ExamRepository examRepository,
            QuestionPaperService questionPaperService,
            StringRedisTemplate redisTemplate) {

        this.examSessionRepository = examSessionRepository;
        this.candidateRepository = candidateRepository;
        this.examRepository = examRepository;
        this.questionPaperService = questionPaperService;
        this.redisTemplate = redisTemplate;
    }

    public ExamSession startExam(
            Long candidateId,
            Long examId,
            String deviceId) {

        Candidate candidate =
                candidateRepository.findById(candidateId)
                        .orElseThrow(() ->
                                new RuntimeException("Candidate not found"));

        Exam exam =
                examRepository.findById(examId)
                        .orElseThrow(() ->
                                new RuntimeException("Exam not found"));

        LocalDateTime now = LocalDateTime.now();

        // --------------------------------------------------
        // Check exam start time
        // --------------------------------------------------

        if (now.isBefore(exam.getStartTime())) {
            throw new RuntimeException("Exam has not started yet");
        }

        // --------------------------------------------------
        // Check exam end time
        // --------------------------------------------------

        if (!now.isBefore(exam.getEndTime())) {
            throw new RuntimeException("Exam has already ended");
        }

        // --------------------------------------------------
        // Redis device lock
        // One candidate + one exam = one active device
        // --------------------------------------------------

        String lockKey =
                "exam-lock:" + candidateId + ":" + examId;

        String existingDevice =
                redisTemplate.opsForValue().get(lockKey);

        // --------------------------------------------------
        // Another device already owns the exam
        // --------------------------------------------------

        if (existingDevice != null &&
                !existingDevice.equals(deviceId)) {

            throw new ActiveExamSessionException(
                    "Candidate already has an active exam session on another device"
            );
        }

        // --------------------------------------------------
        // Try to create distributed Redis lock
        // --------------------------------------------------

        if (existingDevice == null) {

            Duration lockDuration =
                    Duration.between(
                            now,
                            exam.getEndTime()
                    );

            Boolean lockCreated =
                    redisTemplate.opsForValue().setIfAbsent(
                            lockKey,
                            deviceId,
                            lockDuration
                    );

            if (Boolean.FALSE.equals(lockCreated)) {

                String lockedDevice =
                        redisTemplate.opsForValue().get(lockKey);

                if (lockedDevice != null &&
                        !lockedDevice.equals(deviceId)) {

                    throw new ActiveExamSessionException(
                            "Candidate already has an active exam session on another device"
                    );
                }
            }
        }

        // --------------------------------------------------
        // Check database for existing active session
        // --------------------------------------------------

        List<ExamSession> activeSessions =
                examSessionRepository
                        .findAllByCandidateAndExamAndSubmittedFalse(
                                candidate,
                                exam
                        );

        if (!activeSessions.isEmpty()) {

            ExamSession existingSession =
                    activeSessions.get(0);

            if (existingSession.getDeviceId() != null &&
                    existingSession.getDeviceId().equals(deviceId)) {

                return existingSession;
            }

            // Another device has an active database session.
            // Remove our Redis lock because we did not create
            // the actual session.
            String currentLockOwner =
                    redisTemplate.opsForValue().get(lockKey);

            if (deviceId.equals(currentLockOwner)) {
                redisTemplate.delete(lockKey);
            }

            throw new ActiveExamSessionException(
                    "Candidate already has an active exam session on another device"
            );
        }

        // --------------------------------------------------
        // Create new exam session
        // --------------------------------------------------

        ExamSession session = new ExamSession();

        session.setCandidate(candidate);
        session.setExam(exam);
        session.setStartedAt(now);
        session.setExpiresAt(exam.getEndTime());
        session.setSubmitted(false);
        session.setDeviceId(deviceId);

        ExamSession savedSession =
                examSessionRepository.save(session);

        // --------------------------------------------------
        // Generate candidate's question paper
        // --------------------------------------------------

        questionPaperService.generatePaper(
                candidateId,
                examId
        );

        return savedSession;
    }

    public ExamSession recoverSession(
            Long candidateId,
            Long examId,
            String deviceId) {

        Candidate candidate =
                candidateRepository.findById(candidateId)
                        .orElseThrow(() ->
                                new RuntimeException("Candidate not found"));

        Exam exam =
                examRepository.findById(examId)
                        .orElseThrow(() ->
                                new RuntimeException("Exam not found"));

        LocalDateTime now = LocalDateTime.now();

        // --------------------------------------------------
        // Check exam end time
        // --------------------------------------------------

        if (!now.isBefore(exam.getEndTime())) {
            throw new RuntimeException(
                    "Exam time has ended"
            );
        }

        // --------------------------------------------------
        // Redis device lock
        // --------------------------------------------------

        String lockKey =
                "exam-lock:" + candidateId + ":" + examId;

        String existingDevice =
                redisTemplate.opsForValue().get(lockKey);

        if (existingDevice != null &&
                !existingDevice.equals(deviceId)) {

            throw new ActiveExamSessionException(
                    "Exam is currently active on another device"
            );
        }

        // --------------------------------------------------
        // Re-create Redis lock if it does not exist
        // --------------------------------------------------

        if (existingDevice == null) {

            Duration lockDuration =
                    Duration.between(
                            now,
                            exam.getEndTime()
                    );

            Boolean lockCreated =
                    redisTemplate.opsForValue().setIfAbsent(
                            lockKey,
                            deviceId,
                            lockDuration
                    );

            if (Boolean.FALSE.equals(lockCreated)) {

                String lockedDevice =
                        redisTemplate.opsForValue().get(lockKey);

                if (lockedDevice != null &&
                        !lockedDevice.equals(deviceId)) {

                    throw new ActiveExamSessionException(
                            "Exam is currently active on another device"
                    );
                }
            }
        }

        // --------------------------------------------------
        // Find active database session
        // --------------------------------------------------

        List<ExamSession> activeSessions =
                examSessionRepository
                        .findAllByCandidateAndExamAndSubmittedFalse(
                                candidate,
                                exam
                        );

        for (ExamSession session : activeSessions) {

            if (session.getDeviceId() != null &&
                    session.getDeviceId().equals(deviceId)) {

                if (!now.isBefore(session.getExpiresAt())) {

                    session.setSubmitted(true);

                    examSessionRepository.save(session);

                    redisTemplate.delete(lockKey);

                    throw new RuntimeException(
                            "Exam time has ended"
                    );
                }

                return session;
            }
        }

        throw new ActiveExamSessionException(
                "No active exam session found for this device"
        );
    }
}