package org.jenkinsci.plugins.mesos;

import com.mesosphere.usi.core.models.commands.LaunchPod;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang.StringUtils;
import org.apache.mesos.Protos.ContainerInfo.DockerInfo.Network;
import org.jenkinsci.plugins.mesos.api.LaunchCommandBuilder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/** This is the Mesos agent pod spec config set by a user. */
public class MesosAgentSpecTemplate extends AbstractDescribableImpl<MesosAgentSpecTemplate> {

  private final String label;
  private final Set<LabelAtom> labelSet;

  private final Node.Mode mode;
  private final int idleTerminationMinutes;
  private final boolean reusable;
  private final double cpus;
  private final int mem;
  private final double disk;
  private final int minExecutors;
  private final int maxExecutors;
  private final String jnlpArgs;
  private final boolean defaultAgent;
  private final String additionalURIs;
  private final ContainerInfo containerInfo;

  @DataBoundConstructor
  public MesosAgentSpecTemplate(
      String label,
      Node.Mode mode,
      String cpus,
      String mem,
      int idleTerminationMinutes,
      int minExecutors,
      int maxExecutors,
      String disk,
      String jnlpArgs,
      boolean defaultAgent,
      String additionalURIs,
      ContainerInfo containerInfo) {
    this.label = label;
    this.labelSet = Label.parse(label);
    this.mode = mode;
    this.idleTerminationMinutes = idleTerminationMinutes;
    this.reusable = false; // TODO: DCOS_OSS-5048.
    this.cpus = Double.parseDouble(cpus);
    this.mem = Integer.parseInt(mem);
    this.minExecutors = minExecutors;
    this.maxExecutors = maxExecutors;
    this.disk = Double.parseDouble(disk);
    this.jnlpArgs = StringUtils.isNotBlank(jnlpArgs) ? jnlpArgs : "";
    this.defaultAgent = defaultAgent;
    this.additionalURIs = additionalURIs;
    this.containerInfo = containerInfo;
    validate();
  }

  private void validate() {}

  @Extension
  public static final class DescriptorImpl extends Descriptor<MesosAgentSpecTemplate> {

    public DescriptorImpl() {
      load();
    }

    /**
     * Validate that CPUs is a positive double.
     *
     * @param cpus The number of CPUs to user for agent.
     * @return Whether the supplied CPUs is valid.
     */
    public FormValidation doCheckCpus(@QueryParameter String cpus) {
      try {
        if (Double.valueOf(cpus) > 0.0) {
          return FormValidation.ok();
        } else {
          return FormValidation.error(cpus + " must be a positive floating-point-number.");
        }
      } catch (NumberFormatException e) {
        return FormValidation.error(cpus + " must be a positive floating-point-number.");
      }
    }
  }

  /**
   * Creates a LaunchPod command to to create a new Jenkins agent via USI
   *
   * @param jenkinsUrl the URL of the jenkins master.
   * @param name The name of the node to launch.
   * @return a LaunchPod command to be passed to USI.
   */
  public LaunchPod buildLaunchCommand(URL jenkinsUrl, String name)
      throws MalformedURLException, URISyntaxException {
    return new LaunchCommandBuilder()
        .withCpu(this.getCpu())
        .withMemory(this.getMemory())
        .withDisk(this.getDisk())
        .withName(name)
        .withJenkinsUrl(jenkinsUrl)
        .withContainerInfo(Optional.ofNullable(this.getContainerInfo()))
        .withJnlpArguments(this.getJnlpArgs())
        .build();
  }

  public String getLabel() {
    return this.label;
  }

  public Set<LabelAtom> getLabelSet() {
    return this.labelSet;
  }

  public Node.Mode getMode() {
    return this.mode;
  }

  /**
   * Generate a new unique name for a new agent. Note: multiple calls will yield different names.
   *
   * @return A new unique name for an agent.
   */
  public String generateName() {
    return String.format("jenkins-agent-%s-%s", this.label, UUID.randomUUID().toString());
  }

  public double getCpu() {
    return this.cpus;
  }

  public double getDisk() {
    return this.disk;
  }

  public int getMemory() {
    return this.mem;
  }

  public int getIdleTerminationMinutes() {
    return this.idleTerminationMinutes;
  }

  public boolean getReusable() {
    return this.reusable;
  }

  public boolean isDefaultAgent() {
    return defaultAgent;
  }

  public String getAdditionalURIs() {
    return additionalURIs;
  }

  public int getMinExecutors() {
    return minExecutors;
  }

  public int getMaxExecutors() {
    return maxExecutors;
  }

  public String getJnlpArgs() {
    return jnlpArgs;
  }

  public ContainerInfo getContainerInfo() {
    return this.containerInfo;
  }

  public static class ContainerInfo {

    private final String type;
    private final String dockerImage;
    private final List<Volume> volumes;
    private final List<Parameter> parameters;
    private final String networking;
    public static final String DEFAULT_NETWORKING = Network.BRIDGE.name();
    private final List<PortMapping> portMappings;
    private final List<NetworkInfo> networkInfos;
    private final boolean useCustomDockerCommandShell;
    private final String customDockerCommandShell;
    private final boolean dockerPrivilegedMode;
    private final boolean dockerForcePullImage;
    private final boolean dockerImageCustomizable;
    private boolean isDind;

    @DataBoundConstructor
    public ContainerInfo(
        String type,
        String dockerImage,
        boolean dockerPrivilegedMode,
        boolean dockerForcePullImage,
        boolean dockerImageCustomizable,
        boolean useCustomDockerCommandShell,
        String customDockerCommandShell,
        List<Volume> volumes,
        List<Parameter> parameters,
        String networking,
        List<PortMapping> portMappings,
        List<NetworkInfo> networkInfos) {
      this.type = type;
      this.dockerImage = dockerImage;
      this.dockerPrivilegedMode = dockerPrivilegedMode;
      this.dockerForcePullImage = dockerForcePullImage;
      this.dockerImageCustomizable = dockerImageCustomizable;
      this.useCustomDockerCommandShell = useCustomDockerCommandShell;
      this.customDockerCommandShell = customDockerCommandShell;
      this.volumes = volumes;
      this.parameters = parameters;
      this.networkInfos = networkInfos;

      if (networking == null) {
        this.networking = DEFAULT_NETWORKING;
      } else {
        this.networking = networking;
      }

      if (Network.HOST.equals(Network.valueOf(networking))) {
        this.portMappings = Collections.emptyList();
      } else {
        this.portMappings = portMappings;
      }
    }

    public boolean getIsDind() {
      return this.isDind;
    }

    public String getType() {
      return type;
    }

    public String getDockerImage() {
      return dockerImage;
    }

    public boolean getDockerPrivilegedMode() {
      return dockerPrivilegedMode;
    }

    public List<Parameter> getParameters() {
      return parameters;
    }

    public List<Parameter> getParametersOrEmpty() {
      return (this.parameters != null) ? this.parameters : Collections.emptyList();
    }

    public String getNetworking() {
      return (networking != null) ? networking : DEFAULT_NETWORKING;
    }

    public boolean getDockerForcePullImage() {
      return dockerForcePullImage;
    }

    public boolean hasPortMappings() {
      return portMappings != null && !portMappings.isEmpty();
    }

    public List<PortMapping> getPortMappings() {
      return (portMappings != null) ? portMappings : Collections.emptyList();
    }

    public List<NetworkInfo> getNetworkInfos() {
      return networkInfos;
    }

    public List<NetworkInfo> getNetworkInfosOrEmpty() {
      return (this.networkInfos != null) ? this.networkInfos : Collections.emptyList();
    }

    public boolean hasNetworkInfos() {
      return networkInfos != null && !networkInfos.isEmpty();
    }

    public List<Volume> getVolumes() {
      return volumes;
    }

    public List<Volume> getVolumesOrEmpty() {
      return (this.volumes != null) ? this.volumes : Collections.emptyList();
    }
  }

  public static class Parameter {

    private final String key;
    private final String value;

    @DataBoundConstructor
    public Parameter(String key, String value) {
      this.key = key;
      this.value = value;
    }

    public String getKey() {
      return key;
    }

    public String getValue() {
      return value;
    }
  }

  public static class Volume {

    private final String containerPath;
    private final String hostPath;
    private final boolean readOnly;

    @DataBoundConstructor
    public Volume(String containerPath, String hostPath, boolean readOnly) {
      this.containerPath = containerPath;
      this.hostPath = hostPath;
      this.readOnly = readOnly;
    }

    public String getContainerPath() {
      return containerPath;
    }

    public String getHostPath() {
      return hostPath;
    }

    public boolean isReadOnly() {
      return readOnly;
    }
  }

  public static class PortMapping {

    // TODO validate 1 to 65535
    private final Integer containerPort;
    private final Integer hostPort;
    private final String protocol;

    @DataBoundConstructor
    private PortMapping(Integer containerPort, Integer hostPort, String protocol) {
      this.containerPort = containerPort;
      this.hostPort = hostPort;
      this.protocol = protocol;
    }

    public Integer getContainerPort() {
      return containerPort;
    }

    public Integer getHostPort() {
      return hostPort;
    }

    public String getProtocol() {
      return protocol;
    }
  }

  public static class NetworkInfo {

    private final String networkName;

    @DataBoundConstructor
    public NetworkInfo(String networkName) {
      this.networkName = networkName;
    }

    public String getNetworkName() {
      return networkName;
    }

    public boolean hasNetworkName() {
      return networkName != null && !networkName.isEmpty();
    }

    public Optional<String> getOptionalNetworkName() {
      return Optional.of(this.networkName).filter(String::isEmpty);
    }
  }
}
