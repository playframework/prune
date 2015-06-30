package controllers;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import javax.inject.*;
import play.*;
import play.mvc.*;

import views.html.*;

@Singleton
public class Application extends Controller {

  @Inject
  public Application() {}

  public Result simple() {
    return ok("Hello world");
  }

  public Result download(int length) {
    return ok(new byte[length]);
  }

  public Result downloadChunked(final int length) {

    Chunks<byte[]> chunks = new ByteChunks() {
      public void onReady(Chunks.Out<byte[]> out) {
        int remaining = length;
        int maxArraySize = 4 * 1024;
        while (remaining > 0) {
          int arraySize = Math.min(remaining, maxArraySize);
          byte[] array = new byte[arraySize];
          out.write(array);
          remaining -= arraySize;
        }
        out.close();
      }
    };

    // Serves this stream with 200 OK
    return ok(chunks);
  }

  @BodyParser.Of(BodyParser.Raw.class)
  public Result upload() {
    return ok("upload"); // TODO: Verify upload happened
  }

  public Result templateSimple() {
    return ok(views.html.simple.render("simple"));
  }

  public Result templateLang() {
    return ok(views.html.lang.render());
  }

}
