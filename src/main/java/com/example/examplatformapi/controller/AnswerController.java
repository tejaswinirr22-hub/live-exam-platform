package com.example.examplatformapi.controller;

import com.example.examplatformapi.entity.Question;
import com.example.examplatformapi.repository.CandidateAnswerRepository;
import com.example.examplatformapi.repository.ExamSessionRepository;
import com.example.examplatformapi.repository.QuestionRepository;
import com.example.examplatformapi.service.RedisService;
import com.example.examplatformapi.session.ExamSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/answers")
public class AnswerController {

    private final ExamSessionRepository examSessionRepository;
    private final QuestionRepository questionRepository;
    private final CandidateAnswerRepository candidateAnswerRepository;
    private final RedisService redisService;

    public AnswerController(
            ExamSessionRepository examSessionRepository,
            QuestionRepository questionRepository,
            CandidateAnswerRepository candidateAnswerRepository,
            RedisService redisService) {

        this.examSessionRepository = examSessionRepository;
        this.questionRepository = questionRepository;
        this.candidateAnswerRepository = candidateAnswerRepository;
        this.redisService = redisService;
    }

    @PostMapping("/save")
    @ResponseStatus(HttpStatus.OK)
    public AnswerResponse saveAnswer(
            @RequestParam Long sessionId,
            @RequestParam Long questionId,
            @RequestParam String selectedAnswer) {

        ExamSession session =
                examSessionRepository.findById(sessionId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Exam session not found"
                                ));

        LocalDateTime serverTime = LocalDateTime.now();

        /*
         * Once the exam is submitted, no more answers are accepted.
         */
        if (session.isSubmitted()) {

            return new AnswerResponse(
                    false,
                    "Answer rejected: exam has already been submitted",
                    serverTime
            );
        }

        /*
         * Server-side cutoff check.
         * Browser time is NOT trusted.
         */
        if (!serverTime.isBefore(session.getExpiresAt())) {

            session.setSubmitted(true);
            examSessionRepository.save(session);

            return new AnswerResponse(
                    false,
                    "Answer rejected: exam time has ended",
                    serverTime
            );
        }

        /*
         * Validate answer.
         */
        if (selectedAnswer == null ||
                !selectedAnswer.matches("[ABCD]")) {

            return new AnswerResponse(
                    false,
                    "Invalid answer. Please select A, B, C or D.",
                    serverTime
            );
        }

        /*
         * Make sure the question exists.
         */
        questionRepository.findById(questionId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Question not found"
                        ));

        /*
         * Save answer immediately to Redis.
         *
         * PostgreSQL persistence happens asynchronously
         * through AnswerPersistenceService.
         */
        redisService.setAnswer(
                sessionId.toString(),
                questionId.toString(),
                selectedAnswer
        );

        return new AnswerResponse(
                true,
                "Answer saved successfully to Redis",
                serverTime
        );
    }

    @GetMapping("/get")
    public String getAnswer(
            @RequestParam Long sessionId,
            @RequestParam Long questionId) {

        /*
         * First check Redis because it is the
         * fastest answer store.
         */
        String answer =
                redisService.getAnswer(
                        sessionId.toString(),
                        questionId.toString()
                );

        if (answer != null) {
            return answer;
        }

        /*
         * If Redis no longer contains the answer,
         * check PostgreSQL.
         */
        ExamSession session =
                examSessionRepository.findById(sessionId)
                        .orElse(null);

        Question question =
                questionRepository.findById(questionId)
                        .orElse(null);

        if (session == null || question == null) {
            return "No answer saved";
        }

        return candidateAnswerRepository
                .findByExamSessionAndQuestion(
                        session,
                        question
                )
                .map(candidateAnswer ->
                        candidateAnswer.getSelectedAnswer())
                .orElse("No answer saved");
    }

    public record AnswerResponse(
            boolean success,
            String message,
            LocalDateTime serverTime
    ) {
    }
}