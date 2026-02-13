package com.example.demo.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DepartmentGroupMemberRepository extends JpaRepository<DepartmentGroupMember, Long> {
    boolean existsByGroup_IdAndMember_Id(Long groupId, Long memberId);

    Optional<DepartmentGroupMember> findByGroup_IdAndMember_Id(Long groupId, Long memberId);

    Optional<DepartmentGroupMember> findByGroup_IdAndSupervisorTrue(Long groupId);

    List<DepartmentGroupMember> findByMember_Id(Long memberId);

    @Query("select gm from DepartmentGroupMember gm join fetch gm.group where gm.member.id = :memberId")
    List<DepartmentGroupMember> findByMemberIdWithGroup(Long memberId);

    void deleteByGroup_IdAndMember_Id(Long groupId, Long memberId);
}
