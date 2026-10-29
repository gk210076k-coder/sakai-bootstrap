/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Dashboard remains read-only; WM02 writes are isolated in WorkspaceCreateController. */
@Controller
public class WorkspaceController {
    private final WorkspaceCatalogService catalog;
    private final WorkspaceCreationService creation;

    public WorkspaceController(WorkspaceCatalogService catalog, WorkspaceCreationService creation) {
        this.catalog = catalog;
        this.creation = creation;
    }

    @GetMapping({"/", "/index"})
    public String index(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        List<WorkspaceView> workspaces = catalog.listCurrentUserWorkspaces();
        model.addAttribute("workspaces", workspaces);
        model.addAttribute("workspaceCount", workspaces.size());
        model.addAttribute("canCreate", creation.mayCreate());
        return "workspace_index";
    }
}
