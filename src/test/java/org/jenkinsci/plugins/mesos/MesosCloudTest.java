package org.jenkinsci.plugins.mesos;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import com.mesosphere.utils.mesos.MesosClusterExtension;
import com.mesosphere.utils.zookeeper.ZookeeperServerExtension;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import java.util.Collection;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

@ExtendWith(TestUtils.JenkinsParameterResolver.class)
public class MesosCloudTest {

  @RegisterExtension
  static ZookeeperServerExtension zkServer = new ZookeeperServerExtension();

  static ActorSystem system = ActorSystem.create("mesos-scheduler-test");
  static ActorMaterializer materializer = ActorMaterializer.create(system);

  @RegisterExtension
  static MesosClusterExtension mesosCluster =
          MesosClusterExtension.builder()
                  .withMesosMasterUrl(String.format("zk://%s/mesos", zkServer.getConnectionUrl()))
                  .withLogPrefix(ConnectionTest.class.getCanonicalName())
                  .build(system, materializer);

  @Test
  public void testProvision(TestUtils.JenkinsRule j) throws Exception {
    LabelAtom label = new LabelAtom("label");

    MesosCloud cloud = new MesosCloud("mesos", mesosCluster.getMesosUrl(), "jenkinsUrl", "slaveUrl");

    int workload = 3;
    Collection<NodeProvisioner.PlannedNode> plannedNodes = cloud.provision(label, workload);

    Assert.assertEquals(plannedNodes.size(), workload);
  }
}
