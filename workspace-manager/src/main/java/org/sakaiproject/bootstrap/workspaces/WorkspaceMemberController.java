/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Sakai authenticated membership UI; all changes require a one-time session CSRF nonce. */
@Controller
public class WorkspaceMemberController {
    private final WorkspaceMemberService members;
    private final WorkspaceCsrfService csrf;

    public WorkspaceMemberController(WorkspaceMemberService members, WorkspaceCsrfService csrf) {
        this.members = members;
        this.csrf = csrf;
    }

    @GetMapping("/members/{siteId}")
    public String index(@PathVariable("siteId") String siteId, Model model, HttpServletResponse response) {
        noStore(response);
        model.addAttribute("workspace", members.members(siteId));
        var tokens = csrf.formTokens();
        model.addAttribute("csrfToken", tokens.csrfToken());
        model.addAttribute("formNonce", tokens.nonce());
        return "workspace_members";
    }

    @PostMapping("/members/{siteId}/grant")
    public String grant(@PathVariable("siteId") String siteId,
            @RequestParam(name = "userId", required = false) String userId,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "csrfToken", required = false) String token,
            @RequestParam(name = "formNonce", required = false) String nonce,
            Model model, HttpServletResponse response) {
        noStore(response);
        // Permission is checked before token consumption; every write rechecks it.
        members.members(siteId);
        csrf.consume(token, nonce);
        try {
            members.grant(siteId, userId, role);
        } catch (WorkspaceMemberInvalidException ex) {
            return invalid(siteId, ex, model, response);
        }
        return "redirect:/members/" + siteId + "?updated=1";
    }

    @PostMapping("/members/{siteId}/remove")
    public String remove(@PathVariable("siteId") String siteId,
            @RequestParam(name = "userId", required = false) String userId,
            @RequestParam(name = "csrfToken", required = false) String token,
            @RequestParam(name = "formNonce", required = false) String nonce,
            Model model, HttpServletResponse response) {
        noStore(response);
        members.members(siteId);
        csrf.consume(token, nonce);
        try {
            members.remove(siteId, userId);
        } catch (WorkspaceMemberInvalidException ex) {
            return invalid(siteId, ex, model, response);
        }
        return "redirect:/members/" + siteId + "?updated=1";
    }

    private String invalid(String siteId, WorkspaceMemberInvalidException ex, Model model,
                           HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("workspace", members.members(siteId));
        var tokens = csrf.formTokens();
        model.addAttribute("csrfToken", tokens.csrfToken());
        model.addAttribute("formNonce", tokens.nonce());
        model.addAttribute("error", ex.getMessage());
        return "workspace_members";
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }
}
