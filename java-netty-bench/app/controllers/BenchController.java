package controllers;

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
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

import models.User;
import play.data.Form;
import play.data.FormFactory;
import play.data.validation.Constraints;

import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;

@Singleton
public class BenchController extends Controller {

  private final FormFactory formFactory;

  @Inject
  public BenchController(FormFactory formFactory) {
    this.formFactory = formFactory;
  }

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

  public Result upload() {
    MultipartFormData<File> body = request().body().asMultipartFormData();
    FilePart<File> uploadedFile = body.getFiles().get(0);

    if (uploadedFile == null) {
      return badRequest("No file");
    }

    String filename = uploadedFile.getFilename();
    File source = uploadedFile.getFile();
    // Do nothing with the file.
    return ok("It works!");
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

  public Result simpleForm() {
    Form<User> userForm = formFactory.form(User.class).bindFromRequest();
    if (userForm.hasErrors()) {
      return badRequest("This shouln't happen");
    }
    User user = userForm.get();
    return ok("It works");
  }
}
