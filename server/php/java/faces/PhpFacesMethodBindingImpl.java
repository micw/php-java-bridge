package php.java.faces;

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
