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
package intent.switchL2.app;

import static org.slf4j.LoggerFactory.getLogger;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.HostToHostIntent;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Set;

/**
 * WORK-IN-PROGRESS: Sample reactive forwarding application using intent framework.
 */
@Component(immediate = true)
public class IntentReactiveForwarding {

    // Objeto para registrar os eventos no LOG no ONOS
    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    // ID da Aplicacao. Para fins de logs e para atrelar variaveis à aplicacao
    private ApplicationId appId;

    // Intercepta apenas os pacotes IPv4
    private final TrafficSelector intercept = DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_IPV4).build();

    // cria um objeto do tipo processor, responsável por solicitar ao ONOS que
    // encaminhe para ele os pacotes capturados pelo controlador
    private ReactivePacketProcessor processor = new ReactivePacketProcessor();

    /**
     * Rotina que dita o que será executado na ativação da aplicação
     * (app activate <app>)
     */
    @Activate
    public void activate() {
        appId = coreService.registerApplication("intent.switch.app");
        packetService.addProcessor(processor, PacketProcessor.ADVISOR_MAX + 2);
        packetService.requestPackets(intercept, PacketPriority.REACTIVE, appId);
        log.info("Aplicacao de Switch com Intents iniciada com ID {}", appId.id());
    }

    /**
     * Rotina que dita o que será executado na desativação da aplicação (app deactivate <app>)
     * Geralmente cancelamos o interceptador de pacotes, removemos as regras, intents
     * e demais objetos criados pela nossa aplicação
     */
    @Deactivate
    public void deactivate() {
        packetService.removeProcessor(processor);
        //packetService.cancelPackets(intercept, PacketPriority.REACTIVE, appId);
        processor = null;
        flowRuleService.removeFlowRulesById(appId);
        removeIntents();
        log.info("Aplicacao finalizada com sucesso");
    }

    /**
     * Classe responsável por processar o pacote e encaminha-lo pelo caminho para o destino
     * criando as Intents para os proximos pacotes do fluxo
     */
    private class ReactivePacketProcessor implements PacketProcessor {

        @Override
        public void process(PacketContext context) {
            // Se o pacote já foi marcado como processado, ignorar ele.
            if (context.isHandled()) {
                return;
            }

            // Obtem o pacote de entrada a partir do contexto recebido pela aplicação,
            // Processa o pacote de entrada (parsed retorna os campos do pacote Ethernet, como src_mac, src_ip, dst_ip, payload, ...
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            // Nao tratar pacotes de controle (LLDP, BBDP, ...)
            if (isControlPacket(ethPkt)) {
                return;
            }

            // Nao tratar pacote de IPv6 multicast
            if (isIpv6Multicast(ethPkt) ) {
                return;
            }

            // Realizar flood de pacotes de Multicast que capturarmos
            if (ethPkt.isMulticast()) {
                flood(context);
                return;
            }

            // Obtem o host de origem e destino a partir dos respectivos enderecos MACs
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();

            Set<Host> srcSet = hostService.getHostsByMac(srcMac);
            Set<Host> dstSet = hostService.getHostsByMac(dstMac);

            // Sabemos quem e o destino? Se nao, flood e saia
            if (!dstSet.iterator().hasNext()) {
                flood(context);
                return;
            }

            Host dst = dstSet.iterator().next();
            Host src = srcSet.iterator().next();

            // Encaminhe o pacote e finalize
            // Verifique o codigo da funcao installIntent para aprender um pouco sobre como o ONOS lida com a criacao de Intents
            installIntent(context, src.id(), dst.id());

            /*
             * TODO Lab 1: Encaminhando o primeiro pacote para o destino
             *
             * Conforme observado na pratica, estamos perdendo o primeiro pacote (que subiu para o controlador) de cada novo fluxo,
             * uma vez que estamos processando o pacote e criando as Intents necessarias apenas.
             * Precisamos encaminhar o pacote recebido pelo controlador e nossa aplicacao para o host de destino, para evitar
             * que se perca o primeiro pacote do fluxo (por exemplo, o 1o. ping)
             *
             * Voce devera utilizar a funcao forwardPacketToDst, responsavel por encaminhar um pacote (context) para o destino informado (dst).
             * Verifique o código da função para entender o seu funcionamento (linha 188 a 197)
             */
            forwardPacketToDst(context, dst);
        }
    }

    /**
     * Encaminha um pacote (context) para o host de destino (dst) informado
     */
    private void forwardPacketToDst(PacketContext context, Host dst) {
        // Altera a porta de saida para a porta onde se encontra o host de destino (dst.location().port()
        TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(dst.location().port()).build();
        // Cria um pacote de saida (OutBoundPacket) para ser enviado ao switch do host de destino (dst.location().deviceId())
        OutboundPacket packet = new DefaultOutboundPacket(dst.location().deviceId(),
                                                          treatment, context.inPacket().unparsed());
        // Envia o pacote criado
        packetService.emit(packet);
        log.info("Enviando o pacote: {}", packet);
    }

    /**
     * Instala uma Intent para o novo fluxo recebido
     */
    private void installIntent(PacketContext context, HostId srcId, HostId dstId) {
        TrafficSelector selector = DefaultTrafficSelector.emptySelector();
        //TrafficSelector selector = DefaultTrafficSelector.builder().build();
        //TrafficSelector selector = DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build();
        TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();
        //TrafficTreatment treatment = DefaultTrafficTreatment.builder().build();

        // Cria uma Intent do tipo HostToHost (srcId <--> dstId)
        HostToHostIntent intent = HostToHostIntent.builder()
                //.priority(5)
                .appId(appId)
                .one(srcId)
                .two(dstId)
                .selector(selector)
                .treatment(treatment)
                .build();

        // Envia a Intent criada para o framework de intents. O framework ira compilar a intent
        // instala-la e monitorar os eventos da rede que possam afetar a intent.
        intentService.submit(intent);
    }

    /**
     * Realiza o flood do pacote (portNumber.FLOOD) se for permitido
     */
    private void flood(PacketContext context) {
        if (topologyService.isBroadcastPoint(topologyService.currentTopology(),
                                             context.inPacket().receivedFrom())) {
            packetOut(context, PortNumber.FLOOD);
        } else {
            context.block();
        }
    }

    /**
     * Envia o pacote para uma porta especifica do switch em que ele foi recebido
     */
    private void packetOut(PacketContext context, PortNumber portNumber) {
        context.treatmentBuilder().setOutput(portNumber);
        context.send();
    }

    /**
     * Verifica se o pacote passado como argumento e um pacote de controle
     */
    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN || type == -30398 || type == -31011;
    }

    /**
     * Verifica se o pacote passado como argumento e um pacote de IPv6 multicast
     */
    private boolean isIpv6Multicast(Ethernet eth) {
        return eth.getEtherType() == Ethernet.TYPE_IPV6 && eth.isMulticast();
    }

    /**
     * Remove as intents criadas pela nossa aplicação.
     */
    private void removeIntents() {
        Iterable<Intent> intentsList = intentService.getIntents();
        for ( Intent intent : intentsList) {
            boolean toRemove = Objects.equals(appId, intent.appId());
            if (toRemove) {
                intentService.withdraw(intent);
            }
        }
    }
}
