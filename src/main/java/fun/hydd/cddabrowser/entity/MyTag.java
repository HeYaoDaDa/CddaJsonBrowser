package fun.hydd.cddabrowser.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MyTag {
  private String name;
  private String message;
  private Date date;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
    this.date = date;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MyTag myTag = (MyTag) o;
    return Objects.equals(name, myTag.name) && Objects.equals(message, myTag.message) && Objects.equals(date, myTag.date);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, message, date);
  }

  @Override
  public String toString() {
    return "Tag{" +
      "name='" + name + '\'' +
      ", message='" + message + '\'' +
      ", date=" + date +
      '}';
  }
}
