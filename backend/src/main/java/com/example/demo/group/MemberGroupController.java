package com.example.demo.group;

import com.example.demo.auth.AuthService;
import com.example.demo.auth.Member;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class MemberGroupController {

    private final AuthService authService;
    private final DepartmentGroupService groupService;

    public MemberGroupController(AuthService authService, DepartmentGroupService groupService) {
        this.authService = authService;
        this.groupService = groupService;
    }

    @GetMapping("/mine")
    public List<MyGroupResponse> myGroups(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Member member = authService.requireMember(authorization);
        return groupService.listGroupsOfMember(member.getId()).stream()
                .map(m -> new MyGroupResponse(m.getGroup().getId(), m.getGroup().getName(), m.isSupervisor()))
                .toList();
    }

    public record MyGroupResponse(Long id, String name, boolean supervisor) {
    }
}
