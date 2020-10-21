package proactive.firewall.app.cli;

/**
 * Created by mininet on 7/29/15.
 */

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import proactive.firewall.app.FirewallService;

/**
 * CLI to add a host to a network.
 */
@Command(scope = "proactiveFirewall", name = "fwRemove-pairRule", description = "Remove regra de Bloqueio para o par de IPS especificados no formato IP:[PORTA]")
public class FwPairRule_Remove extends AbstractShellCommand {

    @Argument(index = 0, name = "srcIpPort", description = "Ip:[Porta] de Origem do bloqueio",
            required = true, multiValued = false)
    String srcIp = null;

    @Argument(index = 1, name = "dstIpPort", description = "Ip:[Porta] de Destino do bloqueio",
            required = true, multiValued = false)
    String dstIp = null;

    @Override
    protected void execute() {
        FirewallService firewallService = get(FirewallService.class);
        firewallService.fwRemovePairRule(srcIp, dstIp);
        print("Bloqueio removido com sucesso para o par de ips - %s <--> %s", srcIp, dstIp);
        print("Para listar as regras existentes, por favor execute o comando fwlist-allrules");
    }
}
