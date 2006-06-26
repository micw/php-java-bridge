package php.java.bridge;
public class IllegalArgumentException extends
    java.lang.IllegalArgumentException {

  private static final long serialVersionUID = -6180293871441493489L;

  public IllegalArgumentException(String string, Exception e) {
      super(string);
      initCause(e);
  }

  public IllegalArgumentException(String string) {
      super(string);
  }
}
