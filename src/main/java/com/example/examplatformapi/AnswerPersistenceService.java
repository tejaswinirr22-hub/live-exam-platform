package com.example.examplatformapi.service;

import com.example.examplatformapi.answer.CandidateAnswer;
import com.example.examplatformapi.entity.Question;
import com.example.examplatformapi.repository.CandidateAnswerRepository;
import com.example.examplatformapi.repository.ExamSessionRepository;
import com.example.examplatformapi.repository.QuestionRepository;
import com.example.examplatformapi.session.ExamSession;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class AnswerPersistenceService {

    private final StringRedisTemplate redisTemplate;
    private final CandidateAnswerRepository candidateAnswerRepository;
    private final ExamSessionRepository examSessionRepository;
    private final QuestionRepository questionRepository;

    public AnswerPersistenceService(
            StringRedisTemplate redisTemplate,
            CandidateAnswerRepository candidateAnswerRepository,
            ExamSessionRepository examSessionRepository,
            QuestionRepository questionRepository) {

        this.redisTemplate = redisTemplate;
        this.candidateAnswerRepository = candidateAnswerRepository;
        this.examSessionRepository = examSessionRepository;
        this.questionRepository = questionRepository;
    }

    /*
     * Runs every 5 seconds.
     *
     * Redis is used as the fast answer store.
     * This background process copies pending answers to PostgreSQL.
     */
    @Scheduled(fixedDelay = 5000)
    public void persistAnswersToDatabase() {

        Set<String> keys =
                redisTemplate.keys("exam-answer:*");

        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {

            try {
                String value =
                        redisTemplate.opsForValue().get(key);

                if (value == null) {
                    continue;
                }

                String[] parts = key.split(":");

                if (parts.length != 3) {
                    continue;
                }

                Long sessionId =
                        Long.valueOf(parts[1]);

                Long questionId =
                        Long.valueOf(parts[2]);

                ExamSession session =
                        examSessionRepository.findById(sessionId)
                                .orElse(null);

                Question question =
                        questionRepository.findById(questionId)
                                .orElse(null);

                if (session == null || question == null) {
                    continue;
                }

                CandidateAnswer answer =
                        candidateAnswerRepository
                                .findByExamSessionAndQuestion(
                                        session,
                                        question
                                )
                                .orElse(new CandidateAnswer());

                answer.setExamSession(session);
                answer.setQuestion(question);
                answer.setSelectedAnswer(value);
                answer.setLastSavedAt(LocalDateTime.now());

                candidateAnswerRepository.save(answer);

                /*
                 * Delete the Redis key only after
                 * successfully persisting to PostgreSQL.
                 */
                redisTemplate.delete(key);

            } catch (Exception e) {

                /*
                 * Keep the Redis answer if database persistence fails.
                 * It can be retried during the next scheduled run.
                 */
                System.err.println(
                        "Failed to persist answer: "
                                + key
                                + " - "
                                + e.getMessage()
                );
            }
        }
    }
}