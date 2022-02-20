package fun.hydd.cddabrowser;

import fun.hydd.cddabrowser.entity.JsonEntry;
import fun.hydd.cddabrowser.entity.Version;
import fun.hydd.cddabrowser.exception.NoNeedUpdateException;
import fun.hydd.cddabrowser.utils.FileUtil;
import fun.hydd.cddabrowser.utils.GitManager;
import fun.hydd.cddabrowser.utils.HttpUtil;
import fun.hydd.cddabrowser.utils.JsonEntryUtil;
import fun.hydd.cddabrowser.utils.JsonUtil;
import fun.hydd.cddabrowser.utils.MongoDBUtil;
import fun.hydd.cddabrowser.utils.VersionUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {
  public static final String PROJECT_NAME = "cdda-browser";
  private static final String MONGODB_URL = "mongodb://127.0.0.1:27017";
  private static final String COLLECTION_TEST = "test";

  private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);
  private String translateDirPath;
  private Version latestVersion;

  @Override
  public void start(Promise<Void> startPromise) {
//    final JsonObject mongoConfig = new JsonObject()
//      .put("connection_string", MONGODB_URL)
//      .put("db_name", COLLECTION_TEST);
//    MongoClient.createShared(this.vertx, mongoConfig);
//    vertx.setTimer(1000L, aLong -> timedUpdateGameData());
//    startPromise.complete();
    vertx.deployVerticle(new GitManager(), new DeploymentOptions().setWorker(true).setMaxWorkerExecuteTime(30).setMaxWorkerExecuteTimeUnit(TimeUnit.MINUTES))
      .onSuccess(event -> {
        String testRepoPath = Objects.requireNonNull(MainVerticle.class.getClassLoader().getResource("")).getPath();
        testRepoPath += File.separator + "Cataclysm-DDA-test";
        JsonObject jsonObject = new JsonObject().put("repoName", testRepoPath).put("repoUrl", "https://github.com/HeYaoDaDa/Cataclysm-DDA.git");
        vertx.eventBus().request("git-manager.init", jsonObject).onComplete(event1 -> log.info("git-manager.init is reply!"));
        startPromise.complete();
      });
  }

  private void timedUpdateGameData() {
//    VersionUtil.catchLatestVersionFromGithub(vertx)
//      .onSuccess(version -> latestVersion = version)
//      .compose(this::judgeIsNeedUpdate)
//      .onSuccess(version -> FileUtil.clearUnzipDirAndTranslateDir())
//      .compose(this::downloadGameZip)
//      .compose(gameZipPath -> ProcessUtil.unzip(vertx, gameZipPath, FileUtil.getUnzipDirPath()))
//      .onSuccess(unused -> gameRootDirPath = FileUtil.findGameRootDirPath())
//      .compose(unused -> ProcessUtil.compileMo(vertx, gameRootDirPath))
//      .compose(unused -> FileUtil.copyEnJsonFile(vertx, gameRootDirPath, translateDirPath))
//      .compose(unused -> ProcessUtil.translateJsonFile(vertx, FileUtil.getTranslatePythonShellPath(), gameRootDirPath
//        , translateDirPath))
    Future.succeededFuture()
      .onSuccess(o -> latestVersion = new JsonObject("{\n" +
        "  \"name\" : \"Cataclysm-DDA experimental build 2021-11-14-0817\",\n" +
        "  \"tag_name\" : \"cdda-experimental-2021-11-14-0817\",\n" +
        "  \"target_commitish\" : \"564d632595faa5feb47c7dc9d34a9a5b932e8c08\",\n" +
        "  \"branch\" : 0,\n" +
        "  \"created_at\" : 1636877859000\n" +
        "}").mapTo(Version.class))
      .onSuccess(o -> translateDirPath = FileUtil.getTranslateDirPath() + latestVersion.getTagName())
//      .compose(unused -> this.processGameJsonFile())
      .onSuccess(jsonEntryList -> log.info("processGameJsonFile success"))
      .compose(unused -> this.processOriginalJsonEntry())
      .onSuccess(unused -> log.info("SAVE VERSION"))
      .onFailure(throwable -> {
        if (throwable instanceof NoNeedUpdateException) {
          this.log.warn(throwable.getMessage());
        } else {
          this.log.error("TimedUpdateVersion() is fail:\n", throwable);
        }
      });
  }

  private Future<Void> processOriginalJsonEntry() {
    MongoClient mongoClient = MongoClient.createShared(vertx, new JsonObject());
    return JsonEntryUtil.getAllOriginalCollection(mongoClient)
      .compose(this::processCollectionListOriginalJsonEntry);
  }

  private Future<Void> processCollectionListOriginalJsonEntry(List<String> collectionList) {
    MongoClient mongoClient = MongoClient.createShared(vertx, new JsonObject());
    if (collectionList.isEmpty()) {
      return Future.succeededFuture();
    }
    String collection = collectionList.remove(0);
    return JsonEntryUtil.getSortedMod(mongoClient, collection)
      .compose(strings -> processCollectionListOriginalJsonEntryByMod(collection, strings));
  }

  private Future<Void> processCollectionListOriginalJsonEntryByMod(String collection,
                                                                   List<String> sortedModList) {
    MongoClient mongoClient = MongoClient.createShared(vertx, new JsonObject());
    if (sortedModList.isEmpty()) {
      return Future.succeededFuture();
    }
    String mod = sortedModList.remove(0);
    log.info("start process {},mod is {}", collection, mod);
    return JsonEntryUtil.getNeedProcessInheritJsonObjectByMod(mongoClient, collection, latestVersion, mod)
      .compose(jsonObjectList -> Future.succeededFuture(jsonObjectList.stream()
        .map(jsonObject -> {
          JsonEntry jsonEntry = jsonObject.mapTo(JsonEntry.class);
          jsonEntry.setData(jsonObject.getJsonObject("data"));
          return jsonEntry;
        })
        .collect(Collectors.toList())))
      .compose(jsonEntryList -> JsonEntryUtil.processInheritJsonEntryList(mongoClient, jsonEntryList, sortedModList))
      .compose(jsonEntryList -> JsonEntryUtil.processNewJsonEntryList(mongoClient, jsonEntryList))
      .compose(bulkOperationListMap -> MongoDBUtil.bulkWriteBulkOperationListMap(mongoClient,
        bulkOperationListMap))
      .compose(unused -> processCollectionListOriginalJsonEntryByMod(collection, sortedModList));
  }

  private Future<Void> processGameJsonFile() {
    List<File> fileList = new ArrayList<>();
    fileList.add(new File(translateDirPath));
    return FileUtil.scanDirectory(fileList, this::processFile);
  }

  private Future<Void> processFile(File file) {
    if (file.isDirectory() || !JsonUtil.isJsonFile(file) || JsonUtil.isIgnoreJsonFile(file)) {
      return Future.succeededFuture();
    }
    log.info("process file is {}", file);
    MongoClient mongoClient = MongoClient.createShared(vertx, new JsonObject());
    return vertx.fileSystem().readFile(file.getAbsolutePath())
      .compose(buffer -> MongoDBUtil.escapeBsonField(buffer, vertx))
      .compose(buffer -> {
        List<JsonObject> jsonObjectList = JsonUtil.bufferToJsonObjectList(buffer);
        return JsonEntryUtil.processNewJsonEntryListByJsonObjectList(mongoClient,
          JsonEntryUtil.parserLanguage(file),
          latestVersion,
          JsonEntryUtil.parserRelativePath(file, latestVersion),
          jsonObjectList)
          .compose(stringListMap -> MongoDBUtil.bulkWriteBulkOperationListMap(mongoClient, stringListMap));
      });
  }

  private Future<Version> judgeIsNeedUpdate(Version version) {
    MongoClient mongoClient = MongoClient.createShared(vertx, new JsonObject());
    return VersionUtil.findLatestVersionByBranch(mongoClient, version.getBranch())
      .compose(dbVersion -> {
        if (dbVersion == null || version.getCreatedAt().after(dbVersion.getCreatedAt())) {
          return Future.succeededFuture(version);
        } else {
          return Future.failedFuture(new NoNeedUpdateException());
        }
      });
  }

  private Future<String> downloadGameZip(Version version) {
    final String tagName = version.getTagName();
    String downloadUrl = HttpUtil.GITHUB_DOWNLOAD_SOURCE_ZIP_URL_PREFIX + tagName;
    String targetFilePath = FileUtil.getApplicationHomePath() + tagName;
    return HttpUtil.downloadFile(vertx, downloadUrl, targetFilePath);
  }

}
