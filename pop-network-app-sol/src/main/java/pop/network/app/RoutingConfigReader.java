package pop.network.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onosproject.net.ConnectPoint;
import org.onosproject.store.service.StorageService;
import org.slf4j.Logger;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Modulo para ler o arquivo de configuracao dos clientes
 */
@Component(immediate = true)
public class RoutingConfigReader {

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    private static final String CONFIG_DIR = "../config";
    private static final String DEFAULT_CONFIG_FILE = "conexoes.json";
    private String configFileName = DEFAULT_CONFIG_FILE;

    protected static ConcurrentMap<IpPrefix, Set<IpAddress>> clientesPoP = Maps.newConcurrentMap();
    protected  static ConcurrentMap<IpAddress, ConnectPoint> networks = Maps.newConcurrentMap();

    public RoutingConfigReader() {
    }

    public void read() {
        readConfiguration(configFileName);
    }

    public ConcurrentMap<IpPrefix, Set<IpAddress>> getClients() {
        return clientesPoP;
    }

    public void setClients(ConcurrentMap<IpPrefix, Set<IpAddress>> clientsPoP) {
        this.clientesPoP = clientesPoP;
    }

    public static ConcurrentMap<IpAddress, ConnectPoint> getNetworks() {
        return networks;
    }

    public void setConexoes(ConcurrentMap<IpAddress, ConnectPoint> networks) {
        this.networks = networks;
    }

    @Activate
    protected void activate() {
       /* if (config != null) {
            applyNetworkConfig(config);
        }*/
        /*networks = storageService.<IpAddress, ConnectPoint>consistentMapBuilder()
                 .withSerializer(Serializer.using(KryoNamespaces.API))
                 .withName("POP-SCI")
                 .build();*/
        readConfiguration(configFileName);
        log.info("Started network config reader");
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
    }

    protected void readConfiguration(String configFilename) {

        File configFile = new File(CONFIG_DIR, configFilename);
        ObjectMapper mapper = new ObjectMapper();

        try {
            log.info("Carregando arquivos de configuracao: {}", configFile.getAbsolutePath());
            Configuration config = mapper.readValue(configFile,
                                                    Configuration.class);
            for (Clientes roteadores : config.getRoteadores()) {
                log.info("lendo roteador: {}", roteadores.getName());

                for (String rota: roteadores.getRotas()) {
                    if (clientesPoP.containsKey(IpPrefix.valueOf(rota))){
                        clientesPoP.get(IpPrefix.valueOf(rota)).add(IpAddress.valueOf(roteadores.getIpRouter()));
                    }
                    else{
                        Set <IpAddress> dstRouter = new LinkedHashSet<>();
                        dstRouter.add(IpAddress.valueOf(roteadores.getIpRouter()));
                        clientesPoP.putIfAbsent(IpPrefix.valueOf(rota), dstRouter);
                    }
                }
            }
            for (Conexoes connectionPoint : config.getConexoes()) {
                String device = null;
                for (String ips: connectionPoint.getIpAddresses()) {
                    device = String.join("/", dpidToUri(connectionPoint.getDpid()), connectionPoint.getPortNumber());
                    ConnectPoint conexao = ConnectPoint.deviceConnectPoint(device);
                    networks.put(IpAddress.valueOf(ips), conexao);
                }
            }

        } catch (FileNotFoundException e) {
            log.warn("Configuration file not found: {}", configFileName);
        } catch (IOException e) {
            log.error("Error loading configuration", e);
        }
    }

    private static String dpidToUri(String dpid) {
        return "of:" + dpid.replace(":", "");
    }
}