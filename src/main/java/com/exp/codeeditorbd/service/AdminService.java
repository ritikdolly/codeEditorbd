package com.exp.codeeditorbd.service;

import com.exp.codeeditorbd.dto.DashboardStatsDto;
import com.exp.codeeditorbd.dto.UserResponseDto;
import com.exp.codeeditorbd.entity.Role;
import com.exp.codeeditorbd.repository.SubmissionRepository;
import com.exp.codeeditorbd.repository.TestEntityRepository;
import com.exp.codeeditorbd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final TestEntityRepository testRepository;
    private final SubmissionRepository submissionRepository;

    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> UserResponseDto.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .createdAt(user.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public List<UserResponseDto> getUsersByRole(Role role) {
        return userRepository.findAll().stream()
                .filter(user -> user.getRole() == role)
                .map(user -> UserResponseDto.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .createdAt(user.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public DashboardStatsDto getDashboardStats() {
        long teachers = userRepository.findAll().stream().filter(u -> u.getRole() == Role.TEACHER).count();
        long students = userRepository.findAll().stream().filter(u -> u.getRole() == Role.STUDENT).count();
        long tests = testRepository.count();
        long submissions = submissionRepository.count();

        return DashboardStatsDto.builder()
                .totalTeachers(teachers)
                .totalStudents(students)
                .totalTests(tests)
                .totalSubmissions(submissions)
                .build();
    }
}
