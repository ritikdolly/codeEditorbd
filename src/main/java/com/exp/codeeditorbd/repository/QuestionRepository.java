package com.exp.codeeditorbd.repository;

import com.exp.codeeditorbd.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    // JOIN FETCH ensures testCases are loaded eagerly in a single query
    @Query("SELECT DISTINCT q FROM Question q LEFT JOIN FETCH q.testCases WHERE q.teacher.id = :teacherId")
    List<Question> findByTeacherId(@Param("teacherId") UUID teacherId);

    @Query("SELECT DISTINCT q FROM Question q LEFT JOIN FETCH q.testCases WHERE q.id = :id")
    Optional<Question> findByIdWithTestCases(@Param("id") UUID id);
}
