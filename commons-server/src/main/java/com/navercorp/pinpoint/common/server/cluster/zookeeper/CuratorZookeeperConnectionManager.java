/*
 * Copyright 2018 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.common.server.cluster.zookeeper;

import com.navercorp.pinpoint.common.server.cluster.zookeeper.exception.PinpointZookeeperException;
import com.navercorp.pinpoint.common.util.Assert;
import org.apache.curator.RetryPolicy;
import org.apache.curator.RetrySleeper;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author Taejin Koo
 */
class CuratorZookeeperConnectionManager {

    private static final int DEFAULT_CONNECTION_TIMEOUT = 3000;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final CuratorFramework curatorFramework;
    private final PinpointZookeeperConnectionStateListener connectionStateListener;


    public CuratorZookeeperConnectionManager(String hostPort, int sessionTimeout, ZookeeperEventWatcher zookeeperEventWatcher) {
        Assert.requireNonNull(hostPort, "hostPort must not be null");
        Assert.isTrue(sessionTimeout > 0, "sessionTimeout must be greater than 0");
        Assert.requireNonNull(zookeeperEventWatcher, "zookeeperEventWatcher must not be null");

        CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder();
        builder.connectString(hostPort);
        builder.retryPolicy(new RetryPolicy() {
            @Override
            public boolean allowRetry(int retryCount, long elapsedTimeMs, RetrySleeper sleeper) {
                return false;
            }
        });

        if (sessionTimeout < DEFAULT_CONNECTION_TIMEOUT) {
            builder.connectionTimeoutMs(sessionTimeout);
        } else {
            builder.connectionTimeoutMs(DEFAULT_CONNECTION_TIMEOUT);
        }

        builder.sessionTimeoutMs(sessionTimeout);

        this.curatorFramework = builder.build();

        this.connectionStateListener = new PinpointZookeeperConnectionStateListener(zookeeperEventWatcher);
        curatorFramework.getConnectionStateListenable().addListener(connectionStateListener);
    }

    public void start() throws IOException, PinpointZookeeperException {
        boolean connected = false;
        try {
            curatorFramework.start();
            connected = curatorFramework.blockUntilConnected(3000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            ZookeeperExceptionResolver.resolve(e, true);
        } finally {
            if (!connected) {
                curatorFramework.close();
            }
        }
    }

    public void stop() {
        if (curatorFramework != null) {
            curatorFramework.close();
        }
    }

    public boolean isConnected() {
        return curatorFramework.getZookeeperClient().isConnected();
    }

    CuratorFramework getZookeeperClient() {
        return curatorFramework;
    }

}
