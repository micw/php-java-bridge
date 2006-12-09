/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

/*
 * Copyright (C) 2006 Jost Boekemeier
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

/**
     * Represents the pipe or socket channel name.
     * @author jostb
     */
    public abstract class AbstractChannelName {
        protected String name, kontext;
        protected ContextRunner runner;
        protected IContextFactory currentCtx;
        
        /**
         * Create a new ChannelName. 
         * @param name The name of the channel, see X_JAVABRIDGE_CHANNEL
         * @param kontext The name of client's default context, see X_JAVABRIDGE_CONTEXT_DEFAULT
         * @param currentCtx The ContextFactory associated with the current request.
         */
        public AbstractChannelName(String name, String kontext, IContextFactory currentCtx) {
            this.name = name;
            this.kontext = kontext;
            this.currentCtx = currentCtx;
        }
        /**
         * Returns the name of the channel, for example the socket # or the pipe name.
         * @return the name of the channel
         */
        public String getName() {
            return name;
        }
        
        /**
         * Returns the default (persistent) name of the channel, if it exists. Otherwise it returns the name.
         * @return the persistent name of the channel from X_JAVABRIDGE_CONTEXT_DEFAULT.
         * @see #getName()
         */
        public String getDefaultName() {
            if(runner!=null) return runner.getChannel().getName();
            return name;
        }
        /**
         * Check if the default name exists, see X_JAVABRIDGE_CONTEXT_DEFAULT
         * @return true, if the persistent name exists, false otherwise.
         */
        public boolean hasDefault() {
            return runner!=null;
        }
        /**
         * Return the 
         * @return the value for X_JAVABRIDGE_CONTEXT.
         */
        public IContextFactory getCtx() {
            return currentCtx;
        }
        /**
         * @return the value for X_JAVABRIDGE_CONTEXT_DEFAULT.
         * @see #getDefaultName() 
         */
        public String getKontext() {
            return kontext;
        }
        /**
         * Start the channel. This method calls IContextServer.start()
         */
        protected abstract boolean startChannel();
        
        /**
         * Check if there's a persistent ContextRunner available for the kontext.
         * After calling this method the persistent name can be queried with 
         * {@link #getDefaultName()} and
         * {@link #hasDefault()}.
         * @return the default name or null.
         */
        public ContextRunner schedule() {
            return runner = ContextRunner.checkRunner(this);
        }
        /**
         * Recycle a ContextRunner for a new ContextServer. It checks if a ContextRunner is available for the 
         * default ContextFactory and updates the ContextRunner with the JavaBridge from the fresh ContextFactory.
         * The new JavaBridge and ContextFactory instance are destroyed, when the request is done.
         */
	public void recycle() {
	    if(runner != null) 
		runner.recycle(currentCtx);
	}
	/**
	 * Start a new ContextRunner for a given ContextServer. The current ContextFactory becomes the 
	 * default for this runner.
	 * @return true, if the channel is available, false otherwise.
	 */
	public boolean start() {
	    if(runner == null) 
		return startChannel();
	    return true;
	}
    }