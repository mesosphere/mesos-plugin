package org.jenkinsci.plugins.mesos;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.DescribableList;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import org.apache.mesos.Protos.ContainerInfo.DockerInfo.Network;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This POJO describes a Jenkins agent for Mesos on 0.x and 1.x of the plugin. It is used to migrate
 * older configurations to {@link MesosAgentSpecTemplate} during deserialization. See {@link
 * MesosCloud#readResolve()} for the full migration.
 */
public class MesosSlaveInfo {

  private static final Logger logger = LoggerFactory.getLogger(MesosSlaveInfo.class);

  private transient Node.Mode mode;
  private transient String labelString;
  private transient Double slaveCpus;
  private transient Double diskNeeded;
  private transient int slaveMem;
  private transient int minExecutors;
  private transient int maxExecutors;
  private transient int idleTerminationMinutes;
  private transient String jnlpArgs;
  private transient boolean defaultSlave;
  private transient ContainerInfo containerInfo;
  private transient List<URI> additionalURIs;

  // The following fields are dropped during the migration.
  @SuppressFBWarnings("UUF_UNUSED_FIELD")
  private transient Double executorCpus;

  @SuppressFBWarnings("UUF_UNUSED_FIELD")
  private transient int executorMem;

  @SuppressFBWarnings("UUF_UNUSED_FIELD")
  private transient String remoteFSRoot;

  @SuppressFBWarnings("UUF_UNUSED_FIELD")
  private transient String jvmArgs;

  @SuppressFBWarnings("UUF_UNUSED_FIELD")
  private DescribableList<NodeProperty<?>, NodePropertyDescriptor> nodeProperties;

  /**
   * Resolves the old agent configuration after deserialization.
   *
   * @return the agent config as a {@link MesosAgentSpecTemplate}.
   */
  private Object readResolve() {

    // Migrate to 2.x spec template
    return new MesosAgentSpecTemplate(
        this.labelString,
        this.mode,
        this.slaveCpus.toString(),
        Integer.toString(this.slaveMem),
        this.idleTerminationMinutes,
        this.minExecutors,
        this.maxExecutors,
        this.diskNeeded.toString(),
        this.jnlpArgs,
        this.defaultSlave,
        this.additionalURIs,
        this.containerInfo.dockerImage);
  }

  public static class URI extends AbstractDescribableImpl<URI> {
    @Extension
    public static class DescriptorImpl extends Descriptor<URI> {
      public String getDisplayName() {
        return "";
      }
    }

    private final String value;
    private final boolean executable;
    private final boolean extract;

    @DataBoundConstructor
    public URI(String value, boolean executable, boolean extract) {
      this.value = value;
      this.executable = executable;
      this.extract = extract;
    }

    public String getValue() {
      return value;
    }

    public boolean isExecutable() {
      return executable;
    }

    public boolean isExtract() {
      return extract;
    }
  }

  public static class ContainerInfo {

    private final String type;
    private final String dockerImage;
    private final List<Volume> volumes;
    private final List<Parameter> parameters;
    private final String networking;
    private static final String DEFAULT_NETWORKING = Network.BRIDGE.name();
    private final List<PortMapping> portMappings;
    private final List<NetworkInfo> networkInfos;
    private final boolean useCustomDockerCommandShell;
    private final String customDockerCommandShell;
    private final boolean dockerPrivilegedMode;
    private final boolean dockerForcePullImage;
    private final boolean dockerImageCustomizable;

    private ContainerInfo(
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
  }

  public static class Parameter {

    private final String key;
    private final String value;

    public Parameter(String key, String value) {
      this.key = key;
      this.value = value;
    }
  }

  static class Volume {

    private final String containerPath;
    private final String hostPath;
    private final boolean readOnly;

    private Volume(String containerPath, String hostPath, boolean readOnly) {
      this.containerPath = containerPath;
      this.hostPath = hostPath;
      this.readOnly = readOnly;
    }
  }

  static class PortMapping {

    // TODO validate 1 to 65535
    private final Integer containerPort;
    private final Integer hostPort;
    private final String protocol;

    private PortMapping(Integer containerPort, Integer hostPort, String protocol) {
      this.containerPort = containerPort;
      this.hostPort = hostPort;
      this.protocol = protocol;
    }
  }

  static class NetworkInfo {

    private final String networkName;

    private NetworkInfo(String networkName) {
      this.networkName = networkName;
    }
  }
}
