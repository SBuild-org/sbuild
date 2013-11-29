import org.testng.annotations.Test;

public class HelloTest {

  private Hello hello = new Hello();

  @Test
  public void test() {
    hello.hello();
  }

}
