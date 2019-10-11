package org.jenkinsci.plugins.mesos.api;

import static org.apache.mesos.v1.Protos.ContainerInfo.Type.MESOS;
import static org.apache.mesos.v1.Protos.Image.Type.DOCKER;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.usi.core.models.TaskBuilder;
import com.mesosphere.usi.core.models.TaskName;
import com.mesosphere.usi.core.models.resources.ResourceRequirement;
import com.mesosphere.usi.core.models.template.FetchUri;
import com.mesosphere.usi.core.models.template.LegacyLaunchRunTemplate;
import com.mesosphere.usi.core.models.template.RunTemplate;
import com.mesosphere.usi.core.models.template.SimpleRunTemplateFactory.SimpleTaskInfoBuilder;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.mesos.v1.Protos.ContainerInfo;
import org.apache.mesos.v1.Protos.ContainerInfo.DockerInfo;
import org.apache.mesos.v1.Protos.ContainerInfo.DockerInfo.Network;
import org.apache.mesos.v1.Protos.ContainerInfo.DockerInfo.PortMapping;
import org.apache.mesos.v1.Protos.Image;
import org.apache.mesos.v1.Protos.NetworkInfo;
import org.apache.mesos.v1.Protos.Offer;
import org.apache.mesos.v1.Protos.Parameter;
import org.apache.mesos.v1.Protos.Request;
import org.apache.mesos.v1.Protos.Resource;
import org.apache.mesos.v1.Protos.TaskInfo;
import org.apache.mesos.v1.Protos.Value;
import org.apache.mesos.v1.Protos.Volume;
import org.apache.mesos.v1.Protos.Volume.Mode;
import org.jenkinsci.plugins.mesos.MesosSlaveInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.collection.JavaConverters;
import scala.collection.Seq;
import scala.collection.immutable.Map;

/** The builder is used by {@link LaunchCommandBuilder} to construct a USI {@link RunTemplate}. */
public class RunTemplateFactory {

  /**
   * Constructs a {@link RunTemplate} based on the passed parameters.
   *
   * <p>The template uses either the {@link SimpleTaskInfoBuilder} or a custom {@link
   * ContainerInfoTaskInfoBuilder}.
   *
   * @param requirements The resource requirements for a Jenkins agent.
   * @param shellCommand The shell command built by {@link LaunchCommandBuilder}.
   * @param role The Mesos role the Jenkins agent will assume.
   * @param fetchUris Artifacts that are fetched, eg the Jenkins agent.jar.
   * @param containerInfo Optional information for a Docker or Mesos container.
   * @return the new USI run template.
   */
  static RunTemplate newRunTemplate(
      List<ResourceRequirement> requirements,
      String shellCommand,
      String role,
      List<FetchUri> fetchUris,
      Optional<MesosSlaveInfo.ContainerInfo> containerInfo) {
    TaskBuilder taskBuilder =
        new SimpleTaskInfoBuilder(
            convertListToSeq(requirements),
            shellCommand,
            role,
            convertListToSeq(fetchUris),
            Option.empty());
    if (containerInfo.isPresent()) {
      taskBuilder = new ContainerInfoTaskInfoBuilder(taskBuilder, containerInfo.get());
    }
    return new LegacyLaunchRunTemplate(role, taskBuilder);
  }

  private static <T> Seq<T> convertListToSeq(List<T> inputList) {
    return JavaConverters.asScalaIteratorConverter(inputList.iterator()).asScala().toSeq();
  }

  /**
   * This is a small USI {@link TaskBuilder} that wraps the {@link SimpleTaskInfoBuilder} and adds
   * {@link org.apache.mesos.v1.Protos.ContainerInfo} to the Mesos task info.
   */
  public static class ContainerInfoTaskInfoBuilder implements TaskBuilder {

    private static final Logger logger =
        LoggerFactory.getLogger(ContainerInfoTaskInfoBuilder.class);

    public static final String PORT_RESOURCE_NAME = "ports";
    public static final String MESOS_DEFAULT_ROLE = "*";

    final TaskBuilder simpleTaskInfoBuilder;
    final MesosSlaveInfo.ContainerInfo containerInfo;

    /**
     * Constructs a new {@link TaskBuilder}.
     *
     * <p>This is basically a port of JenkinsScheduler.getContainerInfoBuilder from v1.1 of the
     * plugin.
     *
     * @param taskInfoBuilder The original {@link SimpleTaskInfoBuilder}.
     * @param containerInfo The additional container information.
     */
    public ContainerInfoTaskInfoBuilder(
        TaskBuilder taskInfoBuilder, MesosSlaveInfo.ContainerInfo containerInfo) {
      this.simpleTaskInfoBuilder = taskInfoBuilder;
      this.containerInfo = containerInfo;
    }

    @Override
    public Seq<ResourceRequirement> resourceRequirements() {
      return this.simpleTaskInfoBuilder.resourceRequirements();
    }

    @Override
    public void buildTask(
        TaskInfo.Builder builder,
        Offer matchedOffer,
        Seq<Resource> taskResources,
        Map<TaskName, Seq<Resource>> peerTaskResources) {
      this.simpleTaskInfoBuilder.buildTask(builder, matchedOffer, taskResources, peerTaskResources);
      this.getContainerInfoBuilder(matchedOffer, "what name?", builder);
    }

    private void getContainerInfoBuilder(
        Offer offer, String agentName, TaskInfo.Builder taskBuilder) {
      ContainerInfo.Type containerType = ContainerInfo.Type.valueOf(this.containerInfo.getType());

      ContainerInfo.Builder containerInfoBuilder =
          ContainerInfo.newBuilder().setType(containerType);

      switch (containerType) {
        case DOCKER:
          logger.info("Launching in Docker Mode:" + this.containerInfo.getDockerImage());
          DockerInfo.Builder dockerInfoBuilder =
              DockerInfo.newBuilder() //
                  .setImage(this.containerInfo.getDockerImage())
                  .setPrivileged(this.containerInfo.getDockerPrivilegedMode())
                  .setForcePullImage(this.containerInfo.getDockerForcePullImage());

          if (this.containerInfo.getParameters() != null) {
            for (MesosSlaveInfo.Parameter parameter : this.containerInfo.getParameters()) {
              logger.info(
                  "Adding Docker parameter '"
                      + parameter.getKey()
                      + ":"
                      + parameter.getValue()
                      + "'");
              dockerInfoBuilder.addParameters(
                  Parameter.newBuilder()
                      .setKey(parameter.getKey())
                      .setValue(parameter.getValue())
                      .build());
            }
          }

          String networking = this.containerInfo.getNetworking();
          dockerInfoBuilder.setNetwork(Network.valueOf(networking));

          //  https://github.com/jenkinsci/mesos-plugin/issues/109
          if (dockerInfoBuilder.getNetwork() != Network.HOST) {
            containerInfoBuilder.setHostname(agentName);
          }

          if (this.containerInfo.hasPortMappings()) {
            List<MesosSlaveInfo.PortMapping> portMappings = this.containerInfo.getPortMappings();
            Set<Long> portsToUse = findPortsToUse(offer, portMappings.size());
            String roleToUse = findRoleForPorts(offer);
            Iterator<Long> iterator = portsToUse.iterator();
            Value.Ranges.Builder portRangesBuilder = Value.Ranges.newBuilder();

            for (MesosSlaveInfo.PortMapping portMapping : portMappings) {
              PortMapping.Builder portMappingBuilder =
                  PortMapping.newBuilder() //
                      .setContainerPort(portMapping.getContainerPort()) //
                      .setProtocol(portMapping.getProtocol());

              Long portToUse =
                  portMapping.getHostPort() == null
                      ? iterator.next()
                      : Long.valueOf(portMapping.getHostPort());

              portMappingBuilder.setHostPort(portToUse.intValue());

              portRangesBuilder.addRange(
                  Value.Range.newBuilder().setBegin(portToUse).setEnd(portToUse));

              logger.info("Adding portMapping: " + portMapping);
              dockerInfoBuilder.addPortMappings(portMappingBuilder);
            }

            taskBuilder.addResources(
                Resource.newBuilder()
                    .setName("ports")
                    .setType(Value.Type.RANGES)
                    .setRole(roleToUse)
                    .setRanges(portRangesBuilder));
          } else {
            logger.info("No portMappings found");
          }

          containerInfoBuilder.setDocker(dockerInfoBuilder);
          break;
        case MESOS:
          logger.info("Launching in UCR Mode:" + this.containerInfo.getDockerImage());

          Image dockerImage =
              Image.newBuilder()
                  .setType(DOCKER)
                  .setDocker(
                      Image.Docker.newBuilder()
                          .setName(this.containerInfo.getDockerImage())
                          .build())
                  .build();

          containerInfoBuilder
              .setType(MESOS)
              .setMesos(ContainerInfo.MesosInfo.newBuilder().setImage(dockerImage).build());

          if (this.containerInfo.getIsDind()) {
            containerInfoBuilder.addVolumes(
                Volume.newBuilder()
                    .setContainerPath("/var/lib/docker")
                    .setHostPath("docker")
                    .setMode(Mode.RW));
          }
          break;

        default:
          logger.warn("Unknown container type:" + this.containerInfo.getType());
      }

      if (this.containerInfo.getVolumes() != null) {
        for (MesosSlaveInfo.Volume volume : this.containerInfo.getVolumes()) {
          logger.info("Adding volume '" + volume.getContainerPath() + "'");
          Volume.Builder volumeBuilder =
              Volume.newBuilder()
                  .setContainerPath(volume.getContainerPath())
                  .setMode(volume.isReadOnly() ? Mode.RO : Mode.RW);
          if (!volume.getHostPath().isEmpty()) {
            volumeBuilder.setHostPath(volume.getHostPath());
          }
          containerInfoBuilder.addVolumes(volumeBuilder.build());
        }
      }

      if (this.containerInfo.hasNetworkInfos()) {
        for (MesosSlaveInfo.NetworkInfo networkInfo : this.containerInfo.getNetworkInfos()) {

          NetworkInfo.Builder networkInfoBuilder = NetworkInfo.newBuilder();

          networkInfo
              .getOptionalNetworkName()
              .ifPresent(
                  networkName -> {
                    // Add the virtual network specified, trimming edges for whitespace
                    networkInfoBuilder.setName(networkName.trim());
                    logger.info("Launching container on network " + networkName);
                  });

          containerInfoBuilder.addNetworkInfos(networkInfoBuilder.build());
        }
      }

      taskBuilder.setContainer(containerInfoBuilder.build());
    }

    @VisibleForTesting
    SortedSet<Long> findPortsToUse(Offer offer, int maxCount) {
      SortedSet<Long> portsToUse = new TreeSet<Long>();
      List<Value.Range> portRangesList = null;

      // Locate the port resource in the offer
      for (Resource resource : offer.getResourcesList()) {
        if (resource.getName().equals(PORT_RESOURCE_NAME)) {
          portRangesList = resource.getRanges().getRangeList();
          break;
        }
      }

      logger.info("portRangesList=" + portRangesList);

      /**
       * We need to find maxCount ports to use. We are provided a list of port ranges to use We are
       * assured by the offer check that we have enough ports to use
       */
      // Check this port range for ports that we can use
      if (portRangesList != null) {
        for (Value.Range currentPortRange : portRangesList) {
          // Check each port until we reach the end of the current range
          long begin = currentPortRange.getBegin();
          long end = currentPortRange.getEnd();
          for (long candidatePort = begin;
              candidatePort <= end && portsToUse.size() < maxCount;
              candidatePort++) {
            portsToUse.add(candidatePort);
          }
        }
      }

      return portsToUse;
    }

    @VisibleForTesting
    String findRoleForPorts(Offer offer) {

      String role = MESOS_DEFAULT_ROLE;
      // Locate the port resource in the offer
      for (Resource resource : offer.getResourcesList()) {
        if (resource.getName().equals(PORT_RESOURCE_NAME)) {
          role = resource.getRole();
        }
      }
      return role;
    }
  }
}
