package com.example.examplatformapi.controller;

import com.example.examplatformapi.repository.ExamSessionRepository;
import com.example.examplatformapi.session.ExamSession;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final ExamSessionRepository examSessionRepository;

    public SubmissionController(ExamSessionRepository examSessionRepository) {
        this.examSessionRepository = examSessionRepository;
    }

    @GetMapping("/submit")
    public String submitExam(@RequestParam Long sessionId) {

        ExamSession session = examSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Exam session not found"));

        LocalDateTime now = LocalDateTime.now();

        if (session.isSubmitted()) {
            return "Exam already submitted";
        }

        if (!now.isBefore(session.getExpiresAt())) {
            session.setSubmitted(true);
            examSessionRepository.save(session);

            return "Exam force-submitted because the time limit has ended";
        }

        session.setSubmitted(true);
        examSessionRepository.save(session);

        return "Exam submitted successfully";
    }
}