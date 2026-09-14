package com.example.examplatformapi.repository;

import com.example.examplatformapi.answer.CandidateAnswer;
import com.example.examplatformapi.session.ExamSession;
import com.example.examplatformapi.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CandidateAnswerRepository extends JpaRepository<CandidateAnswer, Long> {

    Optional<CandidateAnswer> findByExamSessionAndQuestion(
            ExamSession examSession,
            Question question
    );
}