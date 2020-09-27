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
package simple.switchl2.app;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Set;

@Component(immediate = true)
public class SimpleSwitch {

    // Variaveis globais relativas a prioridade dos Fluxos
    // e tempo de expiracao de uma regra de fluxo criada pela aplicacao
    private static final int DEFAULT_TIMEOUT = 30;
    private static final int DEFAULT_PRIORITY = 100;

    // Objeto para registrar os eventos no LOG no ONOS
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    // ID da Aplicacao. Para fins de logs e para atrelar variaveis à aplicacao
    private ApplicationId appId;

    // cria um objeto do tipo processor, responsável por solicitar ao ONOS que
    // encaminhe para ele os pacotes capturados pelo controlador
    private L2PacketProcessor processor = new L2PacketProcessor();

    private int flowPriority = DEFAULT_PRIORITY;
    private int flowTimeout = DEFAULT_TIMEOUT;

    /**
     * Rotina que dita o que será executado na ativação da aplicação
     * (app activate <app>)
     */
    @Activate
    protected void activate() {
        appId = coreService.registerApplication("simple.switchl2.app");
        packetService.addProcessor(processor, PacketProcessor.ADVISOR_MAX + 2);
        requestIntercepts();
        log.info("Started with Application ID {}", appId.id());
    }

    /**
     * Rotina que dita o que será executado na desativação da aplicação (app deactivate <app>)
     * Geralmente cancelamos o interceptador de pacotes, removemos as regras, intents
     * e demais objetos criados pela nossa aplicação
     */
    @Deactivate
    protected void deactivate() {
        withdrawIntercepts();
        flowRuleService.removeFlowRulesById(appId);
        packetService.removeProcessor(processor);
        processor = null;
        log.info("Stopped");
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
     * Classe responsável por processar o pacote e encaminha-lo pelo caminho para o destino
     */
    private class L2PacketProcessor implements PacketProcessor {

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

            // Obtem do pacote os enderecos MAC de origem e destino
            MacAddress srcMac = ethPkt.getSourceMAC();
            MacAddress dstMac = ethPkt.getDestinationMAC();

            // Obtem o host de destino a partir do seu endereco MAC
            //HostId id = HostId.hostId(dstMac);

            // Nao processe links locais
            //if (id.mac().isLinkLocal()) {
            //    return;
            //}

            Set <Host> dstSet = hostService.getHostsByMac(dstMac);

            // Sabemos quem e o destino? Se nao, flood e saia
            //Host dst = hostService.getHost(id);
            if (!dstSet.iterator().hasNext()) {
                flood(context);
                return;
            }

            Host dst = dstSet.iterator().next();

           /*
            * TODO Lab 1: Criando regras de Fluxo - Switch de Borda e Caminho na Topologia
            * Retire os comentarios da linha 205 e 237 para descomentar o bloco inteiro
            * Iremos utilizar o servico de topologia do ONOS para calcular os caminhos mais
            * curtos ate o destino, se existir, e criar os fluxos pelo caminho.
            */

            log.warn("Calculo do caminho de {} para {}", context.inPacket().receivedFrom().deviceId(), dst.location().deviceId());

            //Se estamos no mesmo switch de borda que o nosso destino
            //encaminhar o pacote (criar regra de fluxo) para o destino e sair
            if (pkt.receivedFrom().deviceId().equals(dst.location().deviceId())) {
                if (!context.inPacket().receivedFrom().port().equals(dst.location().port())) {
                    installRule(context, dst.location().port());
                }
                return;
            }

            // Se não estamos no switch do destino, descubra os caminhos que levam daqui (pkt.receivedFrom().deviceId())
            // para o switch de borda do destino (dst.localtion().deviceId())
            Set<Path> paths =
                    topologyService.getPaths(topologyService.currentTopology(),
                                             pkt.receivedFrom().deviceId(),
                                             dst.location().deviceId());
            // Se não existem caminhos, flood e saia
            if (paths.isEmpty()) {
                flood(context);
                return;
            }

            // Escolha um dos caminhos possiveis (calculados) que não retornam para onde viemos (evitar loop na topologia L2)
            // Se não existir tal caminho, flood e saia
            Path path = pickForwardPath(paths, pkt.receivedFrom().port());
            if (path == null) {
                log.warn("Oh Nao... Não sei para onde ir... {} -> {} recebido em {}",
                         ethPkt.getSourceMAC(), ethPkt.getDestinationMAC(),
                         pkt.receivedFrom());
                flood(context);
                return;
            }


            /*
             * TODO Lab 1: Criando regras de Fluxo  - Corrigindo o ponto de conexão
             *
             * Aqui, voce devera trocar o ponto de conexão (switch/porta) onde se encontra o destino destino (dst.location())
             * pela porta de saida do switch atual que nos levará para o proximo switch no caminho para o destino (path.src())
             * Para tanto, atribua ao ponto de conexao egressPort o valor correto (path.src)
             *
             */

            //ConnectPoint egressPort = new ConnectPoint(dst.location().deviceId(), dst.location().port());
            ConnectPoint egressPort = new ConnectPoint(path.src().deviceId(), path.src().port());

            /*
             * TODO Lab 1: Criando regras de Fluxo - Substituindo o envio do pacote pela criacao da regra de fluxo
             *
             * Você também deverá trocar a função sendTo, responsavel por enviar o pacote diretamente para o destino
             * pela funcao responsavel por criar a regra de fluxo no switch para evitar que os proximos pacotes do mesmo fluxos sejam tratados novamente
             * Para tanto, comente a função sendTo (linha 262) e a substitua pela funcao de installRule, com os parametros corretos, para criar as regras de fluxo no Switch.
             * Veja o codigo da funcao installRule (linha 306) para aprender um pouco como o ONOS trata a criacao de regras
             */

            // Encaminhe o pacote e finalize
            //sendTo(context.inPacket().parsed(), egressPort);
            installRule(context, egressPort.port());

            // Registre no Log (como warning) o encaminhamento realizado
            log.warn("Trafego do mac {} para o mac_dst {} via Switch/porta {}", srcMac, dstMac, egressPort.toString());
        }
    }

    /**
     * Encaminha o pacote para o switch/porta especificados
     */
    private void sendTo(Ethernet packet, ConnectPoint outPort) {

        TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
        builder.setOutput(outPort.port());

        packetService.emit(new DefaultOutboundPacket(outPort.deviceId(),
                                                  builder.build(), ByteBuffer.wrap(packet.serialize())));
    }

    /**
     * Instala uma regra, encaminhando o pacote para a porta especifica em portNumber
     */
    private void installRule(PacketContext context, PortNumber portNumber) {
        Ethernet inPkt = context.inPacket().parsed();
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();

        // Nao crie regras para pacotes ARPs. Apenas encaminhe-os para a porta de saida
        if (inPkt.getEtherType() == Ethernet.TYPE_ARP) {
            packetOut(context, portNumber);
            return;
        }

        // Cria o seletor que sera utilizado para o match da regra com o fluxo
        selectorBuilder.matchInPort(context.inPacket().receivedFrom().port())
                .matchEthSrc(inPkt.getSourceMAC())
                .matchEthDst(inPkt.getDestinationMAC())
                .matchEthType(Ethernet.TYPE_IPV4);

        // Cria a acao que sera realizada nos pacotes que realizarem match com a regra
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(portNumber)
                .build();

        // Cria a regra de fluxo. Repare na prioridade da regra e no parametro makeTemporary
        // que torna a regra temporaria, expirando-a apos o tempo definido em "flowTimeout"
        // Podemos utilizar o parametro .makePermanent() para tornar a regra permanente.
        ForwardingObjective forwardingObjective = DefaultForwardingObjective.builder()
                .fromApp(appId)
                .withSelector(selectorBuilder.build())
                .withTreatment(treatment)
                .withPriority(flowPriority)
                .withFlag(ForwardingObjective.Flag.VERSATILE)
                .makeTemporary(flowTimeout)
                .add();

        flowObjectiveService.forward(context.inPacket().receivedFrom().deviceId(),
                                     forwardingObjective);

        log.warn("Instalando regra de fluxo no switch {}", context.inPacket().receivedFrom().deviceId());

        // Envia o pacote para o proximo switch no caminho (para evitarmos perder o primeiro 'pacote do fluxos')
        packetOut(context, portNumber);
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
     * Selecione um caminho de um dado conjunto de caminhos, tal que ele nao retorne à porta de origem
     * Utilizamos essa funcao para evitar caminhos com loops na topologia
     */
    private Path pickForwardPath(Set<Path> paths, PortNumber notToPort) {
        for (Path path : paths) {
            if (!path.src().port().equals(notToPort)) {
                return path;
            }
        }
        return null;
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
}