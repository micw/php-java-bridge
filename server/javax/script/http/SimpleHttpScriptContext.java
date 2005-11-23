package javax.script.http;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;

import javax.script.SimpleScriptContext;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SimpleHttpScriptContext extends SimpleScriptContext implements HttpScriptContext {
    
    public static final String[] defaultMethods = {"GET", "POST"};
    protected static boolean disableScript = false;
    protected static boolean displayResults = false;
    protected static String docRoot;
    protected static String[] languages;
    protected static String[] methods;
    protected static boolean useSession = true;

    protected HttpServletRequest request;
    protected HttpServletResponse response;
    protected Servlet servlet;

    public SimpleHttpScriptContext() {
        super();
     }
    
    public boolean disableScript() {
        return disableScript;
        
    }
    
    public boolean displayResults() {
        return displayResults;
        
    }
       
    public String[] getAllowedLanguages() {
        return languages;
    }
    
    public Object getAttribute(String key, int scope){
        if(scope == HttpScriptContext.REQUEST_SCOPE){
            return request.getAttribute(key);
            
        }else if(scope == HttpScriptContext.SESSION_SCOPE){
            if(useSession()){
                return request.getSession().getAttribute(key);
        
            }else{
                return null;
            
            }
        }else if(scope == HttpScriptContext.APPLICATION_SCOPE){
            return getServlet().getServletConfig().getServletContext().getAttribute(key);
                        
        }else{
            return null;
            
        }
    }

    
    public void setAttribute(String key, Object value, int scope)
            throws IllegalArgumentException {    	
        if(scope == HttpScriptContext.REQUEST_SCOPE){
            request.setAttribute(key, value);
            
        }else if(scope == HttpScriptContext.SESSION_SCOPE){
            if(useSession()){
                request.getSession().setAttribute(key, value);
                
            }else{
                throw new IllegalArgumentException("Session is disabled");
                
            }        
        }else if(scope == HttpScriptContext.APPLICATION_SCOPE){
            servlet.getServletConfig().getServletContext().setAttribute(key, value);
            
        }else{
            throw new IllegalArgumentException("Invalid scope");            
        }
    }
    
	public void forward(String relativePath) throws ServletException, IOException {
        ServletContext context =  servlet.getServletConfig().getServletContext();

        String baseURI;
        String requestURI = request.getRequestURI();
        
        if(relativePath.startsWith("/")){
            baseURI = requestURI.substring(0, request.getContextPath().length());
            
        }else{
            baseURI = requestURI.substring(0, requestURI.lastIndexOf("/"));
        }
        context.getRequestDispatcher(baseURI+relativePath).forward(request, response);
	}
	

	public String[] getMethods() {
		return methods;
	}

	public HttpServletRequest getRequest() {
        return new HttpScriptRequest(this, request);        
	}
	
	public HttpServletResponse getResponse() {
		return new HttpScriptResponse(this, response);
	}
	
    public Reader getScriptSource() {
        String requestURI = request.getRequestURI();
        String resourcePath =
            requestURI.substring(request.getContextPath().length());
                
        if(docRoot == null){
            InputStream stream =
                servlet.getServletConfig().getServletContext().getResourceAsStream(resourcePath);
            return new InputStreamReader(stream);
        }else{
            String fullPath;
            if(docRoot.endsWith(File.separator)){
                fullPath = docRoot + resourcePath;
            }else{
                fullPath = docRoot+File.separator+resourcePath;
            }
            try{
                return new FileReader(fullPath);                
            }catch(IOException ioe){
                return null;
            }                
        }
	}
	
	public Servlet getServlet() {
		return servlet;
	}
	
    public void include(String relativePath) throws ServletException, IOException {
        ServletContext context =  servlet.getServletConfig().getServletContext();

        String baseURI;
        String requestURI = request.getRequestURI();
        
        if(relativePath.startsWith("/")){
            baseURI = requestURI.substring(0, request.getContextPath().length());
            
        }else{
            baseURI = requestURI.substring(0, requestURI.lastIndexOf("/"));
        }
        context.getRequestDispatcher(baseURI+relativePath).include(request, response);
        
	}
	
    public void initialize(Servlet servlet, HttpServletRequest req,
            HttpServletResponse res) throws ServletException {
		
        request = req;
		response = res;
        this.servlet = servlet;
    }
    
    public static void initializeGlobal(ServletConfig config) throws ServletException {        
        ServletContext context = config.getServletContext();
        
        docRoot = context.getInitParameter("script-directory");
//        if(docRoot == null || !(new File(docRoot).isDirectory())){
//            throw new ServletException("Specifed script directory either does " +
//                    "not exist or not a directory");
//        }
        
        String disable = context.getInitParameter("script-disable");
        if(disable != null && disable.equals("true")){
            disableScript = true;
            return;
        }
        
        String session = context.getInitParameter("script-use-session");
        if(session != null && session.equals("false")){
            useSession = false;
        }
        
        String display = context.getInitParameter("script-display-results");
        if(display != null && display.equals("true")){
            displayResults = true;
        }
        
        String methodNames = context.getInitParameter("script-methods");
        if(methodNames != null){
            methods = methodNames.split(",");
        }else{
            methods = defaultMethods;
        }
        
        String languageNames = context.getInitParameter("allowed-languages");
        if(languageNames != null){
            languages = languageNames.split(",");
        }       

    }
	
	public void release() {
        disableScript = false;
        displayResults = false;
        useSession = true;
		servlet = null;
		request = null;
		response = null;
	}
	
	
	public boolean useSession() {
		return useSession;
	}

    public Writer getWriter() {
        try{
            return response.getWriter();
       }catch(IOException ioe){
           return null;
       }
	}
    

}
