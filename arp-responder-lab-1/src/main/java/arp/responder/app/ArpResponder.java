/*
 * Copyright 2014 Open Networking Laboratory
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
package arp.responder.app;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.ARP;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Host;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Component(immediate = true)
public class ArpResponder {

    // Objeto para registrar os eventos no LOG no ONOS
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String REQUEST_NULL = "Requisiçao ARP ou NDP nao pode ser nula";
    private static final String NOT_ARP_REQUEST = "Nao e uma requisicao ARP.";

    // Servicos do CORE do ONOS (KARAF) que nossa aplicação espera utilizar
    // Prove um serie de funcionalidades para a aplicação
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    /*
     * TODO Lab 2: Utilize o servico (primitiva) de HOSTS para obter as informacoes dos HOSTs da rede
     *
     * Tudo que você precisa fazer é descomentar as duas linhas a seguir:
     */
    //@Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    //protected HostService hostService;

    // ID da Aplicacao. Para fins de logs e para atrelar variaveis à aplicacao
    private ApplicationId appId;

    /* cria um objeto do tipo processor, responsável por solicitar ao ONOS que encaminhe para ele
       os pacotes capturados pelo ONOS */
    private ArpPacketProcessor processor = new ArpPacketProcessor();

    /*
     * TODO Lab 2: Uma vez que iremos utilizar o servico de HOSTS, podemos comentar a nossa tabela de ip_macs
     *
     * Basta comentar a linha baixo (HashMap...)
     */
    HashMap<Ip4Address, MacAddress> ip_mac = new HashMap();

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("arp.responder.app");
        packetService.addProcessor(processor, PacketProcessor.ADVISOR_MAX + 1);
        requestIntercepts();
        log.info("Aplicacao ARP-RESPONDER iniciada com o ID {}", appId.id());
    }

    @Deactivate
    protected void deactivate() {
        withdrawIntercepts();
        packetService.removeProcessor(processor);
        processor = null;
        ip_mac.clear();
        log.info("Aplicacao de ARP-RESPONDER desativada");
    }

    /**
     * Request packet in via PacketService.
     */
    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        //packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
        selector.matchEthType(Ethernet.TYPE_ARP);
        //packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    private class ArpPacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {

            if (context.isHandled()) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            // Bail if this is deemed to be a control packet.
            if (isControlPacket(ethPkt)) {
                return;
            }
            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6) {
                return;
            }
            handlePacket(context, ethPkt);
        }
    }

    public boolean handlePacket(PacketContext context, Ethernet ethPkt) {

        if (ethPkt.getEtherType() == Ethernet.TYPE_ARP) {
            checkNotNull(ethPkt, REQUEST_NULL);
            return handleArp(context, ethPkt);
        }
        return false;
    }

    private boolean handleArp(PacketContext context, Ethernet ethPkt) {
        ARP arp = (ARP) ethPkt.getPayload();

        checkArgument(arp.getOpCode() == ARP.OP_REQUEST || arp.getOpCode() == ARP.OP_REPLY, NOT_ARP_REQUEST);

       /*
        * TODO Lab 2: Não precisamos mais armazenar os ip's e mac's em nossa tabela hash de endereços
        *
        * Basta comentar as 3 linhas abaixo
        */
        Ip4Address srcAddress = Ip4Address.valueOf(arp.getSenderProtocolAddress());
        MacAddress srcMac = MacAddress.valueOf(arp.getSenderHardwareAddress());
        ip_mac.put(srcAddress, srcMac);

        if (arp.getOpCode() == ARP.OP_REPLY) {
            return true;
        }   else if (arp.getOpCode() == ARP.OP_REQUEST) {
            reply(ethPkt, context.inPacket().receivedFrom(), context);
            context.block();
            return true;
        }
        return false;
    }

    public void reply(Ethernet eth, ConnectPoint inPort, PacketContext context) {
        ARP arp = (ARP) eth.getPayload();
        Ip4Address targetAddress = Ip4Address.valueOf(arp.getTargetProtocolAddress());

        /*
        * TODO Lab 2: "Descomente a linha do dstHost uma vez que utilizaremos as primitivas do ONOS
        * TODO Lab 2:  no lugar de nossa tabela HASH (2a. linha) para recuperar os dados do HOST
        *
        * Descomente a linha abaixo
        * Comente a segunda linha (dstMac)
        */
        //Set<Host> dstHost = hostService.getHostsByIp(targetAddress);
        MacAddress dstMac = ip_mac.get(targetAddress);

        /*
        * TODO Lab 2: "Checaremos se o HOST de destino já é conhecido pelo ONOS. Antes verificavamos
        * TODO Lab 2:  se o Mac de destino (dstMac) estava em nosso HASH)
        *
        * Descomente a linha abaixo
        * Comente a segunda linha (dstMac)
        */
        //if (dstHost.isEmpty()) {
        if (dstMac == null) {
            flood(context);
            return;
        }
       /*
        * TODO Lab 2: Recupere o mac address de destino a partir do HOSTS (database) do ONOS
        *
        * Descomente a linha abaixo
        */
        //MacAddress dstMac = dstHost.iterator().next().mac();

        Ethernet arpReply = ARP.buildArpReply(targetAddress, dstMac, eth);
        sendTo(arpReply, inPort);
        return;
    }

    private void sendTo(Ethernet packet, ConnectPoint outPort) {

        TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
        builder.setOutput(outPort.port());
       /*
        * TODO Lab 1: Corrija o erro da aplicação que não está respondendo os ARPS
        *
        * A função emit está comentada. Ela é a responsável por gerar um pacote de saida (OutboundPacket)
        * Iremos enviar o pacote para o dispositivo e porta onde o host de origem da requisição ARP se encontra (outPort.deviceId() e outPort.port())
        * Apenas descomente as linhas abaixo (apaguei as linhas 236 e 239)
        */

        packetService.emit(new DefaultOutboundPacket(outPort.deviceId(),
                                                  builder.build(), ByteBuffer.wrap(packet.serialize())));

    }

    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN || type == -30398 || type == -31011;
    }

    // Floods the specified packet if permissible.
    private void flood(PacketContext context) {
        if (topologyService.isBroadcastPoint(topologyService.currentTopology(),
                                             context.inPacket().receivedFrom())) {
            packetOut(context, PortNumber.FLOOD);
        } else {
            context.block();
        }
    }

    // Sends a packet out the specified port.
    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }
}