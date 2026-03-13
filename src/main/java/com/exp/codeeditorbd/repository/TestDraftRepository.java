package com.exp.codeeditorbd.repository;

import com.exp.codeeditorbd.entity.TestDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestDraftRepository extends JpaRepository<TestDraft, UUID> {
    Optional<TestDraft> findByTestIdAndQuestionIdAndStudentId(UUID testId, UUID questionId, UUID studentId);
}
