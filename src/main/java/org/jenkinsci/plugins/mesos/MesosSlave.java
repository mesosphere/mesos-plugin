package org.jenkinsci.plugins.mesos;

import static org.awaitility.Awaitility.await;

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
import java.util.concurrent.*;
import org.apache.mesos.v1.Protos.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Representation of a Jenkins node on Mesos. */
public class MesosSlave extends AbstractCloudSlave implements EphemeralNode {

  private static final Logger logger = LoggerFactory.getLogger(MesosSlave.class);

  // Holds the current USI status for this agent.
  Optional<PodStatus> currentStatus = Optional.empty();

  private final Boolean reusable;

  private final MesosCloud cloud;

  private final ExecutorService executorService;

  private final String podId;

  public MesosSlave(
      MesosCloud cloud,
      String id,
      String nodeDescription,
      String labelString,
      List<? extends NodeProperty<?>> nodeProperties)
      throws Descriptor.FormException, IOException {
    super(
        id, nodeDescription, null, 1, null, labelString, new JNLPLauncher(), null, nodeProperties);

    // pass around the MesosApi connection via MesosCloud
    this.cloud = cloud;
    this.reusable = true;
    this.executorService = Executors.newSingleThreadExecutor();
    this.podId = id;
  }

  /**
   * Polls the agent until it is online. Note: This is a non-blocking call in contrast to the
   * blocking {@link AbstractCloudComputer#waitUntilOnline}.
   *
   * @return The future agent that will come online.
   */
  public Future<MesosSlave> waitUntilOnlineAsync() {
    return executorService.submit(
        () -> {
          await()
              .with()
              .pollInterval(2, TimeUnit.SECONDS)
              .and()
              .pollDelay(1, TimeUnit.SECONDS)
              .atMost(5, TimeUnit.MINUTES)
              .until(this::isRunning);

          return this.isRunning() ? this : null;
        });
  }

  /** @return whether the agent is running or not. */
  public synchronized boolean isRunning() {
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
  public synchronized void update(PodStatusUpdated event) {
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
    try {
      logger.info("killing task {}", this.podId);

      // create a terminating spec for this pod
      this.getCloud().getMesosClient().enqueueAgent(this.getCloud(), 0, 0, Optional.of(this.podId));
    } catch (Exception ex) {
      logger.warn("error when killing task {}", this.podId);
    }
  }

  public Boolean getReusable() {
    // TODO: implement reusable slaves
    return reusable;
  }

  public MesosCloud getCloud() {
    return cloud;
  }
}
