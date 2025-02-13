/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.aws;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class AwsSpanAssertions {

  private AwsSpanAssertions() {}

  static SpanDataAssert sqs(SpanDataAssert span, String spanName) {
    return sqs(span, spanName, null, null, CLIENT);
  }

  static SpanDataAssert sqs(SpanDataAssert span, String spanName, String queueUrl) {
    return sqs(span, spanName, queueUrl, null, CLIENT);
  }

  static SpanDataAssert sqs(
      SpanDataAssert span, String spanName, String queueUrl, String queueName) {
    return sqs(span, spanName, queueUrl, queueName, CLIENT);
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  static SpanDataAssert sqs(
      SpanDataAssert span, String spanName, String queueUrl, String queueName, SpanKind spanKind) {

    String rpcMethod;
    if (spanName.startsWith("SQS.")) {
      rpcMethod = spanName.substring(4);
    } else if (spanName.endsWith("process")) {
      rpcMethod = "ReceiveMessage";
    } else if (spanName.endsWith("publish")) {
      rpcMethod = "SendMessage";
    } else {
      throw new IllegalStateException("can't get rpc method from span name " + spanName);
    }

    List<AttributeAssertion> attributeAssertions = new ArrayList<>();
    attributeAssertions.addAll(
        Arrays.asList(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            satisfies(stringKey("aws.endpoint"), val -> val.isInstanceOf(String.class)),
            satisfies(
                stringKey("aws.queue.name"),
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isEqualTo(queueName), v -> assertThat(v).isNull())),
            satisfies(
                stringKey("aws.queue.url"),
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isEqualTo(queueUrl), v -> assertThat(v).isNull())),
            equalTo(SemanticAttributes.HTTP_METHOD, "POST"),
            satisfies(
                SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(), v -> assertThat(v).isInstanceOf(Long.class))),
            satisfies(SemanticAttributes.HTTP_URL, val -> val.isInstanceOf(String.class)),
            satisfies(
                stringKey("net.peer.name"),
                stringAssert -> stringAssert.isInstanceOf(String.class)),
            satisfies(
                longKey("net.peer.port"),
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(),
                        v -> assertThat(v).isInstanceOf(Number.class))),
            equalTo(stringKey("rpc.system"), "aws-api"),
            satisfies(stringKey("rpc.method"), stringAssert -> stringAssert.isEqualTo(rpcMethod)),
            equalTo(stringKey("rpc.service"), "AmazonSQS")));

    if (!spanName.endsWith("process")) {
      attributeAssertions.addAll(
          Arrays.asList(
              equalTo(SemanticAttributes.HTTP_STATUS_CODE, 200),
              satisfies(
                  SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                  val ->
                      val.satisfiesAnyOf(
                          v -> assertThat(v).isNull(),
                          v -> assertThat(v).isInstanceOf(Long.class))),
              equalTo(SemanticAttributes.NET_PROTOCOL_NAME, "http"),
              equalTo(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1")));
    }
    if (spanName.endsWith("receive")
        || spanName.endsWith("process")
        || spanName.endsWith("publish")) {
      attributeAssertions.addAll(
          Arrays.asList(
              equalTo(SemanticAttributes.MESSAGING_DESTINATION_NAME, queueName),
              equalTo(SemanticAttributes.MESSAGING_SYSTEM, "AmazonSQS")));
      if (spanName.endsWith("receive")) {
        attributeAssertions.add(equalTo(SemanticAttributes.MESSAGING_OPERATION, "receive"));
      } else if (spanName.endsWith("process")) {
        attributeAssertions.add(equalTo(SemanticAttributes.MESSAGING_OPERATION, "process"));
      } else if (spanName.endsWith("publish")) {
        attributeAssertions.add(equalTo(SemanticAttributes.MESSAGING_OPERATION, "publish"));
      }
    }

    return span.hasName(spanName)
        .hasKind(spanKind)
        .hasAttributesSatisfyingExactly(attributeAssertions);
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  static SpanDataAssert s3(SpanDataAssert span, String spanName, String bucketName, String method) {
    return span.hasName(spanName)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            satisfies(stringKey("aws.endpoint"), val -> val.isInstanceOf(String.class)),
            equalTo(stringKey("rpc.system"), "aws-api"),
            equalTo(stringKey("rpc.method"), spanName.substring(3)),
            equalTo(stringKey("rpc.service"), "Amazon S3"),
            equalTo(stringKey("aws.bucket.name"), bucketName),
            equalTo(stringKey("http.method"), method),
            equalTo(longKey("http.status_code"), 200),
            satisfies(stringKey("http.url"), val -> val.isInstanceOf(String.class)),
            equalTo(SemanticAttributes.NET_PROTOCOL_NAME, "http"),
            equalTo(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1"),
            satisfies(stringKey("net.peer.name"), val -> val.isInstanceOf(String.class)),
            satisfies(
                SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(), v -> assertThat(v).isInstanceOf(Long.class))),
            satisfies(
                stringKey("net.peer.port"),
                val ->
                    val.satisfiesAnyOf(
                        v -> val.isInstanceOf(Number.class), v -> assertThat(v).isNull())));
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  static SpanDataAssert sns(SpanDataAssert span, String spanName) {
    return span.hasName(spanName)
        .hasKind(CLIENT)
        .hasAttributesSatisfyingExactly(
            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
            satisfies(stringKey("aws.endpoint"), val -> val.isInstanceOf(String.class)),
            equalTo(stringKey("rpc.system"), "aws-api"),
            equalTo(stringKey("rpc.method"), spanName.substring(4)),
            equalTo(stringKey("rpc.service"), "AmazonSNS"),
            equalTo(stringKey("http.method"), "POST"),
            equalTo(longKey("http.status_code"), 200),
            satisfies(stringKey("http.url"), val -> val.isInstanceOf(String.class)),
            equalTo(SemanticAttributes.NET_PROTOCOL_NAME, "http"),
            equalTo(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1"),
            satisfies(stringKey("net.peer.name"), val -> val.isInstanceOf(String.class)),
            satisfies(
                stringKey("net.peer.port"),
                val ->
                    val.satisfiesAnyOf(
                        v -> val.isInstanceOf(Number.class), v -> assertThat(v).isNull())),
            satisfies(
                SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH,
                val ->
                    val.satisfiesAnyOf(
                        v -> assertThat(v).isNull(), v -> assertThat(v).isInstanceOf(Long.class))));
  }
}
