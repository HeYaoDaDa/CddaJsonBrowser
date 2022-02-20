package fun.hydd.cddabrowser;

import fun.hydd.cddabrowser.entity.MyTag;
import fun.hydd.cddabrowser.entity.NewVersion;
import fun.hydd.cddabrowser.manager.GithubManager;
import fun.hydd.cddabrowser.manager.NewGitManager;
import fun.hydd.cddabrowser.manager.VersionManager;
import fun.hydd.cddabrowser.utils.CommonUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TestVerticle extends AbstractVerticle {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private NewGitManager gitManager;
  private VersionManager versionManager;
  private GithubManager githubManager;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    this.gitManager = NewGitManager.getInstance();
    this.versionManager = VersionManager.getInstance();
    this.githubManager = GithubManager.getInstance();
    init()
      .onSuccess(event -> startPromise.complete())
      .onFailure(startPromise::fail)
      .onSuccess(event -> prepareLatestTagAndLocalTag()
        .onSuccess(event1 -> {
          MyTag latestTag = event1.resultAt(0);
          MyTag dbTag = event1.resultAt(1);

          if (dbTag == null) {
            logger.info("first version,start update json and save version");
            processJson();
          } else if (latestTag.equals(dbTag)) {
            logger.info("latest tag equal db tag, no need update");
          } else if (latestTag.getDate().before(dbTag.getDate())) {
            logger.warn("latest tag before db tag");
          } else if (latestTag.getDate().after(dbTag.getDate())) {
            logger.info("need update json");
            gitManager.getAfterTagList(dbTag.getDate())
              .onSuccess(myTags -> CommonUtil.chainCall(myTags, myTag -> gitManager.reset(myTag.getName())
                .compose(unused -> processJson())
              ));
          }
        })
      )
      .onFailure(event -> logger.info("error", event));
  }

  private CompositeFuture init() {
    JsonObject repository = config().getJsonObject("repository");
    String repositoryPath = System.getProperty("user.home") + File.separator + repository.getString("name");
    Future<NewGitManager> gitManagerFuture = gitManager.init(vertx, repositoryPath, repository.getString("url"));
    Future<Void> versionFuture = versionManager.init(vertx);
    Future<Void> githubFuture = githubManager.init(vertx);
    return CompositeFuture.all(gitManagerFuture, versionFuture, githubFuture);
  }

  private CompositeFuture prepareLatestTagAndLocalTag() {
    Future<MyTag> gitTagFuture = gitManager.update()
      .compose(unused -> gitManager.getLatestTag());
    Future<MyTag> dbTagFuture = versionManager.findLatestVersion()
      .compose(newVersion -> Future.succeededFuture(newVersion.getMyTag()))
      .recover(throwable -> Future.succeededFuture(null));
    return CompositeFuture.all(gitTagFuture, dbTagFuture);
  }

  private Future<MyTag> processJson() {
    return gitManager.getLatestTag()
      .compose(latestTag1 -> {
        //TODO insert json
        logger.info("latest tag is {}", latestTag1);
        return prepareJson().compose(unused -> gitManager.getHeadTag()
          .onSuccess(headTag -> logger.info("head tag is {}", headTag)));
      })
      .onSuccess(myTag -> githubManager.getReleaseByTagName(myTag.getName())
        .onSuccess(myRelease -> {
          NewVersion newVersion = VersionManager.createVersion(myTag, myRelease);
          versionManager.saveVersion(newVersion)
            .onSuccess(versionId -> logger.info("version is save success, tagName: {}, id is {}", myTag.getName(), versionId));
        }));
  }

  private Future<Void> prepareJson() {
    return cleanOutDir()
      .compose(unused -> copyEnOldDir())
      .onFailure(event -> logger.error("error", event));
  }

  private Future<Void> cleanOutDir() {
    JsonObject repository = config().getJsonObject("repository");
    String outPath = System.getProperty("user.home") + File.separator + repository.getString("name") + File.separator + "out";
    return vertx.executeBlocking(promise -> {
      try {
        if (new File(outPath).exists()) {
          FileUtils.cleanDirectory(outPath);
        }
        promise.complete();
      } catch (IOException e) {
        promise.fail(e);
      }
    });
  }

  static final String[] JSON_DIR_PATHS = new String[]{
    "/data/core/",
    "/data/help/",
    "/data/json/",
    "/data/mods/",
    "/data/raw/"};

  private Future<Void> copyEnOldDir() {
    JsonObject repository = config().getJsonObject("repository");
    String repositoryPath = System.getProperty("user.home") + File.separator + repository.getString("name");
    String outPath = repositoryPath + File.separator + "out";
    final FileSystem fileSystem = vertx.fileSystem();
    @SuppressWarnings("rawtypes") final List<Future> futureList = new ArrayList<>();
    final String toDirPath = outPath + File.separator + "en" + File.separator + "old";
    final File toDirFile = new File(toDirPath);
    if (toDirFile.exists()) {
      try {
        FileUtils.deleteDirectory(toDirFile);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    for (final String jsonDirPath : JSON_DIR_PATHS) {
      final String jsonDir = Paths.get(repositoryPath, jsonDirPath).toFile().getPath();
      final File toDirDetailedFile = Paths.get(toDirPath, jsonDirPath.split("/")[2]).toFile();
      final String toDetailedDirPath = toDirDetailedFile.getPath();
      if (!toDirDetailedFile.exists() && !toDirDetailedFile.mkdirs()) {
        return Future.failedFuture("mkdirs " + toDirPath + " fail");
      }
      futureList.add(fileSystem.copyRecursive(jsonDir, toDetailedDirPath, true));
    }
    return CompositeFuture.join(futureList)
      .compose(compositeFuture -> Future.succeededFuture());
  }
}
