/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Invalid workspace member request")
public final class WorkspaceMemberInvalidException extends RuntimeException {
    public WorkspaceMemberInvalidException(String message) { super(message); }
}
