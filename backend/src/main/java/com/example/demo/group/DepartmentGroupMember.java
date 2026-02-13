package com.example.demo.group;

import com.example.demo.auth.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "department_group_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_department_group_member", columnNames = {"group_id", "member_id"})
)
public class DepartmentGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private DepartmentGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private boolean supervisor;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected DepartmentGroupMember() {
    }

    public DepartmentGroupMember(DepartmentGroup group, Member member, boolean supervisor) {
        this.group = group;
        this.member = member;
        this.supervisor = supervisor;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public DepartmentGroup getGroup() {
        return group;
    }

    public Member getMember() {
        return member;
    }

    public boolean isSupervisor() {
        return supervisor;
    }

    public void setSupervisor(boolean supervisor) {
        this.supervisor = supervisor;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
