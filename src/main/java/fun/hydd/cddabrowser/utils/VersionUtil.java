package fun.hydd.cddabrowser.utils;

import fun.hydd.cddabrowser.Constants;
import fun.hydd.cddabrowser.MainVerticle;
import fun.hydd.cddabrowser.entity.MyRelease;
import fun.hydd.cddabrowser.entity.Version;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class VersionUtil {
  public static final String GITHUB_RELEASES_URL = "https://api.github.com/repos/CleverRaven/Cataclysm-DDA/releases";
  public static final String COLLECTION_VERSIONS = "versions";
  private static final Logger logger = LoggerFactory.getLogger(VersionUtil.class);

  private VersionUtil() {
  }

  public static Future<MyRelease> getReleaseByTagName(Vertx vertx, String tagName) {
    String uri = "/repos/" + Constants.USER_CDDA + "/" + Constants.REPOSITORY_CDDA + "/releases/tags/" + tagName;
    return HttpUtil.request(vertx,
      new RequestOptions()
        .setHost(Constants.HOST_API_GITHUB)
        .setURI(uri)
        .setMethod(HttpMethod.GET)
        .setPort(443)
        .putHeader("User-Agent", MainVerticle.PROJECT_NAME)
        .setSsl(true)
    ).compose(buffer -> {
      JsonObject jsonObject = buffer.toJsonObject();
      if (jsonObject.isEmpty()) {
        return Future.failedFuture("fillReleaseInfo(),jsonArray is Empty");
      }
      MyRelease myRelease = jsonObject.mapTo(MyRelease.class);
      return Future.succeededFuture(myRelease);
    });
  }

  public static Future<Version> catchLatestVersionFromGithub(Vertx vertx) {
    return HttpUtil.request(vertx,
      new RequestOptions()
        .setAbsoluteURI(GITHUB_RELEASES_URL)
        .putHeader("User-Agent", MainVerticle.PROJECT_NAME)
        .setSsl(true))
      .compose(buffer -> {
        final JsonArray jsonArray = buffer.toJsonArray();
        if (jsonArray.isEmpty()) {
          return Future.failedFuture("catchLatestVersion(),jsonArray is Empty");
        }
        final JsonObject jsonObject = jsonArray.getJsonObject(0);
        final Version version = jsonObject.mapTo(Version.class);
        version.setBranch(parseBranchByTag(version.getTagName()));
        logger.info("catchLatestVersion:\n" +
          "{}", Json.encodePrettily(version));
        return Future.succeededFuture(version);
      });
  }

  public static Future<Version> findLatestVersionByBranch(final MongoClient mongoClient, final int branch) {
    if (mongoClient == null) {
      return Future.failedFuture("findLatestVersionByBranch(),mongoClient is null");
    }
    final JsonObject queryCondition = new JsonObject()
      .put("branch", branch);
    final FindOptions findOptions = new FindOptions()
      .setSort(new JsonObject().put("created_at", -1))
      .setLimit(1);
    return mongoClient
      .findWithOptions(COLLECTION_VERSIONS, queryCondition, findOptions)
      .compose(jsonObjectList -> {
        if (jsonObjectList == null || jsonObjectList.isEmpty()) {
          return Future.succeededFuture();
        } else {
          logger.info("findLatestVersionByBranch\n" +
            "{}", jsonObjectList.get(0).encodePrettily());
          final Version version = jsonObjectList.get(0).mapTo(Version.class);
          return Future.succeededFuture(version);
        }
      });
  }

  public static int parseBranchByTag(final String tag) {
    if (tag == null) {
      return Version.EXPERIMENTAL;
    }
    final String pattern = "0\\.[A-Z](-[0-9])?";
    if (Pattern.matches(pattern, tag)) {
      return Version.STABLE;
    }
    return Version.EXPERIMENTAL;
  }
}
