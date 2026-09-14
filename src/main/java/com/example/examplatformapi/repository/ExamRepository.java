package com.example.examplatformapi.repository;

import com.example.examplatformapi.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRepository extends JpaRepository<Exam, Long> {
}