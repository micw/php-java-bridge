/*-*- mode: Java; tab-width:8 -*-*/

package php.java.faces;

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
