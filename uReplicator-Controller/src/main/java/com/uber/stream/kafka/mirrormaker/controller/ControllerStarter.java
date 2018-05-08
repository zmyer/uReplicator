/*
 * Copyright (C) 2015-2017 Uber Technologies, Inc. (streaming-data@uber.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.uber.stream.kafka.mirrormaker.controller;

import com.uber.stream.kafka.mirrormaker.controller.core.AutoTopicWhitelistingManager;
import com.uber.stream.kafka.mirrormaker.controller.core.ClusterInfoBackupManager;
import com.uber.stream.kafka.mirrormaker.controller.core.FileBackUpHandler;
import com.uber.stream.kafka.mirrormaker.controller.core.GitBackUpHandler;
import com.uber.stream.kafka.mirrormaker.controller.core.HelixMirrorMakerManager;
import com.uber.stream.kafka.mirrormaker.controller.core.KafkaBrokerTopicObserver;
import com.uber.stream.kafka.mirrormaker.controller.reporter.HelixKafkaMirrorMakerMetricsReporter;
import com.uber.stream.kafka.mirrormaker.controller.rest.ControllerRestApplication;
import com.uber.stream.kafka.mirrormaker.controller.validation.SourceKafkaClusterValidationManager;
import com.uber.stream.kafka.mirrormaker.controller.validation.ValidationManager;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.data.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * The main entry point for everything.
 *
 * @author xiangfu
 */
// TODO: 2018/5/2 by zmyer
public class ControllerStarter {

    private static final String DEST_KAFKA_CLUSTER = "destKafkaCluster";
    private static final String SRC_KAFKA_CLUSTER = "srcKafkaCluster";
    private static final Logger LOGGER = LoggerFactory.getLogger(ControllerStarter.class);
    private final ControllerConf _config;

    private final Component _component;
    private final Application _controllerRestApp;
    private final HelixMirrorMakerManager _helixMirrorMakerManager;
    private final ValidationManager _validationManager;
    private final SourceKafkaClusterValidationManager _srcKafkaValidationManager;
    private final AutoTopicWhitelistingManager _autoTopicWhitelistingManager;
    private final ClusterInfoBackupManager _clusterInfoBackupManager;
    private final Map<String, KafkaBrokerTopicObserver> _kafkaBrokerTopicObserverMap =
            new HashMap<String, KafkaBrokerTopicObserver>();

    // TODO: 2018/5/2 by zmyer
    public ControllerStarter(ControllerConf conf) {
        LOGGER.info("Trying to init ControllerStarter with config: {}", conf);
        _config = conf;
        HelixKafkaMirrorMakerMetricsReporter.init(conf);
        _component = new Component();
        _controllerRestApp = new ControllerRestApplication(null);
        //创建mirror marker管理器
        _helixMirrorMakerManager = new HelixMirrorMakerManager(_config);
        //创建验证管理器
        _validationManager = new ValidationManager(_helixMirrorMakerManager);
        //创建kafka源机器验证管理器
        _srcKafkaValidationManager = getSourceKafkaClusterValidationManager();
        //获取自动topic白名单管理器
        _autoTopicWhitelistingManager = getAutoTopicWhitelistingManager();
        if (_config.getBackUpToGit()) {
            //创建集群信息备份管理器
            _clusterInfoBackupManager = new ClusterInfoBackupManager(_helixMirrorMakerManager,
                    new GitBackUpHandler(conf.getRemoteBackupRepo(), conf.getLocalGitRepoPath()), _config);
        } else {
            //创建集群信息备份管理器
            _clusterInfoBackupManager = new ClusterInfoBackupManager(_helixMirrorMakerManager,
                    new FileBackUpHandler(conf.getLocalBackupFilePath()), _config);
        }
    }

    private SourceKafkaClusterValidationManager getSourceKafkaClusterValidationManager() {
        if (_config.getEnableSrcKafkaValidation()) {
            if (!_kafkaBrokerTopicObserverMap.containsKey(SRC_KAFKA_CLUSTER)) {
                _kafkaBrokerTopicObserverMap.put(SRC_KAFKA_CLUSTER,
                        new KafkaBrokerTopicObserver(SRC_KAFKA_CLUSTER, _config.getSrcKafkaZkPath(),
                                _config.getRefreshTimeInSeconds()));
            }
            return new SourceKafkaClusterValidationManager(
                    _kafkaBrokerTopicObserverMap.get(SRC_KAFKA_CLUSTER),
                    _helixMirrorMakerManager, _config.getEnableAutoTopicExpansion());
        } else {
            LOGGER.info("Not init SourceKafkaClusterValidationManager!");
            return null;
        }
    }

    // TODO: 2018/5/2 by zmyer
    private AutoTopicWhitelistingManager getAutoTopicWhitelistingManager() {
        if (_config.getEnableAutoWhitelist()) {
            if (!_kafkaBrokerTopicObserverMap.containsKey(SRC_KAFKA_CLUSTER)) {
                _kafkaBrokerTopicObserverMap.put(SRC_KAFKA_CLUSTER,
                        new KafkaBrokerTopicObserver(SRC_KAFKA_CLUSTER, _config.getSrcKafkaZkPath(),
                                _config.getRefreshTimeInSeconds()));
            }
            if (!_kafkaBrokerTopicObserverMap.containsKey(DEST_KAFKA_CLUSTER)) {
                _kafkaBrokerTopicObserverMap.put(DEST_KAFKA_CLUSTER,
                        new KafkaBrokerTopicObserver(DEST_KAFKA_CLUSTER, _config.getDestKafkaZkPath(),
                                _config.getRefreshTimeInSeconds()));
            }

            String patternToExcludeTopics = _config.getPatternToExcludeTopics();
            if (patternToExcludeTopics != null && patternToExcludeTopics.trim().length() > 0) {
                patternToExcludeTopics = patternToExcludeTopics.trim();
            } else {
                patternToExcludeTopics = "";
            }
            LOGGER.info("Pattern to exclude topics is " + patternToExcludeTopics);

            return new AutoTopicWhitelistingManager(_kafkaBrokerTopicObserverMap.get(SRC_KAFKA_CLUSTER),
                    _kafkaBrokerTopicObserverMap.get(DEST_KAFKA_CLUSTER), _helixMirrorMakerManager,
                    patternToExcludeTopics, _config.getRefreshTimeInSeconds(), _config.getInitWaitTimeInSeconds());
        } else {
            LOGGER.info("Not init AutoTopicWhitelistingManager!");
            return null;
        }
    }

    public HelixMirrorMakerManager getHelixResourceManager() {
        return _helixMirrorMakerManager;
    }

    // TODO: 2018/5/2 by zmyer
    public void start() throws Exception {
        _component.getServers().add(Protocol.HTTP, Integer.parseInt(_config.getControllerPort()));
        _component.getClients().add(Protocol.FILE);
        _component.getClients().add(Protocol.JAR);
        _component.getClients().add(Protocol.WAR);

        final Context applicationContext = _component.getContext().createChildContext();

        LOGGER.info("injecting conf and resource manager to the api context");
        applicationContext.getAttributes().put(ControllerConf.class.toString(), _config);
        applicationContext.getAttributes().put(HelixMirrorMakerManager.class.toString(),
                _helixMirrorMakerManager);
        applicationContext.getAttributes().put(ValidationManager.class.toString(), _validationManager);

        if (_srcKafkaValidationManager != null) {
            applicationContext.getAttributes().put(SourceKafkaClusterValidationManager.class.toString(),
                    _srcKafkaValidationManager);

            applicationContext.getAttributes().put(KafkaBrokerTopicObserver.class.toString(),
                    _kafkaBrokerTopicObserverMap.get(SRC_KAFKA_CLUSTER));
        }
        if (_autoTopicWhitelistingManager != null) {
            applicationContext.getAttributes().put(AutoTopicWhitelistingManager.class.toString(),
                    _autoTopicWhitelistingManager);
        }

        _controllerRestApp.setContext(applicationContext);

        _component.getDefaultHost().attach(_controllerRestApp);

        try {
            LOGGER.info("starting helix mirror maker manager");
            //启动mirror maker管理器
            _helixMirrorMakerManager.start();
            //启动验证管理器
            _validationManager.start();
            if (_autoTopicWhitelistingManager != null) {
                //启动topic白名单管理器
                _autoTopicWhitelistingManager.start();
            }
            if (_srcKafkaValidationManager != null) {
                //启动kafka源集群验证管理器
                _srcKafkaValidationManager.start();
            }
            if (_clusterInfoBackupManager != null) {
                //启动集群信息备份管理器
                _clusterInfoBackupManager.start();
            }
            LOGGER.info("starting api component");
            //启动整个服务
            _component.start();
        } catch (final Exception e) {
            LOGGER.error("Caught exception while starting controller", e);
            throw e;
        }
    }

    // TODO: 2018/5/2 by zmyer
    public void stop() {
        try {
            LOGGER.info("stopping broker topic observers");
            for (String key : _kafkaBrokerTopicObserverMap.keySet()) {
                try {
                    //获取topic观察者对象
                    KafkaBrokerTopicObserver observer = _kafkaBrokerTopicObserverMap.get(key);
                    //关闭观察者
                    observer.stop();
                } catch (Exception e) {
                    LOGGER.error("Failed to stop KafkaBrokerTopicObserver: {}!", key);
                }
            }
            LOGGER.info("stopping api component");
            //关闭服务
            _component.stop();

            LOGGER.info("stopping resource manager");
            _helixMirrorMakerManager.stop();

        } catch (final Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    // TODO: 2018/5/2 by zmyer
    public static ControllerConf getDefaultConf() {
        final ControllerConf conf = new ControllerConf();
        conf.setControllerPort("9000");
        conf.setZkStr("localhost:2181");
        conf.setHelixClusterName("testMirrorMaker");
        conf.setBackUpToGit("false");
        conf.setAutoRebalanceDelayInSeconds("120");
        conf.setLocalBackupFilePath("/var/log/kafka-mirror-maker-controller");
        return conf;
    }

    // TODO: 2018/5/2 by zmyer
    public static ControllerConf getExampleConf() {
        final ControllerConf conf = new ControllerConf();
        conf.setControllerPort("9000");
        conf.setZkStr("localhost:2181");
        conf.setHelixClusterName("testMirrorMaker");
        conf.setBackUpToGit("false");
        conf.setAutoRebalanceDelayInSeconds("120");
        conf.setLocalBackupFilePath("/var/log/kafka-mirror-maker-controller");
        conf.setEnableAutoWhitelist("true");
        conf.setEnableAutoTopicExpansion("true");
        conf.setSrcKafkaZkPath("localhost:2181/cluster1");
        conf.setDestKafkaZkPath("localhost:2181/cluster2");
        conf.setInitWaitTimeInSeconds("10");
        conf.setRefreshTimeInSeconds("20");
        return conf;
    }

    // TODO: 2018/5/2 by zmyer
    public static ControllerStarter init(CommandLine cmd) {
        ControllerConf conf = null;
        if (cmd.hasOption("example1")) {
            conf = getDefaultConf();
        } else if (cmd.hasOption("example2")) {
            conf = getExampleConf();
        } else {
            try {
                //获取控制器配置
                conf = ControllerConf.getControllerConf(cmd);
            } catch (Exception e) {
                throw new RuntimeException("Not valid controller configurations!", e);
            }
        }
        final ControllerStarter starter = new ControllerStarter(conf);
        return starter;
    }

    // TODO: 2018/5/2 by zmyer
    public static void main(String[] args) throws Exception {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        cmd = parser.parse(ControllerConf.constructControllerOptions(), args);
        if (cmd.getOptions().length == 0 || cmd.hasOption("help")) {
            HelpFormatter f = new HelpFormatter();
            f.printHelp("OptionsTip", ControllerConf.constructControllerOptions());
            System.exit(0);
        }
        //初始化控制器启动对象
        final ControllerStarter controllerStarter = ControllerStarter.init(cmd);

        //注册关闭钩子函数
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                try {
                    //关闭启动器
                    controllerStarter.stop();
                } catch (Exception e) {
                    LOGGER.error("Caught error during shutdown! ", e);
                }
            }
        });

        try {
            //启动启动器
            controllerStarter.start();
        } catch (Exception e) {
            LOGGER.error("Cannot start Helix Mirror Maker Controller: ", e);
        }
    }
}
