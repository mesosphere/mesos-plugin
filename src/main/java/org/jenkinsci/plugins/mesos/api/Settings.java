package org.jenkinsci.plugins.mesos.api;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.time.Duration;

/**
 * Operation settings for the Jenkins plugin. These should not be set by Jenkins admins and users
 * but rather by Jenkins operators.
 */
public class Settings {

  private final Duration agentTimeout;
  private final int commandQueueBufferSize;

  public Settings(Duration agentTimeout, int commandQueueBufferSize) {
    this.agentTimeout = agentTimeout;
    this.commandQueueBufferSize = commandQueueBufferSize;
  }

  public Settings withCommandQueueBufferSize(int commandQueueBufferSize) {
    return new Settings(this.agentTimeout, commandQueueBufferSize);
  }

  public Duration getAgentTimeout() {
    return this.agentTimeout;
  }

  public int getCommandQueueBufferSize() {
    return this.commandQueueBufferSize;
  }

  public static Settings fromConfig(Config conf) {
    return new Settings(
        conf.getDuration("agent-timeout"), conf.getInt("command-queue-buffer-size"));
  }

  public static Settings load(ClassLoader loader) {
    Config conf = ConfigFactory.load(loader).getConfig("usi.jenkins");
    return fromConfig(conf);
  }

  public static Settings load() {
    Config conf = ConfigFactory.load().getConfig("usi.jenkins");
    return fromConfig(conf);
  }
}
