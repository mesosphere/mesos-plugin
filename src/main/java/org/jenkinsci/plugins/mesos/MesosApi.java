package org.jenkinsci.plugins.mesos;

import com.mesosphere.mesos.conf.MesosClientSettings;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang.NotImplementedException;
import org.apache.mesos.v1.Protos;

public class MesosApi {

  private final String slavesUser;
  private final String frameworkName;
  private final Protos.FrameworkID frameworkId;
  private final MesosClientSettings clientSettings;

  public MesosApi(String user, String frameworkName) {
    this.frameworkName = frameworkName;
    this.frameworkId =
        Protos.FrameworkID.newBuilder().setValue(UUID.randomUUID().toString()).build();
    this.slavesUser = user;

    Config conf = ConfigFactory.load().getConfig("mesos-client");
    this.clientSettings = MesosClientSettings.fromConfig(conf);
    connectClient();
  }

  public CompletableFuture<MesosSlave> startAgent() {
    throw new NotImplementedException();
  }

  /** Establish a connection to Mesos via the v1 client. */
  private void connectClient() {
    Protos.FrameworkInfo frameworkInfo =
        Protos.FrameworkInfo.newBuilder()
            .setUser(slavesUser)
            .setName(frameworkName)
            .setId(frameworkId)
            .addRoles("test")
            .addCapabilities(
                Protos.FrameworkInfo.Capability.newBuilder()
                    .setType(Protos.FrameworkInfo.Capability.Type.MULTI_ROLE))
            .setFailoverTimeout(0d)
            .build();
  }
}
