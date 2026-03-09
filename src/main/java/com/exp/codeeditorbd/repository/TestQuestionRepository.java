package com.exp.codeeditorbd.repository;

import com.exp.codeeditorbd.entity.TestQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestQuestionRepository extends JpaRepository<TestQuestion, UUID> {
    List<TestQuestion> findByTestId(UUID testId);
}
