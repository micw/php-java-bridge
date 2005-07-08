public class NoClassDefFound {

    Object o;
    public static String s = "test okay";

    public NoClassDefFound() {
	o = new DoesNotExist();
    }


    public String toString() {
	return String.valueOf(o);
    }
}

	
