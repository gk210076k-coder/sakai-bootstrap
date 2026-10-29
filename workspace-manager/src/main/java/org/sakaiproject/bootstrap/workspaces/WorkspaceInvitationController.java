/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** WM04: no membership changes from GET, tokens checked on every state-changing POST. */
@Controller
public class WorkspaceInvitationController {
    private final WorkspaceInvitationService invitations;
    private final WorkspaceCsrfService csrf;
    public WorkspaceInvitationController(WorkspaceInvitationService invitations, WorkspaceCsrfService csrf) {
        this.invitations = invitations;
        this.csrf = csrf;
    }

    @GetMapping("/members/{siteId}/invitations")
    public String manage(@PathVariable("siteId") String siteId, Model model, HttpServletResponse response) {
        secure(response);
        populateManager(siteId, model);
        return "workspace_invitations";
    }

    @PostMapping("/members/{siteId}/invitations")
    public String issue(@PathVariable("siteId") String siteId,
            @RequestParam(name = "userId", required = false) String userId,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "csrfToken", required = false) String token,
            @RequestParam(name = "formNonce", required = false) String nonce,
            Model model, HttpServletResponse response) {
        secure(response);
        invitations.manage(siteId); // authorization first, then one-use CSRF token
        csrf.consume(token, nonce);
        try {
            var created = invitations.issue(siteId, userId, role);
            populateManager(siteId, model);
            // Use the standalone Workspace Manager context rather than Sakai Portal's
            // forwarded /portal/site/!admin/tool/... context. Invitees do not have
            // access to the !admin site. Keep a relative link to avoid Host poisoning.
            model.addAttribute("newInviteLink",
                    "/workspace-manager/invitations/respond?token=" + created.secret());
            model.addAttribute("newInvitee", userId);
            model.addAttribute("newInviteExpires", created.expiresAt());
            return "workspace_invitations";
        } catch (WorkspaceInvitationInvalidException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            populateManager(siteId, model);
            model.addAttribute("error", ex.getMessage());
            return "workspace_invitations";
        }
    }

    @PostMapping("/members/{siteId}/invitations/revoke")
    public String revoke(@PathVariable("siteId") String siteId,
            @RequestParam(name = "invitationId", required = false) String invitationId,
            @RequestParam(name = "csrfToken", required = false) String token,
            @RequestParam(name = "formNonce", required = false) String nonce,
            Model model, HttpServletResponse response) {
        secure(response);
        invitations.manage(siteId);
        csrf.consume(token, nonce);
        try {
            invitations.revoke(siteId, invitationId);
            return "redirect:/members/" + siteId + "/invitations?revoked=1";
        } catch (WorkspaceInvitationInvalidException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            populateManager(siteId, model);
            model.addAttribute("error", ex.getMessage());
            return "workspace_invitations";
        }
    }

    @GetMapping("/invitations/respond")
    public String respond(@RequestParam(name = "token", required = false) String token,
            Model model, HttpServletResponse response) {
        secure(response);
        model.addAttribute("invitation", invitations.preview(token));
        model.addAttribute("inviteToken", token);
        var tokens = csrf.formTokens();
        model.addAttribute("csrfToken", tokens.csrfToken());
        model.addAttribute("formNonce", tokens.nonce());
        return "workspace_invitation_respond";
    }

    @PostMapping("/invitations/accept")
    public String accept(@RequestParam(name = "token", required = false) String token,
            @RequestParam(name = "csrfToken", required = false) String csrfToken,
            @RequestParam(name = "formNonce", required = false) String nonce,
            HttpServletResponse response) {
        secure(response);
        invitations.preview(token); // authenticate exact target before consuming nonce
        csrf.consume(csrfToken, nonce);
        String siteId = invitations.accept(token);
        return "redirect:/members/" + siteId + "/invitations/accepted";
    }

    @GetMapping("/members/{siteId}/invitations/accepted")
    public String accepted(@PathVariable("siteId") String siteId, Model model, HttpServletResponse response) {
        secure(response);
        // Do not require membership management permission here: ordinary invitees can view this.
        model.addAttribute("siteId", siteId);
        return "workspace_invitation_accepted";
    }

    @PostMapping("/invitations/decline")
    public String decline(@RequestParam(name = "token", required = false) String token,
            @RequestParam(name = "csrfToken", required = false) String csrfToken,
            @RequestParam(name = "formNonce", required = false) String nonce,
            HttpServletResponse response) {
        secure(response);
        invitations.preview(token);
        csrf.consume(csrfToken, nonce);
        invitations.decline(token);
        return "redirect:/index?invitationDeclined=1";
    }

    private void populateManager(String siteId, Model model) {
        model.addAttribute("workspace", invitations.manage(siteId));
        var tokens = csrf.formTokens();
        model.addAttribute("csrfToken", tokens.csrfToken());
        model.addAttribute("formNonce", tokens.nonce());
    }
    private static void secure(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, private");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'self'; style-src 'self' 'unsafe-inline'; form-action 'self'; base-uri 'none'");
    }
}
