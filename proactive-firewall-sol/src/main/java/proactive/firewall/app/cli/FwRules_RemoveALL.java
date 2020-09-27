package proactive.firewall.app.cli;

import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import proactive.firewall.app.FirewallService;

/**
 * CLI para remover todas as regras do Firewall
 * O parametro name especifica o nome do comando na CLI
 */
@Command(scope = "proactiveFirewall", name = "fwremove-AllRules", description = "Remove todas as regras criadas pelo firewall")
public class FwRules_RemoveALL extends AbstractShellCommand {

    // codigo a ser executado ao se utilizar o comando na CLI
    @Override
    protected void execute() {
        FirewallService firewallService = get(FirewallService.class);
        /*
         * TODO Lab 1: Manipulando Comandos (Remover todas as Regras)
         *
         * Não ha misterios nesse comando. Devemos apenas realizar uma chamada à função fwRemoveAllRule()
         * implementada em nossa classe principal (ProactiveFirewall.java, linha 202).
         * Após chamar a função de remoção das regras, vá ate a linha mencionada
         * para implementar a funcionamento do comando.
         */
        firewallService.fwRemoveAllRule();
        print("Todas as regras do Firewall foram removidas. Nao ha bloqueios ativos.");
    }
}
