package com.example.examplatformapi.controller;

import com.example.examplatformapi.entity.Question;
import com.example.examplatformapi.entity.QuestionPaper;
import com.example.examplatformapi.repository.QuestionRepository;
import com.example.examplatformapi.service.QuestionPaperService;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/papers")
public class QuestionPaperController {

    private final QuestionPaperService questionPaperService;
    private final QuestionRepository questionRepository;

    public QuestionPaperController(
            QuestionPaperService questionPaperService,
            QuestionRepository questionRepository) {

        this.questionPaperService = questionPaperService;
        this.questionRepository = questionRepository;
    }

    @GetMapping("/generate")
    public List<QuestionResponse> generatePaper(
            @RequestParam Long candidateId,
            @RequestParam Long examId) {

        QuestionPaper paper =
                questionPaperService.generatePaper(candidateId, examId);

        List<Long> questionIds = Arrays.stream(paper.getQuestionIds().split(","))
                .map(Long::valueOf)
                .toList();

        return questionIds.stream()
                .map(questionRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(question -> new QuestionResponse(
                        question.getId(),
                        question.getQuestionText(),
                        question.getOptionA(),
                        question.getOptionB(),
                        question.getOptionC(),
                        question.getOptionD()
                ))
                .toList();
    }

    public record QuestionResponse(
            Long id,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD
    ) {
    }
}