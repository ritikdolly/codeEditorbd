package com.exp.codeeditorbd.repository;

import com.exp.codeeditorbd.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, UUID> {
    List<TestCase> findByQuestionId(UUID questionId);
}
