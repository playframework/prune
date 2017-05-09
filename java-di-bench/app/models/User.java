package models;

import play.data.validation.Constraints;

public class User {

  @Constraints.Required
  public String name;

  @Constraints.Email
  public String email;

  @Constraints.Required
  public Integer age;

  public String twitter;
  public String github;

  public String getName() {
    return this.name;
  }

  public String getEmail() {
    return this.email;
  }

  public Integer getAge() {
    return this.age;
  }

  public String getTwitter() {
    return this.twitter;
  }

  public String getGithub() {
    return this.github;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setEmail(String email) {
    this.email = email;
  }
  
  public void setAge(Integer age) {
    this.age = age;
  }

  public void setTwitter(String twitter) {
    this.twitter = twitter;
  }

  public void setGithub(String github) {
    this.github = github;
  }

}
