package com.exp.codeeditorbd.repository;

import com.exp.codeeditorbd.entity.TestAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestAttemptRepository extends JpaRepository<TestAttempt, UUID> {
    Optional<TestAttempt> findByTestIdAndStudentId(UUID testId, UUID studentId);
    
    Optional<TestAttempt> findFirstByTestIdAndStudentIdOrderByStartTimeDesc(UUID testId, UUID studentId);
}
