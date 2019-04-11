package org.jenkinsci.plugins.mesos;

import com.mesosphere.usi.core.models.FetchUri;
import com.mesosphere.usi.core.models.Goal.Running$;
import com.mesosphere.usi.core.models.PodId;
import com.mesosphere.usi.core.models.PodSpec;
import com.mesosphere.usi.core.models.PodStatus;
import com.mesosphere.usi.core.models.PodStatusUpdated;
import com.mesosphere.usi.core.models.RunSpec;
import com.mesosphere.usi.core.models.resources.ScalarRequirement;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.EphemeralNode;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProperty;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang.NotImplementedException;
import org.apache.mesos.v1.Protos.TaskState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.collection.JavaConverters;
import scala.collection.Seq;

/** Representation of a Jenkins node on Mesos. */
public class MesosSlave extends AbstractCloudSlave implements EphemeralNode {

  private static final Logger logger = LoggerFactory.getLogger(MesosSlave.class);

  // Holds the current USI status for this agent.
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

  public PodSpec getPodSpec(double cpu, double mem) throws URISyntaxException {
    final var role = "jenkins";
    final var uri = new URI("localhost:80/jnlpJars/agent.jar");
    final var fetchUri = new FetchUri(uri, false, false, false, Option.empty());
    RunSpec spec =
        new RunSpec(
            convertListToSeq(
                Arrays.asList(ScalarRequirement.cpus(cpu), ScalarRequirement.memory(mem))),
            "echo Hello! && sleep 1000000",
            role,
            convertListToSeq(List.of(fetchUri)));
    PodSpec podSpec = new PodSpec(new PodId(this.name), Running$.MODULE$, spec);
    return podSpec;
  }

  private <T> Seq<T> convertListToSeq(List<T> inputList) {
    return JavaConverters.asScalaIteratorConverter(inputList.iterator()).asScala().toSeq();
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
