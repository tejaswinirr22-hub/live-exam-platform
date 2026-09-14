package com.example.examplatformapi.service;

import com.example.examplatformapi.repository.ExamSessionRepository;
import com.example.examplatformapi.session.ExamSession;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExamAutoSubmitService {

    private final ExamSessionRepository examSessionRepository;

    public ExamAutoSubmitService(
            ExamSessionRepository examSessionRepository) {
        this.examSessionRepository = examSessionRepository;
    }

    /*
     * Checks every second for exam sessions whose
     * expiry time has been reached.
     *
     * This uses SERVER TIME, not the candidate's
     * browser/device time.
     */
    @Scheduled(fixedRate = 1000)
    public void autoSubmitExpiredSessions() {

        LocalDateTime now = LocalDateTime.now();

        List<ExamSession> sessions =
                examSessionRepository.findAllBySubmittedFalse();

        for (ExamSession session : sessions) {

            if (!now.isBefore(session.getExpiresAt())) {

                session.setSubmitted(true);

                examSessionRepository.save(session);

                System.out.println(
                        "Exam session "
                                + session.getId()
                                + " automatically submitted at "
                                + now
                );
            }
        }
    }
}