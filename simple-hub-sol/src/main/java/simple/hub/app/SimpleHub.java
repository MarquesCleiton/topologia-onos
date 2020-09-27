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
package simple.hub.app;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(immediate = true)
public class SimpleHub {

    // Objeto para registrar os eventos no LOG no ONOS
    private final Logger log = LoggerFactory.getLogger(getClass());

    // Servicos do CORE do ONOS (KARAF) que nossa aplicação espera utilizar
    // Prove um serie de funcionalidades para a aplicação
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    // ID da Aplicacao. Para fins de logs e para atrelar variaveis à aplicacao
    private ApplicationId appId;

    /* cria um objeto do tipo processor, responsável por solicitar ao ONOS que encaminhe para ele
       os pacotes capturados pelo ONOS */
    private final HubPacketProcessor packetProcessor = new HubPacketProcessor();

    // Rotina que dita o que será executado na ativação da aplicação (app activate <app>
    @Activate
    public void activate() {
        appId = coreService.registerApplication("hub.simples.app");

        /* Adiciona o objeto processor criado no packetService para que o ONOS encaminhe os pacotes
           para ser processado pela nossa aplicação */
        packetService.addProcessor(packetProcessor, PacketProcessor.ADVISOR_MAX + 10);
        // Seleciona quais tipos de pacotes deverão ser interceptados pelo ONOS
        requestIntercepts();

        log.info("Aplicacao de HUB iniciada com o ID {}", appId.id());
    }

    /**
     * Rotina que dita o que será executado na desativação da aplicação (app deactivate <app>)
     * Geralmente cancelamos o interceptador de pacotes, removemos as regras, intents
     * e demais objetos criados pela nossa aplicação
     */
    @Deactivate
    public void deactivate() {
        withdrawIntercepts();
        flowRuleService.removeFlowRulesById(appId);
        packetService.removeProcessor(packetProcessor);
        log.info("Aplicação de HUB desativada");
    }

    /**
     * Solicita os pacotes de entrada via PacketService
     * Captura os pacotes de ARP e IPV4 para a nossa aplicação
     */
    private void requestIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    /**
     * Retira a solicitação de captura dos pacotes de ARP e IPv4
     */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        //packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
        selector.matchEthType(Ethernet.TYPE_ARP);
        //packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, appId);
    }

    /**
     * Classe responsável por processar o pacote e lidar com os demais aspectos da nossa aplicação
     * como criar as regras de fluxo ou alterar o payload do pacote
     */
    private class HubPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {

            // Se o pacote já foi marcado como processado, ignorar ele.
            if (context.isHandled()) {
                return;
            }

            // Retira o pacote de entrada a partir do contexto recebido pela aplicação,
            InboundPacket pkt = context.inPacket();
            // Processa o pacote de entrada (parsed retorna os campos do pacote Ethernet, como src_mac, src_ip, dst_ip, payload, ...
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }

            // não trata pacotes de controle (LLDP, BBDP, ...)
            if (isControlPacket(ethPkt)) {
                return;
            }

            // O hub apenas realizar o flood de todos os pacotes
            flood(context);
        }
    }

    /**
     * Verifique se e um pacote de controle
     */
    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN || type == -30398 || type == -31011;
    }

    /**
     * Funcao responsavel por realizar o flood do pacote em todas as portas do switch
     */
    private void flood(PacketContext context) {
        // Verifica se o broadcast é permitido para esse pacote, recebido na interface especifica
        if (topologyService.isBroadcastPoint(topologyService.currentTopology(),
                                             context.inPacket().receivedFrom())) {
            /*
            * TODO Lab 1: Corrija o erro da aplicação que está enviando os pacotes apenas para a porta de origem
            *
            * A porta está errada. Nosso pacote está sendo enviado para a porta em que ele foi recebido
            * Precisamos utilizar a porta de FLOOD, que encaminhará o pacote para todas as portas, menos a de entrada.
            * Para tanto, apenas troque o 2o. parametro do packetOut pela floodPort
            */
            //PortNumber fakePort = PortNumber.portNumber(3);
            PortNumber floodPort = PortNumber.FLOOD;
            packetOut(context, floodPort);
        } else {
            context.block();
        }
    }

    /**
     * Envia o pacote para a porta especificada
     */
    private void packetOut(PacketContext context, PortNumber portNumber) {
        // Altera a porta de saida para a porta especificada pelo portNumber
        context.treatmentBuilder().setOutput(portNumber);
        log.info("SCI - HUB: Porta de saida {}", portNumber);
        // Envia o pacote para a porta de saida
        context.send();
    }
}
