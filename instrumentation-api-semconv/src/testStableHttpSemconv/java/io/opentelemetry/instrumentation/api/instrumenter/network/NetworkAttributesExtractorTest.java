/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.network;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkAttributes;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class NetworkAttributesExtractorTest {

  static class TestNetworkAttributesGetter
      implements NetworkAttributesGetter<Map<String, String>, Void> {

    @Nullable
    @Override
    public String getNetworkTransport(Map<String, String> request, @Nullable Void response) {
      return request.get("transport");
    }

    @Nullable
    @Override
    public String getNetworkType(Map<String, String> request, @Nullable Void response) {
      return request.get("type");
    }

    @Nullable
    @Override
    public String getNetworkProtocolName(Map<String, String> request, @Nullable Void response) {
      return request.get("protocolName");
    }

    @Nullable
    @Override
    public String getNetworkProtocolVersion(Map<String, String> request, @Nullable Void response) {
      return request.get("protocolVersion");
    }

    @Nullable
    @Override
    public String getNetworkLocalAddress(Map<String, String> request, @Nullable Void response) {
      return request.get("localAddress");
    }

    @Nullable
    @Override
    public Integer getNetworkLocalPort(Map<String, String> request, @Nullable Void response) {
      String value = request.get("localPort");
      return value == null ? null : Integer.parseInt(value);
    }

    @Nullable
    @Override
    public String getNetworkPeerAddress(Map<String, String> request, @Nullable Void response) {
      return request.get("peerAddress");
    }

    @Nullable
    @Override
    public Integer getNetworkPeerPort(Map<String, String> request, @Nullable Void response) {
      String value = request.get("peerPort");
      return value == null ? null : Integer.parseInt(value);
    }
  }

  @Test
  void allAttributes() {
    Map<String, String> request = new HashMap<>();
    request.put("transport", "TcP");
    request.put("type", "IPv4");
    request.put("protocolName", "Http");
    request.put("protocolVersion", "1.1");
    request.put("localAddress", "1.2.3.4");
    request.put("localPort", "8080");
    request.put("peerAddress", "4.3.2.1");
    request.put("peerPort", "9090");

    AttributesExtractor<Map<String, String>, Void> extractor =
        NetworkAttributesExtractor.create(new TestNetworkAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), request);
    assertThat(startAttributes.build()).isEmpty();

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, null, null);
    assertThat(endAttributes.build())
        .containsOnly(
            entry(SemanticAttributes.NETWORK_TRANSPORT, "tcp"),
            entry(SemanticAttributes.NETWORK_TYPE, "ipv4"),
            entry(SemanticAttributes.NETWORK_PROTOCOL_NAME, "http"),
            entry(SemanticAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
            entry(NetworkAttributes.NETWORK_LOCAL_ADDRESS, "1.2.3.4"),
            entry(NetworkAttributes.NETWORK_LOCAL_PORT, 8080L),
            entry(NetworkAttributes.NETWORK_PEER_ADDRESS, "4.3.2.1"),
            entry(NetworkAttributes.NETWORK_PEER_PORT, 9090L));
  }

  @Test
  void noAttributes() {
    AttributesExtractor<Map<String, String>, Void> extractor =
        NetworkAttributesExtractor.create(new TestNetworkAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), emptyMap());
    assertThat(startAttributes.build()).isEmpty();

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), emptyMap(), null, null);
    assertThat(endAttributes.build()).isEmpty();
  }

  @Test
  void doesNotSetNegativePortValues() {
    Map<String, String> request = new HashMap<>();
    request.put("localAddress", "1.2.3.4");
    request.put("localPort", "-12");
    request.put("peerAddress", "4.3.2.1");
    request.put("peerPort", "-42");

    AttributesExtractor<Map<String, String>, Void> extractor =
        NetworkAttributesExtractor.create(new TestNetworkAttributesGetter());

    AttributesBuilder startAttributes = Attributes.builder();
    extractor.onStart(startAttributes, Context.root(), request);
    assertThat(startAttributes.build()).isEmpty();

    AttributesBuilder endAttributes = Attributes.builder();
    extractor.onEnd(endAttributes, Context.root(), request, null, null);
    assertThat(endAttributes.build())
        .containsOnly(
            entry(NetworkAttributes.NETWORK_LOCAL_ADDRESS, "1.2.3.4"),
            entry(NetworkAttributes.NETWORK_PEER_ADDRESS, "4.3.2.1"));
  }
}
