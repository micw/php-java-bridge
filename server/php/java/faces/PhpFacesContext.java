/*-*- mode: Java; tab-width:8 -*-*/

package php.java.faces;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.UnknownHostException;
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

import php.java.bridge.NotImplementedException;
import php.java.script.PhpScriptEngine;
import php.java.script.PhpScriptWriter;
import php.java.script.URLReader;
import php.java.servlet.CGIServlet;

/**
 * A custom FacesContext. Stores the baseURL, creates script engines.
 * @author jostb
 *
 */
public class PhpFacesContext extends FacesContext {


    private FacesContext context;
    private HashMap scriptEngines;
	
    protected ServletContext ctx;
    protected HttpServletRequest req;
    protected HttpServletResponse res;

    /**
     * 
     * @return The base URL, e.g. http://127.0.0.1:8080/JavaBridge
     */
    public String getBaseURL() {
	return getBaseURL(String.valueOf(CGIServlet.getLocalPort(req)));
    }
    /**
     * 
     * @return The base URL, e.g. http://127.0.0.1:8080/JavaBridge
     */
    public String getBaseURL(String port) {
	StringBuffer buf = new StringBuffer(req.isSecure()?"https://127.0.0.1:":"http://127.0.0.1:");
	buf.append(port);
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
	releaseEngines();
	context.release();
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

    /**
     * Writes the output from php scripts to the servlet log.
     */
    private class LogOutputStream extends OutputStream {
        public void write(int b) throws IOException {
            throw new NotImplementedException();
        }
        public void write(byte[] b, int start, int length) {
            ctx.log(new String(b, start, length));
        }
        public void write(byte[] b, int start) {
            write(b, start, b.length);
        }
        public void write(byte[] b) {
            write(b, 0, b.length);
        }
    }
    private LogOutputStream log = new LogOutputStream();
    /**
     * Get a script engine
     * @param key The Script proxy
     * @param url The URL, for example getBaseURL() + "/foo.php";
     * @return The script engine.
     * @throws ScriptException
     * @throws IOException
     * @throws UnknownHostException
     * @see #getBaseURL()
     */
    public synchronized ScriptEngine getScriptEngine(Object key, URL url)  throws UnknownHostException, ScriptException, IOException  {
	ScriptEngine e = null;
	if((e=(ScriptEngine)scriptEngines.get(key))!=null) return e;
	e = new PhpFacesScriptEngine(ctx, req, res, new PhpScriptWriter(log));
	scriptEngines.put(key, e);
	try {
	    e.eval(new URLReader(url));
	} catch (UnknownHostException ex) {
        scriptEngines.remove(key);
        throw ex;
    } catch (ScriptException ex) {
        scriptEngines.remove(key);
        throw ex;
    } catch (IOException ex) {
        scriptEngines.remove(key);
        throw ex;
    }
	return e;
    }
	
    /**
     * Release all script engines.
     *
     */
    private synchronized void releaseEngines() {
	for(Iterator ii = scriptEngines.keySet().iterator(); ii.hasNext();) {
	    Object key = ii.next();
	    Object val = scriptEngines.get(key);
	    ((PhpScriptEngine)val).release();
	    ii.remove();
	}
    }
    

}
