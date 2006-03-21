/*-*- mode: Java; tab-width:8 -*-*/

/**
 * Based on JSF FacesServlet
 */
package php.java.faces;

import java.io.IOException;

import javax.faces.FactoryFinder;
import javax.faces.context.FacesContext;
import javax.faces.context.FacesContextFactory;
import javax.faces.lifecycle.Lifecycle;
import javax.faces.lifecycle.LifecycleFactory;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public final class FacesServlet
    implements Servlet
{
    private FacesContextFactory facesContextFactory;
    private Lifecycle lifecycle;
    private ServletConfig servletConfig;

    public FacesServlet()
    {
        facesContextFactory = null;
        lifecycle = null;
        servletConfig = null;
    }

    public void destroy()
    {
        facesContextFactory = null;
        lifecycle = null;
        servletConfig = null;
    }

    public ServletConfig getServletConfig()
    {
        return servletConfig;
    }

    public String getServletInfo()
    {
        return getClass().getName();
    }

    public void init(ServletConfig servletConfig)
        throws ServletException
    {
        this.servletConfig = servletConfig;
        try
        {
            facesContextFactory = (FacesContextFactory)FactoryFinder.getFactory("javax.faces.context.FacesContextFactory");
            LifecycleFactory lifecycleFactory = (LifecycleFactory)FactoryFinder.getFactory("javax.faces.lifecycle.LifecycleFactory");
            String lifecycleId = servletConfig.getServletContext().getInitParameter("javax.faces.LIFECYCLE_ID");
            if(lifecycleId == null) lifecycleId = "DEFAULT";
            lifecycle = lifecycleFactory.getLifecycle(lifecycleId);
        }
        catch(Throwable e)
        {
            servletConfig.getServletContext().log("ERROR: Java Server Faces is not available", e);
        }
    }

    public void service(ServletRequest request, ServletResponse response)
        throws IOException, ServletException
    {
        FacesContext context = facesContextFactory.getFacesContext(servletConfig.getServletContext(), request, response, lifecycle);
        try
        {
            lifecycle.execute(context);
            lifecycle.render(context);
        }
        catch(Exception e)
        {
            Throwable t = e.getCause();
            if(t == null)
                throw new ServletException(String.valueOf(e.getMessage()), e);
            if(t instanceof ServletException)
                throw (ServletException)t;
            if(t instanceof IOException)
                throw (IOException)t;
            else
                throw new ServletException(String.valueOf(t.getMessage()), t);
        }
        finally
        {
            context.release();
        }
    }
}
