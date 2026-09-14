package com.example.examplatformapi.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "question_papers")
public class QuestionPaper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long candidateId;

    @Column(nullable = false)
    private Long examId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionIds;

    public QuestionPaper() {
    }

    public QuestionPaper(Long candidateId, Long examId, String questionIds) {
        this.candidateId = candidateId;
        this.examId = examId;
        this.questionIds = questionIds;
    }

    public Long getId() {
        return id;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }

    public Long getExamId() {
        return examId;
    }

    public void setExamId(Long examId) {
        this.examId = examId;
    }

    public String getQuestionIds() {
        return questionIds;
    }

    public void setQuestionIds(String questionIds) {
        this.questionIds = questionIds;
    }
}