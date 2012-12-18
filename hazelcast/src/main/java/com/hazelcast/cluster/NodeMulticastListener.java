/*
 * Copyright (c) 2008-2012, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.cluster;

import com.hazelcast.instance.Node;
import com.hazelcast.core.Member;
import com.hazelcast.nio.Address;
import com.hazelcast.util.AddressUtil;

import java.util.Set;
import java.util.logging.Level;

public class NodeMulticastListener implements MulticastListener {

    final Node node;
    final Set<String> trustedInterfaces;

    public NodeMulticastListener(Node node) {
        this.node = node;
        this.trustedInterfaces = node.getConfig().getNetworkConfig()
                .getJoin().getMulticastConfig().getTrustedInterfaces();
    }

    public void onMessage(Object msg) {
        if (msg != null && msg instanceof JoinInfo) {
            JoinInfo joinInfo = (JoinInfo) msg;
            if (node.getThisAddress() != null && !node.getThisAddress().equals(joinInfo.address)) {
                boolean validJoinRequest;
                try {
                    validJoinRequest = node.getClusterService().validateJoinRequest(joinInfo);
                } catch (Exception e) {
                    validJoinRequest = false;
                }
                if (validJoinRequest) {
                    if (node.isActive() && node.joined()) {
                        if (joinInfo.isRequest()) {
                            if (node.isMaster()) {
                                node.multicastService.send(joinInfo.copy(false, node.getThisAddress(),
                                        node.getClusterService().getMembers().size()));
                            } else if (isMasterNode(joinInfo.address) && !checkMasterUuid(joinInfo.getUuid())) {
                                node.getLogger("NodeMulticastListener").log(Level.WARNING,
                                        "New join request has been received from current master. "
                                        + "Removing " + node.getMasterAddress());
                                node.getClusterService().removeAddress(node.getMasterAddress());
                            }
                        }
                    } else {
                        if (!node.joined() && !joinInfo.isRequest()) {
                            if (node.getMasterAddress() == null) {
                                final String masterHost = joinInfo.address.getHost();
                                if (trustedInterfaces.isEmpty() ||
                                    AddressUtil.matchAnyInterface(masterHost, trustedInterfaces)) {
                                    node.setMasterAddress(new Address(joinInfo.address));
                                }
                            }
                        } else if (joinInfo.isRequest()) {
                            Joiner joiner = node.getJoiner();
                            if (joiner instanceof MulticastJoiner) {
                                MulticastJoiner mjoiner = (MulticastJoiner) joiner;
                                mjoiner.onReceivedJoinInfo(joinInfo);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isMasterNode(Address address) {
        return address.equals(node.getMasterAddress());
    }

    private boolean checkMasterUuid(String uuid) {
        final Member masterMember = getMasterMember(node.getClusterService().getMembers());
        return masterMember == null || masterMember.getUuid().equals(uuid);
    }

    private Member getMasterMember(final Set<Member> members) {
        if (members == null || members.isEmpty()) return null;
        return members.iterator().next();
    }
}