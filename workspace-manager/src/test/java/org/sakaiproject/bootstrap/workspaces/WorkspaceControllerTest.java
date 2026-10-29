/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Test;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.SessionManager;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Verifies real controller and service coordination, not a mocked controller. */
public class WorkspaceControllerTest {
    @Test public void modelContainsOnlyCatalogResults() {
        SiteService siteService = mock(SiteService.class);
        SessionManager sessions = mock(SessionManager.class);
        when(sessions.getCurrentSessionUserId()).thenReturn("user-1");
        when(siteService.getUserSites(false, true)).thenReturn(List.of());
        WorkspaceController controller = new WorkspaceController(new WorkspaceCatalogService(siteService, sessions), new WorkspaceCreationService(siteService, sessions));
        Model model = new ConcurrentModel();
        HttpServletResponse response = mock(HttpServletResponse.class);
        assertEquals("workspace_index", controller.index(model, response));
        assertEquals(0, model.getAttribute("workspaceCount"));
        verify(response).setHeader("Cache-Control", "no-store");
        assertTrue(((List<?>) model.getAttribute("workspaces")).isEmpty());
    }
}
