/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

/** Server-validated input; no caller-supplied site id, owner or tool ids. */
public record WorkspaceCreateRequest(String name, String type, String visibility, String description) { }
