/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;


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

import java.util.LinkedList;

/**
 * A blocking FIFO implementation which can be shut down.
 * 
 * @author jostb
 *
 */
public class BlockingQueue implements java.io.Serializable {
    
    private static final long serialVersionUID = -5471314729763001963L;
    private LinkedList queue;
    private boolean destroyed;
    
    /**
     * Create a new queue
     */
    public BlockingQueue () {
	queue = new LinkedList();
	destroyed = false;
    }
    /**
     * Add a new element to the queue
     * @param element the new element
     */
    public synchronized void add(Object element) {
	if (destroyed) return;
	
	queue.add(element);
	notify();
    }
    /**
     * Remove the oldest element from the queue. Block until an element becomes available.
     * 
     * @return the element or null, if the queue has been shut down or its thread has been interrupted.
     */
    public synchronized Object remove() {
	while (!destroyed && queue.isEmpty())
	    try {
	        wait();
            } catch (InterruptedException e) {
        	destroyed = true;
        	return null;
            }
	return destroyed ? null : queue.remove();
    }
    /**
     * Shut down the queue. All locks are released, add and remove become void operations.
     */
    public synchronized void shutdown () {
	destroyed = true;
	notifyAll();
    }
}
