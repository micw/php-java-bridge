/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

/*
 * Copyright (C) 2003-2007 Jost Boekemeier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER(S) OR AUTHOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import php.java.bridge.Util;
import php.java.bridge.Util.HeaderParser;

/**
 * This class can be used to connect to a HTTP server to allocate and to invoke php scripts.
 * Example:<p>
 * <code>
 * PhpScriptEngine e = new PhpScriptEngine();<br>
 * e.eval(new URLReader(new URL("http://localhost:80/foo.php"));<br>
 * System.out.println(((Invocable)e).invoke("java_get_server_name", new Object[]{}));<br>
 * e.release();<br>
 * </code>
 * @author jostb
 *
 */
public class URLReader extends Reader {

    private URL url;
    private HttpURLConnection conn;

    private HostnameVerifier hostNameVerifier;
    private HostnameVerifier getHostNameVerifier () {
        if (hostNameVerifier != null) return hostNameVerifier;
        return hostNameVerifier = new HostnameVerifier () {
            public boolean verify(String arg0, SSLSession arg1) {
                return true;
            }
         };
    }
    
    private SSLSocketFactory sslSocketFactory;
    private SSLSocketFactory getSslSocketFactory () throws NoSuchAlgorithmException, KeyManagementException {
        if (sslSocketFactory != null) return sslSocketFactory;
        
        X509TrustManager tm = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException { /*ignore*/ }
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException { /*ignore*/ }
            public X509Certificate[] getAcceptedIssuers() { return null; }
        };
        KeyManager[] km = null;
        X509TrustManager[] tma = new X509TrustManager[] { tm };
        SSLContext sslContext = null;
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(km, tma, new java.security.SecureRandom());
        return sslSocketFactory = sslContext.getSocketFactory();
    }
    
    /**
     * Create a special reader which can be used to read data from a URL.
     * @param url
     * @throws IOException
     * @throws UnknownHostException
     */
    public URLReader(URL url) throws UnknownHostException, IOException {
        this.url = url;
        this.conn = (HttpURLConnection) url.openConnection();
        if (this.conn instanceof HttpsURLConnection) {
            allowSelfSignedCertificates ();
        }
            
        this.conn.setDoInput(true);
    }
        
    private void allowSelfSignedCertificates() {
        HttpsURLConnection xcon = (HttpsURLConnection) this.conn;
        try {
            xcon.setSSLSocketFactory(getSslSocketFactory());
        } catch (KeyManagementException e) {
            Util.printStackTrace(e);
        } catch (NoSuchAlgorithmException e) {
            Util.printStackTrace(e);
        }
        xcon.setHostnameVerifier(getHostNameVerifier());
    }

    /**
     * Returns the URL to which this reader connects.
     * @return the URL.
     */
    public URL getURL() {
        return url;
    }

    /**{@inheritDoc}*/
    public int read(char[] cbuf, int off, int len) throws IOException {
            throw new IllegalStateException("Use urlReader.read(Hashtable, OutputStream) or use a FileReader() instead.");
    }

    private void appendListValues (StringBuffer buf, List list)
    {
        for (Iterator ii=list.iterator(); ii.hasNext(); ) {
            buf.append (ii.next());
            if (ii.hasNext()) 
                buf.append ("; ");
        }
    }
    /**
     * Read from the URL and write the data to out.
     * @param env The environment, must contain values for X_JAVABRIDGE_CONTEXT. It may contain X_JAVABRIDGE_OVERRIDE_HOSTS.
     * @param out The OutputStream.
     * @param headerParser The header parser
     * @throws IOException
     */
    public void read(Map env, OutputStream out, HeaderParser headerParser) throws IOException {
        InputStream natIn = null;
        
        try {
            
            String overrideHosts = (String) env.get("X_JAVABRIDGE_OVERRIDE_HOSTS");
            String include = (String) env.get("X_JAVABRIDGE_INCLUDE");
            byte[] buf = new byte[Util.BUF_SIZE];
            
            conn.setRequestMethod("GET");
            conn.setRequestProperty ("X_JAVABRIDGE_CONTEXT", (String)env.get("X_JAVABRIDGE_CONTEXT"));
            if(include!=null) 
                conn.setRequestProperty("X_JAVABRIDGE_INCLUDE", include);
            if(overrideHosts!=null) {
                conn.setRequestProperty("X_JAVABRIDGE_OVERRIDE_HOSTS", overrideHosts);
                // workaround for a problem in php (it confuses the OVERRIDE_HOSTS from the environment with OVERRIDE_HOSTS from the request meta-data 
                conn.setRequestProperty("X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT", overrideHosts);
            }
            natIn = conn.getInputStream();
            if (headerParser != Util.DEFAULT_HEADER_PARSER) {
                StringBuffer sbuf = new StringBuffer ();
                for (Iterator ii = conn.getHeaderFields().entrySet().iterator(); ii.hasNext(); )
                {
                    Map.Entry e = (Entry) ii.next();
                    sbuf.append (e.getKey());
                    sbuf.append (": ");
                    appendListValues (sbuf, (List) e.getValue());
                    headerParser.parseHeader(sbuf.toString());
                    sbuf.setLength(0);
                }
            }
            int count;
            while ((count=natIn.read(buf)) > 0)
                out.write (buf, 0, count);
        } catch (IOException x) {
            Util.printStackTrace(x);
            throw x;
        } finally {
            if(natIn!=null) try { natIn.close(); } catch (IOException e) {/*ignore*/}
        }
    }

    /**{@inheritDoc}*/
    public void close() throws IOException {
    }
    
    /**{@inheritDoc}*/
    public String toString() {
        return String.valueOf(url);
    }
}
