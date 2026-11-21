package com.ouroboros.security.rbac.provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshedEvent;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.security.rbac.model.AnnotationAuthorityModel;
import com.ouroboros.webapi.RequestMappingMetadata;
import com.ouroboros.webapi.RequestMappingMetadataProvider;

@Component
public class AnnotationAuthorityProvider implements AuthorityProvider {

  private volatile List<Authority> authorities = new ArrayList<>();

  @Autowired(required = false)
  private List<RequestMappingMetadataProvider> requestMappingMetadataProviders;

  @EventListener
  @Order(1)
  public void init(OuroborosMetadataRefreshedEvent event) {
    var authorities = getDeclaredRequestMappingMetadata()
        .flatMap(metadata -> Optional.ofNullable(metadata.getMethod().getAnnotation(com.ouroboros.security.annotation.Authority.class))
            .map(com.ouroboros.security.annotation.Authority::value)
            .map(Arrays::stream)
            .orElseGet(java.util.stream.Stream::empty)
            .filter(authority -> authority != null && !authority.trim().isEmpty())
            .distinct()
            .map(authority -> Tuple.of(authority, metadata.getApi())))
        .distinct()
        .collect(Collectors.groupingBy(Tuple2::_1, Collectors.mapping(Tuple2::_2, Collectors.toList())));

    this.authorities = authorities.entrySet().stream()
        .map(entry -> new AnnotationAuthorityModel(entry.getKey(), entry.getValue()))
        .collect(Collectors.toList());
  }

  private Stream<RequestMappingMetadata> getDeclaredRequestMappingMetadata() {
    return Optional.ofNullable(requestMappingMetadataProviders).orElseGet(Collections::emptyList).stream()
        .flatMap(provider -> provider.getRequestMappingMetadata().stream());
  }

  @Override
  public List<Authority> getAuthority(String fullName) {
    if (fullName == null) {
      return Collections.emptyList();
    }
    return authorities.stream()
        .filter(authority -> fullName.equalsIgnoreCase(authority.getFullName()))
        .collect(Collectors.toList());
  }

  @Override
  public List<Authority> getAllAuthorities() {
    return Collections.unmodifiableList(authorities);
  }
}
