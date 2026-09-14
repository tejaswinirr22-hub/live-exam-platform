package com.example.examplatformapi;

import com.example.examplatformapi.entity.Candidate;
import com.example.examplatformapi.entity.Exam;
import com.example.examplatformapi.repository.CandidateRepository;
import com.example.examplatformapi.repository.ExamRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner loadData(
            CandidateRepository candidateRepository,
            ExamRepository examRepository) {

        return args -> {

            /*
             * ---------------------------------------------------------
             * CREATE TEST CANDIDATE ONLY IF ONE DOES NOT EXIST
             * ---------------------------------------------------------
             */

            if (candidateRepository.count() == 0) {

                candidateRepository.save(
                        new Candidate(
                                "Test Candidate",
                                "candidate@example.com"
                        )
                );

                System.out.println(
                        "Test Candidate created successfully."
                );

            } else {

                System.out.println(
                        "Candidate data already exists. No changes made."
                );
            }


            /*
             * ---------------------------------------------------------
             * CREATE DEVELOPMENT EXAM ONLY IF ONE DOES NOT EXIST
             * ---------------------------------------------------------
             *
             * IMPORTANT:
             * We do NOT update the exam time when the application
             * restarts.
             *
             * This prevents the exam schedule from changing whenever
             * Spring Boot is restarted.
             */

            if (examRepository.count() == 0) {

                LocalDateTime startTime =
                        LocalDateTime.now().plusMinutes(5);

                LocalDateTime endTime =
                        startTime.plusHours(3);

                Exam exam =
                        new Exam(
                                "National Mock Exam",
                                startTime,
                                endTime,
                                true
                        );

                examRepository.save(exam);

                System.out.println(
                        "Development exam created successfully."
                );

                System.out.println(
                        "Development exam start time: "
                                + startTime
                );

                System.out.println(
                        "Development exam end time: "
                                + endTime
                );

            } else {

                Exam exam =
                        examRepository.findAll().get(0);

                System.out.println(
                        "Exam already exists. Existing schedule preserved."
                );

                System.out.println(
                        "Existing exam start time: "
                                + exam.getStartTime()
                );

                System.out.println(
                        "Existing exam end time: "
                                + exam.getEndTime()
                );
            }


            /*
             * ---------------------------------------------------------
             * QUESTION DATA
             * ---------------------------------------------------------
             *
             * QuestionDataGenerator.java is responsible for creating
             * the question pool.
             *
             * Therefore, this class does NOT create or modify questions.
             */

            System.out.println(
                    "Question data is managed by QuestionDataGenerator."
            );

            System.out.println(
                    "Sample exam data initialization completed."
            );
        };
    }
}