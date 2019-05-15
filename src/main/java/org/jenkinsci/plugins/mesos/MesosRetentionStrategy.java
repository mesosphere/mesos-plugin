package org.jenkinsci.plugins.mesos;

import hudson.model.Descriptor;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.RetentionStrategy;
import java.io.IOException;
import java.time.Duration;
import javax.annotation.concurrent.GuardedBy;
import jenkins.util.SystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A strategy to terminate idle {@link MesosComputer}
 *
 * <p>This is a fork of {@link CloudRetentionStrategy} that supports {@link Duration} to support
 * more fine grained idle times than minutes.
 */
public class MesosRetentionStrategy extends RetentionStrategy<AbstractCloudComputer> {

  private static final Logger logger = LoggerFactory.getLogger(MesosRetentionStrategy.class);

  final Duration idleTime;

  /**
   * Constructs a new {@link hudson.slaves.RetentionStrategy}. This is called by {@link
   * MesosJenkinsAgent()}.
   *
   * @param idleTime The duration to wait before terminating an idle {@link MesosComputer}
   */
  public MesosRetentionStrategy(Duration idleTime) {
    this.idleTime = idleTime;
  }

  @Override
  @GuardedBy("hudson.model.Queue.lock")
  public long check(final AbstractCloudComputer c) {
    final AbstractCloudSlave computerNode = c.getNode();
    if (c.isIdle() && !disabled && computerNode != null) {
      logger.debug("Checking if {} has been idle for longer than {}", c.getName(), idleTime);
      final long idleMilliseconds = System.currentTimeMillis() - c.getIdleStartMilliseconds();
      if (idleMilliseconds > idleTime.toMillis()) {
        logger.info("Disconnecting {}", c.getName());
        try {
          computerNode.terminate();
        } catch (InterruptedException | IOException e) {
          logger.warn("Failed to terminate {}", c.getName(), e);
        }
      }
    }
    return 1;
  }

  /** Try to connect to it ASAP. */
  @Override
  public void start(AbstractCloudComputer c) {
    c.connect(false);
  }

  public static boolean disabled =
      SystemProperties.getBoolean(CloudRetentionStrategy.class.getName() + ".disabled");

  public static class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
    @Override
    public String getDisplayName() {
      return "Mesos Retention Strategy";
    }
  }
}
