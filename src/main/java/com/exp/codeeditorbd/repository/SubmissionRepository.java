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

    /**
     * Fetches the best (highest-scored) submission per student per question for a given test.
     * When two submissions share the same top score, both survive the subquery; the service
     * layer de-duplicates via a Map keyed on (studentId, questionId).
     */
    @Query("""
            SELECT s FROM Submission s
              JOIN FETCH s.student
              JOIN FETCH s.question
            WHERE s.test.id = :testId
              AND s.score = (
                SELECT MAX(s2.score)
                FROM Submission s2
                WHERE s2.student.id  = s.student.id
                  AND s2.question.id = s.question.id
                  AND s2.test.id     = :testId
              )
            """)
    List<Submission> findBestSubmissionsPerStudentPerQuestion(@Param("testId") UUID testId);

    /**
     * Same as above but only for a single student — used by the per-student detail endpoint.
     */
    @Query("""
            SELECT s FROM Submission s
              JOIN FETCH s.student
              JOIN FETCH s.question
            WHERE s.test.id    = :testId
              AND s.student.id = :studentId
              AND s.score = (
                SELECT MAX(s2.score)
                FROM Submission s2
                WHERE s2.student.id  = s.student.id
                  AND s2.question.id = s.question.id
                  AND s2.test.id     = :testId
              )
            """)
    List<Submission> findBestSubmissionsForStudent(
            @Param("testId")    UUID testId,
            @Param("studentId") UUID studentId);
}
