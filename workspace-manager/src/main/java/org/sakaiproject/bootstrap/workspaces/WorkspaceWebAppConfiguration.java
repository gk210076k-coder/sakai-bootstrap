/* Copyright (c) 2026 Sakai Bootstrap contributors. Licensed under ECL-2.0. */
package org.sakaiproject.bootstrap.workspaces;

import java.util.EnumSet;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import org.sakaiproject.util.RequestFilter;
import org.sakaiproject.util.SakaiContextLoaderListener;
import org.sakaiproject.util.ToolListener;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/** Uses the same Sakai tool lifecycle as the existing Dashboard WAR. */
public class WorkspaceWebAppConfiguration implements WebApplicationInitializer {
    @Override
    public void onStartup(ServletContext context) throws ServletException {
        AnnotationConfigWebApplicationContext spring = new AnnotationConfigWebApplicationContext();
        spring.setServletContext(context);
        spring.register(WorkspaceWebMvcConfiguration.class);
        context.addListener(new ToolListener());
        context.addListener(new SakaiContextLoaderListener(spring));
        context.addFilter("sakai.request", RequestFilter.class)
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST,
                        DispatcherType.FORWARD, DispatcherType.INCLUDE), true, "/*");
        ServletRegistration.Dynamic servlet = context.addServlet("sakai.bootstrap.workspaces", new DispatcherServlet(spring));
        servlet.addMapping("/");
        servlet.setLoadOnStartup(1);
    }
}
