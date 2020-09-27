package proactive.firewall.app.cli;

import org.apache.felix.gogo.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import proactive.firewall.app.FirewallService;

/**
 * CLI to add a host to a network.
 */
@Command(scope = "proactiveFirewall", name = "fwRemove-dstRule", description = "Remove Regra de Bloqueio para o destino especificada - IP/[PORTA]")
public class FwDstRule_Remove extends AbstractShellCommand {

    @Argument(index = 0, name = "srcIp", description = "Ip de destino do Bloqueio",
            required = true, multiValued = false)
    String dstIp = null;

    @Argument(index = 1, name = "srcPort", description = "Porta a ser bloqueada [opcional]",
            required = false, multiValued = false)
    String dstPort = null;


    @Override
    protected void execute() {
        FirewallService firewallService = get(FirewallService.class);
        firewallService.fwRemoveDstRule(dstIp, dstPort);
        print("Bloqueio removido com sucesso para o destino %s - A porta informada foi %s [null representa todas as portas]", dstIp, dstPort);
        print("Para listar as regras existentes, por favor execute o comando fwlist-allrules");
    }
}
