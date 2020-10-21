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

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import proactive.firewall.app.FirewallService;

/**
 * CLI para criar a regra de origem especificada no Firewall
 * O parametro name especifica o nome do comando na CLI
 */
@Command(scope = "proactiveFirewall", name = "fwAdd-srcRule", description = "Adicione Regra de Bloqueio para a origem especificada - IP/[PORTA]")
public class FwSrcRule_Add extends AbstractShellCommand {

    // Os argumentos indicam os parametros do comando. Index 0 é o primeiro parametro do comando
    @Argument(index = 0, name = "srcIp", description = "Ip de Origem do Bloqueio",
            required = true, multiValued = false)
    String srcIp = null;

    // Os argumentos indicam os parametros do comando. O argumento required=false indica que o parametro é opcional
    @Argument(index = 1, name = "srcPort", description = "Porta a ser bloqueada [opcional]",
            required = false, multiValued = false)
    String srcPort = null;

    // Execute() é o codigo que sera executado pela aplicação ao se executar o comando na CLI
    @Override
    protected void execute() {
        FirewallService firewallService = get(FirewallService.class);
        // Executa a funçao responsavel por adicionar a regra no Firewall e criar o bloqueio.
        firewallService.fwAddSrcRule(srcIp, srcPort);
        print("Bloqueio criado com sucesso para o origem %s - A porta informada foi %s [null representa todas as portas]", srcIp, srcPort);
        print("Para listar as regras existentes, por favor execute o comando fwlist-allrules");
    }
}