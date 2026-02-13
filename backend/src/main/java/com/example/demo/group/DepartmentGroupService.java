package com.example.demo.group;

import com.example.demo.auth.Member;
import com.example.demo.auth.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DepartmentGroupService {

    private final DepartmentGroupRepository groupRepository;
    private final DepartmentGroupMemberRepository groupMemberRepository;
    private final MemberRepository memberRepository;

    public DepartmentGroupService(
            DepartmentGroupRepository groupRepository,
            DepartmentGroupMemberRepository groupMemberRepository,
            MemberRepository memberRepository
    ) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentGroup> listAll() {
        return groupRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public DepartmentGroup createGroup(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Group name is required");
        }
        if (groupRepository.existsByNameIgnoreCase(normalized)) {
            throw new ResponseStatusException(CONFLICT, "Group name already exists");
        }
        return groupRepository.save(new DepartmentGroup(normalized));
    }

    @Transactional
    public DepartmentGroup addMember(Long groupId, Long memberId) {
        DepartmentGroup group = groupRepository.findWithMembershipsById(groupId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Group not found"));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Member not found"));
        if (!groupMemberRepository.existsByGroup_IdAndMember_Id(groupId, memberId)) {
            group.addMembership(new DepartmentGroupMember(group, member, false));
            groupRepository.save(group);
        }
        return groupRepository.findWithMembershipsById(groupId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Group not found"));
    }

    @Transactional
    public DepartmentGroup removeMember(Long groupId, Long memberId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResponseStatusException(NOT_FOUND, "Group not found");
        }
        groupMemberRepository.deleteByGroup_IdAndMember_Id(groupId, memberId);
        return groupRepository.findWithMembershipsById(groupId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Group not found"));
    }

    @Transactional
    public DepartmentGroup setSupervisor(Long groupId, Long memberId) {
        DepartmentGroup group = groupRepository.findWithMembershipsById(groupId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Group not found"));
        DepartmentGroupMember target = groupMemberRepository.findByGroup_IdAndMember_Id(groupId, memberId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Member must join group before becoming supervisor"));

        group.getMemberships().forEach(m -> m.setSupervisor(false));
        target.setSupervisor(true);
        groupRepository.save(group);
        return groupRepository.findWithMembershipsById(groupId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Group not found"));
    }

    @Transactional(readOnly = true)
    public List<DepartmentGroupMember> listGroupsOfMember(Long memberId) {
        return groupMemberRepository.findByMember_Id(memberId);
    }

    @Transactional(readOnly = true)
    public DepartmentGroup requireGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Group not found"));
    }

    @Transactional(readOnly = true)
    public boolean isMemberInGroup(Long groupId, Long memberId) {
        return groupMemberRepository.existsByGroup_IdAndMember_Id(groupId, memberId);
    }

    @Transactional(readOnly = true)
    public boolean hasSupervisor(Long groupId) {
        return groupMemberRepository.findByGroup_IdAndSupervisorTrue(groupId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isSupervisor(Long groupId, Long memberId) {
        return groupMemberRepository.findByGroup_IdAndMember_Id(groupId, memberId)
                .map(DepartmentGroupMember::isSupervisor)
                .orElse(false);
    }
}
