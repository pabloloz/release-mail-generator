package release_mail_generator.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import release_mail_generator.service.GlobalSearchService;

import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class GlobalSearchController {

    private final GlobalSearchService searchService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> search(@RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(searchService.search(q));
    }
}
