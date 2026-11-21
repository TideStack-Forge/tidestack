package container;

import java.util.*;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import io.vavr.control.Either;

import com.ouroboros.container.model.Container;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Application.class})
public class TestContainerService {
  private static final String ENABLED_PROPERTY = "ouroboros.test.docker.integration.enabled";
  private static final String TEST_IMAGE_PROPERTY = "ouroboros.test.docker.integration.image";
  private static final String TEST_VOLUME_PROPERTY = "ouroboros.test.docker.integration.volume";
  private static final String DEFAULT_TEST_IMAGE = "ouroboros-mothership:1.0-SNAPSHOT";

  @Autowired
  ContainerService service;
  private String CONTAINER_ID = "";

  @Before
  public void requireExplicitOptIn() {
    Assume.assumeTrue("Docker integration tests are disabled by default. Set -D"
        + ENABLED_PROPERTY + "=true to enable them.", Boolean.getBoolean(ENABLED_PROPERTY));
  }

  @Test
  public void all() throws Exception {
    System.out.println("---------------------- default ----------------------");
    list();
    System.out.println("---------------------- default ----------------------");
    System.out.println("---------------------- create ----------------------");
    create();
    get();
    System.out.println("---------------------- create ----------------------");
    System.out.println("---------------------- start ----------------------");
    start();
    get();
    System.out.println("---------------------- start ----------------------");
    System.out.println("---------------------- pause ----------------------");
    pause();
    get();
    System.out.println("---------------------- pause ----------------------");
    System.out.println("---------------------- stop ----------------------");
    stop();
    get();
    System.out.println("---------------------- stop ----------------------");
    System.out.println("---------------------- restart ----------------------");
    restart();
    get();
    System.out.println("---------------------- restart ----------------------");
    System.out.println("---------------------- delete ----------------------");
    stop();
    delete();
    list();
    System.out.println("---------------------- delete ----------------------");
  }

  @Test
  public void create() throws Exception {
    Container container = new Container();

    container.setName("create-test-002");
    container.setImage(System.getProperty(TEST_IMAGE_PROPERTY, DEFAULT_TEST_IMAGE));
    container.setPortBindings(Collections.singletonMap("tcp", Collections.singletonMap(80, 81)));
    var testVolume = System.getProperty(TEST_VOLUME_PROPERTY);
    if (testVolume != null && !testVolume.isEmpty()) {
      container.setVolumeBindings(Collections.singletonMap("/home/public/application", testVolume));
    }
    container.setEnvironmentalVariables(Collections.singletonMap("DEBUG", "true"));
    container.setLabels(Collections.singletonMap("created", "liansz-test"));

    Either<Exception, String> either = service.create(container);
    if (either.isLeft()) {
      throw either.getLeft();
    }

    CONTAINER_ID = either.get();

    System.out.println(CONTAINER_ID);
  }

  @Test
  public void delete() throws Exception {
    Either<Exception, Void> either = service.delete(CONTAINER_ID);
    if (either.isLeft()) {
      throw either.getLeft();
    }
  }

  @Test
  public void start() throws Exception {
    Either<Exception, Void> either = service.start(CONTAINER_ID);
    if (either.isLeft()) {
      throw either.getLeft();
    }
  }

  @Test
  public void pause() throws Exception {
    Either<Exception, Void> either = service.pause(CONTAINER_ID);
    if (either.isLeft()) {
      throw either.getLeft();
    }
  }

  @Test
  public void stop() throws Exception {
    Either<Exception, Void> either = service.stop(CONTAINER_ID);
    if (either.isLeft()) {
      throw either.getLeft();
    }
  }

  @Test
  public void restart() throws Exception {
    Either<Exception, Void> either = service.restart(CONTAINER_ID);
    if (either.isLeft()) {
      throw either.getLeft();
    }
  }

  @Test
  public void get() throws Exception {
    Either<Exception, Container> either = service.getContainerInfoById(CONTAINER_ID);
    if (either.isLeft()) {
      throw either.getLeft();
    }

    System.out.println(Json.toJsonString(either.get()));
  }

  @Test
  public void list() throws Exception {
    Map<String, Collection<String>> filterMap = new HashMap<>();

    Either<Exception, List<Container>> either = service.searchContainerInfo(filterMap);
    if (either.isLeft()) {
      throw either.getLeft();
    }

    System.out.println(Json.toJsonString(either.get()));
  }
}
