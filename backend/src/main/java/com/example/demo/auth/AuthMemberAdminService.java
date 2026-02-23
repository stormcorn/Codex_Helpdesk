package com.example.demo.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AuthMemberAdminService {

    private final MemberRepository memberRepository;
    private final AuthTokenService authTokenService;

    public AuthMemberAdminService(MemberRepository memberRepository, AuthTokenService authTokenService) {
        this.memberRepository = memberRepository;
        this.authTokenService = authTokenService;
    }

    @Transactional(readOnly = true)
    public List<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    @Transactional
    public void deleteMemberById(Long id) {
        Member target = memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Member not found"));

        if (target.getRole() == MemberRole.ADMIN) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot delete admin account");
        }

        authTokenService.revokeMemberTokens(target.getId());
        memberRepository.delete(target);
    }

    @Transactional
    public Member updateMemberRole(Long id, MemberRole role) {
        Member target = memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Member not found"));
        if (target.getRole() == MemberRole.ADMIN) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot modify admin role");
        }
        if (role == MemberRole.ADMIN) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot assign admin role");
        }
        target.setRole(role);
        return memberRepository.save(target);
    }
}
