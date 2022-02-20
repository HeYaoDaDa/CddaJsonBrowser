package fun.hydd.cddabrowser.manager;

import fun.hydd.cddabrowser.entity.MyRelease;
import fun.hydd.cddabrowser.entity.MyTag;
import fun.hydd.cddabrowser.entity.NewVersion;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VersionManager {
  public static final String COLLECTION_VERSIONS = "versions";
  private static final String MONGODB_URL = "mongodb://127.0.0.1:27017";
  private static final String COLLECTION_TEST = "test";

  private static final VersionManager instance = new VersionManager();
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private MongoClient mongoClient;
  private Vertx vertx;

  private VersionManager() {
  }

  public static VersionManager getInstance() {
    return instance;
  }

  public Future<Void> init(Vertx vertx) {
    final JsonObject mongoConfig = new JsonObject()
      .put("connection_string", MONGODB_URL)
      .put("db_name", COLLECTION_TEST);
    this.mongoClient = MongoClient.createShared(vertx, mongoConfig);
    this.vertx = vertx;
    return Future.succeededFuture();
  }

  public static NewVersion createVersion(final MyTag myTag, final MyRelease myRelease) {
    return new NewVersion(myTag, myRelease);
  }

  public Future<String> saveVersion(final NewVersion version) {
    return mongoClient.save(COLLECTION_VERSIONS, JsonObject.mapFrom(version));
  }

  public Future<NewVersion> findLatestVersion() {
    final JsonObject queryCondition = new JsonObject();
    return getVersion(queryCondition);
  }

  public Future<NewVersion> findLatestVersionByBranch(final int branch) {
    final JsonObject queryCondition = new JsonObject()
      .put("branch", branch);
    return getVersion(queryCondition);
  }

  private Future<NewVersion> getVersion(JsonObject queryCondition) {
    final FindOptions findOptions = new FindOptions()
      .setSort(new JsonObject().put("myTag.date", -1))
      .setLimit(1);
    return mongoClient
      .findWithOptions(COLLECTION_VERSIONS, queryCondition, findOptions)
      .compose(jsonObjectList -> {
        if (jsonObjectList == null || jsonObjectList.isEmpty()) {
          return Future.failedFuture("no find");
        } else {
          logger.info("findLatestVersionByBranch\n" +
            "{}", jsonObjectList.get(0).encodePrettily());
          final NewVersion version = jsonObjectList.get(0).mapTo(NewVersion.class);
          return Future.succeededFuture(version);
        }
      });
  }

}
