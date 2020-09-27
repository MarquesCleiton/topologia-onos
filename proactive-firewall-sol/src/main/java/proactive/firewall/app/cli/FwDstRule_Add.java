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

package proactive.firewall.app.cli;

import org.apache.felix.gogo.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import proactive.firewall.app.FirewallService;

/**
 * CLI para criar a regra de destino especificada no Firewall
 * O parametro name especifica o nome do comando na CLI
 */
@Command(scope = "proactiveFirewall", name = "fwAdd-dstRule", description = "Adicione Regra de Bloqueio para o destino especificado - IP/[PORTA]")
public class FwDstRule_Add extends AbstractShellCommand {

    @Argument(index = 0, name = "dstIp", description = "Ip de Destino do Bloqueio",
            required = true, multiValued = false)
    String dstIp = null;

    @Argument(index = 1, name = "dstPort", description = "Porta a ser bloqueada [opcional]",
            required = false, multiValued = false)
    String dstPort = null;

    @Override
    protected void execute() {
        FirewallService firewallService = get(FirewallService.class);
        firewallService.fwAddDstRule(dstIp, dstPort);
        print("Bloqueio criado com sucesso para o destino %s - A porta informada foi %s [null representa todas as portas]", dstIp, dstPort);
        print("Para listar as regras existentes, por favor execute o comando fwlist-allrules");
    }
}