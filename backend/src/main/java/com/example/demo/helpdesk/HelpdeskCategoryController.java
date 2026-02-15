package com.example.demo.helpdesk;

import com.example.demo.auth.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/helpdesk/categories")
public class HelpdeskCategoryController {

    private final AuthService authService;
    private final HelpdeskCategoryService categoryService;

    public HelpdeskCategoryController(AuthService authService, HelpdeskCategoryService categoryService) {
        this.authService = authService;
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> list(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        authService.requireMember(authorization);
        return categoryService.listAll().stream().map(CategoryResponse::from).toList();
    }

    public record CategoryResponse(Long id, String name, LocalDateTime createdAt) {
        static CategoryResponse from(HelpdeskCategory category) {
            return new CategoryResponse(category.getId(), category.getName(), category.getCreatedAt());
        }
    }
}
