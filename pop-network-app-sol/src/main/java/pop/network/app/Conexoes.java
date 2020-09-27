package pop.network.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Representa as conexoes de cada roteador a' rede SDN (PoP)
 */
public class Conexoes {

    // Descrevem o switch, porta onde o Roteador do cliente se conecta à rede do POP
    private String dpid;
    private String portNumber;

    // Endereço IP do ponto a ponto
    private List<String> ipAddresses;

    public String getDpid() {
        return dpid;
    }

    @JsonProperty("dpid")
    public void setDpid(String strDpid) {
        this.dpid = strDpid;
    }

    public String getPortNumber() {
        return portNumber;
    }

    @JsonProperty("port")
    public void setPortNumber(String portNumber) {
        this.portNumber = portNumber;
    }

    public List<String> getIpAddresses() {
        return ipAddresses;
    }

    @JsonProperty("ips")
    public void setIpAddresses(List<String> strIps) {
        this.ipAddresses = strIps;
    }
}

