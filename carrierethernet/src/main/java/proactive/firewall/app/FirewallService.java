package proactive.firewall.app;

/*
 * Copyright 2015 Open Networking Laboratory
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

import java.util.ArrayList;
import java.util.List;

/**
 * Service that allows to create virtual named networks
 * which provide mesh connectivity between hosts of a
 * given network.
 */
public interface FirewallService {

    ArrayList<String> listAllRules();

    void fwAddSrcRule(String srcIp, String srcPort);

    void fwAddDstRule(String dstIp, String dstPort);

    void fwAddPairRule(String srcFlow, String dstFlow);

    void fwRemoveSrcRule(String srcIp, String srcPort);

    void fwRemoveDstRule(String dstIp, String dstPort);

    void fwRemovePairRule(String srcFlow, String dstFlow);

    void fwRemoveAllRule();
}