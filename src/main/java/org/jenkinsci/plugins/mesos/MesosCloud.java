package org.jenkinsci.plugins.mesos;

import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.NodeProvisioner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Jenkins Cloud implementation for Mesos.
 *
 * <p>The layout is inspired by the Nomad Plugin.
 *
 * @see https://github.com/jenkinsci/nomad-plugin
 */
class MesosCloud extends AbstractCloudImpl {

  private static final Logger LOGGER = Logger.getLogger(MesosCloud.class.getName());

  private MesosApi mesos;

  private final String frameworkName = "JenkinsMesos";

  private final String slavesUser = "example";

  private String jenkinsUrl;

  private String mesosUrl;

  private String slaveUrl;

  @DataBoundConstructor
  public MesosCloud(String name, String mesosUrl, String jenkinsUrl, String slaveUrl)
      throws InterruptedException, ExecutionException {
    super(name, null);

    String masterUrl = null;
    this.mesos = new MesosApi(masterUrl, slavesUser, frameworkName);
    this.jenkinsUrl = jenkinsUrl;
    this.mesosUrl = mesosUrl;
    this.slaveUrl = slaveUrl;
  }

  /**
   * Provision one or more Jenkins nodes on Mesos.
   *
   * <p>The provisioning follows the Nomad plugin. The Jenkins agnets is started as a Mesos task and
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
        LOGGER.log(
            Level.INFO,
            "Excess workload of "
                + excessWorkload
                + ", provisioning new Jenkins slave on Nomad cluster");
        String slaveName = "undefined";

        nodes.add(
            new NodeProvisioner.PlannedNode(
                slaveName,
                MesosComputer.threadPoolForRemoting.submit(new ProvisioningCallback(this)),
                1));
        excessWorkload--;
      } catch (Exception ex) {
        LOGGER.warning("could not create planned Node");
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
    // TODO: implement executor limits
    return true;
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
  private class ProvisioningCallback implements Callable<Node> {
    MesosCloud cloud;

    ProvisioningCallback(MesosCloud cloud) {
      this.cloud = cloud;
    }

    @Override
    public Node call() throws Exception {
      return mesos
          .enqueueAgent(cloud, 0.1, 32, Optional.empty())
          .thenApply(
              mesosSlave -> {
                try {
                  Jenkins.getInstanceOrNull().addNode(mesosSlave);
                  LOGGER.info("waiting for slave to come online...");
                } catch (Exception e) {
                  LOGGER.info("error occured when waiting for slave to come online...");
                }
                return mesosSlave.waitUntilOnlineAsync();
              })
          .toCompletableFuture()
          .get()
          .get();
    }
  }
}
