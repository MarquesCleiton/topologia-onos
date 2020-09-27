package proactive.firewall.app.cli;

import org.apache.felix.gogo.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import proactive.firewall.app.FirewallService;

/**
 * CLI para remover a regra especificada no argumento
 * O parametro name especifica o nome do comando na CLI
 */
@Command(scope = "proactiveFirewall", name = "fwRemove-srcRule", description = "Remove Regra de Bloqueio para a origem especificada - IP/[PORTA]")
public class FwSrcRule_Remove extends AbstractShellCommand {

    @Argument(index = 0, name = "srcIp", description = "Ip de Origem do Bloqueio",
            required = true, multiValued = false)
    String srcIp = null;

    @Argument(index = 1, name = "srcPort", description = "Porta a ser bloqueada [opcional]",
            required = false, multiValued = false)
    String srcPort = null;

    @Override
    protected void execute() {
        FirewallService firewallService = get(FirewallService.class);
        firewallService.fwRemoveSrcRule(srcIp, srcPort);
        print("Bloqueio removido com sucesso para o origem %s - A porta informada foi %s [null representa todas as portas]", srcIp, srcPort);
        print("Para listar as regras existentes, por favor execute o comando fwlist-allrules");
    }
}
