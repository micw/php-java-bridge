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
import javax.faces.component.StateHolder;
import javax.faces.component.UIComponentBase;
import javax.faces.context.FacesContext;
import javax.faces.el.MethodBinding;
import javax.faces.el.MethodNotFoundException;
import javax.faces.el.ReferenceSyntaxException;
import javax.faces.el.ValueBinding;

/**
 * A custom MethodBindingImpl, forwards method calls to Script
 * @author jostb
 * @see php.java.faces.Script
 */
public class PhpFacesMethodBindingImpl extends MethodBinding implements StateHolder
{
    private Class args[];
    private String name;
    private String rawRef;
    private ValueBinding vb;
    private boolean transientFlag;
	 

    public String getExpressionString()
    {
	return rawRef;
    }

 
    public Class getType(FacesContext context)
    {
 	return String.class;
    }

    public Object saveState(FacesContext context)
    {
	Object values[] = new Object[4];
	values[0] = name;
	values[1] = UIComponentBase.saveAttachedState(context, vb);
	values[2] = args;
	values[3] = rawRef;
	return ((Object) (values));
    }

    public void restoreState(FacesContext context, Object state)
    {
	Object values[] = (Object[])state;
	name = (String)values[0];
	vb = (ValueBinding)UIComponentBase.restoreAttachedState(context, values[1]);
	args = (Class[])values[2];
	rawRef = (String)values[3];
    }


    public PhpFacesMethodBindingImpl() {
	transientFlag = false;
    }
    public PhpFacesMethodBindingImpl(Application application, String ref, Class args[])
    {
	transientFlag = false;
	if(application == null || ref == null)
	    throw new NullPointerException();
	if(!ref.startsWith("#{") || !ref.endsWith("}"))
	    {
		throw new ReferenceSyntaxException(ref);
	    }
	rawRef = ref;
	ref = rawRef.substring(2, rawRef.length() - 1);     	
	this.args = args;
	String vbRef = null;
	if(ref.endsWith("]"))
	    {
		int left = ref.lastIndexOf("[");
		vbRef = "#{" + ref.substring(0, left) + "}";
		vb = application.createValueBinding(vbRef);
		name = ref.substring(left + 1);
		name = name.substring(0, name.length() - 1);
	    } else
	    {
		int period = ref.lastIndexOf(".");
		vbRef = "#{" + ref.substring(0, period) + "}";
		vb = application.createValueBinding(vbRef);
		name = ref.substring(period + 1);
	    }
    }

    public Object invoke(FacesContext context, Object args[])
	throws MethodNotFoundException
    {
	Object base;
	if(context == null)
	    throw new NullPointerException();
	base = vb.getValue(context);
	try {
	    return ((Script)base).call(name, this.args, args);
	} catch (Exception e) {
	    throw new MethodNotFoundException(e);
	}
    }


    /* (non-Javadoc)
     * @see javax.faces.component.StateHolder#isTransient()
     */
    public boolean isTransient() {
	return transientFlag;
    }


    /* (non-Javadoc)
     * @see javax.faces.component.StateHolder#setTransient(boolean)
     */
    public void setTransient(boolean newTransientValue) {

	transientFlag = newTransientValue;
    }

}
