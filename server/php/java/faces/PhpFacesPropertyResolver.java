/*-*- mode: Java; tab-width:8 -*-*/

package php.java.faces;

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
