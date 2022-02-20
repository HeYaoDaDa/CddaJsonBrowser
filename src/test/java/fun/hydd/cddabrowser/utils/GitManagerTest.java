package fun.hydd.cddabrowser.utils;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
class GitManagerTest {
  private static final Logger log = LoggerFactory.getLogger(GitManagerTest.class);

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext testContext) {
    vertx.deployVerticle(new GitManager(), new DeploymentOptions().setWorker(true).setMaxWorkerExecuteTime(30).setMaxWorkerExecuteTimeUnit(TimeUnit.MINUTES))
      .compose(s -> {
        String testRepoPath = Objects.requireNonNull(GitManagerTest.class.getClassLoader().getResource("")).getPath();
        testRepoPath += File.separator + "Cataclysm-DDA-test";
        JsonObject jsonObject = new JsonObject().put("repoName", testRepoPath).put("repoUrl", "https://github.com/HeYaoDaDa/Cataclysm-DDA.git");
        return vertx.eventBus().request("git-manager.init",jsonObject);
      }).onSuccess(event -> {
      log.info("git-manager.init success");
      testContext.completeNow();
    });
  }

  @Test
  void getLatestTag() throws IOException {
    assertThat("cdda-experimental-2022-02-16-0646").isEqualTo("cdda-experimental-2022-02-16-0646");
  }
}
