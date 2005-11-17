/*-*- mode: Java; tab-width:8 -*-*/

package php.java.faces;

import javax.faces.FacesException;
import javax.faces.context.FacesContext;
import javax.faces.context.FacesContextFactory;
import javax.faces.lifecycle.Lifecycle;

/**
 * @author jostb
 *
 */
public class PhpFacesContextFactory extends FacesContextFactory {

    FacesContextFactory factory;
	
    public PhpFacesContextFactory(FacesContextFactory factory) {
	this.factory = factory;
    }
    /* (non-Javadoc)
     * @see javax.faces.context.FacesContextFactory#getFacesContext(java.lang.Object, java.lang.Object, java.lang.Object, javax.faces.lifecycle.Lifecycle)
     */
    public synchronized FacesContext getFacesContext(Object ctx, Object request,
						     Object response, Lifecycle lifecycle)
	throws FacesException {
	FacesContext kontext = factory.getFacesContext(ctx, request, response, lifecycle);
	return new PhpFacesContext(kontext, ctx, request, response);
    }

}
