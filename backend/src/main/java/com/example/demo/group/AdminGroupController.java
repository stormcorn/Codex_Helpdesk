package com.example.demo.group;

import com.example.demo.auth.AuthService;
import com.example.demo.auth.Member;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/groups")
public class AdminGroupController {

    private final AuthService authService;
    private final DepartmentGroupService groupService;

    public AdminGroupController(AuthService authService, DepartmentGroupService groupService) {
        this.authService = authService;
        this.groupService = groupService;
    }

    @GetMapping
    public List<GroupResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        authService.requireAdmin(authorization);
        return groupService.listAll().stream().map(GroupResponse::from).toList();
    }

    @PostMapping
    public GroupResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CreateGroupRequest request
    ) {
        Member admin = authService.requireAdmin(authorization);
        return GroupResponse.from(groupService.createGroup(admin, request.name()));
    }

    @PatchMapping("/{groupId}/members/{memberId}")
    public GroupResponse addMember(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long groupId,
            @PathVariable Long memberId
    ) {
        Member admin = authService.requireAdmin(authorization);
        return GroupResponse.from(groupService.addMember(admin, groupId, memberId));
    }

    @DeleteMapping("/{groupId}/members/{memberId}")
    public GroupResponse removeMember(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long groupId,
            @PathVariable Long memberId
    ) {
        Member admin = authService.requireAdmin(authorization);
        return GroupResponse.from(groupService.removeMember(admin, groupId, memberId));
    }

    @PatchMapping("/{groupId}/supervisor/{memberId}")
    public GroupResponse setSupervisor(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long groupId,
            @PathVariable Long memberId
    ) {
        Member admin = authService.requireAdmin(authorization);
        return GroupResponse.from(groupService.setSupervisor(admin, groupId, memberId));
    }

    public record CreateGroupRequest(String name) {
    }

    public record GroupResponse(Long id, String name, LocalDateTime createdAt, List<GroupMemberResponse> members) {
        static GroupResponse from(DepartmentGroup group) {
            return new GroupResponse(
                    group.getId(),
                    group.getName(),
                    group.getCreatedAt(),
                    group.getMemberships().stream()
                            .map(GroupMemberResponse::from)
                            .sorted((a, b) -> Long.compare(a.memberId(), b.memberId()))
                            .toList()
            );
        }
    }

    public record GroupMemberResponse(Long memberId, String employeeId, String name, String role, boolean supervisor) {
        static GroupMemberResponse from(DepartmentGroupMember membership) {
            return new GroupMemberResponse(
                    membership.getMember().getId(),
                    membership.getMember().getEmployeeId(),
                    membership.getMember().getName(),
                    membership.getMember().getRole().name(),
                    membership.isSupervisor()
            );
        }
    }
}
