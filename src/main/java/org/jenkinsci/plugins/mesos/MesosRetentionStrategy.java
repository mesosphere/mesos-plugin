package org.jenkinsci.plugins.mesos;

import hudson.model.Descriptor;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.RetentionStrategy;

public class MesosRetentionStrategy extends CloudRetentionStrategy {

  public MesosRetentionStrategy(int idleMinutes) {
    super(idleMinutes);
  }

  public static class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
    @Override
    public String getDisplayName() {
      return "Mesos Retention Strategy";
    }
  }
}
