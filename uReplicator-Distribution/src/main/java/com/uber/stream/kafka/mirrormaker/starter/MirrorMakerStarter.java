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
package com.uber.stream.kafka.mirrormaker.starter;

import com.uber.stream.kafka.mirrormaker.controller.ControllerStarter;
import kafka.mirrormaker.MirrorMakerWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * This is the entry point to start mirror maker controller and worker.
 * The 1st parameter indicates the module to start:
 * - startMirrorMakerController means to start a controller
 * - startMirrorMakerWorker means to start a worker
 * The following parameters are for each module separately.
 */
// TODO: 2018/5/2 by zmyer
public class MirrorMakerStarter {
    //日志
    private static final Logger LOGGER = LoggerFactory.getLogger(MirrorMakerStarter.class);

    // TODO: 2018/5/2 by zmyer
    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            if (args[0].equalsIgnoreCase("startMirrorMakerController")) {
                LOGGER.info("Trying to start MirrorMaker Controller with args: {}", Arrays.toString(args));
                //启动控制器
                ControllerStarter.main(args);
            } else if (args[0].equalsIgnoreCase("startMirrorMakerWorker")) {
                LOGGER.info("Trying to start MirrorMaker Worker with args: {}", Arrays.toString(args));
                //启动工作进程
                new MirrorMakerWorker().main(args);
            } else {
                LOGGER.error("Start script should provide the module(startMirrorMakerController/startMirrorMakerWorker)"
                        + " to start as the first parameter! Current args: {}", Arrays.toString(args));
            }
        } else {
            LOGGER.error("Start script doesn't provide enough parameters! Current args: {}.", Arrays.toString(args));
        }
    }

}
