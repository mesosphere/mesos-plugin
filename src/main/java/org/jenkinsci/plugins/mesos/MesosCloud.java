package org.jenkinsci.plugins.mesos;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import jenkins.model.Jenkins;
import org.apache.commons.lang.NotImplementedException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jenkins Cloud implementation for Mesos.
 *
 * <p>The layout is inspired by the Nomad Plugin.
 *
 * @see https://github.com/jenkinsci/nomad-plugin
 */
public class MesosCloud extends AbstractCloudImpl {

  private static final Logger logger = LoggerFactory.getLogger(MesosCloud.class);

  private final URL mesosMasterUrl;
  private MesosApi mesos;

  private final String agentUser;

  private final URL jenkinsUrl;

  private final List<MesosAgentSpec> mesosAgentSpecs;

  @DataBoundConstructor
  public MesosCloud(
      String mesosMasterUrl, String frameworkName, String role, String agentUser, String jenkinsUrl, List<MesosAgentSpec> mesosAgentSpecs)
      throws InterruptedException, ExecutionException, MalformedURLException {
    super("MesosCloud", null);

    this.mesosMasterUrl = new URL(mesosMasterUrl);
    this.jenkinsUrl = new URL(jenkinsUrl);
    this.agentUser = agentUser; // TODO: default to system user
    this.mesosAgentSpecs = mesosAgentSpecs;

    mesos = new MesosApi(this.mesosMasterUrl, this.jenkinsUrl, agentUser, frameworkName, role);
  }

  /**
   * Provision one or more Jenkins nodes on Mesos.
   *
   * <p>The provisioning follows the Nomad plugin. The Jenkins agents is started as a Mesos task and
   * added to the available Jenkins nodes. This differs from the old plugin when the provision
   * method would return immediately.
   *
   * @param label
   * @param excessWorkload
   * @return A collection of future nodes.
   */
  @Override
  public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
    List<NodeProvisioner.PlannedNode> nodes = new ArrayList<>();

    while (excessWorkload > 0) {
      try {
        logger.info(
            "Excess workload of "
                + excessWorkload
                + ", provisioning new Jenkins slave on Mesos cluster");
        String slaveName = "undefined";

        nodes.add(new NodeProvisioner.PlannedNode(slaveName, startAgent(), 1));
        excessWorkload--;
      } catch (Exception ex) {
        logger.warn("could not create planned Node");
      }
    }

    return nodes;
  }

  /**
   * Start a Jenkins agent.jar on Mesos.
   *
   * <p>The future completes when the agent.jar is running on Mesos and the agent became online.
   *
   * @return A future reference to the launched node.
   */
  @Override
  public boolean canProvision(Label label) {
    for (MesosAgentSpec spec : this.mesosAgentSpecs) {
      if (label.matches(spec.getLabelSet())) {
        return true;
      }
    }
    return false;
  }

  public MesosApi getMesosClient() {
    return this.mesos;
  }

  /**
   * Start a Jenkins agent.jar on Mesos.
   *
   * <p>Provide a callback for Jenkins to start a Node.
   *
   * @return A future reference to the launched node.
   */
  public Future<Node> startAgent() throws Exception {
    return mesos
        .enqueueAgent(this, 0.1, 32)
        .thenCompose(
            mesosSlave -> {
              try {
                Jenkins.getInstanceOrNull().addNode(mesosSlave);
                logger.info("waiting for slave to come online...");
                return mesosSlave.waitUntilOnlineAsync();
              } catch (Exception ex) {
                throw new CompletionException(ex);
              }
            })
        .toCompletableFuture();
  }

  @Extension
  public static class DescriptorImpl extends Descriptor<Cloud> {

    public DescriptorImpl() {
      load();
    }

    @Override
    public String getDisplayName() {
      return "Mesos Cloud";
    }

    // TODO: validate URLs

    /** Test connection from configuration page. */
    public FormValidation doTestConnection(
        @QueryParameter("mesosMasterUrl") String mesosMasterUrl) {
      throw new NotImplementedException("Connection testing is not supported yet.");
    }
  }

  // Getters

  public String getMesosMasterUrl() {
    return this.mesosMasterUrl.toString();
  }

  public String getFrameworkName() {
    return this.mesos.getFrameworkName();
  }

  public String getJenkinsUrl() {
    return this.jenkinsUrl.toString();
  }

  public String getAgentUser() {
    return "kjeschkies";
  }

  public String getRole() {
    return "*";
  }
}
