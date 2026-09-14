package com.example.examplatformapi.repository;

import com.example.examplatformapi.entity.QuestionPaper;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestionPaperRepository extends JpaRepository<QuestionPaper, Long> {

    Optional<QuestionPaper> findByCandidateIdAndExamId(
            Long candidateId,
            Long examId
    );
}