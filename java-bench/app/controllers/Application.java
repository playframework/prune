package controllers;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import play.*;
import play.mvc.*;

import views.html.*;

public class Application extends Controller {

  @BodyParser.Of(BodyParser.Empty.class)
  public static Result simple() {
    return ok("Hello world");
  }

  @BodyParser.Of(BodyParser.Empty.class)
  public static Result download(int length) {
    return ok(new byte[length]);
  }

  @BodyParser.Of(BodyParser.Empty.class)
  public static Result downloadChunked(final int length) {

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
  public static Result upload() {
    return ok("upload"); // TODO: Verify upload happened
  }

  @BodyParser.Of(BodyParser.Empty.class)
  public static Result templateSimple() {
    return ok(views.html.simple.render("simple"));
  }

  @BodyParser.Of(BodyParser.Empty.class)
  public static Result templateLang() {
    return ok(views.html.lang.render());
  }

  //
  // https://github.com/TechEmpower/FrameworkBenchmarks/blob/master/play-java-jpa/app/controllers/Application.java

  private static JsonFactory JSON_FACTORY = new JsonFactory();

  @BodyParser.Of(BodyParser.Empty.class)
  public static Result jsonEncodeStreaming() throws IOException {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    final JsonGenerator generator = JSON_FACTORY.createGenerator(baos);
    generator.writeStartObject();
    generator.writeStringField("message", "Hello World!");
    generator.writeEndObject();
    generator.close();
    response().setContentType("application/json");
    return ok(baos.toByteArray());
  }

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @BodyParser.Of(BodyParser.Empty.class)
  public static Result jsonEncodeDataBinding() {
    final ObjectNode result = OBJECT_MAPPER.createObjectNode();
    result.put("message", "Hello World!");
    return ok(result);
  }

}
