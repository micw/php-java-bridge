/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import php.java.bridge.NotImplementedException;

/**
 * A connection pool. Example:<br><br>
 * <code>
 * ConnectionPool pool = new ConnectionPool("127.0.0.1", 8080, 20, new ConnectionPool.Factory());<br>
 * ConnectionPool.Connection conn = pool.openConnection();<br>
 * InputStream in =  conn.getInputStream();<br>
 * OutputStream out = conn.getOutputStream();<br>
 * ...<br>
 * in.close();<br>
 * out.close();<br>
 * ...<br>
 * pool.destroy();<br>
 * </code>
 * <p>Instead of using delegation (decorator pattern), it is possible to pass a factory 
 * which may create custom In- and OutputStreams. Example:<br><br>
 * <code>
 * new ConnectionPool(..., new ConnectionPool.Factory() {<br>
 * &nbsp;&nbsp;public InputStream getInputStream() {<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;return new ConnectionPool.DefaultInputStream() {<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;...<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;}<br>
 * &nbsp;&nbsp;}<br>
 * }<br>
 * </code>
 * </p>
 * @author jostb
 *
 */
public class ConnectionPool {

    private String host;
    private int port;
    private int limit;
    private int connections = 0;
    private List freeList = new LinkedList();
    private List connectionList = new LinkedList();
    private Factory factory;
    
    private static final class BufferedOutputStream extends java.io.BufferedOutputStream {
        public BufferedOutputStream(OutputStream out) {
            super(out);
        }
	public byte[] getBuffer() {
            return buf;
        }
    }
    /** Thrown when an IO exception occurs */
    public static class ConnectionException extends IOException {
       private static final long serialVersionUID = -5174286702617481362L;
	protected ConnectionException(IOException ex) {
            super();
            initCause(ex);
        }
    }
    /**
     * In-/OutputStream factory.
     * 
     * Override this class if you want to use your own streams.
     * 
     * @author jostb
     *
     */
    public static class Factory {
        /**
         * Create a new socket and connect
         * it to the given host/port
         * @param host The host, for example 127.0.0.1
         * @param port The port, for example 9667
         * @return The socket
         * @throws UnknownHostException
         * @throws ConnectionException
         */
        public Socket connect(String host, int port) throws ConnectionException, SocketException {
            try {
	      return new Socket(InetAddress.getByName(host), port);
	    } catch (IOException e) {
	        throw new ConnectionException(e);
	    }
        }
        /** 
         * Create a new InputStream.
         * @return The input stream. 
         */
        public InputStream getInputStream() throws ConnectionException {
           DefaultInputStream in = new DefaultInputStream();
           return in;
        }
        /**
         * Create a new OutputStream.
         * @return The output stream.
         * @throws ConnectionException
         */
        public OutputStream getOutputStream() throws ConnectionException {
            DefaultOutputStream out = new DefaultOutputStream();
            return out;
            
        }
    }
    /**
     * Default InputStream used by the connection pool.
     * 
     * @author jostb
     *
     */
    public static class DefaultInputStream extends InputStream {
      private Connection connection;
      private InputStream in;

      protected void setConnection(Connection connection) throws ConnectionException {
	  this.connection = connection;	  
	  try {
	    this.in = connection.socket.getInputStream();
	  } catch (IOException e) {
	      throw new ConnectionException(e);
	  }	  
      }
      public int read(byte buf[]) throws ConnectionException {
	  return read(buf, 0, buf.length);
      }
      public int read(byte buf[], int off, int buflength) throws ConnectionException {
	  try {
	      int count = in.read(buf, off, buflength);
	      if(count==-1) {
		  connection.setIsClosed();
	      }
	      return count;
	  } catch (IOException ex) {
	      connection.setIsClosed();
	      throw new ConnectionException(ex);
	  }
      }
      public int read() throws ConnectionException {
	throw new NotImplementedException();
      }      
      public void close() throws ConnectionException {
	  connection.state|=1;
	  if(connection.state==connection.ostate)
	    try {
	      connection.close();
	    } catch (IOException e) {
	      throw new ConnectionException(e);
	    }
      }
    }
    /**
     * Default OutputStream used by the connection pool.
     * 
     * @author jostb
     *
     */
    public static class DefaultOutputStream extends OutputStream {
        private Connection connection;
        private BufferedOutputStream out;
        
	protected void setConnection(Connection connection) throws ConnectionException {
	    this.connection = connection;
            try {
	      this.out = new BufferedOutputStream(connection.socket.getOutputStream());
	    } catch (IOException e) {
	      throw new ConnectionException(e);
	    }
	}
	public void write(byte buf[]) throws ConnectionException {
	    write(buf, 0, buf.length);
	}
	public void write(byte buf[], int off, int buflength) throws ConnectionException {
	  try {
	      out.write(buf, off, buflength);
	  } catch (IOException ex) {
	      connection.setIsClosed();
	      throw new ConnectionException(ex);
	  }
	}
	public void write(int b) throws ConnectionException {
	    throw new NotImplementedException();
	}
	public void close() throws ConnectionException {
	    try { 
	        flush();
	    } finally {
	        connection.state|=2;
	        if(connection.state==connection.ostate)
		  try {
		    connection.close();
		  } catch (IOException e) {
		     throw new ConnectionException(e);
		  }
	    }
	}
	public void flush() throws ConnectionException {
	    try {
	        out.flush();
	    } catch (IOException ex) {
	        connection.setIsClosed();
	        throw new ConnectionException(ex);
	    }
	}
    }
    /**
     * Represents the connection kept by the pool.
     * 
     * @author jostb
     *
     */
    public final class Connection {
        protected int ostate, state; // bit0: input closed, bit1: output closed
	protected Socket socket;
	private String host;
	private int port;
	private DefaultOutputStream outputStream;
	private DefaultInputStream inputStream;
	private boolean isClosed;
	private Factory factory;
	
	protected void reset() {
            this.state = this.ostate = 0;	    
	}
	protected void init() throws ConnectionException, SocketException {
            this.socket = factory.connect(host, port);
            this.isClosed = false;
            inputStream = null;
            outputStream = null;
            reset();
	}
	protected Connection(String host, int port, Factory factory) throws ConnectionException, SocketException {
            this.host = host;
            this.port = port;
            this.factory = factory;
            init();
        }
	protected void setIsClosed() {
	    isClosed=true;
	}
	protected void close() throws ConnectionException, SocketException {
	    if(isClosed) {
	        destroy();
	        init();
	    }
	    closeConnection(this);
        }

	protected void destroy() {
	    try {
	        socket.close();
	    } catch (IOException e) {/*ignore*/}
	}
	/**
	 * Returns the OutputStream associated with this connection.
	 * @return The output stream.
	 * @throws IOException
	 */
	public OutputStream getOutputStream() throws ConnectionException {
	    if(outputStream != null) return outputStream;
	    DefaultOutputStream outputStream = (DefaultOutputStream) factory.getOutputStream();
	    outputStream.setConnection(this);
	    ostate |= 2;
	    return outputStream;
	}
	/**
	 * Returns the InputStream associated with this connection.
	 * @return The input stream.
	 * @throws IOException
	 */
	public InputStream getInputStream() throws ConnectionException {
	    if(inputStream != null) return inputStream;
	    DefaultInputStream inputStream = (DefaultInputStream) factory.getInputStream();
	    inputStream.setConnection(this);
	    ostate |= 1;
	    return inputStream;
	}
    }
    /**
     * Create a new connection pool.
     * 
     * @param host The host
     * @param port The port number
     * @param limit The max. number of physical connections
     * @param factory A factory for creating In- and OutputStreams.
     * @throws ConnectionException 
     * @throws UnknownHostException 
     * @see ConnectionPool.Factory
     */
    public ConnectionPool(String host, int port, int limit, Factory factory) throws ConnectionException {
        this.host = host;
        this.port = port;
        this.limit = limit;
        this.factory = factory;
 
        Socket testSocket;
	try {
	  testSocket = new Socket(InetAddress.getByName(host), port);
	  testSocket.close();
	} catch (IOException e) {
	  throw new ConnectionException(e);
	}
    }
    private Connection createNewConnection() throws ConnectionException, SocketException {
        Connection connection = new Connection(host, port, factory);
        connectionList.add(connection);
        connections++;
        return connection;
    }
    /**
     * Opens a connection to the back end.
     * @return The connection
     * @throws UnknownHostException
     * @throws InterruptedException
     * @throws SocketException 
     * @throws IOException 
     */
    public synchronized Connection openConnection() throws InterruptedException, ConnectionException, SocketException {
        Connection connection;
        int size = freeList.size();
      	if(size==0 && connections<limit) {
      	    connection = createNewConnection();
      	} else {
      	    if(size==0) wait();
      	    connection = (Connection) freeList.remove(0);
      	    connection.reset();
      	}
      	return connection;
    }
    private synchronized void closeConnection(Connection connection) {
        freeList.add(connection);
        notify();
    }
    /**
     * Destroy the connection pool. 
     * 
     * It releases all physical connections.
     *
     */
    public synchronized void destroy() {
        for(Iterator ii = connectionList.iterator(); ii.hasNext();) {
            Connection connection = (Connection) ii.next();
            connection.destroy();
        }
    }
}
