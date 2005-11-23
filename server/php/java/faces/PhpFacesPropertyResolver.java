/*-*- mode: Java; tab-width:8 -*-*/

package php.java.faces;

import javax.faces.el.EvaluationException;
import javax.faces.el.PropertyNotFoundException;
import javax.faces.el.PropertyResolver;


/**
 * A custom property resolver for php scripts
 * @author jostb
 * @see php.java.faces.Script
 */
public class PhpFacesPropertyResolver extends javax.faces.el.PropertyResolver {

    /**
     * Create a custom resolver from the given resolver.
     * @param resolver
     */
    public PhpFacesPropertyResolver(PropertyResolver resolver) {
    }
    /* (non-Javadoc)
     * @see javax.faces.el.PropertyResolver#getType(java.lang.Object, int)
     */
    public Class getType(Object base, int index) throws EvaluationException, PropertyNotFoundException {
	return String.class;
    }

    /* (non-Javadoc)
     * @see javax.faces.el.PropertyResolver#getType(java.lang.Object, java.lang.Object)
     */
    public Class getType(Object base, Object property) throws EvaluationException, PropertyNotFoundException {
	return String.class;
    }

    /* (non-Javadoc)
     * @see javax.faces.el.PropertyResolver#getValue(java.lang.Object, int)
     */
    public Object getValue(Object base, int index) throws EvaluationException, PropertyNotFoundException {
	return ((Script)base).getValue(index);
    }

    /* (non-Javadoc)
     * @see javax.faces.el.PropertyResolver#getValue(java.lang.Object, java.lang.Object)
     */
    public Object getValue(Object base, Object property) throws EvaluationException, PropertyNotFoundException {
	return ((Script)base).getValue(property);
    }

    /* (non-Javadoc)
     * @see javax.faces.el.PropertyResolver#isReadOnly(java.lang.Object, int)
     */
    public boolean isReadOnly(Object base, int index) throws EvaluationException, PropertyNotFoundException {
	return false;
    }

    /* (non-Javadoc)
     * @see javax.faces.el.PropertyResolver#isReadOnly(java.lang.Object, java.lang.Object)
     */
    public boolean isReadOnly(Object base, Object property) throws EvaluationException, PropertyNotFoundException {
	return false;
    }

    /* (non-Javadoc)
     * @see javax.faces.el.PropertyResolver#setValue(java.lang.Object, int, java.lang.Object)
     */
    public void setValue(Object base, int index, Object value) throws EvaluationException, PropertyNotFoundException {
	((Script)base).setValue(index, value);
    }

    /* (non-Javadoc)
     * @see javax.faces.el.PropertyResolver#setValue(java.lang.Object, java.lang.Object, java.lang.Object)
     */
    public void setValue(Object base, Object property, Object value) throws EvaluationException, PropertyNotFoundException {
	((Script)base).setValue(property, value);
    }
	
}
