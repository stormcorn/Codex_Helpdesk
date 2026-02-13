package com.example.demo.group;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentGroupRepository extends JpaRepository<DepartmentGroup, Long> {
    boolean existsByNameIgnoreCase(String name);

    @EntityGraph(attributePaths = {"memberships", "memberships.member"})
    List<DepartmentGroup> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = {"memberships", "memberships.member"})
    Optional<DepartmentGroup> findWithMembershipsById(Long id);
}
