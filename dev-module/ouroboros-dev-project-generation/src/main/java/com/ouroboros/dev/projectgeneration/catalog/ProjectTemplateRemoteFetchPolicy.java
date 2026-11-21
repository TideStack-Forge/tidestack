package com.ouroboros.dev.projectgeneration.catalog;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ProjectTemplateRemoteFetchPolicy {

  private final String host;
  private final int port;
  private final List<CidrBlock> allowedCidrs;

  private ProjectTemplateRemoteFetchPolicy(String host, int port, List<CidrBlock> allowedCidrs) {
    this.host = host;
    this.port = port;
    this.allowedCidrs = Collections.unmodifiableList(new ArrayList<>(allowedCidrs));
  }

  public static ProjectTemplateRemoteFetchPolicy exactOrigin(String origin, List<String> allowedCidrs) {
    URI originUri = URI.create(origin);
    if (!"https".equalsIgnoreCase(originUri.getScheme()) || originUri.getHost() == null) {
      throw new IllegalArgumentException("Remote template origin must be an https origin");
    }
    if (originUri.getRawPath() != null && !originUri.getRawPath().isBlank()) {
      throw new IllegalArgumentException("Remote template origin must not include path");
    }
    return new ProjectTemplateRemoteFetchPolicy(
        originUri.getHost().toLowerCase(Locale.ROOT),
        originUri.getPort() == -1 ? 443 : originUri.getPort(),
        parseCidrs(allowedCidrs)
    );
  }

  public void validate(URI uri, List<InetAddress> resolvedAddresses) {
    if (uri == null) {
      throw new IllegalArgumentException("Remote template URI is required");
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      throw new IllegalArgumentException("Remote template URI must use https");
    }
    if (uri.getUserInfo() != null || uri.getFragment() != null) {
      throw new IllegalArgumentException("Remote template URI must not contain userinfo or fragment");
    }
    String requestHost = uri.getHost();
    if (requestHost == null || isIpLiteral(requestHost)) {
      throw new IllegalArgumentException("Remote template URI must use the configured DNS host");
    }
    if (!host.equals(requestHost.toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("Remote template URI origin is not allowed");
    }
    int requestPort = uri.getPort() == -1 ? 443 : uri.getPort();
    if (requestPort != port) {
      throw new IllegalArgumentException("Remote template URI port is not allowed");
    }
    if (resolvedAddresses == null || resolvedAddresses.isEmpty()) {
      return;
    }
    for (InetAddress address : resolvedAddresses) {
      validateAddress(address);
    }
  }

  public void validatePeerAddress(URI uri, InetAddress peerAddress, List<InetAddress> resolvedAddresses) {
    validate(uri, resolvedAddresses);
    validateAddress(peerAddress);
    if (resolvedAddresses == null || resolvedAddresses.isEmpty()) {
      throw new IllegalArgumentException("Resolved remote addresses are required for peer validation");
    }
    for (InetAddress resolvedAddress : resolvedAddresses) {
      if (resolvedAddress.equals(peerAddress)) {
        return;
      }
    }
    throw new IllegalArgumentException("Remote peer address does not match validated DNS results");
  }

  private void validateAddress(InetAddress address) {
    if (address == null) {
      throw new IllegalArgumentException("Resolved remote address is required");
    }
    if (isDefaultBlockedAddress(address) && allowedCidrs.stream().noneMatch(cidr -> cidr.contains(address))) {
      throw new IllegalArgumentException("Resolved remote address is not in an allowed CIDR");
    }
  }

  private static boolean isDefaultBlockedAddress(InetAddress address) {
    byte[] raw = address.getAddress();
    return address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()
        || isIpv6UniqueLocal(raw)
        || isIpv4MappedDefaultBlockedAddress(raw);
  }

  private static boolean isIpv6UniqueLocal(byte[] raw) {
    return raw.length == 16 && ((raw[0] & 0xfe) == 0xfc);
  }

  private static boolean isIpv4MappedDefaultBlockedAddress(byte[] raw) {
    if (raw.length != 16) {
      return false;
    }
    for (int i = 0; i < 10; i++) {
      if (raw[i] != 0) {
        return false;
      }
    }
    if (raw[10] != (byte) 0xff || raw[11] != (byte) 0xff) {
      return false;
    }
    int first = raw[12] & 0xff;
    int second = raw[13] & 0xff;
    return first == 0
        || first == 10
        || first == 127
        || (first == 169 && second == 254)
        || (first == 172 && second >= 16 && second <= 31)
        || (first == 192 && second == 168)
        || (first >= 224 && first <= 239);
  }

  private static boolean isIpLiteral(String host) {
    return host.matches("^[0-9.]+$") || host.contains(":");
  }

  private static List<CidrBlock> parseCidrs(List<String> allowedCidrs) {
    if (allowedCidrs == null || allowedCidrs.isEmpty()) {
      return List.of();
    }
    List<CidrBlock> blocks = new ArrayList<>();
    for (String cidr : allowedCidrs) {
      blocks.add(CidrBlock.parse(cidr));
    }
    return blocks;
  }

  private record CidrBlock(BigInteger network, int prefixLength, int byteLength) {
    static CidrBlock parse(String cidr) {
      try {
        String[] parts = cidr.split("/", -1);
        if (parts.length != 2) {
          throw new IllegalArgumentException("CIDR must include prefix length");
        }
        InetAddress address = InetAddress.getByName(parts[0]);
        int byteLength = address.getAddress().length;
        int prefixLength = Integer.parseInt(parts[1]);
        if (prefixLength < 0 || prefixLength > byteLength * 8) {
          throw new IllegalArgumentException("CIDR prefix length is out of range");
        }
        BigInteger mask = mask(prefixLength, byteLength);
        return new CidrBlock(toBigInteger(address).and(mask), prefixLength, byteLength);
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid allowed CIDR: " + cidr, e);
      }
    }

    boolean contains(InetAddress address) {
      if (address.getAddress().length != byteLength) {
        return false;
      }
      return toBigInteger(address).and(mask(prefixLength, byteLength)).equals(network);
    }

    private static BigInteger toBigInteger(InetAddress address) {
      return new BigInteger(1, address.getAddress());
    }

    private static BigInteger mask(int prefixLength, int byteLength) {
      int bitLength = byteLength * 8;
      if (prefixLength == 0) {
        return BigInteger.ZERO;
      }
      return BigInteger.ONE.shiftLeft(bitLength).subtract(BigInteger.ONE).shiftRight(bitLength - prefixLength)
          .shiftLeft(bitLength - prefixLength);
    }
  }
}
