package org.jenkinsci.plugins.mesos.fixture;

import hudson.model.Node.Mode;
import java.util.Collections;
import org.apache.mesos.v1.Protos.ContainerInfo.DockerInfo.Network;
import org.jenkinsci.plugins.mesos.MesosAgentSpecTemplate;
import org.jenkinsci.plugins.mesos.MesosAgentSpecTemplate.ContainerInfo;

/**
 * A Mother object for {@link org.jenkinsci.plugins.mesos.MesosAgentSpecTemplate}.
 *
 * @see <a href="https://martinfowler.com/bliki/ObjectMother.html">ObjectMother</a>
 */
public class AgentSpecMother {

  public static final MesosAgentSpecTemplate simple =
      new MesosAgentSpecTemplate(
          "label", Mode.EXCLUSIVE, "0.1", "32", 1, 1, 1, "0", "", false, "", null);

  public static final MesosAgentSpecTemplate docker =
      new MesosAgentSpecTemplate(
          "label",
          Mode.EXCLUSIVE,
          "0.5",
          "512",
          3,
          1,
          1,
          "1",
          "",
          false,
          "",
          new ContainerInfo(
              "DOCKER",
              "mesosphere/jenkins-dind:0.6.0-alpine",
              true,
              true,
              false,
              false,
              Collections.emptyList(),
              Collections.emptyList(),
              Network.HOST.name(),
              Collections.emptyList()));
}
