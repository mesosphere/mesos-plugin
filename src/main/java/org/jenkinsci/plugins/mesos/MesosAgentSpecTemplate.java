package org.jenkinsci.plugins.mesos;

import com.mesosphere.usi.core.models.LaunchPod;
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
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.mesos.api.LaunchCommandBuilder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/** This is the Mesos agent pod spec config set by a user. */
public class MesosAgentSpecTemplate extends AbstractDescribableImpl<MesosAgentSpecTemplate> {

  private final String label;
  private final Set<LabelAtom> labelSet;

  private final Node.Mode mode;
  private final Duration idleTermination;
  private final Boolean reusable;
  private final double cpus;
  private final int mem;
  private final double disk;
  private final int minExecutors;
  private final int maxExecutors;
  private final int executorMem;
  private final String remoteFsRoot;
  private final String jvmArgs;
  private final String jnlpArgs;
  private final boolean defaultAgent;
  private String agentAttributes;
  private final String additionalURIs;
  private String nodeProperties;

  @DataBoundConstructor
  public MesosAgentSpecTemplate(
      String label,
      Node.Mode mode,
      String cpus,
      String mem,
      String idleTerminationMinutes,
      Boolean reusable,
      String minExecutors,
      String maxExecutors,
      String disk,
      String executorMem,
      String remoteFsRoot,
      String agentAttributes,
      String jvmArgs,
      String jnlpArgs,
      String defaultAgent,
      String additionalURIs,
      String nodeProperties) {
    this(
        label,
        Label.parse(label),
        mode,
        Double.parseDouble(cpus),
        Integer.parseInt(mem),
        Duration.ofMinutes(Integer.parseInt(idleTerminationMinutes)),
        reusable,
        Integer.parseInt(minExecutors) < 1 ? 1 : Integer.parseInt(minExecutors),
        Integer.parseInt(maxExecutors),
        Double.parseDouble(disk),
        Integer.parseInt(executorMem),
        StringUtils.isNotBlank(remoteFsRoot) ? remoteFsRoot.trim() : "jenkins",
        StringUtils.isNotBlank(jnlpArgs) ? jnlpArgs : "",
        agentAttributes,
        jvmArgs,
        Boolean.valueOf(defaultAgent),
        additionalURIs,
        nodeProperties);
  }

  /** Typesafe constructor. */
  public MesosAgentSpecTemplate(
      String label,
      Set<LabelAtom> labelSet,
      Node.Mode mode,
      Double cpus,
      int mem,
      Duration idleTermination,
      Boolean reusable,
      int minExecutors,
      int maxExecutors,
      Double disk,
      int executorMem,
      String remoteFsRoot,
      String agentAttributes,
      String jvmArgs,
      String jnlpArgs,
      boolean defaultAgent,
      String additionalURIs,
      String nodeProperties) {
    this.label = label;
    this.labelSet = labelSet;
    this.mode = mode;
    this.idleTermination = idleTermination;
    this.reusable = reusable;
    this.cpus = cpus;
    this.mem = mem;
    this.minExecutors = minExecutors;
    this.maxExecutors = maxExecutors;
    this.disk = disk;
    this.executorMem = executorMem;
    this.remoteFsRoot = remoteFsRoot;
    this.jnlpArgs = jnlpArgs;
    this.defaultAgent = defaultAgent;
    this.agentAttributes = agentAttributes;
    this.jvmArgs = jvmArgs;
    this.additionalURIs = additionalURIs;
    this.nodeProperties = nodeProperties;
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

  public long getIdleTerminationMinutes() {
    return this.idleTermination.toMinutes();
  }

  public Duration getIdleTermination() {
    return this.idleTermination;
  }

  public Boolean getReusable() {
    return this.reusable;
  }

  public String getAgentAttributes() {
    return agentAttributes;
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

  public int getExecutorMem() {
    return executorMem;
  }

  public String getRemoteFsRoot() {
    return remoteFsRoot;
  }

  public String getJvmArgs() {
    return jvmArgs;
  }

  public String getJnlpArgs() {
    return jnlpArgs;
  }

  public String getNodeProperties() {
    return nodeProperties;
  }
}
