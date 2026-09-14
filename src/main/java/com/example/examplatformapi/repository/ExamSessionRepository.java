package com.example.examplatformapi.repository;

import com.example.examplatformapi.entity.Candidate;
import com.example.examplatformapi.entity.Exam;
import com.example.examplatformapi.session.ExamSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {

    List<ExamSession> findAllByCandidateAndExamAndSubmittedFalse(
            Candidate candidate,
            Exam exam
    );

    long countByCandidateAndExamAndSubmittedFalse(
            Candidate candidate,
            Exam exam
    );

    List<ExamSession> findAllBySubmittedFalse();
}