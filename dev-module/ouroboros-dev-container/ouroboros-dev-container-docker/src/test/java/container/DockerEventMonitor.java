package container;

import java.util.Objects;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Event;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

/**
 * @author liansz
 **/
public class DockerEventMonitor {
  public static void main(String[] args) throws InterruptedException {
    DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost("tcp://localhost:2375")
        .withRegistryUsername(null)
        .withRegistryPassword(null)
        .build();

    DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();
    Info info = dockerClient.infoCmd().exec();

    dockerClient.eventsCmd().exec(new ResultCallback.Adapter<Event>() {
      @Override
      public void onNext(Event event) {
        if (!Objects.equals(event.getType().getValue(), "container")) {
          return;
        }

        System.out.println(event);
      }
    }).awaitCompletion();
  }
}

