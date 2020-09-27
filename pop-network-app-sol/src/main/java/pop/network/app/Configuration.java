/*
 * Copyright 2014 Open Networking Laboratory
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
package pop.network.app;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Configuration {
    private List<Clientes> clientes;
    private List<Conexoes> conexoes;


    /**
     * Default constructor.
     */
    public Configuration() {
    }

    public List<Clientes> getRoteadores() {
        return Collections.unmodifiableList(clientes);
    }

    @JsonProperty("roteadores")
    public void setRoteadores(List<Clientes> clientes) {
        this.clientes = clientes;
    }

    public List<Conexoes> getConexoes() {
        return Collections.unmodifiableList(conexoes);
    }

    @JsonProperty("dispositivos")
    public void setConexoes(List<Conexoes> conexoes) {
        this.conexoes = conexoes;
    }
}