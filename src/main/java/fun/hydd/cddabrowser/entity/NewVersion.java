package fun.hydd.cddabrowser.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NewVersion {
  private MyTag myTag;
  private MyRelease myRelease;

  public NewVersion() {
  }

  public NewVersion(MyTag myTag, MyRelease myRelease) {
    this.myTag = myTag;
    this.myRelease = myRelease;
  }

  public MyTag getMyTag() {
    return myTag;
  }

  public void setMyTag(MyTag myTag) {
    this.myTag = myTag;
  }

  public MyRelease getMyRelease() {
    return myRelease;
  }

  public void setMyRelease(MyRelease myRelease) {
    this.myRelease = myRelease;
  }
}
