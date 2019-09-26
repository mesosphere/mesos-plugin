package org.jenkinsci.plugins.mesos;

import com.thoughtworks.xstream.XStream;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestUtils.JenkinsParameterResolver.class)
public class MesosCloudTest {

  @Test
  void deserializeOldConfig() {
    final XStream xstream = new XStream();
    MesosCloud cloud = (MesosCloud)xstream.fromXML(new File("/Users/kjeschkies/Projects/mesos-plugin/src/test/resources/config_1.x.xml"));
  }
}
