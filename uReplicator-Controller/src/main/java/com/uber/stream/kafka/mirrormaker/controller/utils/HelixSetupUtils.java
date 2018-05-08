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
package com.uber.stream.kafka.mirrormaker.controller.utils;

import com.uber.stream.kafka.mirrormaker.controller.core.OnlineOfflineStateModel;
import org.apache.helix.HelixAdmin;
import org.apache.helix.HelixManager;
import org.apache.helix.controller.HelixControllerMain;
import org.apache.helix.manager.zk.ZKHelixAdmin;
import org.apache.helix.manager.zk.ZKHelixManager;
import org.apache.helix.messaging.handling.HelixTaskExecutor;
import org.apache.helix.model.HelixConfigScope;
import org.apache.helix.model.HelixConfigScope.ConfigScopeProperty;
import org.apache.helix.model.Message.MessageType;
import org.apache.helix.model.builder.HelixConfigScopeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * HelixSetupUtils handles how to create or get a helixCluster in controller.
 */
// TODO: 2018/5/2 by zmyer
public class HelixSetupUtils {

    //日志
    private static final Logger LOGGER = LoggerFactory.getLogger(HelixSetupUtils.class);

    // TODO: 2018/5/2 by zmyer
    public static synchronized HelixManager setup(String helixClusterName, String zkPath,
            String controllerInstanceId) {
        try {
            //创建helix集群对象
            createHelixClusterIfNeeded(helixClusterName, zkPath);
        } catch (final Exception e) {
            LOGGER.error("Caught exception", e);
            return null;
        }

        try {
            //创建helix控制器
            return startHelixControllerInStandadloneMode(helixClusterName, zkPath, controllerInstanceId);
        } catch (final Exception e) {
            LOGGER.error("Caught exception", e);
            return null;
        }
    }

    // TODO: 2018/5/2 by zmyer
    public static void createHelixClusterIfNeeded(String helixClusterName, String zkPath) {
        //创建helix管理者
        final HelixAdmin admin = new ZKHelixAdmin(zkPath);

        //如果当前的集群已经创建，则直接退出
        if (admin.getClusters().contains(helixClusterName)) {
            LOGGER.info(
                    "cluster already exist, skipping it.. ********************************************* ");
            return;
        }

        LOGGER.info("Creating a new cluster, as the helix cluster : " + helixClusterName
                + " was not found ********************************************* ");
        //创建helix集群对象
        admin.addCluster(helixClusterName, false);

        LOGGER.info("Enable mirror maker machines auto join.");
        //创建helix配置范围对象
        final HelixConfigScope scope = new HelixConfigScopeBuilder(ConfigScopeProperty.CLUSTER)
                .forCluster(helixClusterName).build();

        //设置集群配置信息
        final Map<String, String> props = new HashMap<String, String>();
        //运行自动加入
        props.put(ZKHelixManager.ALLOW_PARTICIPANT_AUTO_JOIN, String.valueOf(true));
        //设置最大线程数目
        props.put(MessageType.STATE_TRANSITION + "." + HelixTaskExecutor.MAX_THREADS,
                String.valueOf(100));

        //配置管理对象
        admin.setConfig(scope, props);

        LOGGER.info("Adding state model definition named : OnlineOffline generated using : "
                + OnlineOfflineStateModel.class.toString()
                + " ********************************************** ");

        // add state model definition
        //添加状态模型定义
        admin.addStateModelDef(helixClusterName, "OnlineOffline", OnlineOfflineStateModel.build());
        LOGGER.info("New Cluster setup completed... ********************************************** ");
    }

    // TODO: 2018/5/2 by zmyer
    private static HelixManager startHelixControllerInStandadloneMode(String helixClusterName,
            String zkUrl, String controllerInstanceId) {
        LOGGER.info("Starting Helix Standalone Controller ... ");
        //启动helix控制器
        return HelixControllerMain.startHelixController(zkUrl, helixClusterName, controllerInstanceId,
                HelixControllerMain.STANDALONE);
    }
}
