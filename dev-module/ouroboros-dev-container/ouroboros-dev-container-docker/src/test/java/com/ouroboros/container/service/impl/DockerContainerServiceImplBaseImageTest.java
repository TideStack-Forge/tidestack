package com.ouroboros.container.service.impl;

import java.util.Arrays;
import java.util.Collections;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.model.Image;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DockerContainerServiceImplBaseImageTest {

  @Mock
  private DockerClient dockerClient;

  @Mock
  private ListImagesCmd listImagesCmd;

  @Test
  void getAllTaggedImageNames_filtersDigestDanglingAndUntaggedImages() {
    var taggedImage = mock(Image.class);
    when(taggedImage.getRepoTags()).thenReturn(new String[]{
        "registry.example.com/team/ouroboros-mothership:1.0.0-beta.2",
        "registry.example.com/team/ouroboros-mothership:latest"
    });

    var danglingImage = mock(Image.class);
    when(danglingImage.getRepoTags()).thenReturn(new String[]{"<none>:<none>"});

    var digestImage = mock(Image.class);
    when(digestImage.getRepoTags()).thenReturn(new String[]{"registry.example.com/team/ouroboros-mothership@sha256:abcdef"});

    var untaggedImage = mock(Image.class);
    when(untaggedImage.getRepoTags()).thenReturn(null);

    when(dockerClient.listImagesCmd()).thenReturn(listImagesCmd);
    when(listImagesCmd.exec()).thenReturn(Arrays.asList(taggedImage, danglingImage, digestImage, untaggedImage));

    var service = new DockerContainerServiceImpl();
    ReflectionTestUtils.setField(service, "dockerClient", dockerClient);

    var result = service.getAllTaggedImageNames();

    assertTrue(result.isRight());
    assertEquals(Arrays.asList(
            "registry.example.com/team/ouroboros-mothership:1.0.0-beta.2",
            "registry.example.com/team/ouroboros-mothership:latest"
        ),
        result.get());
  }
}
