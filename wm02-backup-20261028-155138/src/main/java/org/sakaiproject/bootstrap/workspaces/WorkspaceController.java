/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** No create, invite, role-change, or other write endpoints exist in WM01. */
@Controller
public class WorkspaceController {
    private final WorkspaceCatalogService catalog;

    public WorkspaceController(WorkspaceCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping({"/", "/index"})
    public String index(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        List<WorkspaceView> workspaces = catalog.listCurrentUserWorkspaces();
        model.addAttribute("workspaces", workspaces);
        model.addAttribute("workspaceCount", workspaces.size());
        return "workspace_index";
    }
}
