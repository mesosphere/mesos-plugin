package org.jenkinsci.plugins.mesos;

import com.mesosphere.usi.core.models.PodSpec;
import org.apache.commons.lang.NotImplementedException;

import java.util.concurrent.CompletableFuture;

public class MesosApi {

    public CompletableFuture<MesosSlave> startAgent() {
        throw new NotImplementedException();
    }

}
