package pop.network.app;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Representa as rotas de um cliente especifico
 */
public class Clientes {
    private String name;
    // IP do Ponto a Ponto do Cliente
    private String ipRouter;
    // Rotas divulgadas pelo cliente (BLOCO)
    private List<String> rotas;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String strName) {
        this.name = strName;
    }

    public String getIpRouter() {
        return ipRouter;
    }

    @JsonProperty("ip")
    public void setIpRouter(String ipRouter) {
        this.ipRouter = ipRouter;
    }

    public List<String> getRotas() {
        return rotas;
    }

    @JsonProperty("rotas")
    public void setRotas(List<String> strIps) {
        this.rotas = strIps;
    }

}