package org.jenkinsci.plugins.mesos;

import hudson.util.XStream2;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestUtils.JenkinsParameterResolver.class)
public class MesosCloudTest {

  @Test
  void deserializeOldConfig(TestUtils.JenkinsRule j) {
    final XStream2 xstream = new XStream2();
//    MesosAgentSpecTemplate.DescriptorImpl.serilizationAliases();
    MesosCloud cloud =
        (MesosCloud)
            xstream.fromXML(
                new File(
                    "/Users/kjeschkies/Projects/mesos-plugin/src/test/resources/config_1.x.xml"));
  }
}
