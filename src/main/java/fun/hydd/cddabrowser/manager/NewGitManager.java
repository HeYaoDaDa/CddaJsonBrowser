package fun.hydd.cddabrowser.manager;

import fun.hydd.cddabrowser.entity.MyTag;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class NewGitManager {
  private static final NewGitManager instance = new NewGitManager();
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private Vertx vertx;
  private Repository repository;
  private Git git;
  private String repositoryUrl;

  private NewGitManager() {
  }

  public static NewGitManager getInstance() {
    return instance;
  }

  public Future<NewGitManager> init(Vertx vertx, String repositoryPath, String repositoryUrl) {
    logger.info("start init GitManager");
    this.vertx = vertx;
    this.repositoryUrl = repositoryUrl;

    return vertx.executeBlocking(promise -> {
      File repositoryDir = new File(repositoryPath);
      if (repositoryDir.exists()) {
        logger.info("repositoryDir '{}' exist", repositoryDir);
        FileRepositoryBuilder fileRepositoryBuilder = new FileRepositoryBuilder();
        File gitDir = Paths.get(repositoryDir.getAbsolutePath(), ".git").toFile();
        try (
          Repository newRepository = fileRepositoryBuilder
            .setGitDir(gitDir)
            .readEnvironment()
            .findGitDir()
            .build()
        ) {
          logger.info("find repository: {}", newRepository.getDirectory());
          this.git = new Git(newRepository);
          promise.complete(this);
        } catch (IOException e) {
          promise.fail(e);
        }
      } else {
        logger.info("repositoryDir '{}' no exist", repositoryDir);
        if (repositoryDir.mkdir()) {
          logger.info("repositoryDir '{}' mkdir", repositoryDir);
          try (
            Git newGit = Git.cloneRepository()
              .setDirectory(repositoryDir)
              .setURI(this.repositoryUrl)
              .setBranch("master")
              .call()
          ) {
            Repository newRepository = newGit.getRepository();
            logger.info("clone repository: {}", newRepository.getDirectory());
            this.git = new Git(newRepository);
            promise.complete(this);
          } catch (GitAPIException e) {
            promise.fail(e);
          }
        } else {
          logger.info("repositoryDir '{}' mkdir fail", repositoryDir);
          promise.fail("repositoryDir '" + repositoryDir + "' mkdir fail");
        }
      }
    });
  }

  public Future<MyTag> getLatestTag() {
    return getLatestTagRef()
      .compose(this::getTagByTagRef);
  }

  public Future<Void> update() {
    return vertx.executeBlocking(event -> {
      try {
        git.pull().setRemote(Constants.DEFAULT_REMOTE_NAME).setRemoteBranchName(Constants.MASTER).call();
      } catch (GitAPIException e) {
        event.fail(e);
      }
      event.complete();
    });
  }

  public Future<List<MyTag>> getAfterTagList(Date date) {
    return vertx.executeBlocking(promise -> {
      try {
        List<MyTag> myTagList = new ArrayList<>();
        List<Ref> localRefs = git.tagList()
          .call();
        List<Future> futureList = new ArrayList<>(localRefs.size());
        for (Ref ref : localRefs) {
          futureList.add(getTagByTagRef(ref)
            .onSuccess(myTag -> {
              if (myTag.getDate().after(date)) {
                myTagList.add(myTag);
              }
            }));
        }
        CompositeFuture.all(futureList)
          .onSuccess(event -> promise.complete(myTagList.stream().sorted(Comparator.comparing(MyTag::getDate)).collect(Collectors.toList())));
      } catch (GitAPIException e) {
        promise.fail(e);
      }
    });
  }

  public Future<Void> reset(String tagName) {
    return vertx.executeBlocking(promise -> {
      try {
        git.reset()
          .setMode(ResetCommand.ResetType.HARD)
          .setRef(tagName)
          .call();
        logger.info("reset to {}", tagName);
        promise.complete();
      } catch (GitAPIException e) {
        promise.fail(e);
      }
    });
  }

  public Future<MyTag> getHeadTag() {
    return getHeadRef()
      .compose(headRef -> getTagRef(headRef.getObjectId())
        .compose(this::getTagByTagRef)
      );
  }

  public Future<List<Ref>> getNoUpdateTagRefList() {
    return vertx.executeBlocking(promise -> {
      try {
        logger.info("start getLocalNoHasRemoteTagRefList()");
        Collection<Ref> remoteRefs = null;
        remoteRefs = git.lsRemote()
          .setRemote(this.repositoryUrl)
          .setTags(true)
          .call();
        logger.info("remoteRefs size is {}", remoteRefs.size());
        List<Ref> localRefs = git.tagList()
          .call();
        logger.info("localRefs size is {}", localRefs.size());
        remoteRefs = remoteRefs
          .stream()
          .filter(
            ref -> localRefs
              .stream()
              .noneMatch(
                ref1 -> ref
                  .getName()
                  .equals(ref1.getName())
              )
          )
          .collect(Collectors.toList());
        promise.complete(List.copyOf(remoteRefs));
      } catch (GitAPIException e) {
        promise.fail(e);
      }
    });
  }

  private Future<Ref> getHeadRef() {
    return vertx.executeBlocking(promise -> {
      try {
        Ref ref = git.getRepository().getRefDatabase().findRef(Constants.R_HEADS + Constants.MASTER);
        promise.complete(ref);
      } catch (IOException e) {
        promise.fail(e);
      }
    });
  }

  private Future<Ref> getLatestTagRef() {
    Ref result = null;
    Date latestDate = null;
    try {
      List<Ref> tagRefs = git.tagList().call();
      for (Ref tagRef : tagRefs) {
        Date currentDate = getTagRefDate(tagRef);
        if (latestDate == null || (currentDate != null && currentDate.after(latestDate))) {
          result = tagRef;
          latestDate = currentDate;
        }
      }
    } catch (GitAPIException e) {
      return Future.failedFuture(e);
    }
    return Future.succeededFuture(result);
  }

  private Future<MyTag> getTagByTagRef(Ref tagRef) {
    MyTag myTag = new MyTag();
    try (RevWalk revWalk = new RevWalk(git.getRepository())) {
      RevObject revObject = revWalk.parseAny(tagRef.getObjectId());
      if (Constants.OBJ_TAG == revObject.getType()) {
        RevTag revTag = (RevTag) revObject;
        myTag.setName(revTag.getTagName());
        myTag.setDate(revTag.getTaggerIdent().getWhen());
        myTag.setMessage(revTag.getFullMessage());
      } else if (Constants.OBJ_COMMIT == revObject.getType()) {
        RevCommit revCommit = (RevCommit) revObject;
        myTag.setName(Repository.shortenRefName(tagRef.getName()));
        myTag.setDate(revCommit.getAuthorIdent().getWhen());
      }
    } catch (IOException e) {
      return Future.failedFuture(e);
    }
    return Future.succeededFuture(myTag);
  }

  private Date getTagRefDate(Ref tagRef) {
    if (tagRef == null) {
      logger.warn("getTagRefDate() parameter has null");
      return null;
    }
    Date result = null;
    try (RevWalk revWalk = new RevWalk(git.getRepository())) {
      try {
        RevObject revObject = revWalk.parseAny(tagRef.getObjectId());
        if (Constants.OBJ_TAG == revObject.getType()) {
          RevTag revTag = (RevTag) revObject;
          result = revTag.getTaggerIdent().getWhen();
        } else if (Constants.OBJ_COMMIT == revObject.getType()) {
          RevCommit revCommit = (RevCommit) revObject;
          result = revCommit.getAuthorIdent().getWhen();
        } else {
          logger.warn("getTagRefDate() ragRef no tag or commit, type is {}, id is {}", revObject.getType(), tagRef.getObjectId());
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return result;
  }

  private Future<Ref> getTagRef(ObjectId commitObjectId) {
    return vertx.executeBlocking(promise -> {
      List<Ref> result = new ArrayList<>(1);
      List<Future> futureList = new ArrayList<>();
      try (RevWalk revWalk = new RevWalk(git.getRepository())) {
        List<Ref> tagRefs = git.tagList().call();
        for (Ref tagRef : tagRefs) {
          futureList.add(equalTagRefAndCommitObjectId(tagRef, commitObjectId, revWalk)
            .onSuccess(aBoolean -> {
              if (Boolean.TRUE.equals(aBoolean)) {
                result.add(tagRef);
              }
            }));
        }
      } catch (GitAPIException e) {
        e.printStackTrace();
      }
      CompositeFuture.all(futureList)
        .onSuccess(event -> {
          if (result.size() == 1) {
            promise.complete(result.get(0));
          } else if (result.isEmpty()) {
            logger.warn("getTagRef result size is 0, commitObjectId is {}", commitObjectId);
            promise.fail("getTagRef result size is 0, commitObjectId is " + commitObjectId);
          } else {
            logger.warn("getTagRef result size is greater 1, commitObjectId is {}", commitObjectId);
            for (Ref wrongResult : result) {
              logger.warn("\tname is {}, objectId is {}.", wrongResult.getName(), wrongResult.getObjectId());
            }
            promise.complete(result.get(0));
          }
        });
    });
  }

  private Future<Boolean> equalTagRefAndCommitObjectId(Ref tagRef, ObjectId commitObjectId, RevWalk revWalk) {
    return vertx.executeBlocking(promise -> {
      try {
        RevObject revObject = revWalk.parseAny(tagRef.getObjectId());
        if (Constants.OBJ_TAG == revObject.getType()) {
          RevTag revTag = (RevTag) revObject;
          RevObject peeled = revWalk.peel(revTag.getObject());
          promise.complete(commitObjectId.equals(peeled.getId()));
        } else if (Constants.OBJ_COMMIT == revObject.getType()) {
          promise.complete(commitObjectId.equals(tagRef.getObjectId()));
        } else {
          logger.warn("equalTagRefAndCommitObjectId() ragRef no tag or commit, type is {}, commitObjectId is {}", revObject.getType(), commitObjectId);
          promise.complete(Boolean.FALSE);
        }
      } catch (IOException e) {
        promise.complete(Boolean.FALSE);
      }
    });
  }
}
