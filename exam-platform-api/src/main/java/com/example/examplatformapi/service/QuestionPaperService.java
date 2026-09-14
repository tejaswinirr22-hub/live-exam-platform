package com.example.examplatformapi.service;

import com.example.examplatformapi.entity.QuestionPaper;
import com.example.examplatformapi.repository.QuestionPaperRepository;
import com.example.examplatformapi.repository.QuestionRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class QuestionPaperService {

    private static final int QUESTIONS_PER_PAPER = 20;

    private final QuestionRepository questionRepository;
    private final QuestionPaperRepository questionPaperRepository;
    private final StringRedisTemplate redisTemplate;

    public QuestionPaperService(
            QuestionRepository questionRepository,
            QuestionPaperRepository questionPaperRepository,
            StringRedisTemplate redisTemplate) {

        this.questionRepository = questionRepository;
        this.questionPaperRepository = questionPaperRepository;
        this.redisTemplate = redisTemplate;
    }

    public QuestionPaper generatePaper(
            Long candidateId,
            Long examId) {

        // --------------------------------------------------
        // Redis cache key
        // --------------------------------------------------

        String redisKey =
                "exam-paper:" + candidateId + ":" + examId;

        // --------------------------------------------------
        // Step 1: Check Redis first
        // --------------------------------------------------

        String cachedQuestionIds =
                redisTemplate.opsForValue().get(redisKey);

        if (cachedQuestionIds != null) {

            return new QuestionPaper(
                    candidateId,
                    examId,
                    cachedQuestionIds
            );
        }

        // --------------------------------------------------
        // Step 2: Check PostgreSQL
        // --------------------------------------------------

        var existingPaper =
                questionPaperRepository
                        .findByCandidateIdAndExamId(
                                candidateId,
                                examId
                        );

        if (existingPaper.isPresent()) {

            QuestionPaper paper =
                    existingPaper.get();

            // Put existing paper into Redis
            redisTemplate.opsForValue().set(
                    redisKey,
                    paper.getQuestionIds(),
                    Duration.ofHours(24)
            );

            return paper;
        }

        // --------------------------------------------------
        // Step 3: Get all question IDs
        // --------------------------------------------------

        List<Long> questionIds =
                questionRepository.findAllIds();

        if (questionIds.size() < QUESTIONS_PER_PAPER) {

            throw new RuntimeException(
                    "Not enough questions available to generate the exam paper"
            );
        }

        // --------------------------------------------------
        // Step 4: Randomize question IDs
        // --------------------------------------------------

        Collections.shuffle(questionIds);

        // --------------------------------------------------
        // Step 5: Select 20 questions
        // --------------------------------------------------

        List<Long> selectedQuestionIds =
                questionIds.subList(
                        0,
                        QUESTIONS_PER_PAPER
                );

        // --------------------------------------------------
        // Step 6: Convert IDs to comma-separated string
        // --------------------------------------------------

        String questionIdString =
                selectedQuestionIds
                        .stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));

        // --------------------------------------------------
        // Step 7: Save permanently in PostgreSQL
        // --------------------------------------------------

        QuestionPaper paper =
                new QuestionPaper(
                        candidateId,
                        examId,
                        questionIdString
                );

        QuestionPaper savedPaper =
                questionPaperRepository.save(paper);

        // --------------------------------------------------
        // Step 8: Cache paper in Redis
        // --------------------------------------------------

        redisTemplate.opsForValue().set(
                redisKey,
                questionIdString,
                Duration.ofHours(24)
        );

        return savedPaper;
    }
}