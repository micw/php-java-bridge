/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet.fastcgi;

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

import php.java.bridge.NotImplementedException;
import php.java.servlet.fastcgi.ConnectionPool.Connection;

/**
 * Default InputStream used by the connection pool.
 * 
 * @author jostb
 *
 */
public class DefaultInputStream extends InputStream {
  protected Connection connection;
  private InputStream in;

  protected void setConnection(Connection connection) throws ConnectionException {
      this.connection = connection;	  
      try {
        this.in = connection.channel.getInputStream();
      } catch (IOException e) {
          throw new ConnectionException(connection, e);
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
          throw new ConnectionException(connection, ex);
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
          throw new ConnectionException(connection, e);
        }
  }
}