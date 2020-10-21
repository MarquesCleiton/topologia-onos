package org.onosproject.ecord.carrierethernet.cli.commands;

import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;

@Command(scope = "onos", name = "meu-teste",
description = "Um teste meu")
public class MeuTeste extends AbstractShellCommand{

	@Override
	protected void execute() {
		print("Mais uma aplicacao Onos");
		
	}

}
