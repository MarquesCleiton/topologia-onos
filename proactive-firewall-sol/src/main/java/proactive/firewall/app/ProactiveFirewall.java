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
package proactive.firewall.app;

import com.google.common.collect.HashMultimap;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.IpAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Component(immediate = true)
@Service
public class ProactiveFirewall implements FirewallService{

    private static Logger log = LoggerFactory.getLogger(ProactiveFirewall.class);

    private static final int DROP_PRIORITY = 129;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    private ApplicationId appId;

    private final HashSet<BlockRecord> srcBlockList = new HashSet<>();
    private final HashSet<BlockRecord> dstBlockList = new HashSet<>();

    private final HashMultimap<BlockRecord,BlockRecord> pairBlockList = HashMultimap.create();

    @Activate
    public void activate() {
        appId = coreService.registerApplication("proactive.firewall.app");
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        flowRuleService.removeFlowRulesById(appId);
        srcBlockList.clear();
        dstBlockList.clear();
        pairBlockList.clear();
        log.info("Stopped");
    }

    @Override
    public ArrayList<String> listAllRules() {
        ArrayList<String> listFirewall = new ArrayList<>();

        for (BlockRecord record : srcBlockList) {
            String rule = record.toString();
            listFirewall.add("SrcRule: ".concat(rule));
        }
        for (BlockRecord record : dstBlockList) {
            String rule = record.toString();
            listFirewall.add("DstRule: ".concat(rule));
        }
        for (BlockRecord keyRecord: pairBlockList.keySet()) {
            String src = keyRecord.toString();
            Set<BlockRecord> values = pairBlockList.get(keyRecord);
            for (BlockRecord record: values) {
                log.info("A regra solicitada ja existe.");
                String dst = record.toString();
                listFirewall.add("SrcDstRule: ".concat(src.concat("<-->")).concat(dst));
            }
        }
        return listFirewall;
    }

    @Override
    public void fwAddSrcRule(String srcIp, String srcPort) {

        BlockRecord srcRule;

        if (srcPort == null) {
            srcRule = new BlockRecord(srcIp);
        }
        else {
            srcRule = new BlockRecord(srcIp, srcPort);
        }
        createRule("srcIp", srcRule);
    }

    @Override
    public void fwAddDstRule(String dstIp, String dstPort) {

        BlockRecord dstRule;

        if (dstPort == null) {
            dstRule = new BlockRecord(dstIp);
        }
        else {
            dstRule = new BlockRecord(dstIp, dstPort);
        }
        createRule("dstIp", dstRule);
    }

    @Override
    public void fwAddPairRule(String srcFlow, String dstFlow) {
        String[] src = srcFlow.split(":");
        String[] dst = dstFlow.split(":");

        BlockRecord srcRule;
        BlockRecord dstRule;

        if (src.length == 2) srcRule = new BlockRecord(src[0], src[1]);
        else srcRule = new BlockRecord(src[0]);

        if (dst.length == 2) dstRule = new BlockRecord(dst[0], dst[1]);
        else dstRule = new BlockRecord(dst[0]);

        createRule(srcRule, dstRule);
    }

    @Override
    public void fwRemoveSrcRule(String srcIp, String srcPort) {

        BlockRecord srcRule;

        if (srcPort == null) {
            srcRule = new BlockRecord(srcIp);
        }
        else {
            srcRule = new BlockRecord(srcIp, srcPort);
        }
        removeRule("srcIp", srcRule);
    }

    @Override
    public void fwRemoveDstRule(String dstIp, String dstPort) {

        BlockRecord dstRule;

        if (dstPort == null) {
            dstRule = new BlockRecord(dstIp);
        }
        else {
            dstRule = new BlockRecord(dstIp, dstPort);
        }
        removeRule("dstIp", dstRule);
    }

    @Override
    public void fwRemovePairRule(String srcFlow, String dstFlow) {

        String[] src = srcFlow.split(":");
        String[] dst = dstFlow.split(":");

        BlockRecord srcRule;
        BlockRecord dstRule;

        if (src.length == 2) srcRule = new BlockRecord(src[0], src[1]);
        else srcRule = new BlockRecord(src[0]);

        if (dst.length == 2) dstRule = new BlockRecord(dst[0], dst[1]);
        else dstRule = new BlockRecord(dst[0]);

        removeRule(srcRule, dstRule);
    }

    @Override
    public void fwRemoveAllRule() {

        /*
         * TODO Lab 1: Manipulando Comandos (Remover todas as Regras)
         *
         * Para limpar todas as regras, iremos remover as regras de fluxos criadas (utilize a funcao removeFlowRulesById, do servico de flowRule do ONOS)
         * pela aplicação (appId) e limpar as regras armazenadas no banco de dados da aplicacao (srcBlockList, dstBlockList e pairBlocList)
         */
        flowRuleService.removeFlowRulesById(appId);
        srcBlockList.clear();
        dstBlockList.clear();
        pairBlockList.clear();
    }

    private void applyRule(String opCode, String type, BlockRecord newRule){

        String ip = newRule.ip;
        String tcpPort = null;
        boolean noPort  = newRule.port == null;
        if (!noPort) {
            tcpPort = newRule.port;
        }

        Iterable<Device> devices = deviceService.getDevices();

        byte ipv4Protocol = IPv4.PROTOCOL_TCP;
        TrafficSelector selector;
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4);

        if (type.equals("srcIp")) {
            if (noPort) {
                selector = selectorBuilder.matchIPSrc(IpAddress.valueOf(ip).toIpPrefix()).build();
            }
            else {
                selector = selectorBuilder.matchIPSrc(IpAddress.valueOf(ip).toIpPrefix()).matchIPProtocol(ipv4Protocol).matchTcpSrc(Short.parseShort(tcpPort)).build();
            }

            if (opCode.equals("add")) {
                for (Device d : devices) {
                    DeviceId deviceId = d.id();
                    flowRuleAdd(deviceId, selector);
                }
            }
            else {
                for (Device d : devices) {
                    DeviceId deviceId = d.id();
                    flowRuleRemove(deviceId, selector);
                }
            }
            return;
        }
        if (type.equals("dstIp")) {
            if (noPort) {
                selector = selectorBuilder.matchIPDst(IpAddress.valueOf(ip).toIpPrefix()).build();

            }
            else {
                selector = selectorBuilder.matchIPDst(IpAddress.valueOf(ip).toIpPrefix()).matchIPProtocol(ipv4Protocol).matchTcpDst(Short.parseShort(tcpPort)).build();
            }

            if (opCode.equals("add")) {
                for (Device d : devices) {
                    DeviceId deviceId = d.id();
                    flowRuleAdd(deviceId, selector);
                }
            }
            else {
                for (Device d : devices) {
                    DeviceId deviceId = d.id();
                    flowRuleRemove(deviceId, selector);
                }
            }
            return;
        }
    }

    private void applyRule (String opCode, BlockRecord src, BlockRecord dst){

        String srcIp = src.ip;
        String srcPort = null;
        boolean noSrcPort  = src.port == null;
        if (!noSrcPort) {
            srcPort = src.port;
        }

        String dstIp = dst.ip;
        String dstPort = null;
        boolean noDstPort  = dst.port == null;
        if (!noDstPort) {
            dstPort = dst.port;
        }

        Iterable<Device> devices = deviceService.getDevices();

        byte ipv4Protocol = IPv4.PROTOCOL_TCP;
        TrafficSelector selector;
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).matchIPSrc(IpAddress.valueOf(srcIp).toIpPrefix()).matchIPDst(IpAddress.valueOf(dstIp).toIpPrefix());

        if (noSrcPort) {
            if (noDstPort) {
                selector = selectorBuilder.build();
            }
            else {
                selector = selectorBuilder.matchIPProtocol(ipv4Protocol).matchTcpDst(Short.parseShort(dstPort)).build();
            }
        }
        else {
            if (noDstPort) {
                selector = selectorBuilder.matchIPProtocol(ipv4Protocol).matchTcpSrc(Short.parseShort(srcPort)).build();

            } else {
                selector = selectorBuilder.matchIPProtocol(ipv4Protocol).matchTcpSrc(Short.parseShort(srcPort)).matchTcpDst(Short.parseShort(dstPort)).build();
            }
        }

        if (opCode.equals("add")) {
            for (Device d : devices) {
                DeviceId deviceId = d.id();
                flowRuleAdd(deviceId, selector);
            }
        }
        else {
            for (Device d : devices) {
                DeviceId deviceId = d.id();
                flowRuleRemove(deviceId, selector);
            }
        }
        return;
    }
    private void createRule(BlockRecord src, BlockRecord dst) {

        String opCode = "add";
        if (pairBlockList.containsKey(src)) {
            Set<BlockRecord> values = pairBlockList.get(src);
            for (BlockRecord rule: values){
                if (rule.equals(dst)) {
                    log.info("A regra solicitada ja existe.");
                    return;
                }
            }
        }
        pairBlockList.put(src, dst);
        applyRule(opCode, src, dst);
    }

    private void createRule(String type, BlockRecord newRule) {

        String opCode = "add";
        if (type.equals("srcIp")){
            for (BlockRecord rule : srcBlockList) {
                if (rule.equals(newRule)) {
                   log.info("A regra solicitada ja existe.");
                   return;
                }
            }
            srcBlockList.add(newRule);
            applyRule(opCode, type, newRule);
        }
        else if (type.equals("dstIp")){
            for (BlockRecord rule : dstBlockList) {
                if (rule.equals(newRule)) {
                    log.info("A regra solicitada ja existe.");
                    return;
                }
            }
            dstBlockList.add(newRule);
            applyRule(opCode, type, newRule);
        }
    }

    private void removeRule(String type, BlockRecord newRule) {

        String opCode = "remove";
        if (type.equals("srcIp")){
            for (BlockRecord rule : srcBlockList) {
                if (rule.equals(newRule)) {
                    log.info("A regra solicitada ja existe. Removendo a regra");
                    srcBlockList.remove(newRule);
                    applyRule(opCode, type, newRule);
                    return;
                }
            }
            log.info("A regra solicitada nao existe. Retornando...");
            return;
        }
        else if (type.equals("dstIp")){
            for (BlockRecord rule : dstBlockList) {
                if (rule.equals(newRule)) {
                    log.info("A regra solicitada ja existe.");
                    dstBlockList.remove(newRule);
                    applyRule(opCode, type, newRule);
                    return;
                }
            }
            log.info("A regra solicitada nao existe. Retornando...");
            return;
        }
    }

    private void removeRule(BlockRecord src, BlockRecord dst) {

        String opCode = "remove";
        if (pairBlockList.containsKey(src)) {
            Set<BlockRecord> values = pairBlockList.get(src);
            for (BlockRecord rule: values){
                if (rule.equals(dst)) {

                    log.info("A regra solicitada ja existe.");
                    pairBlockList.remove(src, dst);
                    applyRule(opCode, src, dst);
                    return;
                }
            }
        }
        log.info("A regra solicitada nao existe. Retornando...");
        return;
    }

    /** PARTE IMPORTANTE DA PRÀTICA REFERENTE AO ONOS   **/

    private void flowRuleAdd(DeviceId deviceId, TrafficSelector selector) {
        TrafficTreatment drop = DefaultTrafficTreatment.builder()
                .drop().build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withTreatment(drop)
                .withSelector(selector)
                .withPriority(DROP_PRIORITY)
                .makePermanent()
                .forDevice(deviceId)
                .build();

        flowRuleService.applyFlowRules(flowRule);
    }

    private void flowRuleRemove(DeviceId deviceId, TrafficSelector selector) {
        TrafficTreatment drop = DefaultTrafficTreatment.builder()
                .drop().build();

        FlowRule flowRule = DefaultFlowRule.builder()
                .fromApp(appId)
                .withTreatment(drop)
                .withSelector(selector)
                .withPriority(DROP_PRIORITY)
                .makePermanent()
                .forDevice(deviceId)
                .build();

        flowRuleService.removeFlowRules(flowRule);
    }

    private class BlockRecord {
        private  String ip;
        private  String port;

        BlockRecord(String ip, String port) {
            this.ip = ip;
            this.port = port;
        }

        BlockRecord() {
            this.ip = null;
            this.port = null;
        }

        BlockRecord(String ip) {
            this.ip = ip;
        }

        @Override
        public int hashCode() {
            return Objects.hash(ip, port);
        }

        @Override
        public String toString() {

            String record = this.ip;
            boolean noSrcPort  = this.port == null;
            if (!noSrcPort) {
                return record.concat(":").concat(this.port);
            }
            else return record;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final BlockRecord other = (BlockRecord) obj;
            return Objects.equals(this.ip, other.ip) && Objects.equals(this.port, other.port);
        }
    }
}