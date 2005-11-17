/*-*- mode: Java; tab-width:8 -*-*/

package php.java.faces;

import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;

import javax.faces.application.Application;
import javax.faces.application.FacesMessage;
import javax.faces.application.FacesMessage.Severity;
import javax.faces.component.UIViewRoot;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseStream;
import javax.faces.context.ResponseWriter;
import javax.faces.render.RenderKit;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.script.PhpScriptEngine;
import php.java.script.URLReader;

/**
 * @author jostb
 *
 */
public class PhpFacesContext extends FacesContext {


    private FacesContext context;
    private HashMap scriptEngines;
	
    protected ServletContext ctx;
    protected HttpServletRequest req;
    protected HttpServletResponse res;
    protected String baseURL;
	
    private String getOverrideString() {
	StringBuffer buf = new StringBuffer(req.isSecure()?"https://127.0.0.1:":"http://127.0.0.1:");
	buf.append(req.getServerPort());
	buf.append(req.getContextPath());
	//req.getPathInfo()
	return buf.toString();
    }
    /**
     * @param facesContext
     */
    public PhpFacesContext(FacesContext facesContext, Object kontext, Object request, Object response) {
	context = facesContext;
	scriptEngines = new HashMap();
	setCurrentInstance(this);

	ctx = (ServletContext)kontext;
	req = (HttpServletRequest)request;
	res = (HttpServletResponse)response;
	baseURL = getOverrideString();
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#getApplication()
     */
    public Application getApplication() {
	return context.getApplication();
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#getClientIdsWithMessages()
     */
    public Iterator getClientIdsWithMessages() {
	return context.getClientIdsWithMessages();
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#getExternalContext()
     */
    public ExternalContext getExternalContext() {
	return context.getExternalContext();
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#getMaximumSeverity()
     */
    public Severity getMaximumSeverity() {
	return context.getMaximumSeverity();
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#getMessages()
     */
    public Iterator getMessages() {
	return context.getMessages();
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#getMessages(java.lang.String)
     */
    public Iterator getMessages(String clientId) {
	return context.getMessages(clientId);
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#getRenderKit()
     */
    public RenderKit getRenderKit() {
	return context.getRenderKit();
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#getRenderResponse()
     */
    public boolean getRenderResponse() {
	return context.getRenderResponse();
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#getResponseComplete()
     */
    public boolean getResponseComplete() {
	return context.getResponseComplete();
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#getResponseStream()
     */
    public ResponseStream getResponseStream() {
	return context.getResponseStream();
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#setResponseStream(javax.faces.context.ResponseStream)
     */
    public void setResponseStream(ResponseStream responseStream) {
	context.setResponseStream(responseStream);
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#getResponseWriter()
     */
    public ResponseWriter getResponseWriter() {
	return context.getResponseWriter();
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#setResponseWriter(javax.faces.context.ResponseWriter)
     */
    public void setResponseWriter(ResponseWriter responseWriter) {
	context.setResponseWriter(responseWriter);
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#getViewRoot()
     */
    public UIViewRoot getViewRoot() {
	return context.getViewRoot();
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#setViewRoot(javax.faces.component.UIViewRoot)
     */
    public void setViewRoot(UIViewRoot root) {
	context.setViewRoot(root);
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#addMessage(java.lang.String, javax.faces.context.FacesMessage)
     */
    public void addMessage(String clientId, FacesMessage message) {
	context.addMessage(clientId, message);
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#release()
     */
    public void release() {
	context.release();
	releaseEngines();
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#renderResponse()
     */
    public void renderResponse() {
	context.renderResponse();
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#responseComplete()
     */
    public void responseComplete() {
	context.responseComplete();
    }

    /* (non-Javadoc)
     * @see javax.faces.context.FacesContext#getELContext()
     */
    //	public ELContext getELContext() {
    //		return context.getELContext();
    //	}

    public synchronized ScriptEngine getScriptEngine(Object key, URL url) throws ScriptException {
	ScriptEngine e;
	if((e=(ScriptEngine)scriptEngines.get(key))!=null) return e;
	e = new PhpFacesScriptEngine(ctx, req, res);
	scriptEngines.put(key, e);
	e.eval(new URLReader(url));
	return e;
    }
	
    private synchronized void releaseEngines() {
	for(Iterator ii = scriptEngines.keySet().iterator(); ii.hasNext();) {
	    Object key = ii.next();
	    Object val = scriptEngines.get(key);
	    ((PhpScriptEngine)val).release();
	    ii.remove();
	}
    }
    /**
     * @return
     */
    public String getBase() {
	return baseURL;
    }

}
