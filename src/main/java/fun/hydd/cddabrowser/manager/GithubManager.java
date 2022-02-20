package fun.hydd.cddabrowser.manager;

import fun.hydd.cddabrowser.Constants;
import fun.hydd.cddabrowser.MainVerticle;
import fun.hydd.cddabrowser.entity.MyRelease;
import fun.hydd.cddabrowser.utils.HttpUtil;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GithubManager {
  private static final GithubManager instance = new GithubManager();
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private Vertx vertx;

  private GithubManager() {
  }

  public static GithubManager getInstance() {
    return instance;
  }

  public Future<Void> init(Vertx vertx) {
    this.vertx = vertx;
    return Future.succeededFuture();
  }

  public Future<MyRelease> getReleaseByTagName(String tagName) {
    String uri = "/repos/" + Constants.USER_CDDA + "/" + Constants.REPOSITORY_CDDA + "/releases/tags/" + tagName;
    RequestOptions requestOptions = new RequestOptions()
      .setHost(Constants.HOST_API_GITHUB)
      .setURI(uri)
      .setMethod(HttpMethod.GET)
      .setPort(443)
      .putHeader("User-Agent", MainVerticle.PROJECT_NAME)
      .setSsl(true);
    return HttpUtil.request(vertx, requestOptions)
      .compose(buffer -> {
        JsonObject jsonObject = buffer.toJsonObject();
        if (jsonObject.isEmpty()) {
          return Future.failedFuture("fillReleaseInfo(),jsonArray is Empty");
        }
        MyRelease myRelease = jsonObject.mapTo(MyRelease.class);
        return Future.succeededFuture(myRelease);
      });
  }
}
