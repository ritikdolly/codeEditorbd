package com.exp.codeeditorbd.repository;

import com.exp.codeeditorbd.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    List<Submission> findByTestId(UUID testId);
    List<Submission> findByStudentId(UUID studentId);

    @Query("SELECT s FROM Submission s JOIN FETCH s.student JOIN FETCH s.question WHERE s.test.id = :testId")
    List<Submission> findByTestIdWithDetails(@Param("testId") UUID testId);
}
