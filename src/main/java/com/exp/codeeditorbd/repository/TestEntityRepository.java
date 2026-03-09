package com.exp.codeeditorbd.repository;

import com.exp.codeeditorbd.entity.TestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestEntityRepository extends JpaRepository<TestEntity, UUID> {
    List<TestEntity> findByTeacherId(UUID teacherId);
}
