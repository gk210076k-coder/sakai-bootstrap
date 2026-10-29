/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

/** Immutable, deliberately minimal view of a site already authorized by Sakai. */
public record WorkspaceView(String id, String title, String type, String role,
                            boolean published, String portalUrl) { }
