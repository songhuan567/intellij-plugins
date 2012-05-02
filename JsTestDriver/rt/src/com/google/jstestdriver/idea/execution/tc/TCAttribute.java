package com.google.jstestdriver.idea.execution.tc;

import org.jetbrains.annotations.NotNull;

/**
* @author Sergey Simonchik
*/
public enum TCAttribute {

  NAME("name"),
  TEST_DURATION("duration"),
  LOCATION_URL("locationHint"),
  ACTUAL("actual"),
  EXPECTED("expected"),
  DETAILS("details"),
  STDOUT("out"),
  EXCEPTION_MESSAGE("message"),
  EXCEPTION_STACKTRACE("details"),
  IS_TEST_ERROR("error"),
  ;

  private final String myName;

  TCAttribute(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }
}
