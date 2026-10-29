/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** UI-driven, authenticated creation. No anonymous or GET-based write actions. */
@Controller
public class WorkspaceCreateController {
    private final WorkspaceCreationService creation;
    private final WorkspaceCsrfService csrf;

    public WorkspaceCreateController(WorkspaceCreationService creation, WorkspaceCsrfService csrf) {
        this.creation = creation;
        this.csrf = csrf;
    }

    @GetMapping("/create")
    public String createForm(Model model, HttpServletResponse response) {
        noStore(response);
        if (!creation.mayCreate()) throw new WorkspaceCreationDeniedException();
        addFormDefaults(model, new WorkspaceCreateRequest("Customer Portal", "application", "private", ""));
        return "workspace_create";
    }

    @PostMapping("/create")
    public String submit(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "visibility", required = false) String visibility,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "csrfToken", required = false) String csrfToken,
            @RequestParam(name = "formNonce", required = false) String formNonce,
            Model model, HttpServletResponse response) {
        noStore(response);
        WorkspaceCreateRequest input = new WorkspaceCreateRequest(name, type, visibility, description);
        // Validate before consuming nonce so users can correct a form mistake.
        try {
            WorkspaceCreationService.normalizeAndValidate(input);
        } catch (WorkspaceInvalidRequestException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("error", ex.getMessage());
            addFormDefaults(model, input);
            return "workspace_create";
        }
        csrf.consume(csrfToken, formNonce);
        WorkspaceCreateResult result = creation.create(input);
        // The next view obtains fresh tokens. This request is consumed once.
        return "redirect:/index?created=1";
    }

    private void addFormDefaults(Model model, WorkspaceCreateRequest input) {
        WorkspaceCsrfService.FormTokens tokens = csrf.formTokens();
        model.addAttribute("csrfToken", tokens.csrfToken());
        model.addAttribute("formNonce", tokens.nonce());
        model.addAttribute("name", input.name() == null ? "" : input.name());
        model.addAttribute("description", input.description() == null ? "" : input.description());
        model.addAttribute("type", "application");
        model.addAttribute("visibility", "private");
    }

    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
