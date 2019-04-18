package org.jenkinsci.plugins.mesos;

import hudson.model.Executor;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The running state of a {@link hudson.model.Node} or rather {@link MesosAgent} in our case. */
public class MesosComputer extends AbstractCloudComputer<MesosAgent> {

  private static final Logger logger = LoggerFactory.getLogger(MesosComputer.class);

  private final Boolean reusable;

  /**
   * Constructs a new computer. This is called by {@link MesosAgent#createComputer()}.
   *
   * @param slave The {@link hudson.model.Node} this computer belongs to.
   */
  public MesosComputer(MesosAgent slave) {
    super(slave);
    this.reusable = slave.getReusable();
  }

  @Override
  public void taskAccepted(Executor executor, Queue.Task task) {
    super.taskAccepted(executor, task);
    logger.info("Computer {}: task accepted", this);
  }

  @Override
  public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
    super.taskCompleted(executor, task, durationMS);
    logger.info("Computer {}: task completed", this);
  }

  @Override
  public void taskCompletedWithProblems(
      Executor executor, Queue.Task task, long durationMS, Throwable problems) {
    super.taskCompletedWithProblems(executor, task, durationMS, problems);
    logger.warn("Computer {} task completed with problems", this);
  }

  @Override
  public String toString() {
    return String.format("%s (slave: %s)", getName(), getNode());
  }
}
