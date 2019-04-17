package org.jenkinsci.plugins.mesos;

import hudson.model.Executor;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;
import java.io.IOException;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The running state of a {@link hudson.model.Node} or rather {@link MesosSlave} in our case. */
public class MesosComputer extends AbstractCloudComputer<MesosSlave> {

  private static final Logger logger = LoggerFactory.getLogger(MesosComputer.class);

  private final Boolean reusable;
  /**
   * Constructs a new computer. This is called by {@link MesosSlave#createComputer()}.
   *
   * @param slave The {@link hudson.model.Node} this computer belongs to.
   */
  public MesosComputer(MesosSlave slave) {
    super(slave);
    this.reusable = slave.getReusable();
  }

  @Override
  public void taskAccepted(Executor executor, Queue.Task task) {
    super.taskAccepted(executor, task);
    if (!reusable) {
      // single use computer will only accept one task, after completing task it will go idle and be
      // killed by MesosRetentionStrategy
      logger.warn(
          " Computer " + this + ": is no longer accepting tasks and was marked as single-use");
      setAcceptingTasks(false);
    }
    logger.info(" Computer " + this + ": task accepted");
  }

  @Override
  public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
    super.taskCompleted(executor, task, durationMS);
    logger.info(" Computer " + this + ": task completed");
  }

  @Override
  public void taskCompletedWithProblems(
      Executor executor, Queue.Task task, long durationMS, Throwable problems) {
    super.taskCompletedWithProblems(executor, task, durationMS, problems);
    logger.warn(" Computer " + this + " task completed with problems");
  }

  @Override
  public String toString() {
    return String.format("%s (slave: %s)", getName(), getNode());
  }

  @Override
  public MesosSlave getNode() {
    return super.getNode();
  }

  @Override
  public HttpResponse doDoDelete() throws IOException {
    try {
      getNode().terminate();
    } catch (InterruptedException e) {
      logger.warn(" got exception " + e + "failure to delete agent" + getNode().getPodId());
    }
    return new HttpRedirect("..");
  }
}
