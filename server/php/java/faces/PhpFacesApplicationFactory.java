/*-*- mode: Java; tab-width:8 -*-*/

package php.java.faces;

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

import javax.faces.application.Application;
import javax.faces.application.ApplicationFactory;

/**
 * Create a PhpFacesApplication.
 * @author jostb
 *
 */
public class PhpFacesApplicationFactory extends javax.faces.application.ApplicationFactory {

    private ApplicationFactory factory;
    private Application application = null;
	
    public PhpFacesApplicationFactory(ApplicationFactory factory) {
	this.factory = factory;
    }
    public PhpFacesApplicationFactory() {
        throw new IllegalStateException("DefaultApplicationFactory missing. Please update your jsf-impl.jar and jsf-api.jar.");
    }
	
    /* (non-Javadoc)
     * @see javax.faces.context.PhpFacesApplicationFactory#getApplication()
     */
    public synchronized Application getApplication() {
	if(application==null)
	    return application = new PhpFacesApplication(factory.getApplication());
	return application;
    }

    /* (non-Javadoc)
     * @see javax.faces.context.PhpFacesApplicationFactory#setApplication(javax.faces.context.Application)
     */
    public void setApplication(Application application) {
	if(!(application instanceof PhpFacesApplication))
	    setApplication(getApplication());
	else
	    this.application = application;
    }

}
