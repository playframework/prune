package controllers;

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import javax.inject.*;
import java.util.ArrayList;
import java.util.List;
import play.*;
import play.http.HttpEntity;
import play.mvc.*;
import play.libs.Json;

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

    assert (length < 100 * 1024): "Chunked download creates all arrays in memory so size is limited to 100,000 bytes";

    // Cap each chunk at 4k
    final int maxArraySize = 4 * 1024;

    // Create all the chunks and put them in a buffer. It would
    // be better to do this lazily, but it's simpler to do it eagerly. :)
    final List<ByteString> chunkBuffer = new ArrayList<ByteString>(length / maxArraySize + 1);
    int remaining = length;
    while (remaining > 0) {
      final int chunkSize = Math.min(remaining, maxArraySize);
      final ByteString chunk = ByteString.fromArray(new byte[chunkSize]);
      chunkBuffer.add(chunk);
      remaining -= chunkSize;
    }

    return ok().chunked(Source.from(chunkBuffer));
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

  public Result jsonEncode() {
    ObjectNode result = Json.newObject();
    result.put("message", "Hello World!");
    return ok(result);
  }

}
