package br.com.usjt.simplefirewall;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;

@Command(scope = "onos", name = "meu-teste", description = "meu teste")
public class MaisUmTeste extends AbstractShellCommand{
	
	@Argument(index = 0, name = "argEvcCfgId",
            description = "EVC configuration ID", required = true, multiValued = false)
    String argEvcCfgId = null;
	@Override
	protected void execute() {
		print("testando a minha aplicação");
	}

}
