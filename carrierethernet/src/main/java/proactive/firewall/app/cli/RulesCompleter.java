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

import org.apache.karaf.shell.console.Completer;
import org.apache.karaf.shell.console.completer.StringsCompleter;
import org.onosproject.cli.AbstractShellCommand;
import proactive.firewall.app.FirewallService;

import java.util.ArrayList;
import java.util.List;


public class RulesCompleter implements Completer {

    @Override
    public int complete(String buffer, int cursor, List<String> candidates) {
        StringsCompleter delegate = new StringsCompleter();

        FirewallService service = AbstractShellCommand.get(FirewallService.class);
        ArrayList <String> lista = service.listAllRules();
        for (String rule : lista) {
            if (rule.startsWith("SrcRule") || rule.startsWith("DstRule")) {
                rule = rule.substring(9);
            }
            else if (rule.startsWith("SrcDstRule")){
                rule = rule.substring(12);
            }
            rule = rule.replace("<-->","");
            rule = rule.replace(':', ' ');

            delegate.getStrings().add(rule);
        }

        //delegate.getStrings().addAll(service.listAllRules());

        // Now let the completer do the work for figuring out what to offer.
        return delegate.complete(buffer, cursor, candidates);
    }
}