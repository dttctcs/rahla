package rahla.components.fradi;

public class FradiException extends RuntimeException{
  public FradiException() {
    super();
  }

  public FradiException(String message) {
    super(message);
  }

  public FradiException(String message, Throwable cause) {
    super(message, cause);
  }

  public FradiException(Throwable cause) {
    super(cause);
  }

  protected FradiException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
