package com.example.demo.group;

import com.example.demo.audit.AuditLogService;
import com.example.demo.auth.Member;
import com.example.demo.auth.MemberRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DepartmentGroupService {

    private final DepartmentGroupRepository groupRepository;
    private final DepartmentGroupMemberRepository groupMemberRepository;
    private final MemberRepository memberRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public DepartmentGroupService(
            DepartmentGroupRepository groupRepository,
            DepartmentGroupMemberRepository groupMemberRepository,
            MemberRepository memberRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper
    ) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.memberRepository = memberRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<DepartmentGroup> listAll() {
        return groupRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public DepartmentGroup createGroup(Member actor, String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Group name is required");
        }
        if (groupRepository.existsByNameIgnoreCase(normalized)) {
            throw new ResponseStatusException(CONFLICT, "Group name already exists");
        }
        DepartmentGroup created = groupRepository.save(new DepartmentGroup(normalized));
        auditLogService.record(
                actor,
                "GROUP_CREATE",
                "GROUP",
                created.getId(),
                null,
                toJson(groupSnapshot(created)),
                null
        );
        return created;
    }

    @Transactional
    public DepartmentGroup addMember(Member actor, Long groupId, Long memberId) {
        DepartmentGroup group = groupRepository.findWithMembershipsById(groupId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Group not found"));
        Map<String, Object> before = groupSnapshot(group);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Member not found"));
        boolean added = false;
        if (!groupMemberRepository.existsByGroup_IdAndMember_Id(groupId, memberId)) {
            group.addMembership(new DepartmentGroupMember(group, member, false));
            groupRepository.save(group);
            added = true;
        }
        DepartmentGroup updated = groupRepository.findWithMembershipsById(groupId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Group not found"));
        if (added) {
            auditLogService.record(
                    actor,
                    "GROUP_MEMBER_ADD",
                    "GROUP",
                    updated.getId(),
                    toJson(before),
                    toJson(groupSnapshot(updated)),
                    toJson(Map.of("memberId", memberId))
            );
        }
        return updated;
    }

    @Transactional
    public DepartmentGroup removeMember(Member actor, Long groupId, Long memberId) {
        DepartmentGroup group = groupRepository.findWithMembershipsById(groupId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Group not found"));
        Map<String, Object> before = groupSnapshot(group);
        boolean existed = groupMemberRepository.existsByGroup_IdAndMember_Id(groupId, memberId);
        groupMemberRepository.deleteByGroup_IdAndMember_Id(groupId, memberId);
        DepartmentGroup updated = groupRepository.findWithMembershipsById(groupId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Group not found"));
        if (existed) {
            auditLogService.record(
                    actor,
                    "GROUP_MEMBER_REMOVE",
                    "GROUP",
                    updated.getId(),
                    toJson(before),
                    toJson(groupSnapshot(updated)),
                    toJson(Map.of("memberId", memberId))
            );
        }
        return updated;
    }

    @Transactional
    public DepartmentGroup setSupervisor(Member actor, Long groupId, Long memberId) {
        DepartmentGroup group = groupRepository.findWithMembershipsById(groupId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Group not found"));
        Map<String, Object> before = groupSnapshot(group);
        DepartmentGroupMember target = groupMemberRepository.findByGroup_IdAndMember_Id(groupId, memberId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Member must join group before becoming supervisor"));

        Long previousSupervisorId = groupMemberRepository.findByGroup_IdAndSupervisorTrue(groupId)
                .map(m -> m.getMember().getId())
                .orElse(null);
        group.getMemberships().forEach(m -> m.setSupervisor(false));
        target.setSupervisor(true);
        groupRepository.save(group);
        DepartmentGroup updated = groupRepository.findWithMembershipsById(groupId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Group not found"));
        if (previousSupervisorId == null || !previousSupervisorId.equals(memberId)) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("memberId", memberId);
            metadata.put("previousSupervisorId", previousSupervisorId);
            auditLogService.record(
                    actor,
                    "GROUP_SUPERVISOR_SET",
                    "GROUP",
                    updated.getId(),
                    toJson(before),
                    toJson(groupSnapshot(updated)),
                    toJson(metadata)
            );
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public List<DepartmentGroupMember> listGroupsOfMember(Long memberId) {
        return groupMemberRepository.findByMemberIdWithGroup(memberId);
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

    private Map<String, Object> groupSnapshot(DepartmentGroup group) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", group.getId());
        out.put("name", group.getName());
        out.put("members", group.getMemberships().stream()
                .map(m -> Map.of(
                        "memberId", m.getMember().getId(),
                        "employeeId", m.getMember().getEmployeeId(),
                        "supervisor", m.isSupervisor()
                ))
                .toList());
        return out;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit payload", e);
        }
    }
}
