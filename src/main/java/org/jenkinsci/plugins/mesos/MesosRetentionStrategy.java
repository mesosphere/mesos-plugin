package org.jenkinsci.plugins.mesos;

import hudson.slaves.CloudRetentionStrategy;

public class MesosRetentionStrategy extends CloudRetentionStrategy {
  public MesosRetentionStrategy(int idleMinutes) {
    super(idleMinutes);
  }
}
