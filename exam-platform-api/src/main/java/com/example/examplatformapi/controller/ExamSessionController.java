package com.example.examplatformapi.controller;

import com.example.examplatformapi.service.ExamSessionService;
import com.example.examplatformapi.session.ExamSession;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
public class ExamSessionController {

    private final ExamSessionService examSessionService;

    public ExamSessionController(ExamSessionService examSessionService) {
        this.examSessionService = examSessionService;
    }

    @PostMapping("/start")
    public ExamSession startExam(
            @RequestParam Long candidateId,
            @RequestParam Long examId,
            @RequestParam String deviceId) {

        return examSessionService.startExam(
                candidateId,
                examId,
                deviceId
        );
    }

    @GetMapping("/recover")
    public ExamSession recoverSession(
            @RequestParam Long candidateId,
            @RequestParam Long examId,
            @RequestParam String deviceId) {

        return examSessionService.recoverSession(
                candidateId,
                examId,
                deviceId
        );
    }
}