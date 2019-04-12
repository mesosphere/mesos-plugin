package org.jenkinsci.plugins.mesos;

import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import java.util.Collection;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class MesosCloudTest {

  @Test
  public void testProvision() throws Exception {
    LabelAtom label = Mockito.mock(LabelAtom.class);

    MesosCloud cloud = new MesosCloud("mesos", "localhost:5050", "jenkinsUrl", "slaveUrl");

    int workload = 3;
    Collection<NodeProvisioner.PlannedNode> plannedNodes = cloud.provision(label, workload);

    Assert.assertEquals(plannedNodes.size(), workload);
  }
}
