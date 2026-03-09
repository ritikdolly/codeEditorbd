package com.exp.codeeditorbd.repository;

import com.exp.codeeditorbd.entity.SubmissionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubmissionResultRepository extends JpaRepository<SubmissionResult, UUID> {
    List<SubmissionResult> findBySubmissionId(UUID submissionId);
}
