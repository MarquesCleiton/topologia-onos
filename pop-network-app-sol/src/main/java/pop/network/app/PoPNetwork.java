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
package pop.network.app;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.ARP;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

@Component(immediate = true)
public class PoPNetwork {

    // Objeto para registrar os eventos no LOG no ONOS
    private static Logger log = LoggerFactory.getLogger(PoPNetwork.class);

    public static final String INTENT_FORMAT = "%s~%s";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    // ID da Aplicacao. Para fins de logs e para atrelar variaveis à aplicacao
    private ApplicationId appId;

    // cria um objeto do tipo processor, responsável por solicitar ao ONOS que
    // encaminhe para ele os pacotes capturados pelo controlador
    private final PacketProcessor packetProcessor = new PopPacketProcessor();

    // Intercepta apenas os pacotes IPv4
    private final TrafficSelector intercept = DefaultTrafficSelector.builder()
            .matchEthType(Ethernet.TYPE_IPV4).build();

    // Registros para armazenar as informacoes da rede e dos clientes conectados e seus blocos conhecidos
    private ConcurrentMap<IpAddress, ConnectPoint> networks;
    private ConcurrentMap<IpPrefix, Set<IpAddress>> clientes;

    MacAddress virtual = MacAddress.valueOf("aa:aa:aa:aa:aa:aa");

    private ArrayList<IpAddress> defaultRouter = null;

    /**
     * Rotina que dita o que será executado na ativação da aplicação
     * (app activate <app>)
     */
    @Activate
    public void activate() {
        appId = coreService.registerApplication("pop.network.app");

        // Le e carrega as configuracoes da rede e dos clientes dos arquivos de configuracao
        clientes = RoutingConfigReader.clientesPoP;
        networks = RoutingConfigReader.networks;

        // A prioridade mais alta (+6) é para que essa aplicação não tenha precedência em relação
        // a aplicação de teste de velocidade que será utilizada em conjunto com ela
        packetService.addProcessor(packetProcessor, PacketProcessor.ADVISOR_MAX + 6);
        packetService.requestPackets(intercept, PacketPriority.REACTIVE, appId);
        log.info("Aplicacao do PoP iniciada com o ID {}", appId.id());
    }

    /**
     * Rotina que dita o que será executado na desativação da aplicação (app deactivate <app>)
     * Geralmente cancelamos o interceptador de pacotes, removemos as regras, intents
     * e demais objetos criados pela nossa aplicação
     */
    @Deactivate
    public void deactivate() {
        packetService.removeProcessor(packetProcessor);
        //packetService.cancelPackets(intercept, PacketPriority.REACTIVE, appId);
        flowRuleService.removeFlowRulesById(appId);
        clientes.clear();
        networks.clear();
        removeIntents();
        log.info("Parando a execucao da aplicacao PoP");
    }

    /**
     * Cria as intents para o envio do pacote recebido a partir do sw de origem (srcHostId) para o switch de destino (dstHostId)
     */
    private void packetOut(PacketContext context, HostId srcHostId, HostId dstHostId, IpPrefix rotaEscolhida) {

        Host dstHost = hostService.getHost(dstHostId);

        log.info("Alterando par de MAC original {}<-->{} para {}<-->{}", context.inPacket().parsed().getSourceMAC(), context.inPacket().parsed().getDestinationMAC(), virtual, dstHost.mac());
        context.inPacket().parsed().setDestinationMACAddress(dstHost.mac());
        context.inPacket().parsed().setSourceMACAddress(virtual);
        log.info("MACs do pacote recebido alterado");

        installIntent(context, srcHostId, dstHostId, rotaEscolhida);
    }

    /**
     * Envia o pacote para uma porta especifica do switch em que ele foi recebido
     */
    private void packetOutPort(PacketContext context, PortNumber portNumber) {

        DeviceId id = context.inPacket().receivedFrom().deviceId();

        TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
        builder.setOutput(portNumber);
        packetService.emit(new DefaultOutboundPacket(id,
                                                     builder.build(), ByteBuffer.wrap(context.inPacket().parsed().serialize())));
    }

    /**
     * Envia o pacote para o switch/porta informados pelo ponto de conexao outPort
     */
    private void packetOutPort(PacketContext context, ConnectPoint outPort) {

        TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
        builder.setOutput(outPort.port());
        packetService.emit(new DefaultOutboundPacket(outPort.deviceId(),
                                                     builder.build(), ByteBuffer.wrap(context.inPacket().parsed().serialize())));
    }

    /**
     * Envia o pacote ARP para o sw/porta onde o destino se encontra (dstIp)
     */
    private void sendTo(Ethernet packet) {

        ARP pkt = (ARP) packet.getPayload();
        IpAddress dstIp = Ip4Address.valueOf(pkt.getTargetProtocolAddress());

        ConnectPoint outPort = checkNotNull(networks.get(dstIp), "Ip de destino %s does not exist", dstIp);

        TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
        builder.setOutput(outPort.port());
        packetService.emit(new DefaultOutboundPacket(outPort.deviceId(),
                                                     builder.build(), ByteBuffer.wrap(packet.serialize())));
    }

    /**
     *  Constroi o pacote de requisicao ARP (ARP_REQUEST)
     *  Utilizamos o ARP_REQUEST para descobrir os roteadores dos clientes de nossa rede.
     */
    public static Ethernet buildArpRequest(Ip4Address srcIp, MacAddress srcMac,
                                           Ip4Address dstIp) {

        Ethernet eth = new Ethernet();
        MacAddress broadcast = MacAddress.valueOf("ff:ff:ff:ff:ff:ff");
        eth.setDestinationMACAddress(broadcast);
        eth.setSourceMACAddress(srcMac);
        eth.setEtherType(Ethernet.TYPE_ARP);

        ARP arp = new ARP();
        arp.setOpCode(ARP.OP_REQUEST);
        arp.setProtocolType(ARP.PROTO_TYPE_IP);
        arp.setHardwareType(ARP.HW_TYPE_ETHERNET);

        arp.setProtocolAddressLength((byte) Ip4Address.BYTE_LENGTH);
        arp.setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH);
        arp.setSenderHardwareAddress(srcMac.toBytes());
        arp.setTargetHardwareAddress(srcMac.toBytes());

        arp.setTargetProtocolAddress(dstIp.toInt());
        arp.setSenderProtocolAddress(srcIp.toInt());

        eth.setPayload(arp);
        return eth;
    }

    /**
     * Processq os pacotes recebidos e encaminha-os para o destino
     * criando as Intents para os proximos pacotes do fluxo
     */
    private void processPacket(PacketContext context, Ethernet eth) {

        // Se o pacote já foi processado por outro modulo, não faça nada
        if (context.isHandled()) {
            return;
        }

        IPv4 ipv4Packet = (IPv4) eth.getPayload();
        Ip4Address dstIp = Ip4Address.valueOf(ipv4Packet.getDestinationAddress());
        Ip4Address srcIp = Ip4Address.valueOf((ipv4Packet.getSourceAddress()));

        Set<IpPrefix> rotas = clientes.keySet();

        boolean existDefault = clientes.containsKey(IpPrefix.valueOf("0.0.0.0/0"));

        if (existDefault) {
            defaultRouter = new ArrayList<>(clientes.remove(IpPrefix.valueOf("0.0.0.0/0")));
        }

        IpAddress routerIp = null;
        IpPrefix rotaEscolhida = null;

        for (IpPrefix rota : rotas) {
            if (rota.contains(dstIp)) {
                log.warn("Procurando a rota para o ip de destino {} ", dstIp);
                Random rand = new Random(System.currentTimeMillis());
                int index = rand.nextInt(clientes.get(rota).size());
                Iterator<IpAddress> iter = clientes.get(rota).iterator();
                for (int i = 0; i < index; i++) {
                    iter.next();
                }
                routerIp = iter.next();
                rotaEscolhida = rota;
                break;
            }
        }

        if (routerIp == null && defaultRouter == null) {
            log.warn("Nao ha rota para IP de Destino {}. E não há rota default configurada", dstIp);
            context.block();
            return;
        } else if (routerIp == null) {
            log.warn("Nao ha rota para IP de Destino {}. Enviando para rota default", dstIp);
            Random rand = new Random(System.currentTimeMillis());
            int index = rand.nextInt(defaultRouter.size());
            Iterator<IpAddress> iter = defaultRouter.iterator();
            for (int i = 0; i < index; i++) {
                iter.next();
            }
            routerIp = iter.next();
            rotaEscolhida = IpPrefix.valueOf(dstIp, IpPrefix.MAX_INET_MASK_LENGTH);

        }

        Set<Host> dstHosts = hostService.getHostsByIp(routerIp);
        MacAddress srcMac = eth.getSourceMAC();

        //while (dstHosts.isEmpty()) {
        if (dstHosts.isEmpty())  {
            Ethernet arpReply = buildArpRequest(srcIp, srcMac, routerIp.getIp4Address());
            sendTo(arpReply);
            context.block();
            return;
        }

        HostId dstHostId = dstHosts.iterator().next().id();
        HostId srcHostId = HostId.hostId(srcMac);

        packetOut(context, srcHostId, dstHostId, rotaEscolhida);
    }

    /**
     * Verifica se o EtherType (tipo) do pacote é IPv4
     */
    private boolean isIpv4Packet(Ethernet eth) {
        return eth.getEtherType() == Ethernet.TYPE_IPV4;
    }

    /**
     * Classe responsavel por tratar os pacotes interceptados pelo controlador e
     * encaminhados para a aplicação. Aqui apenas verificamos se é um pacote IPv4 e que não seja de controle
     */
    private class PopPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            Ethernet eth = context.inPacket().parsed();

            // Saia se for um pacote de controle
            if (isControlPacket(eth) || !isIpv4Packet(eth)) {
                return;
            }
            processPacket(context, eth);
        }
    }

    /**
     * Verifica se o pacote passado como argumento e um pacote de controle (lldp, bbdp, etc)
     */
    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN || type == -30398 || type == -31011;
    }

    /**
     * Instala uma Intent para o novo fluxo recebido e encaminha o pacote para o destino
     */
    private void installIntent(PacketContext context, HostId srcHost, HostId dstHost, IpPrefix rotaEscolhida) {

        Ethernet inPkt = context.inPacket().parsed();
        TrafficSelector.Builder selectorBuilder = DefaultTrafficSelector.builder();

        Host srcRouter = hostService.getHost(srcHost);
        Host dstRouter = hostService.getHost(dstHost);

        IPv4 ipv4Packet = (IPv4) inPkt.getPayload();

        // Porta de saida do pacote - SW onde o pacote deve ser encaminhado para o host de destino
        ConnectPoint egress =  new ConnectPoint(DeviceId.deviceId(dstRouter.location().deviceId().uri()),
                                                PortNumber.portNumber(dstRouter.location().port().toLong()));
        // Porta de entrada do pacote - SW onde o pacote foi recebido
        ConnectPoint ingress =  new ConnectPoint(DeviceId.deviceId(srcRouter.location().deviceId().uri()),
                                                 PortNumber.portNumber(srcRouter.location().port().toLong()));

        Key key = Key.of(format(INTENT_FORMAT, srcHost, Ip4Address.valueOf(ipv4Packet.getDestinationAddress())), appId);

        // Seletor do tráfego (match no mac de origem, mac de destino e IP de destino
        selectorBuilder.matchInPort(context.inPacket().receivedFrom().port())
                .matchEthSrc(srcRouter.mac())
                .matchEthDst(virtual)
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(rotaEscolhida);

        /*
         * TODO Lab 1: Corrigir as intents criadas
         *
         * As intents não estão definindo nenhum tratamento para os pacotes. Para que o roteamento funcione, devemos
         * inserir o mac address do gateway virtual (aa:aa:aa:aa:aa:aa) como MAC origem do pacote e o o mac address
         * do roteador de borda do cliente como MAC de destino, assim como fizemos no pacote que subiu ao controlador (1º pacote do fluxo)
         * Insira a linha para setar o endereço mac de destino como o do roteador de borda do cliente no tratamento das intents abaixo
         * Para obter o endereço MAC do roteador de destino, basta utilizar a chamada "dstRouter.mac()"
         */
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setEthSrc(virtual)
                .setEthDst(dstRouter.mac())
                .build();

        // cria a intent do tipo P2P com as informacoes acima
        PointToPointIntent intent = PointToPointIntent.builder()
                .appId(appId)
                .key(key)
                .egressPoint(egress)
                .ingressPoint(ingress)
                .selector(selectorBuilder.build())
                .treatment(treatment)
                .build();

        log.info("Intent do tipo PointToPoint criada {}", intent);
        intentService.submit(intent);

        // evita perder o primeiro pacote (envia ele diretamente para o destino apos processamento)
        packetOutPort(context, egress);
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
                intentService.purge(intent);
            }
        }
    }
}