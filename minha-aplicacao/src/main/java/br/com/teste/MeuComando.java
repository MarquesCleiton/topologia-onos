package br.com.teste;

import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;

@Command(scope = "onos", name = "meu-comando", description = "testando o meu comando")
public class MeuComando extends AbstractShellCommand{

	@Override
	protected void execute() {
		
	}

}
