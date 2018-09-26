/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.kafka;

import kafka.admin.AdminUtils;
import kafka.admin.RackAwareMode;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.MockTime;
import kafka.utils.TestUtils;
import kafka.utils.ZKStringSerializer$;
import kafka.utils.ZkUtils;
import kafka.zk.EmbeddedZookeeper;
import org.I0Itec.zkclient.ZkClient;
import org.apache.commons.io.FileUtils;
import org.apache.kafka.common.utils.Time;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Test Helper Class to start and stop a kafka broker
 */
class KafkaBrokerResource extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaBrokerResource.class);
  private static final String TOPIC = "TEST-CREATE_TOPIC";
  public static final String BROKER_IP_PORT = "127.0.0.1:9092";
  private EmbeddedZookeeper zkServer;
  private ZkClient zkClient;
  private ZkUtils zkUtils;
  private KafkaServer kafkaServer;
  private Path tmpLogDir;

  /**
   * Override to set up your specific external resource.
   *
   * @throws Throwable if setup fails (which will disable {@code after}
   */
  @Override protected void before() throws Throwable {
    // Start the ZK and the Broker
    LOG.info("init embedded Zookeeper");
    zkServer = new EmbeddedZookeeper();
    tmpLogDir = Files.createTempDirectory("kafka-log-dir-").toAbsolutePath();
    String zkConnect = "127.0.0.1:" + zkServer.port();
    zkClient = new ZkClient(zkConnect, 3000, 3000, ZKStringSerializer$.MODULE$);
    zkUtils = ZkUtils.apply(zkClient, false);
    LOG.info("init kafka broker");
    Properties brokerProps = new Properties();
    brokerProps.setProperty("zookeeper.connect", zkConnect);
    brokerProps.setProperty("broker.id", "0");
    brokerProps.setProperty("log.dir",tmpLogDir.toString());
    brokerProps.setProperty("listeners", "PLAINTEXT://" + BROKER_IP_PORT);
    brokerProps.setProperty("offsets.topic.replication.factor", "1");
    brokerProps.setProperty("transaction.state.log.replication.factor", "1");
    brokerProps.setProperty("transaction.state.log.min.isr", "1");
    KafkaConfig config = new KafkaConfig(brokerProps);
    Time mock = new MockTime();
    kafkaServer = TestUtils.createServer(config, mock);
    kafkaServer.startup();
    LOG.info("Creating kafka TOPIC [{}]", TOPIC);
    AdminUtils.createTopic(zkUtils, TOPIC, 1, 1, new Properties(), RackAwareMode.Disabled$.MODULE$);
  }

  /**
   * Override to tear down your specific external resource.
   */
  @Override protected void after() {
    super.after();
    try {
      FileUtils.deleteDirectory(new File(tmpLogDir.toString()));
    } catch (IOException e) {
      LOG.error("Error cleaning " + tmpLogDir.toString(), e);
    }
    if (kafkaServer != null) {
      kafkaServer.shutdown();
      kafkaServer.zkUtils().close();
      kafkaServer.awaitShutdown();
    }
    zkServer.shutdown();
    zkClient.close();
    zkUtils.close();
  }

  public void deleteTopic(String topic) {
    AdminUtils.deleteTopic(zkUtils, topic);
  }
}
