package fun.hydd.cddabrowser.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MyRelease {
  @JsonProperty("id")
  private String id;
  @JsonProperty("name")
  private String name;
  @JsonProperty("body")
  private String body;
  @JsonProperty("tag_name")
  private String tagName;
  @JsonProperty("target_commitish")
  private String targetCommit;
  @JsonProperty("prerelease")
  private boolean prerelease;
  @JsonProperty("created_at")
  private Date createdAt;
  @JsonProperty("published_at")
  private Date publishedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public String getTagName() {
    return tagName;
  }

  public void setTagName(String tagName) {
    this.tagName = tagName;
  }

  public String getTargetCommit() {
    return targetCommit;
  }

  public void setTargetCommit(String targetCommit) {
    this.targetCommit = targetCommit;
  }

  public boolean isPrerelease() {
    return prerelease;
  }

  public void setPrerelease(boolean prerelease) {
    this.prerelease = prerelease;
  }

  public Date getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Date createdAt) {
    this.createdAt = createdAt;
  }

  public Date getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(Date publishedAt) {
    this.publishedAt = publishedAt;
  }

  @Override
  public String toString() {
    return "Release{" +
      "id='" + id + '\'' +
      ", name='" + name + '\'' +
      ", body='" + body + '\'' +
      ", tagName='" + tagName + '\'' +
      ", targetCommit='" + targetCommit + '\'' +
      ", prerelease=" + prerelease +
      ", createdAt=" + createdAt +
      ", publishedAt=" + publishedAt +
      '}';
  }
}
