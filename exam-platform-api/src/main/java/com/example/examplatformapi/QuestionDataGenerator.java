package com.example.examplatformapi;

import com.example.examplatformapi.entity.Question;
import com.example.examplatformapi.repository.QuestionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class QuestionDataGenerator implements CommandLineRunner {

    private static final int TARGET_QUESTION_COUNT = 10_000;
    private static final int BATCH_SIZE = 500;

    private final QuestionRepository questionRepository;

    public QuestionDataGenerator(
            QuestionRepository questionRepository) {

        this.questionRepository = questionRepository;
    }

    @Override
    public void run(String... args) {

        long existingQuestions = questionRepository.count();

        System.out.println(
                "Current question count: " + existingQuestions
        );

        if (existingQuestions >= TARGET_QUESTION_COUNT) {

            System.out.println(
                    "Question pool already contains "
                            + existingQuestions
                            + " questions."
            );

            return;
        }

        System.out.println(
                "Generating questions until the pool reaches "
                        + TARGET_QUESTION_COUNT
                        + " questions..."
        );

        long questionsToCreate =
                TARGET_QUESTION_COUNT - existingQuestions;

        long startNumber = existingQuestions + 1;

        List<Question> batch = new ArrayList<>();

        for (long i = startNumber;
             i <= TARGET_QUESTION_COUNT;
             i++) {

            String subject;
            String difficulty;

            // Rotate subjects
            if (i % 4 == 0) {
                subject = "Java";
            } else if (i % 4 == 1) {
                subject = "Database";
            } else if (i % 4 == 2) {
                subject = "Computer Science";
            } else {
                subject = "Web Development";
            }

            // Rotate difficulty
            if (i % 3 == 0) {
                difficulty = "Easy";
            } else if (i % 3 == 1) {
                difficulty = "Medium";
            } else {
                difficulty = "Hard";
            }

            Question question =
                    new Question(
                            "Sample exam question number " + i
                                    + " related to " + subject + "?",

                            "Option A for question " + i,

                            "Option B for question " + i,

                            "Option C for question " + i,

                            "Option D for question " + i,

                            "A",

                            subject,

                            difficulty
                    );

            batch.add(question);

            // Save questions in batches
            if (batch.size() >= BATCH_SIZE) {

                questionRepository.saveAll(batch);

                batch.clear();

                System.out.println(
                        "Generated "
                                + (i - existingQuestions)
                                + " / "
                                + questionsToCreate
                                + " new questions"
                );
            }
        }

        // Save remaining questions
        if (!batch.isEmpty()) {

            questionRepository.saveAll(batch);
        }

        System.out.println(
                "Question pool generation completed."
        );

        System.out.println(
                "Total questions: "
                        + questionRepository.count()
        );
    }
}