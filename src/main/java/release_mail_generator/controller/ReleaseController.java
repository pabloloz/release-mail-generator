package release_mail_generator.controller;

import release_mail_generator.model.ReleaseRequest;
import release_mail_generator.service.EmailGeneratorService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class ReleaseController {

    private final EmailGeneratorService emailGeneratorService;

    public ReleaseController(
            EmailGeneratorService emailGeneratorService
    ) {
        this.emailGeneratorService = emailGeneratorService;
    }

    @GetMapping("/")
    public String home(Model model) {

        model.addAttribute(
                "releaseRequest",
                new ReleaseRequest()
        );

        return "index";
    }

    @PostMapping("/generate")
    public String generateEmail(
            @ModelAttribute ReleaseRequest releaseRequest,
            Model model
    ) {

        String generatedEmail =
                emailGeneratorService.generateEmail(releaseRequest);

        model.addAttribute("generatedEmail", generatedEmail);
        model.addAttribute("releaseRequest", releaseRequest);

        return "index";
    }

}
