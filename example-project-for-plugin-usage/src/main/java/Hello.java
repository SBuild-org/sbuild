import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hello {

  private final Logger log = LoggerFactory.getLogger(Hello.class);

  public void hello() {
    log.info("Hello");
  }

}
