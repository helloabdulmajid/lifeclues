package in.abdulmajid.lifeclues.memory.controller;

import in.abdulmajid.lifeclues.memory.dto.CategoryResponse;
import in.abdulmajid.lifeclues.memory.service.CategoryService;
import in.abdulmajid.lifeclues.security.CurrentUser;
import in.abdulmajid.lifeclues.security.UserPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> listCategories(@CurrentUser UserPrincipal currentUser) {
        return categoryService.listCategories(currentUser.getId());
    }
}