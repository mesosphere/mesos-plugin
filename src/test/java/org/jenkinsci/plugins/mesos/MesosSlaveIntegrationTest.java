package org.jenkinsci.plugins.mesos;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import com.mesosphere.utils.mesos.MesosClusterExtension;
import com.mesosphere.utils.zookeeper.ZookeeperServerExtension;
import hudson.model.labels.LabelAtom;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;

@ExtendWith(TestUtils.JenkinsParameterResolver.class)
public class MesosSlaveIntegrationTest {

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
    public void testAgentLifecycle(TestUtils.JenkinsRule j) throws Exception {
        LabelAtom label = new LabelAtom("label");
        MesosCloud cloud = new MesosCloud("mesos", mesosCluster.getMesosUrl(), "jenkinsUrl", "slaveUrl");

        MesosSlave agent = (MesosSlave) cloud.startAgent().get();
        agent.waitUntilOnlineAsync().get();

        //verify slave is running when the future completes;
        Assert.assertTrue(agent.isRunning());

        agent.terminate();
        await().atMost(5, TimeUnit.MINUTES).until(agent::isKilled);
        Assert.assertTrue(agent.isKilled());
    }
}
