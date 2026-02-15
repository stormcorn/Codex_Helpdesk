package com.example.demo.helpdesk;

import com.example.demo.auth.AuthService;
import com.example.demo.auth.Member;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/helpdesk-categories")
public class AdminHelpdeskCategoryController {

    private final AuthService authService;
    private final HelpdeskCategoryService categoryService;

    public AdminHelpdeskCategoryController(AuthService authService, HelpdeskCategoryService categoryService) {
        this.authService = authService;
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        authService.requireAdmin(authorization);
        return categoryService.listAll().stream().map(CategoryResponse::from).toList();
    }

    @PostMapping
    public CategoryResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CreateCategoryRequest request
    ) {
        Member admin = authService.requireAdmin(authorization);
        return CategoryResponse.from(categoryService.createCategory(admin, request.name()));
    }

    public record CreateCategoryRequest(String name) {
    }

    public record CategoryResponse(Long id, String name, LocalDateTime createdAt) {
        static CategoryResponse from(HelpdeskCategory category) {
            return new CategoryResponse(category.getId(), category.getName(), category.getCreatedAt());
        }
    }
}
