package org.jenkinsci.plugins.mesos;

import com.mesosphere.usi.core.models.PodStatus;
import com.mesosphere.usi.core.models.PodStatusUpdated;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.EphemeralNode;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProperty;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang.NotImplementedException;
import org.apache.mesos.v1.Protos.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Representation of a Jenkins node on Mesos. */
public class MesosSlave extends AbstractCloudSlave implements EphemeralNode {

  private static final Logger logger = LoggerFactory.getLogger(MesosSlave.class);

  Optional<PodStatus> currentStatus = Optional.empty();

  public MesosSlave(
      String name,
      String nodeDescription,
      String labelString,
      List<? extends NodeProperty<?>> nodeProperties)
      throws Descriptor.FormException, IOException {
    super(
        name,
        nodeDescription,
        null,
        1,
        null,
        labelString,
        new JNLPLauncher(),
        null,
        nodeProperties);
  }

  /**
   * Polls the agent until it is online. Note: This is a non-blocking call in contrast to the
   * blocking {@link AbstractCloudComputer#waitUntilOnline}.
   *
   * @return This agent when it's online.
   */
  public CompletableFuture<MesosSlave> waitUntilOnlineAsync() {
    throw new NotImplementedException();
  }

  public boolean isRunning() {
    if (currentStatus.isPresent()) {
      return currentStatus
          .get()
          .taskStatuses()
          .values()
          .forall(taskStatus -> taskStatus.getState() == TaskState.TASK_RUNNING);
    } else {
      return false;
    }
  }

  /**
   * Updates the state of the slave.
   *
   * @param event The state event from USI which informs about the task status.
   */
  public void update(PodStatusUpdated event) {
    logger.info("Updating slave for pod {}", event.id().value());
    if (event.newStatus().isDefined()) {
      logger.info("Received new status for {}", event.id().value());
      this.currentStatus = Optional.of(event.newStatus().get());
    }
  }

  @Override
  public Node asNode() {
    return this;
  }

  @Override
  public AbstractCloudComputer createComputer() {
    return new MesosComputer(this);
  }

  @Override
  protected void _terminate(TaskListener listener) {
    throw new NotImplementedException();
  }
}
