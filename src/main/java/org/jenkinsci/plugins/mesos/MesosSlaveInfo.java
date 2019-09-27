package org.jenkinsci.plugins.mesos;

public class MesosSlaveInfo {

  // TODO: Define POJO as in 1.x.

  private Object readResolve() {
    System.out.println("Resolve agent spec template");
    return this;
  }
}
